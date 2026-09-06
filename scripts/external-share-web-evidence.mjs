#!/usr/bin/env node
import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { createRequire } from "node:module";
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, extname, isAbsolute, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { spawn } from "node:child_process";
import { assertStorageObjectAbsent } from "./e2e-fixtures/supabase-storage-cleanup.mjs";
import { redactEvidenceReport, redactEvidenceString, redactEvidenceUrl } from "./e2e-fixtures/evidence-redaction.mjs";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const credentialsFileEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_CREDENTIALS_FILE";
const defaultCredentialsFile = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const defaultDistribution = "web/build/dist/wasmJs/productionExecutable";
const { chromium } = loadPackage("playwright-core");

const options = parseArgs(process.argv.slice(2));
const report = {
  check: "EXTERNAL-SHARE-WEB-001",
  platform: "web",
  status: "failed",
  git: await gitMetadata(),
  evidenceDir: options.evidenceDir,
  checks: [],
  anchors: [],
  screenshots: [],
  browserDiagnostics: [],
  cleanup: { verified: false },
};

let server;
let browser;
let context;
let servedDistribution;
let cleanupMessageIds = [];
let cleanupStoragePaths = new Set();
try {
  await requireFile(join(options.distribution, "index.html"));
  const backend = await publicBackendConfig();
  servedDistribution = await configuredDistribution(options.distribution, backend);
  const [actor, peer] = await authorizedUsers();
  const actorSession = await login(backend, actor);
  const peerSession = await login(backend, peer);
  report.actorProfileSha256 = sha256(actorSession.profileId);
  report.peerProfileSha256 = sha256(peerSession.profileId);

  const threadId = await getOrCreatePrivateThread(backend, actorSession, peerSession.profileId);
  const conversationId = `sb:${threadId}`;
  report.threadSha256 = sha256(String(threadId));
  server = await startStaticServer(servedDistribution);
  browser = await chromium.launch({ executablePath: options.chrome, headless: true });
  context = await browser.newContext({
    locale: "es-ES",
    viewport: { width: 430, height: 930 },
    deviceScaleFactor: 1,
    acceptDownloads: true,
  });
  await context.addInitScript((session) => {
    localStorage.setItem("quata_web_access_token", session.accessToken);
    localStorage.setItem("quata_web_refresh_token", session.refreshToken);
    localStorage.setItem("quata_web_session_token", session.webSessionToken);
    localStorage.setItem("quata_web_user_id", session.profileId);
    localStorage.setItem("quata_web_expires_at", String(session.expiresAt));
    if (session.displayName) localStorage.setItem("quata_web_display_name", session.displayName);
    localStorage.setItem("web.auth.session_ready", "true");
    localStorage.setItem("quata_web_client_instance_id", session.clientInstanceId);
  }, actorSession);
  const page = await context.newPage();
  attachBrowserDiagnostics(page);
  await page.goto(server.origin, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  const marker = `qadata-external-share-web-${randomUUID()}`;
  const normalizedShareText = `External Share\n${marker}\nhttps://example.test/${marker.slice(0, 8)}`;
  const attachmentName = `external-share-${marker.slice(-8)}.txt`;
  await seedIncomingShare(page, {
    id: `share-${randomUUID()}`,
    text: normalizedShareText,
    createdAt: Date.now(),
    attachments: [{
      name: attachmentName,
      mimeType: "text/plain",
      text: `External share attachment fixture ${marker}\n`,
    }],
  });
  report.checks.push("incoming_share_seeded_in_real_web_store", "incoming_share_blob_seeded");

  await page.goto(`${server.origin}/?quata-external-share-e2e=1#share-target`, { waitUntil: "domcontentloaded" });
  await waitRoute(page, "share-target");
  await waitExternalShareBridge(page);
  await screenshot(page, "share-target-mounted");
  report.checks.push("share_target_route_mounted_with_restored_session");

  const peerAnchor = peerSession.displayName || peer.phone;
  await waitForCandidateReady(page, peerSession.profileId, peerAnchor);
  await semanticClickExternalShare(page, `external-share.candidate.action.${peerSession.profileId}`, "recipient candidate");
  report.anchors.push({ action: "select_recipient", resolvedBy: "bridgeTestTag", profileSha256: sha256(peerSession.profileId) });
  await semanticClickExternalShare(page, "external-share.confirm", "confirm send");
  report.anchors.push({ action: "confirm_send", resolvedBy: "bridgeTestTag", labels: ["external-share.confirm"] });
  await screenshot(page, "share-target-sent");

  const sent = await pollMessage(backend, actorSession, threadId, (message) => messageText(message).includes(marker), 60_000);
  cleanupMessageIds.push(messageId(sent));
  const sentAttachment = messageAttachments(sent).find((attachment) => String(attachment?.name ?? "").includes(attachmentName));
  if (!sentAttachment) throw new Error("external_share_attachment_not_persisted");
  const storagePath = storagePathOf(sentAttachment);
  if (storagePath) cleanupStoragePaths.add(storagePath);
  report.sentMessage = {
    idSha256: sha256(String(messageId(sent))),
    textProbeSha256: sha256(marker),
    conversationSha256: sha256(conversationId),
    attachment: {
      name: sentAttachment.name ?? null,
      mimeType: sentAttachment.mime_type ?? sentAttachment.mimeType ?? null,
      storagePathSha256: storagePath ? sha256(storagePath) : null,
    },
  };
  report.checks.push("ui_send_persisted_shared_text_url_to_backend", "ui_send_persisted_blob_attachment_to_backend");

  await page.waitForFunction(async () => {
    const database = await openQuataWebDb();
    const count = await new Promise((resolve, reject) => {
      const request = database.transaction("incoming-shares", "readonly").objectStore("incoming-shares").getAll();
      request.onsuccess = () => resolve(request.result.length);
      request.onerror = () => reject(request.error);
    });
    return count === 0;
    function openQuataWebDb() {
      return new Promise((resolve, reject) => {
        const request = indexedDB.open("quata-web", 2);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
      });
    }
  }, undefined, { timeout: 15_000 });
  const remainingShares = await incomingShareCount(page);
  if (remainingShares !== 0) throw new Error(`incoming_share_not_discarded:${remainingShares}`);
  report.checks.push("send_discards_incoming_share_claim");

  await deleteMessages(backend, actorSession, threadId, cleanupMessageIds);
  cleanupMessageIds = [];
  await cleanupStorageObjects(backend, actorSession, cleanupStoragePaths);
  cleanupStoragePaths.clear();
  await assertNoMarker(backend, actorSession, threadId, marker);
  report.cleanup = { verified: true, messageMarkerAbsent: true, storagePhysicalResidue: 0 };
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  throw error;
} finally {
  if (cleanupMessageIds.length) {
    await deleteMessagesSafe(cleanupMessageIds).catch(() => {});
  }
  if (cleanupStoragePaths.size) {
    await cleanupStorageObjectsSafe(cleanupStoragePaths).catch(() => {});
  }
  await context?.close().catch(() => undefined);
  await browser?.close().catch(() => undefined);
  await server?.close?.().catch(() => undefined);
  if (servedDistribution) await rm(servedDistribution, { recursive: true, force: true }).catch(() => undefined);
  await writeReport(report, options.output);
  console.log(JSON.stringify({
    check: report.check,
    status: report.status,
    output: options.output,
    sha: report.git.head,
    cleanup: report.cleanup,
    error: report.error,
  }));
}

function parseArgs(argv) {
  const result = {
    distribution: resolve(defaultDistribution),
    chrome: process.env.QUATA_CHROME_PATH ?? "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/external-share-evidence.json"),
    evidenceDir: resolve("build-reports/web/external-share-evidence"),
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
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

async function gitMetadata() {
  const [head, status] = await Promise.all([
    runSilent("git", ["rev-parse", "HEAD"]),
    runSilent("git", ["status", "--porcelain"]),
  ]);
  return { head: head.trim(), workingTreeDirty: status.trim().length > 0 };
}

async function publicBackendConfig() {
  const source = await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8");
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const key = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !key || key.startsWith("sb_secret_")) throw new Error("missing_public_supabase_configuration");
  return { baseUrl, key };
}

async function configuredDistribution(source, config) {
  if (!await exists(source)) throw new Error("distribution_missing");
  const target = await mkdtemp(join(tmpdir(), "quata-external-share-web-dist-"));
  await cp(source, target, { recursive: true });
  const index = join(target, "index.html");
  let html = await readFile(index, "utf8");
  html = html.replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${escapeHtml(config.baseUrl)}"`)
    .replace('name="quata-supabase-publishable-key" content=""', `name="quata-supabase-publishable-key" content="${escapeHtml(config.key)}"`);
  if (!html.includes(escapeHtml(config.key))) throw new Error("runtime_configuration_injection_failed");
  await writeFile(index, html, "utf8");
  return target;
}

async function authorizedUsers() {
  const file = process.env[credentialsFileEnvironment]?.trim() || defaultCredentialsFile;
  const parsed = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  const user = (entry, label) => ({
    label,
    countryCode: String(entry?.country_code ?? entry?.countryCode ?? "").trim(),
    phone: String(entry?.phone ?? "").replace(/\D/g, "").replace(/^240/, ""),
    password: String(entry?.password ?? ""),
  });
  const users = [user(parsed.a, "A"), user(parsed.b, "B")];
  if (users.some((candidate) => !candidate.countryCode || !candidate.phone || !candidate.password)) {
    throw new Error("missing_external_share_credentials");
  }
  return users;
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
      client_instance_id: `external-share-web-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session;
  const profile = payload?.profile;
  const webSession = payload?.web_session;
  if (!uuid.test(profile?.id ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at) || !webSession?.token) {
    throw new Error(`invalid_auth_response:${user.label}`);
  }
  return {
    label: user.label,
    profileId: profile.id,
    displayName: String(profile.display_name ?? profile.displayName ?? profile.name ?? "").trim(),
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    expiresAt: session.expires_at,
    webSessionToken: webSession.token,
    clientInstanceId: `external-share-web-${randomUUID()}`,
  };
}

function headers(config, token) {
  return {
    apikey: config.key,
    "content-type": "application/json",
    "x-client-info": "quata-external-share-web-evidence",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
}

async function jsonRequest(url, options, prefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(25_000) }); }
  catch { throw new Error(`${prefix}:network`); }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  try { return text ? JSON.parse(text) : {}; } catch { throw new Error(`${prefix}:invalid_json`); }
}

function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
}

async function getOrCreatePrivateThread(config, actorSession, peerProfileId) {
  const payload = await rpc(config, actorSession, "quata_chat_get_or_create_private_thread", {
    p_actor_profile_id: actorSession.profileId,
    p_peer_profile_id: peerProfileId,
  });
  return threadId(payload);
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
  throw new Error("external_share_backend_poll_timeout");
}

async function deleteMessages(config, session, thread, ids) {
  const unique = [...new Set(ids.map(Number).filter(Number.isFinite))];
  if (!unique.length) return;
  await rpc(config, session, "quata_chat_delete_messages", {
    p_actor_profile_id: session.profileId,
    p_thread_id: thread,
    p_message_ids: unique,
  });
}

async function assertNoMarker(config, session, thread, marker) {
  const detail = await rpc(config, session, "quata_chat_get_thread", {
    p_actor_profile_id: session.profileId,
    p_thread_id: thread,
    p_known_message_ids: [],
    p_limit: 250,
  });
  if (rows(detail, "messages").some((message) => messageText(message).includes(marker))) {
    throw new Error("external_share_cleanup_residue_detected");
  }
}

async function seedIncomingShare(page, payload) {
  await page.evaluate(async (entry) => {
    const database = await new Promise((resolve, reject) => {
      const request = indexedDB.open("quata-web", 2);
      request.onupgradeneeded = () => {
        const database = request.result;
        if (!database.objectStoreNames.contains("incoming-shares")) {
          database.createObjectStore("incoming-shares", { keyPath: "id" });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    await new Promise((resolve, reject) => {
      const transaction = database.transaction("incoming-shares", "readwrite");
      transaction.objectStore("incoming-shares").put({
        ...entry,
        attachments: (entry.attachments || []).map((attachment) => ({
          name: attachment.name,
          mimeType: attachment.mimeType,
          blob: new Blob([attachment.text || ""], { type: attachment.mimeType || "application/octet-stream" }),
        })),
      });
      transaction.oncomplete = resolve;
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error);
    });
  }, payload);
}

async function incomingShareCount(page) {
  return await page.evaluate(async () => {
    const database = await new Promise((resolve, reject) => {
      const request = indexedDB.open("quata-web", 2);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    return await new Promise((resolve, reject) => {
      const request = database.transaction("incoming-shares", "readonly").objectStore("incoming-shares").getAll();
      request.onsuccess = () => resolve(request.result.length);
      request.onerror = () => reject(request.error);
    });
  });
}

async function waitRoute(page, route) {
  await page.waitForFunction(
    (expected) => document.documentElement.getAttribute("data-quata-shell-route") === expected,
    route,
    { timeout: 45_000 },
  );
}

async function waitExternalShareBridge(page) {
  await page.waitForFunction(
    () => document.documentElement.getAttribute("data-quata-external-share-e2e") === "ready" &&
      typeof globalThis.__quataExternalShareE2eProduct?.hasSemanticTarget === "function" &&
      typeof globalThis.__quataExternalShareE2eProduct?.semanticClick === "function",
    null,
    { timeout: 30_000 },
  );
}

async function semanticClickExternalShare(page, target, label) {
  const clicked = await page.evaluate((semanticTarget) => {
    return globalThis.__quataExternalShareE2eProduct?.semanticClick?.(semanticTarget) === true;
  }, target);
  if (!clicked) throw new Error(`missing_stable_anchor:${label}`);
}

async function clickStableText(page, text, label, timeout = 15_000) {
  const locator = page.getByText(new RegExp(escapeRegExp(text), "i")).first();
  if (!await locator.isVisible({ timeout }).catch(() => false)) {
    throw new Error(`missing_stable_anchor:${label}`);
  }
  await locator.click({ timeout: 10_000, force: true });
}

async function clickStableCandidate(page, profileId, textFallback, label, timeout = 15_000) {
  const candidate = candidateActionLocator(page, profileId).first();
  if (await candidate.isVisible({ timeout }).catch(() => false)) {
    await candidate.scrollIntoViewIfNeeded({ timeout: 5_000 }).catch(() => {});
    await candidate.click({ timeout: 10_000, force: true });
    return;
  }
  await clickStableText(page, textFallback, label, 2_000);
}

async function clickStableControl(page, labels, label) {
  for (const name of labels) {
    const control = page.locator(`[aria-label="${cssEscape(name)}"]`).first();
    if (await control.isVisible({ timeout: 1_000 }).catch(() => false)) {
      await control.click({ timeout: 10_000, force: true });
      return;
    }
  }
  for (const name of labels) {
    const button = page.getByRole("button", { name: new RegExp(escapeRegExp(name), "i") }).first();
    if (await button.isVisible({ timeout: 1_000 }).catch(() => false)) {
      await button.click({ timeout: 10_000, force: true });
      return;
    }
  }
  throw new Error(`missing_stable_anchor:${label}`);
}

async function screenshot(page, name) {
  await mkdir(options.evidenceDir, { recursive: true });
  const path = join(options.evidenceDir, `${name}.png`);
  const bytes = await page.screenshot({ fullPage: true });
  const stored = process.env.QUATA_EXTERNAL_SHARE_STORE_RAW_SCREENSHOTS === "1";
  if (stored) await writeFile(path, bytes);
  report.screenshots.push({
    name,
    sha256: createHash("sha256").update(bytes).digest("hex"),
    bytes: bytes.length,
    stored,
    path: stored ? path : null,
  });
  report.checks.push(`screenshot:${name}`);
}

function attachBrowserDiagnostics(page) {
  page.on("request", (request) => {
    if (request.method() !== "POST") return;
    const path = storagePathFromUploadUrl(request.url());
    if (path) {
      cleanupStoragePaths.add(path);
      report.browserDiagnostics.push({
        source: "storage-upload",
        storagePathSha256: sha256(path),
      });
    }
  });
  page.on("console", (message) => {
    if (message.type() === "debug") return;
      report.browserDiagnostics.push({
        source: "console",
        type: message.type(),
        text: redactEvidenceString(message.text()),
      });
  });
  page.on("pageerror", (error) => {
    report.browserDiagnostics.push({
      source: "pageerror",
      text: redactEvidenceString(error?.message ?? String(error)),
    });
  });
  page.on("requestfailed", (request) => {
    report.browserDiagnostics.push({
      source: "requestfailed",
      url: redactEvidenceUrl(request.url()),
      failure: redactEvidenceString(request.failure()?.errorText ?? "request_failed"),
    });
  });
  page.on("response", (response) => {
    const url = response.url();
    if (url.includes("/rest/v1/rpc/") || url.includes("/functions/v1/")) {
      report.browserDiagnostics.push({
        source: "response",
        status: response.status(),
        url: redactEvidenceUrl(url),
      });
    }
  });
}

async function cleanupStorageObjects(config, session, paths) {
  const unique = [...new Set([...paths].filter(Boolean))];
  for (const storagePath of unique) {
    await deleteStorageObject(config, session, storagePath);
    await assertStorageObjectAbsent({ bucket: "chat-attachments", storagePath });
  }
}

async function deleteStorageObject(config, session, storagePath) {
  const cleanPath = storagePath.trim().trimStart("/");
  if (!cleanPath || cleanPath.includes("..")) throw new Error("external_share_storage_path_invalid");
  const response = await fetch(`${config.baseUrl}/storage/v1/object/chat-attachments`, {
    method: "DELETE",
    headers: headers(config, session.accessToken),
    body: JSON.stringify({ prefixes: [cleanPath] }),
    signal: AbortSignal.timeout(25_000),
  });
  if (!response.ok) throw new Error(`external_share_storage_delete_failed:${response.status}`);
}

async function waitForCandidateReady(page, profileId, expectedPeerText) {
  const peer = candidateActionLocator(page, profileId).first();
  const empty = page.getByText(/No hay destinatarios disponibles/i).first();
  const error = page.getByText(/No se pudieron cargar los destinatarios/i).first();
  const ready = await Promise.race([
    page.waitForFunction(
      (candidateId) => globalThis.__quataExternalShareE2eProduct?.hasSemanticTarget?.(`external-share.candidate.action.${candidateId}`) === true,
      profileId,
      { timeout: 35_000 },
    ).then(() => "peer", () => null),
    peer.waitFor({ state: "visible", timeout: 35_000 }).then(() => "peer", () => null),
    empty.waitFor({ state: "visible", timeout: 35_000 }).then(() => "empty", () => null),
    error.waitFor({ state: "visible", timeout: 35_000 }).then(() => "error", () => null),
  ]);
  if (!ready) {
    await screenshot(page, "share-target-candidates-timeout");
    const visibleText = await page.locator("body").innerText({ timeout: 2_000 }).catch(() => "");
    report.visibleTextProbe = {
      sha256: sha256(visibleText),
      length: visibleText.length,
    };
    throw new Error("external_share_candidates_timeout");
  }
  if (ready !== "peer") {
    await screenshot(page, "share-target-candidates-error");
    throw new Error(`missing_stable_anchor:recipient candidate`);
  }
}

function candidateActionLocator(page, profileId) {
  const tag = `external-share.candidate.action.${profileId}`;
  const escaped = cssEscape(tag);
  return page.locator(`[id="${escaped}"], [aria-label="${escaped}"], [title="${escaped}"]`);
}

async function startStaticServer(root) {
  const server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? "/", "http://127.0.0.1");
      const pathname = decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname);
      const file = resolve(root, `.${pathname}`);
      if (!file.startsWith(resolve(root))) {
        response.writeHead(403).end();
        return;
      }
      let target = file;
      if (!await exists(target)) target = resolve(root, "index.html");
      response.writeHead(200, { "content-type": contentType(target) });
      response.end(await readFile(target));
    } catch {
      response.writeHead(500).end();
    }
  });
  await new Promise((resolveListen, rejectListen) => {
    server.once("error", rejectListen);
    server.listen(0, "127.0.0.1", resolveListen);
  });
  const address = server.address();
  return {
    origin: `http://127.0.0.1:${address.port}`,
    close: () => new Promise((resolveClose) => server.close(resolveClose)),
  };
}

