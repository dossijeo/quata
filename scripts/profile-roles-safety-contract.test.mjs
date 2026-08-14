import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const commonHost = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt", import.meta.url), "utf8");
const detailsContent = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileDetailsContent.kt", import.meta.url), "utf8");
const rolesContent = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileRoleControlsContent.kt", import.meta.url), "utf8");
const moderationActions = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileModerationActions.kt", import.meta.url), "utf8");
const moderationConfirmation = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileModerationConfirmation.kt", import.meta.url), "utf8");
const fixtures = await readFile(new URL("./e2e-fixtures/chat-attachments.mjs", import.meta.url), "utf8");
const webRunner = await readFile(new URL("./chat-actions-notifications-web-evidence.mjs", import.meta.url), "utf8");
const androidRunner = await readFile(new URL("./chat-actions-notifications-android-evidence.mjs", import.meta.url), "utf8");
const androidUiTest = await readFile(new URL("../app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt", import.meta.url), "utf8");
const iosRunner = await readFile(new URL("./chat-actions-notifications-ios-evidence.mjs", import.meta.url), "utf8");
const iosWrapper = await readFile(new URL("./run-ios-chat-actions-notifications-ui-test.sh", import.meta.url), "utf8");
const iosUiTest = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("PROF-ROLES/SAFETY exposes common semantic anchors", () => {
  for (const expected of [
    'PublicProfileModerationRootTestTagPrefix = "public-profile.safety."',
    'PublicProfileModerationReportTestTagPrefix = "public-profile.safety.report."',
    'PublicProfileModerationBlockTestTagPrefix = "public-profile.safety.block."',
    'PublicProfileModerationUnblockTestTagPrefix = "public-profile.safety.unblock."',
    'PublicProfileRolesRootTestTagPrefix = "public-profile.roles."',
    'PublicProfileRolesAdminTestTagPrefix = "public-profile.roles.admin."',
    'PublicProfileRolesOfficialTestTagPrefix = "public-profile.roles.official."',
    'PublicProfileModerationDialogConfirmTestTagPrefix = "public-profile.safety.dialog.confirm."',
  ]) {
    assert.match(commonHost, new RegExp(expected.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
  assert.match(detailsContent, /PublicProfileDetailsTestTag = "public-profile\.details"/);
  assert.match(detailsContent, /testTag\(PublicProfileDetailsTestTag\)/);
  assert.match(commonHost, /ProfileModerationActions\(\s*userId = profile\.user\.id,/);
  assert.match(rolesContent, /PublicProfileRolesRootTestTagPrefix \+ user\.id/);
  assert.match(rolesContent, /PublicProfileRolesAdminTestTagPrefix \+ user\.id/);
  assert.match(rolesContent, /PublicProfileRolesOfficialTestTagPrefix \+ user\.id/);
  assert.match(rolesContent, /contentDescription = tag/);
  assert.match(moderationActions, /userId: String/);
  assert.match(moderationActions, /PublicProfileModerationReportTestTagPrefix \+ userId/);
  assert.match(moderationActions, /PublicProfileModerationBlockTestTagPrefix \+ userId/);
  assert.match(moderationActions, /PublicProfileModerationUnblockTestTagPrefix \+ userId/);
  assert.match(moderationConfirmation, /PublicProfileModerationDialogTestTagPrefix \+ actionTag/);
  assert.match(moderationConfirmation, /PublicProfileModerationDialogConfirmTestTagPrefix \+ actionTag/);
  assert.match(moderationConfirmation, /PublicProfileModerationDialogCancelTestTag/);
});

test("PROF-ROLES/SAFETY fixture snapshots and restores every mutated backend surface", () => {
  assert.match(fixtures, /export async function prepareProfileRolesSafetyFixture/);
  assert.match(fixtures, /select id, is_admin, is_official[\s\S]*from public\.community_profiles[\s\S]*for update/);
  assert.match(fixtures, /update public\.community_profiles set is_admin = true where id = \$1::uuid/);
  assert.match(fixtures, /update public\.community_profiles set is_admin = false, is_official = false where id = \$1::uuid/);
  assert.match(fixtures, /from public\.chat_profile_blocks[\s\S]*thread_id is null/);
  assert.match(fixtures, /from public\.ugc_reports[\s\S]*target_type = 'profile'/);
  assert.match(fixtures, /export async function pollProfileRoles/);
  assert.match(fixtures, /export async function pollProfileGlobalBlock/);
  assert.match(fixtures, /export async function pollProfileReport/);
  assert.match(fixtures, /export async function cleanupProfileRolesSafetyFixture/);
  assert.match(fixtures, /target_id = \$3\) as report_count/);
  assert.match(fixtures, /\[fixture\.actorProfileId, fixture\.targetProfileId, fixture\.targetProfileId\]/);
  assert.match(fixtures, /cleanup_verified_profile_roles_safety_restored/);
  assert.match(fixtures, /cleanup_residue_detected:profile_roles_safety/);
  assert.doesNotMatch(fixtures, /680242607|680242608|21085800|SERVICE_ROLE\s*=/);
});

test("PROF-ROLES/SAFETY evidence runners are opt-in, semantic-first and fail-closed", () => {
  for (const runner of [webRunner, androidRunner, iosRunner]) {
    assert.match(runner, /profile-roles-safety-only/);
    assert.match(runner, /prepareProfileRolesSafetyFixture/);
    assert.match(runner, /pollProfileRoles/);
    assert.match(runner, /pollProfileReport/);
    assert.match(runner, /pollProfileGlobalBlock/);
    assert.match(runner, /cleanupProfileRolesSafetyFixture/);
    assert.match(runner, /profile_roles_safety_roles_report_and_block_verified_by_db/);
    assert.match(runner, /cleanup_verified_profile_roles_safety_restored/);
    assert.doesNotMatch(runner, /680242607|680242608|21085800|SERVICE_ROLE\s*=/);
  }
  assert.match(androidRunner, /profileRolesSafetyOnly/);
  assert.match(androidRunner, /"profile-roles-safety"/);
  assert.match(androidUiTest, /"profile-roles-safety" -> runProfileRolesSafetyStage/);
  assert.match(androidUiTest, /public-profile\.roles\.official\.\$profileId/);
  assert.match(androidUiTest, /public-profile\.safety\.dialog\.confirm\.report/);
  assert.match(androidUiTest, /public-profile\.safety\.dialog\.confirm\.block/);
  assert.match(webRunner, /clickProfileAnchorOrText/);
  assert.match(webRunner, /public-profile\.roles\.official\.\$\{profileId\}/);
  assert.match(webRunner, /public-profile\.safety\.dialog\.confirm\.report/);
  assert.match(webRunner, /public-profile\.safety\.dialog\.confirm\.block/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E/);
  assert.match(iosWrapper, /testProfileRolesAndSafetyFromChatUseSharedPublicProfileControls/);
  assert.match(iosUiTest, /testProfileRolesAndSafetyFromChatUseSharedPublicProfileControls/);
  assert.match(iosUiTest, /public-profile\.roles\.official\.\\\(peerProfileId\)/);
  assert.match(iosUiTest, /public-profile\.safety\.dialog\.confirm\.report/);
  assert.match(iosUiTest, /public-profile\.safety\.dialog\.confirm\.block/);
});

test("PROF-ROLES/SAFETY contract is part of local fast contract suites", () => {
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/profile-roles-safety-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/profile-roles-safety-contract\.test\.mjs/);
});
