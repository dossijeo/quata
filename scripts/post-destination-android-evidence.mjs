#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const CHECK = "POST-DESTINATION-ANDROID-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const deviceCredentialsPath = "app-internal:post-destination-credentials.json";
const deviceTempCredentialsPath = "/data/local/tmp/post-destination-credentials.json";
const deviceEvidencePath = "files/post-publish-evidence";
const defaultModes = ["multiple", "empty", "failure"];

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  attempts: [],
  evidence: {},
};

const adb = process.env.ADB?.trim() || "adb";
let localCredentials;

try {
  const credentials = (await loadCredentials(options.credentialsFile)).a;
  localCredentials = join("build-reports", "android", `post-destination-credentials-${randomUUID()}.json`);
  await mkdir(dirname(localCredentials), { recursive: true });
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: credentials.country_code,
      phone: credentials.phone,
      password: credentials.password,
    })}\n`,
    { mode: 0o600 },
  );

  const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"], gradleEnvironment());
  report.steps = ["android_debug_and_test_apks_built"];
  await run(adb, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
  await run(adb, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  await run(adb, ["push", localCredentials, deviceTempCredentialsPath]);
  await run(adb, ["shell", "chmod", "644", deviceTempCredentialsPath]);
  await run(adb, ["shell", "run-as", "com.quata", "cp", deviceTempCredentialsPath, `files/${deviceCredentialsPath.replace("app-internal:", "")}`]);
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]);
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);

  for (const mode of options.modes) {
    const output = await runCapture(adb, [
      "shell", "am", "instrument", "-w", "-r",
      "-e", "class", "com.quata.feature.postcomposer.presentation.PostPublishRealInstrumentedTest#authenticatedUserExercisesDestinationStatesFromCommonComposer",
      "-e", "quataPostPublishCredentialsFile", deviceCredentialsPath,
      "-e", "quataPostDestinationEvidenceMode", mode,
      "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
    ]);
    const attempt = { mode, status: "failed", instrumentationTail: redactedTail(output) };
    if (!/OK \(\d+ tests?\)/.test(output)) throw new Error(`android_instrumentation_not_ok:${mode}`);
    if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(output)) throw new Error(`android_instrumentation_semantic_failure:${mode}`);
    report.attempts.push({ ...attempt, status: "passed" });
  }

  const evidenceDir = resolve(options.evidenceDir);
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  await copyDeviceEvidence(evidenceDir);
  report.evidence.directory = evidenceDir;
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  await copyDeviceEvidence(resolve(options.evidenceDir)).catch(() => {});
} finally {
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-f", `files/${deviceCredentialsPath.replace("app-internal:", "")}`]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]).catch(() => {});
  await rm(localCredentials ?? "", { force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Post destination Android evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post destination Android evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post destination Android evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    output: resolve(join("build-reports", "android", "post-destination-evidence.json")),
    evidenceDir: resolve(join("build-reports", "android", "post-destination-evidence")),
    credentialsFile: process.env.QUATA_POST_PUBLISH_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE,
    modes: defaultModes,
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--out", "--evidence-dir", "--credentials-file", "--modes"].includes(key) || !value || value.startsWith("--")) {
      throw new Error(`invalid_argument:${key}`);
    }
    index += 1;
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
    if (key === "--credentials-file") parsed.credentialsFile = value;
    if (key === "--modes") parsed.modes = value.split(",").map((entry) => entry.trim()).filter(Boolean);
  }
  if (parsed.modes.some((mode) => !defaultModes.includes(mode))) throw new Error("invalid_destination_mode");
  return parsed;
}

async function loadCredentials(path) {
  const credentials = JSON.parse(await readFile(path, "utf8"));
  for (const profile of ["a"]) {
    for (const field of ["country_code", "phone", "password"]) {
      if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
    }
  }
  return credentials;
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

function gradleEnvironment() {
  return { ...process.env, JAVA_HOME: process.env.JAVA_HOME || "C:/Program Files/Android/Android Studio/jbr" };
}

async function gitMetadata() {
  const head = (await runCapture("git", ["rev-parse", "HEAD"])).trim();
  const branch = (await runCapture("git", ["branch", "--show-current"])).trim();
  return { head, branch };
}

function run(command, args, env = process.env) {
  return runCapture(command, args, { env }).then(() => undefined);
}

function runCapture(command, args, { env = process.env } = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { env, stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32" });
    let output = "";
    child.stdout.on("data", (chunk) => { output += chunk; });
    child.stderr.on("data", (chunk) => { output += chunk; });
    child.on("close", (code) => code === 0 ? resolvePromise(output) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${output.slice(-2000)}`)));
  });
}

function runBuffer(command, args) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32" });
    const chunks = [];
    let stderr = "";
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("close", (code) => code === 0 ? resolvePromise(Buffer.concat(chunks)) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${stderr.slice(-2000)}`)));
  });
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

function redactedTail(value) {
  return String(value)
    .replace(/\b\d{6,}\b/g, "[digits]")
    .split(/\r?\n/)
    .slice(-80)
    .join("\n");
}
