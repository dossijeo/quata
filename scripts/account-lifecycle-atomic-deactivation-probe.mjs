#!/usr/bin/env node
import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { dirname, resolve } from "node:path";
import process from "node:process";

const require = createRequire(import.meta.url);
const { Client } = require("pg");
const root = resolve(import.meta.dirname, "..");
const migrationPath = resolve(root, "supabase/migrations/20260928013000_account_deactivation_atomic_web_revocation.sql");
const rollbackPath = resolve(root, "supabase/rollbacks/20260928013000_account_deactivation_atomic_web_revocation.rollback.sql");
const predecessorFunctionIdentity = "public.quata_account_deactivate(uuid,uuid)";
const successorFunctionIdentity = "public.quata_account_deactivate(uuid,uuid,uuid)";
const output = resolve(argument("--out") || "build-reports/account-lifecycle/atomic-deactivation-probe.json");
const report = {
  schemaVersion: 2,
  check: "ACCOUNT-DEACTIVATION-ATOMIC-ROLLBACK-002",
  status: "failed",
  migration: {},
  assertions: {},
  cleanup: { verified: false },
};

let client;
let transactionOpen = false;
try {
  const [migration, rollback, configuration] = await Promise.all([
    readFile(migrationPath, "utf8"),
    readFile(rollbackPath, "utf8"),
    databaseConfiguration(),
  ]);
  report.migration = {
    version: "20260928013000",
    sha256: createHash("sha256").update(migration).digest("hex"),
  };
  client = new Client(configuration);
  await client.connect();
  const before = await databaseSurface(client);
  await client.query("begin isolation level repeatable read");
  transactionOpen = true;
  await client.query("select pg_advisory_xact_lock(hashtextextended($1, 0))", ["quata/account-deactivation-atomic-probe/v2"]);
  await client.query(migration);
  const installed = await databaseSurface(client, successorFunctionIdentity);
  report.assertions.installed = atomicDefinition(installed.definition);
  report.assertions.serviceRoleOnly = installed.serviceExecute === true
    && installed.anonExecute === false
    && installed.authenticatedExecute === false
    && installed.operationTableClientReadable === false;
  report.assertions.predecessorEdgeCompatible = installed.compatibilityDefinition.includes(
    "quata_account_deactivate(gen_random_uuid(), p_profile_id, p_auth_user_id)",
  );
  report.assertions.raceGuardsInstalled = installed.guardTriggerCount === 4;
  requireAssertion(report.assertions.installed, "atomic_definition_postcondition_failed");
  requireAssertion(report.assertions.serviceRoleOnly, "atomic_acl_postcondition_failed");
  requireAssertion(report.assertions.predecessorEdgeCompatible, "predecessor_edge_compatibility_missing");
  requireAssertion(report.assertions.raceGuardsInstalled, "race_guards_missing");

  const fixture = await createFixture(client);
  await installForcedFailure(client);
  await client.query("savepoint forced_failure");
  let forcedFailureObserved = false;
  try {
    await client.query("select public.quata_account_deactivate($1, $2, $3)", [randomUUID(), fixture.profileId, fixture.authUserId]);
  } catch (error) {
    forcedFailureObserved = String(error?.message || "").includes("probe_forced_subscription_failure");
    await client.query("rollback to savepoint forced_failure");
  }
  report.assertions.forcedFailureObserved = forcedFailureObserved;
  requireAssertion(forcedFailureObserved, "forced_failure_not_observed");
  const afterFailure = await fixtureState(client, fixture);
  report.assertions.forcedFailureRolledBackAllRows = isActiveFixture(afterFailure) && afterFailure.operation_count === 0;
  requireAssertion(report.assertions.forcedFailureRolledBackAllRows, "forced_failure_left_partial_rows");
  await uninstallForcedFailure(client);

  const firstTransition = await deactivate(client, fixture);
  const resumedFirstTransition = await deactivate(client, fixture);
  report.assertions.deactivationRetryResumesReceipt = resumedFirstTransition.operationId === firstTransition.operationId
    && resumedFirstTransition.deactivatedAt === firstTransition.deactivatedAt;
  requireAssertion(report.assertions.deactivationRetryResumesReceipt, "deactivation_retry_did_not_resume_receipt");
  const afterDeactivation = await fixtureState(client, fixture);
  report.assertions.successRetiredAllRows = isDeactivatedFixture(afterDeactivation)
    && afterDeactivation.database_applied_count === 1;
  requireAssertion(report.assertions.successRetiredAllRows, "successful_deactivation_postcondition_failed");

  await client.query("savepoint premature_reactivation");
  let prematureReactivationRejected = false;
  try {
    await client.query("select public.quata_account_reactivation_begin($1, $2)", [randomUUID(), fixture.profileId]);
  } catch (error) {
    prematureReactivationRejected = error?.code === "55000";
    await client.query("rollback to savepoint premature_reactivation");
  }
  report.assertions.prematureReactivationRejected = prematureReactivationRejected;
  requireAssertion(prematureReactivationRejected, "premature_reactivation_was_not_rejected");

  await client.query("savepoint stale_writer");
  let staleWriterRejected = false;
  try {
    await client.query(
      `insert into public.push_tokens(user_id, auth_user_id, token, platform)
       values ($1, $2, $3, 'android')`,
      [fixture.profileId, fixture.authUserId, `probe-stale-${randomUUID()}`],
    );
  } catch (error) {
    staleWriterRejected = error?.code === "42501";
    await client.query("rollback to savepoint stale_writer");
  }
  report.assertions.staleWriterRejected = staleWriterRejected;
  requireAssertion(staleWriterRejected, "stale_writer_was_not_rejected");

  await client.query("savepoint open_transition_rollback");
  let openTransitionRollbackRejected = false;
  try {
    await client.query(rollback);
  } catch (error) {
    openTransitionRollbackRejected = String(error?.message || "").includes("rollback refused");
    await client.query("rollback to savepoint open_transition_rollback");
  }
  report.assertions.openTransitionRollbackRejected = openTransitionRollbackRejected;
  requireAssertion(openTransitionRollbackRejected, "open_transition_rollback_was_not_rejected");

  await client.query(
    "select public.quata_account_deactivation_compensate($1, $2, $3)",
    [firstTransition.operationId, fixture.profileId, fixture.authUserId],
  );
  const afterCompensation = await fixtureState(client, fixture);
  report.assertions.compensationRestoredAllRows = isActiveFixture(afterCompensation)
    && afterCompensation.rolled_back_count === 1;
  requireAssertion(report.assertions.compensationRestoredAllRows, "compensation_postcondition_failed");

  const secondTransition = await deactivate(client, fixture);
  await client.query(
    "select public.quata_account_deactivation_complete($1, $2, $3)",
    [secondTransition.operationId, fixture.profileId, fixture.authUserId],
  );
  const afterCompletion = await fixtureState(client, fixture);
  report.assertions.completedTransitionDurableInsideTransaction = isDeactivatedFixture(afterCompletion)
    && afterCompletion.completed_count === 1;
  requireAssertion(report.assertions.completedTransitionDurableInsideTransaction, "completion_postcondition_failed");

  await client.query(
    "update public.account_deactivation_operations set state='database_applied', updated_at=now()-interval '20 minutes', lease_expires_at=now()+interval '1 second' where id=$1",
    [secondTransition.operationId],
  );
  const lateResume = await deactivate(client, fixture);
  const renewedLease = await client.query(
    "select lease_expires_at > clock_timestamp()+interval '14 minutes' as renewed from public.account_deactivation_operations where id=$1",
    [secondTransition.operationId],
  );
  report.assertions.lateResumeRenewsLease = lateResume.operationId === secondTransition.operationId
    && renewedLease.rows[0]?.renewed === true;
  requireAssertion(report.assertions.lateResumeRenewsLease, "late_resume_did_not_renew_lease");
  await client.query("savepoint active_lease");
  let activeLeaseRejected = false;
  try {
    await client.query(
      "select public.quata_account_reactivation_begin($1, $2)",
      [randomUUID(), fixture.profileId],
    );
  } catch (error) {
    activeLeaseRejected = error?.code === "55000";
    await client.query("rollback to savepoint active_lease");
  }
  report.assertions.activeLeaseFencesReactivation = activeLeaseRejected;
  requireAssertion(activeLeaseRejected, "active_deactivation_lease_was_not_fenced");
  await client.query(
    "update public.account_deactivation_operations set lease_expires_at=now()-interval '1 second' where id=$1",
    [secondTransition.operationId],
  );

  const reactivation = await client.query(
    "select public.quata_account_reactivation_begin($1, $2) as transition",
    [randomUUID(), fixture.profileId],
  );
  const reactivationTransition = reactivation.rows[0]?.transition;
  requireAssertion(Boolean(reactivationTransition?.operation_id), "reactivation_reservation_missing");
  report.assertions.staleFinalizationRecovered = reactivationTransition.operation_id === secondTransition.operationId;
  requireAssertion(report.assertions.staleFinalizationRecovered, "stale_finalization_was_not_recovered");
  const resumedReactivation = await client.query(
    "select public.quata_account_reactivation_begin($1, $2) as transition",
    [randomUUID(), fixture.profileId],
  );
  report.assertions.reactivationRetryResumesReservation = resumedReactivation.rows[0]?.transition?.operation_id
    === reactivationTransition.operation_id;
  requireAssertion(report.assertions.reactivationRetryResumesReservation, "reactivation_retry_did_not_resume_reservation");
  await client.query(
    "select public.quata_account_reactivation_cancel($1, $2, $3)",
    [reactivationTransition.operation_id, fixture.profileId, fixture.authUserId],
  );
  const afterReactivationCancel = await fixtureState(client, fixture);
  report.assertions.reactivationReservationCancelledFailClosed = isDeactivatedFixture(afterReactivationCancel)
    && afterReactivationCancel.completed_count === 1;
  requireAssertion(report.assertions.reactivationReservationCancelledFailClosed, "reactivation_cancel_postcondition_failed");

  await client.query(rollback);
  const restoredInsideTransaction = await databaseSurface(client, predecessorFunctionIdentity);
  report.assertions.rollbackRestoredExactDefinition = restoredInsideTransaction.definition === before.definition;
  report.assertions.rollbackRestoredExactAcl = sameAcl(restoredInsideTransaction, before);
  report.assertions.rollbackRemovedSuccessorObjects = restoredInsideTransaction.guardTriggerCount === before.guardTriggerCount
    && restoredInsideTransaction.operationTableExists === before.operationTableExists;
  requireAssertion(report.assertions.rollbackRestoredExactDefinition, "rollback_definition_mismatch");
  requireAssertion(report.assertions.rollbackRestoredExactAcl, "rollback_acl_mismatch");
  requireAssertion(report.assertions.rollbackRemovedSuccessorObjects, "rollback_successor_objects_remain");

  await client.query("rollback");
  transactionOpen = false;
  const finalSurface = await databaseSurface(client, predecessorFunctionIdentity);
  report.assertions.remoteBaselinePreserved = sameSurface(finalSurface, before);
  requireAssertion(report.assertions.remoteBaselinePreserved, "remote_baseline_changed");
  report.status = "passed";
  report.cleanup = { verified: true, state: "probe_transaction_rolled_back" };
} catch (error) {
  if (transactionOpen) await client?.query("rollback").catch(() => {});
  report.failureCode = String(error?.message || "probe_failed").replace(/[^a-zA-Z0-9_.:-]/g, "_").slice(0, 160);
  process.exitCode = 1;
} finally {
  await client?.end().catch(() => {});
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  process.stdout.write(`${report.status === "passed" ? "PASS" : "FAIL"} ${report.check} report=${output}\n`);
}

