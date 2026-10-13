import assert from "node:assert/strict";
import test from "node:test";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");

test("Web video export requires MP4/H.264 and never publishes a WebM fallback", async () => {
  const source = await readFile(resolve(root, "web/src/wasmJsMain/kotlin/com/quata/web/WebPostVideoEditor.kt"), "utf8");

  assert.match(source, /video\/mp4;codecs=avc1\.42E01E,opus/);
  assert.match(source, /video\/mp4;codecs=avc1\.42E01E,mp4a\.40\.2/);
  assert.match(source, /video\/mp4;codecs=avc1\.42E01E/);
  assert.match(source, /web_post_video_editor_cross_platform_codec_unavailable/);
  assert.match(source, /web_post_video_editor_cross_platform_audio_codec_unavailable/);
  assert.match(source, /if \(!requestedMimeType\) \{\s*failOnce\('web_post_video_editor_cross_platform_codec_unavailable'\);\s*return;/);
  assert.match(source, /!normalizedMimeType\.startsWith\('video\/mp4'\)[\s\S]*failOnce\('web_post_video_editor_cross_platform_codec_unavailable'\);\s*return;/);
  assert.match(source, /hasAudioTrack[\s\S]*failOnce\('web_post_video_editor_cross_platform_audio_codec_unavailable'\);\s*return;/);
  assert.match(source, /stream\?\.getTracks\?\.\(\)\.forEach\(track => track\.stop\?\.\(\)\)/);
  assert.match(source, /delete globalThis\.__quataPostVideoEditorActiveExport/);
  assert.doesNotMatch(source, /throw Error\('web_post_video_editor_cross_platform_(?:audio_)?codec_unavailable'\)/);
  assert.match(source, /const maximumCaptureWallMs = durationMs \+ 500/);
  assert.match(source, /reachedCaptureTail \|\| reachedCaptureDeadline/);
  assert.match(source, /name: 'video\.mp4'/);
  assert.doesNotMatch(source, /video\/webm/);
  assert.doesNotMatch(source, /name: 'video\.' \+ extension/);
});

test("the physical Web evidence rejects non-H.264 video and non-Opus audio", async () => {
  const runner = await readFile(resolve(root, "scripts/post-video-editor-web-evidence.mjs"), "utf8");

  assert.match(runner, /web-post-video-editor-export-\$\{label\}\.mp4/);
  assert.match(runner, /videoStream\.codec_name !== "h264"/);
  assert.match(runner, /audioStream\.codec_name !== "opus"/);
  assert.match(runner, /web_video_editor_export_cross_platform_mime/);
  assert.match(runner, /\["success", "failed"\]\.includes/);
  assert.doesNotMatch(runner, /web-post-video-editor-export-\$\{label\}\.webm/);
});

test("cross-platform codec attestation preserves exact evidence and residual boundaries", async () => {
  const manifestPath = resolve(root, "docs/candidate-attestations/post-video-web-cross-platform-codec.json");
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  assert.match(manifest.productSha, /^[0-9a-f]{40}$/);
  assert.deepEqual(manifest.units, ["POST-VIDEO-EDITOR", "OVR-MEDIA"]);

  for (const platform of ["web", "android", "ios"]) {
    const item = manifest.evidence[platform];
    assert.equal(item.status, "passed");
    assert.equal(item.sha, manifest.productSha);
    assert.equal(item.cleanup.verified, true);
    const report = await readFile(resolve(root, item.report));
    assert.equal(createHash("sha256").update(report).digest("hex"), item.reportSha256);
    const parsed = JSON.parse(report);
    assert.equal(parsed.status, "passed");
    assert.equal(parsed.git.head, item.reportGitHead);
    assert.equal(parsed.git.workingTreeDirty, false);
  }

  const android = JSON.parse(await readFile(resolve(root, manifest.evidence.android.report), "utf8"));
  const ios = JSON.parse(await readFile(resolve(root, manifest.evidence.ios.report), "utf8"));
  assert.equal(android.fixture.sha256, "e2213d891edc68c0b621499d8a7092cd281520769ca4fc0a7d6ff8215a628bf9");
  assert.equal(ios.fixture.sha256, android.fixture.sha256);
  assert.equal(android.test.failures, 0);
  assert.equal(ios.result.itemStatus, "readyToPlay");

  const inventory = await readFile(resolve(root, "docs/MULTIPLATFORM_INVENTORY.md"), "utf8");
  const screens = await readFile(resolve(root, "docs/SCREEN_MIGRATION_INVENTORY_V2.md"), "utf8");
  const board = await readFile(resolve(root, "docs/MULTIPLATFORM_MIGRATION_BOARD.md"), "utf8");
  for (const document of [inventory, screens, board]) {
    assert.match(document, /d484e5b5/);
    assert.match(document, /post-video-web-cross-platform-codec\.json/);
    assert.match(document, /codecs heredados|codecs heredados\/terceros/);
  }
  assert.match(
    board,
    /^## POST-VIDEO-EDITOR \/ formato Web reproducible en Android e iOS — integrado por #610$/m,
  );
  assert.doesNotMatch(
    board,
    /^## POST-VIDEO-EDITOR \/ formato Web reproducible en Android e iOS — candidata$/m,
  );
});
