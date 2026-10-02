import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

const root = resolve(import.meta.dirname, "..");
const read = (path) => readFileSync(resolve(root, path), "utf8");
const version = "20261002010000";
const migration = read(`supabase/migrations/${version}_community_post_likes_actor_guard.sql`);
const rollback = read(`supabase/rollbacks/${version}_community_post_likes_actor_guard.rollback.sql`);
const sqlTest = read("scripts/sql/community-post-likes-actor-guard.test.sql");
const sqlRunner = read("scripts/test-community-post-likes-actor-guard.ps1");
const postgrestSetup = read("scripts/sql/community-post-likes-postgrest.setup.sql");
const postgrestTest = read("scripts/community-post-likes-postgrest.test.mjs");
const postgrestRunner = read("scripts/run-community-post-likes-postgrest-test.ps1");
const releasePostflight = read("scripts/community-post-likes-release-postflight.mjs");
const transactionalProbe = read("scripts/community-post-likes-transactional-probe.mjs");
const selectiveExecutor = read("scripts/selective-db-release-executor.mjs");
const restoreDrill = read("scripts/restore-db-logical-backup-drill.ps1");
const androidRepository = read("app/src/main/java/com/quata/feature/feed/data/FeedRepositoryImpl.kt");
const androidApi = read("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt");
const webRepository = read("web/src/wasmJsMain/kotlin/com/quata/web/WebFeedRepository.kt");
const iosRepository = read("feature/feed/src/iosMain/kotlin/com/quata/feature/feed/data/IosAuthenticatedFeedRepository.kt");
const publishedReference = read("docs/ANDROID_PUBLISHED_REFERENCE_V32.md");
const productionBaselineRaw = read("docs/runbooks/migration/evidence/community-post-likes-production-baseline-20261002.json");
const productionBaseline = JSON.parse(productionBaselineRaw);
const publishedEvidenceRaw = read("docs/runbooks/migration/evidence/community-post-likes-published-v32-static-20261002.json");
const publishedEvidence = JSON.parse(publishedEvidenceRaw);
const packageJson = JSON.parse(read("package.json"));

test("sanitized production baseline proves the unrestricted mutation surface", () => {
  assert.equal(productionBaseline.status, "confirmed");
  assert.equal(productionBaseline.table, "community_post_likes");
  assert.equal(productionBaseline.guarantees.transaction, "read-only");
  assert.equal(productionBaseline.guarantees.productionDmlAttempted, false);
  assert.equal(productionBaseline.finding.anonymousInsertAllowed, true);
  assert.equal(productionBaseline.finding.anonymousDeleteAllowed, true);
  assert.deepEqual(productionBaseline.grants.anon, ["DELETE", "INSERT", "REFERENCES", "SELECT", "TRIGGER", "TRUNCATE", "UPDATE"]);
  assert.deepEqual(productionBaseline.policies.map(({ command }) => command), ["DELETE", "INSERT", "SELECT"]);
  assert.deepEqual(productionBaseline.userTriggers, []);
  assert.doesNotMatch(productionBaselineRaw, /[A-Z]:\\|postgres(?:ql)?:\/\/|eyJ[A-Za-z0-9_-]+\./i);
});

test("forward migration preserves public reads and binds both mutations to the active actor", () => {
  assert.match(migration, /alter table public\.community_post_likes enable row level security/i);
  assert.match(migration, /create policy community_post_likes_public_read[\s\S]*for select[\s\S]*to public[\s\S]*using \(true\)/i);
  assert.match(migration, /create policy community_post_likes_insert_own[\s\S]*for insert[\s\S]*to authenticated[\s\S]*profile_id = \(select public\.quata_chat_auth_profile_id\(\)\)/i);
  assert.match(migration, /create policy community_post_likes_delete_own[\s\S]*for delete[\s\S]*to authenticated[\s\S]*profile_id = \(select public\.quata_chat_auth_profile_id\(\)\)/i);
  assert.equal((migration.match(/\(select public\.quata_chat_auth_profile_id\(\)\) is not null/g) ?? []).length, 2);
  assert.match(migration, /revoke all privileges on table public\.community_post_likes[\s\S]*from public, anon, authenticated/i);
  assert.match(migration, /grant select on table public\.community_post_likes to anon/i);
  assert.match(migration, /grant select, insert, delete on table public\.community_post_likes to authenticated/i);
  assert.doesNotMatch(migration, /quata_legacy_android_v32_compatibility|x-quata-client-generation|okhttp\/4\.12\.0/i);
});

