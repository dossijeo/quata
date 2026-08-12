#!/usr/bin/env node
/**
 * Real-browser Auth/Profile read-only journey. Fixture mode is the default and is fully hermetic.
 * Remote mode explicitly accepts the deployed bridge's identity/session mutations, requires a
 * dedicated preprovisioned account, and cannot pass until global revocation has been verified.
 * Chat and every product mutation remain outside this runner.
 */
import { createServer } from "node:http";
import { execFileSync } from "node:child_process";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { chromium } from "playwright-core";
import {
  assertExplicitRefreshTokenRejection,
} from "./web-authenticated-browser-security.mjs";
import {
  DISTRIBUTION_REVISION_FILE,
  READ_ONLY_ROUTE_EXCLUSIONS,
  READ_ONLY_ROUTE_MATRIX,
  assertExactDistributionRevision,
  backendBrowserRequestDecision,
  loadRealAuthConfiguration,
} from "./web-authenticated-browser-policy.mjs";

const TURNSTILE_BOOTSTRAP = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
const STORAGE_KEYS = [
  "quata_web_access_token", "quata_web_refresh_token", "quata_web_session_token",
  "quata_web_user_id", "quata_web_expires_at", "web.auth.session_ready",
];
const FIXTURE = Object.freeze({
  // LoginUiState defaults to this prefix; the native Web input deliberately owns
  // just the local phone number, as the Compose field did before it.
  countryCode: "240",
  phone: "600000001",
  password: "fixture-only-password",
  profileId: "11111111-1111-4111-8111-111111111111",
  accessToken: "fixture.access.token",
  refreshToken: "fixture-refresh-token",
  webSessionToken: "fixture-web-session-token",
});
const PRIMARY_NAVIGATION_STRESS_SEQUENCES = Object.freeze([
  { name: "browser_back_forward", fragments: ["communities", "chat", "official", "", "profile"] },
  { name: "primary_forward", fragments: ["communities", "chat", "official", "", "profile", "communities"] },
  { name: "primary_reverse", fragments: ["", "official", "chat", "communities", "profile", ""] },
  { name: "feed_official_toggle", fragments: ["", "official"] },
  { name: "communities_chat_toggle", fragments: ["communities", "chat"] },
  { name: "direct_fragments", fragments: ["communities", "chat", "official", "", "profile"] },
]);
const NAVIGATION_STRESS_CYCLES = 50;
// Chat is intentionally remounted throughout the matrix and performs one initial inbox read.
// This bound permits those route reads while still rejecting the former 2,000+ badge restarts.
const MAX_AUTHENTICATED_INBOX_READS = NAVIGATION_STRESS_CYCLES * 16;
const PRIVATE_RETURN_FRAGMENT = "chat-sb%3Ateam%2F42?message=msg%209";
const PRIVATE_RETURN_ROUTE = "chat/sb:team/42";

const options = parseArguments(process.argv.slice(2));
const report = {
  check: "WEB-AUTH-READONLY-BROWSER-03",
  mode: options.real ? "real_existing_account_opt_in" : "hermetic_local_fixture",
  status: "failed",
  steps: [],
  cleanup: { state: "not_started" },
  bridgeEffects: {
    accepted: options.real,
    scope: "auth_identity_metadata_web_session_and_global_refresh_revocation",
    productDml: "forbidden",
  },
};
const fixtureState = { login: 0, profileReads: 0, notificationInboxReads: 0, webLogout: 0, globalLogout: 0 };
const unexpectedNetwork = [];
const blockedBackendMutations = [];
const productReadEvidence = { profileSelfReads: 0, authenticatedGets: 0, notificationInboxReads: 0, notificationInboxReadStages: [] };
let server;
let browser;
let context;
let page;
let cleanupSession;
let backend;
let stage = "initializing";
const browserDiagnostics = [];
let navigationStressFailure;

