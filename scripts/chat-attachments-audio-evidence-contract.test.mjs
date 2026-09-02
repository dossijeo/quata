import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
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
  commonAudioController,
  commonAudioPolicy,
  androidHost,
  appContainer,
  androidDocumentReaderHost,
  androidNativeChatScreen,
  webHost,
  iosAttachmentPreviewService,
  iosDocumentOpenService,
  iosHost,
  iosMediaContent,
  iosMediaBridge,
  androidUiTest,
  iosUiTest,
  iosWrapper,
  androidRunner,
  webRunner,
  iosRunner,
  browserAudioPlayer,
  browserChatMedia,
  androidMediaViewer,
  fullscreenMediaOverlay,
  androidPlatformServices,
  iosAudioPlayerHost,
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
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatAudioPlaybackController.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatConsecutiveAudioPolicy.kt"),
  source("app/src/main/java/com/quata/feature/chat/presentation/chat/AndroidChatProductScreen.kt"),
  source("app/src/main/java/com/quata/core/di/AppContainer.kt"),
  source("document-reader/src/main/java/com/quata/documentreader/AndroidDocumentOpenService.kt"),
  source("app/src/main/java/com/quata/feature/chat/presentation/chat/ChatScreen.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatAttachmentPreviewService.kt"),
  source("core/src/iosMain/kotlin/com/quata/core/platform/IosDocumentOpenService.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/IosChatMediaContent.kt"),
  source("iosApp/iosApp/IosChatMediaBridge.swift"),
  source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  source("scripts/chat-actions-notifications-android-evidence.mjs"),
  source("scripts/chat-actions-notifications-web-evidence.mjs"),
  source("scripts/chat-actions-notifications-ios-evidence.mjs"),
  source("core/src/wasmJsMain/kotlin/com/quata/core/platform/BrowserAudioPlayerService.wasm.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/BrowserChatMediaContent.kt"),
  source("app/src/main/java/com/quata/core/ui/components/AttachmentMediaViewer.kt"),
  source("designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataFullscreenMediaOverlayContent.kt"),
  source("core/src/androidMain/kotlin/com/quata/core/platform/AndroidPlatformServices.kt"),
  source("core/src/iosMain/kotlin/com/quata/core/platform/IosAvFoundationAudioPlayerHost.kt"),
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

