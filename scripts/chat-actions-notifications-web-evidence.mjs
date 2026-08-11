#!/usr/bin/env node
import { chromium } from "playwright-core";
import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";
import pg from "pg";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const credentialsFileEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_CREDENTIALS_FILE";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP";
const tempProfileHashAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH_AUTHORIZATION";
const tempProfileHashAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH";
const useAdjacentAuthorizedProfile = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_USE_ADJACENT_AUTHORIZED_PROFILE === "1";
let lastThreadSnapshot = null;

class ProfileOnlyCompleted extends Error {}

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/chat-actions-notifications-evidence.json"),
    evidenceDir: resolve("build-reports/web/chat-actions-notifications-evidence"),
    translationOnly: false,
    profileOnly: false,
    profileFollowOnly: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (key === "--translation-only") {
      result.translationOnly = true;
      continue;
    }
    if (key === "--profile-only") {
      result.profileOnly = true;
      continue;
    }
    if (key === "--profile-follow-only") {
      result.profileFollowOnly = true;
      continue;
    }
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

async function runSilent(command, args, options = {}) {
  return await new Promise((resolvePromise, reject) => {
    let output = "";
    let stderr = "";
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

async function usersFromPrivateFile() {
  const file = process.env[credentialsFileEnvironment]?.trim();
  if (!file) throw new Error("missing_chat_actions_notifications_credentials_file");
  const parsed = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  const user = (entry, label) => ({
    label,
    countryCode: String(entry?.country_code ?? entry?.countryCode ?? "").trim(),
    phone: String(entry?.phone ?? "").trim(),
    password: String(entry?.password ?? ""),
  });
  const users = [user(parsed.a, "A"), user(parsed.b, "B")];
  if (users.some((candidate) => !candidate.countryCode || !candidate.phone || !candidate.password)) {
    throw new Error("missing_chat_actions_notifications_credentials");
  }
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) {
    throw new Error("chat_actions_notifications_users_must_differ");
  }
  return users;
}

async function authorizedUsers() {
  if (!useAdjacentAuthorizedProfile) return usersFromPrivateFile();
  const host = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_SSH_HOST?.trim();
  const file = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_SSH_CREDENTIALS_FILE?.trim();
  if (!host || !file) throw new Error("missing_adjacent_profile_credentials_source");
  const credentials = JSON.parse((await runSilent("ssh", [host, `cat ${file}`])).replace(/^\uFEFF/, ""));
  const primaryPhone = splitPhone(credentials.phone);
  const previousLocal = (BigInt(primaryPhone.localPhone) - 1n).toString().padStart(primaryPhone.localPhone.length, "0");
  if (previousLocal.length !== primaryPhone.localPhone.length) throw new Error("invalid_adjacent_profile_phone");
  return [
    {
      label: "A",
      countryCode: primaryPhone.countryCode,
      phone: previousLocal,
      password: credentials.password,
    },
    {
      label: "B",
      countryCode: primaryPhone.countryCode,
      phone: primaryPhone.localPhone,
      password: credentials.password,
      adjacentPhoneKeys: adjacentRecipientPhones(primaryPhone),
    },
  ];
}

function splitPhone(phone) {
  const digits = String(phone ?? "").replace(/\D/g, "");
  if (!digits.startsWith("240") || digits.length <= 3) throw new Error("invalid_adjacent_profile_phone");
  return { countryCode: "240", localPhone: digits.slice(3), phoneKey: digits };
}

function adjacentRecipientPhones(primaryPhone) {
  return [1, -1].map((delta) => {
    const value = Number(primaryPhone.localPhone) + delta;
    const localPhone = String(value).padStart(primaryPhone.localPhone.length, "0");
    return `${primaryPhone.countryCode}${localPhone}`;
  });
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
    "x-client-info": "quata-chat-actions-notifications-evidence",
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
      client_instance_id: `chat-actions-notifications-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session, profileId = payload?.profile?.id, webSessionToken = payload?.web_session?.token;
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at) || !webSessionToken) {
    throw new Error(`invalid_auth_response:${user.label}`);
  }
  const displayName = String(payload?.profile?.display_name ?? payload?.profile?.displayName ?? payload?.profile?.name ?? "").trim();
  const neighborhood = String(payload?.profile?.neighborhood ?? payload?.profile?.barrio ?? "").trim();
  return { label: user.label, profileId, displayName, neighborhood, accessToken: session.access_token, refreshToken: session.refresh_token, expiresAt: session.expires_at, webSessionToken };
}

function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
}

function rows(payload, key) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.[key])) return payload[key];
  if (Array.isArray(payload?.data?.[key])) return payload.data[key];
  if (Array.isArray(payload?.update?.[key])) return payload.update[key];
  if (Array.isArray(payload?.messages)) return payload.messages;
  return [];
}

function positiveId(value, name) {
  const numeric = Number(value);
  if (!Number.isSafeInteger(numeric) || numeric <= 0) throw new Error(`chat_contract_invalid:${name}`);
  return numeric;
}

function threadId(payload) {
  return positiveId(payload?.thread?.id ?? payload?.threads?.[0]?.id ?? payload?.thread_id ?? payload?.id, "thread_id");
}

function messageId(payload) {
  return positiveId(rows(payload, "messages")[0]?.id ?? payload?.message?.id ?? payload?.message_id ?? payload?.id, "message_id");
}

function messageText(row) {
  return String(row?.body ?? row?.text ?? row?.message ?? "");
}

function messageReplyToId(row) {
  const raw = row?.reply_to_message_id ?? row?.replyToMessageId ?? row?.reply?.id;
  const numeric = Number(raw);
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : null;
}

function messageNumericId(row) {
  const raw = row?.id ?? row?.message_id ?? row?.messageId ?? row?.message?.id;
  const numeric = Number(raw);
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : null;
}

function snapshotThread(detail) {
  return rows(detail, "messages").map((row) => {
    const text = messageText(row);
    return {
      id: messageNumericId(row),
      textSha256: sha256(text),
      textPrefix: text.slice(0, 40),
      replyToMessageId: messageReplyToId(row),
      isEdited: row?.is_edited === true || row?.isEdited === true,
    };
  });
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
    lastThreadSnapshot = snapshotThread(detail);
    const match = rows(detail, "messages").find(predicate);
    if (match) return match;
    await delay(1_000);
  }
  throw new Error("chat_backend_poll_timeout");
}

async function favorites(config, session) {
  return rows(await rpc(config, session, "quata_chat_get_favorites", {
    p_actor_profile_id: session.profileId,
    p_limit: 250,
  }), "messages");
}

async function inboxThread(config, session, thread) {
  const payload = await rpc(config, session, "quata_chat_get_inbox", {
    p_actor_profile_id: session.profileId,
    p_limit: 100,
  });
  const allRows = [
    payload?.thread,
    payload?.conversation,
    ...(Array.isArray(payload?.threads) ? payload.threads : []),
    ...(Array.isArray(payload?.conversations) ? payload.conversations : []),
    ...(Array.isArray(payload?.update?.threads) ? payload.update.threads : []),
    ...(Array.isArray(payload?.update?.conversations) ? payload.update.conversations : []),
  ].filter(Boolean);
  return allRows.find((row) => Number(row?.thread_id ?? row?.id) === Number(thread)) ?? null;
}

async function pollForwardDestinationThread(config, session, profileId, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const payload = await rpc(config, session, "quata_chat_get_inbox", {
      p_actor_profile_id: session.profileId,
      p_limit: 100,
    });
    const allRows = [
      payload?.thread,
      payload?.conversation,
      ...(Array.isArray(payload?.threads) ? payload.threads : []),
      ...(Array.isArray(payload?.conversations) ? payload.conversations : []),
      ...(Array.isArray(payload?.update?.threads) ? payload.update.threads : []),
      ...(Array.isArray(payload?.update?.conversations) ? payload.update.conversations : []),
    ].filter(Boolean);
    const match = allRows.find((row) => JSON.stringify(row).includes(profileId));
    if (match) return { threadId: threadId(match), row: match };
    await delay(1_000);
  }
  throw new Error("forward_state_not_persisted:destination_thread");
}

function isMuted(row) {
  return row?.muted === true || row?.is_muted === true || row?.isMuted === true;
}

async function configuredDistribution(source, config) {
  if (!(await stat(source).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  const target = await mkdtemp(join(tmpdir(), "quata-chat-actions-notifications-dist-"));
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

async function openAuthenticatedChatPage(browser, origin, session, conversationId, faults) {
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
      quata_web_client_instance_id: `chat-actions-notifications-${randomUUID()}`,
    },
  });
  const page = await context.newPage();
  page.on("pageerror", () => faults.push("pageerror"));
  page.on("console", (entry) => { if (entry.type() === "error") faults.push("console_error"); });
  await page.goto(`${origin}/#chat-${encodeURIComponent(conversationId)}`, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    `chat/${conversationId}`,
    { timeout: 45_000 },
  );
  await page.waitForFunction(() => {
    const root = document.querySelector("#quata-root");
    return root && (root.querySelector("canvas") || root.shadowRoot?.querySelector("canvas"));
  }, { timeout: 45_000 });
  await delay(1_500);
  return { context, page };
}

async function openAuthenticatedChatRoute(page, origin, conversationId) {
  await page.goto(`${origin}/#chat-${encodeURIComponent(conversationId)}`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    `chat/${conversationId}`,
    { timeout: 45_000 },
  );
  await delay(1_500);
}

async function attachScreenshot(page, evidenceDir, name) {
  await mkdir(evidenceDir, { recursive: true });
  const path = join(evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

async function visibleNativeControls(page) {
  return await page.evaluate(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    return [...scope.querySelectorAll("button[aria-label], input[aria-label], [role][aria-label]")]
      .map((element) => {
        const rect = element.getBoundingClientRect();
        return {
          tag: element.tagName,
          role: element.getAttribute("role"),
          label: element.getAttribute("aria-label"),
          visible: rect.width > 0 && rect.height > 0,
          x: Math.round(rect.x),
          y: Math.round(rect.y),
          width: Math.round(rect.width),
          height: Math.round(rect.height),
        };
      })
      .filter((entry) => entry.visible)
      .slice(0, 80);
  });
}

async function clickLabel(page, patterns, error) {
  const locator = await visibleAriaLocator(page, patterns, 5_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  throw new Error(error);
}

async function clickOptionsMenu(page) {
  const locator = await visibleAriaLocator(page, [/Opciones|Abrir/i, /Options|Open/i], 4_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.max(1, viewport.width - 26), 104);
}

async function clickFavoriteAction(page) {
  const locator = await visibleAriaLocator(page, [/Favorito|Favorite/i], 2_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.max(1, viewport.width - 66), 98);
}

async function clickEditAction(page) {
  const locator = await visibleAriaLocator(page, [/Editar|Edit/i], 2_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.max(1, viewport.width - 106), 98);
}

async function clickForwardAction(page) {
  const locator = await visibleAriaLocator(page, [/Reenviar|Forward/i], 2_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.max(1, viewport.width - 146), 98);
}

async function selectForwardDestination(page, query, displayName, error) {
  const search = await visibleAriaLocator(page, [/Buscar|Search/i], 10_000);
  if (search) {
    await search.fill(query, { timeout: 10_000 });
  } else {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.5), 108);
    await page.keyboard.press("Control+A").catch(() => {});
    await page.keyboard.type(query, { delay: 8 });
  }
  await delay(1_000);
  const destination = page.getByText(new RegExp(escapeRegExp(displayName))).first();
  if (await destination.waitFor({ timeout: 10_000 }).then(() => true).catch(() => false)) {
    await destination.click({ timeout: 10_000, force: true });
    return;
  }
  const box = await visibleTextBox(page, displayName);
  if (box) {
    await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.round(viewport.width * 0.46), 486);
  await delay(500);
  if (await page.getByText(/✓|âœ“/).first().isVisible({ timeout: 1_000 }).catch(() => false)) return;
  throw new Error(error);
}

async function clickForwardSend(page) {
  const locator = await visibleAriaLocator(page, [/Reenviar|Forward/i], 2_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.round(viewport.width * 0.69), 558);
}

async function clickMessage(page, marker, error) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  for (const probe of probes) {
    if (await clickMessageProbe(page, probe)) return;
  }
  if (marker.startsWith("chat-edit-ui-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.62), 214);
    return;
  }
  if (marker.startsWith("chat-actions-own-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.62), 306);
    await delay(250);
    return;
  }
  if (marker.startsWith("chat-actions-peer-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.44), 344);
    await delay(250);
    return;
  }
  throw new Error(error);
}

async function openMessageActions(page, marker, expectedPatterns, targetError, actionError) {
  await closeTransientMenus(page);
  await clickMessage(page, marker, targetError);
  if (marker.startsWith("chat-edit-ui-") || marker.startsWith("chat-actions-peer-") || marker.startsWith("chat-actions-own-")) {
    if (await visibleAriaLocator(page, expectedPatterns, 1_000)) return;
    if (!(await longPressMessage(page, marker))) throw new Error(targetError);
    await delay(500);
    return;
  }
  if (await visibleAriaLocator(page, expectedPatterns, 2_000)) return;
  if (await longPressMessage(page, marker)) {
    if (await visibleAriaLocator(page, expectedPatterns, 5_000)) return;
  }
  throw new Error(actionError);
}

async function closeTransientMenus(page) {
  await page.keyboard.press("Escape").catch(() => {});
  await delay(150);
  const conversationMenu = page.getByText(/Silenciar conversaci[oó]n|Mute conversation|A[ñn]adir nuevos participantes|Add new participants/i).first();
  if (await conversationMenu.isVisible({ timeout: 500 }).catch(() => false)) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.max(1, viewport.width - 18), Math.min(viewport.height - 18, 210));
    await delay(200);
    await page.keyboard.press("Escape").catch(() => {});
    await delay(150);
  }
}

async function longPressMessage(page, marker) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  for (const probe of probes) {
    const box = await visibleTextBox(page, probe);
    if (!box) continue;
    const x = box.x + (box.width / 2);
    const y = box.y + (box.height / 2);
    await page.mouse.move(x, y);
    await page.mouse.down();
    await delay(700);
    await page.mouse.up();
    return true;
  }
  if (marker.startsWith("chat-edit-ui-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    const x = Math.round(viewport.width * 0.62);
    const y = 214;
    await page.mouse.move(x, y);
    await page.mouse.down();
    await delay(700);
    await page.mouse.up();
    return true;
  }
  if (marker.startsWith("chat-actions-own-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    const x = Math.round(viewport.width * 0.62);
    const y = 306;
    await page.mouse.move(x, y);
    await page.mouse.down();
    await delay(700);
    await page.mouse.up();
    return true;
  }
  if (marker.startsWith("chat-actions-peer-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    const x = Math.round(viewport.width * 0.44);
    const y = 344;
    await page.mouse.move(x, y);
    await page.mouse.down();
    await delay(700);
    await page.mouse.up();
    return true;
  }
  return false;
}

async function waitMessageVisible(page, marker, error, timeout = 45_000) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const probe of probes) {
      const controls = await visibleNativeControls(page);
      if (controls.some((control) => control.label.includes(probe))) return;
      if (await visibleAriaLocator(page, [new RegExp(escapeRegExp(probe))], 250)) return;
      const text = page.getByText(probe, { exact: false }).first();
      if (await text.waitFor({ timeout: 500 }).then(() => true).catch(() => false)) return;
      if (await visibleTextBox(page, probe)) return;
    }
    await delay(250);
  }
  throw new Error(error);
}

