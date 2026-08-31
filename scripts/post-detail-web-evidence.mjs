#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { extname, join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import {
  cleanupFeedOfficialCommentsFixture,
  seedFeedOfficialCommentsFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "POST-DETAIL-WEB-COMMON-001";
const defaultCredentialsFile = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const { chromium } = loadPackage("playwright-core");
const pg = loadPackage("pg");

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: gitMetadata(),
  steps: [],
  evidence: {},
  anchors: [],
  fixture: {},
};

let server;
let browser;
let fixture;
let cleanup;

try {
  const config = await publicConfig();
  const credentials = await loadCredentials();
  const actorSession = await login(config, credentials.a, "post-detail-web-actor");
  const targetSession = await login(config, credentials.b, "post-detail-web-target");
  fixture = {
    marker: `qadata-feed-official-comments-post-detail-${randomUUID()}`,
    actorSession,
    targetSession,
  };
  report.steps.push("authenticated_profiles_loaded_without_logging_credentials");
  await seedFeedOfficialCommentsFixture({ fixture, withDatabase });
  report.steps.push("shared_feed_official_fixture_seeded");

  server = await startServer(options.distribution, await wordpressBaseUrl(), config);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: options.headless,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  const context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
  await context.addInitScript((session) => {
    localStorage.setItem("quata_web_access_token", session.accessToken);
    localStorage.setItem("quata_web_refresh_token", session.refreshToken);
    localStorage.setItem("quata_web_session_token", session.webSessionToken);
    localStorage.setItem("quata_web_user_id", session.profileId);
    localStorage.setItem("quata_web_expires_at", String(session.expiresAt));
    localStorage.setItem("web.auth.session_ready", "true");
    localStorage.setItem("quata_web_client_instance_id", session.clientInstanceId);
  }, actorSession);
  const page = await context.newPage();
  await verifyFeedDetail(page, server.origin, fixture);
  await verifyOfficialDetail(page, server.origin, fixture);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.diagnostics = {
    ...(report.diagnostics ?? {}),
    message: String(error?.message ?? error).slice(0, 500),
  };
} finally {
  await browser?.close().catch(() => {});
  await server?.close?.().catch(() => {});
  if (fixture) {
    cleanup = await cleanupFeedOfficialCommentsFixture({ fixture, withDatabase }).catch((error) => ({ status: "failed", error: safeFailure(error) }));
    report.cleanup = cleanup;
    if (cleanup?.status?.startsWith("cleanup_verified")) report.steps.push("shared_fixture_cleanup_verified_zero_residue");
  }
  report.finishedAt = new Date().toISOString();
  await mkdir(resolve(options.output, ".."), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(redactReport(report), null, 2)}\n`);
  console.log(`Post detail Web evidence written: ${options.output}`);
}

if (report.status !== "passed" || !cleanup?.status?.startsWith("cleanup_verified")) {
  console.error(`Post detail Web evidence failed: ${report.error ?? cleanup?.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post detail Web evidence passed.");
}

async function verifyFeedDetail(page, origin, state) {
  await openRoute(page, origin, `post-${encodeURIComponent(state.feed.postId)}`, `post/${state.feed.postId}`);
  await waitForAttribute(page, "data-quata-feed-detail", state.feed.postId, "feed_detail_marker_missing");
  await waitForAnchor(page, "feed.detail.chrome");
  await waitForAnchor(page, "feed.detail.back");
  await waitForAttribute(page, "data-quata-feed-detail-text", state.feed.postBody, "feed_detail_body_marker_missing");
  const bodyVisibleInAccessibility = await visibleText(page, state.feed.postBody, 2_000);
  report.anchors.push("feed.detail.chrome", "feed.detail.back");
  report.diagnostics = { ...(report.diagnostics ?? {}), feedBodyVisibleInAccessibility: bodyVisibleInAccessibility };
  report.evidence.feedDetail = await screenshot(page, "web-post-detail-feed-open");
  await clickAnchor(page, "feed.detail.back");
  await waitForRoute(page, "feed", "feed_back_route_missing");
  await waitForAttribute(page, "data-quata-feed-detail", "", "feed_detail_marker_not_cleared");
  report.evidence.feedBack = await screenshot(page, "web-post-detail-feed-back");
  report.steps.push("feed_detail_common_chrome_and_back_verified");
}

async function verifyOfficialDetail(page, origin, state) {
  await openRoute(page, origin, `official-${encodeURIComponent(state.official.postId)}`, `official/${state.official.postId}`);
  await waitForAnchor(page, "official.detail.chrome");
  await waitForAnchor(page, "official.detail.back");
  await waitForAttribute(page, "data-quata-official-detail-title", state.official.title, "official_detail_title_marker_missing");
  await waitForAttribute(page, "data-quata-official-detail-summary", state.official.summary, "official_detail_summary_marker_missing");
  const titleVisibleInAccessibility = await visibleText(page, state.official.title, 2_000);
  report.anchors.push("official.detail.chrome", "official.detail.back");
  report.diagnostics = { ...(report.diagnostics ?? {}), officialTitleVisibleInAccessibility: titleVisibleInAccessibility };
  report.evidence.officialDetail = await screenshot(page, "web-post-detail-official-open");
  await clickAnchor(page, "official.detail.back");
  await waitForRoute(page, "official", "official_back_route_missing");
  report.evidence.officialBack = await screenshot(page, "web-post-detail-official-back");
  report.steps.push("official_detail_common_chrome_and_back_verified");
}

async function openRoute(page, origin, fragment, expectedRoute) {
  await page.goto(`${origin}/?quata-post-detail-e2e=1&route-reload=${Date.now()}#${fragment}`, {
    waitUntil: "domcontentloaded",
    timeout: 60_000,
  });
  await waitForRoute(page, expectedRoute, `route_missing:${expectedRoute}`, 35_000);
  await delay(1_200);
}

async function waitForRoute(page, expectedRoute, error, timeout = 15_000) {
  await page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    expectedRoute,
    { timeout },
  ).catch(() => {
    throw new Error(error);
  });
}

