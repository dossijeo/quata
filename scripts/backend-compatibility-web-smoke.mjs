#!/usr/bin/env node
import { createServer } from "node:http";
import { mkdtemp, readFile, rm, stat } from "node:fs/promises";
import { extname, join, normalize, resolve } from "node:path";
import { tmpdir } from "node:os";
import { chromium } from "playwright-core";
import { inspectBackendRequest } from "./backend-compatibility-request-policy.mjs";
import { publicPostIdFromPayload, detailEvidence, detailEvidenceEvent } from "./backend-compatibility-feed-detail.mjs";
import { sanitizeWebSmokeRequest, webSmokePhase } from "./backend-compatibility-web-smoke-report.mjs";

const options = parseArguments(process.argv.slice(2));
const distribution = resolve(options.dist ?? "web/build/dist/wasmJs/productionExecutable");
const chrome = options.chrome ?? process.env.QUATA_CHROME_PATH ?? (
  process.platform === "win32" ? "C:/Program Files/Google/Chrome/Application/chrome.exe" : "google-chrome"
);
const baseUrl = process.env.QUATA_SUPABASE_URL?.trim().replace(/\/+$/, "");
const publishableKey = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY?.trim();
if (!baseUrl || !publishableKey) throw new Error("Missing QUATA_SUPABASE_URL or QUATA_SUPABASE_PUBLISHABLE_KEY.");
if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("Invalid public Supabase URL.");
await requireFile(join(distribution, "index.html"));

