#!/usr/bin/env node
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import process from 'node:process';
import pg from 'pg';

const { Client } = pg;

function parseArgs(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === '--db-url-file') args.dbUrlFile = argv[++index];
    else if (value === '--tls-ca-file') args.tlsCaFile = argv[++index];
    else throw new Error('probe_unknown_argument');
  }
  if (!args.dbUrlFile || !args.tlsCaFile) throw new Error('probe_private_input_required');
  return args;
}

async function assertInstalled(client) {
  const result = await client.query(`
    select count(*)::int as matching_rows
      from supabase_migrations.schema_migrations
     where version::text = '20261009070000'
       and coalesce(name, '') = 'chat_private_thread_open_idempotency'
  `);
  if (result.rows[0]?.matching_rows !== 1) throw new Error('probe_installed_ledger_invalid');
  const functionState = await client.query(`
    select p.prosecdef as security_definer, p.provolatile as volatility,
           p.proconfig as configuration, pg_get_functiondef(p.oid) as definition
      from pg_proc p join pg_namespace n on n.oid=p.pronamespace
     where n.nspname='public'
       and p.oid=to_regprocedure('public.quata_chat_get_or_create_private_thread(uuid,uuid)')
  `);
  const row = functionState.rows[0];
  if (functionState.rowCount !== 1 || !row.security_definer || row.volatility !== 'v'
      || JSON.stringify(row.configuration) !== JSON.stringify(['search_path=public'])
      || !/v_created boolean := false/i.test(row.definition ?? '')
      || !/if v_created then[\s\S]*private_thread_opened[\s\S]*end if/i.test(row.definition ?? '')) {
    throw new Error('probe_deployed_function_invalid');
  }
}

async function selectFixture(client) {
  const pair = await client.query(`
    select actor.id as actor_id, actor.auth_user_id, peer.id as peer_id
      from public.community_profiles actor
      join public.community_profiles peer on peer.id > actor.id
     where actor.auth_user_id is not null
       and actor.account_status = 'active'
       and peer.account_status = 'active'
       and not exists (
         select 1 from public.chat_private_threads private_pair
          where private_pair.profile_low_id = least(actor.id, peer.id)
            and private_pair.profile_high_id = greatest(actor.id, peer.id)
       )
     order by actor.id, peer.id
     limit 1
     for share of actor, peer
  `);
  if (pair.rowCount !== 1) throw new Error('probe_unused_private_pair_required');
  const member = await client.query(`
    select id
      from public.community_profiles
     where account_status='active' and id <> all($1::uuid[])
     order by id
     limit 1
     for share
  `, [[pair.rows[0].actor_id, pair.rows[0].peer_id]]);
  if (member.rowCount !== 1) throw new Error('probe_third_active_profile_required');
  return { ...pair.rows[0], member_id: member.rows[0].id };
}

