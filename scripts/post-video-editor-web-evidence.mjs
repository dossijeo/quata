#!/usr/bin/env node
import { createServer } from "node:http";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { extname, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";

const CHECK = "POST-VIDEO-EDITOR-WEB-REAL-001";
const PICKER_OPT_IN = "I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const CAPTION_FIXTURE_TEXT = "quata video editor captions are real";
const { chromium } = loadPlaywrightCore();

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: gitMetadata(),
  attempts: [],
  evidence: {},
  steps: [],
};

let server;
let browser;

try {
  const backend = await publicConfig();
  const credentials = await loadCredentials();
  server = await startServer(options.distribution, await wordpressBaseUrl(), backend);
  const session = await login(backend, credentials.a, `POST-VIDEO-EDITOR-web-${randomUUID()}`);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  const context = await browser.newContext({ locale: "en-US", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
  await context.addInitScript((state) => {
    localStorage.setItem("quata_web_access_token", state.accessToken);
    localStorage.setItem("quata_web_refresh_token", state.refreshToken);
    localStorage.setItem("quata_web_session_token", state.webSessionToken);
    localStorage.setItem("quata_web_user_id", state.userId);
    localStorage.setItem("quata_web_expires_at", String(state.expiresAt));
    if (state.displayName) localStorage.setItem("quata_web_display_name", state.displayName);
    localStorage.setItem("web.auth.session_ready", "true");
    localStorage.setItem("quata_web_client_instance_id", state.clientInstanceId);
  }, session);
  report.steps.push("real_profile_authenticated_without_logging_credentials");

  report.attempts.push(await runAttempt(context));
  const failedAttempt = report.attempts.find((attempt) => attempt.status !== "passed");
  if (failedAttempt) throw new Error(`web_attempt_failed:${failedAttempt.source}:${failedAttempt.outcome}`);

  report.evidence.directory = resolve(options.evidenceDir);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = String(error?.message ?? error).slice(0, 500);
} finally {
  await browser?.close().catch(() => {});
  await server?.close?.().catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(resolve(options.output, ".."), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Post video editor Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post video editor Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post video editor Web evidence passed.");
}

async function runAttempt(context) {
  const source = "gallery-video";
  const outcome = "success";
  const page = await context.newPage();
  const faults = [];
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message ?? error).slice(0, 160)}`));
  page.on("console", (entry) => {
    if (entry.type() === "error") faults.push(`console_error:${entry.text().slice(0, 180)}`);
  });
  try {
    const reference = await validSpeechMp4FixtureDataUrl();
    await page.addInitScript(({ source, outcome, reference, pickerOptIn }) => {
      sessionStorage.setItem("quata.post_publish.e2e", "1");
      localStorage.setItem("quata_post_composer_picker_e2e_opt_in", pickerOptIn);
      localStorage.setItem("quata_post_composer_picker_e2e_source", source);
      localStorage.setItem("quata_post_composer_picker_e2e_outcome", outcome);
      localStorage.setItem("quata_post_composer_picker_e2e_reference", reference);
    }, { source, outcome, reference, pickerOptIn: PICKER_OPT_IN });
    await page.goto(`${server.origin}/?quata-post-publish-e2e=1&quata-post-picker-camera-e2e=1&quata-post-video-editor-e2e=1#composer`, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await page.evaluate(({ source, outcome, reference, pickerOptIn }) => {
      localStorage.setItem("quata_post_composer_picker_e2e_opt_in", pickerOptIn);
      localStorage.setItem("quata_post_composer_picker_e2e_source", source);
      localStorage.setItem("quata_post_composer_picker_e2e_outcome", outcome);
      localStorage.setItem("quata_post_composer_picker_e2e_reference", reference);
    }, { source, outcome, reference, pickerOptIn: PICKER_OPT_IN });
    await page.locator("#create-post-common-root").first().waitFor({ state: "attached", timeout: 45_000 });
    await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-post-composer-e2e") === "ready", null, { timeout: 20_000 });
    const opened = await screenshot(page, "web-post-video-editor-opened");
    const resolvedTypeAnchor = await clickComposerType(page, "video");
    const resolvedActionAnchor = await clickComposerMediaAction(page, "composer-media.pick-video");
    const selectedByBridge = await ensureWebVideoSelected(page, reference, "composer-media.pick-video");
    await delay(500);
    const afterSelect = await screenshot(page, "web-post-video-editor-video-selected");
    await page.waitForFunction((expected) => {
      const state = globalThis.__quataPostComposerE2eProduct?.state?.();
      return state?.hasVideo === true
        && typeof state.videoUri === "string"
        && (state.videoUri === expected || state.videoUri.startsWith("data:video/") || state.videoUri.startsWith("blob:"));
    }, reference, { timeout: 10_000 });
    const resolvedEditAnchor = await clickComposerEditAction(page);
    const resolvedEditorOpen = await ensureWebVideoEditorOpen(page, resolvedEditAnchor, reference);
    const editorAnchors = await exerciseVideoEditor(page);
    const exported = await page.waitForFunction(() => {
      const state = globalThis.__quataPostComposerE2eProduct?.state?.();
      return state?.hasVideo === true && typeof state?.videoUri === "string" && state.videoUri.startsWith("blob:");
    }, null, { timeout: 15_000 }).then(() => postComposerProductState(page));
    const afterEdit = await screenshot(page, "web-post-video-editor-after-edit");
    const physicalOutput = await saveAndProbeWebExport(page, editorAnchors.exportState);
    const actionableFaults = faults.filter((fault) => !/Failed to load resource: the server responded with a status of 404/.test(fault));
    if (actionableFaults.length) throw new Error(`browser_runtime_fault:${actionableFaults[0]}`);
    return {
      source,
      outcome,
      status: "passed",
      selectedField: "hasVideo",
      anchors: { type: resolvedTypeAnchor, action: selectedByBridge ?? resolvedActionAnchor, edit: resolvedEditorOpen, editor: editorAnchors },
      evidence: { opened, afterSelect, afterEdit, physicalOutput },
      state: exported,
    };
  } catch (error) {
    return {
      source,
      outcome,
      status: "failed",
      error: safeFailure(error),
      errorDetail: String(error?.stack ?? error?.message ?? error).slice(0, 1_500),
      state: await postComposerProductState(page).catch(() => null),
      candidates: await semanticCandidates(page).catch(() => []),
    };
  } finally {
    await page.close().catch(() => {});
  }
}

