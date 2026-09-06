#!/usr/bin/env node
import { chromium } from "playwright-core";
import { createHash } from "node:crypto";
import { createServer } from "node:http";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { spawn } from "node:child_process";

const SplashAnchor = "quata-splash-root";
const options = parseArgs(process.argv.slice(2));
const report = {
  check: "FLOW-SPLASH-STARTUP-WEB-001",
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  evidence: {},
};

let server;
let browser;

try {
  await assertDistribution(options.distribution);
  server = await startServer(options.distribution);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--no-first-run"],
  });
  const context = await browser.newContext({ locale: "es-ES", viewport: { width: 390, height: 844 } });
  await context.addInitScript(() => {
    window.__quataStartupRouteHistory = [];
    const recordRoute = (source, value) => {
      if (!value) return;
      window.__quataStartupRouteHistory.push({ source, value, at: Date.now() });
    };
    const storagePrototype = Object.getPrototypeOf(window.localStorage);
    const originalSetItem = storagePrototype.setItem;
    storagePrototype.setItem = function trackedSetItem(key, value) {
      if (key === "web.navigation.route") recordRoute("localStorage", value);
      return originalSetItem.call(this, key, value);
    };
    const observer = new MutationObserver(() => {
      recordRoute("shell", document.documentElement.getAttribute("data-quata-shell-route"));
    });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-quata-shell-route"] });
  });
  const page = await context.newPage();

  await page.goto(`${server.origin}/?startup-evidence=${Date.now()}`, { waitUntil: "domcontentloaded" });
  await page.locator("canvas").first().waitFor({ state: "visible", timeout: 30_000 });
  const splashAnchor = await observeSplashAnchor(page);
  report.evidence.splashAnchor = splashAnchor;
  if (!splashAnchor.found) {
    throw new Error("startup_splash_missing_accessible_anchor");
  }
  report.evidence.splash = await screenshot(page, "web-startup-splash");
  report.steps.push("shared_splash_visible_with_accessible_anchor");

  await waitForRoute(page, "feed");
  await waitForSplashGone(page);
  report.evidence.feed = await screenshot(page, "web-startup-feed");
  report.evidence.finalState = await page.evaluate(() => ({
    hash: location.hash,
    navigationRoute: localStorage.getItem("web.navigation.route"),
    shellRoute: document.documentElement.getAttribute("data-quata-shell-route"),
    authRoute: localStorage.getItem("web.navigation.route") === "auth",
  }));
  if (report.evidence.finalState.authRoute) {
    throw new Error("startup_unexpected_auth_route");
  }
  report.evidence.routeHistory = await routeHistory(page);
  const unexpectedRoutes = report.evidence.routeHistory.filter((entry) => ["auth", "whats-new"].includes(entry.value));
  if (unexpectedRoutes.length > 0) {
    throw new Error(`startup_unexpected_intermediate_route:${unexpectedRoutes.map((entry) => entry.value).join(",")}`);
  }
  report.steps.push("startup_transition_reached_public_feed_without_auth_flash");
  report.status = "passed";
} catch (error) {
  report.error = safeError(error);
} finally {
  if (browser) await browser.close().catch(() => {});
  if (server) await new Promise((resolveClose) => server.close(resolveClose));
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Startup/Splash Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Startup/Splash Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Startup/Splash Web evidence passed.");
}

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/startup-splash-evidence.json"),
    evidenceDir: resolve("build-reports/web/startup-splash-evidence"),
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[++index];
    if (!["--dist", "--chrome", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) {
      throw new Error("invalid_arguments");
    }
    if (key === "--dist") result.distribution = resolve(value);
    if (key === "--chrome") result.chrome = resolve(value);
    if (key === "--out") result.output = resolve(value);
    if (key === "--evidence-dir") result.evidenceDir = resolve(value);
  }
  return result;
}

async function assertDistribution(root) {
  const entry = join(root, "index.html");
  const info = await stat(entry).catch(() => null);
  if (!info?.isFile()) throw new Error(`missing_web_distribution:${entry}`);
}

async function startServer(root) {
  const server = createServer(async (request, response) => {
    const rawPath = new URL(request.url ?? "/", "http://127.0.0.1").pathname;
    const relative = decodeURIComponent(rawPath === "/" ? "/index.html" : rawPath).replace(/^\/+/, "");
    const file = resolve(root, relative);
    if (!file.startsWith(resolve(root))) {
      response.writeHead(403).end();
      return;
    }
    const fallback = join(root, "index.html");
    const target = await stat(file).then((info) => info.isFile() ? file : fallback).catch(() => fallback);
    response.writeHead(200, {
      "content-type": contentType(target),
      "cache-control": "no-store",
    });
    response.end(await readFile(target));
  });
  await new Promise((resolveListen) => server.listen(0, "127.0.0.1", resolveListen));
  const address = server.address();
  server.origin = `http://127.0.0.1:${address.port}`;
  return server;
}

function contentType(file) {
  switch (extname(file)) {
    case ".html": return "text/html; charset=utf-8";
    case ".js": return "text/javascript; charset=utf-8";
    case ".wasm": return "application/wasm";
    case ".json": return "application/json; charset=utf-8";
    case ".css": return "text/css; charset=utf-8";
    case ".png": return "image/png";
    case ".svg": return "image/svg+xml";
    default: return "application/octet-stream";
  }
}

async function observeSplashAnchor(page) {
  const anchor = page.getByLabel(SplashAnchor).first();
  const found = await anchor.waitFor({ state: "visible", timeout: 2_500 }).then(() => true).catch(() => false);
  return {
    target: SplashAnchor,
    found,
    fallback: found ? null : "compose_wasm_canvas_screenshot",
  };
}

async function waitForRoute(page, route) {
  await page.waitForFunction((expected) =>
    localStorage.getItem("web.navigation.route") === expected ||
    document.documentElement.getAttribute("data-quata-shell-route") === expected,
  route, { timeout: 45_000 });
}

async function waitForSplashGone(page) {
  await page.getByLabel(SplashAnchor).first().waitFor({ state: "hidden", timeout: 15_000 });
}

async function routeHistory(page) {
  return await page.evaluate(() => globalThis.__quataStartupRouteHistory ?? []);
}

async function screenshot(page, name) {
  await mkdir(options.evidenceDir, { recursive: true });
  const path = join(options.evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return {
    path,
    sha256: createHash("sha256").update(await readFile(path)).digest("hex"),
  };
}

async function gitMetadata() {
  const head = (await runSilent("git", ["rev-parse", "HEAD"])).trim();
  const status = await runSilent("git", ["status", "--porcelain"]);
  return { head, workingTreeDirty: status.trim().length > 0 };
}

async function runSilent(command, args) {
  return await new Promise((resolveRun, reject) => {
    let output = "";
    let stderr = "";
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: false });
    child.stdout.on("data", (chunk) => { output += chunk.toString(); });
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolveRun(output) : reject(new Error(`command_failed:${command}:${code}:${stderr.trim()}`)));
  });
}

function safeError(error) {
  return String(error?.message ?? error ?? "unknown").replace(/[A-Za-z0-9_-]{32,}/g, "[REDACTED]");
}
