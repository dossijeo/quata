import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("conversation search persistence is actor scoped, bounded and wired to every launcher", async () => {
  const [preferences, model, host, android, web, webMain, ios, iosBootstrap] = await Promise.all([
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationSearchPreferences.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsViewModel.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsScreenHost.kt"),
    read("app/src/main/java/com/quata/feature/chat/presentation/conversations/ConversationsAndroidViewModel.kt"),
    read("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
    read("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
    read("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt"),
    read("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/IosChatRuntimeBootstrap.kt"),
  ]);

  assert.match(preferences, /KeyPrefix = "quata\.chat\.conversations\.search\.v1\."/);
  assert.match(preferences, /"\$KeyPrefix\$actorProfileId"/);
  assert.match(preferences, /const val MaxQueryLength = 160/);
  assert.match(preferences, /if \(bounded\.isBlank\(\)\) store\.remove/);
  assert.match(model, /searchRevision/);
  assert.match(model, /searchRevision == revision/);
  assert.match(model, /repository\.currentUser\(\)\?\.id == actorId/);
  assert.match(model, /if \(actorId == null\)/);
  assert.match(model, /override fun onConversationQueryChanged/);
  assert.match(host, /query = state\.searchQuery/);
  assert.match(host, /onQueryChange = viewModel::onConversationQueryChanged/);
  assert.match(android, /AndroidPreferenceStore\(context\.applicationContext\)/);
  assert.match(web, /ConversationSearchPreferences\(preferences\)/);
  assert.match(webMain, /preferences = platformServices\.preferences/);
  assert.match(ios, /ConversationSearchPreferences\(dependencies\.preferences\)/);
  assert.match(iosBootstrap, /IosPreferenceStore\(\)/);
});

test("focused evidence uses real cold relaunch boundaries and verifies the filtered empty state", async () => {
  const [androidRunner, androidTest, webRunner, iosRunner, iosShellRunner, iosTest] = await Promise.all([
    read("scripts/chat-actions-notifications-android-evidence.mjs"),
    read("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
    read("scripts/chat-actions-notifications-web-evidence.mjs"),
    read("scripts/chat-actions-notifications-ios-evidence.mjs"),
    read("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
    read("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  ]);

  assert.match(androidRunner, /--conversations-cold-search-only/);
  assert.match(androidRunner, /"am", "force-stop", "com\.quata"/);
  assert.match(androidTest, /ConversationEmptyTestTag/);
  assert.match(androidTest, /assertTextEquals\(conversationSubject\)/);
  assert.match(webRunner, /forceReload: true/);
  assert.match(webRunner, /conversations_cold_search_value_not_restored/);
  assert.match(iosRunner, /QUATA_IOS_CONVERSATIONS_COLD_SEARCH_ONLY/);
  assert.match(iosShellRunner, /QUATA_IOS_CONVERSATIONS_COLD_SEARCH_ONLY/);
  assert.match(iosTest, /app\.terminate\(\)/);
  assert.match(iosTest, /identifier: "conversation\.empty"/);
  assert.match(iosTest, /XCTAssertEqual\(restoredSearch\.value as\? String, conversationsSubject/);
  assert.match(iosTest, /replaceConversationSearchText/);
  assert.match(iosTest, /withNormalizedOffset: CGVector\(dx: 0\.96, dy: 0\.5\)/);
});
