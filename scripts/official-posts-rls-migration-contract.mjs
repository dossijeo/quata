#!/usr/bin/env node
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

const migration = readFileSync(resolve("supabase/migrations/20260808_0001_official_posts_actor_guard.sql"), "utf8");
const testSql = readFileSync(resolve("scripts/official-posts-rls-migration-test.sql"), "utf8");

function need(pattern, label) {
  assert.match(migration, pattern, `official_posts_rls_migration_missing:${label}`);
}

test("Official posts RLS migration closes the SECURITY DEFINER bypass with explicit policies", () => {
  need(/alter function public\.quata_guard_official_posts\(\) security invoker/i, "trigger_security_invoker");
  need(/revoke all on function public\.quata_guard_official_posts\(\)[\s\S]*from public, anon, authenticated, service_role/i, "trigger_acl_revoke");
  need(/quata_official_post_insert_allowed/i, "insert_helper");
  need(/quata_official_post_owner_or_admin_allowed/i, "owner_admin_helper");
  need(/account_status = 'active'/i, "active_official_check");
  need(/drop policy if exists official_posts_authenticated_insert/i, "drop_old_insert_policy");
  need(/drop policy if exists official_posts_admin_update/i, "drop_old_admin_update_policy");
  need(/official_posts_authenticated_insert_official_own/i, "insert_policy");
  need(/with check \(\s*public\.quata_official_post_insert_allowed\(profile_id\)\s*\)/i, "insert_policy_check");
  need(/official_posts_authenticated_update_author_or_admin/i, "update_policy");
  need(/official_posts_authenticated_delete_author_or_admin/i, "delete_policy");
  need(/grant select, insert, update, delete on public\.official_posts to authenticated/i, "explicit_authenticated_grants");
});

test("Official posts isolated SQL test reproduces baseline spoof and asserts 42501 after migration", () => {
  assert.match(testSql, /Baseline spoof reproduced/);
  assert.match(testSql, /OFFICIAL_POSTS_RLS_MIGRATION_TEST_OK/);
  assert.match(testSql, /exception when insufficient_privilege/);
});

test("Official posts RLS artifacts do not contain deployment commands", () => {
  assert.doesNotMatch(migration + testSql, /supabase db push|migration repair/i);
});