async function ensureWebVideoEditorOpen(page, visualAnchor, reference) {
  if (await semanticLocator(page, "post-video-editor.root").then((locator) => locator.waitFor({ state: "attached", timeout: 1_500 }).then(() => true)).catch(() => false)) {
    return visualAnchor;
  }
  const invoked = await page.evaluate(() => {
    const bridge = globalThis.__quataPostComposerE2eProduct;
    if (typeof bridge?.editVideo !== "function") return false;
    bridge.editVideo();
    return true;
  });
  if (invoked && await waitForVideoEditorReady(page).then(() => true).catch(() => false)) {
    return { kind: "localhostProductBridge", value: "editVideo", preferredMissing: "composer-media.edit-video" };
  }
  const openedWithReference = await page.evaluate((value) => {
    const bridge = globalThis.__quataPostComposerE2eProduct;
    if (typeof bridge?.setVideo !== "function") return false;
    bridge.setVideo(value);
    return true;
  }, reference);
  if (!openedWithReference) throw new Error("missing_stable_anchor:composer-media.edit-video");
  await waitForVideoEditorReady(page);
  return { kind: "localhostProductBridge", value: "setVideo:openEditor", preferredMissing: "composer-media.edit-video" };
}

async function exerciseVideoEditor(page) {
  const anchors = {};
  await waitForVideoEditorReady(page);
  anchors.root = { kind: "testTagOrBridge", value: "post-video-editor.root" };
  for (const id of [
    "post-video-editor.preview",
    "post-video-editor.mute",
    "post-video-editor.play-pause",
    "post-video-editor.timeline",
    "post-video-editor.crop",
    "post-video-editor.captions",
  ]) {
    anchors[id] = await resolveVideoEditorAnchor(page, id);
  }
  await invokeVideoEditorAction(page, "mute");
  await invokeVideoEditorAction(page, "playPause");
  await invokeVideoEditorAction(page, "trimStart", 0.08);
  await invokeVideoEditorAction(page, "trimEnd", 0.62);
  await invokeVideoEditorAction(page, "crop");
  await invokeVideoEditorAction(page, "cropMode", "Square");
  await invokeVideoEditorAction(page, "cropZoom", 1.32);
  await invokeVideoEditorAction(page, "cropPan", 0.08, -0.04);
  await invokeVideoEditorAction(page, "captions");
  await invokeVideoEditorAction(page, "captionStyle", "Karaoke");
  await invokeVideoEditorAction(page, "export");
  await page.waitForFunction(() => globalThis.__quataPostVideoEditorExport?.status === "success", null, { timeout: 180_000 });
  anchors.export = anchors["post-video-editor.export"];
  anchors.exportState = await page.evaluate(() => globalThis.__quataPostVideoEditorExport || null);
  assertWebVideoEditorExportParity(anchors.exportState, { expectCaptions: true });
  return anchors;
}

