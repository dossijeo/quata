#!/usr/bin/env node
import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const CHECK = "ABOUT-RELEASE-HISTORY-IOS-COMMON-001";

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  evidence: {},
};

try {
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
bash scripts/build-ios-intel-simulator-signed.sh
`);
    report.steps.push("ios_simulator_signed_build_succeeded_on_mac");
  }

  await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
export QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR=${shellQuote(options.remoteLogDir)}
export QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_RESULT_BUNDLE_DIR=${shellQuote(`${options.remoteLogDir}/xcresults`)}
bash scripts/run-ios-about-release-history-ui-test.sh
`);
  report.steps.push("ios_about_release_history_ui_test_passed");

  await copyRemoteEvidence(options);
  report.evidence.files = await evidenceFileHashes(options.evidenceDir);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  await copyRemoteEvidence(options).catch((copyError) => {
    report.evidence.copyWarning = safeFailure(copyError);
  });
} finally {
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`About/Release History iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`About/Release History iOS evidence failed: ${report.error?.message ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("About/Release History iOS evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    remoteLogDir: process.env.QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR?.trim() || "build/reports/ios/ABOUT-RELEASE-HISTORY-ui",
    output: resolve(join("build-reports", "ios", "about-release-history-evidence.json")),
    evidenceDir: resolve(join("build-reports", "ios", "about-release-history-evidence")),
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
      if (key === "--out") parsed.output = resolve(value);
      if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
      if (key === "--simulator") parsed.simulatorUdid = value;
    } else if (key === "--build-first") {
      parsed.buildFirst = true;
    } else {
      throw new Error(`unknown_argument:${key}`);
    }
  }
  if (!parsed.simulatorUdid) throw new Error("missing_environment:QUATA_IOS_SIMULATOR_UDID");
  return parsed;
}

async function copyRemoteEvidence({ host, project, remoteLogDir, evidenceDir }) {
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  const source = remoteLogDir.startsWith("/") ? remoteLogDir : `${project}/${remoteLogDir}`;
  await run("scp", ["-r", `${host}:${source}/.`, evidenceDir]);
  report.evidence.directory = resolve(evidenceDir);
}

async function evidenceFileHashes(evidenceDir) {
  const files = {};
  for (const entry of await readdir(evidenceDir, { withFileTypes: true })) {
    const path = join(evidenceDir, entry.name);
    files[entry.name] = entry.isFile()
      ? createHash("sha256").update(await readFile(path)).digest("hex")
      : { type: "directory" };
  }
  return files;
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

function runCapture(command, args, { input = null } = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, { stdio: ["pipe", "pipe", "pipe"], shell: process.platform === "win32" });
    let output = "";
    child.stdout.on("data", (chunk) => { output += chunk; });
    child.stderr.on("data", (chunk) => { output += chunk; });
    child.on("error", rejectPromise);
    child.on("close", (code) => code === 0 ? resolvePromise(output) : rejectPromise(new Error(`${command} ${args.join(" ")} failed:${code}\n${redactedTail(output)}`)));
    if (input) child.stdin.end(input); else child.stdin.end();
  });
}

function safeFailure(error) {
  return {
    name: error?.name ?? "Error",
    message: redactedTail(error?.message ?? String(error)).slice(0, 800),
  };
}

function redactedTail(value) {
  return String(value)
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .split(/\r?\n/)
    .slice(-100)
    .join("\n");
}
