#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import process from "node:process";
import pg from "pg";

const { Client } = pg;

function parseArgs(argv) {
  const args = { migration: "supabase/migrations/20261002010000_community_post_likes_actor_guard.sql" };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === "--db-url-file") args.dbUrlFile = argv[++index];
    else if (value === "--tls-ca-file") args.tlsCaFile = argv[++index];
    else if (value === "--migration") args.migration = argv[++index];
    else throw new Error("probe_unknown_argument");
  }
  if (!args.dbUrlFile || !args.tlsCaFile) throw new Error("probe_private_input_required");
  return args;
}

function unwrapMigration(source) {
  const withoutBegin = source.replace(/^\s*begin;\s*/i, "");
  const withoutCommit = withoutBegin.replace(/\s*commit;\s*$/i, "");
  if (withoutBegin === source || withoutCommit === withoutBegin) throw new Error("probe_migration_transaction_wrapper_invalid");
  return withoutCommit;
}

async function snapshot(client) {
  const [table, policies, grants, triggers] = await Promise.all([
    client.query(`
      select c.relrowsecurity as rls_enabled
        from pg_class c join pg_namespace n on n.oid = c.relnamespace
       where n.nspname = 'public' and c.relname = 'community_post_likes'
    `),
    client.query(`
      select policyname, roles::text, cmd, qual, with_check
        from pg_catalog.pg_policies
       where schemaname = 'public' and tablename = 'community_post_likes'
       order by policyname
    `),
    client.query(`
      select grantee, privilege_type
        from information_schema.role_table_grants
       where table_schema = 'public' and table_name = 'community_post_likes'
         and grantee in ('PUBLIC', 'anon', 'authenticated')
       order by grantee, privilege_type
    `),
    client.query(`
      select count(*)::int as count
        from pg_trigger t join pg_class c on c.oid = t.tgrelid
        join pg_namespace n on n.oid = c.relnamespace
       where n.nspname = 'public' and c.relname = 'community_post_likes' and not t.tgisinternal
    `),
  ]);
  return {
    rlsEnabled: table.rows[0]?.rls_enabled === true,
    policies: policies.rows,
    grants: grants.rows,
    triggerCount: triggers.rows[0]?.count,
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
  for (const row of value.policies.filter(({ cmd }) => cmd !== "SELECT")) {
    const expression = String(row.qual ?? row.with_check ?? "").replace(/\s+/g, " ");
    if (row.roles !== "{authenticated}" || !expression.includes("quata_chat_auth_profile_id()") ||
        !expression.includes("profile_id =") || !expression.includes("IS NOT NULL")) {
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
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [connectionString, ca, migration] = await Promise.all([
    readFile(args.dbUrlFile, "utf8").then((value) => value.trim()),
    readFile(args.tlsCaFile, "utf8"),
    readFile(args.migration, "utf8").then(unwrapMigration),
  ]);
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
  try {
    await client.connect();
    assertBaseline(await snapshot(client));
    await client.query("begin");
    transactionOpen = true;
    await client.query("set local lock_timeout = '5s'");
    await client.query("set local statement_timeout = '20s'");
    await client.query(migration);
    assertForward(await snapshot(client));
    await client.query("rollback");
    transactionOpen = false;
    assertBaseline(await snapshot(client));
    process.stdout.write("COMMUNITY_POST_LIKES_TRANSACTIONAL_PROBE_PASS\n");
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