const routes = ["feed", "official", "communities"];
const profile = await mkdtemp(join(tmpdir(), "quata-backend-gate-web-"));
const server = await startServer(distribution, baseUrl, publishableKey);
const browserErrors = [];
const checks = [];
const backendResponses = [];
const failedBackendRequests = [];
const blockedRequests = [];
const requests = [];
let observedPostId = null;
let context;
let currentRoute = "<unknown>";
try {
  context = await chromium.launchPersistentContext(profile, {
    executablePath: chrome,
    headless: true,
    args: ["--disable-gpu", "--no-first-run", "--no-default-browser-check"],
  });
  const page = context.pages()[0] ?? await context.newPage();
  const requestDiagnostics = new WeakMap();
  const recordRequest = (request) => {
    const existing = requestDiagnostics.get(request);
    if (existing) return existing;
    const diagnostic = sanitizeWebSmokeRequest({
      url: request.url(),
      method: request.method(),
      resourceType: request.resourceType(),
      frame: request.frame() === page.mainFrame() ? "main" : "subframe",
      phase: webSmokePhase(currentRoute, request.resourceType()),
      route: currentRoute,
    }, baseUrl);
    requestDiagnostics.set(request, diagnostic);
    requests.push(diagnostic);
    return diagnostic;
  };
  page.on("pageerror", (error) => browserErrors.push(error.message));
  page.on("request", recordRequest);
  page.on("response", async (response) => {
    const match = response.url().match(/\/rest\/v1\/([a-z0-9_]+)/i);
    if (!match) return;
    const headers = await response.request().allHeaders();
    const responseUrl = new URL(response.url());
    const payloadPostId = publicPostIdFromPayload(await response.text().catch(() => ""));
    backendResponses.push({
      table: match[1],
      status: response.status(),
      method: response.request().method(),
      hasApiKey: typeof headers.apikey === "string" && headers.apikey.trim() !== "",
      hasBearerAuthorization: typeof headers.authorization === "string" && headers.authorization.trim() !== "",
      query: responseUrl.searchParams.toString(),
      payloadPostId,
    });
    if (match[1] === "community_posts" && response.request().method() === "GET" && response.status() >= 200 && response.status() < 300 && !observedPostId) {
      observedPostId = payloadPostId;
    }
  });
  page.on("requestfailed", (request) => {
    const match = request.url().match(/\/rest\/v1\/([a-z0-9_]+)/i);
    if (match) failedBackendRequests.push({ table: match[1], reason: request.failure()?.errorText ?? "unknown" });
  });
  await page.route("**/*", async (route) => {
    const request = route.request();
    const decision = inspectBackendRequest({ url: request.url(), method: request.method(), headers: request.headers() }, baseUrl);
    if (!decision.allowed) {
      blockedRequests.push({ ...recordRequest(request), reason: decision.reason });
      return route.abort("blockedbyclient");
    }
    return route.continue();
  });
  await page.addInitScript(() => {
    localStorage.setItem("web.auth.session_ready", "true");
    for (const key of Object.keys(localStorage)) {
      if (/access.?token|refresh.?token/i.test(key)) localStorage.removeItem(key);
    }
  });
  for (const route of routes) {
    currentRoute = route;
    const backendEventStart = backendResponses.length;
    await page.goto(`${server.origin}/#${route}`, { waitUntil: "domcontentloaded" });
    await page.locator("canvas").first().waitFor({ state: "visible", timeout: 30_000 });
    await page.waitForFunction(
      (expectedRoute) => localStorage.getItem("web.navigation.route") === expectedRoute,
      route,
      { timeout: 30_000 },
    );
    if (route === "feed") {
      await page.waitForFunction(() =>
        localStorage.getItem("web.runtime.backend_configured") === "true" &&
        localStorage.getItem("web.feed.collector_started") === "true" &&
        localStorage.getItem("web.feed.remote_read_state") === "request_succeeded",
      { timeout: 30_000 });
    }
    await page.waitForTimeout(1_000);
    const canvasCount = await page.locator("canvas").count();
    const sessionReady = await page.evaluate(() => localStorage.getItem("web.auth.session_ready"));
    const observedBackend = backendResponses.slice(backendEventStart);
    const feedDiagnostics = route === "feed" ? await page.evaluate(() => ({
      backendConfigured: localStorage.getItem("web.runtime.backend_configured"),
      collectorStarted: localStorage.getItem("web.feed.collector_started"),
      remoteReadState: localStorage.getItem("web.feed.remote_read_state"),
      remoteReadError: localStorage.getItem("web.feed.remote_read_error"),
    })) : undefined;
    const unsafeBackend = observedBackend.filter((response) =>
      response.method !== "GET" || !response.hasApiKey || response.hasBearerAuthorization || response.status < 200 || response.status >= 300
    );
    checks.push({
      route,
      canvasCount,
      sessionReady,
      navigationRoute: await page.evaluate(() => localStorage.getItem("web.navigation.route")),
      observedBackendRequestCount: observedBackend.length,
      ...(feedDiagnostics ? { diagnostics: feedDiagnostics } : {}),
      passed: canvasCount > 0 && sessionReady === "true" && unsafeBackend.length === 0 &&
        (route !== "feed" || feedDiagnostics.remoteReadState === "request_succeeded"),
    });
  }
  if (!observedPostId) {
    checks.push({ route: "post/<id>", passed: false, reason: "public_post_id_not_observed" });
  } else {
    currentRoute = `post/${observedPostId}`;
    const start = backendResponses.length;
    await page.goto(`${server.origin}/#post-${observedPostId}`, { waitUntil: "domcontentloaded" });
    await page.locator("canvas").first().waitFor({ state: "visible", timeout: 30_000 });
    await page.waitForFunction((id) => localStorage.getItem("web.navigation.route") === `post/${id}`, observedPostId, { timeout: 30_000 });
    await page.waitForFunction((id) => document.documentElement.getAttribute("data-quata-feed-detail") === id, observedPostId, { timeout: 30_000 });
    await page.waitForTimeout(1_000);
    const detailResponses = backendResponses.slice(start);
    const evidence = detailEvidenceEvent(detailResponses, observedPostId);
    checks.push({ route: `post/${observedPostId}`, canvasCount: await page.locator("canvas").count(), detailGet2xx: detailEvidence(detailResponses, observedPostId), evidence: evidence && { method: evidence.method, status: evidence.status, payloadPostId: evidence.payloadPostId }, passed: detailEvidence(detailResponses, observedPostId) });
  }
} finally {
  await context?.close().catch(() => undefined);
  await server.close();
  await rm(profile, { recursive: true, force: true });
}

