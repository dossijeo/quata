import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => await readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("conversation list exposes stable common anchors through every host", async () => {
  const [host, list, header, web, android, ios] = await Promise.all([
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsScreenHost.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsListContent.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsListHeaderContent.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
    source("app/src/main/java/com/quata/feature/chat/presentation/conversations/ConversationsScreen.kt"),
    source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt"),
  ]);

  assert.match(list, /ConversationRowTestTagPrefix: String = "conversation\.row\."/);
  assert.match(list, /conversationRowTestTag\(row\.conversation\.id\)/);
  assert.match(header, /ConversationSearchTestTag = "conversation\.search"/);
  for (const tag of [
    "conversation.favorites",
    "conversation.new",
    "conversation.picker",
    "conversation.picker.search",
    "conversation.picker.candidate.",
    "conversation.picker.dismiss",
  ]) {
    assert.match(host, new RegExp(tag.replaceAll(".", "\\.")));
  }
  for (const launcher of [web, android, ios]) {
    assert.match(launcher, /ConversationsScreenHost\(/);
  }
});

test("Web focal evidence filters two custodied rows and opens real common destinations", async () => {
  const runner = await source("scripts/chat-actions-notifications-web-evidence.mjs");

  assert.match(runner, /--conversations-only/);
  assert.match(runner, /qadata-chat-actions-notifications-conversations-control-/);
  assert.match(runner, /conversations_search_control_not_filtered/);
  assert.match(runner, /conversation\.row\.\$\{conversationId\}/);
  assert.match(runner, /data-quata-shell-route/);
  assert.match(runner, /`chat\/\$\{conversationId\}`/);
  assert.match(runner, /chat\/__favorite_messages__/);
  assert.match(runner, /conversation\.picker\.candidate\.\$\{fixture\.peerProfileId\}/);
  assert.match(runner, /conversations_new_picker_search_candidate_and_route_reset_verified_without_mutation/);
  assert.match(runner, /hardDeleteTemporaryThread\(\s*controlThreadId/);
  assert.match(runner, /cleanup_verified_conversations_control_physical_residue_absent/);
  assert.ok(
    runner.indexOf("state.conversations = {") < runner.indexOf("const controlThreadId = threadId(await rpc"),
    "control-thread cleanup intent must be durable before the create RPC",
  );
  assert.match(runner, /waitForTemporaryThreadIdByUniqueKey\(state\.conversations\.controlUniqueKey\)/);
  assert.match(runner, /while \(Date\.now\(\) < deadline\)[\s\S]*?await delay\(250\)/);
  assert.match(runner, /cleanup_verified_conversations_control_thread_absent_after_uncertain_create/);
});

test("iOS focal runner propagates the Conversations fixture into XCTest", async () => {
  const [coordinator, runner, uiTest, favoritesHeader] = await Promise.all([
    source("scripts/chat-actions-notifications-ios-evidence.mjs"),
    source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
    source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/FavoriteMessagesHeaderContent.kt"),
  ]);

  for (const key of [
    "QUATA_IOS_CONVERSATIONS_UI_E2E",
    "QUATA_IOS_CONVERSATIONS_CONVERSATION_ID",
    "QUATA_IOS_CONVERSATIONS_DECOY_CONVERSATION_ID",
    "QUATA_IOS_CONVERSATIONS_SUBJECT",
    "QUATA_IOS_CONVERSATIONS_CANDIDATE_QUERY",
  ]) {
    assert.match(coordinator, new RegExp(key));
    assert.match(runner, new RegExp(`'${key}'`));
    assert.match(uiTest, new RegExp(`environment\\[\"${key}\"\\]`));
  }
  assert.match(runner, /testConversationsPostflightUsesSharedSurface/);
  assert.match(uiTest, /runConversationsPostflight\(/);
  assert.match(coordinator, /conversations_picker_closed_without_backend_mutation/);
  assert.match(coordinator, /conversations_backend_mutated/);
  assert.match(coordinator, /conversationTopologySnapshot/);
  assert.match(coordinator, /topologyBefore: redactConversationTopology/);
  assert.match(coordinator, /QUATA_IOS_SIGNED_DERIVED_DATA_PATH=\$\{shellQuote\(options\.derivedDataPath\)\}/);
  assert.match(coordinator, /QUATA_IOS_SIGNED_RESULT_BUNDLE_PATH=\$\{shellQuote\(`/);
  assert.match(coordinator, /waitForConversationsControlThreadIdByUniqueKey\(state\.decoyUniqueKey\)/);
  assert.match(coordinator, /cleanup_verified_conversations_search_control_absent_after_uncertain_create/);
  assert.match(uiTest, /decoyRow\.waitForExistence/);
  assert.match(uiTest, /decoyRow\.waitForNonExistence/);
  assert.match(uiTest, /tapTaggedButton\("chat\.back", in: app, context: "return to conversations after exact thread"\)/);
  assert.match(uiTest, /tapTaggedButton\("chat\.back", in: app, context: "return to conversations after favorites"\)/);
  assert.match(favoritesHeader, /testTag = "chat\.back"/);
});

test("Android focal evidence proves differential search, exact thread and unchanged backend topology", async () => {
  const [coordinator, uiTest] = await Promise.all([
    source("scripts/chat-actions-notifications-android-evidence.mjs"),
    source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  ]);

  assert.match(coordinator, /quataConversationsDecoyConversationId/);
  assert.match(coordinator, /conversationTopologySnapshot/);
  assert.match(coordinator, /conversations_topology_mutated/);
  assert.match(coordinator, /conversations_picker_closed_without_backend_topology_mutation/);
  assert.match(coordinator, /waitForConversationsControlThreadIdByUniqueKey\(state\.decoyUniqueKey\)/);
  assert.match(coordinator, /cleanup_verified_conversations_search_control_absent_after_uncertain_create/);
  assert.match(uiTest, /waitForTag\(decoyRowTag, "seeded search control row"/);
  assert.match(uiTest, /waitForTagGone\(decoyRowTag, "non-matching conversation filtered by search"/);
  assert.match(uiTest, /waitForMarker\(favoriteProbe, "unique marker from exact inbox thread"/);
});