async function waitForVideoEditorReady(page) {
  if (await semanticLocator(page, "post-video-editor.root").then((locator) => locator.waitFor({ state: "attached", timeout: 1_500 }).then(() => true)).catch(() => false)) return;
  await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-post-video-editor-e2e") === "ready", null, { timeout: 10_000 });
}

async function resolveVideoEditorAnchor(page, id) {
  if (await semanticLocator(page, id).then((locator) => locator.waitFor({ state: "attached", timeout: 500 }).then(() => true)).catch(() => false)) {
    return { kind: "testTag", value: id };
  }
  return { kind: "localhostProductBridge", value: id, preferredMissing: id };
}

async function invokeVideoEditorAction(page, action, ...args) {
  const id = {
    mute: "post-video-editor.mute",
    playPause: "post-video-editor.play-pause",
    crop: "post-video-editor.crop",
    captions: "post-video-editor.captions",
    export: "post-video-editor.export",
  }[action];
  if (id && await semanticLocator(page, id).then(async (locator) => {
    await locator.click({ force: true, timeout: 800 });
    return true;
  }).catch(() => false)) return;
  const invoked = await page.evaluate(({ name, args: bridgeArgs }) => {
    const bridge = globalThis.__quataPostVideoEditorE2eProduct;
    if (typeof bridge?.[name] !== "function") return false;
    bridge[name](...bridgeArgs);
    return true;
  }, { name: action, args });
  if (!invoked) throw new Error(`missing_stable_anchor:${id || action}`);
}

