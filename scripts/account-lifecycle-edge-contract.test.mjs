import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../supabase/functions/quata-account-lifecycle/index.ts", import.meta.url), "utf8");
const migration = await readFile(new URL("../supabase/migrations/20260928013000_account_deactivation_atomic_web_revocation.sql", import.meta.url), "utf8");
const rollback = await readFile(new URL("../supabase/rollbacks/20260928013000_account_deactivation_atomic_web_revocation.rollback.sql", import.meta.url), "utf8");
const previous = await readFile(new URL("../supabase/migrations/20260922175500_account_deactivation_auth_link.sql", import.meta.url), "utf8");
const authBridge = await readFile(new URL("../supabase/functions/quata-auth-bridge/index.ts", import.meta.url), "utf8");
const probe = await readFile(new URL("./account-lifecycle-atomic-deactivation-probe.mjs", import.meta.url), "utf8");

test("deactivation completes Auth revocation or compensates its durable database transition", () => {
  const deactivate = source.indexOf('admin.rpc("quata_account_deactivate"');
  const ban = source.indexOf("admin.auth.admin.updateUserById", deactivate);
  const compensate = source.indexOf('"quata_account_deactivation_compensate"', ban);
  const signOut = source.indexOf('admin.auth.admin.signOut(token, "global")', compensate);
  const complete = source.indexOf('"quata_account_deactivation_complete"', signOut);
  assert.ok(deactivate >= 0 && ban > deactivate && compensate > ban && signOut > compensate && complete > signOut);
  assert.match(source, /if \(signOutError\) throw signOutError/);
  assert.match(source, /if \(banError\) throw banError/);
  assert.match(source, /ban_duration: "none"[\s\S]*quata_account_deactivation_compensate/);
  assert.match(source, /p_operation_id: requestedOperationId/);
  assert.match(source, /transition\.state === "completed"/);
  assert.doesNotMatch(source, /Could not ban deactivated auth user|Could not globally revoke deactivated session/);
  assert.doesNotMatch(source, /revokeWebSessions|from\("web_push_subscriptions"\)|from\("web_client_sessions"\)/);
});

