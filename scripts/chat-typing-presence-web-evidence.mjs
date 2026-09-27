#!/usr/bin/env node

import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { extname, join, resolve } from "node:path";
import { chromium } from "playwright-core";
import { createChatTypingPeer } from "./e2e-fixtures/chat-typing-peer.mjs";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const delay = (millis) => new Promise((done) => setTimeout(done, millis));
const hash = (value) => createHash("sha256").update(String(value)).digest("hex");

function options(argv) {
  const value = {
    dist: "web/build/dist/wasmJs/productionExecutable",
    out: "build-reports/web/chat-typing-presence-evidence.json",
    evidenceDirectory: "build-reports/web/chat-typing-presence-evidence",
    credentialsFile: process.env.QUATA_CHAT_GROUP_CREDENTIALS_FILE,
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--dist") value.dist = argv[++index];
    else if (arg === "--out") value.out = argv[++index];
    else if (arg === "--evidence-dir") value.evidenceDirectory = argv[++index];
    else if (arg === "--credentials-file") value.credentialsFile = argv[++index];
    else if (arg === "--chrome") value.chrome = argv[++index];
    else throw new Error(`unknown_argument:${arg}`);
  }
  if (!value.credentialsFile) throw new Error("missing_chat_typing_credentials_file");
  return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, key === "chrome" ? entry : resolve(entry)]));
}

async function backendConfig() {
  const source = await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8");
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const publishableKey = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !publishableKey || publishableKey.startsWith("sb_secret_")) throw new Error("missing_public_backend_configuration");
  return { baseUrl, publishableKey };
}

async function credentials(path) {
  const parsed = JSON.parse((await readFile(path, "utf8")).replace(/^\uFEFF/, ""));
  const user = (entry, label) => ({
    label,
    countryCode: String(entry?.country_code ?? "").trim(),
    phone: String(entry?.phone ?? "").trim(),
    password: String(entry?.password ?? ""),
  });
  const users = [user(parsed.a, "A"), user(parsed.b, "B")];
  if (users.some((entry) => !entry.countryCode || !entry.phone || !entry.password)) throw new Error("invalid_chat_typing_credentials");
  return users;
}

