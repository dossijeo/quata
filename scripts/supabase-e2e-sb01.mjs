#!/usr/bin/env node
/** SB-01: read-only Supabase contract catalogue. */
import { createRequire } from "node:module";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { buildSb01TlsConnection, loadSb01CertificateAuthority } from "./supabase-e2e-sb01-tls.mjs";

const require = createRequire(import.meta.url);
let Client;
try { ({ Client } = require("pg")); } catch {
  console.error("SB-01 requires the pg package. Run scripts/run-supabase-e2e-sb01.ps1.");
  process.exitCode = 2;
  process.exit();
}

const TABLE_CONTRACTS = [
  "chat_attachments", "chat_messages", "chat_participants", "chat_threads",
  "community_comments", "community_emergency_contacts", "community_post_likes",
  "community_posts", "community_profiles", "community_walls_stats",
  "conversation_user_state", "official_post_comments", "official_post_likes", "official_posts",
];
const RPC_CONTRACTS = [
  "quata_chat_get_inbox", "quata_chat_get_or_create_private_thread", "quata_chat_get_thread",
  "quata_chat_mark_thread_read", "quata_chat_register_attachment",
  "quata_chat_search_conversation_candidates", "quata_chat_send_message",
  "quata_chat_set_muted", "quata_chat_start_thread",
];
const BUCKET_CONTRACTS = ["chat-attachments"];
const BLOCKED_SQL = /\b(?:alter|analyze|call|copy|create|delete|drop|grant|insert|listen|lock|notify|reassign|refresh|reindex|revoke|select\s+.*\bfor\s+(?:share|update)|truncate|update|vacuum)\b/i;

function parseArgs(argv) {
  if (argv.length === 0) return { output: null };
  if (argv.length === 2 && argv[0] === "--out" && argv[1].trim()) return { output: argv[1] };
  if (argv.length === 1 && argv[0] === "--help") {
    console.log("Usage: node scripts/supabase-e2e-sb01.mjs [--out <safe-local-report.json>]");
    process.exit(0);
  }
  throw new Error("invalid_arguments");
}

function query(sql, values = []) {
  if (BLOCKED_SQL.test(sql)) throw new Error("blocked_sql");
  return { text: sql, values };
}

function safeFailure(error) {
  // Driver messages can embed endpoint/user information. Do not print them.
  const code = typeof error?.code === "string" ? error.code : "unknown";
  return { status: "failed", error: `database_catalogue_query_failed:${code}` };
}

function expected(found, contracts) {
  const present = new Set(found.map((item) => item.name));
  return { present: contracts.filter((name) => present.has(name)), missing: contracts.filter((name) => !present.has(name)) };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const startedAt = new Date().toISOString();
  let client;
  try {
    const connectionString = process.env.SUPABASE_DB_URL;
    if (!connectionString) throw new Error("missing_database_url");
    const certificateAuthority = await loadSb01CertificateAuthority();
    const tlsConnection = buildSb01TlsConnection(connectionString, certificateAuthority);
    client = new Client({
      ...tlsConnection, application_name: "quata-sb01-readonly", connectionTimeoutMillis: 15_000, query_timeout: 15_000,
      // PostgreSQL enforces this before the first statement; BEGIN READ ONLY is a second guard.
      options: "-c default_transaction_read_only=on -c statement_timeout=15000",
    });
    await client.connect();
    await client.query(query("BEGIN TRANSACTION READ ONLY"));
    await client.query(query("SET LOCAL TRANSACTION READ ONLY"));
    const [version, relations, functions, buckets] = await Promise.all([
      client.query(query("SELECT current_setting('server_version') AS version")),
      client.query(query("SELECT n.nspname AS schema, c.relname AS name, c.relkind AS kind, c.relrowsecurity AS rls_enabled FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm') ORDER BY c.relname")),
      client.query(query("SELECT p.proname AS name, pg_get_function_result(p.oid) AS result_type FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public' AND p.proname = ANY($1::text[]) ORDER BY p.proname", [RPC_CONTRACTS])),
      client.query(query("SELECT id AS name, public FROM storage.buckets ORDER BY id")),
    ]);
    await client.query(query("ROLLBACK"));
    const tableResult = expected(relations.rows, TABLE_CONTRACTS);
    const rpcResult = expected(functions.rows, RPC_CONTRACTS);
    const bucketResult = expected(buckets.rows, BUCKET_CONTRACTS);
    const report = {
      check: "SB-01", startedAt, finishedAt: new Date().toISOString(), mode: "postgres_catalogue_read_only",
      database: { serverVersion: version.rows[0]?.version ?? "unknown" },
      contracts: {
        tables: { ...tableResult, discovered: relations.rows }, rpc: { ...rpcResult, discovered: functions.rows },
        buckets: { ...bucketResult, discovered: buckets.rows },
      },
      status: [tableResult, rpcResult, bucketResult].every((result) => result.missing.length === 0) ? "passed" : "contract_mismatch",
      mutationPolicy: "No DDL, DML, RPC invocation, or user-data query was executed.",
    };
    const serialized = `${JSON.stringify(report, null, 2)}\n`;
    if (args.output) {
      const output = resolve(args.output);
      await mkdir(dirname(output), { recursive: true });
      await writeFile(output, serialized, { encoding: "utf8", mode: 0o600 });
      console.log(`SB-01 report written: ${output}`);
    } else console.log(serialized);
    process.exitCode = report.status === "passed" ? 0 : 1;
  } catch (error) {
    console.error(JSON.stringify({ check: "SB-01", startedAt, finishedAt: new Date().toISOString(), ...safeFailure(error) }));
    process.exitCode = 1;
  } finally { await client?.end().catch(() => undefined); }
}

main();
