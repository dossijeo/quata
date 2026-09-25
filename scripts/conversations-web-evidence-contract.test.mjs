import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => await readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("conversation list exposes stable common anchors and terminal root states through every host", async () => {
  const [host, list, header, rootStates, androidRootStates, viewModel, web, android, ios] = await Promise.all([
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsScreenHost.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsListContent.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsListHeaderContent.kt"),
    source("feature/chat/src/commonTest/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsRootStatesTest.kt"),
    source("app/src/androidTest/java/com/quata/feature/chat/presentation/conversations/ConversationsRootStatesInstrumentedTest.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsViewModel.kt"),
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
    "conversation.empty",
    "conversation.error",
    "conversation.retry",
    "conversation.picker",
    "conversation.picker.search",
    "conversation.picker.candidate.",
    "conversation.picker.dismiss",
  ]) {
    assert.match(host, new RegExp(tag.replaceAll(".", "\\.")));
  }
  assert.match(host, /emptyContent = \{[\s\S]*?state\.loadError \?: strings\.empty/);
  assert.match(host, /viewModel\.onEvent\(ConversationsUiEvent\.Refresh\)/);
  assert.match(rootStates, /rootExposesLoadingEmptyErrorAndRetryThroughTheSharedHost/);
  assert.match(rootStates, /populatedRootKeepsTheConversationInsideTheStableListAnchor/);
  assert.match(androidRootStates, /conversationsRootExposesLoadingEmptyErrorAndRetry/);
  assert.match(androidRootStates, /assertEquals\(2, model\.refreshes\)/);
  assert.match(viewModel, /conversations = conversations\.filter \{ it\.isVisible \},[\s\S]{0,180}?loadError = null/);
  assert.match(viewModel, /restorePendingDeletedConversation\(\)[\s\S]*?copy\(error =/);
  for (const launcher of [web, android, ios]) {
    assert.match(launcher, /ConversationsScreenHost\(/);
  }
});

test("conversation root contract stays in mandatory fast suites", async () => {
  const packageJson = JSON.parse(await source("package.json"));
  for (const suite of ["test:ci-fast-contracts", "test:web-wave2-contracts"]) {
    assert.match(packageJson.scripts[suite], /scripts\/conversations-web-evidence-contract\.test\.mjs/);
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

test("Web conversation creation proves private reuse and exact group creation with reversible custody", async () => {
  const [runner, fixtures, candidateCard] = await Promise.all([
    source("scripts/chat-actions-notifications-web-evidence.mjs"),
    source("scripts/e2e-fixtures/chat-attachments.mjs"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationCandidateCardContent.kt"),
  ]);
  assert.match(runner, /--conversation-create-only/);
  assert.match(runner, /createTemporaryConversationCandidate\(\{ withDatabase, runId, displayNamePrefix: groupSearchQuery \}\)/);
  assert.match(runner, /conversation\.picker\.candidate\.action\.\$\{fixture\.candidate\.id\}/);
  assert.match(runner, /search\.fill\(fixture\.candidate\.phoneLocal/);
  assert.match(runner, /conversation_private_created_from_common_picker_and_exact_route_opened/);
  assert.match(runner, /conversation_private_reopened_from_picker_without_duplicate_thread/);
  assert.match(runner, /conversation\.picker\.groupTitle/);
  assert.match(
    runner,
    /const candidateSearch = await visibleAriaLocator\([\s\S]*?candidateSearch\.fill\(fixture\.groupSearchQuery[\s\S]*?for \(const \[candidateIndex, candidate\] of \[fixture\.candidate, fixture\.groupCandidate\]\.entries\(\)\)/,
  );
  assert.match(runner, /const groupSearchQuery = `QADATA Group \$\{runId\.slice\(0, 8\)\}`/);
  assert.match(fixtures, /displayNamePrefix = "QADATA Conversation"/);
  assert.match(
    runner,
    /const groupCandidateRowTags = \[fixture\.candidate, fixture\.groupCandidate\][\s\S]*?for \(const rowTag of groupCandidateRowTags\)[\s\S]*?const stableRow = await visibleAriaLocator/,
  );
  assert.match(runner, /clickLocatorFraction\(page, row, 0\.5, "conversation_group_create_candidate_not_clickable"\)/);
  assert.match(runner, /web-conversation-group-candidate-selected-/);
  assert.match(runner, /snapshotTemporaryGroupConversation\(/);
  assert.match(runner, /conversation_group_created_from_common_picker_with_exact_title_members_and_route/);
  assert.match(runner, /cleanupTemporaryGroupConversation\(/);
  assert.match(runner, /conversation_group_create_cleanup_verified_physical_residue_absent/);
  assert.match(runner, /secondSnapshot\.length !== 1/);
  assert.match(runner, /cleanupTemporaryConversationCandidate\(/);
  assert.match(runner, /cleanup_residue_detected:conversation_candidate_multiple_threads/);
  assert.match(runner, /conversation_create_cleanup_verified_physical_residue_absent/);
  assert.match(fixtures, /profile_low_id = least\(\$1::uuid, \$2::uuid\)/);
  assert.match(fixtures, /cleanup_residue_detected:conversation_candidate_thread_not_owned/);
  assert.match(fixtures, /chat_private_threads/);
  assert.match(fixtures, /t\.type = 'group'/);
  assert.match(fixtures, /conversation_group_thread_not_owned/);
  assert.match(candidateCard, /\.testTag\(tag\)\.semantics/);
  assert.match(candidateCard, /\.clickable\(enabled = !isOpening, role = Role\.Button, onClick = onOpen\)/);
  assert.doesNotMatch(candidateCard, /if \(!isSelectionMode\) \{\s*Button\(/);
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
  assert.match(uiTest, /label BEGINSWITH/);
  assert.match(uiTest, /"Contactos", "Contacts"/);
  assert.match(uiTest, /"John Appleseed"/);
  assert.match(uiTest, /simulatorContact\.coordinate\(withNormalizedOffset/);
  assert.match(uiTest, /nativeDone\.tap\(\)/);
  assert.match(uiTest, /nativePickerContact\.coordinate\(withNormalizedOffset/);
  assert.match(uiTest, /nativePickerDone\.tap\(\)/);
  assert.match(uiTest, /real ContactsUI multiselection must expose confirmation/);
  assert.match(uiTest, /real ContactsUI picker must follow private-contact access selection/);
  assert.match(uiTest, /reopen common picker after ContactsUI/);
  assert.match(coordinator, /conversations_picker_closed_without_backend_mutation/);
  assert.match(coordinator, /ios_conversations_real_contactsui_two_stage_selection_completed_and_common_picker_reopened/);
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

test("Android and iOS conversation creation prove private reuse and exact group creation", async () => {
  const [androidCoordinator, androidUi, iosCoordinator, iosRunner, iosUi] = await Promise.all([
    source("scripts/chat-actions-notifications-android-evidence.mjs"),
    source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
    source("scripts/chat-actions-notifications-ios-evidence.mjs"),
    source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
    source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  ]);

  for (const coordinator of [androidCoordinator, iosCoordinator]) {
    assert.match(coordinator, /--conversation-create-only/);
    assert.match(coordinator, /createTemporaryConversationCandidate\(\{ withDatabase, runId \}\)/);
    assert.match(coordinator, /snapshotTemporaryPrivateConversation\(/);
    assert.match(coordinator, /snapshotTemporaryGroupConversation\(/);
    assert.match(coordinator, /privateThreads\.length !== 1/);
    assert.match(coordinator, /cleanupTemporaryConversationCandidate\(/);
    assert.match(coordinator, /cleanupTemporaryGroupConversation\(/);
    assert.match(coordinator, /cleanup_verified_conversation_candidate_physical_residue_absent/);
  }
  assert.match(androidCoordinator, /runInstrumentationStage\("conversation-create"\)/);
  assert.match(androidUi, /repeat\(2\)/);
  assert.match(androidUi, /ConversationPickerCandidateActionTestTagPrefix \+ profileId/);
  assert.match(androidUi, /android-conversation-create-second/);
  assert.match(androidUi, /ConversationPickerGroupTitleTestTag/);
  assert.match(androidUi, /ConversationPickerConfirmTestTag/);
  assert.match(androidUi, /android-conversation-group-created/);

  for (const key of [
    "QUATA_IOS_CONVERSATION_CREATE_UI_E2E",
    "QUATA_IOS_CONVERSATION_CREATE_PROFILE_ID",
    "QUATA_IOS_CONVERSATION_CREATE_QUERY",
    "QUATA_IOS_CONVERSATION_GROUP_CREATE_PROFILE_ID",
    "QUATA_IOS_CONVERSATION_GROUP_CREATE_QUERY",
    "QUATA_IOS_CONVERSATION_GROUP_CREATE_TITLE",
  ]) {
    assert.match(iosCoordinator, new RegExp(key));
    assert.match(iosRunner, new RegExp(key));
    assert.match(iosUi, new RegExp(key));
  }
  assert.match(iosRunner, /testConversationCreateUsesSharedPickerAndReusesPrivateThread/);
  assert.match(iosUi, /for index in 0\.\.<2/);
  assert.match(iosUi, /conversation\.picker\.candidate\.action/);
  assert.match(iosUi, /XCTAssertEqual\(route, firstRoute/);
  assert.match(iosUi, /conversation\.picker\.groupTitle/);
  assert.match(iosUi, /conversation\.picker\.confirm/);
  assert.match(iosUi, /ios-conversation-group-created/);
});
