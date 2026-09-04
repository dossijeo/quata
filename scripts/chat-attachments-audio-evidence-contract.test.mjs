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
  androidDocumentOpenService,
  androidHost,
  appContainer,
  androidDocumentReaderHost,
  androidDocumentReaderActivity,
  androidDocumentReaderFallback,
  pdfReaderActivity,
  viewRtfActivity,
  viewFilesActivity,
  androidNativeChatScreen,
  webHost,
  iosAttachmentPreviewService,
  iosDocumentOpenService,
  iosHost,
  iosAppDelegate,
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
  iosAvPlayerAudioEngine,
  iosAudioHost,
  iosEvidenceAudioHost,
  iosShareService,
  iosFeedFrameworkTests,
  iosChatAttachmentDownloader,
  iosChatAttachmentAudioPlayerService,
  attestationJson,
  pickerAttestationJson,
  androidAttachmentFileCache,
  androidChatAttachmentAudioPlayerService,
  iosProjectConfig,
  iosSignedBuildScript,
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
  source("core/src/androidMain/kotlin/com/quata/core/platform/AndroidDocumentOpenService.kt"),
  source("app/src/main/java/com/quata/feature/chat/presentation/chat/AndroidChatProductScreen.kt"),
  source("app/src/main/java/com/quata/core/di/AppContainer.kt"),
  source("document-reader/src/main/java/com/quata/documentreader/AndroidDocumentOpenService.kt"),
  source("document-reader/src/main/java/com/quata/documentreader/activity/All_Document_Reader_Activity.kt"),
  source("document-reader/src/main/java/com/quata/documentreader/DocumentReaderFallback.java"),
  source("document-reader/src/main/java/com/quata/documentreader/activity/PDF_Reader_Activity.java"),
  source("document-reader/src/main/java/com/quata/documentreader/activity/ViewRtf_Activity.java"),
  source("document-reader/src/main/java/com/quata/documentreader/activity/ViewFiles_Activity.java"),
  source("app/src/main/java/com/quata/feature/chat/presentation/chat/ChatScreen.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatAttachmentPreviewService.kt"),
  source("core/src/iosMain/kotlin/com/quata/core/platform/IosDocumentOpenService.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt"),
  source("iosApp/iosApp/QuataIosApp.swift"),
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
  source("iosApp/iosApp/IosAvPlayerAudioEngine.swift"),
  source("core/src/iosMain/kotlin/com/quata/core/platform/IosAvFoundationAudioHost.kt"),
  source("core/src/iosMain/kotlin/com/quata/core/platform/IosEvidenceAudioRecorderHost.kt"),
  source("core/src/iosMain/kotlin/com/quata/core/platform/IosShareService.kt"),
  source("iosApp/iosAppTests/QuataFeedFrameworkTests.swift"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatAttachmentDownloader.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatAttachmentAudioPlayerService.kt"),
  source("docs/candidate-attestations/chat-attachments-audio.json"),
  source("docs/candidate-attestations/chat-attachment-picker.json"),
  source("app/src/main/java/com/quata/feature/chat/data/ChatAttachmentFileCache.kt"),
  source("app/src/main/java/com/quata/feature/chat/data/AndroidChatAttachmentAudioPlayerService.kt"),
  source("iosApp/project.yml"),
  source("scripts/build-ios-intel-simulator-signed.sh"),
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
      ["ChatMediaAttachmentOpenTestTagSuffix", ".open"],
    ]],
  ]) {
    for (const [constant, tag] of anchors) {
      assert.match(sourceText, new RegExp(`${constant} = "${tag.replaceAll(".", "\\.")}"`));
      if (constant.endsWith("TestTag") && constant !== "ChatMediaAttachmentTestTag") {
        assert.match(sourceText, new RegExp(`testTag = ${constant}`));
      }
      if (constant === "ChatMediaAttachmentTestTag") {
        assert.match(sourceText, /chatMediaAttachmentSemanticAnchor/);
        assert.match(sourceText, /semanticTestTag: String = chatMediaAttachmentSemanticAnchor\(kind\)/);
        assert.match(sourceText, /role = Role\.Button/);
        assert.match(sourceText, /clickable\(role = Role\.Button, onClick = onOpen\)/);
        assert.match(sourceText, /val mediaOwnsOpen = kind == ChatAttachmentKind\.Video \|\| kind == ChatAttachmentKind\.Image/);
        assert.match(sourceText, /val openButtonTestTag = "\$semanticTestTag\$ChatMediaAttachmentOpenTestTagSuffix"/);
        assert.doesNotMatch(sourceText, /val primaryTestTag = if \(mediaOwnsOpen\) openButtonTestTag else semanticTestTag/);
        assert.match(sourceText, /\.semantics\(mergeDescendants = false\)/);
        assert.match(sourceText, /testTag = semanticTestTag/);
        assert.match(sourceText, /Surface\([\s\S]*?onClick = onOpen[\s\S]*?modifier = Modifier[\s\S]*?\.size\(62\.dp\)[\s\S]*?testTag = openButtonTestTag/);
        assert.match(sourceText, /contentDescription = semanticAnchor/);
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

test("iOS media attachment evidence uses semantic media open controls before fallbacks", () => {
  assert.doesNotMatch(iosUiTest, /waitForFocusedMessageVisible\(videoMessageId/);
  assert.doesNotMatch(iosUiTest, /waitForFocusedMessageVisible\(imageMessageId/);
  assert.match(iosUiTest, /waitForFocusedMessageVisible\(\s*documentMessageId,[\s\S]*?reportFailure: false/);
  assert.match(iosUiTest, /waitForFocusedMessageVisible\(\s*audioMessageId,[\s\S]*?reportFailure: false/);
  assert.match(iosUiTest, /makeChatAnchorVisible\(identifier: "chat\.attachment\.document"/);
  assert.match(iosUiTest, /makeAudioAnchorVisible\(identifier: "chat\.attachment\.audio\.player"/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_MESSAGE_ID/);
  const openResolvedMedia = iosUiTest.slice(
    iosUiTest.indexOf("private func openResolvedMedia"),
    iosUiTest.indexOf("    private func assertFullscreenMediaOpened", iosUiTest.indexOf("private func openResolvedMedia")),
  );
  assert.match(openResolvedMedia, /isElementActionablyVisibleInChatViewport\(media, in: app\),\s*openSemanticMedia\(media, context: context, in: app, failOnMiss: false\)/);
  assert.match(openResolvedMedia, /isElementVisibleInChatViewport\(media, in: app\),\s*tapVisibleChatViewportCenter\(of: media, in: app\),\s*assertFullscreenMediaOpened\(context: context, in: app, reportFailure: false\)/s);
  assert.match(openResolvedMedia, /media-open-not-hittable/);
  assert.doesNotMatch(openResolvedMedia, /tapVisibleFrameCenter/);
  assert.doesNotMatch(openResolvedMedia, /tapResolvedMedia/);
  assert.doesNotMatch(openResolvedMedia, /coordinate\(withNormalizedOffset: CGVector\(dx: 0\.5, dy: 0\.35\)\)\.tap\(\)/);
  assert.doesNotMatch(openResolvedMedia, /app\.coordinate\(withNormalizedOffset:/);
  const openSemanticMedia = iosUiTest.slice(
    iosUiTest.indexOf("private func openSemanticMedia"),
    iosUiTest.indexOf("private func openHittableMedia"),
  );
  assert.match(openSemanticMedia, /tapVisibleChatViewportCenter\(of: media, in: app\)/);
  assert.doesNotMatch(openSemanticMedia, /media\.tap\(\)/);
  assert.match(openSemanticMedia, /media-open-semantic-failed/);
  const openHittableMedia = iosUiTest.slice(
    iosUiTest.indexOf("private func openHittableMedia"),
    iosUiTest.indexOf("private func assertFullscreenMediaOpened"),
  );
  assert.match(iosUiTest, /private func tapVisibleChatViewportCenter\(of element: XCUIElement, in app: XCUIApplication\) -> Bool/);
  assert.match(openHittableMedia, /tapVisibleChatViewportCenter\(of: media, in: app\)/);
  assert.match(openHittableMedia, /media-open-hittable-no-visible-frame/);
  assert.doesNotMatch(openHittableMedia, /coordinate\(withNormalizedOffset: CGVector\(dx: 0\.5, dy: 0\.5\)\)\.tap\(\)/);
  assert.doesNotMatch(openHittableMedia, /CGVector\(dx: 0\.5, dy: 0\.35\)/);
  const openChatMediaAttachment = iosUiTest.slice(
    iosUiTest.indexOf("private func openChatMediaAttachment"),
    iosUiTest.indexOf("private func openResolvedMedia"),
  );
  assert.match(openChatMediaAttachment, /messageSpecificOpenIdentifier = "\\\(messageSpecificIdentifier\)\.open"/);
  assert.match(openChatMediaAttachment, /func mediaElement\(actionablyVisible: Bool = false\) -> XCUIElement\?/);
  assert.match(openChatMediaAttachment, /func baseMediaElement\(actionablyVisible: Bool = false\) -> XCUIElement\?/);
  assert.match(openChatMediaAttachment, /\.filter \{ !\$0\.element\.identifier\.hasSuffix\("\.open"\) \}/);
  assert.match(openChatMediaAttachment, /func exactMediaElement\(actionablyVisible: Bool = false\) -> XCUIElement\?/);
  assert.match(openChatMediaAttachment, /func openResolvedMediaOrBaseFallback\(_ media: XCUIElement, failOnMiss: Bool = false\) -> Bool/);
  assert.match(openChatMediaAttachment, /media\.identifier\.hasSuffix\("\.open"\)/);
  assert.match(openChatMediaAttachment, /let base = baseMediaElement\(\)/);
  assert.match(openChatMediaAttachment, /media-base-anchor-fallback/);
  assert.match(openChatMediaAttachment, /if left\.priority != right\.priority\s*\{\s*return left\.priority < right\.priority\s*\}/);
  assert.match(openChatMediaAttachment, /visibleChatViewportArea\(left\.element, in: app\) > visibleChatViewportArea\(right\.element, in: app\)/);
  assert.match(openChatMediaAttachment, /waitForFocusedMessageVisible\(messageId, in: app, context: context, reportFailure: false\)[\s\S]*let semanticOpenProbe/);
  assert.match(openChatMediaAttachment, /let semanticOpenProbe = app\.descendants\(matching: \.any\)[\s\S]*?\.matching\(identifier: messageSpecificOpenIdentifier\)[\s\S]*?\.firstMatch/);
  assert.match(openChatMediaAttachment, /for _ in 0\.\.<2/);
  assert.doesNotMatch(openChatMediaAttachment, /for _ in 0\.\.<4/);
  assert.match(openChatMediaAttachment, /for _ in 0\.\.<6/);
  assert.match(openChatMediaAttachment, /if let semanticOpen = exactMediaElement\(\),\s*isElementVisibleInChatViewport\(semanticOpen, in: app\)/);
  assert.match(openChatMediaAttachment, /func mediaRecoveryScrollDirections\(\) -> \[Bool\]/);
  assert.match(openChatMediaAttachment, /messageText\(markerProbe, in: app\)/);
  assert.match(openChatMediaAttachment, /return \[false, false, true, false, true\]/);
  assert.match(openChatMediaAttachment, /func recoverMediaVisibilityByDirectionalScroll\(\) -> XCUIElement\?/);
  assert.match(openChatMediaAttachment, /for scrollDown in mediaRecoveryScrollDirections\(\)/);
  assert.match(openChatMediaAttachment, /scrollMediaContextTowardViewport\(\)[\s\S]*if let recovered = recoverMediaVisibilityByDirectionalScroll\(\)/);
  assert.doesNotMatch(openChatMediaAttachment, /for scrollDown in \[true, true, false, false, true\]/);
  assert.match(openChatMediaAttachment, /exactMediaElement\(actionablyVisible: true\) \?\? mediaElement\(actionablyVisible: true\)/);
  assert.match(openChatMediaAttachment, /guard let media = mediaElement\(\) else/);
  assert.match(openChatMediaAttachment, /scrollElementTowardActionableChatViewport\(media, in: app\)/);
  assert.match(openChatMediaAttachment, /ios-\\\(slug\(context\)\)-media-anchor-missing/);
  assert.match(openChatMediaAttachment, /if isElementVisibleInChatViewport\(media, in: app\) \{/);
  assert.doesNotMatch(openChatMediaAttachment, /if media\.isHittable \{/);
  assert.match(openChatMediaAttachment, /mediaScrollSnapshot\(media, in: app\)/);
  assert.match(openChatMediaAttachment, /return openResolvedMediaOrBaseFallback\(media, failOnMiss: true\)/);
  assert.doesNotMatch(openChatMediaAttachment, /if openResolvedMedia\(media, context: context, in: app\)/);
  assert.match(iosUiTest, /private func visibleChatViewportArea\(_ element: XCUIElement, in app: XCUIApplication\) -> CGFloat/);
  assert.match(openChatMediaAttachment, /messageSpecificOpen\.map \{ \(element: \$0, priority: 0\) \}/);
  assert.match(openChatMediaAttachment, /if left\.priority != right\.priority/);
  assert.match(openChatMediaAttachment, /let openCandidates = candidates\.filter \{ \$0\.identifier\.hasSuffix\("\.open"\) \}/);
  assert.match(openChatMediaAttachment, /openCandidates\.first\(where: \{ isElementActionablyVisibleInChatViewport\(\$0, in: app\) \}\)/);
  assert.match(iosUiTest, /let frame = element\.frame\.intersection\(chatMessageViewport\(in: app\)\)/);
  assert.doesNotMatch(iosUiTest, /viewport\.contains\(CGPoint\(x: frame\.midX, y: frame\.midY\)\)/);
  const waitForFocusedMessageVisible = iosUiTest.slice(
    iosUiTest.indexOf("private func waitForFocusedMessageVisible"),
    iosUiTest.indexOf("    private func waitForMessagePendingToClear"),
  );
  assert.match(waitForFocusedMessageVisible, /candidates\.contains\(where: \{ visibleChatViewportArea\(\$0, in: app\) > 0 \}\)/);
  assert.match(waitForFocusedMessageVisible, /scrollElementTowardViewport\(existing, in: app\)/);
  assert.match(waitForFocusedMessageVisible, /scrollChatMessagesWhileFocusedMessageIsUnmaterialized\(attempt: unmaterializedScrollAttempt, in: app\)/);
  assert.match(iosUiTest, /private func scrollChatMessagesWhileFocusedMessageIsUnmaterialized\(attempt: Int, in app: XCUIApplication\)/);
  assert.match(iosUiTest, /attempt % 4 == 3[\s\S]*list\.swipeDown\(\)[\s\S]*list\.swipeUp\(\)/);
  assert.match(waitForFocusedMessageVisible, /reportFailure: Bool = true/);
  assert.match(waitForFocusedMessageVisible, /-> Bool/);
  assert.match(waitForFocusedMessageVisible, /if reportFailure \{\s*XCTFail/);
  assert.match(waitForFocusedMessageVisible, /return false/);
  assert.doesNotMatch(waitForFocusedMessageVisible, /if focused\.exists \|\| message\.exists \|\| messageSpecificAnchor\.exists/);
  assert.match(iosUiTest, /assertChatRoute\(conversationId, messageId: videoMessageId, in: app, context: "attachments\/audio video message"\)/);
  assert.match(iosUiTest, /assertChatRoute\(conversationId, messageId: imageMessageId, in: app, context: "attachments\/audio image message"\)/);
  assert.match(iosUiTest, /assertChatRoute\(conversationId, messageId: documentMessageId, in: app, context: "attachments\/audio document message"\)/);
  assert.match(iosUiTest, /assertChatRoute\(conversationId, messageId: audioMessageId, in: app, context: "attachments\/audio audio message after document viewer"\)/);
  const isElementVisibleInChatViewport = iosUiTest.slice(
    iosUiTest.indexOf("private func isElementVisibleInChatViewport"),
    iosUiTest.indexOf("    private func scrollElementTowardViewport"),
  );
  assert.match(isElementVisibleInChatViewport, /private func isElementActionablyVisibleInChatViewport/);
  assert.match(isElementVisibleInChatViewport, /visible\.width >= min\(frame\.width \* 0\.5, 44\)/);
  assert.match(isElementVisibleInChatViewport, /visible\.height >= min\(frame\.height \* 0\.5, 44\)/);
  const isElementActionablyVisibleInChatViewport = iosUiTest.slice(
    iosUiTest.indexOf("private func isElementActionablyVisibleInChatViewport"),
    iosUiTest.indexOf("    private func scrollElementTowardViewport"),
  );
  assert.doesNotMatch(isElementActionablyVisibleInChatViewport, /\.isHittable/);
  assert.doesNotMatch(isElementVisibleInChatViewport, /frame\.width \* 0\.2/);
  assert.doesNotMatch(isElementVisibleInChatViewport, /frame\.height \* 0\.2/);
  const scrollElementTowardViewport = iosUiTest.slice(
    iosUiTest.indexOf("private func scrollElementTowardViewport"),
    iosUiTest.indexOf("private func chatMessagesList"),
  );
  assert.match(scrollElementTowardViewport, /safeViewport = viewport\.insetBy\(dx: 0, dy: 12\)/);
  assert.match(scrollElementTowardViewport, /visible = frame\.intersection\(safeViewport\)/);
  assert.match(scrollElementTowardViewport, /visible\.width >= min\(frame\.width \* 0\.5, 44\)/);
  assert.match(scrollElementTowardViewport, /visible\.height >= min\(frame\.height \* 0\.5, 44\)/);
  assert.match(scrollElementTowardViewport, /frame\.maxY < safeViewport\.minY/);
  assert.match(scrollElementTowardViewport, /frame\.minY > safeViewport\.maxY/);
  assert.match(scrollElementTowardViewport, /frame\.midY < safeViewport\.midY/);
  assert.match(scrollElementTowardViewport, /frame\.midY > safeViewport\.midY/);
});

test("Android attachments/audio evidence precompiles debug package and avoids fullscreen coordinate fallbacks", () => {
  assert.match(androidRunner, /"cmd", "package", "compile", "-m", "speed", "com\.quata"/);
  assert.match(androidRunner, /android_debug_package_precompiled_before_attachments_audio_instrumentation/);
  assert.match(androidRunner, /android_debug_manifest_removes_firebase_messaging_wakeup_components/);
  assert.match(androidRunner, /"quataChatActionsImageMessageId"/);
  assert.match(androidRunner, /String\(state\.attachmentsAudio\.image\.messageId\)/);
  assert.match(androidRunner, /"quataChatActionsVideoMessageId"/);
  assert.match(androidRunner, /String\(state\.attachmentsAudio\.video\.messageId\)/);
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
  assert.match(attachmentsAudioStage, /withShellLaunchedChat\(chatUrl\)/);
  assert.match(attachmentsAudioStage, /withShellLaunchedChat\(audioUrl\)/);
  assert.match(androidUiTest, /private fun launchChatWithAmStart\(url: String\)/);
  assert.match(androidUiTest, /"am",\s*"start",\s*"-W"/);
  assert.doesNotMatch(attachmentsAudioStage, /ActivityScenario\.launch<MainActivity>\(chatIntent/);
  assert.doesNotMatch(attachmentsAudioStage, /clickVisibleDocumentAttachmentOpen\(documentName\)\s*compose\.waitForIdle\(\)/);
  assert.match(attachmentsAudioStage, /clickVisibleDocumentAttachmentOpen\(documentName\)\s*SystemClock\.sleep\(700\)/);
  assert.match(androidUiTest, /val imageMessageId = optionalArgument\("quataChatActionsImageMessageId"\)/);
  assert.match(androidUiTest, /val videoMessageId = optionalArgument\("quataChatActionsVideoMessageId"\)/);
  assert.match(androidUiTest, /"\$ChatVideoAttachmentContentDescription\.\$videoMessageId\$ChatMediaAttachmentOpenTestTagSuffix"/);
  assert.match(androidUiTest, /"\$ChatImageAttachmentContentDescription\.\$imageMessageId\$ChatMediaAttachmentOpenTestTagSuffix"/);
  assert.match(androidUiTest, /performScrollToNode\(tagMatcher\)/);
  assert.match(androidUiTest, /performSemanticsAction\(SemanticsActions\.OnClick\)/);
  assert.match(androidUiTest, /private fun visibleObject\(selector: BySelector\): Boolean/);
  assert.doesNotMatch(androidUiTest, /device\.displayWidth - 70 to 405/);
  assert.doesNotMatch(androidUiTest, /device\.displayWidth - 90 to 575/);
});

test("signed iOS simulator build produces an XCTest run manifest for evidence replay", () => {
  assert.match(iosProjectConfig, /test:\s*\n\s*config: SimulatorSigned/);
  assert.match(iosSignedBuildScript, /xctestrun_count="\$\(find "\$derived_data_path\/Build\/Products" -name '\*\.xctestrun' -type f \| wc -l \| tr -d ' '\)"/);
  assert.match(iosSignedBuildScript, /Expected one signed \.xctestrun was not produced/);
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
  assert.match(webRunner, /const bridgeReady = await waitWebMediaAttachmentBridge\(page, attachmentName, kind, allowScroll \? 1_500 : 0\)/);
  assert.match(webRunner, /const opener = bridgeReady\s*\?\s*null/);
  assert.match(webRunner, /waitWebMediaOverlayBridge\(page/);
  assert.match(webRunner, /invokeWebMediaOverlayBridgeClose\(page\)/);
  assert.match(webRunner, /web_\$\{kind\}_attachment_opened_by_media_attachment_semantic_bridge/);
  assert.match(webRunner, /web_\$\{kind\}_attachment_closed_by_media_overlay_semantic_bridge/);
});

test("focused chat deep links keep attachments away from the viewport edge", () => {
  assert.match(commonConversationDetail, /FocusedMessageViewportInsetFraction = 0\.18f/);
  assert.match(commonConversationDetail, /ChatConversationMessagesBottomPadding\s*=\s*96\.dp/);
  assert.match(commonConversationDetail, /ChatConversationFocusedMessagesTopPadding\s*=\s*96\.dp/);
  assert.doesNotMatch(commonConversationDetail, /ChatConversationFocusedMediaScrollPadding/);
  assert.match(commonConversationDetail, /ChatConversationMessagesTopPadding\s*=\s*12\.dp/);
  assert.match(commonConversationDetail, /val focusedMessageIsMedia = remember\(focusedMessageId, messages\)/);
  assert.match(commonConversationDetail, /val focusedViewportOffset = remember\(focusedMessageId, messages\)/);
  assert.match(commonConversationDetail, /focusedMessageIsMedia -> ChatConversationFocusedMessagesTopPadding/);
  assert.doesNotMatch(commonConversationDetail, /FocusedMediaTopPadding/);
  assert.match(commonConversationDetail, /focusedMessageId != null -> ChatConversationFocusedMessagesTopPadding/);
  assert.match(commonConversationDetail, /else -> ChatConversationMessagesTopPadding/);
  assert.match(commonConversationDetail, /val focusedViewportOffsetPx = with\(density\) \{ focusedViewportOffset\.roundToPx\(\) \}/);
  assert.match(commonConversationDetail, /val focusedScrollOffsetPx = -focusedViewportOffsetPx/);
  assert.doesNotMatch(commonAttachmentPresentation, /BringIntoViewRequester/);
  assert.doesNotMatch(commonAttachmentPresentation, /requestFocusIntoView: Boolean = false/);
  assert.match(commonAttachmentPresentation, /val mediaOwnsOpen = kind == ChatAttachmentKind\.Video \|\| kind == ChatAttachmentKind\.Image/);
  assert.doesNotMatch(commonAttachmentPresentation, /val primaryTestTag = if \(mediaOwnsOpen\) openButtonTestTag else semanticTestTag/);
  assert.match(commonAttachmentPresentation, /\.semantics\(mergeDescendants = false\) \{\s*testTag = semanticTestTag\s*contentDescription = semanticAnchor/s);
  assert.match(commonAttachmentPresentation, /Surface\([\s\S]*?onClick = onOpen[\s\S]*?modifier = Modifier[\s\S]*?\.size\(62\.dp\)[\s\S]*?testTag = openButtonTestTag/);
  assert.match(commonConversationDetail, /top = focusedViewportOffset/);
  assert.doesNotMatch(commonConversationDetail, /Spacer\(Modifier\.height\(ChatConversationFocusedMessagesTopPadding\)\)/);
  assert.match(commonConversationDetail, /bottom = ChatConversationMessagesBottomPadding/);
  assert.match(commonConversationDetail, /listState\.scrollToItem\(index, scrollOffset = focusedScrollOffsetPx\)/);
  assert.match(commonConversationDetail, /onFocusedMessageVisible\(focusedMessage\.id\)\s*initialPositionReady = true/);
  assert.match(commonConversationDetail, /ChatAttachmentKind\.Image \|\| it == ChatAttachmentKind\.Video/);
  assert.match(commonConversationDetail, /focusedItem = listState\.layoutInfo\.visibleItemsInfo\.firstOrNull/);
  assert.match(commonConversationDetail, /desiredTop = maxOf\(0, listState\.layoutInfo\.viewportStartOffset\) \+ focusInset/);
  assert.match(commonConversationDetail, /val desiredHeight = desiredBottom - desiredTop/);
  assert.match(commonConversationDetail, /val itemCenter = itemTop \+ focusedItem\.size \/ 2f/);
  assert.match(commonConversationDetail, /val desiredCenter = desiredTop \+ desiredHeight \/ 2f/);
  assert.match(commonConversationDetail, /focusedItem\.size > desiredHeight -> itemCenter - desiredCenter/);
  assert.match(commonConversationDetail, /itemBottom > desiredBottom && focusedItem\.size <= desiredHeight -> itemBottom - desiredBottom/);
  assert.match(commonConversationDetail, /itemTop < desiredTop -> itemTop - desiredTop/);
  assert.match(commonConversationDetail, /listState\.scrollBy\(scrollDelta\)/);
  assert.match(webRunner, /data-quata-chat-focused-message-selected/);
  assert.match(webRunner, /document\.documentElement\.getAttribute\("data-quata-chat-focused-message-selected"\) === String\(messageId\)/);
  assert.doesNotMatch(commonConversationDetail, /listState\.scrollBy\(-focusInset\)/);
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

test("remote Chat attachment media is materialized before native players/viewers receive it", () => {
  assert.match(androidAttachmentFileCache, /ChatAttachmentPublicUrlPolicy\.canonicalUrlOrNull/);
  assert.match(androidAttachmentFileCache, /followRedirects\(false\)/);
  assert.match(androidAttachmentFileCache, /followSslRedirects\(false\)/);
  assert.match(androidAttachmentFileCache, /\.header\("apikey", publishableKey\)/);
  assert.match(androidAttachmentFileCache, /header\("Authorization", "Bearer \$it"\)/);
  assert.match(androidAttachmentFileCache, /MAX_ATTACHMENT_BYTES = 50L \* 1024L \* 1024L/);
  assert.match(androidAttachmentFileCache, /copyBounded\(input, output\)/);

  assert.match(androidPlatformServices, /android_audio_reference_remote_unsupported/);
  assert.match(androidPlatformServices, /scheme == null \|\| scheme == "file" \|\| scheme == "content"/);
  assert.match(browserAudioPlayer, /web_audio_reference_remote_unsupported/);
  assert.doesNotMatch(browserAudioPlayer, /globalThis\.fetch\(source/);
  assert.match(webHost, /WebChatAttachmentAudioPlayerService\(audioPlayer\)/);
  assert.match(webHost, /file\.reference\.safeBrowserChatMediaUrl\(\)/);
  assert.match(webHost, /DocumentPreviewKind\.Office -> reference\.safeBrowserChatMediaUrl\(\)[\s\S]{0,120}documentOpener\.open\(copy\(reference = it\)\)/);
  assert.match(webHost, /materializeCancelableWebAttachment\(source, file\.displayName, file\.mimeType\)/);
  assert.match(webHost, /suspendCancellableCoroutine/);
  assert.match(webHost, /cancelWebAttachmentMaterialization\(requestId\)/);
  assert.match(webHost, /result\.releaseMaterializedWebAttachmentIfOwned\(\)/);
  assert.match(webHost, /cancelledResult\.releaseMaterializedWebAttachmentIfOwned\(\)/);
  assert.match(webHost, /private fun PlatformResult<PlatformFile>\.releaseMaterializedWebAttachmentIfOwned\(\)/);
  assert.match(webHost, /AbortController/);
  assert.match(webHost, /signal: controller\.signal/);
  assert.match(webHost, /error\?\.name === 'AbortError' \? 'cancelled'/);
  assert.match(webHost, /ownedObjectUrl = it\.reference/);
  assert.match(webHost, /releaseOwnedObjectUrl\(\)/);
  assert.match(webHost, /redirect: 'error'/);
  assert.match(webHost, /response\.headers\?\.get\?\.\('content-length'\)/);
  assert.match(webHost, /response\.body\?\.getReader/);
  assert.match(webHost, /web_chat_attachment_download_stream_unavailable/);
  assert.match(webHost, /web_chat_attachment_share_stream_unavailable/);
  assert.doesNotMatch(webHost, /if \(!response\.body\?\.getReader\) return response\.blob\(\)/);
  assert.match(webHost, /reader\.cancel\(\)/);
  assert.match(webHost, /50 \* 1024 \* 1024/);

  assert.doesNotMatch(androidDocumentReaderActivity, /HttpURLConnection/);
  assert.doesNotMatch(androidDocumentReaderActivity, /downloadUri\(/);
  assert.doesNotMatch(androidDocumentReaderActivity, /"https" ->/);
  assert.match(androidDocumentReaderActivity, /putExtra\(QuataDocumentReader\.EXTRA_FALLBACK_URI, fallbackUri\.toString\(\)\)/);
  assert.match(androidDocumentReaderFallback, /public static void failOrOpenChooser/);
  assert.match(androidDocumentReaderFallback, /Intent\.ACTION_VIEW/);
  assert.match(androidDocumentReaderFallback, /QuataDocumentReader\.EXTRA_FALLBACK_URI/);
  assert.match(androidDocumentReaderFallback, /!"content"\.equals\(scheme\) && !"file"\.equals\(scheme\)/);
  assert.doesNotMatch(androidDocumentReaderFallback, /http/i);
  assert.match(androidDocumentReaderFallback, /FLAG_GRANT_READ_URI_PERMISSION/);
  assert.match(androidDocumentReaderFallback, /activity\.finish\(\)/);
});

test("Android internal reader late render failures fall back to the system chooser", () => {
  assert.match(androidDocumentReaderFallback, /openSystemChooser\(Activity activity\)/);
  assert.match(pdfReaderActivity, /DocumentReaderFallback\.failOrOpenChooser\(this\)/);
  assert.match(viewRtfActivity, /DocumentReaderFallback\.failOrOpenChooser\(this\)/);
  assert.match(viewFilesActivity, /DocumentReaderFallback\.failOrOpenChooser\(this\)/);
  assert.doesNotMatch(viewRtfActivity, /loadDataWithBaseURL\("", "", "text\/html"/);
});

test("iOS media overlay close is exposed through a native accessibility anchor", () => {
  assert.match(commonAttachmentPresentation, /nativeClose: @Composable BoxScope\.\(onDismiss: \(\) -> Unit\) -> Unit = \{\}/);
  assert.match(commonHost, /nativeClose = \{ dismiss -> mediaSlots\.nativeClose\(this, dismiss\) \}/);
  assert.match(commonAttachmentPresentation, /\.semantics\(mergeDescendants = false\)/);
  assert.match(commonAttachmentPresentation, /testTag = openButtonTestTag/);
  assert.doesNotMatch(commonAttachmentPresentation, /val primaryTestTag = if \(mediaOwnsOpen\) openButtonTestTag else semanticTestTag/);
  assert.match(commonAttachmentPresentation, /contentDescription = semanticAnchor/);
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
  assert.match(iosUiTest, /guard \(rootVisible \|\| titleVisible\), closeVisible else/);
  assert.doesNotMatch(iosUiTest, /guard titleVisible, chromeCloseVisible, mediaCloseVisible/);
  assert.doesNotMatch(iosUiTest, /matching\(identifier: "fullscreen-media\.root"\)[\s\S]{0,120}waitForExistence\(timeout: 10\)/);
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
  const attachmentsAudioStage = androidUiTest.slice(
    androidUiTest.indexOf("private fun runAttachmentsAudioStage"),
    androidUiTest.indexOf("private fun waitForDocumentAttachment"),
  );
  for (const [constant, tag] of [
    ["ChatAudioAttachmentPlayerTestTag", "chat.attachment.audio.player"],
    ["ChatAudioAttachmentToggleTestTag", "chat.attachment.audio.toggle"],
    ["ChatAudioAttachmentProgressTestTag", "chat.attachment.audio.progress"],
    ["ChatAudioAttachmentStateLoading", "chat.attachment.audio.state.loading"],
    ["ChatAudioAttachmentStatePlaying", "chat.attachment.audio.state.playing"],
    ["ChatAudioAttachmentStatePaused", "chat.attachment.audio.state.paused"],
    ["ChatAudioAttachmentStateFailed", "chat.attachment.audio.state.failed"],
  ]) {
    assert.match(commonAudioPlayer, new RegExp(`${constant} = "${tag.replaceAll(".", "\\.")}"`));
    if (constant.endsWith("TestTag")) {
      assert.match(commonAudioPlayer, new RegExp(`testTag = ${constant}`));
    }
  }
  assert.match(commonAudioPlayer, /playPauseDescription/);
  assert.match(commonAudioPlayer, /val toggleDescription = if \(isLoading\) "Loading \$displayText" else "\$playPauseDescription \$displayText"/);
  assert.match(commonAudioPlayer, /contentDescription = toggleDescription/);
  assert.match(commonAudioPlayer, /val progressStateDescription = "\$playbackStateDescription \$progressPercent%"/);
  assert.match(commonAudioPlayer, /stateDescription = progressStateDescription/);
  assert.match(commonAudioPlayer, /errorText/);
  assert.match(commonAudioPlayer, /if \(hasError\) errorText else displayText/);
  assert.match(commonAudioPlayer, /onTogglePlayback/);
  assert.match(commonAudioPlayer, /onClick\(label = playPauseDescription\) \{\s*onTogglePlayback\(\)\s*true\s*\}/);
  assert.match(commonAudioPlayer, /\.clickable\(\s*enabled = true,\s*role = Role\.Button,\s*onClick = onTogglePlayback,\s*\)/);
  assert.doesNotMatch(commonAudioPlayer, /if \(!hasError\) \{\s*onClick/);
  assert.match(commonAudioPlayer, /onSeekToFraction/);
  assert.match(commonAudioPlayer, /val boundedProgress = progress\.coerceIn\(0f, 1f\)/);
  assert.match(commonAudioPlayer, /val progressPercent = \(boundedProgress \* 100f\)\.toInt\(\)\.coerceIn\(0, 100\)/);
  assert.match(commonAudioPlayer, /contentDescription = "\$ChatAudioAttachmentProgressTestTag \$displayText \$progressPercent%"/);
  assert.match(commonAudioPlayer, /role = Role\.ValuePicker/);
  assert.match(commonAudioPlayer, /ProgressBarRangeInfo\(boundedProgress, 0f\.\.1f, 0\)/);
  assert.match(commonAudioPlayer, /setProgress \{ target ->/);
  assert.match(commonAudioPlayer, /BringIntoViewRequester/);
  assert.match(commonAudioPlayer, /bringIntoViewRequester\(bringIntoViewRequester\)/);
  assert.match(commonAudioPlayer, /requestFocusIntoView: Boolean = false/);
  assert.match(commonHost, /requestFocusIntoView = requestFocusIntoView/);
  assert.doesNotMatch(iosUiTest, /QUATA_IOS_CHAT_AUDIO_ATTACHMENT_E2E/);
  assert.doesNotMatch(iosUiTest, /#chat-audio-e2e\?action=toggle/);
  assert.doesNotMatch(iosUiTest, /#chat-audio-e2e\?action=seek/);
  assert.match(iosUiTest, /String\(describing: progress\.value \?\? ""\)/);
  assert.match(iosUiTest, /audioToggle\.tap\(\)/);
  assert.match(iosUiTest, /waitForAudioPhase\(audioName: audioName, phase: "chat\.attachment\.audio\.state\.playing"/);
  assert.match(iosUiTest, /let pauseToggle = audioToggleElement\(audioName: audioName, action: "Pausar", fallbackAction: "Pause", in: app\)/);
  assert.match(iosUiTest, /pauseToggle\.tap\(\)/);
  assert.match(iosUiTest, /waitForAudioPhase\(audioName: audioName, phase: "chat\.attachment\.audio\.state\.paused"/);
  assert.match(iosUiTest, /progress\.adjust\(toNormalizedSliderPosition: position\)/);
  assert.match(iosUiTest, /let resumeToggle = audioToggleElement\(audioName: audioName, action: "Reproducir", fallbackAction: "Play", in: app\)/);
  assert.match(iosUiTest, /resumeToggle\.tap\(\)/);
  assert.doesNotMatch(iosHost, /audioAttachmentActionsHost = \{ actions ->\s*IosChatAudioAttachmentE2eBridge\(actions\)/s);
  assert.doesNotMatch(iosAppDelegate, /IosChatAudioAttachmentE2eBridgeKt\.iosChatAudioAttachmentE2eHandleUrl/);
  assert.match(androidUiTest, /performSemanticsAction\(SemanticsActions\.SetProgress\) \{ seek -> seek\(0\.8f\) \}/);
  assert.match(androidUiTest, /private fun audioAttachmentStateMatcher\(name: String, state: String\)/);
  assert.match(androidUiTest, /SemanticsProperties\.StateDescription\)\?\.startsWith\(state\) == true/);
  assert.match(androidUiTest, /audioAttachmentStateMatcher\(audioName, ChatAudioAttachmentStatePlaying\)/);
  assert.match(androidUiTest, /audioAttachmentStateMatcher\(nextAudioName, ChatAudioAttachmentStatePlaying\)/);
  assert.match(androidUiTest, /scrollToAudioAttachmentToggle\([\s\S]*messageId = audioMessageId[\s\S]*followingAudioMessageId = nextAudioMessageId/);
  assert.match(androidUiTest, /private fun chatMessageMatcher\(messageId: String\)/);
  assert.match(androidUiTest, /performScrollToNode\(chatMessageMatcher\(messageId\)\)/);
  assert.doesNotMatch(androidUiTest, /performScrollToNode\(followingMatcher\)/);
  assert.match(androidRunner, /quataChatActionsAudioUrl/);
  assert.match(androidRunner, /quataChatActionsAudioMessageId/);
  assert.match(androidRunner, /quataChatActionsNextAudioMessageId/);
  assert.match(androidUiTest, /withShellLaunchedChat\(chatUrl\)/);
  assert.match(androidUiTest, /withShellLaunchedChat\(audioUrl\)/);
  assert.match(androidUiTest, /"-f",\s*"0x14008000"/);
  assert.match(androidUiTest, /output\.contains\("Status: ok"\)/);
  assert.match(androidUiTest, /output\.contains\("Status: timeout"\)/);
  assert.match(androidUiTest, /output\.contains\("Activity: \$component"\)/);
  assert.match(androidUiTest, /output\.contains\("Complete"\)/);
  assert.doesNotMatch(attachmentsAudioStage, /ActivityScenario\.launch<MainActivity>\(chatIntent\(chatUrl\)\)\.use/);
  assert.doesNotMatch(attachmentsAudioStage, /ActivityScenario\.launch<MainActivity>\(chatIntent\(audioUrl\)\)\.use/);
  assert.doesNotMatch(androidUiTest, /targetContext\.startActivity\(chatIntent\(audioUrl\)/);
  assert.match(androidUiTest, /private fun scrollToAudioAttachmentToggle/);
  assert.match(androidUiTest, /private fun scrollSemanticAudioToggleAwayFromComposer/);
  assert.match(androidUiTest, /performSemanticsAction\(SemanticsActions\.ScrollBy\) \{ action -> action\(0f, scrollBy\) \}/);
  assert.match(androidUiTest, /visibleAboveComposerNodes\(toggleMatcher\)\.isNotEmpty\(\)/);
  assert.doesNotMatch(androidUiTest, /swipeUp\(\)/);
  assert.doesNotMatch(androidUiTest, /center\.x \* 1\.8f/);
  assert.doesNotMatch(androidUiTest, /center\.x \* 1\.9f/);
  assert.doesNotMatch(androidUiTest, /size\.width/);
  assert.doesNotMatch(iosAudioPlayerHost, /private var playbackRequested/);
  assert.match(iosAudioPlayerHost, /interface IosNativeAudioPlaybackEngine/);
  assert.match(iosAudioPlayerHost, /fun startPlayback\(\): IosNativeAudioPlaybackEngineState/);
  assert.match(iosAudioPlayerHost, /fun pausePlayback\(\): IosNativeAudioPlaybackEngineState/);
  assert.match(iosAudioPlayerHost, /fun seekPlaybackTo\(positionMillis: Long\): IosNativeAudioPlaybackEngineState/);
  assert.match(iosAudioPlayerHost, /fun stopPlayback\(\): IosNativeAudioPlaybackEngineState/);
  assert.match(iosAudioPlayerHost, /private var fallbackDurationMillis = 0L/);
  assert.match(iosAudioPlayerHost, /val played = engine\.startPlayback\(\)/);
  assert.match(iosAudioPlayerHost, /if \(played\.errorReason != null\) \{/);
  assert.match(iosAudioPlayerHost, /if \(played\.isPlaying\) AudioPlaybackPhase\.Playing else AudioPlaybackPhase\.Loading/);
  assert.doesNotMatch(iosAudioPlayerHost, /audio_player_play_not_started/);
  assert.match(iosAudioPlayerHost, /isPlaying = native\.isPlaying/);
  assert.match(iosAudioPlayerHost, /native\.isPlaying -> AudioPlaybackPhase\.Playing/);
  assert.match(iosAudioPlayerHost, /fun playbackStateChanged\(\)/);
  assert.match(iosAudioPlayerHost, /AudioPlaybackEvent\.StateChanged\(stateValue\(\)\)/);
  assert.match(iosAudioPlayerHost, /AudioPlaybackEvent\.Ended/);
  assert.match(iosAudioPlayerHost, /AudioPlaybackEvent\.Failed/);
  assert.match(iosAudioPlayerHost, /sessionId = sessionId/);
  assert.match(iosAudioPlayerHost, /fallbackDurationMillis = nextFallbackDurationMillis/);
  assert.match(iosAudioPlayerHost, /AVURLAsset\(uRL = NSURL\.fileURLWithPath\(path\), options = null\)\.duration/);
  assert.match(iosAppDelegate, /audioPlayerEngine: IosAvPlayerAudioEngine\(\)/);
  assert.match(iosAudioPlayerHost, /class IosAvFoundationAudioPlayerHost\(\s*private val engine: IosNativeAudioPlaybackEngine = IosAvAudioPlayerEngine\(\),\s*\)/);
  assert.match(iosFeedFrameworkTests, /audioPlayerEngine: nil/);
  assert.match(iosAudioPlayerHost, /activePlayer\?\.playing == true/);
  assert.match(iosAudioPlayerHost, /audioPlayerDidFinishPlaying/);
  assert.match(iosAudioPlayerHost, /audioPlayerDecodeErrorDidOccur/);
  assert.match(iosAvPlayerAudioEngine, /final class IosAvPlayerAudioEngine: NSObject, IosNativeAudioPlaybackEngine/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /weak var listener/);
  assert.match(iosAvPlayerAudioEngine, /private var listener: \(any IosNativeAudioPlaybackEngineListener\)\?/);
  assert.match(iosAvPlayerAudioEngine, /AVAudioPlayerDelegate/);
  assert.match(iosAvPlayerAudioEngine, /private var player: AVAudioPlayer\?/);
  assert.match(iosAvPlayerAudioEngine, /private var playbackStartWatchdog: DispatchWorkItem\?/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /private var item: AVPlayerItem\?/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /private var periodicTimeObserver: Any\?/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /private var statusObservation: NSKeyValueObservation\?/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /private var timeControlObservation: NSKeyValueObservation\?/);
  assert.match(iosAvPlayerAudioEngine, /private var generation: Int64 = 0/);
  assert.match(iosAvPlayerAudioEngine, /try session\.setActive\(true\)/);
  assert.match(iosAvPlayerAudioEngine, /private static let dataBackedPlayerMaxBytes/);
  assert.match(iosAvPlayerAudioEngine, /let data = try Data\(contentsOf: url, options: \[\.mappedIfSafe\]\)/);
  assert.match(iosAvPlayerAudioEngine, /let dataPlayer = try AVAudioPlayer\(data: data\)/);
  assert.match(iosAvPlayerAudioEngine, /let urlPlayer = try AVAudioPlayer\(contentsOf: url\)/);
  assert.match(iosAvPlayerAudioEngine, /lastErrorReason = lastErrorReason \?\? errorReason\(error, fallback: fallback\)/);
  assert.match(iosAvPlayerAudioEngine, /listener\?\.playbackStateChanged\(\)/);
  assert.match(iosAvPlayerAudioEngine, /if !activePlayer\.play\(\)/);
  assert.match(iosAvPlayerAudioEngine, /installPlaybackStartWatchdog\(for: activePlayer, generation: generation\)/);
  assert.match(iosAvPlayerAudioEngine, /func audioPlayerDidFinishPlaying\(_ player: AVAudioPlayer, successfully flag: Bool\)/);
  assert.match(iosAvPlayerAudioEngine, /func audioPlayerDecodeErrorDidOccur\(_ player: AVAudioPlayer, error: \(any Error\)\?\)/);
  assert.match(iosAvPlayerAudioEngine, /requestGeneration == self\.generation/);
  assert.match(iosAvPlayerAudioEngine, /activePlayer === self\.player/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /ios_avplayer_play_not_started_/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /ios_avplayer_play_not_advancing/);
  assert.match(iosAvPlayerAudioEngine, /activePlayer\.isPlaying/);
  assert.match(iosAvPlayerAudioEngine, /isPlaying: activePlayer\?\.isPlaying \?\? false/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /player\.rate > 0/);
  assert.match(iosAvPlayerAudioEngine, /activePlayer\.currentTime = Double\(boundedMillis\) \/ 1_000\.0/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /AVPlayerItem\(url:/);
  assert.match(iosAvPlayerAudioEngine, /listener\?\.playbackEnded\(\)/);
  assert.match(iosAvPlayerAudioEngine, /listener\?\.playbackFailed\(reason: lastErrorReason\)/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /playbackRequested/);
  assert.match(iosChatAttachmentDownloader, /NSFileProtectionCompleteUntilFirstUserAuthentication/);
  assert.match(androidPlatformServices, /ANDROID_AUDIO_SEEK_CONFIRMATION_TOLERANCE_MS/);
  assert.match(androidPlatformServices, /android_audio_seek_not_confirmed/);
  assert.doesNotMatch(androidPlatformServices, /positionMillis = target/);
  assert.match(iosChatAttachmentAudioPlayerService, /SharedIosChatAttachmentAudioLeaseStore/);
  assert.match(iosChatAttachmentAudioPlayerService, /class IosChatAttachmentAudioLeaseStore/);
  assert.match(iosChatAttachmentAudioPlayerService, /private var cachedLease: Lease\?/);
  assert.doesNotMatch(iosChatAttachmentDownloader, /NSFileProtectionComplete\)/);
  assert.doesNotMatch(iosChatAttachmentDownloader, /NSFileProtectionCompleteUnlessOpen/);
  assert.match(iosAudioPlayerHost, /val nextFallbackDurationMillis = file\.containerDurationMillis\(url\)\s*\?: file\.wavDurationMillis\(url\)\s*\?: 0L/);
  assert.match(iosAudioPlayerHost, /private fun PlatformFile\.wavDurationMillis\(url: NSURL\): Long\?/);
  assert.match(iosAudioPlayerHost, /WAV_METADATA_FALLBACK_MAX_BYTES/);
  const wavFallback = iosAudioPlayerHost.slice(iosAudioPlayerHost.indexOf("private fun PlatformFile.wavDurationMillis"));
  assert.ok(
    wavFallback.indexOf("attributesOfItemAtPath") < wavFallback.indexOf("NSData.dataWithContentsOfURL(url)"),
    "iOS WAV fallback must check file size before loading the body into NSData.",
  );
  assert.match(iosAudioPlayerHost, /private fun dataBackedAudioPlayer\(url: NSURL, sizeBytes: Long\): AVAudioPlayer\?/);
  assert.match(iosAudioPlayerHost, /DATA_BACKED_PLAYER_MAX_BYTES/);
  assert.ok(
    iosAudioPlayerHost.indexOf("if (sizeBytes <= 0L || sizeBytes > DATA_BACKED_PLAYER_MAX_BYTES) return null") <
      iosAudioPlayerHost.indexOf("val data = NSData.dataWithContentsOfURL(url) ?: return null"),
    "iOS data-backed player fallback must check file size before loading the body into NSData.",
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
  assert.doesNotMatch(androidPlatformServices, /awaitPlaybackState\(active, predicate = \{ it\.isPlaying \}\)/);
  assert.match(androidPlatformServices, /while \(active === player && !predicate\(active\) && System\.currentTimeMillis\(\) < deadline\)/);
  assert.match(androidPlatformServices, /sessionId = sessionId/);
  assert.match(androidPlatformServices, /abs\(state\.positionMillis - target\)/);
});

test("audio playback controller keeps progress polling off the UI dispatcher and stops final ended sessions", () => {
  assert.match(commonAudioController, /scope\.launch\(Dispatchers\.Default\) \{\s*while \(!disposed\)/);
  assert.match(commonAudioController, /private val progressRefreshIntervalMillis: Long = DefaultProgressRefreshIntervalMillis/);
  assert.match(commonAudioController, /if \(progressRefreshIntervalMillis > 0L\)/);
  assert.match(commonAudioController, /delay\(progressRefreshIntervalMillis\)/);
  assert.match(commonAudioController, /const val DefaultProgressRefreshIntervalMillis = 1_000L/);
  assert.match(commonAudioController, /withContext\(dispatcher\) \{\s*refreshPosition\(\)\s*\}/);
  assert.match(commonAudioController, /stabilizeNonPlayingState\(event\.state\)/);
  assert.match(commonAudioController, /playback\.phase == AudioPlaybackPhase\.Paused[\s\S]*next\.phase == AudioPlaybackPhase\.Ready[\s\S]*!next\.isPlaying[\s\S]*next\.copy\(phase = AudioPlaybackPhase\.Paused\)/);
  assert.match(commonAudioController, /private val ownerToken = Any\(\)/);
  assert.match(commonAudioController, /claimPlaybackOwner\(\)/);
  assert.match(commonAudioController, /globalAudioMutex\.withLock/);
  assert.match(commonAudioController, /return if \(ownsPlayback\(\) && !disposed\) result else null/);
  assert.match(commonAudioController, /releaseOwnedPlayer\(\)[\s\S]*audioPlayer\.stop\(\)/);
  assert.match(commonAudioController, /releaseOwnedPlayer\(\)\s*generation \+= 1L\s*_state\.value = ChatAudioPlaybackUiState\(\)/);
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
  assert.match(commonHost, /fun sendComposerMessage\(\) \{/);
  assert.match(commonHost, /var audioRecordingGeneration by remember \{ mutableLongStateOf\(0L\) \}/);
  assert.match(commonHost, /fun sendComposerMessage\(\) \{\s*audioRecordingGeneration \+= 1L/);
  assert.match(commonHost, /if \(isRecordingAudio\) \{\s*isRecordingAudio = false\s*recordingElapsedSeconds = 0L\s*scope\.launch \{ audioRecorder\.cancel\(\) \}/);
  assert.match(commonHost, /val generation = audioRecordingGeneration[\s\S]*audioRecorder\.start/);
  assert.match(commonHost, /if \(audioRecordingGeneration != generation\) return@launch[\s\S]*isRecordingAudio = true/);
  assert.match(commonHost, /audioRecorder\.stop\(\)[\s\S]*if \(audioRecordingGeneration != generation\) \{\s*audioRecordingReferences\?\.release\(result\.value\)\s*return@launch/);
  assert.match(commonHost, /audioRecordingGeneration \+= 1L[\s\S]*audioRecorder\.cancel\(\)/);
  assert.match(commonHost, /recordingError = null\s*viewModel\.onEvent\(ChatUiEvent\.Send\)/);
  assert.match(commonHost, /send = if \(state\.messageText\.isNotBlank\(\) \|\| state\.attachmentUri != null\)/);
  assert.match(commonHost, /::sendComposerMessage/);
  assert.match(commonHost, /if \(event == ChatUiEvent\.Send\) \{\s*sendComposerMessage\(\)/);
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

test("iOS chat audio playback activates the native session and forwards live events", () => {
  const iosChatAudioAdapter = readFileSync("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatAttachmentAudioPlayerService.kt", "utf8");
  assert.match(iosAppDelegate, /import AVFoundation/);
  assert.match(iosAppDelegate, /configureChatAudioPlaybackSession\(\)/);
  assert.match(iosAppDelegate, /try session\.setCategory\(\.playback, mode: \.default\)/);
  assert.match(iosAppDelegate, /try session\.setActive\(true\)/);
  assert.match(iosChatAudioAdapter, /override val events: Flow<AudioPlaybackEvent>\s*get\(\) = delegate\.events/);
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
  assert.match(iosHost, /finally \{\s*attachmentDownloader\.discard\(localFile\)\s*\}/);
});

test("iOS attachment share keeps the temporary file until the native sheet completes", () => {
  assert.match(iosChatAttachmentDownloader, /internal fun discard\(file: PlatformFile\)/);
  assert.match(iosShareService, /suspendCoroutine/);
  assert.doesNotMatch(iosShareService, /suspendCancellableCoroutine/);
  assert.match(iosShareService, /UIModalPresentationFullScreen/);
  assert.match(iosShareService, /activityController\.modalPresentationStyle = UIModalPresentationFullScreen/);
  assert.match(iosShareService, /completionWithItemsHandler = \{ _, completed, _, error ->/);
  assert.match(iosShareService, /completed -> PlatformResult\.Success\(Unit\)/);
  assert.match(iosShareService, /else -> PlatformResult\.Cancelled/);
  assert.match(iosHost, /try \{\s*shareService\.share\(/);
  assert.match(iosHost, /finally \{\s*attachmentDownloader\.discard\(localFile\)\s*\}/);
});

test("Android document reader owns and cleans only its bounded temporary cache", async () => {
  const quataDocumentReader = await source("document-reader/src/main/java/com/quata/documentreader/QuataDocumentReader.kt");
  const csvReaderActivity = await source("document-reader/src/main/java/com/quata/documentreader/activity/CSVViewer_Activity.java");
  const textReaderActivity = await source("document-reader/src/main/java/com/quata/documentreader/activity/QuataTextDocumentActivity.kt");
  assert.match(quataDocumentReader, /EXTRA_OWNED_TEMP_PATH/);
  assert.match(quataDocumentReader, /cleanupOwnedTempFile\(context: Context, path: String\?\)/);
  assert.match(quataDocumentReader, /ownedTempFileOrNull/);
  assert.match(quataDocumentReader, /File\(context\.cacheDir, DocumentReaderTempDirectory\)\.canonicalFile/);
  assert.match(quataDocumentReader, /pruneOwnedTempFiles\(context: Context\)/);
  assert.match(androidDocumentReaderActivity, /QuataDocumentReader\.pruneOwnedTempFiles\(this\)/);
  assert.match(androidDocumentReaderActivity, /runCatching \{\s*target\.delete\(\)\s*\}/);
  assert.match(androidDocumentReaderActivity, /UUID\.randomUUID\(\)/);
  assert.match(androidDocumentReaderActivity, /putExtra\(QuataDocumentReader\.EXTRA_OWNED_TEMP_PATH, path\)/);
  for (const activity of [pdfReaderActivity, viewRtfActivity, viewFilesActivity, csvReaderActivity, textReaderActivity]) {
    assert.match(activity, /cleanupOwnedTempFile/);
    assert.match(activity, /EXTRA_OWNED_TEMP_PATH/);
    assert.match(activity, /isChangingConfigurations\(?\)?/);
  }
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
  assert.ok(
    iosDocumentOpenService.indexOf("activePreview = preview") <
      iosDocumentOpenService.indexOf("runCatching(onPreviewAccepted)"),
    "iOS Quick Look must retain the active preview before adopting the temporary document lease.",
  );
  assert.ok(
    iosDocumentOpenService.indexOf("activeNavigationController = navigationController") <
      iosDocumentOpenService.indexOf("runCatching(onPreviewAccepted)"),
    "iOS Quick Look must retain its presented navigation controller before adopting the temporary document lease.",
  );
  assert.match(iosDocumentOpenService, /activeNavigationController: UINavigationController\?/);
  assert.match(iosDocumentOpenService, /val presentedController = activeNavigationController \?: preview\s*presentedController\.dismissViewControllerAnimated\(animated\) \{\s*dismissAndRelease\(\)\s*\}/);
  assert.match(iosDocumentOpenService, /IosQuickLookCloseTarget \{\s*dismissPreviewAndRelease\(animated = false\)\s*\}/);
  assert.match(iosDocumentOpenService, /continuation\.invokeOnCancellation \{[\s\S]*dismissPreviewAndRelease\(animated = false\)/);
  assert.ok(
    iosDocumentOpenService.indexOf("presenter.presentViewController") <
      iosDocumentOpenService.indexOf("runCatching(onPreviewAccepted)"),
    "iOS Quick Look must adopt the temporary document lease only after UIKit accepts presentation.",
  );
  assert.match(iosDocumentOpenService, /document_open_preview_presentation_failed/);
  assert.match(iosDocumentOpenService, /else if \(continuation\.isActive\) \{\s*runCatching\(onPreviewAccepted\)\s*continuation\.resume\(PlatformResult\.Success\(Unit\)\)/);
  assert.match(iosAttachmentPreviewService, /onPreviewAccepted = \{ adoptedByDismissAwareViewer = true \}/);
  assert.doesNotMatch(iosAttachmentPreviewService, /is PlatformResult\.Success -> \{\s*adoptedByDismissAwareViewer = documentOpener is IosDismissAwareDocumentOpenService/);
  assert.doesNotMatch(iosDocumentOpenService, /dismissViewControllerAnimated\(false, completion = null\)\s*dismissAndRelease\(\)/);
});

test("iOS document attachment evidence observes real Quick Look presentation and reopen lifecycle", () => {
  assert.match(iosUiTest, /let documentName = nonEmpty\(environment\["QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_NAME"\]\)/);
  assert.match(iosWrapper, /"\$\{QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_NAME:-\}"/);
  assert.match(iosRunner, /export QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_NAME=/);
  assert.match(iosWrapper, /attachment_document_name/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_NAME'\] = attachment_document_name/);
  assert.match(iosUiTest, /assertQuickLookPresented\(documentName: documentName, context: "Chat document attachment first open", in: app\)/);
  assert.match(iosUiTest, /closeQuickLook\(documentName: documentName, context: "Chat document attachment first open", in: app\)/);
  assert.match(iosUiTest, /assertQuickLookPresented\(documentName: documentName, context: "Chat document attachment reopen", in: app\)/);
  assert.match(iosUiTest, /closeQuickLook\(documentName: documentName, context: "Chat document attachment reopen", in: app\)/);
  assert.match(iosUiTest, /private func assertQuickLookPresented\(documentName: String, context: String, in app: XCUIApplication\)/);
  assert.match(iosUiTest, /private func closeQuickLook\(documentName: String, context: String, in app: XCUIApplication\)/);
  assert.match(iosUiTest, /matching\(identifier: "chat\.attachment\.document\.open"\)/);
  assert.match(iosUiTest, /documentOpen\.exists && documentOpen\.isHittable/);
  assert.doesNotMatch(iosUiTest, /document-viewer-status-root"\)\.firstMatch\.waitForExistence\(timeout: 15\)/);
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
  assert.match(commonHost, /ChatAudioPlaybackController\([\s\S]*audioPlayer = audioPlayer[\s\S]*messages = \{ viewModel\.uiState\.value\.messages \}[\s\S]*progressRefreshIntervalMillis = audioPlaybackProgressRefreshIntervalMillis/);
  assert.doesNotMatch(commonHost, /AudioPlaybackState\(isLoaded = true,\s*isPlaying = true\)/);
  assert.doesNotMatch(commonHost, /LaunchedEffect\(activeAudioReference/);
  assert.doesNotMatch(commonHost, /didAudioPlaybackFinish/);
  assert.match(commonAudioController, /audioPlayer\.events\.collect/);
  assert.match(commonAudioController, /AudioPlaybackEvent\.Ended/);
  assert.match(commonAudioController, /AudioPlaybackEvent\.Failed/);
  assert.match(commonAudioController, /private var generation = 0L/);
  assert.match(commonAudioController, /private val activeOperations = mutableSetOf<Job>\(\)/);
  assert.match(commonAudioController, /private var seekOperation: Job\? = null/);
  assert.match(commonAudioController, /seekOperation\?\.cancel\(\)[\s\S]*seekOperation = launchSerial\(cancelActive = false\)/);
  assert.match(commonAudioController, /operationsToCancel\.forEach \{ it\.cancel\(\) \}/);
  assert.match(commonAudioController, /operationsToCancel\.forEach \{ it\.join\(\) \}/);
  assert.match(commonAudioController, /requestNewPlaybackGeneration\(\)/);
  assert.match(commonAudioController, /event\.state\.sessionId != 0L && event\.state\.sessionId != current\.playback\.sessionId/);
  assert.match(commonAudioController, /isTerminalPlaybackFailure\(\)/);
  assert.match(commonAudioController, /withContext\(NonCancellable\) \{ audioPlayer\.stop\(\) \}/);
  assert.match(commonAudioController, /current\.playback\.phase == AudioPlaybackPhase\.Failed \|\| !current\.playback\.isLoaded -> startNewPlayback/);
  assert.match(commonAudioController, /nextConsecutiveAudioMessage\(messages\(\), key\)/);
  assert.match(commonAudioController, /audioPlayer\.seekTo/);
  assert.match(androidChatAttachmentAudioPlayerService, /class AndroidChatAttachmentAudioPlayerService/);
  assert.match(androidChatAttachmentAudioPlayerService, /delegate\.stop\(\)/);
  assert.match(androidChatAttachmentAudioPlayerService, /resolver\.resolve\(file\)/);
  assert.match(androidChatAttachmentAudioPlayerService, /delegate\.load\(resolvedFile\)/);
  assert.match(androidChatAttachmentAudioPlayerService, /AndroidChatAttachmentFileCacheAudioResolver/);
  assert.match(appContainer, /AndroidChatAttachmentAudioPlayerService/);
  assert.match(appContainer, /AndroidChatAttachmentFileCacheAudioResolver/);
  assert.match(iosAvPlayerAudioEngine, /activePlayer\.currentTime = Double\(boundedMillis\) \/ 1_000\.0/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /ios_avplayer_seek_not_completed/);
  assert.match(iosAvPlayerAudioEngine, /listener\?\.playbackStateChanged\(\)/);
  assert.doesNotMatch(commonAudioPolicy, /currentIndex - 1/);
  assert.doesNotMatch(commonAudioPolicy, /isNearEnd/);
  assert.doesNotMatch(commonAudioPolicy, /didAudioPlaybackFinish/);
  assert.match(commonAudioPolicy, /messages\.sortedWith/);
  assert.match(commonAudioPolicy, /Instant\.parse\(sentAt\)\.toEpochMilliseconds\(\)/);
  assert.match(commonAudioPolicy, /if \(current\.isDeleted\) return null/);
  assert.match(commonAudioPolicy, /ordered\.getOrNull\(currentIndex \+ 1\)/);
  assert.match(androidDocumentOpenService, /Only content URIs are allowed/);
  assert.doesNotMatch(androidDocumentOpenService, /"https" -> parsed\.takeIf/);
  assert.doesNotMatch(androidDocumentReaderActivity, /instanceFollowRedirects = false/);
  assert.doesNotMatch(androidDocumentReaderActivity, /HttpURLConnection/);
  assert.doesNotMatch(androidDocumentReaderActivity, /downloadUri\(/);
  assert.doesNotMatch(androidDocumentReaderActivity, /"https" ->/);
  assert.match(androidDocumentReaderActivity, /copyBounded\(input, output\)/);
  assert.doesNotMatch(androidDocumentReaderActivity, /input\.copyTo\(output\)/);
  assert.match(androidDocumentReaderActivity, /if \(total > MaxDocumentReaderBytes\)/);
  assert.match(androidDocumentReaderActivity, /showOpenErrorOrChooser\(source\)/);
  assert.match(androidDocumentReaderActivity, /showOpenErrorOrChooser\(activeSourceUri \?: path\.toUri\(\)\)/);
  assert.match(androidDocumentReaderActivity, /Intent\.createChooser/);
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
  assert.match(androidUiTest, /val audioMessageId = optionalArgument\("quataChatActionsAudioMessageId"\)/);
  assert.match(androidUiTest, /val nextAudioMessageId = optionalArgument\("quataChatActionsNextAudioMessageId"\)/);
  assert.match(androidUiTest, /val nextAudioName = optionalArgument\("quataChatActionsNextAudioName"\)/);
  assert.match(androidUiTest, /val imageMessageId = optionalArgument\("quataChatActionsImageMessageId"\)/);
  assert.match(androidUiTest, /val videoMessageId = optionalArgument\("quataChatActionsVideoMessageId"\)/);
  assert.match(androidUiTest, /"attachments-audio" -> listOf\(chatUrl, documentProbe, documentName, audioProbe, audioName, audioUrl, audioMessageId, nextAudioMessageId, nextAudioName, imageProbe, imageMessageId, videoProbe, videoMessageId, audioRecordingMarker\)/);
  assert.match(androidUiTest, /runAttachmentsAudioStage\(\s*chatUrl = chatUrl\.orEmpty\(\),\s*documentProbe = documentProbe\.orEmpty\(\),\s*documentName = documentName\.orEmpty\(\),\s*audioUrl = audioUrl\.orEmpty\(\),\s*audioMessageId = audioMessageId\.orEmpty\(\),/);
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
  assert.match(androidUiTest, /audioAttachmentStateMatcher\(audioName, ChatAudioAttachmentStatePlaying\)/);
  assert.match(androidUiTest, /audioAttachmentStateMatcher\(nextAudioName, ChatAudioAttachmentStatePlaying\)/);
  assert.match(androidUiTest, /waitForAudioProgressToStart\(audioName\)/);
  assert.match(androidUiTest, /private fun waitForAudioProgressToStart\(name: String, timeoutMillis: Long = 20_000\)/);
  assert.match(androidUiTest, /private fun waitForAudioAttachment\(messageId: String, name: String, context: String, timeoutMillis: Long = 45_000\)/);
  assert.match(androidUiTest, /val audioMatcher = hasTestTag\(ChatAudioAttachmentPlayerTestTag\) and hasAnyDescendant\(hasAudioDescription\(name\)\)/);
  assert.match(androidUiTest, /performScrollToNode\(chatMessageMatcher\(messageId\)\)/);
  assert.match(androidUiTest, /visibleNodes\(audioMatcher\)\.isNotEmpty\(\)/);
  assert.match(androidUiTest, /waitForAudioAttachment\(audioMessageId, audioName, "audio attachment message"\)/);
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
  assert.match(iosUiTest, /audioToggle\.tap\(\)/);
  assert.match(iosUiTest, /waitForAudioPhase\(audioName: audioName, phase: "chat\.attachment\.audio\.state\.playing"/);
  assert.match(iosUiTest, /let pauseToggle = audioToggleElement\(audioName: audioName, action: "Pausar", fallbackAction: "Pause", in: app\)/);
  assert.match(iosUiTest, /pauseToggle\.tap\(\)/);
  assert.match(iosUiTest, /waitForAudioPhase\(audioName: audioName, phase: "chat\.attachment\.audio\.state\.paused"/);
  assert.match(iosUiTest, /let resumeToggle = audioToggleElement\(audioName: audioName, action: "Reproducir", fallbackAction: "Play", in: app\)/);
  assert.match(iosUiTest, /resumeToggle\.tap\(\)/);
  assert.match(iosUiTest, /RunLoop\.current\.run\(until: Date\(\)\.addingTimeInterval\(1\.5\)\)/);
  assert.match(iosUiTest, /RunLoop\.current\.run\(until: Date\(\)\.addingTimeInterval\(12\)\)/);
  assert.match(iosWrapper, /require_ios_audio_evidence_events\(\)/);
  assert.match(iosWrapper, /quata-ios-audio-evidence\.log/);
  assert.match(iosWrapper, /ios_audio_native_evidence_verified/);
  assert.match(iosWrapper, /PASS_IOS_AUDIO_NATIVE_EVENTS/);
  assert.match(iosAvPlayerAudioEngine, /recordEvidenceEvent\(activePlayer\.isPlaying \? "playing" : "play_requested"\)/);
  assert.match(iosAvPlayerAudioEngine, /if isEvidenceDiagnosticEnabled\(\), let activePlayer = player, activePlayer\.isPlaying \{\s*recordEvidenceEvent\("progress"\)/);
  assert.match(iosAvPlayerAudioEngine, /recordEvidenceEvent\("ended"\)[\s\S]*listener\?\.playbackEnded\(\)/);
  assert.doesNotMatch(iosUiTest, /ios-chat-audio-toggle-attempted/);
  assert.doesNotMatch(iosUiTest, /ios-chat-audio-seek-attempted/);
  assert.doesNotMatch(iosUiTest, /ios-chat-audio-consecutive-next-playing/);
  assert.doesNotMatch(iosUiTest, /ios-chat-audio-toggle-not-playing/);
  assert.match(iosWrapper, /ios-audio-native-events\.log/);
  assert.doesNotMatch(iosUiTest, /audioToggleElement\(audioName: nextAudioName, action: "Pausar", fallbackAction: "Pause"/);
  assert.match(commonHost, /ChatAudioPlaybackController\([\s\S]*audioPlayer = audioPlayer[\s\S]*progressRefreshIntervalMillis = audioPlaybackProgressRefreshIntervalMillis/);
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
  assert.match(iosUiTest, /propagateAttachmentsAudioEnvironment\(to: app\)/);
  assert.match(iosUiTest, /app\.launchEnvironment\[key\] = value/);
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
  assert.doesNotMatch(iosUiTest, /ios-chat-audio-seek-attempted/);
  assert.doesNotMatch(iosUiTest, /#chat-audio-e2e\?action=seek/);
  assert.match(iosUiTest, /setAudioProgress\(audioProgress, toNormalizedPosition: 0\.8\)/);
  assert.match(iosUiTest, /private func setAudioProgress\(_ progress: XCUIElement, toNormalizedPosition position: CGFloat\)/);
  assert.doesNotMatch(iosUiTest, /audioProgress[\s\S]{0,120}coordinate\(withNormalizedOffset: CGVector\(dx: 0\.95/);
  assert.match(iosUiTest, /chat\.attachment\.pending/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_AUDIO_RECORDING_MARKER/);
  assert.match(iosUiTest, /chat\.composer\.send/);
  assert.match(iosUiTest, /ios-chat-attachment-media-viewer/);
  assert.match(iosUiTest, /ios-chat-attachment-video-viewer/);
  assert.match(iosUiTest, /ios-chat-attachment-document-visible/);
  assert.match(iosUiTest, /ios-chat-attachment-document-quicklook-presented/);
  assert.match(iosUiTest, /ios-chat-attachment-document-quicklook-reopened/);
  assert.ok(iosUiTest.includes("?message=\\(encodedQuery(audioMessageId))"));
  const attachmentsAudioTest = iosUiTest.slice(
    iosUiTest.indexOf("func testAttachmentsAndAudioExposeSharedAnchors"),
    iosUiTest.indexOf("func testAttachmentPickerFixtureUsesSharedComposerAnchors"),
  );
  assert.ok(
    attachmentsAudioTest.indexOf("setAudioProgress(audioProgress, toNormalizedPosition: 0.8)") <
      attachmentsAudioTest.indexOf("verifyAudioRecordingComposer(marker: audioRecordingMarker, in: app)"),
  );
  assert.ok(
    attachmentsAudioTest.indexOf("verifyAudioRecordingComposer(marker: audioRecordingMarker, in: app)") >
      attachmentsAudioTest.indexOf("message: \\(encodedQuery(videoMessageId))"),
  );
  assert.doesNotMatch(iosUiTest, /matching\(identifier: "document-viewer-status-close"\)\.firstMatch\.tap\(\)/);
  assert.doesNotMatch(iosUiTest, /ios-chat-audio-toggle-attempted/);
  assert.match(iosUiTest, /Set\(\[documentProbe, audioProbe, imageProbe, videoProbe\]\)\.count/);
  assert.match(iosUiTest, /app\.terminate\(\)[\s\S]*?openDeepLink\("quata:\/\/egquata\.com\/#chat-/);
  assert.match(iosUiTest, /openChatMediaAttachment\([\s\S]*identifier: "chat\.attachment\.media\.video"[\s\S]*messageId: videoMessageId[\s\S]*markerProbe: videoProbe/);
  assert.doesNotMatch(iosUiTest, /waitForFocusedMessageVisible\(videoMessageId, in: app/);
  assert.doesNotMatch(iosUiTest, /waitForFocusedMessageVisible\(imageMessageId, in: app/);
  assert.match(iosUiTest, /matching\(identifier: "chat\.message\.[^"]*messageId[^"]*"\)/);
  assert.match(iosUiTest, /\.allElementsBoundByIndex/);
  assert.match(iosUiTest, /NSPredicate\(format: "identifier CONTAINS %@", "\.\\\(messageId\)"\)/);
  assert.match(iosUiTest, /candidates\.contains\(where: \{ visibleChatViewportArea\(\$0, in: app\) > 0 \}\)/);
  assert.match(iosUiTest, /scrollElementTowardViewport\(existing, in: app\)/);
  assert.doesNotMatch(iosUiTest, /focused\.exists \|\| message\.exists \|\| messageSpecificAnchor\.exists/);
  assert.match(iosUiTest, /candidates\.first\(where: \{ isElementActionablyVisibleInChatViewport\(\$0, in: app\) \}\)/);
  assert.match(iosUiTest, /candidates\.first\(where: \{ isElementVisibleInChatViewport\(\$0, in: app\) \}\)/);
  assert.match(iosUiTest, /media-anchor-offscreen/);
  assert.match(iosUiTest, /guard makeAudioAnchorVisible\(identifier: "chat\.attachment\.audio\.player", audioName: audioName/);
  assert.match(iosUiTest, /keepAudioElementAboveComposer\(identifier: "chat\.attachment\.audio\.player", audioName: audioName/);
  assert.match(iosUiTest, /audioElement\(identifier: identifier, audioName: audioName, in: app\)\.waitForExistence/);
  assert.match(iosUiTest, /guard makeAudioAnchorVisible\(identifier: "chat\.attachment\.audio\.toggle", audioName: audioName/);
  assert.match(iosUiTest, /guard makeAudioAnchorVisible\(identifier: "chat\.attachment\.audio\.progress", audioName: audioName/);
  assert.match(iosUiTest, /keepAudioElementAboveComposer\(identifier: "chat\.attachment\.audio\.progress", audioName: audioName/);
  assert.match(iosUiTest, /private func makeAudioAnchorVisible\(identifier: String, audioName: String, context: String, in app: XCUIApplication\) -> Bool/);
  assert.match(iosUiTest, /private func audioElement\(identifier: String, audioName: String, in app: XCUIApplication\) -> XCUIElement/);
  assert.match(iosUiTest, /identifier == %@ AND label CONTAINS\[c\] %@/);
  const makeChatAnchorVisible = iosUiTest.slice(
    iosUiTest.indexOf("private func makeChatAnchorVisible"),
    iosUiTest.indexOf("    private func openChatMediaAttachment"),
  );
  assert.match(makeChatAnchorVisible, /isElementActionablyVisibleInChatViewport\(anchor, in: app\)/);
  assert.match(makeChatAnchorVisible, /scrollElementTowardViewport\(anchor, in: app\)/);
  assert.match(makeChatAnchorVisible, /anchor-not-actionable/);
  assert.doesNotMatch(makeChatAnchorVisible, /return true\s*\}\s*$/);
  const keepElementAboveComposer = iosUiTest.slice(
    iosUiTest.indexOf("private func keepElementAboveComposer"),
    iosUiTest.indexOf("    private func propagatePickerFixtureEnvironment"),
  );
  assert.match(keepElementAboveComposer, /isElementActionablyVisibleInChatViewport\(element, in: app\)/);
  assert.match(keepElementAboveComposer, /scrollElementTowardViewport\(element, in: app\)/);
  assert.doesNotMatch(keepElementAboveComposer, /app\.swipeUp\(\)/);
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
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_MESSAGE_ID/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_AUDIO_MESSAGE_ID'\] = attachment_audio_message/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID'\] = attachment_image_message/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID'\] = attachment_video_message/);
  assert.match(iosWrapper, /env\['QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_MESSAGE_ID'\] = attachment_document_message/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_AUDIO_MESSAGE_ID/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_MESSAGE_ID/);
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
  assert.match(sharedFixtures, /function chatAttachmentFixtureMedia\(kind, platformLabel\)/);
  assert.match(sharedFixtures, /validM4aFixture/);
  assert.doesNotMatch(sharedFixtures, /extension: "wav"/);
  assert.doesNotMatch(sharedFixtures, /mimeType: "audio\/wav"/);
  assert.match(sharedFixtures, /mimeType: "audio\/mp4"/);
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
  assert.match(webRunner, /waitMediaAttachmentReadyNearCurrentPosition\([\s\S]*fixtures\.video\.name[\s\S]*"video"[\s\S]*fixtures\.video\.marker[\s\S]*"focused_video_attachment_not_ready_after_route"/);
  assert.match(webRunner, /messageId: fixtures\.image\.messageId/);
  assert.match(webRunner, /waitMediaAttachmentReadyNearCurrentPosition\([\s\S]*fixtures\.image\.name[\s\S]*"image"[\s\S]*fixtures\.image\.marker[\s\S]*"focused_image_attachment_not_ready_after_route"/);
  assert.match(webRunner, /markerSeen = markerSeen \|\| await waitMessageVisible/);
  assert.match(webRunner, /message_marker_seen_without_media_anchor/);
  assert.doesNotMatch(webRunner, /resolvedBy: "message_marker"/);
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
  assert.match(androidUiTest, /startsWith\(state\)\s*==\s*true/);
  assert.doesNotMatch(androidUiTest, /StateDescription\)\s*==\s*state/);
  assert.match(androidUiTest, /Audio attachment must report Playing only after native playback confirmation/);
  assert.doesNotMatch(iosUiTest, /#chat-audio-e2e\?action=seek/);
  assert.match(iosUiTest, /progress\.adjust\(toNormalizedSliderPosition: position\)/);
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

test("Web audio player only accepts already materialized local Blob URLs", () => {
  assert.match(browserAudioPlayer, /AbortController/);
  assert.match(browserAudioPlayer, /controller\.abort\(\)/);
  assert.match(browserAudioPlayer, /web_audio_reference_remote_unsupported/);
  assert.match(browserAudioPlayer, /web_audio_load_timeout/);
  assert.match(browserAudioPlayer, /completed = true/);
  assert.match(browserAudioPlayer, /if \(completed \|\| !playableSource\) return/);
  assert.match(browserAudioPlayer, /if \(completed\) return;\s*cleanup\(\)/);
  assert.match(browserAudioPlayer, /element\.src = playableSource/);
  assert.match(browserAudioPlayer, /const targetMillis = Number\(positionMillis\)/);
  assert.match(browserAudioPlayer, /element\.ended && durationMillis > 0 \? durationMillis/);
  assert.doesNotMatch(browserAudioPlayer, /browserAudioStop[\s\S]*revokeObjectURL/);
  assert.doesNotMatch(browserAudioPlayer, /globalThis\.fetch\(source/);
  assert.doesNotMatch(browserAudioPlayer, /globalThis\.URL\.createObjectURL\(blob\)/);
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

test("Android audio edge does not declare play failure from a fixed startup polling deadline", async () => {
  const androidPlatformServices = await source("core/src/androidMain/kotlin/com/quata/core/platform/AndroidPlatformServices.kt");
  assert.match(androidPlatformServices, /active\.playWhenReady = true/);
  assert.match(androidPlatformServices, /active\.play\(\)/);
  assert.match(androidPlatformServices, /onIsPlayingChanged\(isPlaying: Boolean\)/);
  assert.match(androidPlatformServices, /if \(isPlaying\) currentState\(AudioPlaybackPhase\.Playing\)/);
  assert.match(androidPlatformServices, /PlatformResult\.Success\(currentState\(\)\.copy\(isPlaying = false, phase = AudioPlaybackPhase\.Loading\)\)/);
  assert.doesNotMatch(androidPlatformServices, /awaitPlaybackState\(active, predicate = \{ it\.isPlaying \}\)/);
  assert.doesNotMatch(androidPlatformServices, /android_audio_play_not_started/);
  assert.doesNotMatch(androidPlatformServices, /PlatformResult\.Success\(awaitPlaybackState\(active, predicate = \{ it\.isPlaying \}\)\)/);
});

test("iOS audio edge requests playback and reports native AVFoundation state", () => {
  assert.match(iosAvPlayerAudioEngine, /if !activePlayer\.play\(\)/);
  assert.match(iosAvPlayerAudioEngine, /installPlaybackStartWatchdog\(for: activePlayer, generation: generation\)/);
  assert.match(iosAvPlayerAudioEngine, /listener\?\.playbackStateChanged\(\)/);
  assert.match(iosAvPlayerAudioEngine, /isPlaying: activePlayer\?\.isPlaying \?\? false/);
  assert.match(iosAvPlayerAudioEngine, /func audioPlayerDidFinishPlaying\(_ player: AVAudioPlayer, successfully flag: Bool\)/);
  assert.match(iosAvPlayerAudioEngine, /func audioPlayerDecodeErrorDidOccur\(_ player: AVAudioPlayer, error: \(any Error\)\?\)/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /player\.rate > 0/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /AVPlayerItemDidPlayToEndTime/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /statusObservation = item\.observe/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /Date\(\)\.addingTimeInterval\(1\.0\)/);
  assert.doesNotMatch(iosAvPlayerAudioEngine, /ios_avplayer_play_not_started/);
});