try {
  const sourceRevision = await verifyDistributionProvenance(options.distribution);
  report.sourceRevision = sourceRevision;
  const configuration = loadConfiguration(options.real);
  server = await startServer(options.distribution, fixtureState, configuration);
  backend = options.real ? configuration.baseUrl : server.origin;
  const credentials = options.real ? configuration : FIXTURE;

  stage = "launching_browser";
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: [
      "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--no-first-run",
      ...(options.real ? [] : [
        "--proxy-server=http://127.0.0.1:9",
        "--proxy-bypass-list=127.0.0.1;localhost",
      ]),
    ],
  });
  context = await browser.newContext({ locale: "es-ES", acceptDownloads: true });
  await context.route("**/*", async route => {
    const request = route.request();
    const url = request.url();
    const decision = backendBrowserRequestDecision({
      backend,
      url,
      method: request.method(),
      stage,
      body: request.postData(),
    });
    if (decision.backendApi) {
      if (!decision.allowed) {
        blockedBackendMutations.push({
          method: request.method().toUpperCase(),
          path: safeBackendPath(url, backend),
          stage,
          reason: decision.reason,
        });
        return route.abort("blockedbyclient");
      }
      observeProductRead(request, url, backend, cleanupSession, productReadEvidence, stage);
    }
    if (url.startsWith(`${server.origin}/`)) return route.continue();
    if (url === TURNSTILE_BOOTSTRAP) {
      return route.fulfill({ status: 200, contentType: "text/javascript", body: "globalThis.turnstile={};" });
    }
    if (options.real && url === `${backend}/functions/v1/quata-auth-bridge` &&
        route.request().method() === "POST") {
      const response = await route.fetch();
      const body = await response.body();
      if (response.ok()) {
        const captured = sessionFromLoginPayload(body);
        if (captured) cleanupSession = captured;
      }
      return route.fulfill({ response, body });
    }
    if (options.real && decision.allowed && url.startsWith(`${backend}/`)) return route.continue();
    unexpectedNetwork.push(safeOrigin(url));
    return route.abort("blockedbyclient");
  });
  context.on("request", request => {
    const url = request.url();
    if (!url.startsWith(`${server.origin}/`) && url !== TURNSTILE_BOOTSTRAP &&
        !(options.real && url.startsWith(`${backend}/`))) {
      unexpectedNetwork.push(safeOrigin(url));
    }
  });
  page = context.pages()[0] ?? await context.newPage();
  page.on("console", message => browserDiagnostics.push(
    options.real ? `console:${message.type()}` : `console:${message.type()}:${message.text()}`,
  ));
  page.on("pageerror", error => browserDiagnostics.push(
    options.real ? "pageerror:present" : `pageerror:${error.stack ?? error.message}`,
  ));

  stage = "mounting_real_product";
  // Start on Auth so the Compose hash listener is installed before the private-route contract
  // drives its transition. Initial deep-link bootstrap is covered separately by the raw-CDP
  // smoke; this authenticated journey must not depend on a browser omitting hashchange for its
  // already-present initial fragment.
  await page.goto(`${server.origin}/?quata-auth-e2e=1&backend=${encodeURIComponent(backend)}#auth`);
  await page.waitForFunction(() => {
    const root = document.querySelector("#quata-root");
    return globalThis.__quataAuthE2eProduct?.version === 1 && root &&
      (root.childElementCount > 0 || (root.shadowRoot?.childElementCount ?? 0) > 0);
  });
  report.steps.push("real_compose_auth_shell_mounted");
  await page.waitForFunction(() =>
    location.hash === "#auth" && localStorage.getItem("web.navigation.route") === "auth" &&
    !document.documentElement.hasAttribute("data-quata-shell-route"),
  );
  report.steps.push("auth_router_bootstrap_ready_before_private_transition");

  await resolveAuthSurface(page);
  stage = "private_participation_gate";
  await assertPrivateAuthenticationGate(page);
  report.steps.push("encoded_private_chat_returns_to_public_feed_with_visible_participation_gate");
  await invokeAuthGateAction(page, "dismiss");
  await assertDismissedAuthenticationGate(page);
  report.steps.push("participation_gate_dismiss_keeps_public_feed");
  await assertPrivateAuthenticationGate(page);
  await invokeAuthGateAction(page, "chooseRegister");
  await assertFullScreenAuthDestination(page, "register");
  report.steps.push("participation_gate_create_account_opens_fullscreen_register");
  stage = "register_legal_documents";
  report.legalDocuments = await assertRegisterLegalDocumentViewer(page);
  report.steps.push("register_shared_legal_documents_opened_from_local_assets");
  stage = "anonymous_public_shell";
  await assertAnonymousPublicShell(page);
  await assertPrivateAuthenticationGate(page);
  await invokeAuthGateAction(page, "chooseLogin");
  await assertFullScreenAuthDestination(page, "login");
  report.steps.push("anonymous_feed_neighborhoods_official_notifications_shell_and_private_chat_participation_gate");
  report.steps.push("participation_gate_existing_account_opens_fullscreen_login");
  // The repository bridge submits through the real AuthRepository after the E2E has exercised
  // both actual callbacks bound to the common Compose participation dialog.
  stage = "compose_auth_bridge_login";
  await loginWithComposeAuthBridge(page, credentials);
  report.steps.push("compose_auth_bridge_login_product_repository_activation");
  await assertAutomaticLoginReturn(page);
  cleanupSession = await readSession(page);
  assertCompleteSession(cleanupSession);

  stage = "authenticated_browser_restore";
  await page.reload();
  await page.waitForFunction(() => globalThis.__quataAuthE2eProduct?.version === 1);
  await page.waitForFunction(({ fragment, route }) => {
    const root = document.querySelector("#quata-root");
    return localStorage.getItem("web.auth.session_ready") === "true" && location.hash === `#${fragment}` &&
      localStorage.getItem("web.navigation.route") === route && root &&
      (root.childElementCount > 0 || (root.shadowRoot?.childElementCount ?? 0) > 0);
  }, { fragment: PRIVATE_RETURN_FRAGMENT, route: PRIVATE_RETURN_ROUTE });
  if (browserDiagnostics.some(entry => entry.includes("#auth"))) throw new Error("private_reload_redirected_to_auth");
  if (browserDiagnostics.some(entry => entry.startsWith("pageerror:"))) throw new Error("feed_mount_pageerror");
  report.steps.push("product_session_restored_after_reload");

  stage = "authenticated_route_matrix";
  for (const route of READ_ONLY_ROUTE_MATRIX) {
    await navigateReadOnlyRoute(page, route);
    assertNoBlockedBackendMutations(blockedBackendMutations);
    report.steps.push(`read_only_route_${route.route}`);
    if (route.route === "profile") {
      await waitFor(
        async () => productReadEvidence.profileSelfReads > 0,
        "product_profile_get_not_observed",
      );
      report.steps.push("product_profile_authenticated_get_observed");
    }
  }
  if (browserDiagnostics.some(entry => entry.startsWith("pageerror:"))) throw new Error("read_only_route_pageerror");
  if (productReadEvidence.authenticatedGets < 1) throw new Error("authenticated_product_get_not_observed");
  assertNoBlockedBackendMutations(blockedBackendMutations);

  stage = "authenticated_account_settings_legal_documents";
  report.accountSettingsLegalDocuments = await assertAccountSettingsLegalDocumentViewer(page, options.output);
  report.steps.push("account_settings_shared_legal_documents_opened_from_local_assets");

  stage = "authenticated_settings_push_consent";
  await assertAuthenticatedSettingsPushConsent(page, options.output);
  report.steps.push("authenticated_settings_push_consent_uses_trusted_native_click");

  stage = "authenticated_navigation_stress";
  report.navigationStress = await runAuthenticatedNavigationStress(page, browserDiagnostics);
  if (productReadEvidence.notificationInboxReads > MAX_AUTHENTICATED_INBOX_READS) {
    throw new Error("authenticated_inbox_read_storm");
  }
  report.navigationStress.finalShellScreenshot = await captureShellScreenshot(page, options.output);
  report.steps.push("authenticated_navigation_stress_6_sequences_50_cycles");

  stage = "compose_auth_bridge_logout";
  await logoutWithComposeAuthBridge(page);
  report.steps.push("compose_auth_bridge_logout_product_coordinator_activation");
  await page.waitForFunction(() => localStorage.getItem("web.auth.session_ready") !== "true");
  if ((await page.evaluate(keys => keys.some(key => localStorage.getItem(key) !== null), STORAGE_KEYS))) {
    throw new Error("product_logout_storage_remains");
  }
  stage = "logout_to_anonymous_public_shell";
  await assertAnonymousPublicShellAfterLogout(page);
  report.steps.push("product_logout_returns_to_anonymous_feed_and_official_shell");
  assertNoBlockedBackendMutations(blockedBackendMutations);

  stage = "global_session_cleanup";
  await revokeAndVerify(backend, credentials.publishableKey ?? "fixture-public-key", cleanupSession);
  cleanupSession = null;
  report.cleanup = { state: "sessions_revoked_and_verified" };

  if (!options.real) {
    if (fixtureState.login !== 1 || fixtureState.profileReads < 1 || fixtureState.notificationInboxReads < 1 ||
        fixtureState.webLogout !== 1 || fixtureState.globalLogout !== 1) {
      throw new Error("fixture_journey_incomplete");
    }
    if (unexpectedNetwork.length !== 0) throw new Error("unexpected_external_network");
  }
  await page.waitForTimeout(100);
  assertNoBlockedBackendMutations(blockedBackendMutations);
  report.readOnlyEvidence = {
    routes: READ_ONLY_ROUTE_MATRIX.map(route => route.route),
    excludedRoutes: READ_ONLY_ROUTE_EXCLUSIONS.flatMap(exclusion => exclusion.fragments),
    authenticatedGets: productReadEvidence.authenticatedGets,
    profileSelfReads: productReadEvidence.profileSelfReads,
    notificationInboxReads: productReadEvidence.notificationInboxReads,
    notificationInboxReadStages: productReadEvidence.notificationInboxReadStages,
    blockedMutations: blockedBackendMutations.length,
  };
  report.status = "passed";
} catch (error) {
  report.error = safeError(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  if (navigationStressFailure) report.navigationStressFailure = navigationStressFailure;
  report.failureStage = stage;
  if (page) {
    const failureCapture = options.output.replace(/\.json$/i, "-failure.png");
    await page.screenshot({ path: failureCapture, fullPage: true }).catch(() => null);
    report.browserState = await page.evaluate(() => ({
      productBridge: globalThis.__quataAuthE2eProduct?.version === 1,
      rootPresent: document.querySelector("#quata-root") !== null,
      canvasCount: document.querySelectorAll("#quata-root canvas").length,
      rootChildren: document.querySelector("#quata-root")?.childElementCount ?? 0,
      shadowChildren: document.querySelector("#quata-root")?.shadowRoot?.childElementCount ?? 0,
      nativeControls: Array.from(document.querySelector("#quata-root")?.shadowRoot?.querySelectorAll("input, button") ?? [])
        .map(element => {
          const rect = element.getBoundingClientRect();
          return { tag: element.tagName, name: element.getAttribute("aria-label"), type: element.getAttribute("type"), disabled: element.disabled, rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height } };
        }),
      hash: location.hash,
      shellRoute: document.documentElement.getAttribute("data-quata-shell-route"),
      primarySelectedRoute: document.documentElement.getAttribute("data-quata-primary-selected-route"),
    })).catch(() => ({ unavailable: true }));
    report.browserState.failureScreenshot = failureCapture;
    report.browserState.diagnostics = browserDiagnostics.slice(-20);
  }
} finally {
  if (cleanupSession && backend) {
    try {
      const key = options.real ? process.env.QUATA_SUPABASE_PUBLISHABLE_KEY?.trim() : "fixture-public-key";
      await revokeAndVerify(backend, key, cleanupSession);
      report.cleanup = { state: "sessions_revoked_and_verified_after_failure" };
      cleanupSession = null;
    } catch {
      report.cleanup = { state: "revocation_unverified", action: "revoke_existing_test_account_sessions" };
      report.status = "failed";
    }
  }
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  await server?.close().catch(() => {});
  report.finishedAt = new Date().toISOString();
  report.networkPolicy = {
    blockedBackendMutations: blockedBackendMutations.map(({ method, path, stage, reason }) => ({ method, path, stage, reason })),
    notificationInboxReads: productReadEvidence.notificationInboxReads,
  };
  report.network = options.real ? { policy: "local_and_exact_configured_backend" } : { policy: "local_only", unexpectedOrigins: [...new Set(unexpectedNetwork)].length };
  await writeSafeReport(options.output, report);
}

