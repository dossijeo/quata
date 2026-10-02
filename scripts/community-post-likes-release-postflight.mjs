#!/usr/bin/env node
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import process from "node:process";
import pg from "pg";

const { Client } = pg;
const EXPECTED_VERSION = "20261002010000";
const EXPECTED_NAME = "community_post_likes_actor_guard";

function parseArgs(argv) {
  const args = {
    output: "build-reports/db-release-safety/community-post-likes-postflight.json",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === "--db-url-file") args.dbUrlFile = argv[++index];
    else if (value === "--tls-ca-file") args.tlsCaFile = argv[++index];
    else if (value === "--output") args.output = argv[++index];
    else throw new Error("postflight_unknown_argument");
  }
  if (!args.dbUrlFile || !args.tlsCaFile) throw new Error("postflight_private_input_required");
  return args;
}

const normalized = (value) => String(value ?? "").replace(/\s+/g, " ").trim();

function assertPolicies(rows) {
  const byName = new Map(rows.map((row) => [row.policyname, row]));
  const expectedNames = [
    "community_post_likes_delete_own",
    "community_post_likes_insert_own",
    "community_post_likes_public_read",
  ];
  if (rows.length !== expectedNames.length || expectedNames.some((name) => !byName.has(name))) {
    throw new Error("postflight_policy_set_mismatch");
  }
  const read = byName.get("community_post_likes_public_read");
  if (read.cmd !== "SELECT" || read.roles !== "{public}" || normalized(read.qual) !== "true" || read.with_check !== null) {
    throw new Error("postflight_public_read_policy_mismatch");
  }
  for (const [name, command, expressionField] of [
    ["community_post_likes_insert_own", "INSERT", "with_check"],
    ["community_post_likes_delete_own", "DELETE", "qual"],
  ]) {
    const row = byName.get(name);
    const expression = normalized(row?.[expressionField]);
    if (row?.cmd !== command || row?.roles !== "{authenticated}" ||
        !expression.includes("quata_chat_auth_profile_id()") ||
        !expression.includes("profile_id =") ||
        !expression.includes("IS NOT NULL")) {
      throw new Error(`postflight_${command.toLowerCase()}_policy_mismatch`);
    }
  }
}

function assertGrants(rows) {
  const grants = new Map();
  for (const row of rows) {
    if (!grants.has(row.grantee)) grants.set(row.grantee, []);
    grants.get(row.grantee).push(row.privilege_type);
  }
  for (const values of grants.values()) values.sort();
  if (JSON.stringify(grants.get("anon") ?? []) !== JSON.stringify(["SELECT"])) {
    throw new Error("postflight_anon_grants_mismatch");
  }
  if (JSON.stringify(grants.get("authenticated") ?? []) !== JSON.stringify(["DELETE", "INSERT", "SELECT"])) {
    throw new Error("postflight_authenticated_grants_mismatch");
  }
  if ((grants.get("PUBLIC") ?? []).length !== 0) throw new Error("postflight_public_table_grant_present");
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [connectionString, ca] = await Promise.all([
    readFile(args.dbUrlFile, "utf8").then((value) => value.trim()),
    readFile(args.tlsCaFile, "utf8"),
  ]);
  const connectionUrl = new URL(connectionString);
  if (connectionUrl.searchParams.get("sslmode") !== "verify-full") {
    throw new Error("postflight_database_url_requires_verify_full");
  }
  connectionUrl.searchParams.delete("sslmode");
  const client = new Client({
    connectionString: connectionUrl.toString(),
    ssl: { ca, rejectUnauthorized: true },
    connectionTimeoutMillis: 20_000,
    query_timeout: 30_000,
    application_name: "quata_community_post_likes_postflight",
  });
  try {
    await client.connect();
    await client.query("begin read only");
    const [ledger, table, policies, grants, triggers] = await Promise.all([
      client.query(
        "select version::text, coalesce(name, '') as name from supabase_migrations.schema_migrations where version = $1",
        [EXPECTED_VERSION],
      ),
      client.query(`
        select c.relrowsecurity as rls_enabled, c.relforcerowsecurity as force_rls
          from pg_class c join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public' and c.relname = 'community_post_likes'
      `),
      client.query(`
        select policyname, roles::text, cmd, permissive, qual, with_check
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
    await client.query("rollback");

    if (ledger.rowCount !== 1 || ledger.rows[0].name !== EXPECTED_NAME) throw new Error("postflight_migration_ledger_mismatch");
    if (table.rowCount !== 1 || table.rows[0].rls_enabled !== true) throw new Error("postflight_rls_not_enabled");
    assertPolicies(policies.rows);
    assertGrants(grants.rows);
    if (triggers.rows[0]?.count !== 0) throw new Error("postflight_unexpected_trigger");

    const report = {
      schemaVersion: 1,
      check: "COMMUNITY-POST-LIKES-ACTOR-GUARD",
      phase: "postflight",
      generatedAt: new Date().toISOString(),
      status: "passed",
      migration: { version: EXPECTED_VERSION, name: EXPECTED_NAME, ledgerExact: true },
      table: {
        name: "community_post_likes",
        rlsEnabled: true,
        forceRls: table.rows[0].force_rls,
        policies: policies.rows,
        grants: grants.rows,
        userTriggerCount: 0,
      },
      guarantees: {
        tls: "verify-full with one explicit CA",
        transaction: "read-only",
        businessValuesEmitted: false,
        secretsEmitted: false,
      },
    };
    report.evidenceFingerprintSha256 = createHash("sha256").update(JSON.stringify(report)).digest("hex");
    const output = resolve(args.output);
    await mkdir(dirname(output), { recursive: true });
    await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
    process.stdout.write(`${JSON.stringify({ check: report.check, phase: report.phase, status: report.status, evidenceFingerprintSha256: report.evidenceFingerprintSha256 })}\n`);
  } finally {
    await client.query("rollback").catch(() => {});
    await client.end().catch(() => {});
  }
}

main().catch((error) => {
  const code = typeof error?.message === "string" && /^postflight_[a-z0-9_]+$/.test(error.message)
    ? error.message
    : "postflight_failed_redacted";
  process.stderr.write(`${code}\n`);
  process.exitCode = 1;
});
