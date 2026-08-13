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
const recoveryEvidence = await readFile(new URL("./web-auth-recovery-evidence.mjs", import.meta.url), "utf8");
const realRecoveryEvidence = await readFile(new URL("./auth-recovery-real-bridge-evidence.mjs", import.meta.url), "utf8");

test("hermetic Auth gate keeps the Compose product surface and uses only the localhost product bridge", () => {
  assert.match(runner, /chromium\.launch\(/);
  assert.match(runner, /input\[aria-label="Teléfono"\]/);
  assert.doesNotMatch(runner, /__quataAuthE2eProduct\.restore\(\)/);
  assert.match(runner, /resolveAuthSurface\(page\)/);
  assert.match(runner, /await resolveAuthSurface\(page\);/);
  assert.match(runner, /loginWithComposeAuthBridge\(page, credentials\)/);
  assert.match(runner, /logoutWithComposeAuthBridge\(page\)/);
  assert.match(runner, /compose_auth_shell_missing/);
  assert.match(runner, /compose_auth_canvas_missing/);
  assert.match(runner, /compose_auth_bridge_missing/);
  assert.match(runner, /bridge\.login\(countryCode, phone, password\)/);
  assert.match(runner, /bridge\.logout\(\)/);
  assert.match(bridge, /openRecovery: \(\) => openRecovery\(\)/);
  assert.match(bridge, /recoveryQuestion: \(countryCode, phone\)/);
  assert.match(bridge, /resetPassword: \(countryCode, phone, secretAnswer, newPassword\)/);
  assert.match(runner, /button\[aria-label="Cerrar sesión"\]/);
  assert.match(main, /authRepository\.login\(countryCode, phone, password\)/);
  assert.match(main, /preferences\.putString\(WebSessionReadyKey, "true"\)/);
  assert.match(main, /authRepository\.restoreLocalSession\(\)/);
  assert.match(main, /sessionCoordinator\.logoutCurrentSession\(\)/);
  assert.doesNotMatch(bridge, /innerHTML|createElement\(['"]input|addEventListener\(['"]click/);
});

test("the hermetic browser journey proves the permanent public shell, participation gate and common private-route login return", () => {
  assert.match(runner, /const PRIVATE_RETURN_FRAGMENT = "chat-sb%3Ateam%2F42\?message=msg%209"/);
  assert.match(runner, /await assertPrivateAuthenticationGate\(page\)/);
  assert.match(runner, /await invokeAuthGateAction\(page, "dismiss"\)/);
  assert.match(runner, /await invokeAuthGateAction\(page, "chooseRegister"\)/);
  assert.match(runner, /await assertFullScreenAuthDestination\(page, "register"\)/);
  assert.match(runner, /visibleText: "Crea tu cuenta"/);
  assert.match(runner, /await assertRegisterLegalDocumentViewer\(page\)/);
  assert.match(runner, /register_shared_legal_documents_opened_from_local_assets/);
  assert.match(runner, /clickAndCaptureDocumentViewer\(page, \/privacidad\|Privacy policy\/i, "privacy_es\.docx", 0\)/);
  assert.match(runner, /clickAndCaptureDocumentViewer\(page, \/Seguridad infantil\|Seguridad de menores\|Child safety\/i, "child_safety_es\.docx", 1\)/);
  assert.match(runner, /__quataDocumentOpenEvidence/);
  assert.match(runner, /data-quata-docmentis-viewer/);
  assert.match(runner, /viewer: "docmentis-overlay"/);
  assert.match(runner, /renderReady/);
  assert.match(runner, /async function dismissDocumentViewer\(page\)/);
  assert.match(runner, /document_viewer_close_failed/);
  assert.match(runner, /getByRole\("button", \{ name: pattern \}\)/);
  assert.match(runner, /findVisibleTextBounds\(page, pattern\)/);
  assert.match(runner, /element\.innerText \|\| element\.textContent/);
  assert.match(runner, /\[scope, \.\.\.scope\.querySelectorAll\("\*"\)\]\.filter\(Boolean\)/);
  assert.match(runner, /matches\.sort\(\(left, right\) => left\.area - right\.area\)/);
  assert.match(runner, /legalFallbackBounds\(page, fallbackIndex\)/);
  assert.match(runner, /rect\.width >= 720/);
  assert.match(runner, /await invokeAuthGateAction\(page, "chooseLogin"\)/);
  assert.match(runner, /await assertFullScreenAuthDestination\(page, "login"\)/);
  assert.match(runner, /data-quata-auth-required-prompt/);
  assert.match(runner, /data-quata-auth-pending-route/);
  assert.match(runner, /data-quata-auth-destination/);
  assert.match(runner, /auth_router_bootstrap_ready_before_private_transition/);
  assert.match(runner, /localStorage\.getItem\("web\.navigation\.route"\) === "feed"/);
  assert.match(runner, /await assertAutomaticLoginReturn\(page\)/);
  assert.match(runner, /await assertAnonymousPublicShellAfterLogout\(page\)/);
  assert.match(runner, /anonymous_feed_neighborhoods_official_notifications_shell_and_private_chat_participation_gate/);
  assert.match(runner, /\{ fragment: "communities", route: "communities" \}/);
  assert.match(runner, /\{ fragment: "notifications", route: "notifications" \}/);
  assert.match(runner, /product_logout_returns_to_anonymous_feed_and_official_shell/);
  assert.match(runner, /data-quata-shell-route/);
  assert.match(runner, /location\.hash === ""/);
  assert.match(runner, /location\.hash === `#\$\{fragment\}`/);
  assert.doesNotMatch(runner, /page\.goto\([^\n]*#feed/);
  assert.match(main, /internal val WebNavigationState\.isPublicRoute/);
  assert.match(main, /internal val WebNavigationState\.requiresAuthentication/);
  assert.match(main, /pendingAuthenticationFragment/);
  assert.match(main, /onAuthRequired = ::requestAuthenticationForCurrentRoute/);
  assert.match(main, /clearWebNavigationShellMarker\(\)/);
  assert.match(main, /fun completeLogin\(\)[\s\S]*?navigation\.navigate\(pendingAuthenticationFragment \?: ""\)/);
  assert.match(main, /fun completeLogout[\s\S]*?navigation\.navigate\(""\)/);
  assert.match(main, /onLoginSuccess = ::completeLogin/);
  assert.match(main, /var isSessionResolved by remember/);
  assert.match(main, /!isSessionResolved && navigationState\.requiresAuthentication -> \{/);
  assert.match(main, /isSessionResolved = true/);
  assert.match(runner, /private_reload_redirected_to_auth/);
});

test("authenticated Settings proves web-push consent starts from its native trusted control", () => {
  assert.match(runner, /stage = "authenticated_settings_push_consent"/);
  assert.match(runner, /await assertAuthenticatedSettingsPushConsent\(page, options\.output\)/);
  assert.match(runner, /authenticated_settings_push_consent_uses_trusted_native_click/);
  assert.match(runner, /globalThis\.location\.hash = "settings"/);
  assert.match(runner, /button\[aria-label="Activar notificaciones"\]/);
  assert.match(runner, /await enablePush\.focus\(\)/);
  assert.match(runner, /push_control_focus_missing/);
  assert.match(runner, /await page\.keyboard\.press\("Space"\)/);
  assert.match(runner, /await page\.mouse\.click\(/);
  assert.match(runner, /documentElementFromPoint/);
  assert.match(runner, /shadowElementFromPoint/);
  assert.match(runner, /push_pointer_callback_not_exactly_once/);
  assert.match(runner, /push_keyboard_callback_not_exactly_once_or_not_trusted/);
  assert.match(runner, /navigator\?\.userActivation\?\.isActive === true/);
  assert.match(runner, /push_consent_denied_state_unexpected/);
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
  assert.match(runner, /MAX_AUTHENTICATED_INBOX_READS = NAVIGATION_STRESS_CYCLES \* 16/);
  assert.match(runner, /\{ name: "browser_back_forward"[\s\S]*?\{ name: "primary_forward"/);
  assert.match(runner, /if \(cycle === 1\) \{\s+for \(const \[index, fragment\] of sequence\.fragments\.entries\(\)\)/);
  assert.match(runner, /globalThis\.history\[historyMethod\]\(globalThis\.history\.state, "", nextURL\)/);
  assert.match(runner, /globalThis\.dispatchEvent\(new HashChangeEvent\("hashchange"/);
  assert.match(runner, /globalThis\.history\[historyDirection\]\(\), direction/);
  assert.doesNotMatch(runner, /page\.goBack\(\)|page\.goForward\(\)/);
  assert.match(main, /var hasEvaluatedWhatsNewStartup by remember \{ mutableStateOf\(false\) \}/);
  assert.match(main, /!hasEvaluatedWhatsNewStartup[\s\S]*?hasEvaluatedWhatsNewStartup = true[\s\S]*?navigationState\.route != "feed"/);
  assert.doesNotMatch(main, /LaunchedEffect\([^\n]*navigationState\.route[^\n]*whatsNewInstalledVersionCode/);
  assert.match(runner, /authenticated_inbox_read_storm/);
  assert.match(runner, /notificationInboxReads: productReadEvidence\.notificationInboxReads/);
  assert.match(runner, /notificationInboxReadStages: productReadEvidence\.notificationInboxReadStages/);
  assert.doesNotMatch(runner, /chatExcluded/);
  assert.match(runner, /product_profile_authenticated_get_observed/);
  assert.match(runner, /READ_ONLY_ROUTE_MATRIX/);
  assert.doesNotMatch(runner, /quata-chat-e2e|__quataChatE2eProduct|native_chat_controls/);
  assert.match(runner, /page\.keyboard\.press\("Enter"\)/);
  assert.deepEqual(
    READ_ONLY_ROUTE_MATRIX.map(route => route.route),
    ["feed", "profile", "settings", "communities", "official", "whats-new", "about", "release-history"],
  );
  assert.ok(READ_ONLY_ROUTE_MATRIX.every(route => Object.keys(route).sort().join(",") === "fragment,route"));
  assert.match(runner, /globalThis\.location\.hash = fragment/);
  assert.match(main, /val notificationCountFlow = remember\(notificationsRepository, shouldObserveNotifications\)/);
  assert.match(main, /notificationCountFlow\.collectAsState\(initial = 0\)/);
});

test("WhatsNew routes use the source-controlled local catalog and stay inside the strict read-only matrix", () => {
  assert.deepEqual(READ_ONLY_ROUTE_EXCLUSIONS, []);
  const routedFragments = new Set(READ_ONLY_ROUTE_MATRIX.flatMap(route => [route.fragment, route.route]));
  for (const fragment of ["whats-new", "about", "release-history"]) assert.equal(routedFragments.has(fragment), true);
  assert.match(whatsNewHost, /QuataLocalWhatsNewCatalog\.webReleases\(\)/);
  assert.doesNotMatch(whatsNewHost, /rpcClient|quata_android_release_history/);
  assert.match(documentation, /Novedades e Historial de versiones usan el cat[aá]logo local compartido/);
  assert.match(documentation, /`POST \/rest\/v1\/rpc\/quata_chat_search_conversation_candidates`/);
  assert.match(documentation, /cuerpo cerrado con actor UUID/);
  assert.match(runner, /excludedRoutes: READ_ONLY_ROUTE_EXCLUSIONS\.flatMap/);
});

test("the final report rechecks mutations and snapshots read-only evidence immediately before passing", () => {
  assert.match(
    runner,
    /await page\.waitForTimeout\(100\);\n  assertNoBlockedBackendMutations\(blockedBackendMutations\);\n  report\.readOnlyEvidence = \{[\s\S]*?blockedMutations: blockedBackendMutations\.length,[\s\S]*?\n  \};\n  report\.status = "passed";/,
  );
});

test("the focal Web recovery runner exercises the real repository bridge without backend secrets", () => {
  assert.match(recoveryEvidence, /WEB-AUTH-RECOVERY-001/);
  assert.match(recoveryEvidence, /globalThis\.__quataAuthE2eProduct\.openRecovery\(\)/);
  assert.match(recoveryEvidence, /globalThis\.__quataAuthE2eProduct\.recoveryQuestion/);
  assert.match(recoveryEvidence, /globalThis\.__quataAuthE2eProduct\.resetPassword/);
  assert.match(recoveryEvidence, /TURNSTILE_BOOTSTRAP/);
  assert.match(recoveryEvidence, /globalThis\.turnstile=\{\};/);
  assert.match(recoveryEvidence, /unexpected_external_network/);
  assert.match(recoveryEvidence, /fixture_recovery_journey_incomplete/);
  assert.doesNotMatch(recoveryEvidence, /SUPABASE_DB_URL|service_role|migration repair|supabase db push/);
});

test("real Auth recovery evidence is opt-in, reversible and excludes privileged Supabase material", () => {
  assert.match(realRecoveryEvidence, /AUTH-RECOVERY-REAL-BRIDGE-001/);
  assert.match(realRecoveryEvidence, /I_ACCEPT_PASSWORD_RESET_ROUNDTRIP/);
  assert.match(realRecoveryEvidence, /I_ACCEPT_AUTHORIZED_RECOVERY_ACCOUNT_MUTATION/);
  assert.match(realRecoveryEvidence, /I_ACCEPT_DB_RECOVERY_SECRET_ROUNDTRIP/);
  assert.match(realRecoveryEvidence, /update_recovery_secret/);
  assert.match(realRecoveryEvidence, /reset_password/);
  assert.match(realRecoveryEvidence, /original_password_restored/);
  assert.match(realRecoveryEvidence, /restored_password_login_succeeded/);
  assert.match(realRecoveryEvidence, /passwordRestored: false/);
  assert.match(realRecoveryEvidence, /passwordRestored = true/);
  assert.match(realRecoveryEvidence, /recoverySecretRestored/);
  assert.match(realRecoveryEvidence, /hasColumn\(client, "community_profiles", "secret_answer_hash"\)/);
  assert.match(realRecoveryEvidence, /select \$\{selectColumns\}[\s\S]*where phone_local = \$1/);
  assert.match(realRecoveryEvidence, /where id = \$4/);
  assert.match(realRecoveryEvidence, /where id = \$3/);
  assert.match(realRecoveryEvidence, /privileged_environment_forbidden/);
  assert.doesNotMatch(realRecoveryEvidence, /service_role|migration repair|supabase db push|admin\/users|deleteUser/);
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

test("browser policy allows only declared read RPCs and Auth lifecycle effects", () => {
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
  const candidateBody = {
    p_actor_profile_id: "00000000-0000-4000-8000-000000000001",
    p_query: "fixture",
    p_limit: 30,
    p_offset: 0,
  };
  const candidateRead = decision({
    url: `${backend}/rest/v1/rpc/quata_chat_search_conversation_candidates`,
    method: "POST",
    body: JSON.stringify(candidateBody),
  });
  assert.equal(candidateRead.allowed, true);
  assert.equal(candidateRead.reason, "declared_chat_candidate_directory_read");
  for (const stage of ["compose_auth_bridge_login", "authenticated_browser_restore", "authenticated_route_matrix"]) {
    assert.equal(decision({
      url: `${backend}/rest/v1/rpc/quata_chat_search_conversation_candidates`,
      method: "POST",
      stage,
      body: JSON.stringify(candidateBody),
    }).allowed, true);
  }
  for (const body of [
    "{}",
    "not-json",
    JSON.stringify({ ...candidateBody, p_limit: 0 }),
    JSON.stringify({ ...candidateBody, p_limit: 51 }),
    JSON.stringify({ ...candidateBody, p_offset: -1 }),
    JSON.stringify({ ...candidateBody, p_actor_profile_id: "not-a-uuid" }),
    JSON.stringify({ ...candidateBody, unexpected: true }),
  ]) {
    assert.equal(decision({
      url: `${backend}/rest/v1/rpc/quata_chat_search_conversation_candidates`,
      method: "POST",
      body,
    }).allowed, false);
  }
  assert.equal(decision({
    url: `${backend}/rest/v1/rpc/quata_chat_search_conversation_candidates_extra`,
    method: "POST",
    body: JSON.stringify(candidateBody),
  }).allowed, false);
  assert.equal(decision({
    url: `${backend}/rest/v1/rpc/quata_chat_search_conversation_candidates`,
    method: "PATCH",
    body: JSON.stringify(candidateBody),
  }).allowed, false);
  assert.equal(decision({
    url: `${backend}/rest/v1/rpc/quata_chat_search_conversation_candidates`,
    method: "POST",
    stage: "undeclared_login_like_stage",
    body: JSON.stringify(candidateBody),
  }).allowed, false);
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

test("native push consent also permits only the exact read-only inbox RPC", () => {
  const backend = "https://project-ref.supabase.co";
  const decide = (path, method = "POST") => backendBrowserRequestDecision({ backend, url: `${backend}${path}`, method, stage: "authenticated_settings_push_consent", body: "{}" });
  assert.equal(decide("/rest/v1/rpc/quata_chat_get_inbox").allowed, true);
  assert.equal(decide("/rest/v1/rpc/quata_chat_send_message").allowed, false);
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
