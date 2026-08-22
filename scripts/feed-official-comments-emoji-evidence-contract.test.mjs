import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const root = new URL("../", import.meta.url);
const source = (path) => readFileSync(new URL(path, root), "utf8");

test("shared action rail exposes stable comments anchors for Feed and Official", () => {
  const rail = source("designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataFeedActionRail.kt");
  const compactControls = source("designsystem/src/commonMain/kotlin/com/quata/core/ui/components/CompactControls.kt");
  const feed = source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedReelPostContent.kt");
  const official = source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostActionRailContent.kt");
  assert.match(rail, /actionTestTagPrefix/);
  assert.match(rail, /actionTestTagSuffix/);
  assert.match(rail, /quataFeedActionTestTag\(actionTestTagPrefix, "comments", actionTestTagSuffix\)/);
  assert.match(rail, /accessibleDescription = listOfNotNull\(description, testTag\)\.joinToString\(" "\)/);
  assert.match(rail, /contentDescription = accessibleDescription/);
  assert.match(compactControls, /contentDescription: String\? = null/);
  assert.match(compactControls, /accessibleDescription = listOfNotNull\(contentDescription, testTag\)\.joinToString\(" "\)/);
  assert.match(feed, /actionTestTagPrefix = "feed\.action"/);
  assert.match(feed, /actionTestTagSuffix = post\.id/);
  assert.match(official, /actionTestTagPrefix = "official\.action"/);
  assert.match(official, /actionTestTagSuffix = post\.id/);
});

test("Feed and Official comments use the common emoji picker and common comment input tags", () => {
  const feed = source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt");
  const feedEvents = source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedUiEvent.kt");
  const feedViewModel = source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedViewModel.kt");
  const official = source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialCommentsPanelContent.kt");
  const officialHost = source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt");
  const officialViewModel = source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedViewModel.kt");
  for (const [label, content, prefix] of [["feed", feed, "feed"], ["official", official, "official"]]) {
    assert.match(content, /CommunityEmojiPanelContent/, `${label}:picker`);
    assert.match(content, /insertAtSelection/, `${label}:insert`);
    assert.match(content, new RegExp(`${prefix}\\.comments\\.emoji`), `${label}:emoji_tag`);
    assert.match(content, /contentDescription = strings\.showEmojis/, `${label}:emoji_accessible_tag`);
    assert.match(content, new RegExp(`${prefix}\\.comments\\.input`), `${label}:input_tag`);
    assert.match(content, new RegExp(`${prefix}\\.comments\\.send`), `${label}:send_tag`);
    assert.match(content, /emptyMessage = strings\.emojiLabels\.empty/, `${label}:empty_localized`);
  }
  assert.match(feedEvents, /data class FocusPost/);
  assert.match(feed, /FeedUiEvent\.FocusPost\(focusedPostId\)/);
  assert.match(feedViewModel, /repository\.refreshPost\(postId\)/);
  assert.match(feedViewModel, /feedStore\.prependIfMissing\(post\)/);
  assert.match(feedViewModel, /feedStore\.replace\(postId\) \{ it\.withoutLocalPendingComment\(comment\) \}/);
  assert.match(feedViewModel, /withoutLocalPendingComment/);
  assert.match(officialHost, /empty="No emojis available\."/);
  assert.match(officialHost, /empty="Aucun emoji disponible\."/);
  assert.match(officialViewModel, /feedStore\.replace\(postId\) \{ it\.withoutLocalPendingComment\(comment\) \}/);
  assert.match(officialViewModel, /exactLoadedPosts = exactLoadedPosts\.mapValues/);
  assert.match(feed, /commentsPostId = null[\s\S]*onOpenUserProfile\(profileId\)/);
  assert.match(officialHost, /commentsPost = null[\s\S]*onOpenUserProfile\(profileId\)/);
});

test("shared Feed/Official comments fixtures centralize SQL, cleanup and polling", () => {
  const fixtures = source("scripts/e2e-fixtures/chat-attachments.mjs");
  assert.match(fixtures, /export async function seedFeedOfficialCommentsFixture/);
  assert.match(fixtures, /export async function cleanupFeedOfficialCommentsFixture/);
  assert.match(fixtures, /export async function pollFeedOfficialComment/);
  assert.match(fixtures, /insert into public\.community_posts/);
  assert.match(fixtures, /insert into public\.official_posts/);
  assert.match(fixtures, /cleanup_verified_feed_official_comments_residue_absent/);
});

