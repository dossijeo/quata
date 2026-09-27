#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { dirname, resolve } from "node:path";
import process from "node:process";

const require = createRequire(import.meta.url);
const { Client } = require("pg");
const root = resolve(import.meta.dirname, "..");
const migrationPath = resolve(root, "supabase/migrations/20260927130000_chat_forward_atomicity.sql");
const VERSION = "20260927130000";
const NAME = "chat_forward_atomicity";
const FUNCTION_IDENTITY = "public.quata_chat_forward_message(uuid,bigint,bigint[])";
const RELEASE_LOCK = "quata/chat-forward-atomicity/v1";
const APPLY_OPT_IN = "I_ACCEPT_ATOMIC_CHAT_FORWARD_DATABASE_RELEASE";

const options = parseArgs(process.argv.slice(2));
const report = {
  version: 1,
  check: "CHAT-FORWARD-ATOMICITY-001",
  status: "failed",
  action: options.action,
  git: { head: git(["rev-parse", "HEAD"]).trim(), workingTreeDirty: git(["status", "--porcelain"]).trim().length > 0 },
  steps: [],
  migration: { version: VERSION, name: NAME, applied: false, ledgerRecorded: false },
  assertions: {},
  cleanup: { verified: false, state: "transaction_not_started" },
};

let client;
let transactionOpen = false;
let failureStage = "configuration";
try {
  if (report.git.workingTreeDirty) throw new Error("release_checkout_dirty");
  const config = await databaseConfig();
  failureStage = "connection";
  client = new Client(config);
  await client.connect();
  report.steps.push("tls_verified_database_connection_opened");

  failureStage = "baseline";
  const before = await releaseState(client);
  report.assertions.baseline = before;
  if (options.action === "probe") {
    report.status = "passed";
    report.cleanup = { verified: true, state: "not_required_read_only_probe" };
  } else {
    if (process.env.QUATA_CHAT_FORWARD_ATOMICITY_RELEASE_OPT_IN !== APPLY_OPT_IN) {
      throw new Error("release_mutation_opt_in_required");
    }
    if (before.ledgerRecorded || before.atomicDefinition || !before.partialDefinition) {
      throw new Error("release_baseline_mismatch");
    }
    failureStage = "candidate_selection";
    const migrationSql = await readFile(migrationPath, "utf8");
    const candidate = await findCandidate(client);

    failureStage = "release_transaction";
    await client.query("begin isolation level repeatable read");
    transactionOpen = true;
    await client.query("select pg_advisory_xact_lock(hashtextextended($1, 0))", [RELEASE_LOCK]);
    report.steps.push("exclusive_release_lock_acquired");
    const lockedState = await releaseState(client);
    if (lockedState.ledgerRecorded || lockedState.atomicDefinition || !lockedState.partialDefinition) {
      throw new Error("release_baseline_changed_under_lock");
    }

    failureStage = "function_install";
    await client.query(migrationSql);
    const installed = await functionState(client);
    if (!installed.atomicDefinition || installed.partialDefinition) throw new Error("atomic_function_postcondition_failed");
    report.steps.push("atomic_function_installed_inside_transaction");

    failureStage = "ledger_stage";
    await client.query(
      `insert into supabase_migrations.schema_migrations(version, statements, name)
       values ($1, $2::text[], $3)`,
      [VERSION, [migrationSql], NAME],
    );
    report.steps.push("migration_ledger_staged_inside_transaction");

    failureStage = "atomic_behavior";
    report.assertions.behavior = await verifyAtomicBehavior(client, candidate);
    report.steps.push("mixed_destination_failure_rolled_back_without_partial_copy");
    report.steps.push("duplicate_destination_success_created_one_transactional_copy");

    failureStage = "commit";
    await client.query("commit");
    transactionOpen = false;
    report.steps.push("verified_release_transaction_committed");

    failureStage = "commit_recheck";
    const after = await releaseState(client);
    if (!after.ledgerRecorded || !after.atomicDefinition || after.partialDefinition) {
      throw new Error("release_commit_recheck_failed");
    }
    report.assertions.committed = after;
    report.migration = { version: VERSION, name: NAME, applied: true, ledgerRecorded: true };
    report.status = "passed";
    report.cleanup = { verified: true, state: "transactional_probes_rolled_back_zero_persistent_rows" };
  }
} catch (error) {
  if (transactionOpen && client) {
    await client.query("rollback").catch(() => {});
    transactionOpen = false;
    report.cleanup = { verified: true, state: "release_transaction_rolled_back" };
  }
  report.failureCode = sanitizeFailure(error);
  report.failureStage = failureStage;
  if (/^[0-9A-Z]{5}$/.test(error?.code ?? "")) report.databaseCode = error.code;
  process.exitCode = 1;
} finally {
  await client?.end().catch(() => {});
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  process.stdout.write(`${report.status === "passed" ? "PASS" : "FAIL"} ${report.check} report=${options.output}\n`);
}

