import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const gateway = read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/ChatRealtimeGateway.kt");
const migration = read("supabase/migrations/20261003183000_community_directory_realtime.sql");
const audit = read("scripts/db-community-directory-realtime-semantics-audit.sql");
const android = read("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt");
const web = read("web/src/wasmJsMain/kotlin/com/quata/web/WebChatRealtimeGateway.kt");
const ios = read("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatRealtimeGateway.kt");
const androidSocket = read("app/src/main/java/com/quata/data/supabase/SupabaseRealtimeClient.kt");
const evidence = read("scripts/community-directory-realtime-evidence.mjs");
const trigger = read("scripts/community-directory-realtime-trigger.py");
const { buildCommunityRealtimeSuccessReport } = await import("./community-directory-realtime-report.mjs");

const baseTables = [
  "community_profiles",
  "community_walls",
  "community_members",
  "community_posts",
  "community_messages",
];

for (const table of baseTables) {
  assert.match(gateway, new RegExp(`"${table}"`), `${table} must invalidate the directory snapshot`);
  assert.match(audit, new RegExp(`'${table}'`), `${table} must be covered by the production audit`);
}

for (const table of baseTables.filter((table) => table !== "community_messages")) {
  assert.match(migration, new RegExp(`'${table}'`), `${table} must be added to supabase_realtime`);
}

assert.doesNotMatch(
  gateway.match(/CommunityDirectoryRealtimeTables[\s\S]*?\)/)?.[0] ?? "",
  /community_walls_stats/,
  "the view cannot be used as a Postgres Changes source",
);
assert.match(migration, /not exists[\s\S]*pg_publication_tables/);
assert.match(migration, /alter publication supabase_realtime add table public\.%I \(%s\)/);
assert.match(migration, /\('community_profiles', 'id'\)/);
assert.match(migration, /\('community_walls', 'id'\)/);
assert.match(migration, /\('community_members', 'wall_id, profile_id'\)/);
assert.match(migration, /\('community_posts', 'id'\)/);
assert.match(audit, /community_walls_stats/);
assert.match(audit, /newPublicationColumnsSafe/);
assert.match(audit, /attnames = array\['id'\]::name\[\]/);
assert.match(audit, /attnames = array\['wall_id', 'profile_id'\]::name\[\]/);
assert.match(android, /chatDatabaseRealtimeTables\(hasAuthenticatedSession = false\)/);
assert.match(android, /accessToken = ""/);
assert.match(android, /activeDatabaseRealtimeSession\(\)/);
assert.match(android, /usableSession == null[\s\S]*?connectDirectoryRealtime\(\)/);
assert.match(web, /chatDatabaseRealtimeTables\(session != null\)/);
assert.match(web, /databaseSessionUserId != null\) disconnectDatabase\("chat-session-refresh"\)/);
assert.match(web, /databaseSocket == null && shouldConnectDatabase\(\)\) connectDatabase\(\)/);
assert.match(ios, /chatDatabaseRealtimeTables\(freshSession != null\)/);
assert.match(web, /accessToken\?\.takeIf\(String::isNotBlank\)/);
assert.match(ios, /session\?\.bearerToken\?\.takeIf\(String::isNotBlank\)/);
assert.match(androidSocket, /accessToken\.takeIf \{ it\.isNotBlank\(\) \}\?\.let \{ put\("access_token", it\) \}/);
assert.match(gateway, /PublicCommunityDirectoryRealtimeTables[\s\S]*?"community_posts"[\s\S]*?\)/);
assert.doesNotMatch(
  gateway.match(/PublicCommunityDirectoryRealtimeTables[\s\S]*?\)/)?.[0] ?? "",
  /community_messages/,
  "the anonymous directory socket must not subscribe to legacy public message bodies",
);
assert.match(evidence, /assertRealtimeQuotaAvailable/);
assert.match(evidence, /blockedByExternalService: true/);
assert.match(evidence, /mutationStarted: evidenceState\?\.mutationStarted/);
assert.match(evidence, /sessionCreated: evidenceState\?\.sessionCreated/);
assert.match(evidence, /databaseFixtureRemovalRequired: evidenceState\?\.mutationStarted/);
assert.match(evidence, /databaseFixtureRemoved: evidenceState\?\.mutationStarted \? evidenceState\.databaseFixtureRemoved : null/);
assert.match(evidence, /community_realtime_logout_incomplete/);
assert.match(evidence, /secretsPersisted: false/);
assert.match(trigger, /parser\.add_argument\("--journal", required=True\)/);
assert.match(trigger, /write_journal\(journal_path, fixture\)/);
assert.match(trigger, /insert into public\.community_walls/);
assert.match(trigger, /delete from public\.community_walls where id = %s and slug = %s and name = %s/);
assert.doesNotMatch(trigger, /update public\.community_walls/);

const publicOnlyReport = buildCommunityRealtimeSuccessReport({
  checkedAt: "2026-10-03T00:00:00.000Z",
  publicTables: ["community_profiles"],
  authenticatedTables: ["community_profiles", "community_messages"],
  publicCount: 1,
  authenticatedCount: null,
  authenticatedSession: null,
  triggerTable: "community_walls",
  triggerRowSha256: "synthetic",
});
assert.equal(publicOnlyReport.mode, "public-only");
assert.deepEqual(publicOnlyReport.authenticated, { attempted: false, reason: "public_only_mode" });
assert.equal(publicOnlyReport.cleanup.authenticationSessionRevocationRequired, false);
assert.equal(publicOnlyReport.cleanup.authenticationSessionRevoked, null);
assert.doesNotMatch(JSON.stringify(publicOnlyReport), /402|quota/);

console.log("community directory realtime contract ok");
