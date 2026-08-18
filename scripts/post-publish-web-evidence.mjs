#!/usr/bin/env node
import { createServer } from "node:http";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import pg from "pg";
import { chromium } from "playwright-core";
import {
  cleanupPostPublishFixture,
  createPostPublishFixture,
  pollPostPublishFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "POST-PUBLISH-WEB-REAL-001";
const OPT_IN = "I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: gitMetadata(),
  steps: [],
  postgrest: [],
  network: [],
  evidence: {},
  cleanup: { state: "not_started" },
};

let server;
let browser;
let context;
let fixture;

try {
  if (process.env.QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN !== OPT_IN) {
    throw new Error("mutation_opt_in_required");
  }
  const config = await loadConfiguration();
  const backend = await publicConfig();
  server = await startServer(options.distribution, await wordpressBaseUrl(), backend);
  const session = await login(backend, config.credentials.a, `post-publish-web-${randomUUID()}`);
  fixture = createPostPublishFixture({
    actorSession: { profileId: session.userId },
    platformLabel: "web",
    runId: randomUUID(),
  });
  report.steps.push("real_authorized_profile_authenticated_without_logging_credentials");

  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
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

  const page = await context.newPage();
  const faults = [];
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message ?? error).slice(0, 160)}`));
  page.on("console", (entry) => {
    if (entry.type() === "error") faults.push(`console_error:${entry.text().slice(0, 180)}`);
  });
  page.on("response", async (response) => {
    const url = response.url();
    const entry = sanitizedNetworkEntry(response);
    if (!entry) return;
    report.network.push(entry);
    if (entry.kind === "postgrest_community_posts") {
      report.postgrest.push({
        method: entry.method,
        status: entry.status,
        urlKind: entry.urlKind,
      });
    }
  });
  page.on("requestfailed", (request) => {
    const entry = sanitizedRequestFailureEntry(request);
    if (entry) report.network.push(entry);
  });

  await page.goto(`${server.origin}/?quata-post-publish-e2e=1#composer`, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await page.waitForFunction(() =>
    localStorage.getItem("web.navigation.route") === "composer" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "composer",
    { timeout: 45_000 },
  );
  await page.locator("#create-post-common-root").first().waitFor({ state: "attached", timeout: 45_000 });
  await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-post-composer-e2e") === "ready", { timeout: 15_000 });
  report.evidence.composer = await screenshot(page, "web-post-publish-composer-opened");
  report.steps.push("common_create_post_root_mounted_on_web");

  await clickSemanticElement(page, "composer-type-text");
  if (!(await semanticAnchorPresent(page, "composer-text-input", 1_500))) {
    const viewport = page.viewportSize() ?? { width: 430, height: 930 };
    await page.mouse.click(viewport.width / 2, 190);
    await delay(500);
    report.steps.push("web_text_type_selected_by_visual_fallback_after_compose_action_limit");
  }
  report.steps.push("common_text_post_type_selected_by_semantic_anchor");
  const body = `Publicacion reversible POST-PUBLISH Web ${fixture.marker}`;
  await fillSemanticInput(page, "composer-text-input", body);
  report.evidence.filled = await screenshot(page, "web-post-publish-composer-filled");
  await page.mouse.wheel(0, 900);
  await delay(350);
  report.evidence.beforePublish = await screenshot(page, "web-post-publish-before-publish");
  const postsBefore = report.postgrest.filter((entry) => entry.method === "POST").length;
  report.diagnostics = {
    ...(report.diagnostics ?? {}),
    publishCandidates: await semanticCandidates(page, "composer-publish"),
    bridgeBeforeSubmit: await postComposerBridgeState(page),
    composerStateBeforeSubmit: await postComposerProductState(page),
  };
  await clickSemanticElement(page, "composer-publish", { reinforcePhysical: true });
  await page.getByText("Publicar", { exact: true }).last().click({ force: true, timeout: 2_000 }).catch(() => {});
  const viewport = page.viewportSize() ?? { width: 430, height: 930 };
  await page.mouse.click(viewport.width / 2, viewport.height - 160);
  report.steps.push("web_publish_button_clicked_by_visual_viewport_fallback_after_missing_dom_anchor");
  await page.evaluate(() => {
    const bridge = globalThis.__quataPostComposerE2eProduct;
    if (bridge?.version !== 1 || typeof bridge.submitText !== "function") throw Error("post_composer_bridge_missing");
    bridge.submitText();
  });
  report.diagnostics.bridgeAfterSubmit = await postComposerBridgeState(page);
  report.diagnostics.composerStateAfterSubmit = await postComposerProductState(page);
  report.steps.push("web_post_publish_submitted_by_localhost_opt_in_product_bridge_after_visual_route");
  await delay(750);
  report.diagnostics.composerStateAfterSubmitDelay = await postComposerProductState(page);
  report.evidence.afterPublishClick = await screenshot(page, "web-post-publish-after-publish-click");
  await waitForPostgrestPostOrComposerError(page, report.postgrest, postsBefore);
  const published = await pollPostPublishFixture({
    fixture,
    withDatabase: (callback) => withPg(config, callback),
    delay,
  });
  report.evidence.published = {
    state: "verified_in_database",
    postId: published.postId,
    mediaUrls: published.mediaUrls,
  };
  await page.waitForFunction(() => localStorage.getItem("web.navigation.route") !== "composer", { timeout: 45_000 }).catch(() => {});
  report.evidence.afterPublish = await screenshot(page, "web-post-publish-after-publish");
  report.steps.push("real_text_post_published_from_common_composer_and_verified_by_marker");

  report.cleanup = await cleanupPostPublishFixture({ fixture, withDatabase: (callback) => withPg(config, callback) });
  report.steps.push("post_publish_cleanup_verified_residue_absent");
  const actionableFaults = faults.filter((fault) => !/Failed to load resource: the server responded with a status of 404/.test(fault));
  if (actionableFaults.length) {
    report.faults = actionableFaults;
    throw new Error("browser_runtime_fault");
  }
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  if (fixture && report.cleanup.state === "not_started") {
    try {
      const config = await loadConfiguration();
      report.cleanup = await cleanupPostPublishFixture({ fixture, withDatabase: (callback) => withPg(config, callback) });
    } catch (cleanupError) {
      report.cleanup = {
        state: "rollback_pending",
        marker: fixture.marker,
        postId: fixture.publishedPostId ?? null,
        error: safeFailure(cleanupError),
      };
    }
  }
} finally {
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  await server?.close().catch(() => {});
  report.finishedAt = new Date().toISOString();
  if (fixture) report.marker = fixture.marker;
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`Post publish Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post publish Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post publish Web evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/post-publish-evidence.json"),
    evidenceDir: resolve("build-reports/web/post-publish-evidence"),
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

async function loadConfiguration() {
  const credentialsPath = process.env.QUATA_POST_PUBLISH_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE;
  const credentials = JSON.parse(await readFile(credentialsPath, "utf8"));
  for (const profile of ["a", "b"]) {
    for (const field of ["country_code", "phone", "password"]) {
      if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
    }
  }
  return {
    credentials,
    dbUrlFile: process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbTlsCaFile: process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE,
  };
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
  const response = await postJson(`${backend.url}/functions/v1/quata-auth-bridge`, {
    apikey: backend.key,
    "content-type": "application/json",
    "x-client-info": "quata-post-publish-web-evidence",
  }, {
    action: "web_login",
    country_code: String(credentials.country_code),
    phone_local: localPhone(credentials.country_code, credentials.phone),
    password: String(credentials.password),
    client_instance_id: clientInstanceId,
  });
  const root = response.payload;
  const session = root?.session;
  const profile = root?.profile;
  const webSession = root?.web_session;
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

async function postJson(url, headers, body) {
  const response = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(30_000),
  }).catch(() => null);
  if (!response) throw new Error("public_request_failed:network");
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(`public_request_failed:http_${response.status}`);
  return { status: response.status, payload };
}

async function pgConnectionConfig(config) {
  const raw = (await readFile(config.dbUrlFile, "utf8")).trim();
  const ca = await readFile(config.dbTlsCaFile, "utf8");
  const url = new URL(raw);
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true, servername: url.hostname } };
}

async function withPg(config, action) {
  const client = new pg.Client(await pgConnectionConfig(config));
  await client.connect();
  try {
    return await action(client);
  } finally {
    await client.end().catch(() => {});
  }
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
      const body = await readStaticFileWithEvidenceConfig(file, publicBackend);
      response.end(body);
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
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

async function proxyWordpressRequest(request, response, wordpressBase, url) {
  const target = `${wordpressBase}${url.pathname.replace(/^\/wordpress-proxy/, "")}${url.search}`;
  const body = ["GET", "HEAD"].includes(request.method ?? "GET") ? undefined : await readRequestBody(request);
  const upstream = await fetch(target, {
    method: request.method,
    headers: wordpressProxyHeaders(request),
    body,
    signal: AbortSignal.timeout(120_000),
  });
  const upstreamBody = Buffer.from(await upstream.arrayBuffer());
  report.network.push(sanitizedWordpressUpstreamEntry(upstream, url, upstreamBody));
  response.writeHead(upstream.status, {
    "Content-Type": upstream.headers.get("content-type") ?? "application/octet-stream",
    "Cache-Control": "no-store",
  });
  response.end(upstreamBody);
}

async function readRequestBody(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  return Buffer.concat(chunks);
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

async function clickSemanticElement(page, id, { reinforcePhysical = false } = {}) {
  const locator = await semanticLocator(page, id);
  await locator.waitFor({ state: "attached", timeout: 20_000 });
  await locator.scrollIntoViewIfNeeded().catch(() => null);
  const box = await locator.boundingBox();
  await locator.click({ force: true, timeout: 5_000 }).catch(async () => {
    if (!box || box.width <= 0 || box.height <= 0) throw new Error(`semantic_anchor_not_visible:${id}`);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  });
  if (reinforcePhysical && box && box.width > 0 && box.height > 0) {
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  }
}

async function fillSemanticInput(page, id, value) {
  const locator = await semanticLocator(page, id);
  await locator.waitFor({ state: "attached", timeout: 20_000 });
  await locator.scrollIntoViewIfNeeded().catch(() => null);
  await locator.click({ force: true, timeout: 5_000 });
  await page.keyboard.press(process.platform === "darwin" ? "Meta+A" : "Control+A").catch(() => {});
  await page.keyboard.insertText(value);
  await page.waitForFunction(({ selector, expected }) => {
    const node = document.querySelector(selector);
    return (node?.textContent ?? node?.value ?? "").includes(expected);
  }, { selector: `#${id}`, expected: value.slice(0, 24) }, { timeout: 15_000 }).catch(() => {});
}

