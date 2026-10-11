import assert from "node:assert/strict";
import test from "node:test";
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
