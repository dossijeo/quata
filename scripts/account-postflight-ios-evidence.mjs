#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";

const CHECK = "ACCOUNT-POSTFLIGHT-IOS-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  attempts: [],
  evidence: {},
  cleanup: { temporaryCredentialsRemoved: false, runtimeConfigRestored: false },
  steps: [],
};

let localCredentials;
let remoteCredentials;
let remoteRuntimeBackup;
try {
  const credentials = (await loadCredentials()).a;
  localCredentials = join(await mkdirTemp("quata-ios-account-postflight-credentials-"), "credentials.json");
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: credentials.country_code,
      phone: e164Phone(credentials.country_code, credentials.phone),
      password: credentials.password,
    })}\n`,
    { mode: 0o600 },
  );
  remoteCredentials = (await runCapture("ssh", [options.host, "mktemp /tmp/quata-ios-account-postflight-credentials.XXXXXX"])).trim();
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

  report.attempts.push(await runAttempt());
  const failedAttempt = report.attempts.find((attempt) => attempt.status !== "passed");
  if (failedAttempt) throw new Error(`ios_attempt_failed:${failedAttempt.error ?? "unknown"}`);
  report.steps.push("ios_account_root_navigation_and_lifecycle_cancellation_verified");
  report.steps.push("ios_authenticated_session_preserved_after_cancellation_and_relaunch");
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message.slice(0, 500) : String(error).slice(0, 500);
} finally {
  await copyRemoteEvidence(options).catch((error) => {
    report.evidence.copyWarning = safeFailure(error);
    report.status = "failed";
  });
  if (remoteRuntimeBackup) {
    await restoreRemotePublicRuntimeConfig(options, remoteRuntimeBackup).catch((error) => {
      report.cleanup.runtimeConfigRestoreError = safeFailure(error);
      report.status = "failed";
    });
    report.cleanup.runtimeConfigRestored = !report.cleanup.runtimeConfigRestoreError;
  }
  await cleanupGeneratedXcodeProject(options).catch((error) => {
    report.cleanup.xcodeProjectCleanupError = safeFailure(error);
    report.status = "failed";
  });
  if (remoteCredentials) await run("ssh", [options.host, "rm", "-f", remoteCredentials]).catch((error) => {
    report.cleanup.remoteCredentialsError = safeFailure(error);
    report.status = "failed";
  });
  if (localCredentials) await rm(dirname(localCredentials), { recursive: true, force: true }).catch((error) => {
    report.cleanup.localCredentialsError = safeFailure(error);
    report.status = "failed";
  });
  report.cleanup.temporaryCredentialsRemoved = !report.cleanup.remoteCredentialsError && !report.cleanup.localCredentialsError;
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Account postflight iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Account postflight iOS evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Account postflight iOS evidence passed.");
}

async function runAttempt() {
  try {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
export QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
    export QUATA_IOS_ACCOUNT_POSTFLIGHT_UI_LOG_DIR=${shellQuote(options.remoteLogDir)}
    export QUATA_IOS_ACCOUNT_POSTFLIGHT_UI_RESULT_BUNDLE_DIR=${shellQuote(`${options.remoteLogDir}/xcresults`)}
    bash scripts/run-ios-account-postflight-ui-test.sh
`);
    return { source: "account-postflight", outcome: "success", status: "passed", remoteLogDir: options.remoteLogDir };
  } catch (error) {
    return { source: "account-postflight", outcome: "success", status: "failed", remoteLogDir: options.remoteLogDir, error: safeFailure(error) };
  }
}

function parseArgs(args) {
  const parsed = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    remoteLogDir: process.env.QUATA_IOS_ACCOUNT_POSTFLIGHT_UI_LOG_DIR?.trim() || "build/reports/ios/ACCOUNT-POSTFLIGHT-ui",
    output: join("build-reports", "ios", "account-postflight-evidence.json"),
    evidenceDir: join("build-reports", "ios", "account-postflight-evidence"),
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
  const credentials = JSON.parse(await readFile(process.env.QUATA_ACCOUNT_POSTFLIGHT_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
  for (const profile of ["a"]) {
    for (const field of ["country_code", "phone", "password"]) {
      if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
    }
  }
  return credentials;
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
  const backupPath = (await runCapture("ssh", [host, "mktemp /tmp/quata-ios-account-postflight-runtime.XXXXXX"])).trim();
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
