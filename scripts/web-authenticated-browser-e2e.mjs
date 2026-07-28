#!/usr/bin/env node
/**
 * Real-browser Auth journey. Fixture mode is the default and is fully hermetic. Remote mode is
 * explicit, uses an already-provisioned account, and cannot pass until its issued sessions have
 * been globally revoked and that revocation has been verified.
 */
import { createServer } from "node:http";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { chromium } from "playwright-core";
import {
  assertExplicitRefreshTokenRejection,
  isPublicSupabaseKey,
} from "./web-authenticated-browser-security.mjs";

const TURNSTILE_BOOTSTRAP = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
const STORAGE_KEYS = [
  "quata_web_access_token", "quata_web_refresh_token", "quata_web_session_token",
  "quata_web_user_id", "quata_web_expires_at", "web.auth.session_ready",
];
const REAL_OPT_IN = "I_ACCEPT_SESSION_REVOCATION";
const FIXTURE = Object.freeze({
  // LoginUiState defaults to this prefix; the native Web input deliberately owns
  // just the local phone number, as the Compose field did before it.
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
  check: "WEB-AUTH-BROWSER-02",
  mode: options.real ? "real_existing_account_opt_in" : "hermetic_local_fixture",
  status: "failed",
  steps: [],
  cleanup: { state: "not_started" },
};
const fixtureState = { login: 0, profileReads: 0, webLogout: 0, globalLogout: 0 };
const unexpectedNetwork = [];
let server;
let browser;
let context;
let page;
let cleanupSession;
let backend;
let stage = "initializing";
const browserDiagnostics = [];

try {
  const configuration = loadConfiguration(options.real);
  server = await startServer(options.distribution, fixtureState, configuration);
  backend = options.real ? configuration.baseUrl : server.origin;
  const credentials = options.real ? configuration : FIXTURE;

  stage = "launching_browser";
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: [
      "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--no-first-run",
      ...(options.real ? [] : [
        "--proxy-server=http://127.0.0.1:9",
        "--proxy-bypass-list=127.0.0.1;localhost",
      ]),
    ],
  });
  context = await browser.newContext({ locale: "es-ES" });
  await context.route("**/*", async route => {
    const url = route.request().url();
    if (url.startsWith(`${server.origin}/`)) return route.continue();
    if (url === TURNSTILE_BOOTSTRAP) {
      return route.fulfill({ status: 200, contentType: "text/javascript", body: "globalThis.turnstile={};" });
    }
    if (options.real && url === `${backend}/functions/v1/quata-auth-bridge` &&
        route.request().method() === "POST") {
      const response = await route.fetch();
      const body = await response.body();
      if (response.ok()) {
        const captured = sessionFromLoginPayload(body);
        if (captured) cleanupSession = captured;
      }
      return route.fulfill({ response, body });
    }
    if (options.real && url.startsWith(`${backend}/`)) return route.continue();
    unexpectedNetwork.push(safeOrigin(url));
    return route.abort("blockedbyclient");
  });
  context.on("request", request => {
    const url = request.url();
    if (!url.startsWith(`${server.origin}/`) && url !== TURNSTILE_BOOTSTRAP &&
        !(options.real && url.startsWith(`${backend}/`))) {
      unexpectedNetwork.push(safeOrigin(url));
    }
  });
  page = context.pages()[0] ?? await context.newPage();
  page.on("console", message => browserDiagnostics.push(`console:${message.type()}:${message.text()}`));
  page.on("pageerror", error => browserDiagnostics.push(`pageerror:${error.stack ?? error.message}`));

  stage = "mounting_real_product";
  await page.goto(`${server.origin}/?quata-auth-e2e=1&backend=${encodeURIComponent(backend)}#auth`);
  await page.waitForFunction(() => {
    const root = document.querySelector("#quata-root");
    return globalThis.__quataAuthE2eProduct?.version === 1 && root &&
      (root.childElementCount > 0 || (root.shadowRoot?.childElementCount ?? 0) > 0);
  });
  report.steps.push("real_compose_auth_shell_mounted");

  stage = "native_login_controls";
  const phone = page.locator('input[aria-label="Teléfono"]');
  const password = page.locator('input[aria-label="Contraseña"]');
  const login = page.locator('button[aria-label="Entrar"]');
  await Promise.all([phone.waitFor(), password.waitFor(), login.waitFor()]);
  await assertUniqueNativeAx(page, { role: "textbox", name: "Teléfono", selector: 'input[aria-label="Teléfono"]' });
  await assertUniqueNativeAx(page, { role: "textbox", name: "Contraseña", selector: 'input[aria-label="Contraseña"]' });
  await assertUniqueNativeAx(page, { role: "button", name: "Entrar", selector: 'button[aria-label="Entrar"]' });
  await phone.fill(credentials.phone);
  await password.fill(credentials.password);
  await waitFor(async () => await login.isEnabled(), "native_login_submit_disabled");
  await password.focus();
  await page.keyboard.press("Tab");
  if (!(await login.evaluate(node => node.getRootNode().activeElement === node))) throw new Error("native_login_focus_missing");
  await assertUniqueNativeAx(page, { role: "button", name: "Entrar", selector: 'button[aria-label="Entrar"]', focused: true });
  await page.keyboard.press("Enter");
  await page.waitForFunction(() => localStorage.getItem("web.auth.session_ready") === "true");
  cleanupSession = await readSession(page);
  assertCompleteSession(cleanupSession);
  report.steps.push("native_login_role_name_focus_keyboard_activation");

  stage = "browser_restart_restore";
  await page.reload();
  await page.waitForFunction(() => globalThis.__quataAuthE2eProduct?.version === 1);
  await page.evaluate(() => globalThis.__quataAuthE2eProduct.restore());
  await page.goto(`${server.origin}/?quata-auth-e2e=1&backend=${encodeURIComponent(backend)}#feed`);
  await page.waitForFunction(() => {
    const root = document.querySelector("#quata-root");
    return localStorage.getItem("web.auth.session_ready") === "true" && root &&
      (root.childElementCount > 0 || (root.shadowRoot?.childElementCount ?? 0) > 0);
  });
  report.steps.push("product_session_restored_after_reload");

  await page.goto(`${server.origin}/?quata-auth-e2e=1&backend=${encodeURIComponent(backend)}#chat-local%3Aax`);

  stage = "authenticated_profile_read";
  const profileRead = await page.evaluate(async ({ backend, key, profileId }) => {
    const response = await fetch(`${backend}/rest/v1/community_profiles?select=id&id=eq.${profileId}`, {
      headers: {
        apikey: key,
        authorization: `Bearer ${localStorage.getItem("quata_web_access_token")}`,
      },
    });
    const rows = response.ok ? await response.json() : [];
    return { ok: response.ok, exact: rows.length === 1 && rows[0]?.id === profileId };
  }, { backend, key: credentials.publishableKey ?? "fixture-public-key", profileId: cleanupSession.profileId });
  if (!profileRead.ok || !profileRead.exact) throw new Error("authenticated_profile_read_failed");
  report.steps.push("authenticated_browser_profile_read");

  stage = "native_chat_controls";
  const message = page.locator('input[aria-label="Mensaje"]');
  const send = page.locator('button[aria-label="Enviar"]');
  await Promise.all([message.waitFor(), send.waitFor()]);
  await assertUniqueNativeAx(page, { role: "textbox", name: "Mensaje", selector: 'input[aria-label="Mensaje"]' });
  await assertUniqueNativeAx(page, { role: "button", name: "Enviar", selector: 'button[aria-label="Enviar"]' });
  if (await send.isEnabled()) throw new Error("native_chat_send_initial_state_changed");
  await message.fill("mensaje AX local");
  await waitFor(async () => await send.isEnabled(), "native_chat_send_enabled_state_missing");
  await send.focus();
  await assertUniqueNativeAx(page, { role: "button", name: "Enviar", selector: 'button[aria-label="Enviar"]', focused: true });
  await page.keyboard.press("Enter");
  report.steps.push("native_chat_role_name_state_keyboard_activation");

  stage = "native_logout";
  const logout = page.locator('button[aria-label="Cerrar sesión"]');
  await assertUniqueNativeAx(page, { role: "button", name: "Cerrar sesión", selector: 'button[aria-label="Cerrar sesión"]' });
  await logout.focus();
  if (!(await logout.evaluate(node => node.getRootNode().activeElement === node))) throw new Error("native_logout_focus_missing");
  await assertUniqueNativeAx(page, { role: "button", name: "Cerrar sesión", selector: 'button[aria-label="Cerrar sesión"]', focused: true });
  await page.keyboard.press("Space");
  await page.waitForFunction(() => localStorage.getItem("web.auth.session_ready") !== "true");
  if ((await page.evaluate(keys => keys.some(key => localStorage.getItem(key) !== null), STORAGE_KEYS))) {
    throw new Error("product_logout_storage_remains");
  }
  report.steps.push("native_logout_role_name_focus_keyboard_activation");

  stage = "global_session_cleanup";
  await revokeAndVerify(backend, credentials.publishableKey ?? "fixture-public-key", cleanupSession);
  cleanupSession = null;
  report.cleanup = { state: "sessions_revoked_and_verified" };

  if (!options.real) {
    if (fixtureState.login !== 1 || fixtureState.profileReads < 1 ||
        fixtureState.webLogout !== 1 || fixtureState.globalLogout !== 1) {
      throw new Error("fixture_journey_incomplete");
    }
    if (unexpectedNetwork.length !== 0) throw new Error("unexpected_external_network");
  }
  report.status = "passed";
} catch (error) {
  report.error = safeError(error);
  report.failureStage = stage;
  if (page) {
    report.browserState = await page.evaluate(() => ({
      productBridge: globalThis.__quataAuthE2eProduct?.version === 1,
      rootPresent: document.querySelector("#quata-root") !== null,
      canvasCount: document.querySelectorAll("#quata-root canvas").length,
      rootChildren: document.querySelector("#quata-root")?.childElementCount ?? 0,
      shadowChildren: document.querySelector("#quata-root")?.shadowRoot?.childElementCount ?? 0,
      nativeControls: Array.from(document.querySelector("#quata-root")?.shadowRoot?.querySelectorAll("input, button") ?? [])
        .map(element => {
          const rect = element.getBoundingClientRect();
          return { tag: element.tagName, name: element.getAttribute("aria-label"), type: element.getAttribute("type"), disabled: element.disabled, rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height } };
        }),
      shadowText: document.querySelector("#quata-root")?.shadowRoot?.innerText?.slice(0, 2_000) ?? "",
      hash: location.hash,
    })).catch(() => ({ unavailable: true }));
    report.browserState.diagnostics = browserDiagnostics.slice(-20);
  }
} finally {
  if (cleanupSession && backend) {
    try {
      const key = options.real ? process.env.QUATA_SUPABASE_PUBLISHABLE_KEY?.trim() : "fixture-public-key";
      await revokeAndVerify(backend, key, cleanupSession);
      report.cleanup = { state: "sessions_revoked_and_verified_after_failure" };
      cleanupSession = null;
    } catch {
      report.cleanup = { state: "revocation_unverified", action: "revoke_existing_test_account_sessions" };
      report.status = "failed";
    }
  }
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  await server?.close().catch(() => {});
  report.finishedAt = new Date().toISOString();
  report.network = options.real ? { policy: "local_and_exact_configured_backend" } : { policy: "local_only", unexpectedOrigins: [...new Set(unexpectedNetwork)].length };
  await writeSafeReport(options.output, report);
}