async function exists(path) {
  return await stat(path).then(() => true, () => false);
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

function rows(payload, key) {
  if (Array.isArray(payload?.[key])) return payload[key];
  if (Array.isArray(payload?.data?.[key])) return payload.data[key];
  if (Array.isArray(payload?.update?.[key])) return payload.update[key];
  if (Array.isArray(payload?.result?.[key])) return payload.result[key];
  return [];
}

function threadId(payload) {
  const candidates = [
    payload?.thread_id,
    payload?.thread?.id,
    payload?.thread?.thread_id,
    payload?.conversation?.id,
    payload?.conversation?.thread_id,
    rows(payload, "threads")[0]?.id,
    rows(payload, "threads")[0]?.thread_id,
  ];
  const match = candidates.map(Number).find((value) => Number.isFinite(value) && value > 0);
  if (!match) throw new Error("external_share_thread_id_missing");
  return match;
}

function messageId(message) {
  const id = Number(message?.id ?? message?.message_id);
  if (!Number.isFinite(id) || id <= 0) throw new Error("external_share_message_id_missing");
  return id;
}

function messageText(message) {
  return String(message?.message ?? message?.body ?? message?.text ?? "");
}

function messageAttachments(message) {
  if (Array.isArray(message?.attachments)) return message.attachments;
  if (Array.isArray(message?.files)) return message.files;
  return [];
}

function storagePathOf(attachment) {
  const explicit = attachment?.storage_path ?? attachment?.storagePath ?? attachment?.path;
  if (typeof explicit === "string" && explicit.trim()) return explicit.trim().replace(/^\/+/, "");
  const url = String(attachment?.url ?? attachment?.file_url ?? attachment?.publicUrl ?? "");
  return storagePathFromPublicUrl(url);
}

function storagePathFromPublicUrl(value) {
  try {
    const url = new URL(value);
    const marker = "/storage/v1/object/public/chat-attachments/";
    const index = url.pathname.indexOf(marker);
    if (index < 0) return null;
    return decodeURIComponent(url.pathname.slice(index + marker.length)).replace(/^\/+/, "");
  } catch {
    return null;
  }
}

function storagePathFromUploadUrl(value) {
  try {
    const url = new URL(value);
    const marker = "/storage/v1/object/chat-attachments/";
    const index = url.pathname.indexOf(marker);
    if (index < 0) return null;
    return decodeURIComponent(url.pathname.slice(index + marker.length)).replace(/^\/+/, "");
  } catch {
    return null;
  }
}

async function writeReport(value, output) {
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(redactEvidenceReport(value), null, 2)}\n`);
}

function shortId(value) {
  return String(value).slice(0, 8);
}

function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function cssEscape(value) {
  return String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

function escapeHtml(value) {
  return String(value).replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

function safeFailure(error) {
  const message = String(error?.message ?? error ?? "");
  return [
    "invalid_arguments",
    "missing_public_supabase_configuration",
    "runtime_configuration_injection_failed",
    "missing_external_share_credentials",
    "public_auth_request_failed",
    "invalid_auth_response",
    "chat_rpc_failed",
    "external_share_thread_id_missing",
    "missing_stable_anchor",
    "external_share_backend_poll_timeout",
    "external_share_candidates_timeout",
    "external_share_attachment_not_persisted",
    "external_share_storage_path_invalid",
    "external_share_storage_delete_failed",
    "incoming_share_not_discarded",
    "external_share_cleanup_residue_detected",
  ].find((prefix) => message.startsWith(prefix)) ?? "unexpected_external_share_web_failure";
}

function delay(ms) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, ms));
}

function runSilent(command, args) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"] });
    const chunks = [];
    const errors = [];
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => errors.push(chunk));
    child.on("close", (code) => {
      if (code === 0) resolveRun(Buffer.concat(chunks).toString("utf8"));
      else rejectRun(new Error(Buffer.concat(errors).toString("utf8") || `${command}_failed:${code}`));
    });
  });
}

async function requireFile(path) {
  if (!await exists(path)) throw new Error(`distribution_missing:${path}`);
}

function loadPackage(name) {
  try {
    return createRequire(import.meta.url)(name);
  } catch (firstError) {
    const root = process.env.QUATA_NODE_MODULES?.trim();
    if (!root || !isAbsolute(root)) throw firstError;
    try {
      return createRequire(join(root, ".quata-require.cjs"))(name);
    } catch {
      throw firstError;
    }
  }
}

async function deleteMessagesSafe(ids) {
  if (!ids.length) return;
  const backend = await publicBackendConfig();
  const [actor, peer] = await authorizedUsers();
  const actorSession = await login(backend, actor);
  const peerSession = await login(backend, peer);
  const thread = await getOrCreatePrivateThread(backend, actorSession, peerSession.profileId);
  await deleteMessages(backend, actorSession, thread, ids);
}

async function cleanupStorageObjectsSafe(paths) {
  const backend = await publicBackendConfig();
  const [actor] = await authorizedUsers();
  const actorSession = await login(backend, actor);
  await cleanupStorageObjects(backend, actorSession, paths);
}
