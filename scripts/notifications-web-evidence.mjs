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

  await page.goto(`${server.origin}/?quata-chat-e2e=1&quata-auth-e2e=1#notifications`);
  await waitForRoute(page, "notifications");
  await page.waitForTimeout(2_000);
  report.evidence.list = await screenshot(page, "web-notifications-list");
  await assertNonBlankPng(report.evidence.list);
  report.steps.push("notifications_fixture_list_rendered_from_mounted_chat_repository");

  await clickVisibleText(page, /Chat de prueba/);
  report.observedConversation = await waitForExactPendingConversation(page, "local:ax");
  await page.waitForTimeout(2_000);
  report.evidence.authGate = await screenshot(page, "web-notifications-exact-chat-auth-gate");
  await assertNonBlankPng(report.evidence.authGate);
  report.steps.push("notification_requested_exact_fixture_conversation");
  report.steps.push("anonymous_private_conversation_showed_auth_prompt");

  await dismissAuthenticationPrompt(page);
  await page.evaluate(() => { location.hash = "notifications"; });
  await waitForRoute(page, "notifications");
  await page.getByText(/Aún no hay avisos/).waitFor({ state: "visible", timeout: 30_000 });
  if (await page.getByText(/Chat de prueba/).count() !== 0) {
    throw new Error("read_notification_still_visible");
  }
  report.evidence.readState = await screenshot(page, "web-notifications-read-empty");
  await assertNonBlankPng(report.evidence.readState);
  report.steps.push("notification_marked_read_and_removed_from_list");

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

async function waitForExactPendingConversation(page, conversationId) {
  const expectedFragment = `chat-${encodeURIComponent(conversationId)}`;
  await page.waitForFunction((expected) =>
    location.hash === "" &&
    localStorage.getItem("web.navigation.route") === "feed" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "feed" &&
    document.documentElement.getAttribute("data-quata-auth-required-prompt") === "visible" &&
    document.documentElement.getAttribute("data-quata-auth-pending-route") === expected &&
    globalThis.__quataAuthGateE2eProduct?.version === 1,
  expectedFragment, { timeout: 30_000 });
  return await page.evaluate(() => ({
    route: localStorage.getItem("web.navigation.route"),
    shellRoute: document.documentElement.getAttribute("data-quata-shell-route"),
    hash: location.hash,
    authPrompt: document.documentElement.getAttribute("data-quata-auth-required-prompt"),
    pendingRoute: document.documentElement.getAttribute("data-quata-auth-pending-route"),
  }));
}

async function dismissAuthenticationPrompt(page) {
  await page.evaluate(() => {
    const bridge = globalThis.__quataAuthGateE2eProduct;
    if (bridge?.version !== 1 || typeof bridge.dismiss !== "function") {
      throw new Error("compose_auth_gate_dismiss_missing");
    }
    bridge.dismiss();
  });
  await page.waitForFunction(() =>
    !document.documentElement.hasAttribute("data-quata-auth-required-prompt") &&
    !document.documentElement.hasAttribute("data-quata-auth-pending-route"));
}

async function clickVisibleText(page, pattern) {
  const locator = page.getByText(pattern).first();
  await locator.waitFor({ state: "visible", timeout: 30_000 });
  const box = await locator.boundingBox();
  if (!box || box.width <= 0 || box.height <= 0) throw new Error("visible_notification_bounds_missing");
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
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
    "visible_notification_bounds_missing",
    "read_notification_still_visible",
    "compose_auth_gate_dismiss_missing",
  ].find((prefix) => message.startsWith(prefix)) ?? message.slice(0, 240);
}
