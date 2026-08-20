#!/usr/bin/env node
import { createServer } from "node:http";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { extname, resolve } from "node:path";

const CHECK = "POST-DESTINATION-WEB-REAL-001";
const OPT_IN = "I_ACCEPT_WEB_POST_DESTINATION_FIXTURE";
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
  const session = await login(backend, credentials.a, `post-destination-web-${randomUUID()}`);
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
    localStorage.setItem("web.auth.session_ready", "true");
    localStorage.setItem("quata_web_client_instance_id", state.clientInstanceId);
  }, session);
  report.steps.push("real_profile_authenticated_without_logging_credentials");

  for (const mode of options.modes) {
    report.attempts.push(await runAttempt(context, mode));
  }
  const failed = report.attempts.find((attempt) => attempt.status !== "passed");
  if (failed) throw new Error(`web_destination_attempt_failed:${failed.mode}`);
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
  console.log(`Post destination Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post destination Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post destination Web evidence passed.");
}

async function runAttempt(context, mode) {
  const page = await context.newPage();
  try {
    await page.addInitScript(({ mode, optIn }) => {
      sessionStorage.setItem("quata.post_publish.e2e", "1");
      localStorage.setItem("quata_post_destination_e2e_opt_in", optIn);
      localStorage.setItem("quata_post_destination_e2e_mode", mode);
    }, { mode, optIn: OPT_IN });
    await page.goto(`${server.origin}/?quata-post-publish-e2e=1&quata-post-destination-e2e=1#composer`, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await page.locator("#create-post-common-root").first().waitFor({ state: "attached", timeout: 45_000 });
    await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-post-composer-e2e") === "ready", null, { timeout: 20_000 });
    const opened = await screenshot(page, `web-post-destination-opened-${mode}`);
    await clickComposerType(page);
    const form = await screenshot(page, `web-post-destination-form-${mode}`);
    if (mode === "multiple") {
      await clickSemanticElement(page, "composer-destination-option.e2e-wall-bata");
      await page.waitForFunction(() => globalThis.__quataPostComposerE2eProduct?.state?.()?.selectedDestinationWallId === "e2e-wall-bata", null, { timeout: 8_000 });
      const selected = await screenshot(page, "web-post-destination-multiple-selected");
      return { mode, status: "passed", anchors: ["composer-destination-option.e2e-wall-bata", "composer-destination-selected"], evidence: { opened, form, selected } };
    }
    const expected = mode === "empty" ? "composer-destination-empty" : "composer-destination-error";
    await semanticLocator(page, expected).then((locator) => locator.waitFor({ state: "attached", timeout: 10_000 }));
    await semanticLocator(page, "composer-destination-retry").then((locator) => locator.waitFor({ state: "attached", timeout: 8_000 }));
    await clickSemanticElement(page, "composer-publish");
    await semanticLocatorWithDiscoveryScroll(page, "composer-feedback-error").then((locator) => locator.waitFor({ state: "attached", timeout: 8_000 }));
    const blocked = await screenshot(page, `web-post-destination-${mode}-publish-blocked`);
    return { mode, status: "passed", anchors: [expected, "composer-destination-retry", "composer-feedback-error"], evidence: { opened, form, blocked } };
  } catch (error) {
    return { mode, status: "failed", error: safeFailure(error), candidates: await semanticCandidates(page).catch(() => []) };
  } finally {
    await page.close().catch(() => {});
  }
}

async function clickComposerType(page) {
  await clickSemanticElement(page, "composer-type-text").catch(async () => {
    await page.getByText(/POSTEAR TEXTO|POST TEXT/i).first().click({ force: true, timeout: 10_000 });
  });
}

async function clickSemanticElement(page, id) {
  const locator = await semanticLocatorWithDiscoveryScroll(page, id);
  await locator.waitFor({ state: "attached", timeout: 20_000 });
  await locator.scrollIntoViewIfNeeded().catch(() => null);
  const box = await visibleSemanticBox(page, locator, id);
  await locator.click({ force: true, timeout: 5_000 }).catch(async () => {
    if (!box || box.width <= 0 || box.height <= 0) throw new Error(`semantic_anchor_not_visible:${id}`);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  });
}

