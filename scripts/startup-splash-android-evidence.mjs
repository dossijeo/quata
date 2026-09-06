#!/usr/bin/env node
import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const CHECK = "FLOW-SPLASH-STARTUP-ANDROID-001";
const deviceEvidencePath = "files/startup-splash-evidence";
const externalDeviceEvidencePath = "/sdcard/Android/data/com.quata/files/startup-splash-evidence";
const requiredEvidenceFiles = [
  "android-startup-splash-evidence.json",
  "android-startup-splash.png",
  "android-startup-launcher-evidence.json",
  "android-main-activity-startup-splash.png",
  "android-main-activity-after-startup.png",
];

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
  await run(adb, ["shell", "rm", "-rf", externalDeviceEvidencePath]).catch(() => {});

  const instrumentationOutput = [
    await runStartupSplashTest("sharedSplashRendersAndFinishesFromCommonCallback"),
    await runStartupSplashTest("mainActivityLaunchMountsSharedSplashAndDismissesIt"),
  ].join("\n--- startup-splash-test-boundary ---\n");
  report.instrumentationTail = redactedTail(instrumentationOutput);
  if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) throw new Error("android_instrumentation_not_ok");
  if (/FAILURES!!!|AssumptionViolatedException/i.test(instrumentationOutput)) {
    throw new Error("android_instrumentation_semantic_failure");
  }
  report.steps.push("android_shared_startup_splash_test_passed");

  const evidenceDir = resolve(options.evidenceDir);
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  await copyDeviceEvidence(evidenceDir);
  report.evidence.directory = evidenceDir;
  report.evidence.files = await evidenceFileHashes(evidenceDir);
  assertRequiredEvidence(report.evidence.files);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  await copyDeviceEvidence(resolve(options.evidenceDir)).catch(() => {});
} finally {
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]).catch(() => {});
  await run(adb, ["shell", "rm", "-rf", externalDeviceEvidencePath]).catch(() => {});
  await run(adb, ["uninstall", "com.quata.test"]).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Startup/Splash Android evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Startup/Splash Android evidence failed: ${report.error?.message ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Startup/Splash Android evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    output: resolve(join("build-reports", "android", "startup-splash-evidence.json")),
    evidenceDir: resolve(join("build-reports", "android", "startup-splash-evidence")),
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
  const copied = new Set();
  const externalListing = await runCapture(adb, ["shell", "ls", externalDeviceEvidencePath]).catch(() => "");
  for (const name of externalListing.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)) {
    await adbCat(`${externalDeviceEvidencePath}/${name}`, join(evidenceDir, name)).catch(() => {});
    copied.add(name);
  }
  const internalListing = await runCapture(adb, ["shell", "run-as", "com.quata", "ls", deviceEvidencePath]).catch(() => "");
  for (const name of internalListing.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)) {
    if (copied.has(name)) continue;
    await adbRunAsCat(`${deviceEvidencePath}/${name}`, join(evidenceDir, name)).catch(() => {});
  }
}

async function adbRunAsCat(devicePath, localPath) {
  const output = await runBuffer(adb, ["exec-out", "run-as", "com.quata", "cat", devicePath]);
  await writeFile(localPath, output);
}

async function adbCat(devicePath, localPath) {
  const output = await runBuffer(adb, ["exec-out", "cat", devicePath]);
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

function assertRequiredEvidence(files) {
  const missing = requiredEvidenceFiles.filter((name) => !files[name] || typeof files[name] !== "string");
  if (missing.length > 0) throw new Error(`android_evidence_artifact_missing:${missing.join(",")}`);
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

async function runStartupSplashTest(methodName) {
  await run(adb, ["shell", "am", "force-stop", "com.quata"]).catch(() => {});
  return await runCapture(adb, [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", `com.quata.core.startup.StartupSplashCommonInstrumentedTest#${methodName}`,
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
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
