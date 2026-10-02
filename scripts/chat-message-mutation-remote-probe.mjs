#!/usr/bin/env node
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import process from "node:process";
import pg from "pg";

const { Client } = pg;

function parseArgs(argv) {
  const args = {
    migration: "supabase/migrations/20261002003000_chat_message_mutation_idempotency.sql",
    mode: "predeploy",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === "--db-url-file") args.dbUrlFile = argv[++index];
    else if (value === "--tls-ca-file") args.tlsCaFile = argv[++index];
    else if (value === "--migration") args.migration = argv[++index];
    else if (value === "--mode") args.mode = argv[++index];
    else throw new Error("probe_unknown_argument");
  }
  if (!args.dbUrlFile || !args.tlsCaFile) throw new Error("probe_private_input_required");
  if (!["predeploy", "postdeploy"].includes(args.mode)) throw new Error("probe_mode_invalid");
  return args;
}

function unwrapMigration(source) {
  const withoutBegin = source.replace(/^\s*begin;\s*/i, "");
  const withoutCommit = withoutBegin.replace(/\s*commit;\s*$/i, "");
  if (withoutBegin === source || withoutCommit === withoutBegin) throw new Error("probe_migration_transaction_wrapper_invalid");
  return withoutCommit;
}

async function scalar(client, text, values = []) {
  const result = await client.query(text, values);
  return result.rows[0]?.value;
}

async function assertBaselineAbsent(client) {
  const result = await client.query(`
    select
      to_regclass('public.chat_message_mutation_receipts') is null as table_absent,
      to_regprocedure('public.quata_chat_edit_message_v2(uuid,bigint,bigint,text,text)') is null as edit_absent,
      to_regprocedure('public.quata_chat_delete_messages_v2(uuid,bigint,bigint[],text)') is null as delete_absent
  `);
  const row = result.rows[0];
  if (!row?.table_absent || !row?.edit_absent || !row?.delete_absent) throw new Error("probe_target_already_present");
}

async function assertInstalledBoundary(client) {
  const boundary = await client.query(`
    select
      c.relrowsecurity as rls_enabled,
      not has_table_privilege('anon', c.oid, 'SELECT,INSERT,UPDATE,DELETE') as anon_table_denied,
      not has_table_privilege('authenticated', c.oid, 'SELECT,INSERT,UPDATE,DELETE') as authenticated_table_denied,
      has_function_privilege('authenticated', 'public.quata_chat_edit_message_v2(uuid,bigint,bigint,text,text)', 'EXECUTE') as authenticated_edit_execute,
      has_function_privilege('authenticated', 'public.quata_chat_delete_messages_v2(uuid,bigint,bigint[],text)', 'EXECUTE') as authenticated_delete_execute,
      not has_function_privilege('anon', 'public.quata_chat_edit_message_v2(uuid,bigint,bigint,text,text)', 'EXECUTE') as anon_edit_denied,
      not has_function_privilege('anon', 'public.quata_chat_delete_messages_v2(uuid,bigint,bigint[],text)', 'EXECUTE') as anon_delete_denied,
      not pg_has_role('anon', 'authenticated', 'member') as anon_not_authenticated_member,
      not exists (
        select 1 from pg_proc p, lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
         where p.oid = 'public.quata_chat_edit_message_v2(uuid,bigint,bigint,text,text)'::regprocedure
           and acl.grantee = 0 and acl.privilege_type = 'EXECUTE'
      ) as edit_public_execute_absent,
      not exists (
        select 1 from pg_proc p, lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
         where p.oid = 'public.quata_chat_delete_messages_v2(uuid,bigint,bigint[],text)'::regprocedure
           and acl.grantee = 0 and acl.privilege_type = 'EXECUTE'
      ) as delete_public_execute_absent
      , not exists (
        select 1
          from pg_proc p, lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
         where p.oid in (
           'public.quata_chat_edit_message_v2(uuid,bigint,bigint,text,text)'::regprocedure,
           'public.quata_chat_delete_messages_v2(uuid,bigint,bigint[],text)'::regprocedure
         )
           and acl.grantee <> 0
           and pg_has_role('anon', acl.grantee, 'member')
      ) as anon_no_grantee_membership
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = 'chat_message_mutation_receipts'
  `);
  const row = boundary.rows[0];
  if (!row) throw new Error("probe_installed_boundary_missing");
  const failedBoundary = Object.entries(row).filter(([, value]) => value !== true).map(([name]) => name);
  if (failedBoundary.length) throw new Error(`probe_installed_boundary_invalid_${failedBoundary.join("_")}`);

  await client.query("savepoint receipt_acl");
  let directReadDenied = false;
  try {
    await client.query("set local role authenticated");
    await client.query("select count(*) from public.chat_message_mutation_receipts");
  } catch (error) {
    directReadDenied = error?.code === "42501";
  } finally {
    await client.query("rollback to savepoint receipt_acl");
    await client.query("release savepoint receipt_acl");
  }
  if (!directReadDenied) throw new Error("probe_receipt_table_direct_read_not_denied");
}

