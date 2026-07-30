import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  assertExplicitRefreshTokenRejection,
  isPublicSupabaseKey,
} from "./web-authenticated-browser-security.mjs";
import {
  BRIDGE_MUTATION_OPT_IN,
  DEDICATED_ACCOUNT_SCOPE,
  PREPROVISIONED_AUTH_USER,
  READ_ONLY_ROUTE_EXCLUSIONS,
  READ_ONLY_ROUTE_MATRIX,
  REAL_SESSION_OPT_IN,
  assertExactDistributionRevision,
  backendBrowserRequestDecision,
  loadRealAuthConfiguration,
} from "./web-authenticated-browser-policy.mjs";

const runner = await readFile(new URL("./web-authenticated-browser-e2e.mjs", import.meta.url), "utf8");
const wrapper = await readFile(new URL("./run-web-authenticated-browser-e2e.ps1", import.meta.url), "utf8");
const bridge = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebAuthE2eBridge.kt", import.meta.url), "utf8");
const main = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt", import.meta.url), "utf8");
const browserFileCache = await readFile(new URL("../core/src/wasmJsMain/kotlin/com/quata/core/platform/BrowserFileCacheService.wasm.kt", import.meta.url), "utf8");
const workflow = await readFile(new URL("../.github/workflows/web-android-pr.yml", import.meta.url), "utf8");
const webBuild = await readFile(new URL("../web/build.gradle.kts", import.meta.url), "utf8");
const documentation = await readFile(new URL("../docs/WEB_AUTHENTICATED_BROWSER_E2E.md", import.meta.url), "utf8");
const whatsNewHost = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebWhatsNewHost.kt", import.meta.url), "utf8");

