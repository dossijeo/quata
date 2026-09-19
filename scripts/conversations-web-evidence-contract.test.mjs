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

test("Web and iOS mount explicit contact pickers and the common invitation channel", async () => {
  const [commonAdapters, commonHost, commonModel, permissionPrompt, web, ios, webServices, iosServices] = await Promise.all([
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationInvitePlatformAdapters.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsScreenHost.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsViewModel.kt"),
    source("designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataPermissionPromptCardContent.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
    source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt"),
    source("core/src/wasmJsMain/kotlin/com/quata/core/platform/BrowserContactPickerService.wasm.kt"),
    source("core/src/iosMain/kotlin/com/quata/core/platform/IosContactPickerService.kt"),
  ]);

  assert.match(commonHost, /fun loadInviteContacts\(contacts: List<ChatInviteContact>\? = null\)/);
  assert.match(commonModel, /val resolvedContacts = contacts \?: withContext\(dispatchers\.io\) \{ readContacts\(\) \}/);
  assert.match(commonAdapters, /fun platformContactsForChatInvites\(/);
  assert.match(commonAdapters, /fun PlatformInviteChannelSheet\(/);
  assert.match(commonAdapters, /shareService\.share\(SharePayload\(text = strings\.message/);
  assert.match(permissionPrompt, /Text\(\s*message,[\s\S]*?modifier = Modifier\.weight\(1f\)/);
  assert.match(permissionPrompt, /modifier = Modifier\.semantics \{ contentDescription = actionLabel \}/);

  for (const [name, launcher] of [["Web", web], ["iOS", ios]]) {
    assert.match(launcher, /(?:dependencies\.)?contactPicker\.pickContacts\(\)/, `${name} must invoke its injected native picker`);
    assert.match(launcher, /platformContactsForChatInvites\(result\.value\)/, `${name} must map the selected contacts into the common model`);
    assert.match(launcher, /conversationsModel\.loadInviteContacts\(selectedInviteContacts\.value\)/, `${name} must run common registered-phone matching`);
    assert.match(launcher, /PlatformInviteChannelSheet\(/, `${name} must use the common share/copy sheet`);
    assert.match(launcher, /autoRequestInviteContacts = false/, `${name} must wait for the explicit contacts CTA`);
  }

  assert.match(web, /PlatformResult\.Unsupported -> showGenericInviteSheet = true/);
  assert.match(webServices, /navigator\?\.contacts\?\.select/);
  assert.match(webServices, /PlatformResult\.Unsupported/);
  assert.match(iosServices, /CNContactPickerViewController\(\)/);
  assert.match(iosServices, /didSelectContacts: List<\*>/);
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
  assert.match(runner, /conversations_invite_contact_picker_action_missing/);
  assert.match(runner, /conversations_invite_fallback_sheet_missing/);
  assert.match(runner, /QADATA invite no match web/);
  assert.match(runner, /conversations_invite_no_match_query_requested_terminal_candidate_page/);
  assert.match(runner, /visibleAriaLocatorWithWheelOnly\(page, \[\/\(Permitir\|Autoriser\|Allow\)\/i\]/);
  assert.match(runner, /conversations_web_explicit_contact_picker_unsupported_fallback_opened_common_share_copy_sheet/);
  assert.match(runner, /conversations_new_picker_search_candidate_and_route_reset_verified_without_mutation/);
  assert.match(runner, /hardDeleteTemporaryThread\(\s*controlThreadId/);
  assert.match(runner, /cleanup_verified_conversations_control_physical_residue_absent/);
  assert.ok(
    runner.indexOf("state.conversations = {") < runner.indexOf("const controlThreadId = threadId(await rpc"),
    "control-thread cleanup intent must be durable before the create RPC",
  );
  assert.match(runner, /waitForTemporaryThreadIdByUniqueKey\(state\.conversations\.controlUniqueKey\)/);
  assert.match(runner, /while \(Date\.now\(\) < deadline\)[\s\S]*?await delay\(250\)/);
  assert.match(runner, /throw new Error\("cleanup_pending_conversations_control_thread_uncertain_create"\)/);
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
  assert.match(uiTest, /ios-conversations-native-contact-picker/);
  assert.match(uiTest, /QADATA invite no match iOS/);
  assert.match(uiTest, /The explicit contacts action must present the real ContactsUI picker/);
  assert.match(uiTest, /nativeCancel\.tap\(\)/);
  assert.match(coordinator, /conversations_picker_closed_without_backend_mutation/);
  assert.match(coordinator, /ios_conversations_explicit_contacts_action_opened_and_cancelled_real_contactsui/);
  assert.match(coordinator, /conversations_backend_mutated/);
  assert.match(coordinator, /conversationTopologySnapshot/);
  assert.match(coordinator, /topologyBefore: redactConversationTopology/);
  assert.match(coordinator, /QUATA_IOS_SIGNED_DERIVED_DATA_PATH=\$\{shellQuote\(options\.derivedDataPath\)\}/);
  assert.match(coordinator, /QUATA_IOS_SIGNED_RESULT_BUNDLE_PATH=\$\{shellQuote\(`/);
  assert.match(coordinator, /waitForConversationsControlThreadIdByUniqueKey\(state\.decoyUniqueKey\)/);
  assert.match(coordinator, /throw new Error\("cleanup_pending_conversations_search_control_uncertain_create"\)/);
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
  assert.match(coordinator, /throw new Error\("cleanup_pending_conversations_search_control_uncertain_create"\)/);
  assert.match(uiTest, /waitForTag\(decoyRowTag, "seeded search control row"/);
  assert.match(uiTest, /waitForTagGone\(decoyRowTag, "non-matching conversation filtered by search"/);
  assert.match(uiTest, /waitForMarker\(favoriteProbe, "unique marker from exact inbox thread"/);
});
