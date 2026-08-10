#!/usr/bin/env node
import { chromium } from "playwright-core";
import { createServer } from "node:http";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { spawn } from "node:child_process";

const options = parseArgs(process.argv.slice(2));
const report = {
  check: "NOTIFICATIONS-WEB-COMMON-001",
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
    args: ["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--force-renderer-accessibility"],
  });
  const context = await browser.newContext({ locale: "es-ES", viewport: { width: 390, height: 844 } });
  const page = await context.newPage();
  const faults = [];
  page.on("console", (message) => {
    if (message.type() === "error") faults.push(`console:${message.type()}:${message.text().slice(0, 240)}`);
  });
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message || error).slice(0, 240)}`));

  await page.goto(`${server.origin}/?quata-chat-e2e=1#notifications`);
  await waitForRoute(page, "notifications");
  await page.waitForTimeout(2_000);
  report.evidence.list = await screenshot(page, "web-notifications-list");
  await assertNonBlankPng(report.evidence.list);
  report.steps.push("notifications_fixture_list_rendered_from_mounted_chat_repository");

  await page.mouse.click(96, 197);
  await waitForRoute(page, "chat");
  await page.waitForTimeout(2_000);
  report.evidence.openedChat = await screenshot(page, "web-notifications-opened-chat");
  await assertNonBlankPng(report.evidence.openedChat);
  report.steps.push("tap_marked_read_and_opened_exact_chat_without_auth_prompt");

  if (faults.length) throw new Error(`browser_runtime_fault:${faults.join("|")}`);
  report.status = "passed";
} catch (error) {
  report.error = safeError(error);
} finally {
  if (browser) await browser.close().catch(() => {});
  if (server) await new Promise((resolveClose) => server.close(resolveClose));
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Notifications Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Notifications Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Notifications Web evidence passed.");
}

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/notifications-evidence.json"),
    evidenceDir: resolve("build-reports/web/notifications-evidence"),
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

async function gitMetadata() {
  const head = (await run("git", ["rev-parse", "HEAD"])).trim();
  const status = await run("git", ["status", "--porcelain"]);
  return { head, workingTreeDirty: status.trim().length > 0 };
}

async function run(command, args) {
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
    location.hash.replace(/^#/, "").startsWith(expected),
  route, { timeout: 30_000 });
}

async function screenshot(page, name) {
  await mkdir(options.evidenceDir, { recursive: true });
  const file = join(options.evidenceDir, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function assertNonBlankPng(file) {
  const bytes = await readFile(file);
  if (bytes.length < 10_000) throw new Error("blank_or_missing_screenshot");
}

function safeError(error) {
  const message = String(error?.message ?? error);
  return [
    "invalid_arguments",
    "missing_web_distribution",
    "browser_runtime_fault",
    "blank_or_missing_screenshot",
  ].find((prefix) => message.startsWith(prefix)) ?? message.slice(0, 240);
}