if (report.status !== "passed") {
  console.error(`Authenticated browser E2E failed: ${report.error ?? "unknown_failure"}.`);
  process.exitCode = 1;
} else {
  console.log(`Authenticated browser E2E passed (${report.mode}).`);
}

function parseArguments(args) {
  const parsed = {
    real: false,
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || (process.platform === "win32"
      ? "C:/Program Files/Google/Chrome/Application/chrome.exe" : "google-chrome"),
    output: resolve("build-reports/web/authenticated-browser-e2e.json"),
  };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--real") parsed.real = true;
    else if (["--dist", "--chrome", "--out"].includes(argument)) {
      const value = args[++index];
      if (!value || value.startsWith("--")) throw new Error("invalid_arguments");
      parsed[argument === "--dist" ? "distribution" : argument === "--chrome" ? "chrome" : "output"] = resolve(value);
    } else if (argument === "--help" || argument === "-h") {
      console.log("Usage: node scripts/web-authenticated-browser-e2e.mjs [--real] [--dist DIR] [--chrome PATH] [--out REPORT]");
      process.exit(0);
    } else throw new Error("invalid_arguments");
  }
  return parsed;
}

function loadConfiguration(real) {
  if (!real) return {};
  return loadRealAuthConfiguration(process.env);
}

async function startServer(distribution, state, configuration) {
  if (!(await stat(distribution).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  let origin;
  const server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? "/", "http://localhost");
      if (url.pathname === "/functions/v1/quata-auth-bridge") {
        const body = await jsonBody(request);
        if (request.method !== "POST" || body.action !== "web_login" ||
            body.country_code !== FIXTURE.countryCode || body.phone_local !== FIXTURE.phone ||
            body.password !== FIXTURE.password || typeof body.client_instance_id !== "string") {
          return json(response, 401, { error: "invalid_fixture_login" });
        }
        state.login += 1;
        return json(response, 200, {
          profile: { id: FIXTURE.profileId, display_name: "Fixture User" },
          user: { id: "22222222-2222-4222-8222-222222222222" },
          session: {
            access_token: FIXTURE.accessToken, refresh_token: FIXTURE.refreshToken,
            expires_at: Math.floor(Date.now() / 1000) + 3600,
          },
          web_session: { token: FIXTURE.webSessionToken },
        });
      }
      if (url.pathname === "/functions/v1/quata-web-push") {
        const body = await jsonBody(request);
        if (request.method === "POST" && body.action === "logout" &&
            request.headers.authorization === `Bearer ${FIXTURE.accessToken}` &&
            request.headers["x-quata-web-session"] === FIXTURE.webSessionToken) {
          state.webLogout += 1;
          return json(response, 200, { ok: true });
        }
        return json(response, 405, { error: "fixture_mutation_forbidden" });
      }
      if (url.pathname === "/auth/v1/logout") {
        if (request.method !== "POST") return json(response, 405, { error: "fixture_method_forbidden" });
        if (request.headers.authorization === `Bearer ${FIXTURE.accessToken}`) state.globalLogout += 1;
        return json(response, 204, null);
      }
      if (url.pathname === "/auth/v1/user") {
        return json(response, state.globalLogout > 0 ? 401 : 200, state.globalLogout > 0 ? { error: "revoked" } : { id: FIXTURE.profileId });
      }
      if (url.pathname === "/auth/v1/token" && url.searchParams.get("grant_type") === "refresh_token") {
        return json(response, state.globalLogout > 0 ? 400 : 200, state.globalLogout > 0
          ? { error: "refresh_token_revoked" }
          : { access_token: FIXTURE.accessToken, refresh_token: FIXTURE.refreshToken, expires_in: 3600 });
      }
      if (url.pathname === "/rest/v1/community_profiles") {
        if (request.method !== "GET") return json(response, 405, { error: "fixture_product_mutation_forbidden" });
        state.profileReads += 1;
        return json(response, 200, [{
          id: FIXTURE.profileId,
          display_name: "Fixture User",
          neighborhood: "Fixture District",
          country_code: FIXTURE.countryCode,
          phone_local: FIXTURE.phone,
        }]);
      }
      if (url.pathname === "/rest/v1/rpc/quata_chat_get_inbox") {
        const body = await jsonBody(request);
        if (request.method !== "POST" || request.headers.authorization !== `Bearer ${FIXTURE.accessToken}` ||
            body.p_actor_profile_id !== FIXTURE.profileId || body.p_limit !== 100) {
          return json(response, 405, { error: "fixture_notification_inbox_read_forbidden" });
        }
        state.notificationInboxReads += 1;
        return json(response, 200, { threads: [], messages: [], profiles: [] });
      }
      if (url.pathname === "/rest/v1/rpc/quata_chat_search_conversation_candidates") {
        const body = await jsonBody(request);
        if (request.method !== "POST" || request.headers.authorization !== `Bearer ${FIXTURE.accessToken}` ||
            body.p_actor_profile_id !== FIXTURE.profileId || typeof body.p_query !== "string" ||
            !Number.isInteger(body.p_limit) || body.p_limit < 1 || body.p_limit > 50 ||
            !Number.isInteger(body.p_offset) || body.p_offset < 0 || Object.keys(body).length !== 4) {
          return json(response, 405, { error: "fixture_chat_candidate_directory_read_forbidden" });
        }
        return json(response, 200, { items: [], has_more: false, next_offset: body.p_offset, total: 0, actor_neighborhood: "Fixture District" });
      }
      if (url.pathname.startsWith("/rest/v1/")) {
        if (request.method !== "GET") return json(response, 405, { error: "fixture_product_mutation_forbidden" });
        return json(response, 200, []);
      }
      if (url.pathname === "/favicon.ico") return response.writeHead(204).end();

      const pathname = decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname);
      const file = resolve(distribution, `.${pathname}`);
      const rel = relative(distribution, file);
      if (rel.startsWith(`..${sep}`) || rel === ".." || isAbsolute(rel)) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      let content = await readFile(file);
      if (pathname === "/index.html") {
        const backendFromQuery = url.searchParams.get("backend") || origin;
        const publishableKey = configuration.publishableKey || "fixture-public-key";
        content = Buffer.from(content.toString("utf8")
          .replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${escapeHtml(backendFromQuery)}"`)
          .replace('name="quata-supabase-publishable-key" content=""', `name="quata-supabase-publishable-key" content="${escapeHtml(publishableKey)}"`));
      }
      response.writeHead(200, {
        "Content-Type": contentType(file), "Cache-Control": "no-store",
        "Cross-Origin-Opener-Policy": "same-origin", "Cross-Origin-Embedder-Policy": "require-corp",
      }).end(content);
    } catch {
      response.writeHead(500).end();
    }
  });
  await new Promise((resolveServer, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolveServer);
  });
  origin = `http://127.0.0.1:${server.address().port}`;
  return {
    origin,
    close: () => new Promise((resolveServer, reject) => server.close(error => error ? reject(error) : resolveServer())),
  };
}

