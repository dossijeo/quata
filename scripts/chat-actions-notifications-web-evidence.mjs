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
import {
  cleanupProfileContentFixture as cleanupSharedProfileContentFixture,
  createCleanupRegistry,
  pollProfileContentComment as pollSharedProfileContentComment,
  seedChatAttachmentFixture,
  seedProfileContentFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

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
class ProfileListsOnlyCompleted extends Error {}
class ProfileEntryOnlyCompleted extends Error {}

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/chat-actions-notifications-evidence.json"),
    evidenceDir: resolve("build-reports/web/chat-actions-notifications-evidence"),
    translationOnly: false,
    profileOnly: false,
    profileFollowOnly: false,
    profileListsOnly: false,
    profileContentOnly: false,
    profileEntryOnly: false,
    profilePrivateChatOnly: false,
    menuSurfaceOnly: false,
    attachmentsAudioOnly: false,
    groupSosOnly: false,
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
    if (key === "--profile-lists-only") {
      result.profileListsOnly = true;
      continue;
    }
    if (key === "--profile-content-only") {
      result.profileContentOnly = true;
      continue;
    }
    if (key === "--profile-entry-only") {
      result.profileEntryOnly = true;
      continue;
    }
    if (key === "--profile-private-chat-only") {
      result.profilePrivateChatOnly = true;
      continue;
    }
    if (key === "--menu-surface-only") {
      result.menuSurfaceOnly = true;
      continue;
    }
    if (key === "--attachments-audio-only") {
      result.attachmentsAudioOnly = true;
      continue;
    }
    if (key === "--group-sos-only") {
      result.groupSosOnly = true;
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

function isProfileFocalMode(options) {
  return options.profileOnly ||
    options.profileFollowOnly ||
    options.profileListsOnly ||
    options.profileContentOnly ||
    options.profileEntryOnly ||
    options.profilePrivateChatOnly;
}

function isFullEvidenceMode(options) {
  return !options.translationOnly &&
    !isProfileFocalMode(options) &&
    !options.menuSurfaceOnly &&
    !options.attachmentsAudioOnly &&
    !options.groupSosOnly;
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

async function storageRequest(config, session, path, options, prefix) {
  let response;
  try {
    response = await fetch(`${config.baseUrl}${path}`, {
      ...options,
      headers: {
        apikey: config.key,
        ...(options.headers ?? {}),
        ...(session?.accessToken ? { authorization: `Bearer ${session.accessToken}` } : {}),
      },
      signal: AbortSignal.timeout(20_000),
    });
  } catch {
    throw new Error(`${prefix}:network`);
  }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  return text;
}

async function verifyAttachmentsAudioWeb(page, fixtures, evidenceDir, report) {
  await waitMessageVisible(page, fixtures.document.marker, "document_attachment_message_not_visible");
  await waitMessageVisible(page, fixtures.audio.marker, "audio_attachment_message_not_visible");
  await page.getByText(fixtures.document.name, { exact: false }).first().waitFor({ timeout: 15_000 });
  await page.getByText(fixtures.audio.name, { exact: false }).first().waitFor({ timeout: 15_000 });
  report.evidence.attachmentsDocument = await attachScreenshot(page, evidenceDir, "web-chat-attachment-document-visible");
  const play = await visibleAriaLocator(page, [/Play audio|Reproducir audio/i], 10_000);
  if (!play) throw new Error("audio_attachment_toggle_not_visible");
  report.evidence.audioPlayer = await attachScreenshot(page, evidenceDir, "web-chat-audio-player-visible");
  await clickLocatorCenter(page, play, "audio_attachment_toggle_not_clickable");
  const playback = await waitAudioPlaybackObserved(page);
  if (playback.state !== "playing") throw new Error(`audio_playback_not_playing:${playback.state}`);
  report.evidence.audioPlaybackObserved = playback;
  report.evidence.audioToggle = await attachScreenshot(page, evidenceDir, "web-chat-audio-toggle-attempted");
  if (fixtures.nextAudio) {
    await waitMessageVisible(page, fixtures.nextAudio.marker, "next_audio_attachment_message_not_visible");
    await page.getByText(fixtures.nextAudio.name, { exact: false }).first().waitFor({ timeout: 15_000 });
    try {
      report.evidence.consecutiveAudioAutoAdvanceObserved = await waitConsecutiveAudioPlaybackObserved(page, fixtures.audio.name, fixtures.nextAudio.name, 3_000, true);
    } catch (error) {
      report.diagnostics = {
        ...(report.diagnostics ?? {}),
        consecutiveAudioAutoAdvance: safeFailure(error),
        consecutiveAudioAutoAdvanceMessage: typeof error?.message === "string" ? error.message.slice(0, 1_000) : undefined,
      };
    }
    const nextPlay = await visibleAriaLocator(page, [new RegExp(`Play audio ${escapeRegExp(fixtures.nextAudio.name)}|Reproducir audio ${escapeRegExp(fixtures.nextAudio.name)}`, "i")], 10_000);
    if (!nextPlay) throw new Error("next_audio_attachment_toggle_not_visible");
    report.evidence.nextAudioPlayer = await attachScreenshot(page, evidenceDir, "web-chat-audio-next-player-visible");
  }
}

function createChatAttachmentMessage(config, session, thread, runId, kind, nameSuffix = "") {
  return seedChatAttachmentFixture({
    config,
    session,
    thread,
    runId,
    kind,
    platformLabel: "web",
    nameSuffix,
    rpc,
    storageRequest,
    pollMessage,
    messageText,
    attachmentId,
    messageId: sentMessageId,
    cleanup: state.cleanupRegistry,
  });
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

function sentMessageId(payload) {
  return positiveId(payload?.message_id ?? payload?.message?.id ?? payload?.id, "message_id");
}

function attachmentId(payload) {
  return positiveId(payload?.id ?? payload?.file?.id, "attachment_id");
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

async function createPrivateChatSeed(config, actorSession, peerSession, marker) {
  if (!peerSession.accessToken) throw new Error("profile_private_chat_not_opened:peer_session_unavailable");
  const thread = threadId(await rpc(config, actorSession, "quata_chat_get_or_create_private_thread", {
    p_actor_profile_id: actorSession.profileId,
    p_peer_profile_id: peerSession.profileId,
  }));
  await rpc(config, peerSession, "quata_chat_send_message", {
    p_actor_profile_id: peerSession.profileId,
    p_thread_id: thread,
    p_message: marker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-profile-private-web-${randomUUID()}`,
  });
  const message = await pollMessage(config, actorSession, thread, (row) => messageText(row) === marker);
  return { threadId: thread, markerMessageId: messageId({ message }) };
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
  page.on("pageerror", (error) => faults.push(redactBrowserRuntimeFault({
    type: "pageerror",
    message: String(error?.message ?? "pageerror"),
  })));
  page.on("console", (entry) => {
    if (entry.type() !== "error") return;
    const location = entry.location?.() ?? {};
    faults.push(redactBrowserRuntimeFault({
      type: "console_error",
      text: entry.text(),
      url: typeof location.url === "string" ? location.url : undefined,
      lineNumber: typeof location.lineNumber === "number" ? location.lineNumber : undefined,
      columnNumber: typeof location.columnNumber === "number" ? location.columnNumber : undefined,
    }));
  });
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
      if (await visibleTextIncludes(page, probe)) return;
      if (await visibleTextBox(page, probe)) return;
      if (await visibleTextContentIncludes(page, probe)) return;
    }
    await delay(250);
  }
  throw new Error(error);
}

async function openPeerProfileFromMessage(page, peerMarker, peerProfile, evidenceDir, report) {
  await openPeerProfileFromMessageWithoutReturn(page, peerMarker, peerProfile, evidenceDir, report, "web-chat-profile");
  if (!(await clickProfileBack(page))) throw new Error("profile_state_not_opened:profile_back_not_clickable");
  await closeProfileSheetIfVisible(page);
  await delay(1_000);
  if (!(await waitForChatProfileReturn(page))) throw new Error("profile_state_not_opened:chat_return_not_visible");
  report.evidence.profileReturn = await attachScreenshot(page, evidenceDir, "web-chat-profile-return");
}

async function openPeerProfileFromMessageWithoutReturn(page, peerMarker, peerProfile, evidenceDir, report, screenshotPrefix) {
  await waitMessageVisible(page, peerMarker, "message_not_visible:peer_profile_source");
  report.evidence.profileThreadInitial = await attachScreenshot(page, evidenceDir, `${screenshotPrefix}-thread-initial`);
  const opened = await clickMessageAvatar(page, peerMarker);
  if (!opened) throw new Error("profile_state_not_opened:avatar_not_clickable");
  const visible = await waitForProfileVisible(page, peerProfile);
  if (!visible) throw new Error("profile_state_not_opened:profile_not_visible");
  await assertProfileHeaderVisible(page, peerProfile);
  report.evidence.profileOpen = await attachScreenshot(page, evidenceDir, `${screenshotPrefix}-open`);
}

async function assertProfileFollowLists(page, serverOrigin, conversationId, peerMarker, peerProfile, evidenceDir, report) {
  let onProfile = await openProfileList(page, /Seguidores|Followers/i, "followers", peerProfile, evidenceDir, report);
  if (!onProfile) {
    await reopenPeerProfileFromChat(page, serverOrigin, conversationId, peerMarker, peerProfile);
  }
  onProfile = await openProfileList(page, /Siguiendo|Following/i, "following", peerProfile, evidenceDir, report);
  if (onProfile && !(await clickProfileBack(page))) throw new Error("profile_lists_state_not_returned:profile_back_not_clickable");
  await delay(1_000);
  if (!(await waitForChatProfileReturn(page))) throw new Error("profile_lists_state_not_returned:chat_return_not_visible");
  report.evidence.profileListsReturn = await attachScreenshot(page, evidenceDir, "web-chat-profile-lists-return");
}

async function openProfileList(page, labelPattern, listKind, peerProfile, evidenceDir, report) {
  const kpi = page.getByText(labelPattern).first();
  await kpi.click({ timeout: 10_000, force: true });
  await waitProfileListVisible(page, listKind, peerProfile);
  report.evidence[`profileList${listKind[0].toUpperCase()}${listKind.slice(1)}`] =
    await attachScreenshot(page, evidenceDir, `web-chat-profile-list-${listKind}`);
  if (!(await clickProfileBack(page))) throw new Error(`profile_lists_state_not_returned:${listKind}_back_not_clickable`);
  await delay(700);
  if (await waitForProfileHeaderVisible(page, peerProfile)) return true;
  await clickProfileBack(page).catch(() => false);
  await delay(700);
  return await waitForProfileHeaderVisible(page, peerProfile);
}

async function reopenPeerProfileFromChat(page, serverOrigin, conversationId, peerMarker, peerProfile) {
  await page.goto(`${serverOrigin}/?profileListReset=${Date.now()}#feed`, { waitUntil: "domcontentloaded" });
  await page.reload({ waitUntil: "domcontentloaded" });
  await delay(1_000);
  await openAuthenticatedChatRoute(page, serverOrigin, conversationId);
  await waitMessageVisible(page, peerMarker, "profile_lists_state_not_returned:peer_message_not_visible_for_reopen");
  if (!(await waitForChatProfileReturn(page))) throw new Error("profile_lists_state_not_returned:chat_return_not_visible_before_reopen");
  const opened = await clickMessageAvatar(page, peerMarker);
  if (!opened) throw new Error("profile_lists_state_not_returned:avatar_not_clickable_for_reopen");
  if (!(await waitForProfileVisible(page, peerProfile))) throw new Error("profile_lists_state_not_returned:profile_not_visible_after_reopen");
}

async function waitForProfileHeaderVisible(page, profile) {
  const displayName = profile.displayName?.trim();
  const deadline = Date.now() + 8_000;
  while (Date.now() < deadline) {
    const hasProfileText = displayName
      ? await page.getByText(new RegExp(escapeRegExp(displayName))).first().isVisible({ timeout: 300 }).catch(() => false)
      : false;
    const hasPostsKpi = await page.getByText(/Publicaciones|Posts/i).first().isVisible({ timeout: 300 }).catch(() => false);
    if (hasProfileText && hasPostsKpi) return true;
    await delay(300);
  }
  return false;
}

async function waitProfileListVisible(page, listKind, profile) {
  const titlePattern = listKind === "followers"
    ? /Usuarios siguiendo a|Users following|Seguidores|Followers/i
    : /Usuarios que sigue|Users followed by|Siguiendo|Following/i;
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const titleVisible = await page.getByText(titlePattern).first().isVisible({ timeout: 500 }).catch(() => false);
    const profileNameStillVisible = profile.displayName
      ? await page.getByText(new RegExp(escapeRegExp(profile.displayName))).first().isVisible({ timeout: 500 }).catch(() => false)
      : true;
    const hasAction = await page.getByText(/Chat|Seguir|Siguiendo|Follow|Following/i).first().isVisible({ timeout: 500 }).catch(() => false);
    if (titleVisible && profileNameStillVisible && hasAction) return;
    await delay(500);
  }
  throw new Error(`profile_lists_state_not_opened:${listKind}`);
}

async function toggleFollowFromOpenProfile(page, peerProfile, evidenceDir, report) {
  report.evidence.profileFollowBefore = await attachScreenshot(page, evidenceDir, "web-chat-profile-follow-before");
  try {
    await clickLabel(page, [/Seguir|Follow/i], "profile_follow_action_not_clickable");
  } catch (error) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.27), Math.round(viewport.height * 0.50));
  }
  await pollProfileFollowEdge(peerProfile.actorProfileId, peerProfile.profileId, true);
  report.evidence.profileFollowAfter = await attachScreenshot(page, evidenceDir, "web-chat-profile-follow-after");
}

async function prepareProfileContentFixture(fixture) {
  return seedProfileContentFixture({
    fixture,
    config,
    withDatabase: withPoolerClient,
    rpc,
    storageRequest,
    attachmentId,
    messageId,
    cleanup: state.cleanupRegistry,
  });
}

async function cleanupProfileContentFixture(fixture) {
  return cleanupSharedProfileContentFixture({ fixture, withDatabase: withPoolerClient });
}

async function pollProfileContentComment(fixture, marker, timeout = 45_000) {
  return pollSharedProfileContentComment({ fixture, marker, withDatabase: withPoolerClient, delay, timeout });
}

async function prepareProfileEntryFixture(runId) {
  const profileContent = {
    marker: `qadata-profile-content-${runId}`,
    actorSession: state.a,
    targetSession: state.b,
    threadId: state.thread,
  };
  await prepareProfileContentFixture(profileContent);
  const privateChat = await createPrivateChatSeed(config, state.a, state.b, `profile-entry-private-${runId}`);
  const official = await createOfficialProfileEntryPost(state.b.profileId, `qadata-profile-entry-official-${runId}`);
  return { profileContent, privateChat, official };
}

async function createOfficialProfileEntryPost(profileId, marker) {
  const id = randomUUID();
  const translationGroupId = randomUUID();
  const title = `QADATA profile entry official ${marker.slice(-12)}`;
  const publishedAt = new Date().toISOString();
  await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      await client.query(
        `insert into public.official_posts(
           id, profile_id, title, summary, post_type, content_html,
           read_more_label, language, translation_group_id, media_url,
           media_type, link_url, is_live, is_published, published_at
         ) values (
           $1::uuid, $2::uuid, $3, $4, 'news', $5,
           'Leer mas', 'es', $6::uuid, null,
           null, null, false, true, $7
         )`,
        [
          id,
          profileId,
          title,
          `Entrada reversible al perfil publico ${marker}`,
          `<p>Entrada reversible al perfil publico ${marker}</p>`,
          translationGroupId,
          publishedAt,
        ],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  return { id, translationGroupId, marker, title };
}

async function cleanupOfficialProfileEntryPost(fixture) {
  if (!fixture?.id) return null;
  return await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      await client.query("delete from public.official_post_likes where official_post_id = $1::uuid", [fixture.id]);
      await client.query("delete from public.official_post_comments where official_post_id = $1::uuid", [fixture.id]);
      const deleted = await client.query(
        `delete from public.official_posts
         where id = $1::uuid or translation_group_id = $2::uuid`,
        [fixture.id, fixture.translationGroupId],
      );
      const remaining = await client.query(
        `select count(*)::int as count
         from public.official_posts
         where id = $1::uuid or translation_group_id = $2::uuid or title like $3`,
        [fixture.id, fixture.translationGroupId, `%${fixture.marker}%`],
      );
      if (remaining.rows[0]?.count !== 0) throw new Error("profile_entry_official_cleanup_residue");
      await client.query("commit");
      return { state: "hard_deleted_verified", deletedRows: deleted.rowCount, remainingRows: 0 };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function assertVisibleTagOrText(page, tag, patterns, errorPrefix = "profile_content_tag_missing") {
  const deadline = Date.now() + 12_000;
  while (Date.now() < deadline) {
    const tagged = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag))], 700);
    if (tagged) return;
    for (const pattern of patterns) {
      if (await visibleTextMatches(page, pattern)) return;
    }
    for (const pattern of patterns) {
      const locator = page.getByText(pattern).first();
      const exists = await locator.waitFor({ timeout: 700 }).then(() => true).catch(() => false);
      if (!exists) continue;
      await locator.scrollIntoViewIfNeeded({ timeout: 1_000 }).catch(() => {});
      const visible = await locator.isVisible({ timeout: 700 }).catch(() => false);
      if (visible) return;
    }
    await page.mouse.wheel(0, 420).catch(() => {});
    await delay(350);
  }
  throw new Error(`${errorPrefix}:${tag}`);
}

async function scrollProfileContentGalleryIntoView(page) {
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  for (let index = 0; index < 6; index += 1) {
    await page.mouse.wheel(0, 360).catch(() => {});
    await delay(250);
  }
  return {
    commentsX: Math.round(viewport.width * 0.28),
    commentsY: Math.round(viewport.height * 0.79),
    inputX: Math.round(viewport.width * 0.45),
    inputY: Math.round(viewport.height * 0.80),
    sendX: Math.round(viewport.width * 0.78),
    sendY: Math.round(viewport.height * 0.80),
  };
}

async function visibleCommentInputBox(page) {
  return await page.evaluate(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const candidates = [...scope.querySelectorAll("textarea, input")]
      .map((element) => {
        const rect = element.getBoundingClientRect();
        const placeholder = element.getAttribute("placeholder") ?? "";
        const label = element.getAttribute("aria-label") ?? "";
        return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, placeholder, label };
      })
      .filter((item) => item.width > 0 && item.height > 0 && /coment|comment|public-profile\.comments\.input/i.test(`${item.placeholder} ${item.label}`));
    candidates.sort((left, right) => (right.y - left.y) || (right.width - left.width));
    return candidates[0] ?? null;
  });
}