async function visibleSemanticBox(page, locator, id) {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const box = await locator.boundingBox().catch(() => null);
    if (box && box.width > 0 && box.height > 0) return box;
    await scrollAllScrollableContainers(page, attempt + 1);
    await locator.scrollIntoViewIfNeeded().catch(() => null);
    await page.waitForTimeout(120);
  }
  throw new Error(`semantic_anchor_not_visible:${id}`);
}

async function semanticLocatorWithDiscoveryScroll(page, id) {
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const locator = await semanticLocator(page, id).catch(() => null);
    if (locator && await locator.count()) return locator;
    await scrollAllScrollableContainers(page, attempt + 1);
    await page.waitForTimeout(150);
  }
  await page.evaluate(() => {
    const scroller = document.scrollingElement || document.documentElement;
    scroller.scrollTo({ top: 0, behavior: "instant" });
  }).catch(() => null);
  return semanticLocator(page, id);
}

async function scrollAllScrollableContainers(page, step) {
  await page.evaluate((distance) => {
    const amount = distance * Math.max(240, Math.floor(window.innerHeight * 0.45));
    const candidates = [document.scrollingElement, document.documentElement, document.body, ...document.querySelectorAll("*")].filter(Boolean);
    for (const element of candidates) {
      const style = getComputedStyle(element);
      const canScroll = element.scrollHeight > element.clientHeight + 8 && /(auto|scroll|overlay)/.test(`${style.overflowY} ${style.overflow}`);
      if (canScroll || element === document.scrollingElement || element === document.documentElement || element === document.body) {
        element.scrollTop = amount;
      }
    }
  }, step);
  await page.mouse.wheel(0, Math.max(360, Math.floor(step * 180)));
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

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/post-destination-evidence.json"),
    evidenceDir: resolve("build-reports/web/post-destination-evidence"),
    modes: ["multiple", "empty", "failure"],
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--dist", "--chrome", "--out", "--evidence-dir", "--modes"].includes(key) || !value || value.startsWith("--")) throw new Error("invalid_arguments");
    index += 1;
    if (key === "--dist") parsed.distribution = resolve(value);
    if (key === "--chrome") parsed.chrome = resolve(value);
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
    if (key === "--modes") parsed.modes = value.split(",").map((item) => item.trim()).filter(Boolean);
  }
  if (parsed.modes.some((mode) => !["multiple", "empty", "failure"].includes(mode))) throw new Error("invalid_destination_mode");
  return parsed;
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_POST_DESTINATION_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
  for (const profile of ["a"]) for (const field of ["country_code", "phone", "password"]) {
    if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
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
    headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-post-destination-web-evidence" },
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
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      response.writeHead(200, { "Content-Type": contentType(file), "Cross-Origin-Opener-Policy": "same-origin", "Cross-Origin-Embedder-Policy": "require-corp", "Cache-Control": "no-store" });
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

async function proxyWordpressRequest(request, response, wordpressBase, url) {
  const target = `${wordpressBase}${url.pathname.replace(/^\/wordpress-proxy/, "")}${url.search}`;
  const upstream = await fetch(target, { method: request.method, headers: wordpressProxyHeaders(request), signal: AbortSignal.timeout(120_000) });
  response.writeHead(upstream.status, { "Content-Type": upstream.headers.get("content-type") ?? "application/octet-stream", "Cache-Control": "no-store" });
  response.end(Buffer.from(await upstream.arrayBuffer()));
}

function wordpressProxyHeaders(request) {
  const headers = {};
  for (const [key, value] of Object.entries(request.headers)) {
    const lower = key.toLowerCase();
    if (["host", "connection", "content-length"].includes(lower)) continue;
    headers[key] = Array.isArray(value) ? value.join(", ") : value;
  }
  return headers;
}

function htmlAttr(value) {
  return String(value).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"], [".mjs", "text/javascript; charset=utf-8"],
    [".wasm", "application/wasm"], [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"], [".webp", "image/webp"], [".png", "image/png"],
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
