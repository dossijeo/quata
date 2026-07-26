#!/usr/bin/env node
/*
 * Applies only the two reviewed RLS migrations without invoking Supabase CLI.
 * The database URL is deliberately read only from process environment; do not
 * add a URL command-line option (argv is commonly persisted by CI shells).
 */
import { createHash } from "node:crypto";
import { readFile, mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve, relative, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import process from "node:process";
const pg = createRequire(import.meta.url)("pg");

const thisFile = fileURLToPath(import.meta.url);
const root = resolve(dirname(thisFile), "..");
const allowlistPath = resolve(root, "scripts/security-release-serial-allowlist.json");
const releaseLock = "quata/security-release/001-002/v1";
const targetForAction = { "apply-001": "20260726171001", "apply-002": "20260726171002" };

function usage(message) {
  if (message) console.error(message);
  console.error("Usage: node scripts/security-release-serial-executor.mjs --action dry-run|apply-001|apply-002 [--expected-precondition-sha256 SHA256] [--gate-evidence FILE --expected-gate-evidence-sha256 SHA256 --expected-release-commit SHA --expected-snapshot-fingerprint SHA] [--out FILE]");
  process.exitCode = 2;
}

export function sha256(value) { return createHash("sha256").update(value).digest("hex"); }

export function validateAllowlist(allowlist, version, source) {
  const entry = allowlist.migrations?.[version];
  if (!entry) throw new Error(`serial_release_allowlist_missing:${version}`);
  const actual = sha256(source);
  if (actual !== entry.sha256) throw new Error(`serial_release_migration_hash_mismatch:${version}`);
  return entry;
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (!key.startsWith("--") || i + 1 >= argv.length) throw new Error("invalid_arguments");
    args[key.slice(2)] = argv[++i];
  }
  if (!args.action || !["dry-run", "apply-001", "apply-002"].includes(args.action)) throw new Error("invalid_action");
  return args;
}

async function databaseConfig() {
  const connectionString = process.env.SUPABASE_DB_URL;
  const caFile = process.env.SUPABASE_DB_TLS_CA_FILE;
  if (!connectionString) throw new Error("serial_release_database_url_not_configured");
  if (!caFile) throw new Error("serial_release_tls_ca_file_not_configured");
  let url;
  try { url = new URL(connectionString); } catch { throw new Error("serial_release_database_url_invalid"); }
  if (!["postgres:", "postgresql:"].includes(url.protocol)) throw new Error("serial_release_database_url_scheme_invalid");
  const ca = await readFile(caFile, "utf8");
  if (!ca.trim()) throw new Error("serial_release_tls_ca_empty");
  return { connectionString, ssl: { ca, rejectUnauthorized: true, servername: url.hostname }, application_name: "quata-security-release-serial" };
}

function unwrapOuterTransaction(sql, version) {
  // These two reviewed files own one outer BEGIN/COMMIT.  Removing only that
  // wrapper lets the executor commit DDL and the ledger row atomically.
  const begin = /^((?:\s*--[^\n]*(?:\n|$))*\s*)begin\s*;\s*/i;
  const end = /\s*commit\s*;\s*$/i;
  const withoutBegin = sql.replace(begin, "$1");
  if (withoutBegin === sql || !end.test(withoutBegin)) throw new Error(`serial_release_transaction_wrapper_invalid:${version}`);
  const body = withoutBegin.replace(end, "\n");
  // A transaction command inside a PL/pgSQL dollar-quoted function is data,
  // not a SQL transaction command. Remove quoted/comment text before refusing
  // any executable nested transaction control statement.
  const executable = body
    .replace(/\$[A-Za-z_0-9]*\$[\s\S]*?\$[A-Za-z_0-9]*\$/g, "")
    .replace(/'(?:''|[^'])*'/g, "")
    .replace(/--[^\n]*/g, "");
  if (/\b(?:begin|commit|rollback|start\s+transaction)\b/i.test(executable)) {
    throw new Error(`serial_release_nested_transaction_refused:${version}`);
  }
  return body;
}