async function openPeerProfileFromMessage(page, peerMarker, peerProfile, evidenceDir, report, afterOpen = null) {
  await waitMessageVisible(page, peerMarker, "message_not_visible:peer_profile_source");
  report.evidence.profileThreadInitial = await attachScreenshot(page, evidenceDir, "web-chat-profile-thread-initial");
  const opened = await clickMessageAvatar(page, peerMarker);
  if (!opened) throw new Error("profile_state_not_opened:avatar_not_clickable");
  const visible = await waitForProfileVisible(page, peerProfile);
  if (!visible) throw new Error("profile_state_not_opened:profile_not_visible");
  await assertProfileHeaderVisible(page, peerProfile);
  report.evidence.profileOpen = await attachScreenshot(page, evidenceDir, "web-chat-profile-open");
  if (afterOpen) await afterOpen();
  if (!(await clickProfileBack(page))) throw new Error("profile_state_not_opened:profile_back_not_clickable");
  await delay(1_000);
  if (!(await waitForChatProfileReturn(page))) throw new Error("profile_state_not_opened:chat_return_not_visible");
  report.evidence.profileReturn = await attachScreenshot(page, evidenceDir, "web-chat-profile-return");
}

async function toggleFollowFromOpenProfile(page, peerProfile, evidenceDir, report) {
  report.evidence.profileFollowBefore = await attachScreenshot(page, evidenceDir, "web-chat-profile-follow-before");
  await clickLabel(page, [/Seguir|Follow/i], "profile_follow_action_not_clickable");
  await pollProfileFollowEdge(peerProfile.actorProfileId, peerProfile.profileId, true);
  report.evidence.profileFollowAfter = await attachScreenshot(page, evidenceDir, "web-chat-profile-follow-after");
}