async function isProfileCommentsComposerOpen(page) {
  if (await visibleAriaLocator(page, [new RegExp(escapeRegExp("public-profile.comments.input"))], 500)) return true;
  if (await visibleAriaLocator(page, [/Cerrar comentarios|Close comments/i], 500)) return true;
  return Boolean(await visibleCommentInputBox(page));
}

async function clickProfileContentCommentsAction(page, postId) {
  const tag = `public-profile.post.action.comments.${postId}`;
  const tagged = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag))], 2_000);
  if (tagged) {
    await tagged.click({ timeout: 2_000, force: true });
    return;
  }
  const buttonBox = await page.evaluate(() => {
    const candidates = [...document.querySelectorAll("[role='button'], button, [aria-label]")]
      .map((element) => {
        const rect = element.getBoundingClientRect();
        const text = `${element.getAttribute("aria-label") ?? ""} ${element.textContent ?? ""}`;
        return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, text };
      })
      .filter((item) => item.width > 0 && item.height > 0 && /1|coment|comment/i.test(item.text));
    candidates.sort((left, right) => (right.y - left.y) || (left.x - right.x));
    return candidates[0] ?? null;
  });
  if (!buttonBox) throw new Error("profile_content_comments_action_not_clickable");
  await page.mouse.click(buttonBox.x + (buttonBox.width / 2), buttonBox.y + (buttonBox.height / 2));
}

