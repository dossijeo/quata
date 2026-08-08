#!/usr/bin/env node
import { createServer } from "node:http";
import { execFileSync } from "node:child_process";
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { chromium } from "playwright-core";

const PROFILE_ID = "11111111-1111-4111-8111-111111111111";
const ACCESS_TOKEN = "fixture.official.access.token";
const REFRESH_TOKEN = "fixture-official-refresh-token";
const WEB_SESSION_TOKEN = "fixture-official-web-session-token";

const options = parseArgs(process.argv.slice(2));
const git = gitMetadata();
const report = {
  check: "OFFICIAL-EDITOR-WEB-CTA-001",
  status: "failed",
  startedAt: new Date().toISOString(),
  git,
  pullRequest: {
    number: process.env.GITHUB_PR_NUMBER ?? process.env.PR_NUMBER ?? null,
    base: process.env.GITHUB_BASE_SHA ?? null,
    head: process.env.GITHUB_HEAD_SHA ?? null,
    merge: process.env.GITHUB_MERGE_SHA ?? null,
  },
  steps: [],
  requests: [],
  evidence: {},
};

let distribution;
let server;
let browser;
let context;
try {
  if (options.requirePrIdentity) assertPullRequestIdentity(report.pullRequest, git.head);
  distribution = await configuredDistribution(options.distribution);
  server = await startServer(distribution, report.requests);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
  await context.addInitScript(({ profileId, accessToken, refreshToken, webSessionToken }) => {
    localStorage.setItem("quata_web_access_token", accessToken);
    localStorage.setItem("quata_web_refresh_token", refreshToken);
    localStorage.setItem("quata_web_session_token", webSessionToken);
    localStorage.setItem("quata_web_user_id", profileId);
    localStorage.setItem("quata_web_expires_at", String(Math.floor(Date.now() / 1000) + 3600));
    localStorage.setItem("web.auth.session_ready", "true");
    localStorage.setItem("quata_web_client_instance_id", `official-editor-web-${crypto.randomUUID()}`);
  }, {
    profileId: PROFILE_ID,
    accessToken: ACCESS_TOKEN,
    refreshToken: REFRESH_TOKEN,
    webSessionToken: WEB_SESSION_TOKEN,
  });

  const page = await context.newPage();
  const faults = [];
  page.on("pageerror", () => faults.push("pageerror"));
  page.on("console", (entry) => {
    if (entry.type() !== "error") return;
    const text = entry.text();
    if (/403|postgrest_http_403|fixture_publish_forbidden/i.test(text)) return;
    faults.push(`console_error:${text.slice(0, 120)}`);
  });
  await page.goto(`${server.origin}/#official`, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await page.waitForFunction(() =>
    localStorage.getItem("web.navigation.route") === "official" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "official",
    { timeout: 45_000 },
  );
  await waitForRequest("community_profiles", report.requests);
  report.steps.push("official_shell_mounted_with_authenticated_fixture");

  const createButton = page.getByLabel(/Crear comunicado|Create notice|Créer un communiqué/i).first();
  await createButton.waitFor({ timeout: 45_000 });
  const buttonBox = await createButton.boundingBox();
  if (!buttonBox || buttonBox.width <= 0 || buttonBox.height <= 0) {
    throw new Error("official_create_cta_not_visible");
  }
  report.steps.push("shared_create_cta_visible_for_official_profile");
  report.evidence.official = await screenshot(page, options.evidenceDir, "web-official-create-cta-visible");

  await createButton.click({ force: true });
  await page.waitForFunction(() =>
    localStorage.getItem("web.navigation.route") === "official-editor" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "official-editor",
    { timeout: 45_000 },
  );
  report.steps.push("create_cta_opens_shared_official_editor_route");
  await page.getByText(/Crear publicaci[oó]n oficial|Create official post|Créer une publication officielle/i)
    .waitFor({ timeout: 45_000 });
  report.evidence.editor = await screenshot(page, options.evidenceDir, "web-official-editor-opened");

  const publishButton = page.locator("#official-editor-publish").first();
  await publishButton.waitFor({ state: "attached", timeout: 45_000 });
  await clickSemanticElement(page, "official-editor-publish");
  await expectSemanticText(page, "official-editor-feedback", /A(?:ñ|Ã±)ade texto|Add text|Ajoute/i);
  if (report.requests.some((entry) => entry.table === "official_posts" && entry.method === "POST")) {
    throw new Error("official_editor_invalid_draft_mutated");
  }
  report.steps.push("empty_publish_shows_shared_validation_feedback_without_mutation");
  report.evidence.validation = await screenshot(page, options.evidenceDir, "web-official-editor-validation-feedback");

  await clickSemanticElement(page, "official-editor-body-action");
  const bodyField = page.getByRole("textbox").first();
  await bodyField.waitFor({ state: "attached", timeout: 15_000 });
  await bodyField.click({ force: true });
  await page.keyboard.insertText("Official editor reversible evidence");
  await page.locator("#official-editor-preview")
    .getByText(/Official editor reversible evidence/i)
    .waitFor({ state: "attached", timeout: 15_000 });
  await clickSemanticElement(page, "official-editor-publish");
  await waitForRequest("official_posts", report.requests, 45_000, (entry) => entry.method === "POST" && entry.authenticated);
  if (!report.requests.some((entry) => entry.table === "official_posts" && entry.method === "POST" && entry.authenticated)) {
    throw new Error("official_editor_publish_request_missing");
  }
  await expectSemanticText(
    page,
    "official-editor-feedback",
    /fixture_publish_forbidden|web_official_publish_failed|web_official_postgrest|postgrest_http_403/i,
  );
  report.steps.push("valid_publish_attempt_uses_shared_postgrest_plan_and_fails_closed");
  report.evidence.publishFailure = await screenshot(page, options.evidenceDir, "web-official-editor-publish-fails-closed");

  if (faults.length) {
    report.faults = faults;
    throw new Error("browser_runtime_fault");
  }
  if (!report.requests.some((entry) => entry.table === "community_profiles" && entry.authenticated)) {
    throw new Error("official_profile_permission_read_missing");
  }
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
} finally {
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  await server?.close().catch(() => {});
  await rm(distribution, { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`Official editor Web evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Official editor Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Official editor Web evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/official-editor-evidence.json"),
    evidenceDir: resolve("build-reports/web/official-editor-evidence"),
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    if (key === "--require-pr-identity") {
      parsed.requirePrIdentity = true;
      continue;
    }
    const value = args[++index];
    if (!["--dist", "--chrome", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) {
      throw new Error("invalid_arguments");
    }
    if (key === "--dist") parsed.distribution = resolve(value);
    if (key === "--chrome") parsed.chrome = resolve(value);
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
  }
  return parsed;
}

async function configuredDistribution(source) {
  if (!(await stat(source).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  await assertDistributionRevision(source);
  const target = await mkdtemp(join(tmpdir(), "quata-official-editor-dist-"));
  await cp(source, target, { recursive: true });
  const index = join(target, "index.html");
  let html = await readFile(index, "utf8");
  html = html.replace('name="quata-supabase-url" content=""', 'name="quata-supabase-url" content="__LOCAL_BACKEND__"')
    .replace('name="quata-supabase-publishable-key" content=""', 'name="quata-supabase-publishable-key" content="fixture-public-anon-key"');
  await writeFile(index, html, "utf8");
  return target;
}

async function startServer(root, requests) {
  let origin;
  const server = createServer(async (request, response) => {
    try {
      if (!origin) throw new Error("server_origin_missing");
      const url = new URL(request.url ?? "/", origin);
      if (url.pathname.startsWith("/rest/v1/")) {
        return handleRest(url, request, response, requests);
      }
      if (url.pathname === "/favicon.ico") return response.writeHead(204).end();
      const file = resolve(root, `.${url.pathname === "/" ? "/index.html" : decodeURIComponent(url.pathname)}`);
      if (!file.startsWith(`${root}\\`) && !file.startsWith(`${root}/`) && file !== root) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      let body = await readFile(file);
      if (url.pathname === "/" || url.pathname.endsWith("index.html")) {
        body = Buffer.from(body.toString("utf8").replace("__LOCAL_BACKEND__", origin), "utf8");
      }
      response.writeHead(200, {
        "Content-Type": contentType(file),
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
        "Cache-Control": "no-store",
      });
      response.end(body);
    } catch {
      response.writeHead(500).end();
    }
  });
  await new Promise((ok, fail) => { server.once("error", fail); server.listen(0, "127.0.0.1", ok); });
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("static_server_start_failed");
  origin = `http://127.0.0.1:${address.port}`;
  return { origin, close: () => new Promise((ok, fail) => server.close((error) => error ? fail(error) : ok())) };
}

function handleRest(url, request, response, requests) {
  const table = url.pathname.replace("/rest/v1/", "");
  const authenticated = request.headers.authorization === `Bearer ${ACCESS_TOKEN}`;
  const query = Object.fromEntries(url.searchParams.entries());
  requests.push({ table, method: request.method, authenticated, query });
  if (request.method === "POST" && table === "rpc/quata_chat_get_inbox") {
    if (!authenticated) return json(response, 401, { error: "fixture_auth_required" });
    return json(response, 200, []);
  }
  if (table === "official_posts" || table === "official_post_comments" || table === "official_post_likes") {
    if (request.method === "GET") return json(response, 200, []);
    if (table === "official_posts" && request.method === "POST") {
      if (!authenticated) return json(response, 401, { error: "fixture_auth_required" });
      return json(response, 403, { error: "fixture_publish_forbidden" });
    }
    return json(response, 405, { error: "fixture_mutation_forbidden" });
  }
  if (request.method !== "GET") return json(response, 405, { error: "fixture_mutation_forbidden" });
  if (table === "community_profiles") {
    if (!authenticated) return json(response, 401, { error: "fixture_auth_required" });
    if (url.searchParams.get("id") !== `in.(${PROFILE_ID})`) {
      return json(response, 400, { error: "fixture_profile_filter_required" });
    }
    return json(response, 200, [{
      id: PROFILE_ID,
      display_name: "Cuenta oficial fixture",
      barrio: "Bovano",
      neighborhood: "Bovano",
      nombre: "Cuenta oficial fixture",
      avatar_url: "",
      avatar: "",
      is_admin: "false",
      is_official: "true",
    }]);
  }
  return json(response, 404, { error: "fixture_table_missing" });
}

function gitMetadata() {
  const head = execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim();
  const status = execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], { encoding: "utf8" }).trim();
  return { head, trackedChanges: status };
}

function assertPullRequestIdentity(pullRequest, checkoutHead) {
  if (!/^\d+$/.test(String(pullRequest.number ?? ""))) throw new Error("pr_identity_missing_number");
  for (const key of ["base", "head", "merge"]) {
    if (!/^[0-9a-f]{40}$/i.test(pullRequest[key] ?? "")) throw new Error(`pr_identity_missing_${key}`);
  }
  if (pullRequest.merge.toLowerCase() !== checkoutHead.toLowerCase()) {
    throw new Error("pr_identity_checkout_not_merge");
  }
  if (pullRequest.head.toLowerCase() === pullRequest.base.toLowerCase()) {
    throw new Error("pr_identity_head_matches_base");
  }
}

async function assertDistributionRevision(source) {
  const marker = join(source, "quata-source-revision.txt");
  const revision = (await readFile(marker, "utf8").catch(() => "")).trim();
  const expected = execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim();
  const status = execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], { encoding: "utf8" }).trim();
  if (!/^[0-9a-f]{40}$/i.test(revision)) throw new Error("distribution_revision_missing_or_invalid");
  if (revision.toLowerCase() !== expected.toLowerCase()) throw new Error("distribution_revision_mismatch");
  if (status) throw new Error("distribution_source_tree_dirty");
}