function assertWebVideoEditorExportParity(exportState, { expectCaptions = false } = {}) {
  if (!exportState || exportState.status !== "success") throw new Error("web_video_editor_export_missing_success_state");
  const requiredOperations = ["trim", "mute", "crop"];
  if (expectCaptions) requiredOperations.push("captions");
  for (const operation of requiredOperations) {
    if (exportState.operations?.[operation] !== true) {
      throw new Error(`web_video_editor_export_missing_operation:${operation}`);
    }
  }
  if (!expectCaptions && exportState.operations?.captions === true) {
    throw new Error("web_video_editor_caption_false_positive");
  }
  if (expectCaptions) {
    const captionText = String(exportState.spec?.captionText || "").trim().toLowerCase();
    const captionDocumentWire = String(exportState.spec?.captionDocumentWire || "").trim();
    const captionSegments = Array.isArray(exportState.spec?.captionSegments) ? exportState.spec.captionSegments : [];
    if (!captionText || captionText === String(exportState.spec?.captionStyle || "").trim().toLowerCase()) {
      throw new Error("web_video_editor_caption_text_not_real_transcript");
    }
    if (!captionText.includes("quata") && !captionText.includes("video") && !captionText.includes("captions")) {
      throw new Error(`web_video_editor_caption_unexpected_transcript:${captionText.slice(0, 80)}`);
    }
    if (!captionDocumentWire.includes("\t") || captionSegments.length <= 0) {
      throw new Error("web_video_editor_caption_document_missing_timings");
    }
    const timedWords = captionSegments.flatMap((segment) => Array.isArray(segment.words) ? segment.words : []);
    if (timedWords.length <= 0 || timedWords.some((word) => !(Number(word.endMs) > Number(word.startMs)))) {
      throw new Error("web_video_editor_caption_document_invalid_word_timings");
    }
  }
  if (!exportState.output || exportState.output.size <= 0) throw new Error("web_video_editor_export_empty_output");
  if (!isSupportedVideoEditorProfile(exportState.output.outputWidth, exportState.output.outputHeight)) {
    throw new Error(`web_video_editor_export_unexpected_dimensions:${exportState.output.outputWidth}x${exportState.output.outputHeight}`);
  }
  if (exportState.output.physicalBackgroundBlur !== true) throw new Error("web_video_editor_background_blur_not_exported");
}

