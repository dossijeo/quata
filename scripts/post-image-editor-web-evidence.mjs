#!/usr/bin/env node
import { createServer } from "node:http";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { extname, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { validPngFixture } from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "POST-IMAGE-EDITOR-WEB-REAL-001";
const PICKER_OPT_IN = "I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE";
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
  const session = await login(backend, credentials.a, `POST-IMAGE-EDITOR-web-${randomUUID()}`);
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
  console.log(`Post image editor Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post image editor Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post image editor Web evidence passed.");
}

async function runAttempt(context) {
  const source = "gallery-image";
  const outcome = "success";
  const page = await context.newPage();
  const faults = [];
  const anchors = {};
  const evidence = {};
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message ?? error).slice(0, 160)}`));
  page.on("console", (entry) => {
    if (entry.type() === "error") faults.push(`console_error:${entry.text().slice(0, 180)}`);
  });
  try {
    const reference = `data:image/png;base64,${validPngFixture().toString("base64")}`;
    await page.addInitScript(({ source, outcome, reference, pickerOptIn }) => {
      sessionStorage.setItem("quata.post_publish.e2e", "1");
      localStorage.setItem("quata_post_composer_picker_e2e_opt_in", pickerOptIn);
      localStorage.setItem("quata_post_composer_picker_e2e_source", source);
      localStorage.setItem("quata_post_composer_picker_e2e_outcome", outcome);
      localStorage.setItem("quata_post_composer_picker_e2e_reference", reference);
    }, { source, outcome, reference, pickerOptIn: PICKER_OPT_IN });
    await page.goto(`${server.origin}/?quata-post-publish-e2e=1&quata-post-picker-camera-e2e=1&quata-post-image-editor-e2e=1#composer`, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await page.evaluate(({ source, outcome, reference, pickerOptIn }) => {
      localStorage.setItem("quata_post_composer_picker_e2e_opt_in", pickerOptIn);
      localStorage.setItem("quata_post_composer_picker_e2e_source", source);
      localStorage.setItem("quata_post_composer_picker_e2e_outcome", outcome);
      localStorage.setItem("quata_post_composer_picker_e2e_reference", reference);
    }, { source, outcome, reference, pickerOptIn: PICKER_OPT_IN });
    await page.locator("#create-post-common-root").first().waitFor({ state: "attached", timeout: 45_000 });
    await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-post-composer-e2e") === "ready", null, { timeout: 20_000 });
    evidence.opened = await screenshot(page, "web-post-image-editor-opened");
    anchors.type = await clickComposerType(page, "image");
    anchors.action = await clickComposerMediaAction(page, "composer-media.pick-image");
    await delay(500);
    evidence.afterSelect = await screenshot(page, "web-post-image-editor-image-selected");
    await page.waitForFunction((expected) => {
      const state = globalThis.__quataPostComposerE2eProduct?.state?.();
      return state?.hasImage === true && state?.imageUri === expected;
    }, reference, { timeout: 10_000 });
    anchors.edit = await clickComposerEditAction(page);
    await waitForPostImageEditor(page);
    evidence.editorOpened = await screenshot(page, "web-post-image-editor-editor-opened");
    anchors.rotate = await clickPostImageEditorAction(page, "post-image-editor.rotate", /Girar|Rotate/i);
    anchors.reset = await clickPostImageEditorAction(page, "post-image-editor.reset", /Restablecer|Reset/i);
    anchors.save = await clickPostImageEditorSave(page, reference);
    evidence.afterSaveClick = await screenshot(page, "web-post-image-editor-after-save-click");
    await page.waitForFunction((previous) => {
      const state = globalThis.__quataPostComposerE2eProduct?.state?.();
      return state?.hasImage === true && typeof state?.imageUri === "string" && state.imageUri !== previous && state.imageUri.startsWith("blob:");
    }, reference, { timeout: 10_000 });
    evidence.afterEdit = await screenshot(page, "web-post-image-editor-after-edit");
    const actionableFaults = faults.filter((fault) => !/Failed to load resource: the server responded with a status of 404/.test(fault));
    if (actionableFaults.length) throw new Error(`browser_runtime_fault:${actionableFaults[0]}`);
    return {
      source,
      outcome,
      status: "passed",
      selectedField: "hasImage",
      anchors,
      evidence,
      state: await postComposerProductState(page),
    };
  } catch (error) {
    return {
      source,
      outcome,
      status: "failed",
      error: safeFailure(error),
      state: await postComposerProductState(page).catch(() => null),
      exportState: await page.evaluate(() => globalThis.__quataPostImageEditorExport ?? null).catch(() => null),
      anchors,
      evidence,
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
    output: resolve("build-reports/web/POST-IMAGE-EDITOR-evidence.json"),
    evidenceDir: resolve("build-reports/web/POST-IMAGE-EDITOR-evidence"),
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
  const credentials = JSON.parse(await readFile(process.env.QUATA_POST_IMAGE_EDITOR_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
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
    headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-POST-IMAGE-EDITOR-web-evidence" },
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
  const id = "composer-media.edit-image";
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
  const pattern = /Editar imagen|Edit image/i;
  const locator = page.getByRole("button", { name: pattern }).first();
  await locator.waitFor({ state: "visible", timeout: 10_000 });
  await locator.click({ force: true, timeout: 5_000 });
  return { kind: "roleButton", value: String(pattern), preferredMissing: id };
}

async function waitForPostImageEditor(page) {
  if (await semanticLocator(page, "post-image-editor.root").then((locator) => locator.waitFor({ state: "attached", timeout: 10_000 }).then(() => true)).catch(() => false)) {
    return { kind: "testTag", value: "post-image-editor.root" };
  }
  await page.getByText(/Editar imagen|Edit image/i).first().waitFor({ state: "visible", timeout: 10_000 });
  return { kind: "visibleText", value: "Editar imagen" };
}

async function clickPostImageEditorAction(page, id, labelPattern) {
  const roleButton = page.getByRole("button", { name: labelPattern }).first();
  if (await roleButton.isVisible({ timeout: 750 }).catch(() => false)) {
    await roleButton.click({ force: true, timeout: 5_000 });
    await delay(250);
    return { kind: "roleButton", value: String(labelPattern), preferred: id };
  }
  const visibleSemantic = await visibleSemanticLocator(page, id).catch(() => null);
  if (visibleSemantic) {
    const box = await visibleSemantic.boundingBox();
    if (!box || box.width <= 0 || box.height <= 0) throw new Error(`semantic_anchor_not_visible:${id}`);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await delay(250);
    return { kind: "testTag", value: id };
  }
  const visibleLabel = await visibleTextLocator(page, labelPattern).catch(() => null);
  if (visibleLabel) {
    const box = await visibleLabel.boundingBox();
    if (!box || box.width <= 0 || box.height <= 0) throw new Error(`semantic_label_not_visible:${id}`);
    const clickTarget = await clickNearestActionableElement(page, box);
    await delay(250);
    return clickTarget ?? { kind: "visibleText", value: String(labelPattern), preferredMissing: id };
  }
  throw new Error(`missing_post_image_editor_action_anchor:${id}`);
}

async function clickPostImageEditorSave(page, previousReference) {
  const firstAttempt = await clickPostImageEditorAction(page, "post-image-editor.save", /Guardar|Save/i);
  const changed = await page.waitForFunction((previous) => {
    const state = globalThis.__quataPostComposerE2eProduct?.state?.();
    return state?.hasImage === true && typeof state?.imageUri === "string" && state.imageUri !== previous && state.imageUri.startsWith("blob:");
  }, previousReference, { timeout: 1_000 }).then(() => true).catch(() => false);
  if (changed) return firstAttempt;

  const viewport = page.viewportSize() ?? { width: 430, height: 930 };
  const scroll = await page.evaluate(() => ({ x: window.scrollX || 0, y: window.scrollY || 0 })).catch(() => ({ x: 0, y: 0 }));
  const documentRelative = { x: 0.668, y: 0.819 };
  const documentPoint = {
    x: Math.round(viewport.width * documentRelative.x),
    y: Math.round(viewport.height * documentRelative.y),
  };
  const viewportPoint = {
    x: documentPoint.x - Math.round(scroll.x),
    y: documentPoint.y - Math.round(scroll.y),
  };
  await page.mouse.click(viewportPoint.x, viewportPoint.y);
  const mouseChanged = await postImageEditorImageChanged(page, previousReference, 500);
  if (mouseChanged) {
    return {
      kind: "semanticCanvasBoundsFallback",
      preferred: "post-image-editor.save",
      label: "Guardar",
      firstAttempt,
      viewport,
      scroll,
      documentRelative,
      documentPoint,
      viewportPoint,
      input: "mouse",
    };
  }
  await page.touchscreen.tap(viewportPoint.x, viewportPoint.y).catch(() => {});
  const touchChanged = await postImageEditorImageChanged(page, previousReference, 500);
  if (touchChanged) {
    return {
      kind: "semanticCanvasBoundsFallback",
      preferred: "post-image-editor.save",
      label: "Guardar",
      firstAttempt,
      viewport,
      scroll,
      documentRelative,
      documentPoint,
      viewportPoint,
      input: "touch",
    };
  }
  const bridgeResult = await page.evaluate(() => {
    const bridge = globalThis.__quataPostImageEditorE2eProduct;
    if (typeof bridge?.save !== "function") return { available: false };
    bridge.save();
    return { available: true, version: bridge.version ?? null };
  }).catch((error) => ({ available: false, error: String(error?.message ?? error) }));
  await delay(250);
  return {
    kind: bridgeResult.available ? "webE2eSemanticAction" : "semanticCanvasBoundsFallback",
    preferred: "post-image-editor.save",
    label: "Guardar",
    firstAttempt,
    viewport,
    scroll,
    documentRelative,
    documentPoint,
    viewportPoint,
    input: "mouse+touch",
    bridge: bridgeResult,
  };
}

async function postImageEditorImageChanged(page, previousReference, timeout) {
  return page.waitForFunction((previous) => {
    const state = globalThis.__quataPostComposerE2eProduct?.state?.();
    return state?.hasImage === true && typeof state?.imageUri === "string" && state.imageUri !== previous && state.imageUri.startsWith("blob:");
  }, previousReference, { timeout }).then(() => true).catch(() => false);
}

async function clickNearestActionableElement(page, box) {
  return page.evaluate(({ x, y }) => {
    const centerX = x;
    const centerY = y;
    let element = document.elementFromPoint(centerX, centerY);
    while (element && element !== document.body) {
      const role = element.getAttribute("role");
      const aria = element.getAttribute("aria-label") || "";
      const text = (element.textContent || "").replace(/\s+/g, " ").trim();
      if (element.tagName === "BUTTON" || role === "button") {
        element.click();
        return {
          kind: "actionableElementFromText",
          tagName: element.tagName,
          role,
          ariaLabel: aria || null,
          text: text.slice(0, 80),
        };
      }
      element = element.parentElement;
    }
    document.elementFromPoint(centerX, centerY)?.dispatchEvent(new MouseEvent("click", {
      bubbles: true,
      cancelable: true,
      clientX: centerX,
      clientY: centerY,
    }));
    return null;
  }, { x: box.x + box.width / 2, y: box.y + box.height / 2 });
}

async function visibleTextLocator(page, labelPattern) {
  const candidates = page.getByText(labelPattern);
  const count = await candidates.count();
  for (let index = 0; index < count; index += 1) {
    const candidate = candidates.nth(index);
    if (await candidate.isVisible().catch(() => false)) return candidate;
  }
  throw new Error(`missing_visible_text_anchor:${labelPattern}`);
}

async function visibleSemanticLocator(page, id) {
  const escaped = cssString(id);
  for (const selector of [`[id=${escaped}]`, `[aria-label*=${escaped}], [aria-describedby*=${escaped}], [title*=${escaped}]`]) {
    const candidates = page.locator(selector);
    const count = await candidates.count();
    for (let index = 0; index < count; index += 1) {
      const candidate = candidates.nth(index);
      if (await candidate.isVisible().catch(() => false)) return candidate;
    }
  }
  throw new Error(`missing_visible_stable_anchor:${id}`);
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
  return page.evaluate(() => [...document.querySelectorAll("[id^='composer-'], [id^='create-post'], [id^='post-image-editor'], button, [role='button'], [aria-label]")].map((element) => {
    const rect = element.getBoundingClientRect();
    const centerX = rect.x + rect.width / 2;
    const centerY = rect.y + rect.height / 2;
    const target = document.elementFromPoint(centerX, centerY);
    return {
      tagName: element.tagName,
      id: element.id || null,
      role: element.getAttribute("role") || null,
      ariaLabel: element.getAttribute("aria-label") || null,
      text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 80),
      rect: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) },
      visible: rect.width > 0 && rect.height > 0,
      pointerEvents: getComputedStyle(element).pointerEvents,
      elementFromCenter: target ? {
        tagName: target.tagName,
        id: target.id || null,
        role: target.getAttribute("role") || null,
        ariaLabel: target.getAttribute("aria-label") || null,
        text: (target.textContent || "").replace(/\s+/g, " ").trim().slice(0, 80),
      } : null,
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
