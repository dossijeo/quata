import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

const [
  commonHost,
  androidHost,
  webHost,
  iosHost,
  iosInbox,
] = await Promise.all([
  source("feature/externalshare/src/commonMain/kotlin/com/quata/feature/externalshare/ExternalShareDestinationHostContent.kt"),
  source("app/src/main/java/com/quata/feature/externalshare/ShareToQuataDialog.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebExternalShareHost.kt"),
  source("feature/externalshare/src/iosMain/kotlin/com/quata/feature/externalshare/QuataExternalShareViewController.kt"),
  source("feature/externalshare/src/iosMain/kotlin/com/quata/feature/externalshare/IosExternalShareInbox.kt"),
]);

test("external share exposes stable common anchors for all platform runners", () => {
  for (const tag of [
    "external-share.root",
    "external-share.search",
    "external-share.confirm",
    "external-share.dismiss",
    "external-share.payload.text",
    "external-share.attachment.",
    "external-share.candidate.",
    "external-share.candidate.action.",
  ]) {
    assert.match(commonHost, new RegExp(tag.replaceAll(".", "\\.")));
  }
  assert.match(commonHost, /rootTestTag = ExternalShareRootTestTag/);
  assert.match(commonHost, /confirmTestTag = ExternalShareConfirmTestTag/);
  assert.match(commonHost, /candidateActionTestTagPrefix = ExternalShareCandidateActionTestTagPrefix/);
});

test("Android Web and iOS consume the shared destination host", () => {
  for (const [name, sourceText] of [
    ["Android", androidHost],
    ["Web", webHost],
    ["iOS", iosHost],
  ]) {
    assert.match(sourceText, /ExternalShareDestinationHostContent\(/, `${name} must use the common external-share destination host`);
    assert.match(sourceText, /ExternalShareAttachmentRowContent\(/, `${name} must use the common attachment row`);
  }
});

test("Web and iOS do not keep parallel destination picker UI", () => {
  assert.doesNotMatch(webHost, /ConversationCandidatePickerDialogContent\(/);
  assert.doesNotMatch(webHost, /ConversationsUiState\(/);
  assert.doesNotMatch(iosHost, /LazyColumn\(/);
  assert.doesNotMatch(iosHost, /OutlinedTextField\(/);
  assert.doesNotMatch(iosHost, /ExternalShareSendingStateContent\(/);
});

test("iOS App Group claim still injects the real repository and cleans up on dismissal", () => {
  assert.match(iosHost, /val repository: ChatRepository/);
  assert.match(iosHost, /repository = dependencies\.repository/);
  assert.match(iosHost, /viewModelFactory = \{ _, _ -> dependencies\.viewModel \}/);
  assert.doesNotMatch(iosHost, /onOpenAttachment: \(ExternalShareAttachment\) -> Unit = \{\}/);
  assert.match(iosHost, /UIApplication\.sharedApplication\.openURL/);
  assert.match(iosInbox, /repository = chatRepository/);
  assert.match(iosInbox, /claim\.cleanup\(\)\s*[\r\n]+\s*onDismiss\(\)/);
});
