import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const migration = read("supabase/migrations/20261004113000_community_feed_total_order.sql");
const rollback = read("supabase/rollbacks/20261004113000_community_feed_total_order.rollback.sql");
const domain = read("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/domain/FeedRepository.kt");
const complete = read("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/domain/FeedCompleteRanking.kt");
const android = read("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt");
const web = read("web/src/wasmJsMain/kotlin/com/quata/web/WebFeedRepository.kt");
const ios = read("feature/feed/src/iosMain/kotlin/com/quata/feature/feed/data/IosFeedReadTransport.kt");
const iosNeighborhoods = read("feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt");
const state = read("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedUiState.kt");
const executor = read("scripts/selective-db-release-executor.mjs");
const restoreDrill = read("scripts/restore-db-logical-backup-drill.ps1");
const remoteRepository = read("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/data/RemoteFeedReadRepository.kt");
const runner = read("scripts/run-community-feed-total-order-test.ps1");
const rollbackTrial = read("scripts/sql/community-feed-total-order-rollback.test.sql");
const packageJson = read("package.json");

assert.match(migration, /create or replace function public\.quata_community_feed_page/);
assert.match(migration, /security invoker/);
assert.match(migration, /cursor_values not in \(0, 2\)/);
assert.match(migration, /\(post\.created_at, post\.id\)\s*<\s*\(p_before_created_at, p_before_id\)/s);
assert.match(migration, /order by post\.created_at desc, post\.id desc/);
assert.match(migration, /grant execute[\s\S]*to anon, authenticated/);
assert.match(rollback, /drop function if exists public\.quata_community_feed_page/);

assert.match(domain, /data class FeedCursor[\s\S]*createdAt: String[\s\S]*postId: String/);
assert.match(complete, /refreshFeed\(\)\.getOrThrow\(\)/);
assert.match(complete, /feed_ranking_duplicate_post/);
assert.match(complete, /feed_ranking_cursor_not_advanced/);
for (const source of [android, web, ios]) {
  assert.match(source, /rpc\/quata_community_feed_page/);
  assert.match(source, /p_before_created_at/);
  assert.match(source, /p_before_id/);
}
assert.match(iosNeighborhoods, /RemoteFeedReadRepository\([\s\S]*?\)\.loadFeedPage\(ProfilePostLimit\)\.getOrThrow\(\)/);
assert.doesNotMatch(iosNeighborhoods, /loadOlderFeedPage\(beforeCreatedAt\s*=/);
assert.match(android, /observeCommunityFeedPage[\s\S]*rpc\/quata_community_feed_page/);
assert.match(android, /loadCompleteKeyset[\s\S]*cursorOf = CommunityPostLike::id/);
assert.match(remoteRepository, /fetchLikesPage/);
assert.match(remoteRepository, /cursorOf = FeedRemoteLike::id/);
for (const source of [web, ios]) {
  assert.match(source, /fetchLikesPage/);
  assert.match(source, /order["),\s]+id\.asc/);
}
assert.match(state, /rankingPosts: List<Post>\?/);
assert.match(state, /isLoadingRanking: Boolean/);
assert.match(state, /rankingError: String\?/);
assert.match(executor, /\["20261004113000", "96a05a158a4faaa206c9c3ba7683f9df7a0ecd7b79e84f2b62dc82b98c0dee20"\]/);
assert.match(executor, /selectedVersions\.includes\("20261004113000"\)/);
assert.match(executor, /selective_release_community_feed_function_postcondition_failed/);
assert.match(executor, /selective_release_community_feed_index_postcondition_failed/);
assert.match(restoreDrill, /\[switch\]\$CommunityFeedScope/);
assert.match(restoreDrill, /restore_community_feed_scope_requires_full_backup/);
assert.match(restoreDrill, /backup_toc_community_posts_table_missing/);
assert.match(restoreDrill, /backup_toc_community_posts_data_missing/);
assert.match(restoreDrill, /ExpectedCommunityPosts/);
assert.match(runner, /community-feed-total-order-rollback\.test\.sql/);
assert.equal((runner.match(/community-feed-total-order-postgrest\.test\.mjs/g) ?? []).length, 2);
assert.match(rollbackTrial, /quata_community_feed_page[\s\S]*survived rollback/);
assert.match(rollbackTrial, /community_posts_public_total_order_idx[\s\S]*missing after reapply/);
assert.match(packageJson, /test:community-feed-ranking-completeness-contract/);
for (const gate of ["test:web-wave2-contracts", "test:ci-fast-contracts"]) {
  const command = JSON.parse(packageJson).scripts[gate];
  assert.match(command, /community-feed-ranking-completeness-contract\.test\.mjs/);
}

console.log("community feed ranking completeness contract: PASS");
