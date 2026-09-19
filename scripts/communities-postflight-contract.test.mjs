import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const directory = await readFile(
  new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodListContent.kt", import.meta.url),
  "utf8",
);
const members = await readFile(
  new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodUsersContent.kt", import.meta.url),
  "utf8",
);
const host = await readFile(
  new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreenHost.kt", import.meta.url),
  "utf8",
);
const viewModelTest = await readFile(
  new URL("../feature/neighborhoods/src/commonTest/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModelTest.kt", import.meta.url),
  "utf8",
);
const webRunner = await readFile(new URL("./chat-actions-notifications-web-evidence.mjs", import.meta.url), "utf8");
const androidRunner = await readFile(new URL("./chat-actions-notifications-android-evidence.mjs", import.meta.url), "utf8");
const androidUiTest = await readFile(
  new URL("../app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt", import.meta.url),
  "utf8",
);
const iosRunner = await readFile(new URL("./chat-actions-notifications-ios-evidence.mjs", import.meta.url), "utf8");
const iosUiTest = await readFile(
  new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift", import.meta.url),
  "utf8",
);

test("SCR-COMMUNITIES exposes common directory and state anchors", () => {
  for (const anchor of [
    "neighborhood.directory.root",
    "neighborhood.directory.search",
    "neighborhood.directory.loading",
    "neighborhood.directory.empty",
    "neighborhood.directory.error",
  ]) {
    assert.match(directory, new RegExp(`const val \\w+ = "${anchor.replaceAll(".", "\\.")}"`));
  }
  for (const symbol of [
    "NeighborhoodDirectoryRootTestTag",
    "NeighborhoodDirectorySearchTestTag",
    "NeighborhoodDirectoryLoadingTestTag",
    "NeighborhoodDirectoryEmptyTestTag",
    "NeighborhoodDirectoryErrorTestTag",
  ]) {
    assert.match(directory, new RegExp(`\\.testTag\\(${symbol}\\)`));
    assert.match(directory, new RegExp(`contentDescription = ${symbol}`));
  }
});

test("SCR-COMMUNITIES exposes common members and return anchors", () => {
  for (const anchor of [
    "neighborhood.members.root",
    "neighborhood.members.back",
    "neighborhood.members.empty",
  ]) {
    assert.match(members, new RegExp(`const val \\w+ = "${anchor.replaceAll(".", "\\.")}"`));
  }
  for (const symbol of [
    "NeighborhoodMembersRootTestTag",
    "NeighborhoodMembersBackTestTag",
    "NeighborhoodMembersEmptyTestTag",
  ]) {
    assert.match(members, new RegExp(`\\.testTag\\(${symbol}\\)`));
    assert.match(members, new RegExp(`contentDescription = ${symbol}`));
  }
  assert.match(host, /onBack = \{ selectedCommunity = null \}/);
  assert.match(host, /onShowUsers = \{ selectedCommunity = it\.name \}/);
});

test("SCR-COMMUNITIES keeps profile and community Chat as existing dependent flows", () => {
  assert.match(host, /onOpenProfile = \{ user -> onOpenUserProfile\(user\.id\) \}/);
  assert.match(host, /viewModel\.openChat\(community\.name, onOpenConversation\)/);
  assert.doesNotMatch(`${directory}\n${members}\n${host}`, /Thread\.sleep|delay\(|fixedCoordinate|SERVICE_ROLE/);
});

test("SCR-COMMUNITIES reuses the existing focal coordinators on every platform", () => {
  assert.match(webRunner, /communities_web_directory_search_members_and_return_verified/);
  assert.match(webRunner, /web-communities-filtered/);
  assert.match(webRunner, /web-communities-members-returned/);
  assert.match(webRunner, /membersTagPattern = new RegExp\(`\^\$\{escapeRegExp\(membersTag\)\}\(\?:\\\\s\|\$\)`\)/);
  assert.match(webRunner, /filteredMembers\.boundingBox\(\)/);
  assert.match(webRunner, /membersBackIcon = await visibleAriaLocator\(page, \[\/\^\(Volver\|Back\)\$\/i\]/);
  assert.match(webRunner, /membersBack = await visibleAriaLocator\(page, \[\/\^neighborhood\\\.members\\\.back/);
  assert.match(webRunner, /membersBackTarget\.boundingBox\(\)/);
  assert.doesNotMatch(webRunner, /filteredMembers\.click\(\)|membersBack\.click\(\)/);
  assert.match(androidRunner, /communities_android_directory_search_members_and_return_verified/);
  assert.match(androidRunner, /android-communities-filtered\.png/);
  assert.match(androidUiTest, /performTextReplacement\(communityName\)/);
  assert.match(androidUiTest, /clickStableTag\("neighborhood\.members\.back"\)/);
  assert.match(iosRunner, /communities_ios_directory_search_members_and_return_verified/);
  assert.match(iosUiTest, /clearAndTypeText\(communityName, into: "neighborhood\.directory\.search"/);
  assert.match(iosUiTest, /tapTaggedButton\("neighborhood\.members\.back"/);
});

test("SCR-COMMUNITIES preserves deterministic load and Chat failure coverage", () => {
  assert.match(viewModelTest, /directory load failure leaves a stable error state/);
  assert.match(viewModelTest, /communitiesFlow = flow \{ throw IllegalStateException\("offline"\) \}/);
  assert.match(viewModelTest, /community chat failure marks only the failed community/);
  assert.match(viewModelTest, /assertEquals\("Bata", model\.uiState\.value\.chatErrorNeighborhood\)/);
});
