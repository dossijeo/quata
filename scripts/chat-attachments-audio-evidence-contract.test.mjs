import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(path, "utf8");

const [
  packageJson,
  inventory,
  commonHost,
  commonConversationDetail,
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
  iosAudioHost,
  iosEvidenceAudioHost,
  attestationJson,
  pickerAttestationJson,
] = await Promise.all([
  source("package.json"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBrowserHostContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatConversationDetailContent.kt"),
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
  source("core/src/iosMain/kotlin/com/quata/core/platform/IosAvFoundationAudioHost.kt"),
  source("core/src/iosMain/kotlin/com/quata/core/platform/IosEvidenceAudioRecorderHost.kt"),
  source("docs/candidate-attestations/chat-attachments-audio.json"),
  source("docs/candidate-attestations/chat-attachment-picker.json"),
]);

const attestation = JSON.parse(attestationJson);
const pickerAttestation = JSON.parse(pickerAttestationJson);

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
    [commonComposer, [
      ["ChatAttachmentErrorTestTag", "chat.attachment.error"],
    ]],
    [commonDocumentAttachment, [
      ["ChatDocumentAttachmentTestTag", "chat.attachment.document"],
      ["ChatDocumentAttachmentOpenTestTag", "chat.attachment.document.open"],
      ["ChatDocumentAttachmentDownloadTestTag", "chat.attachment.document.download"],
      ["ChatDocumentAttachmentShareTestTag", "chat.attachment.document.share"],
    ]],
    [commonAttachmentPresentation, [
      ["ChatMediaAttachmentTestTag", "chat.attachment.media"],
      ["ChatImageAttachmentContentDescription", "chat.attachment.media.image"],
      ["ChatVideoAttachmentContentDescription", "chat.attachment.media.video"],
    ]],
  ]) {
    for (const [constant, tag] of anchors) {
      assert.match(sourceText, new RegExp(`${constant} = "${tag.replaceAll(".", "\\.")}"`));
      if (constant.endsWith("TestTag") && constant !== "ChatMediaAttachmentTestTag") {
        assert.match(sourceText, new RegExp(`testTag = ${constant}`));
      }
      if (constant === "ChatMediaAttachmentTestTag") {
        assert.match(sourceText, /chatMediaAttachmentSemanticAnchor/);
        assert.match(sourceText, /testTag = semanticAnchor/);
        assert.match(sourceText, /contentDescription = semanticAnchor/);
        assert.match(sourceText, /role = Role\.Button/);
        assert.match(sourceText, /onClick\(label = semanticAnchor\)/);
        assert.match(sourceText, /ChatAttachmentKind\.Video -> ChatVideoAttachmentContentDescription/);
        assert.match(sourceText, /ChatAttachmentKind\.Image -> ChatImageAttachmentContentDescription/);
      }
    }
  }
});

test("media attachments own the primary tap instead of the whole message bubble", () => {
  assert.match(commonConversationDetail, /val mediaAttachmentOwnsTap = message\.mediaAttachmentKind\(\)\?\.let/);
  assert.match(commonConversationDetail, /it == ChatAttachmentKind\.Image \|\| it == ChatAttachmentKind\.Video/);
  assert.match(commonConversationDetail, /private fun Message\.mediaAttachmentKind\(\): ChatAttachmentKind\?/);
  assert.match(commonConversationDetail, /chatAttachmentKind\(PlatformFile\(reference, attachmentName, attachmentMimeType\)\)/);
  assert.match(
    commonConversationDetail,
    /if \(mediaAttachmentOwnsTap\) bubbleSemantics else bubbleSemantics\.clickable\(onClick = onClick\)/,
  );
});