async function createFixture(db) {
  const suffix = randomUUID();
  const authUserId = randomUUID();
  const profileId = randomUUID();
  const phone = suffix.replaceAll("-", "").slice(0, 15);
  await db.query("insert into auth.users(id) values ($1)", [authUserId]);
  await db.query(
    `insert into public.community_profiles(
       id, auth_user_id, display_name, phone, pass_hash, phone_normalized, phone_local, account_status
     ) values ($1, $2, $3, $4, $5, $4, $4, 'active')`,
    [profileId, authUserId, `probe-${suffix.slice(0, 8)}`, phone, "probe-noncredential-hash"],
  );
  const session = await db.query(
    `insert into public.web_client_sessions(
       profile_id, auth_user_id, client_instance_id, token_hash
     ) values ($1, $2, $3, $4) returning id`,
    [profileId, authUserId, `probe-${suffix}`, createHash("sha256").update(suffix).digest("hex")],
  );
  const sessionId = session.rows[0].id;
  const subscription = await db.query(
    `insert into public.web_push_subscriptions(
       web_session_id, profile_id, auth_user_id, endpoint, p256dh, auth_secret
     ) values ($1, $2, $3, $4, $5, $6) returning id`,
    [sessionId, profileId, authUserId, `https://example.invalid/${suffix}`, "p".repeat(40), "a".repeat(16)],
  );
  const token = await db.query(
    `insert into public.push_tokens(user_id, auth_user_id, token, platform)
     values ($1, $2, $3, 'android') returning id`,
    [profileId, authUserId, `probe-${suffix}`],
  );
  return {
    profileId,
    authUserId,
    sessionId,
    subscriptionId: subscription.rows[0].id,
    tokenId: token.rows[0].id,
  };
}

