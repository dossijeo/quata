#!/usr/bin/env node
import { spawn, spawnSync } from "node:child_process";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { access, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const CHECK = "POST-VIDEO-EDITOR-ANDROID-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const CAPTION_FIXTURE_TEXT = "quata video editor captions are real";
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
  await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", ":vosk_model_en:assembleDebug", "--console=plain"]);
  report.steps.push("android_debug_test_and_vosk_model_en_apks_built");

  const appApk = "app/build/outputs/apk/debug/app-debug.apk";
  const voskModelEnApk = "vosk_model_en/build/outputs/apk/debug/vosk_model_en-debug.apk";
  const testApk = "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk";
  await assertFileExists(appApk);
  await assertFileExists(voskModelEnApk);
  await assertFileExists(testApk);

  await run(adb, ["install-multiple", "-r", appApk, voskModelEnApk]);
  await run(adb, ["install", "-r", "-t", testApk]);
  await run(adb, ["push", localCredentials, deviceTempCredentialsPath]);
  await run(adb, ["shell", "chmod", "644", deviceTempCredentialsPath]);
  await run(adb, ["shell", "run-as", "com.quata", "cp", deviceTempCredentialsPath, `files/${deviceCredentialsPath.replace("app-internal:", "")}`]);
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]);
  const videoFixture = options.videoFixture ?? await validSpeechMp4FixturePath();
  await run(adb, ["push", videoFixture, deviceTempVideoPath]);
  await run(adb, ["shell", "chmod", "644", deviceTempVideoPath]);
  await run(adb, ["shell", "run-as", "com.quata", "cp", deviceTempVideoPath, "files/post-video-editor-fixture.mp4"]);
  await run(adb, ["shell", "rm", "-f", deviceTempVideoPath]);
  const evidenceDir = resolve(options.evidenceDir);
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  for (const attemptSpec of [
    { label: "muted", mute: true },
    { label: "unmuted", mute: false },
  ]) {
    await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);
    const instrumentationOutput = await runCapture(adb, [
      "shell", "am", "instrument", "-w", "-r",
      "-e", "class", "com.quata.feature.postcomposer.presentation.PostPublishRealInstrumentedTest#authenticatedUserExercisesPostVideoEditorFromCommonComposer",
      "-e", "quataPostPublishCredentialsFile", deviceCredentialsPath,
      "-e", "quataPostVideoEditorEvidence", "1",
      "-e", "quataPostVideoEditorFixturePath", deviceVideoPath,
      "-e", "quataPostVideoEditorMute", attemptSpec.mute ? "1" : "0",
      "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
    ]);
    const attempt = {
      source: "gallery-video",
      outcome: "success",
      label: attemptSpec.label,
      mute: attemptSpec.mute,
      instrumentationTail: redactedTail(instrumentationOutput),
    };
    if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) {
      report.attempts.push({ ...attempt, status: "failed" });
      throw new Error(`android_instrumentation_not_ok:${attemptSpec.label}`);
    }
    if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentationOutput)) {
      report.attempts.push({ ...attempt, status: "failed" });
      throw new Error(`android_instrumentation_semantic_failure:${attemptSpec.label}`);
    }
    await copyDeviceEvidence(evidenceDir);
    const exportPath = join(evidenceDir, `android-post-video-editor-export-${attemptSpec.label}.mp4`);
    try {
      const physicalExport = probeAndroidExport(exportPath, videoFixture, { mute: attemptSpec.mute });
      report.attempts.push({ ...attempt, status: "passed", physicalExport });
    } catch (probeError) {
      report.attempts.push({ ...attempt, status: "failed", probeError: safeFailure(probeError) });
      throw probeError;
    }
  }
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
    videoFixture: process.env.QUATA_POST_VIDEO_EDITOR_FIXTURE?.trim()
      ? resolve(process.env.QUATA_POST_VIDEO_EDITOR_FIXTURE.trim())
      : null,
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

