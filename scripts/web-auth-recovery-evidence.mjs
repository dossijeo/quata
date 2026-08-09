#!/usr/bin/env node
import { createServer } from "node:http";
import { spawnSync } from "node:child_process";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, isAbsolute, relative, resolve, sep } from "node:path";
import { chromium } from "playwright-core";

const FIXTURE = Object.freeze({
  countryCode: "240",
  phone: "600000001",
  missingPhone: "699999999",
  secretQuestion: "Nombre de tu primer barrio",
  secretAnswer: "Bata",
  newPassword: "fixture-reset-21085800",
});
const TURNSTILE_BOOTSTRAP = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

const options = parseArguments(process.argv.slice(2));
const report = {
  check: "WEB-AUTH-RECOVERY-001",
  mode: "hermetic_local_fixture",
  status: "failed",
  revision: repositoryRevision(),
  steps: [],
  fixture: {
    countryCode: FIXTURE.countryCode,
    phone: FIXTURE.phone,
    missingPhone: FIXTURE.missingPhone,
  },
};
const state = { questionReads: 0, missingReads: 0, resets: 0 };
const unexpectedNetwork = [];
let server;
let browser;
let context;
let page;

try {
  server = await startServer(options.distribution, state);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: [
      "--no-sandbox",
      "--disable-dev-shm-usage",
      "--disable-gpu",
      "--no-first-run",
      "--proxy-server=http://127.0.0.1:9",
      "--proxy-bypass-list=127.0.0.1;localhost",
    ],
  });
  context = await browser.newContext({ locale: "es-ES", viewport: { width: 390, height: 844 } });
  await context.route("**/*", route => {
    const url = route.request().url();
    if (url.startsWith(`${server.origin}/`)) return route.continue();
    if (url === TURNSTILE_BOOTSTRAP) {
      return route.fulfill({ status: 200, contentType: "text/javascript", body: "globalThis.turnstile={};" });
    }
    unexpectedNetwork.push(safeOrigin(url));
    return route.abort("blockedbyclient");
  });
  page = await context.newPage();
  await page.goto(`${server.origin}/?quata-auth-e2e=1&backend=${encodeURIComponent(server.origin)}#auth`);
  await page.waitForFunction(() => globalThis.__quataAuthE2eProduct?.version === 1);
  await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-auth-destination") === "login");
  report.steps.push("common_login_surface_mounted");

  await page.evaluate(() => globalThis.__quataAuthE2eProduct.openRecovery());
  await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-auth-destination") === "recovery");
  report.steps.push("common_recovery_surface_mounted");
  await page.screenshot({ path: screenshotPath("recovery-mounted"), fullPage: true });

  const missing = await page.evaluate(async ({ countryCode, phone }) =>
    globalThis.__quataAuthE2eProduct.recoveryQuestion(countryCode, phone),
    { countryCode: FIXTURE.countryCode, phone: FIXTURE.missingPhone },
  );
  if (missing !== "") throw new Error("missing_account_question_not_empty");
  report.steps.push("missing_account_returns_no_question");

  const question = await page.evaluate(async ({ countryCode, phone }) =>
    globalThis.__quataAuthE2eProduct.recoveryQuestion(countryCode, phone),
    { countryCode: FIXTURE.countryCode, phone: FIXTURE.phone },
  );
  if (question !== FIXTURE.secretQuestion) throw new Error("fixture_question_mismatch");
  report.steps.push("registered_account_question_returned");

  const reset = await page.evaluate(async payload =>
    globalThis.__quataAuthE2eProduct.resetPassword(
      payload.countryCode,
      payload.phone,
      payload.secretAnswer,
      payload.newPassword,
    ),
    {
      countryCode: FIXTURE.countryCode,
      phone: FIXTURE.phone,
      secretAnswer: FIXTURE.secretAnswer,
      newPassword: FIXTURE.newPassword,
    },
  );
  if (reset !== "password_reset") throw new Error("fixture_reset_result_mismatch");
  report.steps.push("reset_password_uses_product_repository_bridge");

  await page.evaluate(() => globalThis.__quataAuthE2eProduct.openLogin());
  await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-auth-destination") === "login");
  report.steps.push("recovery_return_to_login");
  await page.screenshot({ path: screenshotPath("login-return"), fullPage: true });

  if (state.questionReads !== 1 || state.missingReads !== 1 || state.resets !== 1) {
    throw new Error("fixture_recovery_journey_incomplete");
  }
  if (unexpectedNetwork.length > 0) throw new Error("unexpected_external_network");
  report.network = { policy: "local_only", unexpectedOrigins: 0 };
  report.backend = { questionReads: state.questionReads, missingReads: state.missingReads, resets: state.resets };
  report.status = "passed";
} catch (error) {
  report.error = safeError(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  if (page) {
    report.browserState = await page.evaluate(() => ({
      bridge: globalThis.__quataAuthE2eProduct?.version ?? null,
      destination: document.documentElement.getAttribute("data-quata-auth-destination"),
      route: localStorage.getItem("web.navigation.route"),
      rootPresent: document.querySelector("#quata-root") !== null,
      rootChildren: document.querySelector("#quata-root")?.childElementCount ?? 0,
      shadowChildren: document.querySelector("#quata-root")?.shadowRoot?.childElementCount ?? 0,
    })).catch(() => ({ unavailable: true }));
  }
} finally {
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  await server?.close().catch(() => {});
  report.finishedAt = new Date().toISOString();
  report.network ??= { policy: "local_only", unexpectedOrigins: [...new Set(unexpectedNetwork)].length };
  await writeReport(options.output, report);
}

if (report.status !== "passed") {
  console.error(`Web Auth Recovery evidence failed: ${report.error ?? "unknown_failure"}.`);
  process.exitCode = 1;
} else {
  console.log("Web Auth Recovery evidence passed.");
}

function parseArguments(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || (process.platform === "win32"
      ? "C:/Program Files/Google/Chrome/Application/chrome.exe"
      : "google-chrome"),
    output: resolve("build-reports/web/auth-recovery-evidence.json"),
  };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--dist" || argument === "--chrome" || argument === "--out") {
      const value = args[++index];
      if (!value || value.startsWith("--")) throw new Error("invalid_arguments");
      parsed[argument === "--dist" ? "distribution" : argument === "--chrome" ? "chrome" : "output"] = resolve(value);
    } else if (argument === "--help" || argument === "-h") {
      console.log("Usage: node scripts/web-auth-recovery-evidence.mjs [--dist DIR] [--chrome PATH] [--out REPORT]");
      process.exit(0);
    } else {
      throw new Error("invalid_arguments");
    }
  }
  return parsed;
}

