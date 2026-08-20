import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const webRunner = readFileSync(new URL("./post-publish-web-evidence.mjs", import.meta.url), "utf8");
const androidRunner = readFileSync(new URL("./post-publish-android-evidence.mjs", import.meta.url), "utf8");
const iosRunner = readFileSync(new URL("./post-publish-ios-evidence.mjs", import.meta.url), "utf8");
const iosWrapper = readFileSync(new URL("./run-ios-post-publish-ui-test.sh", import.meta.url), "utf8");
const sharedFixtures = readFileSync(new URL("./e2e-fixtures/chat-attachments.mjs", import.meta.url), "utf8");
const mainActivity = readFileSync(new URL("../app/src/main/java/com/quata/MainActivity.kt", import.meta.url), "utf8");
const androidAppNavGraph = readFileSync(new URL("../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt", import.meta.url), "utf8");
const androidCreatePostScreen = readFileSync(new URL("../app/src/main/java/com/quata/feature/postcomposer/presentation/CreatePostScreen.kt", import.meta.url), "utf8");
const androidCreatePostViewModel = readFileSync(new URL("../app/src/main/java/com/quata/feature/postcomposer/presentation/CreatePostAndroidViewModel.kt", import.meta.url), "utf8");
const androidPostPublishTest = readFileSync(new URL("../app/src/androidTest/java/com/quata/feature/postcomposer/presentation/PostPublishRealInstrumentedTest.kt", import.meta.url), "utf8");
const iosPostPublishTest = readFileSync(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedPostPublishUITests.swift", import.meta.url), "utf8");
const commonCreatePostRoot = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/CreatePostRoot.kt", import.meta.url), "utf8");
const commonCreatePostMediaPermissions = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/CreatePostMediaPermissions.kt", import.meta.url), "utf8");
const commonCreatePostViewModel = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/CreatePostViewModel.kt", import.meta.url), "utf8");
const commonMediaSourceForm = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerMediaSourceFormContent.kt", import.meta.url), "utf8");
const commonPublishButton = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerPublishButtonContent.kt", import.meta.url), "utf8");
const commonDestinationSelector = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerDestinationSelectorContent.kt", import.meta.url), "utf8");
const commonLocationSection = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerLocationSectionContent.kt", import.meta.url), "utf8");
const commonLocationEditor = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerLocationTextEditorContent.kt", import.meta.url), "utf8");
const commonComposerRepository = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/data/ActorBoundPostComposerRepository.kt", import.meta.url), "utf8");
const commonFailOnceComposerRepository = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/data/FailOncePostComposerRepository.kt", import.meta.url), "utf8");
const commonDestinationEvidenceRepository = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/data/DestinationEvidencePostComposerRepository.kt", import.meta.url), "utf8");
const commonFailAfterUploadTransport = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/data/FailInsertAfterUploadComposerTransport.kt", import.meta.url), "utf8");
const commonSubmissionFeedback = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerSubmissionFeedbackContent.kt", import.meta.url), "utf8");
const iosComposerHost = readFileSync(new URL("../feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/presentation/IosComposerHost.kt", import.meta.url), "utf8");
const iosApp = readFileSync(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8");
const webPostComposerHost = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerHost.kt", import.meta.url), "utf8");
const webPostComposerBridge = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerE2eBridge.kt", import.meta.url), "utf8");
const webPostComposerRoute = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerRoute.kt", import.meta.url), "utf8");
const corePlatformServiceContracts = readFileSync(new URL("../core/src/commonMain/kotlin/com/quata/core/platform/PlatformServiceContracts.kt", import.meta.url), "utf8");
const androidPermissionHost = readFileSync(new URL("../app/src/main/java/com/quata/core/platform/MainActivityPermissionHost.kt", import.meta.url), "utf8");
const androidPlatformServices = readFileSync(new URL("../core/src/androidMain/kotlin/com/quata/core/platform/AndroidPlatformServices.kt", import.meta.url), "utf8");
const browserPermissionService = readFileSync(new URL("../core/src/wasmJsMain/kotlin/com/quata/core/platform/BrowserPermissionService.wasm.kt", import.meta.url), "utf8");
const iosPermissionAggregator = readFileSync(new URL("../core/src/iosMain/kotlin/com/quata/core/platform/IosCoreLocationHost.kt", import.meta.url), "utf8");
const androidPickerCameraRunner = readFileSync(new URL("./post-picker-camera-android-evidence.mjs", import.meta.url), "utf8");
const webPickerCameraRunner = readFileSync(new URL("./post-picker-camera-web-evidence.mjs", import.meta.url), "utf8");
const iosPickerCameraRunner = readFileSync(new URL("./post-picker-camera-ios-evidence.mjs", import.meta.url), "utf8");
const iosPickerCameraWrapper = readFileSync(new URL("./run-ios-post-picker-camera-ui-test.sh", import.meta.url), "utf8");
const androidDestinationRunner = readFileSync(new URL("./post-destination-android-evidence.mjs", import.meta.url), "utf8");
const webDestinationRunner = readFileSync(new URL("./post-destination-web-evidence.mjs", import.meta.url), "utf8");
const iosDestinationRunner = readFileSync(new URL("./post-destination-ios-evidence.mjs", import.meta.url), "utf8");
const iosDestinationWrapper = readFileSync(new URL("./run-ios-post-destination-ui-test.sh", import.meta.url), "utf8");
const androidImageEditorDialog = readFileSync(new URL("../app/src/main/java/com/quata/feature/postcomposer/imageeditor/QuataImageEditorDialog.kt", import.meta.url), "utf8");
const commonImageEditorModels = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/imageeditor/ImageEditorModels.kt", import.meta.url), "utf8");
const commonPostImageEditorContent = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/imageeditor/PostImageEditorContent.kt", import.meta.url), "utf8");
const commonEditorScaffold = readFileSync(new URL("../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataEditorScaffold.kt", import.meta.url), "utf8");
const androidImageEditorRunner = readFileSync(new URL("./post-image-editor-android-evidence.mjs", import.meta.url), "utf8");
const webImageEditorRunner = readFileSync(new URL("./post-image-editor-web-evidence.mjs", import.meta.url), "utf8");
const iosImageEditorRunner = readFileSync(new URL("./post-image-editor-ios-evidence.mjs", import.meta.url), "utf8");
const iosImageEditorWrapper = readFileSync(new URL("./run-ios-post-image-editor-ui-test.sh", import.meta.url), "utf8");
const androidVideoEditorDialog = readFileSync(new URL("../app/src/main/java/com/quata/feature/postcomposer/videoeditor/QuataVideoEditor.kt", import.meta.url), "utf8");
const commonVideoEditorModels = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/videoeditor/VideoEditorModels.kt", import.meta.url), "utf8");
const commonPostVideoEditorContent = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/videoeditor/PostVideoEditorContent.kt", import.meta.url), "utf8");
const commonPostVideoEditorSpec = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/videoeditor/PostVideoEditorExportSpec.kt", import.meta.url), "utf8");
const commonCaptionDocumentWireCodec = readFileSync(new URL("../core/src/commonMain/kotlin/com/quata/core/captions/core/CaptionDocumentWireCodec.kt", import.meta.url), "utf8");
const webPostVideoEditor = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostVideoEditor.kt", import.meta.url), "utf8");
const webBuildGradle = readFileSync(new URL("../web/build.gradle.kts", import.meta.url), "utf8");
const iosPostVideoEditor = readFileSync(new URL("../feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/presentation/IosPostVideoEditor.kt", import.meta.url), "utf8");
const iosPostVideoEditorNativeDriver = readFileSync(new URL("../iosApp/iosApp/IosPostVideoEditorNativeDriver.swift", import.meta.url), "utf8");
const iosQuataApp = readFileSync(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8");
const iosInfoPlist = readFileSync(new URL("../iosApp/iosApp/Info.plist", import.meta.url), "utf8");
const androidVideoEditorRunner = readFileSync(new URL("./post-video-editor-android-evidence.mjs", import.meta.url), "utf8");
const webVideoEditorRunner = readFileSync(new URL("./post-video-editor-web-evidence.mjs", import.meta.url), "utf8");
const iosVideoEditorRunner = readFileSync(new URL("./post-video-editor-ios-evidence.mjs", import.meta.url), "utf8");
const iosVideoEditorWrapper = readFileSync(new URL("./run-ios-post-video-editor-ui-test.sh", import.meta.url), "utf8");

test("post publish web runner uses the shared reversible fixture and cleanup", () => {
  assert.match(webRunner, /createPostPublishFixture/);
  assert.match(webRunner, /pollPostPublishFixture/);
  assert.match(webRunner, /cleanupPostPublishFixture/);
  assert.match(webRunner, /selectPostPublishDestinationFixture/);
  assert.match(webRunner, /clickSemanticElement\(page, `composer-type-\$\{composerType\}`\)/);
  assert.match(webRunner, /const composerType = options\.mode === "image-location" \? "image" : "text"/);
  assert.match(webRunner, /composer-destination-option\.\$\{destination\.wallId\}/);
  assert.match(webRunner, /clickSemanticElement\(page, "composer-publish", \{ reinforcePhysical: true \}\)/);
  assert.match(webRunner, /expectedWallId/);
  assert.doesNotMatch(webRunner, /delete from public\.community_posts/);
});

test("post publish runner requires explicit reversible mutation opt-in", () => {
  assert.match(webRunner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  assert.match(androidRunner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  assert.match(iosRunner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  assert.match(iosWrapper, /QUATA_IOS_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  assert.match(webRunner, /I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION/);
  assert.match(androidRunner, /I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION/);
  assert.match(iosRunner, /I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION/);
});

test("post progress rollback evidence uses a shared fail-once repository and common retry anchor", () => {
  assert.match(commonFailOnceComposerRepository, /class FailOncePostComposerRepository/);
  assert.match(commonFailOnceComposerRepository, /delegate\.loadDestinations\(\)/);
  assert.match(commonFailOnceComposerRepository, /post_composer_e2e_forced_first_publish_failure/);
  assert.match(commonSubmissionFeedback, /ComposerFeedbackRetryTestTag = "composer-feedback-retry"/);
  assert.match(commonCreatePostRoot, /retryLabel = copy\.retry/);
  assert.match(commonCreatePostRoot, /lastFailedSubmitType/);
  assert.match(webPostComposerRoute, /quata-post-progress-rollback-e2e/);
  assert.match(webPostComposerRoute, /FailOncePostComposerRepository\(real\)/);
  assert.match(webPostComposerHost, /retryAvailable/);
  assert.match(webRunner, /--fail-once/);
  assert.match(webRunner, /composer-feedback-retry/);
  assert.match(androidAppNavGraph, /FailOncePostComposerRepository/);
  assert.match(androidRunner, /quataPostProgressRollbackFailOnce/);
  assert.match(androidPostPublishTest, /ComposerFeedbackRetryTestTag/);
  assert.match(iosApp, /QUATA_IOS_POST_PROGRESS_ROLLBACK_FAIL_ONCE/);
  assert.match(iosRunner, /--fail-once/);
  assert.match(iosWrapper, /QUATA_IOS_POST_PROGRESS_ROLLBACK_FAIL_ONCE/);
  assert.match(iosPostPublishTest, /waitForRetryAndTap/);
});

test("post storage rollback evidence forces a post-upload failure and verifies shared Storage cleanup", () => {
  assert.match(commonFailAfterUploadTransport, /class FailInsertAfterUploadComposerTransport/);
  assert.match(commonFailAfterUploadTransport, /request\.imageUrl != null \|\| request\.videoUrl != null/);
  assert.match(commonFailAfterUploadTransport, /post_composer_e2e_forced_insert_after_upload_failure/);
  assert.match(sharedFixtures, /snapshotPostImageStorageObjects/);
  assert.match(sharedFixtures, /waitForPostImageStorageRollback/);
  assert.match(sharedFixtures, /cleanupPostPublishStorageObjects/);
  assert.match(sharedFixtures, /bucket_id = 'community-posts'/);
  assert.match(webPostComposerRoute, /quata-post-storage-rollback-e2e/);
  assert.match(webPostComposerRoute, /FailInsertAfterUploadComposerTransport/);
  assert.match(webRunner, /--fail-after-upload/);
  assert.match(webRunner, /quata_post_storage_rollback_fail_after_upload/);
  assert.match(webRunner, /fail_after_upload_requires_image_location_mode/);
  assert.match(androidRunner, /--fail-after-upload/);
  assert.match(androidRunner, /quataPostStorageRollbackFailAfterUpload/);
  assert.match(androidPostPublishTest, /android-post-storage-rollback-after-error/);
  assert.match(mainActivity, /POST_STORAGE_ROLLBACK_FAIL_AFTER_UPLOAD_FOR_EVIDENCE/);
  assert.match(iosRunner, /--fail-after-upload/);
  assert.match(iosRunner, /QUATA_IOS_POST_STORAGE_ROLLBACK_FAIL_AFTER_UPLOAD/);
  assert.match(iosWrapper, /QUATA_IOS_POST_STORAGE_ROLLBACK_FAIL_AFTER_UPLOAD/);
  assert.match(iosApp, /FailInsertAfterUploadComposerTransport/);
  assert.match(iosPostPublishTest, /ios-post-storage-rollback-after-error/);
});

test("web composer submit uses a localhost opt-in bridge without replacing common UI state", () => {
  assert.match(webPostComposerHost, /installWebPostComposerE2eBridge/);
  assert.match(webPostComposerHost, /CreatePostUiEvent\.TextChanged/);
  assert.match(webPostComposerHost, /CreatePostUiEvent\.ImageSelected/);
  assert.match(webPostComposerHost, /CreatePostUiEvent\.VideoSelected/);
  assert.match(webPostComposerHost, /CreatePostUiEvent\.LocationLabelChanged/);
  assert.match(webPostComposerHost, /viewModel\.submit\(PostComposerType\.Text\)/);
  assert.match(webPostComposerHost, /viewModel\.submit\(PostComposerType\.Image\)/);
  assert.match(webPostComposerHost, /textLength/);
  assert.match(webPostComposerHost, /locationLabel/);
  assert.match(webPostComposerHost, /selectedDestinationWallId/);
  assert.match(webPostComposerBridge, /quata-post-publish-e2e/);
  assert.match(webPostComposerBridge, /__quataPostComposerE2eProduct/);
  assert.match(webPostComposerBridge, /setText:/);
  assert.match(webPostComposerBridge, /setImage:/);
  assert.match(webPostComposerBridge, /setVideo:/);
  assert.match(webPostComposerBridge, /setLocation:/);
  assert.match(webPostComposerBridge, /submitImage:/);
  assert.match(webPostComposerBridge, /state:/);
  assert.match(webRunner, /__quataPostComposerE2eProduct/);
  assert.match(webRunner, /image-location/);
  assert.match(webRunner, /validPngFixture/);
  assert.match(webRunner, /expectedLocationLabel/);
  assert.match(webRunner, /web_text_written_by_localhost_opt_in_product_bridge_after_compose_keyboard_limit/);
  assert.match(webRunner, /web_post_publish_submitted_by_localhost_opt_in_product_bridge_after_visual_route/);
  assert.match(webRunner, /quata-supabase-url/);
  assert.match(webRunner, /post_publish_ui_error/);
  assert.doesNotMatch(webPostComposerHost, /publishButton =/);
});

test("common destination selector exposes stable anchors for all platform replays", () => {
  assert.match(commonDestinationSelector, /ComposerDestinationSelectorTestTag = "composer-destination-selector"/);
  assert.match(commonDestinationSelector, /ComposerDestinationSelectedTestTag = "composer-destination-selected"/);
  assert.match(commonDestinationSelector, /ComposerDestinationLoadingTestTag = "composer-destination-loading"/);
  assert.match(commonDestinationSelector, /ComposerDestinationErrorTestTag = "composer-destination-error"/);
  assert.match(commonDestinationSelector, /ComposerDestinationEmptyTestTag = "composer-destination-empty"/);
  assert.match(commonDestinationSelector, /ComposerDestinationRetryTestTag = "composer-destination-retry"/);
  assert.match(commonDestinationSelector, /composer-destination-option\.\$\{destination\.wallId\}/);
  assert.match(commonDestinationSelector, /contentDescription = "Destino:/);
  assert.match(commonCreatePostViewModel, /ReloadDestinations -> loadDestinations\(\)/);
  assert.match(commonCreatePostViewModel, /destinationRequired/);
});

test("post destination evidence uses common repository states and platform semantic anchors", () => {
  assert.match(commonDestinationEvidenceRepository, /class DestinationEvidencePostComposerRepository/);
  assert.match(commonDestinationEvidenceRepository, /"empty" -> Result\.success\(emptyList\(\)\)/);
  assert.match(commonDestinationEvidenceRepository, /"failure" -> Result\.failure/);
  assert.match(commonDestinationEvidenceRepository, /"multiple" -> Result\.success/);
  assert.match(commonDestinationEvidenceRepository, /e2e-wall-bata/);
  assert.match(webPostComposerRoute, /quata-post-destination-e2e/);
  assert.match(webPostComposerRoute, /I_ACCEPT_WEB_POST_DESTINATION_FIXTURE/);
  assert.match(webPostComposerRoute, /DestinationEvidencePostComposerRepository/);
  assert.match(mainActivity, /EXTRA_POST_DESTINATION_EVIDENCE_MODE/);
  assert.match(androidAppNavGraph, /postDestinationEvidenceMode/);
  assert.match(androidAppNavGraph, /DestinationEvidencePostComposerRepository/);
  assert.match(androidPostPublishTest, /authenticatedUserExercisesDestinationStatesFromCommonComposer/);
  assert.match(androidPostPublishTest, /ComposerDestinationErrorTestTag/);
  assert.match(androidPostPublishTest, /ComposerDestinationEmptyTestTag/);
  assert.match(androidPostPublishTest, /e2e-wall-bata/);
  assert.match(iosApp, /QUATA_IOS_POST_DESTINATION_E2E_MODE/);
  assert.match(iosPostPublishTest, /testAuthenticatedSessionExercisesDestinationStatesFromCommonComposer/);
  assert.match(iosPostPublishTest, /composer-destination-error/);
  assert.match(iosPostPublishTest, /composer-destination-empty/);
  assert.match(iosPostPublishTest, /e2e-wall-bata/);
  assert.match(webDestinationRunner, /POST-DESTINATION-WEB-REAL-001/);
  assert.match(webDestinationRunner, /composer-destination-error/);
  assert.match(webDestinationRunner, /composer-feedback-error/);
  assert.match(androidDestinationRunner, /POST-DESTINATION-ANDROID-REAL-001/);
  assert.match(androidDestinationRunner, /authenticatedUserExercisesDestinationStatesFromCommonComposer/);
  assert.match(iosDestinationRunner, /POST-DESTINATION-IOS-REAL-001/);
  assert.match(iosDestinationWrapper, /testAuthenticatedSessionExercisesDestinationStatesFromCommonComposer/);
  assert.match(iosDestinationWrapper, /IOS_POST_DESTINATION_UI_GATE_PASSED/);
  for (const runner of [webDestinationRunner, androidDestinationRunner, iosDestinationRunner]) {
    assert.doesNotMatch(runner, /community_posts/);
    assert.doesNotMatch(runner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  }
});

test("post location uses common anchors and the shared remote metadata codec", () => {
  assert.match(commonLocationSection, /ComposerLocationSectionTestTag = "composer-location-section"/);
  assert.match(commonLocationSection, /ComposerLocationValueTestTag = "composer-location-value"/);
  assert.match(commonLocationSection, /ComposerLocationEditTestTag = "composer-location-edit"/);
  assert.match(commonLocationSection, /contentDescription = "\$title: \$locationText"/);
  assert.match(commonLocationEditor, /ComposerLocationInputTestTag = "composer-location-input"/);
  assert.match(commonComposerRepository, /PostComposerType\.Image -> buildPostBodyWithMeta\(imageLocation = locationLabel, channel = "feed"\)/);
  assert.match(iosComposerHost, /val location: LocationService/);
  assert.match(iosComposerHost, /val permissions: PermissionService/);
  assert.match(iosComposerHost, /requestLocation = \{ resolved ->/);
  assert.match(iosComposerHost, /dependencies\.location\.currentLocation\(\)/);
  assert.match(iosApp, /location: services\.location/);
  assert.match(iosApp, /permissions: services\.permissions/);
});

test("shared post publish fixture owns community post cleanup and residue verification", () => {
  assert.match(sharedFixtures, /export async function cleanupPostPublishFixture/);
  assert.match(sharedFixtures, /delete from public\.community_post_likes/);
  assert.match(sharedFixtures, /delete from public\.community_comments/);
  assert.match(sharedFixtures, /delete from public\.community_posts/);
  assert.match(sharedFixtures, /cleanup_residue_detected:post_publish/);
});

test("post publish android runner delegates backend fixture ownership to shared helpers", () => {
  assert.match(androidRunner, /createPostPublishFixture/);
  assert.match(androidRunner, /pollPostPublishFixture/);
  assert.match(androidRunner, /cleanupPostPublishFixture/);
  assert.match(androidRunner, /selectPostPublishDestinationFixture/);
  assert.match(androidRunner, /quataPostPublishDestinationWallId/);
  assert.match(androidRunner, /quataPostPublishMode/);
  assert.match(androidRunner, /quataPostPublishLocationLabel/);
  assert.match(androidRunner, /expectedWallId/);
  assert.match(androidRunner, /expectedLocationLabel/);
  assert.match(androidRunner, /PostPublishRealInstrumentedTest/);
  assert.doesNotMatch(androidRunner, /delete from public\.community_posts/);
});

test("post publish android evidence uses common composer tags through the debug start route", () => {
  assert.match(mainActivity, /AppDestinations\.CreatePost\.route/);
  assert.match(androidPostPublishTest, /CreatePostCommonRootTestTag/);
  assert.match(androidPostPublishTest, /ComposerTextInputTestTag/);
  assert.match(androidPostPublishTest, /ComposerLocationSectionTestTag/);
  assert.match(androidPostPublishTest, /ComposerLocationValueTestTag/);
  assert.match(androidPostPublishTest, /createImageLocationEvidenceUri/);
  assert.match(androidPostPublishTest, /POST_PUBLISH_EVIDENCE_IMAGE_URI/);
  assert.match(androidPostPublishTest, /POST_PUBLISH_EVIDENCE_LOCATION_LABEL/);
  assert.match(mainActivity, /EXTRA_POST_PUBLISH_EVIDENCE_IMAGE_URI/);
  assert.match(mainActivity, /postPublishEvidenceImageUri = postPublishEvidenceImageUri/);
  assert.match(androidAppNavGraph, /postPublishEvidenceImageUri: String\? = null/);
  assert.match(androidAppNavGraph, /evidenceImageUri = postPublishEvidenceImageUri/);
  assert.match(androidCreatePostScreen, /evidenceImageUri: String\? = null/);
  assert.match(androidCreatePostScreen, /initialEvidenceImageUri = evidenceImageUri/);
  assert.match(androidCreatePostScreen, /initialEvidenceLocationLabel = evidenceLocationLabel/);
  assert.match(androidCreatePostScreen, /initialStep = if \(evidenceImageUri != null\) CreatePostStep\.Image else null/);
  assert.match(androidCreatePostViewModel, /CreatePostUiEvent\.ImageSelected\(initialEvidenceImageUri\)/);
  assert.match(androidCreatePostViewModel, /CreatePostUiEvent\.LocationLabelChanged\(initialEvidenceLocationLabel\)/);
  assert.doesNotMatch(androidPostPublishTest, /AndroidPostComposerEvidenceSeed/);
  assert.match(androidPostPublishTest, /if \(mode == "text"\)/);
  assert.match(androidPostPublishTest, /composer-type-text/);
  assert.match(androidPostPublishTest, /composer-destination-option\.\$destinationWallId/);
  assert.match(androidPostPublishTest, /ComposerPublishButtonTestTag/);
  assert.match(androidPostPublishTest, /START_DESTINATION_FOR_EVIDENCE/);
  assert.match(androidPostPublishTest, /"create_post"/);
});

test("post publish ios runner delegates backend fixture ownership to shared helpers", () => {
  assert.match(iosRunner, /createPostPublishFixture/);
  assert.match(iosRunner, /pollPostPublishFixture/);
  assert.match(iosRunner, /cleanupPostPublishFixture/);
  assert.match(iosRunner, /selectPostPublishDestinationFixture/);
  assert.match(iosRunner, /QUATA_IOS_POST_PUBLISH_DESTINATION_WALL_ID/);
  assert.match(iosRunner, /QUATA_IOS_POST_PUBLISH_MODE/);
  assert.match(iosRunner, /QUATA_IOS_POST_PUBLISH_LOCATION_LABEL/);
  assert.match(iosRunner, /image-location/);
  assert.match(iosRunner, /expectedWallId/);
  assert.match(iosRunner, /expectedLocationLabel/);
  assert.match(iosRunner, /run-ios-post-publish-ui-test\.sh/);
  assert.match(iosRunner, /mac_checkout_sha_matches_local_candidate/);
  assert.doesNotMatch(iosRunner, /delete from public\.community_posts/);
});

test("post publish ios wrapper seeds Keychain and requires executed XCTest markers", () => {
  assert.match(iosWrapper, /QuataIosAuthenticatedSessionSeederTests\/testSeedAuthenticatedSessionForVisualGates/);
  assert.match(iosWrapper, /QuataIosAuthenticatedPostPublishUITests\/testAuthenticatedSessionPublishesRealTextPost/);
  assert.match(iosWrapper, /QUATA_IOS_POST_PUBLISH_DESTINATION_WALL_ID/);
  assert.match(iosWrapper, /QUATA_IOS_POST_PUBLISH_MODE/);
  assert.match(iosWrapper, /QUATA_IOS_POST_PUBLISH_LOCATION_LABEL/);
  assert.match(iosWrapper, /check-ios-xctest-executed\.py/);
  assert.match(iosWrapper, /--require-terminal-success-marker/);
  assert.match(iosWrapper, /IOS_POST_PUBLISH_UI_GATE_PASSED/);
});

test("post publish ios UI test uses common semantic anchors", () => {
  assert.match(iosPostPublishTest, /quata-ios-composer-host/);
  assert.match(iosPostPublishTest, /feed\.action\.publish\./);
  assert.match(iosPostPublishTest, /create-post-common-root/);
  assert.match(iosPostPublishTest, /image-location/);
  assert.match(iosPostPublishTest, /assertImageLocationDraft/);
  assert.match(iosPostPublishTest, /composer-location-value/);
  assert.match(iosPostPublishTest, /app\.launchEnvironment\["QUATA_IOS_POST_PUBLISH_LOCATION_LABEL"\]/);
  assert.match(iosPostPublishTest, /composer-type-text/);
  assert.match(iosPostPublishTest, /composer-destination-option\.\\\(wallId\)/);
  assert.match(iosPostPublishTest, /composer-text-input/);
  assert.match(iosPostPublishTest, /composer-publish/);
  assert.match(iosPostPublishTest, /QUATA_IOS_POST_PROGRESS_ROLLBACK_FAIL_ONCE/);
  assert.match(iosPostPublishTest, /composer-feedback-success/);
});

test("common publish button exposes an accessibility action for iOS XCTest replay", () => {
  assert.match(commonPublishButton, /Button\(/);
  assert.match(commonPublishButton, /enabled = !isLoading/);
  assert.match(commonPublishButton, /onClick\(label = publishLabel\)/);
  assert.match(commonPublishButton, /if \(!isLoading\) onSubmit\(\)/);
  assert.doesNotMatch(commonPublishButton, /\.clickable\(/);
});

test("post picker/camera uses common semantic anchors before platform adapters", () => {
  for (const tag of [
    "composer-media.pick-image",
    "composer-media.capture-image",
    "composer-media.edit-image",
    "composer-media.pick-video",
    "composer-media.capture-video",
    "composer-media.edit-video",
    "composer-media.selected-image-preview",
    "composer-media.selected-video-preview",
    "composer-media.error",
  ]) {
    assert.match(commonCreatePostRoot, new RegExp(tag.replace(/[.]/g, "\\.")));
  }
  assert.match(commonCreatePostRoot, /m\.testTag\(ComposerPickImageTestTag\)/);
  assert.match(commonCreatePostRoot, /m\.testTag\(ComposerCaptureImageTestTag\)/);
  assert.match(commonCreatePostRoot, /m\.testTag\(ComposerPickVideoTestTag\)/);
  assert.match(commonCreatePostRoot, /m\.testTag\(ComposerCaptureVideoTestTag\)/);
  assert.match(commonCreatePostRoot, /contentDescription = ComposerSelectedImagePreviewTestTag/);
  assert.match(commonCreatePostRoot, /contentDescription = ComposerSelectedVideoPreviewTestTag/);
  assert.match(commonMediaSourceForm, /ComposerMediaErrorTestTag/);
  assert.match(commonMediaSourceForm, /contentDescription = "\$ComposerMediaErrorTestTag \$message"/);
  assert.match(commonCreatePostViewModel, /MediaSelectionFailed/);
});

test("post picker/camera evidence is opt-in and does not open native pickers under replay", () => {
  assert.match(webPostComposerRoute, /quata-post-picker-camera-e2e/);
  assert.match(webPostComposerRoute, /I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE/);
  assert.match(webPostComposerRoute, /gallery-image/);
  assert.match(webPostComposerRoute, /camera-video/);
  assert.match(iosComposerHost, /I_ACCEPT_IOS_POST_COMPOSER_PICKER_FIXTURE/);
  assert.match(iosComposerHost, /QUATA_IOS_POST_COMPOSER_PICKER_SOURCE/);
  assert.match(iosComposerHost, /captureVideo = \{ selectVideo\(FilePickerSource\.Camera\) \}/);
  assert.match(androidCreatePostScreen, /AndroidPostComposerPickerEvidence/);
  assert.match(androidCreatePostScreen, /GalleryImage/);
  assert.match(androidCreatePostScreen, /CameraVideo/);
  assert.match(androidCreatePostScreen, /mediaPermissionDenied/);
  assert.match(androidCreatePostScreen, /MediaSelectionFailed/);
  assert.match(mainActivity, /EXTRA_POST_COMPOSER_PICKER_EVIDENCE_SOURCE/);
  assert.match(androidAppNavGraph, /postComposerPickerEvidenceSource/);
});

test("post media permissions are common and preserve platform-specific permission APIs", () => {
  assert.match(corePlatformServiceContracts, /PlatformPermission \{ Camera, Microphone, Photos, Videos, Files, Location, Notifications, Contacts \}/);
  assert.match(commonCreatePostMediaPermissions, /CreatePostMediaPermissionRequest/);
  assert.match(commonCreatePostMediaPermissions, /GalleryImage -> listOf\(PlatformPermission\.Photos\)/);
  assert.match(commonCreatePostMediaPermissions, /GalleryVideo -> listOf\(PlatformPermission\.Videos\)/);
  assert.match(commonCreatePostMediaPermissions, /CameraVideo -> listOf\(PlatformPermission\.Camera, PlatformPermission\.Microphone\)/);
  assert.match(commonCreatePostMediaPermissions, /CreatePostMediaPermissionDeniedReason/);
  assert.match(androidPlatformServices, /PlatformPermission\.Videos -> if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.TIRAMISU\)/);
  assert.match(androidPlatformServices, /android\.Manifest\.permission\.READ_MEDIA_VIDEO/);
  assert.match(androidPermissionHost, /PlatformPermission\.Videos -> if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.TIRAMISU\)/);
  assert.match(browserPermissionService, /PlatformPermission\.Videos,/);
  assert.match(iosPermissionAggregator, /PlatformPermission\.Videos -> photos\.status\(permission\)/);
  assert.match(iosComposerHost, /CreatePostMediaPermissionRequest\.CameraVideo/);
  assert.match(iosComposerHost, /iosPostComposerEvidenceShouldBypassNativePermission/);
  assert.doesNotMatch(iosComposerHost, /CreatePostMediaPermissionRequest\.GalleryImage/);
  assert.match(webPostComposerRoute, /CreatePostMediaPermissionRequest\.CameraVideo/);
  assert.match(webPostComposerRoute, /allowUnavailable = setOf\(PlatformPermission\.Videos\)/);
  assert.match(webPostComposerHost, /CreatePostMediaPermissionDeniedReason/);
});

test("android post picker/camera runner exercises shared UI anchors with no backend mutation", () => {
  assert.match(androidPickerCameraRunner, /POST-PICKER-CAMERA-ANDROID-REAL-001/);
  assert.match(androidPickerCameraRunner, /authenticatedUserExercisesMediaSourceActionsFromCommonComposer/);
  assert.match(androidPickerCameraRunner, /gallery-image/);
  assert.match(androidPickerCameraRunner, /gallery-video/);
  assert.match(androidPickerCameraRunner, /post-picker-camera-long-video\.mp4/);
  assert.match(androidPostPublishTest, /quataPostComposerPickerVideoPath/);
  assert.match(androidPickerCameraRunner, /camera-image:cancelled/);
  assert.match(androidPickerCameraRunner, /permission-denied/);
  assert.match(androidPostPublishTest, /ComposerMediaErrorTestTag/);
  assert.match(androidPostPublishTest, /outcome == "failure" \|\| outcome == "unsupported" \|\| outcome == "permission-denied"/);
  assert.match(androidPostPublishTest, /android_permission_denied_must_use_common_media_permission_copy/);
  assert.match(androidPickerCameraRunner, /quataPostComposerPickerSource/);
  assert.match(androidPickerCameraRunner, /quataPostComposerPickerOutcome/);
  assert.doesNotMatch(androidPickerCameraRunner, /community_posts/);
  assert.doesNotMatch(androidPickerCameraRunner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
});

test("web post picker/camera runner exercises shared UI anchors with no backend mutation", () => {
  assert.match(webPickerCameraRunner, /POST-PICKER-CAMERA-WEB-REAL-001/);
  assert.match(webPickerCameraRunner, /I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE/);
  assert.match(webPickerCameraRunner, /longMp4FixturePath/);
  assert.match(webPickerCameraRunner, /post-picker-camera-long-video\.mp4/);
  assert.match(webPickerCameraRunner, /composer-media\.pick-image/);
  assert.match(webPickerCameraRunner, /composer-media\.capture-image/);
  assert.match(webPickerCameraRunner, /composer-media\.edit-image/);
  assert.match(webPickerCameraRunner, /hasImage/);
  assert.match(webPickerCameraRunner, /hasVideo/);
  assert.match(webPickerCameraRunner, /hasMediaError/);
  assert.match(webPickerCameraRunner, /composer-media\.error/);
  assert.match(webPickerCameraRunner, /permission-denied/);
  assert.match(webPickerCameraRunner, /permission_denied_must_use_common_media_permission_copy/);
  assert.match(webPostComposerHost, /put\("hasVideo", !state\.videoUri\.isNullOrBlank\(\)\)/);
  assert.match(webPostComposerHost, /MediaSelectionFailed/);
  assert.doesNotMatch(webPickerCameraRunner, /community_posts/);
  assert.doesNotMatch(webPickerCameraRunner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
});

test("ios post picker/camera runner exercises shared UI anchors with no backend mutation", () => {
  assert.match(iosPickerCameraRunner, /POST-PICKER-CAMERA-IOS-REAL-001/);
  assert.match(iosPickerCameraRunner, /run-ios-post-picker-camera-ui-test\.sh/);
  assert.match(iosPickerCameraRunner, /QUATA_IOS_POST_COMPOSER_PICKER_FIXTURE_OPT_IN/);
  assert.match(iosPickerCameraRunner, /I_ACCEPT_IOS_POST_COMPOSER_PICKER_FIXTURE/);
  assert.match(iosPickerCameraRunner, /QUATA_IOS_POST_COMPOSER_PICKER_MEDIA_TYPE/);
  assert.match(iosPickerCameraRunner, /longMp4FixturePath/);
  assert.match(iosPickerCameraRunner, /permission-denied/);
  assert.match(iosPickerCameraWrapper, /testAuthenticatedSessionExercisesMediaSourceActionsFromCommonComposer/);
  assert.match(iosPickerCameraWrapper, /IOS_POST_PICKER_CAMERA_UI_GATE_PASSED/);
  assert.ok(iosPostPublishTest.includes("composer-media.pick-\\(mediaType)"));
  assert.ok(iosPostPublishTest.includes("composer-media.capture-\\(mediaType)"));
  assert.ok(iosPostPublishTest.includes("composer-media.selected-\\(mediaType)-preview"));
  assert.match(iosPostPublishTest, /composer-media\.error/);
  assert.match(iosPostPublishTest, /Permission denied replay must expose the shared media permission copy/);
  assert.match(iosComposerHost, /dispatchIosComposerMediaResult/);
  assert.match(iosComposerHost, /MediaSelectionFailed/);
  assert.doesNotMatch(iosPickerCameraRunner, /community_posts/);
  assert.doesNotMatch(iosPickerCameraRunner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
});

test("post image editor exposes stable common anchors and Android uses the shared editor surface", () => {
  for (const tag of [
    "post-image-editor.root",
    "post-image-editor.preview",
    "post-image-editor.reset",
    "post-image-editor.rotate",
    "post-image-editor.crop",
    "post-image-editor.save",
  ]) {
    assert.match(commonImageEditorModels, new RegExp(tag.replace(/[.]/g, "\\.")));
  }
  assert.match(androidImageEditorDialog, /PostImageEditorDialogContent\(/);
  assert.match(androidImageEditorDialog, /postImageEditorGeometry\(/);
  assert.match(androidImageEditorDialog, /AndroidPostImageEditorPreview\(/);
  assert.match(androidImageEditorDialog, /context\.exportEditedImage\(/);
  assert.doesNotMatch(androidImageEditorDialog, /QuataEditorScaffold/);
  assert.doesNotMatch(androidImageEditorDialog, /ImageCropControls/);
  assert.doesNotMatch(androidImageEditorDialog, /ImageCropAdjustmentPane/);
  assert.match(commonPostImageEditorContent, /PostImageEditorDialogContent/);
  assert.match(commonPostImageEditorContent, /PostImageEditorRootTestTag/);
  assert.match(commonPostImageEditorContent, /PostImageEditorRotateTestTag/);
  assert.match(commonPostImageEditorContent, /PostImageEditorResetTestTag/);
  assert.match(commonPostImageEditorContent, /PostImageEditorSaveTestTag/);
});

test("post image editor runners exercise editor anchors without backend mutation", () => {
  assert.match(androidImageEditorRunner, /POST-IMAGE-EDITOR-ANDROID-REAL-001/);
  assert.match(androidImageEditorRunner, /authenticatedUserExercisesPostImageEditorFromCommonComposer/);
  assert.match(androidImageEditorRunner, /quataPostImageEditorEvidence/);
  assert.match(androidPostPublishTest, /ComposerEditImageTestTag/);
  assert.match(androidPostPublishTest, /PostImageEditorRootTestTag/);
  assert.match(androidPostPublishTest, /PostImageEditorSaveTestTag/);
  assert.match(androidPostPublishTest, /filterToOne\(hasClickAction\(\)\)/);

  assert.match(webImageEditorRunner, /POST-IMAGE-EDITOR-WEB-REAL-001/);
  assert.match(webImageEditorRunner, /quata-post-image-editor-e2e/);
  assert.match(webImageEditorRunner, /composer-media\.edit-image/);
  assert.match(webImageEditorRunner, /post-image-editor\.root/);
  assert.match(webImageEditorRunner, /post-image-editor\.rotate/);
  assert.match(webImageEditorRunner, /post-image-editor\.reset/);
  assert.match(webImageEditorRunner, /post-image-editor\.save/);
  assert.match(webImageEditorRunner, /state\.imageUri !== previous/);
  assert.match(webPostComposerRoute, /editImage =/);
  assert.match(webPostComposerRoute, /imageEditor =/);
  assert.match(webPostComposerRoute, /WebPostImageEditor/);
  assert.match(webPostComposerRoute, /quata_post_composer_image_editor_e2e_opt_in/);
  assert.match(webPostComposerHost, /put\("imageUri", it\.take\(220\)\)/);
  assert.match(webPostComposerHost, /imageEditorReference/);

  assert.match(iosImageEditorRunner, /POST-IMAGE-EDITOR-IOS-REAL-001/);
  assert.match(iosImageEditorRunner, /run-ios-post-image-editor-ui-test\.sh/);
  assert.match(iosImageEditorWrapper, /testAuthenticatedSessionExercisesPostImageEditorFromCommonComposer/);
  assert.match(iosImageEditorWrapper, /IOS_POST_IMAGE_EDITOR_UI_GATE_PASSED/);
  assert.match(iosPostPublishTest, /QUATA_IOS_POST_IMAGE_EDITOR_UI_E2E/);
  assert.match(iosPostPublishTest, /composer-media\.edit-image/);
  assert.match(iosPostPublishTest, /post-image-editor\.root/);
  assert.match(iosPostPublishTest, /post-image-editor\.save/);
  assert.match(iosComposerHost, /IosPostImageEditor/);
  assert.doesNotMatch(iosComposerHost, /I_ACCEPT_IOS_POST_COMPOSER_IMAGE_EDITOR_FIXTURE/);

  for (const runner of [androidImageEditorRunner, webImageEditorRunner, iosImageEditorRunner]) {
    assert.doesNotMatch(runner, /community_posts/);
    assert.doesNotMatch(runner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  }
});

test("post video editor exposes stable common anchors and Android forwards them to Compose UI", () => {
  for (const tag of [
    "post-video-editor.root",
    "post-video-editor.preview",
    "post-video-editor.mute",
    "post-video-editor.crop",
    "post-video-editor.captions",
    "post-video-editor.export",
    "post-video-editor.timeline",
    "post-video-editor.play-pause",
  ]) {
    assert.match(`${commonVideoEditorModels}\n${commonPostVideoEditorContent}`, new RegExp(tag.replace(/[.]/g, "\\.")));
  }
  assert.match(commonPostVideoEditorContent, /fun PostVideoEditorDialogContent/);
  assert.match(commonPostVideoEditorContent, /VideoCropMode/);
  assert.match(commonPostVideoEditorContent, /cropPanelOpen/);
  assert.match(commonPostVideoEditorContent, /captionsPanelOpen/);
  assert.match(commonPostVideoEditorContent, /captionOptions: List<CaptionStyleOption>/);
  assert.match(commonPostVideoEditorContent, /post-video-editor\.caption-style\.\$\{id \?: "none"\}/);
  assert.match(commonPostVideoEditorContent, /PostVideoEditorTimelineContent\(/);
  assert.match(commonPostVideoEditorContent, /timelineFrameContent: @Composable \(Int, Modifier\) -> Unit/);
  assert.doesNotMatch(commonPostVideoEditorContent, /timeline: @Composable \(Modifier\) -> Unit/);
  assert.match(commonPostVideoEditorSpec, /postVideoEditorStateAfterCropPan/);
  assert.match(commonPostVideoEditorSpec, /postVideoEditorStateAfterCropZoomChange/);
  assert.match(commonPostVideoEditorContent, /DialogProperties\(usePlatformDefaultWidth = false\)/);
  assert.match(androidVideoEditorDialog, /PostVideoEditorDialogContent\(/);
  assert.match(androidVideoEditorDialog, /CaptionStyleOption/);
  assert.doesNotMatch(androidVideoEditorDialog, /timeline = \{ timelineModifier ->/);
  assert.doesNotMatch(androidVideoEditorDialog, /private fun VideoTimeline\(/);
  assert.match(androidVideoEditorDialog, /VideoPreviewPane\(/);
  assert.doesNotMatch(androidVideoEditorDialog, /QuataEditorScaffold\(/);
  assert.doesNotMatch(androidVideoEditorDialog, /QuataEditorToolButton\(/);
});

test("post video editor runners exercise editor anchors without backend mutation", () => {
  assert.match(androidVideoEditorRunner, /POST-VIDEO-EDITOR-ANDROID-REAL-001/);
  assert.match(androidVideoEditorRunner, /authenticatedUserExercisesPostVideoEditorFromCommonComposer/);
  assert.match(androidVideoEditorRunner, /quataPostVideoEditorEvidence/);
  assert.match(androidVideoEditorRunner, /sample-video-vertical\.mp4/);
  assert.match(androidPostPublishTest, /ComposerEditVideoTestTag/);
  assert.match(androidPostPublishTest, /PostVideoEditorRootTestTag/);
  assert.match(androidPostPublishTest, /PostVideoEditorExportTestTag/);
  assert.match(androidPostPublishTest, /onAllNodesWithTag\(PostVideoEditorMuteTestTag/);
  assert.match(androidPostPublishTest, /onAllNodesWithTag\(PostVideoEditorExportTestTag/);

  assert.match(webVideoEditorRunner, /POST-VIDEO-EDITOR-WEB-REAL-001/);
  assert.match(webVideoEditorRunner, /quata-post-video-editor-e2e/);
  assert.match(webVideoEditorRunner, /composer-media\.edit-video/);
  assert.match(webVideoEditorRunner, /videoEditorOpen === true/);
  assert.match(webVideoEditorRunner, /kind: "alreadyOpen", value: "post-video-editor\.root"/);
  assert.match(webVideoEditorRunner, /post-video-editor\.root/);
  assert.match(webVideoEditorRunner, /post-video-editor\.export/);
  assert.match(webVideoEditorRunner, /__quataPostVideoEditorExport/);
  assert.match(webVideoEditorRunner, /assertWebVideoEditorExportParity/);
  assert.match(webVideoEditorRunner, /validSpeechMp4FixtureDataUrl/);
  assert.match(webVideoEditorRunner, /CAPTION_FIXTURE_TEXT/);
  assert.match(webVideoEditorRunner, /expectCaptions: true/);
  assert.match(webVideoEditorRunner, /web_video_editor_caption_text_not_real_transcript/);
  assert.match(webVideoEditorRunner, /const requiredOperations = \["trim", "mute", "crop"\]/);
  assert.match(webVideoEditorRunner, /requiredOperations\.push\("captions"\)/);
  assert.match(webVideoEditorRunner, /web_video_editor_caption_false_positive/);
  assert.match(webVideoEditorRunner, /saveAndProbeWebExport/);
  assert.match(webVideoEditorRunner, /ffprobe/);
  assert.match(webVideoEditorRunner, /probeCaptionPixels/);
  assert.match(webVideoEditorRunner, /web_video_editor_caption_pixels_missing/);
  assert.match(webVideoEditorRunner, /web_video_editor_physical_audio_stream_present_after_mute/);
  assert.match(webVideoEditorRunner, /web_video_editor_physical_trim_duration/);
  assert.match(webVideoEditorRunner, /web_video_editor_export_missing_operation:\$\{operation\}/);
  assert.match(webPostComposerRoute, /videoEditor =/);
  assert.match(webPostVideoEditor, /PostVideoEditorDialogContent/);
  assert.match(webPostVideoEditor, /webPostVideoEditorExportEdited/);
  assert.match(webPostVideoEditor, /__quataPostVideoEditorExportNative/);
  assert.match(webPostVideoEditor, /trimStart: value => trimStart/);
  assert.match(webPostVideoEditor, /cropMode: value => cropMode/);
  assert.match(webPostVideoEditor, /videoWidth > 0 && it\.videoHeight > 0/);
  assert.match(webPostVideoEditor, /videoAspectRatio = it\.videoWidth\.toFloat\(\) \/ it\.videoHeight\.toFloat\(\)/);
  assert.match(webPostVideoEditor, /backgroundCropLeft/);
  assert.match(webPostVideoEditor, /webPostVideoEditorReadMetadata/);
  assert.match(webPostVideoEditor, /webPostVideoEditorTranscribeCaptions/);
  assert.match(webPostVideoEditor, /vosk-browser/);
  assert.match(commonCaptionDocumentWireCodec, /object CaptionDocumentWireCodec/);
  assert.match(commonCaptionDocumentWireCodec, /fun encodeDocument\(document: CaptionDocument\): String/);
  assert.match(commonCaptionDocumentWireCodec, /fun decodeDocument\(value: String\): CaptionDocument/);
  assert.match(commonPostVideoEditorSpec, /val captionDocument: CaptionDocument\?/);
  assert.match(commonPostVideoEditorSpec, /captionDocument[\s\S]*\?\.trimTo\(trimStartMs, trimEndMs\)/);
  assert.match(commonPostVideoEditorSpec, /selectedCaptionStyle != null/);
  assert.match(webPostVideoEditor, /effectiveDurationMs/);
  assert.match(webPostVideoEditor, /drawCropFill\(backgroundCrop\)/);
  assert.match(webPostVideoEditor, /drawCropFit\(crop\)/);
  assert.match(webPostVideoEditor, /web_post_video_editor_caption_transcript_missing/);
  assert.match(webPostVideoEditor, /CaptionDocument\.fromWords/);
  assert.match(webPostVideoEditor, /CaptionDocumentWireCodec\.decodeWords/);
  assert.match(webPostVideoEditor, /CaptionDocumentWireCodec::encodeDocument/);
  assert.match(webPostVideoEditor, /captionSegments/);
  assert.match(webPostVideoEditor, /drawCaptionSegment/);
  assert.match(webBuildGradle, /packageWebVoskModels/);
  assert.match(webBuildGradle, /vosk-browser/);
  assert.match(webBuildGradle, /vosk_model_es/);
  assert.doesNotMatch(webPostVideoEditor, /val captionText = "Q/);
  assert.doesNotMatch(webPostVideoEditor, /captionWords = String\(captionStyle/);
  assert.match(webVideoEditorRunner, /invokeVideoEditorAction\(page, "cropMode", "Square"\)/);
  assert.match(webVideoEditorRunner, /web_video_editor_caption_document_missing_timings/);
  assert.match(webVideoEditorRunner, /web_video_editor_caption_document_invalid_word_timings/);
  assert.doesNotMatch(webPostVideoEditor, /webPostVideoEditorExportCopy/);
  assert.match(webPostVideoEditor, /MediaRecorder/);
  assert.match(webPostVideoEditor, /canvas\.captureStream/);
  assert.doesNotMatch(webPostComposerRoute, /quata_post_composer_video_editor_e2e_opt_in/);
  assert.match(webPostComposerHost, /put\("videoUri", it\.take\(220\)\)/);

  assert.match(iosVideoEditorRunner, /POST-VIDEO-EDITOR-IOS-REAL-001/);
  assert.match(iosVideoEditorRunner, /validSpeechMp4FixturePath/);
  assert.match(iosVideoEditorRunner, /CAPTION_FIXTURE_TEXT/);
  assert.match(iosVideoEditorRunner, /run-ios-post-video-editor-ui-test\.sh/);
  assert.match(iosVideoEditorWrapper, /testAuthenticatedSessionExercisesPostVideoEditorFromCommonComposer/);
  assert.match(iosVideoEditorWrapper, /speech-recognition/);
  assert.match(iosVideoEditorWrapper, /QUATA_IOS_POST_VIDEO_EDITOR_UI_TIMEOUT_SECONDS:=420/);
  assert.match(iosVideoEditorWrapper, /IOS_POST_VIDEO_EDITOR_UI_GATE_PASSED/);
  assert.match(iosPostPublishTest, /QUATA_IOS_POST_VIDEO_EDITOR_UI_E2E/);
  assert.match(iosPostPublishTest, /composer-media\.edit-video/);
  assert.match(iosPostPublishTest, /post-video-editor\.root/);
  assert.match(iosPostPublishTest, /post-video-editor\.caption-style\.Karaoke/);
  assert.match(iosPostPublishTest, /post-video-editor\.export/);
  assert.match(iosPostPublishTest, /addingTimeInterval\(180\)/);
  assert.match(iosPostPublishTest, /IOS_POST_VIDEO_EDITOR_UI_GATE_PASSED/);
  assert.match(iosComposerHost, /IosPostVideoEditor/);
  assert.match(iosPostVideoEditor, /PostVideoEditorDialogContent/);
  assert.match(iosPostVideoEditor, /IosVideoThumbnailService/);
  assert.match(iosPostVideoEditor, /IosPostVideoEditorNativeDriver/);
  assert.match(iosPostVideoEditor, /metadata\(source: PlatformFile\)/);
  assert.match(iosPostVideoEditor, /transcribe\(source: PlatformFile/);
  assert.match(iosPostVideoEditor, /iosPostVideoEditorTranscribeCaptions/);
  assert.match(iosPostVideoEditor, /metadata\?\.durationMs/);
  assert.match(iosPostVideoEditor, /metadata\?\.aspectRatio/);
  assert.match(iosPostVideoEditor, /postVideoEditorExportSpec\(state, videoAspectRatio, durationMs, captionDocument\)/);
  assert.match(iosPostVideoEditor, /CaptionDocument\.fromWords/);
  assert.match(iosPostVideoEditor, /CaptionDocumentWireCodec\.decodeWords/);
  assert.match(iosPostVideoEditor, /CaptionDocumentWireCodec::encodeDocument/);
  assert.doesNotMatch(iosPostVideoEditor, /val captionText = "Q/);
  assert.match(iosPostVideoEditor, /UIKitView/);
  assert.match(iosPostVideoEditorNativeDriver, /SFSpeechRecognizer/);
  assert.match(iosPostVideoEditorNativeDriver, /SFSpeechURLRecognitionRequest/);
  assert.match(iosPostVideoEditorNativeDriver, /QUATA_IOS_POST_VIDEO_EDITOR_TRANSCRIPTION_LOCALE/);
  assert.match(iosPostVideoEditorNativeDriver, /transcriptionTimeoutSeconds\(for: sourceUrl\)/);
  assert.match(iosPostVideoEditorNativeDriver, /shouldReportPartialResults = true/);
  assert.match(iosPostVideoEditorNativeDriver, /captionWire\(from: result\.bestTranscription\)/);
  assert.match(iosPostVideoEditorNativeDriver, /transcription\.segments/);
  assert.match(iosPostVideoEditorNativeDriver, /ios_post_video_editor_caption_transcript_timeout/);
  assert.match(iosPostVideoEditorNativeDriver, /CaptionDocumentWire\.parse/);
  assert.match(iosPostVideoEditorNativeDriver, /AVAssetExportSession/);
  assert.match(iosPostVideoEditorNativeDriver, /AVVideoCompositionCoreAnimationTool/);
  assert.match(iosPostVideoEditorNativeDriver, /request\.removeAudio/);
  assert.match(iosPostVideoEditorNativeDriver, /ios_post_video_editor_caption_transcript_missing/);
  assert.match(iosPostVideoEditorNativeDriver, /hasBackgroundCrop/);
  assert.match(iosPostVideoEditorNativeDriver, /backgroundTrack/);
  assert.match(iosPostVideoEditorNativeDriver, /VideoLayerScaleMode/);
  assert.match(iosPostVideoEditorNativeDriver, /segment\.text\.uppercased\(\)/);
  assert.doesNotMatch(iosPostVideoEditorNativeDriver, /captionStyle\.uppercased\(\)/);
  assert.match(iosPostVideoEditorNativeDriver, /captionAnimationTool/);
  assert.match(iosPostVideoEditorNativeDriver, /QUATA_IOS_POST_VIDEO_EDITOR_EXPORT_DIAGNOSTICS/);
  assert.match(iosVideoEditorRunner, /ios_video_editor_caption_document_missing_timings/);
  assert.match(iosVideoEditorRunner, /QUATA_IOS_POST_VIDEO_EDITOR_TRANSCRIPTION_LOCALE='en_US'/);
  assert.match(iosVideoEditorWrapper, /QUATA_IOS_POST_VIDEO_EDITOR_TRANSCRIPTION_LOCALE/);
  assert.match(iosVideoEditorRunner, /ios_video_editor_caption_pixels_missing/);
  assert.match(iosVideoEditorRunner, /ffprobe/);
  assert.match(iosQuataApp, /IosPostVideoEditorNativeDriverBridge\.shared/);
  assert.match(iosInfoPlist, /NSSpeechRecognitionUsageDescription/);
  assert.doesNotMatch(iosComposerHost, /I_ACCEPT_IOS_POST_COMPOSER_VIDEO_EDITOR_FIXTURE/);
  assert.match(androidPostPublishTest, /compose\.waitUntil\(240_000\)/);

  for (const runner of [androidVideoEditorRunner, webVideoEditorRunner, iosVideoEditorRunner]) {
    assert.doesNotMatch(runner, /community_posts/);
    assert.doesNotMatch(runner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  }
});