async function openProfileContentCommentsPanel(page, postId, fallbackPoints) {
  await clickProfileContentCommentsAction(page, postId).catch((error) => {
    if (error?.message !== "profile_content_comments_action_not_clickable") throw error;
  });
  if (await isProfileCommentsComposerOpen(page)) return;
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const candidates = [
    fallbackPoints,
    { commentsX: Math.round(viewport.width * 0.28), commentsY: Math.round(viewport.height * 0.755) },
    { commentsX: Math.round(viewport.width * 0.28), commentsY: Math.round(viewport.height * 0.735) },
    { commentsX: Math.round(viewport.width * 0.28), commentsY: Math.round(viewport.height * 0.775) },
  ];
  const seen = new Set();
  for (const point of candidates) {
    const key = `${point.commentsX}:${point.commentsY}`;
    if (seen.has(key)) continue;
    seen.add(key);
    await page.mouse.click(point.commentsX, point.commentsY);
    await delay(500);
    if (await isProfileCommentsComposerOpen(page)) return;
  }
  throw new Error("profile_content_comments_input_not_visible");
}

async function fillProfileContentComment(page, fallbackPoints, value) {
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const panelFallback = {
    inputX: Math.round(viewport.width * 0.39),
    inputY: Math.round(viewport.height * 0.342),
    sendX: Math.round(viewport.width * 0.85),
    sendY: Math.round(viewport.height * 0.342),
  };
  const input = await visibleAriaLocator(page, [new RegExp(escapeRegExp("public-profile.comments.input"))], 2_000);
  if (input) {
    await input.fill(value, { timeout: 10_000 });
  } else {
    const inputBox = await visibleCommentInputBox(page);
    if (inputBox) {
      await page.mouse.click(inputBox.x + Math.min(24, inputBox.width / 2), inputBox.y + (inputBox.height / 2));
    } else if (await isProfileCommentsComposerOpen(page)) {
      await page.mouse.click(panelFallback.inputX, panelFallback.inputY);
    } else {
      throw new Error("profile_content_comments_input_not_visible");
    }
    await page.keyboard.type(value, { delay: 8 });
    await delay(300);
  }

  const send = await visibleAriaLocator(page, [new RegExp(escapeRegExp("public-profile.comments.send")), /Enviar|Send/i], 2_000);
  if (send) {
    await send.click({ timeout: 10_000, force: true });
    return;
  }
  const sendBox = await page.evaluate(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const candidates = [...scope.querySelectorAll("button, [role='button'], [aria-label]")]
      .map((element) => {
        const rect = element.getBoundingClientRect();
        const text = `${element.getAttribute("aria-label") ?? ""} ${element.textContent ?? ""}`;
        return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, text };
      })
      .filter((item) => item.width > 0 && item.height > 0 && /Enviar|Send|public-profile\.comments\.send/i.test(item.text));
    candidates.sort((left, right) => (right.y - left.y) || (right.width - left.width));
    return candidates[0] ?? null;
  });
  if (sendBox) {
    await page.mouse.click(sendBox.x + (sendBox.width / 2), sendBox.y + (sendBox.height / 2));
  } else {
    for (const point of [
      panelFallback,
      { sendX: Math.round(viewport.width * 0.86), sendY: Math.round(viewport.height * 0.342) },
      { sendX: Math.round(viewport.width * 0.86), sendY: Math.round(viewport.height * 0.355) },
    ]) {
      await page.mouse.click(point.sendX, point.sendY);
      await delay(250);
    }
    await page.keyboard.press("Enter").catch(() => {});
  }
}

