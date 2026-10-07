import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("shared Profile exposes a separately confirmed global logout action", async () => {
  const [profile, android, web, ios] = await Promise.all([
    source("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt"),
    source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
    source("iosApp/iosApp/QuataIosApp.swift"),
  ]);

  assert.match(profile, /ProfileLogoutEverywhereOpenTestTag = "profile\.management\.logout-everywhere"/);
  assert.match(profile, /confirmation = ProfileDangerousAction\.LogoutEverywhere/);
  assert.match(profile, /ProfileDangerousAction\.LogoutEverywhere -> onLogoutEverywhere\?\.invoke\(\)/);
  assert.match(android, /onLogoutEverywhere = \{[\s\S]*?authRepository\.logoutEverywhere\(\)[\s\S]*?navigateToFeed\(\)/);
  assert.match(web, /onLogoutEverywhere = \{[\s\S]*?completeLogout\(global = true\)/);
  assert.match(ios, /onLogoutEverywhere: \{ \[weak self\] in self\?\.authenticatedHost\.performLogoutEverywhere\(\) \}/);
  assert.match(ios, /logoutHandler\.logoutEverywhere\([\s\S]*?onCompleted: completed,[\s\S]*?onFailure:/);
});

test("all transports keep explicit local scope and route global logout through the retirement endpoint", async () => {
  const [androidConfig, androidRepository, webRepository, iosRepository] = await Promise.all([
    source("app/src/main/java/com/quata/data/supabase/SupabaseConfig.kt"),
    source("app/src/main/java/com/quata/feature/auth/data/AuthRepositoryImpl.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebAuthRepository.kt"),
    source("feature/auth/src/iosMain/kotlin/com/quata/feature/auth/data/IosAuthRepository.kt"),
  ]);

  assert.match(androidConfig, /authLogoutUrl[\s\S]*logout\?scope=local/);
  assert.match(androidConfig, /globalLogoutUrl[\s\S]*quata-auth-global-logout/);
  assert.match(androidRepository, /override suspend fun logout\(\) = logout\(global = false\)/);
  assert.match(androidRepository, /override suspend fun logoutEverywhere\(\) = logout\(global = true\)/);
  assert.match(webRepository, /override suspend fun logout\(\)[\s\S]*logoutWithBrowserUnsubscribe\(global = false\)/);
  assert.match(webRepository, /override suspend fun logoutEverywhere\(\)[\s\S]*logoutWithBrowserUnsubscribe\(global = true\)/);
  assert.match(webRepository, /supabaseLogoutEndpoint\(\)[\s\S]*logout\?scope=local/);
  assert.match(webRepository, /globalLogoutEndpoint\(\)[\s\S]*quata-auth-global-logout/);
  assert.match(iosRepository, /override suspend fun logout\(\) = performLogout\(global = false\)/);
  assert.match(iosRepository, /override suspend fun logoutEverywhere\(\) = performLogout\(global = true\)/);
  assert.match(iosRepository, /supabaseLogoutEndpoint\(\)[\s\S]*logout\?scope=local/);
  assert.match(iosRepository, /globalLogoutEndpoint\(\)[\s\S]*quata-auth-global-logout/);
});

test("Web keeps local server retirement ordered and leaves a failed global action retryable", async () => {
  const web = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebAuthRepository.kt");
  const lifecycle = web.slice(web.indexOf("suspend fun logoutWithBrowserUnsubscribe"), web.indexOf("override suspend fun register"));
  const push = lifecycle.indexOf("notifyServerLogout()");
  const auth = lifecycle.indexOf("notifySupabaseLogout(global)");
  const browser = lifecycle.indexOf("browserUnsubscribe().getOrThrow()");
  const clear = lifecycle.indexOf("WebAuthStorage.clear(preferences)");
  assert.ok(push >= 0 && auth > push && browser > auth && clear > browser);
  assert.match(lifecycle, /val webSessionFailure = if \(global\) null else/);
  assert.match(lifecycle, /if \(global && authFailure != null\) return Result\.failure\(authFailure\)/);
  assert.match(lifecycle, /val failure = webSessionFailure \?: authFailure \?: browserFailure/);
});

test("focal platform tests distinguish local Auth logout from global device retirement", async () => {
  const [androidTest, webTest, iosTest] = await Promise.all([
    source("app/src/test/java/com/quata/data/supabase/SupabaseLogoutScopeTest.kt"),
    source("web/src/wasmJsTest/kotlin/com/quata/web/WebAuthLogoutScopeTest.kt"),
    source("feature/auth/src/iosTest/kotlin/com/quata/feature/auth/data/IosAuthLogoutOrderingTest.kt"),
  ]);
  for (const testSource of [androidTest, webTest, iosTest]) assert.match(testSource, /logout\?scope=local/);
  for (const testSource of [androidTest, webTest, iosTest]) assert.match(testSource, /quata-auth-global-logout/);
  assert.match(webTest, /assertNull\(preferences\.getString\(WebAuthStorage\.AccessToken\)\)/);
  assert.match(iosTest, /recording\.logoutEverywhere\(\)/);
});

test("backend retirement is actor-bound, service-only and precedes global Auth sign-out", async () => {
  const [migration, edge, selectiveRelease, edgeConfig] = await Promise.all([
    source("supabase/migrations/20261007090000_auth_global_logout_device_retirement.sql"),
    source("supabase/functions/quata-auth-global-logout/index.ts"),
    source("scripts/selective-db-release-executor.mjs"),
    source("supabase/config.toml"),
  ]);
  for (const table of ["push_tokens", "web_push_subscriptions", "web_client_sessions"]) {
    assert.match(migration, new RegExp(`update public\\.${table}`));
  }
  assert.match(migration, /revoke all on function public\.quata_retire_all_device_endpoints\(uuid\) from public, anon, authenticated/);
  assert.match(migration, /grant execute on function public\.quata_retire_all_device_endpoints\(uuid\) to service_role/);
  const retire = edge.indexOf('"quata_retire_all_device_endpoints"');
  const signOut = edge.indexOf('admin.auth.admin.signOut(accessToken, "global")');
  assert.ok(retire >= 0 && signOut > retire);
  assert.match(edge, /admin\.auth\.getUser\(accessToken\)/);
  assert.match(edgeConfig, /\[functions\.quata-auth-global-logout\]\s+verify_jwt = true/);
  assert.match(selectiveRelease, /20261007090000[^\n]+f7d3f63da639fafb4162b5057195bb7db65bcf9d8cfbc771270023b5005b8756/);
  assert.match(selectiveRelease, /selective_release_auth_global_logout_security_postcondition_failed/);
  assert.match(selectiveRelease, /selective_release_auth_global_logout_definition_postcondition_failed/);
});

test("Web acceptance drives the product confirmation and proves two-session retirement", async () => {
  const [runner, manifest] = await Promise.all([
    source("scripts/account-postflight-web-evidence.mjs"),
    source("package.json"),
  ]);
  assert.match(manifest, /"evidence:auth-global-logout-web"[^\n]+--global-logout/);
  assert.match(runner, /openLogoutEverywhereConfirmation/);
  assert.match(runner, /waitForPostflightState\(page, "Management", "LogoutEverywhere"\)/);
  assert.match(runner, /confirmLogoutEverywhere/);
  assert.match(runner, /auth\/v1\/token\?grant_type=refresh_token/);
  assert.match(runner, /ownedAuthSessions !== 0/);
  assert.match(runner, /activePushTokens !== 0/);
  assert.match(runner, /activeWebPushSubscriptions !== 0/);
  assert.match(runner, /activeWebSessions !== 0/);
});