async function probe(client) {
  const fixture = await selectFixture(client);
  const groupKey = `conversation-uncertain-${randomUUID()}`;
  await client.query("select set_config('request.jwt.claim.sub', $1::text, true)", [fixture.auth_user_id]);

  const privateFirst = await client.query(
    'select public.quata_chat_get_or_create_private_thread($1::uuid,$2::uuid) as payload',
    [fixture.actor_id, fixture.peer_id],
  );
  const privateThreadId = privateFirst.rows[0]?.payload?.thread?.id;
  if (!privateThreadId) throw new Error('probe_private_first_response_invalid');
  const privateSecond = await client.query(
    'select public.quata_chat_get_or_create_private_thread($1::uuid,$2::uuid) as payload',
    [fixture.actor_id, fixture.peer_id],
  );
  if (privateSecond.rows[0]?.payload?.thread?.id !== privateThreadId) {
    throw new Error('probe_private_retry_thread_mismatch');
  }
  const privateCounts = await client.query(`
    select
      (select count(*)::int from public.chat_threads where id=$1) as threads,
      (select count(*)::int from public.chat_private_threads where thread_id=$1) as pairs,
      (select count(*)::int from public.chat_participants where thread_id=$1) as participants,
      (select count(*)::int from public.chat_events where thread_id=$1 and event_type='private_thread_opened') as events
  `, [privateThreadId]);
  if (JSON.stringify(privateCounts.rows[0]) !== JSON.stringify({ threads: 1, pairs: 1, participants: 2, events: 1 })) {
    throw new Error('probe_private_retry_cardinality_invalid');
  }

  const groupParams = [fixture.actor_id, [fixture.peer_id, fixture.member_id], 'Uncertain response group', 'group', 'First message', groupKey];
  const groupFirst = await client.query(
    'select public.quata_chat_start_thread($1::uuid,$2::uuid[],$3::text,$4::text,$5::text,$6::text,null) as payload',
    groupParams,
  );
  const groupThreadId = groupFirst.rows[0]?.payload?.thread?.id;
  if (!groupThreadId) throw new Error('probe_group_first_response_invalid');
  const groupSecond = await client.query(
    'select public.quata_chat_start_thread($1::uuid,$2::uuid[],$3::text,$4::text,$5::text,$6::text,null) as payload',
    groupParams,
  );
  if (groupSecond.rows[0]?.payload?.thread?.id !== groupThreadId) throw new Error('probe_group_retry_thread_mismatch');
  const groupCounts = await client.query(`
    select
      (select count(*)::int from public.chat_threads where id=$1) as threads,
      (select count(*)::int from public.chat_participants where thread_id=$1) as participants,
      (select count(*)::int from public.chat_messages where thread_id=$1 and body='First message') as messages,
      (select count(*)::int from public.chat_events where thread_id=$1 and event_type='thread_started') as events
  `, [groupThreadId]);
  if (JSON.stringify(groupCounts.rows[0]) !== JSON.stringify({ threads: 1, participants: 3, messages: 1, events: 1 })) {
    throw new Error('probe_group_retry_cardinality_invalid');
  }
  return { privateThreadId, groupKey };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [connectionString, ca] = await Promise.all([
    readFile(args.dbUrlFile, 'utf8').then((value) => value.trim()),
    readFile(args.tlsCaFile, 'utf8'),
  ]);
  const url = new URL(connectionString);
  if (url.searchParams.get('sslmode') !== 'verify-full') throw new Error('probe_database_url_requires_verify_full');
  url.searchParams.delete('sslmode');
  const client = new Client({
    connectionString: url.toString(),
    ssl: { ca, rejectUnauthorized: true },
    connectionTimeoutMillis: 20_000,
    query_timeout: 30_000,
    application_name: 'quata_conversation_create_uncertain_response_probe',
  });
  let transactionOpen = false;
  let fixture = null;
  try {
    await client.connect();
    await assertInstalled(client);
    await client.query('begin');
    transactionOpen = true;
    await client.query("set local lock_timeout='5s'");
    await client.query("set local statement_timeout='20s'");
    fixture = await probe(client);
    await client.query('rollback');
    transactionOpen = false;
    await assertInstalled(client);
    const residue = await client.query(`
      select
        (select count(*)::int from public.chat_threads where id=$1) as private_threads,
        (select count(*)::int from public.chat_threads where unique_key=$2) as group_threads
    `, [fixture.privateThreadId, fixture.groupKey]);
    if (residue.rows[0]?.private_threads !== 0 || residue.rows[0]?.group_threads !== 0) {
      throw new Error('probe_transaction_residue_detected');
    }
    process.stdout.write('CONVERSATION_CREATE_UNCERTAIN_RESPONSE_POSTDEPLOY_PASS\n');
  } finally {
    if (transactionOpen) await client.query('rollback').catch(() => {});
    await client.end().catch(() => {});
  }
}

main().catch((error) => {
  const code = typeof error?.message === 'string' && /^probe_[a-z0-9_]+$/.test(error.message)
    ? error.message
    : 'probe_failed_redacted';
  process.stderr.write(`${code}\n`);
  process.exitCode = 1;
});