async function verifyDistributionProvenance(distribution) {
  if (!(await stat(distribution).catch(() => null))?.isDirectory()) {
    throw new Error("distribution_missing");
  }
  const repositoryRoot = resolve(import.meta.dirname, "..");
  let repositoryRevision;
  let trackedChanges;
  try {
    repositoryRevision = execFileSync("git", ["rev-parse", "HEAD"], {
      cwd: repositoryRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
    trackedChanges = execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], {
      cwd: repositoryRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    });
  } catch {
    throw new Error("repository_revision_unavailable");
  }
  const markerRevision = await readFile(join(distribution, DISTRIBUTION_REVISION_FILE), "utf8")
    .then(value => value.trim())
    .catch(() => "");
  return assertExactDistributionRevision({ repositoryRevision, markerRevision, trackedChanges });
}

async function navigateReadOnlyRoute(page, route) {
  await page.evaluate(fragment => {
    globalThis.location.hash = fragment;
  }, route.fragment);
  await page.waitForFunction(expectedRoute => {
    const root = document.querySelector("#quata-root");
    return localStorage.getItem("web.navigation.route") === expectedRoute && root &&
      (root.childElementCount > 0 || (root.shadowRoot?.childElementCount ?? 0) > 0);
  }, route.route);
  await page.waitForTimeout(150);
}

/** Exercises the actual Compose hash router before authenticating against the hermetic bridge. */
async function assertAnonymousPublicShell(page) {
  for (const route of [
    { fragment: "", route: "feed" },
    { fragment: "communities", route: "communities" },
    { fragment: "official", route: "official" },
    { fragment: "notifications", route: "notifications" },
  ]) {
    await page.evaluate(fragment => { globalThis.location.hash = fragment; }, route.fragment);
    await page.waitForFunction(expected =>
      localStorage.getItem("web.navigation.route") === expected &&
      document.documentElement.getAttribute("data-quata-shell-route") === expected,
    route.route);
  }
}

async function assertPrivateAuthenticationGate(page) {
  await page.evaluate(fragment => { globalThis.location.hash = fragment; }, PRIVATE_RETURN_FRAGMENT);
  await page.waitForFunction(() =>
    location.hash === "" && localStorage.getItem("web.navigation.route") === "feed" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "feed" &&
    document.documentElement.getAttribute("data-quata-auth-required-prompt") === "visible" &&
    document.documentElement.getAttribute("data-quata-auth-pending-route") ===
      "chat-sb%3Ateam%2F42?message=msg%209" &&
    globalThis.__quataAuthGateE2eProduct?.version === 1,
  );
}

async function invokeAuthGateAction(page, action) {
  await page.evaluate(name => {
    const bridge = globalThis.__quataAuthGateE2eProduct;
    if (bridge?.version !== 1 || typeof bridge[name] !== "function") {
      throw new Error(`compose_auth_gate_action_missing_${name}`);
    }
    bridge[name]();
  }, action);
}

async function assertDismissedAuthenticationGate(page) {
  await page.waitForFunction(() =>
    location.hash === "" &&
    localStorage.getItem("web.navigation.route") === "feed" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "feed" &&
    !document.documentElement.hasAttribute("data-quata-auth-required-prompt") &&
    !document.documentElement.hasAttribute("data-quata-auth-pending-route"),
  );
}

async function assertFullScreenAuthDestination(page, destination) {
  const marker = destination === "register"
    ? { destination, visibleText: "Crea tu cuenta" }
    : { destination, visibleText: "Inicia sesion" };
  await page.waitForFunction(expected =>
    location.hash === "#auth" &&
    localStorage.getItem("web.navigation.route") === "auth" &&
    !document.documentElement.hasAttribute("data-quata-shell-route") &&
    !document.documentElement.hasAttribute("data-quata-auth-required-prompt") &&
    (document.documentElement.getAttribute("data-quata-auth-destination") === expected.destination ||
      [...document.querySelectorAll("*")].some(element =>
        (element.innerText || element.textContent || "").includes(expected.visibleText))),
  marker);
}

async function assertRegisterLegalDocumentViewer(page) {
  await scrollRegisterLegalLinksIntoView(page);
  return [
    await clickAndCaptureDocumentViewer(page, /privacidad|Privacy policy/i, "privacy_es.docx", 0),
    await clickAndCaptureDocumentViewer(page, /Seguridad infantil|Seguridad de menores|Child safety/i, "child_safety_es.docx", 1),
  ];
}

async function assertAccountSettingsLegalDocumentViewer(page, reportOutput) {
  const evidence = {};
  for (const route of ["profile", "settings"]) {
    await page.evaluate(fragment => { globalThis.location.hash = fragment; }, route);
    await waitForShellRoute(page, route);
    if (route === "profile") await returnToProfileOverview(page);
    await scrollLegalLinksIntoView(page);
    const screenshot = reportOutput.replace(/\.json$/i, `.legal-${route}.png`);
    await page.screenshot({ path: screenshot, fullPage: true });
    evidence[route] = {
      screenshot,
      documents: [
        await clickAndCaptureDocumentViewer(page, /privacidad|Privacy policy/i, "privacy_es.docx", 0),
        await clickAndCaptureDocumentViewer(page, /Seguridad infantil|Seguridad de menores|Child safety/i, "child_safety_es.docx", 1),
      ],
    };
  }
  return evidence;
}

async function scrollRegisterLegalLinksIntoView(page) {
  await scrollLegalLinksIntoView(page);
}

