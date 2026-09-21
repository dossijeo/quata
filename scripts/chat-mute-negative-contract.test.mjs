import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("CHAT-NOTIFICATIONS mute failure stays opt-in and proves exact rollback on every host", async () => {
  const [viewModel, commonTest, androidRepository, androidFault, androidTest, webRepository, webRunner, iosBootstrap, iosTest, iosRunner, iosWrapper] = await Promise.all([
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
    source("feature/chat/src/commonTest/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModelComposerActionsTest.kt"),
    source("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt"),
    source("app/src/main/java/com/quata/feature/chat/data/ChatMuteEvidenceFaults.kt"),
    source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatRepository.kt"),
    source("scripts/chat-actions-notifications-web-evidence.mjs"),
    source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/IosChatRuntimeBootstrap.kt"),
    source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
    source("scripts/chat-actions-notifications-ios-evidence.mjs"),
    source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  ]);

  assert.match(viewModel, /conversation = previousConversation[\s\S]*error = text\(ChatText\.Update\)/);
  assert.match(commonTest, /failedMuteRestoresTheExactConversationAndSurfacesTheCommonError/);
  assert.match(commonTest, /assertEquals\(before, model\.uiState\.value\.conversation\)/);

  assert.match(androidFault, /AtomicBoolean/);
  assert.match(androidRepository, /BuildConfig\.DEBUG && ChatMuteEvidenceFaults\.consumeFailure\(\)/);
  assert.match(androidTest, /runMenuMuteNegativeStage/);
  assert.match(androidTest, /check\(waitForText\("Silenciar conversaci", "Mute conversation", timeoutMillis = 10_000\) != null\)[\s\S]*chat_mute_negative_initial_action_not_found/);
  assert.match(androidTest, /compose\.waitUntil\(10_000\) \{ nodeWithTagVisible\(ChatAttachmentErrorTestTag\) \}/);
  assert.match(androidTest, /No se pudo actualizar la conversación[\s\S]*Could not update the conversation[\s\S]*chat_mute_negative_error_not_exact/);
  assert.match(androidTest, /check\(waitForText\("Silenciar conversaci", "Mute conversation", timeoutMillis = 10_000\) != null\)[\s\S]*chat_mute_negative_restored_action_not_found/);
  assert.match(androidTest, /android-chat-mute-negative-restored/);

  assert.match(webRepository, /\['localhost', '127\.0\.0\.1'\][\s\S]*__QUATA_CHAT_MUTE_FORCE_FAILURE__/);
  assert.match(webRunner, /--mute-negative-only/);
  assert.match(webRunner, /mute_negative_backend_state_changed/);
  assert.match(webRunner, /delete globalThis\.__QUATA_CHAT_MUTE_FORCE_FAILURE__/);

  assert.match(iosBootstrap, /QUATA_IOS_CHAT_MUTE_FORCE_FAILURE/);
  assert.match(iosBootstrap, /functionName == "quata_chat_set_muted"/);
  assert.match(iosTest, /testOptionsMenuMuteFailureRestoresTheUnmutedSurface/);
  assert.match(iosTest, /ios-chat-mute-negative-restored/);
  assert.match(iosRunner, /--mute-negative-only/);
  assert.match(iosRunner, /mute_negative_backend_state_changed/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_MUTE_NEGATIVE_UI_E2E/);
});
