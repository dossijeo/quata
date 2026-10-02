import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { accountDeactivateDefinitionMd5 } from "./selective-db-release-postconditions.mjs";

const root = new URL("../", import.meta.url);
const read = (path) => readFile(new URL(path, root), "utf8");

test("favorites pagination is additive, actor-bound, stable, and least-privilege", async () => {
  const [sql, rollback, legacyMigration, selectiveRelease] = await Promise.all([
    read("supabase/migrations/20261001211500_chat_favorites_cursor_pagination.sql"),
    read("supabase/rollbacks/20261001211500_chat_favorites_cursor_pagination.rollback.sql"),
    read("supabase/migrations/20260628_0002_chat_rpc.sql"),
    read("scripts/selective-db-release-executor.mjs"),
  ]);

  assert.match(sql, /create or replace function public\.quata_chat_get_favorites_page\s*\(/);
  assert.doesNotMatch(sql, /create or replace function public\.quata_chat_get_favorites\s*\(/);
  assert.match(sql, /quata_chat_actor_profile_id\(p_actor_profile_id\)/);
  assert.match(sql, /order by m\.created_at desc, m\.id desc/);
  assert.match(sql, /limit v_limit \+ 1/);
  assert.match(sql, /m\.created_at < p_before_created_at/);
  assert.match(sql, /m\.created_at = p_before_created_at and m\.id < p_before_message_id/);
  assert.match(sql, /'has_more', v_has_more/);
  assert.match(sql, /'next_cursor'/);
  assert.match(sql, /revoke all on function public\.quata_chat_get_favorites_page[\s\S]*from public, anon/);
  assert.match(sql, /grant execute on function public\.quata_chat_get_favorites_page[\s\S]*to authenticated/);
  assert.doesNotMatch(sql, /to anon/);

  assert.match(legacyMigration, /create or replace function public\.quata_chat_get_favorites\s*\(/);
  assert.match(rollback, /drop function if exists public\.quata_chat_get_favorites_page/);
  assert.doesNotMatch(rollback, /drop function if exists public\.quata_chat_get_favorites\s*\(\s*uuid\s*,\s*integer\s*\)/);

  assert.match(selectiveRelease, /20261001211500/);
  assert.match(selectiveRelease, /50ef988f6b843ae921de5e41e69e739da443c737844159b2a8d5b064eea54f71/);
  assert.match(
    selectiveRelease,
    /async function assertProductPostconditions\(client, selectedVersions, installedVersions\)/,
  );
  assert.match(
    selectiveRelease,
    /expectedAccountDeactivateMd5 = accountDeactivateDefinitionMd5\(installedVersions\)/,
  );
  assert.match(
    selectiveRelease,
    /installedVersions = \[\.\.\.pkg\.anchors, \.\.\.pkg\.selected\]\.map\(\(\{ version \}\) => version\)/,
  );
  assert.match(
    selectiveRelease,
    /assertProductPostconditions\(client, selectedVersions, installedVersions\)/,
  );
  for (const postcondition of [
    "selective_release_favorites_pagination_function_missing",
    "selective_release_favorites_pagination_security_failed",
    "selective_release_favorites_pagination_definition_failed",
    "selective_release_favorites_pagination_legacy_contract_changed",
    "selective_release_favorites_pagination_first_page_postcondition_failed",
    "selective_release_favorites_pagination_second_page_postcondition_failed",
  ]) {
    assert.match(selectiveRelease, new RegExp(postcondition));
  }
});

test("later releases validate the installed account-deactivation definition", () => {
  assert.equal(
    accountDeactivateDefinitionMd5(["20260928013000", "20261001211500"]),
    "290fcd85f9a57e8c999f3132235fdbe4",
  );
  assert.equal(
    accountDeactivateDefinitionMd5(["20261001211500"]),
    "d2504acfb2095176289fb99a939f7621",
  );
});

test("common and Android runtimes page favorites, preserve depth, and isolate actors", async () => {
  const [common, androidRepository, androidPager, androidPagerTest, androidApi, androidModels, viewModel, repositoryTest] = await Promise.all([
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/PostgrestChatRepository.kt"),
    read("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt"),
    read("app/src/main/java/com/quata/feature/chat/data/AndroidChatFavoritesPager.kt"),
    read("app/src/test/java/com/quata/feature/chat/data/AndroidChatFavoritesPagerTest.kt"),
    read("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt"),
    read("app/src/main/java/com/quata/data/supabase/SupabaseModels.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
    read("feature/chat/src/commonTest/kotlin/com/quata/feature/chat/data/PostgrestChatRepositoryTest.kt"),
  ]);

  assert.match(common, /quata_chat_get_favorites_page/);
  assert.match(common, /p_before_created_at/);
  assert.match(common, /p_before_message_id/);
  assert.match(common, /loadedFavoritesPageCount\.coerceAtLeast\(1\)/);
  assert.match(common, /while \(loadedPages < pagesToRetain && hasMore\)/);
  assert.match(common, /chat_favorites_cursor_missing/);
  assert.match(common, /favoritesMutex\.withLock/);
  assert.match(common, /favoritesOwnerActorId/);
  assert.match(common, /favoritesGeneration/);
  assert.match(common, /chat_favorites_actor_generation_changed/);

  assert.match(androidRepository, /getChatFavoritesPage/);
  assert.match(androidRepository, /favoritesPager\.refresh/);
  assert.match(androidRepository, /favoritesPager\.loadOlder/);
  assert.match(androidRepository, /chat_favorites_session_actor_mismatch/);
  assert.match(androidRepository, /chat_favorites_cursor_missing/);
  assert.match(androidPager, /class AndroidChatFavoritesPager/);
  assert.match(androidPager, /private val mutex = Mutex\(\)/);
  assert.match(androidPager, /suspend fun switchActor/);
  assert.match(androidPager, /suspend fun refresh/);
  assert.match(androidPager, /suspend fun loadOlder/);
  assert.match(androidPager, /commit\(snapshot\)/);
  assert.match(androidApi, /"quata_chat_get_favorites_page"/);
  assert.match(androidModels, /data class QuataChatFavoritesPageRequest/);

  assert.doesNotMatch(viewModel, /conversationId\s*==\s*AppDestinations\.FavoriteMessagesConversationId\)\s*return/);
  assert.match(repositoryTest, /favoritesCursorLoadsSixHundredAndOneMessagesAcrossThreeStablePages/);
  assert.match(repositoryTest, /assertEquals\(601, complete\.size\)/);
  assert.match(repositoryTest, /p_before_message_id\\\":352/);
  assert.match(repositoryTest, /p_before_message_id\\\":102/);
  assert.match(repositoryTest, /favoritesActorSwitchClearsPreviousActorWhileOffline/);
  assert.match(repositoryTest, /favoritesInFlightResponseCannotCrossActorBoundary/);
  assert.match(repositoryTest, /favoritesRefreshSerializesWithOlderPageLoadWithoutTruncatingDepth/);

  assert.match(androidPagerTest, /exhaustsSixHundredAndOneRowsAndRefreshPreservesAllLoadedPages/);
  assert.match(androidPagerTest, /refreshAndOlderLoadAreSerializedWithoutDepthTruncation/);
  assert.match(androidPagerTest, /actorSwitchClearsCursorAndRejectsStaleInFlightLoader/);
  assert.match(androidPagerTest, /assertEquals\(601, refreshed\.messages\.size\)/);
});

test("disposable PostgreSQL proof covers ACL, 601 rows, cursor ties, and rollback", async () => {
  const [fixture, runner] = await Promise.all([
    read("scripts/sql/chat-favorites-cursor-pagination.test.sql"),
    read("scripts/test-chat-favorites-cursor-pagination.ps1"),
  ]);

  assert.match(fixture, /generate_series\(1, 601\)/);
  assert.match(fixture, /count\(distinct \(row->>'id'\)::bigint\)/);
  assert.match(fixture, /v_unique_count <> 601/);
  assert.match(fixture, /next_cursor,message_id}'\)::bigint <> 352/);
  assert.match(fixture, /next_cursor,message_id}'\)::bigint <> 102/);
  assert.match(fixture, /has_function_privilege\('authenticated'/);
  assert.match(fixture, /has_function_privilege\('anon'/);
  assert.match(runner, /postgres:17-alpine/);
  assert.match(runner, /20261001211500_chat_favorites_cursor_pagination\.rollback\.sql/);
  assert.match(runner, /CHAT_FAVORITES_CURSOR_PAGINATION_POSTGRES_PASS/);
});
