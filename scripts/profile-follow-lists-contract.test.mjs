import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

const [
  profileHost,
  profileList,
  userRow,
  androidTest,
  androidRunner,
  webRunner,
  webChatHost,
  iosTest,
  iosWrapper,
  iosRunner,
  keysetLoader,
  keysetLoaderTest,
  androidApi,
  androidHttpClient,
  androidEmissionGateTest,
  androidRepository,
  webRepository,
  iosRepository,
] = await Promise.all([
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileUsersListCommon.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodUserRowContent.kt"),
  source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  source("scripts/chat-actions-notifications-android-evidence.mjs"),
  source("scripts/chat-actions-notifications-web-evidence.mjs"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  source("scripts/chat-actions-notifications-ios-evidence.mjs"),
  source("core/src/commonMain/kotlin/com/quata/core/data/KeysetPageLoader.kt"),
  source("core/src/commonTest/kotlin/com/quata/core/data/KeysetPageLoaderTest.kt"),
  source("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt"),
  source("app/src/main/java/com/quata/data/supabase/SupabaseHttpClient.kt"),
  source("app/src/test/java/com/quata/data/supabase/CachedResponseEmissionGateTest.kt"),
  source("app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt"),
  source("feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt"),
]);

test("public profile follower and following lists expose stable common evidence anchors", () => {
  for (const tag of [
    "public-profile.list.",
    "public-profile.list.back.",
    "public-profile.list.row.",
    "public-profile.list.avatar.",
    "public-profile.list.name.",
    "public-profile.list.follow.",
    "public-profile.list.chat.",
  ]) {
    assert.match(profileList, new RegExp(tag.replaceAll(".", "\\.")));
  }

  assert.match(profileHost, /ProfileUserList\(val testTagSuffix: String\)/);
  assert.match(profileHost, /Followers\("followers"\)/);
  assert.match(profileHost, /Following\("following"\)/);
  assert.match(profileHost, /listKind = selectedList\.testTagSuffix/);
  assert.match(profileList, /val rowKey = "\$listKind\.\$\{user\.id\}"/);
  assert.match(userRow, /modifier: Modifier = Modifier/);
  assert.match(userRow, /nameModifier: Modifier = Modifier/);
  assert.match(userRow, /followModifier: Modifier = Modifier/);
  assert.match(userRow, /chatModifier: Modifier = Modifier/);
});

test("Android profile list evidence runs as an isolated profile stage", () => {
  assert.match(androidTest, /"profile-lists" -> runProfileListsStage/);
  assert.match(androidTest, /Public profile \$listKind list must expose at least one visible test-profile row/);
  assert.match(androidTest, /waitForObject\(By\.textContains\("Gabriel"\), "public profile \$listKind row"/);
  assert.match(androidTest, /android-chat-profile-list-\$listKind/);
  assert.match(androidRunner, /process\.argv\.includes\("--profile-lists-only"\)/);
  assert.match(androidRunner, /profileListsOnly \? "profile-lists"/);
  assert.match(androidRunner, /runInstrumentationStage\(profileStage\)/);
  assert.match(androidRunner, /profile_follow_list_edges_prepared_reversibly/);
  assert.match(androidRunner, /profile_lists_only_completed/);
});

test("Web profile list evidence opens both common lists and returns to Chat", () => {
  assert.match(webRunner, /--profile-lists-only/);
  assert.match(webRunner, /openPeerProfileFromMessageWithoutReturn/);
  assert.match(webRunner, /assertProfileFollowLists/);
  assert.match(webRunner, /web-chat-profile-list-\$\{listKind\}/);
  assert.match(webRunner, /peer_public_profile_followers_and_following_lists_opened_and_returned/);
  assert.match(webRunner, /ProfileListsOnlyCompleted/);
  assert.match(webChatHost, /blurWebChatActiveElement\(\)/);
  assert.match(webChatHost, /document\?\.activeElement/);
  assert.match(webChatHost, /if \(openingProfileUserId == null\)/);
  assert.match(webChatHost, /Box\(modifier\.height\(62\.dp\)\)/);
});

test("iOS profile list evidence selects the opt-in follow-list XCTest", () => {
  assert.match(iosTest, /testProfileFollowListsFromChatOpenAndReturn/);
  assert.match(iosTest, /"public-profile\.list\.\\\(listKind\)"/);
  assert.match(iosTest, /"public-profile\.list\.row\.\\\(listKind\)\."/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_PROFILE_LISTS_UI_E2E/);
  assert.match(iosWrapper, /testProfileFollowListsFromChatOpenAndReturn/);
  assert.match(iosRunner, /--profile-lists-only/);
  assert.match(iosRunner, /profileListsOnly/);
  assert.match(iosRunner, /ios_xctest_profile_followers_and_following_lists_verified/);
});

test("common keyset loader exhausts long lists and rejects ambiguous cursors", () => {
  assert.match(keysetLoader, /while \(true\)/);
  assert.match(keysetLoader, /if \(page\.size < pageSize\) return result/);
  assert.match(keysetLoader, /keyset_page_not_strictly_ordered/);
  assert.match(keysetLoaderTest, /1_205/);
  assert.match(keysetLoaderTest, /listOf\(null, "0500", "1000"\)/);
  assert.match(keysetLoaderTest, /exactPageBoundaryRequestsAnEmptyTerminalPage/);
  assert.match(keysetLoaderTest, /rejectsAnUnorderedOrRepeatedCursorInsteadOfLoopingOrDroppingRows/);
});

test("Android exhausts follow edges and batches related profiles", () => {
  assert.match(androidApi, /suspend fun getAllProfileFollows/);
  assert.match(androidApi, /loadCompleteKeyset\(/);
  assert.match(androidApi, /"id" to afterIdExclusive\?\.let \{ "gt\.\$it" \}/);
  assert.match(androidApi, /"order" to "id\.asc"/);
  assert.match(androidApi, /PROFILE_FOLLOW_PAGE_SIZE = 500/);
  assert.match(androidApi, /PROFILE_ID_BATCH_SIZE = 100/);
  assert.match(androidApi, /suspend fun getProfilesBatched/);
  assert.match(androidApi, /fun observeProfilesBatched/);
  assert.match(androidApi, /afterIdExclusive = afterExclusive,\s+cacheMode = SupabaseCacheMode\.NETWORK_ONLY,/);
  assert.match(androidApi, /observeProfileFollows[\s\S]*emitUnchangedAfterInvalidation = true,/);
  assert.match(androidHttpClient, /emissionGate\.markInvalidated\(emitUnchangedAfterInvalidation\)/);
  assert.match(androidEmissionGateTest, /unchangedFirstThousandRowsReemitAfterOffWindowInvalidation/);
  assert.match(androidEmissionGateTest, /assertTrue\(gate\.accepts\(firstThousandRows\)\)/);
  assert.match(androidRepository, /supabaseApi\.getAllProfileFollows\(followedProfileId = userId/);
  assert.match(androidRepository, /supabaseApi\.getAllProfileFollows\(followerProfileId = userId/);
  assert.match(androidRepository, /supabaseApi\.getProfilesBatched\(relatedIds/);
  assert.match(androidRepository, /supabaseApi\.observeProfilesBatched\(relatedIds/);
});

for (const [platform, repository, followType] of [
  ["Web", webRepository, "WebCommunityFollow"],
  ["iOS", iosRepository, "IosCommunityFollow"],
]) {
  test(`${platform} exhausts follow edges and batches related profiles`, () => {
    assert.match(repository, /loadCompleteKeyset\(/);
    assert.match(repository, /ProfileFollowPageSize = 500/);
    assert.match(repository, /ProfileIdBatchSize = 100/);
    assert.match(repository, /chunked\(ProfileIdBatchSize\)/);
    assert.match(repository, /put\("id", "gt\.\$\{.*require.*Identifier\(\)\}"\)/);
    assert.match(repository, /put\("order", "id\.asc"\)/);
    assert.match(repository, new RegExp(`data class ${followType}\\(val id: String, val followerId: String, val followedId: String\\)`));
  });
}