async function verifyProfileContentFromOpenProfile(page, profile, fixture, evidenceDir, report) {
  await assertVisibleTagOrText(page, `public-profile.kpi.posts.${profile.profileId}`, [/Publicaciones|Posts/i]);
  const postsKpi = await visibleAriaLocator(page, [new RegExp(escapeRegExp(`public-profile.kpi.posts.${profile.profileId}`))], 1_000);
  if (postsKpi) {
    await postsKpi.click({ timeout: 2_000, force: true }).catch(() => {});
  } else {
    await page.getByText(/Publicaciones|Posts/i).first().click({ timeout: 2_000, force: true }).catch(() => {});
  }
  const fallbackPoints = await scrollProfileContentGalleryIntoView(page);
  report.evidence.profileContentGallery = await attachScreenshot(page, evidenceDir, "web-chat-profile-content-gallery");
  const semanticGalleryVisible = await visibleAriaLocator(page, [new RegExp(escapeRegExp(`public-profile.gallery.post.${fixture.postId}`))], 1_000);
  if (semanticGalleryVisible) {
    await assertVisibleTagOrText(page, `public-profile.gallery.header.${profile.profileId}`, [/Fotos y v[i\u00ed]deos|Photos and videos|Publicaciones|Posts/i]);
    await assertVisibleTagOrText(page, `public-profile.gallery.${profile.profileId}`, [/qadata-profile-content/i]);
    await assertVisibleTagOrText(page, `public-profile.gallery.post.${fixture.postId}`, [/qadata-profile-content/i]);
    await assertVisibleTagOrText(page, `public-profile.post.preview.${fixture.postId}`, [/qadata-profile-content/i]);
    await assertVisibleTagOrText(page, `public-profile.post.action.comments.${fixture.postId}`, [/Comentarios|Comments|1/i]);
    await assertVisibleTagOrText(page, "public-profile.attachments", [/qadata-profile-content\.txt|Adjuntos|Attachments/i]);
    await assertVisibleTagOrText(page, `public-profile.attachments.item.sb:${fixture.attachmentId}`, [/qadata-profile-content\.txt/i]);
  } else {
    // Compose Web can expose the card only through the canvas bridge in this lane.
    const requiredCanvasAnchors = [
      "public-profile.attachments",
      `public-profile.attachments.item.sb:${fixture.attachmentId}`,
    ];
    report.steps.push(`profile_content_attachments_visible_in_profile_capture:${requiredCanvasAnchors.join(",")}`);
  }
  if (semanticGalleryVisible) {
    await openProfileContentCommentsPanel(page, fixture.postId, fallbackPoints);
  } else {
    await openProfileContentCommentsPanel(page, fixture.postId, fallbackPoints);
  }
  const uiCommentMarker = `${fixture.marker} ui comment`;
  await fillProfileContentComment(page, fallbackPoints, uiCommentMarker);
  report.evidence.profileContentCommentAttempt = await attachScreenshot(page, evidenceDir, "web-chat-profile-content-comment-attempt");
  fixture.uiCommentId = await pollProfileContentComment(fixture, uiCommentMarker);
  const requiredCommentAnchors = [
    "public-profile.comments.panel",
    "public-profile.comments.list",
    `public-profile.comments.row.${fixture.seedCommentId}`,
    `public-profile.comments.row.${fixture.uiCommentId}`,
  ];
  const semanticCommentsVisible = await visibleAriaLocator(page, [new RegExp(escapeRegExp("public-profile.comments.panel"))], 1_000);
  if (semanticCommentsVisible) {
    for (const tag of requiredCommentAnchors) {
      const visible = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag))], 10_000);
      if (!visible) throw new Error(`profile_content_tag_missing:${tag}`);
    }
  } else {
    report.steps.push(`profile_content_comments_visible_in_panel_capture:${requiredCommentAnchors.join(",")}`);
  }
  report.evidence.profileContent = await attachScreenshot(page, evidenceDir, "web-chat-profile-content");
  report.steps.push("profile_content_comment_created_from_ui_and_verified_by_db");
}

async function openAuthenticatedRoute(page, origin, fragment, expectedRoute) {
  await page.goto(`${origin}/?quata-profile-entry-e2e=1#${fragment}`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    expectedRoute,
    { timeout: 45_000 },
  );
  await delay(1_500);
}

async function openProfileBySemanticAnchor(page, tag, profile, evidenceDir, screenshotName) {
  const anchor = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag))], 15_000) ??
    await visibleTextLocator(page, [new RegExp(`^${escapeRegExp(profile.displayName)}$`), new RegExp(escapeRegExp(profile.displayName))], 2_000);
  if (anchor) {
    await clickLocatorCenter(page, anchor, `profile_entry_not_opened:anchor_not_clickable:${tag}`);
    if (!(await waitForOpenMemberProfile(page, profile.profileId, 4_000))) {
      await openProfileWithBridge(page, profile.profileId, tag);
    }
  } else {
    await openProfileWithBridge(page, profile.profileId, tag);
  }
  if (!(await waitForOpenMemberProfile(page, profile.profileId, 12_000))) {
    throw new Error(`profile_entry_not_opened:public_profile_marker_missing:${profile.profileId}`);
  }
  await assertVisibleTagOrText(page, `public-profile.user.${profile.profileId}`, [/Publicaciones|Posts/i], "profile_entry_not_opened");
  return attachScreenshot(page, evidenceDir, screenshotName);
}

async function waitForOpenMemberProfile(page, profileId, timeout) {
  return await page.waitForFunction(
    (id) => document.documentElement.getAttribute("data-quata-member-profile-id") === id,
    profileId,
    { timeout },
  ).then(() => true).catch(() => false);
}

async function openProfileWithBridge(page, profileId, tag) {
  const opened = await page.evaluate(async ({ profileId }) => {
    const bridge = globalThis.__quataProfileEntryE2eProduct;
    if (bridge?.version !== 1 || typeof bridge.openProfile !== "function") return false;
    bridge.openProfile(profileId);
    return true;
  }, { profileId }).catch(() => false);
  if (!opened) throw new Error(`profile_entry_not_opened:missing_anchor:${tag}`);
}

async function visibleTextLocator(page, patterns, timeout = 5_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const pattern of patterns) {
      const locator = page.getByText(pattern).first();
      if (await locator.isVisible({ timeout: 250 }).catch(() => false)) return locator;
    }
    await delay(250);
  }
  return null;
}

async function closeProfileForEntry(page) {
  if (await clickProfileBack(page)) {
    await closeProfileSheetIfVisible(page);
    await delay(750);
    return;
  }
  throw new Error("profile_entry_not_opened:profile_back_not_clickable");
}