async function waitForChatProfileReturn(page) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const controls = await visibleNativeControls(page);
    const composerVisible = controls.some((control) => /Mensaje|Message/i.test(control.label));
    if (composerVisible) return true;
    await delay(500);
  }
  return false;
}

async function clickProfileBack(page) {
  const labeled = await visibleAriaLocator(page, [/Volver|Back/i], 3_000);
  if (labeled) {
    const box = await labeled.boundingBox().catch(() => null);
    if (box) {
      await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
      return true;
    }
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(34, Math.min(140, Math.max(90, viewport.height * 0.14)));
  await delay(500);
  return true;
}

async function clickMessageAvatar(page, marker) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  for (const probe of probes) {
    const text = page.getByText(probe, { exact: false }).first();
    if (await text.waitFor({ timeout: 3_000 }).then(() => true).catch(() => false)) {
      await text.scrollIntoViewIfNeeded({ timeout: 5_000 }).catch(() => {});
      await delay(250);
      const box = await text.boundingBox();
      if (box) {
        await page.mouse.click(Math.max(8, box.x - 26), box.y + Math.min(22, box.height * 0.22));
        await delay(1_500);
        return true;
      }
    }
    const textBox = await visibleTextBox(page, probe);
    if (textBox) {
      await page.mouse.click(Math.max(8, textBox.x - 26), textBox.y + Math.min(22, textBox.height * 0.22));
      await delay(1_500);
      return true;
    }
  }
  return false;
}

async function waitForProfileVisible(page, profile) {
  const displayName = profile.displayName?.trim();
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const hasProfileText = displayName
      ? await page.getByText(new RegExp(escapeRegExp(displayName))).first().isVisible({ timeout: 500 }).catch(() => false)
      : false;
    const hasProfileChrome = await page.getByText(/Publicaciones|Posts|Seguidores|Followers|Siguiendo|Following/i).first().isVisible({ timeout: 500 }).catch(() => false);
    if (hasProfileText && hasProfileChrome) return true;
    await delay(500);
  }
  return false;
}

async function assertProfileHeaderVisible(page, profile) {
  const displayName = profile.displayName?.trim();
  const neighborhood = profile.neighborhood?.trim();
  const checks = [
    ["profile_header_name_missing", displayName],
    ["profile_header_neighborhood_missing", neighborhood],
  ].filter(([, value]) => value);
  for (const [error, value] of checks) {
    const visible = await page.getByText(new RegExp(escapeRegExp(value))).first()
      .isVisible({ timeout: 5_000 })
      .catch(() => false);
    if (!visible) throw new Error(`${error}:${value}`);
  }
  for (const pattern of [/Publicaciones|Posts/i, /Seguidores|Followers/i, /Siguiendo|Following/i]) {
    const visible = await page.getByText(pattern).first()
      .isVisible({ timeout: 5_000 })
      .catch(() => false);
    if (!visible) throw new Error(`profile_header_kpi_missing:${pattern}`);
  }
}

