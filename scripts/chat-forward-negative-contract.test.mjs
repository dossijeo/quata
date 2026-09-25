import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => await readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("shared forward failure keeps picker and selection for an exact retry", async () => {
  const [viewModel, picker] = await Promise.all([
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerAndActionsContent.kt"),
  ]);
  assert.match(viewModel, /repository\.forwardMessage\(message, conversationIds\)[\s\S]*?onFailure \{[\s\S]*?isConversationActionInProgress = false,[\s\S]*?error = text\(ChatText\.Forward\)/);
  const sendForward = viewModel.slice(
    viewModel.indexOf("private fun sendForward()"),
    viewModel.indexOf("companion object", viewModel.indexOf("private fun sendForward()")),
  );
  const failure = sendForward.slice(sendForward.lastIndexOf(".onFailure"));
  assert.doesNotMatch(failure, /isForwardDialogOpen\s*=\s*false|selectedForwardProfileIds\s*=\s*emptyList/);
  assert.match(picker, /enabled = state\.selectedForwardProfileIds\.isNotEmpty\(\) && !state\.isConversationActionInProgress/);
  assert.match(picker, /candidate\.profileId in state\.selectedForwardProfileIds/);
  assert.match(picker, /state\.error\?\.let \{ error ->[\s\S]*?testTag = ChatMutationErrorTestTag/);
});

test("platform hooks are opt-in, one-shot and fail before the forward RPC", async () => {
  const [android, web, ios] = await Promise.all([
    source("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatRepository.kt"),
    source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/IosChatRuntimeBootstrap.kt"),
  ]);
  assert.match(android, /override suspend fun forwardMessage[\s\S]*?consumeForwardFailureForEvidence\(\)[\s\S]*?chat_forward_e2e_forced_failure[\s\S]*?remote\.forwardChatMessage/);
  assert.match(android, /I_ACCEPT_ANDROID_CHAT_FORWARD_FAILURE_FIXTURE/);
  assert.match(android, /remove\(CHAT_FORWARD_FAILURE_KEY\)/);
  assert.match(web, /functionName == "quata_chat_forward_message" && consumeWebChatForwardFailure\(\)/);
  assert.match(web, /host !== 'localhost' && host !== '127\.0\.0\.1'/);
  assert.match(web, /__QUATA_CHAT_FORWARD_FORCE_FAILURE__ = false/);
  assert.match(ios, /forwardFailurePending && functionName == "quata_chat_forward_message"[\s\S]*?forwardFailurePending = false/);
  assert.match(ios, /I_ACCEPT_IOS_CHAT_FORWARD_FAILURE_FIXTURE/);
});

test("focal runners preserve selection, retry once and require one backend copy", async () => {
  const [web, android, ios, swift, shell] = await Promise.all([
    source("scripts/chat-actions-notifications-web-evidence.mjs"),
    source("scripts/chat-actions-notifications-android-evidence.mjs"),
    source("scripts/chat-actions-notifications-ios-evidence.mjs"),
    source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
    source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  ]);
  for (const runner of [web, android, ios]) {
    assert.match(runner, /forward-negative-only/);
    assert.match(runner, /forward_negative_expected_one_copy_after_retry/);
    assert.match(runner, /same_selected_destination_retried_successfully_with_one_forwarded_copy/);
  }
  assert.match(web, /forward_negative_failure_created_copy/);
  assert.match(web, /forward_negative_failure_dropped_selected_destination/);
  assert.match(android, /"forward-negative"/);
  assert.match(swift, /testForwardFailureKeepsSelectionAndRetryCreatesOneCopy/);
  assert.match(swift, /The chosen destination must remain selected after failure/);
  assert.match(shell, /QUATA_IOS_CHAT_FORWARD_NEGATIVE_UI_E2E/);
  assert.match(shell, /forward-negative\.log/);
});
