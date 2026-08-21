#!/usr/bin/env node
import { spawn } from "node:child_process";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const CHECK = "POST-VIDEO-EDITOR-ANDROID-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const DEFAULT_VIDEO_FIXTURE = "play-store/05-assets/source-media/sample-video-vertical.mp4";
const deviceCredentialsPath = "app-internal:post-video-editor-credentials.json";
const deviceTempCredentialsPath = "/data/local/tmp/post-video-editor-credentials.json";
const deviceTempVideoPath = "/data/local/tmp/post-video-editor-fixture.mp4";
const deviceVideoPath = "/data/data/com.quata/files/post-video-editor-fixture.mp4";
const deviceEvidencePath = "files/post-publish-evidence";

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

const adb = process.env.ADB?.trim() || "adb";
let localCredentials;

try {
  const credentials = await loadCredentials(options.credentialsFile);
  localCredentials = join("build-reports", "android", `post-video-editor-credentials-${randomUUID()}.json`);
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
  report.steps.push("android_debug_and_test_apks_built");

  await run(adb, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
  await run(adb, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  await run(adb, ["push", localCredentials, deviceTempCredentialsPath]);
  await run(adb, ["shell", "chmod", "644", deviceTempCredentialsPath]);
  await run(adb, ["shell", "run-as", "com.quata", "cp", deviceTempCredentialsPath, `files/${deviceCredentialsPath.replace("app-internal:", "")}`]);
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]);
  await run(adb, ["push", options.videoFixture, deviceTempVideoPath]);
  await run(adb, ["shell", "chmod", "644", deviceTempVideoPath]);
  await run(adb, ["shell", "run-as", "com.quata", "cp", deviceTempVideoPath, "files/post-video-editor-fixture.mp4"]);
  await run(adb, ["shell", "rm", "-f", deviceTempVideoPath]);
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);

  const instrumentationOutput = await runCapture(adb, [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.feature.postcomposer.presentation.PostPublishRealInstrumentedTest#authenticatedUserExercisesPostVideoEditorFromCommonComposer",
    "-e", "quataPostPublishCredentialsFile", deviceCredentialsPath,
    "-e", "quataPostVideoEditorEvidence", "1",
    "-e", "quataPostVideoEditorFixturePath", deviceVideoPath,
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  const attempt = { source: "gallery-video", outcome: "success", instrumentationTail: redactedTail(instrumentationOutput) };
  if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) {
    report.attempts.push({ ...attempt, status: "failed" });
    throw new Error("android_instrumentation_not_ok");
  }
  if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentationOutput)) {
    report.attempts.push({ ...attempt, status: "failed" });
    throw new Error("android_instrumentation_semantic_failure");
  }
  report.attempts.push({ ...attempt, status: "passed" });

  const evidenceDir = resolve(options.evidenceDir);
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  await copyDeviceEvidence(evidenceDir);
  report.evidence.physicalExport = probeAndroidExport(join(evidenceDir, "android-post-video-editor-export.mp4"));
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
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-f", "files/post-video-editor-fixture.mp4"]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]).catch(() => {});
  await rm(localCredentials ?? "", { force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Post video editor Android evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post video editor Android evidence failed: ${report.error?.message ?? report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post video editor Android evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    output: resolve(join("build-reports", "android", "post-video-editor-evidence.json")),
    evidenceDir: resolve(join("build-reports", "android", "post-video-editor-evidence")),
    credentialsFile: process.env.QUATA_POST_PUBLISH_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE,
    videoFixture: resolve(process.env.QUATA_POST_VIDEO_EDITOR_FIXTURE?.trim() || DEFAULT_VIDEO_FIXTURE),
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--out", "--evidence-dir", "--credentials-file", "--video-fixture"].includes(key) || !value || value.startsWith("--")) {
      throw new Error(`invalid_argument:${key}`);
    }
    index += 1;
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
    if (key === "--credentials-file") parsed.credentialsFile = value;
    if (key === "--video-fixture") parsed.videoFixture = resolve(value);
  }
  return parsed;
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

function probeAndroidExport(outputPath) {
  const ffprobe = JSON.parse(execFileSync("ffprobe", [
    "-v", "error",
    "-print_format", "json",
    "-show_format",
    "-show_streams",
    outputPath,
  ], { encoding: "utf8" }));
  const videoStream = ffprobe.streams?.find((stream) => stream.codec_type === "video");
  const audioStream = ffprobe.streams?.find((stream) => stream.codec_type === "audio");
  if (!videoStream) throw new Error("android_video_editor_physical_video_stream_missing");
  if (audioStream) throw new Error("android_video_editor_physical_audio_stream_present_after_mute");
  const width = Number(videoStream.width || 0);
  const height = Number(videoStream.height || 0);
  if (!isSupportedVideoEditorProfile(width, height)) {
    throw new Error(`android_video_editor_physical_dimensions:${width}x${height}`);
  }
  const durationMs = Math.round(Number(ffprobe.format?.duration || videoStream.duration || 0) * 1000);
  if (durationMs <= 0 || durationMs > 90_500) {
    throw new Error(`android_video_editor_physical_duration:${durationMs}`);
  }
  const frameRate = parseFrameRate(videoStream.avg_frame_rate || videoStream.r_frame_rate);
  if (frameRate > 30.5) {
    throw new Error(`android_video_editor_physical_frame_rate:${frameRate}`);
  }
  return {
    path: outputPath,
    durationMs,
    video: { codec: videoStream.codec_name, width, height, frameRate },
    audioStreamPresent: Boolean(audioStream),
  };
}

function isSupportedVideoEditorProfile(width, height) {
  return [
    [720, 1280],
    [480, 854],
    [432, 768],
  ].some(([expectedWidth, expectedHeight]) => width === expectedWidth && height === expectedHeight);
}

function parseFrameRate(value) {
  const [rawNumerator, rawDenominator] = String(value || "0/1").split("/");
  const numerator = Number(rawNumerator || 0);
  const denominator = Number(rawDenominator || 1);
  return denominator ? numerator / denominator : 0;
}

async function adbRunAsCat(devicePath, localPath) {
  const output = await runBuffer(adb, ["exec-out", "run-as", "com.quata", "cat", devicePath]);
  await writeFile(localPath, output);
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

function runCapture(command, args, options = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32", ...options });
    let output = "";
    child.stdout.on("data", (chunk) => { output += chunk; });
    child.stderr.on("data", (chunk) => { output += chunk; });
    child.on("close", (code) => code === 0 ? resolvePromise(output) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${redactedTail(output)}`)));
  });
}

function runBuffer(command, args, options = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32", ...options });
    const chunks = [];
    let stderr = "";
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("close", (code) => code === 0 ? resolvePromise(Buffer.concat(chunks)) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${redactedTail(stderr)}`)));
  });
}

function safeFailure(error) {
  return {
    name: error?.name ?? "Error",
    message: redactedTail(error?.message ?? String(error)).slice(0, 500),
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