test("hermetic Auth gate uses native controls when present or the localhost-only product bridge for a stable Compose canvas", () => {
  assert.match(runner, /chromium\.launch\(/);
  assert.match(runner, /input\[aria-label="Teléfono"\]/);
  assert.match(runner, /__quataAuthE2eProduct\.restore/);
  assert.match(runner, /resolveAuthSurface\(page\)/);
  assert.match(runner, /authSurface === "native_controls"/);
  assert.match(runner, /loginWithComposeAuthBridge\(page, credentials\)/);
  assert.match(runner, /logoutWithComposeAuthBridge\(page\)/);
  assert.match(runner, /compose_auth_shell_missing/);
  assert.match(runner, /compose_auth_canvas_missing/);
  assert.match(runner, /compose_auth_bridge_missing/);
  assert.match(runner, /bridge\.login\(countryCode, phone, password\)/);
  assert.match(runner, /bridge\.logout\(\)/);
  assert.match(runner, /button\[aria-label="Cerrar sesión"\]/);
  assert.match(main, /authRepository\.login\(countryCode, phone, password\)/);
  assert.match(main, /preferences\.putString\(WebSessionReadyKey, "true"\)/);
  assert.match(main, /authRepository\.restoreLocalSession\(\)/);
  assert.match(main, /sessionCoordinator\.logoutCurrentSession\(\)/);
  assert.doesNotMatch(bridge, /innerHTML|createElement\(['"]input|addEventListener\(['"]click/);
});

test("Wasm file-cache interop expressions remain valid object-property expressions", () => {
  for (const operation of ["store", "get", "remove"]) {
    assert.doesNotMatch(
      browserFileCache,
      new RegExp(`web_file_cache_${operation}_failed'\\)\\);`),
    );
  }
});

test("fixture fails closed on external network while proving the notification inbox read", () => {
  assert.match(runner, /context\.route\("\*\*\/\*"/);
  assert.match(runner, /proxy-server=http:\/\/127\.0\.0\.1:9/);
  assert.match(runner, /unexpected_external_network/);
  assert.match(runner, /fixtureState\.login !== 1/);
  assert.match(runner, /fixtureState\.webLogout !== 1/);
  assert.match(runner, /fixtureState\.globalLogout !== 1/);
  assert.match(runner, /fixtureState\.notificationInboxReads < 1/);
  assert.match(runner, /notificationInboxReads: productReadEvidence\.notificationInboxReads/);
  assert.match(runner, /notificationInboxReadStages: productReadEvidence\.notificationInboxReadStages/);
  assert.doesNotMatch(runner, /chatExcluded/);
  assert.match(runner, /product_profile_authenticated_get_observed/);
  assert.match(runner, /READ_ONLY_ROUTE_MATRIX/);
  assert.doesNotMatch(runner, /quata-chat-e2e|__quataChatE2eProduct|native_chat_controls/);
  assert.match(runner, /page\.keyboard\.press\("Enter"\)/);
  assert.deepEqual(
    READ_ONLY_ROUTE_MATRIX.map(route => route.route),
    ["feed", "profile", "settings", "communities", "official"],
  );
  assert.ok(READ_ONLY_ROUTE_MATRIX.every(route => Object.keys(route).sort().join(",") === "fragment,route"));
  assert.match(runner, /globalThis\.location\.hash = fragment/);
});

test("WhatsNew RPC POST remains explicitly outside the strict GET-only route matrix", () => {
  assert.deepEqual(READ_ONLY_ROUTE_EXCLUSIONS, [{
    fragments: ["whats-new", "about"],
    method: "POST",
    path: "/rest/v1/rpc/quata_android_release_history",
    reason: "postgrest_rpc_post_not_get_only",
  }]);
  const routedFragments = new Set(READ_ONLY_ROUTE_MATRIX.flatMap(route => [route.fragment, route.route]));
  for (const fragment of ["whats-new", "about"]) assert.equal(routedFragments.has(fragment), false);
  assert.match(
    whatsNewHost,
    /override suspend fun getReleaseHistory[\s\S]*?releases\("quata_android_release_history"[\s\S]*?private suspend fun releases[\s\S]*?rpcClient\.post\(function,/,
  );
  assert.match(documentation, /Novedades usa un RPC de lectura transportado como\s+`POST`/);
  assert.match(documentation, /exclusivamente `POST \/rest\/v1\/rpc\/quata_chat_get_inbox`/);
  assert.match(runner, /excludedRoutes: READ_ONLY_ROUTE_EXCLUSIONS\.flatMap/);
});

test("the final report rechecks mutations and snapshots read-only evidence immediately before passing", () => {
  assert.match(
    runner,
    /await page\.waitForTimeout\(100\);\n  assertNoBlockedBackendMutations\(blockedBackendMutations\);\n  report\.readOnlyEvidence = \{[\s\S]*?blockedMutations: blockedBackendMutations\.length,[\s\S]*?\n  \};\n  report\.status = "passed";/,
  );
});

test("real mode requires a dedicated preprovisioned account and accepts bridge effects explicitly", () => {
  assert.match(wrapper, /\[switch\]\$AllowExistingTestUser/);
  assert.match(wrapper, /\[switch\]\$AcceptSessionRevocation/);
  assert.match(wrapper, /\[switch\]\$AcceptBridgeIdentityAndSessionMutations/);
  assert.match(wrapper, /\[switch\]\$ConfirmDedicatedWebAccount/);
  assert.match(wrapper, /\[switch\]\$ConfirmPreprovisionedAuthUser/);
  assert.match(wrapper, /QUATA_AUTH_E2E_REAL_OPT_IN/);
  assert.match(runner, /loadRealAuthConfiguration/);
  assert.match(runner, /route\.fetch\(\)/);
  assert.match(runner, /cleanupSession = captured/);
  assert.match(runner, /grant_type=refresh_token/);
  assert.match(runner, /global_session_revocation_unverified/);
  assert.doesNotMatch(runner, /quata-register|admin\/users|account-lifecycle|createUser|deleteUser/);
  assert.match(documentation, /puede crear el usuario de Supabase Auth/i);
  assert.match(documentation, /last_login_at/);
});

test("real preflight rejects missing scope, privileged environment and non-public configuration", () => {
  const valid = {
    QUATA_AUTH_E2E_REAL_OPT_IN: REAL_SESSION_OPT_IN,
    QUATA_AUTH_E2E_BRIDGE_MUTATION_OPT_IN: BRIDGE_MUTATION_OPT_IN,
    QUATA_E2E_ACCOUNT_SCOPE: DEDICATED_ACCOUNT_SCOPE,
    QUATA_E2E_AUTH_USER_PREPROVISIONED: PREPROVISIONED_AUTH_USER,
    QUATA_SUPABASE_URL: "https://project-ref.supabase.co",
    QUATA_SUPABASE_PUBLISHABLE_KEY: "sb_publishable_public-test-key",
    QUATA_E2E_COUNTRY_CODE: "240",
    QUATA_E2E_PHONE: "600000001",
    QUATA_E2E_PASSWORD: "not-logged",
  };
  assert.equal(loadRealAuthConfiguration(valid).baseUrl, valid.QUATA_SUPABASE_URL);
  assert.throws(
    () => loadRealAuthConfiguration({ ...valid, QUATA_E2E_ACCOUNT_SCOPE: "shared-ios-account" }),
    { message: "real_mode_dedicated_account_required" },
  );
  assert.throws(
    () => loadRealAuthConfiguration({ ...valid, QUATA_E2E_AUTH_USER_PREPROVISIONED: "" }),
    { message: "real_mode_preprovisioned_auth_user_required" },
  );
  assert.throws(
    () => loadRealAuthConfiguration({ ...valid, SUPABASE_DB_URL: "postgresql://forbidden" }),
    { message: "real_mode_privileged_environment_forbidden" },
  );
  assert.throws(
    () => loadRealAuthConfiguration({ ...valid, QUATA_SUPABASE_PUBLISHABLE_KEY: "sb_secret_forbidden" }),
    { message: "privileged_or_invalid_publishable_key" },
  );
});

test("browser policy allows only reads, the exact notification inbox RPC, and declared Auth lifecycle effects", () => {
  const backend = "https://project-ref.supabase.co";
  const decision = (overrides = {}) => backendBrowserRequestDecision({
    backend,
    url: `${backend}/rest/v1/community_profiles?select=id`,
    method: "GET",
    stage: "authenticated_route_matrix",
    body: null,
    ...overrides,
  });
  assert.equal(decision().allowed, true);
  for (const method of ["POST", "PUT", "PATCH", "DELETE"]) {
    const blocked = decision({ method });
    assert.equal(blocked.backendApi, true);
    assert.equal(blocked.allowed, false);
    assert.match(blocked.reason, /^backend_mutation_blocked_/);
  }
  const whatsNewRpc = decision({
    method: "POST",
    url: `${backend}/rest/v1/rpc/quata_android_release_history`,
    body: JSON.stringify({ p_track: "production" }),
  });
  assert.equal(whatsNewRpc.allowed, false);
  assert.equal(whatsNewRpc.reason, "backend_mutation_blocked_post");
  const notificationInbox = decision({
    url: `${backend}/rest/v1/rpc/quata_chat_get_inbox`,
    method: "POST",
    body: "{}",
  });
  assert.equal(notificationInbox.allowed, true);
  assert.equal(notificationInbox.reason, "declared_notification_inbox_read");
  for (const path of [
    "/rest/v1/rpc/quata_chat_get_thread",
    "/rest/v1/rpc/quata_chat_send_message",
    "/rest/v1/rpc/quata_chat_get_inbox_extra",
  ]) {
    const blocked = decision({ url: `${backend}${path}`, method: "POST", body: "{}" });
    assert.equal(blocked.allowed, false);
    assert.equal(blocked.reason, "backend_mutation_blocked_post");
  }
  assert.equal(decision({
    url: `${backend}/rest/v1/rpc/quata_chat_get_inbox`,
    method: "POST",
    stage: "native_auth_control_login",
    body: "{}",
  }).allowed, true);
  assert.equal(decision({
    url: `${backend}/rest/v1/rpc/quata_chat_get_inbox`,
    method: "POST",
    stage: "compose_auth_bridge_login",
    body: "{}",
  }).allowed, true);
  const undeclaredInboxStage = decision({
    url: `${backend}/rest/v1/rpc/quata_chat_get_inbox`,
    method: "POST",
    stage: "undeclared_login_like_stage",
    body: "{}",
  });
  assert.equal(undeclaredInboxStage.allowed, false);
  assert.equal(undeclaredInboxStage.reason, "backend_mutation_blocked_post");
  assert.equal(decision({
    url: `${backend}/rest/v1/rpc/quata_chat_get_inbox`,
    method: "POST",
    stage: "native_auth_control_logout",
    body: "{}",
  }).allowed, true);
  assert.equal(decision({
    url: `${backend}/rest/v1/rpc/quata_chat_get_inbox`,
    method: "PATCH",
    body: "{}",
  }).allowed, false);
  assert.equal(decision({
    url: `${backend}/functions/v1/quata-auth-bridge`,
    method: "POST",
    stage: "native_auth_control_login",
    body: JSON.stringify({ action: "web_login" }),
  }).allowed, true);
  assert.equal(decision({
    url: `${backend}/functions/v1/quata-auth-bridge`,
    method: "POST",
    stage: "authenticated_route_matrix",
    body: JSON.stringify({ action: "web_login" }),
  }).allowed, false);
  assert.equal(decision({
    url: `${backend}/functions/v1/quata-web-push`,
    method: "POST",
    stage: "native_auth_control_logout",
    body: JSON.stringify({ action: "logout" }),
  }).allowed, true);
  assert.equal(decision({
    url: `${backend}/functions/v1/quata-auth-bridge`,
    method: "POST",
    stage: "compose_auth_bridge_login",
    body: JSON.stringify({ action: "web_login" }),
  }).allowed, true);
  assert.equal(decision({
    url: `${backend}/rest/v1/rpc/quata_chat_get_inbox`,
    method: "POST",
    stage: "compose_auth_bridge_logout",
    body: "{}",
  }).allowed, true);
});

test("navigation stress permits only the exact read-only inbox RPC", () => {
  const backend = "https://project-ref.supabase.co";
  const decide = (path, method = "POST") => backendBrowserRequestDecision({ backend, url: `${backend}${path}`, method, stage: "authenticated_navigation_stress", body: "{}" });
  assert.equal(decide("/rest/v1/rpc/quata_chat_get_inbox").allowed, true);
  for (const path of ["/rest/v1/rpc/quata_chat_get_inbox_extra", "/rest/v1/rpc/quata_chat_send_message"]) assert.equal(decide(path).allowed, false);
  assert.equal(decide("/rest/v1/rpc/quata_chat_get_inbox", "PATCH").allowed, false);
});

test("distribution gate binds a clean tracked tree to one exact commit", () => {
  const revision = "a".repeat(40);
  assert.equal(assertExactDistributionRevision({
    repositoryRevision: revision,
    markerRevision: revision.toUpperCase(),
    trackedChanges: "",
  }), revision);
  assert.throws(() => assertExactDistributionRevision({
    repositoryRevision: revision,
    markerRevision: "b".repeat(40),
    trackedChanges: "",
  }), { message: "distribution_revision_mismatch" });
  assert.throws(() => assertExactDistributionRevision({
    repositoryRevision: revision,
    markerRevision: revision,
    trackedChanges: " M web/src/wasmJsMain/Main.kt",
  }), { message: "distribution_source_tree_dirty" });
  assert.match(webBuild, /quata-source-revision\.txt/);
  assert.match(webBuild, /wasmJsBrowserDistribution/);
});

test("revocation verification accepts only explicit refresh credential rejection", () => {
  assert.doesNotThrow(() => assertExplicitRefreshTokenRejection(
    400,
    JSON.stringify({
      error: "invalid_grant",
      error_description: "Invalid Refresh Token: Refresh Token Not Found",
    }),
  ));
  assert.doesNotThrow(() => assertExplicitRefreshTokenRejection(
    401,
    JSON.stringify({ error_code: "refresh_token_not_found" }),
  ));
  assert.doesNotThrow(() => assertExplicitRefreshTokenRejection(
    400,
    JSON.stringify({ error: "refresh_token_revoked" }),
  ));

  assert.throws(
    () => assertExplicitRefreshTokenRejection(429, JSON.stringify({ error: "refresh_token_revoked" })),
    { message: "global_session_revocation_verification_transient_or_server_error" },
  );
  assert.throws(
    () => assertExplicitRefreshTokenRejection(503, JSON.stringify({ error: "refresh_token_revoked" })),
    { message: "global_session_revocation_verification_transient_or_server_error" },
  );
  assert.throws(
    () => assertExplicitRefreshTokenRejection(400, JSON.stringify({ error: "unexpected_auth_failure" })),
    { message: "global_session_revocation_verification_inconclusive" },
  );
  assert.throws(
    () => assertExplicitRefreshTokenRejection(401, "upstream proxy returned an unknown response"),
    { message: "global_session_revocation_verification_inconclusive" },
  );
  assert.throws(
    () => assertExplicitRefreshTokenRejection(200, JSON.stringify({ access_token: "still-live" })),
    { message: "global_session_revocation_unverified" },
  );
});

test("publishable key validation executes structural key and JWT role checks", () => {
  const jwt = role => [
    Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url"),
    Buffer.from(JSON.stringify({ role })).toString("base64url"),
    Buffer.from("test-signature").toString("base64url"),
  ].join(".");

  assert.equal(isPublicSupabaseKey("sb_publishable_public-test-key"), true);
  assert.equal(isPublicSupabaseKey("sb_publishable_"), false);
  assert.equal(isPublicSupabaseKey("sb_publishable_not valid"), false);
  assert.equal(isPublicSupabaseKey("sb_secret_server-only-key"), false);
  assert.equal(isPublicSupabaseKey(jwt("anon")), true);
  assert.equal(isPublicSupabaseKey(jwt("service_role")), false);
  assert.equal(isPublicSupabaseKey(jwt("authenticated")), false);
  assert.equal(isPublicSupabaseKey("not-a-publishable-key"), false);
});

test("the product bridge is restricted to localhost and an explicit query opt-in", () => {
  assert.match(bridge, /hostname === '127\.0\.0\.1'/);
  assert.match(bridge, /hostname === 'localhost'/);
  assert.match(bridge, /get\('quata-auth-e2e'\) === '1'/);
  assert.match(bridge, /Object\.freeze/);
});

test("PR CI requires both the contract and the hermetic browser journey", () => {
  assert.match(workflow, /npm run test:web-auth-browser-contract/);
  assert.match(workflow, /node scripts\/web-authenticated-browser-e2e\.mjs/);
  assert.match(workflow, /authenticated-browser-e2e\.json/);
  assert.match(workflow, /build\/reports\/web-ci\//);
});
