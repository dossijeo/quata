import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");
const [android, androidRunner, ios, iosTests, web, webTests, webBrowserRunner] = await Promise.all([
  source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
  source("scripts/shell-navigation-android-process-death-evidence.mjs"),
  source("iosApp/iosApp/QuataIosApp.swift"),
  source("iosApp/iosAppTests/QuataFeedFrameworkTests.swift"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
  source("web/src/wasmJsTest/kotlin/com/quata/web/WebNavigationTest.kt"),
  source("scripts/web-authenticated-browser-e2e.mjs"),
]);

test("Android retains the exact focused Chat message in saveable shell state and has a bounded focal proof", () => {
  assert.match(android, /var persistedChatFocusConversationId by rememberSaveable/);
  assert.match(android, /var persistedChatFocusedMessageId by rememberSaveable/);
  assert.match(android, /var activeChatFocusConversationId by remember \{ mutableStateOf\(persistedChatFocusConversationId\) \}/);
  assert.match(android, /var activeChatFocusedMessageId by remember \{ mutableStateOf\(persistedChatFocusedMessageId\) \}/);
  assert.match(android, /persistedChatFocusConversationId = conversationId\.takeIf \{ focusedMessageId != null \}/);
  assert.match(android, /focusedMessageId = activeChatFocusedMessageId\.takeIf \{[\s\S]*activeChatFocusConversationId == conversationId/);
  assert.match(android, /onFocusedMessageHandled = \{[\s\S]*activeChatFocusConversationId = null[\s\S]*activeChatFocusedMessageId = null/);
  assert.match(android, /lastObservedRoute == AppDestinations\.Chat\.route && currentRoute != AppDestinations\.Chat\.route/);
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

test("Web keeps an allowlisted Communities return only for the matching exact Chat route", () => {
  assert.match(web, /quata\.web\.chat-return\.conversation/);
  assert.match(web, /quata\.web\.chat-return\.fragment/);
  assert.match(web, /storedConversationId === conversationId && storedFragment === 'communities'/);
  assert.match(web, /returnFragment: String\? = null/);
  assert.match(web, /takeIf \{ it == "communities" \}/);
  assert.match(web, /clearConversationReturn\(\)/);
  assert.match(webTests, /communityConversationReturnSurvivesAFullDocumentReload/);
  assert.match(webTests, /assertEquals\("message 9", reloadedDocument\.chatMessageId\)/);
  assert.match(webBrowserRunner, /authenticated_exact_chat_document_reload/);
  assert.match(webBrowserRunner, /assertExactChatFocusSurvivesDocumentReload/);
  assert.match(webBrowserRunner, /exact_chat_focus_reselected_once_after_real_document_reload/);
  assert.match(webBrowserRunner, /newDocument: second\.timeOrigin !== first\.timeOrigin/);
});
