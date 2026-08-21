#!/usr/bin/env node
import { execFileSync, spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";

const CHECK = "POST-VIDEO-EDITOR-IOS-REAL-001";
const PICKER_OPT_IN = "I_ACCEPT_IOS_POST_COMPOSER_PICKER_FIXTURE";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const CAPTION_FIXTURE_TEXT = "quata video editor captions are real";
const EXPECTED_CAPTION_STYLE = "Hormozi";

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
  remoteCredentials = (await runCapture("ssh", [options.host, "mktemp -t quata-ios-post-picker-credentials"])).trim();
  await run("scp", [localCredentials, `${options.host}:${remoteCredentials}`]);
  report.steps.push("ios_real_credentials_copied_to_mac_tempfile_without_logging_contents");

  localFixture = options.videoFixture ?? await validSpeechMp4FixturePath();
  remoteFixture = (await runCapture("ssh", [
    options.host,
    "tmp=$(mktemp -t quata-ios-post-picker-fixture) && mv \"$tmp\" \"$tmp.mp4\" && printf '%s\\n' \"$tmp.mp4\"",
  ])).trim();
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

  report.attempts.push(await runAttempt({ label: "muted", mute: true }));
  report.attempts.push(await runAttempt({ label: "unmuted", mute: false }));
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
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Post video editor iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post video editor iOS evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post video editor iOS evidence passed.");
}

