#!/usr/bin/env node
import { chromium } from "playwright-core";
import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_OUTBOX_DURABLE_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_OUTBOX_DURABLE_HARD_CLEANUP";

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/chat-outbox-durable-evidence.json"),
    evidenceDir: resolve("build-reports/web/chat-outbox-durable-evidence"),
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index], value = argv[++index];
    if (!["--dist", "--chrome", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) throw new Error("invalid_arguments");
    if (key === "--dist") result.distribution = resolve(value);
    if (key === "--chrome") result.chrome = resolve(value);
    if (key === "--out") result.output = resolve(value);
    if (key === "--evidence-dir") result.evidenceDir = resolve(value);
  }
  return result;
}

async function runSilent(command, args, options = {}) {
  return await new Promise((resolvePromise, reject) => {
    let output = "", stderr = "";
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: false, ...options });
    child.stdout.on("data", (chunk) => { output += chunk.toString(); });
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolvePromise(output) : reject(new Error(`command_failed:${command}:${code}:${stderr.trim()}`)));
  });
}

async function gitMetadata() {
  const head = (await runSilent("git", ["rev-parse", "HEAD"])).trim();
  const status = await runSilent("git", ["status", "--porcelain"]);
  return { head, workingTreeDirty: status.trim().length > 0 };
}

async function publicBackendConfig() {
  const configuredUrl = process.env.QUATA_SUPABASE_URL?.trim();
  const configuredKey = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY?.trim();
  if (configuredUrl && configuredKey) return { baseUrl: configuredUrl.replace(/\/+$/, ""), key: configuredKey };
  const source = await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8");
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const key = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !key) throw new Error("missing_public_supabase_configuration");
  return { baseUrl, key };
}

function usersFromEnvironment() {
  const users = ["A", "B"].map((label) => ({
    label,
    countryCode: process.env[`QUATA_CHAT_EVIDENCE_${label}_COUNTRY_CODE`]?.trim(),
    phone: process.env[`QUATA_CHAT_EVIDENCE_${label}_PHONE`]?.trim(),
    password: process.env[`QUATA_CHAT_EVIDENCE_${label}_PASSWORD`],
  }));
  if (users.some((user) => !user.countryCode || !user.phone || !user.password)) throw new Error("missing_chat_evidence_credentials");
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) throw new Error("chat_evidence_users_must_differ");
  return users;
}

function isPublicKey(value) {
  if (!value || value.startsWith("sb_secret_") || value.toLowerCase().includes("service_role")) return false;
  if (value.startsWith("sb_publishable_")) return true;
  const parts = value.split(".");
  if (parts.length !== 3) return false;
  try { return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"))?.role === "anon"; } catch { return false; }
}

function headers(config, token) {
  return { apikey: config.key, "content-type": "application/json", "x-client-info": "quata-chat-outbox-durable-evidence", ...(token ? { authorization: `Bearer ${token}` } : {}) };
}

async function jsonRequest(url, options, prefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(20_000) }); }
  catch { throw new Error(`${prefix}:network`); }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  try { return text ? JSON.parse(text) : {}; } catch { throw new Error(`${prefix}:invalid_json`); }
}

