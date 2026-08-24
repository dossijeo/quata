#!/usr/bin/env node
import { createServer } from "node:http";
import { randomUUID } from "node:crypto";
import { createRequire } from "node:module";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { validPngFixture } from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "ACCOUNT-AVATAR-WEB-REAL-001";
const PICKER_OPT_IN = "I_ACCEPT_WEB_ACCOUNT_AVATAR_FIXTURE";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const { chromium } = loadPlaywrightCore();

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: gitMetadata(),
  attempts: [],
  evidence: {},
  cleanup: { attempted: false, storageDeleted: false, profileRestored: false },
};

let server;
let browser;

try {
  const backend = await publicConfig();
  const credentials = (await loadCredentials()).a;
  server = await startServer(options.distribution, await wordpressBaseUrl(), backend);
  const session = await login(backend, credentials, `ACCOUNT-AVATAR-web-${randomUUID()}`);
  const original = await fetchProfile(backend, session);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  const context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
  await context.addInitScript((state) => {
    localStorage.setItem("quata_web_access_token", state.accessToken);
    localStorage.setItem("quata_web_refresh_token", state.refreshToken);
    localStorage.setItem("quata_web_session_token", state.webSessionToken);
    localStorage.setItem("quata_web_user_id", state.userId);
    localStorage.setItem("quata_web_expires_at", String(state.expiresAt));
    if (state.displayName) localStorage.setItem("quata_web_display_name", state.displayName);
    localStorage.setItem("web.auth.session_ready", "true");
    localStorage.setItem("quata_web_client_instance_id", state.clientInstanceId);
  }, session);

  report.attempts.push(await runAttempt(context, backend, session, original));
  const failed = report.attempts.find((attempt) => attempt.status !== "passed");
  if (failed) throw new Error(`web_attempt_failed:${failed.error ?? "unknown"}`);
  report.evidence.directory = resolve(options.evidenceDir);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = String(error?.message ?? error).slice(0, 500);
} finally {
  await browser?.close().catch(() => {});
  await server?.close?.().catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Account avatar Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Account avatar Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Account avatar Web evidence passed.");
}

