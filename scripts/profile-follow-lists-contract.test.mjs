import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

const [profileHost, profileList, userRow, androidTest, androidRunner, webRunner, iosTest, iosWrapper, iosRunner] = await Promise.all([
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileUsersListCommon.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodUserRowContent.kt"),
  source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  source("scripts/chat-actions-notifications-android-evidence.mjs"),
  source("scripts/chat-actions-notifications-web-evidence.mjs"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  source("scripts/chat-actions-notifications-ios-evidence.mjs"),
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
  assert.match(androidRunner, /runInstrumentationStage\("profile-lists"\)/);
  assert.match(androidRunner, /profile_lists_only_completed/);
});

test("Web profile list evidence opens both common lists and returns to Chat", () => {
  assert.match(webRunner, /--profile-lists-only/);
  assert.match(webRunner, /openPeerProfileFromMessageWithoutReturn/);
  assert.match(webRunner, /assertProfileFollowLists/);
  assert.match(webRunner, /web-chat-profile-list-\$\{listKind\}/);
  assert.match(webRunner, /peer_public_profile_followers_and_following_lists_opened_and_returned/);
  assert.match(webRunner, /ProfileListsOnlyCompleted/);
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