async function installForcedFailure(db) {
  await db.query(`
    create function public.quata_account_deactivation_probe_fail() returns trigger
    language plpgsql as $$ begin
      if new.disabled_at is not null and old.disabled_at is null then
        raise exception 'probe_forced_subscription_failure';
      end if;
      return new;
    end $$;
    create trigger zz_quata_account_deactivation_probe_fail
    before update on public.web_push_subscriptions
    for each row execute function public.quata_account_deactivation_probe_fail();
  `);
}

async function uninstallForcedFailure(db) {
  await db.query(`
    drop trigger zz_quata_account_deactivation_probe_fail on public.web_push_subscriptions;
    drop function public.quata_account_deactivation_probe_fail();
  `);
}

async function deactivate(db, fixture) {
  const result = await db.query(
    "select public.quata_account_deactivate($1, $2, $3) as transition",
    [randomUUID(), fixture.profileId, fixture.authUserId],
  );
  const transition = result.rows[0]?.transition;
  if (!transition?.operation_id || !transition?.deactivated_at) throw new Error("deactivation_transition_receipt_missing");
  return { operationId: transition.operation_id, deactivatedAt: transition.deactivated_at };
}

async function fixtureState(db, fixture) {
  const result = await db.query(
    `select
       (select account_status from public.community_profiles where id=$1) as account_status,
       (select auth_user_id from public.community_profiles where id=$1) as linked_auth_user_id,
       (select revoked_at is null from public.web_client_sessions where id=$2) as session_active,
       (select disabled_at is null from public.web_push_subscriptions where id=$3) as subscription_active,
       (select disabled_at is null from public.push_tokens where id=$4) as token_active,
       (select count(*)::int from public.account_deactivation_operations where profile_id=$1) as operation_count,
       (select count(*)::int from public.account_deactivation_operations where profile_id=$1 and state='database_applied') as database_applied_count,
       (select count(*)::int from public.account_deactivation_operations where profile_id=$1 and state='rolled_back') as rolled_back_count,
       (select count(*)::int from public.account_deactivation_operations where profile_id=$1 and state='completed') as completed_count`,
    [fixture.profileId, fixture.sessionId, fixture.subscriptionId, fixture.tokenId],
  );
  return result.rows[0];
}

