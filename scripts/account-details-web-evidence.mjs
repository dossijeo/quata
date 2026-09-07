#!/usr/bin/env node
import { createServer } from "node:http";
import { randomUUID } from "node:crypto";
import { createRequire } from "node:module";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";

const CHECK = "ACCOUNT-DETAILS-WEB-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const STABLE_ACCOUNT_DETAILS_ANCHORS = Object.freeze([
  "profile.details.open",
  "profile.details.root",
  "profile.details.name",
  "profile.details.neighborhood",
  "profile.details.phone",
  "profile.details.save",
]);
const { chromium } = loadPlaywrightCore();

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: gitMetadata(),
  stableAnchors: STABLE_ACCOUNT_DETAILS_ANCHORS,
  attempts: [],
  evidence: {},
  cleanup: { attempted: false, profileRestored: false },
};

let server;
let browser;

try {
  const backend = await publicConfig();
  const credentials = (await loadCredentials()).a;
  server = await startServer(options.distribution, await wordpressBaseUrl(), backend);
  const session = await login(backend, credentials, `ACCOUNT-DETAILS-web-${randomUUID()}`);
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
    localStorage.setItem("quata_account_details_e2e_opt_in", "I_ACCEPT_WEB_ACCOUNT_DETAILS_FIXTURE");
  }, session);

  report.attempts.push(await runAttempt(context, backend, session, original, credentials));
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
  console.log(`Account details Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Account details Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Account details Web evidence passed.");
}

async function runAttempt(context, backend, session, original, credentials) {
  const page = await context.newPage();
  const anchors = {};
  const evidence = {};
  const faults = [];
  const marker = String(Date.now()).slice(-6);
  const update = {
    display_name: `Gabrielo QA ${marker}`,
    neighborhood: `Bata QA ${marker}`,
    country_code: original.country_code || credentials.country_code,
    phone_local: evidencePhone(original.phone_local || localPhone(original.country_code || credentials.country_code, credentials.phone)),
  };
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message ?? error).slice(0, 160)}`));
  page.on("console", (entry) => {
    if (entry.type() === "error") faults.push(`console_error:${entry.text().slice(0, 180)}`);
  });
  page.on("response", (response) => {
    if (response.status() >= 400) faults.push(`http_${response.status()}:${response.url().slice(0, 220)}`);
  });
  try {
    await page.goto(`${server.origin}/?quata-account-details-e2e=1#profile`, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await waitForProfile(page);
    evidence.opened = await screenshot(page, "web-account-details-profile-opened");
    anchors.openDetails = await invokeAccountDetailsBridge(page, "openDetails");
    await waitForAccountDetailsVisible(page);
    evidence.formOpened = await screenshot(page, "web-account-details-form-opened");
    anchors.updateDetails = await invokeAccountDetailsBridge(page, "updateDetails", update.display_name, update.neighborhood, update.country_code, update.phone_local);
    await waitForAccountDetailsState(page, update);
    evidence.formEdited = await screenshot(page, "web-account-details-form-edited");
    anchors.save = await invokeAccountDetailsBridge(page, "saveProfile");
    await waitForRemoteProfile(backend, session, update);
    evidence.saved = await screenshot(page, "web-account-details-saved");
    await page.reload({ waitUntil: "domcontentloaded", timeout: 60_000 });
    await waitForProfile(page);
    anchors.reloadOpenDetails = await invokeAccountDetailsBridge(page, "openDetails");
    await waitForAccountDetailsState(page, update);
    evidence.reloaded = await screenshot(page, "web-account-details-reloaded");
    await restoreProfile(backend, session, original);
    report.cleanup = { attempted: true, profileRestored: await waitForRemoteProfile(backend, session, original) };
    if (report.cleanup.profileRestored !== true) throw new Error("web_account_details_cleanup_not_verified");
    const actionableFaults = faults.filter((fault) => !/Failed to load resource: the server responded with a status of 404/.test(fault));
    if (actionableFaults.length) throw new Error(`browser_runtime_fault:${actionableFaults[0]}`);
    return {
      status: "passed",
      anchors,
      evidence,
      changedFields: {
        displayName: original.display_name !== update.display_name,
        neighborhood: original.neighborhood !== update.neighborhood,
        phone: normalizedDigits(original.phone_local) !== normalizedDigits(update.phone_local),
      },
      cleanup: report.cleanup,
    };
  } catch (error) {
    evidence.failure = await screenshot(page, "web-account-details-failure").catch(() => null);
    await restoreProfile(backend, session, original).catch(() => {});
    return {
      status: "failed",
      error: safeFailure(error),
      anchors,
      evidence,
      candidates: await semanticCandidates(page).catch(() => []),
      faults,
    };
  } finally {
    await page.close().catch(() => {});
  }
}

async function waitForProfile(page) {
  await page.locator("body").waitFor({ state: "attached", timeout: 30_000 });
  if (await page.locator("html[data-quata-account-details-bridge='ready']").waitFor({ state: "attached", timeout: 45_000 }).then(() => true).catch(() => false)) return;
  throw new Error("missing_account_details_bridge");
}