async function waitForAttribute(page, name, value, error, timeout = 15_000) {
  await page.waitForFunction(
    ({ name, value }) => document.documentElement.getAttribute(name) === value,
    { name, value },
    { timeout },
  ).catch(() => {
    throw new Error(error);
  });
}

async function waitForAnchor(page, tag, timeout = 15_000) {
  const locator = await anchorLocator(page, tag, timeout);
  await locator.waitFor({ state: "attached", timeout: 1_000 });
  return locator;
}

async function clickAnchor(page, tag) {
  const locator = await anchorLocator(page, tag, 15_000);
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  const box = await locator.boundingBox().catch(() => null);
  if (!box || box.width <= 0 || box.height <= 0) throw new Error(`anchor_not_visible:${tag}`);
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await delay(500);
}

async function anchorLocator(page, tag, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const locator = page.locator(`[id=${cssString(tag)}], [aria-label*=${cssString(tag)}], [title*=${cssString(tag)}]`).first();
    if (await locator.count()) {
      const box = await locator.boundingBox().catch(() => null);
      if (box && box.width > 0 && box.height > 0) return locator;
    }
    await page.mouse.wheel(0, 420).catch(() => {});
    await delay(250);
  }
  throw new Error(`missing_stable_anchor:${tag}`);
}

async function visibleText(page, text, timeout = 15_000) {
  const compactNeedle = compact(text);
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const foundInDom = await page.evaluate((needle) => {
      const compact = (value) => String(value ?? "").replace(/\s+/g, "");
      return compact(document.body?.innerText ?? document.body?.textContent ?? "").includes(needle);
    }, compactNeedle).catch(() => false);
    if (foundInDom) return true;
    const foundInAccessibility = await accessibilityTreeContainsText(page, compactNeedle);
    if (foundInAccessibility) return true;
    await page.mouse.wheel(0, 420).catch(() => {});
    await delay(250);
  }
  return false;
}

async function accessibilityTreeContainsText(page, compactNeedle) {
  const snapshot = await page.accessibility?.snapshot({ interestingOnly: false }).catch(() => null);
  const stack = snapshot ? [snapshot] : [];
  while (stack.length) {
    const node = stack.pop();
    const text = compact(`${node?.name ?? ""} ${node?.value ?? ""} ${node?.description ?? ""}`);
    if (text.includes(compactNeedle)) return true;
    for (const child of node?.children ?? []) stack.push(child);
  }
  return false;
}