async function runAttempt(context, backend, session, original) {
  const page = await context.newPage();
  const anchors = {};
  const evidence = {};
  const faults = [];
  let uploadedAvatarUrl = null;
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message ?? error).slice(0, 160)}`));
  page.on("console", (entry) => {
    if (entry.type() === "error") faults.push(`console_error:${entry.text().slice(0, 180)}`);
  });
  page.on("response", (response) => {
    if (response.status() >= 400) faults.push(`http_${response.status()}:${response.url().slice(0, 220)}`);
  });
  try {
    await page.addInitScript(({ base64, pickerOptIn }) => {
      const binary = atob(base64);
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
      const blob = new Blob([bytes], { type: "image/png" });
      const reference = URL.createObjectURL(blob);
      globalThis.__quataAccountAvatarE2E = { reference };
      localStorage.setItem("quata_account_avatar_e2e_opt_in", pickerOptIn);
      localStorage.setItem("quata_account_avatar_e2e_source", "gallery");
      localStorage.setItem("quata_account_avatar_e2e_outcome", "success");
    }, { base64: validPngFixture().toString("base64"), pickerOptIn: PICKER_OPT_IN });

    await page.goto(`${server.origin}/?quata-account-avatar-e2e=1#profile`, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await waitForProfile(page);
    evidence.opened = await screenshot(page, "web-account-avatar-opened");
    anchors.change = await clickSemantic(page, "profile.avatar.change");
    anchors.gallery = await clickSemantic(page, "profile.avatar.gallery");
    await waitForEditor(page);
    evidence.editorOpened = await screenshot(page, "web-account-avatar-editor-opened");
    anchors.rotate = await clickEditorAction(page, "post-image-editor.rotate", /Girar|Rotate/i);
    anchors.saveEditor = await clickEditorAction(page, "post-image-editor.save", /Guardar|Save/i);
    await waitForEditorClosed(page);
    evidence.afterEditorSave = await screenshot(page, "web-account-avatar-after-editor-save");
    anchors.saveProfile = await clickSemantic(page, "profile.save");
    uploadedAvatarUrl = await waitForRemoteAvatarChange(backend, session, original.avatar_url ?? null);
    const publicProbe = await probePublicAvatar(uploadedAvatarUrl);
    evidence.afterProfileSave = await screenshot(page, "web-account-avatar-after-profile-save");
    const storagePath = storagePathFromPublicUrl(backend.url, session.userId, uploadedAvatarUrl);
    await cleanupUploadedAvatar(backend, session, original.avatar_url ?? null, uploadedAvatarUrl);
    const afterCleanup = await fetchProfile(backend, session);
    if ((afterCleanup.avatar_url ?? null) !== (original.avatar_url ?? null)) throw new Error("web_account_avatar_profile_not_restored");
    report.cleanup = { attempted: true, storageDeleted: true, profileRestored: true, storagePath };
    const actionableFaults = faults.filter((fault) => !/Failed to load resource: the server responded with a status of 404/.test(fault));
    if (actionableFaults.length) throw new Error(`browser_runtime_fault:${actionableFaults[0]}`);
    return {
      status: "passed",
      source: "gallery",
      anchors,
      evidence,
      originalAvatarPresent: Boolean(original.avatar_url),
      uploadedAvatarPath: storagePath,
      publicProbe,
    };
  } catch (error) {
    evidence.failure = await screenshot(page, "web-account-avatar-failure").catch(() => null);
    if (uploadedAvatarUrl) {
      await cleanupUploadedAvatar(backend, session, original.avatar_url ?? null, uploadedAvatarUrl).catch(() => {});
    }
    return {
      status: "failed",
      error: safeFailure(error),
      anchors,
      evidence,
      candidates: await semanticCandidates(page).catch(() => []),
      pageState: await page.evaluate(() => ({
        url: location.href,
        title: document.title,
        bodyText: (document.body?.innerText || document.body?.textContent || "").replace(/\s+/g, " ").trim().slice(0, 500),
        htmlSnippet: document.documentElement?.outerHTML?.slice(0, 1000) || "",
      })).catch(() => null),
      faults,
    };
  } finally {
    await page.close().catch(() => {});
  }
}

async function waitForProfile(page) {
  await page.locator("body").waitFor({ state: "attached", timeout: 30_000 });
  await semanticLocator(page, "profile.avatar.change").then((locator) => locator.waitFor({ state: "attached", timeout: 45_000 }));
}

async function waitForEditor(page) {
  if (await semanticLocator(page, "post-image-editor.root").then((locator) => locator.waitFor({ state: "attached", timeout: 10_000 }).then(() => true)).catch(() => false)) return;
  await page.getByRole("button", { name: /Guardar|Save/i }).first().waitFor({ state: "visible", timeout: 10_000 });
}

async function waitForEditorClosed(page) {
  await page.waitForFunction(() => !document.querySelector("[id='post-image-editor.root']"), null, { timeout: 20_000 }).catch(async () => {
    await page.getByRole("button", { name: /Guardar|Save/i }).first().waitFor({ state: "hidden", timeout: 5_000 });
  });
}

