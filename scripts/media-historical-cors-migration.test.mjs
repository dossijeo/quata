import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  OPT_IN,
  buildPlan,
  buildRollbackStatements,
  buildSwitchStatements,
  encodeStoragePath,
  sanitizedPlan,
  sha256,
  validateBackupAuthorization,
  validateJournal,
} from "./media-historical-cors-migration.mjs";

const rows = [
  { table: "community_posts", id: "11111111-1111-4111-8111-111111111111", sourceUrl: "https://egquata.com/wp-content/uploads/2026/08/a.mp4" },
  { table: "community_posts", id: "22222222-2222-4222-8222-222222222222", sourceUrl: "https://egquata.com/wp-content/uploads/2026/08/a.mp4" },
  { table: "official_posts", id: "33333333-3333-4333-8333-333333333333", sourceUrl: "https://egquata.com/wp-content/uploads/2026/08/a.mp4" },
];

test("plan is deterministic, deduplicates per bucket and separates bucket semantics", () => {
  const plan = buildPlan(rows, "cors-20261011-abcdef01");
  assert.equal(plan.entries.length, 3);
  assert.equal(plan.targets.length, 2);
  assert.deepEqual(sanitizedPlan(plan).byTable, { community_posts: 2, official_posts: 1 });
  assert.deepEqual(sanitizedPlan(plan).byBucket, { "community-posts": 1, "official-media": 1 });
  assert.equal(plan.entries[0].sourceHash, sha256(rows[0].sourceUrl));
  assert.match(plan.entries[0].objectPath, /^historical-cors-v1\/cors-20261011-abcdef01\/[a-f0-9]{64}\.mp4$/);
  assert.equal(encodeStoragePath("one/two three.mp4"), "one/two%20three.mp4");
});

test("plan rejects unowned source domains and invalid run identifiers", () => {
  assert.throws(() => buildPlan([{ ...rows[0], sourceUrl: "https://example.com/a.mp4" }], "cors-20261011-abcdef01"), /invalid_source_row/);
  assert.throws(() => buildPlan(rows, "../escape"), /invalid_run_id/);
});

test("backup authorization requires a fresh encrypted full backup and matching scoped restore", () => {
  const now = new Date("2026-10-11T12:00:00.000Z");
  const manifest = { format: "quata-logical-backup-v1", scope: "Full", encryption: "AES-256-GCM", containsConnectionData: false, grantsIncluded: true, createdAt: "2026-10-11T11:00:00.000Z" };
  const proof = { status: "passed", cleanupVerified: true, backupManifestSha256: "a".repeat(64), tables: ["public.community_posts", "public.official_posts"] };
  assert.equal(validateBackupAuthorization({ manifest, manifestSha256: "a".repeat(64), restoreProof: proof, now }), true);
  assert.throws(() => validateBackupAuthorization({ manifest: { ...manifest, scope: "Critical" }, manifestSha256: "a".repeat(64), restoreProof: proof, now }), /full_encrypted_backup_required/);
  assert.throws(() => validateBackupAuthorization({ manifest, manifestSha256: "b".repeat(64), restoreProof: proof, now }), /matching_restore_drill_required/);
  assert.throws(() => validateBackupAuthorization({ manifest, manifestSha256: "a".repeat(64), restoreProof: { ...proof, tables: ["public.community_posts"] }, now }), /media_restore_scope_required/);
  assert.equal(OPT_IN, "I_ACCEPT_REVERSIBLE_HISTORICAL_MEDIA_MIGRATION");
});

test("switch and rollback statements are exact-current-value guarded", () => {
  const plan = buildPlan(rows.slice(0, 1), "cors-20261011-abcdef01");
  plan.entries[0].publicUrl = "https://project.supabase.co/storage/v1/object/public/community-posts/historical-cors-v1/run/a.mp4";
  const forward = buildSwitchStatements(plan)[0];
  const rollback = buildRollbackStatements({ entries: plan.entries })[0];
  assert.match(forward.text, /where id = \$1::uuid and video_url = \$2$/);
  assert.deepEqual(forward.values, [plan.entries[0].id, plan.entries[0].sourceUrl, plan.entries[0].publicUrl]);
  assert.deepEqual(rollback.values, [plan.entries[0].id, plan.entries[0].publicUrl, plan.entries[0].sourceUrl]);
});

test("rollback journal cannot redirect SQL or storage cleanup outside the owned run prefix", () => {
  const backendOrigin = "https://project.supabase.co";
  const plan = buildPlan(rows.slice(0, 1), "cors-20261011-abcdef01");
  const entry = plan.entries[0];
  entry.publicUrl = `${backendOrigin}/storage/v1/object/public/${entry.bucket}/${entry.objectPath}`;
  const target = {
    bucket: entry.bucket,
    objectPath: entry.objectPath,
    sourceHash: entry.sourceHash,
    contentSha256: "a".repeat(64),
    size: 100,
  };
  const journal = { check: "OVR-MEDIA-HISTORICAL-CORS-MIGRATION-001", backendOrigin, plan: sanitizedPlan(plan), entries: [entry], targets: [target] };
  assert.equal(validateJournal(journal, backendOrigin), journal);
  assert.throws(() => validateJournal({ ...journal, entries: [{ ...entry, table: "community_posts; drop table public.community_posts;--" }] }, backendOrigin), /journal_entry_scope_invalid/);
  assert.throws(() => validateJournal({ ...journal, targets: [{ ...target, objectPath: "avatars/victim.jpg" }] }, backendOrigin), /journal_target_path_invalid/);
  assert.throws(() => validateJournal({ ...journal, entries: [{ ...entry, publicUrl: "https://attacker.example/victim.mp4" }] }, backendOrigin), /journal_entry_public_url_invalid/);
  assert.throws(() => validateJournal({ ...journal, entries: [{ ...entry, sourceHash: "b".repeat(64) }] }, backendOrigin), /journal_entry_mapping_invalid/);
  assert.throws(() => validateJournal({ ...journal, targets: [...journal.targets, { ...target, bucket: "official-media" }] }, backendOrigin), /journal_target_unreferenced/);
});

