import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

const runner = await source("scripts/external-share-web-evidence.mjs");
const storageCleanup = await source("scripts/e2e-fixtures/supabase-storage-cleanup.mjs");

test("external share Web evidence runner injects runtime config into a temporary distribution", () => {
  assert.match(runner, /configuredDistribution\(options\.distribution, backend\)/);
  assert.match(runner, /mkdtemp\(join\(tmpdir\(\), "quata-external-share-web-dist-"\)\)/);
  assert.match(runner, /name="quata-supabase-url" content=""/);
  assert.match(runner, /name="quata-supabase-publishable-key" content=""/);
  assert.match(runner, /runtime_configuration_injection_failed/);
  assert.match(runner, /if \(servedDistribution\) await rm\(servedDistribution, \{ recursive: true, force: true \}\)/);
});

test("external share Web evidence uses semantic anchors instead of coordinates", () => {
  assert.match(runner, /clickStableText\(page, peerAnchor, "recipient candidate", 30_000\)/);
  assert.match(runner, /clickStableControl\(page, \["external-share\.confirm", "Enviar", "Send"\], "confirm send"\)/);
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
  assert.match(runner, /assertNoMarker\(backend, actorSession, threadId, marker\)/);
  assert.match(runner, /send_discards_incoming_share_claim/);
  assert.match(runner, /messageMarkerAbsent: true/);
  assert.match(runner, /storagePhysicalResidue: 0/);
});

test("external share Web evidence records redacted diagnostics only", () => {
  assert.match(runner, /attachBrowserDiagnostics\(page\)/);
  assert.match(runner, /redactDiagnostic/);
  assert.match(runner, /Bearer <redacted>/);
  assert.match(runner, /apikey=<redacted>/);
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
