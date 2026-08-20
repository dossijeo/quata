#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { longMp4FixturePath, validPngFixture } from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "POST-PICKER-CAMERA-IOS-REAL-001";
const OPT_IN = "I_ACCEPT_IOS_POST_COMPOSER_PICKER_FIXTURE";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";

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
let localFixture;
let localFixtureTempDir;
let remoteFixture;

try {
  const credentials = (await loadCredentials()).a;
  localCredentials = join(await mkdirTemp("quata-ios-post-picker-credentials-"), "credentials.json");
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: credentials.country_code,
      phone: e164Phone(credentials.country_code, credentials.phone),
      password: credentials.password,
    })}\n`,
    { mode: 0o600 },
  );
  remoteCredentials = (await runCapture("ssh", [options.host, "mktemp /tmp/quata-ios-post-picker-credentials.XXXXXX.json"])).trim();
  await run("scp", [localCredentials, `${options.host}:${remoteCredentials}`]);
  report.steps.push("ios_real_credentials_copied_to_mac_tempfile_without_logging_contents");

  const fixtureExtension = options.mediaType === "video" ? "mp4" : "png";
  const fixtureName = options.mediaType === "video" ? "post-picker-camera-fixture.mp4" : "post-picker-camera-fixture.png";
  if (options.mediaType === "video") {
    localFixture = options.videoFixture;
  } else {
    localFixtureTempDir = await mkdirTemp("quata-ios-post-picker-fixture-");
    localFixture = join(localFixtureTempDir, fixtureName);
    await writeFile(localFixture, validPngFixture(), { mode: 0o600 });
  }
  remoteFixture = (await runCapture("ssh", [options.host, `mktemp /tmp/quata-ios-post-picker-fixture.XXXXXX.${fixtureExtension}`])).trim();
  await run("scp", [localFixture, `${options.host}:${remoteFixture}`]);
  report.steps.push("ios_picker_fixture_copied_to_mac_tempfile");

  const remoteHead = (await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
git rev-parse HEAD
`)).trim();
  report.mac = { host: options.host, project: options.project, head: remoteHead };
  if (remoteHead !== report.git.head) throw new Error(`mac_checkout_sha_mismatch:${remoteHead}:${report.git.head}`);
  report.steps.push("mac_checkout_sha_matches_local_candidate");

  if (options.buildFirst) {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
scripts/build-ios-intel-simulator-signed.sh
`);
    report.steps.push("ios_simulator_signed_build_succeeded_on_mac");
  }

  for (const descriptor of options.sources) {
    report.attempts.push(await runAttempt(descriptor));
  }
  const failedAttempt = report.attempts.find((attempt) => attempt.status !== "passed");
  if (failedAttempt) throw new Error(`ios_attempt_failed:${failedAttempt.source}:${failedAttempt.outcome}`);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
} finally {
  await copyRemoteEvidence(options).catch((error) => {
    report.evidence.copyWarning = safeFailure(error);
  });
  if (remoteCredentials) await run("ssh", [options.host, "rm", "-f", remoteCredentials]).catch(() => {});
  if (remoteFixture) await run("ssh", [options.host, "rm", "-f", remoteFixture]).catch(() => {});
  if (localCredentials) await rm(dirname(localCredentials), { recursive: true, force: true }).catch(() => {});
  if (localFixtureTempDir) await rm(localFixtureTempDir, { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Post picker/camera iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post picker/camera iOS evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post picker/camera iOS evidence passed.");
}

async function runAttempt(descriptor) {
  const [source, outcome = "success"] = descriptor.split(":");
  if (!["gallery", "camera"].includes(source)) throw new Error(`invalid_source:${source}`);
  if (!["success", "cancelled", "failure", "unsupported", "permission-denied"].includes(outcome)) throw new Error(`invalid_outcome:${outcome}`);
  const remoteLogDir = `${options.remoteLogDir}/${source}-${outcome}`;
  try {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
export QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
export QUATA_IOS_POST_PICKER_CAMERA_UI_LOG_DIR=${shellQuote(remoteLogDir)}
export QUATA_IOS_POST_PICKER_CAMERA_UI_RESULT_BUNDLE_DIR=${shellQuote(`${remoteLogDir}/xcresults`)}
export QUATA_IOS_POST_COMPOSER_PICKER_FIXTURE_OPT_IN=${shellQuote(OPT_IN)}
export QUATA_IOS_POST_COMPOSER_PICKER_SOURCE=${shellQuote(source)}
export QUATA_IOS_POST_COMPOSER_PICKER_OUTCOME=${shellQuote(outcome)}
export QUATA_IOS_POST_COMPOSER_PICKER_MEDIA_TYPE=${shellQuote(options.mediaType)}
export QUATA_IOS_POST_COMPOSER_PICKER_PATH=${shellQuote(remoteFixture)}
export QUATA_IOS_POST_COMPOSER_PICKER_NAME=${shellQuote(options.mediaType === "video" ? "post-picker-camera-fixture.mp4" : "post-picker-camera-fixture.png")}
export QUATA_IOS_POST_COMPOSER_PICKER_MIME=${shellQuote(options.mediaType === "video" ? "video/mp4" : "image/png")}
bash scripts/run-ios-post-picker-camera-ui-test.sh
`);
    return { source, outcome, mediaType: options.mediaType, status: "passed", remoteLogDir };
  } catch (error) {
    return { source, outcome, mediaType: options.mediaType, status: "failed", remoteLogDir, error: safeFailure(error) };
  }
}

