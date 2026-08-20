import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

const commonModels = read("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/imageeditor/ImageEditorModels.kt");
const commonContent = read("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/imageeditor/PostImageEditorContent.kt");
const webHost = read("web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerHost.kt");
const webEditor = read("web/src/wasmJsMain/kotlin/com/quata/web/WebPostImageEditor.kt");
const androidEditor = read("app/src/main/java/com/quata/feature/postcomposer/imageeditor/QuataImageEditorDialog.kt");
const webEvidence = read("scripts/post-image-editor-web-evidence.mjs");
const iosHost = read("feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/presentation/IosComposerHost.kt");
const iosEditor = read("feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/presentation/IosPostImageEditor.kt");
const iosUiTest = read("iosApp/iosAppUITests/QuataIosAuthenticatedPostPublishUITests.swift");
const androidUiTest = read("app/src/androidTest/java/com/quata/feature/postcomposer/presentation/PostPublishRealInstrumentedTest.kt");

test("post image editor owns a common transform, geometry and shared control surface", () => {
  assert.match(commonModels, /data class PostImageEditorTransform/);
  assert.match(commonModels, /fun postImageEditorGeometry\(/);
  assert.match(commonModels, /ImageEditorPostOutputSpec/);
  assert.match(commonModels, /fun postImageEditorPanAfterDrag\(/);
  assert.match(commonContent, /PostImageEditorDialogContent/);
  for (const tag of [
    "PostImageEditorRootTestTag",
    "PostImageEditorPreviewTestTag",
    "PostImageEditorCancelTestTag",
    "PostImageEditorResetTestTag",
    "PostImageEditorRotateTestTag",
    "PostImageEditorCropTestTag",
    "PostImageEditorSaveTestTag",
  ]) {
    assert.match(commonContent, new RegExp(tag));
  }
  assert.match(commonContent, /cropLocked: Boolean = false/);
  assert.match(commonContent, /cropPanelOpen by remember/);
  assert.match(commonContent, /cropApplied by remember/);
  assert.match(commonContent, /cropLocked \|\| cropPanelOpen \|\| cropApplied/);
  assert.match(commonContent, /BoxWithConstraints/);
  assert.match(commonContent, /val landscape = maxWidth > 560\.dp/);
  assert.match(commonContent, /Icons\.Filled\.Crop/);
  assert.match(commonContent, /detectDragGestures/);
  assert.match(commonContent, /Slider\(/);
  assert.match(commonContent, /preview\(transform, geometry, cropPanelOpen, cropApplied/);
  assert.match(commonContent, /onSave\(shouldCrop\)/);
});

test("Web composer opens the real Compose/Wasm post image editor and exports a JPEG blob", () => {
  assert.match(webHost, /imageEditorReference/);
  assert.match(webHost, /mediaSlots\.imageEditor/);
  assert.match(webHost, /CreatePostUiEvent\.ImageSelected\(edited\)/);
  assert.match(webEditor, /WebPostImageEditor/);
  assert.match(webEditor, /PostImageEditorDialogContent/);
  assert.match(webEditor, /rememberBrowserCanvasImage\(sourceReference\)/);
  assert.match(webEditor, /postImageEditorGeometry/);
  assert.match(webEditor, /webPostImageEditorExportJpeg/);
  assert.match(webEditor, /cropToOutputAspect: Boolean = true/);
  assert.match(webEditor, /const shouldCrop = Boolean\(cropToOutputAspect\)/);
  assert.match(webEditor, /const outputWidth = shouldCrop \? 1080/);
  assert.match(webEditor, /const scale = \(shouldCrop \?/);
  assert.match(webEditor, /canvas\.width = outputWidth; canvas\.height = outputHeight/);
  assert.match(webEditor, /context\.rotate\(turns \* Math\.PI \/ 2\)/);
  assert.match(webEditor, /canvas\.toBlob[\s\S]*'image\/jpeg', 0\.92/);
});

test("Android composer uses the same common post image editor surface and native JPEG export edge", () => {
  assert.match(androidEditor, /PostImageEditorDialogContent\(/);
  assert.match(androidEditor, /val cropLocked = mode == QuataImageEditorMode\.Avatar/);
  assert.match(androidEditor, /cropLocked = cropLocked/);
  assert.match(androidEditor, /cropToOutputAspect: Boolean/);
  assert.match(androidEditor, /if \(!cropToOutputAspect\)/);
  assert.match(androidEditor, /source\.rotateClockwise\(turns\)/);
  assert.match(androidEditor, /PostImageEditorTransform\.Default/);
  assert.match(androidEditor, /postImageEditorGeometry\(/);
  assert.match(androidEditor, /AndroidPostImageEditorPreview\(/);
  assert.match(androidEditor, /Context\.exportEditedImage\(/);
  assert.match(androidEditor, /Bitmap\.createBitmap\(outputSpec\.width, outputSpec\.height/);
  assert.match(androidEditor, /canvas\.rotate\(turns \* 90f\)/);
  assert.match(androidEditor, /Bitmap\.CompressFormat\.JPEG, ImageEditorJpegQuality/);
  assert.doesNotMatch(androidEditor, /QuataEditorScaffold/);
  assert.doesNotMatch(androidEditor, /QuataEditorToolButton/);
  assert.doesNotMatch(androidEditor, /ImageCropAdjustmentPane/);
});

test("iOS composer opens a real editor surface and exports a temporary JPEG", () => {
  assert.match(iosHost, /editImage = \(\{ imageFile\?\.let \{ imageEditorFile = it \} \}\)/);
  assert.doesNotMatch(iosHost, /editImage\s*=\s*\{\{/);
  assert.doesNotMatch(iosHost, /editVideo\s*=\s*if[\s\S]*?\{\{/);
  assert.doesNotMatch(webHost, /editImage\s*=\s*when[\s\S]*?\{\{/);
  assert.doesNotMatch(iosHost, /iosPostComposerImageEditorEvidenceEditedFile/);
  assert.match(iosHost, /IosPostImageEditor/);
  assert.match(iosEditor, /PostImageEditorDialogContent/);
  assert.match(iosEditor, /cropToOutputAspect: Boolean/);
  assert.match(iosEditor, /val scale = if \(cropToOutputAspect\)/);
  assert.match(iosEditor, /outputWidth = if \(cropToOutputAspect\)/);
  assert.match(iosEditor, /UIGraphicsBeginImageContextWithOptions/);
  assert.match(iosEditor, /CGContextRotateCTM/);
  assert.match(iosEditor, /UIImageJPEGRepresentation\(it, 0\.92\)/);
  assert.match(iosEditor, /PlatformFile\(reference = url\.absoluteString/);
});

test("post image editor evidence must exercise root, cancel, controls and save on all platforms", () => {
  for (const tag of ["post-image-editor.root", "post-image-editor.cancel", "post-image-editor.rotate", "post-image-editor.reset", "post-image-editor.save"]) {
    assert.match(webEvidence, new RegExp(tag.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
    assert.match(iosUiTest, new RegExp(tag.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
  assert.match(webEvidence, /afterCancel/);
  assert.match(webEvidence, /editorReopened/);
  assert.match(iosUiTest, /ios-post-image-editor-after-cancel/);
  assert.match(iosUiTest, /ios-post-image-editor-reopened/);
  assert.match(webEvidence, /state\.imageUri !== previous/);
  assert.doesNotMatch(webEvidence, /quata_post_composer_image_editor_e2e_reference/);
  assert.match(commonModels, /PostImageEditorCancelTestTag/);
  assert.match(commonContent, /contentDescription = strings\.cancel/);
  assert.match(androidUiTest, /onNodeWithContentDescription\(spanish/);
  assert.match(androidUiTest, /onNodeWithContentDescription\(english/);
  assert.match(androidUiTest, /onNodeWithText\(spanish/);
  assert.match(androidUiTest, /PostImageEditorRootTestTag/);
  assert.match(androidUiTest, /android-post-image-editor-after-cancel/);
  assert.match(androidUiTest, /android-post-image-editor-reopened/);
  assert.match(androidUiTest, /PostImageEditorRotateTestTag/);
  assert.match(androidUiTest, /PostImageEditorResetTestTag/);
  assert.match(androidUiTest, /PostImageEditorSaveTestTag/);
});