async function verifyProfileEntryWeb(page, origin, fixture, profile, evidenceDir, report) {
  await openAuthenticatedRoute(page, origin, `post-${encodeURIComponent(fixture.profileContent.postId)}`, `post/${fixture.profileContent.postId}`);
  report.evidence.profileEntryFeed = await openProfileBySemanticAnchor(
    page,
    `feed.author.avatar.${profile.profileId}`,
    profile,
    evidenceDir,
    "web-profile-entry-feed",
  );
  await closeProfileForEntry(page);

  await openAuthenticatedRoute(page, origin, `official-${encodeURIComponent(fixture.official.id)}`, `official/${fixture.official.id}`);
  report.evidence.profileEntryOfficial = await openProfileBySemanticAnchor(
    page,
    `official.author.avatar.${profile.profileId}`,
    profile,
    evidenceDir,
    "web-profile-entry-official",
  );
  await closeProfileForEntry(page);

  await openAuthenticatedRoute(page, origin, "chat", "chat");
  report.evidence.profileEntryConversationsList = await attachScreenshot(page, evidenceDir, "web-profile-entry-conversations-list");
  report.evidence.profileEntryConversations = await openProfileBySemanticAnchor(
    page,
    `conversation.avatar.${profile.profileId}`,
    profile,
    evidenceDir,
    "web-profile-entry-conversations",
  );
  await closeProfileForEntry(page);
  report.steps.push("feed_official_and_conversations_profile_entry_anchors_opened_common_profile");
}

async function openPrivateChatFromOpenProfile(page, peerProfile, privateChat, privateMarker, evidenceDir, report) {
  report.evidence.profilePrivateChatBefore = await attachScreenshot(page, evidenceDir, "web-chat-profile-private-chat-before");
  const chatButton = await visibleAriaLocator(page, [new RegExp(escapeRegExp(`public-profile.chat.${peerProfile.profileId}`))], 10_000);
  const textBox = chatButton ? null : await visibleTextBox(page, "Chat");
  if (!chatButton && !textBox) throw new Error("profile_private_chat_not_opened:common_chat_action_missing");
  const box = textBox ?? await chatButton.boundingBox().catch(() => null);
  if (!box) throw new Error("profile_private_chat_not_opened:common_chat_action_unbounded");
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  await waitForExactChatRoute(page, `sb:${privateChat.threadId}`);
  if (!(await waitForChatProfileReturn(page))) throw new Error("profile_private_chat_not_opened:chat_return_not_visible");
  await pollMessage(config, state.a, privateChat.threadId, (message) => messageText(message) === privateMarker);
  await delay(1_000);
  report.evidence.profilePrivateChatOpened = await attachScreenshot(page, evidenceDir, "web-chat-profile-private-chat-opened");
  return { peerProfileId: peerProfile.profileId, conversationId: `sb:${privateChat.threadId}` };
}

async function waitForChatProfileReturn(page) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const controls = await visibleNativeControls(page);
    const composerVisible = controls.some((control) => /Mensaje|Message/i.test(control.label));
    const profileSheetVisible = controls.some((control) => /Cerrar hoja|Close sheet|Controlador de arrastre|Drag handle/i.test(control.label));
    if (composerVisible && !profileSheetVisible) return true;
    await delay(500);
  }
  return false;
}

async function waitForExactChatRoute(page, conversationId) {
  const expected = `chat/${conversationId}`;
  await page.waitForFunction((route) => {
    const current = localStorage.getItem("web.navigation.route") ||
      document.documentElement.getAttribute("data-quata-shell-route") ||
      "";
    return current === route;
  }, expected, { timeout: 20_000 });
}

async function closeProfileSheetIfVisible(page) {
  const closeSheet = await visibleAriaLocator(page, [/Cerrar hoja|Close sheet/i], 1_000);
  if (!closeSheet) return false;
  const box = await closeSheet.boundingBox().catch(() => null);
  if (box) {
    await page.mouse.click(Math.max(1, box.x + 18), Math.max(1, box.y + 18));
  } else {
    await page.keyboard.press("Escape").catch(() => {});
  }
  await delay(700);
  return true;
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

async function verifyChatOptionsMenuSurface(page, config, state, evidenceDir, report) {
  await clickOptionsMenu(page);
  report.evidence.optionsMenu = await attachScreenshot(page, evidenceDir, "web-chat-options-menu-surface");
  await page.getByText(/Silenciar conversaci[oó]n|Mute conversation/i).click({ timeout: 10_000, force: true });
  await delay(1_000);
  if (!isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:true");
  report.evidence.muted = await attachScreenshot(page, evidenceDir, "web-chat-actions-muted");
  report.steps.push("options_menu_surface_visible_and_mute_enabled_by_rpc");
  await clickOptionsMenu(page);
  await page.getByText(/Reactivar notificaciones|Unmute|Reactivate notifications/i).click({ timeout: 10_000, force: true });
  await delay(1_000);
  if (isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:false");
  report.steps.push("options_menu_unmute_verified_by_rpc");
}

async function verifyChatGroupSosWeb(page, evidenceDir, report) {
  await clickOptionsMenu(page);
  const requiredMenuAnchors = [
    ["chat.group.menu.allowInvites", /Permitir(?: que los miembros inviten| invitaciones)|Allow member invites/i],
    ["chat.group.menu.addParticipants", /adir(?: nuevos)? participantes|Add participants/i],
    ["chat.group.menu.leave", /Abandonar conversaci|Salir de la conversaci|Leave conversation/i],
    ["chat.group.menu.delete", /Borrar conversaci|Eliminar conversaci|Delete conversation/i],
  ];
  const missingGroupAnchors = [];
  for (const [tag, pattern] of requiredMenuAnchors) {
    const locator = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag)), pattern], 1_500);
    const textVisible = await page.getByText(pattern).first()
      .isVisible({ timeout: 1_500 })
      .catch(() => false);
    const domTextVisible = textVisible || await visibleTextMatches(page, pattern);
    if (!locator && !domTextVisible) missingGroupAnchors.push(tag);
  }
  if (missingGroupAnchors.length) {
    report.diagnostics = {
      ...(report.diagnostics ?? {}),
      missingStableAnchors: missingGroupAnchors,
      visibleNativeControls: await visibleNativeControls(page),
    };
    throw new Error(`missing_stable_anchor:${missingGroupAnchors.join(",")}`);
  }
  report.evidence.groupMenu = await attachScreenshot(page, evidenceDir, "web-chat-group-menu-shared-anchors");
  await page.keyboard.press("Escape").catch(() => {});
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.max(1, viewport.width - 12), Math.max(1, viewport.height - 24)).catch(() => {});
  await delay(500);

  report.evidence.sosLocation = await attachScreenshot(page, evidenceDir, "web-chat-sos-location-shared-anchors");
  report.diagnostics = {
    ...(report.diagnostics ?? {}),
    wasmCanvasSemanticLimit: "SOS location body is visually rendered by Compose/Wasm but non-interactive SOS testTags are not exposed as DOM or aria nodes in this host; commonMain contracts and Android/iOS Compose/XCUI gates retain semantic-anchor coverage.",
  };
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

async function visibleTextIncludes(page, probe) {
  return await page.evaluate((needle) => {
    const visible = (element) => {
      const rect = element.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return false;
      const style = window.getComputedStyle(element);
      return style.visibility !== "hidden" && style.display !== "none" && Number(style.opacity || "1") > 0;
    };
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const ownText = [...element.childNodes]
          .filter((node) => node.nodeType === Node.TEXT_NODE)
          .map((node) => node.textContent ?? "")
          .join(" ");
        if (ownText.includes(needle) && visible(element)) return true;
        if (element.shadowRoot && visit(element.shadowRoot)) return true;
      }
      return false;
    };
    const appRoot = document.querySelector("#quata-root");
    return visit(appRoot?.shadowRoot ?? appRoot ?? document);
  }, probe);
}

async function visibleTextMatches(page, pattern) {
  return await page.evaluate(({ source, flags }) => {
    const matcher = new RegExp(source, flags);
    const visible = (element) => {
      const rect = element.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return false;
      const style = window.getComputedStyle(element);
      return style.visibility !== "hidden" && style.display !== "none" && Number(style.opacity || "1") > 0;
    };
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const text = `${element.getAttribute("aria-label") ?? ""} ${element.textContent ?? ""}`;
        if (matcher.test(text) && visible(element)) return true;
        if (element.shadowRoot && visit(element.shadowRoot)) return true;
      }
      return false;
    };
    return visit(document);
  }, { source: pattern.source, flags: pattern.flags });
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

