#!/usr/bin/env node
import { createServer } from "node:http";
import { mkdtemp, readFile, rm, stat } from "node:fs/promises";
import { extname, join, normalize, resolve } from "node:path";
import { tmpdir } from "node:os";
import { chromium } from "playwright-core";
import { inspectBackendRequest } from "./backend-compatibility-request-policy.mjs";

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
let context;
try {
  context = await chromium.launchPersistentContext(profile, {
    executablePath: chrome,
    headless: true,
    args: ["--disable-gpu", "--no-first-run", "--no-default-browser-check"],
  });
  const page = context.pages()[0] ?? await context.newPage();
  page.on("pageerror", (error) => browserErrors.push(error.message));
  page.on("response", async (response) => {
    const match = response.url().match(/\/rest\/v1\/([a-z0-9_]+)/i);
    if (!match) return;
    const headers = await response.request().allHeaders();
    backendResponses.push({
      table: match[1],
      status: response.status(),
      method: response.request().method(),
      hasBearerAuthorization: typeof headers.authorization === "string" && headers.authorization.trim() !== "",
    });
  });
  page.on("requestfailed", (request) => {
    const match = request.url().match(/\/rest\/v1\/([a-z0-9_]+)/i);
    if (match) failedBackendRequests.push({ table: match[1], reason: request.failure()?.errorText ?? "unknown" });
  });
  await page.route("**/*", async (route) => {
    const request = route.request();
    const decision = inspectBackendRequest({ url: request.url(), method: request.method(), headers: request.headers() }, baseUrl);
    if (!decision.allowed) { blockedRequests.push({ method: request.method(), reason: decision.reason }); return route.abort("blockedbyclient"); }
    return route.continue();
  });
  await page.addInitScript(() => {
    localStorage.setItem("web.auth.session_ready", "true");
    for (const key of Object.keys(localStorage)) {
      if (/access.?token|refresh.?token/i.test(key)) localStorage.removeItem(key);
    }
  });
  for (const route of routes) {
    const backendEventStart = backendResponses.length;
    await page.goto(`${server.origin}/#${route}`, { waitUntil: "domcontentloaded" });
    await page.locator("canvas").first().waitFor({ state: "visible", timeout: 30_000 });
    await page.waitForFunction(
      (expectedRoute) => localStorage.getItem("web.navigation.route") === expectedRoute,
      route,
      { timeout: 30_000 },
    );
    await page.waitForTimeout(1_000);
    const canvasCount = await page.locator("canvas").count();
    const sessionReady = await page.evaluate(() => localStorage.getItem("web.auth.session_ready"));
    const observedBackend = backendResponses.slice(backendEventStart);
    const unsafeBackend = observedBackend.filter((response) =>
      response.method !== "GET" || response.hasBearerAuthorization || response.status < 200 || response.status >= 300
    );
    checks.push({
      route,
      canvasCount,
      sessionReady,
      navigationRoute: await page.evaluate(() => localStorage.getItem("web.navigation.route")),
      observedBackendRequestCount: observedBackend.length,
      passed: canvasCount > 0 && sessionReady === "true" && unsafeBackend.length === 0,
    });
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
      response.method === "GET" && !response.hasBearerAuthorization && response.status >= 200 && response.status < 300
    ) ? "passed" : "failed",
  checks,
  browserErrorCount: browserErrors.length,
  failedBackendRequests,
  blockedRequests,
  backendResponses,
  mutationPolicy: "Only credential-free navigation and public GET responses are accepted; any POST/PATCH/PUT/DELETE or bearer authorization fails the smoke. The disposable browser profile is removed.",
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
        const meta = `<meta name="quata-supabase-url" content="${escapeHtml(supabaseUrl)}">` +
          `<meta name="quata-supabase-publishable-key" content="${escapeHtml(key)}">`;
        body = Buffer.from(html.replace(/<head([^>]*)>/i, `<head$1>${meta}`), "utf8");
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