test("rollback restores the exact pre-release public policies and broad grants", () => {
  assert.match(rollback, /create policy "public delete likes"[\s\S]*for delete[\s\S]*to public[\s\S]*using \(true\)/i);
  assert.match(rollback, /create policy "public insert likes"[\s\S]*for insert[\s\S]*to public[\s\S]*with check \(true\)/i);
  assert.match(rollback, /create policy "public read likes"[\s\S]*for select[\s\S]*to public[\s\S]*using \(true\)/i);
  assert.match(rollback, /grant delete, insert, references, select, trigger, truncate, update[\s\S]*to anon, authenticated/i);
});

test("disposable PostgreSQL test distinguishes anon, cross-actor and own-actor behavior", () => {
  assert.match(sqlTest, /set role anon[\s\S]*anon mutation was not rejected/i);
  assert.match(sqlTest, /cross-actor insert unexpectedly succeeded/i);
  assert.match(sqlTest, /cross-actor delete removed the owner row/i);
  assert.match(sqlTest, /own delete did not remove the row/i);
  assert.match(sqlTest, /public read did not preserve both visible rows/i);
  assert.match(sqlTest, /set_config\('request\.jwt\.claim\.profile_id', '00000000-0000-0000-0000-000000000002'[\s\S]*insert into public\.community_post_likes[\s\S]*delete from public\.community_post_likes[\s\S]*cross-actor delete removed the owner row/i);
  assert.match(sqlTest, /COMMUNITY_POST_LIKES_ACTOR_GUARD_TEST_OK/);
  assert.match(sqlTest, /20261002010000_community_post_likes_actor_guard\.sql/);
  assert.match(sqlTest, /20261002010000_community_post_likes_actor_guard\.rollback\.sql/);
  assert.match(sqlRunner, /postgres:17-alpine/);
  assert.match(sqlRunner, /community-post-likes-actor-guard\.test\.sql/);
  assert.match(sqlRunner, /\$ErrorActionPreference = "Continue"[\s\S]*\$psqlExitCode = \$LASTEXITCODE[\s\S]*\$ErrorActionPreference = \$previous/);
});

test("isolated PostgREST gate exercises the same HTTP boundary as all platform clients", () => {
  assert.match(postgrestSetup, /20261002010000_community_post_likes_actor_guard\.sql/);
  assert.match(postgrestRunner, /postgres:17-alpine/);
  assert.match(postgrestRunner, /postgrest\/postgrest:v12\.2\.3/);
  assert.match(postgrestRunner, /community-post-likes-postgrest\.test\.mjs/);
  assert.match(postgrestRunner, /\$ErrorActionPreference = "Continue"[\s\S]*\$setupExitCode = \$LASTEXITCODE[\s\S]*\$ErrorActionPreference = \$previous/);
  assert.match(postgrestSetup, /create function auth\.uid\(\)[\s\S]*request\.jwt\.claim\.sub/);
  assert.match(postgrestSetup, /security definer[\s\S]*set search_path = public, auth[\s\S]*cp\.account_status = 'active'/);
  assert.match(postgrestSetup, /cp\.id = auth\.uid\(\) or cp\.auth_user_id = auth\.uid\(\)/);
  assert.doesNotMatch(postgrestSetup, /request\.jwt\.claim\.profile_id/);
  assert.match(postgrestTest, /anonymous insert was not rejected/);
  assert.match(postgrestTest, /cross-actor insert was not rejected/);
  assert.match(postgrestTest, /cross-actor delete was not filtered by RLS/);
  assert.match(postgrestTest, /cross-actor delete changed public state/);
  assert.match(postgrestTest, /anonymous delete was not rejected/);
  assert.match(postgrestTest, /actor A own delete failed/);
  assert.match(postgrestTest, /actor B own delete failed/);
  assert.match(postgrestTest, /inactive mapped actor was not rejected/);
  assert.match(postgrestTest, /PostgREST fixture residue remained/);
});

test("Android, Web/Wasm and iOS current clients require an authenticated actor", () => {
  assert.match(androidRepository, /currentSession\(\) \?: error\("No hay sesion activa"\)[\s\S]*remote\.toggleLike\(postId, session\.userId\)/);
  assert.match(androidApi, /suspend fun toggleLike[\s\S]*getSingleOrNull<CommunityPostLike>\("community_post_likes"[\s\S]*client\.delete\("community_post_likes"[\s\S]*client\.post<CommunityPostLike, CommunityPostLikeCreate>\("community_post_likes"/);
  assert.match(webRepository, /override suspend fun toggleLike[\s\S]*restoreLocalSession\(\)\?\.userId \?: error\("web_session_missing"\)[\s\S]*client\.post\("community_post_likes"[\s\S]*client\.delete\("community_post_likes"/);
  assert.match(iosRepository, /override suspend fun toggleLike[\s\S]*transport\.currentUserId\(\)[\s\S]*transport\.mutate\("community_post_likes", "DELETE"[\s\S]*transport\.mutate\("community_post_likes", "POST"/);
});

test("published Android v32 static evidence proves no anonymous like compatibility is needed", () => {
  assert.equal(publishedEvidence.status, "passed");
  assert.equal(publishedEvidence.artifact.versionCode, 32);
  assert.equal(publishedEvidence.artifact.aabSha256, "bf6aadc60e18b05d4f4203c8356a9e9a8b4c917262cc0a1d28518b8c6baf70ff");
  assert.equal(publishedEvidence.extractedArtifacts.r8MappingSha256, "670f59516a727c84537fdd12e9ae6da26b8d84a70f72ac6047c3763313ec2320");
  assert.equal(publishedEvidence.extractedArtifacts.baseDexSha256, "51aa7c0c8e987847d2bcd80bd1d243801f5afc66bf1891cf94e67a375c0bcc3");
  assert.deepEqual(publishedEvidence.mappingProof.map(({ line }) => line), [76656, 76660, 76663, 387637]);
  assert.equal(publishedEvidence.conclusion.likeMutationRequiresAuthenticatedSession, true);
  assert.equal(publishedEvidence.conclusion.anonymousCompatibilityRequired, false);
  assert.equal(publishedEvidence.privacy.containsSecrets, false);
  assert.doesNotMatch(publishedEvidenceRaw, /[A-Z]:\\|postgres(?:ql)?:\/\/|eyJ[A-Za-z0-9_-]+\./i);
  assert.match(publishedReference, /Contrato de likes comprobado en el binario/);
  assert.match(publishedReference, /no necesita una rama anónima de compatibilidad para likes/i);
});

test("full-backup restore drill has a dedicated affected-table scope", () => {
  assert.match(restoreDrill, /\[switch\]\$CommunityPostLikesScope/);
  assert.match(restoreDrill, /restore_community_post_likes_scope_requires_full_backup/);
  assert.match(restoreDrill, /backup_toc_community_post_likes_table_missing/);
  assert.match(restoreDrill, /backup_toc_community_post_likes_data_missing/);
  assert.match(restoreDrill, /backup_toc_community_post_likes_acl_missing/);
  assert.match(restoreDrill, /backup_toc_community_post_likes_policy_state_missing/);
  assert.match(restoreDrill, /backup_toc_community_post_likes_resolver_acl_missing/);
  assert.match(restoreDrill, /restore_expected_community_post_likes_required/);
  assert.match(restoreDrill, /--use-list=\/backup\/community-post-likes\.restore\.list/);
  assert.match(restoreDrill, /WriteAllLines\(\$likesRestoreList[\s\S]*UTF8Encoding\]::new\(\$false\)/);
  assert.match(restoreDrill, /restore_community_post_likes_security_state_mismatch/);
  assert.match(restoreDrill, /public delete likes[\s\S]*public insert likes[\s\S]*public read likes/);
  assert.match(restoreDrill, /DELETE,INSERT,REFERENCES,SELECT,TRIGGER,TRUNCATE,UPDATE/);
  assert.match(restoreDrill, /quata_chat_auth_profile_id[\s\S]*has_function_privilege\('authenticated'/);
  assert.match(read("scripts/test-db-logical-backup-drill.ps1"), /docker cp \$seedFile[\s\S]*-f \/tmp\/quata-backup-seed\.sql/);
  assert.match(restoreDrill, /ExpectedCommunityPostLikes/);
});

test("production postflight is read-only, TLS-verified and emits metadata only", () => {
  assert.match(releasePostflight, /--db-url-file/);
  assert.match(releasePostflight, /--tls-ca-file/);
  assert.match(releasePostflight, /sslmode[\s\S]*verify-full/);
  assert.match(releasePostflight, /rejectUnauthorized:\s*true/);
  assert.match(releasePostflight, /begin read only/);
  assert.match(releasePostflight, /const ledger = await client\.query[\s\S]*const resolver = await client\.query/);
  assert.doesNotMatch(releasePostflight, /const \[ledger, table, policies, grants, triggers, resolver\] = await Promise\.all/);
  assert.match(releasePostflight, /supabase_migrations\.schema_migrations/);
  assert.match(releasePostflight, /postflight_policy_set_mismatch/);
  assert.match(releasePostflight, /expression !== EXPECTED_ACTOR_EXPRESSION/);
  assert.match(releasePostflight, /normalized\(resolver\.source\) !== EXPECTED_RESOLVER_SOURCE/);
  assert.match(releasePostflight, /postflight_resolver_mismatch/);
  assert.match(releasePostflight, /has_function_privilege\('authenticated'/);
  assert.match(releasePostflight, /postflight_anon_grants_mismatch/);
  assert.match(releasePostflight, /postflight_authenticated_grants_mismatch/);
  assert.match(releasePostflight, /businessValuesEmitted:\s*false/);
  assert.match(releasePostflight, /secretsEmitted:\s*false/);
  assert.match(releasePostflight, /postflight_failed_redacted/);
  assert.doesNotMatch(releasePostflight, /select\s+\*\s+from\s+public\.community_post_likes/i);
  assert.doesNotMatch(releasePostflight, /console\.(?:log|error)/);
});

test("production probe validates predeploy and committed postdeploy behavior inside rollback custody", () => {
  assert.match(transactionalProbe, /--db-url-file/);
  assert.match(transactionalProbe, /--tls-ca-file/);
  assert.match(transactionalProbe, /sslmode[\s\S]*verify-full/);
  assert.match(transactionalProbe, /rejectUnauthorized:\s*true/);
  assert.match(transactionalProbe, /await client\.query\("begin"\)/);
  assert.match(transactionalProbe, /mode: "predeploy"/);
  assert.match(transactionalProbe, /"postdeploy"/);
  assert.match(transactionalProbe, /if \(args\.mode === "predeploy"\) await client\.query\(migration\)/);
  assert.match(transactionalProbe, /assertForward\(await snapshot\(client\)\)/);
  assert.match(transactionalProbe, /assertInstalledLedger/);
  assert.match(transactionalProbe, /20261002010000/);
  assert.match(transactionalProbe, /community_post_likes_actor_guard/);
  assert.match(transactionalProbe, /probe_forward_resolver_mismatch/);
  assert.match(transactionalProbe, /client\.query\(`set local role \$\{role\}`\)/);
  assert.match(transactionalProbe, /client, "anon_insert", "anon", null, insert/);
  assert.match(transactionalProbe, /probe_anonymous_insert_not_rejected/);
  assert.match(transactionalProbe, /probe_cross_actor_insert_not_rejected/);
  assert.match(transactionalProbe, /probe_cross_actor_delete_not_filtered/);
  assert.match(transactionalProbe, /probe_own_insert_failed/);
  assert.match(transactionalProbe, /probe_own_delete_failed/);
  assert.match(transactionalProbe, /await client\.query\("rollback"\)/);
  assert.equal((transactionalProbe.match(/assertBaseline\(await snapshot\(client\)\)/g) ?? []).length, 2);
  assert.match(transactionalProbe, /COMMUNITY_POST_LIKES_TRANSACTIONAL_PROBE_PASS/);
  assert.match(transactionalProbe, /COMMUNITY_POST_LIKES_POSTDEPLOY_PASS/);
  assert.match(transactionalProbe, /probe_fixture_residue_detected/);
  assert.match(transactionalProbe, /probe_failed_redacted/);
  assert.doesNotMatch(transactionalProbe, /client\.query\(["']commit["']\)/i);
  assert.doesNotMatch(transactionalProbe, /select\s+\*\s+from\s+public\.community_post_likes/i);
  assert.doesNotMatch(transactionalProbe, /console\.(?:log|error)/);
});

test("the focal contract is present in both fast suites", () => {
  assert.match(packageJson.scripts["test:community-post-likes-actor-guard"], /community-post-likes-actor-guard-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /community-post-likes-actor-guard-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /community-post-likes-actor-guard-contract\.test\.mjs/);
});

test("selective release allowlist binds the exact migration byte SHA-256", () => {
  assert.match(selectiveExecutor, /20261002010000[\s\S]*749ff3d6f7748be355e4b7f88f77db1f4bdeb015689590b42c449f1d1e60753c/);
  assert.doesNotMatch(selectiveExecutor, /dc46d2e38227b3d52a73f21200bb68481fdaa576/);
});

test("selective release postconditions require the exact relation boundary", () => {
  assert.match(selectiveExecutor, /selectedVersions\.includes\("20261002010000"\)/);
  assert.match(selectiveExecutor, /relrowsecurity as rls_enabled/);
  assert.match(selectiveExecutor, /user_trigger_count !== 0/);
  assert.match(selectiveExecutor, /selective_release_community_post_likes_relation_postcondition_failed/);
});

test("selective release pins policies and least-privilege grants", () => {
  for (const token of [
    "community_post_likes_public_read",
    "community_post_likes_insert_own",
    "community_post_likes_delete_own",
    "{public}",
    "{authenticated}",
    "quata_chat_auth_profile_id",
  ]) assert.ok(selectiveExecutor.includes(token), `missing ${token}`);
  assert.match(selectiveExecutor, /policies\.length !== 3/);
  assert.match(selectiveExecutor, /grants\.get\("anon"\)[\s\S]*\["SELECT"\]/);
  assert.match(selectiveExecutor, /grants\.get\("authenticated"\)[\s\S]*\["DELETE", "INSERT", "SELECT"\]/);
  assert.match(selectiveExecutor, /grants\.get\("PUBLIC"\)/);
  assert.match(selectiveExecutor, /selective_release_community_post_likes_policy_postcondition_failed/);
  assert.match(selectiveExecutor, /selective_release_community_post_likes_grant_postcondition_failed/);
});

test("selective release pins resolver definition, security and execution ACL", () => {
  assert.match(selectiveExecutor, /p\.prosecdef as security_definer/);
  assert.match(selectiveExecutor, /search_path=public, auth/);
  assert.match(selectiveExecutor, /cp\.account_status = 'active'/);
  assert.match(selectiveExecutor, /resolver\.public_execute/);
  assert.match(selectiveExecutor, /resolver\.anon_execute/);
  assert.match(selectiveExecutor, /resolver\.authenticated_execute/);
  assert.match(selectiveExecutor, /selective_release_community_post_likes_resolver_postcondition_failed/);
});
