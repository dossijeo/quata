import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

test("migration exposes a stable invoker RPC with a complete total-order cursor", () => {
  const migration = read("supabase/migrations/20261002013000_official_feed_total_order.sql");
  const rollback = read("supabase/rollbacks/20261002013000_official_feed_total_order.rollback.sql");

  assert.match(migration, /create or replace function public\.quata_official_feed_page\([\s\S]*?p_before_sort_at timestamptz[\s\S]*?p_before_created_at timestamptz[\s\S]*?p_before_id uuid/);
  assert.match(migration, /stable\s+security invoker\s+set search_path = public, pg_temp/i);
  assert.match(migration, /cursor_values not in \(0, 3\)/);
  assert.match(migration, /errcode = '22023'/);
  assert.match(migration, /coalesce\(chosen\.published_at, chosen\.created_at\),\s*chosen\.created_at,\s*chosen\.id[\s\S]*?< \([\s\S]*?p_before_sort_at,\s*p_before_created_at,\s*p_before_id/);
  assert.match(migration, /grant execute on function public\.quata_official_feed_page[\s\S]*?to anon, authenticated/);
  assert.match(rollback, /drop function if exists public\.quata_official_feed_page/);
  assert.match(rollback, /drop index if exists public\.official_posts_public_total_order_idx/);
});

test("shared domain carries the exact server tuple into older-page requests", () => {
  const models = read("feature/official/src/commonMain/kotlin/com/quata/feature/official/domain/OfficialModels.kt");
  const repository = read("feature/official/src/commonMain/kotlin/com/quata/feature/official/domain/OfficialRepository.kt");
  const viewModel = read("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedViewModel.kt");
  const protocol = read("feature/official/src/commonMain/kotlin/com/quata/feature/official/data/OfficialRemoteProtocol.kt");

  assert.match(repository, /data class OfficialFeedCursor\([\s\S]*?sortAt: String[\s\S]*?createdAt: String[\s\S]*?postId: String/);
  assert.match(models, /fun OfficialPostItem\.feedCursor\(\)[\s\S]*?publishedAt\.ifBlank \{ sourceCreatedAt \}[\s\S]*?createdAt = sourceCreatedAt[\s\S]*?postId = id/);
  assert.match(repository, /loadOlderOfficialFeedPage\(cursor: OfficialFeedCursor, limit: Int\)/);
  assert.match(viewModel, /lastOrNull\(\)\?\.feedCursor\(\)/);
  assert.match(protocol, /publishedAt = publishedAt \?: createdAt\.orEmpty\(\)[\s\S]*?sourceCreatedAt = createdAt \?: publishedAt\.orEmpty\(\)/);
});

test("Android Web and iOS use the anonymous RPC with language custody", () => {
  const androidApi = read("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt");
  const androidHttp = read("app/src/main/java/com/quata/data/supabase/SupabaseHttpClient.kt");
  const androidRepository = read("app/src/main/java/com/quata/feature/official/data/OfficialRepositoryImpl.kt");
  const webClient = read("web/src/wasmJsMain/kotlin/com/quata/web/WebPostgrestClient.kt");
  const webRepository = read("web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialRepository.kt");
  const iosRepository = read("feature/official/src/iosMain/kotlin/com/quata/feature/official/data/IosOfficialReadRepository.kt");

  assert.match(androidApi, /getOfficialFeedPage[\s\S]*?getPublicList\([\s\S]*?"rpc\/quata_official_feed_page"[\s\S]*?"p_before_sort_at"[\s\S]*?"p_before_created_at"[\s\S]*?"p_before_id"/);
  assert.match(androidHttp, /executePublicGet[\s\S]*?apiKeyOverride = config\.anonKey/);
  assert.match(androidHttp, /publicApiKeyOverride != null && bearerOverride\.isNullOrBlank\(\)[\s\S]*?removeHeader\("Authorization"\)/);
  assert.match(androidHttp, /addHeader\("x-quata-official-language", officialLanguage\)/);
  assert.match(androidRepository, /getOfficialFeedPage\([\s\S]*?beforeSortAt = cursor\?\.sortAt[\s\S]*?beforeCreatedAt = cursor\?\.createdAt[\s\S]*?beforeId = cursor\?\.postId/);

  assert.match(webRepository, /table = "rpc\/quata_official_feed_page"[\s\S]*?WebPostgrestAuthMode\.Public[\s\S]*?officialLanguage = officialLanguage/);
  assert.match(webClient, /if \(accessToken\) headers\.Authorization/);
  assert.match(webClient, /if \(officialLanguage\) headers\['x-quata-official-language'\]/);
  assert.match(iosRepository, /table = "rpc\/quata_official_feed_page"[\s\S]*?IosOfficialReadAuthMode\.Public[\s\S]*?officialLanguage = officialLanguage/);
  assert.match(iosRepository, /iosOfficialPublicHeaders[\s\S]*?x-quata-official-language/);
  assert.doesNotMatch(iosRepository.match(/internal fun iosOfficialPublicHeaders[\s\S]*?\n}/)?.[0] ?? "", /Authorization/);
});

test("Android anonymous RPC preserves language-scoped cache and observation", () => {
  const androidApi = read("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt");
  const androidHttp = read("app/src/main/java/com/quata/data/supabase/SupabaseHttpClient.kt");
  const androidRepository = read("app/src/main/java/com/quata/feature/official/data/OfficialRepositoryImpl.kt");
  const instrumented = read("app/src/androidTest/java/com/quata/data/supabase/OfficialFeedPublicCacheInstrumentedTest.kt");

  assert.match(androidApi, /observeOfficialFeedPage[\s\S]*?observePublicList\([\s\S]*?cacheTable = "official_posts"/);
  assert.match(androidApi, /getOfficialFeedPage[\s\S]*?cacheMode: SupabaseCacheMode[\s\S]*?cacheTable = "official_posts"[\s\S]*?cacheMode = cacheMode/);
  assert.match(androidRepository, /supabaseApi\.observeOfficialFeedPage\(limit = OfficialFeedPageSize\)/);
  assert.match(androidRepository, /beforeId = cursor\?\.postId,[\s\S]*?cacheMode = cacheMode/);
  assert.match(androidHttp, /publicCacheKey\(url, language\)/);
  assert.match(androidHttp, /#public-language=\$\{enc\(officialLanguage\.lowercase\(\)\)\}/);
  assert.match(androidHttp, /refreshPublicCachedGet[\s\S]*?executePublicGet\(url, officialLanguage\)[\s\S]*?tableName = tableName/);
  assert.match(androidHttp, /cacheInvalidationGeneration\(tableName\)[\s\S]*?executePublicGet\(url, officialLanguage\)[\s\S]*?cacheInvalidationGeneration\(tableName\) != generation/);
  assert.match(androidHttp, /cacheInvalidationGenerations\.computeIfAbsent\(table\)[\s\S]*?incrementAndGet\(\)[\s\S]*?invalidateTable\(table\)/);
  assert.match(androidHttp, /store\.invalidateKey\(key\)/);
  assert.match(instrumented, /network_must_not_run_for_fresh_offline_cache/);
  assert.match(instrumented, /client\.invalidateTables\("official_posts"\)/);
  assert.match(instrumented, /assertNull\(request\.header\("Authorization"\)\)/);
  assert.match(instrumented, /invalidationDuringPublicRpcDiscardsTheOlderResponse/);
  assert.match(instrumented, /firstRequestStarted[\s\S]*?invalidateTables\("official_posts"\)[\s\S]*?releaseFirstResponse\.countDown\(\)/);
});

test("isolated runner exercises SQL and real anonymous PostgREST", () => {
  const runner = read("scripts/run-official-feed-total-order-test.ps1");
  const probe = read("scripts/official-feed-total-order-postgrest.test.mjs");
  assert.match(runner, /official-feed-total-order\.test\.sql/);
  assert.match(runner, /postgrest\/postgrest:v12\.2\.3/);
  assert.match(runner, /official-feed-total-order-postgrest\.test\.mjs/);
  assert.match(probe, /anonymous Official feed probe must not send Authorization/);
  assert.match(probe, /new Set\(all\.map/);
  assert.match(probe, /incomplete\.status === 400 && incomplete\.value\?\.code === "22023"/);
});

test("selective release pins the exact migration and production postconditions", () => {
  const executor = read("scripts/selective-db-release-executor.mjs");
  assert.match(executor, /\["20261002013000", "64241db48e63ee41c599ed0c2ef53030c6857bd3b942dc44fbf86a991f04eb63"\]/);
  assert.match(executor, /selectedVersions\.includes\("20261002013000"\)/);
  assert.match(executor, /selective_release_official_feed_function_postcondition_failed/);
  assert.match(executor, /selective_release_official_feed_index_postcondition_failed/);
  assert.match(executor, /public_execute[\s\S]*?anon_execute[\s\S]*?authenticated_execute/);
  assert.match(executor, /partition by op\\\.translation_group_id/);
  assert.match(executor, /official_posts_public_total_order_idx/);
});