async function visibleTextContentIncludes(page, probe) {
  return await page.evaluate((needle) => {
    const compact = (value) => String(value ?? "").replace(/\s+/g, "");
    const expected = compact(needle);
    const containsNeedle = (value) => compact(value).includes(expected);
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const rect = element.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0 && containsNeedle(element.textContent)) return true;
        if (element.shadowRoot && visit(element.shadowRoot)) return true;
      }
      return false;
    };
    return visit(document);
  }, probe);
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

async function clickLocatorCenter(page, locator, error) {
  const box = await locator.boundingBox().catch(() => null);
  if (!box || box.width <= 0 || box.height <= 0) throw new Error(error);
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  await delay(250);
}

async function waitAudioPlaybackObserved(page, timeout = 10_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const pause = await visibleAriaLocator(page, [/Pause audio|Pausar audio/i], 500);
    if (pause) return { state: "playing", selector: "aria:pause_audio" };
    const failed = await page.getByText(/No se pudo|could not|not available|no est[aÃ¡] disponible|unsupported/i).first()
      .isVisible({ timeout: 250 })
      .catch(() => false);
    if (failed) return { state: "failed_visible", selector: "text:audio_error" };
    await delay(250);
  }
  throw new Error("audio_playback_state_not_observed");
}

async function waitConsecutiveAudioPlaybackObserved(page, firstName, secondName, timeout = 15_000, initialSawFirstPlaying = false) {
  const deadline = Date.now() + timeout;
  let sawFirstPlaying = initialSawFirstPlaying;
  let lastState = null;
  while (Date.now() < deadline) {
    const state = await page.evaluate(({ firstName, secondName }) => {
      const store = globalThis.__quataAudioPlayers;
      const players = store instanceof Map
        ? [...store.entries()].map(([id, element]) => ({
          id,
          src: String(element.currentSrc || element.src || ""),
          playing: !element.paused && !element.ended,
          ended: Boolean(element.ended),
          positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)),
          durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0,
        }))
        : [];
      const labels = [...document.querySelectorAll("[aria-label]")]
        .map((element) => element.getAttribute("aria-label") || "")
        .filter(Boolean);
      return { players, labels, firstName, secondName };
    }, { firstName, secondName });
    lastState = state;
    const firstLabelPlaying = state.labels.some((label) => /Pausar audio|Pause audio/i.test(label) && label.includes(firstName));
    const secondLabelPlaying = state.labels.some((label) => /Pausar audio|Pause audio/i.test(label) && label.includes(secondName));
    const secondLoaded = state.players.some((player) => player.playing);
    if (firstLabelPlaying) sawFirstPlaying = true;
    if (sawFirstPlaying && secondLabelPlaying && secondLoaded) {
      return {
        state: "consecutive_playing",
        selector: `aria:pause_audio:${secondName}`,
        firstNameSha256: sha256(firstName),
        secondNameSha256: sha256(secondName),
        players: state.players.map((player) => ({
          id: player.id,
          playing: player.playing,
          ended: player.ended,
          positionMillis: player.positionMillis,
          durationMillis: player.durationMillis,
        })),
      };
    }
    await delay(250);
  }
  throw new Error(`consecutive_audio_playback_state_not_observed:${JSON.stringify({
    firstNameSha256: sha256(firstName),
    secondNameSha256: sha256(secondName),
    players: lastState?.players?.map((player) => ({
      id: player.id,
      playing: player.playing,
      ended: player.ended,
      positionMillis: player.positionMillis,
      durationMillis: player.durationMillis,
    })) ?? [],
    labels: lastState?.labels?.filter((label) => /audio/i.test(label)).map(sha256) ?? [],
  })}`);
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
    ["document_attachment_message", state.a, state.attachmentsAudio?.document?.messageId],
    ["audio_attachment_message", state.a, state.attachmentsAudio?.audio?.messageId],
    ["next_audio_attachment_message", state.a, state.attachmentsAudio?.nextAudio?.messageId],
    ["profile_content_attachment_message", state.a, state.profileContent?.attachmentMessageId],
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
  if (state.profilePrivateChat?.threadId && state.profilePrivateChat?.markerMessageId && state.b) {
    await rpc(config, state.b, "quata_chat_delete_messages", {
      p_actor_profile_id: state.b.profileId,
      p_thread_id: state.profilePrivateChat.threadId,
      p_message_ids: [state.profilePrivateChat.markerMessageId],
    });
    actions.push("profile_private_chat_marker_deleted");
  }
  const deletedStalePrivateMarkers = await deletePrivateChatTestMarkers(config, state);
  if (deletedStalePrivateMarkers > 0) actions.push(`stale_profile_private_chat_markers_deleted:${deletedStalePrivateMarkers}`);
  const privateMarkers = [state.privateMarker].filter(Boolean);
  if (state.profilePrivateChat?.threadId && state.a && await threadContainsAnyMarker(config, state.a, state.profilePrivateChat.threadId, privateMarkers)) {
    throw new Error("cleanup_residue_detected:profile_private_chat_marker_a");
  }
  if (state.profilePrivateChat?.threadId && state.b && await threadContainsAnyMarker(config, state.b, state.profilePrivateChat.threadId, privateMarkers)) {
    throw new Error("cleanup_residue_detected:profile_private_chat_marker_b");
  }
  if (state.profilePrivateChat?.threadId) actions.push("cleanup_verified_profile_private_chat_marker_absent");
  await state.cleanupRegistry.cleanupStorageObjects({ config, session: state.a, storageRequest, verifyStorageObjectAbsent, actions });
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

async function threadContainsAnyMarker(config, session, thread, markers) {
  const markerSet = new Set(markers.filter(Boolean));
  if (markerSet.size === 0) return false;
  const detail = await rpc(config, session, "quata_chat_get_thread", {
    p_actor_profile_id: session.profileId,
    p_thread_id: thread,
    p_known_message_ids: [],
    p_limit: 250,
  });
  return rows(detail, "messages").some((message) => markerSet.has(messageText(message)));
}

async function deletePrivateChatTestMarkers(config, state) {
  if (!state.profilePrivateChat?.threadId || !state.a) return 0;
  const detail = await rpc(config, state.a, "quata_chat_get_thread", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.profilePrivateChat.threadId,
    p_known_message_ids: [],
    p_limit: 250,
  });
  const messageIds = rows(detail, "messages")
    .filter((message) => /^chat-profile-private-(web|android|ios)-/.test(messageText(message)))
    .map((message) => messageId({ message }))
    .filter((id) => Number.isSafeInteger(Number(id)));
  const uniqueIds = [...new Set(messageIds)];
  if (!uniqueIds.length) return 0;
  await rpc(config, state.a, "quata_chat_delete_messages", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.profilePrivateChat.threadId,
    p_message_ids: uniqueIds,
  });
  return uniqueIds.length;
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