async function scrollLegalLinksIntoView(page) {
  for (let attempt = 0; attempt < 8; attempt += 1) {
    if (await findVisibleTextBounds(page, /privacidad|Privacy policy/i)) return;
    await page.mouse.wheel(0, 720);
    await page.keyboard.press("PageDown").catch(() => {});
    await page.waitForTimeout(150);
  }
}

async function returnToProfileOverview(page) {
  const managementVisible = await findVisibleTextBounds(page, /Gestión de cuenta|Account management/i);
  if (!managementVisible) return;
  await page.mouse.click(Math.max(8, managementVisible.x - 24), managementVisible.y + managementVisible.height / 2);
  await page.waitForFunction(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    return [scope, ...scope.querySelectorAll("*")].some(element =>
      (element.innerText || element.textContent || "").includes("Configurar contactos de emergencia") ||
      (element.innerText || element.textContent || "").includes("Documentos legales"));
  });
}

async function clickAndCaptureDocumentViewer(page, pattern, expectedName, fallbackIndex) {
  const previousOpenCount = await page.evaluate(() =>
    Array.isArray(globalThis.__quataDocumentOpenEvidence) ? globalThis.__quataDocumentOpenEvidence.length : 0,
  );
  const nativeButton = page.getByRole("button", { name: pattern }).first();
  if (await nativeButton.count()) {
    await nativeButton.click({ timeout: 3_000 }).catch(async () => {
      const box = await waitForTextBounds(page, pattern, 2_000).catch(() => legalFallbackBounds(page, fallbackIndex));
      await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    });
  } else {
    const box = await waitForTextBounds(page, pattern, 2_000).catch(() => legalFallbackBounds(page, fallbackIndex));
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  }
  const openedHandle = await page.waitForFunction(({ name, previousCount }) =>
    (Array.isArray(globalThis.__quataDocumentOpenEvidence) ? globalThis.__quataDocumentOpenEvidence : [])
      .slice(previousCount)
      .find(event => event?.displayName === name && event?.reference?.endsWith(`legal/${name}`)),
  { name: expectedName, previousCount: previousOpenCount }, { timeout: 30_000 });
  const opened = await openedHandle.jsonValue();
  await page.waitForFunction((name) => {
    const viewer = document.querySelector("[data-quata-docmentis-viewer='true']");
    return viewer?.getAttribute("aria-label") === name;
  }, expectedName, { timeout: 5_000 }).catch(() => null);
  const renderReady = await page.evaluate(() =>
    document.querySelector("[data-quata-docmentis-viewer='true']")
      ?.getAttribute("data-quata-docmentis-render-ready") === "true",
  );
  const overlayVisible = await page.evaluate((name) =>
    document.querySelector("[data-quata-docmentis-viewer='true']")?.getAttribute("aria-label") === name,
  expectedName);
  if (overlayVisible) {
    await page.getByRole("button", { name: "Close document viewer" }).click();
    await page.waitForFunction(() => document.querySelector("[data-quata-docmentis-viewer='true']") === null);
  }
  return {
    displayName: expectedName,
    localAsset: opened.reference.endsWith(`legal/${expectedName}`) ? `legal/${expectedName}` : opened.reference,
    viewer: "docmentis-overlay",
    overlayVisible,
    renderReady,
  };
}

async function clickVisibleText(page, pattern) {
  const box = await waitForTextBounds(page, pattern, 2_000);
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
}

async function legalFallbackBounds(page, index) {
  return await page.evaluate((fallbackIndex) => {
    const root = document.querySelector("#quata-root");
    const rect = root?.getBoundingClientRect();
    if (!rect || rect.width <= 0 || rect.height <= 0) throw new Error("legal_root_missing");
    const isWide = rect.width >= 720;
    return isWide
      ? { x: rect.x + rect.width * 0.47, y: rect.y + rect.height * (0.79 + fallbackIndex * 0.07), width: rect.width * 0.12, height: 34 }
      : { x: rect.x + rect.width * 0.08, y: rect.y + rect.height * (0.82 + fallbackIndex * 0.07), width: rect.width * 0.84, height: 34 };
  }, index);
}

async function waitForTextBounds(page, pattern, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const box = await findVisibleTextBounds(page, pattern);
    if (box) return box;
    await page.waitForTimeout(100);
  }
  throw new Error(`legal_text_target_missing:${pattern}`);
}

async function findVisibleTextBounds(page, pattern) {
  return await page.evaluate(({ source, flags }) => {
    const expression = new RegExp(source, flags);
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const candidates = [scope, ...scope.querySelectorAll("*")].filter(Boolean);
    const matches = [];
    for (const element of candidates) {
      if (typeof element.getBoundingClientRect !== "function") continue;
      const text = element.innerText || element.textContent || "";
      if (!expression.test(text)) continue;
      const rect = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      if (rect.width > 0 && rect.height > 0 && style.visibility !== "hidden" && style.display !== "none") {
        matches.push({ x: rect.x, y: rect.y, width: rect.width, height: rect.height, area: rect.width * rect.height });
      }
    }
    matches.sort((left, right) => left.area - right.area);
    const match = matches[0];
    return match ? { x: match.x, y: match.y, width: match.width, height: match.height } : null;
  }, { source: pattern.source, flags: pattern.flags });
}

async function assertAutomaticLoginReturn(page) {
  await page.waitForFunction(({ fragment, route }) =>
    localStorage.getItem("web.auth.session_ready") === "true" &&
    location.hash === `#${fragment}` &&
    localStorage.getItem("web.navigation.route") === route &&
    document.documentElement.getAttribute("data-quata-shell-route") === route,
  { fragment: PRIVATE_RETURN_FRAGMENT, route: PRIVATE_RETURN_ROUTE });
}

async function assertAnonymousPublicShellAfterLogout(page) {
  await page.waitForFunction(() =>
    location.hash === "" && localStorage.getItem("web.navigation.route") === "feed" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "feed",
  );
  await page.evaluate(() => { globalThis.location.hash = "official"; });
  await page.waitForFunction(() =>
    localStorage.getItem("web.auth.session_ready") !== "true" &&
    localStorage.getItem("web.navigation.route") === "official" &&
    document.documentElement.getAttribute("data-quata-shell-route") === "official",
  );
}

/**
 * The permission request must originate in the real, authenticated Settings control. Playwright's
 * locator click is a trusted browser interaction; the local Notification mock lets this fixture
 * prove that the request begins while the transient activation is still present without showing a
 * real browser prompt or registering a subscription.
 */