function parseArgs(args) {
  const parsed = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    remoteLogDir: process.env.QUATA_IOS_POST_PICKER_CAMERA_UI_LOG_DIR?.trim() || "build/reports/ios/post-picker-camera-ui",
    output: join("build-reports", "ios", "post-picker-camera-evidence.json"),
    evidenceDir: join("build-reports", "ios", "post-picker-camera-evidence"),
    mediaType: process.env.QUATA_POST_PICKER_CAMERA_MEDIA_TYPE?.trim() || "image",
    videoFixture: resolve(process.env.QUATA_POST_PICKER_CAMERA_VIDEO_FIXTURE?.trim() || longMp4FixturePath()),
    simulatorUdid: process.env.QUATA_IOS_SIMULATOR_UDID?.trim() || "",
    buildFirst: process.env.QUATA_IOS_BUILD_FIRST === "1",
    sources: ["gallery", "camera", "camera:cancelled", "gallery:permission-denied", "camera:permission-denied"],
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (["--host", "--project", "--derived-data", "--remote-log-dir", "--out", "--evidence-dir", "--simulator", "--sources", "--media", "--video-fixture"].includes(key)) {
      if (!value || value.startsWith("--")) throw new Error(`missing_value:${key}`);
      index += 1;
      if (key === "--host") parsed.host = value;
      if (key === "--project") parsed.project = value;
      if (key === "--derived-data") parsed.derivedDataPath = value;
      if (key === "--remote-log-dir") parsed.remoteLogDir = value;
      if (key === "--out") parsed.output = value;
      if (key === "--evidence-dir") parsed.evidenceDir = value;
      if (key === "--simulator") parsed.simulatorUdid = value;
      if (key === "--sources") parsed.sources = value.split(",").map((item) => item.trim()).filter(Boolean);
      if (key === "--media") parsed.mediaType = value;
      if (key === "--video-fixture") parsed.videoFixture = resolve(value);
    } else if (key === "--build-first") {
      parsed.buildFirst = true;
    } else {
      throw new Error(`unknown_argument:${key}`);
    }
  }
  if (!parsed.simulatorUdid) throw new Error("missing_environment:QUATA_IOS_SIMULATOR_UDID");
  if (!["image", "video"].includes(parsed.mediaType)) throw new Error(`invalid_media:${parsed.mediaType}`);
  parsed.output = resolve(parsed.output);
  parsed.evidenceDir = resolve(parsed.evidenceDir);
  return parsed;
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_POST_PICKER_CAMERA_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
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
