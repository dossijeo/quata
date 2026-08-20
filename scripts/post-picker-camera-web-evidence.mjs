#!/usr/bin/env node
import { createServer } from "node:http";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { extname, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { longMp4FixturePath, validPngFixture } from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "POST-PICKER-CAMERA-WEB-REAL-001";
const OPT_IN = "I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
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
  const session = await login(backend, credentials.a, `post-picker-camera-web-${randomUUID()}`);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  const context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
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

  for (const attempt of options.sources) {
    report.attempts.push(await runAttempt(context, attempt));
  }
  const failedAttempt = report.attempts.find((attempt) => attempt.status !== "passed");
  if (failedAttempt) throw new Error(`web_attempt_failed:${failedAttempt.source}:${failedAttempt.outcome}`);

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
  console.log(`Post picker/camera Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post picker/camera Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post picker/camera Web evidence passed.");
}

async function runAttempt(context, descriptor) {
  const [source, outcome = "success"] = descriptor.split(":");
  if (!["gallery-image", "camera-image", "gallery-video", "camera-video"].includes(source)) {
    throw new Error(`invalid_source:${source}`);
  }
  if (!["success", "cancelled", "failure", "unsupported"].includes(outcome)) throw new Error(`invalid_outcome:${outcome}`);
  const page = await context.newPage();
  const faults = [];
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message ?? error).slice(0, 160)}`));
  page.on("console", (entry) => {
    if (entry.type() === "error") faults.push(`console_error:${entry.text().slice(0, 180)}`);
  });
  try {
    const reference = source.endsWith("image")
      ? `data:image/png;base64,${validPngFixture().toString("base64")}`
      : `${server.origin}/__quata-fixtures/post-picker-camera-long-video.mp4`;
    await page.addInitScript(({ source, outcome, reference }) => {
      sessionStorage.setItem("quata.post_publish.e2e", "1");
      localStorage.setItem("quata_post_composer_picker_e2e_opt_in", "I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE");
      localStorage.setItem("quata_post_composer_picker_e2e_source", source);
      localStorage.setItem("quata_post_composer_picker_e2e_outcome", outcome);
      localStorage.setItem("quata_post_composer_picker_e2e_reference", reference);
    }, { source, outcome, reference });
    await page.goto(`${server.origin}/?quata-post-publish-e2e=1&quata-post-picker-camera-e2e=1#composer`, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await page.evaluate(({ source, outcome, reference }) => {
      localStorage.setItem("quata_post_composer_picker_e2e_opt_in", "I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE");
      localStorage.setItem("quata_post_composer_picker_e2e_source", source);
      localStorage.setItem("quata_post_composer_picker_e2e_outcome", outcome);
      localStorage.setItem("quata_post_composer_picker_e2e_reference", reference);
    }, { source, outcome, reference });
    await page.locator("#create-post-common-root").first().waitFor({ state: "attached", timeout: 45_000 });
    await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-post-composer-e2e") === "ready", null, { timeout: 20_000 });
    const opened = await screenshot(page, `web-post-picker-camera-opened-${source}-${outcome}`);
    const resolvedTypeAnchor = await clickComposerType(page, source.endsWith("image") ? "image" : "video");
    const actionTag = {
      "gallery-image": "composer-media.pick-image",
      "camera-image": "composer-media.capture-image",
      "gallery-video": "composer-media.pick-video",
      "camera-video": "composer-media.capture-video",
    }[source];
    const resolvedActionAnchor = await clickComposerMediaAction(page, actionTag);
    await delay(500);
    const afterTap = await screenshot(page, `web-post-picker-camera-after-tap-${source}-${outcome}`);
    const selectedField = source.endsWith("image") ? "hasImage" : "hasVideo";
    if (outcome === "success") {
      await page.waitForFunction((field) => globalThis.__quataPostComposerE2eProduct?.state?.()?.[field] === true, selectedField, { timeout: 10_000 });
      const editAnchor = await waitForComposerEditAction(page, source.endsWith("image") ? "image" : "video").catch((error) => ({
        kind: "missingStableAnchor",
        value: source.endsWith("image") ? "composer-media.edit-image" : "composer-media.edit-video",
        reason: String(error?.message ?? error).slice(0, 200),
      }));
      if (editAnchor.kind === "missingStableAnchor") report.steps.push(`web_edit_anchor_not_blocking_picker_state:${source}`);
    } else {
      await page.waitForFunction((field) => globalThis.__quataPostComposerE2eProduct?.state?.()?.[field] !== true, selectedField, { timeout: 5_000 });
      if (outcome === "failure" || outcome === "unsupported") {
        await page.waitForFunction(() => globalThis.__quataPostComposerE2eProduct?.state?.()?.hasMediaError === true, null, { timeout: 8_000 });
        await page.locator('[id="composer-media.error"], [aria-label*="composer-media.error"]').first()
          .waitFor({ state: "attached", timeout: 8_000 });
      } else {
        const state = await postComposerProductState(page);
        if (state?.hasMediaError === true) throw new Error(`cancelled_picker_must_not_show_media_error:${source}`);
      }
    }
    const afterAction = await screenshot(page, `web-post-picker-camera-after-action-${source}-${outcome}`);
    const actionableFaults = faults.filter((fault) => !/Failed to load resource: the server responded with a status of 404/.test(fault));
    if (actionableFaults.length) throw new Error(`browser_runtime_fault:${actionableFaults[0]}`);
    return {
      source,
      outcome,
      status: "passed",
      selectedField,
      fixture: source.endsWith("video") ? { path: options.videoFixture, kind: "long-mp4" } : { kind: "png" },
      anchors: { type: resolvedTypeAnchor, action: resolvedActionAnchor },
      evidence: { opened, afterTap, afterAction },
      state: await postComposerProductState(page),
    };
  } catch (error) {
    return {
      source,
      outcome,
      status: "failed",
      error: safeFailure(error),
      state: await postComposerProductState(page).catch(() => null),
      candidates: await semanticCandidates(page).catch(() => []),
    };
  } finally {
    await page.close().catch(() => {});
  }
}

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/post-picker-camera-evidence.json"),
    evidenceDir: resolve("build-reports/web/post-picker-camera-evidence"),
    videoFixture: resolve(process.env.QUATA_POST_PICKER_CAMERA_VIDEO_FIXTURE?.trim() || longMp4FixturePath()),
    sources: ["gallery-image", "camera-image", "camera-image:cancelled"],
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--dist", "--chrome", "--out", "--evidence-dir", "--sources", "--video-fixture"].includes(key) || !value || value.startsWith("--")) {
      throw new Error("invalid_arguments");
    }
    index += 1;
    if (key === "--dist") parsed.distribution = resolve(value);
    if (key === "--chrome") parsed.chrome = resolve(value);
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
    if (key === "--video-fixture") parsed.videoFixture = resolve(value);
    if (key === "--sources") parsed.sources = value.split(",").map((item) => item.trim()).filter(Boolean);
  }
  return parsed;
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_POST_PICKER_CAMERA_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
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
    headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-post-picker-camera-web-evidence" },
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
      if (url.pathname === "/__quata-fixtures/post-picker-camera-long-video.mp4") {
        response.writeHead(200, { "Content-Type": "video/mp4", "Cache-Control": "no-store" });
        return response.end(await readFile(options.videoFixture));
      }
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
  } catch (error) {
    const extra = process.env.QUATA_NODE_MODULES?.trim();
    if (extra) {
      try {
        return require(require.resolve("playwright-core", { paths: [extra] }));
      } catch {}
    }
    throw error;
  }
}