async function runAttempt({ label, mute }) {
  const source = "gallery";
  const outcome = "success";
  const remoteLogDir = `${options.remoteLogDir}/${label}`;
  const remoteDiagnosticsDir = remoteLogDir.startsWith("/") ? remoteLogDir : `${options.project}/${remoteLogDir}`;
  const remoteDiagnostics = `${remoteDiagnosticsDir}/export-diagnostics.json`;
  try {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
mkdir -p ${shellQuote(remoteLogDir)}
rm -f ${shellQuote(remoteDiagnostics)}
rm -f ${shellQuote(`${remoteDiagnostics}.events.jsonl`)}
export QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
export QUATA_IOS_POST_VIDEO_EDITOR_UI_LOG_DIR=${shellQuote(remoteLogDir)}
export QUATA_IOS_POST_VIDEO_EDITOR_UI_RESULT_BUNDLE_DIR=${shellQuote(`${remoteLogDir}/xcresults`)}
export QUATA_IOS_POST_VIDEO_EDITOR_EXPORT_DIAGNOSTICS=${shellQuote(remoteDiagnostics)}
export QUATA_IOS_POST_VIDEO_EDITOR_TRANSCRIPTION_LOCALE='en_US'
export QUATA_IOS_POST_VIDEO_EDITOR_MUTE=${mute ? "'1'" : "'0'"}
export QUATA_IOS_POST_COMPOSER_PICKER_FIXTURE_OPT_IN=${shellQuote(PICKER_OPT_IN)}
export QUATA_IOS_POST_COMPOSER_PICKER_SOURCE=${shellQuote(source)}
export QUATA_IOS_POST_COMPOSER_PICKER_OUTCOME=${shellQuote(outcome)}
export QUATA_IOS_POST_COMPOSER_PICKER_PATH=${shellQuote(remoteFixture)}
export QUATA_IOS_POST_COMPOSER_PICKER_NAME='POST-VIDEO-EDITOR-fixture.mp4'
export QUATA_IOS_POST_COMPOSER_PICKER_MIME='video/mp4'
bash scripts/run-ios-post-video-editor-ui-test.sh
`);
    const diagnosticsText = await runSshScript(options.host, `
set -euo pipefail
cat ${shellQuote(remoteDiagnostics)}
`);
    const diagnostics = JSON.parse(diagnosticsText);
    const eventText = await runSshScript(options.host, `
set -euo pipefail
cat ${shellQuote(`${remoteDiagnostics}.events.jsonl`)}
`);
    const events = parseEvidenceEvents(eventText);
    assertIosExportDiagnostics(diagnostics, events, { mute });
    await mkdir(options.evidenceDir, { recursive: true });
    const localExport = resolve(options.evidenceDir, `ios-post-video-editor-export-${label}.mp4`);
    await run("scp", [`${options.host}:${diagnostics.outputPath}`, localExport]);
    await run("ssh", [options.host, "rm", "-f", diagnostics.outputPath]).catch(() => {});
    const physicalExport = probeIosExport(localExport, diagnostics, { mute });
    return { source, outcome, label, mute, status: "passed", remoteLogDir, diagnostics, events: events.slice(-30), physicalExport };
  } catch (error) {
    return { source, outcome, label, mute, status: "failed", remoteLogDir, error: safeFailure(error) };
  }
}

function assertIosExportDiagnostics(diagnostics, events, { mute }) {
  if (!diagnostics || typeof diagnostics !== "object") throw new Error("ios_video_editor_export_diagnostics_missing");
  if (Number(diagnostics.sizeBytes || 0) <= 0) throw new Error("ios_video_editor_export_empty_output");
  if (!isSupportedVideoEditorProfile(Number(diagnostics.outputWidth || 0), Number(diagnostics.outputHeight || 0))) {
    throw new Error(`ios_video_editor_export_unexpected_dimensions:${diagnostics.outputWidth}x${diagnostics.outputHeight}`);
  }
  if (Boolean(diagnostics.removeAudio) !== mute) {
    throw new Error(`ios_video_editor_export_mute_state:${diagnostics.removeAudio}:${mute}`);
  }
  if (diagnostics.physicalBackgroundBlur !== true) throw new Error("ios_video_editor_background_blur_not_exported");
  if (String(diagnostics.captionStyle || "") !== EXPECTED_CAPTION_STYLE) {
    throw new Error(`ios_video_editor_caption_style_not_selected:${diagnostics.captionStyle || ""}`);
  }
  const selectedEvent = events.find((event) =>
    event?.event === "caption_style_change" &&
    String(event.style || "") === EXPECTED_CAPTION_STYLE
  );
  if (!selectedEvent) throw new Error(`ios_video_editor_caption_style_change_event_missing:${EXPECTED_CAPTION_STYLE}`);
  const text = String(diagnostics.captionText || "").trim().toLowerCase();
  if (!text.includes("quata") && !text.includes("video") && !text.includes("captions")) {
    throw new Error(`ios_video_editor_caption_unexpected_transcript:${text.slice(0, 80)}`);
  }
  if (!String(diagnostics.captionDocumentWire || "").includes("\t")) {
    throw new Error("ios_video_editor_caption_document_missing_timings");
  }
  if (Number(diagnostics.captionSegmentCount || 0) <= 0 || Number(diagnostics.captionWordCount || 0) <= 0) {
    throw new Error("ios_video_editor_caption_document_empty");
  }
}

function parseEvidenceEvents(value) {
  return String(value || "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      try {
        return JSON.parse(line);
      } catch {
        return { event: "malformed_event", raw: line.slice(0, 120) };
      }
    });
}

function probeIosExport(outputPath, diagnostics, { mute }) {
  const ffprobe = JSON.parse(execFileSync("ffprobe", [
    "-v", "error",
    "-print_format", "json",
    "-show_format",
    "-show_streams",
    outputPath,
  ], { encoding: "utf8" }));
  const videoStream = ffprobe.streams?.find((stream) => stream.codec_type === "video");
  const audioStream = ffprobe.streams?.find((stream) => stream.codec_type === "audio");
  if (!videoStream) throw new Error("ios_video_editor_physical_video_stream_missing");
  if (mute && audioStream) throw new Error("ios_video_editor_physical_audio_stream_present_after_mute");
  if (!mute && !audioStream) throw new Error("ios_video_editor_physical_audio_stream_missing_without_mute");
  const width = Number(videoStream.width || 0);
  const height = Number(videoStream.height || 0);
  if (width !== Number(diagnostics.outputWidth || 0) || height !== Number(diagnostics.outputHeight || 0)) {
    throw new Error(`ios_video_editor_physical_dimensions:${width}x${height}`);
  }
  const durationMs = Math.round(Number(ffprobe.format?.duration || videoStream.duration || 0) * 1000);
  const expectedDurationMs = Math.max(500, Number(diagnostics.trimEndMs || 0) - Number(diagnostics.trimStartMs || 0));
  if (durationMs <= 0) {
    throw new Error("ios_video_editor_physical_duration_unmeasured");
  }
  if (durationMs < expectedDurationMs * 0.8 || durationMs > expectedDurationMs * 1.35) {
    throw new Error(`ios_video_editor_physical_trim_duration:${durationMs}:${expectedDurationMs}`);
  }
  const physicalBitrate = Number(ffprobe.format?.bit_rate || videoStream.bit_rate || 0);
  const targetBitrate = Number(diagnostics.outputTargetBitrate || 0);
  if (targetBitrate > 0 && physicalBitrate > targetBitrate * 2.25) {
    throw new Error(`ios_video_editor_physical_bitrate:${physicalBitrate}:${targetBitrate}`);
  }
  const captionPixelProbe = probeCaptionPixels(outputPath, firstCaptionProbeSecond(diagnostics.captionDocumentWire));
  const backgroundBlurPixelProbe = probeBackgroundBlurPixels(outputPath);
  return {
    path: outputPath,
    video: { codec: videoStream.codec_name, width, height, bitRate: physicalBitrate },
    durationMs,
    expectedTrimDurationMs: expectedDurationMs,
    targetBitrate,
    audioStreamPresent: Boolean(audioStream),
    captionPixelProbe,
    backgroundBlurPixelProbe,
  };
}

function isSupportedVideoEditorProfile(width, height) {
  return [
    [720, 1280],
    [480, 854],
    [432, 768],
  ].some(([expectedWidth, expectedHeight]) => width === expectedWidth && height === expectedHeight);
}

function firstCaptionProbeSecond(captionDocumentWire) {
  const firstWord = String(captionDocumentWire || "")
    .split(/\r?\n/)
    .map((line) => line.split("\t"))
    .find((parts) => parts.length >= 3 && Number.isFinite(Number(parts[1])));
  const startMs = firstWord ? Number(firstWord[1]) : 500;
  return Math.max(0, (startMs + 250) / 1000);
}

function probeCaptionPixels(outputPath, seekSecond) {
  const width = 180;
  const height = 80;
  const pixels = execFileSync("ffmpeg", [
    "-v", "error",
    "-ss", String(seekSecond),
    "-i", outputPath,
    "-vf", `crop=iw*0.84:ih*0.12:iw*0.08:ih*0.70,scale=${width}:${height}`,
    "-frames:v", "1",
    "-f", "rawvideo",
    "-pix_fmt", "rgba",
    "pipe:1",
  ]);
  if (pixels.length !== width * height * 4) {
    throw new Error(`ios_video_editor_caption_pixel_probe_unexpected_size:${pixels.length}`);
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
    throw new Error(`ios_video_editor_caption_pixels_missing:${brightFraction.toFixed(4)}:${darkFraction.toFixed(4)}`);
  }
  return { width, height, seekSecond, brightFraction, darkFraction };
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
  const topBackground = sample("crop=iw*0.86:ih*0.16:iw*0.07:ih*0.06");
  const centerForeground = sample("crop=iw*0.86:ih*0.16:iw*0.07:ih*0.42");
  const backgroundSharpness = averageAdjacentLumaDelta(topBackground, width, height);
  const foregroundSharpness = averageAdjacentLumaDelta(centerForeground, width, height);
  if (!(backgroundSharpness < foregroundSharpness * 0.82)) {
    throw new Error(`ios_video_editor_background_blur_pixels_missing:${backgroundSharpness.toFixed(2)}:${foregroundSharpness.toFixed(2)}`);
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

function parseArgs(args) {
  const parsed = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    remoteLogDir: process.env.QUATA_IOS_POST_VIDEO_EDITOR_UI_LOG_DIR?.trim() || "build/reports/ios/POST-VIDEO-EDITOR-ui",
    output: join("build-reports", "ios", "POST-VIDEO-EDITOR-evidence.json"),
    evidenceDir: join("build-reports", "ios", "POST-VIDEO-EDITOR-evidence"),
    videoFixture: process.env.QUATA_POST_VIDEO_EDITOR_FIXTURE?.trim()
      ? resolve(process.env.QUATA_POST_VIDEO_EDITOR_FIXTURE.trim())
      : null,
    simulatorUdid: process.env.QUATA_IOS_SIMULATOR_UDID?.trim() || "",
    buildFirst: process.env.QUATA_IOS_BUILD_FIRST === "1",
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (["--host", "--project", "--derived-data", "--remote-log-dir", "--out", "--evidence-dir", "--simulator", "--video-fixture"].includes(key)) {
      if (!value || value.startsWith("--")) throw new Error(`missing_value:${key}`);
      index += 1;
      if (key === "--host") parsed.host = value;
      if (key === "--project") parsed.project = value;
      if (key === "--derived-data") parsed.derivedDataPath = value;
      if (key === "--remote-log-dir") parsed.remoteLogDir = value;
      if (key === "--out") parsed.output = value;
      if (key === "--evidence-dir") parsed.evidenceDir = value;
      if (key === "--simulator") parsed.simulatorUdid = value;
      if (key === "--video-fixture") parsed.videoFixture = resolve(value);
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

async function validSpeechMp4FixturePath() {
  await mkdir(options.evidenceDir, { recursive: true });
  const wavPath = resolve(options.evidenceDir, "ios-post-video-editor-caption-source.wav");
  const videoPath = resolve(options.evidenceDir, "ios-post-video-editor-caption-source.mp4");
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
  report.evidence.captionFixture = {
    source: "generated_windows_sapi_en_us_fixture",
    text: CAPTION_FIXTURE_TEXT,
    path: videoPath,
  };
  return videoPath;
}

function powershellQuote(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_POST_VIDEO_EDITOR_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
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
  const remoteEvidenceDir = resolve(evidenceDir, "remote-logs");
  await rm(remoteEvidenceDir, { recursive: true, force: true });
  await mkdir(remoteEvidenceDir, { recursive: true });
  const source = remoteLogDir.startsWith("/") ? remoteLogDir : `${project}/${remoteLogDir}`;
  await run("scp", ["-r", `${host}:${source}/.`, remoteEvidenceDir]);
  report.evidence.directory = resolve(evidenceDir);
  report.evidence.remoteLogsDirectory = remoteEvidenceDir;
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