async function validSpeechMp4FixturePath() {
  const fixtureDir = resolve(dirname(options.evidenceDir), "post-video-editor-fixtures");
  await mkdir(fixtureDir, { recursive: true });
  const wavPath = resolve(fixtureDir, "android-post-video-editor-caption-source.wav");
  const videoPath = resolve(fixtureDir, "android-post-video-editor-caption-source.mp4");
  const speechScript = [
    "$ErrorActionPreference = 'Stop'",
    "Add-Type -AssemblyName System.Speech",
    "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer",
    "try { $synth.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::NotSet, [System.Speech.Synthesis.VoiceAge]::NotSet, 0, [System.Globalization.CultureInfo]::GetCultureInfo('en-US')) } catch {}",
    `$synth.SetOutputToWaveFile(${powershellQuote(wavPath)})`,
    `$synth.Speak(${powershellQuote(CAPTION_FIXTURE_TEXT)})`,
    "$synth.Dispose()",
  ].join("; ");
  execFileSync("powershell", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", speechScript], { stdio: "pipe" });
  execFileSync("ffmpeg", [
    "-y",
    "-f", "lavfi",
    "-i", "testsrc2=s=720x1280:r=30:d=6",
    "-i", wavPath,
    "-shortest",
    "-c:v", "libx264",
    "-pix_fmt", "yuv420p",
    "-c:a", "aac",
    "-b:a", "128k",
    "-movflags", "+faststart",
    videoPath,
  ], { stdio: "pipe" });
  const fixture = await readFile(videoPath);
  if (fixture.length < 8_000 || fixture.subarray(4, 8).toString("ascii") !== "ftyp") {
    throw new Error("invalid_speech_mp4_fixture");
  }
  report.evidence.captionFixture = {
    source: "generated_windows_sapi_en_us_fixture",
    text: CAPTION_FIXTURE_TEXT,
    path: videoPath,
    sizeBytes: fixture.length,
  };
  return videoPath;
}

function powershellQuote(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

async function loadCredentials(path) {
  const credentials = JSON.parse(await readFile(path, "utf8"));
  for (const field of ["country_code", "phone", "password"]) {
    if (!credentials?.a?.[field]) throw new Error(`credentials_missing:a.${field}`);
  }
  return credentials.a;
}

async function assertFileExists(path) {
  try {
    await access(path);
  } catch {
    throw new Error(`required_file_missing:${path}`);
  }
}

async function copyDeviceEvidence(evidenceDir) {
  await mkdir(evidenceDir, { recursive: true });
  const listing = await runCapture(adb, ["shell", "run-as", "com.quata", "ls", deviceEvidencePath]).catch(() => "");
  for (const name of listing.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)) {
    await adbRunAsCat(`${deviceEvidencePath}/${name}`, join(evidenceDir, name)).catch(() => {});
  }
}

function probeAndroidExport(outputPath, sourcePath, { mute }) {
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
  if (mute && audioStream) throw new Error("android_video_editor_physical_audio_stream_present_after_mute");
  if (!mute && !audioStream) throw new Error("android_video_editor_physical_audio_stream_missing_without_mute");
  const width = Number(videoStream.width || 0);
  const height = Number(videoStream.height || 0);
  if (!isSupportedVideoEditorProfile(width, height)) {
    throw new Error(`android_video_editor_physical_dimensions:${width}x${height}`);
  }
  const durationMs = Math.round(Number(ffprobe.format?.duration || videoStream.duration || 0) * 1000);
  if (durationMs <= 0 || durationMs > 90_500) {
    throw new Error(`android_video_editor_physical_duration:${durationMs}`);
  }
  const sourceDurationMs = probeMediaDurationMs(sourcePath);
  if (sourceDurationMs > 0 && durationMs >= sourceDurationMs * 0.85) {
    throw new Error(`android_video_editor_physical_trim_not_applied:${durationMs}:${sourceDurationMs}`);
  }
  const frameRate = parseFrameRate(videoStream.avg_frame_rate || videoStream.r_frame_rate);
  if (frameRate > 30.5) {
    throw new Error(`android_video_editor_physical_frame_rate:${frameRate}`);
  }
  const captionPixelProbe = probeCaptionPixels(outputPath);
  const backgroundBlurPixelProbe = probeBackgroundBlurPixels(outputPath);
  const audioProbe = !mute ? probeAudioSignal(outputPath, "android_video_editor_physical_audio_silent") : null;
  return {
    path: outputPath,
    durationMs,
    sourceDurationMs,
    video: { codec: videoStream.codec_name, width, height, frameRate },
    audioStreamPresent: Boolean(audioStream),
    audioProbe,
    captionPixelProbe,
    backgroundBlurPixelProbe,
  };
}

function probeAudioSignal(outputPath, errorCode) {
  const result = spawnSync("ffmpeg", [
    "-hide_banner",
    "-nostats",
    "-i", outputPath,
    "-af", "volumedetect",
    "-vn",
    "-sn",
    "-dn",
    "-f", "null",
    "NUL",
  ], { encoding: "utf8" });
  const output = `${result.stdout || ""}\n${result.stderr || ""}`;
  if (result.status !== 0) throw new Error(`${errorCode}:ffmpeg_${result.status}`);
  const mean = Number(/mean_volume:\s*(-?\d+(?:\.\d+)?) dB/.exec(output)?.[1] ?? NaN);
  const max = Number(/max_volume:\s*(-?\d+(?:\.\d+)?) dB/.exec(output)?.[1] ?? NaN);
  if (!Number.isFinite(mean) || mean < -55) throw new Error(`${errorCode}:${mean}`);
  return { meanVolumeDb: mean, maxVolumeDb: max };
}

function probeMediaDurationMs(path) {
  const ffprobe = JSON.parse(execFileSync("ffprobe", [
    "-v", "error",
    "-print_format", "json",
    "-show_format",
    path,
  ], { encoding: "utf8" }));
  return Math.round(Number(ffprobe.format?.duration || 0) * 1000);
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

function probeCaptionPixels(outputPath) {
  const width = 180;
  const height = 80;
  const pixels = execFileSync("ffmpeg", [
    "-v", "error",
    "-i", outputPath,
    "-vf", `crop=iw*0.84:ih*0.12:iw*0.08:ih*0.70,scale=${width}:${height}`,
    "-frames:v", "1",
    "-f", "rawvideo",
    "-pix_fmt", "rgba",
    "pipe:1",
  ]);
  if (pixels.length !== width * height * 4) {
    throw new Error(`android_video_editor_caption_pixel_probe_unexpected_size:${pixels.length}`);
  }
  let bright = 0;
  let dark = 0;
  for (let offset = 0; offset < pixels.length; offset += 4) {
    const luminance = 0.2126 * pixels[offset] + 0.7152 * pixels[offset + 1] + 0.0722 * pixels[offset + 2];
    if (luminance > 230) bright += 1;
    if (luminance < 50) dark += 1;
  }
  const total = width * height;
  const brightFraction = bright / total;
  const darkFraction = dark / total;
  if (brightFraction < 0.004 || darkFraction < 0.12) {
    throw new Error(`android_video_editor_caption_pixels_missing:${brightFraction.toFixed(4)}:${darkFraction.toFixed(4)}`);
  }
  return { width, height, brightFraction, darkFraction };
}

function probeBackgroundBlurPixels(outputPath) {
  const width = 180;
  const height = 80;
  const sample = (crop) => execFileSync("ffmpeg", [
    "-v", "error",
    "-i", outputPath,
    "-vf", `${crop},scale=${width}:${height}`,
    "-frames:v", "1",
    "-f", "rawvideo",
    "-pix_fmt", "rgba",
    "pipe:1",
  ]);
  const leftBackground = sample("crop=iw*0.18:ih*0.30:iw*0.03:ih*0.35");
  const rightBackground = sample("crop=iw*0.18:ih*0.30:iw*0.79:ih*0.35");
  const centerForeground = sample("crop=iw*0.34:ih*0.30:iw*0.33:ih*0.35");
  const backgroundSharpness = (
    averageAdjacentLumaDelta(leftBackground, width, height) +
    averageAdjacentLumaDelta(rightBackground, width, height)
  ) / 2;
  const foregroundSharpness = averageAdjacentLumaDelta(centerForeground, width, height);
  if (!(backgroundSharpness < foregroundSharpness * 0.82)) {
    throw new Error(`android_video_editor_background_blur_pixels_missing:${backgroundSharpness.toFixed(2)}:${foregroundSharpness.toFixed(2)}`);
  }
  return { width, height, backgroundSharpness, foregroundSharpness };
}

function averageAdjacentLumaDelta(pixels, width, height) {
  if (pixels.length !== width * height * 4) throw new Error(`video_editor_blur_probe_unexpected_size:${pixels.length}`);
  let total = 0;
  let count = 0;
  const luma = (offset) => 0.2126 * pixels[offset] + 0.7152 * pixels[offset + 1] + 0.0722 * pixels[offset + 2];
  for (let y = 0; y < height; y += 1) {
    for (let x = 1; x < width; x += 1) {
      const offset = (y * width + x) * 4;
      total += Math.abs(luma(offset) - luma(offset - 4));
      count += 1;
    }
  }
  return total / Math.max(1, count);
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
