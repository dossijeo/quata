#!/usr/bin/env node
import { chromium } from "playwright-core";
import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { setTimeout as delay } from "node:timers/promises";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const favoriteConversationId = "__favorite_messages__";
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_FAVORITES_FOCUSED_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_FAVORITES_FOCUSED_HARD_CLEANUP";
const evidenceUserEnvironment = [
  {
    label: "A",
    countryCode: "QUATA_CHAT_EVIDENCE_A_COUNTRY_CODE",
    phone: "QUATA_CHAT_EVIDENCE_A_PHONE",
    password: "QUATA_CHAT_EVIDENCE_A_PASSWORD",
  },
  {
    label: "B",
    countryCode: "QUATA_CHAT_EVIDENCE_B_COUNTRY_CODE",
    phone: "QUATA_CHAT_EVIDENCE_B_PHONE",
    password: "QUATA_CHAT_EVIDENCE_B_PASSWORD",
  },
];

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/chat-favorites-focused-evidence.json"),
    evidenceDir: resolve("build-reports/web/chat-favorites-focused-evidence"),
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index], value = argv[++index];
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
  const users = evidenceUserEnvironment.map((entry) => ({
    label: entry.label,
    countryCode: process.env[entry.countryCode]?.trim(),
    phone: process.env[entry.phone]?.trim(),
    password: process.env[entry.password],
  }));
  if (users.some((user) => !user.countryCode || !user.phone || !user.password)) {
    throw new Error("missing_chat_evidence_credentials");
  }
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) {
    throw new Error("chat_evidence_users_must_differ");
  }
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
  return {
    apikey: config.key,
    "content-type": "application/json",
    "x-client-info": "quata-chat-favorites-focused-evidence",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
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
      client_instance_id: `chat-fav-focus-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session, profileId = payload?.profile?.id, webSessionToken = payload?.web_session?.token;
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at) || !webSessionToken) {
    throw new Error("invalid_auth_response");
  }
  return { label: user.label, profileId, accessToken: session.access_token, refreshToken: session.refresh_token, expiresAt: session.expires_at, webSessionToken };
}

function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
}

function positiveId(value, name) {
  const numeric = Number(value);
  if (!Number.isSafeInteger(numeric) || numeric <= 0) throw new Error(`chat_contract_invalid:${name}`);
  return numeric;
}

function threadId(payload) {
  return positiveId(payload?.thread?.id ?? payload?.threads?.[0]?.id ?? payload?.thread_id, "thread_id");
}

function rpcThreads(payload) {
  return [
    payload?.thread,
    payload?.conversation,
    ...(Array.isArray(payload?.threads) ? payload.threads : []),
    ...(Array.isArray(payload?.conversations) ? payload.conversations : []),
    ...(Array.isArray(payload?.update?.threads) ? payload.update.threads : []),
    ...(Array.isArray(payload?.update?.conversations) ? payload.update.conversations : []),
  ].filter(Boolean);
}

function rawThreadId(row) {
  const value = row?.thread_id ?? row?.id;
  const numeric = Number(value);
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : null;
}

function rpcMessages(payload) {
  return [
    payload?.message,
    ...(Array.isArray(payload?.messages) ? payload.messages : []),
    ...(Array.isArray(payload?.update?.messages) ? payload.update.messages : []),
  ].filter(Boolean);
}

function messageId(payload) {
  return positiveId(rpcMessages(payload)[0]?.id ?? payload?.message_id, "message_id");
}

async function pollMessage(config, session, thread, predicate, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const detail = await rpc(config, session, "quata_chat_get_thread", {
      p_actor_profile_id: session.profileId,
      p_thread_id: thread,
      p_known_message_ids: [],
      p_limit: 250,
    });
    const found = rpcMessages(detail).find(predicate);
    if (found) return found;
    await delay(1_000);
  }
  throw new Error("chat_backend_poll_timeout");
}

async function favorites(config, session) {
  return rpcMessages(await rpc(config, session, "quata_chat_get_favorites", {
    p_actor_profile_id: session.profileId,
    p_limit: 250,
  }));
}

async function inboxContainsThread(config, session, thread) {
  const inbox = await rpc(config, session, "quata_chat_get_inbox", {
    p_actor_profile_id: session.profileId,
    p_limit: 100,
  });
  return rpcThreads(inbox).some((row) => rawThreadId(row) === thread);
}

async function threadContainsMarker(config, session, thread, marker) {
  const detail = await rpc(config, session, "quata_chat_get_thread", {
    p_actor_profile_id: session.profileId,
    p_thread_id: thread,
    p_known_message_ids: [],
    p_limit: 250,
  });
  return rpcMessages(detail).some((message) => {
    if (Number(message?.id) !== Number(message?.message_id ?? message?.id)) return false;
    return message?.body === marker || message?.text === marker || message?.message === marker;
  });
}

async function configuredDistribution(source, config) {
  if (!(await stat(source).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  const target = await mkdtemp(join(tmpdir(), "quata-chat-fav-focus-dist-"));
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
      if (!file.startsWith(`${root}\\`) && !file.startsWith(`${root}/`) && file !== root) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      response.writeHead(200, {
        "Content-Type": contentType(file),
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
        "Cache-Control": "no-store",
      });
      response.end(await readFile(file));
    } catch { response.writeHead(500).end(); }
  });
  await new Promise((ok, fail) => { server.once("error", fail); server.listen(0, "127.0.0.1", ok); });
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("static_server_start_failed");
  return { origin: `http://127.0.0.1:${address.port}`, close: () => new Promise((ok, fail) => server.close((error) => error ? fail(error) : ok())) };
}

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"], [".webp", "image/webp"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

