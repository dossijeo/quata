#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { setTimeout as delay } from "node:timers/promises";

const CHECK = "ACCOUNT-DETAILS-IOS-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  attempts: [],
  evidence: {},
  cleanup: { attempted: false, profileRestored: false },
  steps: [],
};

let localCredentials;
let remoteCredentials;
let remoteRuntimeBackup;
let backend;
let session;
let original;

try {
  backend = await publicConfig();
  const credentials = (await loadCredentials()).a;
  session = await login(backend, credentials, `ACCOUNT-DETAILS-ios-${randomUUID()}`);
  original = await fetchProfile(backend, session);
  const marker = String(Date.now()).slice(-6);
  const update = {
    display_name: `Gabrielo QA ${marker}`,
    neighborhood: `Bata QA ${marker}`,
    country_code: original.country_code || credentials.country_code,
    phone_local: evidencePhone(original.phone_local || localPhone(original.country_code || credentials.country_code, credentials.phone)),
  };

  localCredentials = join(await mkdirTemp("quata-ios-account-details-credentials-"), "credentials.json");
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: credentials.country_code,
      phone: e164Phone(credentials.country_code, credentials.phone),
      password: credentials.password,
    })}\n`,
    { mode: 0o600 },
  );
  remoteCredentials = (await runCapture("ssh", [options.host, "mktemp /tmp/quata-ios-account-details-credentials.XXXXXX.json"])).trim();
  await run("scp", [localCredentials, `${options.host}:${remoteCredentials}`]);
  report.steps.push("ios_real_credentials_copied_to_mac_tempfile_without_logging_contents");

  const remoteState = JSON.parse((await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
head="$(git rev-parse HEAD)"
if [ -n "$(git status --porcelain)" ]; then dirty=true; else dirty=false; fi
printf '{"head":"%s","workingTreeDirty":%s}\\n' "$head" "$dirty"
`)).trim());
  report.mac = { host: options.host, project: options.project, head: remoteState.head, workingTreeDirty: remoteState.workingTreeDirty };
  if (remoteState.head !== report.git.head) throw new Error(`mac_checkout_sha_mismatch:${remoteState.head}:${report.git.head}`);
  if (remoteState.workingTreeDirty !== false) throw new Error("mac_checkout_dirty");
  report.steps.push("mac_checkout_sha_matches_local_candidate");
  report.steps.push("mac_checkout_clean");

  remoteRuntimeBackup = await prepareRemotePublicRuntimeConfig(options);
  report.steps.push("ios_public_runtime_xcconfig_prepared_transiently");

  if (options.buildFirst) {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
scripts/build-ios-intel-simulator-signed.sh
`);
    report.steps.push("ios_simulator_signed_build_succeeded_on_mac");
  }

  report.attempts.push(await runAttempt(update));
  const failedAttempt = report.attempts.find((attempt) => attempt.status !== "passed");
  if (failedAttempt) throw new Error(`ios_attempt_failed:${failedAttempt.error ?? "unknown"}`);
  const persisted = await waitForRemoteProfile(backend, session, update);
  if (!persisted) throw new Error("ios_account_details_remote_persist_timeout");
  report.evidence.remote = {
    changedFields: {
      displayName: original.display_name !== update.display_name,
      neighborhood: original.neighborhood !== update.neighborhood,
      phone: normalizedDigits(original.phone_local) !== normalizedDigits(update.phone_local),
    },
    remotePersisted: true,
    reloadVerifiedByUi: true,
  };
  await restoreProfile(backend, session, original);
  report.cleanup = { attempted: true, profileRestored: await waitForRemoteProfile(backend, session, original) };
  if (report.cleanup.profileRestored !== true) throw new Error("ios_account_details_cleanup_not_verified");
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message.slice(0, 500) : String(error).slice(0, 500);
  if (backend && session && original) {
    await restoreProfile(backend, session, original).catch((cleanupError) => {
      report.cleanup.error = safeFailure(cleanupError);
    });
  }
} finally {
  await copyRemoteEvidence(options).catch((error) => {
    report.evidence.copyWarning = safeFailure(error);
  });
  if (remoteRuntimeBackup) {
    await restoreRemotePublicRuntimeConfig(options, remoteRuntimeBackup).catch((error) => {
      report.cleanup.runtimeConfigRestoreError = safeFailure(error);
    });
  }
  await cleanupGeneratedXcodeProject(options).catch((error) => {
    report.cleanup.xcodeProjectCleanupError = safeFailure(error);
  });
  if (remoteCredentials) await run("ssh", [options.host, "rm", "-f", remoteCredentials]).catch(() => {});
  if (localCredentials) await rm(dirname(localCredentials), { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Account details iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Account details iOS evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Account details iOS evidence passed.");
}

async function runAttempt(update) {
  try {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
export QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
export QUATA_IOS_ACCOUNT_DETAILS_UI_LOG_DIR=${shellQuote(options.remoteLogDir)}
export QUATA_IOS_ACCOUNT_DETAILS_UI_RESULT_BUNDLE_DIR=${shellQuote(`${options.remoteLogDir}/xcresults`)}
export QUATA_IOS_ACCOUNT_DETAILS_DISPLAY_NAME=${shellQuote(update.display_name)}
export QUATA_IOS_ACCOUNT_DETAILS_NEIGHBORHOOD=${shellQuote(update.neighborhood)}
export QUATA_IOS_ACCOUNT_DETAILS_PHONE=${shellQuote(update.phone_local)}
bash scripts/run-ios-account-details-ui-test.sh
`);
    return { source: "account-details", outcome: "success", status: "passed", remoteLogDir: options.remoteLogDir };
  } catch (error) {
    return { source: "account-details", outcome: "success", status: "failed", remoteLogDir: options.remoteLogDir, error: safeFailure(error) };
  }
}