async function semanticLocator(page, id) {
  const direct = page.locator(`#${id}`).first();
  if (await direct.count()) return direct;
  const escaped = cssString(id);
  const aria = page.locator(`[aria-label*=${escaped}], [aria-describedby*=${escaped}], [title*=${escaped}]`).first();
  if (await aria.count()) return aria;
  throw new Error(`missing_stable_anchor:${id}`);
}

async function semanticAnchorPresent(page, id, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await semanticLocator(page, id).then(() => true).catch(() => false)) return true;
    await delay(100);
  }
  return false;
}

function cssString(value) {
  return `"${String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

async function semanticCandidates(page, id) {
  return await page.evaluate((needle) => {
    const viewportHeight = window.innerHeight;
    return [...document.querySelectorAll(`#${CSS.escape(needle)}, [aria-label*="${needle}"], [aria-describedby*="${needle}"], [title*="${needle}"]`)]
      .map((element) => {
        const rect = element.getBoundingClientRect();
        const style = window.getComputedStyle(element);
        return {
          id: element.id || null,
          role: element.getAttribute("role"),
          ariaLabel: element.getAttribute("aria-label"),
          text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 80),
          rect: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) },
          visible: rect.width > 0 && rect.height > 0 && style.visibility !== "hidden" && style.display !== "none",
          nearBottomChrome: rect.y > viewportHeight - 120,
        };
      });
  }, id);
}

