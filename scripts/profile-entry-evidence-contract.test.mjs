import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const webRunner = await readFile(new URL("./chat-actions-notifications-web-evidence.mjs", import.meta.url), "utf8");
const feedAnchor = await readFile(new URL("../feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedReelPostContent.kt", import.meta.url), "utf8");
const officialAnchor = await readFile(new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostCardContent.kt", import.meta.url), "utf8");
const officialHost = await readFile(new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt", import.meta.url), "utf8");
const webOfficialHost = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt", import.meta.url), "utf8");
const webMain = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt", import.meta.url), "utf8");
const webBridge = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebProfileEntryE2eBridge.kt", import.meta.url), "utf8");
const webProfileRoute = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebFeedMemberProfileRoute.kt", import.meta.url), "utf8");
const conversationAnchor = await readFile(new URL("../feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationAvatarPresentation.kt", import.meta.url), "utf8");
const conversationsHost = await readFile(new URL("../feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsScreenHost.kt", import.meta.url), "utf8");

test("PROF-ENTRY Web evidence is opt-in, semantic-first and reversible", () => {
  assert.match(webRunner, /--profile-entry-only/);
  assert.match(webRunner, /prepareProfileEntryFixture/);
  assert.match(webRunner, /verifyProfileEntryWeb/);
  assert.match(webRunner, /feed\.author\.avatar\.\$\{profile\.profileId\}/);
  assert.match(webRunner, /official\.author\.avatar\.\$\{profile\.profileId\}/);
  assert.match(webRunner, /conversation\.avatar\.\$\{profile\.profileId\}/);
  assert.match(webRunner, /visibleTextLocator/);
  assert.match(webRunner, /profile\.displayName/);
  assert.match(webRunner, /openProfileWithBridge/);
  assert.match(webRunner, /data-quata-member-profile-id/);
  assert.match(webRunner, /quata-profile-entry-e2e=1/);
  assert.match(webRunner, /profile_entry_feed_official_and_conversations_fixtures_prepared/);
  assert.match(webRunner, /profile_entry_official_post_deleted/);
  assert.match(webRunner, /cleanup_verified_profile_entry_official_residue_absent/);
  assert.match(webRunner, /cleanupProfileContentFixture/);
  assert.match(webRunner, /createPrivateChatSeed/);
  assert.match(webRunner, /openAuthenticatedRoute\(page, origin, `post-/);
  assert.match(webRunner, /openAuthenticatedRoute\(page, origin, `official-/);
  assert.match(webRunner, /openAuthenticatedRoute\(page, origin, "chat", "chat"\)/);
  assert.doesNotMatch(webRunner, /680242607|680242608|21085800|SERVICE_ROLE\s*=/);
});

test("PROF-ENTRY product anchors live in common/shared surfaces", () => {
  assert.match(feedAnchor, /fun feedAuthorAvatarTestTag\(profileId: String\): String = "feed\.author\.avatar\.\$profileId"/);
  assert.match(officialAnchor, /fun officialAuthorAvatarTestTag\(profileId: String\): String = "official\.author\.avatar\.\$profileId"/);
  assert.match(officialHost, /authorModifier\s*\n\s*\.testTag\(officialAuthorAvatarTestTag\(post\.author\.id\)\)/);
  assert.match(officialHost, /contentDescription = officialAuthorAvatarTestTag\(post\.author\.id\)/);
  assert.match(webOfficialHost, /BrowserOfficialAuthorAvatar\(post, onOpenUserProfile/);
  assert.match(webOfficialHost, /officialAuthorAvatarTestTag\(post\.author\.id\)/);
  assert.match(conversationAnchor, /fun conversationAvatarTestTag\(profileId: String\): String = "conversation\.avatar\.\$profileId"/);
  assert.match(conversationAnchor, /contentDescription = conversationAvatarTestTag\(id\)/);
  assert.match(conversationsHost, /rowModifier = \{ row ->/);
  assert.match(conversationsHost, /contentDescription = conversationAvatarTestTag\(profileId\)/);
  assert.match(webMain, /installWebProfileEntryE2eBridge\(feedMemberProfileRoute::open\)/);
  assert.match(webMain, /setWebMemberProfileMarker\(feedMemberProfileRoute\.profileId\)/);
  assert.match(webBridge, /quata-profile-entry-e2e/);
  assert.match(webBridge, /localhost/);
  assert.match(webBridge, /__quataProfileEntryE2eProduct/);
  assert.match(webProfileRoute, /setWebMemberProfileRouteMarker\(this\.profileId\)/);
  assert.match(webProfileRoute, /data-quata-member-profile-id/);
});
