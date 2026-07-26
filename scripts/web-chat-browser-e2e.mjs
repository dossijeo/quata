#!/usr/bin/env node
import { chromium } from "playwright-core";
import { createServer } from "node:http";
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { setTimeout as delay } from "node:timers/promises";
import { recordLogicalCleanupFailure } from "./web-chat-browser-e2e-report.mjs";

const required = [
  "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_CHAT_A_COUNTRY_CODE", "QUATA_E2E_CHAT_A_PHONE", "QUATA_E2E_CHAT_A_PASSWORD",
  "QUATA_E2E_CHAT_B_COUNTRY_CODE", "QUATA_E2E_CHAT_B_PHONE", "QUATA_E2E_CHAT_B_PASSWORD",
  "QUATA_E2E_CHAT_A_E2E_SCOPE", "QUATA_E2E_CHAT_B_E2E_SCOPE", "QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP",
];
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: "build-reports/web/chat-browser-e2e.json",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index], value = argv[++index];
    if (!["--dist", "--chrome", "--out"].includes(key) || !value || value.startsWith("--")) throw new Error("invalid_arguments");
    result[key === "--dist" ? "distribution" : key === "--chrome" ? "chrome" : "output"] = resolve(value);
  }
  return result;
}
function isPublicKey(value) {
  if (value.startsWith("sb_secret_") || value.toLowerCase().includes("service_role")) return false;
  const parts = value.split(".");
  if (parts.length !== 3) return true;
  try { return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"))?.role !== "service_role"; } catch { return false; }
}
function configuration() {
  const missing = required.filter((name) => !process.env[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  const baseUrl = process.env.QUATA_SUPABASE_URL.trim().replace(/\/+$/, "");
  const key = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY.trim();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(key)) throw new Error("invalid_or_privileged_supabase_key");
  if (process.env.QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP !== "approved_isolated_account_purge") throw new Error("safe_cleanup_contract_missing");
  if (["A", "B"].some((label) => process.env[`QUATA_E2E_CHAT_${label}_E2E_SCOPE`] !== "isolated_sb04_account")) throw new Error("isolated_e2e_account_scope_missing");
  const users = ["A", "B"].map((label) => ({
    label,
    countryCode: process.env[`QUATA_E2E_CHAT_${label}_COUNTRY_CODE`].trim(),
    phone: process.env[`QUATA_E2E_CHAT_${label}_PHONE`].trim(),
    password: process.env[`QUATA_E2E_CHAT_${label}_PASSWORD`],
  }));
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) throw new Error("isolated_e2e_accounts_must_differ");
  return { baseUrl, key, users };
}
function headers(config, token) {
  return { apikey: config.key, "content-type": "application/json", "x-client-info": "quata-web-chat-browser-e2e", ...(token ? { authorization: `Bearer ${token}` } : {}) };
}
async function jsonRequest(url, options, prefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(20_000) }); }
  catch { throw new Error(`${prefix}:network`); }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  try { return text ? JSON.parse(text) : {}; } catch { throw new Error(`${prefix}:invalid_json`); }
}
async function login(config, user) {
  const payload = await jsonRequest(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: headers(config),
    body: JSON.stringify({
      action: "web_login",
      country_code: user.countryCode,
      phone_local: user.phone,
      password: user.password,
      client_instance_id: `web-chat-e2e-${user.label.toLowerCase()}-${crypto.randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session, profileId = payload?.profile?.id, webSessionToken = payload?.web_session?.token;
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at) || !webSessionToken) throw new Error("invalid_auth_response");
  return { label: user.label, profileId, accessToken: session.access_token, refreshToken: session.refresh_token, expiresAt: session.expires_at, webSessionToken };
}
function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST", headers: headers(config, session.accessToken), body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
}
function positiveId(value, name) {
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error(`chat_contract_invalid:${name}`);
  return value;
}
function threadId(payload) {
  return positiveId(payload?.thread?.id ?? payload?.threads?.[0]?.id ?? payload?.thread_id, "thread_id");
}
function messages(payload) {
  return [payload?.message, ...(Array.isArray(payload?.messages) ? payload.messages : []), ...(Array.isArray(payload?.update?.messages) ? payload.update.messages : [])].filter(Boolean);
}
async function pollMessage(config, session, thread, predicate, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const detail = await rpc(config, session, "quata_chat_get_thread", {
      p_actor_profile_id: session.profileId, p_thread_id: thread, p_known_message_ids: [], p_limit: 250,
    });
    const found = messages(detail).find(predicate);
    if (found) return found;
    await delay(1_000);
  }
  throw new Error("chat_backend_poll_timeout");
}

async function configuredDistribution(source, config) {
  if (!(await stat(source).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  const target = await mkdtemp(join(tmpdir(), "quata-web-chat-e2e-dist-"));
  await cp(source, target, { recursive: true });
  const index = join(target, "index.html");
  let html = await readFile(index, "utf8");
  html = html.replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${escapeHtml(config.baseUrl)}"`)
    .replace('name="quata-supabase-publishable-key" content=""', `name="quata-supabase-publishable-key" content="${escapeHtml(config.key)}"`);
  if (!html.includes(escapeHtml(config.key))) throw new Error("runtime_configuration_injection_failed");
  await writeFile(index, html, "utf8");
  return target;
}
function escapeHtml(value) {
  return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}
async function startServer(root) {
  const server = createServer(async (request, response) => {
    try {
      const pathname = decodeURIComponent(new URL(request.url ?? "/", "http://localhost").pathname);
      if (pathname === "/favicon.ico") return response.writeHead(204).end();
      const file = resolve(root, `.${pathname === "/" ? "/index.html" : pathname}`);
      if (!file.startsWith(`${root}\\`) && file !== root) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      response.writeHead(200, {
        "Content-Type": contentType(file), "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp", "Cache-Control": "no-store",
      });
      response.end(await readFile(file));
    } catch { response.writeHead(500).end(); }
  });
  await new Promise((ok, fail) => { server.once("error", fail); server.listen(0, "127.0.0.1", ok); });
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("static_server_start_failed");
  return {
    origin: `http://127.0.0.1:${address.port}`,
    close: () => new Promise((ok, fail) => server.close((error) => error ? fail(error) : ok())),
  };
}
function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"], [".webp", "image/webp"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}
async function openChatPage(browser, origin, session, thread) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const values = {
    quata_web_access_token: session.accessToken,
    quata_web_refresh_token: session.refreshToken,
    quata_web_session_token: session.webSessionToken,
    quata_web_user_id: session.profileId,
    quata_web_expires_at: String(session.expiresAt),
    "web.auth.session_ready": "true",
    quata_web_client_instance_id: `web-chat-e2e-${crypto.randomUUID()}`,
  };
  await context.addInitScript(({ storage }) => {
    for (const [key, value] of Object.entries(storage)) localStorage.setItem(key, value);
  }, { storage: values });
  const page = await context.newPage(), faults = [];
  page.on("pageerror", () => faults.push("uncaught_exception"));
  page.on("console", (entry) => { if (entry.type() === "error") faults.push("console_error"); });
  await page.goto(`${origin}/#chat-sb%3A${thread}`, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await page.getByLabel("Mensaje").waitFor({ timeout: 30_000 });
  return { context, page, faults };
}
async function sendText(page, marker) {
  await page.getByLabel("Mensaje").fill(marker);
  await page.getByRole("button", { name: "Enviar", exact: true }).click();
  await page.getByText(marker, { exact: true }).waitFor({ timeout: 15_000 });
}
async function startReply(page, sourceText) {
  await page.getByText(sourceText, { exact: true }).click();
  await page.getByRole("button", { name: "Responder", exact: true }).click();
  await page.getByText("Respondiendo a", { exact: false }).waitFor({ timeout: 10_000 });
}
async function logout(page) {
  await page.getByRole("button", { name: /Cerrar sesi/i }).click();
  await page.getByText("Inicio de sesi", { exact: false }).first().waitFor({ timeout: 30_000 });
  if (await page.evaluate(() => localStorage.getItem("quata_web_access_token"))) throw new Error("web_logout_local_session_remains");
}
async function logicalCleanup(config, state) {
  const actions = [];
  if (state.thread && state.a && state.messageA) {
    await rpc(config, state.a, "quata_chat_delete_messages", {
      p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_message_ids: [state.messageA],
    });
    actions.push("message_a_logically_deleted");
  }
  if (state.thread && state.b && state.messageB) {
    await rpc(config, state.b, "quata_chat_delete_messages", {
      p_actor_profile_id: state.b.profileId, p_thread_id: state.thread, p_message_ids: [state.messageB],
    });
    actions.push("reply_b_logically_deleted");
  }
  if (state.thread && state.a) {
    await rpc(config, state.a, "quata_chat_delete_thread", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread });
    actions.push("thread_hidden_for_a");
  }
  if (state.thread && state.b) {
    await rpc(config, state.b, "quata_chat_delete_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread });
    actions.push("thread_hidden_for_b");
  }
  return actions;
}
function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  return [
    "invalid_arguments", "missing_environment", "invalid_public_supabase_url", "invalid_or_privileged_supabase_key",
    "safe_cleanup_contract_missing", "isolated_e2e_account_scope_missing", "isolated_e2e_accounts_must_differ",
    "public_auth_request_failed", "invalid_auth_response", "chat_rpc_failed", "chat_contract_invalid",
    "distribution_missing", "runtime_configuration_injection_failed", "static_server_start_failed",
    "chat_backend_poll_timeout", "browser_runtime_fault", "web_logout_local_session_remains",
  ].find((prefix) => message.startsWith(prefix)) ?? "unexpected_chat_browser_e2e_failure";
}

const options = parseArgs(process.argv.slice(2));
const report = {
  check: "WEB-CHAT-BROWSER-02", status: "failed", startedAt: new Date().toISOString(),
  mode: "two_isolated_users_playwright_compose_wasm", steps: [], cleanup: { state: "not_started" },
};
let config, distribution, server, browser, pageA, pageB;
const state = { a: null, b: null, thread: null, messageA: null, messageB: null };
try {
  config = configuration();
  state.a = await login(config, config.users[0]);
  state.b = await login(config, config.users[1]);
  if (state.a.profileId === state.b.profileId) throw new Error("isolated_e2e_accounts_must_differ");
  report.steps.push("two_isolated_users_logged_in");
  state.thread = threadId(await rpc(config, state.a, "quata_chat_get_or_create_private_thread", {
    p_actor_profile_id: state.a.profileId, p_peer_profile_id: state.b.profileId,
  }));
  report.steps.push("private_thread_ready");
  distribution = await configuredDistribution(options.distribution, config);
  server = await startServer(distribution);
  browser = await chromium.launch({
    executablePath: options.chrome, headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"],
  });
  pageA = await openChatPage(browser, server.origin, state.a, state.thread);
  pageB = await openChatPage(browser, server.origin, state.b, state.thread);
  report.steps.push("two_authenticated_chat_views_opened");

  const markerA = `e2e-chat-a-${crypto.randomUUID()}`, markerB = `e2e-chat-b-${crypto.randomUUID()}`;
  await sendText(pageA.page, markerA);
  state.messageA = positiveId((await pollMessage(config, state.b, state.thread, (message) => message?.text === markerA))?.id, "message_a");
  await pageB.page.getByText(markerA, { exact: true }).waitFor({ timeout: 45_000 });
  report.steps.push("a_text_received_by_b_via_polling_ui");

  await startReply(pageB.page, markerA);
  await sendText(pageB.page, markerB);
  const reply = await pollMessage(config, state.a, state.thread, (message) => message?.text === markerB);
  state.messageB = positiveId(reply?.id, "message_b");
  if (reply?.reply_to_message_id !== state.messageA) throw new Error("chat_contract_invalid:reply_link");
  await pageA.page.getByText(markerB, { exact: true }).waitFor({ timeout: 45_000 });
  report.steps.push("b_reply_received_by_a_via_polling_ui_and_backend_linked");
  if (pageA.faults.length || pageB.faults.length) throw new Error("browser_runtime_fault");

  await logout(pageA.page);
  await logout(pageB.page);
  report.steps.push("both_ui_logouts_completed");
  report.status = "passed_with_external_hard_cleanup_pending";
  report.cleanup = { state: "ui_sessions_ended_external_hard_purge_required" };
  report.pollingContract = "No Realtime is claimed. Each peer message became visible in Compose UI within 45 seconds around the repository's 30-second polling interval.";
} catch (error) {
  report.error = safeFailure(error);
} finally {
  if (config && state.thread) {
    try {
      report.cleanup = { state: "external_hard_purge_required", logicalActions: await logicalCleanup(config, state) };
    } catch {
      recordLogicalCleanupFailure(report);
    }
  }
  for (const value of [pageA, pageB]) if (value?.context) await value.context.close().catch(() => {});
  if (browser) await browser.close().catch(() => {});
  if (server) await server.close().catch(() => {});
  if (distribution) await rm(distribution, { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  const target = resolve(options.output);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`Chat browser report written: ${target}`);
}
if (!report.status.startsWith("passed")) {
  console.error(`Chat browser E2E failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat browser E2E passed: text, reply, polling reception and UI logout verified.");
}
