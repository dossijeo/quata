import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostEditorRoot.kt", import.meta.url),
  "utf8",
);

const requiredTags = [
  "official-editor-common-root",
  "official-editor-mode-selector",
  "official-editor-main-section",
  "official-editor-media-section",
  "official-editor-pick-image",
  "official-editor-pick-video",
  "official-editor-body-section",
  "official-editor-body-action",
  "official-editor-preview",
  "official-editor-feedback",
  "official-editor-publish",
];

test("Official editor exposes stable commonMain semantics for platform evidence", () => {
  assert.match(root, /import androidx\.compose\.ui\.platform\.testTag/);
  for (const tag of requiredTags) {
    assert.match(root, new RegExp(`"${tag}"`), `${tag} must stay in commonMain`);
  }
  assert.match(root, /modifier = modifier\.testTag\(OfficialEditorRootTestTag\)/);
  assert.match(root, /OfficialEditorPublishTestTag/);
  assert.match(root, /fun requestPublication\(\)[\s\S]*?if \(!canPublish\)[\s\S]*?if \(!draftState\.canPublish\(\)\)/);
  assert.match(root, /OfficialPublishButtonContent\([\s\S]*?enabled = true/);
  assert.doesNotMatch(root, /fun canSubmitDraft\(/);
});

test("Official editor semantics contract stays hermetic", () => {
  assert.doesNotMatch(root, /SUPABASE_DB_URL|SERVICE_ROLE|21085800|\+240|68024260/);
});