const report = {
  check: "BACKEND-COMPATIBILITY-WEB",
  mode: "credential_free_route_shell",
  status: checks.every((check) => check.passed) && browserErrors.length === 0 &&
    failedBackendRequests.length === 0 &&
    blockedRequests.length === 0 &&
    backendResponses.every((response) =>
      response.method === "GET" && response.hasApiKey && !response.hasBearerAuthorization && response.status >= 200 && response.status < 300
    ) ? "passed" : "failed",
  checks,
  browserErrorCount: browserErrors.length,
  failedBackendRequests,
  blockedRequests,
  requests,
  detail: { observedPostId, accreditedGet2xx: checks.find((check) => check.route.startsWith("post/"))?.detailGet2xx ?? false },
  // query is intentionally retained only in process memory to accredit the
  // detail request.  It is never emitted because reports must be safe even
  // when a browser sends unexpected user data in a query string.
  backendResponses: backendResponses.map(({ query, ...response }) => response),
  mutationPolicy: "Only credential-free public GET responses are accepted; every other Supabase method or credential fails the smoke. The disposable browser profile is removed.",
};
console.log(JSON.stringify(report, null, 2));
process.exitCode = report.status === "passed" ? 0 : 1;

function parseArguments(args) {
  const parsed = {};
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--dist" || argument === "--chrome") {
      const value = args[index + 1];
      if (!value || value.startsWith("--")) throw new Error(`Missing value for ${argument}.`);
      parsed[argument.slice(2)] = value;
      index += 1;
    } else if (argument === "--help" || argument === "-h") {
      console.log("Usage: node scripts/backend-compatibility-web-smoke.mjs [--dist DIR] [--chrome PATH]");
      process.exit(0);
    } else throw new Error(`Unknown argument: ${argument}`);
  }
  return parsed;
}

async function requireFile(path) {
  const info = await stat(path).catch(() => null);
  if (!info?.isFile()) throw new Error(`Missing Web distribution file: ${path}`);
}

async function startServer(root, supabaseUrl, key) {
  const server = createServer(async (request, response) => {
    try {
      const requestUrl = new URL(request.url ?? "/", "http://localhost");
      const requested = requestUrl.pathname === "/" ? "index.html" : requestUrl.pathname.replace(/^\/+/, "");
      const path = resolve(root, normalize(requested));
      if (!path.startsWith(`${resolve(root)}\\`) && path !== resolve(root, "index.html") && process.platform === "win32") {
        response.writeHead(403).end();
        return;
      }
      if (!path.startsWith(`${resolve(root)}/`) && path !== resolve(root, "index.html") && process.platform !== "win32") {
        response.writeHead(403).end();
        return;
      }
      let body = await readFile(path);
      if (requested === "index.html") {
        const html = body.toString("utf8");
        const replaceMeta = (source, name, value) => source.replace(
          new RegExp(`(<meta\\s+name=["']${name}["']\\s+content=["'])[^"']*(["'][^>]*>)`, "i"),
          `$1${escapeHtml(value)}$2`,
        );
        body = Buffer.from(
          replaceMeta(replaceMeta(html, "quata-supabase-url", supabaseUrl), "quata-supabase-publishable-key", key),
          "utf8",
        );
      }
      response.writeHead(200, {
        "content-type": contentType(path),
        "cache-control": "no-store",
        "cross-origin-opener-policy": "same-origin",
        "cross-origin-embedder-policy": "require-corp",
      });
      response.end(body);
    } catch {
      response.writeHead(404).end();
    }
  });
  await new Promise((resolveReady, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolveReady);
  });
  const address = server.address();
  return {
    origin: `http://127.0.0.1:${address.port}`,
    close: () => new Promise((resolveClose) => server.close(resolveClose)),
  };
}

function escapeHtml(value) {
  return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

function contentType(path) {
  return {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".wasm": "application/wasm",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json",
  }[extname(path).toLowerCase()] ?? "application/octet-stream";
}
