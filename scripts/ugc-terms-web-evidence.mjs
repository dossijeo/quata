#!/usr/bin/env node
import { createServer } from "node:http";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { cp, mkdir, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { chromium } from "playwright-core";
import pg from "pg";

const CHECK = "UGC-TERMS-WEB-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: gitMetadata(),
  steps: [],
  network: [],
  evidence: {},
  cleanup: { state: "not_started" },
};

let browser;
let context;
let acceptedContext;
let server;
let configuredDist;
let fixture;
let page;
let acceptedPage;

try {
  const config = await loadConfiguration();
  const backend = await publicConfig();
  const termsVersion = await currentTermsVersion();
  configuredDist = await configuredDistribution(options.distribution, backend);
  await assertExactDistributionRevision(configuredDist, report.git.head);
  server = await startServer(configuredDist);

  const session = await login(backend, config.credentials.a, `ugc-terms-web-${randomUUID()}`);
  fixture = await prepareUnacceptedTermsFixture(config, session.profileId, termsVersion);
  report.steps.push("terms_acceptance_snapshot_registered_before_remote_mutation");
  report.steps.push("authorized_profile_authenticated_without_logging_credentials");

  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  context = await browser.newContext({
    locale: "es-ES",
    viewport: { width: 430, height: 930 },
    deviceScaleFactor: 1,
    acceptDownloads: true,
  });
  await installSession(context, session);
  page = await context.newPage();
  const faults = [];
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message ?? error).slice(0, 120)}`));
  page.on("console", (entry) => {
    if (entry.type() === "error") faults.push(`console_error:${entry.text().slice(0, 160)}`);
  });
  page.on("response", async (response) => {
    const entry = sanitizedNetworkEntry(response, backend.url);
    if (entry) report.network.push(entry);
  });

  await page.goto(`${server.origin}/?quata-auth-e2e=1#feed`, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await page.waitForFunction(() => globalThis.__quataUgcTermsE2eProduct?.version === 1, null, { timeout: 30_000 });
  await waitForRequiredGate(page);
  report.evidence.required = await screenshot(page, options.evidenceDir, "web-ugc-terms-required");
  report.steps.push("common_ugc_terms_dialog_blocks_authenticated_web_shell_until_acceptance");

  await openLegalDocument(page, "childsafety", "Normas");
  report.evidence.childSafety = await screenshot(page, options.evidenceDir, "web-ugc-terms-child-safety-document");
  await closeDocumentViewer(page);
  await openLegalDocument(page, "privacy", "Privacidad");
  report.evidence.privacy = await screenshot(page, options.evidenceDir, "web-ugc-terms-privacy-document");
  await closeDocumentViewer(page);
  report.steps.push("ugc_terms_common_legal_links_open_real_web_document_viewer");

  await acceptTerms(page);
  await page.waitForFunction(() =>
    document.documentElement.getAttribute("data-quata-ugc-terms-state") === "accepted",
    null,
    { timeout: 30_000 },
  );
  report.evidence.accepted = await screenshot(page, options.evidenceDir, "web-ugc-terms-accepted-shell");
  report.steps.push("ugc_terms_acceptance_invoked_real_gateway_and_unblocked_shell");

  const acceptedRow = await readTermsAcceptance(config, session.profileId, termsVersion);
  if (!acceptedRow) throw new Error("terms_acceptance_row_missing_after_accept");
  report.steps.push("supabase_terms_acceptance_row_created_by_product_rpc");

  acceptedContext = await browser.newContext({
    locale: "es-ES",
    viewport: { width: 430, height: 930 },
    deviceScaleFactor: 1,
  });
  await installSession(acceptedContext, session);
  acceptedPage = await acceptedContext.newPage();
  await acceptedPage.goto(`${server.origin}/?quata-auth-e2e=1#feed`, { waitUntil: "domcontentloaded" });
  await acceptedPage.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await acceptedPage.waitForFunction(() =>
    document.documentElement.getAttribute("data-quata-ugc-terms-state") === "accepted",
    null,
    { timeout: 45_000 },
  );
  report.evidence.remoteAccepted = await screenshot(acceptedPage, options.evidenceDir, "web-ugc-terms-remote-accepted");
  report.steps.push("fresh_web_context_reads_remote_acceptance_without_reprompt");

  if (faults.length) throw Object.assign(new Error("browser_runtime_faults"), { safeDiagnostic: [...new Set(faults)] });

  await restoreTermsFixture(config, fixture);
  report.cleanup = await verifyRestored(config, fixture);
  if (report.cleanup.state !== "restored") throw new Error("terms_cleanup_verification_failed");
  report.status = "passed";
} catch (error) {
  report.error = String(error?.message ?? error);
  if (error?.safeDiagnostic) report.diagnostics = error.safeDiagnostic;
  if (page && !report.diagnostics) report.diagnostics = await browserStateDiagnostic(page).catch(() => ({ diagnosticUnavailable: true }));
  if (fixture) {
    try {
      await restoreTermsFixture(await loadConfiguration(), fixture);
      report.cleanup = await verifyRestored(await loadConfiguration(), fixture);
    } catch (cleanupError) {
      report.cleanup = { state: "failed", error: String(cleanupError?.message ?? cleanupError) };
    }
  }
  process.exitCode = 1;
} finally {
  await acceptedContext?.close().catch(() => {});
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  await server?.close().catch(() => {});
  if (configuredDist) await rm(configuredDist, { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`);
  console.log(JSON.stringify({
    check: report.check,
    status: report.status,
    git: report.git,
    steps: report.steps.length,
    cleanup: report.cleanup?.state,
    output: options.output,
  }, null, 2));
}

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/ugc-terms-evidence.json"),
    evidenceDir: resolve("build-reports/web/ugc-terms-evidence"),
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

async function loadConfiguration() {
  const credentialsPath = process.env.QUATA_UGC_TERMS_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE;
  const credentials = JSON.parse(await readFile(credentialsPath, "utf8"));
  for (const field of ["country_code", "phone", "password"]) {
    if (!credentials?.a?.[field]) throw new Error(`credentials_missing:a.${field}`);
  }
  return {
    credentials,
    dbUrlFile: process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbTlsCaFile: process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE,
  };
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
}

async function currentTermsVersion() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/moderation/ModerationModels.kt", import.meta.url), "utf8");
  const version = /CurrentUgcTermsVersion\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!version) throw new Error("missing_current_ugc_terms_version");
  return version;
}

async function configuredDistribution(source, backend) {
  if (!(await stat(source).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  const dir = join(tmpdir(), `quata-ugc-terms-web-${randomUUID()}`);
  await cp(source, dir, { recursive: true });
  const index = join(dir, "index.html");
  let html = await readFile(index, "utf8");
  html = html
    .replace(/<meta name="quata-supabase-url" content="[^"]*">/, `<meta name="quata-supabase-url" content="${escapeHtml(backend.url)}">`)
    .replace(/<meta name="quata-supabase-publishable-key" content="[^"]*">/, `<meta name="quata-supabase-publishable-key" content="${escapeHtml(backend.key)}">`);
  await writeFile(index, html, "utf8");
  return dir;
}

async function assertExactDistributionRevision(dist, expectedSha) {
  const revision = (await readFile(join(dist, "quata-source-revision.txt"), "utf8").catch(() => "")).trim();
  if (revision !== expectedSha) throw new Error(`distribution_revision_mismatch:${revision || "missing"}`);
  report.sourceRevision = revision;
}

function escapeHtml(value) {
  return String(value).replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

async function login(backend, credentials, clientInstanceId) {
  const payload = await postJson(`${backend.url}/functions/v1/quata-auth-bridge`, {
    apikey: backend.key,
    "content-type": "application/json",
    "x-client-info": "quata-ugc-terms-web-evidence",
  }, {
    action: "web_login",
    country_code: String(credentials.country_code),
    phone_local: localPhone(credentials.country_code, credentials.phone),
    password: String(credentials.password),
    client_instance_id: clientInstanceId,
  });
  const session = payload?.session;
  const profile = payload?.profile;
  const webSession = payload?.web_session;
  if (!session?.access_token || !session?.refresh_token || !webSession?.token || !profile?.id) throw new Error("invalid_auth_response");
  return {
    profileId: profile.id,
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    webSessionToken: webSession.token,
    expiresAt: Number(session.expires_at ?? Math.floor(Date.now() / 1000) + Number(session.expires_in ?? 3600)),
    displayName: typeof profile.display_name === "string" ? profile.display_name : null,
  };
}

function localPhone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  return digits.startsWith(country) ? digits.slice(country.length) : digits;
}

async function postJson(url, headers, body) {
  let response;
  try {
    response = await fetch(url, { method: "POST", headers, body: JSON.stringify(body), signal: AbortSignal.timeout(30_000) });
  } catch {
    throw new Error("public_request_failed:network");
  }
  const text = await response.text();
  if (!response.ok) throw new Error(`public_request_failed:http_${response.status}`);
  return text ? JSON.parse(text) : {};
}

async function pgConnectionConfig(config) {
  const raw = (await readFile(config.dbUrlFile, "utf8")).trim();
  const ca = await readFile(config.dbTlsCaFile, "utf8");
  const url = new URL(raw);
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true, servername: url.hostname } };
}

async function withPg(config, action) {
  const client = new pg.Client(await pgConnectionConfig(config));
  await client.connect();
  try {
    return await action(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function prepareUnacceptedTermsFixture(config, profileId, termsVersion) {
  const fixture = { profileId, termsVersion, original: null, cleanupRegistered: true };
  await withPg(config, async (client) => {
    await client.query("begin");
    const snapshot = await client.query(
      "select accepted_at from public.ugc_terms_acceptances where profile_id = $1::uuid and terms_version = $2",
      [profileId, termsVersion],
    );
    fixture.original = snapshot.rows[0]?.accepted_at?.toISOString?.() ?? null;
    await client.query(
      "delete from public.ugc_terms_acceptances where profile_id = $1::uuid and terms_version = $2",
      [profileId, termsVersion],
    );
    await client.query("commit");
  });
  return fixture;
}

async function readTermsAcceptance(config, profileId, termsVersion) {
  return withPg(config, async (client) => {
    const result = await client.query(
      "select accepted_at from public.ugc_terms_acceptances where profile_id = $1::uuid and terms_version = $2",
      [profileId, termsVersion],
    );
    return result.rows[0] ?? null;
  });
}

async function restoreTermsFixture(config, currentFixture) {
  report.cleanup.state = "running";
  await withPg(config, async (client) => {
    await client.query("begin");
    if (currentFixture.original) {
      await client.query(
        `insert into public.ugc_terms_acceptances(profile_id, terms_version, accepted_at)
         values ($1::uuid, $2, $3::timestamptz)
         on conflict (profile_id, terms_version) do update set accepted_at = excluded.accepted_at`,
        [currentFixture.profileId, currentFixture.termsVersion, currentFixture.original],
      );
    } else {
      await client.query(
        "delete from public.ugc_terms_acceptances where profile_id = $1::uuid and terms_version = $2",
        [currentFixture.profileId, currentFixture.termsVersion],
      );
    }
    await client.query("commit");
  });
}

async function verifyRestored(config, currentFixture) {
  const row = await readTermsAcceptance(config, currentFixture.profileId, currentFixture.termsVersion);
  const state = currentFixture.original
    ? (row?.accepted_at?.toISOString?.() === currentFixture.original ? "restored" : "failed")
    : (!row ? "restored" : "failed");
  return {
    state,
    originalAcceptancePresent: Boolean(currentFixture.original),
    temporaryAcceptanceRemoved: !currentFixture.original,
  };
}

async function installSession(contextToInstall, session) {
  await contextToInstall.addInitScript((state) => {
    localStorage.setItem("quata_web_access_token", state.accessToken);
    localStorage.setItem("quata_web_refresh_token", state.refreshToken);
    localStorage.setItem("quata_web_session_token", state.webSessionToken);
    localStorage.setItem("quata_web_user_id", state.profileId);
    localStorage.setItem("quata_web_expires_at", String(state.expiresAt));
    localStorage.setItem("web.auth.session_ready", "true");
    localStorage.setItem("web.push.consent.v1", "disabled");
    if (state.displayName) localStorage.setItem("quata_web_display_name", state.displayName);
    for (const key of Object.keys(localStorage)) if (key.startsWith("ugc_terms:")) localStorage.removeItem(key);
    sessionStorage.setItem("quata.auth.e2e", "1");
  }, session);
}

async function waitForRequiredGate(pageToInspect) {
  try {
    await pageToInspect.waitForFunction(() =>
      document.documentElement.getAttribute("data-quata-ugc-terms-state") === "required",
      null,
      { timeout: 45_000 },
    );
  } catch (error) {
    throw Object.assign(new Error("ugc_terms_required_state_timeout"), {
      safeDiagnostic: await browserStateDiagnostic(pageToInspect).catch(() => ({ diagnosticUnavailable: true })),
      cause: error,
    });
  }
}

async function browserStateDiagnostic(pageToInspect) {
  return pageToInspect.evaluate(() => ({
    hash: location.hash,
    navigationRoute: localStorage.getItem("web.navigation.route"),
    shellRoute: document.documentElement.getAttribute("data-quata-shell-route"),
    ugcState: document.documentElement.getAttribute("data-quata-ugc-terms-state"),
    ugcProfileMarkerPresent: document.documentElement.hasAttribute("data-quata-ugc-terms-profile-id"),
    authBridge: globalThis.__quataAuthE2eProduct?.version ?? null,
    ugcBridge: globalThis.__quataUgcTermsE2eProduct?.version ?? null,
    legalBridge: globalThis.__quataLegalDocumentsE2eProduct?.["ugc-terms"]?.version ?? null,
    docViewer: document.querySelector("[data-quata-docmentis-viewer='true']") !== null,
    rootChildren: document.querySelector("#quata-root")?.childElementCount ?? 0,
    shadowChildren: document.querySelector("#quata-root")?.shadowRoot?.childElementCount ?? 0,
  }));
}

async function openLegalDocument(page, documentName, expectedLabel) {
  await page.evaluate((name) => {
    const bridge = globalThis.__quataLegalDocumentsE2eProduct?.["ugc-terms"];
    if (bridge?.version !== 1) throw new Error("ugc_terms_legal_bridge_missing");
    bridge.open(name);
  }, documentName);
  await page.waitForFunction(() => document.querySelector("[data-quata-docmentis-viewer='true']") !== null, null, { timeout: 20_000 });
  await page.waitForFunction((label) => {
    const viewer = document.querySelector("[data-quata-docmentis-viewer='true']");
    return viewer?.getAttribute("aria-label")?.includes(label) === true ||
      viewer?.getAttribute("data-quata-docmentis-render-ready") === "true";
  }, expectedLabel, { timeout: 45_000 });
}

async function closeDocumentViewer(page) {
  await page.getByLabel("Close document viewer").click({ timeout: 10_000 }).catch(async () => {
    await page.keyboard.press("Escape").catch(() => {});
  });
  await page.waitForFunction(() => document.querySelector("[data-quata-docmentis-viewer='true']") === null, null, { timeout: 15_000 });
}

async function acceptTerms(page) {
  const button = page.getByRole("button", { name: /Acepto|I accept|J'accepte/i }).first();
  if (await button.isVisible({ timeout: 2_000 }).catch(() => false)) {
    await button.click({ timeout: 5_000 });
    return;
  }
  await page.evaluate(() => {
    const bridge = globalThis.__quataUgcTermsE2eProduct;
    if (bridge?.version !== 1) throw new Error("ugc_terms_bridge_missing");
    return bridge.accept();
  });
}

async function screenshot(page, dir, name) {
  await mkdir(dir, { recursive: true });
  const file = resolve(dir, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function startServer(root) {
  let origin;
  const raw = createServer(async (request, response) => {
    try {
      if (!origin) throw new Error("server_origin_missing");
      const pathname = decodeURIComponent(new URL(request.url ?? "/", origin).pathname);
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

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"],
    [".png", "image/png"], [".jpg", "image/jpeg"], [".jpeg", "image/jpeg"], [".webp", "image/webp"],
    [".woff2", "font/woff2"], [".ttf", "font/ttf"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

function sanitizedNetworkEntry(response, backendUrl) {
  const url = response.url();
  if (!url.startsWith(`${backendUrl}/rest/v1/rpc/quata_`) && !url.startsWith(`${backendUrl}/functions/v1/quata-auth-bridge`)) return null;
  const path = new URL(url).pathname;
  return { method: response.request().method(), status: response.status(), path };
}

function gitMetadata() {
  const run = (args) => execFileSync("git", args, { encoding: "utf8" }).trim();
  return {
    head: run(["rev-parse", "HEAD"]),
    branch: run(["branch", "--show-current"]),
    originMain: run(["rev-parse", "origin/main"]),
  };
}