async function saveAndProbeWebExport(page, exportState) {
  const reference = exportState?.reference ?? exportState?.output?.reference;
  if (typeof reference !== "string" || !reference.startsWith("blob:")) {
    throw new Error("web_video_editor_export_missing_blob_reference");
  }
  await mkdir(options.evidenceDir, { recursive: true });
  const outputPath = resolve(options.evidenceDir, "web-post-video-editor-export.webm");
  const base64 = await page.evaluate(async (blobReference) => {
    const response = await fetch(blobReference);
    if (!response.ok) throw new Error(`web_video_editor_blob_fetch_${response.status}`);
    const bytes = new Uint8Array(await response.arrayBuffer());
    let binary = "";
    const chunkSize = 0x8000;
    for (let offset = 0; offset < bytes.length; offset += chunkSize) {
      binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
    }
    return btoa(binary);
  }, reference);
  await writeFile(outputPath, Buffer.from(base64, "base64"));
  const outputStats = await stat(outputPath);
  if (outputStats.size <= 0) throw new Error("web_video_editor_physical_export_empty");

  const ffprobe = JSON.parse(execFileSync("ffprobe", [
    "-v", "error",
    "-print_format", "json",
    "-show_format",
    "-show_streams",
    outputPath,
  ], { encoding: "utf8" }));
  const videoStream = ffprobe.streams?.find((stream) => stream.codec_type === "video");
  const audioStream = ffprobe.streams?.find((stream) => stream.codec_type === "audio");
  if (!videoStream) throw new Error("web_video_editor_physical_video_stream_missing");
  if (audioStream) throw new Error("web_video_editor_physical_audio_stream_present_after_mute");
  const width = Number(videoStream.width || 0);
  const height = Number(videoStream.height || 0);
  if (width !== Number(exportState.output?.outputWidth || 0) || height !== Number(exportState.output?.outputHeight || 0)) {
    throw new Error(`web_video_editor_physical_dimensions:${width}x${height}`);
  }
  const ffprobeDurationMs = Math.round(Number(ffprobe.format?.duration || videoStream.duration || 0) * 1000);
  const bridgeDurationMs = Math.round(Number(exportState.output?.effectiveDurationMs || 0));
  const physicalDurationMs = ffprobeDurationMs > 0 ? ffprobeDurationMs : bridgeDurationMs;
  const expectedDurationMs = Math.max(500, Number(exportState.spec?.trimEndMs || 0) - Number(exportState.spec?.trimStartMs || 0));
  if (physicalDurationMs < expectedDurationMs * 0.45 || physicalDurationMs > expectedDurationMs * 1.45) {
    throw new Error(`web_video_editor_physical_trim_duration:${physicalDurationMs}:${expectedDurationMs}`);
  }
  const captionPixelProbe = exportState.operations?.captions === true ? probeCaptionPixels(outputPath) : null;
  const backgroundBlurPixelProbe = probeBackgroundBlurPixels(outputPath);
  return {
    path: outputPath,
    sizeBytes: outputStats.size,
    durationMs: physicalDurationMs,
    ffprobeDurationMs,
    bridgeDurationMs,
    expectedTrimDurationMs: expectedDurationMs,
    video: { codec: videoStream.codec_name, width, height },
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
  ].some(([expectedWidth, expectedHeight]) =>
    Number(width) === expectedWidth && Number(height) === expectedHeight
  );
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
    throw new Error(`web_video_editor_caption_pixel_probe_unexpected_size:${pixels.length}`);
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
    throw new Error(`web_video_editor_caption_pixels_missing:${brightFraction.toFixed(4)}:${darkFraction.toFixed(4)}`);
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
  const topBackground = sample("crop=iw*0.86:ih*0.16:iw*0.07:ih*0.06");
  const centerForeground = sample("crop=iw*0.86:ih*0.16:iw*0.07:ih*0.42");
  const backgroundSharpness = averageAdjacentLumaDelta(topBackground, width, height);
  const foregroundSharpness = averageAdjacentLumaDelta(centerForeground, width, height);
  if (!(backgroundSharpness < foregroundSharpness * 0.82)) {
    throw new Error(`web_video_editor_background_blur_pixels_missing:${backgroundSharpness.toFixed(2)}:${foregroundSharpness.toFixed(2)}`);
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
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/POST-VIDEO-EDITOR-evidence.json"),
    evidenceDir: resolve("build-reports/web/POST-VIDEO-EDITOR-evidence"),
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--dist", "--chrome", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) {
      throw new Error("invalid_arguments");
    }
    index += 1;
    if (key === "--dist") parsed.distribution = resolve(value);
    if (key === "--chrome") parsed.chrome = resolve(value);
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
  }
  return parsed;
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_POST_VIDEO_EDITOR_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
  for (const profile of ["a", "b"]) {
    for (const field of ["country_code", "phone", "password"]) {
      if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
    }
  }
  return credentials;
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
}

async function wordpressBaseUrl() {
  const source = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebRuntimeConfiguration.kt", import.meta.url), "utf8");
  const url = /wordpressBaseUrl:\s*String\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  if (!url) throw new Error("missing_public_wordpress_configuration");
  return url;
}

async function login(backend, credentials, clientInstanceId) {
  const response = await fetch(`${backend.url}/functions/v1/quata-auth-bridge`, {
    method: "POST",
      headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-POST-VIDEO-EDITOR-web-evidence" },
    body: JSON.stringify({
      action: "web_login",
      country_code: String(credentials.country_code),
      phone_local: localPhone(credentials.country_code, credentials.phone),
      password: String(credentials.password),
      client_instance_id: clientInstanceId,
    }),
    signal: AbortSignal.timeout(30_000),
  }).catch(() => null);
  if (!response) throw new Error("public_request_failed:network");
  const payload = JSON.parse(await response.text());
  if (!response.ok) throw new Error(`public_request_failed:http_${response.status}`);
  const session = payload?.session;
  const profile = payload?.profile;
  const webSession = payload?.web_session;
  if (typeof session?.access_token !== "string" || typeof session?.refresh_token !== "string") throw new Error("invalid_auth_response");
  if (typeof webSession?.token !== "string" || typeof profile?.id !== "string") throw new Error("invalid_auth_response");
  return {
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    webSessionToken: webSession.token,
    userId: profile.id,
    expiresAt: Number(session.expires_at ?? Math.floor(Date.now() / 1000) + Number(session.expires_in ?? 3600)),
    displayName: typeof profile.display_name === "string" ? profile.display_name : null,
    clientInstanceId,
  };
}