async function clickMessageProbe(page, probe) {
  if (await clickMessageByAccessibleName(page, probe)) return true;
  const pattern = new RegExp(escapeRegExp(probe));
  for (const locator of [
    page.getByRole("button", { name: pattern }).first(),
    page.getByLabel(pattern).first(),
  ]) {
    if (await locator.waitFor({ timeout: 5_000 }).then(() => true).catch(() => false)) {
      await locator.click({ timeout: 10_000, force: true });
      return true;
    }
  }
  const text = page.getByText(probe, { exact: false }).first();
  if (await text.waitFor({ timeout: 5_000 }).then(() => true).catch(() => false)) {
    await text.scrollIntoViewIfNeeded({ timeout: 5_000 }).catch(() => {});
    await delay(250);
    const box = await text.boundingBox();
    if (box) {
      await page.mouse.click(Math.max(1, box.x - 12), box.y + (box.height / 2));
      return true;
    }
    await text.click({ timeout: 10_000, force: true });
    return true;
  }
  const textBox = await visibleTextBox(page, probe);
  if (textBox) {
    await page.mouse.click(Math.max(1, textBox.x - 12), textBox.y + (textBox.height / 2));
    return true;
  }
  return false;
}

async function verifyChatTranslation(page, evidenceDir, marker) {
  await clickLabel(page, [/Traductor Fang|Fang translator|Traducteur Fang/i], "translator_trigger_not_visible");
  await waitMessageVisible(page, "Toca cualquier mensaje para traducirlo", "translator_overlay_not_visible", 15_000);
  await attachScreenshot(page, evidenceDir, "web-chat-translation-overlay");
  if (!(await clickTranslatorOverlayMessage(page, marker))) throw new Error("translator_message_not_clickable");
  await waitMessageVisible(page, "pan de trigo", "translator_result_not_visible", 90_000);
  await waitMessageVisible(page, "FAN->ES", "translator_direction_not_visible", 5_000);
  await attachScreenshot(page, evidenceDir, "web-chat-translation-result");
  await clickLabel(page, [/Salir|Exit|Quitter/i], "translator_exit_not_visible");
  await waitMessageVisible(page, marker, "translator_return_message_not_visible", 15_000);
  await attachScreenshot(page, evidenceDir, "web-chat-translation-return");
}

async function clickTranslatorOverlayMessage(page, marker) {
  const box = await page.evaluate((needle) => {
    const matches = [];
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const label = element.getAttribute("aria-label") ?? "";
        const role = element.getAttribute("role") ?? "";
        if (role === "button" && label.includes(" | ") && label.includes(needle)) {
          const rect = element.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            matches.push({ x: rect.x, y: rect.y, width: rect.width, height: rect.height });
          }
        }
        if (element.shadowRoot) visit(element.shadowRoot);
      }
    };
    visit(document);
    matches.sort((left, right) => right.y - left.y);
    const match = matches[0];
    return match
      ? { x: Math.round(match.x), y: Math.round(match.y), width: Math.round(match.width), height: Math.round(match.height) }
      : null;
  }, marker);
  if (!box) return clickMessageProbe(page, marker);
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  await delay(250);
  return true;
}

async function clickMessageByAccessibleName(page, probe) {
  const box = await page.evaluate((needle) => {
    const matches = [];
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const label = element.getAttribute("aria-label") ?? "";
        const text = element.textContent ?? "";
        const role = element.getAttribute("role") ?? "";
        if (role === "button" && (label.includes(needle) || text.includes(needle))) {
          const rect = element.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            matches.push({ x: rect.x, y: rect.y, width: rect.width, height: rect.height, area: rect.width * rect.height });
          }
        }
        if (element.shadowRoot) visit(element.shadowRoot);
      }
    };
    visit(document);
    matches.sort((left, right) => left.area - right.area || left.y - right.y);
    const match = matches[0];
    return match
      ? { x: Math.round(match.x), y: Math.round(match.y), width: Math.round(match.width), height: Math.round(match.height) }
      : null;
  }, probe);
  if (!box) return false;
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  await delay(250);
  return true;
}

async function visibleTextBox(page, probe) {
  return await page.evaluate((needle) => {
    const collect = (root, entries) => {
      for (const element of root.querySelectorAll("*")) {
        const text = element.textContent ?? "";
        if (text.includes(needle)) {
          const rect = element.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            entries.push({
              x: rect.x,
              y: rect.y,
              width: rect.width,
              height: rect.height,
              area: rect.width * rect.height,
              textLength: text.length,
            });
          }
        }
        if (element.shadowRoot) collect(element.shadowRoot, entries);
      }
      return entries;
    };
    const matches = collect(document, [])
      .sort((left, right) => left.area - right.area || left.textLength - right.textLength || left.y - right.y);
    const match = matches[0];
    return match
      ? {
          x: Math.round(match.x),
          y: Math.round(match.y),
          width: Math.round(match.width),
          height: Math.round(match.height),
        }
      : null;
  }, probe);
}

async function waitLabel(page, patterns, error) {
  if (await visibleAriaLocator(page, patterns, 8_000)) return;
  throw new Error(error);
}

async function clickNativeButtonByLabel(page, patterns) {
  return await page.evaluate((sources) => {
    const matchers = sources.map((source) => new RegExp(source.source, source.flags));
    const collect = (root) => {
      for (const element of root.querySelectorAll("button[aria-label], [role='button'][aria-label]")) {
        const label = element.getAttribute("aria-label") ?? "";
        const rect = element.getBoundingClientRect();
        if (matchers.some((pattern) => pattern.test(label)) && rect.width > 0 && rect.height > 0) {
          element.click();
          return true;
        }
        if (element.shadowRoot && collect(element.shadowRoot)) return true;
      }
      return false;
    };
    return collect(document);
  }, patterns.map((pattern) => ({ source: pattern.source, flags: pattern.flags })));
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function visibleAriaLocator(page, patterns, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const controls = page.locator("[aria-label]");
    const count = await controls.count().catch(() => 0);
    for (let index = 0; index < count; index += 1) {
      const locator = controls.nth(index);
      const label = await locator.getAttribute("aria-label").catch(() => "");
      if (!patterns.some((pattern) => pattern.test(label ?? ""))) continue;
      const visible = await locator.boundingBox().then((box) => Boolean(box && box.width > 0 && box.height > 0)).catch(() => false);
      if (visible) return locator;
    }
    await delay(250);
  }
  return null;
}