async function login(config, user, acceptCustody) {
  const payload = await jsonRequest(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST", headers: headers(config), body: JSON.stringify({
      action: "web_login", country_code: user.countryCode, phone_local: user.phone, password: user.password,
      client_instance_id: `chat-outbox-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session, profileId = payload?.profile?.id, webSessionToken = payload?.web_session?.token;
  const candidate = { label: user.label, profileId, accessToken: session?.access_token, refreshToken: session?.refresh_token, expiresAt: session?.expires_at, webSessionToken };
  if (candidate.accessToken) acceptCustody(candidate);
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at) || !webSessionToken) throw new Error("invalid_auth_response");
  return candidate;
}

async function logout(config, session) {
  if (!session) return;
  const failures = [];
  try {
    await jsonRequest(`${config.baseUrl}/functions/v1/quata-web-push`, {
      method: "POST", headers: { ...headers(config, session.accessToken), "x-quata-web-session": session.webSessionToken }, body: JSON.stringify({ action: "logout" }),
    }, "web_logout_failed");
  } catch (error) { failures.push(error); }
  try {
    const response = await fetch(`${config.baseUrl}/auth/v1/logout?scope=local`, { method: "POST", headers: headers(config, session.accessToken), body: "{}", signal: AbortSignal.timeout(20_000) });
    if (!response.ok && response.status !== 401) throw new Error(`auth_logout_failed:http_${response.status}`);
  } catch (error) { failures.push(error); }
  if (failures.length) throw new Error("session_logout_incomplete");
}

function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, { method: "POST", headers: headers(config, session.accessToken), body: JSON.stringify(body) }, `chat_rpc_failed:${name}`);
}

function positiveId(value, name) {
  const numeric = Number(value);
  if (!Number.isSafeInteger(numeric) || numeric <= 0) throw new Error(`chat_contract_invalid:${name}`);
  return numeric;
}
function threadId(payload) { return positiveId(payload?.thread?.id ?? payload?.threads?.[0]?.id ?? payload?.thread_id, "thread_id"); }
function rpcMessages(payload) { return [payload?.message, ...(Array.isArray(payload?.messages) ? payload.messages : []), ...(Array.isArray(payload?.update?.messages) ? payload.update.messages : [])].filter(Boolean); }

async function getThreadMessages(config, session, thread) {
  return rpcMessages(await rpc(config, session, "quata_chat_get_thread", {
    p_actor_profile_id: session.profileId, p_thread_id: thread, p_known_message_ids: [], p_limit: 250,
  }));
}
function bodyOf(message) { return message?.body ?? message?.text ?? message?.message ?? null; }
async function pollExactMarker(config, session, thread, marker, timeout = 60_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const rows = (await getThreadMessages(config, session, thread)).filter((row) => bodyOf(row) === marker);
    if (rows.length > 1) throw new Error("chat_outbox_duplicate_remote_effect");
    if (rows.length === 1) return rows[0];
    await delay(1_000);
  }
  throw new Error("chat_backend_poll_timeout");
}

async function configuredDistribution(source, config) {
  if (!(await stat(source).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  const target = await mkdtemp(join(tmpdir(), "quata-chat-outbox-dist-"));
  await cp(source, target, { recursive: true });
  const index = join(target, "index.html");
  let html = await readFile(index, "utf8");
  html = html.replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${escapeHtml(config.baseUrl)}"`)
    .replace('name="quata-supabase-publishable-key" content=""', `name="quata-supabase-publishable-key" content="${escapeHtml(config.key)}"`);
  if (!html.includes(escapeHtml(config.key))) throw new Error("runtime_configuration_injection_failed");
  await writeFile(index, html, "utf8");
  return target;
}
function escapeHtml(value) { return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;"); }

async function startServer(root) {
  const server = createServer(async (request, response) => {
    try {
      const pathname = decodeURIComponent(new URL(request.url ?? "/", "http://localhost").pathname);
      if (pathname === "/favicon.ico") return response.writeHead(204).end();
      const file = resolve(root, `.${pathname === "/" ? "/index.html" : pathname}`);
      if (!file.startsWith(`${root}\\`) && !file.startsWith(`${root}/`) && file !== root) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      response.writeHead(200, { "Content-Type": contentType(file), "Cross-Origin-Opener-Policy": "same-origin", "Cross-Origin-Embedder-Policy": "require-corp", "Cache-Control": "no-store" });
      response.end(await readFile(file));
    } catch { response.writeHead(500).end(); }
  });
  await new Promise((ok, fail) => { server.once("error", fail); server.listen(0, "127.0.0.1", ok); });
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("static_server_start_failed");
  return { origin: `http://127.0.0.1:${address.port}`, close: () => new Promise((ok, fail) => server.close((error) => error ? fail(error) : ok())) };
}
function contentType(path) { return new Map([[".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"], [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"], [".json", "application/json"], [".css", "text/css"]]).get(extname(path).toLowerCase()) ?? "application/octet-stream"; }

async function createAuthenticatedContext(browser, session, faults) {
  const context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
  await context.addInitScript(({ storage }) => {
    try {
      for (const [key, value] of Object.entries(storage)) localStorage.setItem(key, value);
      sessionStorage.setItem("quata.chat_composer.e2e", "1");
    } catch {
      // Chromium evaluates init scripts once on the opaque about:blank document, where storage is unavailable.
    }
  }, { storage: {
    quata_web_access_token: session.accessToken, quata_web_refresh_token: session.refreshToken,
    quata_web_session_token: session.webSessionToken, quata_web_user_id: session.profileId,
    quata_web_expires_at: String(session.expiresAt), "web.auth.session_ready": "true",
    quata_web_client_instance_id: `chat-outbox-${randomUUID()}`,
  } });
  context.on("page", (page) => page.on("pageerror", (error) => faults.push(String(error?.message ?? "pageerror"))));
  return context;
}

async function openChatPage(context, origin, thread) {
  const page = await context.newPage();
  await page.goto(`${origin}/#chat-${encodeURIComponent(`sb:${thread}`)}`, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await page.waitForFunction((route) => document.documentElement.getAttribute("data-quata-shell-route") === route, `chat/sb:${thread}`, { timeout: 45_000 });
  await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-chat-composer-e2e") === "ready", null, { timeout: 45_000 });
  await delay(2_000);
  return page;
}

async function visibleAriaLocator(page, patterns, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const viewport = page.viewportSize() ?? { width: 430, height: 930 };
    const controls = page.locator("[aria-label]");
    const count = await controls.count().catch(() => 0);
    for (let index = 0; index < count; index += 1) {
      const locator = controls.nth(index);
      const label = await locator.getAttribute("aria-label").catch(() => "");
      if (!patterns.some((pattern) => pattern.test(label ?? ""))) continue;
      const box = await locator.boundingBox().catch(() => null);
      if (box && box.width > 0 && box.height > 0 && box.x + box.width > 0 && box.y + box.height > 0 && box.x < viewport.width && box.y < viewport.height) return locator;
    }
    await delay(250);
  }
  return null;
}

async function sendThroughProductComposer(page, marker) {
  const input = await visibleAriaLocator(page, [/Mensaje|Message|Composer/i], 15_000);
  if (!input) throw new Error("composer_input_not_visible");
  await input.fill(marker, { timeout: 10_000 });
  let send = await visibleAriaLocator(page, [/Enviar|Send/i], 5_000);
  if (!send) {
    await input.click({ force: true });
    await page.keyboard.press("Control+A");
    await page.keyboard.press("Backspace");
    await page.keyboard.type(marker, { delay: 15 });
    send = await visibleAriaLocator(page, [/Enviar|Send/i], 10_000);
  }
  if (!send) {
    const state = await input.evaluate((element) => ({
      tagName: element.tagName,
      role: element.getAttribute("role"),
      ariaLabel: element.getAttribute("aria-label"),
      valueLength: typeof element.value === "string" ? element.value.length : null,
    }));
    const bridge = await page.evaluate(() => globalThis.__quataChatComposerE2eProduct?.available?.() ?? null);
    throw new Error(`composer_state_not_updated:${JSON.stringify({ state, bridgeMessageLength: typeof bridge?.messageText === "string" ? bridge.messageText.length : null, bridgeSend: bridge?.send ?? null })}`);
  }
  await send.click({ force: true, timeout: 10_000 });
}

async function readOutbox(page) {
  return await page.evaluate(async () => {
    const db = await new Promise((resolvePromise, reject) => {
      const request = indexedDB.open("quata-chat-outbox", 1);
      request.onsuccess = () => resolvePromise(request.result);
      request.onerror = () => reject(request.error);
    });
    try {
      if (!db.objectStoreNames.contains("messages")) return [];
      const raw = await new Promise((resolvePromise, reject) => {
        const request = db.transaction("messages", "readonly").objectStore("messages").getAll();
        request.onsuccess = () => resolvePromise(request.result ?? []);
        request.onerror = () => reject(request.error);
      });
      return raw.map((value) => JSON.parse(value));
    } finally { db.close(); }
  });
}

async function waitOutbox(page, predicate, timeout = 20_000) {
  const deadline = Date.now() + timeout;
  let rows = [];
  while (Date.now() < deadline) {
    rows = await readOutbox(page);
    if (predicate(rows)) return rows;
    await delay(250);
  }
  throw new Error("chat_outbox_state_timeout");
}

async function hardDeleteTemporaryThread(thread, uniqueKey) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) throw new Error("missing_hard_cleanup_authorization");
  if (!uniqueKey.startsWith("qadata-chat-outbox-durable-web-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
  const [connectionString, ca] = await Promise.all([readFile(process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile, "utf8"), readFile(process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const { Client } = await import("pg");
  const client = new Client({ connectionString: parsedConnection.toString(), ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname } });
  await client.connect();
  try {
    await client.query("begin");
    const owned = await client.query("select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-outbox-durable-web-%' for update", [thread, uniqueKey]);
    if (owned.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_not_owned");
    const deleted = await client.query("delete from public.chat_threads where id = $1 and unique_key = $2 returning id", [thread, uniqueKey]);
    if (deleted.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_delete_failed");
    const residue = await client.query(`select
      (select count(*)::int from public.chat_threads where id = $1 or unique_key = $2) as chat_threads,
      (select count(*)::int from public.chat_messages where thread_id = $1) as chat_messages,
      (select count(*)::int from public.chat_participants where thread_id = $1) as chat_participants,
      (select count(*)::int from public.chat_attachments where thread_id = $1) as chat_attachments,
      (select count(*)::int from public.chat_message_states where thread_id = $1) as chat_message_states,
      (select count(*)::int from public.chat_events where thread_id = $1) as chat_events,
      (select count(*)::int from public.conversation_user_state where conversation_id = $1) as conversation_user_state`, [thread, uniqueKey]);
    const counts = residue.rows[0] ?? {};
    if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:physical_rows");
    await client.query("commit");
    return { threadId: thread, uniqueKeySha256: sha256(uniqueKey), residueCounts: counts };
  } catch (error) { await client.query("rollback").catch(() => {}); throw error; }
  finally { await client.end().catch(() => {}); }
}

function sha256(value) { return createHash("sha256").update(value).digest("hex"); }
function messageId(row) { return positiveId(row?.id ?? row?.message_id, "message_id"); }
function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  return ["invalid_arguments", "missing_public_supabase_configuration", "invalid_public_supabase_url", "invalid_or_privileged_supabase_key", "missing_chat_evidence_credentials", "chat_evidence_users_must_differ", "public_auth_request_failed", "invalid_auth_response", "chat_rpc_failed", "chat_contract_invalid", "chat_backend_poll_timeout", "distribution_missing", "runtime_configuration_injection_failed", "static_server_start_failed", "composer_input_not_visible", "composer_send_not_available", "chat_outbox_state_timeout", "chat_outbox_duplicate_remote_effect", "browser_runtime_fault", "cleanup_residue_detected", "missing_hard_cleanup_authorization", "session_logout_incomplete"].find((prefix) => message.startsWith(prefix)) ?? "unexpected_chat_outbox_durable_failure";
}

const options = parseArgs(process.argv.slice(2));
const report = { check: "CHAT-OUTBOX-DURABLE-WEB-001", status: "failed", startedAt: new Date().toISOString(), git: await gitMetadata(), steps: [], cleanup: { state: "not_started" }, evidence: {} };
const state = { a: null, b: null, thread: null, message: null, marker: null, clientMessageId: null, uniqueKey: null };
let config, distribution, server, browser, context;
const faults = [];
try {
  config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");
  const users = usersFromEnvironment();
  const loginA = login(config, users[0], (session) => { state.a = session; });
  const loginB = login(config, users[1], (session) => { state.b = session; });
  const loginResults = await Promise.allSettled([loginA, loginB]);
  const loginFailure = loginResults.find((result) => result.status === "rejected");
  if (loginFailure) throw loginFailure.reason;
  report.steps.push("two_authorized_profiles_logged_in");

  const runId = randomUUID();
  state.uniqueKey = `qadata-chat-outbox-durable-web-${runId}`;
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId, p_recipient_profile_ids: [state.b.profileId], p_subject: `QADATA chat durable outbox ${runId}`,
    p_type: "group", p_message: "", p_unique_key: state.uniqueKey, p_community_id: null,
  }));
  report.steps.push("isolated_group_thread_ready");

  state.marker = `chat-outbox-durable-${randomUUID()}`;
  distribution = await configuredDistribution(options.distribution, config);
  const sourceRevision = (await readFile(join(options.distribution, "quata-source-revision.txt"), "utf8")).trim();
  report.evidence.distribution = { sourceRevision, webJsSha256: sha256(await readFile(join(options.distribution, "web.js"))) };
  server = await startServer(distribution);
  browser = await chromium.launch({ executablePath: options.chrome, headless: true, args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"] });
  context = await createAuthenticatedContext(browser, state.a, faults);

  let page = await openChatPage(context, server.origin, state.thread);
  await context.setOffline(true);
  await page.waitForFunction(() => navigator.onLine === false, null, { timeout: 5_000 });
  await sendThroughProductComposer(page, state.marker);
  const queued = await waitOutbox(page, (rows) => rows.filter((row) => row.actorId === state.a.profileId && row.text === state.marker && row.conversationId === `sb:${state.thread}`).length === 1);
  const ownedRows = queued.filter((row) => row.actorId === state.a.profileId && row.text === state.marker && row.conversationId === `sb:${state.thread}`);
  state.clientMessageId = ownedRows[0].clientMessageId;
  if (!state.clientMessageId) throw new Error("chat_contract_invalid:client_message_id");
  report.evidence.queued = { count: ownedRows.length, markerSha256: sha256(state.marker), clientMessageIdSha256: sha256(state.clientMessageId), attempts: ownedRows[0].attempts, deliveredAwaitingCleanup: ownedRows[0].deliveredAwaitingCleanup };
  report.steps.push("real_product_composer_persisted_one_indexeddb_outbox_row_offline");
  await mkdir(options.evidenceDir, { recursive: true });
  const offlineScreenshot = join(options.evidenceDir, "web-chat-outbox-offline.png");
  await page.screenshot({ path: offlineScreenshot, fullPage: true });
  report.evidence.offlineScreenshot = offlineScreenshot;
  await page.close();
  report.steps.push("original_product_page_destroyed_while_outbox_remained_durable");

  await context.setOffline(false);
  page = await openChatPage(context, server.origin, state.thread);
  await page.waitForFunction(() => navigator.onLine === true, null, { timeout: 5_000 });
  const restored = await waitOutbox(page, (rows) => rows.some((row) => row.actorId === state.a.profileId && row.clientMessageId === state.clientMessageId));
  if (restored.filter((row) => row.actorId === state.a.profileId && row.clientMessageId === state.clientMessageId).length !== 1) throw new Error("chat_contract_invalid:restored_outbox_count");
  report.steps.push("new_product_page_restored_same_durable_outbox_row");
  await context.setOffline(true);
  await page.waitForFunction(() => navigator.onLine === false, null, { timeout: 5_000 });
  await context.setOffline(false);
  await page.waitForFunction(() => navigator.onLine === true, null, { timeout: 5_000 });
  report.steps.push("new_product_page_observed_offline_to_online_recovery_transition");
  const delivered = await pollExactMarker(config, state.b, state.thread, state.marker);
  state.message = messageId(delivered);
  const remaining = await waitOutbox(page, (rows) => !rows.some((row) => row.actorId === state.a.profileId && row.clientMessageId === state.clientMessageId), 30_000);
  const allRemote = (await getThreadMessages(config, state.b, state.thread)).filter((row) => bodyOf(row) === state.marker);
  if (allRemote.length !== 1) throw new Error("chat_outbox_duplicate_remote_effect");
  const deliveredClientId = delivered?.client_message_id ?? delivered?.clientMessageId ?? null;
  if (deliveredClientId && deliveredClientId !== state.clientMessageId) throw new Error("chat_contract_invalid:client_message_id_mismatch");
  report.evidence.delivered = { messageId: state.message, exactRemoteCount: allRemote.length, clientMessageIdMatched: deliveredClientId ? true : "field_not_returned", remainingOutboxCount: remaining.filter((row) => row.actorId === state.a.profileId).length };
  const replayScreenshot = join(options.evidenceDir, "web-chat-outbox-replayed.png");
  await page.screenshot({ path: replayScreenshot, fullPage: true });
  report.evidence.replayScreenshot = replayScreenshot;
  report.steps.push("new_product_page_replayed_exact_message_after_network_recovery");
  report.steps.push("remote_effect_unique_and_indexeddb_row_removed");
  report.evidence.browserPageErrors = faults.map((message) => ({ sha256: sha256(message), networkExpected: /fetch|network|offline|internet|failed to load|load failed/i.test(message) }));
  if (faults.some((message) => !/fetch|network|offline|internet|failed to load|load failed/i.test(message))) throw new Error("browser_runtime_fault");
  report.status = "passed";
  report.fixture = { threadId: state.thread, conversationId: `sb:${state.thread}`, messageId: state.message, markerSha256: sha256(state.marker), clientMessageIdSha256: sha256(state.clientMessageId) };
} catch (error) {
  report.error = safeFailure(error);
  report.diagnostic = String(error?.message ?? error).slice(0, 500);
} finally {
  const cleanup = { state: "completed", actions: [] };
  let cleanupFailed = false;
  if (config && state.thread && state.message && state.a) {
    try {
      await rpc(config, state.a, "quata_chat_delete_messages", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_message_ids: [state.message] });
      cleanup.actions.push("exact_test_message_deleted");
    } catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
  }
  if (config && state.thread && state.uniqueKey) {
    try {
      cleanup.hardCleanup = await hardDeleteTemporaryThread(state.thread, state.uniqueKey);
      cleanup.actions.push("hard_deleted_owned_temporary_thread");
      cleanup.actions.push("cleanup_verified_physical_residue_absent");
    } catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
  }
  for (const session of [state.a, state.b]) {
    if (!config || !session) continue;
    try { await logout(config, session); cleanup.actions.push(`session_${session.label.toLowerCase()}_revoked`); }
    catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
  }
  if (cleanupFailed) {
    cleanup.state = "failed_or_incomplete";
    if (report.status === "passed") { report.status = "failed"; report.error = cleanup.error ?? "cleanup_residue_detected"; }
  }
  report.cleanup = cleanup;
  if (context) await context.close().catch(() => {});
  if (browser) await browser.close().catch(() => {});
  if (server) await server.close().catch(() => {});
  if (distribution) await rm(distribution, { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Chat outbox durable Web evidence written: ${options.output}`);
}
if (report.status !== "passed") { console.error(`Chat outbox durable Web evidence failed: ${report.error ?? "unknown"}.`); process.exitCode = 1; }
else console.log("Chat outbox durable Web evidence passed.");