async function fetchProfile(backend, session) {
  const response = await fetch(`${backend.url}/rest/v1/community_profiles?id=eq.${encodeURIComponent(session.userId)}&select=display_name,nombre,neighborhood,barrio,country_code,code,phone_local,phone,telefono`, {
    headers: authHeaders(backend.key, session.accessToken),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`profile_fetch_failed:${response.status}`);
  const row = (await response.json())?.[0] ?? {};
  return normalizeProfile(row);
}

function normalizeProfile(row) {
  const countryCode = row.country_code ?? row.code ?? "";
  return {
    display_name: row.display_name ?? row.nombre ?? "",
    neighborhood: row.neighborhood ?? row.barrio ?? "",
    country_code: countryCode,
    phone_local: row.phone_local ?? localPhone(countryCode, row.phone ?? row.telefono ?? ""),
  };
}

async function waitForRemoteProfile(backend, session, expected) {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    const current = await fetchProfile(backend, session);
    if (
      current.display_name === expected.display_name &&
      current.neighborhood === expected.neighborhood &&
      normalizedDigits(current.country_code) === normalizedDigits(expected.country_code) &&
      normalizedDigits(current.phone_local) === normalizedDigits(expected.phone_local)
    ) {
      return true;
    }
    await delay(1_000);
  }
  return false;
}

