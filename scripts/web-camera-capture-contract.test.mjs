import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const camera = readFileSync(new URL("../core/src/wasmJsMain/kotlin/com/quata/core/platform/BrowserCameraCaptureService.wasm.kt", import.meta.url), "utf8");
const route = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerRoute.kt", import.meta.url), "utf8");
const services = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPlatformServices.kt", import.meta.url), "utf8");
const composerHost = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerHost.kt", import.meta.url), "utf8");
const composerBridge = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerE2eBridge.kt", import.meta.url), "utf8");
const runner = readFileSync(new URL("./post-picker-camera-web-evidence.mjs", import.meta.url), "utf8");
const inventory = readFileSync(new URL("../docs/MULTIPLATFORM_INVENTORY.md", import.meta.url), "utf8");
const pkg = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8"));

test("Web camera capture uses the real MediaDevices stream and owns its lifecycle", () => {
  assert.match(camera, /media\.getUserMedia\(\{[\s\S]*facingMode: \{ ideal: 'environment' \}[\s\S]*audio: false/);
  assert.match(camera, /canvas\.toBlob\([\s\S]*'image\/jpeg', 0\.92/);
  assert.match(camera, /stream\.getTracks\(\)\.forEach\(\(track\) => track\.stop\(\)\)/);
  assert.match(camera, /video\.srcObject = null/);
  assert.match(camera, /video\.remove\?\.\(\)/);
  assert.match(camera, /camera_capture_frame_timeout/);
  assert.match(camera, /issuedReferences/);
  assert.match(camera, /URL\?\.revokeObjectURL/);
});

test("Web composition injects the real camera service outside the explicit fixture", () => {
  assert.match(services, /val cameraCapture: BrowserCameraCaptureService = BrowserCameraCaptureService\(\)/);
  assert.match(route, /webPostComposerPickerEvidenceShouldHandle\("camera-image"\)[\s\S]*platformServices\.cameraCapture\.capturePhoto\(CameraCaptureRequest\("quata-photo\.jpg"\)\)/);
  assert.match(route, /PlatformResult\.Success\(result\.value\.reference\)/);
});

test("focal browser evidence bypasses the picker fixture and verifies a readable JPEG plus stopped tracks", () => {
  assert.match(runner, /camera-image-native/);
  assert.match(runner, /--use-fake-device-for-media-stream/);
  assert.match(runner, /--use-fake-ui-for-media-stream/);
  assert.match(runner, /const evidenceQuery = nativeCamera \? "quata-post-publish-e2e=1"/);
  assert.match(composerBridge, /captureImage: \(\) => captureImage\(\)/);
  assert.match(composerHost, /captureImageAction[\s\S]*mediaSlots\.captureImage\(\)\.dispatchMediaResult/);
  assert.match(composerHost, /captureImage = captureImageAction/);
  assert.match(runner, /bridge\.captureImage\(\)/);
  assert.doesNotMatch(runner, /BrowserCameraCaptureService\s*\(/);
  assert.match(runner, /captureRequestCount !== 1/);
  assert.match(runner, /native_camera_tracks_not_stopped/);
  assert.match(runner, /native_camera_video_not_removed/);
  assert.match(runner, /native_camera_jpeg_unreadable/);
  assert.match(runner, /bytes\[0\] === 0xff && bytes\[1\] === 0xd8 && bytes\[2\] === 0xff/);
  assert.match(runner, /Promise\.allSettled\(\[[\s\S]*webLogout\(backend, session\)[\s\S]*revokeSessions\(backend, session\)/);
  assert.match(runner, /auth\/v1\/logout\?scope=local/);
  assert.doesNotMatch(runner, /scope:\s*"global"/);
  assert.match(runner, /webSessionDisabled: webSessionCleanup\.status === "fulfilled"/);
  assert.match(runner, /authSessionRevoked: authSessionCleanup\.status === "fulfilled"/);
});

test("inventory and mandatory suites retain the Web camera runtime guarantee", () => {
  assert.doesNotMatch(inventory, /cámara nativa Web sigue pendiente de host propio/);
  assert.match(inventory, /BrowserCameraCaptureService/);
  for (const suite of ["test:ci-fast-contracts", "test:web-wave2-contracts"]) {
    const entries = pkg.scripts[suite].split(/\s+/);
    assert.equal(entries.filter((entry) => entry === "scripts/web-camera-capture-contract.test.mjs").length, 1, `${suite} registration`);
  }
});