function localPhone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  return digits.startsWith(country) ? digits.slice(country.length) : digits;
}

async function startServer(root, wordpressBase, publicBackend) {
  let origin;
  const raw = createServer(async (request, response) => {
    try {
      if (!origin) throw new Error("server_origin_missing");
      const url = new URL(request.url ?? "/", origin);
      if (url.pathname === "/favicon.ico") return response.writeHead(204).end();
      if (url.pathname.startsWith("/wordpress-proxy/")) return proxyWordpressRequest(request, response, wordpressBase, url);
      const file = resolve(root, `.${url.pathname === "/" ? "/index.html" : decodeURIComponent(url.pathname)}`);
      if (!file.startsWith(`${root}\\`) && !file.startsWith(`${root}/`) && file !== root) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      response.writeHead(200, {
        "Content-Type": contentType(file),
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
        "Cache-Control": "no-store",
      });
      response.end(await readStaticFileWithEvidenceConfig(file, publicBackend));
    } catch {
      response.writeHead(500).end();
    }
  });
  await new Promise((ok, fail) => { raw.once("error", fail); raw.listen(0, "127.0.0.1", ok); });
  const address = raw.address();
  if (!address || typeof address === "string") throw new Error("static_server_start_failed");
  origin = `http://127.0.0.1:${address.port}`;
  return { origin, close: () => new Promise((ok, fail) => raw.close((error) => error ? fail(error) : ok())) };
}

async function readStaticFileWithEvidenceConfig(file, publicBackend) {
  if (!file.toLowerCase().endsWith("index.html")) return readFile(file);
  const body = await readFile(file, "utf8");
  return body
    .replace(/<meta name="quata-supabase-url" content="[^"]*">/, `<meta name="quata-supabase-url" content="${htmlAttr(publicBackend.url)}">`)
    .replace(/<meta name="quata-supabase-publishable-key" content="[^"]*">/, `<meta name="quata-supabase-publishable-key" content="${htmlAttr(publicBackend.key)}">`);
}

