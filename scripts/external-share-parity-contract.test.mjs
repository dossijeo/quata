import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

const [
  commonHost,
  commonInbox,
  pickerHost,
  androidHost,
  androidParser,
  webHost,
  webContract,
  webWorker,
  iosHost,
  iosInbox,
  iosShareQueue,
  iosShareViewController,
] = await Promise.all([
  source("feature/externalshare/src/commonMain/kotlin/com/quata/feature/externalshare/ExternalShareDestinationHostContent.kt"),
  source("feature/externalshare/src/commonMain/kotlin/com/quata/feature/externalshare/ExternalShareInboxContract.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationCandidatePickerDialogContent.kt"),
  source("app/src/main/java/com/quata/feature/externalshare/ShareToQuataDialog.kt"),
  source("app/src/main/java/com/quata/feature/externalshare/ExternalSharePayload.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebExternalShareHost.kt"),
  source("web/src/commonMain/kotlin/com/quata/web/WebIncomingShareTargetContract.kt"),
  source("web/src/wasmJsMain/resources/quata-sw.js"),
  source("feature/externalshare/src/iosMain/kotlin/com/quata/feature/externalshare/QuataExternalShareViewController.kt"),
  source("feature/externalshare/src/iosMain/kotlin/com/quata/feature/externalshare/IosExternalShareInbox.kt"),
  source("iosApp/iosShareQueue/ShareQueue.swift"),
  source("iosApp/iosShareExtension/ShareViewController.swift"),
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
  assert.match(commonHost, /dismissEnabled = !state\.isSending/);
  assert.match(commonHost, /panelHost\(dismissEnabled\)/);
  assert.match(commonHost, /\.heightIn\(max = 180\.dp\)/);
  assert.match(commonHost, /\.verticalScroll\(rememberScrollState\(\)\)/);
  assert.match(pickerHost, /dismissEnabled: Boolean = true/);
  assert.match(pickerHost, /CompactIconButton\(onClick = onDismiss, enabled = dismissEnabled/);
  assert.match(androidHost, /QuataStandardFloatingPanel\(onDismiss = onDismiss, dismissEnabled = dismissEnabled/);
  assert.match(webHost, /QuataFloatingPanelContent\(onDismiss = \{/);
  assert.match(webHost, /dismissEnabled = dismissEnabled/);
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
  assert.match(iosHost, /val documentOpener: DocumentOpenService/);
  assert.match(iosHost, /repository = dependencies\.repository/);
  assert.match(iosHost, /dependencies\.documentOpener\.open/);
  assert.match(iosHost, /PlatformFile\(/);
  assert.match(iosHost, /viewModelFactory = \{ _, _ -> dependencies\.viewModel \}/);
  assert.doesNotMatch(iosHost, /UIApplication\.sharedApplication\.openURL/);
  assert.match(iosInbox, /repository = chatRepository/);
  assert.match(iosInbox, /documentOpener = documentOpener/);
  assert.match(iosInbox, /claim\.cleanup\(\)\s*[\r\n]+\s*onDismiss\(\)/);
});

test("external share file limits remain aligned across Android Web iOS and common inbox", () => {
  assert.match(commonInbox, /const val MaxExternalShareFiles = 10/);
  assert.match(androidParser, /const val MAX_SHARED_FILES = 10/);
  assert.match(webContract, /const val maxFiles: Int = 10/);
  assert.match(webWorker, /const MAX_SHARED_FILES = 10;/);
  assert.match(webWorker, /const MAX_SHARED_FILE_BYTES = 25 \* 1024 \* 1024;/);
  assert.match(iosShareQueue, /static let maximumFiles = 10/);
  assert.match(iosShareQueue, /static let maximumFileBytes: Int64 = 25 \* 1024 \* 1024/);
  assert.match(iosShareQueue, /static let maximumTotalBytes: Int64 = maximumFileBytes \* Int64\(maximumFiles\)/);
});

test("iOS text files are preserved as attachments while URL shares stay textual", () => {
  assert.match(iosShareViewController, /hasItemConformingToTypeIdentifier\(UTType\.url\.identifier\)[\s\S]*textParts\.append\(url\.absoluteString\)[\s\S]*continue/);
  assert.match(iosShareViewController, /type\.conforms\(to: \.plainText\)[\s\S]*provider\.loadFile\(for: type\)[\s\S]*attachments\.append\(\.init\(sourceURL: source/);
  assert.match(iosShareViewController, /else \{\s*textParts\.append\(text\)\s*\}/);
});
