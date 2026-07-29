#!/usr/bin/env node
/**
 * Hermetic browser owner for WEB-CHAT-A11Y-E2E-001. It deliberately has no real mode, remote
 * credentials or DML path; Auth/Profile's real-backend gate remains a separate executable.
 */
import { execFileSync } from "node:child_process";
import { createServer } from "node:http";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { chromium } from "playwright-core";
import {
  DISTRIBUTION_REVISION_FILE,
  assertExactDistributionRevision,
} from "./web-authenticated-browser-policy.mjs";

const TURNSTILE_BOOTSTRAP = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
const FIXTURE = Object.freeze({
  countryCode: "240",
  phone: "600000001",
  password: "fixture-only-password",
  profileId: "11111111-1111-4111-8111-111111111111",
  accessToken: "fixture.access.token",
  refreshToken: "fixture-refresh-token",
  webSessionToken: "fixture-web-session-token",
});
const options = parseArguments(process.argv.slice(2));
const report = {
  check: "WEB-CHAT-A11Y-BROWSER-02",
  mode: "hermetic_local_fixture",
  status: "failed",
  steps: [],
};
const unexpectedNetwork = [];
let server;
let browser;
let context;
let page;

try {
  report.sourceRevision = await verifyDistributionProvenance(options.distribution);
  server = await startServer(options.distribution);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: [
      "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--no-first-run",
      "--proxy-server=http://127.0.0.1:9",
      "--proxy-bypass-list=127.0.0.1;localhost",
    ],
  });
  context = await browser.newContext({ locale: "es-ES" });
  await context.route("**/*", route => {
    const url = route.request().url();
    if (url.startsWith(`${server.origin}/`)) return route.continue();
    if (url === TURNSTILE_BOOTSTRAP) {
      return route.fulfill({ status: 200, contentType: "text/javascript", body: "globalThis.turnstile={};" });
    }
    unexpectedNetwork.push(safeOrigin(url));
    return route.abort("blockedbyclient");
  });
  page = context.pages()[0] ?? await context.newPage();
  await page.goto(
    `${server.origin}/?quata-auth-e2e=1&quata-chat-e2e=1&backend=${encodeURIComponent(server.origin)}#auth`,
  );
  await page.waitForFunction(() => globalThis.__quataAuthE2eProduct?.version === 1);

  const phone = page.locator('input[aria-label="Teléfono"]');
  const password = page.locator('input[aria-label="Contraseña"]');
  const login = page.locator('button[aria-label="Entrar"]');
  await Promise.all([phone.waitFor(), password.waitFor(), login.waitFor()]);
  await phone.fill(FIXTURE.phone);
  await password.fill(FIXTURE.password);
  await login.click();
  await page.waitForFunction(() => localStorage.getItem("web.auth.session_ready") === "true");
  report.steps.push("fixture_session_ready");

  await page.evaluate(() => {
    globalThis.location.hash = "chat-local%3Aax";
  });
  await page.waitForFunction(() => localStorage.getItem("web.navigation.route")?.startsWith("chat/") === true);

  const message = page.locator('input[aria-label="Mensaje"]');
  const send = page.locator('button[aria-label="Enviar"]');
  await Promise.all([message.waitFor(), send.waitFor()]);
  await assertUniqueNativeAx(page, { role: "textbox", name: "Mensaje", selector: 'input[aria-label="Mensaje"]' });
  await assertUniqueNativeAx(page, { role: "button", name: "Enviar", selector: 'button[aria-label="Enviar"]' });
  if (await send.isEnabled()) throw new Error("native_chat_send_initial_state_changed");
  const chatMarker = "mensaje AX local";
  await message.fill(chatMarker);
  await waitFor(async () => await send.isEnabled(), "native_chat_send_enabled_state_missing");
  await send.focus();
  await assertUniqueNativeAx(page, {
    role: "button",
    name: "Enviar",
    selector: 'button[aria-label="Enviar"]',
    focused: true,
  });
  await page.keyboard.press("Enter");
  await page.waitForFunction(marker => {
    const value = globalThis.__quataChatE2eProduct;
    return value?.version === 1 && value.sends === 1 && value.text === marker;
  }, chatMarker);
  await page.waitForTimeout(250);
  const chatFixture = await page.evaluate(() => globalThis.__quataChatE2eProduct);
  if (chatFixture?.sends !== 1 || chatFixture.text !== chatMarker) {
    throw new Error("native_chat_send_callback_not_exactly_once");
  }
  if (unexpectedNetwork.length !== 0) throw new Error("unexpected_external_network");
  report.steps.push("native_chat_role_name_state_keyboard_activation_and_real_fixture_callback_once");
  report.status = "passed";
} catch (error) {
  report.error = safeError(error);
} finally {
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  await server?.close().catch(() => {});
  report.finishedAt = new Date().toISOString();
  report.network = {
    policy: "local_only",
    unexpectedOrigins: [...new Set(unexpectedNetwork)].length,
  };
  await writeSafeReport(options.output, report);
}

