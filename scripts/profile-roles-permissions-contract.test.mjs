import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { assertProfileRoleMutationDenied } from "./e2e-fixtures/chat-attachments.mjs";

const read = async (path) => await readFile(new URL(path, import.meta.url), "utf8");
const commonHost = await read("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt");
const androidRepository = await read("../app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt");
const webRepository = await read("../web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt");
const iosRepository = await read("../feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt");
const actorGuard = await read("../supabase/migrations/20260726171003_community_profiles_actor_guard.sql");
const actorGuardRollback = await read("../supabase/rollbacks/20260726171003_community_profiles_actor_guard.rollback.sql");
const v32PublishableCompatibility = await read("../supabase/migrations/20260924153500_android_v32_publishable_key_compatibility.sql");
const v32PublishableCompatibilityRollback = await read("../supabase/rollbacks/20260924153500_android_v32_publishable_key_compatibility.rollback.sql");
const v32GatewayCompatibility = await read("../supabase/migrations/20260924154500_android_v32_gateway_header_compatibility.sql");
const v32GatewayCompatibilityRollback = await read("../supabase/rollbacks/20260924154500_android_v32_gateway_header_compatibility.rollback.sql");
const selectiveExecutor = await read("./selective-db-release-executor.mjs");
const androidHttpClient = await read("../app/src/main/java/com/quata/data/supabase/SupabaseHttpClient.kt");
const fixtures = await read("./e2e-fixtures/chat-attachments.mjs");
const webRunner = await read("./chat-actions-notifications-web-evidence.mjs");
const androidRunner = await read("./chat-actions-notifications-android-evidence.mjs");
const androidUiTest = await read("../app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");
const iosRunner = await read("./chat-actions-notifications-ios-evidence.mjs");
const iosWrapper = await read("./run-ios-chat-actions-notifications-ui-test.sh");
const iosUiTest = await read("../iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift");
const packageJson = JSON.parse(await read("../package.json"));

test("PROF-ROLES hides role controls from non-admin actors", () => {
  assert.match(commonHost, /if \(currentUserIsAdmin && !isOwnProfile && onSetUserRoles != null\)/);
  assert.match(androidUiTest, /"profile-roles-permissions" -> runProfileRolesPermissionsStage/);
  assert.match(androidUiTest, /public-profile\.roles\.official\.\$profileId[\s\S]*Role control \$tag must remain absent for a non-admin actor/);
  assert.match(webRunner, /verifyProfileRolesPermissionsFromOpenProfile/);
  assert.match(webRunner, /profile_role_controls_visible_to_non_admin/);
  assert.match(webRunner, /await scrollProfileAdministrationIntoView\(page\)/);
  assert.match(webRunner, /label\.includes\(identifier\)/);
  assert.match(webRunner, /Administraci\[oó\]n\|Administration\|Administrador\|Administrateur\|Administrator/);
  assert.match(iosUiTest, /rolesSafetyMode == "permissions"/);
  assert.match(iosUiTest, /Role control \\\(identifier\) must remain absent for a non-admin actor/);
});

test("PROF-ROLES adapters and database reject unauthorized role mutation", () => {
  assert.match(androidRepository, /check\(currentProfile\?\.is_admin == true\)/);
  assert.match(webRepository, /check\(isCurrentUserAdmin\(\)\) \{ "web_community_admin_required" \}/);
  assert.match(iosRepository, /check\(isCurrentUserAdmin\(\)\) \{ "ios_communities_admin_required" \}/);
  assert.match(actorGuard, /create policy "authenticated update profiles"[\s\S]*public\.quata_current_profile_is_admin\(\)/);
  assert.match(actorGuard, /create or replace function public\.quata_guard_profile_roles\(\)/);
  assert.match(actorGuard, /if not v_actor_is_admin then[\s\S]*Only administrators can change official roles/);
  assert.match(actorGuard, /using errcode = '42501'/);
  assert.match(actorGuard, /create trigger quata_guard_profile_roles_trg/);
});

