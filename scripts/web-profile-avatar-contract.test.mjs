import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const host = read('web/src/wasmJsMain/kotlin/com/quata/web/WebProfileHost.kt');
const uploader = read('web/src/wasmJsMain/kotlin/com/quata/web/WebProfileAvatarUploader.kt');
const editor = read('web/src/wasmJsMain/kotlin/com/quata/web/WebAvatarImageEditor.kt');
const iosHost = read('feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileHost.kt');
const iosBootstrap = read('feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileSosRuntimeBootstrap.kt');
const iosUploader = read('feature/profile/src/iosMain/kotlin/com/quata/feature/profile/data/IosProfileAvatarUploader.kt');
const iosPostImageEditor = read('feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/presentation/IosPostImageEditor.kt');
const profileGradle = read('feature/profile/build.gradle.kts');

test('Web Profile uses the Android avatar action copy, icon and real browser sources', () => {
  assert.match(host, /CompactIcon\(Icons\.Filled\.PhotoCamera, null\)/);
  assert.match(host, /Text\("Cambiar foto de perfil"\)/);
  assert.match(host, /Text\("Elegir de galería"\)/);
  assert.match(host, /Text\("Hacer foto"\)/);
  assert.match(host, /FilePickerSource\.Gallery/);
  assert.match(host, /cameraCapture\.capturePhoto/);
  assert.match(host, /Popup\([\s\S]*popupPositionProvider = CenteredAvatarActionMenuPosition/);
  assert.match(host, /BoxWithConstraints\(Modifier\.wrapContentSize\(\)\)[\s\S]*webAvatarActionMenuWidth\(maxWidth\.value\.toInt\(\)\)\.dp[\s\S]*Modifier\.width\(menuWidth\)/);
  assert.match(host, /fun webAvatarActionMenuWidth\(availableWidthDp: Int\): Int =[\s\S]*availableWidthDp\.coerceIn\(1, WebAvatarActionMenuPreferredWidthDp\)/);
  assert.doesNotMatch(host, /Surface\(\s*modifier = Modifier\.fillMaxWidth\(\)/);
  assert.match(host, /webCenteredAvatarActionMenuOffset\([\s\S]*anchorBounds\.left \+ \(anchorBounds\.width - popupContentSize\.width\) \/ 2[\s\S]*desiredX\.coerceIn\(0, maxX\)/);
  assert.match(host, /leadingIcon = \{ CompactIcon\(Icons\.Filled\.PermMedia, null\) \}/);
  assert.match(host, /leadingIcon = \{ CompactIcon\(Icons\.Filled\.PhotoCamera, null\) \}/);
  assert.match(host, /WebAvatarImageEditor\([\s\S]*sourceReference = reference[\s\S]*onConfirm = \{ transform ->[\s\S]*references\.saveEditorTransform\(reference, transform\)[\s\S]*onAvatarChanged\(reference\)/);
  assert.match(host, /onDismiss = \{[\s\S]*references\.release\(reference\)[\s\S]*pendingReference = null/);
  assert.match(host, /val releaseScope = remember \{ MainScope\(\) \}/);
  assert.match(host, /DisposableEffect\(Unit\)[\s\S]*references\.release\(latestPendingReference\)[\s\S]*finally \{[\s\S]*releaseScope\.cancel\(\)/);
  assert.doesNotMatch(host, /pending de subida segura/);
  assert.doesNotMatch(host, /enabled = false/);
});

test('Web Profile avatar upload is actor-bound, edited square-JPEG and never persists a Blob URL', () => {
  assert.match(uploader, /avatars\/\$profileId\/\$safeToken\.jpg/);
  assert.match(uploader, /webComposerStorageUploadContract/);
  assert.match(uploader, /session\.userId == profileId/);
  assert.match(uploader, /binary\.prepareSquareJpeg\(normalized, references\.editorTransform\(normalized\)\)/);
  assert.match(uploader, /canvas\.width = 1080; canvas\.height = 1080/);
  assert.match(uploader, /const turns = \(\(Number\(quarterTurns\) % 4\) \+ 4\) % 4/);
  assert.match(uploader, /const outputDrawnWidth = turns % 2 === 0 \? sourceDrawnWidth : sourceDrawnHeight/);
  assert.match(uploader, /const outputDrawnHeight = turns % 2 === 0 \? sourceDrawnHeight : sourceDrawnWidth/);
  assert.match(uploader, /const maxPanX = Math\.max\(0, \(outputDrawnWidth - 1080\) \/ 2\)/);
  assert.match(uploader, /const maxPanY = Math\.max\(0, \(outputDrawnHeight - 1080\) \/ 2\)/);
  assert.match(uploader, /context\.translate\(540 \+ Math\.max\(-1, Math\.min\(1, Number\(panX\) \|\| 0\)\) \* maxPanX/);
  assert.match(uploader, /context\.rotate\(turns \* Math\.PI \/ 2\)/);
  assert.match(uploader, /context\.drawImage\(image, -sourceDrawnWidth \/ 2, -sourceDrawnHeight \/ 2, sourceDrawnWidth, sourceDrawnHeight\)/);
  assert.match(uploader, /webProfileAvatarPanAfterDrag\([\s\S]*dragX \/ geometry\.maxPanX[\s\S]*dragY \/ geometry\.maxPanY/);
  assert.doesNotMatch(uploader, /sourceSide/);
  assert.match(uploader, /canvas\.toBlob[\s\S]*'image\/jpeg', 0\.9/);
  assert.match(uploader, /finally \{[\s\S]*binary\.revokePrepared[\s\S]*references\.release/);
  assert.match(uploader, /require\(isBrowserAvatarBlobUrl\(normalized\)\)/);
  assert.match(uploader, /if \(isBrowserAvatarUrl\(normalized\)\) return normalized/);
});

test('Web Profile avatar editor is Compose-owned and exposes the complete locked-avatar controls', () => {
  assert.match(editor, /AlertDialog\(/);
  assert.match(editor, /rememberBrowserCanvasImage\(sourceReference\)/);
  assert.match(editor, /webProfileAvatarExportGeometry\([\s\S]*sourceWidth = ready\.bitmap\.width[\s\S]*outputSide = frameSidePx/);
  assert.match(editor, /detectDragGestures[\s\S]*webProfileAvatarPanAfterDrag/);
  assert.match(editor, /AvatarEditorCanvasPreview\(imageState, geometry, transform\)/);
  assert.match(editor, /Canvas\(Modifier\.fillMaxSize\(\)\)[\s\S]*translate\([\s\S]*transform\.panX \* current\.maxPanX[\s\S]*rotate\(degrees = transform\.quarterTurns \* 90f\)/);
  assert.match(editor, /Slider\([\s\S]*transform\.withZoom/);
  assert.match(editor, /transform = transform\.rotateClockwise\(\)/);
  assert.match(editor, /transform = AvatarImageEditorTransform\.Default/);
  assert.match(editor, /Button\(onClick = \{ onConfirm\(transform\) \}\)/);
  assert.match(editor, /OutlinedButton\(onClick = onDismiss\)/);
  assert.doesNotMatch(editor, /document\.createElement|innerHTML/);
});

test('Web Profile appearance copy remains byte-for-byte aligned with Android Spanish', () => {
  assert.match(host, /AppearanceSettingsStrings\("Activar Qüata TouchFlow", "Modo de color", "Sistema", "Modo Oscuro", "Modo Claro"\)/);
});

test('iOS Profile avatar uses the same gallery-camera-editor-save flow as Android and Web', () => {
  assert.match(profileGradle, /iosMain\.dependencies \{[\s\S]*implementation\(project\(":feature:postcomposer"\)\)/);
  assert.match(iosBootstrap, /avatar writes use the same authenticated Storage/);
  assert.match(iosBootstrap, /cameraCapture: com\.quata\.core\.platform\.CameraCaptureService/);
  assert.match(iosHost, /CompactIcon\(Icons\.Filled\.PhotoCamera, null\)/);
  assert.match(iosHost, /Text\("Change photo"\)/);
  assert.match(iosHost, /Text\("Choose from gallery"\)/);
  assert.match(iosHost, /Text\("Take photo"\)/);
  assert.match(iosHost, /FilePickerSource\.Gallery/);
  assert.match(iosHost, /dependencies\.cameraCapture\.capturePhoto\(CameraCaptureRequest\("quata-avatar\.jpg"\)\)/);
  assert.match(iosHost, /IosAvatarImageEditor\([\s\S]*source = file[\s\S]*onEdited = \{ edited ->[\s\S]*onAvatarChanged\(edited\.reference\)/);
  assert.doesNotMatch(iosHost, /result\.value\.firstOrNull\(\)\?\.reference\?\.let\(onAvatarChanged\)/);
});

test('iOS avatar editor reuses the common locked square crop contract', () => {
  assert.match(iosPostImageEditor, /fun IosAvatarImageEditor/);
  assert.match(iosPostImageEditor, /outputSpec = ImageEditorAvatarOutputSpec/);
  assert.match(iosPostImageEditor, /cropLocked = true/);
  assert.match(iosPostImageEditor, /iosPostImageEditorExport\([\s\S]*cropToOutputAspect = true[\s\S]*outputSpec = ImageEditorAvatarOutputSpec/);
  assert.match(iosPostImageEditor, /displayName = outputName/);
});

test('iOS Profile avatar uploader keeps actor-bound Storage semantics', () => {
  assert.match(iosUploader, /avatars\/\$profileId\/\$token\.jpg/);
  assert.match(iosUploader, /requireIosProfileAvatarActor\(profileId, session\.profileId\)/);
  assert.match(iosUploader, /"x-upsert" to "true"/);
  assert.match(iosUploader, /UIImageJPEGRepresentation/);
});