function isActiveFixture(state) {
  return state.account_status === "active" && state.linked_auth_user_id != null
    && state.session_active === true && state.subscription_active === true && state.token_active === true;
}

function isDeactivatedFixture(state) {
  return state.account_status === "deactivated" && state.linked_auth_user_id == null
    && state.session_active === false && state.subscription_active === false && state.token_active === false;
}

async function databaseSurface(db, functionIdentity = predecessorFunctionIdentity) {
  const result = await db.query(
    `select
       pg_get_functiondef($1::regprocedure) as definition,
       has_function_privilege('service_role', $1, 'execute') as service_execute,
       has_function_privilege('anon', $1, 'execute') as anon_execute,
       has_function_privilege('authenticated', $1, 'execute') as authenticated_execute,
       pg_get_functiondef('public.quata_account_deactivate(uuid,uuid)'::regprocedure) as compatibility_definition,
       to_regclass('public.account_deactivation_operations') is not null as operation_table_exists,
       case when to_regclass('public.account_deactivation_operations') is null then false
            else has_table_privilege('authenticated', 'public.account_deactivation_operations', 'select') end
         as operation_table_client_readable,
       (select count(*)::int from pg_trigger
         where not tgisinternal and tgname in (
           'quata_community_profiles_deactivation_guard',
           'quata_push_tokens_active_owner_guard',
           'quata_web_client_sessions_active_owner_guard',
           'quata_web_push_subscriptions_active_owner_guard'
         )) as guard_trigger_count`,
    [functionIdentity],
  );
  const row = result.rows[0];
  return {
    definition: row.definition || "",
    serviceExecute: row.service_execute,
    anonExecute: row.anon_execute,
    authenticatedExecute: row.authenticated_execute,
    compatibilityDefinition: row.compatibility_definition || "",
    operationTableExists: row.operation_table_exists,
    operationTableClientReadable: row.operation_table_client_readable,
    guardTriggerCount: row.guard_trigger_count,
  };
}

