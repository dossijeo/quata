#!/usr/bin/env node
import { createServer } from "node:http";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { copyFile, cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { Client } from "pg";
import { chromium } from "playwright-core";

const CHECK = "OFFICIAL-EDITOR-WEB-REAL-UI-001";
const OPT_IN = "I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const REQUIRED_ENV = [
  "QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN",
  "QUATA_OFFICIAL_E2E_COUNTRY_CODE",
  "QUATA_OFFICIAL_E2E_OFFICIAL_PHONE",
  "QUATA_OFFICIAL_E2E_PASSWORD",
];

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: gitMetadata(),
  steps: [],
  postgrest: [],
  mediaRequests: [],
  evidence: {},
};

let distribution;
let fixtureDir;
let server;
let browser;
let context;
let marker;
let visibleMarker;
let created = { ids: [], translationGroupIds: [] };
let cleanup = { state: "not_started" };

try {
  const config = requireEnvironment();
  const backend = await publicConfig();
  const wordpressBase = await wordpressBaseUrl();
  distribution = await configuredDistribution(options.distribution, backend);
  fixtureDir = await mkdtemp(join(tmpdir(), "quata-official-editor-real-fixtures-"));
  marker = `official-web-ui-${randomUUID()}`;
  visibleMarker = marker.replace(/[^a-z0-9]/gi, "").slice(-10);
  server = await startServer(distribution, wordpressBase);

  const loginSession = await login(backend, config, `official-editor-web-real-${randomUUID()}`);
  report.steps.push("official_profile_authenticated_through_public_web_bridge");

  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
  await context.addInitScript((session) => {
    localStorage.setItem("quata_web_access_token", session.accessToken);
    localStorage.setItem("quata_web_refresh_token", session.refreshToken);
    localStorage.setItem("quata_web_session_token", session.webSessionToken);
    localStorage.setItem("quata_web_user_id", session.userId);
    localStorage.setItem("quata_web_expires_at", String(session.expiresAt));
    if (session.displayName) localStorage.setItem("quata_web_display_name", session.displayName);
    localStorage.setItem("quata_web_is_official", String(session.isOfficial === true));
    localStorage.setItem("web.auth.session_ready", "true");
    localStorage.setItem("quata_web_client_instance_id", session.clientInstanceId);
  }, loginSession);

  const page = await context.newPage();
  const faults = [];
  page.on("pageerror", (error) => faults.push(`pageerror:${String(error?.message ?? error).slice(0, 120)}`));
  page.on("console", (entry) => {
    if (entry.type() === "error") faults.push(`console_error:${entry.text().slice(0, 160)}`);
  });
  page.on("response", async (response) => {
    const url = response.url();
    if (!url.includes("/rest/v1/official_posts")) return;
    const request = response.request();
    report.postgrest.push({
      table: "official_posts",
      method: request.method(),
      status: response.status(),
      urlKind: url.includes("select=") ? "read" : "mutation_or_read",
    });
  });
  page.on("response", async (response) => {
    const url = response.url();
    if (!url.includes("/wordpress-proxy/") && !url.includes("/wp-json/quqos/") && !url.includes("/wp-admin/admin-ajax.php")) return;
    report.mediaRequests.push({
      method: response.request().method(),
      status: response.status(),
      urlKind: url.includes("upload-video") ? "wordpress_video_upload" : "wordpress_ajax",
      body: response.status() >= 400 ? sanitizeMediaResponse(await response.text().catch(() => "")) : undefined,
    });
  });
  page.on("requestfailed", (request) => {
    const url = request.url();
    if (!url.includes("/wordpress-proxy/") && !url.includes("/wp-json/quqos/") && !url.includes("/wp-admin/admin-ajax.php")) return;
    report.mediaRequests.push({
      method: request.method(),
      status: "failed",
      urlKind: url.includes("upload-video") ? "wordpress_video_upload" : "wordpress_ajax",
      failure: request.failure()?.errorText?.slice(0, 120) ?? "unknown",
    });
  });

  await page.goto(`${server.origin}/#official`, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await page.waitForFunction(() =>
    localStorage.getItem("web.navigation.route") === "official" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "official",
    { timeout: 45_000 },
  );
  report.steps.push("official_route_mounted_with_restored_real_session");

  const createButton = page.locator("#official-create-action").first();
  await createButton.waitFor({ timeout: 45_000 });
  const createBox = await createButton.boundingBox();
  assertVisibleBox(createBox, "official_create_cta_not_visible");
  report.evidence.official = await screenshot(page, options.evidenceDir, "web-real-official-create-cta-visible");
  report.steps.push("shared_create_cta_visible_for_real_official_profile");

  await page.mouse.click(createBox.x + createBox.width / 2, createBox.y + createBox.height / 2);
  await page.waitForFunction(() =>
    localStorage.getItem("web.navigation.route") === "official-editor" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "official-editor",
    { timeout: 45_000 },
  );
  await page.getByText(/Crear publicaci(?:Ã³|ó)n oficial|Create official post|Cr(?:Ã©|é)er une publication officielle/i)
    .waitFor({ timeout: 45_000 });
  report.evidence.editor = await screenshot(page, options.evidenceDir, "web-real-official-editor-opened");
  report.steps.push("create_cta_opens_common_official_editor");

  const postsBeforeValidation = report.postgrest.filter((entry) => entry.method === "POST").length;
  await clickSemanticElement(page, "official-editor-publish");
  await expectSemanticText(page, "official-editor-feedback", /A(?:Ã±|ñ)ade texto|Add text|Ajoute/i);
  const postsAfterValidation = report.postgrest.filter((entry) => entry.method === "POST").length;
  if (postsAfterValidation !== postsBeforeValidation) throw new Error("official_editor_invalid_draft_mutated");
  report.evidence.validation = await screenshot(page, options.evidenceDir, "web-real-official-editor-validation-feedback");
  report.steps.push("empty_publish_shows_shared_validation_without_backend_mutation");

  if (options.media === "image" || options.media === "video") {
    const mediaFixture = options.media === "image"
      ? await createPngFixture(fixtureDir, marker)
      : await createMp4Fixture(fixtureDir, marker);
    const picker = options.media === "image" ? "official-editor-pick-image" : "official-editor-pick-video";
    const previewName = options.media === "image"
      ? "web-real-official-editor-image-preview"
      : "web-real-official-editor-video-preview";
    const [fileChooser] = await Promise.all([
      page.waitForEvent("filechooser", { timeout: 15_000 }),
      page.locator(`#${picker}`).first().click({ force: true }),
    ]);
    await fileChooser.setFiles(mediaFixture);
    await page.locator("#official-editor-preview").first().waitFor({ state: "attached", timeout: 15_000 });
    report.evidence.mediaPreview = await screenshot(page, options.evidenceDir, previewName);
    report.steps.push(`real_${options.media}_picker_selects_media_and_common_preview_renders`);
  }

  await clickSemanticElement(page, "official-editor-body-action");
  const bodyField = page.locator("#official-editor-body-section #quata-portable-rich-text-field").first();
  await bodyField.waitFor({ state: "attached", timeout: 15_000 });
  await bodyField.click({ force: true });
  await page.keyboard.insertText(
    `Aviso temporal de prueba reversible ${visibleMarker}\n` +
      `Este comunicado de prueba verifica el editor oficial en espanol desde la version web. ` +
      `Marcador tecnico ${marker}.`,
  );
  await page.locator("#official-editor-preview")
    .getByText(new RegExp(escapeRegExp(visibleMarker)))
    .waitFor({ state: "attached", timeout: 15_000 });
  await clickSemanticElement(page, "official-editor-publish");
  if (await clickTranslationSingleLanguageIfShown(page)) {
    report.evidence.translationPrompt = await screenshot(page, options.evidenceDir, "web-real-official-editor-after-translation-skip");
    report.steps.push("shared_fasttext_translation_prompt_skipped_for_reversible_single_language_publish");
  }

  await waitForPostgrestPost(page, report.postgrest, options.evidenceDir);
  created = await readCreatedRows(config, marker);
  if (created.ids.length < 1) throw new Error("created_post_readback_missing");
  const storagePaths = storagePathsFromMediaUrls(created.mediaUrls);
  const wordpressVideoUrls = wordpressVideoUrlsFromMediaUrls(created.mediaUrls);
  report.evidence.created = {
    state: "verified_in_database",
    postIds: created.ids,
    translationGroupIds: created.translationGroupIds,
    media: options.media,
    storagePaths,
    wordpressVideoUrls: wordpressVideoUrls.length,
    visibleMarker,
  };
  await page.goto(`${server.origin}/#official-${created.ids[0]}`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction((postId) =>
    localStorage.getItem("web.navigation.route") === `official/${postId}` &&
    document.documentElement.getAttribute("data-quata-shell-route") === `official/${postId}`,
    created.ids[0],
    { timeout: 60_000 },
  );
  report.evidence.published = await screenshot(page, options.evidenceDir, "web-real-official-post-visible-after-publish");
  report.routeDiagnostics = await routeDiagnostics(page);
  report.steps.push("real_publish_focuses_created_official_route_and_captures_rendered_card");

  const storageCleanup = await cleanupStorageObjects(backend, loginSession, storagePaths);
  const wordpressCleanup = await cleanupWordpressVideoUrls(wordpressVideoUrls);
  const storagePostCleanup = await assertStorageObjectsAbsent(config, storagePaths);
  const wordpressPostCleanup = await assertWordpressVideoUrlsAbsent(wordpressVideoUrls);
  cleanup = await cleanupPosts(config, created.ids, created.translationGroupIds);
  const absence = await assertNoMarkerRows(config, marker, created.translationGroupIds);
  report.storageCleanup = storageCleanup;
  report.wordpressVideoCleanup = wordpressCleanup;
  report.storagePostCleanupReadback = storagePostCleanup;
  report.wordpressPostCleanupReadback = wordpressPostCleanup;
  report.cleanup = cleanup;
  report.postCleanupReadback = absence;
  report.steps.push("created_post_cleaned_by_exact_ids_and_marker_absence_verified");

  const actionableFaults = faults.filter((fault) => !isIgnorablePublishedVideoCorsFault(fault));
  if (faults.length !== actionableFaults.length) {
    report.ignoredRuntimeFaults = faults.filter((fault) => isIgnorablePublishedVideoCorsFault(fault));
  }
  if (actionableFaults.length) {
    report.faults = actionableFaults;
    throw new Error("browser_runtime_fault");
  }
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  if (marker && cleanup.state === "not_started") {
    try {
      const config = requireEnvironment();
      const found = created.ids.length ? created : await readCreatedRows(config, marker);
      cleanup = await cleanupPosts(config, found.ids, found.translationGroupIds);
      report.cleanup = cleanup;
      report.postCleanupReadback = await assertNoMarkerRows(config, marker, found.translationGroupIds);
    } catch {
      report.cleanup = {
        state: "rollback_pending",
        action: "hard_delete_official_posts_by_recorded_ids_or_unique_marker",
        postIds: created.ids,
        translationGroupIds: created.translationGroupIds,
      };
    }
  }
} finally {
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  await server?.close().catch(() => {});
  await rm(distribution, { recursive: true, force: true }).catch(() => {});
  await rm(fixtureDir, { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  if (marker) report.marker = marker;
  if (visibleMarker) report.visibleMarker = visibleMarker;
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`Official editor Web real evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Official editor Web real evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Official editor Web real evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/official-editor-real-evidence.json"),
    evidenceDir: resolve("build-reports/web/official-editor-real-evidence"),
    media: "none",
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--dist", "--chrome", "--out", "--evidence-dir", "--media"].includes(key) || !value || value.startsWith("--")) {
      throw new Error("invalid_arguments");
    }
    index += 1;
    if (key === "--dist") parsed.distribution = resolve(value);
    if (key === "--chrome") parsed.chrome = resolve(value);
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
    if (key === "--media") {
      if (!["none", "image", "video"].includes(value)) throw new Error("invalid_arguments");
      parsed.media = value;
    }
  }
  return parsed;
}

function requireEnvironment() {
  const missing = REQUIRED_ENV.filter((name) => !process.env[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  if (process.env.QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN !== OPT_IN) throw new Error("mutation_opt_in_required");
  return {
    countryCode: process.env.QUATA_OFFICIAL_E2E_COUNTRY_CODE.trim(),
    officialPhone: process.env.QUATA_OFFICIAL_E2E_OFFICIAL_PHONE.trim(),
    password: process.env.QUATA_OFFICIAL_E2E_PASSWORD,
    dbUrlFile: process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbTlsCaFile: process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE,
  };
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(url)) throw new Error("invalid_public_supabase_url");
  return { url, key };
}

async function wordpressBaseUrl() {
  const source = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebRuntimeConfiguration.kt", import.meta.url), "utf8");
  const url = /wordpressBaseUrl:\s*String\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  if (!url || !/^https:\/\/[a-z0-9.-]+$/i.test(url)) throw new Error("missing_public_wordpress_configuration");
  return url;
}

async function configuredDistribution(source, backend) {
  if (!(await stat(source).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  await assertDistributionRevision(source);
  const target = await mkdtemp(join(tmpdir(), "quata-official-editor-real-dist-"));
  await cp(source, target, { recursive: true });
  const index = join(target, "index.html");
  let html = await readFile(index, "utf8");
  html = html
    .replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${backend.url}"`)
    .replace('name="quata-supabase-publishable-key" content=""', `name="quata-supabase-publishable-key" content="${backend.key}"`);
  await writeFile(index, html, "utf8");
  return target;
}

async function startServer(root, wordpressBase) {
  let origin;
  const raw = createServer(async (request, response) => {
    try {
      if (!origin) throw new Error("server_origin_missing");
      const url = new URL(request.url ?? "/", origin);
      if (url.pathname === "/favicon.ico") return response.writeHead(204).end();
      if (url.pathname.startsWith("/wordpress-proxy/")) {
        return proxyWordpressRequest(request, response, wordpressBase, url);
      }
      const file = resolve(root, `.${url.pathname === "/" ? "/index.html" : decodeURIComponent(url.pathname)}`);
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

async function proxyWordpressRequest(request, response, wordpressBase, url) {
  const target = `${wordpressBase}${url.pathname.replace(/^\/wordpress-proxy/, "")}${url.search}`;
  const body = ["GET", "HEAD"].includes(request.method ?? "GET") ? undefined : await readRequestBody(request);
  const upstream = await fetch(target, {
    method: request.method,
    headers: wordpressProxyHeaders(request),
    body,
    signal: AbortSignal.timeout(180_000),
  });
  response.writeHead(upstream.status, {
    "Content-Type": upstream.headers.get("content-type") ?? "application/octet-stream",
    "Cache-Control": "no-store",
  });
  response.end(Buffer.from(await upstream.arrayBuffer()));
}

async function readRequestBody(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  return Buffer.concat(chunks);
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

function sanitizeMediaResponse(value) {
  return value
    .replace(/https?:\/\/\S+/gi, "[url]")
    .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, "[email]")
    .slice(0, 240);
}

async function login(backend, config, clientInstanceId) {
  const response = await postJson(`${backend.url}/functions/v1/quata-auth-bridge`, {
    apikey: backend.key,
    "content-type": "application/json",
    "x-client-info": "quata-official-editor-web-real-evidence",
  }, {
    action: "web_login",
    country_code: config.countryCode,
    phone_local: config.officialPhone,
    password: config.password,
    client_instance_id: clientInstanceId,
  });
  const root = response.payload;
  const session = root?.session;
  const profile = root?.profile;
  const webSession = root?.web_session;
  if (typeof session?.access_token !== "string" || typeof session?.refresh_token !== "string") throw new Error("invalid_auth_response");
  if (typeof webSession?.token !== "string" || typeof profile?.id !== "string") throw new Error("invalid_auth_response");
  const isOfficial = profile.is_official === true || await readAuthenticatedProfileIsOfficial(backend, session.access_token, profile.id);
  return {
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    webSessionToken: webSession.token,
    userId: profile.id,
    expiresAt: Number(session.expires_at ?? Math.floor(Date.now() / 1000) + Number(session.expires_in ?? 3600)),
    displayName: typeof profile.display_name === "string" ? profile.display_name : null,
    isOfficial,
    clientInstanceId,
  };
}

async function readAuthenticatedProfileIsOfficial(backend, accessToken, profileId) {
  const url = new URL(`${backend.url}/rest/v1/community_profiles`);
  url.searchParams.set("select", "is_official");
  url.searchParams.set("id", `eq.${profileId}`);
  url.searchParams.set("limit", "1");
  const response = await fetch(url, {
    method: "GET",
    headers: {
      apikey: backend.key,
      authorization: `Bearer ${accessToken}`,
      "x-client-info": "quata-official-editor-web-real-evidence",
    },
    signal: AbortSignal.timeout(20_000),
  }).catch(() => null);
  if (!response) throw new Error("profile_role_read_failed:network");
  if (!response.ok) throw new Error(`profile_role_read_failed:http_${response.status}`);
  const rows = await response.json();
  return rows?.[0]?.is_official === true;
}

async function postJson(url, headers, body) {
  const response = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(20_000),
  }).catch(() => null);
  if (!response) throw new Error("public_request_failed:network");
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(`public_request_failed:http_${response.status}`);
  return { status: response.status, payload };
}

async function pgConnectionConfig(config) {
  const raw = (await readFile(config.dbUrlFile, "utf8")).trim();
  const ca = await readFile(config.dbTlsCaFile, "utf8");
  const url = new URL(raw);
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true } };
}

async function withPg(config, action) {
  const client = new Client(await pgConnectionConfig(config));
  await client.connect();
  try {
    return await action(client);
  } finally {
    await client.end();
  }
}

async function readCreatedRows(config, uniqueMarker) {
  return withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query({
        text: `select id, translation_group_id, media_url
               from public.official_posts
               where title like $1 or content_html like $1
               order by created_at desc`,
        values: [`%${uniqueMarker}%`],
      });
      await client.query("rollback");
      return {
        ids: rows.map((row) => row.id).filter(Boolean),
        translationGroupIds: [...new Set(rows.map((row) => row.translation_group_id).filter(Boolean))],
        mediaUrls: [...new Set(rows.map((row) => row.media_url).filter(Boolean))],
      };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function cleanupStorageObjects(backend, session, storagePaths) {
  if (!storagePaths.length) return { state: "not_needed", deletedPaths: [] };
  const response = await fetch(`${backend.url}/storage/v1/object/community-posts`, {
    method: "DELETE",
    headers: {
      apikey: backend.key,
      authorization: `Bearer ${session.accessToken}`,
      "content-type": "application/json",
      "x-client-info": "quata-official-editor-web-real-evidence",
    },
    body: JSON.stringify({ prefixes: storagePaths }),
    signal: AbortSignal.timeout(20_000),
  }).catch(() => null);
  if (!response) throw new Error("storage_cleanup_failed:network");
  if (!response.ok) throw new Error(`storage_cleanup_failed:http_${response.status}`);
  return { state: "deleted", deletedPaths: storagePaths };
}

async function cleanupWordpressVideoUrls(videoUrls) {
  if (!videoUrls.length) return { state: "not_needed", deletedUrls: 0 };
  const endpoint = wordpressAdminAjaxUrl(videoUrls[0]);
  for (const url of videoUrls) {
    if (wordpressAdminAjaxUrl(url) !== endpoint) throw new Error("wordpress_cleanup_failed:mixed_origin");
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "content-type": "application/x-www-form-urlencoded",
        "x-client-info": "quata-official-editor-web-real-evidence",
      },
      body: new URLSearchParams({ action: "quqos_delete_post_video", url }).toString(),
      signal: AbortSignal.timeout(20_000),
    }).catch(() => null);
    if (!response) throw new Error("wordpress_cleanup_failed:network");
    const text = await response.text();
    if (!response.ok) throw new Error(`wordpress_cleanup_failed:http_${response.status}`);
    if (/"success"\s*:\s*false/i.test(text)) throw new Error("wordpress_cleanup_failed:success_false");
  }
  return { state: "delete_requested", deletedUrls: videoUrls.length };
}

async function assertWordpressVideoUrlsAbsent(videoUrls) {
  if (!videoUrls.length) return { state: "not_needed" };
  const checked = [];
  for (const url of videoUrls) {
    const probe = new URL(url);
    probe.searchParams.set("quata_cleanup_probe", randomUUID());
    const response = await fetch(probe, {
      method: "GET",
      headers: {
        range: "bytes=0-0",
        "cache-control": "no-store",
        "x-client-info": "quata-official-editor-web-real-evidence",
      },
      signal: AbortSignal.timeout(20_000),
    }).catch(() => null);
    if (!response) throw new Error("wordpress_post_cleanup_verification_failed:network");
    checked.push({ urlKind: wordpressVideoUrlKind(url), status: response.status });
    if (![404, 410].includes(response.status)) {
      throw new Error(`wordpress_post_cleanup_verification_failed:http_${response.status}`);
    }
  }
  return { state: "verified_absent", checked };
}

async function assertStorageObjectsAbsent(config, storagePaths) {
  if (!storagePaths.length) return { state: "not_needed" };
  return withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query({
        text: `select count(*)::int as count
               from storage.objects
               where bucket_id = 'community-posts'
                 and name = any($1::text[])`,
        values: [storagePaths],
      });
      await client.query("rollback");
      if (rows[0]?.count !== 0) throw new Error("storage_post_cleanup_verification_failed:object_metadata_remains");
      return { state: "verified_absent", checkedPaths: storagePaths };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

function storagePathsFromMediaUrls(mediaUrls) {
  return [...new Set(mediaUrls.map((url) => storagePathFromMediaUrl(null, url)).filter(Boolean))];
}

function wordpressVideoUrlsFromMediaUrls(mediaUrls) {
  return [...new Set(mediaUrls.filter((url) => {
    if (storagePathFromMediaUrl(null, url)) return false;
    try {
      const parsed = new URL(url);
      return /^https?:$/i.test(parsed.protocol) && parsed.hostname.toLowerCase().endsWith("egquata.com");
    } catch {
      return false;
    }
  }))];
}

function wordpressAdminAjaxUrl(value) {
  const parsed = new URL(value);
  return `${parsed.origin}/wp-admin/admin-ajax.php`;
}

function wordpressVideoUrlKind(value) {
  const parsed = new URL(value);
  return `${parsed.hostname}/wp-content/uploads/${parsed.pathname.split("/").pop()}`;
}

function storagePathFromMediaUrl(backend, value) {
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    return null;
  }
  if (backend && parsed.origin !== new URL(backend.url).origin) return null;
  const markerPath = "/storage/v1/object/public/community-posts/";
  const index = parsed.pathname.indexOf(markerPath);
  if (index < 0 || parsed.search || parsed.hash) return null;
  return parsed.pathname
    .slice(index + markerPath.length)
    .split("/")
    .map((part) => decodeURIComponent(part))
    .join("/")
    .trim()
    || null;
}

async function createPngFixture(root, uniqueMarker) {
  const path = join(root, `official-editor-${uniqueMarker}.png`);
  await writeFile(path, Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFUlEQVR42mP8z8Dwn4GBgYGJAQoAHxcCAns1m2AAAAAASUVORK5CYII=",
    "base64",
  ));
  return path;
}

