import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

const [
  profileHost,
  attachments,
  pager,
  preview,
  commentsPanel,
  commentsInput,
  commentsRow,
] = await Promise.all([
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileAttachmentsContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfilePostsPagerContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfilePostPreviewContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentsPanelContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentInputContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentRowContent.kt"),
]);

test("public profile content exposes common gallery anchors", () => {
  assert.match(profileHost, /PublicProfileGalleryTestTagPrefix = "public-profile\.gallery\."/);
  assert.match(profileHost, /PublicProfileGalleryHeaderTestTagPrefix = "public-profile\.gallery\.header\."/);
  assert.match(profileHost, /testTag = PublicProfileGalleryHeaderTestTagPrefix \+ profile\.user\.id/);
  assert.match(profileHost, /testTag = PublicProfileGalleryTestTagPrefix \+ profile\.user\.id/);
  assert.match(pager, /PublicProfilePostPageTestTagPrefix = "public-profile\.gallery\.post\."/);
  assert.match(pager, /testTag = PublicProfilePostPageTestTagPrefix \+ post\.id/);
});

test("public profile content exposes common post preview and action anchors", () => {
  for (const constant of [
    "PublicProfilePostPreviewTestTagPrefix",
    "PublicProfilePostMediaTestTagPrefix",
    "PublicProfilePostTextFallbackTestTagPrefix",
    "PublicProfilePostLikeActionTestTagPrefix",
    "PublicProfilePostCommentsActionTestTagPrefix",
    "PublicProfilePostShareActionTestTagPrefix",
    "PublicProfilePostReportActionTestTagPrefix",
  ]) {
    assert.match(preview, new RegExp(`${constant} = "public-profile\\.`));
    assert.match(preview, new RegExp(`testTag = ${constant} \\+ post\\.id`));
  }
});

test("public profile content exposes common attachment anchors", () => {
  assert.match(attachments, /PublicProfileAttachmentsTestTag = "public-profile\.attachments"/);
  assert.match(attachments, /PublicProfileAttachmentsEmptyTestTag = "public-profile\.attachments\.empty"/);
  assert.match(attachments, /PublicProfileAttachmentItemTestTagPrefix = "public-profile\.attachments\.item\."/);
  assert.match(attachments, /testTag = PublicProfileAttachmentsTestTag/);
  assert.match(attachments, /testTag = PublicProfileAttachmentsEmptyTestTag/);
  assert.match(attachments, /testTag = PublicProfileAttachmentItemTestTagPrefix \+ attachment\.id/);
});

test("public profile content exposes common comments anchors", () => {
  assert.match(commentsPanel, /PublicProfileCommentsPanelTestTag = "public-profile\.comments\.panel"/);
  assert.match(commentsPanel, /PublicProfileCommentsListTestTag = "public-profile\.comments\.list"/);
  assert.match(commentsPanel, /PublicProfileCommentsCloseTestTag = "public-profile\.comments\.close"/);
  assert.match(commentsInput, /PublicProfileCommentsInputTestTag = "public-profile\.comments\.input"/);
  assert.match(commentsInput, /PublicProfileCommentsSendTestTag = "public-profile\.comments\.send"/);
  assert.match(commentsRow, /PublicProfileCommentRowTestTagPrefix = "public-profile\.comments\.row\."/);
  assert.match(commentsRow, /testTag = PublicProfileCommentRowTestTagPrefix \+ comment\.id/);
});