function sameAcl(left, right) {
  return left.serviceExecute === right.serviceExecute
    && left.anonExecute === right.anonExecute
    && left.authenticatedExecute === right.authenticatedExecute;
}

function sameSurface(left, right) {
  return left.definition === right.definition && sameAcl(left, right)
    && left.operationTableExists === right.operationTableExists
    && left.guardTriggerCount === right.guardTriggerCount;
}

async function databaseConfiguration() {
  const urlFile = process.env.QUATA_DB_URL_FILE;
  const caFile = process.env.QUATA_DB_TLS_CA_FILE;
  if (!urlFile || !caFile) throw new Error("database_configuration_missing");
  const [raw, ca] = await Promise.all([readFile(urlFile, "utf8"), readFile(caFile, "utf8")]);
  const url = new URL(raw.trim());
  if (!["postgres:", "postgresql:"].includes(url.protocol) || !ca.includes("BEGIN CERTIFICATE")) {
    throw new Error("database_configuration_invalid");
  }
  for (const key of ["sslmode", "uselibpqcompat", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return {
    connectionString: url.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: url.hostname },
    application_name: "quata-account-deactivation-atomic-probe",
    connectionTimeoutMillis: 10_000,
    query_timeout: 60_000,
    statement_timeout: 60_000,
    lock_timeout: 10_000,
  };
}

function atomicDefinition(value) {
  return value.includes("update public.web_push_subscriptions")
    && value.includes("update public.web_client_sessions")
    && value.includes("update public.push_tokens")
    && value.includes("deactivated_auth_user_id = p_auth_user_id")
    && value.includes("lease_expires_at")
    && value.includes("account_deactivation_operations");
}

function requireAssertion(value, code) {
  if (!value) throw new Error(code);
}

function argument(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}