async function invokeAccountDetailsBridge(page, method, ...args) {
  await page.locator("html[data-quata-account-details-bridge='ready']").waitFor({ state: "attached", timeout: 45_000 });
  await page.evaluate(({ method, args }) => {
    const bridge = globalThis.__quataAccountDetailsE2EProduct;
    if (!bridge || typeof bridge[method] !== "function") throw new Error(`account_details_bridge_missing:${method}`);
    bridge[method](...args);
  }, { method, args });
  await delay(350);
  return { kind: "webE2eBridge", value: `__quataAccountDetailsE2EProduct.${method}` };
}

async function waitForAccountDetailsVisible(page) {
  await page.waitForFunction(() => {
    globalThis.__quataAccountDetailsE2EProduct?.snapshotDetails?.();
    return document.documentElement.getAttribute("data-quata-account-details-visible") === "true";
  }, null, { timeout: 20_000 });
}

async function waitForAccountDetailsState(page, expected) {
  await page.waitForFunction((expected) => {
    globalThis.__quataAccountDetailsE2EProduct?.snapshotDetails?.();
    const element = document.documentElement;
    return element.getAttribute("data-quata-account-details-visible") === "true" &&
      element.getAttribute("data-quata-account-details-display-name") === expected.display_name &&
      element.getAttribute("data-quata-account-details-neighborhood") === expected.neighborhood &&
      element.getAttribute("data-quata-account-details-country-code") === expected.country_code &&
      element.getAttribute("data-quata-account-details-phone") === expected.phone_local;
  }, expected, { timeout: 20_000 });
}

async function fetchProfile(backend, session) {
  const response = await fetch(`${backend.url}/rest/v1/community_profiles?id=eq.${encodeURIComponent(session.userId)}&select=display_name,nombre,neighborhood,barrio,country_code,code,phone_local,phone,telefono`, {
    headers: authHeaders(backend.key, session.accessToken),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`profile_fetch_failed:${response.status}`);
  const row = (await response.json())?.[0] ?? {};
  return normalizeProfile(row);
}

function normalizeProfile(row) {
  const countryCode = row.country_code ?? row.code ?? "";
  return {
    display_name: row.display_name ?? row.nombre ?? "",
    neighborhood: row.neighborhood ?? row.barrio ?? "",
    country_code: countryCode,
    phone_local: row.phone_local ?? localPhone(countryCode, row.phone ?? row.telefono ?? ""),
  };
}

async function waitForRemoteProfile(backend, session, expected) {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    const current = await fetchProfile(backend, session);
    if (
      current.display_name === expected.display_name &&
      current.neighborhood === expected.neighborhood &&
      normalizedDigits(current.country_code) === normalizedDigits(expected.country_code) &&
      normalizedDigits(current.phone_local) === normalizedDigits(expected.phone_local)
    ) {
      return true;
    }
    await delay(1_000);
  }
  return false;
}

async function restoreProfile(backend, session, original) {
  const response = await fetch(`${backend.url}/rest/v1/community_profiles?id=eq.${encodeURIComponent(session.userId)}`, {
    method: "PATCH",
    headers: { ...authHeaders(backend.key, session.accessToken), "content-type": "application/json", prefer: "return=minimal" },
    body: JSON.stringify({
      display_name: original.display_name,
      nombre: original.display_name,
      neighborhood: original.neighborhood,
      barrio: original.neighborhood,
      country_code: original.country_code,
      code: original.country_code,
      phone_local: original.phone_local,
      phone: `+${normalizedDigits(original.country_code)}${normalizedDigits(original.phone_local)}`,
      telefono: original.phone_local,
    }),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`profile_restore_failed:${response.status}`);
}

function authHeaders(key, accessToken) {
  return { apikey: key, authorization: `Bearer ${accessToken}`, "x-client-info": "quata-account-details-web-evidence" };
}

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/account-details-evidence.json"),
    evidenceDir: resolve("build-reports/web/account-details-evidence"),
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
  const credentials = JSON.parse(await readFile(process.env.QUATA_ACCOUNT_DETAILS_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
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
    headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-account-details-web-evidence" },
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
  const country = normalizedDigits(countryCode);
  const digits = normalizedDigits(phone);
  return country && digits.startsWith(country) ? digits.slice(country.length) : digits;
}

function evidencePhone(originalPhone) {
  const digits = normalizedDigits(originalPhone);
  if (digits.endsWith("607")) return `${digits.slice(0, -3)}609`;
  if (digits.endsWith("608")) return `${digits.slice(0, -3)}606`;
  if (digits.length > 1) return `${digits.slice(0, -1)}${digits.endsWith("9") ? "7" : "9"}`;
  return "680242609";
}

function normalizedDigits(value) {
  return String(value ?? "").replace(/\D/g, "");
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
  return page.evaluate(() => [...document.querySelectorAll("[id^='profile.'], button, [role='button'], [aria-label], input, textarea")].map((element) => {
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