if (report.status !== "passed") {
  console.error(`Authenticated browser E2E failed: ${report.error ?? "unknown_failure"}.`);
  process.exitCode = 1;
} else {
  console.log(`Authenticated browser E2E passed (${report.mode}).`);
}

function parseArguments(args) {
  const parsed = {
    real: false,
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || (process.platform === "win32"
      ? "C:/Program Files/Google/Chrome/Application/chrome.exe" : "google-chrome"),
    output: resolve("build-reports/web/authenticated-browser-e2e.json"),
  };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--real") parsed.real = true;
    else if (["--dist", "--chrome", "--out"].includes(argument)) {
      const value = args[++index];
      if (!value || value.startsWith("--")) throw new Error("invalid_arguments");
      parsed[argument === "--dist" ? "distribution" : argument === "--chrome" ? "chrome" : "output"] = resolve(value);
    } else if (argument === "--help" || argument === "-h") {
      console.log("Usage: node scripts/web-authenticated-browser-e2e.mjs [--real] [--dist DIR] [--chrome PATH] [--out REPORT]");
      process.exit(0);
    } else throw new Error("invalid_arguments");
  }
  return parsed;
}

function loadConfiguration(real) {
  if (!real) return {};
  if (process.env.QUATA_AUTH_E2E_REAL_OPT_IN !== REAL_OPT_IN) throw new Error("real_mode_opt_in_required");
  const required = [
    "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
    "QUATA_E2E_COUNTRY_CODE", "QUATA_E2E_PHONE", "QUATA_E2E_PASSWORD",
  ];
  if (required.some(name => !process.env[name]?.trim())) throw new Error("real_mode_environment_missing");
  const baseUrl = process.env.QUATA_SUPABASE_URL.trim().replace(/\/+$/, "");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("invalid_public_supabase_url");
  const publishableKey = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY.trim();
  if (!isPublicSupabaseKey(publishableKey)) throw new Error("privileged_or_invalid_publishable_key");
  return {
    baseUrl, publishableKey,
    countryCode: process.env.QUATA_E2E_COUNTRY_CODE.trim(),
    phone: process.env.QUATA_E2E_PHONE.trim(),
    password: process.env.QUATA_E2E_PASSWORD,
  };
}