async function assertAuthenticatedSettingsPushConsent(page, reportOutput) {
  await page.evaluate(() => {
    localStorage.setItem("web.push.consent.v1", "disabled");
    globalThis.__quataPushPermissionProbe = [];
    Object.defineProperty(globalThis, "Notification", {
      configurable: true,
      value: {
        permission: "default",
        requestPermission: () => {
          globalThis.__quataPushPermissionProbe.push({
            active: globalThis.navigator?.userActivation?.isActive === true,
          });
          return Promise.resolve("denied");
        },
      },
    });
    globalThis.location.hash = "settings";
  });
  report.pushConsentEvidence = { stage: "settings_ready" };
  await waitForShellRoute(page, "settings");
  await waitFor(async () => {
    const availability = await page.evaluate(() => {
      const button = document.querySelector("#quata-root")?.shadowRoot
        ?.querySelector('button[aria-label="Activar notificaciones"]');
      const rect = button?.getBoundingClientRect();
      return { exists: !!button, visible: !!rect && rect.width > 0 && rect.height > 0 };
    });
    report.pushConsentEvidence = { stage: "settings_ready", availability };
    return availability.exists && availability.visible;
  }, "push_control_not_ready");
  const enablePush = page.locator('button[aria-label="Activar notificaciones"]');
  await assertUniqueNativeAx(page, {
    role: "button",
    name: "Activar notificaciones",
    selector: 'button[aria-label="Activar notificaciones"]',
  });
  const bounds = await enablePush.boundingBox();
  if (!bounds || bounds.width <= 0 || bounds.height <= 0) throw new Error("push_control_bounds_missing");
  const screenshot = reportOutput.replace(/\.json$/i, ".push-settings.png");
  const controlScreenshot = reportOutput.replace(/\.json$/i, ".push-settings-control.png");
  await page.screenshot({ path: screenshot, fullPage: true });
  await enablePush.screenshot({ path: controlScreenshot });
  const hitTest = await enablePush.evaluate(button => {
    const rect = button.getBoundingClientRect();
    const x = rect.left + rect.width / 2;
    const y = rect.top + rect.height / 2;
    const describe = element => element ? {
      tag: element.tagName,
      id: element.id || null,
      ariaLabel: element.getAttribute?.("aria-label") ?? null,
      className: typeof element.className === "string" ? element.className : null,
    } : null;
    const parentChain = [];
    for (let element = button; element; element = element.parentElement) parentChain.push(describe(element));
    const shadow = button.getRootNode();
    const style = getComputedStyle(button);
    return {
      nativeRect: { left: rect.left, top: rect.top, width: rect.width, height: rect.height, right: rect.right, bottom: rect.bottom },
      viewport: { width: innerWidth, height: innerHeight, devicePixelRatio },
      center: { x, y },
      documentElementFromPoint: describe(document.elementFromPoint(x, y)),
      shadowElementFromPoint: shadow instanceof ShadowRoot ? describe(shadow.elementFromPoint(x, y)) : null,
      computed: { pointerEvents: style.pointerEvents, zIndex: style.zIndex, position: style.position },
      parentChain,
      state: { disabled: button.disabled, ariaDisabled: button.getAttribute("aria-disabled"), ariaCurrent: button.getAttribute("aria-current"), ariaLabel: button.getAttribute("aria-label") },
    };
  });
  report.pushConsentEvidence = { stage: "pointer_event", screenshot, controlScreenshot, bounds, hitTest };
  if (hitTest.computed.pointerEvents !== "auto" || hitTest.documentElementFromPoint?.id !== "quata-root" ||
      hitTest.shadowElementFromPoint?.tag !== "BUTTON" || hitTest.state.disabled || hitTest.state.ariaDisabled !== "false") {
    throw new Error(`push_control_hit_test_invalid:${JSON.stringify(hitTest)}`);
  }
  await enablePush.evaluate(button => {
    globalThis.__quataPushClickProbe = [];
    button.addEventListener("click", () => {
      globalThis.__quataPushClickProbe.push({
        active: globalThis.navigator?.userActivation?.isActive === true,
      });
    });
  });
  await page.mouse.click(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
  report.pushConsentEvidence.stage = "pointer_exact_once";
  await waitFor(async () => (await page.evaluate(() => globalThis.__quataPushClickProbe?.length)) === 1, "push_pointer_callback_missing");
  await page.waitForTimeout(250);
  let clicks = await page.evaluate(() => globalThis.__quataPushClickProbe);
  let permissions = await page.evaluate(() => globalThis.__quataPushPermissionProbe);
  if (clicks?.length !== 1 || permissions?.length !== 1 || permissions[0]?.active !== true) {
    throw new Error(`push_pointer_callback_not_exactly_once:${JSON.stringify({ clicks, permissions: permissions ?? null })}`);
  }
  await enablePush.focus();
  if (!(await enablePush.evaluate(button => button.getRootNode().activeElement === button))) {
    throw new Error("push_control_focus_missing");
  }
  report.pushConsentEvidence.stage = "Enter";
  await page.keyboard.press("Enter");
  await waitFor(async () => (await page.evaluate(() => globalThis.__quataPushClickProbe?.length)) === 2, "push_enter_callback_missing");
  report.pushConsentEvidence.stage = "Space";
  await page.keyboard.press("Space");
  await waitFor(async () => (await page.evaluate(() => globalThis.__quataPushClickProbe?.length)) === 3, "push_space_callback_missing");
  await page.waitForTimeout(250);
  clicks = await page.evaluate(() => globalThis.__quataPushClickProbe);
  permissions = await page.evaluate(() => globalThis.__quataPushPermissionProbe);
  report.pushConsentEvidence.stage = "permission";
  if (clicks?.length !== 3 || clicks.some(click => click.active !== true) || permissions?.length !== 3 || permissions.some(probe => probe.active !== true)) {
    const status = await page.evaluate(() => localStorage.getItem("web.push.subscription_status"));
    throw new Error(`push_keyboard_callback_not_exactly_once_or_not_trusted:${JSON.stringify({ clicks, permissions: permissions ?? null, status })}`);
  }
  const consent = await page.evaluate(() => localStorage.getItem("web.push.consent.v1"));
  if (consent !== "disabled") throw new Error(`push_consent_denied_state_unexpected:${consent ?? "null"}`);
}


async function runAuthenticatedNavigationStress(page, diagnostics) {
  const results = [];
  for (const sequence of PRIMARY_NAVIGATION_STRESS_SEQUENCES) {
    const diagnosticsAtStart = diagnostics.length;
    for (let cycle = 1; cycle <= NAVIGATION_STRESS_CYCLES; cycle += 1) {
      if (sequence.name === "browser_back_forward") {
        // Build one bounded history chain before the high-volume route stress. Reusing it
        // keeps the browser's same-document history limit from turning a product assertion
        // into a test-runner artifact after hundreds of hash navigations.
        if (cycle === 1) {
          for (const [index, fragment] of sequence.fragments.entries()) {
            await seedStressHistoryFragment(page, fragment, index === 0 ? "replaceState" : "pushState");
          }
        }
        for (let index = 1; index < sequence.fragments.length; index += 1) {
          const expected = expectedRouteForFragment(sequence.fragments.at(-1 - index));
          await navigateHistory(page, "back", index, expected);
        }
        for (let index = 1; index < sequence.fragments.length; index += 1) {
          const expected = expectedRouteForFragment(sequence.fragments[index]);
          await navigateHistory(page, "forward", index, expected);
        }
      } else if (sequence.name === "direct_fragments") {
        for (const fragment of sequence.fragments) {
          await navigateStressFragment(page, fragment);
        }
      } else {
        for (const fragment of sequence.fragments) await navigateStressFragment(page, fragment);
      }
      assertHealthyAuthenticatedShell(diagnostics, diagnosticsAtStart);
    }
    results.push({ name: sequence.name, cycles: NAVIGATION_STRESS_CYCLES, status: "passed" });
  }
  const pageErrors = diagnostics.filter(entry => entry.startsWith("pageerror:"));
  const knownFixtureConsoleErrors = diagnostics.filter(entry => entry.includes("/realtime/v1/websocket") && entry.includes("Unexpected response code: 404"));
  const unexpectedConsoleErrors = diagnostics.filter(entry => entry.startsWith("console:error:") && !knownFixtureConsoleErrors.includes(entry));
  if (pageErrors.length || unexpectedConsoleErrors.length) throw new Error("navigation_stress_console_exception");
  return { status: "passed", sequences: results, knownFixtureConsoleErrors: knownFixtureConsoleErrors.length, unexpectedConsoleErrors: unexpectedConsoleErrors.length, uncaughtExceptions: pageErrors.length };
}

async function seedStressHistoryFragment(page, fragment, method) {
  const expected = expectedRouteForFragment(fragment);
  await page.evaluate(({ value, historyMethod }) => {
    const oldURL = globalThis.location.href;
    const nextURL = value ? `#${value}` : `${globalThis.location.pathname}${globalThis.location.search}`;
    globalThis.history[historyMethod](globalThis.history.state, "", nextURL);
    globalThis.dispatchEvent(new HashChangeEvent("hashchange", { oldURL, newURL: globalThis.location.href }));
  }, { value: fragment, historyMethod: method });
  await waitForShellRoute(page, expected);
}

async function navigateHistory(page, direction, index, expected) {
  const before = await page.evaluate(() => ({ hash: location.hash, route: localStorage.getItem("web.navigation.route") }));
  await page.evaluate(historyDirection => globalThis.history[historyDirection](), direction);
  try { await waitForShellRoute(page, expected); }
  catch (error) {
    navigationStressFailure = { direction, index, expected, before, after: await page.evaluate(() => ({ hash: location.hash, route: localStorage.getItem("web.navigation.route"), shellRoute: document.documentElement.getAttribute("data-quata-shell-route"), selected: document.documentElement.getAttribute("data-quata-primary-selected-route") })), error: error.message };
    throw error;
  }
}

async function navigateStressFragment(page, fragment) {
  const trace = { requested: fragment, expected: expectedRouteForFragment(fragment), before: await page.evaluate(() => ({ hash: location.hash, route: localStorage.getItem("web.navigation.route") })) };
  await page.evaluate(value => { globalThis.location.hash = value; }, fragment);
  try {
    await waitForShellRoute(page, expectedRouteForFragment(fragment));
  } catch (error) {
    navigationStressFailure = { ...trace, after: await page.evaluate(() => ({ hash: location.hash, route: localStorage.getItem("web.navigation.route"), shellRoute: document.documentElement.getAttribute("data-quata-shell-route"), selected: document.documentElement.getAttribute("data-quata-primary-selected-route") })), error: error.message };
    throw error;
  }
}

function expectedRouteForFragment(fragment) {
  return fragment === "" ? "feed" : fragment === "communities" ? "communities" : fragment;
}

async function waitForShellRoute(page, expectedRoute) {
  await page.waitForFunction(route => {
    const root = document.querySelector("#quata-root");
    const expectedPrimary = route === "communities" ? "neighborhoods" : route === "chat" ? "conversations" : route;
    const isPrimaryRoute = ["feed", "official", "communities", "chat", "profile"].includes(route);
    const shellChildren = root?.shadowRoot?.childElementCount ?? root?.childElementCount ?? 0;
    return localStorage.getItem("web.navigation.route") === route &&
      document.documentElement.getAttribute("data-quata-shell-route") === route &&
      (isPrimaryRoute
        ? document.documentElement.getAttribute("data-quata-primary-selected-route") === expectedPrimary
        : !document.documentElement.hasAttribute("data-quata-primary-selected-route")) &&
      root && shellChildren > 0;
  }, expectedRoute);
}

function assertHealthyAuthenticatedShell(diagnostics, diagnosticsAtStart) {
  const newDiagnostics = diagnostics.slice(diagnosticsAtStart);
  if (newDiagnostics.some(entry => entry.startsWith("pageerror:") || entry.includes("IndexOutOfBoundsException"))) {
    throw new Error("navigation_stress_console_exception");
  }
}

async function captureShellScreenshot(page, reportOutput) {
  const output = reportOutput.replace(/\.json$/i, ".png");
  await page.screenshot({ path: output, fullPage: true });
  return output;
}

function observeProductRead(request, url, _backend, session, evidence, stage) {
  const method = request.method().toUpperCase();
  const authorization = request.headers().authorization ?? "";
  if (!authorization.startsWith("Bearer ")) return;
  const parsed = new URL(url);
  if (method === "POST" && parsed.pathname === "/rest/v1/rpc/quata_chat_get_inbox") {
    evidence.notificationInboxReads += 1;
    evidence.notificationInboxReadStages.push(stage);
    return;
  }
  if (method !== "GET") return;
  evidence.authenticatedGets += 1;
  if (parsed.pathname !== "/rest/v1/community_profiles" || !session?.profileId) return;
  const idFilter = parsed.searchParams.get("id") ?? "";
  if (idFilter.includes(session.profileId)) evidence.profileSelfReads += 1;
}

function assertNoBlockedBackendMutations(blocked) {
  if (blocked.length > 0) throw new Error(blocked[0].reason);
}

function safeBackendPath(url, backend) {
  try {
    const parsed = new URL(url);
    return url.startsWith(`${backend.replace(/\/+$/, "")}/`) ? parsed.pathname : "unexpected-origin";
  } catch {
    return "invalid-url";
  }
}

async function resolveAuthSurface(page) {
  const surface = await page.evaluate(() => {
    const root = document.querySelector("#quata-root");
    const app = root?.shadowRoot;
    const nativeControls = app?.querySelectorAll('input[aria-label], button[aria-label]').length ?? 0;
    const rootRect = root?.getBoundingClientRect();
    const canvases = [...(root?.querySelectorAll("canvas") ?? []), ...(app?.querySelectorAll("canvas") ?? [])]
      .map(canvas => canvas.getBoundingClientRect());
    return {
      bridgeVersion: globalThis.__quataAuthE2eProduct?.version,
      root: rootRect ? { width: rootRect.width, height: rootRect.height } : null,
      nativeControls,
      canvasMounted: canvases.some(rect => rect.width > 0 && rect.height > 0),
    };
  });
  if (surface?.bridgeVersion !== 1) throw new Error("compose_auth_bridge_missing");
  if (!surface.root || surface.root.width <= 0 || surface.root.height <= 0) throw new Error("compose_auth_shell_missing");
  if (surface.nativeControls > 0) return "native_controls";
  if (!surface.canvasMounted) throw new Error("compose_auth_canvas_missing");
  return "compose_canvas";
}

async function loginWithNativeControls(page, credentials) {
  const phone = page.locator('input[aria-label="Teléfono"]');
  const password = page.locator('input[aria-label="Contraseña"]');
  const login = page.locator('button[aria-label="Entrar"]');
  await Promise.all([phone.waitFor(), password.waitFor(), login.waitFor()]);
  await assertUniqueNativeAx(page, { role: "textbox", name: "Teléfono", selector: 'input[aria-label="Teléfono"]' });
  await assertUniqueNativeAx(page, { role: "textbox", name: "Contraseña", selector: 'input[aria-label="Contraseña"]' });
  await assertUniqueNativeAx(page, { role: "button", name: "Entrar", selector: 'button[aria-label="Entrar"]' });
  await phone.fill(credentials.phone);
  await password.fill(credentials.password);
  await waitFor(async () => await login.isEnabled(), "native_login_submit_disabled");
  await password.focus();
  await page.keyboard.press("Tab");
  if (!(await login.evaluate(node => node.getRootNode().activeElement === node))) throw new Error("native_login_focus_missing");
  await assertUniqueNativeAx(page, { role: "button", name: "Entrar", selector: 'button[aria-label="Entrar"]', focused: true });
  await page.keyboard.press("Enter");
}

async function loginWithComposeAuthBridge(page, credentials) {
  const result = await page.evaluate(async ({ countryCode, phone, password }) => {
    const bridge = globalThis.__quataAuthE2eProduct;
    if (bridge?.version !== 1 || typeof bridge.login !== "function") throw new Error("compose_auth_bridge_login_missing");
    return await bridge.login(countryCode, phone, password);
  }, credentials);
  if (result !== "authenticated") throw new Error("compose_auth_bridge_login_unexpected_result");
}

async function logoutWithNativeControls(page) {
  const logout = page.locator('button[aria-label="Cerrar sesión"]');
  await assertUniqueNativeAx(page, { role: "button", name: "Cerrar sesión", selector: 'button[aria-label="Cerrar sesión"]' });
  await logout.focus();
  if (!(await logout.evaluate(node => node.getRootNode().activeElement === node))) throw new Error("native_logout_focus_missing");
  await assertUniqueNativeAx(page, { role: "button", name: "Cerrar sesión", selector: 'button[aria-label="Cerrar sesión"]', focused: true });
  await page.keyboard.press("Space");
}

async function logoutWithComposeAuthBridge(page) {
  const result = await page.evaluate(async () => {
    const bridge = globalThis.__quataAuthE2eProduct;
    if (bridge?.version !== 1 || typeof bridge.logout !== "function") throw new Error("compose_auth_bridge_logout_missing");
    return await bridge.logout();
  });
  if (result !== "logged_out") throw new Error("compose_auth_bridge_logout_unexpected_result");
}

async function readSession(page) {
  return page.evaluate(() => ({
    accessToken: localStorage.getItem("quata_web_access_token"),
    refreshToken: localStorage.getItem("quata_web_refresh_token"),
    webSessionToken: localStorage.getItem("quata_web_session_token"),
    profileId: localStorage.getItem("quata_web_user_id"),
  }));
}
async function assertUniqueNativeAx(page, { role, name, selector, focused = false }) {
  const locator = page.locator(selector);
  if (await locator.count() !== 1) throw new Error(`native_ax_selector_not_unique_${role}`);
  const box = await locator.boundingBox();
  if (!box || box.width <= 0 || box.height <= 0) throw new Error(`native_ax_not_visible_${role}`);
  const client = await page.context().newCDPSession(page);
  try {
    const { nodes } = await client.send("Accessibility.getFullAXTree");
    const matches = nodes.filter(node => !node.ignored && node.role?.value === role && node.name?.value === name);
    if (matches.length !== 1) throw new Error(`native_ax_role_name_not_unique_${role}`);
    if (focused && !matches[0].properties?.some(property => property.name === "focused" && property.value?.value === true)) {
      throw new Error(`native_ax_focus_missing_${role}`);
    }
  } finally {
    await client.detach();
  }
}
async function waitFor(predicate, failureCode, timeoutMs = 5_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await predicate()) return;
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error(failureCode);
}
function assertCompleteSession(session) {
  if (!session?.accessToken || !session.refreshToken || !session.webSessionToken ||
      !/^[0-9a-f-]{36}$/i.test(session.profileId ?? "")) throw new Error("product_session_incomplete");
}