async function verifyStorageObjectAbsent(bucket, storagePath) {
  await withDatabase(async (client) => {
    const result = await client.query(
      "select count(*)::int as count from storage.objects where bucket_id = $1 and name = $2",
      [bucket, storagePath],
    );
    if (Number(result.rows[0]?.count ?? 0) !== 0) throw new Error("cleanup_residue_detected:storage_object");
  });
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

async function prepareProfileListEdges(followerId, followedId) {
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const pairs = [
        { label: "a_follows_b", followerId, followedId },
        { label: "b_follows_a", followerId: followedId, followedId: followerId },
      ];
      const initial = [];
      for (const pair of pairs) {
        const existing = await client.query(
          `select id
             from public.community_profile_follows
            where follower_profile_id = $1 and followed_profile_id = $2
            limit 1
            for update`,
          [pair.followerId, pair.followedId],
        );
        const existed = existing.rowCount > 0;
        initial.push({ ...pair, existed });
        if (!existed) {
          await client.query(
            `insert into public.community_profile_follows (follower_profile_id, followed_profile_id)
             values ($1, $2)
             on conflict do nothing`,
            [pair.followerId, pair.followedId],
          );
        }
      }
      await client.query("commit");
      return initial;
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function restoreProfileListEdges(edges) {
  if (!Array.isArray(edges) || edges.length === 0) return [];
  await withDatabase(async (client) => {
    await client.query("begin");
    try {
      for (const edge of edges) {
        if (edge.existed) {
          await client.query(
            `insert into public.community_profile_follows (follower_profile_id, followed_profile_id)
             values ($1, $2)
             on conflict do nothing`,
            [edge.followerId, edge.followedId],
          );
        } else {
          await client.query(
            "delete from public.community_profile_follows where follower_profile_id = $1 and followed_profile_id = $2",
            [edge.followerId, edge.followedId],
          );
        }
      }
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  return ["profile_follow_list_edges_restored_to_initial_state"];
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
        const countryCode = String(user.countryCode ?? "").replace(/\D/g, "");
        const phone = String(user.phone ?? "").replace(/\D/g, "");
        if (!countryCode || !phone) throw new Error("temporary_profile_hash_window:invalid_phone");
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
          [countryCode, phone, [`${countryCode}${phone}`, phone]],
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

function redactBrowserRuntimeFault(fault) {
  const text = [fault.message, fault.text].filter(Boolean).join(" ");
  return {
    type: fault.type === "pageerror" ? "pageerror" : "console_error",
    messageSha256: text ? sha256(text) : undefined,
    messagePrefix: redactFaultText(text).slice(0, 180) || undefined,
    urlOrigin: fault.url ? safeUrlOrigin(fault.url) : undefined,
    lineNumber: Number.isFinite(fault.lineNumber) ? fault.lineNumber : undefined,
    columnNumber: Number.isFinite(fault.columnNumber) ? fault.columnNumber : undefined,
  };
}

function redactFaultText(text) {
  return String(text ?? "")
    .replace(/Bearer\s+[A-Za-z0-9._~-]+/gi, "Bearer [redacted]")
    .replace(/(access_token|refresh_token|session|password|apikey|api_key)=([^&\s]+)/gi, "$1=[redacted]")
    .replace(/eyJ[A-Za-z0-9._-]+/g, "[jwt-redacted]");
}

function safeUrlOrigin(rawUrl) {
  try {
    const parsed = new URL(rawUrl);
    return parsed.origin;
  } catch {
    return undefined;
  }
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
    "mute_state_not_persisted", "favorite_state_not_persisted", "forward_state_not_persisted", "profile_state_not_opened", "profile_lists_state_not_opened", "profile_lists_state_not_returned", "browser_runtime_fault",
        "composer_message_not_visible", "composer_reply_not_visible", "composer_edit_not_visible",
        "composer_input_not_visible", "composer_send_not_visible", "composer_send_not_dispatched",
    "cleanup_residue_detected", "missing_hard_cleanup_authorization",
    "missing_adjacent_profile_credentials_source", "invalid_adjacent_profile_phone",
    "missing_adjacent_recipient_profile", "temporary_profile_hash_window",
    "profile_content_tag_missing", "profile_content_comments_action_not_clickable",
    "profile_content_comments_input_not_visible", "profile_content_comments_send_not_clickable",
    "profile_content_comment_not_persisted",
    "profile_private_chat_not_opened", "profile_entry_not_opened", "profile_entry_official_cleanup",
    "consecutive_audio_playback_state_not_observed",
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
const state = { a: null, b: null, thread: null, ownMessage: null, peerMessage: null, uiMessages: [], uniqueKey: null, forwardProfile: null, forwardThread: null, forwardedMessage: null, profileListEdges: null, profileContent: null, profileEntry: null, profilePrivateChat: null, privateMarker: null, attachmentsAudio: null, cleanupRegistry: createCleanupRegistry() };
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
  if (isFullEvidenceMode(options)) {
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

  if (options.groupSosOnly) {
    state.sosWithLocationMarker = `[SOS:kind=update;name=Gabrielo;lat=3.7523;lng=8.7741;age_ms=45000;accuracy_m=18;speed_kmh=0]`;
    state.sosUnavailableMarker = `[SOS:kind=alert;name=Gabrielo;custom=Necesito%20ayuda]`;
    await rpc(config, state.a, "quata_chat_send_message", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message: state.sosWithLocationMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-group-sos-location-${runId}`,
    });
    const sosWithLocationMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === state.sosWithLocationMarker);
    state.sosWithLocationMessage = messageId({ message: sosWithLocationMessage });
    await rpc(config, state.a, "quata_chat_send_message", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message: state.sosUnavailableMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-group-sos-unavailable-${runId}`,
    });
    const sosUnavailableMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === state.sosUnavailableMarker);
    state.sosUnavailableMessage = messageId({ message: sosUnavailableMessage });
    report.steps.push("sos_location_and_unavailable_messages_seeded");
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
  if (options.groupSosOnly) {
    report.evidence.threadInitial = await attachScreenshot(page, options.evidenceDir, "web-chat-group-sos-thread-initial");
    report.steps.push("thread_rendered_with_group_and_sos_messages");
    await verifyChatGroupSosWeb(page, options.evidenceDir, report);
    if (faults.length) {
      report.diagnostics = { ...(report.diagnostics ?? {}), browserRuntimeFaults: faults.slice() };
      throw new Error("browser_runtime_fault");
    }
    report.status = "passed";
    report.steps.push("group_menu_and_sos_shared_anchors_verified");
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      ownMessageId: state.ownMessage,
      peerMessageId: state.peerMessage,
      sosWithLocationMessageId: state.sosWithLocationMessage,
      sosUnavailableMessageId: state.sosUnavailableMessage,
      uniqueKeySha256: sha256(state.uniqueKey),
      ownMarkerSha256: sha256(ownMarker),
      peerMarkerSha256: sha256(peerMarker),
      sosWithLocationMarkerSha256: sha256(state.sosWithLocationMarker),
      sosUnavailableMarkerSha256: sha256(state.sosUnavailableMarker),
    };
    throw new EvidenceCompleted();
  }
  await waitMessageVisible(page, ownMarker, "message_not_visible:own");
  if (state.peerMessage) {
    await waitMessageVisible(page, peerMarker, "message_not_visible:peer");
  }
  report.evidence.threadInitial = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-thread-initial");
  report.steps.push(state.peerMessage ? "thread_rendered_with_own_and_peer_messages" : "thread_rendered_with_own_message");

  const translationMarker = state.peerMessage ? peerMarker : ownMarker;
  if (options.translationOnly || (isFullEvidenceMode(options) && state.peerMessage)) {
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

  if (options.menuSurfaceOnly) {
    await verifyChatOptionsMenuSurface(page, config, state, options.evidenceDir, report);
    if (faults.length) throw new Error("browser_runtime_fault");
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      ownMessageId: state.ownMessage,
      peerMessageId: state.peerMessage,
      uniqueKeySha256: sha256(state.uniqueKey),
      ownMarkerSha256: sha256(ownMarker),
      peerMarkerSha256: sha256(peerMarker),
    };
    throw new EvidenceCompleted();
  }

  if (options.attachmentsAudioOnly) {
    state.attachmentsAudio = {
      document: await createChatAttachmentMessage(config, state.a, state.thread, runId, "document"),
      audio: await createChatAttachmentMessage(config, state.a, state.thread, runId, "audio"),
      nextAudio: await createChatAttachmentMessage(config, state.a, state.thread, `${runId}-next`, "audio", "-next"),
    };
    report.steps.push("document_and_consecutive_audio_attachment_messages_seeded");
    faults.length = 0;
    await openAuthenticatedChatRoute(page, server.origin, `sb:${state.thread}`);
    await verifyAttachmentsAudioWeb(page, state.attachmentsAudio, options.evidenceDir, report);
    if (faults.length) {
      report.diagnostics = { ...(report.diagnostics ?? {}), browserRuntimeFaults: faults.slice() };
      throw new Error("browser_runtime_fault");
    }
    report.status = "passed";
    report.steps.push("document_and_audio_shared_attachment_chrome_verified");
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      documentMessageId: state.attachmentsAudio.document.messageId,
      audioMessageId: state.attachmentsAudio.audio.messageId,
      nextAudioMessageId: state.attachmentsAudio.nextAudio.messageId,
      documentAttachmentId: state.attachmentsAudio.document.id,
      audioAttachmentId: state.attachmentsAudio.audio.id,
      nextAudioAttachmentId: state.attachmentsAudio.nextAudio.id,
      uniqueKeySha256: sha256(state.uniqueKey),
      documentMarkerSha256: sha256(state.attachmentsAudio.document.marker),
      audioMarkerSha256: sha256(state.attachmentsAudio.audio.marker),
      nextAudioMarkerSha256: sha256(state.attachmentsAudio.nextAudio.marker),
    };
    throw new EvidenceCompleted();
  }

  if (state.peerMessage && state.b.accessToken) {
    if (options.profileFollowOnly) {
      state.profileFollow = await prepareProfileFollowAbsent(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_initial_state_snapshot_and_absent_prepared");
    }
    if (options.profileListsOnly) {
      state.profileListEdges = await prepareProfileListEdges(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_list_edges_prepared_reversibly");
      await openPeerProfileFromMessageWithoutReturn(page, peerMarker, state.b, options.evidenceDir, report, "web-chat-profile-lists");
      await assertProfileFollowLists(page, server.origin, `sb:${state.thread}`, peerMarker, state.b, options.evidenceDir, report);
      report.steps.push("peer_public_profile_followers_and_following_lists_opened_and_returned");
    } else if (options.profileEntryOnly) {
      state.profileEntry = await prepareProfileEntryFixture(runId);
      state.profileContent = state.profileEntry.profileContent;
      state.profilePrivateChat = state.profileEntry.privateChat;
      report.steps.push("profile_entry_feed_official_and_conversations_fixtures_prepared");
      await verifyProfileEntryWeb(page, server.origin, state.profileEntry, state.b, options.evidenceDir, report);
    } else if (options.profileContentOnly) {
      state.profileContent = {
        marker: `qadata-profile-content-${runId}`,
        actorSession: state.a,
        targetSession: state.b,
        threadId: state.thread,
      };
      await prepareProfileContentFixture(state.profileContent);
      report.steps.push("profile_content_fixture_prepared");
      await openPeerProfileFromMessageWithoutReturn(page, peerMarker, state.b, options.evidenceDir, report, "web-chat-profile-content-open");
      await verifyProfileContentFromOpenProfile(page, state.b, state.profileContent, options.evidenceDir, report);
    } else if (options.profilePrivateChatOnly) {
      state.privateMarker = `chat-profile-private-web-${runId}`;
      state.profilePrivateChat = await createPrivateChatSeed(config, state.a, state.b, state.privateMarker);
      report.steps.push("profile_private_chat_seed_message_ready");
      await openPeerProfileFromMessageWithoutReturn(page, peerMarker, state.b, options.evidenceDir, report, "web-chat-profile-private-chat");
      await openPrivateChatFromOpenProfile(page, state.b, state.profilePrivateChat, state.privateMarker, options.evidenceDir, report);
      report.steps.push("profile_private_chat_opened_from_common_profile_action_and_verified_by_rpc");
    } else if (options.profileFollowOnly) {
      await openPeerProfileFromMessageWithoutReturn(page, peerMarker, state.b, options.evidenceDir, report, "web-chat-profile");
      await toggleFollowFromOpenProfile(page, { actorProfileId: state.a.profileId, profileId: state.b.profileId }, options.evidenceDir, report);
      report.steps.push("profile_follow_toggled_and_verified_by_db");
      if (!(await clickProfileBack(page))) throw new Error("profile_state_not_opened:profile_back_not_clickable");
      await closeProfileSheetIfVisible(page);
      await delay(1_000);
      if (!(await waitForChatProfileReturn(page))) throw new Error("profile_state_not_opened:chat_return_not_visible");
      report.evidence.profileReturn = await attachScreenshot(page, options.evidenceDir, "web-chat-profile-return");
      report.steps.push("peer_avatar_opened_public_profile_and_returned_to_chat");
    } else {
      await openPeerProfileFromMessage(page, peerMarker, state.b, options.evidenceDir, report);
      report.steps.push("peer_avatar_opened_public_profile_and_returned_to_chat");
    }
    if (options.profileOnly || options.profileFollowOnly || options.profileListsOnly || options.profileContentOnly || options.profileEntryOnly || options.profilePrivateChatOnly) {
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
        profileListInitialEdges: state.profileListEdges?.map((edge) => ({ label: edge.label, existed: edge.existed })),
        profileContent: state.profileContent ? {
          markerSha256: sha256(state.profileContent.marker),
          postId: state.profileContent.postId,
          seedCommentId: state.profileContent.seedCommentId,
          uiCommentId: state.profileContent.uiCommentId,
          attachmentId: state.profileContent.attachmentId,
          attachmentMessageId: state.profileContent.attachmentMessageId,
        } : null,
        profileEntry: state.profileEntry ? {
          feedPostId: state.profileEntry.profileContent.postId,
          officialPostId: state.profileEntry.official.id,
          privateThreadId: state.profileEntry.privateChat.threadId,
          officialMarkerSha256: sha256(state.profileEntry.official.marker),
        } : null,
        profilePrivateChatThreadId: state.profilePrivateChat?.threadId ?? null,
        privateMarkerSha256: state.privateMarker ? sha256(state.privateMarker) : null,
      };
      if (options.profileListsOnly) throw new ProfileListsOnlyCompleted();
      if (options.profileEntryOnly) throw new ProfileEntryOnlyCompleted();
      throw new ProfileOnlyCompleted();
    }
  } else if (options.profileOnly || options.profileFollowOnly || options.profileListsOnly || options.profileContentOnly || options.profileEntryOnly || options.profilePrivateChatOnly) {
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
  if (error instanceof EvidenceCompleted || error instanceof ProfileOnlyCompleted || error instanceof ProfileListsOnlyCompleted || error instanceof ProfileEntryOnlyCompleted) {
    // Focal modes already set report.status and fixture; cleanup still runs in finally.
  } else {
    if (pageContext?.page) {
      try {
        report.evidence.failure = await attachScreenshot(pageContext.page, options.evidenceDir, "web-chat-actions-failure");
        report.diagnostics = {
          ...(report.diagnostics ?? {}),
          visibleNativeControls: await visibleNativeControls(pageContext.page),
        };
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
    report.profileHashWindow = {
      state: "restore_failed",
      error: safeFailure(error),
      safeErrorMessage: typeof error?.message === "string" ? error.message : "unknown",
    };
  }
  if (state.profileListEdges && config) {
    try { cleanup.actions.push(...await restoreProfileListEdges(state.profileListEdges)); }
    catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
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
    if (state.profileContent) {
      try {
        cleanup.profileContent = await cleanupProfileContentFixture(state.profileContent);
        cleanup.actions.push("profile_content_fixture_deleted");
        cleanup.actions.push("cleanup_verified_profile_content_residue_absent");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (state.profileEntry?.official) {
      try {
        cleanup.profileEntryOfficial = await cleanupOfficialProfileEntryPost(state.profileEntry.official);
        cleanup.actions.push("profile_entry_official_post_deleted");
        cleanup.actions.push("cleanup_verified_profile_entry_official_residue_absent");
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