test("iOS media attachment evidence replays the resolved semantic element tap", () => {
  assert.match(iosUiTest, /private func tapResolvedMedia\(_ media: XCUIElement\) \{\s*media\.tap\(\)\s*\}/);
  assert.doesNotMatch(
    iosUiTest,
    /private func tapResolvedMedia\(_ media: XCUIElement\) \{\s*media\.coordinate\(withNormalizedOffset:/,
  );
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
  assert.match(commonAudioPlayer, /val boundedProgress = progress\.coerceIn\(0f, 1f\)/);
  assert.match(commonAudioPlayer, /val progressPercent = \(boundedProgress \* 100f\)\.toInt\(\)\.coerceIn\(0, 100\)/);
  assert.match(commonAudioPlayer, /contentDescription = "\$ChatAudioAttachmentProgressTestTag \$displayText \$progressPercent%"/);
});

test("chat composer exposes stable common audio recording anchors", () => {
  for (const [constant, tag] of [
    ["ChatComposerRecordAudioTestTag", "chat.composer.recordAudio"],
    ["ChatComposerRecordingTestTag", "chat.composer.recording"],
    ["ChatComposerRecordingStopTestTag", "chat.composer.recording.stop"],
    ["ChatComposerRecordingCancelTestTag", "chat.composer.recording.cancel"],
    ["ChatComposerRecordingErrorTestTag", "chat.composer.recording.error"],
  ]) {
    assert.match(commonComposer, new RegExp(`${constant} = "${tag.replaceAll(".", "\\.")}"`));
    assert.match(commonComposer, new RegExp(`testTag = ${constant}`));
  }
  assert.match(commonComposer, /onRecordAudio/);
  assert.match(commonComposer, /onStopRecording/);
  assert.match(commonComposer, /onCancelRecording/);
  assert.match(commonComposer, /recordingError/);
});

test("iOS AVFoundation recorder honors pregranted microphone permission before requesting", () => {
  assert.match(iosAudioHost, /audioSession\.recordPermission/);
  assert.match(iosAudioHost, /AVAudioSessionRecordPermissionGranted/);
  assert.match(iosAudioHost, /AVAudioSessionRecordPermissionDenied/);
  assert.match(iosAudioHost, /requestRecordPermission/);
  assert.match(iosAudioHost, /setCategory\(AVAudioSessionCategoryPlayAndRecord/);
  assert.doesNotMatch(iosAudioHost, /setActive\(/);
});

test("iOS UI evidence uses an opt-in deterministic recorder instead of simulator microphone hardware", () => {
  assert.match(iosEvidenceAudioHost, /class IosEvidenceAudioRecorderHost : IosAudioRecorderHost/);
  assert.match(iosEvidenceAudioHost, /QUATA_IOS_AUDIO_RECORDER_E2E_FAKE/);
  assert.match(iosEvidenceAudioHost, /quata_audio_e2e_/);
  assert.match(iosEvidenceAudioHost, /durationMillis = 1_250L/);
  assert.match(iosUiTest, /testAttachmentsAndAudioExposeSharedAnchors\(\)[\s\S]*app\.launchEnvironment\["QUATA_IOS_AUDIO_RECORDER_E2E_FAKE"\] = "1"/);
  assert.doesNotMatch(iosAudioHost, /QUATA_IOS_AUDIO_RECORDER_E2E_FAKE/);
});

test("Android, Web and iOS attach native adapters to the same common chat product host", () => {
  for (const host of [androidHost, webHost, iosHost]) {
    assert.match(host, /ChatProductHostContent\(/);
    assert.match(host, /audioPlayer\s*=/);
    assert.match(host, /audioRecorder\s*=/);
    assert.match(host, /filePicker\s*=/);
    assert.match(host, /onOpenAttachment\s*=/);
    assert.match(host, /onDownloadAttachment\s*=/);
    assert.match(host, /onShareAttachment\s*=/);
    assert.match(host, /mediaSlots\s*=\s*(ChatMediaPlatformSlots|iosChatMediaPlatformSlots)\(/);
  }
  assert.match(androidHost, /openAttachmentWithDocumentReaderOrChooser/);
  assert.match(androidHost, /saveChatAttachmentToDownloads/);
  assert.match(androidHost, /shareService\.share\(/);
  assert.match(webHost, /openWebAttachment\(documentOpener\)/);
  assert.match(webHost, /downloadWebAttachment/);
  assert.match(webHost, /shareWebAttachment\(shareService\)/);
  assert.match(webHost, /materializeWebAttachment/);
  assert.match(webHost, /SharePayload\(title = .*files = listOf\(local\)\)/);
  assert.match(webHost, /revokeWebAttachmentObjectUrl/);
  assert.match(iosHost, /onOpenAttachment: \(PlatformFile\) -> Unit/);
  assert.match(iosHost, /shareDownloadedAttachment/);
  assert.match(iosHost, /attachmentDownloader\.download/);
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
  assert.match(commonHost, /ChatDocumentAttachmentDownloadTestTag|onDownloadAttachment/);
  assert.match(commonHost, /ChatDocumentAttachmentShareTestTag|onShareAttachment/);
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
  const pickerShaPrefix = pickerAttestation.productSha.slice(0, 8);
  assert.match(attachments, new RegExp(`chat-attachment-picker-evidence-${pickerShaPrefix}-\\{document,gallery,camera\\}\\.json`));
  assert.match(attachments, /docs\/candidate-attestations\/chat-attachment-picker\.json/);
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
  for (const platform of ["web", "android", "ios"]) {
    for (const sourceName of ["Document", "Gallery", "Camera"]) {
      const source = sourceName.toLowerCase();
      const evidence = pickerAttestation.evidence[`${platform}${sourceName}`];
      assert.equal(evidence.status, "passed");
      assert.equal(evidence.sha, pickerAttestation.productSha);
      assert.equal(evidence.cleanup.verified, true);
      assert.equal(evidence.cleanup.physicalResidue, 0);
      assert.match(evidence.report, new RegExp(`build-reports/${platform}/chat-attachment-picker-evidence-${pickerShaPrefix}-${source}\\.json`));
    }
  }
  assert.doesNotMatch(attachments, /\*\*GO/);
  assert.doesNotMatch(audio, /\*\*GO/);
});

test("Android and iOS runners expose an opt-in attachments/audio evidence stage", () => {
  assert.match(androidUiTest, /val videoProbe = optionalArgument\("quataChatActionsVideoProbe"\)/);
  assert.match(androidUiTest, /"attachments-audio" -> listOf\(chatUrl, documentProbe, audioProbe, imageProbe, videoProbe, audioRecordingMarker\)/);
  assert.match(androidUiTest, /"attachments-audio" -> runAttachmentsAudioStage\(documentProbe\.orEmpty\(\), audioProbe\.orEmpty\(\), imageProbe\.orEmpty\(\), videoProbe\.orEmpty\(\), audioRecordingMarker\.orEmpty\(\)\)/);
  assert.match(androidUiTest, /ChatVideoAttachmentContentDescription/);
  assert.match(androidUiTest, /ChatImageAttachmentContentDescription/);
  assert.match(androidUiTest, /ChatDocumentAttachmentTestTag/);
  assert.match(androidUiTest, /verifyAndroidAudioRecordingComposer/);
  assert.match(androidUiTest, /Manifest\.permission\.RECORD_AUDIO/);
  assert.match(androidUiTest, /ChatComposerRecordAudioTestTag/);
  assert.match(androidUiTest, /chat\.composer\.recording\.stop/);
  assert.match(androidUiTest, /ChatPendingAttachmentOverlayTestTag/);
  assert.match(androidUiTest, /ChatPendingAttachmentClearTestTag/);
  assert.match(androidUiTest, /android-chat-audio-recording-active/);
  assert.match(androidUiTest, /android-chat-audio-recording-pending-attachment/);
  assert.match(androidUiTest, /ChatDocumentAttachmentOpenTestTag/);
  assert.match(androidUiTest, /ChatDocumentAttachmentDownloadTestTag/);
  assert.match(androidUiTest, /ChatDocumentAttachmentShareTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentPlayerTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentToggleTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentProgressTestTag/);
  assert.match(androidUiTest, /android-chat-attachment-video-viewer/);
  assert.match(androidUiTest, /android-chat-attachment-media-viewer/);
  assert.match(androidUiTest, /android-chat-attachment-document-visible/);
  assert.match(androidRunner, /android-chat-audio-recording-active\.png/);
  assert.match(androidRunner, /android-chat-audio-recording-pending-attachment\.png/);
  assert.match(androidRunner, /android-chat-audio-recording-ready-to-send\.png/);
  assert.match(androidRunner, /android-chat-audio-recording-sent\.png/);
  assert.match(androidRunner, /android-chat-audio-seek-attempted\.png/);
  assert.match(androidUiTest, /android-chat-audio-toggle-attempted/);
  assert.match(androidUiTest, /quataChatActionsAudioRecordingMarker/);
  assert.match(androidUiTest, /android-chat-audio-recording-ready-to-send/);
  assert.match(androidUiTest, /android-chat-audio-recording-sent/);
  assert.match(androidUiTest, /android-chat-audio-seek-attempted/);
  assert.match(androidRunner, /android_audio_recording_sent_by_shared_composer_and_verified_by_rpc/);

  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID/);
  assert.match(iosUiTest, /chat\.attachment\.media/);
  assert.match(iosUiTest, /chat\.attachment\.media\.video/);
  assert.match(iosUiTest, /chat\.attachment\.document/);
  assert.match(iosUiTest, /chat\.attachment\.document\.open/);
  assert.match(iosUiTest, /chat\.attachment\.document\.download/);
  assert.match(iosUiTest, /chat\.attachment\.document\.share/);
  assert.match(iosUiTest, /chat\.attachment\.audio\.player/);
  assert.match(iosUiTest, /chat\.attachment\.audio\.toggle/);
  assert.match(iosUiTest, /chat\.attachment\.audio\.progress/);
  assert.match(iosUiTest, /verifyAudioRecordingComposer\(marker: audioRecordingMarker, in: app\)/);
  assert.match(iosUiTest, /chat\.composer\.record/);
  assert.match(iosUiTest, /chat\.composer\.recording/);
  assert.match(iosUiTest, /chat\.composer\.recording\.stop/);
  assert.match(iosUiTest, /ios-chat-audio-recording-active/);
  assert.match(iosUiTest, /ios-chat-audio-recording-pending-attachment/);
  assert.match(iosUiTest, /ios-chat-audio-recording-ready-to-send/);
  assert.match(iosUiTest, /ios-chat-audio-recording-sent/);
  assert.match(iosUiTest, /dismissKeyboardIfVisible\(in: app\)/);
  assert.match(iosUiTest, /ios-chat-audio-seek-attempted/);
  assert.match(iosUiTest, /chat\.attachment\.pending/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_AUDIO_RECORDING_MARKER/);
  assert.match(iosUiTest, /chat\.composer\.send/);
  assert.match(iosUiTest, /ios-chat-attachment-media-viewer/);
  assert.match(iosUiTest, /ios-chat-attachment-video-viewer/);
  assert.match(iosUiTest, /ios-chat-attachment-document-visible/);
  assert.match(iosUiTest, /ios-chat-audio-toggle-attempted/);
  assert.match(iosUiTest, /Set\(\[documentProbe, audioProbe, imageProbe, videoProbe\]\)\.count/);
  assert.match(iosUiTest, /app\.terminate\(\)[\s\S]*?openDeepLink\("quata:\/\/egquata\.com\/#chat-/);
  assert.match(iosUiTest, /openChatMediaAttachment\([\s\S]*identifier: "chat\.attachment\.media\.video"[\s\S]*messageId: videoMessageId[\s\S]*markerProbe: videoProbe/);
  assert.match(iosUiTest, /messageWithId\(messageId, containing: markerProbe, in: app\)/);
  assert.match(iosUiTest, /guard makeChatAnchorVisible\(identifier: "chat\.attachment\.audio\.player"/);
  assert.match(iosUiTest, /testAttachmentPickerFixtureUsesSharedComposerAnchors/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_OUTCOME/);
  assert.match(iosUiTest, /chat\.composer\.attach/);
  assert.match(iosUiTest, /chat\.attachment\.quickPanel/);
  assert.match(iosUiTest, /chat\.attachment\.pick\.file/);
  assert.match(iosUiTest, /chat\.attachment\.pick\.gallery/);
  assert.match(iosUiTest, /chat\.composer\.camera/);
  assert.match(iosUiTest, /chat\.attachment\.error/);
  assert.match(iosUiTest, /chat\.attachment\.pending/);
  assert.match(iosUiTest, /chat\.composer\.send/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E/);
  assert.match(iosWrapper, /simctl privacy "\$QUATA_IOS_SIMULATOR_UDID" grant microphone com\.quata\.ios/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID'\] = attachment_image_message/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID'\] = attachment_video_message/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_AUDIO_RECORDING_MARKER/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E'\] = attachments_audio/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE'\] = attachment_document/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE'\] = attachment_audio/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE'\] = attachment_image/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE'\] = attachment_video/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_AUDIO_RECORDING_MARKER'\] = audio_recording_marker/);
  assert.match(iosWrapper, /testAttachmentsAndAudioExposeSharedAnchors/);
  assert.match(iosWrapper, /attachments-audio\.log/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_OUTCOME/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_REASON/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_NAME/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_MIME/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_MARKER/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E'\] = attachment_picker/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN'\] = picker_opt_in/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE'\] = picker_source/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_OUTCOME'\] = picker_outcome/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_REASON'\] = picker_reason/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH'\] = picker_path/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_NAME'\] = picker_name/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_MIME'\] = picker_mime/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_PICKER_MARKER'\] = picker_marker/);
  assert.match(iosWrapper, /testAttachmentPickerFixtureUsesSharedComposerAnchors/);
  assert.match(iosWrapper, /attachment-picker\.log/);
  assert.match(androidUiTest, /quataChatActionsAttachmentPickerOutcome/);
  assert.match(androidUiTest, /ChatAttachmentErrorTestTag/);
  assert.match(androidUiTest, /android-chat-attachment-picker-\$outcome-\$source/);
  assert.match(androidRunner, /--attachment-picker-outcome/);
  assert.match(androidRunner, /quataChatActionsAttachmentPickerOutcome/);
  assert.match(androidRunner, /pendingCreated: false/);
  assert.match(webRunner, /--attachment-picker-outcome/);
  assert.match(webRunner, /__quataChatAttachmentPickerE2E/);
  assert.match(webRunner, /attachment_picker_\$\{outcome\}_error_anchor_missing/);
  assert.match(webRunner, /pendingCreated: false/);
  assert.match(iosRunner, /--attachment-picker-outcome/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_PICKER_OUTCOME/);
  assert.match(iosRunner, /pendingCreated: false/);
  assert.match(iosRunner, /QUATA_IOS_REMOTE_JAVA_HOME/);
  assert.match(iosRunner, /--remote-java-home/);
  assert.match(iosRunner, /export JAVA_HOME=\$\{shellQuote\(options\.remoteJavaHome\)\}/);
  assert.match(iosRunner, /export PATH="\$JAVA_HOME\/bin:\$PATH"/);
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
  assert.match(iosRunner, /storage_cleanup_attempted_after_logical_cleanup_failure/);
  assert.match(iosRunner, /logical_cleanup_residue_resolved_by_verified_hard_cleanup/);
  assert.match(webRunner, /verifyAttachmentsAudioWeb/);
  assert.match(webRunner, /verifyDocumentAttachmentActionsWeb/);
  assert.match(webRunner, /acceptDownloads: true/);
  assert.match(webRunner, /__quataSharePayloads/);
  assert.match(webRunner, /chat\\.attachment\\.document\\.download/);
  assert.match(webRunner, /chat\\.attachment\\.document\\.share/);
  assert.match(webRunner, /web_chat_document_attachment_download_and_share_actions_verified/);
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
  assert.match(webRunner, /verifyWebAudioRecordingComposer/);
  assert.match(webRunner, /visibleWebSemanticAnchor/);
  assert.match(webRunner, /chat\.composer\.record/);
  assert.match(webRunner, /Grabar audio/);
  assert.match(webRunner, /chat\.composer\.recording\.stop/);
  assert.match(webRunner, /Detener grabaci/);
  assert.match(webRunner, /Detener y adjuntar/);
  assert.match(webRunner, /Quitar adjunto/);
  assert.match(webRunner, /webAudioRecordingAnchorResolution/);
  assert.doesNotMatch(webRunner, /audio_recording_start_anchor_not_clickable[\s\S]*?page\.mouse\.click\(382/);
  assert.match(webRunner, /web_audio_recording_composer_start_stop_and_sent/);
  assert.match(webRunner, /web_audio_recording_sent_by_shared_composer_and_verified_by_rpc/);
  assert.match(webRunner, /audioRecordingSent/);
  assert.match(webRunner, /seekAudioProgressWeb/);
  assert.match(webRunner, /audioSeekObserved/);
  assert.match(webRunner, /--use-fake-device-for-media-stream/);
  assert.match(webRunner, /grantPermissions\(\["microphone"\]/);
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
