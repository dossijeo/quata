import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostEditorRoot.kt", import.meta.url),
  "utf8",
);
const status = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialStatusContent.kt", import.meta.url),
  "utf8",
);
const androidEditor = await readFile(
  new URL("../app/src/main/java/com/quata/feature/official/presentation/OfficialPostEditorScreen.kt", import.meta.url),
  "utf8",
);
const longEditor = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialLongTextEditorContent.kt", import.meta.url),
  "utf8",
);
const feedHost = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt", import.meta.url),
  "utf8",
);
const feedViewModel = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedViewModel.kt", import.meta.url),
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
  "official-editor-media-preview",
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
  assert.match(status, /OfficialCreateActionTestTag/);
  assert.match(status, /"official-create-action"/);
  assert.match(status, /testTag\(OfficialCreateActionTestTag\)/);
  assert.match(root, /fun requestPublication\(\)[\s\S]*?if \(!canPublish\)[\s\S]*?if \(!draftState\.canPublish\(\)\)/);
  assert.match(root, /OfficialPublishButtonContent\([\s\S]*?enabled = true/);
  assert.doesNotMatch(root, /fun canSubmitDraft\(/);
  assert.match(androidEditor, /unavailable = stringResource\(R\.string\.official_form_unavailable\)/);
  assert.match(androidEditor, /validation = stringResource\(R\.string\.official_form_validation\)/);
  assert.doesNotMatch(androidEditor, /unavailable = stringResource\(R\.string\.error_backend_generic\)/);
  assert.doesNotMatch(androidEditor, /validation = stringResource\(R\.string\.error_backend_generic\)/);
  assert.match(longEditor, /OfficialLongTextEditorBodyTestTag = "official-editor-long-body"/);
  assert.match(longEditor, /OfficialLongTextEditorSaveTestTag = "official-editor-long-save"/);
  assert.match(longEditor, /testTag\(OfficialLongTextEditorBodyTestTag\)/);
  assert.match(longEditor, /testTag\(OfficialLongTextEditorSaveTestTag\)/);
});

test("Official editor semantics contract stays hermetic", () => {
  for (const source of [root, status]) {
    assert.doesNotMatch(source, /SUPABASE_DB_URL|SERVICE_ROLE|21085800|\+240|68024260/);
  }
});

test("Official deep links render only the requested post and retry focused loads", () => {
  assert.match(feedHost, /val visiblePosts = activeFocusedPostId\?\.let \{ target -> state\.posts\.filter \{ post -> post\.id == target \} \} \?: state\.posts/);
  assert.match(feedHost, /pagerState = rememberPagerState\(pageCount = \{ visiblePosts\.size\.coerceAtLeast\(1\) \}\)/);
  assert.match(feedHost, /posts = visiblePosts/);
  assert.match(feedHost, /isInitialLoading = state\.isLoading \|\| focusedPostPending/);
  assert.match(feedHost, /onLoadOlder = \{ if \(activeFocusedPostId == null\) viewModel\.onEvent\(OfficialFeedUiEvent\.LoadOlderPage\) \}/);
  assert.match(feedViewModel, /private const val FocusedPostLoadAttempts = 4/);
  assert.match(feedViewModel, /repeat\(FocusedPostLoadAttempts\)/);
  assert.match(feedViewModel, /delay\(FocusedPostLoadRetryDelayMillis\)/);
  assert.match(feedViewModel, /private var exactLoadedPosts: Map<String, OfficialPostItem> = emptyMap\(\)/);
  assert.match(feedViewModel, /val mergedPosts = feedStore\.setRealtime\(posts\.withExactLoadedPosts\(\)\)/);
  assert.match(feedViewModel, /val mergedPosts = feedStore\.replaceInitialPage\(posts\.withExactLoadedPosts\(\)\)/);
  assert.match(feedViewModel, /posts = mergedPosts\.withLocalPendingCommentsFrom\(state\.posts\)/);
  assert.match(feedViewModel, /exactLoadedPosts = exactLoadedPosts \+ \(post\.id to post\)/);
  assert.match(feedViewModel, /private fun List<OfficialPostItem>\.withLocalPendingCommentsFrom\(existingPosts: List<OfficialPostItem>\): List<OfficialPostItem>/);
});
