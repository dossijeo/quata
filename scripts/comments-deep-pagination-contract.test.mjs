import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("..", import.meta.url);
const source = (path) => readFile(new URL(path, root), "utf8");

const [
  keysetLoader,
  feedRepository,
  feedTest,
  webFeed,
  iosFeed,
  officialProtocol,
  officialTest,
  webOfficial,
  iosOfficial,
  androidApi,
  androidQueryTest,
  postgrestProbe,
  postgrestRunner,
  packageJson,
] = await Promise.all([
  source("core/src/commonMain/kotlin/com/quata/core/data/KeysetPageLoader.kt"),
  source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/data/RemoteFeedReadRepository.kt"),
  source("feature/feed/src/commonTest/kotlin/com/quata/feature/feed/data/RemoteFeedReadRepositoryTest.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebFeedRepository.kt"),
  source("feature/feed/src/iosMain/kotlin/com/quata/feature/feed/data/IosFeedReadTransport.kt"),
  source("feature/official/src/commonMain/kotlin/com/quata/feature/official/data/OfficialRemoteProtocol.kt"),
  source("feature/official/src/commonTest/kotlin/com/quata/feature/official/data/OfficialRemoteProtocolTest.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialRepository.kt"),
  source("feature/official/src/iosMain/kotlin/com/quata/feature/official/data/IosOfficialReadRepository.kt"),
  source("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt"),
  source("app/src/test/java/com/quata/data/supabase/CommentsKeysetQueryTest.kt"),
  source("scripts/comments-deep-pagination-postgrest.test.mjs"),
  source("scripts/run-comments-deep-pagination-test.ps1"),
  source("package.json"),
]);

test("shared Feed and Official loaders exhaust strict keyset pages", () => {
  assert.match(keysetLoader, /keyset_page_not_strictly_ordered/);
  assert.match(feedRepository, /fetchCommentsPage\(request: FeedRemoteCommentPageRequest\)/);
  assert.match(feedRepository, /loadCompleteKeyset\([\s\S]*CommentPageSize = 500/);
  assert.match(feedRepository, /sortedWith\(compareBy<FeedRemoteComment> \{ it\.createdAt\.orEmpty\(\) \}\.thenBy\(FeedRemoteComment::id\)\)/);
  assert.match(feedTest, /1_205/);
  assert.match(feedTest, /listOf\(null, "comment-0500", "comment-1000"\)/);
  assert.match(officialProtocol, /loadCompleteOfficialComments[\s\S]*loadCompleteKeyset/);
  assert.match(officialProtocol, /thenBy\(OfficialRemoteComment::id\)/);
  assert.match(officialTest, /1_101/);
});

for (const [platform, feed, official] of [
  ["Web", webFeed, webOfficial],
  ["iOS", iosFeed, iosOfficial],
]) {
  test(`${platform} sends bounded id cursors for both comment tables`, () => {
    assert.match(feed, /community_comments[\s\S]*afterIdExclusive[\s\S]*"id", "gt\.\$\{/);
    assert.match(feed, /"order", "id\.asc"/);
    assert.match(official, /official_post_comments[\s\S]*afterIdExclusive[\s\S]*"id", "gt\.\$\{/);
    assert.match(official, /"order", "id\.asc"/);
    assert.match(official, /loadCompleteOfficialComments\(postIds\)/);
  });
}

test("Android drains both tables and rehydrates complete snapshots after invalidation", () => {
  assert.match(androidApi, /getOfficialComments[\s\S]*loadCompleteKeyset\(/);
  assert.match(androidApi, /getComments\([\s\S]*loadCompleteKeyset\(/);
  assert.match(androidApi, /commentsKeysetQuery[\s\S]*"deleted_at" to if \(excludeSoftDeleted\) "is\.null" else null/);
  assert.ok((androidApi.match(/excludeSoftDeleted = true/g) ?? []).length >= 2);
  assert.ok((androidApi.match(/excludeSoftDeleted = false/g) ?? []).length >= 2);
  assert.ok((androidApi.match(/emitUnchangedAfterInvalidation = true/g) ?? []).length >= 2);
  assert.match(androidApi, /getOfficialComments\(distinctPostIds, SupabaseCacheMode\.NETWORK_ONLY\)/);
  assert.match(androidApi, /getComments\(distinctPostIds, SupabaseCacheMode\.NETWORK_ONLY\)/);
  assert.match(androidApi, /CommentPageSize = 500/);
  assert.match(androidQueryTest, /officialPagesAndInvalidationTriggersExcludeSoftDeletedRows/);
  assert.match(androidQueryTest, /assertEquals\("is\.null", page\["deleted_at"\]\)/);
  assert.match(androidQueryTest, /communityPagesDoNotReferenceAColumnAbsentFromTheirSchema/);
});

test("isolated PostgreSQL and real PostgREST cross the configured 500-row ceiling", () => {
  assert.match(postgrestRunner, /postgres:17-alpine/);
  assert.match(postgrestRunner, /postgrest\/postgrest:v12\.2\.3/);
  assert.match(postgrestRunner, /PGRST_DB_MAX_ROWS=500/);
  assert.match(postgrestProbe, /pageSizes\.join\(","\) === "500,500,205"/);
  assert.match(postgrestProbe, /new Set\(rows\.map/);
  assert.match(postgrestProbe, /deleted_at/);
});

test("mandatory fast suites retain the comments pagination contract", () => {
  const scripts = JSON.parse(packageJson).scripts;
  assert.match(scripts["test:ci-fast-contracts"], /scripts\/comments-deep-pagination-contract\.test\.mjs/);
  assert.match(scripts["test:web-wave2-contracts"], /scripts\/comments-deep-pagination-contract\.test\.mjs/);
  assert.equal(scripts["test:comments-deep-pagination-isolated"], "powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-comments-deep-pagination-test.ps1");
});
