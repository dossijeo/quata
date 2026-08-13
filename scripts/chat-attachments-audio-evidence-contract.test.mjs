import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(path, "utf8");

const [
  packageJson,
  inventory,
  commonHost,
  commonComposer,
  commonQuickPanel,
  commonPendingAttachment,
  commonDocumentAttachment,
  commonAudioPlayer,
  androidHost,
  webHost,
  iosHost,
  androidUiTest,
  iosUiTest,
  iosWrapper,
  androidRunner,
  webRunner,
  iosRunner,
] = await Promise.all([
  source("package.json"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBrowserHostContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerAndActionsContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatAttachmentQuickPanelContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatPendingAttachmentOverlayContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatDocumentAttachmentContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatAudioAttachmentPlayerContent.kt"),
  source("app/src/main/java/com/quata/feature/chat/presentation/chat/AndroidChatProductScreen.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt"),
  source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  source("scripts/chat-actions-notifications-android-evidence.mjs"),
  source("scripts/chat-actions-notifications-web-evidence.mjs"),
  source("scripts/chat-actions-notifications-ios-evidence.mjs"),
]);

test("CHAT-ATTACHMENTS/AUDIO has a dedicated fast contract in CI", () => {
  const scripts = JSON.parse(packageJson).scripts;
  assert.match(scripts["test:ci-fast-contracts"], /scripts\/chat-attachments-audio-evidence-contract\.test\.mjs/);
  assert.match(scripts["test:web-wave2-contracts"], /scripts\/chat-attachments-audio-evidence-contract\.test\.mjs/);
});

test("attachment picker, pending surface and document card expose stable common anchors", () => {
  for (const [sourceText, anchors] of [
    [commonQuickPanel, [
      ["ChatAttachmentQuickPanelTestTag", "chat.attachment.quickPanel"],
      ["ChatAttachmentPickFileTestTag", "chat.attachment.pick.file"],
      ["ChatAttachmentPickGalleryTestTag", "chat.attachment.pick.gallery"],
    ]],
    [commonPendingAttachment, [
      ["ChatPendingAttachmentOverlayTestTag", "chat.attachment.pending"],
      ["ChatPendingAttachmentClearTestTag", "chat.attachment.pending.clear"],
    ]],
    [commonDocumentAttachment, [
      ["ChatDocumentAttachmentTestTag", "chat.attachment.document"],
    ]],
  ]) {
    for (const [constant, tag] of anchors) {
      assert.match(sourceText, new RegExp(`${constant} = "${tag.replaceAll(".", "\\.")}"`));
      assert.match(sourceText, new RegExp(`testTag = ${constant}`));
    }
  }
});

test("audio attachment player exposes stable common playback anchors", () => {
  for (const [constant, tag] of [
    ["ChatAudioAttachmentPlayerTestTag", "chat.attachment.audio.player"],
    ["ChatAudioAttachmentToggleTestTag", "chat.attachment.audio.toggle"],
    ["ChatAudioAttachmentProgressTestTag", "chat.attachment.audio.progress"],
  ]) {
    assert.match(commonAudioPlayer, new RegExp(`${constant} = "${tag.replaceAll(".", "\\.")}"`));
    assert.match(commonAudioPlayer, new RegExp(`testTag = ${constant}`));
  }
  assert.match(commonAudioPlayer, /playPauseDescription/);
  assert.match(commonAudioPlayer, /onTogglePlayback/);
  assert.match(commonAudioPlayer, /onSeekToFraction/);
});

test("Android, Web and iOS attach native adapters to the same common chat product host", () => {
  for (const host of [androidHost, webHost, iosHost]) {
    assert.match(host, /ChatProductHostContent\(/);
    assert.match(host, /audioPlayer\s*=/);
    assert.match(host, /audioRecorder\s*=/);
    assert.match(host, /filePicker\s*=/);
    assert.match(host, /onOpenAttachment\s*=/);
    assert.match(host, /mediaSlots\s*=\s*(ChatMediaPlatformSlots|iosChatMediaPlatformSlots)\(/);
  }
  assert.match(androidHost, /openAttachmentWithDocumentReaderOrChooser/);
  assert.match(webHost, /openWebAttachment\(documentOpener\)/);
  assert.match(iosHost, /onOpenAttachment: \(PlatformFile\) -> Unit/);
});

test("common chat product routes attachments and audio without platform-specific product forks", () => {
  assert.match(commonComposer, /ChatAttachmentQuickPanelContent\(/);
  assert.match(commonComposer, /ChatPendingAttachmentOverlayContent\(/);
  assert.match(commonHost, /ChatUiEvent\.AttachmentSelected/);
  assert.match(commonHost, /audioRecorder\.start/);
  assert.match(commonHost, /audioRecorder\.stop/);
  assert.match(commonHost, /ChatBrowserAttachmentContent\(/);
  assert.match(commonHost, /ChatMediaAttachmentContent\(/);
  assert.match(commonHost, /ChatDocumentAttachmentContent\(/);
  assert.match(commonHost, /ChatAudioAttachmentPlayerContent\(/);
  assert.match(commonHost, /audioPlayer\.load/);
  assert.match(commonHost, /audioPlayer\.seekTo/);
});

test("inventory keeps CHAT-ATTACHMENTS and CHAT-AUDIO open until full scope evidence exists", () => {
  const attachments = inventory.split(/\r?\n/).find((line) => line.startsWith("| `CHAT-ATTACHMENTS` |"));
  const audio = inventory.split(/\r?\n/).find((line) => line.startsWith("| `CHAT-AUDIO` |"));
  assert.ok(attachments, "CHAT-ATTACHMENTS row must exist");
  assert.ok(audio, "CHAT-AUDIO row must exist");
  assert.match(attachments, /Web\/Wasm, Android e iOS/);
  assert.match(attachments, /build-reports\/web\/chat-attachments-audio-evidence\.json/);
  assert.match(attachments, /build-reports\/android\/chat-actions-notifications-evidence\.json/);
  assert.match(attachments, /build-reports\/ios\/chat-attachments-audio-evidence\.json/);
  assert.match(attachments, /selecci/);
  assert.match(attachments, /limpieza/);
  assert.match(audio, /Web\/Wasm, Android e iOS/);
  assert.match(audio, /build-reports\/web\/chat-attachments-audio-evidence\.json/);
  assert.match(audio, /build-reports\/android\/chat-actions-notifications-evidence\.json/);
  assert.match(audio, /build-reports\/ios\/chat-attachments-audio-evidence\.json/);
  assert.match(audio, /grabaci/);
  assert.match(audio, /reproducci/);
  assert.doesNotMatch(attachments, /\*\*GO/);
  assert.doesNotMatch(audio, /\*\*GO/);
});

test("Android and iOS runners expose an opt-in attachments/audio evidence stage", () => {
  assert.match(androidUiTest, /"attachments-audio" -> listOf\(chatUrl, documentProbe, audioProbe\)/);
  assert.match(androidUiTest, /"attachments-audio" -> runAttachmentsAudioStage\(documentProbe\.orEmpty\(\), audioProbe\.orEmpty\(\)\)/);
  assert.match(androidUiTest, /ChatDocumentAttachmentTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentPlayerTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentToggleTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentProgressTestTag/);
  assert.match(androidUiTest, /android-chat-attachment-document-visible/);
  assert.match(androidUiTest, /android-chat-audio-toggle-attempted/);

  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE/);
  assert.match(iosUiTest, /chat\.attachment\.document/);
  assert.match(iosUiTest, /chat\.attachment\.audio\.player/);
  assert.match(iosUiTest, /chat\.attachment\.audio\.toggle/);
  assert.match(iosUiTest, /chat\.attachment\.audio\.progress/);
  assert.match(iosUiTest, /ios-chat-attachment-document-visible/);
  assert.match(iosUiTest, /ios-chat-audio-toggle-attempted/);
  assert.match(iosUiTest, /messageText\(documentProbe, in: app\)/);
  assert.match(iosUiTest, /messageText\(audioProbe, in: app\)/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E'\] = attachments_audio/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE'\] = attachment_document/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE'\] = attachment_audio/);
  assert.match(iosWrapper, /testAttachmentsAndAudioExposeSharedAnchors/);
  assert.match(iosWrapper, /attachments-audio\.log/);
  const attachmentsMode = iosWrapper.slice(
    iosWrapper.indexOf('if [[ "$QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E" == "1" ]]'),
    iosWrapper.indexOf('elif [[ "$QUATA_IOS_CHAT_PROFILE_ONLY" == "1"'),
  );
  assert.doesNotMatch(attachmentsMode, /:\s*"\$\{QUATA_IOS_CHAT_E2E_MESSAGE_ID:\?/);
  assert.doesNotMatch(attachmentsMode, /:\s*"\$\{QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE:\?/);
});

test("real Chat evidence runners seed reversible document/audio attachments", () => {
  for (const runner of [androidRunner, webRunner, iosRunner]) {
    assert.match(runner, /attachmentsAudioOnly/);
    assert.match(runner, /--attachments-audio-only/);
    assert.match(runner, /createChatAttachmentMessage/);
    assert.match(runner, /quata_chat_register_attachment/);
    assert.match(runner, /quata_chat_send_message/);
    assert.match(runner, /const extension = isAudio \? "wav" : "txt"/);
    assert.match(runner, /const mimeType = isAudio \? "audio\/wav" : "text\/plain"/);
    assert.match(runner, /function validWavFixture\(\)/);
    assert.match(runner, /verifyStorageObjectAbsent\(chatAttachmentsBucket, fixture\.storagePath\)/);
    assert.match(runner, /document_and_audio_attachment_messages_seeded/);
    assert.match(runner, /document_and_audio_shared_attachment_chrome_verified|ios_xctest_document_and_audio_attachment_chrome_verified/);
    assert.match(runner, /chatAttachmentsBucket/);
    assert.match(runner, /storage_delete_verified_absent/);
  }
  assert.match(androidRunner, /runInstrumentationStage\("attachments-audio"\)/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E=\$\{attachmentsAudioOnly \? "1" : "0"\}/);
  assert.match(webRunner, /verifyAttachmentsAudioWeb/);
  assert.match(webRunner, /function sentMessageId\(payload\)/);
  assert.match(webRunner, /const msg = sentMessageId\(await rpc\(config, session, "quata_chat_send_message"/);
  assert.doesNotMatch(webRunner, /consumeBrowserRuntimeFaultsForSyntheticAudio/);
  assert.match(webRunner, /if \(faults\.length\) throw new Error\("browser_runtime_fault"\)/);
  assert.match(webRunner, /!options\.attachmentsAudioOnly && state\.peerMessage/);
  assert.match(webRunner, /web-chat-audio-toggle-attempted/);
});
