#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import {
  destinationFixtureSummary,
  loadPublicBackendConfig,
  loginDestinationActor,
  selectRealAlternateDestination,
} from "./post-destination-real-fixture.mjs";

const CHECK = "POST-DESTINATION-IOS-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const modes = ["real-multiple", "empty", "failure"];

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  attempts: [],
  evidence: {},
  steps: [],
};

let localCredentials;
let remoteCredentials;
let remoteRuntimeBackup;
let remoteGeneratedXcodeProjectOwned = false;
let realMultipleFixture;

try {
  const credentials = (await loadCredentials()).a;
  const backend = await loadPublicBackendConfig();
  const fixtureSession = await loginDestinationActor(backend, credentials, "quata-post-destination-ios-evidence");
  realMultipleFixture = await selectRealAlternateDestination(backend, fixtureSession);
  report.evidence.realMultiple = destinationFixtureSummary(realMultipleFixture);
  localCredentials = join(await mkdirTemp("quata-ios-post-destination-credentials-"), "credentials.json");
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: credentials.country_code,
      phone: e164Phone(credentials.country_code, credentials.phone),
      password: credentials.password,
    })}\n`,
    { mode: 0o600 },
  );
  remoteCredentials = (await runCapture("ssh", [options.host, "mktemp /tmp/quata-ios-post-destination-credentials.XXXXXX.json"])).trim();
  await run("scp", [localCredentials, `${options.host}:${remoteCredentials}`]);
  report.steps.push("ios_real_credentials_copied_to_mac_tempfile_without_logging_contents");

  const remoteHead = (await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
git rev-parse HEAD
`)).trim();
  report.mac = { host: options.host, project: options.project, head: remoteHead };
  if (remoteHead !== report.git.head) throw new Error(`mac_checkout_sha_mismatch:${remoteHead}:${report.git.head}`);
  report.steps.push("mac_checkout_sha_matches_local_candidate");

  remoteRuntimeBackup = await allocateRemoteRuntimeBackup(options);
  await prepareRemotePublicRuntimeConfig(options, remoteRuntimeBackup);
  report.steps.push("ios_public_runtime_xcconfig_prepared_transiently");

  if (options.buildFirst) {
    remoteGeneratedXcodeProjectOwned = await generatedXcodeProjectAbsent(options);
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
scripts/build-ios-intel-simulator-signed.sh
`);
    report.steps.push("ios_simulator_signed_build_succeeded_on_mac");
  }

  for (const mode of options.modes) {
    report.attempts.push(await runAttempt(mode));
  }
  const failed = report.attempts.find((attempt) => attempt.status !== "passed");
  if (failed) throw new Error(`ios_destination_attempt_failed:${failed.mode}`);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
} finally {
  await copyRemoteEvidence(options).catch((error) => {
    report.evidence.copyWarning = safeFailure(error);
  });
  report.environmentCleanup = {};
  if (remoteRuntimeBackup) {
    await restoreRemotePublicRuntimeConfig(options, remoteRuntimeBackup).catch((error) => {
      report.environmentCleanup.runtimeConfigRestoreError = safeFailure(error);
      report.status = "failed";
    });
    report.environmentCleanup.runtimeConfigRestored = !report.environmentCleanup.runtimeConfigRestoreError;
  }
  if (remoteGeneratedXcodeProjectOwned) {
    await cleanupGeneratedXcodeProject(options).catch((error) => {
      report.environmentCleanup.xcodeProjectCleanupError = safeFailure(error);
      report.status = "failed";
    });
    report.environmentCleanup.ownedXcodeProjectRemoved = !report.environmentCleanup.xcodeProjectCleanupError;
  }
  if (remoteCredentials) {
    await removeAndVerifyRemoteCredentials(options, remoteCredentials).catch((error) => {
      report.environmentCleanup.remoteCredentialsCleanupError = safeFailure(error);
      report.status = "failed";
    });
  }
  if (localCredentials) {
    await rm(dirname(localCredentials), { recursive: true, force: true }).catch((error) => {
      report.environmentCleanup.localCredentialsCleanupError = safeFailure(error);
      report.status = "failed";
    });
    if (existsSync(localCredentials)) {
      report.environmentCleanup.localCredentialsCleanupError = "local_credentials_still_present";
      report.status = "failed";
    }
  }
  report.environmentCleanup.temporaryCredentialsRemoved =
    !report.environmentCleanup.remoteCredentialsCleanupError &&
    !report.environmentCleanup.localCredentialsCleanupError;
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Post destination iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post destination iOS evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post destination iOS evidence passed.");
}

async function runAttempt(mode) {
  const remoteLogDir = `${options.remoteLogDir}/${mode}`;
  try {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
export QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
export QUATA_IOS_POST_DESTINATION_UI_LOG_DIR=${shellQuote(remoteLogDir)}
export QUATA_IOS_POST_DESTINATION_UI_RESULT_BUNDLE_DIR=${shellQuote(`${remoteLogDir}/xcresults`)}
export QUATA_IOS_POST_DESTINATION_E2E_MODE=${shellQuote(mode)}
export QUATA_IOS_POST_DESTINATION_ALTERNATE_WALL_ID=${shellQuote(realMultipleFixture.alternateWallId)}
bash scripts/run-ios-post-destination-ui-test.sh
`);
    return { mode, status: "passed", remoteLogDir };
  } catch (error) {
    return { mode, status: "failed", remoteLogDir, error: safeFailure(error) };
  }
}

