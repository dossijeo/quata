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
const targetForAction = { "apply-001": "20260726171001", "apply-002": "20260726171002", "rollback-001": "20260726171001", "rollback-002": "20260726171002" };

function usage(message) {
  if (message) console.error(message);
  console.error("Usage: node scripts/security-release-serial-executor.mjs --action dry-run|apply-001|apply-002|rollback-001|rollback-002 [--expected-precondition-sha256 SHA256] [--gate-evidence FILE ...] [--out FILE]");
  process.exitCode = 2;
}

export function sha256(value) { return createHash("sha256").update(value).digest("hex"); }

export function validateAllowlist(allowlist, version, source, rollback = false) {
  const entry = allowlist.migrations?.[version];
  if (!entry) throw new Error(`serial_release_allowlist_missing:${version}`);
  const actual = sha256(source); const expected = rollback ? entry.rollbackSha256 : entry.sha256;
  if (actual !== expected) throw new Error(`serial_release_${rollback ? "rollback" : "migration"}_hash_mismatch:${version}`);
  return entry;
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (!key.startsWith("--") || i + 1 >= argv.length) throw new Error("invalid_arguments");
    args[key.slice(2)] = argv[++i];
  }
  if (!args.action || !["dry-run", "apply-001", "apply-002", "rollback-001", "rollback-002"].includes(args.action)) throw new Error("invalid_action");
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
  const normalizedUsername = decodeURIComponent(url.username).trim().toLowerCase();
  if (!normalizedUsername) throw new Error("serial_release_database_username_missing");
  const targetIdentity = {
    host: url.hostname.toLowerCase(),
    port: url.port || "5432",
    database: decodeURIComponent(url.pathname.replace(/^\//, "")),
    username: normalizedUsername,
    projectRefHint: normalizedUsername.includes(".") ? normalizedUsername.split(".").at(-1) : null
  };
  // pg-connection-string lets URL SSL parameters replace the explicit TLS
  // object. Remove them and enforce the separately supplied CA/hostname.
  for (const option of ["sslmode", "uselibpqcompat", "sslcert", "sslkey", "sslrootcert"]) url.searchParams.delete(option);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true, servername: url.hostname }, application_name: "quata-security-release-serial", targetIdentity };
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
    : { table: "official_post_likes", funcs: ["public.quata_guard_official_post_likes()", "public.quata_current_profile_id()", "public.quata_current_profile_is_admin()", "public.quata_current_role_is_service()"] };
  const { rows } = await client.query(`
    select jsonb_build_object(
      'table', jsonb_build_object('rls', c.relrowsecurity, 'forceRls', c.relforcerowsecurity, 'acl', coalesce(c.relacl::text, '')),
      'policies', coalesce((select jsonb_agg(jsonb_build_object('name', p.policyname, 'cmd', p.cmd, 'roles', p.roles, 'qual', p.qual, 'check', p.with_check) order by p.policyname) from pg_policies p where p.schemaname = 'public' and p.tablename = $1), '[]'::jsonb),
      'triggers', coalesce((select jsonb_agg(pg_get_triggerdef(t.oid, true) order by t.tgname) from pg_trigger t where t.tgrelid = c.oid and not t.tgisinternal), '[]'::jsonb),
      'functions', coalesce((select jsonb_agg(jsonb_build_object('identity', p.oid::regprocedure::text, 'def', pg_get_functiondef(p.oid), 'acl', coalesce(p.proacl::text, ''), 'definer', p.prosecdef) order by p.oid::regprocedure::text) from pg_proc p where p.oid = any($2::regprocedure[])), '[]'::jsonb)
    ) as fingerprint_source
    from pg_class c where c.oid = ('public.' || $1)::regclass`, [settings.table, settings.funcs]);
  if (rows.length !== 1) throw new Error(`serial_release_precondition_table_missing:${settings.table}`);
  const source = JSON.stringify(rows[0].fingerprint_source);
  return { sha256: sha256(source), source };
}

