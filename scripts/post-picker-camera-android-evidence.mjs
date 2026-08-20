#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const CHECK = "POST-PICKER-CAMERA-ANDROID-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const deviceCredentialsPath = "app-internal:post-picker-camera-credentials.json";
const deviceTempCredentialsPath = "/data/local/tmp/post-picker-camera-credentials.json";
const deviceTempVideoPath = "/data/local/tmp/post-picker-camera-long-video.mp4";
const deviceVideoPath = "/data/data/com.quata/files/post-picker-camera-long-video.mp4";
const deviceEvidencePath = "files/post-publish-evidence";
const DEFAULT_VIDEO_FIXTURE = "play-store/05-assets/source-media/big-buck-bunny-320x180.mp4";
const defaultSources = ["gallery-image", "camera-image", "camera-image:cancelled"];

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  attempts: [],
  evidence: {},
};

const adb = resolveAdbCommand();
let localCredentials;

try {
  const credentials = await loadCredentials(options.credentialsFile);
  localCredentials = join("build-reports", "android", `post-picker-camera-credentials-${randomUUID()}.json`);
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
  await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"]);
  report.steps = ["android_debug_and_test_apks_built"];

  await run(adb, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
  await run(adb, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  await run(adb, ["push", localCredentials, deviceTempCredentialsPath]);
  await run(adb, ["shell", "chmod", "644", deviceTempCredentialsPath]);
  await run(adb, ["shell", "run-as", "com.quata", "cp", deviceTempCredentialsPath, `files/${deviceCredentialsPath.replace("app-internal:", "")}`]);
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]);
  if (options.sources.some((entry) => sourceAndOutcome(entry).source.endsWith("video"))) {
    await run(adb, ["push", options.videoFixture, deviceTempVideoPath]);
    await run(adb, ["shell", "chmod", "644", deviceTempVideoPath]);
    await run(adb, ["shell", "run-as", "com.quata", "cp", deviceTempVideoPath, "files/post-picker-camera-long-video.mp4"]);
    await run(adb, ["shell", "rm", "-f", deviceTempVideoPath]);
    report.steps.push("android_long_video_fixture_copied_to_app_sandbox");
  }
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);

  for (const sourceAttempt of options.sources) {
    const { source, outcome } = sourceAndOutcome(sourceAttempt);
    const instrumentationOutput = await runCapture(adb, [
      "shell", "am", "instrument", "-w", "-r",
      "-e", "class", "com.quata.feature.postcomposer.presentation.PostPublishRealInstrumentedTest#authenticatedUserExercisesMediaSourceActionsFromCommonComposer",
      "-e", "quataPostPublishCredentialsFile", deviceCredentialsPath,
      "-e", "quataPostComposerPickerSource", source,
      "-e", "quataPostComposerPickerOutcome", outcome,
      "-e", "quataPostComposerPickerVideoPath", deviceVideoPath,
      "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
    ]);
    const attempt = { source, outcome, status: "failed", instrumentationTail: redactedTail(instrumentationOutput) };
    if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) {
      report.attempts.push(attempt);
      throw new Error(`android_instrumentation_not_ok:${source}:${outcome}`);
    }
    if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentationOutput)) {
      report.attempts.push(attempt);
      throw new Error(`android_instrumentation_semantic_failure:${source}:${outcome}`);
    }
    report.attempts.push({ source, outcome, status: "passed", instrumentationTail: redactedTail(instrumentationOutput) });
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
  await run(adb, ["shell", "rm", "-f", deviceTempVideoPath]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-f", `files/${deviceCredentialsPath.replace("app-internal:", "")}`]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-f", "files/post-picker-camera-long-video.mp4"]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]).catch(() => {});
  await rm(localCredentials ?? "", { force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Post picker/camera Android evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post picker/camera Android evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post picker/camera Android evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    output: resolve(join("build-reports", "android", "post-picker-camera-evidence.json")),
    evidenceDir: resolve(join("build-reports", "android", "post-picker-camera-evidence")),
    credentialsFile: process.env.QUATA_POST_PUBLISH_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE,
    videoFixture: resolve(process.env.QUATA_POST_PICKER_CAMERA_VIDEO_FIXTURE?.trim() || DEFAULT_VIDEO_FIXTURE),
    sources: defaultSources,
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--out", "--evidence-dir", "--credentials-file", "--sources", "--video-fixture"].includes(key) || !value || value.startsWith("--")) {
      throw new Error(`invalid_argument:${key}`);
    }
    index += 1;
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
    if (key === "--credentials-file") parsed.credentialsFile = value;
    if (key === "--video-fixture") parsed.videoFixture = resolve(value);
    if (key === "--sources") parsed.sources = value.split(",").map((entry) => entry.trim()).filter(Boolean);
  }
  return parsed;
}

function sourceAndOutcome(value) {
  const [source, outcome = "success"] = String(value).split(":");
  if (!["gallery-image", "camera-image", "gallery-video", "camera-video"].includes(source)) {
    throw new Error(`invalid_source:${value}`);
  }
  if (!["success", "cancelled", "failure", "unsupported"].includes(outcome)) {
    throw new Error(`invalid_outcome:${value}`);
  }
  return { source, outcome };
}

async function loadCredentials(path) {
  const credentials = JSON.parse(await readFile(path, "utf8"));
  for (const field of ["country_code", "phone", "password"]) {
    if (!credentials?.a?.[field]) throw new Error(`credentials_missing:a.${field}`);
  }
  return credentials.a;
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

function resolveAdbCommand() {
  return process.env.ADB?.trim() || "adb";
}

async function gitMetadata() {
  const head = (await runCapture("git", ["rev-parse", "HEAD"])).trim();
  const branch = (await runCapture("git", ["branch", "--show-current"])).trim();
  return { head, branch };
}

function run(command, args, options = {}) {
  return runCapture(command, args, options).then(() => undefined);
}

function runCapture(command, args, options = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32", ...options });
    let output = "";
    child.stdout.on("data", (chunk) => { output += chunk; });
    child.stderr.on("data", (chunk) => { output += chunk; });
    child.on("close", (code) => code === 0 ? resolvePromise(output) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${output.slice(-2000)}`)));
  });
}

function runBuffer(command, args, options = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32", ...options });
    const chunks = [];
    let stderr = "";
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("close", (code) => code === 0 ? resolvePromise(Buffer.concat(chunks)) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${stderr.slice(-2000)}`)));
  });
}

function safeFailure(error) {
  return {
    name: error?.name ?? "Error",
    message: typeof error?.message === "string" ? error.message.replace(/Bearer\s+[A-Za-z0-9._-]+/g, "Bearer [REDACTED]") : String(error),
  };
}

function redactedTail(value) {
  return String(value)
    .replace(/\b\d{6,}\b/g, "[digits]")
    .split(/\r?\n/)
    .slice(-80)
    .join("\n");
}