async function clickSemantic(page, id) {
  const locator = await semanticLocator(page, id);
  if (await locator.waitFor({ state: "attached", timeout: 20_000 }).then(() => true).catch(() => false)) {
    await locator.scrollIntoViewIfNeeded().catch(() => null);
    const box = await locator.boundingBox();
    await locator.click({ force: true, timeout: 5_000 }).catch(async () => {
      if (!box || box.width <= 0 || box.height <= 0) throw new Error(`semantic_anchor_not_visible:${id}`);
      await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    });
    await delay(350);
    return { kind: "testTag", value: id };
  }
  const roleFallback = roleFallbackFor(id);
  if (roleFallback) {
    const roleButton = page.getByRole("button", { name: roleFallback }).first();
    if (await roleButton.waitFor({ state: "visible", timeout: 5_000 }).then(() => true).catch(() => false)) {
      await roleButton.click({ force: true, timeout: 5_000 });
      await delay(350);
      return { kind: "roleButton", preferred: id, value: String(roleFallback) };
    }
  }
  const bridgeFallback = bridgeFallbackFor(id);
  if (bridgeFallback) {
    await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-account-avatar-bridge") === "ready", null, { timeout: 10_000 });
    await page.evaluate((method) => {
      const bridge = globalThis.__quataAccountAvatarE2EProduct;
      if (!bridge || typeof bridge[method] !== "function") throw Error(`account_avatar_bridge_method_missing:${method}`);
      bridge[method]();
    }, bridgeFallback);
    await delay(350);
    return { kind: "productBridge", preferred: id, value: bridgeFallback };
  }
  throw new Error(`missing_stable_anchor:${id}`);
}

async function clickEditorAction(page, id, labelPattern) {
  const roleButton = page.getByRole("button", { name: labelPattern }).first();
  if (await roleButton.isVisible({ timeout: 750 }).catch(() => false)) {
    await roleButton.click({ force: true, timeout: 5_000 });
    await delay(350);
    return { kind: "roleButton", preferred: id, value: String(labelPattern) };
  }
  const locator = await semanticLocator(page, id);
  await locator.waitFor({ state: "attached", timeout: 5_000 });
  const box = await locator.boundingBox();
  if (!box || box.width <= 0 || box.height <= 0) throw new Error(`semantic_anchor_not_visible:${id}`);
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await delay(350);
  return { kind: "testTag", value: id };
}

async function semanticLocator(page, id) {
  const escaped = cssString(id);
  return page
    .locator(`[id=${escaped}], [aria-label*=${escaped}], [aria-describedby*=${escaped}], [title*=${escaped}]`)
    .first();
}

function roleFallbackFor(id) {
  if (id === "profile.save") return /Guardar cambios|Save changes/i;
  return null;
}

function bridgeFallbackFor(id) {
  if (id === "profile.save") return "saveProfile";
  return null;
}