test("logical backup restore drill covers both URL-bearing tables and emits a bound proof", async () => {
  const source = await readFile(new URL("./restore-db-logical-backup-drill.ps1", import.meta.url), "utf8");
  assert.match(source, /\[switch\]\$MediaHistoricalCorsScope/);
  assert.match(source, /@\("community_posts", "official_posts"\)/);
  assert.match(source, /ExpectedCommunityPosts/);
  assert.match(source, /ExpectedOfficialPosts/);
  assert.match(source, /backupManifestSha256=\(Get-FileHash \(Join-Path \$BackupSet "manifest\.json"\) -Algorithm SHA256\)\.Hash\.ToLowerInvariant\(\)/);
  assert.match(source, /tables=\$tables/);
  assert.match(source, /target="disposable-postgresql-17"/);
  assert.match(source, /cleanupVerified=\$true/);
  assert.match(source, /\$dockerPsExitCode=\$LASTEXITCODE/);
  assert.match(source, /restore_target_cleanup_verification_failed/);
});

test("runbook preserves the bounded source, backup, postflight and rollback contract", async () => {
  const source = await readFile(new URL("../docs/runbooks/migration/MEDIA_HISTORICAL_CORS.md", import.meta.url), "utf8");
  assert.match(source, /community_posts\.video_url/);
  assert.match(source, /official_posts\.media_url/);
  assert.match(source, /egquata\.com\/wp-content\/uploads/);
  assert.match(source, /-Scope Full/);
  assert.match(source, /-MediaHistoricalCorsScope/);
  assert.match(source, /--mode postflight/);
  assert.match(source, /--mode rollback/);
  assert.match(source, /No se versionan credenciales, URLs de origen, IDs de filas, tokens ni/);
  assert.match(source, /archivo temporal sincronizado y renombrado atómico/);
  assert.match(source, /lock exclusivo/);
});

test("journal checkpoints are atomic and mutation modes hold an exclusive lock", async () => {
  const source = await readFile(new URL("./media-historical-cors-migration.mjs", import.meta.url), "utf8");
  assert.match(source, /await handle\.sync\(\)[\s\S]*await rename\(temporaryPath, path\)/);
  assert.match(source, /open\(lockPath, "wx", 0o600\)/);
  assert.match(source, /\["apply", "postflight", "rollback"\][\s\S]*acquireJournalLock/);
  assert.match(source, /await releaseJournalLock\(journalLock\)/);
});

test("all active post video producers use authenticated Supabase Storage and retain rollback", async () => {
  const [androidFeed, androidOfficial, androidApi, androidHttp, web, webMain, ios, iosOfficial] = await Promise.all([
    readFile(new URL("../app/src/main/java/com/quata/feature/postcomposer/data/PostComposerRepositoryImpl.kt", import.meta.url), "utf8"),
    readFile(new URL("../app/src/main/java/com/quata/feature/official/data/OfficialRepositoryImpl.kt", import.meta.url), "utf8"),
    readFile(new URL("../app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt", import.meta.url), "utf8"),
    readFile(new URL("../app/src/main/java/com/quata/data/supabase/SupabaseHttpClient.kt", import.meta.url), "utf8"),
    readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerRepository.kt", import.meta.url), "utf8"),
    readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt", import.meta.url), "utf8"),
    readFile(new URL("../feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/data/IosPostComposerTransport.kt", import.meta.url), "utf8"),
    readFile(new URL("../feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/QuataOfficialViewController.kt", import.meta.url), "utf8"),
  ]);
  assert.doesNotMatch(androidFeed, /uploadPostVideoRest/);
  assert.doesNotMatch(androidOfficial, /uploadPostVideoRest/);
  assert.match(androidFeed, /supabaseApi\.uploadPostVideo[\s\S]*supabaseApi\.deletePostMediaObject/);
  assert.match(androidOfficial, /supabaseApi\.uploadPostVideo/);
  assert.match(androidOfficial, /supabaseApi\.deletePostMediaObject/);
  assert.match(androidApi, /openStream: \(\) -> InputStream/);
  assert.match(androidHttp, /sink\.writeAll\(input\.source\(\)\)/);
  assert.doesNotMatch(web, /wp-json\/quqos\/v1\/upload-video/);
  assert.match(web, /webComposerUploadBlob[\s\S]*webComposerStorageRollbackToken/);
  assert.match(webMain, /mediaTransport = WebPostComposerTransport\(runtimeConfiguration, authRepository\)/);
  assert.doesNotMatch(ios, /iosComposerWordpressVideoUploadUrl/);
  assert.match(ios, /iosComposerStorageRollbackToken\(IOS_COMPOSER_COMMUNITY_MEDIA_BUCKET, path\)/);
  assert.match(iosOfficial, /mediaTransport = IosPostComposerTransport/);
});