test("iOS media attachment evidence only taps a freshly visible chat viewport frame", () => {
  assert.match(iosUiTest, /waitForFocusedMessageVisible\(videoMessageId/);
  assert.match(iosUiTest, /waitForFocusedMessageVisible\(imageMessageId/);
  assert.match(iosUiTest, /waitForFocusedMessageVisible\(audioMessageId/);
  assert.match(iosUiTest, /tapVisibleFrameCenter\(media, in: app\)/);
  assert.match(iosUiTest, /element\.frame\.intersection\(chatMessageViewport\(in: app\)\)/);
  const openResolvedMedia = iosUiTest.slice(
    iosUiTest.indexOf("private func openResolvedMedia"),
    iosUiTest.indexOf("    @discardableResult", iosUiTest.indexOf("private func openResolvedMedia")),
  );
  assert.match(openResolvedMedia, /guard tapVisibleFrameCenter\(media, in: app\) else/);
  assert.match(openResolvedMedia, /guard isElementVisibleInChatViewport\(media, in: app\) else/);
  assert.doesNotMatch(openResolvedMedia, /tapResolvedMedia/);
  assert.doesNotMatch(openResolvedMedia, /coordinate\(withNormalizedOffset: CGVector\(dx: 0\.5, dy: 0\.35\)\)\.tap\(\)/);
  assert.doesNotMatch(openResolvedMedia, /app\.coordinate\(withNormalizedOffset:/);
  const openChatMediaAttachment = iosUiTest.slice(
    iosUiTest.indexOf("private func openChatMediaAttachment"),
    iosUiTest.indexOf("private func openResolvedMedia"),
  );
  assert.match(openChatMediaAttachment, /func mediaElement\(\) -> XCUIElement\?/);
  assert.match(openChatMediaAttachment, /guard let media = mediaElement\(\) else/);
  assert.match(openChatMediaAttachment, /ios-\\\(slug\(context\)\)-media-anchor-missing/);
  assert.match(openChatMediaAttachment, /return openResolvedMedia\(media, context: context, in: app, failOnMiss: true\)/);
  assert.doesNotMatch(openChatMediaAttachment, /if openResolvedMedia\(media, context: context, in: app\)/);
});

test("Android attachments/audio evidence precompiles debug package and avoids fullscreen coordinate fallbacks", () => {
  assert.match(androidRunner, /"cmd", "package", "compile", "-m", "speed", "com\.quata"/);
  assert.match(androidRunner, /android_debug_package_precompiled_before_attachments_audio_instrumentation/);
  assert.match(androidRunner, /android_debug_manifest_removes_firebase_messaging_wakeup_components/);
  assert.doesNotMatch(androidRunner, /pm", "disable-user"/);
  const debugManifest = readFileSync("app/src/debug/AndroidManifest.xml", "utf8");
  assert.match(debugManifest, /com\.google\.firebase\.iid\.FirebaseInstanceIdReceiver/);
  assert.match(debugManifest, /com\.google\.firebase\.messaging\.FirebaseMessagingService/);
  assert.match(debugManifest, /com\.quata\.core\.notifications\.QuataFirebaseMessagingService/);
  assert.match(debugManifest, /com\.google\.android\.c2dm\.permission\.RECEIVE/);
  assert.match(debugManifest, /tools:node="remove"/);
  const prepareAudioRecording = androidUiTest.slice(
    androidUiTest.indexOf("private fun prepareComposerForAudioRecording"),
    androidUiTest.indexOf("private suspend fun runAttachmentPickerStage"),
  );
  assert.doesNotMatch(prepareAudioRecording, /performTextClearance\(\)/);
  assert.match(prepareAudioRecording, /nodeWithTagVisible\(ChatComposerRecordAudioTestTag\)/);
  const attachmentsAudioStage = androidUiTest.slice(
    androidUiTest.indexOf("private fun runAttachmentsAudioStage"),
    androidUiTest.indexOf("private fun waitForDocumentAttachment"),
  );
  assert.doesNotMatch(attachmentsAudioStage, /clickVisibleDocumentAttachmentOpen\(documentName\)\s*compose\.waitForIdle\(\)/);
  assert.match(attachmentsAudioStage, /clickVisibleDocumentAttachmentOpen\(documentName\)\s*SystemClock\.sleep\(700\)/);
  assert.match(androidUiTest, /private fun visibleObject\(selector: BySelector\): Boolean/);
  assert.doesNotMatch(androidUiTest, /device\.displayWidth - 70 to 405/);
  assert.doesNotMatch(androidUiTest, /device\.displayWidth - 90 to 575/);
});

test("Web media attachment evidence uses an opt-in semantic bridge when Compose/Wasm hides canvas anchors", () => {
  assert.match(commonHost, /data class ChatMediaAttachmentActions/);
  assert.match(commonHost, /mediaAttachmentActionsHost: \(@Composable \(ChatMediaAttachmentActions\) -> Unit\)\? = null/);
  assert.match(commonHost, /mediaAttachmentActionsHost\?\.invoke\(ChatMediaAttachmentActions\(file, kind\) \{ onOpenAttachment\(file\) \}\)/);
  assert.match(webHost, /WebChatMediaAttachmentE2eBridge\(actions\)/);
  assert.match(webHost, /installWebChatMediaAttachmentE2eBridge/);
  assert.match(webHost, /WebChatMediaOverlayE2eBridge\(dismiss\)/);
  assert.match(webHost, /installWebChatMediaOverlayE2eBridge/);
  assert.match(webHost, /quata-chat-media-attachment-e2e/);
  assert.match(webHost, /__quataChatMediaAttachmentE2eProduct/);
  assert.match(webHost, /__quataChatMediaOverlayE2eProduct/);
  assert.match(webRunner, /mediaAttachmentBridge: true/);
  assert.match(webRunner, /sessionStorage\.setItem\("quata\.chat_media_attachment\.e2e", "1"\)/);
  assert.match(webRunner, /waitWebMediaAttachmentBridge\(page, attachmentName, kind/);
  assert.match(webRunner, /invokeWebMediaAttachmentBridge\(page, attachmentName, kind\)/);
  assert.match(webRunner, /waitWebMediaOverlayBridge\(page/);
  assert.match(webRunner, /invokeWebMediaOverlayBridgeClose\(page\)/);
  assert.match(webRunner, /web_\$\{kind\}_attachment_opened_by_media_attachment_semantic_bridge/);
  assert.match(webRunner, /web_\$\{kind\}_attachment_closed_by_media_overlay_semantic_bridge/);
});

test("focused chat deep links keep attachments away from the viewport edge", () => {
  assert.match(commonConversationDetail, /FocusedMessageViewportInsetFraction = 0\.18f/);
  assert.match(commonConversationDetail, /listState\.scrollToItem\(index\)/);
  assert.match(commonConversationDetail, /listState\.scrollBy\(-focusInset\)/);
});

test("Web attachments/audio evidence does not wait for unfocused audio text before semantic playback route", () => {
  const attachmentsAudioStage = webRunner.slice(
    webRunner.indexOf("async function verifyAttachmentsAudioWeb"),
    webRunner.indexOf("async function openFocusedAudioMessageRoute"),
  );
  assert.match(commonHost, /data class ChatAudioAttachmentActions/);
  assert.match(webHost, /WebChatAudioAttachmentE2eBridge/);
  assert.match(webHost, /__quataChatAudioAttachmentE2eProduct/);
  assert.match(webRunner, /audioAttachmentBridge: true/);
  assert.match(webRunner, /sessionStorage\.setItem\("quata\.chat_audio_attachment\.e2e", "1"\)/);
  assert.doesNotMatch(attachmentsAudioStage, /getByText\(fixtures\.audio\.name/);
  assert.doesNotMatch(attachmentsAudioStage, /getByText\(fixtures\.nextAudio\.name/);
  assert.match(attachmentsAudioStage, /messageId: fixtures\.audio\.messageId/);
  assert.match(attachmentsAudioStage, /webNextAudioAnchorResolution/);
  assert.match(webRunner, /state\.audioEntries\.some/);
  assert.match(attachmentsAudioStage, /visibleAriaLocator\(page, \[/);
  assert.match(attachmentsAudioStage, /invokeWebAudioAttachmentBridgeToggle\(page, fixtures\.audio\.name\)/);
  assert.match(attachmentsAudioStage, /audioSeekObserved = await seekAudioProgressWeb\(page, fixtures\.audio\.name, 0\.8\)/);
  assert.match(webRunner, /invokeWebAudioAttachmentBridgeSeek\(page, audioName, fraction\)/);
});

test("iOS media overlay close is exposed through a native accessibility anchor", () => {
  assert.match(commonAttachmentPresentation, /nativeClose: @Composable BoxScope\.\(onDismiss: \(\) -> Unit\) -> Unit = \{\}/);
  assert.match(commonHost, /nativeClose = \{ dismiss -> mediaSlots\.nativeClose\(this, dismiss\) \}/);
  assert.match(commonAttachmentPresentation, /testTag = semanticAnchor/);
  assert.match(iosHost, /iosChatMediaPlatformSlots\(/);
  assert.match(iosMediaContent, /showCommonMediaClose = false/);
  assert.match(iosMediaBridge, /func createCloseButton\(/);
  assert.match(iosMediaBridge, /accessibilityIdentifier = accessibilityIdentifier/);
  assert.match(iosMediaBridge, /accessibilityLabel = accessibilityIdentifier/);
  assert.match(iosMediaBridge, /isAccessibilityElement = true/);
  assert.match(iosMediaBridge, /button\.addTarget\(target, action: #selector\(IosChatNativeMediaCloseTarget\.close\), for: \.touchUpInside\)/);
  assert.match(iosUiTest, /fullscreen-media\.close/);
  assert.match(iosUiTest, /fullscreen-media\.media-close/);
  assert.match(iosUiTest, /chromeCloseVisible \|\| mediaCloseVisible/);
  assert.doesNotMatch(iosUiTest, /guard titleVisible, chromeCloseVisible, mediaCloseVisible/);
  const closeHelper = iosUiTest.slice(
    iosUiTest.indexOf("private func closeFullscreenMedia"),
    iosUiTest.indexOf("@discardableResult", iosUiTest.indexOf("private func closeFullscreenMedia")),
  );
  assert.ok(
    closeHelper.indexOf('"fullscreen-media.close"') < closeHelper.indexOf('"fullscreen-media.back"'),
    "iOS fullscreen media close helper must prefer explicit close anchors before navigation/back fallback.",
  );
  assert.match(iosUiTest, /private func isFullscreenMediaChromeVisible\(in app: XCUIApplication, timeout: TimeInterval = 0\) -> Bool/);
  assert.match(iosUiTest, /isFullscreenMediaChromeVisible\(in: app, timeout: 0\.2\)/);
  assert.doesNotMatch(iosUiTest, /isFullscreenMediaChromeVisible\(in: app, timeout: 5\)/);
  assert.doesNotMatch(iosUiTest, /matching\(identifier: "fullscreen-media\.root"\)\.firstMatch\.waitForExistence\(timeout: 5\)/);
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
  assert.match(commonAudioPlayer, /val toggleDescription = if \(isLoading\) "Loading \$displayText" else "\$playPauseDescription \$displayText"/);
  assert.match(commonAudioPlayer, /contentDescription = toggleDescription/);
  assert.match(commonAudioPlayer, /errorText/);
  assert.match(commonAudioPlayer, /if \(hasError\) errorText else displayText/);
  assert.match(commonAudioPlayer, /onTogglePlayback/);
  assert.match(commonAudioPlayer, /onSeekToFraction/);
  assert.match(commonAudioPlayer, /val boundedProgress = progress\.coerceIn\(0f, 1f\)/);
  assert.match(commonAudioPlayer, /val progressPercent = \(boundedProgress \* 100f\)\.toInt\(\)\.coerceIn\(0, 100\)/);
  assert.match(commonAudioPlayer, /contentDescription = "\$ChatAudioAttachmentProgressTestTag \$displayText \$progressPercent%"/);
  assert.match(commonAudioPlayer, /ProgressBarRangeInfo\(boundedProgress, 0f\.\.1f, 0\)/);
  assert.match(commonAudioPlayer, /setProgress \{ target ->/);
  assert.match(androidUiTest, /performSemanticsAction\(SemanticsActions\.SetProgress\) \{ seek -> seek\(0\.8f\) \}/);
  assert.match(androidUiTest, /scrollToAudioAttachmentToggle\(audioName, "audio attachment toggle"\)/);
  assert.match(androidRunner, /quataChatActionsAudioUrl/);
  assert.match(androidUiTest, /ActivityScenario\.launch<MainActivity>\(chatIntent\(chatUrl\)\)\.use/);
  assert.match(androidUiTest, /ActivityScenario\.launch<MainActivity>\(chatIntent\(audioUrl\)\)\.use/);
  assert.doesNotMatch(androidUiTest, /targetContext\.startActivity\(chatIntent\(audioUrl\)/);
  assert.match(androidUiTest, /private fun scrollToAudioAttachmentToggle/);
  assert.match(androidUiTest, /visibleAboveComposerNodes\(toggleMatcher\)\.isNotEmpty\(\)/);
  assert.doesNotMatch(androidUiTest, /center\.x \* 1\.8f/);
  assert.doesNotMatch(androidUiTest, /center\.x \* 1\.9f/);
  assert.doesNotMatch(androidUiTest, /size\.width/);
  assert.doesNotMatch(iosAudioPlayerHost, /private var playbackRequested/);
  assert.match(iosAudioPlayerHost, /private var playbackClockStartTimeSeconds: Double\? = null/);
  assert.match(iosAudioPlayerHost, /private var playbackClockStartPositionMillis = 0L/);
  assert.match(iosAudioPlayerHost, /private var fallbackDurationMillis = 0L/);
  assert.match(iosAudioPlayerHost, /if \(!player\.play\(\)\) return@playerOrFailure PlatformResult\.Failure\("audio_player_play_failed"\)/);
  assert.match(iosAudioPlayerHost, /startPlaybackClock\(player\)/);
  assert.match(iosAudioPlayerHost, /val wasPlaying = player\.playing/);
  assert.match(iosAudioPlayerHost, /val boundedPositionMillis = if \(durationMillis > 0L\) \{\s*positionMillis\.coerceIn\(0L, durationMillis\)/);
  assert.match(iosAudioPlayerHost, /if \(wasPlaying && !player\.play\(\)\) \{/);
  assert.match(iosAudioPlayerHost, /startPlaybackClock\(player, boundedPositionMillis\)/);
  assert.match(iosAudioPlayerHost, /AVAudioPlayerDelegateProtocol/);
  assert.match(iosAudioPlayerHost, /AudioPlaybackEvent\.Ended/);
  assert.match(iosAudioPlayerHost, /AudioPlaybackEvent\.Failed/);
  assert.match(iosAudioPlayerHost, /playbackClockStartPositionMillis \+ \(\(nowSeconds\(\) - started\) \* 1_000\)/);
  assert.match(iosAudioPlayerHost, /maxOf\(nativePositionMillis, clockPositionMillis \?: nativePositionMillis\)/);
  assert.match(iosAudioPlayerHost, /sessionId = sessionId/);
  assert.match(iosAudioPlayerHost, /phase = overridePhase \?: if \(it\.playing\) AudioPlaybackPhase\.Playing else AudioPlaybackPhase\.Ready/);
  assert.match(iosAudioPlayerHost, /fallbackDurationMillis = file\.wavDurationMillis\(url\) \?: 0L/);
  assert.match(iosAudioPlayerHost, /private fun PlatformFile\.wavDurationMillis\(url: NSURL\): Long\?/);
  assert.match(iosAudioPlayerHost, /WAV_METADATA_FALLBACK_MAX_BYTES/);
  assert.ok(
    iosAudioPlayerHost.indexOf("attributesOfItemAtPath") < iosAudioPlayerHost.indexOf("NSData.dataWithContentsOfURL(url)"),
    "iOS WAV fallback must check file size before loading the body into NSData.",
  );
  assert.match(iosAudioPlayerHost, /val chunkEnd = chunkDataOffset\.toLong\(\) \+ chunkSize/);
  assert.match(iosAudioPlayerHost, /NSData\.dataWithContentsOfURL\(url\)/);
  assert.match(iosAudioPlayerHost, /while \(offset \+ 8 <= bytes\.size\)/);
  assert.match(iosAudioPlayerHost, /"fmt " -> if \(chunkSizeInt >= 16\) byteRate = bytes\.uint32Le\(chunkDataOffset \+ 8\)/);
  assert.match(iosAudioPlayerHost, /"data" -> dataSize = chunkSize\.takeIf/);
  assert.doesNotMatch(iosAudioPlayerHost, /copy\(isPlaying = true\)/);
  assert.doesNotMatch(iosAudioPlayerHost, /it\.playing \|\| playbackRequested/);
  assert.doesNotMatch(iosAudioPlayerHost, /prepareToPlay\(\)\) return PlatformResult\.Failure\("audio_player_prepare_failed"\)/);
  assert.match(androidPlatformServices, /val target = if \(durationMillis > 0L\)/);
  assert.match(androidPlatformServices, /active\.seekTo\(target\)/);
  assert.match(androidPlatformServices, /AudioPlaybackEvent\.Ended/);
  assert.match(androidPlatformServices, /Player\.STATE_ENDED/);
  assert.match(androidPlatformServices, /if \(isPlaying\) currentState\(AudioPlaybackPhase\.Playing\) else currentState\(\)/);
  assert.match(androidPlatformServices, /awaitPlaybackState\(active, predicate = \{ it\.isPlaying \}\)/);
  assert.match(androidPlatformServices, /while \(active === player && !predicate\(active\) && System\.currentTimeMillis\(\) < deadline\)/);
  assert.match(androidPlatformServices, /sessionId = sessionId/);
  assert.match(androidPlatformServices, /positionMillis = target/);
});

test("audio playback controller keeps progress polling off the UI dispatcher and stops final ended sessions", () => {
  assert.match(commonAudioController, /scope\.launch\(Dispatchers\.Default\) \{\s*while \(!disposed\)/);
  assert.match(commonAudioController, /withContext\(dispatcher\) \{\s*refreshPosition\(\)\s*\}/);
  assert.match(commonAudioController, /stabilizeNonPlayingState\(event\.state\)/);
  assert.match(commonAudioController, /playback\.phase == AudioPlaybackPhase\.Paused[\s\S]*next\.phase == AudioPlaybackPhase\.Ready[\s\S]*!next\.isPlaying[\s\S]*next\.copy\(phase = AudioPlaybackPhase\.Paused\)/);
  assert.match(commonAudioController, /withContext\(NonCancellable\) \{ audioPlayer\.stop\(\) \}\s*generation \+= 1L\s*_state\.value = ChatAudioPlaybackUiState\(\)/);
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
  assert.match(commonHost, /send = if \(state\.messageText\.isNotBlank\(\) \|\| state\.attachmentUri != null\)/);
  assert.match(commonHost, /messageText = state\.messageText/);
  assert.match(commonHost, /hasPendingAttachment = state\.attachmentUri != null/);
  assert.match(webHost, /send: typeof send === 'function'/);
  assert.match(webHost, /messageText:\s*String\(messageText \|\| ''\)/);
  assert.match(webHost, /return \{ action: 'chat\.composer\.send' \}/);
  assert.match(webHost, /pendingAttachment:\s*Boolean\(hasPendingAttachment\)/);
  assert.match(webRunner, /waitWebComposerBridgeText\(page, value/);
  assert.match(webRunner, /waitWebComposerBridgeAvailability\(page, "send"/);
  assert.match(webRunner, /waitWebComposerBridgeAvailability\(page, "pendingAttachment"/);
  assert.match(webRunner, /audioRecordingSentScreenshot = await attachScreenshot\(page, evidenceDir, "web-chat-audio-recording-sent"\)/);
  assert.match(webRunner, /visibleTextDetected: visibleAfterRpc/);
  assert.match(androidUiTest, /hasSetTextAction\(\) and hasText\("Message", substring = true\)/);
  assert.match(androidUiTest, /compose\.waitUntil\(15_000\) \{ composerInputText\(\) == text \}/);
});

test("chat composer actionable media controls expose accessible labels beside test tags", () => {
  for (const [constant, label] of [
    ["ChatComposerSendTestTag", "strings.send"],
    ["ChatComposerRecordAudioTestTag", "strings.recordAudio"],
    ["ChatComposerEmojiTestTag", "strings.emoji"],
    ["ChatComposerAttachTestTag", "strings.attach"],
    ["ChatComposerCameraTestTag", "strings.openCamera"],
  ]) {
    const marker = commonComposer.indexOf(`testTag = ${constant}`);
    assert.notEqual(marker, -1, `${constant} is missing from composer actions`);
    const block = commonComposer.slice(marker, marker + 260);
    assert.match(block, new RegExp(`contentDescription = ${label.replace(".", "\\.")}`));
  }
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
  assert.match(androidHost, /documentOpenService: DocumentOpenService/);
  assert.match(androidHost, /documentOpenService\.open\(file\)/);
  assert.match(androidHost, /saveChatAttachmentToDownloads/);
  assert.match(androidHost, /shareService\.share\(/);
  assert.match(androidNativeChatScreen, /nextConsecutiveAudioMessage\(state\.messages, finishedMessage\.composeKey\(\)\)/);
  assert.doesNotMatch(androidNativeChatScreen, /val nextIndex = currentIndex \+ 1/);
  assert.match(androidUiTest, /closeFullscreenMediaViewer\("\.mp4"\)/);
  assert.match(androidUiTest, /closeFullscreenMediaViewer\("\.png"\)/);
  assert.match(androidUiTest, /chat_media_attachment_missing_visible_anchor/);
  assert.match(androidUiTest, /chat_media_attachment_visible_tap_failed/);
  assert.doesNotMatch(androidUiTest, /private fun closeFullscreenMediaViewer\(titleNeedle: String\) \{\s*device\.pressBack\(\)/);
  assert.match(androidUiTest, /clickStableTag\(tag\)/);
  assert.match(androidUiTest, /waitForFullscreenMediaClosed\(titleNeedle, 10_000\)/);
  assert.doesNotMatch(androidUiTest, /if \(waitForFullscreenMediaClosed\(titleNeedle, 2_000\)\) \{\s*return\s*\}/);
  assert.match(androidUiTest, /ensureFullscreenMediaVisuallyDismissed\(titleNeedle\)/);
  assert.match(androidUiTest, /By\.textContains\(titleNeedle\)/);
  assert.match(androidUiTest, /By\.descContains\("fullscreen-media\.close"\)/);
  assert.doesNotMatch(androidUiTest, /device\.displayWidth - 70 to 405/);
  assert.doesNotMatch(androidUiTest, /device\.displayWidth - 90 to 575/);
  assert.match(androidUiTest, /Fullscreen media viewer remained visible after close attempts/);
  assert.match(androidUiTest, /fullscreen-media\.title/);
  assert.match(webHost, /openWebAttachment\(documentOpener\)/);
  assert.match(webHost, /downloadWebAttachment/);
  assert.match(webHost, /shareWebAttachment\(shareService\)/);
  assert.match(webHost, /materializeWebAttachment/);
  assert.match(webHost, /SharePayload\(title = .*files = listOf\(local\)\)/);
  assert.match(webHost, /revokeWebAttachmentObjectUrl/);
  assert.match(androidRunner, /stat\(localFile\)\)\.size === 0/);
  assert.match(iosHost, /onOpenAttachment: suspend \(PlatformFile\) -> PlatformResult<Unit>/);
  assert.match(iosHost, /shareDownloadedAttachment/);
  assert.match(iosHost, /attachmentDownloader\.download/);
});

test("fullscreen media overlay dismisses through common animated state before host removal", () => {
  assert.match(fullscreenMediaOverlay, /val visibility = remember \{ MutableTransitionState\(false\) \}/);
  assert.match(fullscreenMediaOverlay, /val requestDismiss = \{\s*visibility\.targetState = false\s*\}/);
  assert.match(fullscreenMediaOverlay, /QuataFullscreenMediaOverlayTopBar\([\s\S]*onBack = requestDismiss/);
  assert.match(fullscreenMediaOverlay, /CompactIconButton\([\s\S]*onClick = requestDismiss/);
  assert.match(fullscreenMediaOverlay, /nativeClose\(requestDismiss\)/);
  assert.match(fullscreenMediaOverlay, /MutableTransitionState\(false\)/);
  assert.match(fullscreenMediaOverlay, /var hasPresented = false/);
  assert.match(fullscreenMediaOverlay, /snapshotFlow \{ visibility\.isIdle && !visibility\.currentState && !visibility\.targetState \}/);
  assert.match(fullscreenMediaOverlay, /if \(!dismissed\) \{\s*hasPresented = true\s*\} else if \(hasPresented\) \{\s*onDismiss\(\)\s*\}/);
  assert.doesNotMatch(fullscreenMediaOverlay, /delay\(170L\)/);
  assert.doesNotMatch(fullscreenMediaOverlay, /onBack = onDismiss/);
  assert.doesNotMatch(fullscreenMediaOverlay, /nativeClose\(onDismiss\)/);
});

test("iOS Quick Look uses an explicit preview item instead of casting NSURL", () => {
  assert.match(iosDocumentOpenService, /class IosQuickLookPreviewItem\(/);
  assert.match(iosDocumentOpenService, /NSObject\(\), QLPreviewItemProtocol/);
  assert.match(iosDocumentOpenService, /override fun previewItemURL\(\): NSURL\?/);
  assert.match(iosDocumentOpenService, /override fun previewItemTitle\(\): String\?/);
  assert.match(iosDocumentOpenService, /IosQuickLookDataSource\(\s*IosQuickLookPreviewItem\(/);
  assert.match(iosDocumentOpenService, /previewItemAtIndex: Long,[\s\S]*\): QLPreviewItemProtocol = item/);
  assert.doesNotMatch(iosDocumentOpenService, /url as QLPreviewItemProtocol/);
});

test("iOS Quick Look cancellation dismisses before releasing the temporary lease", () => {
  assert.match(iosDocumentOpenService, /onPreviewAccepted: \(\) -> Unit/);
  assert.match(iosDocumentOpenService, /activePreview = preview\s*runCatching\(onPreviewAccepted\)/);
  assert.match(iosDocumentOpenService, /fun dismissPreviewAndRelease\(animated: Boolean\) \{\s*if \(dismissed\) return\s*preview\.dismissViewControllerAnimated\(animated\) \{\s*dismissAndRelease\(\)\s*\}/);
  assert.match(iosDocumentOpenService, /continuation\.invokeOnCancellation \{[\s\S]*dismissPreviewAndRelease\(animated = false\)/);
  assert.match(iosAttachmentPreviewService, /onPreviewAccepted = \{ adoptedByDismissAwareViewer = true \}/);
  assert.doesNotMatch(iosAttachmentPreviewService, /is PlatformResult\.Success -> \{\s*adoptedByDismissAwareViewer = documentOpener is IosDismissAwareDocumentOpenService/);
  assert.doesNotMatch(iosDocumentOpenService, /dismissViewControllerAnimated\(false, completion = null\)\s*dismissAndRelease\(\)/);
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
  assert.match(commonHost, /QuataDocumentViewerStatusContent\(/);
  assert.match(commonHost, /allowPlatformFallbackForUnsupportedFormat = true/);
  assert.match(commonHost, /documentOpenJob\?\.cancel\(\)/);
  assert.match(commonHost, /documentOpenGeneration/);
  assert.match(commonHost, /val openGeneration = documentOpenGeneration \+ 1L/);
  assert.match(commonHost, /if \(documentOpenGeneration == openGeneration\)/);
  assert.doesNotMatch(commonHost, /currentCoroutineContext\(\)\[Job\]/);
  assert.match(commonHost, /documentViewerOpeningState\(file\)/);
  assert.match(commonHost, /ChatDocumentAttachmentDownloadTestTag|onDownloadAttachment/);
  assert.match(commonHost, /ChatDocumentAttachmentShareTestTag|onShareAttachment/);
  assert.match(commonHost, /ChatAudioAttachmentPlayerContent\(/);
  assert.match(commonHost, /ChatAudioPlaybackController\(audioPlayer = audioPlayer/);
  assert.doesNotMatch(commonHost, /AudioPlaybackState\(isLoaded = true,\s*isPlaying = true\)/);
  assert.doesNotMatch(commonHost, /LaunchedEffect\(activeAudioReference/);
  assert.doesNotMatch(commonHost, /didAudioPlaybackFinish/);
  assert.match(commonAudioController, /audioPlayer\.events\.collect/);
  assert.match(commonAudioController, /AudioPlaybackEvent\.Ended/);
  assert.match(commonAudioController, /AudioPlaybackEvent\.Failed/);
  assert.match(commonAudioController, /private var generation = 0L/);
  assert.match(commonAudioController, /requestNewPlaybackGeneration\(\)/);
  assert.match(commonAudioController, /event\.state\.sessionId != 0L && event\.state\.sessionId != current\.playback\.sessionId/);
  assert.match(commonAudioController, /isTerminalPlaybackFailure\(\)/);
  assert.match(commonAudioController, /withContext\(NonCancellable\) \{ audioPlayer\.stop\(\) \}/);
  assert.match(commonAudioController, /current\.playback\.phase == AudioPlaybackPhase\.Failed \|\| !current\.playback\.isLoaded -> startNewPlayback/);
  assert.match(commonAudioController, /nextConsecutiveAudioMessage\(messages\(\), key\)/);
  assert.match(commonAudioController, /audioPlayer\.seekTo/);
  assert.doesNotMatch(commonAudioPolicy, /currentIndex - 1/);
  assert.doesNotMatch(commonAudioPolicy, /isNearEnd/);
  assert.doesNotMatch(commonAudioPolicy, /didAudioPlaybackFinish/);
  assert.match(commonAudioPolicy, /messages\.sortedWith/);
  assert.match(commonAudioPolicy, /Instant\.parse\(sentAt\)\.toEpochMilliseconds\(\)/);
  assert.match(commonAudioPolicy, /if \(current\.isDeleted\) return null/);
  assert.match(commonAudioPolicy, /ordered\.getOrNull\(currentIndex \+ 1\)/);
  assert.match(androidDocumentReaderHost, /runCatching \{ launchReader\(request\) \}\.getOrDefault\(false\)/);
  assert.match(androidDocumentReaderHost, /return runCatching \{[\s\S]*launchChooser\(request\)/);
  assert.match(androidDocumentReaderHost, /Intent\.createChooser/);
  assert.match(appContainer, /isDarkModeProvider|QuataThemeMode\.Dark|UI_MODE_NIGHT_YES/);
  assert.match(webHost, /when \(openWebExternalLinkResult\(it\)\)/);
  assert.doesNotMatch(webHost, /openWebExternalLink\(it\)\s*PlatformResult\.Success/);
  assert.match(iosAttachmentPreviewService, /if \(!supportsQuickLook\(attachment\)\) return PlatformResult\.Unsupported/);
  assert.match(iosAttachmentPreviewService, /var adoptedByDismissAwareViewer = false/);
  assert.match(iosAttachmentPreviewService, /finally \{\s*if \(!adoptedByDismissAwareViewer\) lease\.release\(\)\s*\}/);
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
  assert.match(androidUiTest, /val documentName = optionalArgument\("quataChatActionsDocumentName"\)/);
  assert.match(androidUiTest, /val audioName = optionalArgument\("quataChatActionsAudioName"\)/);
  assert.match(androidUiTest, /val audioUrl = optionalArgument\("quataChatActionsAudioUrl"\)/);
  assert.match(androidUiTest, /val nextAudioName = optionalArgument\("quataChatActionsNextAudioName"\)/);
  assert.match(androidUiTest, /"attachments-audio" -> listOf\(chatUrl, documentProbe, documentName, audioProbe, audioName, audioUrl, nextAudioName, imageProbe, videoProbe, audioRecordingMarker\)/);
  assert.match(androidUiTest, /runAttachmentsAudioStage\(\s*chatUrl = chatUrl\.orEmpty\(\),\s*documentProbe = documentProbe\.orEmpty\(\),\s*documentName = documentName\.orEmpty\(\),\s*audioUrl = audioUrl\.orEmpty\(\),/);
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
  assert.match(androidUiTest, /waitForDocumentAttachment\(documentName, "document attachment message"\)/);
  assert.match(androidUiTest, /private fun documentAttachmentOpenMatcher\(name: String\): SemanticsMatcher/);
  assert.match(androidUiTest, /clickVisibleDocumentAttachmentOpen\(documentName\)/);
  assert.match(androidUiTest, /private fun visibleNodes\(matcher: SemanticsMatcher\)/);
  assert.match(androidUiTest, /document-viewer-status-root/);
  assert.match(androidUiTest, /waitForAndroidDocumentReader\(documentName\)/);
  assert.match(androidUiTest, /android_document_reader_missing_stable_anchor/);
  assert.match(androidRunner, /"quataChatActionsDocumentName", state\.attachmentsAudio\?\.document\?\.name/);
  assert.match(androidUiTest, /android-chat-audio-consecutive-next-playing/);
  assert.match(androidUiTest, /waitForConsecutiveAudioChainToStop\(nextAudioName\)/);
  assert.match(androidUiTest, /android-chat-audio-consecutive-chain-stopped/);
  assert.match(androidUiTest, /compose\.waitUntil\(15_000\)/);
  assert.match(androidUiTest, /hasAudioDescription\(audioName, "Pausar", "Pause"\)/);
  assert.match(androidUiTest, /hasAudioDescription\(nextAudioName, "Pausar", "Pause"\)/);
  assert.match(androidUiTest, /waitForAudioProgressToStart\(audioName\)/);
  assert.match(androidUiTest, /private fun waitForAudioProgressToStart\(name: String, timeoutMillis: Long = 20_000\)/);
  assert.match(androidUiTest, /private fun waitForAudioAttachment\(name: String, context: String, timeoutMillis: Long = 45_000\)/);
  assert.match(androidUiTest, /val audioMatcher = hasTestTag\(ChatAudioAttachmentPlayerTestTag\) and hasAnyDescendant\(hasAudioDescription\(name\)\)/);
  assert.match(androidUiTest, /visibleNodes\(audioMatcher\)\.isNotEmpty\(\)/);
  assert.match(androidUiTest, /waitForAudioAttachment\(audioName, "audio attachment message"\)/);
  assert.doesNotMatch(androidUiTest, /waitForMarker\(audioProbe\.take\(28\), "audio attachment message"\)/);
  assert.match(webRunner, /audioDurationSeconds: 12/);
  assert.match(androidRunner, /audioDurationSeconds: 12/);
  assert.match(iosRunner, /audioDurationSeconds: 12/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_NAME/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_NEXT_AUDIO_NAME/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_NAME/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_NEXT_AUDIO_NAME/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_NAME:\?Set QUATA_IOS_CHAT_ATTACHMENT_AUDIO_NAME/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_AUDIO_NAME'\] = attachment_audio_name/);
  assert.match(iosUiTest, /ios-chat-audio-consecutive-next-playing/);
  assert.match(iosUiTest, /let activeAudioToggle = audioToggleElement\(audioName: audioName, action: "Pausar", fallbackAction: "Pause", in: app\)/);
  assert.match(iosUiTest, /activeAudioToggle\.waitForExistence\(timeout: 15\)/);
  assert.match(iosUiTest, /waitForAudioProgressToStart\(audioName: audioName, in: app, timeout: 20\)/);
  assert.match(iosUiTest, /audioToggleElement\(audioName: nextAudioName, action: "Pausar", fallbackAction: "Pause"/);
  assert.match(commonHost, /ChatAudioPlaybackController\(audioPlayer = audioPlayer/);
  assert.doesNotMatch(commonHost, /var audioOperationInFlight by remember/);
  assert.doesNotMatch(commonHost, /AudioPlaybackState\(isLoaded = true, isPlaying = true\)/);
  assert.doesNotMatch(commonHost, /onPlaybackOperationInFlight/);
  assert.doesNotMatch(commonHost, /onPlaybackCompleted/);
  assert.match(androidUiTest, /ChatAudioAttachmentPlayerTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentToggleTestTag/);
  assert.match(androidUiTest, /ChatAudioAttachmentProgressTestTag/);
  assert.match(androidUiTest, /android-chat-attachment-video-viewer/);
  assert.match(androidUiTest, /android-chat-attachment-media-viewer/);
  assert.match(androidUiTest, /android-chat-attachment-document-visible/);
  assert.match(androidUiTest, /android-chat-attachment-document-viewer-status/);
  assert.match(androidUiTest, /android-chat-attachment-document-reader/);
  assert.match(androidRunner, /android-chat-audio-recording-active\.png/);
  assert.match(androidRunner, /android-chat-audio-recording-pending-attachment\.png/);
  assert.match(androidRunner, /android-chat-audio-recording-ready-to-send\.png/);
  assert.match(androidRunner, /android-chat-audio-recording-sent\.png/);
  assert.match(androidRunner, /android-chat-audio-seek-attempted\.png/);
  assert.match(androidRunner, /android-chat-audio-consecutive-chain-stopped\.png/);
  assert.match(androidUiTest, /android-chat-audio-toggle-attempted/);
  assert.match(androidUiTest, /performSemanticsAction\(SemanticsActions\.SetProgress\) \{ seek -> seek\(0\.8f\) \}/);
  assert.doesNotMatch(androidUiTest, /center\.x \* 1\.8f/);
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
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_MESSAGE_ID/);
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
  assert.match(iosUiTest, /audioProgress\.adjust\(toNormalizedSliderPosition: 0\.8\)/);
  assert.doesNotMatch(iosUiTest, /audioProgress[\s\S]{0,120}coordinate\(withNormalizedOffset: CGVector\(dx: 0\.95/);
  assert.match(iosUiTest, /chat\.attachment\.pending/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_AUDIO_RECORDING_MARKER/);
  assert.match(iosUiTest, /chat\.composer\.send/);
  assert.match(iosUiTest, /ios-chat-attachment-media-viewer/);
  assert.match(iosUiTest, /ios-chat-attachment-video-viewer/);
  assert.match(iosUiTest, /ios-chat-attachment-document-visible/);
  assert.match(iosUiTest, /ios-chat-attachment-document-viewer-status/);
  assert.match(iosUiTest, /document-viewer-status-root/);
  assert.ok(iosUiTest.includes("?message=\\(encodedQuery(audioMessageId))"));
  assert.doesNotMatch(iosUiTest, /matching\(identifier: "document-viewer-status-close"\)\.firstMatch\.tap\(\)/);
  assert.match(iosUiTest, /ios-chat-audio-toggle-attempted/);
  assert.match(iosUiTest, /Set\(\[documentProbe, audioProbe, imageProbe, videoProbe\]\)\.count/);
  assert.match(iosUiTest, /app\.terminate\(\)[\s\S]*?openDeepLink\("quata:\/\/egquata\.com\/#chat-/);
  assert.match(iosUiTest, /openChatMediaAttachment\([\s\S]*identifier: "chat\.attachment\.media\.video"[\s\S]*messageId: videoMessageId[\s\S]*markerProbe: videoProbe/);
  assert.match(iosUiTest, /waitForFocusedMessageVisible\(videoMessageId, in: app/);
  assert.match(iosUiTest, /waitForFocusedMessageVisible\(imageMessageId, in: app/);
  assert.match(iosUiTest, /matching\(identifier: "chat\.message\.[^"]*messageId[^"]*"\)/);
  assert.match(iosUiTest, /\.allElementsBoundByIndex/);
  assert.match(iosUiTest, /candidates\.first\(where: \{ isElementVisibleInChatViewport\(\$0, in: app\) \}\)/);
  assert.match(iosUiTest, /media-anchor-offscreen/);
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
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_MESSAGE_ID/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_AUDIO_MESSAGE_ID'\] = attachment_audio_message/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID'\] = attachment_image_message/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID'\] = attachment_video_message/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_MESSAGE_ID/);
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
    iosWrapper.indexOf('elif [[ "$QUATA_IOS_CHAT_COMPOSER_EMOJI_UI_E2E" == "1"'),
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
  assert.doesNotMatch(webRunner, /image_attachment_message_not_visible/);
  assert.doesNotMatch(webRunner, /document_attachment_message_not_visible/);
  assert.doesNotMatch(webRunner, /audio_attachment_message_not_visible/);
  assert.doesNotMatch(webRunner, /next_audio_attachment_message_not_visible/);
  assert.match(webRunner, /async function waitMessageVisibleNearCurrentPosition\(page, marker, error, timeout = 45_000\)/);
  assert.match(webRunner, /verifyDocumentAttachmentActionsWeb/);
  assert.match(webRunner, /web-chat-attachment-document-viewer-status/);
  assert.match(webRunner, /document-viewer-status-root/);
  assert.match(webRunner, /acceptDownloads: true/);
  assert.match(webRunner, /__quataSharePayloads/);
  assert.match(webRunner, /chat\\.attachment\\.document\\.download/);
  assert.match(webRunner, /chat\\.attachment\\.document\\.share/);
  assert.match(webRunner, /web_chat_document_attachment_download_and_share_actions_verified/);
  assert.match(webRunner, /documentAttachmentOnly/);
  assert.match(webRunner, /--document-attachment-only/);
  assert.match(webRunner, /quata-chat-document-attachment-e2e/);
  assert.match(webRunner, /waitWebDocumentAttachmentBridge/);
  assert.match(webRunner, /invokeWebDocumentAttachmentBridge/);
  assert.match(webRunner, /waitWebDocumentViewerOpened/);
  assert.match(webRunner, /data-quata-docmentis-render-ready/);
  assert.match(webRunner, /web-chat-attachment-document-docmentis-viewer/);
  assert.match(webRunner, /attachmentDocumentViewerKind = viewerKind/);
  const documentOnlyBranch = webRunner.slice(
    webRunner.indexOf("if (options.documentAttachmentOnly)"),
    webRunner.indexOf("if (options.attachmentsAudioOnly)"),
  );
  assert.match(documentOnlyBranch, /document: await createChatAttachmentMessage\(config, state\.a, state\.thread, runId, "document"\)/);
  assert.match(documentOnlyBranch, /verifyDocumentAttachmentActionsWeb\(page, state\.attachmentsAudio\.document[\s\S]*useBridgeFallback: true/);
  assert.match(documentOnlyBranch, /document_attachment_shared_chrome_verified/);
  assert.doesNotMatch(documentOnlyBranch, /verifyWebAudioRecordingComposer|waitAudioPlaybackObserved|nextAudio/);
  assert.match(webHost, /installWebChatDocumentAttachmentE2eBridge/);
  assert.match(webHost, /quata-chat-document-attachment-e2e/);
  assert.match(webHost, /__quataChatDocumentAttachmentE2eProduct/);
  assert.match(webHost, /documentAttachmentActionsHost = \{ actions ->/);
  assert.match(webRunner, /function sentMessageId\(payload\)/);
  assert.match(webRunner, /messageId: sentMessageId/);
  assert.doesNotMatch(webRunner, /consumeBrowserRuntimeFaultsForSyntheticAudio/);
  const attachmentsBranch = webRunner.slice(
    webRunner.indexOf("if (options.attachmentsAudioOnly)"),
    webRunner.indexOf("if (state.peerMessage && state.b.accessToken)"),
  );
  assert.match(attachmentsBranch, /documentAttachmentBridge: true/);
  assert.match(webRunner, /verifyDocumentAttachmentActionsWeb\(page, fixtures\.document[\s\S]*useBridgeFallback: true/);
  assert.match(webRunner, /messageId: fixtures\.video\.messageId/);
  assert.match(webRunner, /messageId: fixtures\.image\.messageId/);
  assert.match(webRunner, /messageId: fixtures\.document\.messageId/);
  assert.match(webRunner, /messageId: fixtures\.audio\.messageId/);
  assert.match(webRunner, /const message = options\.messageId \?/);
  assert.match(webRunner, /encodeURIComponent\(options\.messageId\)/);
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
  assert.doesNotMatch(webRunner, /await page\.mouse\.wheel\(0, 520\)/);
  assert.match(webRunner, /webNextAudioAnchorResolution/);
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
  assert.match(webRunner, /seekAudioProgressWeb\(page, fixtures\.audio\.name, 0\.8\)/);
  assert.match(webRunner, /page\.keyboard\.press\("Home"\)/);
  assert.match(webRunner, /page\.keyboard\.press\("ArrowRight"\)/);
  assert.doesNotMatch(webRunner, /page\.keyboard\.press\("End"\)/);
  assert.doesNotMatch(webRunner, /clickLocatorFraction\(page, progress, fraction/);
  assert.match(webRunner, /--use-fake-device-for-media-stream/);
  assert.match(webRunner, /--autoplay-policy=no-user-gesture-required/);
  assert.match(webRunner, /grantPermissions\(\["microphone"\]/);
  assert.match(webRunner, /waitConsecutiveAudioPlaybackObserved/);
  assert.match(webRunner, /consecutive_audio_playback_state_not_observed/);
  assert.doesNotMatch(webRunner, /secondLoaded/);
  assert.match(webRunner, /visibleNativeControls\(page\)/);
  assert.doesNotMatch(webRunner, /next_audio_attachment_pause_anchor_not_visible/);
  assert.match(webRunner, /bridge:chat\.attachment\.audio\.toggle/);
  assert.doesNotMatch(webRunner, /consecutiveAudioAutoAdvance: safeFailure/);
  assert.match(webRunner, /web-chat-audio-next-player-visible/);
  assert.match(webRunner, /nextAudioMessageId/);
  assert.match(androidUiTest, /android-chat-audio-recording-ready/);
  assert.match(androidUiTest, /dismissComposerImeIfFocused\(\)/);
  assert.match(iosUiTest, /audioProgress\.adjust\(toNormalizedSliderPosition: 0\.8\)/);
  assert.doesNotMatch(iosUiTest, /CGVector\(dx: 0\.95, dy: 0\.5\)/);
  assert.match(iosUiTest, /waitForPendingAttachmentToSend\(marker: marker, in: app, context: "audio recording"\)/);
  assert.match(iosUiTest, /Sending .* must clear the shared pending attachment surface and composer marker/);
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
  assert.match(browserAudioPlayer, /const targetMillis = Number\(positionMillis\)/);
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
