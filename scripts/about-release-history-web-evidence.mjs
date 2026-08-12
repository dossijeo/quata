#!/usr/bin/env node
import { chromium } from "playwright-core";
import { createServer } from "node:http";
import { createHash } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { spawn } from "node:child_process";

const options = parseArgs(process.argv.slice(2));
const report = {
  check: "ABOUT-RELEASE-HISTORY-WEB-001",
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
  const context = await browser.newContext({
    acceptDownloads: true,
    locale: "es-ES",
    viewport: { width: 390, height: 844 },
  });
  const page = await context.newPage();

  await page.goto(`${server.origin}/#about`);
  await waitForRoute(page, "about");
  await expectVisibleText(page, /Acerca de Quata|About Quata/);
  report.steps.push("about_deeplink_rendered");
  report.evidence.about = await screenshot(page, "web-about");
  report.evidence.legalDocuments = [];
  report.evidence.legalDocuments.push(await clickAndCaptureDownload(page, /Política de privacidad|Privacy policy/, "privacy_es.docx"));
  report.evidence.legalDocuments.push(await clickAndCaptureDownload(page, /Seguridad de menores|Child safety/, "child_safety_es.docx"));
  report.steps.push("about_legal_documents_downloaded_from_local_assets");

  await clickVisibleText(page, /Historial de versiones|Release history/);
  await waitForHash(page, "#release-history");
  await waitForRoute(page, "release-history");
  await expectVisibleText(page, /Historial de versiones|Release history/);
  report.steps.push("about_release_history_button_navigated");
  report.evidence.releaseHistoryFromAbout = await screenshot(page, "web-release-history-from-about");

  await page.goto(`${server.origin}/#release-history`);
  await waitForRoute(page, "release-history");
  await expectVisibleText(page, /Historial de versiones|Release history/);
  report.steps.push("release_history_direct_deeplink_rendered");
  report.evidence.releaseHistoryDeepLink = await screenshot(page, "web-release-history-direct");

  report.status = "passed";
} catch (error) {
  report.error = safeError(error);
} finally {
  if (browser) await browser.close().catch(() => {});
  if (server) await new Promise((resolve) => server.close(resolve));
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`About/Release History Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`About/Release History Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("About/Release History Web evidence passed.");
}

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/about-release-history-evidence.json"),
    evidenceDir: resolve("build-reports/web/about-release-history-evidence"),
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

async function waitForRoute(page, route) {
  await page.waitForFunction((expected) =>
    localStorage.getItem("web.navigation.route") === expected ||
    document.documentElement.getAttribute("data-quata-shell-route") === expected ||
    location.hash.replace(/^#/, "") === expected,
  route, { timeout: 30_000 });
}

async function waitForHash(page, hash) {
  await page.waitForFunction((expected) => location.hash === expected, hash, { timeout: 10_000 });
}

async function expectVisibleText(page, pattern) {
  await page.getByText(pattern).first().waitFor({ state: "visible", timeout: 30_000 });
}

async function clickVisibleText(page, pattern) {
  const locator = page.getByText(pattern).first();
  await locator.waitFor({ state: "visible", timeout: 30_000 });
  await clickLocatorCenter(page, locator);
}

async function clickAndCaptureDownload(page, pattern, expectedName) {
  const locator = page.getByText(pattern).first();
  await locator.waitFor({ state: "visible", timeout: 30_000 });
  const [download] = await Promise.all([
    page.waitForEvent("download", { timeout: 30_000 }),
    clickLocatorCenter(page, locator),
  ]);
  const suggestedName = download.suggestedFilename();
  if (suggestedName !== expectedName) throw new Error(`legal_download_name_mismatch:${suggestedName}`);
  return {
    displayName: suggestedName,
    localAsset: `legal/${suggestedName}`,
  };
}

async function clickLocatorCenter(page, locator) {
  const box = await locator.boundingBox();
  if (!box) throw new Error("click_target_missing_bounding_box");
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
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
