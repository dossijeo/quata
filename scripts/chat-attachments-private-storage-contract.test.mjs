import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(path, "utf8");
const migrationPath = "supabase/migrations/20261011110000_chat_attachments_private_v2.sql";
const [migration, executor, restoreDrill, commonRepository, envelope, androidApi, androidProfile, webUploader, webMain, iosTransport] = await Promise.all([
  read(migrationPath),
  read("scripts/selective-db-release-executor.mjs"),
  read("scripts/restore-chat-attachment-metadata-backup-drill.ps1"),
  read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/PostgrestChatRepository.kt"),
  read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/ChatRpcPayloadEnvelope.kt"),
  read("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt"),
  read("app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt"),
  read("web/src/wasmJsMain/kotlin/com/quata/web/WebChatAttachmentUploader.kt"),
  read("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
  read("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosPostgrestChatTransport.kt"),
]);

test("private v2 bucket is additive and preserves the Android v32 compatibility lane", () => {
  assert.match(migration, /values \('chat-attachments-private', 'chat-attachments-private', false\)/);
  assert.doesNotMatch(migration, /update storage\.buckets[\s\S]*chat-attachments['"]/i);
  assert.match(migration, /legacy chat-attachments bucket remains public until v32 has no active clients/i);
  assert.match(commonRepository, /quata_chat_register_attachment_v2/);
  assert.match(commonRepository, /uploaded\.storageBucket/);
  assert.match(androidApi, /bucket = "chat-attachments-private"/);
  assert.match(webUploader, /ChatAttachmentPublicUrlPolicy\.PrivateBucket/);
  assert.match(iosTransport, /ChatAttachmentsBucket = ChatAttachmentPublicUrlPolicy\.PrivateBucket/);
  assert.match(iosTransport, /\$cleanProfileId\/chat\/\$\{NSUUID\.UUID\(\)\.UUIDString\.lowercase\(\)\}/);
});

test("private object reads require visible linked messages or an uploader-owned unlinked object", () => {
  assert.match(migration, /chat_attachments_private_select_visible/);
  assert.match(migration, /participant\.left_at is null/);
  assert.match(migration, /participant\.is_deleted = false/);
  assert.match(migration, /user_state\.deleted_at is null/);
  assert.match(migration, /user_state\.first_visible_message_id is null[\s\S]*message\.id >= user_state\.first_visible_message_id/);
  assert.match(migration, /message\.deleted_at is null/);
  assert.match(migration, /storage\.foldername\(name\)\)\[1\] = public\.quata_chat_auth_profile_id\(\)::text[\s\S]*not public\.quata_chat_private_object_is_linked/);
});

test("uploader cleanup and mutation use a global link guard that cannot be bypassed by attachment RLS", () => {
  assert.match(migration, /create or replace function public\.quata_chat_private_object_is_linked[\s\S]*security definer/);
  assert.match(migration, /from public\.chat_attachments linked[\s\S]*linked\.message_id is not null/);
  for (const policy of ["chat_attachments_private_update_own", "chat_attachments_private_delete_own"]) {
    const start = migration.indexOf(`create policy ${policy}`);
    assert.notEqual(start, -1);
    const block = migration.slice(start, migration.indexOf(";", start) + 1);
    assert.match(block, /not public\.quata_chat_private_object_is_linked\(bucket_id, name\)/);
  }
});

test("v2 registration is actor-bound, object-bound and authenticated-only", () => {
  assert.match(migration, /quata_chat_actor_profile_id\(p_actor_profile_id\)/);
  assert.match(migration, /quata_chat_is_thread_participant\(p_thread_id, v_actor\)/);
  assert.match(migration, /p_storage_bucket is distinct from 'chat-attachments-private'/);
  assert.match(migration, /from storage\.objects object[\s\S]*object\.bucket_id = p_storage_bucket[\s\S]*object\.name = p_storage_path/);
  assert.match(migration, /object\.owner_id = auth\.uid\(\)::text/);
  assert.match(migration, /revoke all on function public\.quata_chat_register_attachment_v2[\s\S]*from public, anon/);
  assert.match(migration, /grant execute on function public\.quata_chat_register_attachment_v2[\s\S]*to authenticated/);
});

test("deleted message placeholders never sign or expose private attachment URLs", () => {
  assert.match(envelope, /message\.takeUnless \{ it\.booleanAt\("is_deleted"\) == true \}/);
  assert.match(envelope, /val attachment = takeUnless \{ isDeleted \}/);
});

test("one unavailable private object cannot fail a message collection or expose its stored URL", () => {
  assert.match(envelope, /catch \(cancelled: CancellationException\)[\s\S]*throw cancelled/);
  assert.match(envelope, /catch \(_: Throwable\)[\s\S]*null/);
  assert.match(envelope, /attachmentUrlResolutionAttempted = attachment != null/);
  assert.match(envelope, /attachmentUri = if \(attachmentUrlResolutionAttempted\) resolvedAttachmentUrl else attachment\?\.stringAt\("url"\)/);
});

test("public profile avatars keep their public route while private attachments stay signed and previews renew", () => {
  assert.match(androidProfile, /attachment\.id\.startsWith\("avatar-"\)/);
  assert.match(androidProfile, /delay\(240_000L\)/);
  assert.match(webMain, /storageObject == null && attachment\.id\.startsWith\("avatar-"\)/);
  assert.match(webMain, /attachmentUrlSigner\.sign\(storageObject\)/);
});

test("unguarded serializers are no longer callable by untrusted roles", () => {
  for (const signature of [
    "quata_chat_attachment_json\\(bigint\\)",
    "quata_chat_message_json\\(bigint, uuid\\)",
    "quata_chat_thread_json\\(bigint, uuid\\)",
  ]) {
    assert.match(migration, new RegExp(`revoke all on function public\\.${signature} from public, anon, authenticated`));
  }
});

test("selective release allowlists exact migration bytes and checks the privacy boundary", () => {
  const digest = createHash("sha256").update(migration).digest("hex");
  assert.equal(digest, "a6decab131ab558dfa4f4607cbb3e27d87df3592e4c0f445c00001cf476967d0");
  assert.match(executor, new RegExp(`20261011110000[^\\n]+${digest}`));
  assert.match(executor, /selectedVersions\.includes\("20261011110000"\)/);
  assert.match(executor, /private_bucket_closed/);
  assert.match(executor, /v32_legacy_bucket_preserved/);
  assert.match(executor, /serializers_private/);
  assert.match(executor, /policies_exact/);
});

test("full encrypted database backup has a focal attachment metadata restore drill", () => {
  assert.match(restoreDrill, /quata-logical-backup-v1/);
  assert.match(restoreDrill, /scope -ne 'Full'/);
  assert.match(restoreDrill, /backup_ciphertext_checksum_mismatch/);
  assert.match(restoreDrill, /backup_plaintext_checksum_mismatch/);
  assert.match(restoreDrill, /TABLE public chat_attachments/);
  assert.match(restoreDrill, /TABLE storage buckets/);
  assert.match(restoreDrill, /TABLE storage objects/);
  assert.match(restoreDrill, /--section=pre-data --table=chat_attachments/);
  assert.match(restoreDrill, /--section=data --table=chat_attachments/);
  assert.match(restoreDrill, /ExpectedChatAttachments/);
  assert.match(restoreDrill, /CHAT_ATTACHMENT_METADATA_BACKUP_RESTORE_DRILL_PASS/);
  assert.match(restoreDrill, /Remove-Item -LiteralPath \$resolved -Recurse -Force/);
});
