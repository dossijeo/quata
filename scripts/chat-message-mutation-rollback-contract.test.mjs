import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("message mutation failures roll common UI state back without changing product semantics", async () => {
  const [viewModel, host, chrome] = await Promise.all([
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatScreenHost.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerAndActionsContent.kt"),
  ]);

  assert.match(viewModel, /optimisticEditedMessages = optimisticEditedMessages - editingMessage\.id[\s\S]*restoreEditDraftIfComposerIsEmpty\(editingMessage, text\)[\s\S]*publishMessages/);
  assert.match(viewModel, /repository\.deleteMessage\(message\.id\)[\s\S]*onSuccess[\s\S]*selectedMessageId = null[\s\S]*onFailure[\s\S]*ChatText\.DeleteMessage/);
  assert.match(chrome, /ChatMutationErrorTestTag = "chat\.mutation\.error"/);
  assert.match(host, /testTag = ChatMutationErrorTestTag[\s\S]*contentDescription = "\$ChatMutationErrorTestTag \$error"/);
});

test("platform fault injection is explicit, bounded and consumed before backend mutation", async () => {
  const [android, web, ios] = await Promise.all([
    source("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatRepository.kt"),
    source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/IosChatRuntimeBootstrap.kt"),
  ]);

  assert.match(android, /I_ACCEPT_ANDROID_CHAT_MESSAGE_MUTATION_FAILURE_FIXTURE/);
  assert.match(android, /consumeMessageMutationFailureForEvidence\("edit"\)[\s\S]*remote\.editChatMessage/);
  assert.match(android, /consumeMessageMutationFailureForEvidence\("delete"\)[\s\S]*remote\.deleteChatMessages/);
  assert.match(android, /remove\(CHAT_MUTATION_FAILURE_KEY\)/);

  assert.match(web, /host !== 'localhost' && host !== '127\.0\.0\.1'/);
  assert.match(web, /I_ACCEPT_WEB_CHAT_MESSAGE_MUTATION_FAILURE_FIXTURE/);
  assert.match(web, /__QUATA_CHAT_MUTATION_FORCE_FAILURE__ = null/);
  assert.match(web, /consumeWebChatMutationFailure\(functionName\)[\s\S]*rpcClient\.post/);

  assert.match(ios, /I_ACCEPT_IOS_CHAT_MESSAGE_MUTATION_FAILURE_FIXTURE/);
  assert.match(ios, /mutationFailure = null[\s\S]*chat_message_mutation_e2e_forced_failure/);
  assert.match(ios, /"quata_chat_edit_message" -> "edit"/);
  assert.match(ios, /"quata_chat_delete_messages" -> "delete"/);
});

test("focal runners select the real shared UI rollback paths", async () => {
  const [web, android, androidUi, ios, iosUi] = await Promise.all([
    source("scripts/chat-actions-notifications-web-evidence.mjs"),
    source("scripts/chat-actions-notifications-android-evidence.mjs"),
    source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
    source("scripts/chat-actions-notifications-ios-evidence.mjs"),
    source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  ]);

  assert.match(web, /--message-mutation-rollback-only/);
  assert.match(web, /messageMutationRollbackOnly[\s\S]*openAuthenticatedChatRoute\(page, server\.origin[\s\S]*composerBridge: true/);
  assert.match(web, /assertMessageMutationRollback[\s\S]*forced_edit_failure_restored_original_message_and_edit_draft/);
  assert.match(web, /visibleAriaLocator\(page, \[\/Editar\|Edit\/i\], 500\)[\s\S]*openMessageActions\(page, ownMarker/);
  assert.match(web, /fillComposerAndSubmitOnce\(page, failedEditMarker\)/);
  assert.match(web, /async function fillComposerAndSubmitOnce[\s\S]*waitWebComposerBridgeText[\s\S]*invokeWebComposerBridge\(page, "send"/);
  assert.match(web, /chat\\\.mutation\\\.error/);
  assert.match(web, /No se pudo enviar el mensaje/);
  assert.doesNotMatch(web.match(/async function assertMessageMutationRollback[\s\S]*?\n}/)?.[0] ?? "", /fillComposerAndSend/);
  assert.match(web, /waitForComposerValue\(page, failedEditMarker/);
  assert.match(web, /input\.inputValue\(\)\.then\(\(value\) => value === expected\)/);
  assert.match(android, /--message-mutation-rollback-only[\s\S]*message-mutation-rollback/);
  assert.match(androidUi, /runMessageMutationRollbackStage[\s\S]*messageMutation\.failure/);
  assert.match(androidUi, /waitForMessageMutationFailureConsumption\(\)[\s\S]*ChatMutationErrorTestTag/);
  assert.match(android, /own_message_unchanged_after_forced_edit_and_delete_failures/);
  assert.match(ios, /--message-mutation-rollback-only[\s\S]*testMessageMutationFailuresRestoreSharedUiState/);
  assert.match(ios, /ios_xctest_message_mutation_failures_restore_shared_ui_state/);
  assert.match(iosUi, /testMessageMutationFailuresRestoreSharedUiState[\s\S]*chat\.mutation\.error/);
  assert.match(iosUi, /waitForComposerValue\(equalTo: failedEditMarker/);
});