function cssString(value) {
  return `"${String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

async function probePublicAvatar(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(20_000) });
  const contentType = response.headers.get("content-type") ?? "";
  const body = response.ok ? "" : (await response.text()).slice(0, 220);
  return {
    ok: response.ok,
    status: response.status,
    contentType,
    body,
  };
}

async function waitForRemoteAvatarChange(backend, session, originalAvatarUrl) {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    const profile = await fetchProfile(backend, session);
    const current = profile.avatar_url ?? null;
    if (current && current !== originalAvatarUrl && current.includes(`/storage/v1/object/public/community-posts/avatars/${session.userId}/`)) {
      return current;
    }
    await delay(1_000);
  }
  throw new Error("web_account_avatar_remote_change_timeout");
}

async function cleanupUploadedAvatar(backend, session, originalAvatarUrl, uploadedAvatarUrl) {
  report.cleanup.attempted = true;
  await patchProfileAvatar(backend, session, originalAvatarUrl);
  report.cleanup.profileRestored = true;
  const path = storagePathFromPublicUrl(backend.url, session.userId, uploadedAvatarUrl);
  await deleteStorageObject(backend, session, path);
  report.cleanup.storageDeleted = true;
}

async function fetchProfile(backend, session) {
  const response = await fetch(`${backend.url}/rest/v1/community_profiles?id=eq.${encodeURIComponent(session.userId)}&select=id,avatar_url`, {
    headers: authHeaders(backend.key, session.accessToken),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`profile_fetch_failed:${response.status}`);
  const rows = await response.json();
  return rows?.[0] ?? {};
}

async function patchProfileAvatar(backend, session, avatarUrl) {
  const response = await fetch(`${backend.url}/rest/v1/community_profiles?id=eq.${encodeURIComponent(session.userId)}`, {
    method: "PATCH",
    headers: { ...authHeaders(backend.key, session.accessToken), "content-type": "application/json", prefer: "return=minimal" },
    body: JSON.stringify({ avatar_url: avatarUrl }),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`profile_restore_failed:${response.status}`);
}

async function deleteStorageObject(backend, session, path) {
  const response = await fetch(`${backend.url}/storage/v1/object/community-posts/${path}`, {
    method: "DELETE",
    headers: authHeaders(backend.key, session.accessToken),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok && response.status !== 404) throw new Error(`avatar_storage_delete_failed:${response.status}`);
}

function storagePathFromPublicUrl(baseUrl, profileId, publicUrl) {
  const marker = `${baseUrl.replace(/\/+$/, "")}/storage/v1/object/public/community-posts/`;
  const path = String(publicUrl ?? "").startsWith(marker) ? String(publicUrl).slice(marker.length) : "";
  if (!path.startsWith(`avatars/${profileId}/`) || path.includes("..")) throw new Error("avatar_storage_path_invalid");
  return path;
}

function authHeaders(key, accessToken) {
  return { apikey: key, authorization: `Bearer ${accessToken}`, "x-client-info": "quata-account-avatar-web-evidence" };
}

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/account-avatar-evidence.json"),
    evidenceDir: resolve("build-reports/web/account-avatar-evidence"),
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--dist", "--chrome", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) {
      throw new Error("invalid_arguments");
    }
    index += 1;
    if (key === "--dist") parsed.distribution = resolve(value);
    if (key === "--chrome") parsed.chrome = resolve(value);
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
  }
  return parsed;
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_ACCOUNT_AVATAR_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
  for (const profile of ["a"]) {
    for (const field of ["country_code", "phone", "password"]) {
      if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
    }
  }
  return credentials;
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
}

async function wordpressBaseUrl() {
  const source = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebRuntimeConfiguration.kt", import.meta.url), "utf8");
  const url = /wordpressBaseUrl:\s*String\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  if (!url) throw new Error("missing_public_wordpress_configuration");
  return url;
}

async function login(backend, credentials, clientInstanceId) {
  const response = await fetch(`${backend.url}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-account-avatar-web-evidence" },
    body: JSON.stringify({
      action: "web_login",
      country_code: String(credentials.country_code),
      phone_local: localPhone(credentials.country_code, credentials.phone),
      password: String(credentials.password),
      client_instance_id: clientInstanceId,
    }),
    signal: AbortSignal.timeout(30_000),
  }).catch(() => null);
  if (!response) throw new Error("public_request_failed:network");
  const payload = JSON.parse(await response.text());
  if (!response.ok) throw new Error(`public_request_failed:http_${response.status}`);
  const session = payload?.session;
  const profile = payload?.profile;
  const webSession = payload?.web_session;
  if (typeof session?.access_token !== "string" || typeof session?.refresh_token !== "string") throw new Error("invalid_auth_response");
  if (typeof webSession?.token !== "string" || typeof profile?.id !== "string") throw new Error("invalid_auth_response");
  return {
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    webSessionToken: webSession.token,
    userId: profile.id,
    expiresAt: Number(session.expires_at ?? Math.floor(Date.now() / 1000) + Number(session.expires_in ?? 3600)),
    displayName: typeof profile.display_name === "string" ? profile.display_name : null,
    clientInstanceId,
  };
}

function localPhone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  return digits.startsWith(country) ? digits.slice(country.length) : digits;
}

