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
  fullscreenOverlay,
  postAction,
] = await Promise.all([
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileAttachmentsContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfilePostsPagerContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfilePostPreviewContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentsPanelContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentInputContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentRowContent.kt"),
  source("designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataFullscreenMediaOverlayContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfilePostActionContent.kt"),
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
    "PublicProfilePostOpenMediaTestTagPrefix",
    "PublicProfilePostTextFallbackTestTagPrefix",
  ]) {
    assert.match(preview, new RegExp(`${constant} = "public-profile\\.`));
    assert.match(preview, new RegExp(`testTag = ${constant} \\+ post\\.id`));
  }
  for (const constant of [
    "PublicProfilePostLikeActionTestTagPrefix",
    "PublicProfilePostCommentsActionTestTagPrefix",
    "PublicProfilePostShareActionTestTagPrefix",
    "PublicProfilePostReportActionTestTagPrefix",
  ]) {
    assert.match(preview, new RegExp(`${constant} = "public-profile\\.`));
    assert.match(preview, new RegExp(`val tag = ${constant} \\+ post\\.id[\\s\\S]*testTag = tag[\\s\\S]*contentDescription = tag`));
  }
  assert.match(preview, /contentDescription = tag/);
  assert.match(postAction, /modifier = modifier\s+\.size\(42\.dp\)[\s\S]*\.clickable\(enabled = enabled, onClick = onClick\)/);
});

test("public profile content exposes common attachment anchors", () => {
  assert.match(attachments, /PublicProfileAttachmentsTestTag = "public-profile\.attachments"/);
  assert.match(attachments, /PublicProfileAttachmentsEmptyTestTag = "public-profile\.attachments\.empty"/);
  assert.match(attachments, /PublicProfileAttachmentItemTestTagPrefix = "public-profile\.attachments\.item\."/);
  assert.match(attachments, /testTag = PublicProfileAttachmentsTestTag/);
  assert.match(attachments, /testTag = PublicProfileAttachmentsEmptyTestTag/);
  assert.match(attachments, /testTag = PublicProfileAttachmentItemTestTagPrefix \+ attachment\.id/);
});

test("public profile content fixture seeds reversible real post media", async () => {
  const sharedFixtures = await source("scripts/e2e-fixtures/chat-attachments.mjs");
  assert.match(sharedFixtures, /export function validPngFixture\(\)/);
  assert.match(sharedFixtures, /profile_content_post_image/);
  assert.match(sharedFixtures, /insert into public\.community_posts\(id, wall_id, profile_id, body, image_url\)/);
  assert.match(sharedFixtures, /trackStorageObject\(\{\s*bucket: chatAttachmentsBucket,\s*storagePath: fixture\.postImageStoragePath/s);
});

test("public profile media opens through the shared fullscreen overlay", () => {
  assert.match(profileHost, /selectedMediaPostId/);
  assert.match(profileHost, /onOpenMedia = \{ selectedMediaPostId = post\.id \}/);
  assert.match(profileHost, /QuataFullscreenMediaOverlayContent/);
  assert.doesNotMatch(profileHost, /openPostMedia/);
  assert.match(fullscreenOverlay, /QuataFullscreenMediaOverlayRootTestTag = "fullscreen-media\.root"/);
  assert.match(fullscreenOverlay, /QuataFullscreenMediaOverlayBackTestTag = "fullscreen-media\.back"/);
  assert.match(fullscreenOverlay, /QuataFullscreenMediaOverlayCloseTestTag = "fullscreen-media\.close"/);
  assert.match(fullscreenOverlay, /QuataFullscreenMediaOverlayMediaCloseTestTag = "fullscreen-media\.media-close"/);
  assert.match(fullscreenOverlay, /QuataFullscreenMediaOverlayTitleTestTag = "fullscreen-media\.title"/);
  assert.match(fullscreenOverlay, /contentDescription = QuataFullscreenMediaOverlayRootTestTag/);
  assert.match(fullscreenOverlay, /contentDescription = QuataFullscreenMediaOverlayBackTestTag/);
  assert.match(fullscreenOverlay, /contentDescription = QuataFullscreenMediaOverlayCloseTestTag/);
  assert.match(fullscreenOverlay, /contentDescription = QuataFullscreenMediaOverlayMediaCloseTestTag/);
  assert.match(fullscreenOverlay, /contentDescription = QuataFullscreenMediaOverlayTitleTestTag/);
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