async function createMp4Fixture(root, uniqueMarker) {
  const path = join(root, `official-editor-${uniqueMarker}.mp4`);
  await copyFile(resolve("play-store/05-assets/quata-demo-video.mp4"), path);
  return path;
}

async function cleanupPosts(config, ids, groupIds) {
  if (!ids.length && !groupIds.length) return { state: "not_needed", deletedRows: 0, remainingRows: 0 };
  return withPg(config, async (client) => {
    await client.query("begin");
    try {
      await client.query({
        text: "delete from public.official_post_likes where official_post_id = any($1::uuid[])",
        values: [ids],
      });
      await client.query({
        text: "delete from public.official_post_comments where official_post_id = any($1::uuid[])",
        values: [ids],
      });
      const deleted = await client.query({
        text: `delete from public.official_posts
               where id = any($1::uuid[])
                  or translation_group_id = any($2::uuid[])`,
        values: [ids, groupIds],
      });
      const remaining = await client.query({
        text: `select count(*)::int as count
               from public.official_posts
               where id = any($1::uuid[])
                  or translation_group_id = any($2::uuid[])`,
        values: [ids, groupIds],
      });
      if (remaining.rows[0]?.count !== 0) throw new Error("cleanup_verification_failed");
      await client.query("commit");
      return { state: "hard_deleted_verified", deletedRows: deleted.rowCount, remainingRows: 0 };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function assertNoMarkerRows(config, uniqueMarker, groupIds) {
  return withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query({
        text: `select count(*)::int as count
               from public.official_posts
               where translation_group_id = any($1::uuid[])
                  or title like $2
                  or content_html like $2`,
        values: [groupIds, `%${uniqueMarker}%`],
      });
      await client.query("rollback");
      if (rows[0]?.count !== 0) throw new Error("marker_cleanup_verification_failed");
      return { state: "verified_absent" };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

function gitMetadata() {
  const head = execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim();
  const status = execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], { encoding: "utf8" }).trim();
  return { head, trackedChanges: status };
}

async function assertDistributionRevision(source) {
  const markerFile = join(source, "quata-source-revision.txt");
  const revision = (await readFile(markerFile, "utf8").catch(() => "")).trim();
  const expected = execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim();
  const status = execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], { encoding: "utf8" }).trim();
  if (!/^[0-9a-f]{40}$/i.test(revision)) throw new Error("distribution_revision_missing_or_invalid");
  if (revision.toLowerCase() !== expected.toLowerCase()) throw new Error("distribution_revision_mismatch");
  if (status) throw new Error("distribution_source_tree_dirty");
}

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"],
    [".webp", "image/webp"], [".png", "image/png"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

function assertVisibleBox(box, error) {
  if (!box || box.width <= 0 || box.height <= 0) throw new Error(error);
}

async function waitForPostgrestPost(page, entries, evidenceDir, timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (entries.some((entry) => entry.table === "official_posts" && entry.method === "POST" && entry.status >= 200 && entry.status < 300)) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  await screenshot(page, evidenceDir, "web-real-official-editor-publish-timeout").catch(() => null);
  throw new Error("official_editor_publish_request_missing");
}

async function clickSemanticElement(page, id) {
  const locator = page.locator(`#${id}`).first();
  await locator.waitFor({ state: "attached", timeout: 15_000 });
  await locator.scrollIntoViewIfNeeded().catch(() => null);
  await locator.click({ force: true, timeout: 5_000 }).catch(async () => {
    await locator.dispatchEvent("click");
  });
}

async function expectSemanticText(page, id, pattern, timeoutMs = 15_000) {
  const locator = page.locator(`#${id}`).first();
  await locator.waitFor({ state: "attached", timeout: timeoutMs });
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const text = await locator.textContent().catch(() => "");
    if (pattern.test(text ?? "")) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`semantic_text_missing:${id}`);
}

async function clickTranslationSingleLanguageIfShown(page) {
  const choices = [
    /Publicar solo este idioma/i,
    /Publish only this language/i,
    /Publier uniquement cette langue/i,
  ];
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    for (const pattern of choices) {
      const action = page.getByText(pattern).first();
      if (await action.isVisible().catch(() => false)) {
        await action.click({ force: true });
        return true;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  return false;
}

async function screenshot(page, evidenceDir, name) {
  await mkdir(evidenceDir, { recursive: true });
  const path = join(evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

async function routeDiagnostics(page) {
  return page.evaluate(() => ({
    hash: globalThis.location?.hash ?? "",
    route: globalThis.localStorage?.getItem("web.navigation.route") ?? "",
    shellRoute: document.documentElement.getAttribute("data-quata-shell-route") ?? "",
    officialIds: Array.from(document.querySelectorAll("[id]"))
      .map((node) => node.id)
      .filter((id) => /official/i.test(id))
      .slice(0, 40),
    text: (document.body?.innerText ?? "").replace(/\s+/g, " ").slice(0, 800),
  }));
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function isIgnorablePublishedVideoCorsFault(value) {
  return /Access to video at 'https:\/\/egquata\.com\/wp-content\/uploads\/[^']+\.mp4' from origin 'http:\/\/127\.0\.0\.1:/i.test(value)
    || value === "console_error:Failed to load resource: net::ERR_FAILED";
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "unknown";
  return [
    "invalid_arguments",
    "missing_environment",
    "mutation_opt_in_required",
    "missing_public_supabase_configuration",
    "invalid_public_supabase_url",
    "missing_public_wordpress_configuration",
    "distribution_missing",
    "distribution_revision_missing_or_invalid",
    "distribution_revision_mismatch",
    "distribution_source_tree_dirty",
    "static_server_start_failed",
    "public_request_failed",
    "invalid_auth_response",
    "official_create_cta_not_visible",
    "official_editor_invalid_draft_mutated",
    "official_editor_publish_request_missing",
    "created_post_readback_missing",
    "storage_cleanup_failed",
    "wordpress_cleanup_failed",
    "storage_post_cleanup_verification_failed",
    "cleanup_verification_failed",
    "marker_cleanup_verification_failed",
    "browser_runtime_fault",
  ].find((prefix) => message.startsWith(prefix)) ?? "unexpected_official_editor_web_real_evidence_failure";
}
