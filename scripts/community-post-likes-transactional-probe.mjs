#!/usr/bin/env node
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import process from "node:process";
import pg from "pg";

const { Client } = pg;

function parseArgs(argv) {
  const args = {
    migration: "supabase/migrations/20261002010000_community_post_likes_actor_guard.sql",
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

async function snapshot(client) {
  const table = await client.query(`
      select c.relrowsecurity as rls_enabled
        from pg_class c join pg_namespace n on n.oid = c.relnamespace
       where n.nspname = 'public' and c.relname = 'community_post_likes'
    `);
  const policies = await client.query(`
      select policyname, roles::text, cmd, qual, with_check
        from pg_catalog.pg_policies
       where schemaname = 'public' and tablename = 'community_post_likes'
       order by policyname
    `);
  const grants = await client.query(`
      select grantee, privilege_type
        from information_schema.role_table_grants
       where table_schema = 'public' and table_name = 'community_post_likes'
         and grantee in ('PUBLIC', 'anon', 'authenticated')
       order by grantee, privilege_type
    `);
  const triggers = await client.query(`
      select count(*)::int as count
        from pg_trigger t join pg_class c on c.oid = t.tgrelid
        join pg_namespace n on n.oid = c.relnamespace
       where n.nspname = 'public' and c.relname = 'community_post_likes' and not t.tgisinternal
    `);
  const resolver = await client.query(`
      select l.lanname as language, p.provolatile as volatility,
             p.prosecdef as security_definer, p.proconfig as config, p.prosrc as source,
             exists (
               select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
                where acl.grantee = 0 and acl.privilege_type = 'EXECUTE'
             ) as public_execute,
             has_function_privilege('anon', p.oid, 'execute') as anon_execute,
             has_function_privilege('authenticated', p.oid, 'execute') as authenticated_execute
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        join pg_language l on l.oid = p.prolang
       where n.nspname = 'public' and p.proname = 'quata_chat_auth_profile_id'
         and p.pronargs = 0
    `);
  return {
    rlsEnabled: table.rows[0]?.rls_enabled === true,
    policies: policies.rows,
    grants: grants.rows,
    triggerCount: triggers.rows[0]?.count,
    resolver: resolver.rows,
  };
}

function grantMap(rows) {
  const result = new Map();
  for (const row of rows) {
    if (!result.has(row.grantee)) result.set(row.grantee, []);
    result.get(row.grantee).push(row.privilege_type);
  }
  for (const values of result.values()) values.sort();
  return result;
}

function assertBaseline(value) {
  const names = value.policies.map(({ policyname }) => policyname);
  if (!value.rlsEnabled || JSON.stringify(names) !== JSON.stringify([
    "public delete likes",
    "public insert likes",
    "public read likes",
  ])) throw new Error("probe_baseline_policy_mismatch");
  const commands = value.policies.map(({ cmd }) => cmd);
  if (JSON.stringify(commands) !== JSON.stringify(["DELETE", "INSERT", "SELECT"]) ||
      value.policies[0].qual !== "true" || value.policies[1].with_check !== "true" || value.policies[2].qual !== "true") {
    throw new Error("probe_baseline_policy_expression_mismatch");
  }
  const grants = grantMap(value.grants);
  const broad = ["DELETE", "INSERT", "REFERENCES", "SELECT", "TRIGGER", "TRUNCATE", "UPDATE"];
  if (JSON.stringify(grants.get("anon") ?? []) !== JSON.stringify(broad) ||
      JSON.stringify(grants.get("authenticated") ?? []) !== JSON.stringify(broad) ||
      (grants.get("PUBLIC") ?? []).length !== 0) {
    throw new Error("probe_baseline_grant_mismatch");
  }
  if (value.triggerCount !== 0) throw new Error("probe_baseline_trigger_mismatch");
}

function assertForward(value) {
  const names = value.policies.map(({ policyname }) => policyname);
  if (!value.rlsEnabled || JSON.stringify(names) !== JSON.stringify([
    "community_post_likes_delete_own",
    "community_post_likes_insert_own",
    "community_post_likes_public_read",
  ])) throw new Error("probe_forward_policy_mismatch");
  const expectedActorExpression = "((( SELECT quata_chat_auth_profile_id() AS quata_chat_auth_profile_id) IS NOT NULL) AND (profile_id = ( SELECT quata_chat_auth_profile_id() AS quata_chat_auth_profile_id)))";
  for (const row of value.policies.filter(({ cmd }) => cmd !== "SELECT")) {
    const expression = String(row.qual ?? row.with_check ?? "").replace(/\s+/g, " ").trim();
    if (row.roles !== "{authenticated}" || expression !== expectedActorExpression) {
      throw new Error("probe_forward_actor_expression_mismatch");
    }
  }
  const read = value.policies.find(({ cmd }) => cmd === "SELECT");
  if (read?.roles !== "{public}" || read?.qual !== "true") throw new Error("probe_forward_public_read_mismatch");
  const grants = grantMap(value.grants);
  if (JSON.stringify(grants.get("anon") ?? []) !== JSON.stringify(["SELECT"]) ||
      JSON.stringify(grants.get("authenticated") ?? []) !== JSON.stringify(["DELETE", "INSERT", "SELECT"]) ||
      (grants.get("PUBLIC") ?? []).length !== 0) {
    throw new Error("probe_forward_grant_mismatch");
  }
  if (value.triggerCount !== 0) throw new Error("probe_forward_trigger_mismatch");
  if (value.resolver.length !== 1) throw new Error("probe_forward_resolver_missing");
  const resolver = value.resolver[0];
  const normalized = (input) => String(input ?? "").replace(/\s+/g, " ").trim();
  const expectedResolver = "select cp.id from public.community_profiles cp where auth.uid() is not null and cp.account_status = 'active' and (cp.id = auth.uid() or cp.auth_user_id = auth.uid()) limit 1";
  if (resolver.language !== "sql" || resolver.volatility !== "s" || resolver.security_definer !== true ||
      JSON.stringify(resolver.config) !== JSON.stringify(["search_path=public, auth"]) ||
      normalized(resolver.source) !== expectedResolver || resolver.public_execute !== true ||
      resolver.anon_execute !== true || resolver.authenticated_execute !== true) {
    throw new Error("probe_forward_resolver_mismatch");
  }
}

async function assertInstalledLedger(client) {
  const result = await client.query(`
    select count(*)::int as matching_rows
      from supabase_migrations.schema_migrations
     where version::text = '20261002010000'
       and coalesce(name, '') = 'community_post_likes_actor_guard'
  `);
  if (result.rows[0]?.matching_rows !== 1) throw new Error("probe_installed_ledger_invalid");
}

async function expectMutationRejected(client, savepoint, role, authUserId, sql, params, failureCode) {
  await client.query(`savepoint ${savepoint}`);
  let rejected = false;
  try {
    if (authUserId) await client.query("select set_config('request.jwt.claim.sub', $1::text, true)", [authUserId]);
    await client.query(`set local role ${role}`);
    await client.query(sql, params);
  } catch (error) {
    rejected = error?.code === "42501";
  } finally {
    await client.query(`rollback to savepoint ${savepoint}`);
    await client.query(`release savepoint ${savepoint}`);
    await client.query("reset role");
  }
  if (!rejected) throw new Error(failureCode);
}

async function runBehaviorProbe(client) {
  const actors = await client.query(`
    select id, auth_user_id
      from public.community_profiles
     where auth_user_id is not null and account_status = 'active'
     order by id
     limit 2
     for share
  `);
  if (actors.rowCount !== 2) throw new Error("probe_two_active_authenticated_profiles_required");
  const [owner, other] = actors.rows;
  const post = await client.query(`
    select candidate.id
      from public.community_posts candidate
     where not exists (
       select 1 from public.community_post_likes existing
        where existing.post_id = candidate.id and existing.profile_id = $1
     )
     order by candidate.id
     limit 1
     for share
  `, [owner.id]);
  if (post.rowCount !== 1) throw new Error("probe_unliked_existing_post_required");
  const postId = post.rows[0].id;
  const likeId = randomUUID();
  const insert = `insert into public.community_post_likes(id, post_id, profile_id) values ($1, $2, $3)`;

  await expectMutationRejected(
    client, "anon_insert", "anon", null, insert, [likeId, postId, owner.id], "probe_anonymous_insert_not_rejected",
  );
  await expectMutationRejected(
    client, "cross_insert", "authenticated", other.auth_user_id, insert, [likeId, postId, owner.id],
    "probe_cross_actor_insert_not_rejected",
  );

  await client.query("select set_config('request.jwt.claim.sub', $1::text, true)", [owner.auth_user_id]);
  await client.query("set local role authenticated");
  const inserted = await client.query(`${insert} returning id`, [likeId, postId, owner.id]);
  await client.query("reset role");
  if (inserted.rowCount !== 1 || inserted.rows[0]?.id !== likeId) throw new Error("probe_own_insert_failed");

  await client.query("savepoint cross_delete");
  await client.query("select set_config('request.jwt.claim.sub', $1::text, true)", [other.auth_user_id]);
  await client.query("set local role authenticated");
  const crossDelete = await client.query(
    "delete from public.community_post_likes where id = $1 returning id",
    [likeId],
  );
  await client.query("rollback to savepoint cross_delete");
  await client.query("release savepoint cross_delete");
  await client.query("reset role");
  if (crossDelete.rowCount !== 0) throw new Error("probe_cross_actor_delete_not_filtered");

  await client.query("select set_config('request.jwt.claim.sub', $1::text, true)", [owner.auth_user_id]);
  await client.query("set local role authenticated");
  const ownDelete = await client.query(
    "delete from public.community_post_likes where id = $1 returning id",
    [likeId],
  );
  await client.query("reset role");
  if (ownDelete.rowCount !== 1 || ownDelete.rows[0]?.id !== likeId) throw new Error("probe_own_delete_failed");

  const remaining = await client.query(
    "select count(*)::int as count from public.community_post_likes where id = $1",
    [likeId],
  );
  if (remaining.rows[0]?.count !== 0) throw new Error("probe_transaction_fixture_not_removed");
  return { likeId, postId, ownerId: owner.id };
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
  const client = new Client({
    connectionString: connectionUrl.toString(),
    ssl: { ca, rejectUnauthorized: true },
    connectionTimeoutMillis: 20_000,
    query_timeout: 30_000,
    application_name: "quata_community_post_likes_transactional_probe",
  });
  let transactionOpen = false;
  let fixture = null;
  try {
    await client.connect();
    if (args.mode === "predeploy") {
      assertBaseline(await snapshot(client));
    } else {
      await assertInstalledLedger(client);
      assertForward(await snapshot(client));
    }
    await client.query("begin");
    transactionOpen = true;
    await client.query("set local lock_timeout = '5s'");
    await client.query("set local statement_timeout = '20s'");
    if (args.mode === "predeploy") await client.query(migration);
    assertForward(await snapshot(client));
    fixture = await runBehaviorProbe(client);
    await client.query("rollback");
    transactionOpen = false;
    if (args.mode === "predeploy") {
      assertBaseline(await snapshot(client));
    } else {
      await assertInstalledLedger(client);
      assertForward(await snapshot(client));
    }
    const residue = await client.query(`
      select
        count(*) filter (where id = $1)::int as id_count,
        count(*) filter (where post_id = $2 and profile_id = $3)::int as pair_count
      from public.community_post_likes
    `, [fixture.likeId, fixture.postId, fixture.ownerId]);
    if (residue.rows[0]?.id_count !== 0 || residue.rows[0]?.pair_count !== 0) {
      throw new Error("probe_fixture_residue_detected");
    }
    process.stdout.write(args.mode === "predeploy"
      ? "COMMUNITY_POST_LIKES_TRANSACTIONAL_PROBE_PASS\n"
      : "COMMUNITY_POST_LIKES_POSTDEPLOY_PASS\n");
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