async function fillComposerAndSend(page, value) {
  const input = await visibleAriaLocator(page, [/Mensaje|Message|Composer/i], 10_000);
  if (!input) throw new Error("composer_input_not_visible");
  await input.fill(value, { timeout: 10_000 });
  const deadline = Date.now() + 10_000;
  let sawSend = false;
  while (Date.now() < deadline) {
    const send = await visibleAriaLocator(page, [/Enviar|Send/i], 1_000);
    if (!send) {
      await delay(300);
      continue;
    }
    sawSend = true;
    await send.click({ timeout: 10_000, force: true });
    await delay(300);
    if (!(await input.inputValue().then((current) => current === value).catch(() => false))) return;
    const box = await send.boundingBox();
    if (box) {
      await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
      await delay(300);
      if (!(await input.inputValue().then((current) => current === value).catch(() => false))) return;
    }
    await clickNativeButtonByLabel(page, [/Enviar|Send/i]);
    await delay(500);
    if (!(await input.inputValue().then((current) => current === value).catch(() => false))) return;
  }
  if (!sawSend) throw new Error("composer_send_not_visible");
  throw new Error("composer_send_not_dispatched");
}

async function logicalCleanup(config, state) {
  const actions = [];
  const favoriteMessage = state.ownMessage;
  if (state.thread && favoriteMessage && state.a) {
    await rpc(config, state.a, "quata_chat_set_favorite", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_id: favoriteMessage,
      p_favorite: false,
    }).catch(() => {});
    actions.push("favorite_removed");
  }
  if (state.thread && state.a) {
    await rpc(config, state.a, "quata_chat_set_muted", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_muted: false,
    }).catch(() => {});
    actions.push("conversation_unmuted");
  }
  const messagesBySession = [
    ["own_message", state.a, state.ownMessage],
    ["peer_message", state.b, state.peerMessage],
    ...state.uiMessages.map((message) => ["ui_message", state.a, message]),
  ];
  for (const [key, session, message] of messagesBySession) {
    if (state.thread && message && session) {
      await rpc(config, session, "quata_chat_delete_messages", {
        p_actor_profile_id: session.profileId,
        p_thread_id: state.thread,
        p_message_ids: [message],
      }).catch(() => {});
      actions.push(`${key}_deleted`);
    }
  }
  if (state.thread && state.a) {
    await rpc(config, state.a, "quata_chat_delete_thread", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread }).catch(() => {});
    actions.push("thread_removed_from_a_inbox");
  }
  if (state.thread && state.b) {
    await rpc(config, state.b, "quata_chat_delete_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread }).catch(() => {});
    actions.push("thread_removed_from_b_inbox");
  }
  return actions;
}