async function screenshot(page, name) {
  await mkdir(options.evidenceDir, { recursive: true });
  const path = join(options.evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

async function withDatabase(callback) {
  const [connectionString, ca] = await Promise.all([
    readFile(process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile, "utf8"),
    readFile(process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile, "utf8"),
  ]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try {
    return await callback(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const baseUrl = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!baseUrl || !key) throw new Error("missing_public_supabase_configuration");
  return { baseUrl, url: baseUrl, key };
}

async function wordpressBaseUrl() {
  const source = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebRuntimeConfiguration.kt", import.meta.url), "utf8");
  const url = /wordpressBaseUrl:\s*String\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  if (!url) throw new Error("missing_public_wordpress_configuration");
  return url;
}

async function login(config, user, label) {
  const response = await fetch(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: { apikey: config.key, "content-type": "application/json", "x-client-info": "quata-post-detail-web-evidence" },
    body: JSON.stringify({
      action: "web_login",
      country_code: String(user.country_code),
      phone_local: localPhone(user.country_code, user.phone),
      password: String(user.password),
      client_instance_id: `${label}-${randomUUID()}`,
    }),
    signal: AbortSignal.timeout(30_000),
  }).catch(() => null);
  if (!response) throw new Error("public_auth_request_failed:network");
  const text = await response.text();
  const payload = JSON.parse(text || "{}");
  if (!response.ok) throw new Error(`public_auth_request_failed:http_${response.status}`);
  const session = payload?.session;
  const profile = payload?.profile;
  const webSession = payload?.web_session;
  if (!session?.access_token || !session?.refresh_token || !webSession?.token || !uuid.test(profile?.id ?? "")) {
    throw new Error("invalid_auth_response");
  }
  return {
    profileId: profile.id,
    displayName: String(profile.display_name ?? profile.displayName ?? "").trim(),
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    webSessionToken: webSession.token,
    expiresAt: Number(session.expires_at ?? Math.floor(Date.now() / 1000) + Number(session.expires_in ?? 3600)),
    clientInstanceId: `${label}-${randomUUID()}`,
  };
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_POST_DETAIL_CREDENTIALS_FILE?.trim() || defaultCredentialsFile, "utf8"));
  for (const profile of ["a", "b"]) for (const field of ["country_code", "phone", "password"]) {
    if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
  }
  return credentials;
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
  await new Promise((ok, fail) => {
    raw.once("error", fail);
    raw.listen(0, "127.0.0.1", ok);
  });
  const address = raw.address();
  if (!address || typeof address === "string") throw new Error("static_server_start_failed");
  origin = `http://127.0.0.1:${address.port}`;
  return { origin, close: () => new Promise((ok, fail) => raw.close((error) => error ? fail(error) : ok())) };
}

async function readStaticFileWithEvidenceConfig(file, publicBackend) {
  if (!file.toLowerCase().endsWith("index.html")) return readFile(file);
  const body = await readFile(file, "utf8");
  return body
    .replace(/<meta name="quata-supabase-url" content="[^"]*">/, `<meta name="quata-supabase-url" content="${htmlAttr(publicBackend.baseUrl)}">`)
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

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/post-detail-evidence.json"),
    evidenceDir: resolve("build-reports/web/post-detail-evidence"),
    headless: true,
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    if (key === "--headed") {
      parsed.headless = false;
      continue;
    }
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

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"],
    [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"],
    [".wasm", "application/wasm"],
    [".json", "application/json"],
    [".css", "text/css"],
    [".svg", "image/svg+xml"],
    [".webp", "image/webp"],
    [".png", "image/png"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

function localPhone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  return digits.startsWith(country) ? digits.slice(country.length) : digits;
}

function cssString(value) {
  return `"${String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

function compact(value) {
  return String(value ?? "").replace(/\s+/g, "");
}

function htmlAttr(value) {
  return String(value).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function gitMetadata() {
  return {
    head: execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim(),
    branch: execFileSync("git", ["branch", "--show-current"], { encoding: "utf8" }).trim(),
    workingTreeDirty: execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], { encoding: "utf8" }).trim().length > 0,
  };
}

function redactReport(value) {
  return JSON.parse(JSON.stringify(value, (key, entry) => {
    if (/token|password|authorization|apikey|secret/i.test(key)) return "[REDACTED]";
    return entry;
  }));
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

function loadPackage(name) {
  const require = createRequire(import.meta.url);
  try {
    return require(name);
  } catch (error) {
    const extra = process.env.QUATA_NODE_MODULES?.trim() || "C:/Users/PC/StudioProjects/quata/node_modules";
    try {
      return require(require.resolve(name, { paths: [extra] }));
    } catch {}
    throw error;
  }
}