async function restoreProfile(backend, session, originalProfile) {
  report.cleanup.attempted = true;
  const response = await fetch(`${backend.url}/rest/v1/community_profiles?id=eq.${encodeURIComponent(session.userId)}`, {
    method: "PATCH",
    headers: { ...authHeaders(backend.key, session.accessToken), "content-type": "application/json", prefer: "return=minimal" },
    body: JSON.stringify({
      display_name: originalProfile.display_name,
      nombre: originalProfile.display_name,
      neighborhood: originalProfile.neighborhood,
      barrio: originalProfile.neighborhood,
      country_code: originalProfile.country_code,
      code: originalProfile.country_code,
      phone_local: originalProfile.phone_local,
      phone: `+${normalizedDigits(originalProfile.country_code)}${normalizedDigits(originalProfile.phone_local)}`,
      telefono: originalProfile.phone_local,
    }),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`profile_restore_failed:${response.status}`);
}

function authHeaders(key, accessToken) {
  return { apikey: key, authorization: `Bearer ${accessToken}`, "x-client-info": "quata-account-details-ios-evidence" };
}

function parseArgs(args) {
  const parsed = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    remoteLogDir: process.env.QUATA_IOS_ACCOUNT_DETAILS_UI_LOG_DIR?.trim() || "build/reports/ios/ACCOUNT-DETAILS-ui",
    output: join("build-reports", "ios", "account-details-evidence.json"),
    evidenceDir: join("build-reports", "ios", "account-details-evidence"),
    simulatorUdid: process.env.QUATA_IOS_SIMULATOR_UDID?.trim() || "",
    buildFirst: process.env.QUATA_IOS_BUILD_FIRST === "1",
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (["--host", "--project", "--derived-data", "--remote-log-dir", "--out", "--evidence-dir", "--simulator"].includes(key)) {
      if (!value || value.startsWith("--")) throw new Error(`missing_value:${key}`);
      index += 1;
      if (key === "--host") parsed.host = value;
      if (key === "--project") parsed.project = value;
      if (key === "--derived-data") parsed.derivedDataPath = value;
      if (key === "--remote-log-dir") parsed.remoteLogDir = value;
      if (key === "--out") parsed.output = value;
      if (key === "--evidence-dir") parsed.evidenceDir = value;
      if (key === "--simulator") parsed.simulatorUdid = value;
    } else if (key === "--build-first") {
      parsed.buildFirst = true;
    } else {
      throw new Error(`unknown_argument:${key}`);
    }
  }
  if (!parsed.simulatorUdid) throw new Error("missing_environment:QUATA_IOS_SIMULATOR_UDID");
  parsed.output = resolve(parsed.output);
  parsed.evidenceDir = resolve(parsed.evidenceDir);
  return parsed;
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_ACCOUNT_DETAILS_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
  for (const profile of ["a"]) {
    for (const field of ["country_code", "phone", "password"]) {
      if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
    }
  }
  return credentials;
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
}

