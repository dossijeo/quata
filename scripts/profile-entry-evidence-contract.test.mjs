import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { prepareReversibleProfileFollow } from "./e2e-fixtures/reversible-profile-follow.mjs";

const webRunner = await readFile(new URL("./chat-actions-notifications-web-evidence.mjs", import.meta.url), "utf8");
const androidRunner = await readFile(new URL("./chat-actions-notifications-android-evidence.mjs", import.meta.url), "utf8");
const androidUiTest = await readFile(new URL("../app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt", import.meta.url), "utf8");
const iosRunner = await readFile(new URL("./chat-actions-notifications-ios-evidence.mjs", import.meta.url), "utf8");
const iosUiTest = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift", import.meta.url), "utf8");
const feedAnchor = await readFile(new URL("../feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedReelPostContent.kt", import.meta.url), "utf8");
const officialAnchor = await readFile(new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostCardContent.kt", import.meta.url), "utf8");
const officialHost = await readFile(new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt", import.meta.url), "utf8");
const webOfficialHost = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt", import.meta.url), "utf8");
const webMain = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt", import.meta.url), "utf8");
const webBridge = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebProfileEntryE2eBridge.kt", import.meta.url), "utf8");
const webProfileRoute = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebFeedMemberProfileRoute.kt", import.meta.url), "utf8");
const conversationAnchor = await readFile(new URL("../feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationAvatarPresentation.kt", import.meta.url), "utf8");
const conversationList = await readFile(new URL("../feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsListContent.kt", import.meta.url), "utf8");
const conversationsHost = await readFile(new URL("../feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsScreenHost.kt", import.meta.url), "utf8");
const bottomNavigation = await readFile(new URL("../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataBottomNavigation.kt", import.meta.url), "utf8");
const mainActivity = await readFile(new URL("../app/src/main/java/com/quata/MainActivity.kt", import.meta.url), "utf8");
const neighborhoodList = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodListContent.kt", import.meta.url), "utf8");
const neighborhoodUsers = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodUsersContent.kt", import.meta.url), "utf8");
const profileUsersList = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileUsersListCommon.kt", import.meta.url), "utf8");
const profileKpiContent = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileKpiContent.kt", import.meta.url), "utf8");
const communityProfileHost = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt", import.meta.url), "utf8");
const neighborhoodsViewModel = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModel.kt", import.meta.url), "utf8");
const profileLoadState = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileLoadStateContent.kt", import.meta.url), "utf8");
const androidProfileLoadFault = await readFile(new URL("../app/src/main/java/com/quata/feature/neighborhoods/data/ProfileLoadEvidenceFaults.kt", import.meta.url), "utf8");
const androidProfileRepository = await readFile(new URL("../app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt", import.meta.url), "utf8");
const webProfileRepository = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt", import.meta.url), "utf8");
const iosProfilePreloader = await readFile(new URL("../feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/presentation/IosCommunityProfilePreloader.kt", import.meta.url), "utf8");
const iosApp = await readFile(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8");
const iosShellRunner = await readFile(new URL("./run-ios-chat-actions-notifications-ui-test.sh", import.meta.url), "utf8");

test("PROF-ENTRY Web evidence is opt-in, semantic-first and reversible", () => {
  assert.match(webRunner, /--profile-entry-only/);
  assert.match(webRunner, /--community-chat-only/);
  assert.match(webRunner, /prepareProfileEntryFixture/);
  assert.match(webRunner, /verifyProfileEntryWeb/);
  assert.match(webRunner, /resolveCommunityChatTarget/);
  assert.match(webRunner, /verifyCommunityChatWeb/);
  assert.match(webRunner, /community_walls_stats/);
  assert.match(webRunner, /neighborhood\.chat\.\$\{neighborhoodTagSuffix\(target\.name\)\}/);
  assert.match(webRunner, /community_chat_flow_anchor_missing/);
  assert.match(webRunner, /community_chat_web_returned_to_source_communities/);
  assert.match(webRunner, /web-community-chat-returned/);
  assert.match(webRunner, /visibleExactAriaLocator\(page, "chat\.back"/);
  assert.match(webRunner, /\/\^\(Volver\|Back\)\$\/i/);
  assert.match(webRunner, /data-quata-shell-route/);
  assert.match(webRunner, /feed\.author\.avatar\.\$\{profile\.profileId\}/);
  assert.match(webRunner, /official\.author\.avatar\.\$\{profile\.profileId\}/);
  assert.match(webRunner, /neighborhood\.members\.\$\{neighborhoodTagSuffix\(profile\.neighborhood\)\}/);
  assert.match(webRunner, /neighborhood\.user\.avatar\.\$\{profile\.profileId\}/);
  assert.match(webRunner, /conversation\.avatar\.\$\{profile\.profileId\}/);
  assert.match(webRunner, /visibleTextLocator/);
  assert.match(webRunner, /profile\.displayName/);
  assert.match(webRunner, /openProfileWithBridge/);
  assert.match(webRunner, /data-quata-member-profile-id/);
  assert.match(webRunner, /quata-profile-entry-e2e=1/);
  assert.match(webRunner, /withTimeout\(pageContext\?\.context\?\.close\(\)/);
  assert.match(webRunner, /playwright_browser_close/);
  assert.match(webRunner, /profile_entry_feed_official_communities_conversations_and_chat_fixtures_prepared/);
  assert.match(webRunner, /profile_entry_official_post_deleted/);
  assert.match(webRunner, /cleanup_verified_profile_entry_official_residue_absent/);
  assert.match(webRunner, /cleanupProfileContentFixture/);
  assert.match(webRunner, /createPrivateChatSeed/);
  assert.match(webRunner, /openAuthenticatedRoute\(page, origin, `post-/);
  assert.match(webRunner, /openAuthenticatedRoute\(page, origin, `official-/);
  assert.match(webRunner, /openAuthenticatedRoute\(page, origin, "chat", "chat"\)/);
  assert.doesNotMatch(webRunner, /680242607|680242608|21085800|SERVICE_ROLE\s*=/);
});

test("PROF-ENTRY Android and iOS evidence cover Feed, Official, Communities, Conversations and Chat", () => {
  assert.match(androidRunner, /--profile-entry-only/);
  assert.match(androidRunner, /--community-chat-only/);
  assert.match(androidRunner, /async function prepareProfileEntryFixture\(config, runId\)/);
  assert.match(androidRunner, /prepareProfileEntryFixture\(config, runId\)/);
  assert.match(androidRunner, /resolveCommunityChatTarget/);
  assert.match(androidRunner, /community_walls_stats/);
  assert.match(androidRunner, /runInstrumentationStage\("community-chat"\)/);
  assert.match(androidRunner, /community_chat_opened_from_shared_android_community_anchor/);
  assert.match(androidRunner, /neighborhood\.chat\.\$\{neighborhoodTagSuffix\(state\.communityChat\.name\)\}/);
  assert.match(androidRunner, /community_chat_only_completed/);
  assert.match(androidRunner, /profile_entry_feed_official_communities_conversations_and_chat_fixtures_prepared/);
  assert.match(androidRunner, /profile_entry_feed_official_communities_conversations_and_chat_opened_common_profile_and_returned/);
  assert.match(androidRunner, /cleanupOfficialProfileEntryPost/);
  assert.match(androidRunner, /quataChatActionsOfficialPostId/);
  assert.match(androidUiTest, /"profile-entry" ->/);
  assert.match(androidUiTest, /"community-chat" ->/);
  assert.match(androidUiTest, /runProfileEntryStage/);
  assert.match(androidUiTest, /runCommunityChatStage/);
  assert.match(androidUiTest, /quataChatActionsCommunityName/);
  assert.match(androidUiTest, /neighborhood\.chat\.\$\{communityName\.toNeighborhoodTagSuffix\(\)\}/);
  assert.match(androidUiTest, /android-community-chat-opened/);
  assert.match(androidUiTest, /quataPostUrl\(feedPostId\)/);
  assert.match(androidUiTest, /quataOfficialPostUrl\(officialPostId\)/);
  assert.match(androidUiTest, /evidenceStartIntent\(AppDestinations\.Conversations\.route\)/);
  assert.match(androidUiTest, /evidenceStartIntent\(AppDestinations\.Neighborhoods\.route\)/);
  assert.match(androidRunner, /quataChatActionsProfileNeighborhood/);
  assert.match(androidUiTest, /quataChatActionsProfileNeighborhood/);
  assert.match(androidUiTest, /toNeighborhoodTagSuffix/);
  assert.match(androidUiTest, /neighborhood\.user\.avatar\.\$profileId/);
  assert.match(androidUiTest, /conversation\.avatar\.\$profileId/);
  assert.match(androidUiTest, /clickStableTag\(tag\)/);
  assert.match(androidUiTest, /By\.descContains\(tag\)/);
  assert.match(mainActivity, /AppDestinations\.Conversations\.route/);

  assert.match(iosRunner, /--profile-entry-only/);
  assert.match(iosRunner, /--community-chat-only/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_PROFILE_ENTRY_UI_E2E/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_COMMUNITY_CHAT_UI_E2E/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_COMMUNITY_NAME/);
  assert.match(iosRunner, /resolveCommunityChatTarget/);
  assert.match(iosRunner, /community_walls_stats/);
  assert.match(iosRunner, /testCommunityChatOpensFromSharedCommunityAnchor/);
  assert.match(iosRunner, /community_chat_opened_and_returned_to_source_communities_ios/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_PROFILE_ENTRY_NEIGHBORHOOD/);
  assert.match(iosRunner, /prepareProfileEntryFixture/);
  assert.match(iosRunner, /profile_entry_feed_official_communities_conversations_and_chat_fixtures_prepared/);
  assert.match(iosRunner, /cleanupOfficialProfileEntryPost/);
  assert.match(iosUiTest, /testProfileEntryFromFeedOfficialConversationsAndChatReturns/);
  assert.match(iosUiTest, /testCommunityChatOpensFromSharedCommunityAnchor/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_COMMUNITY_CHAT_UI_E2E/);
  assert.match(iosUiTest, /neighborhood\.chat\.\\\(neighborhoodTagSuffix\(communityName\)\)/);
  assert.match(iosUiTest, /ios-community-chat-opened/);
  assert.match(iosUiTest, /chat\.back/);
  assert.match(iosUiTest, /ios-community-chat-returned/);
  assert.match(iosUiTest, /Community Chat back must return to Communities/);
  assert.match(iosUiTest, /feed\.author\.avatar\.\\\(peerProfileId\)/);
  assert.match(iosUiTest, /official\.author\.avatar\.\\\(peerProfileId\)/);
  assert.match(iosUiTest, /navigation\.primary\.neighborhoods/);
  assert.match(iosUiTest, /neighborhood\.members\.\\\(neighborhoodTagSuffix\(neighborhood\)\)/);
  assert.match(iosUiTest, /neighborhood\.user\.avatar\.\\\(peerProfileId\)/);
  assert.match(iosUiTest, /navigation\.primary\.conversations/);
  assert.match(iosUiTest, /conversation\.avatar\.\\\(peerProfileId\)/);
  assert.match(iosUiTest, /chat\.profile\.message\.\\\(peerProfileId\)/);
  assert.doesNotMatch(`${androidRunner}\n${iosRunner}`, /680242607|680242608|21085800|SERVICE_ROLE\s*=/);
});

test("PROF-ENTRY product anchors live in common/shared surfaces", () => {
  assert.match(feedAnchor, /fun feedAuthorAvatarTestTag\(profileId: String\): String = "feed\.author\.avatar\.\$profileId"/);
  assert.match(officialAnchor, /fun officialAuthorAvatarTestTag\(profileId: String\): String = "official\.author\.avatar\.\$profileId"/);
  assert.match(officialHost, /authorProfileTestTag = officialAuthorAvatarTestTag\(post\.author\.id\)/);
  assert.doesNotMatch(officialHost, /Modifier\s*\n\s*\.size\(58\.dp\)\s*\n\s*\.testTag\(officialAuthorAvatarTestTag\(post\.author\.id\)\)/);
  assert.doesNotMatch(officialHost, /Modifier\s*\n\s*\.size\(58\.dp\)\s*\n\s*\.semantics\s*\{\s*contentDescription = officialAuthorAvatarTestTag\(post\.author\.id\)\s*\}/);
  assert.match(webOfficialHost, /BrowserOfficialAuthorAvatar\(post, onOpenUserProfile/);
  assert.match(webOfficialHost, /commonHeaderOwnsProfileNavigation = onOpenUserProfile/);
  assert.doesNotMatch(webOfficialHost, /BrowserRemoteAvatar\([\s\S]*officialAuthorAvatarTestTag\(post\.author\.id\)[\s\S]*\.clickable/);
  assert.match(conversationAnchor, /fun conversationAvatarTestTag\(profileId: String\): String = "conversation\.avatar\.\$profileId"/);
  assert.match(conversationAnchor, /contentDescription = conversationAvatarTestTag\(id\)/);
  assert.match(conversationList, /const val ConversationListTestTag: String = "conversation\.list"/);
  assert.match(
    conversationList,
    /modifier = modifier\.fillMaxSize\(\)\.semantics\s*\{\s*testTag = ConversationListTestTag\s*contentDescription = ConversationListTestTag\s*\}/,
  );
  assert.equal((conversationsHost.match(/conversationAvatarTestTag/g) ?? []).length, 0);
  assert.match(neighborhoodList, /fun neighborhoodMembersButtonTestTag\(communityName: String\): String =\s*\n\s*"neighborhood\.members\.\$\{communityName\.toNeighborhoodTestTagSuffix\(\)\}"/);
  assert.match(neighborhoodList, /fun neighborhoodChatButtonTestTag\(communityName: String\): String =\s*\n\s*"neighborhood\.chat\.\$\{communityName\.toNeighborhoodTestTagSuffix\(\)\}"/);
  assert.match(neighborhoodList, /fun neighborhoodChatStatusTestTag\(communityName: String\): String =\s*\n\s*"neighborhood\.chat\.status\.\$\{communityName\.toNeighborhoodTestTagSuffix\(\)\}"/);
  assert.match(neighborhoodList, /internal fun canOpenCommunityChat\(community: NeighborhoodCommunity\): Boolean =\s*\n\s*community\.conversationId != null \|\| community\.wallId != null/);
  assert.match(neighborhoodList, /contentDescription = neighborhoodMembersButtonTestTag\(community\.name\)/);
  assert.match(neighborhoodList, /contentDescription = neighborhoodChatButtonTestTag\(community\.name\)/);
  assert.match(neighborhoodList, /contentDescription = neighborhoodChatStatusTestTag\(community\.name\)/);
  assert.match(neighborhoodUsers, /fun neighborhoodUserAvatarTestTag\(profileId: String\): String = "neighborhood\.user\.avatar\.\$profileId"/);
  assert.match(neighborhoodUsers, /contentDescription = neighborhoodUserAvatarTestTag\(user\.id\)/);
  assert.match(profileUsersList, /contentDescription = avatarTag/);
  assert.match(profileKpiContent, /contentDescription = tag/);
  assert.match(communityProfileHost, /contentDescription = PublicProfileBackTestTag/);
  assert.match(communityProfileHost, /contentDescription = PublicProfileFooterBackTestTag/);
  assert.match(bottomNavigation, /navigation\.primary\.\$\{item\.id\}/);
  assert.match(mainActivity, /AppDestinations\.Neighborhoods\.route/);
  assert.match(webMain, /installWebProfileEntryE2eBridge\(\s*openProfile = feedMemberProfileRoute::open,/);
  assert.match(webMain, /closeProfile = feedMemberProfileRoute::close,/);
  assert.match(webMain, /openCommunityMembers = \{ neighborhood ->/);
  assert.match(webMain, /requestedCommunityMembers = profileEntryCommunityMembersRequest/);
  assert.match(webMain, /setWebMemberProfileMarker\(feedMemberProfileRoute\.profileId\)/);
  assert.match(webBridge, /quata-profile-entry-e2e/);
  assert.match(webBridge, /localhost/);
  assert.match(webBridge, /__quataProfileEntryE2eProduct/);
  assert.match(webBridge, /closeProfile/);
  assert.match(webBridge, /openCommunityMembers/);
  assert.match(webProfileRoute, /setWebMemberProfileRouteMarker\(profileId\)/);
  assert.match(webProfileRoute, /data-quata-member-profile-id/);
  assert.match(webProfileRoute, /private val profileStack = mutableListOf<String>\(\)/);
  assert.match(webProfileRoute, /profileId = profileStack\.lastOrNull\(\)/);
});

test("PROF-ENTRY focal error retry and nested return preserve the exact route on all platforms", () => {
  assert.match(neighborhoodsViewModel, /failedProfileUserId = if \(failedInitialOpen\) userId/);
  assert.match(neighborhoodsViewModel, /fun retryFailedUserProfile\(\)/);
  assert.match(neighborhoodsViewModel, /openUserProfile\(userId\)/);
  for (const tag of ["public-profile.load.error", "public-profile.load.retry", "public-profile.load.back"]) {
    assert.match(profileLoadState, new RegExp(tag.replaceAll(".", "\\.")));
  }

  assert.match(androidProfileLoadFault, /AtomicBoolean/);
  assert.match(androidProfileRepository, /BuildConfig\.DEBUG && ProfileLoadEvidenceFaults\.consumeFailure\(\)/);
  assert.match(androidRunner, /--profile-entry-error-deep-only/);
  assert.match(androidRunner, /prepareProfileFollowPresent/);
  assert.match(androidRunner, /restoreProfileFollowEdge/);
  assert.match(androidUiTest, /runProfileEntryErrorDeepStage/);
  assert.match(androidUiTest, /public-profile\.list\.avatar\.followers\.\$nestedProfileId/);
  assert.match(androidUiTest, /private fun closePublicProfileLevel\(\)/);
  assert.match(androidUiTest, /if \(!closedByCommonBack\) \{\s*device\.pressBack\(\)/);
  assert.match(androidUiTest, /android-chat-profile-error-deep-return/);

  assert.match(webProfileRepository, /quata-profile-load-error-retry-e2e/);
  assert.match(webProfileRepository, /localhost/);
  assert.match(webRunner, /--profile-entry-error-deep-only/);
  assert.match(webRunner, /verifyProfileEntryErrorDeepWeb/);
  assert.match(webRunner, /public-profile\.list\.avatar\.followers\.\$\{state\.a\.profileId\}/);
  assert.match(webRunner, /visibleAriaLocator\(\s*page,\s*\[new RegExp\(escapeRegExp\(followersTag\)\)\]/);
  assert.match(webRunner, /visibleAriaLocator\(\s*page,\s*\[new RegExp\(escapeRegExp\(nestedAvatarTag\)\)\]/);
  assert.match(webRunner, /visibleAriaLocator\(\s*page,\s*\[new RegExp\(escapeRegExp\("public-profile\.back"\)\)\]/);
  assert.match(webRunner, /waitForExactChatRoute\(page, conversationId\)/);
  assert.match(webRunner, /web-chat-profile-error-deep-return/);

  assert.match(iosProfilePreloader, /requestFailureOnceForEvidence/);
  assert.match(iosApp, /-quata-ui-test-profile-load-error-retry/);
  assert.match(iosApp, /presentMemberProfileLoadFailure\(profileId:/);
  assert.match(iosRunner, /--profile-entry-error-deep-only/);
  assert.match(iosRunner, /prepareProfileFollowPresent/);
  assert.match(iosUiTest, /testProfileEntryLoadErrorRetryAndNestedReturnToChat/);
  assert.match(iosUiTest, /public-profile\.list\.avatar\.followers\.\\\(actorProfileId\)/);
  assert.match(iosUiTest, /assertChatRoute\(conversationId, in: app, context: "profile load error\/deep exact Chat return"\)/);
  assert.match(iosShellRunner, /QUATA_IOS_CHAT_PROFILE_ENTRY_ERROR_DEEP_UI_E2E/);
  assert.match(iosShellRunner, /testProfileEntryLoadErrorRetryAndNestedReturnToChat/);
  assert.doesNotMatch(`${androidProfileLoadFault}\n${androidProfileRepository}\n${webProfileRepository}\n${iosProfilePreloader}`, /SERVICE_ROLE\s*=|IMPORT-PASSWORD|BEGIN PRIVATE KEY/);
});

test("PROF-ENTRY restores a committed fixture edge when its confirmation poll fails", async () => {
  let edgePresent = false;
  let retainedSnapshot = null;
  await assert.rejects(
    prepareReversibleProfileFollow({
      exists: async () => edgePresent,
      insert: async () => { edgePresent = true; },
      pollPresent: async () => { throw new Error("poll_failed_after_insert"); },
      restore: async (initiallyFollowing) => { edgePresent = initiallyFollowing; },
      retainSnapshot: (snapshot) => { retainedSnapshot = snapshot; },
    }),
    /poll_failed_after_insert/,
  );
  assert.deepEqual(retainedSnapshot, { initiallyFollowing: false });
  assert.equal(edgePresent, false);
  for (const runner of [androidRunner, webRunner, iosRunner]) {
    assert.match(runner, /prepareReversibleProfileFollow/);
    assert.match(runner, /\(snapshot\) => \{ state\.profileFollow = snapshot; \}/);
  }
});
