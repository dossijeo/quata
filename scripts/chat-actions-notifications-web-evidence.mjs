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

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const credentialsFileEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_CREDENTIALS_FILE";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP";
const useAdjacentAuthorizedProfile = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_USE_ADJACENT_AUTHORIZED_PROFILE === "1";

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/chat-actions-notifications-evidence.json"),
    evidenceDir: resolve("build-reports/web/chat-actions-notifications-evidence"),
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
  const parsed = JSON.parse(await readFile(file, "utf8"));
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
  return [{
    label: "A",
    countryCode: primaryPhone.countryCode,
    phone: primaryPhone.localPhone,
    password: credentials.password,
    adjacentPhoneKeys: adjacentRecipientPhones(primaryPhone),
  }];
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
  return { label: user.label, profileId, accessToken: session.access_token, refreshToken: session.refresh_token, expiresAt: session.expires_at, webSessionToken };
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

async function pollMessage(config, session, thread, predicate, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const detail = await rpc(config, session, "quata_chat_get_thread", {
      p_actor_profile_id: session.profileId,
      p_thread_id: thread,
      p_known_message_ids: [],
      p_limit: 250,
    });
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

async function clickMessage(page, marker, error) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  for (const probe of probes) {
    if (await clickMessageProbe(page, probe)) return;
  }
  throw new Error(error);
}

async function openMessageActions(page, marker, expectedPatterns, targetError, actionError) {
  await clickMessage(page, marker, targetError);
  if (await visibleAriaLocator(page, expectedPatterns, 2_000)) return;
  if (await longPressMessage(page, marker)) {
    if (await visibleAriaLocator(page, expectedPatterns, 5_000)) return;
  }
  throw new Error(actionError);
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
  return false;
}

async function waitMessageVisible(page, marker, error, timeout = 45_000) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const probe of probes) {
      const text = page.getByText(probe, { exact: false }).first();
      if (await text.waitFor({ timeout: 500 }).then(() => true).catch(() => false)) return;
      if (await visibleTextBox(page, probe)) return;
    }
    await delay(250);
  }
  throw new Error(error);
}

async function clickMessageProbe(page, probe) {
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
    "mute_state_not_persisted", "favorite_state_not_persisted", "browser_runtime_fault",
        "composer_message_not_visible", "composer_reply_not_visible", "composer_edit_not_visible",
        "composer_input_not_visible", "composer_send_not_visible", "composer_send_not_dispatched",
    "cleanup_residue_detected", "missing_hard_cleanup_authorization",
    "missing_adjacent_profile_credentials_source", "invalid_adjacent_profile_phone",
    "missing_adjacent_recipient_profile",
  ].find((prefix) => message.startsWith(prefix)) ?? "unexpected_chat_actions_notifications_web_failure";
}

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
const state = { a: null, b: null, thread: null, ownMessage: null, peerMessage: null, uiMessages: [], uniqueKey: null };
let config, distribution, server, browser, pageContext;
const faults = [];
try {
  config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");
  const users = await authorizedUsers();
  state.a = await login(config, users[0]);
  if (useAdjacentAuthorizedProfile) {
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
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId,
    p_recipient_profile_ids: [state.b.profileId],
    p_subject: `QADATA chat actions notifications ${runId}`,
    p_type: "group",
    p_message: "",
    p_unique_key: state.uniqueKey,
    p_community_id: null,
  }));
  const ownMarker = `chat-actions-own-${runId}`;
  const peerMarker = `chat-actions-peer-${runId}`;
  state.ownMessage = messageId(await rpc(config, state.a, "quata_chat_send_message", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message: ownMarker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-actions-own-${runId}`,
  }));
  if (state.b.accessToken) {
    state.peerMessage = messageId(await rpc(config, state.b, "quata_chat_send_message", {
      p_actor_profile_id: state.b.profileId,
      p_thread_id: state.thread,
      p_message: peerMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-actions-peer-${runId}`,
    }));
    await pollMessage(config, state.a, state.thread, (message) => Number(message?.id) === state.peerMessage);
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
    (message) => messageText(message) === replyMarker && messageReplyToId(message) === Number(replyTargetMessageId),
  );
  state.uiMessages.push(messageId({ message: replyMessage }));
  await delay(1_000);
  await page.keyboard.press("Escape").catch(() => {});
  report.evidence.replySent = await attachScreenshot(page, options.evidenceDir, "web-chat-composer-reply-sent");
  report.steps.push("composer_reply_sent_by_shared_ui_and_verified_by_rpc");

  await openMessageActions(page, ownMarker, [/Editar|Edit/i], "message_action_target_not_clickable:edit", "action_bar_not_visible:edit");
  await clickLabel(page, [/Editar|Edit/i], "action_bar_not_visible:edit");
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
  await waitLabel(page, [/Responder|Reply/i], "action_bar_not_visible:reply");
  await waitLabel(page, [/Reenviar|Forward/i], "action_bar_not_visible:forward");
  await waitLabel(page, [/Editar|Edit/i], "action_bar_not_visible:edit");
  await waitLabel(page, [/Favorito|Favorite/i], "action_bar_not_visible:favorite");
  await waitLabel(page, [/Eliminar|Delete/i], "action_bar_not_visible:delete");
  report.evidence.ownActions = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-own-selected");
  report.steps.push("own_message_action_bar_visible");

  await clickLabel(page, [/Favorito|Favorite/i], "action_bar_not_visible:favorite");
  await delay(1_000);
  const favoriteRows = await favorites(config, state.a);
  if (!favoriteRows.some((message) => Number(message?.id) === Number(state.ownMessage))) throw new Error("favorite_state_not_persisted:true");
  report.steps.push("favorite_toggled_and_verified_by_rpc");

  if (state.peerMessage) {
    await openMessageActions(page, peerMarker, [/Copiar mensaje|Copy message/i], "message_action_target_not_clickable:peer_actions", "action_bar_not_visible:peer_copy");
    await waitLabel(page, [/Responder|Reply/i], "action_bar_not_visible:peer_reply");
    await waitLabel(page, [/Reenviar|Forward/i], "action_bar_not_visible:peer_forward");
    await waitLabel(page, [/Reportar|Report|Denunciar/i], "action_bar_not_visible:peer_report");
    await waitLabel(page, [/Favorito|Favorite/i], "action_bar_not_visible:peer_favorite");
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
    uniqueKeySha256: sha256(state.uniqueKey),
    ownMarkerSha256: sha256(ownMarker),
    peerMarkerSha256: sha256(peerMarker),
  };
} catch (error) {
  if (pageContext?.page) {
    try {
      report.evidence.failure = await attachScreenshot(pageContext.page, options.evidenceDir, "web-chat-actions-failure");
      report.diagnostics = { visibleNativeControls: await visibleNativeControls(pageContext.page) };
    } catch {}
  }
  report.error = safeFailure(error);
  if (typeof error?.message === "string" && error.message.startsWith(report.error)) {
    report.diagnostics = { ...(report.diagnostics ?? {}), safeErrorMessage: error.message };
  }
} finally {
  const cleanup = { state: "completed", actions: [] };
  let cleanupFailed = false;
  try { await pageContext?.context?.close(); } catch {}
  try { await browser?.close(); } catch {}
  try { await server?.close(); } catch {}
  try { await rm(distribution, { recursive: true, force: true }); } catch {}
  if (state.thread && config) {
    try { cleanup.actions.push(...await logicalCleanup(config, state)); }
    catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
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
