import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");
const [android, androidActivity, androidRestorePolicyTest, androidNotificationTests, androidRunner, ios, iosTests, web, webTests, webBrowserRunner, postDetailWebRunner] = await Promise.all([
  source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
  source("app/src/main/java/com/quata/MainActivity.kt"),
  source("app/src/test/java/com/quata/IncomingNavigationRestorePolicyTest.kt"),
  source("app/src/test/java/com/quata/core/navigation/NotificationChatReturnRouteTest.kt"),
  source("scripts/shell-navigation-android-process-death-evidence.mjs"),
  source("iosApp/iosApp/QuataIosApp.swift"),
  source("iosApp/iosAppTests/QuataFeedFrameworkTests.swift"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
  source("web/src/wasmJsTest/kotlin/com/quata/web/WebNavigationTest.kt"),
  source("scripts/web-authenticated-browser-e2e.mjs"),
  source("scripts/post-detail-web-evidence.mjs"),
]);

test("Android retains the exact focused Chat message in saveable shell state and has a bounded focal proof", () => {
  assert.match(android, /var persistedChatFocusConversationId by rememberSaveable/);
  assert.match(android, /var persistedChatFocusedMessageId by rememberSaveable/);
  assert.match(android, /var activeChatFocusConversationId by remember \{ mutableStateOf\(persistedChatFocusConversationId\) \}/);
  assert.match(android, /var activeChatFocusedMessageId by remember \{ mutableStateOf\(persistedChatFocusedMessageId\) \}/);
  assert.match(android, /persistedChatFocusConversationId = conversationId\.takeIf \{ focusedMessageId != null \}/);
  assert.match(android, /focusedMessageId = activeChatFocusedMessageId\.takeIf \{[\s\S]*activeChatFocusConversationId == conversationId/);
  assert.match(android, /onFocusedMessageHandled = \{[\s\S]*activeChatFocusConversationId = null[\s\S]*activeChatFocusedMessageId = null/);
  assert.match(android, /shouldClearNotificationChatReturn\(lastObservedRoute, currentRoute, isAuthenticated\)/);
  assert.match(android, /persistedChatFocusConversationId = null[\s\S]*persistedChatFocusedMessageId = null[\s\S]*lastObservedRoute = currentRoute/);
  assert.match(androidRunner, /--exact-chat-only/);
  assert.match(androidRunner, /exact_chat_target_selected_by_uiautomator_after_distinct_task_base_intent/);
  assert.match(androidRunner, /exact_chat_differential_base_intent_not_preserved/);
  assert.match(androidRunner, /quata_chat_get_favorites/);
  assert.match(androidRunner, /PublicLinkTest#openExactFavoriteForProcessDeathProbe/);
  assert.match(androidRunner, /exact_chat_favorite_opened_by_accessibility_action/);
  assert.match(androidRunner, /clickResource\(`chat\.message\.\$\{exactChatTarget\.messageId\}`\)/);
  assert.match(androidRunner, /exact_chat_session_still_refreshable/);
  assert.match(androidRunner, /Promise\.allSettled\(\[webCleanup\(\), authCleanup\(\)\]\)/);
  assert.match(androidRunner, /auxiliarySessionRevoked/);
  assert.match(androidRunner, /exact_chat_conversation_and_message_restored_in_new_process/);
});

test("iOS persists and restores the exact Chat conversation and message without storing content", () => {
  assert.match(ios, /persistedChatRoutePrefix = "chat-v1:"/);
  assert.match(ios, /struct PersistedChatRoute: Codable/);
  assert.match(ios, /conversationId: String/);
  assert.match(ios, /messageId: String\?/);
  assert.match(ios, /JSONEncoder\(\)\.encode/);
  assert.match(ios, /JSONDecoder\(\)\.decode\(PersistedChatRoute\.self/);
  assert.match(ios, /persistPrimaryRoute\("feed"\)/);
  assert.match(iosTests, /testExactChatConversationAndFocusedMessageSurviveRouterRecreation/);
  assert.match(ios, /case let \.chat\(conversationId, _\):[\s\S]*return "chat:\\\(conversationId\)"/);
  assert.match(iosTests, /testMemberProfileOriginSeparatesExactChatConversations/);
  assert.match(iosTests, /"chat:sb:conversation-a"/);
  assert.match(iosTests, /"chat:sb:conversation-b"/);
});

test("Android failed restored profile Back returns through the retained parent", () => {
  assert.match(
    android,
    /failedProfileUserId[\s\S]*CommunityProfileLoadStateContent\([\s\S]*onBack = \{ globalProfileViewModel\.closeUserProfile\(\) \}/,
  );
  assert.doesNotMatch(
    android,
    /failedProfileUserId[\s\S]*CommunityProfileLoadStateContent\([\s\S]*onBack = globalProfileViewModel::dismissUserProfileLoadFailure/,
  );
});

test("iOS persists exact Feed and Official post routes without storing post content", () => {
  assert.match(ios, /persistedFeedPostRoutePrefix = "feed-v1:"/);
  assert.match(ios, /persistedOfficialPostRoutePrefix = "official-v1:"/);
  assert.match(ios, /struct PersistedPostRoute: Codable/);
  assert.match(ios, /encodedPostRoute\(postId: postId, prefix: persistedFeedPostRoutePrefix\)/);
  assert.match(ios, /encodedPostRoute\(postId: postId, prefix: persistedOfficialPostRoutePrefix\)/);
  assert.match(ios, /JSONDecoder\(\)\.decode\(PersistedPostRoute\.self/);
  assert.match(iosTests, /testExactFeedAndOfficialPostIdsSurviveRouterRecreationAndAuthenticationUpgrade/);
  assert.match(iosTests, /testFocusedPostChangesAndClosesRefreshThePersistedNestedRoute/);
  assert.match(iosTests, /testMalformedPersistedPostRouteFailsClosedToTheNormalRoot/);
});

test("Android and Web restore exact Feed and Official posts without replaying a consumed route", () => {
  assert.match(android, /var feedFocusedPostId by rememberSaveable/);
  assert.match(android, /var officialFocusedPostId by rememberSaveable/);
  assert.match(androidActivity, /getBoolean\(IncomingNavigationRestorePolicy\.ConsumedStateKey, false\)/);
  assert.match(androidActivity, /outState\.putBoolean\(IncomingNavigationRestorePolicy\.ConsumedStateKey, incomingLinkConsumed\)/);
  assert.match(androidActivity, /consumedBeforeRecreation = consumedBeforeRecreation/);
  assert.match(androidActivity, /onIncomingLinkHandled = ::clearIncomingLink/);
  assert.match(androidActivity, /incomingLinkConsumed = true/);
  assert.match(androidActivity, /setIntent\(Intent\(this, MainActivity::class\.java\)\.apply \{ action = Intent\.ACTION_MAIN \}\)/);
  assert.match(androidRestorePolicyTest, /pendingDeepLinkIsDeliveredAcrossRecreation/);
  assert.match(androidRestorePolicyTest, /consumedDeepLinkIsNotReplayedAcrossRecreation/);
  assert.match(androidRunner, /--exact-post-only/);
  assert.match(androidRunner, /-Pquata\.useMockBackend=true/);
  assert.match(androidRunner, /exact_\$\{target\.kind\}_post_restored_in_new_process_without_route_replay/);
  assert.match(androidRunner, /"am", "kill", PACKAGE/);
  assert.match(androidRunner, /"am", "start", "-W", "-n", `\$\{PACKAGE\}\/\.MainActivity`/);
  assert.match(androidRunner, /waitForResource\(target\.identityResource\)/);
  assert.match(androidRunner, /exact_post_differential_base_intent_not_preserved/);
  assert.match(androidRunner, /differentialBaseIntentVerified: true/);
  assert.match(androidRunner, /exact_\$\{target\.kind\}_post_back_returned_to_retained_root/);

  assert.match(postDetailWebRunner, /openRoute\(page, origin, `post-\$\{encodeURIComponent\(state\.feed\.postId\)\}`, `post\/\$\{state\.feed\.postId\}`\)/);
  assert.match(postDetailWebRunner, /page\.reload\(\{ waitUntil: "domcontentloaded", timeout: 60_000 \}\)/);
  assert.match(postDetailWebRunner, /data-quata-feed-detail/);
  assert.match(postDetailWebRunner, /data-quata-official-detail-title/);
  assert.match(postDetailWebRunner, /feed_detail_marker_missing_after_reload/);
  assert.match(postDetailWebRunner, /official_detail_title_marker_missing_after_reload/);
  assert.match(postDetailWebRunner, /verifyExactPostSurvivesDocumentReload/);
  assert.match(postDetailWebRunner, /performance\.timeOrigin/);
  assert.match(postDetailWebRunner, /exact_\$\{label\}_post_restored_after_real_web_document_reload_without_route_replay/);
  assert.match(postDetailWebRunner, /\$\{label\}_exact_post_hash_changed_after_reload/);
});

test("Web keeps allowlisted Communities and Notifications returns only for the matching exact Chat route", () => {
  assert.match(web, /quata\.web\.chat-return\.conversation/);
  assert.match(web, /quata\.web\.chat-return\.fragment/);
  assert.match(web, /supportedConversationReturnFragments = setOf\("communities", "notifications"\)/);
  assert.match(web, /storedConversationId === conversationId &&[\s\S]*storedFragment === 'communities' \|\| storedFragment === 'notifications'/);
  assert.match(web, /returnFragment: String\? = null/);
  assert.match(web, /takeIf \{ it in supportedConversationReturnFragments \}/);
  assert.match(web, /clearConversationReturn\(\)/);
  assert.match(webTests, /communityConversationReturnSurvivesAFullDocumentReload/);
  assert.match(webTests, /assertEquals\("message 9", reloadedDocument\.chatMessageId\)/);
  assert.match(webBrowserRunner, /authenticated_exact_chat_document_reload/);
  assert.match(webBrowserRunner, /assertExactChatFocusSurvivesDocumentReload/);
  assert.match(webBrowserRunner, /exact_chat_focus_reselected_once_after_real_document_reload/);
  assert.match(webBrowserRunner, /newDocument: second\.timeOrigin !== first\.timeOrigin/);
});

test("Notifications to exact Chat restores its native parent across recreation on every platform", () => {
  assert.match(android, /persistedChatReturnConversationId by rememberSaveable/);
  assert.match(android, /persistedChatReturnRoute by rememberSaveable/);
  assert.match(android, /navigateToChat\(id, returnRoute = AppDestinations\.Notifications\.route\)/);
  assert.match(android, /notificationChatReturnRoute\([\s\S]*currentConversationId = conversationId/);
  assert.match(android, /popBackStack\(returnRoute, inclusive = false\)/);
  assert.match(androidNotificationTests, /returnsNotificationsOnlyForTheExactConversation/);
  assert.match(androidNotificationTests, /rejectsBlankConversationAndUnrelatedReturnRoutes/);
  assert.match(androidNotificationTests, /clearsTheReturnWhenLeavingChatOrLoggingOut/);
  assert.match(android, /shouldClearNotificationChatReturn\(lastObservedRoute, currentRoute, isAuthenticated\)/);
  assert.match(android, /persistedChatReturnConversationId = null[\s\S]*persistedChatReturnRoute = null/);

  assert.match(web, /navigateConversation\(conversationId, returnFragment = "notifications"\)/);
  assert.match(web, /pendingAuthenticationReturnFragment/);
  assert.match(web, /pendingAuthenticationFragment = null\s+pendingAuthenticationReturnFragment = null\s+isAuthRequiredPromptOpen = false/);
  assert.match(webTests, /notificationConversationReturnsToNotificationsAfterControllerRecreationWithPersistedStorage/);
  assert.match(webTests, /notificationReturnIsBoundToTheExactConversation/);

  assert.match(ios, /persistedChatReturnKey = "quata\.ios\.shell\.chat-return"/);
  assert.match(ios, /func showNotificationsChat\(conversationId: String, messageId: String\? = nil\)/);
  assert.match(ios, /case \.notifications: showNotifications\(\)/);
  assert.match(ios, /restorableChatReturn\([\s\S]*conversationId == snapshot\.conversationId/);
  assert.match(iosTests, /testNotificationsChatBackReturnsToNotificationsAfterRouterRecreation/);
  assert.match(iosTests, /testNotificationsChatReturnIsBoundToExactConversationAndClearedByReplacement/);
  assert.match(iosTests, /testDismissingNotificationChatAuthenticationClearsTheReturnMarker/);
});