async function login(backend, credentials, clientInstanceId) {
  const response = await fetch(`${backend.url}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-account-details-ios-evidence" },
    body: JSON.stringify({
      action: "web_login",
      country_code: String(credentials.country_code),
      phone_local: localPhone(credentials.country_code, credentials.phone),
      password: String(credentials.password),
      client_instance_id: clientInstanceId,
    }),
    signal: AbortSignal.timeout(30_000),
  }).catch(() => null);
  if (!response) throw new Error("public_request_failed:network");
  const payload = JSON.parse(await response.text());
  if (!response.ok) throw new Error(`public_request_failed:http_${response.status}`);
  const sessionPayload = payload?.session;
  const profile = payload?.profile;
  if (typeof sessionPayload?.access_token !== "string" || typeof profile?.id !== "string") throw new Error("invalid_auth_response");
  return { accessToken: sessionPayload.access_token, userId: profile.id, clientInstanceId };
}

function localPhone(countryCode, phone) {
  const country = normalizedDigits(countryCode);
  const digits = normalizedDigits(phone);
  return country && digits.startsWith(country) ? digits.slice(country.length) : digits;
}

function e164Phone(countryCode, phone) {
  const country = normalizedDigits(countryCode);
  const local = localPhone(countryCode, phone);
  if (!country || !local) throw new Error("ios_e164_credentials_required");
  return `+${country}${local}`;
}

function evidencePhone(originalPhone) {
  const digits = normalizedDigits(originalPhone);
  if (digits.endsWith("607")) return `${digits.slice(0, -3)}609`;
  if (digits.endsWith("608")) return `${digits.slice(0, -3)}606`;
  if (digits.length > 1) return `${digits.slice(0, -1)}${digits.endsWith("9") ? "7" : "9"}`;
  return "680242609";
}

function normalizedDigits(value) {
  return String(value ?? "").replace(/\D/g, "");
}

async function copyRemoteEvidence({ host, project, remoteLogDir, evidenceDir }) {
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  const source = remoteLogDir.startsWith("/") ? remoteLogDir : `${project}/${remoteLogDir}`;
  await run("scp", ["-r", `${host}:${source}/.`, evidenceDir]);
  report.evidence.directory = resolve(evidenceDir);
}

async function prepareRemotePublicRuntimeConfig({ host, project }) {
  const backupPath = (await runCapture("ssh", [host, "mktemp /tmp/quata-ios-account-details-runtime.XXXXXX"])).trim();
  await runSshScript(host, `
set -euo pipefail
cd ${shellQuote(project)}
runtime_config="iosApp/Configuration/QuataPublicRuntime.local.xcconfig"
backup_config=${shellQuote(backupPath)}
QUATA_RUNTIME_CONFIG_HAD=0
QUATA_RUNTIME_CONFIG_MODE=""
source scripts/ios-public-runtime-config-backup.sh
quata_backup_runtime_config "$runtime_config" "$backup_config"
{
  printf 'had=%s\\n' "$QUATA_RUNTIME_CONFIG_HAD"
  printf 'mode=%s\\n' "$QUATA_RUNTIME_CONFIG_MODE"
} > "$backup_config.meta"
python3 scripts/ios-public-client-config.py \\
  --source core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt \\
  --output "$runtime_config"
chmod 600 "$runtime_config"
`);
  return backupPath;
}

async function restoreRemotePublicRuntimeConfig({ host, project }, backupPath) {
  await runSshScript(host, `
set -euo pipefail
cd ${shellQuote(project)}
runtime_config="iosApp/Configuration/QuataPublicRuntime.local.xcconfig"
backup_config=${shellQuote(backupPath)}
meta="$backup_config.meta"
QUATA_RUNTIME_CONFIG_HAD=0
QUATA_RUNTIME_CONFIG_MODE=""
if [ -f "$meta" ]; then
  # shellcheck disable=SC1090
  . "$meta"
  QUATA_RUNTIME_CONFIG_HAD="\${had:-0}"
  QUATA_RUNTIME_CONFIG_MODE="\${mode:-}"
fi
source scripts/ios-public-runtime-config-backup.sh
quata_restore_runtime_config "$runtime_config" "$backup_config"
rm -f "$meta"
`);
}

async function cleanupGeneratedXcodeProject({ host, project }) {
  await runSshScript(host, `
set -euo pipefail
cd ${shellQuote(project)}
generated_project="iosApp/QuataIos.xcodeproj"
if [ -d "$generated_project" ]; then
  if git ls-files --error-unmatch "$generated_project" >/dev/null 2>&1; then
    echo "Refusing to remove a versioned Xcode project." >&2
    exit 1
  fi
  if git status --porcelain -- "$generated_project" | grep -q '^?? '; then
    rm -rf "$generated_project"
  fi
fi
`);
}

async function mkdirTemp(prefix) {
  const path = join(tmpdir(), `${prefix}${randomUUID()}`);
  await mkdir(path, { recursive: true, mode: 0o700 });
  return path;
}

async function gitMetadata() {
  const head = (await runCapture("git", ["rev-parse", "HEAD"])).trim();
  const branch = (await runCapture("git", ["branch", "--show-current"])).trim();
  const status = await runCapture("git", ["status", "--porcelain"]);
  return { head, branch, workingTreeDirty: status.trim().length > 0 };
}

async function runSshScript(host, script) {
  return runCapture("ssh", [host, "bash", "-s"], { input: script });
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, "'\\''")}'`;
}

function run(command, args, options = {}) {
  return runCapture(command, args, options).then(() => undefined);
}

function runCapture(command, args, { input = null, allowFailure = false } = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, { stdio: ["pipe", "pipe", "pipe"], shell: process.platform === "win32" });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => { stdout += chunk; });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("error", rejectPromise);
    child.on("close", (code) => {
      if (code !== 0 && !allowFailure) rejectPromise(new Error(`${command} exited ${code}: ${redactedTail(stderr || stdout)}`));
      else resolvePromise(stdout);
    });
    if (input) child.stdin.end(input);
    else child.stdin.end();
  });
}

function redactedTail(value) {
  return String(value ?? "")
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .split(/\r?\n/)
    .slice(-80)
    .join("\n");
}

function safeFailure(error) {
  return redactedTail(error?.message ?? String(error));
}