async function assertEffectiveReleaseState(client, version, rollback) {
  const table = version === "20260726171001" ? "community_comments" : "official_post_likes";
  const { rows } = await client.query(`
    select
      c.relrowsecurity as rls,
      c.relforcerowsecurity as force_rls,
      coalesce((select jsonb_agg(p.policyname order by p.policyname)
        from pg_policies p where p.schemaname = 'public' and p.tablename = $1), '[]'::jsonb) as policies,
      has_table_privilege('anon', c.oid, 'SELECT') as anon_select,
      has_table_privilege('anon', c.oid, 'INSERT') as anon_insert,
      has_table_privilege('anon', c.oid, 'DELETE') as anon_delete,
      has_table_privilege('anon', c.oid, 'UPDATE') as anon_update,
      has_table_privilege('authenticated', c.oid, 'SELECT') as auth_select,
      has_table_privilege('authenticated', c.oid, 'INSERT') as auth_insert,
      has_table_privilege('authenticated', c.oid, 'DELETE') as auth_delete,
      has_table_privilege('authenticated', c.oid, 'UPDATE') as auth_update
    from pg_class c where c.oid = ('public.' || $1)::regclass`, [table]);
  if (rows.length !== 1) throw new Error(`serial_release_postcondition_table_missing:${table}`);
  const state = rows[0];
  const policyNames = state.policies;
  const expectedPolicies = version === "20260726171001"
    ? (rollback
      ? ["public delete comments", "public insert comments", "public update comments"]
      : ["authenticated delete own or admin comments", "authenticated insert own comments"])
    : (rollback
      ? []
      : ["official_post_likes_authenticated_delete_own_or_admin", "official_post_likes_authenticated_insert_own", "official_post_likes_public_read"]);
  if (JSON.stringify(policyNames) !== JSON.stringify(expectedPolicies)) throw new Error(`serial_release_postcondition_policy_mismatch:${version}`);
  if (version === "20260726171001") {
    const grantsOk = rollback
      ? state.anon_insert && state.anon_delete && state.anon_update && state.auth_insert && state.auth_delete && state.auth_update
      : !state.anon_insert && !state.anon_delete && !state.anon_update && state.auth_insert && state.auth_delete && !state.auth_update;
    if (!state.rls || state.force_rls || !grantsOk) throw new Error(`serial_release_postcondition_rls_or_grant_mismatch:${version}`);
  } else {
    const expectedRls = !rollback;
    const grantsOk = rollback
      ? state.anon_select && !state.anon_insert && !state.anon_delete && state.auth_select && state.auth_insert && state.auth_delete
      : state.anon_select && !state.anon_insert && !state.anon_delete && state.auth_select && state.auth_insert && state.auth_delete;
    if (state.rls !== expectedRls || state.force_rls || !grantsOk) throw new Error(`serial_release_postcondition_rls_or_grant_mismatch:${version}`);
    const { rows: functions } = await client.query(`
      with ids as (
        select to_regprocedure('public.quata_official_like_delete_allowed(uuid)') as helper
      )
      select
        (select not p.prosecdef from pg_proc p where p.oid = 'public.quata_guard_official_post_likes()'::regprocedure) as guard_invoker,
        (select md5(pg_get_functiondef(p.oid)) from pg_proc p where p.oid = 'public.quata_guard_official_post_likes()'::regprocedure) as guard_definition,
        (select md5(coalesce(p.proacl::text, '')) from pg_proc p where p.oid = 'public.quata_guard_official_post_likes()'::regprocedure) as guard_acl,
        (select pg_get_userbyid(p.proowner) from pg_proc p where p.oid = 'public.quata_guard_official_post_likes()'::regprocedure) as guard_owner,
        ids.helper is not null as helper_exists,
        case when ids.helper is null then null
          else (select md5(pg_get_functiondef(p.oid)) from pg_proc p where p.oid = ids.helper)
        end as helper_definition,
        case when ids.helper is null then null
          else (select md5(coalesce(p.proacl::text, '')) from pg_proc p where p.oid = ids.helper)
        end as helper_acl,
        case when ids.helper is null then null
          else (select pg_get_userbyid(p.proowner) from pg_proc p where p.oid = ids.helper)
        end as helper_owner,
        case when ids.helper is null then false
          else (select p.prosecdef
                and has_function_privilege('authenticated', p.oid, 'EXECUTE')
                and not has_function_privilege('anon', p.oid, 'EXECUTE')
                and not has_function_privilege('public', p.oid, 'EXECUTE')
                from pg_proc p where p.oid = ids.helper)
        end as helper_acl_ok,
        exists(select 1 from pg_trigger t
          where t.tgrelid = 'public.official_post_likes'::regclass
            and t.tgname = 'quata_guard_official_post_likes_trg'
            and t.tgfoid = 'public.quata_guard_official_post_likes()'::regprocedure
            and not t.tgisinternal) as trigger_ok
      from ids`);
    const f = functions[0];
    const functionStateOk = rollback
      ? !f.guard_invoker
        && f.guard_definition === "11bea734f04319ea619ebdf3dbdad869"
        && f.guard_acl === "d41d8cd98f00b204e9800998ecf8427e"
        && f.guard_owner === "postgres"
        && !f.helper_exists
        && f.trigger_ok
      : f.guard_invoker
        && f.guard_definition === "c9505e6d5b5fbb818c465cf84a3ebf56"
        && f.guard_acl === "d41d8cd98f00b204e9800998ecf8427e"
        && f.guard_owner === "postgres"
        && f.helper_exists
        && f.helper_definition === "139c75e8a54504468e1861557a681264"
        && f.helper_acl === "5fc13192159b7c60c3a808895ae2c2c8"
        && f.helper_owner === "postgres"
        && f.helper_acl_ok
        && f.trigger_ok;
    if (!functionStateOk) throw new Error(`serial_release_postcondition_function_or_trigger_mismatch:${version}`);
  }
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

async function lockReleaseObjects(client, version, rollback) {
  const table = version === "20260726171001" ? "community_comments" : "official_post_likes";
  await client.query("lock table supabase_migrations.schema_migrations in share row exclusive mode");
  await client.query(`lock table public.${table} in share row exclusive mode`);
  const functions = version === "20260726171001"
    ? ["public.quata_chat_auth_profile_id()", "public.quata_current_profile_is_admin()"]
    : ["public.quata_guard_official_post_likes()", "public.quata_current_profile_id()", "public.quata_current_profile_is_admin()", "public.quata_current_role_is_service()",
      ...(rollback ? ["public.quata_official_like_delete_allowed(uuid)"] : [])];
  // PostgreSQL has no LOCK FUNCTION command. Row locks on the exact pg_proc
  // tuples conflict with ALTER/DROP/CREATE OR REPLACE without blocking
  // unrelated function DDL. A helper created by apply-002 is protected by its
  // uncommitted catalog insert until this transaction commits.
  await client.query("savepoint function_row_lock_probe");
  try {
      if (process.env.QUATA_SERIAL_EXECUTOR_TEST_FORCE_FUNCTION_DDL_LOCK === "1") {
        await client.query("do $$ begin raise insufficient_privilege; end $$");
      }
      const lockedFunctions = await client.query(
        `select p.oid
           from unnest($1::text[]) f(identity)
           join pg_catalog.pg_proc p on p.oid = to_regprocedure(f.identity)
           for share of p`,
        [functions]);
      if (lockedFunctions.rowCount !== functions.length) throw new Error(`serial_release_function_lock_missing:${version}`);
      await client.query("release savepoint function_row_lock_probe");
      return "pg_proc_row_share";
  } catch (error) {
    if (error.code !== "42501") throw error;
    await client.query("rollback to savepoint function_row_lock_probe");
    await client.query("release savepoint function_row_lock_probe");
  }
  // Hosted Supabase roles may own these functions while being denied row locks
  // on system catalogs. Force a real function-catalog tuple update and restore
  // the exact COST in the same transaction. The tuple lock survives until
  // commit and is non-cooperative with ALTER/DROP/CREATE OR REPLACE.
  const { rows: ownedFunctions } = await client.query(`
    select f.identity, p.procost::float8 as cost, pg_get_userbyid(p.proowner) as owner, current_user as actor
      from unnest($1::text[]) f(identity)
      join pg_catalog.pg_proc p on p.oid = to_regprocedure(f.identity)
     order by f.identity`, [functions]);
  if (ownedFunctions.length !== functions.length) throw new Error(`serial_release_function_lock_missing:${version}`);
  for (const fn of ownedFunctions) {
    if (fn.owner !== fn.actor) throw new Error(`serial_release_function_lock_owner_mismatch:${version}`);
    const originalCost = Number(fn.cost);
    if (!Number.isFinite(originalCost) || originalCost <= 0) throw new Error(`serial_release_function_lock_cost_invalid:${version}`);
    const alternateCost = originalCost === 1 ? 2 : originalCost + 1;
    const identity = functions.find((candidate) => candidate === fn.identity || candidate.replace(/^public\./, "") === fn.identity);
    if (!identity) throw new Error(`serial_release_function_lock_identity_mismatch:${version}`);
    await client.query(`alter function ${identity} cost ${alternateCost}`);
    await client.query(`alter function ${identity} cost ${originalCost}`);
  }
  return "function_cost_roundtrip";
}

function exactLedgerRow(row, source, entry) {
  return !!row && row.name === entry.name && Array.isArray(row.statements)
    && row.statements.length === 1 && row.statements[0] === source;
}

function isSha256(value) { return typeof value === "string" && /^[a-f0-9]{64}$/.test(value); }

async function readGateEvidence(path, expectedHash, expectedCommit, expectedSnapshot, expectedProject, actualProject) {
  if (!path) throw new Error("serial_release_gate_evidence_required_for_002");
  if (!isSha256(expectedHash) || !/^[a-f0-9]{40}$/i.test(expectedCommit ?? "") || !isSha256(expectedSnapshot) || !isSha256(expectedProject)) {
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
      || value?.databaseProjectFingerprint !== expectedProject || value?.databaseProjectFingerprint !== actualProject || !isSha256(value?.postflight?.sha256)
      || !Number.isFinite(Date.parse(value?.generatedAt ?? "")) || !Number.isFinite(Date.parse(value?.expiresAt ?? "")) || Date.parse(value.expiresAt) <= Date.now()
      || value?.postflight?.status !== "passed" || !reports || requiredReports.some((name) => reports[name]?.status !== "passed" || !isSha256(reports[name]?.sha256) || reports[name]?.databaseProjectFingerprint !== value.databaseProjectFingerprint)) {
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
  const rollback = args.action.startsWith("rollback-");
  const versions = args.action === "dry-run" ? ["20260726171001", "20260726171002"] : [targetForAction[args.action]];
  const sources = {};
  for (const version of versions) {
    const entry = allowlist.migrations[version];
    const source = await readFile(resolve(root, rollback ? "supabase/rollbacks" : "supabase/migrations", rollback ? entry.rollbackFile : entry.file), "utf8");
    validateAllowlist(allowlist, version, source, rollback);
    const migrationSource = await readFile(resolve(root, "supabase/migrations", entry.file), "utf8");
    validateAllowlist(allowlist, version, migrationSource);
    sources[version] = { entry, source, migrationSource, body: unwrapOuterTransaction(source, version) };
  }
  const approved001 = allowlist.migrations["20260726171001"];
  const approved001Source = await readFile(resolve(root, "supabase/migrations", approved001.file), "utf8");
  validateAllowlist(allowlist, "20260726171001", approved001Source);
  if (args.action !== "dry-run" && !/^[a-f0-9]{64}$/.test(args["expected-precondition-sha256"] ?? "")) throw new Error("serial_release_expected_precondition_sha256_required");

  const config = await databaseConfig();
  const client = new pg.Client(config);
  const report = { check: "QUATA-SECURITY-RELEASE-SERIAL", action: args.action, status: "failed", releaseLock, databaseProjectFingerprint: null, migrations: [] };
  try {
    await client.connect();
    let connected;
    try {
      connected = (await client.query(`
        select current_database() as database,
               current_user as role,
               d.oid::text as database_oid,
               pcs.system_identifier::text as system_identifier
          from pg_database d
          cross join pg_control_system() pcs
         where d.datname = current_database()`)).rows[0];
    } catch {
      throw new Error("serial_release_database_identity_unavailable");
    }
    if (connected.database !== config.targetIdentity.database) throw new Error("serial_release_connected_database_mismatch");
    report.databaseProjectFingerprint = sha256(JSON.stringify({
      ...config.targetIdentity,
      connectedDatabase: connected.database,
      connectedRole: connected.role,
      databaseOid: connected.database_oid,
      systemIdentifier: connected.system_identifier
    }));
    if (args.action === "apply-002") {
      await readGateEvidence(args["gate-evidence"], args["expected-gate-evidence-sha256"], args["expected-release-commit"], args["expected-snapshot-fingerprint"], args["expected-database-project-fingerprint"], report.databaseProjectFingerprint);
    }
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
    if (!rollback && present.has(version)) throw new Error(`serial_release_duplicate_ledger_version:${version}`);
    if (!rollback && version === "20260726171001" && present.has("20260726171002")) throw new Error("serial_release_order_violation_002_exists_before_001");
    if (!rollback && version === "20260726171002" && !exactLedgerRow(present.get("20260726171001"), approved001Source, approved001)) throw new Error("serial_release_order_or_001_ledger_drift");
    if (rollback && !exactLedgerRow(present.get(version), sources[version].migrationSource, sources[version].entry)) throw new Error(`serial_release_rollback_ledger_drift_or_missing:${version}`);
    const expected = args["expected-precondition-sha256"];
    if (report.migrations[0].preconditionSha256 !== expected) throw new Error("serial_release_precondition_fingerprint_mismatch");
    const hold = Number(process.env.QUATA_SERIAL_EXECUTOR_TEST_HOLD_LOCK_MS ?? 0);
    if (Number.isSafeInteger(hold) && hold > 0) await new Promise((resolveHold) => setTimeout(resolveHold, hold));
    await client.query("begin isolation level serializable");
    try {
      report.migrations[0].functionLockMode = await lockReleaseObjects(client, version, rollback);
      // Re-read after table+ledger locks: the externally observed dry-run
      // fingerprint is not trusted until this point immediately before DDL.
      const lockedRows = await ledgerRows(client); const locked = new Map(lockedRows.map((row) => [row.version, row]));
      const lockedFingerprint = await catalogFingerprint(client, version);
      if (lockedFingerprint.sha256 !== expected) throw new Error("serial_release_precondition_fingerprint_changed_after_lock");
      if ((!rollback && locked.has(version)) || (rollback && !exactLedgerRow(locked.get(version), sources[version].migrationSource, sources[version].entry))) throw new Error("serial_release_ledger_changed_after_lock");
      if (!rollback && version === "20260726171002" && !exactLedgerRow(locked.get("20260726171001"), approved001Source, approved001)) throw new Error("serial_release_001_ledger_changed_after_lock");
      const holdAfterLock = Number(process.env.QUATA_SERIAL_EXECUTOR_TEST_HOLD_AFTER_LOCK_MS ?? 0);
      if (Number.isSafeInteger(holdAfterLock) && holdAfterLock > 0) await new Promise((resolveHold) => setTimeout(resolveHold, holdAfterLock));
      await client.query(sources[version].body);
      // Validate the effective catalog, grants and function/trigger binding
      // independently from the migration's own SQL before the transaction can commit.
      await assertEffectiveReleaseState(client, version, rollback);
      if (process.env.QUATA_SERIAL_EXECUTOR_TEST_FAIL_BEFORE_LEDGER === "1") throw new Error("serial_release_test_fail_before_ledger");
      if (!rollback) await client.query("insert into supabase_migrations.schema_migrations(version, statements, name) values ($1, $2::text[], $3)", [version, [sources[version].source], sources[version].entry.name]);
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
    report.status = "passed";
    report.migrations[0].ledger = rollback ? "preserved" : "inserted";
    return report;
  } finally {
    await client.end().catch(() => {});
    await writeReport(args.out, report);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === thisFile) {
  run().then((report) => console.log(JSON.stringify(report))).catch((error) => { console.error(error.message); process.exitCode = 1; });
}
