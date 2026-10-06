import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("FLOW-DEEP-LINKS retry uses the shared failure surface and exact recovered message", async () => {
  const [host, repository, viewModel] = await Promise.all([
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatScreenHost.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/DocumentRetryEvidenceChatRepository.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
  ]);

  assert.match(host, /testTag = "chat\.read\.failure"/);
  assert.match(host, /testTag = "chat\.read\.retry"/);
  assert.match(host, /model\.retryMessageLoading\(\)/);
  assert.match(repository, /failFirstMessageObservation: Boolean = false/);
  assert.match(repository, /deep_link_retry_fixture_initial_failure/);
  assert.match(repository, /interface ChatMessageObservationRetryFixture/);
  assert.match(viewModel, /allowMessageObservationRetry\(\)/);
  assert.match(repository, /DocumentRetryEvidenceMessageId = "local-document-retry-message"/);
});

test("native retry fixtures remain fail-closed behind platform-specific exact opt-ins", async () => {
  const [androidFixture, androidTest, androidRunner, iosRuntime, iosApp, iosTest] = await Promise.all([
    source("app/src/main/java/com/quata/feature/chat/presentation/chat/AndroidDocumentRetryEvidenceFixture.kt"),
    source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
    source("scripts/chat-actions-notifications-android-evidence.mjs"),
    source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/IosChatRuntimeBootstrap.kt"),
    source("iosApp/iosApp/QuataIosApp.swift"),
    source("iosApp/iosAppUITests/QuataIosExternalChatLinkUITests.swift"),
  ]);

  assert.match(androidFixture, /I_ACCEPT_ANDROID_DEEP_LINK_RETRY_LOCAL_FIXTURE/);
  assert.match(androidTest, /"deep-link-retry-local"/);
  assert.match(androidTest, /FLOW-DEEP-LINKS-ANDROID-RETRY-001/);
  assert.match(androidTest, /onNodeWithTag\("chat\.read\.retry", useUnmergedTree = true\)\.performClick\(\)/);
  assert.match(androidRunner, /--deep-link-retry-local-only/);
  assert.match(androidRunner, /quataDocumentRetryLocalOptIn", "I_ACCEPT_ANDROID_DOCUMENT_RETRY_LOCAL_FIXTURE/);
  assert.match(androidRunner, /quataDeepLinkRetryLocalOptIn", "I_ACCEPT_ANDROID_DEEP_LINK_RETRY_LOCAL_FIXTURE/);
  assert.match(androidRunner, /android_deep_link_retry_product_report_invalid/);
  assert.match(androidRunner, /android-deep-link-read-retry-recovered\.png/);
  assert.match(iosRuntime, /I_ACCEPT_IOS_DEEP_LINK_RETRY_LOCAL_FIXTURE/);
  assert.match(iosApp, /case "deep-link-retry-local"/);
  assert.match(iosTest, /testLocalDeepLinkReadFailureRetryRecoversExactConversation/);
  assert.match(iosTest, /retry\.tap\(\)/);
});

test("Web retry is localhost-only and the browser runner clicks the real Compose control", async () => {
  const [fixture, runner] = await Promise.all([
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatE2eFixture.kt"),
    source("scripts/chat-actions-notifications-web-evidence.mjs"),
  ]);

  assert.match(fixture, /hostname === '127\.0\.0\.1' \|\| location\?\.hostname === 'localhost'/);
  assert.match(fixture, /quata-chat-deep-link-retry-e2e/);
  assert.match(fixture, /webDeepLinkRetryEvidenceReferenceOrNull\(\)/);
  assert.match(fixture, /failFirstMessageObservation = true/);
  assert.match(runner, /--deep-link-retry-local-only/);
  assert.match(runner, /getByRole\("button", \{ name: \/\^\(Reintentar mensajes\|Retry messages/);
  assert.match(runner, /await retry\.evaluate\(\(element\) => element\.click\(\)\)/);
  assert.match(runner, /deep_link_retry_back_failed/);
  assert.match(runner, /current === "feed" \|\| current === "chats"/);
  assert.match(runner, /FLOW-DEEP-LINKS-WEB-RETRY-001/);
  assert.match(runner, /backend: "not_used"/);
});