function htmlAttr(value) {
  return String(value).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

async function proxyWordpressRequest(request, response, wordpressBase, url) {
  const target = `${wordpressBase}${url.pathname.replace(/^\/wordpress-proxy/, "")}${url.search}`;
  const upstream = await fetch(target, { method: request.method, headers: wordpressProxyHeaders(request), signal: AbortSignal.timeout(120_000) });
  response.writeHead(upstream.status, {
    "Content-Type": upstream.headers.get("content-type") ?? "application/octet-stream",
    "Cache-Control": "no-store",
  });
  response.end(Buffer.from(await upstream.arrayBuffer()));
}

function wordpressProxyHeaders(request) {
  const headers = {};
  for (const [key, value] of Object.entries(request.headers)) {
    const lower = key.toLowerCase();
    if (["host", "connection", "content-length"].includes(lower)) continue;
    if (Array.isArray(value)) headers[key] = value.join(", ");
    else if (typeof value === "string") headers[key] = value;
  }
  return headers;
}

async function clickSemanticElement(page, id) {
  const locator = await semanticLocator(page, id);
  await locator.waitFor({ state: "attached", timeout: 20_000 });
  await locator.scrollIntoViewIfNeeded().catch(() => null);
  const box = await locator.boundingBox();
  await locator.click({ force: true, timeout: 5_000 }).catch(async () => {
    if (!box || box.width <= 0 || box.height <= 0) throw new Error(`semantic_anchor_not_visible:${id}`);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  });
}

async function clickComposerType(page, kind) {
  const id = kind === "image" ? "composer-type-image" : "composer-type-video";
  const labelPattern = kind === "image" ? /POSTEAR FOTO\/IMAGEN|IMAGE POST/i : /POSTEAR V[ÍI]DEO|VIDEO POST/i;
  if (await semanticLocator(page, id).then(async (locator) => {
    await locator.click({ force: true, timeout: 2_000 });
    return true;
  }).catch(() => false)) {
    await delay(300);
    if (await composerMediaActionVisible(page, kind)) return { kind: "testTag", value: id };
  }
  await page.getByText(labelPattern).first().click({ force: true, timeout: 10_000 });
  await page.getByText(kind === "image" ? /Elegir imagen|Choose image/i : /Elegir v[íi]deo|Choose video/i).first()
    .waitFor({ state: "visible", timeout: 10_000 });
  return { kind: "visibleText", value: String(labelPattern) };
}

async function clickComposerMediaAction(page, id) {
  const labelPattern = {
    "composer-media.pick-image": /Elegir imagen|Choose image/i,
    "composer-media.capture-image": /Tomar foto|Take photo/i,
    "composer-media.pick-video": /Elegir v[íi]deo|Choose video/i,
    "composer-media.capture-video": /Grabar v[íi]deo|Record video/i,
  }[id];
  if (labelPattern) {
    let locator = page.getByRole("button", { name: labelPattern }).first();
    let anchorKind = "roleButton";
    if (await locator.count() === 0) {
      locator = page.getByText(labelPattern).first();
      anchorKind = "visibleText";
    }
    await locator.waitFor({ state: "visible", timeout: 10_000 });
    await locator.scrollIntoViewIfNeeded().catch(() => null);
    const box = await locator.boundingBox();
    if (!box || box.width <= 0 || box.height <= 0) throw new Error(`semantic_anchor_not_visible:${id}`);
    await locator.click({ force: true, timeout: 5_000 }).catch(async () => {
      await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    });
    return { kind: anchorKind, value: String(labelPattern), preferredMissing: id };
  }
  await clickSemanticElement(page, id);
  return { kind: "testTag", value: id };
}

async function clickComposerEditAction(page) {
  const id = "composer-media.edit-video";
  const alreadyOpen = await postComposerProductState(page)
    .then((state) => state?.videoEditorOpen === true)
    .catch(() => false);
  if (alreadyOpen) {
    return { kind: "alreadyOpen", value: "post-video-editor.root", preferredMissing: id };
  }
  if (await semanticLocator(page, id).then(async (locator) => {
    await locator.waitFor({ state: "attached", timeout: 3_000 });
    await locator.scrollIntoViewIfNeeded().catch(() => null);
    const box = await locator.boundingBox();
    await locator.click({ force: true, timeout: 5_000 }).catch(async () => {
      if (!box || box.width <= 0 || box.height <= 0) throw new Error(`semantic_anchor_not_visible:${id}`);
      await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    });
    return true;
  }).catch(() => false)) {
    return { kind: "testTag", value: id };
  }
  const pattern = /Editar v[íi]deo|Edit video/i;
  const locator = page.getByRole("button", { name: pattern }).first();
  await locator.waitFor({ state: "visible", timeout: 10_000 });
  await locator.click({ force: true, timeout: 5_000 });
  return { kind: "roleButton", value: String(pattern), preferredMissing: id };
}

async function ensureWebVideoSelected(page, expected, preferredMissing) {
  const state = await postComposerProductState(page).catch(() => null);
  if (isExpectedVideoState(state, expected)) return null;
  const injected = await page.evaluate((value) => {
    const bridge = globalThis.__quataPostComposerE2eProduct;
    if (typeof bridge?.setVideo !== "function") return false;
    bridge.setVideo(value);
    return true;
  }, expected);
  if (!injected) throw new Error(`missing_stable_anchor:${preferredMissing}`);
  await page.waitForFunction((value) => {
    const state = globalThis.__quataPostComposerE2eProduct?.state?.();
    return state?.hasVideo === true
      && typeof state.videoUri === "string"
      && (state.videoUri === value || state.videoUri.startsWith("data:video/") || state.videoUri.startsWith("blob:"));
  }, expected, { timeout: 20_000 });
  return { kind: "localhostProductBridge", value: "setVideo", preferredMissing };
}

function isExpectedVideoState(state, expected) {
  return state?.hasVideo === true
    && typeof state.videoUri === "string"
    && (state.videoUri === expected || state.videoUri.startsWith("data:video/") || state.videoUri.startsWith("blob:"));
}

async function composerMediaActionVisible(page, kind) {
  const pattern = kind === "image" ? /Elegir imagen|Choose image/i : /Elegir v[íi]deo|Choose video/i;
  return page.getByText(pattern).first().waitFor({ state: "visible", timeout: 1_500 }).then(() => true).catch(() => false);
}

async function waitForComposerEditAction(page, kind) {
  const id = kind === "image" ? "composer-media.edit-image" : "composer-media.edit-video";
  if (await semanticLocator(page, id).then((locator) => locator.waitFor({ state: "attached", timeout: 1_500 }).then(() => true)).catch(() => false)) {
    return { kind: "testTag", value: id };
  }
  const pattern = kind === "image" ? /Editar imagen|Edit image/i : /Editar v[íi]deo|Edit video/i;
  await page.getByText(pattern).first().waitFor({ state: "visible", timeout: 10_000 });
  return { kind: "visibleText", value: String(pattern), preferredMissing: id };
}

async function semanticLocator(page, id) {
  const direct = page.locator(`[id=${cssString(id)}]`).first();
  if (await direct.count()) return direct;
  const escaped = cssString(id);
  const aria = page.locator(`[aria-label*=${escaped}], [aria-describedby*=${escaped}], [title*=${escaped}]`).first();
  if (await aria.count()) return aria;
  throw new Error(`missing_stable_anchor:${id}`);
}

function cssString(value) {
  return `"${String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

async function postComposerProductState(page) {
  return page.evaluate(() => {
    const bridge = globalThis.__quataPostComposerE2eProduct;
    return typeof bridge?.state === "function" ? bridge.state() : { error: "post_composer_bridge_state_missing" };
  });
}

async function semanticCandidates(page) {
  return page.evaluate(() => [...document.querySelectorAll("[id^='composer-'], [id^='create-post']")].map((element) => {
    const rect = element.getBoundingClientRect();
    return {
      id: element.id || null,
      text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 80),
      rect: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) },
    };
  }));
}

async function screenshot(page, name) {
  await mkdir(options.evidenceDir, { recursive: true });
  const path = resolve(options.evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"],
    [".webp", "image/webp"], [".png", "image/png"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

function gitMetadata() {
  return {
    head: execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim(),
    branch: execFileSync("git", ["branch", "--show-current"], { encoding: "utf8" }).trim(),
    workingTreeDirty: execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], { encoding: "utf8" }).trim().length > 0,
  };
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

function loadPlaywrightCore() {
  const require = createRequire(import.meta.url);
  try {
    return require("playwright-core");
  } catch (firstError) {
    const extra = process.env.QUATA_NODE_MODULES?.trim();
    if (extra) {
      try {
        return require(require.resolve("playwright-core", { paths: [extra] }));
      } catch {}
    }
    throw firstError;
  }
}

async function validSpeechMp4FixtureDataUrl() {
  await mkdir(options.evidenceDir, { recursive: true });
  const wavPath = resolve(options.evidenceDir, "web-post-video-editor-caption-source.wav");
  const videoPath = resolve(options.evidenceDir, "web-post-video-editor-caption-source.mp4");
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
  return `data:video/mp4;base64,${fixture.toString("base64")}`;
}

function powershellQuote(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}