async function databaseConfig() {
  const urlFile = process.env.QUATA_DB_URL_FILE;
  const caFile = process.env.QUATA_DB_TLS_CA_FILE;
  if (!urlFile || !caFile) throw new Error("release_database_configuration_missing");
  const [raw, ca] = await Promise.all([readFile(urlFile, "utf8"), readFile(caFile, "utf8")]);
  const url = new URL(raw.trim());
  if (!["postgres:", "postgresql:"].includes(url.protocol) || !ca.includes("BEGIN CERTIFICATE")) {
    throw new Error("release_database_configuration_invalid");
  }
  for (const key of ["sslmode", "uselibpqcompat", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return {
    connectionString: url.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: url.hostname },
    application_name: "quata-chat-forward-atomicity-release",
    connectionTimeoutMillis: 10_000,
    query_timeout: 60_000,
    statement_timeout: 60_000,
    lock_timeout: 10_000,
  };
}

async function releaseState(db) {
  const [definition, ledger] = await Promise.all([
    functionState(db),
    db.query("select count(*)::int as count from supabase_migrations.schema_migrations where version=$1", [VERSION]),
  ]);
  return { ...definition, ledgerRecorded: ledger.rows[0].count === 1 };
}

async function functionState(db) {
  const result = await db.query("select pg_get_functiondef($1::regprocedure) as definition", [FUNCTION_IDENTITY]);
  const definition = result.rows[0]?.definition ?? "";
  return {
    atomicDefinition: definition.includes("v_target_thread_ids")
      && definition.includes("profile cannot forward to target thread")
      && !/exception when others/i.test(definition),
    partialDefinition: /exception when others/i.test(definition) && definition.includes("v_errors"),
  };
}

async function findCandidate(db) {
  const result = await db.query(`
    with candidates as (
      select participant.profile_id as actor_id,
             message.id as source_id,
             (
               select other.thread_id
               from public.chat_participants other
               where other.profile_id = participant.profile_id
                 and other.left_at is null
                 and other.thread_id <> message.thread_id
               order by other.thread_id
               limit 1
             ) as valid_target,
             (
               select thread.id
               from public.chat_threads thread
               where not exists (
                 select 1 from public.chat_participants denied
                 where denied.thread_id = thread.id
                   and denied.profile_id = participant.profile_id
                   and denied.left_at is null
               )
               order by thread.id
               limit 1
             ) as invalid_target
      from public.chat_participants participant
      join public.community_profiles profile
        on profile.id = participant.profile_id and profile.account_status = 'active'
      join public.chat_messages message
        on message.thread_id = participant.thread_id and message.deleted_at is null
      where participant.left_at is null
      order by message.id desc
    )
    select actor_id, source_id, valid_target, invalid_target
    from candidates
    where valid_target is not null and invalid_target is not null
    limit 1
  `);
  if (result.rowCount !== 1) throw new Error("release_atomicity_candidate_unavailable");
  return result.rows[0];
}

async function verifyAtomicBehavior(db, candidate) {
  const parameters = [candidate.valid_target, candidate.actor_id, candidate.source_id];
  const countSql = `select count(*)::int as count from public.chat_messages
    where thread_id=$1 and sender_profile_id=$2 and forwarded_from_message_id=$3 and deleted_at is null`;
  const baseline = (await db.query(countSql, parameters)).rows[0].count;

  await db.query("savepoint mixed_destination_probe");
  let denied = false;
  try {
    await db.query(`select public.quata_chat_forward_message($1,$2,$3::bigint[])`, [
      candidate.actor_id,
      candidate.source_id,
      [candidate.valid_target, candidate.invalid_target],
    ]);
  } catch (error) {
    denied = error?.code === "42501";
  }
  await db.query("rollback to savepoint mixed_destination_probe");
  if (!denied) throw new Error("mixed_destination_probe_did_not_fail_closed");
  const afterFailure = (await db.query(countSql, parameters)).rows[0].count;
  if (afterFailure !== baseline) throw new Error("mixed_destination_probe_left_partial_copy");

  await db.query("savepoint duplicate_destination_probe");
  const success = await db.query(`select public.quata_chat_forward_message($1,$2,$3::bigint[]) as result`, [
    candidate.actor_id,
    candidate.source_id,
    [candidate.valid_target, candidate.valid_target],
  ]);
  const value = success.rows[0]?.result;
  if (Object.keys(value?.sent ?? {}).length !== 1 || (value?.errors ?? []).length !== 0) {
    throw new Error("duplicate_destination_probe_result_invalid");
  }
  const duringSuccess = (await db.query(countSql, parameters)).rows[0].count;
  if (duringSuccess !== baseline + 1) throw new Error("duplicate_destination_probe_not_deduplicated");
  await db.query("rollback to savepoint duplicate_destination_probe");
  const afterSuccessRollback = (await db.query(countSql, parameters)).rows[0].count;
  if (afterSuccessRollback !== baseline) throw new Error("transactional_probe_cleanup_failed");

  return {
    candidateAvailable: true,
    unauthorizedTargetRejected: true,
    partialCopyDelta: afterFailure - baseline,
    duplicateRequestCreatedCount: duringSuccess - baseline,
    persistentCopyDelta: afterSuccessRollback - baseline,
  };
}

function parseArgs(argv) {
  const result = { action: "probe", output: resolve(root, "build-reports/chat-forward/atomicity-release.json") };
  for (let index = 0; index < argv.length; index += 1) {
    if (argv[index] === "--action") result.action = argv[++index];
    else if (argv[index] === "--out") result.output = resolve(argv[++index]);
    else throw new Error("release_invalid_argument");
  }
  if (!["probe", "apply"].includes(result.action)) throw new Error("release_invalid_action");
  return result;
}

function sanitizeFailure(error) {
  const message = String(error?.message ?? "release_failed");
  return /^[a-z0-9_]+$/.test(message) ? message : "release_database_operation_failed";
}

function git(args) {
  return execFileSync("git", args, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
}