test("deactivation atomically unlinks the profile and retires every database session", () => {
  const identityUpdate = migration.indexOf("update public.community_profiles");
  const identityGuard = migration.indexOf("if v_updated <> 1", identityUpdate);
  const subscriptions = migration.indexOf("update public.web_push_subscriptions", identityGuard);
  const sessions = migration.indexOf("update public.web_client_sessions", subscriptions);
  const nativeTokens = migration.indexOf("update public.push_tokens", sessions);
  const operation = migration.indexOf("insert into public.account_deactivation_operations", nativeTokens);
  const result = migration.indexOf("return jsonb_build_object", nativeTokens);
  assert.ok(identityUpdate >= 0 && identityGuard > identityUpdate);
  assert.ok(subscriptions > identityGuard && sessions > subscriptions && nativeTokens > sessions && operation > nativeTokens && result > operation);
  assert.match(migration, /disabled_at = v_deactivated_at[\s\S]*last_error_text = 'Disabled on account deactivation'/);
  assert.match(migration, /revoked_at = v_deactivated_at[\s\S]*and revoked_at is null/);
  assert.match(migration, /where \(profile_id = p_profile_id or auth_user_id = p_auth_user_id\)/);
  assert.match(migration, /create trigger quata_push_tokens_active_owner_guard[\s\S]*create trigger quata_web_client_sessions_active_owner_guard[\s\S]*create trigger quata_web_push_subscriptions_active_owner_guard/);
  assert.match(migration, /create trigger quata_community_profiles_deactivation_guard/);
  assert.match(migration, /create or replace function public\.quata_account_deactivation_compensate/);
  assert.match(migration, /revoke all on function public\.quata_account_deactivate\(uuid, uuid, uuid\) from public, anon, authenticated/);
  assert.match(migration, /grant execute on function public\.quata_account_deactivate\(uuid, uuid, uuid\) to service_role/);
  assert.match(migration, /state in \('database_applied', 'completed'\)[\s\S]*operation_id', v_existing_id/);
  assert.match(migration, /select public\.quata_account_deactivate\(gen_random_uuid\(\), p_profile_id, p_auth_user_id\)/);
});

test("rollback restores the exact previous function and ACL", () => {
  const functionAndAcl = (sql) => sql.slice(sql.indexOf("CREATE OR REPLACE FUNCTION")).trim();
  assert.equal(functionAndAcl(rollback), functionAndAcl(previous));
  assert.match(rollback, /drop trigger if exists quata_web_push_subscriptions_active_owner_guard/);
  assert.match(rollback, /drop table if exists public\.account_deactivation_operations/);
  assert.match(rollback, /state in \('database_applied', 'compensating', 'reactivating'\)[\s\S]*rollback refused/);
  assert.doesNotMatch(functionAndAcl(rollback), /web_push_subscriptions|web_client_sessions/);
});

test("login completion is conditional and active delivery writers share the profile lock", () => {
  const reserve = authBridge.indexOf('"quata_account_reactivation_begin"');
  const authMutation = authBridge.indexOf("ensureAuthUser(admin", reserve);
  assert.ok(reserve >= 0 && authMutation > reserve);
  assert.match(authBridge, /account_reactivation_in_progress/);
  assert.match(authBridge, /quata_account_reactivation_complete/);
  assert.match(authBridge, /quata_account_reactivation_cancel/);
  assert.ok(authBridge.indexOf("ban_duration: \"876000h\"", authBridge.indexOf("async function cancelAccountReactivation"))
    < authBridge.indexOf('"quata_account_reactivation_cancel"', authBridge.indexOf("async function cancelAccountReactivation")));
  assert.match(authBridge, /\.eq\("account_status", "active"\)/);
  assert.match(authBridge, /return jsonResponse\(\{ error: "profile_state_changed" \}, 409\)/);
  assert.match(migration, /from public\.community_profiles[\s\S]*where id = v_profile_id[\s\S]*for update/);
  assert.match(migration, /active web session mismatch/);
  assert.match(migration, /create or replace function public\.quata_account_reactivation_begin/);
  assert.match(migration, /state = 'reactivating'[\s\S]*return jsonb_build_object/);
  assert.match(migration, /state = 'compensating'/);
  assert.match(migration, /v_deactivated_at \+ interval '15 minutes'/);
  assert.match(migration, /lease_expires_at > clock_timestamp\(\)/);
  assert.match(migration, /v_existing_state = 'database_applied'[\s\S]*lease_expires_at = clock_timestamp\(\) \+ interval '15 minutes'/);
});

test("remote probe executes failure rollback, stale-writer rejection, compensation and ACL checks", () => {
  assert.match(probe, /probe_forced_subscription_failure/);
  assert.match(probe, /forcedFailureRolledBackAllRows/);
  assert.match(probe, /staleWriterRejected/);
  assert.match(probe, /prematureReactivationRejected/);
  assert.match(probe, /openTransitionRollbackRejected/);
  assert.match(probe, /compensationRestoredAllRows/);
  assert.match(probe, /rollbackRestoredExactAcl/);
  assert.match(probe, /remoteBaselinePreserved/);
  assert.match(probe, /deactivationRetryResumesReceipt/);
  assert.match(probe, /reactivationRetryResumesReservation/);
  assert.match(probe, /predecessorEdgeCompatible/);
  assert.match(probe, /staleFinalizationRecovered/);
  assert.match(probe, /activeLeaseFencesReactivation/);
  assert.match(probe, /lateResumeRenewsLease/);
  assert.match(probe, /insert into auth\.users\(id\)/);
  assert.doesNotMatch(probe, /order by id limit 1/);
});