function headers(config, token) {
  return {
    apikey: config.publishableKey,
    "content-type": "application/json",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
}

async function jsonRequest(url, init, name) {
  const response = await fetch(url, { ...init, signal: AbortSignal.timeout(20_000) }).catch(() => null);
  if (!response) throw new Error(`${name}:network`);
  const text = await response.text();
  if (!response.ok) throw new Error(`${name}:http_${response.status}`);
  try { return text ? JSON.parse(text) : {}; } catch { throw new Error(`${name}:invalid_json`); }
}

async function login(config, user) {
  const body = await jsonRequest(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: headers(config),
    body: JSON.stringify({
      action: "web_login",
      country_code: user.countryCode,
      phone_local: user.phone,
      password: user.password,
      client_instance_id: `chat-typing-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, `chat_typing_login_${user.label.toLowerCase()}`);
  const session = body?.session;
  const profileId = body?.profile?.id;
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !body?.web_session?.token) {
    throw new Error(`chat_typing_login_${user.label.toLowerCase()}:invalid_response`);
  }
  return {
    profileId,
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    expiresAt: session.expires_at,
    webSessionToken: body.web_session.token,
  };
}

function rpc(config, session, functionName, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${functionName}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_typing_rpc_${functionName}`);
}

function threadId(payload) {
  const value = payload?.thread_id ?? payload?.id ?? payload?.thread?.thread_id ?? payload?.thread?.id ?? payload?.conversation?.thread_id ?? payload?.conversation?.id;
  if (!Number.isSafeInteger(Number(value))) throw new Error("chat_typing_thread_missing");
  return Number(value);
}

function messageIds(payload) {
  const rows = payload?.messages ?? payload?.thread?.messages ?? payload?.conversation?.messages ?? [];
  return rows.map((row) => String(row?.id ?? row?.message_id ?? "")).filter(Boolean).sort();
}

async function threadSnapshot(config, session, thread) {
  return messageIds(await rpc(config, session, "quata_chat_get_thread", {
    p_actor_profile_id: session.profileId,
    p_thread_id: thread,
    p_known_message_ids: [],
    p_limit: 250,
  }));
}

async function configuredDistribution(source, config) {
  if (!(await stat(source).catch(() => null))?.isDirectory()) throw new Error("chat_typing_distribution_missing");
  const target = await mkdtemp(join(tmpdir(), "quata-chat-typing-dist-"));
  await cp(source, target, { recursive: true });
  const index = join(target, "index.html");
  let html = await readFile(index, "utf8");
  html = html
    .replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${config.baseUrl}"`)
    .replace('name="quata-supabase-publishable-key" content=""', `name="quata-supabase-publishable-key" content="${config.publishableKey}"`);
  if (!html.includes(config.publishableKey)) throw new Error("chat_typing_runtime_configuration_failed");
  await writeFile(index, html, "utf8");
  return target;
}

async function serve(directory) {
  const mime = { ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".wasm": "application/wasm", ".css": "text/css", ".json": "application/json", ".png": "image/png", ".svg": "image/svg+xml", ".woff2": "font/woff2", ".ttf": "font/ttf" };
  const server = createServer(async (request, response) => {
    try {
      const pathname = decodeURIComponent(new URL(request.url ?? "/", "http://localhost").pathname);
      const relative = pathname === "/" ? "index.html" : pathname.replace(/^\/+/, "");
      const file = resolve(directory, relative);
      if (!file.startsWith(resolve(directory))) throw new Error("outside_root");
      const bytes = await readFile(file).catch(() => readFile(join(directory, "index.html")));
      response.writeHead(200, { "content-type": mime[extname(file)] ?? "application/octet-stream", "cache-control": "no-store" });
      response.end(bytes);
    } catch {
      response.writeHead(404); response.end();
    }
  });
  await new Promise((done, reject) => { server.once("error", reject); server.listen(0, "127.0.0.1", done); });
  return { origin: `http://127.0.0.1:${server.address().port}`, close: () => new Promise((done) => server.close(done)) };
}

async function waitVisible(locator, timeout = 20_000) {
  await locator.waitFor({ state: "visible", timeout });
  const box = await locator.boundingBox();
  if (!box?.width || !box?.height) throw new Error("chat_typing_anchor_has_no_bounds");
  return box;
}

async function main() {
  const value = options(process.argv.slice(2));
  const report = {
    version: 1,
    status: "failed",
    platform: "web-wasm",
    evidenceKind: "AUTHENTICATED_REALTIME_REMOTE_TYPING_PRODUCT_UI",
    git: { head: "", workingTreeDirty: true },
    steps: [],
    assertions: {},
    cleanup: { state: "pending", databaseMutation: false },
  };
  let browser, peer, server, distribution, context, page;
  try {
    const { execFileSync } = await import("node:child_process");
    report.git.head = execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim();
    report.git.workingTreeDirty = execFileSync("git", ["status", "--porcelain"], { encoding: "utf8" }).trim().length > 0;
    const config = await backendConfig();
    const users = await credentials(value.credentialsFile);
    const [actor, remote] = await Promise.all([login(config, users[0]), login(config, users[1])]);
    const thread = threadId(await rpc(config, actor, "quata_chat_get_or_create_private_thread", {
      p_actor_profile_id: actor.profileId,
      p_peer_profile_id: remote.profileId,
    }));
    const conversationId = `sb:${thread}`;
    const before = await threadSnapshot(config, actor, thread);
    distribution = await configuredDistribution(value.dist, config);
    server = await serve(distribution);
    browser = await chromium.launch({
      headless: true,
      executablePath: value.chrome,
      args: [
        "--use-angle=swiftshader",
        "--enable-unsafe-swiftshader",
        "--force-renderer-accessibility",
      ],
    });
    peer = await createChatTypingPeer({
      browser,
      baseUrl: config.baseUrl,
      publishableKey: config.publishableKey,
      accessToken: remote.accessToken,
      profileId: remote.profileId,
      conversationId,
    });
    context = await browser.newContext({
      locale: "es-ES",
      viewport: { width: 430, height: 930 },
      deviceScaleFactor: 1,
    });
    await context.addInitScript(({ actor }) => {
      for (const [key, entry] of Object.entries({
        quata_web_access_token: actor.accessToken,
        quata_web_refresh_token: actor.refreshToken,
        quata_web_session_token: actor.webSessionToken,
        quata_web_user_id: actor.profileId,
        quata_web_expires_at: String(actor.expiresAt),
        "web.auth.session_ready": "true",
        quata_web_client_instance_id: `chat-typing-product-${crypto.randomUUID()}`,
      })) localStorage.setItem(key, entry);
    }, { actor });
    page = await context.newPage();
    await page.bringToFront();
    await page.goto(`${server.origin}/#chat-${encodeURIComponent(conversationId)}`, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
    await page.waitForFunction((route) => document.documentElement.getAttribute("data-quata-shell-route") === route, `chat/${conversationId}`, { timeout: 60_000 });
    await page.waitForFunction(
      () => !document.querySelector('[id="quata-splash-root"], [title="quata-splash-root"]'),
      null,
      { timeout: 30_000 },
    ).catch(() => { throw new Error("chat_typing_splash_unsettled"); });
    // Compose removes the semantic splash before the final Skia frame is necessarily
    // presented in headless Chrome. Keep the product page foreground and wait for that
    // presentation boundary before collecting visual evidence.
    await page.bringToFront();
    await delay(2_000);
    const composer = page.locator('[id="chat.composer.input"], [title="chat.composer.input"]').first();
    await waitVisible(composer, 60_000);
    report.steps.push("authenticated_product_chat_opened_on_exact_conversation");

    const indicator = page.locator('[id="chat.typing.remote"], [title="chat.typing.remote"]').first();
    for (let attempt = 0; attempt < 10; attempt += 1) {
      await peer.sendTyping(true);
      await page.bringToFront();
      if (await indicator.isVisible().catch(() => false)) break;
      await delay(350);
    }
    await waitVisible(indicator, 5_000);
    await mkdir(value.evidenceDirectory, { recursive: true });
    await page.screenshot({ path: join(value.evidenceDirectory, "remote-typing-visible.png") });
    report.steps.push("authenticated_remote_typing_broadcast_rendered_in_product_ui");
    await peer.sendTyping(false);
    await indicator.waitFor({ state: "hidden", timeout: 8_000 });
    report.steps.push("authenticated_remote_stop_removed_product_indicator");

    const inputBox = await waitVisible(composer);
    await page.mouse.click(inputBox.x + inputBox.width / 2, inputBox.y + inputBox.height / 2);
    await page.keyboard.insertText(`typing-${randomUUID()}`);
    const receivedTrueCount = await peer.waitForTyping({ expectedProfileId: actor.profileId, isTyping: true, timeout: 10_000 });
    await page.keyboard.press("Control+A");
    await page.keyboard.press("Backspace");
    await peer.waitForTyping({ expectedProfileId: actor.profileId, isTyping: false, after: receivedTrueCount, timeout: 10_000 });
    report.steps.push("product_composer_emitted_authenticated_typing_and_stop_broadcasts");

    await peer.sendTyping(true);
    await waitVisible(indicator, 5_000);
    await indicator.waitFor({ state: "hidden", timeout: 7_000 });
    report.steps.push("stale_remote_typing_expired_without_stop_broadcast");
    const after = await threadSnapshot(config, actor, thread);
    if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error("chat_typing_unexpected_message_mutation");
    const peerState = await peer.snapshot();
    report.assertions = {
      actorHash: hash(actor.profileId),
      remoteHash: hash(remote.profileId),
      conversationHash: hash(conversationId),
      exactRoute: true,
      remoteIndicatorObserved: true,
      remoteStopObserved: true,
      productTypingBroadcastObserved: peerState.receivedTyping > 0,
      productStopBroadcastObserved: peerState.receivedStopped > 0,
      staleExpiryObserved: true,
      messageSetUnchanged: true,
      peer: peerState,
    };
    report.status = "passed";
    report.cleanup = { state: "completed", databaseMutation: false, draftCleared: true, realtimePeerClosed: true };
  } catch (error) {
    report.error = String(error?.message ?? error).replace(/[A-Za-z0-9_-]{80,}/g, "[redacted]");
    throw error;
  } finally {
    await peer?.sendTyping(false).catch(() => {});
    await peer?.close().catch(() => {});
    await context?.close().catch(() => {});
    await browser?.close().catch(() => {});
    await server?.close().catch(() => {});
    if (distribution) await rm(distribution, { recursive: true, force: true });
    await mkdir(resolve(value.out, ".."), { recursive: true });
    await writeFile(value.out, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  }
}

main().catch((error) => {
  process.stderr.write(`chat_typing_presence_web_failed:${String(error?.message ?? error).replace(/[A-Za-z0-9_-]{80,}/g, "[redacted]")}\n`);
  process.exitCode = 1;
});
