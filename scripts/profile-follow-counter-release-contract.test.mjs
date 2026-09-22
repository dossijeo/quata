import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

const root = resolve(import.meta.dirname, "..");
const read = (path) => readFileSync(resolve(root, path), "utf8");
const actorVersion = "20260922202500";
const counterVersion = "20260922203500";
const marker = `${counterVersion}_community_profile_follow_counter_reconciliation`;
const actor = read(`supabase/migrations/${actorVersion}_community_profile_follows_actor_guard.sql`);
const actorTemplate = read("supabase/templates/community_profile_follows_actor_guard.sql.template");
const actorRollback = read(`supabase/rollbacks/${actorVersion}_community_profile_follows_actor_guard.rollback.sql`);
const actorRollbackTemplate = read("supabase/templates/community_profile_follows_actor_guard.rollback.sql.template");
const counter = read(`supabase/migrations/${counterVersion}_community_profile_follow_counter_reconciliation.sql`);
const counterTemplate = read("supabase/templates/community_profile_follow_counter_reconciliation.sql.template");
const counterRollback = read(`supabase/rollbacks/${counterVersion}_community_profile_follow_counter_reconciliation.rollback.sql`);
const counterRollbackTemplate = read("supabase/templates/community_profile_follow_counter_reconciliation.rollback.sql.template");

test("timestamped follow releases are the exact validated templates", () => {
  assert.ok(actorVersion < counterVersion);
  assert.equal(actor, actorTemplate);
  assert.equal(actorRollback, actorRollbackTemplate);
  assert.equal(counter, counterTemplate.replaceAll("__MIGRATION_VERSION__", marker));
  assert.equal(counterRollback, counterRollbackTemplate.replaceAll("__MIGRATION_VERSION__", marker));
  assert.doesNotMatch(`${actor}\n${counter}\n${actorRollback}\n${counterRollback}`, /__MIGRATION_VERSION__/);
});

test("actor guard preserves public reads and binds every mutation to the active actor", () => {
  assert.match(actor, /create policy community_profile_follows_public_read[\s\S]*to anon, authenticated[\s\S]*using \(true\)/i);
  assert.match(actor, /create policy community_profile_follows_insert_own[\s\S]*to authenticated[\s\S]*follower_profile_id = public\.quata_chat_auth_profile_id\(\)/i);
  assert.match(actor, /create policy community_profile_follows_delete_own_or_admin[\s\S]*quata_profile_follow_delete_allowed/i);
  assert.match(actor, /security invoker[\s\S]*quata_guard_profile_follows_trg/i);
  assert.match(actor, /revoke all privileges on public\.community_profile_follows from anon, authenticated/i);
  assert.match(actor, /grant select on public\.community_profile_follows to anon/i);
  assert.match(actor, /grant select, insert, delete on public\.community_profile_follows to authenticated/i);
  assert.match(actor, /revoke execute on function public\.toggle_follow_profile\(uuid\)[\s\S]*from public, anon, authenticated/i);
});

test("counter reconciliation snapshots dynamic state and installs one authoritative producer", () => {
  assert.match(counter, /pg_advisory_xact_lock/i);
  assert.match(counter, /quata_follow_count_reconciliation_snapshot/i);
  assert.match(counter, /quata_follow_edges_fingerprint/i);
  assert.match(counter, /quata_follow_profiles_fingerprint/i);
  assert.match(counter, /security definer[\s\S]*set search_path = public/i);
  assert.match(counter, /after insert or update or delete on public\.community_profile_follows/i);
  assert.match(counter, /for no key update/i);
  assert.match(counter, /v_updated_count <> v_expected_count/i);
  assert.match(counter, /v_remaining_mismatches <> 0/i);
  assert.match(counter, /revoke execute on function public\.recalculate_profile_follow_counts\(uuid\)[\s\S]*from public, anon, authenticated/i);
  assert.doesNotMatch(counter, /\b(?:112|107|74)\b/);
});

test("rollback refuses to restore across profile or edge drift", () => {
  assert.match(counterRollback, /quata_follow_profiles_fingerprint\(\)/i);
  assert.match(counterRollback, /quata_follow_edges_fingerprint\(\)/i);
  assert.match(counterRollback, /Rollback refused: profile set changed after snapshot/i);
  assert.match(counterRollback, /Rollback refused: follow edges changed after snapshot/i);
  assert.match(counterRollback, /Rollback refused: counters changed after reconciliation/i);
  assert.match(actorRollback, /create policy "allow all"/i);
});

test("isolated PostgreSQL and PostgREST suites execute the validated templates", () => {
  const sql = read("scripts/sql/community-profile-follows-integrity.test.sql");
  const databaseRunner = read("scripts/run-community-profile-follows-integrity-test.ps1");
  const postgrestRunner = read("scripts/run-community-profile-follows-postgrest-test.ps1");
  assert.match(sql, /community_profile_follows_actor_guard\.sql\.template/);
  assert.match(sql, /community_profile_follow_counter_reconciliation\.sql\.template/);
  assert.match(databaseRunner, /community-profile-follows-integrity\.test\.sql/);
  assert.match(databaseRunner, /community-profile-follows-concurrency\.test\.sql/);
  assert.match(postgrestRunner, /postgrest\/postgrest:v12\.2\.3/);
  assert.match(postgrestRunner, /community-profile-follows-postgrest\.test\.mjs/);
});