test("Web runner records a real Feed and Official emoji-comment flow", () => {
  const runner = source("scripts/chat-actions-notifications-web-evidence.mjs");
  assert.match(runner, /--feed-official-comments-only/);
  assert.match(runner, /options\.feedOfficialCommentsOnly/);
  assert.match(runner, /seedFeedOfficialCommentsFixture/);
  assert.match(runner, /`feed\.action\.comments\.\$\{fixture\.feed\.postId\}`/);
  assert.match(runner, /`official\.action\.comments\.\$\{fixture\.official\.postId\}`/);
  assert.match(runner, /feed\.comments\.author\.\$\{fixture\.actorSession\.profileId\}/);
  assert.match(runner, /official\.comments\.author\.\$\{fixture\.actorSession\.profileId\}/);
  assert.match(runner, /assertCommentAuthorAnchorVisible/);
  assert.match(runner, /comment_author_profile_anchor_visible/);
  assert.doesNotMatch(runner, /comment_author_profile_opened/);
  assert.doesNotMatch(runner, /commentAuthorProfileBridgeFallbacks/);
  assert.doesNotMatch(runner, /bottom-sheet dismiss layer intercepted/);
  assert.match(runner, /visibleAriaLocator\(page, \[new RegExp\(escapeRegExp\(`\$\{prefix\}\.emoji`\)\)\]/);
  assert.match(runner, /visibleNativeControl\(page, \[new RegExp\(escapeRegExp\(`\$\{prefix\}\.emoji`\)\)\]/);
  assert.match(runner, /verifyCommunityEmojiPanelSections/);
  assert.match(runner, /communityEmojiPanelProbeSections/);
  assert.match(runner, /community\.emoji\.section\.\$\{section\}/);
  assert.match(runner, /community\.emoji\.grid\.\$\{section\}/);
  assert.match(runner, /community\.emoji\.cell\.\$\{section\}\.0/);
  assert.match(runner, /nativeControlsOnly/);
  assert.doesNotMatch(runner, /visibleAriaLocator\(page, \[\/Comentarios\|Comments\|Commentaires\/i\]/);
  assert.match(runner, /prefix: "feed\.comments"/);
  assert.match(runner, /prefix: "official\.comments"/);
  assert.match(runner, /feed_comments_emoji_created_from_ui_and_verified_by_db/);
  assert.match(runner, /official_comments_emoji_created_from_ui_and_verified_by_db/);
  assert.match(runner, /sendReplyFromCommentTag/);
  assert.match(runner, /waitVisibleSeededSurfaceText\(page, `\$\{fixture\.marker\} feed post body`, "feed_official_comments_feed_post_marker_missing"\)/);
  assert.match(runner, /waitVisibleSeededSurfaceText\(page, fixture\.marker, "feed_official_comments_official_post_marker_missing"\)/);
  assert.match(runner, /pollFeedOfficialReplyComment/);
  assert.match(runner, /feed_comments_reply_created_from_ui_and_verified_by_db/);
  assert.match(runner, /official_comments_reply_created_from_ui_and_verified_by_db/);
  assert.match(runner, /cleanup_verified_feed_official_comments_residue_absent/);
  assert.match(runner, /isNonBlockingFeedOfficialSupabaseConflictFault/);
  assert.match(runner, /startsWith\("feed_official_comments_"\)/);
  assert.match(runner, /status of 409/i);
});

test("Android runner records the same Feed and Official emoji-comment flow", () => {
  const runner = source("scripts/chat-actions-notifications-android-evidence.mjs");
  const uiTest = source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");
  assert.match(runner, /--feed-official-comments-only/);
  assert.match(runner, /seedFeedOfficialCommentsFixture/);
  assert.match(runner, /pollFeedOfficialComment/);
  assert.match(runner, /pollFeedOfficialReplyComment/);
  assert.match(runner, /quataChatActionsFeedCommentId/);
  assert.match(runner, /quataChatActionsOfficialReplyComment/);
  assert.match(runner, /quataChatActionsActorProfileId/);
  assert.match(runner, /cleanup_verified_feed_official_comments_residue_absent/);
  assert.match(uiTest, /"feed-official-comments"/);
  assert.match(uiTest, /feed\.action\.comments\.\$feedPostId/);
  assert.match(uiTest, /official\.action\.comments\.\$officialPostId/);
  assert.match(uiTest, /feed\.comments\.author\.\$actorProfileId/);
  assert.match(uiTest, /official\.comments\.author\.\$actorProfileId/);
  assert.match(uiTest, /openProfileFromAuthorTag/);
  assert.doesNotMatch(uiTest, /requireReturnTag = false/);
  assert.match(uiTest, /requireReturnTag: Boolean = true/);
  assert.match(uiTest, /feed\.comments\.emoji/);
  assert.match(uiTest, /official\.comments\.emoji/);
  assert.match(uiTest, /verifyCommunityEmojiPanelSections/);
  assert.match(uiTest, /communityEmojiPanelProbeSections/);
  assert.match(uiTest, /community\.emoji\.sections/);
  assert.match(uiTest, /community\.emoji\.grid\.\$section/);
  assert.match(uiTest, /community\.emoji\.cell\.frequent\.0/);
  assert.match(uiTest, /sendReplyCommentFromOpenPanel/);
  assert.match(uiTest, /\$prefix\.reply\.\$replyToCommentId/);
});

test("Android evidence runner serializes instrumented runs sharing com.quata.test", () => {
  const runner = source("scripts/chat-actions-notifications-android-evidence.mjs");
  assert.match(runner, /androidEvidenceLockPath/);
  assert.match(runner, /\.chat-actions-notifications\.lock/);
  assert.match(runner, /acquireAndroidEvidenceLock/);
  assert.match(runner, /android_evidence_lock_timeout/);
  assert.match(runner, /releaseAndroidEvidenceLock/);
});

test("iOS runner records the same Feed and Official emoji-comment flow", () => {
  const runner = source("scripts/chat-actions-notifications-ios-evidence.mjs");
  const wrapper = source("scripts/run-ios-chat-actions-notifications-ui-test.sh");
  const uiTest = source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift");
  assert.match(runner, /--feed-official-comments-only/);
  assert.match(runner, /QUATA_IOS_CHAT_FEED_OFFICIAL_COMMENTS_UI_E2E/);
  assert.match(runner, /QUATA_IOS_CHAT_ACTOR_PROFILE_ID/);
  assert.match(runner, /seedFeedOfficialCommentsFixture/);
  assert.match(runner, /pollFeedOfficialComment/);
  assert.match(runner, /pollFeedOfficialReplyComment/);
  assert.match(runner, /QUATA_IOS_CHAT_FEED_COMMENTS_COMMENT_ID/);
  assert.match(runner, /QUATA_IOS_CHAT_OFFICIAL_COMMENTS_REPLY_COMMENT/);
  assert.match(runner, /cleanup_verified_feed_official_comments_residue_absent/);
  assert.match(wrapper, /testFeedAndOfficialCommentsUseSharedEmojiPicker/);
  assert.match(wrapper, /QUATA_IOS_CHAT_FEED_COMMENTS_POST_ID/);
  assert.match(wrapper, /QUATA_IOS_CHAT_FEED_COMMENTS_COMMENT_ID/);
  assert.match(wrapper, /QUATA_IOS_CHAT_FEED_COMMENTS_REPLY_COMMENT/);
  assert.match(wrapper, /QUATA_IOS_CHAT_OFFICIAL_COMMENTS_POST_ID/);
  assert.match(wrapper, /QUATA_IOS_CHAT_OFFICIAL_COMMENTS_COMMENT_ID/);
  assert.match(wrapper, /QUATA_IOS_CHAT_OFFICIAL_COMMENTS_REPLY_COMMENT/);
  assert.match(uiTest, /func testFeedAndOfficialCommentsUseSharedEmojiPicker/);
  assert.ok(uiTest.includes('feed.action.comments.\\(feedPostId)'));
  assert.ok(uiTest.includes('official.action.comments.\\(officialPostId)'));
  assert.ok(uiTest.includes('feed.comments.author.\\(actorProfileId)'));
  assert.ok(uiTest.includes('official.comments.author.\\(actorProfileId)'));
  assert.match(uiTest, /must expose a stable comment author profile anchor/);
  assert.match(uiTest, /feed\.comments\.emoji/);
  assert.match(uiTest, /official\.comments\.emoji/);
  assert.match(uiTest, /verifyCommunityEmojiPanelSections/);
  assert.match(uiTest, /communityEmojiPanelProbeSections/);
  assert.match(uiTest, /community\.emoji\.sections/);
  assert.match(uiTest, /community\.emoji\.grid\.\\\(section\)/);
  assert.match(uiTest, /community\.emoji\.cell\.frequent\.0/);
  assert.match(uiTest, /sendReplyCommentFromTaggedSurface/);
  assert.ok(uiTest.includes('feed.comments.reply.\\(feedCommentId)'));
});
