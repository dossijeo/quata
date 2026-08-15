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
  commonAttachmentPresentation,
  commonAudioPlayer,
  commonAudioPolicy,
  androidHost,
  webHost,
  iosHost,
  androidUiTest,
  iosUiTest,
  iosWrapper,
  androidRunner,
  webRunner,
  iosRunner,
  browserAudioPlayer,
  browserChatMedia,
  androidMediaViewer,
  attestationJson,
] = await Promise.all([
  source("package.json"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBrowserHostContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerAndActionsContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatAttachmentQuickPanelContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatPendingAttachmentOverlayContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatDocumentAttachmentContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatAttachmentPresentation.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatAudioAttachmentPlayerContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatConsecutiveAudioPolicy.kt"),
  source("app/src/main/java/com/quata/feature/chat/presentation/chat/AndroidChatProductScreen.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt"),
  source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  source("scripts/chat-actions-notifications-android-evidence.mjs"),
  source("scripts/chat-actions-notifications-web-evidence.mjs"),
  source("scripts/chat-actions-notifications-ios-evidence.mjs"),
  source("core/src/wasmJsMain/kotlin/com/quata/core/platform/BrowserAudioPlayerService.wasm.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/BrowserChatMediaContent.kt"),
  source("app/src/main/java/com/quata/core/ui/components/AttachmentMediaViewer.kt"),
  source("docs/candidate-attestations/chat-attachments-audio.json"),
]);

const attestation = JSON.parse(attestationJson);

test("CHAT-ATTACHMENTS/AUDIO has a dedicated fast contract in CI", () => {
  const scripts = JSON.parse(packageJson).scripts;
  assert.match(scripts["test:ci-fast-contracts"], /scripts\/chat-attachments-audio-evidence-contract\.test\.mjs/);
  assert.match(scripts["test:web-wave2-contracts"], /scripts\/chat-attachments-audio-evidence-contract\.test\.mjs/);
});

test("attachment picker, pending surface and attachment cards expose stable common anchors", () => {
  for (const [sourceText, anchors] of [
    [commonComposer, [
      ["ChatComposerCameraTestTag", "chat.composer.camera"],
      ["ChatComposerRecordAudioTestTag", "chat.composer.recordAudio"],
    ]],
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
    [commonAttachmentPresentation, [
      ["ChatMediaAttachmentTestTag", "chat.attachment.media"],
      ["ChatImageAttachmentContentDescription", "chat.attachment.media.image"],
      ["ChatVideoAttachmentContentDescription", "chat.attachment.media.video"],
    ]],
  ]) {
    for (const [constant, tag] of anchors) {
      assert.match(sourceText, new RegExp(`${constant} = "${tag.replaceAll(".", "\\.")}"`));
      if (constant.endsWith("TestTag")) {
        assert.match(sourceText, new RegExp(`testTag = ${constant}`));
      }
      if (constant === "ChatMediaAttachmentTestTag") {
        assert.match(sourceText, /contentDescription = when \(kind\)/);
        assert.match(sourceText, /ChatAttachmentKind\.Video -> ChatVideoAttachmentContentDescription/);
        assert.match(sourceText, /ChatAttachmentKind\.Image -> ChatImageAttachmentContentDescription/);
      }
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
  assert.match(commonAudioPlayer, /val toggleDescription = "\$playPauseDescription \$displayText"/);
  assert.match(commonAudioPlayer, /contentDescription = toggleDescription/);
  assert.match(commonAudioPlayer, /errorText/);
  assert.match(commonAudioPlayer, /if \(hasError\) errorText else displayText/);
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
  assert.match(commonHost, /LaunchedEffect\(activeAudioReference\)/);
  assert.doesNotMatch(commonHost, /LaunchedEffect\(activeAudioReference, audioPlayback\.isPlaying\)/);
  assert.match(commonHost, /val finished = didAudioPlaybackFinish\(previousPlayback, currentPlayback\)[\s\S]*if \(finished\)/);
  assert.doesNotMatch(commonHost, /val currentPlayback = audioPlayer\.state\(\)\s+audioPlayback = currentPlayback\s+if \(didAudioPlaybackFinish/);
  assert.match(commonAudioPolicy, /listOf\(currentIndex \+ 1, currentIndex - 1\)/);
});

test("inventory keeps CHAT-ATTACHMENTS and CHAT-AUDIO open until full scope evidence exists", () => {
  const attachments = inventory.split(/\r?\n/).find((line) => line.startsWith("| `CHAT-ATTACHMENTS` |"));
  const audio = inventory.split(/\r?\n/).find((line) => line.startsWith("| `CHAT-AUDIO` |"));
  const productShaPrefix = attestation.productSha.slice(0, 8);
  assert.ok(attachments, "CHAT-ATTACHMENTS row must exist");
  assert.ok(audio, "CHAT-AUDIO row must exist");
  assert.match(attachments, /Web\/Wasm/);
  assert.match(attachments, new RegExp(productShaPrefix));
  assert.match(attachments, new RegExp(attestation.evidence.web.report.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.match(attachments, /build-reports\/android\/chat-actions-notifications-evidence/);
  assert.match(attachments, /build-reports\/ios\/chat-attachments-audio-evidence/);
  assert.match(attachments, /scripts\/e2e-fixtures\/chat-attachments\.mjs/);
  assert.match(attachments, /selecci/);
  assert.match(attachments, /limpieza/);
  assert.match(audio, /Web\/Wasm/);
  assert.match(audio, new RegExp(productShaPrefix));
  assert.match(audio, new RegExp(attestation.evidence.web.report.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.match(audio, /audioPlaybackObserved\.state=playing/);
  assert.match(audio, /build-reports\/android\/chat-actions-notifications-evidence/);
  assert.match(audio, /build-reports\/ios\/chat-attachments-audio-evidence/);
  assert.match(audio, /grabaci/);
  assert.match(audio, /reproducci/);
  assert.equal(attestation.evidence.web.sha, attestation.productSha);
  assert.equal(attestation.evidence.android.sha, attestation.productSha);
  assert.equal(attestation.evidence.ios.sha, attestation.productSha);
  assert.doesNotMatch(attachments, /\*\*GO/);
  assert.doesNotMatch(audio, /\*\*GO/);
});

test("Android and iOS runners expose an opt-in attachments/audio evidence stage", () => {
  assert.match(androidUiTest, /val videoProbe = optionalArgument\("quataChatActionsVideoProbe"\)/);
  assert.match(androidUiTest, /"attachments-audio" -> listOf\(chatUrl, documentProbe, audioProbe, imageProbe, videoProbe\)/);
  assert.match(androidUiTest, /"attachments-audio" -> runAttachmentsAudioStage\(documentProbe\.orEmpty\(\), audioProbe\.orEmpty\(\), imageProbe\.orEmpty\(\), videoProbe\.orEmpty\(\)\)/);
  assert.match(androidUiTest, /ChatVideoAttachmentContentDescription/);
  assert.match(androidUiTest, /ChatImageAttachmentContentDescription/);
  assert.match(androidUiTest, /ChatDocumentAttachmentTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentPlayerTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentToggleTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentProgressTestTag/);
  assert.match(androidUiTest, /android-chat-attachment-video-viewer/);
  assert.match(androidUiTest, /android-chat-attachment-media-viewer/);
  assert.match(androidUiTest, /android-chat-attachment-document-visible/);
  assert.match(androidUiTest, /android-chat-audio-toggle-attempted/);

  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE/);
  assert.match(iosUiTest, /chat\.attachment\.media/);
  assert.match(iosUiTest, /chat\.attachment\.media\.video/);
  assert.match(iosUiTest, /chat\.attachment\.document/);
  assert.match(iosUiTest, /chat\.attachment\.audio\.player/);
  assert.match(iosUiTest, /chat\.attachment\.audio\.toggle/);
  assert.match(iosUiTest, /chat\.attachment\.audio\.progress/);
  assert.match(iosUiTest, /ios-chat-attachment-media-viewer/);
  assert.match(iosUiTest, /ios-chat-attachment-video-viewer/);
  assert.match(iosUiTest, /ios-chat-attachment-document-visible/);
  assert.match(iosUiTest, /ios-chat-audio-toggle-attempted/);
  assert.match(iosUiTest, /messageText\(imageProbe, in: app\)/);
  assert.match(iosUiTest, /messageText\(videoProbe, in: app\)/);
  assert.match(iosUiTest, /messageText\(documentProbe, in: app\)/);
  assert.match(iosUiTest, /messageText\(audioProbe, in: app\)/);
  assert.match(iosUiTest, /testAttachmentPickerFixtureUsesSharedComposerAnchors/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN/);
  assert.match(iosUiTest, /chat\.composer\.attach/);
  assert.match(iosUiTest, /chat\.attachment\.quickPanel/);
  assert.match(iosUiTest, /chat\.attachment\.pick\.file/);
  assert.match(iosUiTest, /chat\.attachment\.pick\.gallery/);
  assert.match(iosUiTest, /chat\.composer\.camera/);
  assert.match(iosUiTest, /chat\.attachment\.pending/);
  assert.match(iosUiTest, /chat\.composer\.send/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E'\] = attachments_audio/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE'\] = attachment_document/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE'\] = attachment_audio/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE'\] = attachment_image/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE'\] = attachment_video/);
  assert.match(iosWrapper, /testAttachmentsAndAudioExposeSharedAnchors/);
  assert.match(iosWrapper, /attachments-audio\.log/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_NAME/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_MIME/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_MARKER/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E'\] = attachment_picker/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN'\] = picker_opt_in/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE'\] = picker_source/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH'\] = picker_path/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_NAME'\] = picker_name/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_MIME'\] = picker_mime/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_MARKER'\] = picker_marker/);
  assert.match(iosWrapper, /testAttachmentPickerFixtureUsesSharedComposerAnchors/);
  assert.match(iosWrapper, /attachment-picker\.log/);
  const attachmentsMode = iosWrapper.slice(
    iosWrapper.indexOf('if [[ "$QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E" == "1" ]]'),
    iosWrapper.indexOf('elif [[ "$QUATA_IOS_CHAT_PROFILE_ONLY" == "1"'),
  );
  assert.doesNotMatch(attachmentsMode, /:\s*"\$\{QUATA_IOS_CHAT_E2E_MESSAGE_ID:\?/);
  assert.doesNotMatch(attachmentsMode, /:\s*"\$\{QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE:\?/);
  const pickerMode = iosWrapper.slice(
    iosWrapper.indexOf('if [[ "$QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E" == "1" ]]'),
    iosWrapper.indexOf('elif [[ "$QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E" == "1" ]]'),
  );
  assert.doesNotMatch(pickerMode, /:\s*"\$\{QUATA_IOS_CHAT_E2E_MESSAGE_ID:\?/);
  assert.doesNotMatch(pickerMode, /:\s*"\$\{QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE:\?/);
});

test("real Chat evidence runners seed reversible document/audio attachments", async () => {
  for (const runner of [androidRunner, webRunner, iosRunner]) {
    assert.match(runner, /attachmentsAudioOnly/);
    assert.match(runner, /--attachments-audio-only/);
    assert.match(runner, /createChatAttachmentMessage/);
    assert.match(runner, /seedChatAttachmentFixture/);
    assert.match(runner, /cleanupRegistry: createCleanupRegistry\(\)/);
    assert.match(runner, /cleanup: state\.cleanupRegistry/);
    assert.match(runner, /cleanupRegistry\.cleanupStorageObjects/);
    assert.doesNotMatch(runner, /function validWavFixture\(\)/);
    assert.doesNotMatch(runner, /attachmentStoragePaths:\s*\[\]/);
    assert.doesNotMatch(runner, /state\.attachmentStoragePaths\.push/);
    assert.doesNotMatch(runner, /function attachmentStorageFixtures\(state\)/);
    assert.match(runner, /image_document_and_(audio|consecutive_audio)_attachment_messages_seeded/);
    assert.match(runner, /document_and_audio_shared_attachment_chrome_verified|ios_xctest_document_and_audio_attachment_chrome_verified/);
  }
  const sharedFixtures = await source("scripts/e2e-fixtures/chat-attachments.mjs");
  assert.match(sharedFixtures, /chatAttachmentsBucket/);
  assert.match(sharedFixtures, /storage_delete_verified_absent/);
  assert.match(sharedFixtures, /quata_chat_register_attachment/);
  assert.match(sharedFixtures, /quata_chat_send_message/);
  assert.match(sharedFixtures, /function chatAttachmentFixtureMedia\(kind\)/);
  assert.match(sharedFixtures, /kind === "image"/);
  assert.match(sharedFixtures, /mimeType: "image\/png"/);
  assert.match(sharedFixtures, /kind === "video"/);
  assert.match(sharedFixtures, /mimeType: "video\/mp4"/);
  assert.match(sharedFixtures, /validMp4Fixture/);
  assert.match(androidRunner, /runInstrumentationStage\("attachments-audio"\)/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E=\$\{attachmentsAudioOnly \? "1" : "0"\}/);
  assert.match(webRunner, /verifyAttachmentsAudioWeb/);
  assert.match(webRunner, /function sentMessageId\(payload\)/);
  assert.match(webRunner, /messageId: sentMessageId/);
  assert.doesNotMatch(webRunner, /consumeBrowserRuntimeFaultsForSyntheticAudio/);
  const attachmentsBranch = webRunner.slice(
    webRunner.indexOf("if (options.attachmentsAudioOnly)"),
    webRunner.indexOf("if (state.peerMessage && state.b.accessToken)"),
  );
  assert.match(attachmentsBranch, /faults\.length = 0;\s*await openAuthenticatedChatRoute/);
  assert.match(attachmentsBranch, /report\.diagnostics = \{ \.\.\.\(report\.diagnostics \?\? \{\}\), browserRuntimeFaults: faults\.slice\(\) \};\s*throw new Error\("browser_runtime_fault"\)/);
  assert.doesNotMatch(attachmentsBranch, /await openAuthenticatedChatRoute[\s\S]*faults\.length = 0;\s*await verifyAttachmentsAudioWeb/);
  assert.match(webRunner, /function redactBrowserRuntimeFault\(fault\)/);
  assert.match(webRunner, /messageSha256/);
  assert.match(webRunner, /urlOrigin/);
  assert.doesNotMatch(webRunner, /text: entry\.text\(\)\.slice/);
  assert.match(webRunner, /\.\.\.\(report\.diagnostics \?\? \{\}\),\s*visibleNativeControls/);
  assert.match(webRunner, /function isFullEvidenceMode\(options\)/);
  assert.match(webRunner, /isFullEvidenceMode\(options\) && state\.peerMessage/);
  assert.doesNotMatch(webRunner, /!\s*options\.attachmentsAudioOnly && state\.peerMessage/);
  assert.match(webRunner, /async function waitAudioPlaybackObserved\(page, timeout = 10_000\)/);
  assert.match(webRunner, /async function clickLocatorCenter\(page, locator, error\)/);
  assert.match(webRunner, /clickLocatorCenter\(page, play, "audio_attachment_toggle_not_clickable"\)/);
  assert.match(webRunner, /report\.evidence\.audioPlaybackObserved = playback/);
  assert.match(webRunner, /if \(playback\.state !== "playing"\) throw new Error\(`audio_playback_not_playing:\$\{playback\.state\}`\)/);
  assert.match(webRunner, /audio_playback_state_not_observed/);
  assert.match(webRunner, /chat\.attachment\.media/);
  assert.match(webRunner, /chat\.attachment\.media\.video/);
  assert.match(webRunner, /chat_attachment_media_viewer_back_missing_after_native_click/);
  assert.match(webRunner, /web-chat-attachment-video-viewer/);
  assert.match(webRunner, /web-chat-attachment-media-viewer/);
  assert.match(webRunner, /web-chat-audio-toggle-attempted/);
  assert.match(webRunner, /await page\.mouse\.wheel\(0, 520\)/);
  assert.match(webRunner, /nextAudio: await createChatAttachmentMessage/);
  assert.match(webRunner, /"audio", "-next"/);
  assert.match(webRunner, /next_audio_attachment_message/);
  assert.match(webRunner, /waitConsecutiveAudioPlaybackObserved/);
  assert.match(webRunner, /consecutive_audio_playback_state_not_observed/);
  assert.match(webRunner, /next_audio_attachment_toggle_not_visible/);
  assert.match(webRunner, /web-chat-audio-next-player-visible/);
  assert.match(webRunner, /nextAudioMessageId/);
  assert.match(androidMediaViewer, /ChatAudioAttachmentPlayerContent\(/);
  assert.match(androidMediaViewer, /errorText = attachment\.name/);
});

test("Web, Android and iOS attachment picker evidence modes exercise native picker boundaries through common UI", () => {
  assert.match(webRunner, /--attachment-picker-only/);
  assert.match(webRunner, /page\.waitForEvent\("filechooser"/);
  assert.match(webRunner, /chat\\.composer\\.attach/);
  assert.match(webRunner, /chat\\.attachment\\.quickPanel/);
  assert.match(webRunner, /chat\\.attachment\\.pick\\.file/);
  assert.match(webRunner, /chat\\.attachment\\.pick\\.gallery/);
  assert.match(webRunner, /chat\\.composer\\.camera/);
  assert.match(webRunner, /chat\\.attachment\\.pending/);
  assert.match(webRunner, /messageAttachments\(message\)/);
  assert.match(webRunner, /trackStorageObject/);

  assert.match(androidRunner, /--attachment-picker-only/);
  assert.match(androidRunner, /runInstrumentationStage\("attachment-picker"\)/);
  assert.match(androidRunner, /quataChatActionsAttachmentPickerSource/);
  assert.match(androidRunner, /quataChatActionsAttachmentPickerName/);
  assert.match(androidRunner, /quataChatActionsAttachmentPickerMarker/);
  assert.match(androidRunner, /messageAttachments\(pickerMessage\)/);
  assert.match(androidRunner, /trackStorageObject/);
  assert.match(androidUiTest, /"attachment-picker" -> runAttachmentPickerStage/);
  assert.match(androidUiTest, /I_ACCEPT_ANDROID_CHAT_ATTACHMENT_PICKER_FIXTURE/);
  assert.match(androidUiTest, /ChatComposerAttachTestTag/);
  assert.match(androidUiTest, /ChatAttachmentQuickPanelTestTag/);
  assert.match(androidUiTest, /ChatAttachmentPickFileTestTag/);
  assert.match(androidUiTest, /ChatAttachmentPickGalleryTestTag/);
  assert.match(androidUiTest, /ChatComposerCameraTestTag/);
  assert.match(androidUiTest, /ChatPendingAttachmentOverlayTestTag/);
  assert.match(androidHost, /AndroidChatEvidenceFilePicker/);
  assert.match(androidHost, /I_ACCEPT_ANDROID_CHAT_ATTACHMENT_PICKER_FIXTURE/);

  assert.match(iosRunner, /--attachment-picker-only/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E/);
  assert.match(iosRunner, /I_ACCEPT_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_MARKER/);
  assert.match(iosRunner, /messageAttachments\(message\)/);
  assert.match(iosRunner, /trackStorageObject/);
  assert.match(iosHost, /IosChatEvidenceFilePicker/);
  assert.match(iosHost, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN/);
  assert.match(iosHost, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE/);
});

test("Web audio player loads remote attachments through local Blob URLs under COEP", () => {
  assert.match(browserAudioPlayer, /AbortController/);
  assert.match(browserAudioPlayer, /controller\.abort\(\)/);
  assert.match(browserAudioPlayer, /globalThis\.fetch\(source, \{ credentials: 'omit', cache: 'no-store', \.\.\.\(controller \? \{ signal: controller\.signal \} : \{\}\) \}\)/);
  assert.match(browserAudioPlayer, /globalThis\.URL\.createObjectURL\(blob\)/);
  assert.match(browserAudioPlayer, /web_audio_load_timeout/);
  assert.match(browserAudioPlayer, /completed = true/);
  assert.match(browserAudioPlayer, /if \(completed\) return null/);
  assert.match(browserAudioPlayer, /if \(completed \|\| !playableSource\) return/);
  assert.match(browserAudioPlayer, /if \(completed\) return;\s*cleanup\(\)/);
  assert.match(browserAudioPlayer, /element\.src = playableSource/);
  assert.match(browserAudioPlayer, /element\.ended && durationMillis > 0 \? durationMillis/);
  assert.match(browserAudioPlayer, /revokeObjectURL/);
  assert.doesNotMatch(browserAudioPlayer, /element\.src = source/);
});

test("Web chat video media loads remote attachments through local Blob URLs under COEP", () => {
  assert.match(browserChatMedia, /resolveBrowserChatVideoSource/);
  assert.match(browserChatMedia, /globalThis\.fetch\(source, \{ credentials: 'omit', cache: 'no-store'/);
  assert.match(browserChatMedia, /const blob = await response\.blob\(\)/);
  assert.match(browserChatMedia, /globalThis\.URL\.createObjectURL\(blob\)/);
  assert.match(browserChatMedia, /revokeBrowserChatVideoSource/);
  assert.match(browserChatMedia, /if \(video\.src != videoSource\) video\.src = videoSource/);
  assert.doesNotMatch(browserChatMedia, /if \(video\.src != source\) video\.src = source/);
});