async function startServer(distribution, state) {
  if (!(await stat(distribution).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  let origin;
  const server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? "/", "http://localhost");
      if (url.pathname === "/functions/v1/quata-auth-bridge") {
        const body = await jsonBody(request);
        if (request.method !== "POST") return json(response, 405, { error: "fixture_method_forbidden" });
        if (body.action === "recovery_question") {
          if (body.country_code !== FIXTURE.countryCode) return json(response, 400, { error: "invalid_country" });
          if (body.phone_local === FIXTURE.missingPhone) {
            state.missingReads += 1;
            return json(response, 200, {});
          }
          if (body.phone_local !== FIXTURE.phone) return json(response, 404, { error: "recovery_profile_not_found" });
          state.questionReads += 1;
          return json(response, 200, { secret_question: FIXTURE.secretQuestion });
        }
        if (body.action === "reset_password") {
          if (
            body.country_code !== FIXTURE.countryCode ||
            body.phone_local !== FIXTURE.phone ||
            body.secret_answer !== FIXTURE.secretAnswer ||
            body.new_password !== FIXTURE.newPassword
          ) {
            return json(response, 401, { error: "invalid_recovery_secret" });
          }
          state.resets += 1;
          return json(response, 200, { ok: true });
        }
        return json(response, 405, { error: "fixture_auth_action_forbidden" });
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
          .replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${escapeHtml(origin)}"`)
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
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolveServer);
  });
  origin = `http://127.0.0.1:${server.address().port}`;
  return {
    origin,
    close: () => new Promise((resolveServer, reject) => server.close(error => error ? reject(error) : resolveServer())),
  };
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
  response.writeHead(status, { "content-type": "application/json", "cache-control": "no-store" }).end(body);
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

function screenshotPath(name) {
  const parsed = resolve(options.output);
  return parsed.replace(/\.json$/i, `-${name}.png`);
}

async function writeReport(path, value) {
  const target = resolve(path);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(value, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`Web Auth Recovery report written: ${target}`);
}

function repositoryRevision() {
  const result = spawnSync("git", ["rev-parse", "HEAD"], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
  return result.status === 0 ? result.stdout.trim() : null;
}

function escapeHtml(value) {
  return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

function safeOrigin(url) {
  try {
    return new URL(url).origin;
  } catch {
    return "invalid-origin";
  }
}

function safeError(error) {
  const value = typeof error?.message === "string" ? error.message : String(error ?? "");
  return [
    "invalid_arguments",
    "distribution_missing",
    "missing_account_question_not_empty",
    "fixture_question_mismatch",
    "fixture_reset_result_mismatch",
    "fixture_recovery_journey_incomplete",
    "unexpected_external_network",
  ].find(code => value.startsWith(code)) ?? "web_auth_recovery_evidence_failure";
}