test("published Android v32 recovery is isolated behind an observable kill switch", () => {
  assert.match(androidHttpClient, /x-quata-client-generation", "android-auth-boundary-v1"/);
  assert.match(actorGuard, /create table if not exists public\.quata_legacy_android_v32_compatibility/);
  assert.match(actorGuard, /context\.jwt_role = 'anon'/);
  assert.match(actorGuard, /context\.method in \('PATCH', 'POST'\)/);
  assert.match(actorGuard, /context\.path = '\/community_profiles'/);
  assert.match(actorGuard, /user-agent[\s\S]*okhttp\/4\.12\.0/);
  assert.match(actorGuard, /x-quata-client-generation'[\s\S]*is null/);
  assert.match(actorGuard, /to anon[\s\S]*using \(public\.quata_legacy_android_v32_request_allowed\(\)\)/);
  assert.match(actorGuard, /grant update \(pass_hash, pass_plain\)[\s\S]*to anon/);
  assert.match(actorGuard, /create policy "public insert profiles"[\s\S]*to anon[\s\S]*quata_legacy_android_v32_request_allowed\(\)/);
  assert.match(actorGuard, /to_jsonb\(new\) - array\['pass_hash', 'pass_plain'\]/);
  assert.match(actorGuard, /sha256\(convert_to\(new\.pass_plain, 'UTF8'\)\)/);
  assert.match(actorGuard, /request_count = request_count \+ 1/);
  assert.match(actorGuardRollback, /drop policy if exists "legacy android v32 password reset"/);
  assert.match(actorGuardRollback, /drop table if exists public\.quata_legacy_android_v32_compatibility/);
  assert.match(selectiveExecutor, /20260726171003", "0914caece0c6d65e39b64c21645cac5992ec492d68d16cf0bb2186cec766c627"/);
  assert.match(selectiveExecutor, /20260924153500", "5b4bb6c652085ed25e4423a25e6ab2f44b97f8821f50b46282a1e7d919af4b6c"/);
  assert.match(selectiveExecutor, /20260924154500", "cc615971b7f19316a293cf5fbc27742c775c585514fcc1610da6d50f42b4510b"/);
});

test("PROF-ROLES permissions fixture is reversible and proves unchanged roles", () => {
  assert.match(fixtures, /actorIsAdmin = true/);
  assert.match(fixtures, /\[actorSession\.profileId, actorIsAdmin\]/);
  assert.match(fixtures, /preparedTargetRoles: \{ isAdmin: false, isOfficial: false \}/);
  assert.match(fixtures, /export async function assertProfileRoleMutationDenied/);
  assert.match(fixtures, /profile_roles_permissions_actor_binding_mismatch/);
  assert.match(fixtures, /profile_roles_permissions_actor_still_admin/);
  assert.match(fixtures, /method: "PATCH"/);
  assert.match(fixtures, /tokenRole !== "authenticated"/);
  assert.match(fixtures, /tokenExpiresAt <= Math\.floor\(Date\.now\(\) \/ 1_000\)/);
  assert.match(fixtures, /profile_roles_permissions_actor_session_rejected/);
  assert.match(fixtures, /response\.status === 403 && responseBody\?\.code === "42501"/);
  assert.match(fixtures, /response\.status === 401\) throw new Error\("profile_roles_permissions_actor_session_rejected_during_mutation:http_401"\)/);
  assert.match(fixtures, /responseBody\.length === 0/);
  assert.match(fixtures, /profile_roles_permissions_backend_mutated/);
  assert.match(fixtures, /targetRolesUnchanged: true/);
  assert.match(fixtures, /actorBindingVerified: true/);
  assert.match(fixtures, /actorSessionVerified: true/);
  assert.match(fixtures, /export async function cleanupProfileRolesSafetyFixture/);
});

test("PROF-ROLES permissions focal mode is wired on every platform", () => {
  for (const runner of [webRunner, androidRunner, iosRunner]) {
    assert.match(runner, /profile-roles-permissions-only/);
    assert.match(runner, /actorIsAdmin: !(?:options\.)?profileRolesPermissionsOnly/);
    assert.match(runner, /assertProfileRoleMutationDenied/);
    assert.match(runner, /profile_roles_controls_absent_backend_denied_and_roles_unchanged/);
  }
  assert.match(androidRunner, /"profile-roles-permissions"/);
  assert.match(androidRunner, /error\?\.message === "profile_roles_permissions_only_completed"/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E=\$\{profileRolesPermissionsOnly \? "permissions"/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E" == "permissions"/);
  assert.match(iosUiTest, /ios-chat-profile-roles-permissions-denied/);
});

test("PROF-ROLES never treats an authentication failure as an RLS denial", async () => {
  const actorProfileId = "11111111-1111-4111-8111-111111111111";
  const targetProfileId = "22222222-2222-4222-8222-222222222222";
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const accessToken = `${encode({ alg: "none" })}.${encode({
    sub: actorProfileId,
    role: "authenticated",
    exp: Math.floor(Date.now() / 1_000) + 3_600,
  })}.fixture`;
  let fetchCall = 0;
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    fetchCall += 1;
    if (fetchCall === 1) {
      return new Response(JSON.stringify([{ id: actorProfileId }]), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    }
    return new Response(JSON.stringify({ code: "PGRST301", message: "JWT expired" }), {
      status: 401,
      headers: { "content-type": "application/json" },
    });
  };
  const withDatabase = async (operation) => await operation({
    query: async () => ({ rowCount: 1, rows: [{ id: actorProfileId, is_admin: false }] }),
  });
  try {
    await assert.rejects(
      assertProfileRoleMutationDenied({
        baseUrl: "https://fixture.supabase.co",
        publicKey: "fixture-public-key",
        actorSession: { accessToken },
        fixture: {
          prepared: true,
          actorProfileId,
          targetProfileId,
          preparedTargetRoles: { isAdmin: false, isOfficial: false },
        },
        withDatabase,
      }),
      /profile_roles_permissions_actor_session_rejected_during_mutation:http_401/,
    );
    assert.equal(fetchCall, 2);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("PROF-ROLES permissions contract is part of local fast contract suites", () => {
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/profile-roles-permissions-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/profile-roles-permissions-contract\.test\.mjs/);
});

test("published Android v32 accepts the embedded Supabase publishable key only within the legacy signature", () => {
  assert.match(v32PublishableCompatibility, /jwt_role is null[\s\S]*apikey[\s\S]*like 'sb_publishable_%'/);
  assert.match(v32PublishableCompatibility, /user-agent[\s\S]*okhttp\/4\.12\.0/);
  assert.match(v32PublishableCompatibility, /x-quata-client-generation[\s\S]*is null/);
  assert.match(v32PublishableCompatibilityRollback, /context\.jwt_role = 'anon'/);
  assert.doesNotMatch(v32PublishableCompatibilityRollback, /sb_publishable_%/);
});
test("published Android v32 tolerates Supabase gateway credential-header stripping", () => {
  assert.match(v32GatewayCompatibility, /context\.jwt_role = 'anon'/);
  assert.match(v32GatewayCompatibility, /user-agent[\s\S]*okhttp\/4\.12\.0/);
  assert.doesNotMatch(v32GatewayCompatibility, /headers ->> 'apikey'/);
  assert.doesNotMatch(v32GatewayCompatibility, /headers ->> 'authorization'/);
  assert.match(v32GatewayCompatibilityRollback, /headers ->> 'apikey'/);
  assert.match(v32GatewayCompatibilityRollback, /headers ->> 'authorization'/);
});