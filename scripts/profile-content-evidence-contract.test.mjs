import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const webRunner = await readFile(new URL("./chat-actions-notifications-web-evidence.mjs", import.meta.url), "utf8");
const androidRunner = await readFile(new URL("./chat-actions-notifications-android-evidence.mjs", import.meta.url), "utf8");
const iosRunner = await readFile(new URL("./chat-actions-notifications-ios-evidence.mjs", import.meta.url), "utf8");
const iosWrapper = await readFile(new URL("./run-ios-chat-actions-notifications-ui-test.sh", import.meta.url), "utf8");
const sharedFixtures = await readFile(new URL("./e2e-fixtures/chat-attachments.mjs", import.meta.url), "utf8");
const androidUiTest = await readFile(new URL("../app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt", import.meta.url), "utf8");
const iosUiTest = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift", import.meta.url), "utf8");
const iosNeighborhoodsHost = await readFile(new URL("../feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/presentation/IosNeighborhoodsHost.kt", import.meta.url), "utf8");
const commonProfileHost = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt", import.meta.url), "utf8");
const commonProfileDetails = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileDetailsContent.kt", import.meta.url), "utf8");
const commonProfileKpi = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileKpiContent.kt", import.meta.url), "utf8");
const commonProfilePostAction = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfilePostActionContent.kt", import.meta.url), "utf8");
const commonProfilePostPreview = await readFile(new URL("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfilePostPreviewContent.kt", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("PROF-CONTENT evidence mode is opt-in, redacted and reversible", () => {
  for (const runner of [webRunner, androidRunner, iosRunner]) {
    assert.match(runner, /--profile-content-only/);
    assert.match(runner, /prepareProfileContentFixture/);
    assert.match(runner, /cleanupProfileContentFixture/);
    assert.match(runner, /qadata-profile-content-/);
    assert.match(runner, /cleanup_verified_profile_content_residue_absent/);
    assert.match(runner, /pollProfileContentComment/);
    assert.match(runner, /profile_content_comment_created_from_ui_and_verified_by_db/);
    assert.match(runner, /attachmentMessageId/);
    assert.match(runner, /seedProfileContentFixture/);
    assert.match(runner, /cleanupSharedProfileContentFixture/);
    assert.match(runner, /pollSharedProfileContentComment/);
    assert.doesNotMatch(runner, /profile content attachment \$\{marker\}/);
    assert.doesNotMatch(runner, /quata_chat_register_attachment"[\s\S]*profile-content-attachment-\$\{marker\}/);
    assert.doesNotMatch(runner, /insert into public\.community_posts/);
    assert.doesNotMatch(runner, /else if \(options\.profileOnly \|\| options\.profileFollowOnly \|\| options\.profileListsOnly \|\| options\.profileContentOnly\) \{\s*\}\s*else if/);
    assert.doesNotMatch(runner, /profile_content_fixture_not_implemented/);
    assert.doesNotMatch(runner, /680242607|680242608|21085800|SERVICE_ROLE\s*=/);
  }
  assert.match(sharedFixtures, /export async function seedProfileContentFixture/);
  assert.match(sharedFixtures, /export async function cleanupProfileContentFixture/);
  assert.match(sharedFixtures, /export async function pollProfileContentComment/);
  assert.match(sharedFixtures, /cleanup\?\.trackStorageObject/);
  assert.match(sharedFixtures, /community_posts/);
  assert.match(sharedFixtures, /community_comments/);
  assert.match(sharedFixtures, /community_post_likes/);
  assert.match(sharedFixtures, /chat_attachments/);
  assert.match(sharedFixtures, /cleanup_verified_profile_content_residue_absent/);
  assert.match(iosRunner, /profile_content_shared_attachment_rpc_verified/);
  assert.match(iosRunner, /profile_content_shared_attachment_rpc_missing/);
  assert.match(iosRunner, /quata_chat_list_shared_attachments/);
});

test("PROF-CONTENT evidence uses common public-profile content anchors on every platform", () => {
  for (const source of [androidUiTest, iosUiTest]) {
    assert.match(source, /public-profile\.kpi\.posts\./);
    assert.match(source, /public-profile\.gallery\.header\./);
    assert.match(source, /public-profile\.gallery\./);
    assert.match(source, /public-profile\.gallery\.post\./);
    assert.match(source, /public-profile\.post\.preview\./);
    assert.match(source, /public-profile\.post\.media\.open\./);
    assert.match(source, /public-profile\.post\.action\.comments\./);
    assert.match(source, /fullscreen-media\.root/);
    assert.match(source, /fullscreen-media\.title/);
    assert.match(source, /fullscreen-media\.back/);
    assert.match(source, /fullscreen-media\.close/);
    assert.match(source, /fullscreen-media\.media-close/);
    assert.match(source, /public-profile\.comments\.panel/);
    assert.match(source, /public-profile\.comments\.list/);
    assert.match(source, /public-profile\.comments\.row\./);
    assert.match(source, /public-profile\.comments\.author\./);
    assert.match(source, /public-profile\.comments\.translator/);
    assert.match(source, /public-profile\.comments\.input/);
    assert.match(source, /public-profile\.comments\.send/);
    assert.match(source, /public-profile\.comments\.emoji/);
    assert.match(source, /community\.emoji\.panel/);
    assert.match(source, /community\.emoji\.cell\.frequent\.0/);
    assert.match(source, /public-profile\.attachments/);
    assert.match(source, /public-profile\.attachments\.item\./);
  }
  for (const source of [webRunner, androidUiTest, iosUiTest]) {
    assert.match(source, /public-profile\.kpi\.posts\./);
    assert.match(source, /public-profile\.gallery\.header\./);
    assert.match(source, /public-profile\.gallery\./);
    assert.match(source, /public-profile\.gallery\.post\./);
    assert.match(source, /public-profile\.post\.preview\./);
    assert.match(source, /public-profile\.post\.media\.open\./);
    assert.match(source, /public-profile\.post\.action\.comments\./);
    assert.match(source, /fullscreen-media\.root/);
    assert.match(source, /fullscreen-media\.title/);
    assert.match(source, /fullscreen-media\.back/);
    assert.match(source, /fullscreen-media\.close/);
    assert.match(source, /fullscreen-media\.media-close/);
    assert.match(source, /public-profile\.comments\.panel/);
    assert.match(source, /public-profile\.comments\.list/);
    assert.match(source, /public-profile\.comments\.row\./);
    assert.match(source, /public-profile\.comments\.author\./);
    assert.match(source, /public-profile\.comments\.translator/);
    assert.match(source, /public-profile\.comments\.input/);
    assert.match(source, /community\.emoji\.panel/);
    assert.match(source, /community\.emoji\.cell\.frequent\.0/);
    assert.match(source, /public-profile\.attachments/);
    assert.match(source, /public-profile\.attachments\.item\./);
  }
  assert.match(webRunner, /selectProfileContentCommentEmoji/);
  assert.match(webRunner, /selectEmojiCommentEmoji/);
  assert.match(webRunner, /fillEmojiComment/);
  assert.match(webRunner, /prefix: "public-profile\.comments"/);
  assert.match(webRunner, /public-profile\.comments\.author\.\$\{fixture\.actorSession\.profileId\}/);
  assert.match(webRunner, /public-profile\.comments\.translator/);
  assert.match(webRunner, /\$\{prefix\}\.emoji/);
  assert.match(webRunner, /\$\{prefix\}\.input/);
  assert.match(webRunner, /\$\{prefix\}\.send/);
  assert.match(androidUiTest, /performProfileCommentTextInput\(postId, visibleCommentText, "after-reply"\)/);
  assert.match(androidUiTest, /compose\.onNodeWithTag\("public-profile\.comments\.input", useUnmergedTree = true\)\s*\.performTextInput\(text\)/);
  assert.match(androidUiTest, /Public profile comments input must remain available after reply submission/);
  assert.match(androidUiTest, /waitForTagGone\("public-profile\.comments\.pending\.\$postId", "public profile comment persistence", 45_000\)/);
  assert.match(androidUiTest, /public-profile\.attachments\.item\.sb:\$attachmentId/);
  assert.match(androidUiTest, /val mediaOpenTag = "public-profile\.post\.media\.open\.\$postId"/);
  assert.match(androidUiTest, /waitForTag\(mediaOpenTag, "public profile media open action", 20_000\)/);
  assert.match(androidUiTest, /clickStableTag\(mediaOpenTag\)/);
  assert.match(androidUiTest, /bringPublicProfilePostIntoView\(profileId, postId\)/);
  assert.match(androidUiTest, /performScrollToNode\(hasTestTag\(pageTag\)\)/);
  assert.match(androidUiTest, /android-chat-profile-content-gallery-page-missing-semantics\.txt/);
  assert.match(androidRunner, /android-chat-profile-content-gallery-page-missing-semantics\.txt/);
  assert.match(androidRunner, /android-chat-profile-comments-panel-reopen-initial-semantics\.txt/);
  assert.match(androidUiTest, /ensurePublicProfileCommentsPanelOpen\(profileId, postId, "initial"\)/);
  assert.match(androidUiTest, /private fun bringPublicProfileTagIntoView\(tag: String\)/);
  assert.match(androidUiTest, /val commentsTag = "public-profile\.post\.action\.comments\.\$postId"\s*repeat\(3\) \{ attempt ->\s*if \(profileId != null\) bringPublicProfilePostIntoView\(profileId, postId\)\s*bringPublicProfileTagIntoView\(commentsTag\)\s*clickSemanticTagPreferCompose\(commentsTag\)/);
  assert.match(androidUiTest, /device\.swipe\(x, startY, x, endY, 18\)/);
  assert.match(androidUiTest, /clickSemanticTagPreferCompose\(postsTag\)/);
  assert.doesNotMatch(androidUiTest, /clickSemanticTagPreferCompose\("public-profile\.post\.action\.comments\.\$postId"\)/);
  assert.match(commonProfileKpi, /testTag: String\? = null/);
  assert.match(commonProfileKpi, /Modifier\.semantics \{ this\.testTag = tag \}/);
  assert.match(commonProfileKpi, /\.then\(interactiveModifier\)\s*\.then\(semanticsModifier\)/);
  assert.match(commonProfilePostAction, /\.clickable\(enabled = enabled, onClick = onClick\)\s*\.then\(modifier\)/);
  assert.match(commonProfilePostPreview, /testTag = PublicProfilePostMediaTestTagPrefix \+ post\.id/);
  assert.match(commonProfilePostPreview, /contentDescription = PublicProfilePostOpenMediaTestTagPrefix \+ post\.id/);
  assert.match(commonProfileHost, /testTag = PublicProfilePostsKpiTestTagPrefix \+ profile\.user\.id/);
  assert.doesNotMatch(commonProfileHost, /Modifier\.weight\(1f\)\.semantics \{ testTag = PublicProfilePostsKpiTestTagPrefix/);
  assert.match(iosUiTest, /public-profile\.attachments\.item\.sb:\\\(attachmentId\)/);
  assert.ok(
    iosUiTest.indexOf('"public-profile.attachments.item.sb:\\(attachmentId)"') <
      iosUiTest.indexOf("posts.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()"),
    "iOS must assert profile attachments before opening the posts gallery so scroll-to-gallery does not hide them.",
  );
  assert.match(webRunner, /public-profile\.attachments\.item\.sb:\$\{fixture\.attachmentId\}/);
  assert.match(webRunner, /async function openAndCloseProfileContentMediaViewer/);
  assert.match(webRunner, /profile_content_media_viewer_opened_and_closed/);
  assert.match(webRunner, /async function openProfileContentCommentsPanel/);
  assert.match(webRunner, /isProfileCommentsComposerOpen\(page\)/);
  assert.match(webRunner, /Cerrar comentarios\|Close comments/);
  assert.match(webRunner, /throw new Error\("profile_content_comments_input_not_visible"\)/);
  assert.match(webRunner, /bottomVisibleNativeControl\(page, sendPatterns/);
  assert.match(webRunner, /errorPrefix: "profile_content_comments"/);
  assert.match(webRunner, /\$\{errorPrefix\}_input_not_editable/);
  assert.match(webRunner, /pollProfileContentReplyComment/);
  assert.match(webRunner, /profile_content_reply_created_from_ui_and_verified_by_db/);
  assert.match(androidUiTest, /"profile-content" -> \{\s*openProfileFromPeerMessage\(peerProbe\.orEmpty\(\), profileId\.orEmpty\(\)\)/);
  assert.match(androidUiTest, /quataChatActionsActorProfileId/);
  assert.match(androidUiTest, /public-profile\.comments\.author\.\$actorProfileId/);
  assert.match(androidUiTest, /quataChatActionsProfileContentReplyComment/);
  assert.match(androidUiTest, /\$prefix\.reply\.\$replyToCommentId/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_PROFILE_CONTENT_UI_COMMENT/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_PROFILE_CONTENT_REPLY_COMMENT/);
  assert.match(iosRunner, /pollProfileContentReplyComment/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_ACTOR_PROFILE_ID/);
  assert.ok(iosUiTest.includes('public-profile.comments.author.\\(actorProfileId)'));
  assert.ok(iosUiTest.includes('public-profile.comments.reply.\\(commentId)'));
  assert.match(iosUiTest, /sendReplyCommentFromTaggedSurface/);
  assert.match(iosUiTest, /tapTaggedButton\("community\.emoji\.cell\.frequent\.0", in: app, context: "profile comments first frequent emoji"\)/);
  assert.doesNotMatch(iosUiTest, /profileElement\("community\.emoji\.cell\.frequent\.0", in: app, context: "profile comments first frequent emoji"\)/);
  assert.match(iosUiTest, /typeText\(String\(uiComment\.dropFirst\(\)\), into: "public-profile\.comments\.input", in: app\)/);
  assert.match(iosUiTest, /public-profile\.comments\.close/);
  assert.match(iosUiTest, /dismissProfileCommentsPanel\(in: app\)/);
  assert.match(iosUiTest, /tapPublicProfileBackOrDismiss\(in: app\)/);
  assert.match(iosUiTest, /public-profile\.back\.footer/);
  assert.match(iosNeighborhoodsHost, /showDismissButton = true/);
  assert.match(commonProfileDetails, /footer: \(@Composable \(\) -> Unit\)\? = null/);
  assert.match(commonProfileHost, /PublicProfileFooterBackTestTag = "public-profile\.back\.footer"/);
  assert.match(commonProfileHost, /QuataFullscreenMediaOverlayContent/);
  assert.match(iosUiTest, /profile comment submitted from iOS must remain visible/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_ACTOR_PROFILE_ID/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_PROFILE_CONTENT_REPLY_COMMENT/);
  assert.match(iosWrapper, /testProfileContentFromChatUsesSharedPublicProfileSurface/);
  assert.match(iosWrapper, /profile-content\.log/);
  assert.match(iosRunner, /acceptRemoteXcodeResultIoError/);
  assert.match(iosRunner, /CASTreeDataStructure\/Importer\.swift:131/);
  assert.match(iosRunner, /check-ios-xctest-executed\.py/);
  assert.match(iosRunner, /testProfileContentFromChatUsesSharedPublicProfileSurface/);
  assert.match(iosWrapper, /elif \[\[ "\$QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E" == "1" \]\]; then\s+run_and_require "\$profile_content" "\$profile_content_method"/);
  assert.match(iosWrapper, /"\$QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E" != "1"/);
});

test("Android PROF-CONTENT runner writes focal reports to requested paths", () => {
  assert.match(androidRunner, /function parseArgs\(argv\)/);
  assert.match(androidRunner, /"--out", "--evidence-dir"/);
  assert.match(androidRunner, /const evidenceDir = options\.evidenceDir/);
  assert.match(androidRunner, /const output = options\.output/);
});

test("PROF-CONTENT runners provide delay to the shared comment poller", () => {
  for (const runner of [androidRunner, iosRunner]) {
    assert.match(runner, /setTimeout as delay/);
    assert.match(runner, /pollSharedProfileContentComment\(\{ fixture, marker, withDatabase, delay, timeout \}\)/);
  }
});

test("PROF-CONTENT contract is part of local fast contract suites", () => {
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/profile-content-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/profile-content-evidence-contract\.test\.mjs/);
});