function json(response, status, value) {
  response.writeHead(status, { "content-type": "application/json", "cache-control": "no-store" });
  response.end(JSON.stringify(value));
}

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"],
    [".webp", "image/webp"], [".png", "image/png"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

async function waitForRequest(table, requests, timeoutMs = 45_000, predicate = () => true) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (requests.some((entry) => entry.table === table && predicate(entry))) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`request_not_observed:${table}`);
}

async function clickSemanticElement(page, id) {
  await page.locator(`#${id}`).first().dispatchEvent("click");
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

async function screenshot(page, evidenceDir, name) {
  await mkdir(evidenceDir, { recursive: true });
  const path = join(evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  return [
    "invalid_arguments", "distribution_missing", "static_server_start_failed",
    "distribution_revision_missing_or_invalid", "distribution_revision_mismatch",
    "distribution_source_tree_dirty",
    "pr_identity_missing_number", "pr_identity_missing_base", "pr_identity_missing_head",
    "pr_identity_missing_merge", "pr_identity_checkout_not_merge", "pr_identity_head_matches_base",
    "official_create_cta_not_visible", "browser_runtime_fault",
    "official_profile_permission_read_missing", "request_not_observed",
  ].find((prefix) => message.startsWith(prefix)) ?? "official_editor_web_evidence_failure";
}
