#!/usr/bin/env node
import { createServer } from "node:http";
import { randomUUID } from "node:crypto";
import { createRequire } from "node:module";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, resolve } from "node:path";

const CHECK = "CREATE-POST-POSTFLIGHT-WEB-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const { chromium } = loadPlaywrightCore();
const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: gitMetadata(),
  steps: [],
  evidence: {},
  cleanup: { publishRequestsObserved: 0, sessionPreserved: false },
};

let server;
let browser;
try {
  const backend = await publicConfig();
  const credentials = (await loadCredentials()).a;
  const session = await login(backend, credentials, `CREATE-POST-POSTFLIGHT-web-${randomUUID()}`);
  server = await startServer(options.distribution, await wordpressBaseUrl(), backend);
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
    sessionStorage.setItem("quata.auth.e2e", "1");
  }, session);

  const page = await context.newPage();
  const faults = [];
  const publishRequests = [];
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message ?? error).slice(0, 160)}`));
  page.on("console", (entry) => {
    if (entry.type() === "error") faults.push(`console_error:${entry.text().slice(0, 180)}`);
  });
  page.on("request", (request) => {
    const url = request.url();
    if (url.includes("/rest/v1/community_posts") && !["GET", "HEAD", "OPTIONS"].includes(request.method())) {
      publishRequests.push({ method: request.method(), path: new URL(url).pathname });
    }
  });

  await page.goto(`${server.origin}/?quata-auth-e2e=1#feed`, { waitUntil: "domcontentloaded", timeout: 60_000 });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await passUgcTermsGate(page);
  await page.waitForFunction(() =>
    document.documentElement.getAttribute("data-quata-shell-route") === "feed" &&
    localStorage.getItem("web.auth.session_ready") === "true",
  null, { timeout: 45_000 });
  const publishAction = page.locator("[id^='feed.action.publish.']").first();
  await publishAction.waitFor({ state: "attached", timeout: 45_000 });
  report.steps.push("authenticated_feed_entry_visible");

  await publishAction.scrollIntoViewIfNeeded().catch(() => {});
  await publishAction.click({ force: true, timeout: 10_000 });
  await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-shell-route") === "composer", null, { timeout: 30_000 });
  await page.locator("#create-post-common-root").first().waitFor({ state: "attached", timeout: 30_000 });
  await page.locator("#navigation\\.primary\\.composer").first().waitFor({ state: "attached", timeout: 15_000 });
  report.steps.push("create_post_opened_from_feed_publish_action");

  for (const id of ["composer-type-text", "composer-type-image", "composer-type-video"]) {
    await page.locator(`#${cssEscape(id)}`).first().waitFor({ state: "attached", timeout: 15_000 });
  }
  report.steps.push("common_create_post_types_visible");
  report.evidence.opened = await screenshot(page, "web-create-post-postflight-opened");

  await page.locator("#navigation\\.primary\\.feed").first().click({ force: true, timeout: 10_000 });
  await page.waitForFunction(() =>
    document.documentElement.getAttribute("data-quata-shell-route") === "feed" &&
    !document.querySelector("#create-post-common-root"),
  null, { timeout: 30_000 });
  await page.locator("[id^='feed.action.publish.']").first().waitFor({ state: "attached", timeout: 30_000 });
  report.steps.push("create_post_returned_to_feed_without_publish");
  report.evidence.returned = await screenshot(page, "web-create-post-postflight-returned");

  const storedActor = await page.evaluate(() => localStorage.getItem("quata_web_user_id"));
  if (storedActor !== session.userId) throw new Error("web_create_post_postflight_actor_changed");
  if (publishRequests.length) throw new Error(`web_create_post_postflight_publish_request_observed:${publishRequests[0].method}`);
  report.steps.push("authenticated_session_preserved_without_publish_mutation");
  report.cleanup = { publishRequestsObserved: 0, sessionPreserved: true };
  const actionableFaults = faults.filter((fault) => !/Failed to load resource: the server responded with a status of 404/.test(fault));
  if (actionableFaults.length) throw new Error(`browser_runtime_fault:${actionableFaults[0]}`);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = String(error?.message ?? error).slice(0, 500);
} finally {
  await browser?.close().catch(() => {});
  await server?.close?.().catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Create Post postflight Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Create Post postflight Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Create Post postflight Web evidence passed.");
}

async function passUgcTermsGate(page) {
  await page.waitForFunction(() => {
    const state = document.documentElement.getAttribute("data-quata-ugc-terms-state");
    return state === "accepted" || state === "required";
  }, null, { timeout: 45_000 });
  const state = await page.evaluate(() => document.documentElement.getAttribute("data-quata-ugc-terms-state"));
  if (state === "accepted") return;
  const button = page.getByRole("button", { name: /Acepto|I accept|J'accepte/i }).first();
  if (await button.isVisible({ timeout: 2_000 }).catch(() => false)) await button.click({ timeout: 5_000 });
  else {
    await page.evaluate(async () => {
      const bridge = globalThis.__quataUgcTermsE2eProduct;
      if (bridge?.version !== 1) throw new Error("ugc_terms_bridge_missing");
      await bridge.accept();
    });
  }
  await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-ugc-terms-state") === "accepted", null, { timeout: 20_000 });
}

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/create-post-postflight-evidence.json"),
    evidenceDir: resolve("build-reports/web/create-post-postflight-evidence"),
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--dist", "--chrome", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) throw new Error("invalid_arguments");
    index += 1;
    if (key === "--dist") parsed.distribution = resolve(value);
    if (key === "--chrome") parsed.chrome = resolve(value);
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
  }
  return parsed;
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_CREATE_POST_POSTFLIGHT_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
  for (const field of ["country_code", "phone", "password"]) {
    if (!credentials?.a?.[field]) throw new Error(`credentials_missing:a.${field}`);
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
    headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-create-post-postflight-web-evidence" },
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
  return country && digits.startsWith(country) ? digits.slice(country.length) : digits;
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
  response.writeHead(upstream.status, { "Content-Type": upstream.headers.get("content-type") ?? "application/octet-stream", "Cache-Control": "no-store" });
  response.end(Buffer.from(await upstream.arrayBuffer()));
}

function wordpressProxyHeaders(request) {
  const headers = {};
  for (const [key, value] of Object.entries(request.headers)) {
    if (["host", "connection", "content-length"].includes(key.toLowerCase())) continue;
    if (Array.isArray(value)) headers[key] = value.join(", ");
    else if (typeof value === "string") headers[key] = value;
  }
  return headers;
}

async function screenshot(page, name) {
  await mkdir(options.evidenceDir, { recursive: true });
  const path = resolve(options.evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

function cssEscape(value) {
  return String(value).replace(/([.:])/g, "\\$1");
}

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"], [".mjs", "text/javascript; charset=utf-8"],
    [".wasm", "application/wasm"], [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"],
    [".webp", "image/webp"], [".png", "image/png"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

function gitMetadata() {
  const exec = createRequire(import.meta.url)("node:child_process").execFileSync;
  return {
    head: exec("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim(),
    branch: exec("git", ["branch", "--show-current"], { encoding: "utf8" }).trim(),
    workingTreeDirty: exec("git", ["status", "--porcelain"], { encoding: "utf8" }).trim().length > 0,
  };
}

function loadPlaywrightCore() {
  const require = createRequire(import.meta.url);
  try {
    return require("playwright-core");
  } catch (firstError) {
    const extra = process.env.QUATA_NODE_MODULES?.trim();
    if (extra) {
      try { return require(require.resolve("playwright-core", { paths: [extra] })); } catch {}
    }
    throw firstError;
  }
}