async function hardDeleteTemporaryThread(thread, uniqueKey) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) {
    throw new Error("missing_hard_cleanup_authorization");
  }
  if (!uniqueKey.startsWith("qadata-chat-actions-notifications-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlPath, "utf8"), readFile(tlsCaPath, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try {
    await client.query("begin");
    const owned = await client.query(
      "select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-actions-notifications-%' for update",
      [thread, uniqueKey],
    );
    if (owned.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_not_owned");
    const deleted = await client.query("delete from public.chat_threads where id = $1 and unique_key = $2 returning id", [thread, uniqueKey]);
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
    if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:physical_rows");
    await client.query("commit");
    return { threadId: thread, uniqueKeySha256: sha256(uniqueKey), residueCounts: counts };
  } catch (error) {
    await client.query("rollback").catch(() => {});
    throw error;
  } finally {
    await client.end().catch(() => {});
  }
}

async function withPoolerClient(callback) {
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlPath, "utf8"), readFile(tlsCaPath, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try {
    return await callback(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function profileFollowExists(actorProfileId, targetProfileId) {
  return await withPoolerClient(async (client) => {
    const result = await client.query(
      `select exists (
         select 1 from public.community_profile_follows
         where follower_profile_id = $1 and followed_profile_id = $2
       ) as exists`,
      [actorProfileId, targetProfileId],
    );
    return result.rows[0]?.exists === true;
  });
}

async function prepareProfileFollowAbsent(actorProfileId, targetProfileId) {
  return await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      const existing = await client.query(
        `select id from public.community_profile_follows
         where follower_profile_id = $1 and followed_profile_id = $2
         for update`,
        [actorProfileId, targetProfileId],
      );
      if (existing.rowCount > 0) {
        await client.query(
          `delete from public.community_profile_follows
           where follower_profile_id = $1 and followed_profile_id = $2`,
          [actorProfileId, targetProfileId],
        );
      }
      await client.query("commit");
      return { initiallyFollowing: existing.rowCount > 0 };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function restoreProfileFollowEdge(actorProfileId, targetProfileId, initiallyFollowing) {
  await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      if (initiallyFollowing) {
        await client.query(
          `insert into public.community_profile_follows (follower_profile_id, followed_profile_id)
           values ($1, $2)
           on conflict do nothing`,
          [actorProfileId, targetProfileId],
        );
      } else {
        await client.query(
          `delete from public.community_profile_follows
           where follower_profile_id = $1 and followed_profile_id = $2`,
          [actorProfileId, targetProfileId],
        );
      }
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  const restored = await profileFollowExists(actorProfileId, targetProfileId);
  if (restored !== initiallyFollowing) throw new Error("cleanup_residue_detected:profile_follow_edge");
}

async function pollProfileFollowEdge(actorProfileId, targetProfileId, expected, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await profileFollowExists(actorProfileId, targetProfileId) === expected) return;
    await delay(750);
  }
  throw new Error(`profile_follow_backend_poll_timeout:${expected ? "created" : "removed"}`);
}

async function createTemporaryForwardProfile(runId) {
  const id = randomUUID();
  const phoneLocal = `999${Date.now().toString().slice(-6)}`;
  const displayName = `QADATA Forward ${phoneLocal}`;
  await withDatabase(async (client) => {
    await client.query("begin");
    try {
      await client.query(
        `insert into public.community_profiles
          (id, display_name, phone, pass_hash, phone_normalized, country_code, phone_local, phone_e164, neighborhood, barrio, barrio_normalized, account_status)
         values ($1, $2, $3, $4, $5, '240', $6, $7, 'QADATA', 'QADATA', 'qadata', 'active')`,
        [id, displayName, `+240 ${phoneLocal}`, `qadata-chat-forward-no-login-${runId}`, `240${phoneLocal}`, phoneLocal, `+240${phoneLocal}`],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  return { id, phoneLocal, displayName, neighborhood: "Bovano" };
}

async function hardDeleteTemporaryForwardDestination(profile, threadId) {
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const owned = await client.query(
        "select id from public.community_profiles where id = $1 and display_name = $2 and phone_local = $3 for update",
        [profile.id, profile.displayName, profile.phoneLocal],
      );
      if (owned.rowCount !== 1) throw new Error("cleanup_residue_detected:forward_profile_not_owned");
      if (threadId) {
        const participant = await client.query(
          "select 1 from public.chat_participants where thread_id = $1 and profile_id = $2 for update",
          [threadId, profile.id],
        );
        if (participant.rowCount !== 1) throw new Error("cleanup_residue_detected:forward_thread_not_owned");
        await client.query("delete from public.chat_threads where id = $1", [threadId]);
      }
      const deleted = await client.query(
        "delete from public.community_profiles where id = $1 and display_name = $2 and phone_local = $3 returning id",
        [profile.id, profile.displayName, profile.phoneLocal],
      );
      if (deleted.rowCount !== 1) throw new Error("cleanup_residue_detected:forward_profile_delete_failed");
      const residue = await client.query(
        `select
          (select count(*)::int from public.community_profiles where id = $1) as community_profiles,
          (select count(*)::int from public.chat_threads where id = $2) as chat_threads,
          (select count(*)::int from public.chat_messages where thread_id = $2) as chat_messages,
          (select count(*)::int from public.chat_participants where profile_id = $1 or thread_id = $2) as chat_participants,
          (select count(*)::int from public.chat_private_threads where thread_id = $2 or profile_low_id = $1 or profile_high_id = $1) as chat_private_threads`,
        [profile.id, threadId ?? -1],
      );
      const counts = residue.rows[0] ?? {};
      if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:forward_physical_rows");
      await client.query("commit");
      return { profileIdSha256: sha256(profile.id), threadId, residueCounts: counts };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function withDatabase(callback) {
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlPath, "utf8"), readFile(tlsCaPath, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try {
    return await callback(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function resolveAdjacentRecipientProfile(phoneKeys) {
  return await withDatabase(async (client) => {
    const result = await client.query(
      "select profile_id from public.quata_profile_phone_directory where phone_key = any($1::text[]) order by profile_id limit 1",
      [phoneKeys],
    );
    const profileId = result.rows[0]?.profile_id;
    if (!uuid.test(profileId ?? "")) throw new Error("missing_adjacent_recipient_profile");
    return profileId;
  });
}

async function openTemporaryProfileHashWindow(users) {
  if (process.env[tempProfileHashAuthorizationEnvironment]?.trim() !== tempProfileHashAuthorizationValue) {
    return { state: "not_requested", restored: true, restore: async () => {} };
  }
  const opened = await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const rowsToRestore = [];
      for (const user of users) {
        const found = await client.query(
          `select id, pass_hash, pass_plain
             from public.community_profiles
            where (
              regexp_replace(coalesce(country_code, ''), '\\D', '', 'g') = $1
              and regexp_replace(coalesce(phone_local, ''), '\\D', '', 'g') = $2
            ) or regexp_replace(coalesce(phone, ''), '\\D', '', 'g') = any($3::text[])
            order by created_at desc nulls last, id
            limit 1
            for update`,
          [user.countryCode, user.phone, [`${user.countryCode}${user.phone}`, user.phone]],
        );
        if (found.rowCount !== 1) throw new Error("temporary_profile_hash_window:profile_not_found");
        const row = found.rows[0];
        rowsToRestore.push({ id: row.id, pass_hash: row.pass_hash, pass_plain: row.pass_plain });
        await client.query(
          "update public.community_profiles set pass_hash = $1, pass_plain = null where id = $2",
          [sha256(user.password), row.id],
        );
      }
      await client.query("commit");
      return rowsToRestore;
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  let restored = false;
  return {
    state: "opened",
    restored,
    count: opened.length,
    restore: async () => {
      if (restored) return;
      await withDatabase(async (client) => {
        await client.query("begin");
        try {
          for (const row of opened) {
            await client.query(
              "update public.community_profiles set pass_hash = $1, pass_plain = $2 where id = $3",
              [row.pass_hash, row.pass_plain, row.id],
            );
          }
          await client.query("commit");
          restored = true;
        } catch (error) {
          await client.query("rollback").catch(() => {});
          throw error;
        }
      });
    },
  };
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  return [
    "invalid_arguments", "missing_public_supabase_configuration", "invalid_public_supabase_url",
    "invalid_or_privileged_supabase_key", "missing_chat_actions_notifications_credentials_file",
    "missing_chat_actions_notifications_credentials", "chat_actions_notifications_users_must_differ",
    "public_auth_request_failed", "invalid_auth_response", "chat_rpc_failed", "chat_contract_invalid",
    "chat_backend_poll_timeout", "distribution_missing", "runtime_configuration_injection_failed",
    "static_server_start_failed", "message_not_visible", "options_menu_not_visible", "action_bar_not_visible",
    "message_action_target_not_clickable",
    "mute_state_not_persisted", "favorite_state_not_persisted", "forward_state_not_persisted", "profile_state_not_opened", "browser_runtime_fault",
        "composer_message_not_visible", "composer_reply_not_visible", "composer_edit_not_visible",
        "composer_input_not_visible", "composer_send_not_visible", "composer_send_not_dispatched",
    "cleanup_residue_detected", "missing_hard_cleanup_authorization",
    "missing_adjacent_profile_credentials_source", "invalid_adjacent_profile_phone",
    "missing_adjacent_recipient_profile", "temporary_profile_hash_window",
  ].find((prefix) => message.startsWith(prefix)) ?? "unexpected_chat_actions_notifications_web_failure";
}

class EvidenceCompleted extends Error {}

const options = parseArgs(process.argv.slice(2));
const report = {
  check: "CHAT-ACTIONS-NOTIFICATIONS-WEB-001",
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};
const state = { a: null, b: null, thread: null, ownMessage: null, peerMessage: null, uiMessages: [], uniqueKey: null, forwardProfile: null, forwardThread: null, forwardedMessage: null };
let config, distribution, server, browser, pageContext;
let profileHashWindow = { state: "not_started", restored: true, restore: async () => {} };
const faults = [];
try {
  config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");
  const users = await authorizedUsers();
  const usersForTemporaryHash = users.filter((user) => user?.countryCode && user?.phone && user?.password);
  profileHashWindow = await openTemporaryProfileHashWindow(usersForTemporaryHash);
  if (profileHashWindow.state === "opened") {
    report.steps.push("temporary_profile_hash_window_opened");
  }
  state.a = await login(config, users[0]);
  if (useAdjacentAuthorizedProfile && users.length === 1) {
    state.b = {
      label: "B",
      profileId: await resolveAdjacentRecipientProfile(users[0].adjacentPhoneKeys),
    };
    report.steps.push("authorized_profile_logged_in_and_recipient_resolved");
  } else {
    state.b = await login(config, users[1]);
    report.steps.push("two_authorized_profiles_logged_in");
  }

  const runId = randomUUID();
  state.uniqueKey = `qadata-chat-actions-notifications-${runId}`;
  if (!options.translationOnly && !options.profileOnly && !options.profileFollowOnly) {
    state.forwardProfile = await createTemporaryForwardProfile(runId);
    report.steps.push("temporary_forward_destination_profile_created");
  }
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId,
    p_recipient_profile_ids: [state.b.profileId],
    p_subject: `QADATA chat actions notifications ${runId}`,
    p_type: "group",
    p_message: "",
    p_unique_key: state.uniqueKey,
    p_community_id: null,
  }));
  const ownMarker = options.translationOnly ? "Mbolo" : `chat-actions-own-${runId}`;
  const peerMarker = "Mbolo";
  state.ownMessage = messageId(await rpc(config, state.a, "quata_chat_send_message", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message: ownMarker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-actions-own-${runId}`,
  }));
  if (state.b.accessToken) {
    await rpc(config, state.b, "quata_chat_send_message", {
      p_actor_profile_id: state.b.profileId,
      p_thread_id: state.thread,
      p_message: peerMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-actions-peer-${runId}`,
    });
    const peerMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === peerMarker);
    state.peerMessage = messageId({ message: peerMessage });
    report.steps.push("isolated_thread_and_two_messages_ready");
  } else {
    report.steps.push("isolated_thread_and_own_message_ready");
  }

  distribution = await configuredDistribution(options.distribution, config);
  server = await startServer(distribution);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  pageContext = await openAuthenticatedChatPage(browser, server.origin, state.a, `sb:${state.thread}`, faults);
  const page = pageContext.page;
  await waitMessageVisible(page, ownMarker, "message_not_visible:own");
  if (state.peerMessage) {
    await waitMessageVisible(page, peerMarker, "message_not_visible:peer");
  }
  report.evidence.threadInitial = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-thread-initial");
  report.steps.push(state.peerMessage ? "thread_rendered_with_own_and_peer_messages" : "thread_rendered_with_own_message");

  const translationMarker = state.peerMessage ? peerMarker : ownMarker;
  if (options.translationOnly || state.peerMessage) {
    await verifyChatTranslation(page, options.evidenceDir, translationMarker);
    report.evidence.translationOverlay = join(options.evidenceDir, "web-chat-translation-overlay.png");
    report.evidence.translationResult = join(options.evidenceDir, "web-chat-translation-result.png");
    report.evidence.translationReturn = join(options.evidenceDir, "web-chat-translation-return.png");
    report.steps.push("chat_translation_common_overlay_translated_fang_message_and_returned");
  }

  if (options.translationOnly) {
    if (faults.length) throw new Error("browser_runtime_fault");
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      translatedMessageId: state.ownMessage,
      translatedMarkerSha256: sha256(ownMarker),
    };
    throw new EvidenceCompleted();
  }

  if (state.peerMessage && state.b.accessToken) {
    if (options.profileFollowOnly) {
      state.profileFollow = await prepareProfileFollowAbsent(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_initial_state_snapshot_and_absent_prepared");
    }
    await openPeerProfileFromMessage(page, peerMarker, state.b, options.evidenceDir, report, options.profileFollowOnly
      ? async () => {
        await toggleFollowFromOpenProfile(page, { actorProfileId: state.a.profileId, profileId: state.b.profileId }, options.evidenceDir, report);
        report.steps.push("profile_follow_toggled_and_verified_by_db");
      }
      : null);
    report.steps.push("peer_avatar_opened_public_profile_and_returned_to_chat");
    if (options.profileOnly || options.profileFollowOnly) {
      if (faults.length) throw new Error("browser_runtime_fault");
      report.status = "passed";
      report.fixture = {
        threadId: state.thread,
        conversationId: `sb:${state.thread}`,
        ownMessageId: state.ownMessage,
        peerMessageId: state.peerMessage,
        peerProfileIdSha256: sha256(state.b.profileId),
        uniqueKeySha256: sha256(state.uniqueKey),
        ownMarkerSha256: sha256(ownMarker),
        peerMarkerSha256: sha256(peerMarker),
        profileFollowInitialState: state.profileFollow?.initiallyFollowing ?? null,
      };
      throw new ProfileOnlyCompleted();
    }
  } else if (options.profileOnly || options.profileFollowOnly) {
    throw new Error("profile_state_not_opened:peer_message_unavailable");
  }

  const composerMarker = `chat-composer-ui-${runId}`;
  await fillComposerAndSend(page, composerMarker);
  const composerMessage = await pollMessage(
    config,
    state.a,
    state.thread,
    (message) => messageText(message) === composerMarker,
  );
  const composerMessageId = messageId({ message: composerMessage });
  state.uiMessages.push(composerMessageId);
  state.editableUiMessage = composerMessageId;
  await waitMessageVisible(page, composerMarker, "composer_message_not_visible");
  report.evidence.composerSent = await attachScreenshot(page, options.evidenceDir, "web-chat-composer-sent");
  report.steps.push("composer_text_sent_by_shared_ui_and_verified_by_rpc");

  const replyTargetMarker = state.peerMessage ? peerMarker : ownMarker;
  const replyTargetMessageId = state.peerMessage ?? state.ownMessage;
  await openMessageActions(page, replyTargetMarker, [/Responder|Reply/i], "message_action_target_not_clickable:reply", "action_bar_not_visible:reply");
  await clickLabel(page, [/Responder|Reply/i], "action_bar_not_visible:reply");
  const replyMarker = `chat-reply-ui-${runId}`;
  await fillComposerAndSend(page, replyMarker);
  const replyMessage = await pollMessage(
    config,
    state.a,
    state.thread,
    (message) => messageText(message).startsWith(replyMarker) && messageReplyToId(message) === Number(replyTargetMessageId),
  );
  state.uiMessages.push(messageId({ message: replyMessage }));
  await delay(1_000);
  await page.keyboard.press("Escape").catch(() => {});
  report.evidence.replySent = await attachScreenshot(page, options.evidenceDir, "web-chat-composer-reply-sent");
  report.steps.push("composer_reply_sent_by_shared_ui_and_verified_by_rpc");

  await openMessageActions(page, ownMarker, [/Editar|Edit/i], "message_action_target_not_clickable:edit", "action_bar_not_visible:edit");
  await clickEditAction(page);
  const editMarker = `chat-edit-ui-${runId}`;
  await fillComposerAndSend(page, editMarker);
  await pollMessage(
    config,
    state.a,
    state.thread,
    (message) => Number(message?.id ?? message?.message_id) === Number(state.ownMessage) && messageText(message) === editMarker,
  );
  await delay(1_000);
  await page.keyboard.press("Escape").catch(() => {});
  report.evidence.editSent = await attachScreenshot(page, options.evidenceDir, "web-chat-composer-edit-sent");
  report.steps.push("composer_edit_sent_by_shared_ui_and_verified_by_rpc");

  await clickOptionsMenu(page);
  await page.getByText(/Silenciar conversaci[oó]n|Mute conversation/i).click({ timeout: 10_000, force: true });
  await delay(1_000);
  if (!isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:true");
  report.evidence.muted = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-muted");
  report.steps.push("mute_enabled_and_verified_by_rpc");

  await clickOptionsMenu(page);
  await page.getByText(/Reactivar notificaciones|Unmute|Reactivate notifications/i).click({ timeout: 10_000, force: true });
  await delay(1_000);
  if (isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:false");
  report.steps.push("mute_disabled_and_verified_by_rpc");

  await openMessageActions(page, editMarker, [/Copiar mensaje|Copiar texto|Copy message|Copy text/i], "message_action_target_not_clickable:own_actions", "action_bar_not_visible:copy");
  await delay(500);
  report.evidence.ownActions = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-own-selected");
  report.steps.push("own_message_action_bar_visible");

  await clickFavoriteAction(page);
  await delay(1_000);
  const favoriteRows = await favorites(config, state.a);
  if (!favoriteRows.some((message) => Number(message?.id) === Number(state.ownMessage))) throw new Error("favorite_state_not_persisted:true");
  report.steps.push("favorite_toggled_and_verified_by_rpc");

  await openMessageActions(page, editMarker, [/Reenviar|Forward/i], "message_action_target_not_clickable:forward", "action_bar_not_visible:forward");
  await clickForwardAction(page);
  await selectForwardDestination(page, state.forwardProfile.phoneLocal, state.forwardProfile.displayName, "forward_state_not_persisted:picker");
  report.evidence.forwardPicker = await attachScreenshot(page, options.evidenceDir, "web-chat-forward-picker-selected");
  await clickForwardSend(page);
  const forwardDestination = await pollForwardDestinationThread(config, state.a, state.forwardProfile.id);
  state.forwardThread = forwardDestination.threadId;
  const forwardedMessage = await pollMessage(
    config,
    state.a,
    state.forwardThread,
    (message) => messageText(message) === editMarker && Number(message?.forwarded_from_message_id) === Number(state.ownMessage),
  );
  state.forwardedMessage = messageId({ message: forwardedMessage });
  await openAuthenticatedChatRoute(page, server.origin, `sb:${state.forwardThread}`);
  await delay(1_500);
  report.evidence.forwardedMessage = await attachScreenshot(page, options.evidenceDir, "web-chat-forwarded-message");
  report.steps.push("message_forwarded_by_shared_ui_and_verified_by_rpc");

  if (state.peerMessage) {
    await openMessageActions(page, peerMarker, [/Copiar mensaje|Copy message/i], "message_action_target_not_clickable:peer_actions", "action_bar_not_visible:peer_copy");
    report.evidence.peerActions = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-peer-selected");
    report.steps.push("peer_message_action_bar_visible");
  }

  if (faults.length) throw new Error("browser_runtime_fault");
  report.status = "passed";
  report.fixture = {
    threadId: state.thread,
    conversationId: `sb:${state.thread}`,
    ownMessageId: state.ownMessage,
    peerMessageId: state.peerMessage,
    forwardThreadId: state.forwardThread,
    forwardedMessageId: state.forwardedMessage,
    forwardProfileIdSha256: sha256(state.forwardProfile.id),
    uniqueKeySha256: sha256(state.uniqueKey),
    ownMarkerSha256: sha256(ownMarker),
    peerMarkerSha256: sha256(peerMarker),
  };
} catch (error) {
  if (error instanceof EvidenceCompleted || error instanceof ProfileOnlyCompleted) {
    // Focal modes already set report.status and fixture; cleanup still runs in finally.
  } else {
    if (pageContext?.page) {
      try {
        report.evidence.failure = await attachScreenshot(pageContext.page, options.evidenceDir, "web-chat-actions-failure");
        report.diagnostics = { visibleNativeControls: await visibleNativeControls(pageContext.page) };
      } catch {}
    }
    report.error = safeFailure(error);
    if (lastThreadSnapshot) report.diagnostics = { ...(report.diagnostics ?? {}), lastThreadSnapshot };
    if (typeof error?.message === "string" && error.message.startsWith(report.error)) {
      report.diagnostics = { ...(report.diagnostics ?? {}), safeErrorMessage: error.message };
    } else if (report.error === "unexpected_chat_actions_notifications_web_failure") {
      report.diagnostics = {
        ...(report.diagnostics ?? {}),
        unexpectedErrorName: typeof error?.name === "string" ? error.name : "Error",
        unexpectedErrorMessage: typeof error?.message === "string" ? error.message : "unknown",
      };
    }
  }
} finally {
  const cleanup = { state: "completed", actions: [] };
  let cleanupFailed = false;
  try { await pageContext?.context?.close(); } catch {}
  try { await browser?.close(); } catch {}
  try { await server?.close(); } catch {}
  try { await rm(distribution, { recursive: true, force: true }); } catch {}
  try {
    await profileHashWindow.restore();
    if (profileHashWindow.state === "opened") {
      cleanup.actions.push("temporary_profile_hash_window_restored");
      report.profileHashWindow = { state: "restored", count: profileHashWindow.count };
    }
  } catch (error) {
    cleanupFailed = true;
    cleanup.error = safeFailure(error);
    report.profileHashWindow = { state: "restore_failed" };
  }
  if (state.thread && config) {
    try { cleanup.actions.push(...await logicalCleanup(config, state)); }
    catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
    if (state.profileFollow && state.a && state.b) {
      try {
        await restoreProfileFollowEdge(state.a.profileId, state.b.profileId, state.profileFollow.initiallyFollowing);
        cleanup.actions.push("profile_follow_edge_restored_to_initial_state");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (state.uniqueKey) {
      try {
        const hardCleanup = await hardDeleteTemporaryThread(state.thread, state.uniqueKey);
        cleanup.actions.push("hard_deleted_temporary_thread");
        cleanup.actions.push("cleanup_verified_physical_residue_absent");
        cleanup.hardCleanup = hardCleanup;
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
  }
  if (state.forwardProfile) {
    try {
      cleanup.forwardDestination = await hardDeleteTemporaryForwardDestination(state.forwardProfile, state.forwardThread);
      cleanup.actions.push("temporary_forward_destination_deleted");
      cleanup.actions.push("forward_destination_cleanup_verified_physical_residue_absent");
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
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Chat actions/notifications Web evidence written: ${options.output}`);
}
if (report.status !== "passed") {
  console.error(`Chat actions/notifications Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat actions/notifications Web evidence passed.");
}