async function startServer(root, wordpressBase, publicBackend) {
  let origin;
  const raw = createServer(async (request, response) => {
    try {
      if (!origin) throw new Error("server_origin_missing");
      const url = new URL(request.url ?? "/", origin);
      if (url.pathname === "/favicon.ico") return response.writeHead(204).end();
      if (url.pathname.startsWith("/wordpress-proxy/")) return proxyWordpressRequest(request, response, wordpressBase, url);
      const file = resolve(root, `.${url.pathname === "/" ? "/index.html" : decodeURIComponent(url.pathname)}`);
      if (!file.startsWith(`${root}\\`) && !file.startsWith(`${root}/`) && file !== root) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      response.writeHead(200, {
        "Content-Type": contentType(file),
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
        "Cache-Control": "no-store",
      });
      response.end(await readStaticFileWithEvidenceConfig(file, publicBackend));
    } catch {
      response.writeHead(500).end();
    }
  });
  await new Promise((ok, fail) => { raw.once("error", fail); raw.listen(0, "127.0.0.1", ok); });
  const address = raw.address();
  if (!address || typeof address === "string") throw new Error("static_server_start_failed");
  origin = `http://127.0.0.1:${address.port}`;
  return { origin, close: () => new Promise((ok, fail) => raw.close((error) => error ? fail(error) : ok())) };
}

async function readStaticFileWithEvidenceConfig(file, publicBackend) {
  if (!file.toLowerCase().endsWith("index.html")) return readFile(file);
  const body = await readFile(file, "utf8");
  return body
    .replace(/<meta name="quata-supabase-url" content="[^"]*">/, `<meta name="quata-supabase-url" content="${htmlAttr(publicBackend.url)}">`)
    .replace(/<meta name="quata-supabase-publishable-key" content="[^"]*">/, `<meta name="quata-supabase-publishable-key" content="${htmlAttr(publicBackend.key)}">`);
}

function htmlAttr(value) {
  return String(value).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

async function proxyWordpressRequest(request, response, wordpressBase, url) {
  const target = `${wordpressBase}${url.pathname.replace(/^\/wordpress-proxy/, "")}${url.search}`;
  const upstream = await fetch(target, { method: request.method, headers: wordpressProxyHeaders(request), signal: AbortSignal.timeout(120_000) });
  response.writeHead(upstream.status, {
    "Content-Type": upstream.headers.get("content-type") ?? "application/octet-stream",
    "Cache-Control": "no-store",
  });
  response.end(Buffer.from(await upstream.arrayBuffer()));
}

function wordpressProxyHeaders(request) {
  const headers = {};
  for (const [key, value] of Object.entries(request.headers)) {
    const lower = key.toLowerCase();
    if (["host", "connection", "content-length"].includes(lower)) continue;
    if (Array.isArray(value)) headers[key] = value.join(", ");
    else if (typeof value === "string") headers[key] = value;
  }
  return headers;
}

async function screenshot(page, name) {
  await mkdir(options.evidenceDir, { recursive: true });
  const path = resolve(options.evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

async function semanticCandidates(page) {
  return page.evaluate(() => [...document.querySelectorAll("[id^='profile.'], [id^='post-image-editor'], button, [role='button'], [aria-label]")].map((element) => {
    const rect = element.getBoundingClientRect();
    return {
      tagName: element.tagName,
      id: element.id || null,
      role: element.getAttribute("role") || null,
      ariaLabel: element.getAttribute("aria-label") || null,
      text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 80),
      rect: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) },
      visible: rect.width > 0 && rect.height > 0,
    };
  }));
}

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"],
    [".webp", "image/webp"], [".png", "image/png"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

function gitMetadata() {
  const exec = createRequire(import.meta.url)("node:child_process").execFileSync;
  return {
    head: exec("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim(),
    branch: exec("git", ["branch", "--show-current"], { encoding: "utf8" }).trim(),
    workingTreeDirty: exec("git", ["status", "--porcelain"], { encoding: "utf8" }).trim().length > 0,
  };
}

function loadPlaywrightCore() {
  const require = createRequire(import.meta.url);
  try {
    return require("playwright-core");
  } catch (firstError) {
    const extra = process.env.QUATA_NODE_MODULES?.trim();
    if (extra) {
      try {
        return require(require.resolve("playwright-core", { paths: [extra] }));
      } catch {}
    }
    throw firstError;
  }
}