async function catalogFingerprint(client, version) {
  const settings = version === "20260726171001"
    ? { table: "community_comments", funcs: ["public.quata_chat_auth_profile_id()", "public.quata_current_profile_is_admin()"] }
    : { table: "official_post_likes", funcs: ["public.quata_guard_official_post_likes()", "public.quata_current_profile_id()", "public.quata_current_profile_is_admin()"] };
  const { rows } = await client.query(`
    select jsonb_build_object(
      'table', jsonb_build_object('rls', c.relrowsecurity, 'forceRls', c.relforcerowsecurity, 'acl', coalesce(c.relacl::text, '')),
      'policies', coalesce((select jsonb_agg(jsonb_build_object('name', p.policyname, 'cmd', p.cmd, 'roles', p.roles, 'qual', p.qual, 'check', p.with_check) order by p.policyname) from pg_policies p where p.schemaname = 'public' and p.tablename = $1), '[]'::jsonb),
      'triggers', coalesce((select jsonb_agg(pg_get_triggerdef(t.oid, true) order by t.tgname) from pg_trigger t where t.tgrelid = c.oid and not t.tgisinternal), '[]'::jsonb),
      'functions', coalesce((select jsonb_agg(jsonb_build_object('identity', p.oid::regprocedure::text, 'def', pg_get_functiondef(p.oid), 'acl', coalesce(p.proacl::text, ''), 'definer', p.prosecdef) order by p.oid::regprocedure::text) from pg_proc p where p.oid::regprocedure::text = any($2::text[])), '[]'::jsonb)
    ) as fingerprint_source
    from pg_class c where c.oid = ('public.' || $1)::regclass`, [settings.table, settings.funcs]);
  if (rows.length !== 1) throw new Error(`serial_release_precondition_table_missing:${settings.table}`);
  const source = JSON.stringify(rows[0].fingerprint_source);
  return { sha256: sha256(source), source };
}

async function assertLedgerShape(client) {
  const { rows } = await client.query(`select a.attname, format_type(a.atttypid, a.atttypmod) type
    from pg_attribute a where a.attrelid = 'supabase_migrations.schema_migrations'::regclass and a.attnum > 0 and not a.attisdropped order by a.attnum`);
  const expected = "version:text,statements:text[],name:text";
  const actual = rows.map((r) => `${r.attname}:${r.type}`).join(",");
  if (actual !== expected) throw new Error(`serial_release_ledger_shape_mismatch:${actual}`);
}

async function ledgerRows(client) {
  await assertLedgerShape(client);
  return (await client.query("select version::text, name, statements from supabase_migrations.schema_migrations where version = any($1::text[]) order by version", [["20260726171001", "20260726171002"]])).rows;
}

function isSha256(value) { return typeof value === "string" && /^[a-f0-9]{64}$/.test(value); }

async function readGateEvidence(path, expectedHash, expectedCommit, expectedSnapshot) {
  if (!path) throw new Error("serial_release_gate_evidence_required_for_002");
  if (!isSha256(expectedHash) || !/^[a-f0-9]{40}$/i.test(expectedCommit ?? "") || !isSha256(expectedSnapshot)) {
    throw new Error("serial_release_gate_evidence_anchor_required_for_002");
  }
  const source = await readFile(resolve(path), "utf8");
  if (sha256(source) !== expectedHash) throw new Error("serial_release_gate_evidence_hash_mismatch");
  const value = JSON.parse(source);
  const reports = value?.reports;
  const requiredReports = ["dbReleaseSafety", "backendCompatibility", "sb07"];
  if (value?.schemaVersion !== 1 || value?.migration !== "20260726171001" || value?.status !== "passed"
      || value?.releaseCommit !== expectedCommit || value?.snapshotFingerprint !== expectedSnapshot
      || !isSha256(value?.preconditionSha256)
      || !reports || requiredReports.some((name) => reports[name]?.status !== "passed" || !isSha256(reports[name]?.sha256))) {
    throw new Error("serial_release_gate_evidence_invalid");
  }
  return value;
}