if (report.status !== "passed") {
  console.error(`Chat accessibility browser E2E failed: ${report.error ?? "unknown_failure"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat accessibility browser E2E passed (hermetic_local_fixture).");
}

function parseArguments(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || (process.platform === "win32"
      ? "C:/Program Files/Google/Chrome/Application/chrome.exe" : "google-chrome"),
    output: resolve("build-reports/web/chat-a11y-browser-e2e.json"),
  };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (["--dist", "--chrome", "--out"].includes(argument)) {
      const value = args[++index];
      if (!value || value.startsWith("--")) throw new Error("invalid_arguments");
      parsed[argument === "--dist" ? "distribution" : argument === "--chrome" ? "chrome" : "output"] = resolve(value);
    } else throw new Error("invalid_arguments");
  }
  return parsed;
}

async function startServer(distribution) {
  if (!(await stat(distribution).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  let origin;
  const localServer = createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? "/", "http://localhost");
      if (url.pathname === "/functions/v1/quata-auth-bridge") {
        const body = await jsonBody(request);
        if (
          request.method !== "POST" ||
          body.action !== "web_login" ||
          body.country_code !== FIXTURE.countryCode ||
          body.phone_local !== FIXTURE.phone ||
          body.password !== FIXTURE.password
        ) {
          return json(response, 401, { error: "invalid_fixture_login" });
        }
        return json(response, 200, {
          profile: { id: FIXTURE.profileId, display_name: "Fixture User" },
          user: { id: "22222222-2222-4222-8222-222222222222" },
          session: {
            access_token: FIXTURE.accessToken,
            refresh_token: FIXTURE.refreshToken,
            expires_at: Math.floor(Date.now() / 1000) + 3600,
          },
          web_session: { token: FIXTURE.webSessionToken },
        });
      }
      if (url.pathname.startsWith("/rest/v1/")) {
        if (request.method !== "GET") return json(response, 405, { error: "fixture_product_mutation_forbidden" });
        return json(response, 200, []);
      }
      if (url.pathname === "/favicon.ico") return response.writeHead(204).end();
      const pathname = decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname);
      const file = resolve(distribution, `.${pathname}`);
      const rel = relative(distribution, file);
      if (rel.startsWith(`..${sep}`) || rel === ".." || isAbsolute(rel)) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      let content = await readFile(file);
      if (pathname === "/index.html") {
        content = Buffer.from(content.toString("utf8")
          .replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${origin}"`)
          .replace('name="quata-supabase-publishable-key" content=""', 'name="quata-supabase-publishable-key" content="fixture-public-key"'));
      }
      response.writeHead(200, {
        "Content-Type": contentType(file),
        "Cache-Control": "no-store",
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
      }).end(content);
    } catch {
      response.writeHead(500).end();
    }
  });
  await new Promise((resolveServer, reject) => {
    localServer.once("error", reject);
    localServer.listen(0, "127.0.0.1", resolveServer);
  });
  origin = `http://127.0.0.1:${localServer.address().port}`;
  return {
    origin,
    close: () => new Promise((resolveServer, reject) =>
      localServer.close(error => error ? reject(error) : resolveServer())),
  };
}

async function verifyDistributionProvenance(distribution) {
  const repositoryRoot = resolve(import.meta.dirname, "..");
  const repositoryRevision = execFileSync("git", ["rev-parse", "HEAD"], {
    cwd: repositoryRoot,
    encoding: "utf8",
  }).trim();
  const trackedChanges = execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], {
    cwd: repositoryRoot,
    encoding: "utf8",
  });
  const markerRevision = await readFile(join(distribution, DISTRIBUTION_REVISION_FILE), "utf8")
    .then(value => value.trim())
    .catch(() => "");
  return assertExactDistributionRevision({ repositoryRevision, markerRevision, trackedChanges });
}

async function assertUniqueNativeAx(page, { role, name, selector, focused = false }) {
  const locator = page.locator(selector);
  if (await locator.count() !== 1) throw new Error(`native_ax_selector_not_unique_${role}`);
  const box = await locator.boundingBox();
  if (!box || box.width <= 0 || box.height <= 0) throw new Error(`native_ax_not_visible_${role}`);
  const client = await page.context().newCDPSession(page);
  try {
    const { nodes } = await client.send("Accessibility.getFullAXTree");
    const matches = nodes.filter(node => !node.ignored && node.role?.value === role && node.name?.value === name);
    if (matches.length !== 1) throw new Error(`native_ax_role_name_not_unique_${role}`);
    if (focused && !matches[0].properties?.some(property =>
      property.name === "focused" && property.value?.value === true)) {
      throw new Error(`native_ax_focus_missing_${role}`);
    }
  } finally {
    await client.detach();
  }
}

async function waitFor(predicate, failureCode, timeoutMs = 5_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await predicate()) return;
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error(failureCode);
}

async function jsonBody(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
  } catch {
    return {};
  }
}

function json(response, status, value) {
  const body = value == null ? "" : JSON.stringify(value);
  response.writeHead(status, {
    "content-type": "application/json",
    "cache-control": "no-store",
  }).end(body);
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

function safeOrigin(url) {
  try {
    return new URL(url).origin;
  } catch {
    return "invalid-origin";
  }
}

function safeError(error) {
  const value = typeof error?.message === "string" ? error.message : "";
  return [
    "invalid_arguments",
    "distribution_missing",
    "distribution_revision_missing_or_invalid",
    "distribution_revision_mismatch",
    "distribution_source_tree_dirty",
    "native_chat_send_initial_state_changed",
    "native_chat_send_enabled_state_missing",
    "native_chat_send_callback_not_exactly_once",
    "native_ax_selector_not_unique",
    "native_ax_not_visible",
    "native_ax_role_name_not_unique",
    "native_ax_focus_missing",
    "unexpected_external_network",
  ].find(code => value.startsWith(code)) ?? "chat_a11y_browser_failure";
}

async function writeSafeReport(path, value) {
  const target = resolve(path);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(value, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
}