function sessionFromLoginPayload(body) {
  try {
    const payload = JSON.parse(body.toString("utf8"));
    const session = {
      accessToken: payload?.session?.access_token,
      refreshToken: payload?.session?.refresh_token,
      webSessionToken: payload?.web_session?.token,
      profileId: payload?.profile?.id,
    };
    assertCompleteSession(session);
    return session;
  } catch {
    return null;
  }
}
async function revokeAndVerify(baseUrl, key, session) {
  const headers = { apikey: key, authorization: `Bearer ${session.accessToken}`, "content-type": "application/json" };
  const logout = await fetch(`${baseUrl}/auth/v1/logout?scope=global`, {
    method: "POST", headers, signal: AbortSignal.timeout(20_000),
  });
  if (!logout.ok) throw new Error("global_session_revocation_failed");
  const verification = await fetch(`${baseUrl}/auth/v1/token?grant_type=refresh_token`, {
    method: "POST",
    headers: { apikey: key, "content-type": "application/json" },
    body: JSON.stringify({ refresh_token: session.refreshToken }),
    signal: AbortSignal.timeout(20_000),
  });
  const verificationBody = await verification.text();
  assertExplicitRefreshTokenRejection(verification.status, verificationBody);
}
async function jsonBody(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  try { return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}"); } catch { return {}; }
}
function json(response, status, value) {
  const body = value == null ? "" : JSON.stringify(value);
  response.writeHead(status, { "content-type": "application/json", "cache-control": "no-store" }).end(body);
}
function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"],
    [".webp", "image/webp"], [".png", "image/png"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}