function chatFragment(conversationId, messageId) {
  let fragment = `chat-${encodeURIComponent(conversationId)}`;
  if (messageId) fragment += `?message=${encodeURIComponent(messageId)}`;
  return fragment;
}

async function openAuthenticatedChatPage(browser, origin, session, fragment, expectedRoute, faults, settleDelayMillis = 1_500) {
  const context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
  await context.addInitScript(({ storage }) => {
    for (const [key, value] of Object.entries(storage)) localStorage.setItem(key, value);
  }, {
    storage: {
      quata_web_access_token: session.accessToken,
      quata_web_refresh_token: session.refreshToken,
      quata_web_session_token: session.webSessionToken,
      quata_web_user_id: session.profileId,
      quata_web_expires_at: String(session.expiresAt),
      "web.auth.session_ready": "true",
      quata_web_client_instance_id: `chat-fav-focus-${randomUUID()}`,
    },
  });
  const page = await context.newPage();
  page.on("pageerror", () => faults.push("pageerror"));
  page.on("console", (entry) => { if (entry.type() === "error") faults.push("console_error"); });
  await page.goto(`${origin}/#${fragment}`, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await page.waitForFunction((route) => document.documentElement.getAttribute("data-quata-shell-route") === route, expectedRoute, { timeout: 45_000 });
  await page.waitForFunction(() => {
    const root = document.querySelector("#quata-root");
    return root && (root.querySelector("canvas") || root.shadowRoot?.querySelector("canvas"));
  }, { timeout: 45_000 });
  if (settleDelayMillis > 0) await delay(settleDelayMillis);
  return { context, page };
}

async function attachScreenshot(page, evidenceDir, name) {
  await mkdir(evidenceDir, { recursive: true });
  const path = join(evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

async function logicalCleanup(config, state) {
  const actions = [];
  if (state.thread && state.message && state.a) {
    await rpc(config, state.a, "quata_chat_set_favorite", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_id: state.message,
      p_favorite: false,
    });
    actions.push("favorite_removed");
  }
  if (state.thread && state.message && state.a) {
    await rpc(config, state.a, "quata_chat_delete_messages", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_ids: [state.message],
    });
    actions.push("test_message_deleted");
  }
  if (state.thread && state.marker && state.a) {
    if (await threadContainsMarker(config, state.a, state.thread, state.marker)) throw new Error("cleanup_residue_detected:message_a");
    actions.push("cleanup_verified_message_absent_for_a");
  }
  if (state.thread && state.marker && state.b) {
    if (await threadContainsMarker(config, state.b, state.thread, state.marker)) throw new Error("cleanup_residue_detected:message_b");
    actions.push("cleanup_verified_message_absent_for_b");
  }
  if (state.thread && state.a) {
    await rpc(config, state.a, "quata_chat_delete_thread", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread });
    actions.push("thread_removed_from_a_inbox");
  }
  if (state.thread && state.b) {
    await rpc(config, state.b, "quata_chat_delete_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread });
    actions.push("thread_removed_from_b_inbox");
  }
  if (state.thread && state.message && state.a) {
    const remaining = await favorites(config, state.a);
    if (remaining.some((message) => Number(message?.id) === state.message)) throw new Error("cleanup_residue_detected:favorite");
    actions.push("cleanup_verified_favorite_absent");
  }
  if (state.thread && state.a) {
    if (await inboxContainsThread(config, state.a, state.thread)) throw new Error("cleanup_residue_detected:thread_a");
    actions.push("cleanup_verified_thread_absent_for_a");
  }
  if (state.thread && state.b) {
    if (await inboxContainsThread(config, state.b, state.thread)) throw new Error("cleanup_residue_detected:thread_b");
    actions.push("cleanup_verified_thread_absent_for_b");
  }
  return actions;
}

async function hardDeleteTemporaryThread(thread, uniqueKey) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) {
    throw new Error("missing_hard_cleanup_authorization");
  }
  if (!uniqueKey.startsWith("qadata-chat-fav-focus-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([
    readFile(dbUrlPath, "utf8"),
    readFile(tlsCaPath, "utf8"),
  ]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const { Client } = await import("pg");
  const client = new Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try {
    await client.query("begin");
    const owned = await client.query(
      "select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-fav-focus-%' for update",
      [thread, uniqueKey],
    );
    if (owned.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_not_owned");
    const deleted = await client.query(
      "delete from public.chat_threads where id = $1 and unique_key = $2 returning id",
      [thread, uniqueKey],
    );
    if (deleted.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_delete_failed");
    const residue = await client.query(
      `select
        (select count(*)::int from public.chat_threads where id = $1 or unique_key = $2) as chat_threads,
        (select count(*)::int from public.chat_messages where thread_id = $1) as chat_messages,
        (select count(*)::int from public.chat_participants where thread_id = $1) as chat_participants,
        (select count(*)::int from public.chat_attachments where thread_id = $1) as chat_attachments,
        (select count(*)::int from public.chat_message_states where thread_id = $1) as chat_message_states,
        (select count(*)::int from public.chat_events where thread_id = $1) as chat_events,
        (select count(*)::int from public.conversation_user_state where conversation_id = $1) as conversation_user_state`,
      [thread, uniqueKey],
    );
    const counts = residue.rows[0] ?? {};
    const dirty = Object.entries(counts).filter(([, count]) => Number(count) !== 0);
    if (dirty.length) throw new Error("cleanup_residue_detected:physical_rows");
    await client.query("commit");
    return { threadId: thread, uniqueKeySha256: sha256(uniqueKey), residueCounts: counts };
  } catch (error) {
    await client.query("rollback").catch(() => {});
    throw error;
  } finally {
    await client.end().catch(() => {});
  }
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  return [
    "invalid_arguments", "missing_public_supabase_configuration", "invalid_public_supabase_url",
    "invalid_or_privileged_supabase_key", "missing_chat_evidence_credentials", "chat_evidence_users_must_differ",
    "public_auth_request_failed", "invalid_auth_response", "chat_rpc_failed", "chat_contract_invalid",
    "chat_backend_poll_timeout", "distribution_missing", "runtime_configuration_injection_failed",
    "static_server_start_failed", "favorite_message_not_visible", "favorite_message_open_failed",
    "focused_message_not_visible", "browser_runtime_fault", "cleanup_residue_detected",
    "missing_hard_cleanup_authorization",
  ].find((prefix) => message.startsWith(prefix)) ?? "unexpected_chat_favorites_focused_failure";
}

const options = parseArgs(process.argv.slice(2));
const report = {
  check: "CHAT-FAVORITES-FOCUSED-WEB-001",
  status: "failed",
  startedAt: new Date().toISOString(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};
const state = { a: null, b: null, thread: null, message: null, marker: null, uniqueKey: null, hardCleanup: null };
let config, distribution, server, browser;
const contexts = [];
const faults = [];
try {
  config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");
  const users = usersFromEnvironment();

  state.a = await login(config, users[0]);
  state.b = await login(config, users[1]);
  report.steps.push("two_authorized_profiles_logged_in");
  const runId = randomUUID();
  state.uniqueKey = `qadata-chat-fav-focus-${runId}`;
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId,
    p_recipient_profile_ids: [state.b.profileId],
    p_subject: `QADATA chat favorite focus ${runId}`,
    p_type: "group",
    p_message: "",
    p_unique_key: state.uniqueKey,
    p_community_id: null,
  }));
  report.steps.push("isolated_group_thread_ready");

  const marker = `chat-fav-focus-${randomUUID()}`;
  state.marker = marker;
  const markerProbe = marker.slice(0, 24);
  const sent = await rpc(config, state.a, "quata_chat_send_message", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message: marker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-fav-focus-${randomUUID()}`,
  });
  state.message = messageId(sent);
  await pollMessage(config, state.b, state.thread, (message) => Number(message?.id) === state.message && message?.body === marker);
  report.steps.push("unique_message_visible_to_peer");

  await rpc(config, state.a, "quata_chat_set_favorite", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message_id: state.message,
    p_favorite: true,
  });
  const favoriteRows = await favorites(config, state.a);
  if (!favoriteRows.some((message) => Number(message?.id) === state.message && message?.favorited === true)) {
    throw new Error("chat_contract_invalid:favorite_missing");
  }
  report.steps.push("favorite_added_and_verified_by_rpc");

  distribution = await configuredDistribution(options.distribution, config);
  server = await startServer(distribution);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });

  const favoriteRoute = `chat/${favoriteConversationId}`;
  const favoritesPage = await openAuthenticatedChatPage(
    browser,
    server.origin,
    state.a,
    chatFragment(favoriteConversationId),
    favoriteRoute,
    faults,
  );
  contexts.push(favoritesPage.context);
  report.evidence.favoritesRouteScreenshot = await attachScreenshot(favoritesPage.page, options.evidenceDir, "web-favorites-route");
  await favoritesPage.page.getByText(markerProbe, { exact: false }).waitFor({ timeout: 45_000 }).catch(() => {
    throw new Error("favorite_message_not_visible");
  });
  report.evidence.favoritesScreenshot = await attachScreenshot(favoritesPage.page, options.evidenceDir, "web-favorites-list");
  report.steps.push("favorite_route_rendered_message");

  await favoritesPage.page.getByText(markerProbe, { exact: false }).click({ timeout: 10_000, force: true }).catch(() => {
    throw new Error("favorite_message_open_failed");
  });
  await favoritesPage.page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    `chat/sb:${state.thread}`,
    { timeout: 45_000 },
  );
  report.evidence.openedSourceScreenshot = await attachScreenshot(favoritesPage.page, options.evidenceDir, "web-favorites-open-source");
  report.steps.push("favorite_click_opened_source_conversation");

  const focusedPage = await openAuthenticatedChatPage(
    browser,
    server.origin,
    state.a,
    chatFragment(`sb:${state.thread}`, String(state.message)),
    `chat/sb:${state.thread}`,
    faults,
    0,
  );
  contexts.push(focusedPage.context);
  await focusedPage.page.getByText(markerProbe, { exact: false }).waitFor({ timeout: 45_000 }).catch(() => {
    throw new Error("focused_message_not_visible");
  });
  report.evidence.focusedScreenshot = await attachScreenshot(focusedPage.page, options.evidenceDir, "web-focused-message");
  report.steps.push("focused_deep_link_rendered_same_message");

  if (faults.length) throw new Error("browser_runtime_fault");
  report.status = "passed";
  report.fixture = {
    threadId: state.thread,
    conversationId: `sb:${state.thread}`,
    messageId: state.message,
    markerSha256: sha256(marker),
  };
} catch (error) {
  report.error = safeFailure(error);
} finally {
  if (config && state.thread) {
    const cleanup = { state: "completed", actions: [] };
    let cleanupFailed = false;
    try {
      cleanup.actions.push(...await logicalCleanup(config, state));
    } catch (error) {
      cleanupFailed = true;
      cleanup.error = safeFailure(error);
    }
    if (state.uniqueKey) {
      try {
        state.hardCleanup = await hardDeleteTemporaryThread(state.thread, state.uniqueKey);
        cleanup.actions.push("hard_deleted_temporary_thread");
        cleanup.actions.push("cleanup_verified_physical_residue_absent");
        cleanup.hardCleanup = state.hardCleanup;
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (cleanupFailed) {
      cleanup.state = "failed_or_incomplete";
      if (report.status === "passed") {
        report.status = "failed";
        report.error = cleanup.error ?? "cleanup_residue_detected";
      }
    }
    report.cleanup = cleanup;
  }
  for (const context of contexts) await context.close().catch(() => {});
  if (browser) await browser.close().catch(() => {});
  if (server) await server.close().catch(() => {});
  if (distribution) await rm(distribution, { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Chat favorites/focused Web evidence written: ${options.output}`);
}
if (report.status !== "passed") {
  console.error(`Chat favorites/focused Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat favorites/focused Web evidence passed.");
}
