#!/usr/bin/env node
import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const CHECK = "ABOUT-RELEASE-HISTORY-ANDROID-COMMON-001";
const deviceEvidencePath = "files/about-release-history-evidence";

const options = parseArgs(process.argv.slice(2));
const adb = process.env.ADB?.trim() || "adb";
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  evidence: {},
};

try {
  const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"]);
  report.steps.push("android_debug_and_test_apks_built");

  await run(adb, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
  await run(adb, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);

  const instrumentationOutput = await runCapture(adb, [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.feature.whatsnew.presentation.AboutReleaseHistoryCommonBridgeInstrumentedTest",
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  report.instrumentationTail = redactedTail(instrumentationOutput);
  if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) throw new Error("android_instrumentation_not_ok");
  if (/FAILURES!!!|AssumptionViolatedException/i.test(instrumentationOutput)) {
    throw new Error("android_instrumentation_semantic_failure");
  }
  report.steps.push("android_common_about_release_history_test_passed");

  const evidenceDir = resolve(options.evidenceDir);
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  await copyDeviceEvidence(evidenceDir);
  await captureAndroidScreenshot(join(evidenceDir, "android-about-release-history-final.png"));
  report.evidence.directory = evidenceDir;
  report.evidence.files = await evidenceFileHashes(evidenceDir);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  await copyDeviceEvidence(resolve(options.evidenceDir)).catch(() => {});
} finally {
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]).catch(() => {});
  await run(adb, ["uninstall", "com.quata.test"]).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`About/Release History Android evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`About/Release History Android evidence failed: ${report.error?.message ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("About/Release History Android evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    output: resolve(join("build-reports", "android", "about-release-history-evidence.json")),
    evidenceDir: resolve(join("build-reports", "android", "about-release-history-evidence")),
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) {
      throw new Error(`invalid_argument:${key}`);
    }
    index += 1;
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
  }
  return parsed;
}

async function copyDeviceEvidence(evidenceDir) {
  await mkdir(evidenceDir, { recursive: true });
  const listing = await runCapture(adb, ["shell", "run-as", "com.quata", "ls", deviceEvidencePath]).catch(() => "");
  for (const name of listing.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)) {
    await adbRunAsCat(`${deviceEvidencePath}/${name}`, join(evidenceDir, name)).catch(() => {});
  }
}

async function adbRunAsCat(devicePath, localPath) {
  const output = await runBuffer(adb, ["exec-out", "run-as", "com.quata", "cat", devicePath]);
  await writeFile(localPath, output);
}

async function captureAndroidScreenshot(localPath) {
  const output = await runBuffer(adb, ["exec-out", "screencap", "-p"]);
  await writeFile(localPath, output);
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

function runBuffer(command, args) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32" });
    const chunks = [];
    let stderr = "";
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("error", rejectPromise);
    child.on("close", (code) => code === 0 ? resolvePromise(Buffer.concat(chunks)) : rejectPromise(new Error(`${command} ${args.join(" ")} failed:${code}\n${redactedTail(stderr)}`)));
  });
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, "'\\''")}'`;
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
    .slice(-80)
    .join("\n");
}