function escapeHtml(value) {
  return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}
function safeOrigin(url) {
  try { return new URL(url).origin; } catch { return "invalid-origin"; }
}
function safeError(error) {
  const value = typeof error?.message === "string" ? error.message : "";
  return [
    "invalid_arguments", "distribution_missing", "repository_revision_unavailable",
    "repository_revision_invalid", "distribution_revision_missing_or_invalid",
    "distribution_revision_mismatch", "distribution_source_tree_dirty",
    "real_mode_session_revocation_opt_in_required", "real_mode_bridge_mutation_opt_in_required",
    "real_mode_dedicated_account_required", "real_mode_preprovisioned_auth_user_required",
    "real_mode_privileged_environment_forbidden", "real_mode_environment_missing",
    "invalid_public_supabase_url", "privileged_or_invalid_publishable_key",
    "product_session_incomplete", "product_profile_get_not_observed",
    "authenticated_product_get_not_observed", "product_logout_storage_remains",
    "native_login_submit_disabled", "native_login_focus_missing", "native_logout_focus_missing",
    "compose_auth_bridge_missing", "compose_auth_shell_missing", "compose_auth_canvas_missing",
    "compose_auth_bridge_login_missing", "compose_auth_bridge_login_unexpected_result",
    "compose_auth_bridge_logout_missing", "compose_auth_bridge_logout_unexpected_result",
    "native_ax_selector_not_unique", "native_ax_not_visible", "native_ax_role_name_not_unique", "native_ax_focus_missing",
    "read_only_route_pageerror", "private_reload_redirected_to_auth", "backend_mutation_blocked",
    "fixture_journey_incomplete", "unexpected_external_network", "global_session_revocation_failed",
    "global_session_revocation_unverified",
  ].find(code => value.startsWith(code)) ?? "browser_auth_e2e_failure";
}
async function writeSafeReport(path, value) {
  const target = resolve(path);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(value, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`Authenticated browser report written: ${target}`);
}
