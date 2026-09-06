import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

const runner = await source("scripts/external-share-web-evidence.mjs");
const storageCleanup = await source("scripts/e2e-fixtures/supabase-storage-cleanup.mjs");
const redaction = await source("scripts/e2e-fixtures/evidence-redaction.mjs");

test("external share Web evidence runner injects runtime config into a temporary distribution", () => {
  assert.match(runner, /configuredDistribution\(options\.distribution, backend\)/);
  assert.match(runner, /mkdtemp\(join\(tmpdir\(\), "quata-external-share-web-dist-"\)\)/);
  assert.match(runner, /name="quata-supabase-url" content=""/);
  assert.match(runner, /name="quata-supabase-publishable-key" content=""/);
  assert.match(runner, /runtime_configuration_injection_failed/);
  assert.match(runner, /if \(servedDistribution\) await rm\(servedDistribution, \{ recursive: true, force: true \}\)/);
});

test("external share Web evidence uses semantic anchors instead of coordinates", () => {
  assert.match(runner, /quata-external-share-e2e=1#share-target/);
  assert.match(runner, /waitExternalShareBridge\(page\)/);
  assert.match(runner, /hasSemanticTarget/);
  assert.match(runner, /semanticClickExternalShare\(page, `external-share\.candidate\.action\.\$\{peerSession\.profileId\}`/);
  assert.match(runner, /external-share\.candidate\.action\.\$\{profileId\}/);
  assert.match(runner, /profileSha256: sha256\(peerSession\.profileId\)/);
  assert.match(runner, /semanticClickExternalShare\(page, "external-share\.confirm", "confirm send"\)/);
  assert.match(runner, /missing_stable_anchor/);
  assert.doesNotMatch(runner, /\.mouse\.click\(/);
  assert.doesNotMatch(runner, /input tap/);
});

test("external share Web evidence proves send and cleanup through backend state", () => {
  assert.match(runner, /seedIncomingShare\(page/);
  assert.match(runner, /incoming_share_blob_seeded/);
  assert.match(runner, /new Blob\(\[attachment\.text \|\| ""\]/);
  assert.match(runner, /pollMessage\(backend, actorSession, threadId/);
  assert.match(runner, /external_share_attachment_not_persisted/);
  assert.match(runner, /deleteMessages\(backend, actorSession, threadId, cleanupMessageIds\)/);
  assert.match(runner, /cleanupStorageObjects\(backend, actorSession, cleanupStoragePaths\)/);
  assert.match(runner, /assertStorageObjectAbsent\(\{ bucket: "chat-attachments", storagePath \}\)/);
  assert.match(runner, /external_share_attachment_storage_path_missing/);
  assert.match(runner, /external_share_storage_upload_not_observed/);
  assert.match(runner, /external_share_storage_cleanup_without_verified_object/);
  assert.match(runner, /assertNoMarker\(backend, actorSession, threadId, marker\)/);
  assert.match(runner, /send_discards_incoming_share_claim/);
  assert.match(runner, /messageMarkerAbsent: true/);
  assert.match(runner, /storagePhysicalResidue: 0/);
});

test("external share Web evidence records redacted diagnostics only", () => {
  assert.match(runner, /attachBrowserDiagnostics\(page\)/);
  assert.match(runner, /redactEvidenceString/);
  assert.match(runner, /redactEvidenceUrl/);
  assert.match(runner, /redactEvidenceReport/);
  assert.match(runner, /registerEvidenceSecret\(marker, "evidence-marker"\)/);
  assert.match(runner, /storagePathSha256/);
  assert.match(runner, /actorProfileSha256/);
  assert.match(runner, /peerProfileSha256/);
  assert.match(runner, /textProbeSha256: sha256\(marker\)/);
  assert.match(runner, /screenshots: \[\]/);
  assert.match(runner, /QUATA_EXTERNAL_SHARE_STORE_RAW_SCREENSHOTS/);
  assert.match(runner, /visibleTextProbe/);
  assert.match(runner, /redactEvidenceReport\(value\)/);
  assert.match(redaction, /<storage-path-sha256:/);
  assert.match(redaction, /<local-path-redacted>/);
  assert.doesNotMatch(runner, /page\.screenshot\(\{ path/);
  assert.doesNotMatch(runner, /report\.visibleText\s*=/);
  assert.doesNotMatch(runner, /textProbe: marker/);
  assert.doesNotMatch(runner, /report\.actorProfile = shortId/);
  assert.doesNotMatch(runner, /storagePath: path/);
  assert.doesNotMatch(runner, /C:\/Users\/PC\/QUATA_CHAT_GROUP_CREDENTIALS_FILE/);
  assert.match(redaction, /redactBareStoragePaths/);
  assert.match(redaction, /registerEvidenceSecret/);
  assert.match(redaction, /Bearer <redacted>/);
  assert.match(redaction, /apikey=<redacted>/);
  assert.match(redaction, /web_session_token=<redacted>/);
  assert.match(redaction, /password=<redacted>/);
  assert.match(redaction, /cookie=<redacted>/);
  const consoleLogCalls = runner.match(/console\.log\([^;]+;/gs) ?? [];
  for (const call of consoleLogCalls) {
    assert.doesNotMatch(call, /accessToken|refreshToken|password|webSessionToken/);
  }
});

test("shared Storage cleanup probe can resolve pg from the workspace dependency root", () => {
  assert.match(storageCleanup, /const \{ Client \} = loadPackage\("pg"\)/);
  assert.match(storageCleanup, /createRequire\(import\.meta\.url\)\(name\)/);
  assert.match(storageCleanup, /process\.env\.QUATA_NODE_MODULES/);
  assert.match(storageCleanup, /begin read only/);
  assert.match(storageCleanup, /select count\(\*\)::int as count from storage\.objects/);
});