function parseArgs(args) {
  const parsed = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    remoteLogDir: process.env.QUATA_IOS_POST_DESTINATION_UI_LOG_DIR?.trim() || "build/reports/ios/post-destination-ui",
    output: join("build-reports", "ios", "post-destination-evidence.json"),
    evidenceDir: join("build-reports", "ios", "post-destination-evidence"),
    simulatorUdid: process.env.QUATA_IOS_SIMULATOR_UDID?.trim() || "",
    buildFirst: process.env.QUATA_IOS_BUILD_FIRST === "1",
    modes,
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (["--host", "--project", "--derived-data", "--remote-log-dir", "--out", "--evidence-dir", "--simulator", "--modes"].includes(key)) {
      if (!value || value.startsWith("--")) throw new Error(`missing_value:${key}`);
      index += 1;
      if (key === "--host") parsed.host = value;
      if (key === "--project") parsed.project = value;
      if (key === "--derived-data") parsed.derivedDataPath = value;
      if (key === "--remote-log-dir") parsed.remoteLogDir = value;
      if (key === "--out") parsed.output = value;
      if (key === "--evidence-dir") parsed.evidenceDir = value;
      if (key === "--simulator") parsed.simulatorUdid = value;
      if (key === "--modes") parsed.modes = value.split(",").map((item) => item.trim()).filter(Boolean);
    } else if (key === "--build-first") {
      parsed.buildFirst = true;
    } else {
      throw new Error(`unknown_argument:${key}`);
    }
  }
  if (!parsed.simulatorUdid) throw new Error("missing_environment:QUATA_IOS_SIMULATOR_UDID");
  if (parsed.modes.some((mode) => !modes.includes(mode))) throw new Error("invalid_destination_mode");
  parsed.output = resolve(parsed.output);
  parsed.evidenceDir = resolve(parsed.evidenceDir);
  return parsed;
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_POST_DESTINATION_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
  for (const profile of ["a"]) {
    for (const field of ["country_code", "phone", "password"]) {
      if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
    }
  }
  return credentials;
}

function localPhone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  return digits.startsWith(country) ? digits.slice(country.length) : digits;
}

function e164Phone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const local = localPhone(countryCode, phone);
  if (!country || !local) throw new Error("ios_e164_credentials_required");
  return `+${country}${local}`;
}

async function copyRemoteEvidence({ host, project, remoteLogDir, evidenceDir }) {
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  const source = remoteLogDir.startsWith("/") ? remoteLogDir : `${project}/${remoteLogDir}`;
  await run("scp", ["-r", `${host}:${source}/.`, evidenceDir]);
  report.evidence.directory = resolve(evidenceDir);
}

async function allocateRemoteRuntimeBackup({ host }) {
  return (await runCapture("ssh", [host, "mktemp /tmp/quata-ios-post-destination-runtime.XXXXXX"])).trim();
}

async function prepareRemotePublicRuntimeConfig({ host, project }, backupPath) {
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
[ -f "$meta" ] || { rm -f "$backup_config"; exit 0; }
. "$meta"
QUATA_RUNTIME_CONFIG_HAD="\${had:-0}"
QUATA_RUNTIME_CONFIG_MODE="\${mode:-}"
source scripts/ios-public-runtime-config-backup.sh
quata_restore_runtime_config "$runtime_config" "$backup_config"
rm -f "$meta"
`);
}

async function generatedXcodeProjectAbsent({ host, project }) {
  const result = await runSshScript(host, `
set -euo pipefail
cd ${shellQuote(project)}
if [ -d iosApp/QuataIos.xcodeproj ]; then printf 'present'; else printf 'absent'; fi
`);
  return result.trim() === "absent";
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

async function removeAndVerifyRemoteCredentials({ host }, credentialsPath) {
  await runSshScript(host, `
set -euo pipefail
credentials=${shellQuote(credentialsPath)}
rm -f "$credentials"
[ ! -e "$credentials" ]
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
  return runCapture(command, args, options).then(() => {});
}

function runCapture(command, args, { input = null, allowFailure = false } = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, { stdio: ["pipe", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => { stdout += chunk; });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("error", rejectPromise);
    child.on("close", (code) => {
      if (code !== 0 && !allowFailure) {
        rejectPromise(new Error(`${command} exited ${code}: ${redactedTail(stderr || stdout)}`));
      } else {
        resolvePromise(stdout);
      }
    });
    if (input) child.stdin.end(input);
    else child.stdin.end();
  });
}

function redactedTail(value) {
  return String(value ?? "")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .split(/\r?\n/)
    .slice(-80)
    .join("\n");
}

function safeFailure(error) {
  return redactedTail(error?.message ?? String(error));
}