async function assertInstalledLedger(client) {
  const ledger = await client.query(`
    select count(*)::int as matching_rows
      from supabase_migrations.schema_migrations
     where version::text = '20261002003000'
       and coalesce(name, '') = 'chat_message_mutation_idempotency'
  `);
  if (ledger.rows[0]?.matching_rows !== 1) throw new Error("probe_installed_ledger_invalid");
}

async function runBehaviorProbe(client, marker) {
  const actor = await client.query(`
    select id, auth_user_id
      from public.community_profiles
     where auth_user_id is not null and account_status = 'active'
     order by id limit 1 for share
  `);
  if (actor.rowCount !== 1) throw new Error("probe_active_authenticated_profile_missing");
  const actorId = actor.rows[0].id;
  const authUserId = actor.rows[0].auth_user_id;
  await client.query("select set_config('request.jwt.claim.sub', $1::text, true)", [authUserId]);

  const thread = await client.query(`
    insert into public.chat_threads(type, title, created_by_profile_id, metadata)
    values ('group', $1, $2, '{}'::jsonb) returning id
  `, [marker, actorId]);
  const threadId = thread.rows[0].id;
  await client.query("insert into public.chat_participants(thread_id, profile_id, role) values ($1, $2, 'owner')", [threadId, actorId]);
  const message = await client.query(`
    insert into public.chat_messages(thread_id, sender_profile_id, body, client_message_id)
    values ($1, $2, 'before', $3) returning id
  `, [threadId, actorId, `${marker}-message`]);
  const messageId = message.rows[0].id;
  const editKey = `chat-mutation-${randomUUID()}`;
  const deleteKey = `chat-mutation-${randomUUID()}`;

  const editArgs = [actorId, threadId, messageId, "after", editKey];
  await client.query("select public.quata_chat_edit_message_v2($1, $2, $3, $4, $5)", editArgs);
  await client.query("select public.quata_chat_edit_message_v2($1, $2, $3, $4, $5)", editArgs);
  const editedBody = await scalar(client, "select body as value from public.chat_messages where id = $1", [messageId]);
  const editEvents = Number(await scalar(client, `
    select count(*)::int as value from public.chat_events
     where thread_id = $1 and event_type = 'message_edited' and payload ->> 'message_id' = $2::text
  `, [threadId, messageId]));
  if (editedBody !== "after" || editEvents !== 1) throw new Error("probe_edit_idempotency_failed");

  await client.query("savepoint mutation_key_reuse");
  let keyReuseDenied = false;
  try {
    await client.query("select public.quata_chat_edit_message_v2($1, $2, $3, $4, $5)", [actorId, threadId, messageId, "different", editKey]);
  } catch (error) {
    keyReuseDenied = error?.code === "22023";
  } finally {
    await client.query("rollback to savepoint mutation_key_reuse");
    await client.query("release savepoint mutation_key_reuse");
  }
  if (!keyReuseDenied) throw new Error("probe_mutation_key_reuse_not_denied");

  const deleteArgs = [actorId, threadId, [messageId], deleteKey];
  await client.query("select public.quata_chat_delete_messages_v2($1, $2, $3::bigint[], $4)", deleteArgs);
  await client.query("select public.quata_chat_delete_messages_v2($1, $2, $3::bigint[], $4)", deleteArgs);
  const deleted = await scalar(client, "select deleted_at is not null as value from public.chat_messages where id = $1", [messageId]);
  const deleteEvents = Number(await scalar(client, `
    select count(*)::int as value from public.chat_events
     where thread_id = $1 and event_type = 'messages_deleted'
       and payload -> 'message_ids' @> to_jsonb(array[$2::bigint])
  `, [threadId, messageId]));
  const receipts = Number(await scalar(client, `
    select count(*)::int as value from public.chat_message_mutation_receipts
     where actor_profile_id = $1 and client_mutation_id = any($2::text[])
       and response is not null and completed_at is not null
  `, [actorId, [editKey, deleteKey]]));
  if (!deleted || deleteEvents !== 1 || receipts !== 2) throw new Error("probe_delete_idempotency_failed");
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [connectionString, ca, migrationSource] = await Promise.all([
    readFile(args.dbUrlFile, "utf8").then((value) => value.trim()),
    readFile(args.tlsCaFile, "utf8"),
    args.mode === "predeploy" ? readFile(args.migration, "utf8") : Promise.resolve(null),
  ]);
  const migration = migrationSource === null ? null : unwrapMigration(migrationSource);
  const connectionUrl = new URL(connectionString);
  if (connectionUrl.searchParams.get("sslmode") !== "verify-full") throw new Error("probe_database_url_requires_verify_full");
  connectionUrl.searchParams.delete("sslmode");
  const marker = `quata-chat-mutation-probe-${randomUUID()}`;
  const client = new Client({
    connectionString: connectionUrl.toString(),
    ssl: { ca, rejectUnauthorized: true },
    connectionTimeoutMillis: 20_000,
    query_timeout: 30_000,
    application_name: "quata_chat_message_mutation_probe",
  });
  let transactionOpen = false;
  let stage = "connect";
  try {
    await client.connect();
    if (args.mode === "predeploy") {
      stage = "baseline";
      await assertBaselineAbsent(client);
    } else {
      stage = "ledger";
      await assertInstalledLedger(client);
    }
    stage = "transaction";
    await client.query("begin");
    transactionOpen = true;
    await client.query("set local lock_timeout = '10s'");
    await client.query("set local statement_timeout = '30s'");
    if (args.mode === "predeploy") {
      stage = "migration";
      await client.query(migration);
    }
    stage = "boundary";
    await assertInstalledBoundary(client);
    stage = "behavior";
    await runBehaviorProbe(client, marker);
    stage = "rollback";
    await client.query("rollback");
    transactionOpen = false;
    stage = "residue";
    if (args.mode === "predeploy") {
      await assertBaselineAbsent(client);
    } else {
      await assertInstalledLedger(client);
      await client.query("begin");
      transactionOpen = true;
      await assertInstalledBoundary(client);
      await client.query("rollback");
      transactionOpen = false;
    }
    const residue = Number(await scalar(client, "select count(*)::int as value from public.chat_threads where title = $1", [marker]));
    if (residue !== 0) throw new Error("probe_fixture_residue_detected");
    process.stdout.write(args.mode === "predeploy"
      ? "CHAT_MESSAGE_MUTATION_REMOTE_ROLLBACK_PASS\n"
      : "CHAT_MESSAGE_MUTATION_REMOTE_POSTDEPLOY_PASS\n");
  } catch (error) {
    const databaseCode = typeof error?.code === "string" && /^[0-9A-Z]{5}$/.test(error.code)
      ? error.code.toLowerCase()
      : "unknown";
    if (typeof error?.message === "string" && /^probe_[a-z0-9_]+$/.test(error.message)) throw error;
    throw new Error(`probe_${stage}_${databaseCode}`);
  } finally {
    if (transactionOpen) await client.query("rollback").catch(() => {});
    await client.end().catch(() => {});
  }
}

main().catch((error) => {
  const code = typeof error?.message === "string" && /^probe_[a-z0-9_]+$/.test(error.message)
    ? error.message
    : "probe_failed_redacted";
  process.stderr.write(`${code}\n`);
  process.exitCode = 1;
});