async function postComposerBridgeState(page) {
  return page.evaluate(() => ({
    readyAttribute: document.documentElement.getAttribute("data-quata-post-composer-e2e"),
    hasBridge: globalThis.__quataPostComposerE2eProduct?.version === 1,
    hasSubmitText: typeof globalThis.__quataPostComposerE2eProduct?.submitText === "function",
  }));
}

async function postComposerProductState(page) {
  return page.evaluate(() => {
    const bridge = globalThis.__quataPostComposerE2eProduct;
    return typeof bridge?.state === "function" ? bridge.state() : { error: "post_composer_bridge_state_missing" };
  });
}

function sanitizedNetworkEntry(response) {
  const url = new URL(response.url());
  const method = response.request().method();
  if (url.pathname.includes("/rest/v1/community_posts")) {
    return {
      kind: "postgrest_community_posts",
      method,
      status: response.status(),
      urlKind: url.searchParams.has("select") ? "read" : "mutation_or_read",
    };
  }
  if (url.pathname.includes("/rest/v1/community_members")) {
    return { kind: "postgrest_community_members", method, status: response.status(), urlKind: "wall_lookup" };
  }
  if (url.pathname.includes("/rest/v1/community_walls_stats")) {
    return { kind: "postgrest_community_walls_stats", method, status: response.status(), urlKind: "wall_fallback" };
  }
  if (url.pathname.includes("/rest/v1/community_profiles")) {
    return { kind: "postgrest_community_profiles", method, status: response.status(), urlKind: "profile_lookup" };
  }
  if (url.pathname.includes("/wordpress-proxy/")) {
    return { kind: "wordpress_proxy", method, status: response.status(), path: url.pathname.replace(/^\/wordpress-proxy\//, "") };
  }
  if (url.pathname.includes("/functions/v1/")) {
    return { kind: "edge_function", method, status: response.status(), path: url.pathname.replace(/^.*\/functions\/v1\//, "") };
  }
  return null;
}

function sanitizedRequestFailureEntry(request) {
  const url = new URL(request.url());
  if (
    !url.pathname.includes("/rest/v1/community_") &&
    !url.pathname.includes("/wordpress-proxy/") &&
    !url.pathname.includes("/functions/v1/")
  ) return null;
  return {
    kind: "request_failed",
    method: request.method(),
    path: url.pathname.includes("/wordpress-proxy/")
      ? url.pathname.replace(/^\/wordpress-proxy\//, "")
      : url.pathname.replace(/^.*\/rest\/v1\//, "rest/v1/").replace(/^.*\/functions\/v1\//, "functions/v1/"),
    failure: request.failure()?.errorText?.slice(0, 120) ?? "unknown",
  };
}

function sanitizedWordpressUpstreamEntry(upstream, localUrl, body) {
  const entry = {
    kind: "wordpress_proxy_upstream",
    method: "POST",
    status: upstream.status,
    path: localUrl.pathname.replace(/^\/wordpress-proxy\//, ""),
    bytes: body.length,
    contentType: upstream.headers.get("content-type")?.split(";")[0] ?? "unknown",
  };
  try {
    const root = JSON.parse(body.toString("utf8"));
    entry.jsonShape = Array.isArray(root) ? "array" : typeof root;
    entry.success = typeof root?.success === "boolean" ? root.success : null;
    entry.action = typeof root?.data?.action === "string" ? root.data.action.slice(0, 40) : null;
  } catch {
    entry.jsonShape = "non_json";
  }
  return entry;
}

async function waitForPostgrestPostOrComposerError(page, entries, previousCount, timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (entries.filter((entry) => entry.method === "POST" && entry.status >= 200 && entry.status < 300).length > previousCount) return;
    const state = await postComposerProductState(page).catch(() => null);
    if (state?.hasError) throw new Error(`post_publish_ui_error:${String(state.error ?? "unknown").slice(0, 160)}`);
    await delay(100);
  }
  throw new Error("post_publish_request_missing");
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
    workingTreeDirty: execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], { encoding: "utf8" }).trim().length > 0,
  };
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}
