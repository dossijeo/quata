import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("../", import.meta.url);
const source = (path) => readFile(new URL(path, root), "utf8");

const [commonHost, commonTest, androidHost, webHost, iosHost, iosImages] = await Promise.all([
  source("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt"),
  source("feature/profile/src/commonTest/kotlin/com/quata/feature/profile/presentation/ProfilePrefixRenderedInteractionTest.kt"),
  source("app/src/main/java/com/quata/feature/profile/presentation/ProfileScreen.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebProfileHost.kt"),
  source("feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileHost.kt"),
  source("designsystem/src/iosMain/kotlin/com/quata/core/ui/components/IosRemoteAvatar.kt"),
]);

test("common Profile owns avatar preview state, semantics, dismiss and back handling", () => {
  assert.match(commonHost, /var avatarPreviewOpen by rememberSaveable/);
  assert.match(commonHost, /ProfileAvatarPreviewOpenTestTag/);
  assert.match(commonHost, /clickable\(enabled = !profile\.avatarUri\.isNullOrBlank\(\), onClick = onAvatarPreview\)/);
  assert.match(commonHost, /if \(avatarPreviewOpen \|\| showSos \|\| page != ProfileAccountPage\.Overview\)/);
  assert.match(commonHost, /if \(avatarPreviewOpen\) \{\s*avatarPreviewOpen = false/);
  assert.match(commonHost, /QuataFullscreenMediaOverlayContent\(/);
  assert.match(commonHost, /slots\.avatarPreview\(/);
});

test("the rendered common interaction proves exact media association and close", () => {
  assert.match(commonTest, /renderedAvatarOpensTheSharedFullscreenPreview/);
  assert.match(commonTest, /onNodeWithTag\(ProfileAvatarPreviewOpenTestTag\)\.performClick\(\)/);
  assert.match(commonTest, /onNodeWithTag\(ProfileAvatarPreviewMediaTestTag\)\.fetchSemanticsNode\(\)/);
  assert.match(commonTest, /assertEquals\("https:\/\/cdn\.example\/avatar\.jpg", previewUri\)/);
  assert.match(commonTest, /onNodeWithTag\(QuataFullscreenMediaOverlayCloseTestTag\)\.performClick\(\)/);
  assert.match(commonTest, /fetchSemanticsNodes\(\)\.isEmpty\(\)/);
});

test("Android supplies the existing fullscreen image content without a second owner", () => {
  assert.match(androidHost, /avatarPreview = \{ name, uri, modifier ->\s*AttachmentFullscreenMediaContent\(AttachmentPreview\(name, uri, "image\/jpeg"\), modifier\)/);
  assert.doesNotMatch(androidHost, /var preview by remember/);
  assert.doesNotMatch(androidHost, /AttachmentViewerDialog/);
});

test("Web renders the decoded source uncropped inside the shared overlay", () => {
  assert.match(webHost, /avatarPreview = \{ name, avatarUrl, modifier ->/);
  assert.match(webHost, /rememberBrowserCanvasImage\(imageUrl\)/);
  assert.match(webHost, /imageState is BrowserCanvasImageState\.Ready/);
  assert.match(webHost, /contentScale = ContentScale\.Fit/);
});

test("iOS renders the decoded remote or local source uncropped inside the shared overlay", () => {
  assert.match(iosHost, /avatarPreview = \{ name, avatarUrl, modifier ->/);
  assert.match(iosHost, /IosRemoteImage\(/);
  assert.match(iosHost, /contentScale = ContentScale\.Fit/);
  assert.match(iosImages, /fun IosRemoteImage\(/);
  assert.match(iosImages, /loadIosRemoteAvatarOrNull\(normalizedUrl\)/);
});