async function writeReport(file, report) {
  if (!file) return;
  const destination = resolve(file);
  const allowed = resolve(root, "build-reports");
  const relation = relative(allowed, destination);
  if (!relation || relation.startsWith("..") || isAbsolute(relation)) throw new Error("serial_release_output_must_be_under_build_reports");
  await mkdir(dirname(destination), { recursive: true });
  await writeFile(destination, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

export async function run(argv = process.argv.slice(2)) {
  const args = parseArgs(argv);
  const allowlist = JSON.parse(await readFile(allowlistPath, "utf8"));
  const versions = args.action === "dry-run" ? ["20260726171001", "20260726171002"] : [targetForAction[args.action]];
  const sources = {};
  for (const version of versions) {
    const entry = allowlist.migrations[version];
    const source = await readFile(resolve(root, "supabase/migrations", entry.file), "utf8");
    validateAllowlist(allowlist, version, source);
    sources[version] = { entry, source, body: unwrapOuterTransaction(source, version) };
  }
  if (args.action === "apply-002") {
    await readGateEvidence(args["gate-evidence"], args["expected-gate-evidence-sha256"], args["expected-release-commit"], args["expected-snapshot-fingerprint"]);
  }
  if (args.action !== "dry-run" && !/^[a-f0-9]{64}$/.test(args["expected-precondition-sha256"] ?? "")) throw new Error("serial_release_expected_precondition_sha256_required");

  const client = new pg.Client(await databaseConfig());
  const report = { check: "QUATA-SECURITY-RELEASE-SERIAL", action: args.action, status: "failed", releaseLock, migrations: [] };
  try {
    await client.connect();
    const lock = await client.query("select pg_try_advisory_lock(hashtextextended($1, 0)) acquired", [releaseLock]);
    if (!lock.rows[0].acquired) throw new Error("serial_release_lock_unavailable");
    const existing = await ledgerRows(client);
    const present = new Map(existing.map((row) => [row.version, row]));
    for (const version of versions) {
      const fp = await catalogFingerprint(client, version);
      report.migrations.push({ version, name: sources[version].entry.name, sha256: sources[version].entry.sha256, preconditionSha256: fp.sha256, ledger: present.has(version) ? "present" : "absent" });
    }
    if (args.action === "dry-run") { report.status = "passed"; return report; }

    const version = versions[0];
    if (present.has(version)) throw new Error(`serial_release_duplicate_ledger_version:${version}`);
    if (version === "20260726171001" && present.has("20260726171002")) throw new Error("serial_release_order_violation_002_exists_before_001");
    if (version === "20260726171002" && !present.has("20260726171001")) throw new Error("serial_release_order_violation_001_missing");
    const expected = args["expected-precondition-sha256"];
    if (report.migrations[0].preconditionSha256 !== expected) throw new Error("serial_release_precondition_fingerprint_mismatch");
    const hold = Number(process.env.QUATA_SERIAL_EXECUTOR_TEST_HOLD_LOCK_MS ?? 0);
    if (Number.isSafeInteger(hold) && hold > 0) await new Promise((resolveHold) => setTimeout(resolveHold, hold));
    await client.query("begin isolation level serializable");
    try {
      await client.query(sources[version].body);
      if (process.env.QUATA_SERIAL_EXECUTOR_TEST_FAIL_BEFORE_LEDGER === "1") throw new Error("serial_release_test_fail_before_ledger");
      await client.query("insert into supabase_migrations.schema_migrations(version, statements, name) values ($1, $2::text[], $3)", [version, [sources[version].source], sources[version].entry.name]);
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
    report.status = "passed";
    report.migrations[0].ledger = "inserted";
    return report;
  } finally {
    await client.end().catch(() => {});
    await writeReport(args.out, report);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === thisFile) {
  run().then((report) => console.log(JSON.stringify(report))).catch((error) => { console.error(error.message); process.exitCode = 1; });
}