async function startServer(distribution, state, configuration) {
  if (!(await stat(distribution).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  let origin;
  const server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? "/", "http://localhost");
      if (url.pathname === "/functions/v1/quata-auth-bridge") {
        const body = await jsonBody(request);
        if (request.method !== "POST" || body.action !== "web_login" ||
            body.country_code !== FIXTURE.countryCode || body.phone_local !== FIXTURE.phone ||
            body.password !== FIXTURE.password || typeof body.client_instance_id !== "string") {
          return json(response, 401, { error: "invalid_fixture_login" });
        }
        state.login += 1;
        return json(response, 200, {
          profile: { id: FIXTURE.profileId, display_name: "Fixture User" },
          user: { id: "22222222-2222-4222-8222-222222222222" },
          session: {
            access_token: FIXTURE.accessToken, refresh_token: FIXTURE.refreshToken,
            expires_at: Math.floor(Date.now() / 1000) + 3600,
          },
          web_session: { token: FIXTURE.webSessionToken },
        });
      }
      if (url.pathname === "/functions/v1/quata-web-push") {
        const body = await jsonBody(request);
        if (body.action === "logout" && request.headers.authorization === `Bearer ${FIXTURE.accessToken}` &&
            request.headers["x-quata-web-session"] === FIXTURE.webSessionToken) state.webLogout += 1;
        return json(response, 200, { ok: true });
      }
      if (url.pathname === "/auth/v1/logout") {
        if (request.headers.authorization === `Bearer ${FIXTURE.accessToken}`) state.globalLogout += 1;
        return json(response, 204, null);
      }
      if (url.pathname === "/auth/v1/user") {
        return json(response, state.globalLogout > 0 ? 401 : 200, state.globalLogout > 0 ? { error: "revoked" } : { id: FIXTURE.profileId });
      }
      if (url.pathname === "/auth/v1/token" && url.searchParams.get("grant_type") === "refresh_token") {
        return json(response, state.globalLogout > 0 ? 400 : 200, state.globalLogout > 0
          ? { error: "refresh_token_revoked" }
          : { access_token: FIXTURE.accessToken, refresh_token: FIXTURE.refreshToken, expires_in: 3600 });
      }
      if (url.pathname === "/rest/v1/community_profiles") {
        state.profileReads += 1;
        return json(response, 200, [{ id: FIXTURE.profileId }]);
      }
      if (url.pathname.startsWith("/rest/v1/") || url.pathname.startsWith("/rest/v1/rpc/")) return json(response, 200, []);
      if (url.pathname === "/favicon.ico") return response.writeHead(204).end();

      const pathname = decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname);
      const file = resolve(distribution, `.${pathname}`);
      const rel = relative(distribution, file);
      if (rel.startsWith(`..${sep}`) || rel === ".." || isAbsolute(rel)) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      let content = await readFile(file);
      if (pathname === "/index.html") {
        const backendFromQuery = url.searchParams.get("backend") || origin;
        const publishableKey = configuration.publishableKey || "fixture-public-key";
        content = Buffer.from(content.toString("utf8")
          .replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${escapeHtml(backendFromQuery)}"`)
          .replace('name="quata-supabase-publishable-key" content=""', `name="quata-supabase-publishable-key" content="${escapeHtml(publishableKey)}"`));
      }
      response.writeHead(200, {
        "Content-Type": contentType(file), "Cache-Control": "no-store",
        "Cross-Origin-Opener-Policy": "same-origin", "Cross-Origin-Embedder-Policy": "require-corp",
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

async function readSession(page) {
  return page.evaluate(() => ({
    accessToken: localStorage.getItem("quata_web_access_token"),
    refreshToken: localStorage.getItem("quata_web_refresh_token"),
    webSessionToken: localStorage.getItem("quata_web_session_token"),
    profileId: localStorage.getItem("quata_web_user_id"),
  }));
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
    if (focused && !matches[0].properties?.some(property => property.name === "focused" && property.value?.value === true)) {
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
function assertCompleteSession(session) {
  if (!session?.accessToken || !session.refreshToken || !session.webSessionToken ||
      !/^[0-9a-f-]{36}$/i.test(session.profileId ?? "")) throw new Error("product_session_incomplete");
}

function sessionFromLoginPayload(body) {
  try {
    const payload = JSON.parse(body.toString("utf8"));
    const session = {
      accessToken: payload?.session?.access_token,
      refreshToken: payload?.session?.refresh_token,
      webSessionToken: payload?.web_session?.token,
      profileId: payload?.profile?.id,
    };
    assertCompleteSession(session);
    return session;
  } catch {
    return null;
  }
}
async function revokeAndVerify(baseUrl, key, session) {
  const headers = { apikey: key, authorization: `Bearer ${session.accessToken}`, "content-type": "application/json" };
  const logout = await fetch(`${baseUrl}/auth/v1/logout?scope=global`, {
    method: "POST", headers, signal: AbortSignal.timeout(20_000),
  });
  if (!logout.ok) throw new Error("global_session_revocation_failed");
  const verification = await fetch(`${baseUrl}/auth/v1/token?grant_type=refresh_token`, {
    method: "POST",
    headers: { apikey: key, "content-type": "application/json" },
    body: JSON.stringify({ refresh_token: session.refreshToken }),
    signal: AbortSignal.timeout(20_000),
  });
  const verificationBody = await verification.text();
  assertExplicitRefreshTokenRejection(verification.status, verificationBody);
}
async function jsonBody(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  try { return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}"); } catch { return {}; }
}
function json(response, status, value) {
  const body = value == null ? "" : JSON.stringify(value);
  response.writeHead(status, { "content-type": "application/json", "cache-control": "no-store" }).end(body);
}
function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"],
    [".webp", "image/webp"], [".png", "image/png"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}
function escapeHtml(value) {
  return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}
function safeOrigin(url) {
  try { return new URL(url).origin; } catch { return "invalid-origin"; }
}
function safeError(error) {
  const value = typeof error?.message === "string" ? error.message : "";
  return [
    "invalid_arguments", "distribution_missing", "real_mode_opt_in_required",
    "real_mode_environment_missing", "invalid_public_supabase_url", "privileged_key_forbidden",
    "product_session_incomplete", "authenticated_profile_read_failed", "product_logout_storage_remains",
    "native_login_submit_disabled", "native_login_focus_missing", "native_chat_send_initial_state_changed", "native_chat_send_enabled_state_missing", "native_logout_focus_missing",
    "native_ax_selector_not_unique", "native_ax_not_visible", "native_ax_role_name_not_unique", "native_ax_focus_missing",
    "fixture_journey_incomplete", "unexpected_external_network", "global_session_revocation_failed",
    "global_session_revocation_unverified",
  ].find(code => value.startsWith(code)) ?? "browser_auth_e2e_failure";
}
async function writeSafeReport(path, value) {
  const target = resolve(path);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(value, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`Authenticated browser report written: ${target}`);
}
