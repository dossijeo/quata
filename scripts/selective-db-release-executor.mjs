#!/usr/bin/env node
import { createHash } from "node:crypto";
import { createRequire } from "node:module";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import process from "node:process";
import { setTimeout as delay } from "node:timers/promises";

const require = createRequire(import.meta.url);
const { Client } = require("pg");
const root = resolve(import.meta.dirname, "..");
const allowedPackagesRoot = resolve(root, "build-reports/db-release-safety");
const releaseLock = "quata/selective-db-release/v1";
const approvedReleases = [
  {
    dependencyMode: "exact",
    migrations: new Map([
      ["20260922173500", "52ea7be5e3695ad826c574f8c7af87e6f54cbb0610dfab9f938bdd99766d7070"],
      ["20260922174500", "4b5a91ceee0d4b81717adbcf9d274a350a1fd6f2d23902383d205c94e4ee00ab"],
      ["20260922175500", "acd70b3062a450ad92c0c40ce916ae2f620e76a2504d9aee2da524dfc573ecd6"],
      ["20260922180500", "db006a7e5d3471456465e73ca01c53195475c5b4006f3d361b58e7048420bfc6"],
      ["20260922185000", "3b3ec782cba730889ca962db38ae1e1158dd5be41aa58f45cec1e67a10a92256"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260922202500", "fe8399d59271a3edbcfa349c655f329ff4bb93bdf9df86ea6987506c4384e7a0"],
      ["20260922203500", "6d2b8aa6bcf76a273051f605ea548c87c668cdc74185b88b491aea7785b0b833"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260726171003", "0914caece0c6d65e39b64c21645cac5992ec492d68d16cf0bb2186cec766c627"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260924153500", "5b4bb6c652085ed25e4423a25e6ab2f44b97f8821f50b46282a1e7d919af4b6c"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260924154500", "cc615971b7f19316a293cf5fbc27742c775c585514fcc1610da6d50f42b4510b"],
    ]),
  },
];

const sha256 = (value) => createHash("sha256").update(value).digest("hex");
const isSha256 = (value) => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
const isCommit = (value) => typeof value === "string" && /^[a-f0-9]{40}$/.test(value);

function parseArgs(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index];
    const value = argv[index + 1];
    if (!key?.startsWith("--") || value === undefined) throw new Error("selective_release_invalid_arguments");
    args[key.slice(2)] = value;
  }
  if (!["dry-run", "apply"].includes(args.action)) throw new Error("selective_release_invalid_action");
  if (!args.package || !isCommit(args["expected-source-commit"])) throw new Error("selective_release_package_anchor_required");
  return args;
}

function assertWithin(candidate, container, code) {
  const relation = relative(container, candidate);
  if (!relation || relation.startsWith("..") || isAbsolute(relation)) throw new Error(code);
}

function scrubSql(sql) {
  return sql
    .replace(/\$[A-Za-z_0-9]*\$[\s\S]*?\$[A-Za-z_0-9]*\$/g, "")
    .replace(/'(?:''|[^'])*'/g, "")
    .replace(/--[^\n]*/g, "")
    .replace(/\/\*[\s\S]*?\*\//g, "");
}

function executableMigrationSql(source, version) {
  const transactionControl = /\b(?:begin|commit|rollback|start\s+transaction)\b/i;
  if (!transactionControl.test(scrubSql(source))) return source;
  const outer = source.match(/^\s*begin\s*;\s*([\s\S]*?)\s*commit\s*;\s*$/i);
  if (!outer || transactionControl.test(scrubSql(outer[1]))) {
    throw new Error(`selective_release_transaction_control_refused:${version}`);
  }
  return outer[1];
}

async function loadPackage(path, expectedSourceCommit) {
  const packageRoot = resolve(path);
  assertWithin(packageRoot, allowedPackagesRoot, "selective_release_package_must_be_under_build_reports");
  const manifestPath = resolve(packageRoot, "release-manifest.json");
  const manifestBytes = await readFile(manifestPath);
  const manifest = JSON.parse(manifestBytes.toString("utf8"));
  if (manifest.schemaVersion !== 1 || manifest.sourceCommit !== expectedSourceCommit
      || manifest.deploymentAuthorized !== false || !isSha256(manifest.reconciliationManifestSha256)) {
    throw new Error("selective_release_manifest_invalid");
  }
  const migrations = Array.isArray(manifest.migrations) ? manifest.migrations : [];
  const anchors = migrations.filter(({ role }) => role === "remote_ledger_anchor");
  const selected = migrations.filter(({ role }) => role === "selected_new_migration");
  if (anchors.length === 0 || selected.length === 0 || anchors.length + selected.length !== migrations.length) {
    throw new Error("selective_release_manifest_roles_invalid");
  }
  const versions = migrations.map(({ version }) => version);
  if (versions.some((version) => !/^\d{8}(?:\d{6})?$/.test(version)) || new Set(versions).size !== versions.length) {
    throw new Error("selective_release_manifest_versions_invalid");
  }
  const testMode = process.env.QUATA_SELECTIVE_RELEASE_TEST_MODE === "1";
  const approvedRelease = approvedReleases.find(({ migrations: approved }) =>
    selected.length === approved.size
    && selected.every(({ version, sha256: hash }) => approved.get(version) === hash));
  if (!testMode && !approvedRelease) {
    throw new Error("selective_release_selected_allowlist_mismatch");
  }
  const required = new Set((manifest.reconciliationDependencies ?? [])
    .flatMap(({ requiredPackageMigrations }) => requiredPackageMigrations ?? []));
  const hasExactDependencyCoverage = required.size === selected.length
    && selected.every(({ file }) => required.has(file));
  const dependencyMode = approvedRelease?.dependencyMode ?? (required.size === 0 ? "none" : "exact");
  if ((dependencyMode === "none" && required.size !== 0)
      || (dependencyMode === "exact" && !hasExactDependencyCoverage)) {
    throw new Error("selective_release_dependency_set_mismatch");
  }
  const sources = new Map();
  const executableSources = new Map();
  for (const migration of migrations) {
    if (!isSha256(migration.sha256) || !/^\d{8}(?:\d{6})?_[a-z0-9_]+\.sql$/.test(migration.file)) {
      throw new Error(`selective_release_migration_manifest_invalid:${migration.version}`);
    }
    const sourcePath = resolve(packageRoot, "supabase/migrations", migration.file);
    assertWithin(sourcePath, resolve(packageRoot, "supabase/migrations"), "selective_release_migration_path_invalid");
    const source = await readFile(sourcePath, "utf8");
    if (sha256(source) !== migration.sha256) throw new Error(`selective_release_migration_hash_mismatch:${migration.version}`);
    sources.set(migration.version, source);
    if (migration.role === "selected_new_migration") {
      executableSources.set(migration.version, executableMigrationSql(source, migration.version));
    }
  }
  return { packageRoot, manifestPath, manifestBytes, manifest, anchors, selected, sources, executableSources };
}

async function databaseConfig() {
  const raw = process.env.SUPABASE_DB_URL;
  const caFile = process.env.SUPABASE_DB_TLS_CA_FILE;
  if (!raw || !caFile) throw new Error("selective_release_database_configuration_missing");
  let url;
  try { url = new URL(raw); } catch { throw new Error("selective_release_database_url_invalid"); }
  if (!["postgres:", "postgresql:"].includes(url.protocol)) throw new Error("selective_release_database_url_invalid");
  const ca = await readFile(caFile, "utf8");
  if (!ca.includes("BEGIN CERTIFICATE")) throw new Error("selective_release_tls_ca_invalid");
  const identity = {
    host: url.hostname.toLowerCase(),
    port: url.port || "5432",
    database: decodeURIComponent(url.pathname.replace(/^\//, "")),
    username: decodeURIComponent(url.username).toLowerCase(),
  };
  for (const key of ["sslmode", "uselibpqcompat", "sslcert", "sslkey", "sslrootcert"]) url.searchParams.delete(key);
  return {
    connectionString: url.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: url.hostname },
    application_name: "quata-selective-db-release",
    connectionTimeoutMillis: 15_000,
    query_timeout: 60_000,
    identity,
  };
}

async function ledgerRows(client) {
  return (await client.query(
    "select version::text, coalesce(name, '') as name from supabase_migrations.schema_migrations order by version",
  )).rows;
}

function expectedName(migration) {
  return migration.file.slice(migration.version.length + 1, -4);
}

function assertLedger(rows, anchors, selected) {
  const remote = new Map(rows.map((row) => [row.version, row]));
  if (rows.length !== anchors.length) throw new Error("selective_release_remote_ledger_set_changed");
  for (const anchor of anchors) {
    const row = remote.get(anchor.version);
    if (!row || row.name !== expectedName(anchor)) throw new Error(`selective_release_anchor_mismatch:${anchor.version}`);
  }
  for (const migration of selected) {
    if (remote.has(migration.version)) throw new Error(`selective_release_selected_version_already_present:${migration.version}`);
  }
}

async function databaseFingerprint(client, identity) {
  const row = (await client.query(`
    select current_database() as database, current_user as role,
      d.oid::text as database_oid, pcs.system_identifier::text as system_identifier
    from pg_database d cross join pg_control_system() pcs
    where d.datname = current_database()
  `)).rows[0];
  if (!row || row.database !== identity.database) throw new Error("selective_release_connected_database_mismatch");
  return sha256(JSON.stringify({ ...identity, connectedDatabase: row.database, connectedRole: row.role,
    databaseOid: row.database_oid, systemIdentifier: row.system_identifier }));
}

async function readAuthorization(pkg, path, databaseProjectFingerprint) {
  if (!path) throw new Error("selective_release_authorization_required");
  const authorizationPath = resolve(path);
  if (authorizationPath !== resolve(pkg.packageRoot, "release-authorization.json")) {
    throw new Error("selective_release_authorization_path_invalid");
  }
  const value = JSON.parse(await readFile(authorizationPath, "utf8"));
  const selectedVersions = pkg.selected.map(({ version }) => version);
  if (value.schemaVersion !== 1 || value.approved !== true || value.scope !== "apply_selected_package"
      || value.sourceCommit !== pkg.manifest.sourceCommit
      || value.releaseManifestSha256 !== sha256(pkg.manifestBytes)
      || value.databaseProjectFingerprint !== databaseProjectFingerprint
      || JSON.stringify(value.selectedVersions) !== JSON.stringify(selectedVersions)
      || !Number.isFinite(Date.parse(value.authorizedAt ?? ""))) {
    throw new Error("selective_release_authorization_invalid");
  }
}

async function reconcileCommit(config, pkg, expectedFingerprint) {
  const verification = new Client(config);
  try {
    await verification.connect();
    const fingerprint = await databaseFingerprint(verification, config.identity);
    if (fingerprint !== expectedFingerprint) throw new Error("selective_release_commit_recheck_target_mismatch");
    const lockStartedAt = Date.now();
    const deadline = Date.now() + 60_000;
    while (true) {
      const lock = await verification.query("select pg_try_advisory_lock(hashtextextended($1, 0)) as acquired", [releaseLock]);
      if (lock.rows[0]?.acquired) break;
      if (Date.now() >= deadline) throw new Error("selective_release_commit_reconciliation_lock_timeout");
      await delay(250);
    }
    const rows = await ledgerRows(verification);
    const remote = new Map(rows.map((row) => [row.version, row]));
    const selectedPresent = pkg.selected.filter((migration) => {
      const row = remote.get(migration.version);
      return row?.name === expectedName(migration);
    });
    if (selectedPresent.length === 0) {
      assertLedger(rows, pkg.anchors, pkg.selected);
      return { outcome: "not_applied", lockWaitMs: Date.now() - lockStartedAt };
    }
    if (selectedPresent.length === pkg.selected.length && rows.length === pkg.anchors.length + pkg.selected.length) {
      for (const anchor of pkg.anchors) {
        const row = remote.get(anchor.version);
        if (!row || row.name !== expectedName(anchor)) throw new Error("selective_release_commit_outcome_inconsistent");
      }
      return { outcome: "applied", lockWaitMs: Date.now() - lockStartedAt };
    }
    throw new Error("selective_release_commit_outcome_inconsistent");
  } finally {
    await verification.end().catch(() => {});
  }
}

async function startTestReconciliationBlocker(config) {
  const duration = Number.parseInt(process.env.QUATA_SELECTIVE_RELEASE_TEST_HOLD_RECONCILIATION_LOCK_MS ?? "", 10);
  if (process.env.QUATA_SELECTIVE_RELEASE_TEST_MODE !== "1" || !Number.isInteger(duration) || duration < 1) return;
  if (config.identity.host !== "127.0.0.1" || duration > 10_000) {
    throw new Error("selective_release_test_reconciliation_blocker_invalid");
  }
  const blocker = new Client(config);
  await blocker.connect();
  await blocker.query("select pg_advisory_lock(hashtextextended($1, 0))", [releaseLock]);
  void delay(duration).then(() => blocker.end()).catch(() => {});
}

async function assertProductPostconditions(client) {
  const functions = (await client.query(`
    select
      md5(replace(pg_get_functiondef('public.quata_account_deactivate(uuid,uuid)'::regprocedure), E'\\r\\n', E'\\n')) as deactivate_md5,
      md5(replace(pg_get_functiondef('public.quata_chat_enforce_private_thread_membership()'::regprocedure), E'\\r\\n', E'\\n')) as private_membership_md5,
      pg_get_functiondef('public.quata_chat_get_thread(uuid,bigint,bigint[],integer)'::regprocedure) as get_thread_definition,
      public.quata_chat_community_key('ÁÀÄÂÃÅÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ') as normalized_sample,
      has_function_privilege('service_role', 'public.quata_account_deactivate(uuid,uuid)', 'execute') as service_execute,
      has_function_privilege('anon', 'public.quata_account_deactivate(uuid,uuid)', 'execute') as anon_execute,
      has_function_privilege('authenticated', 'public.quata_account_deactivate(uuid,uuid)', 'execute') as authenticated_execute,
      exists(select 1 from pg_trigger where tgrelid='public.chat_participants'::regclass
        and tgname='chat_participants_enforce_private_membership'
        and tgfoid='public.quata_chat_enforce_private_thread_membership()'::regprocedure
        and tgenabled='O' and not tgisinternal) as private_trigger_enabled
  `)).rows[0];
  if (functions.deactivate_md5 !== "d2504acfb2095176289fb99a939f7621"
      || functions.private_membership_md5 !== "e857da171d692c6b9e128d8d259a8db1"
      || functions.normalized_sample !== "aaaaaaeeeeiiiiooooouuuunc"
      || !functions.service_execute || functions.anon_execute || functions.authenticated_execute
      || !functions.private_trigger_enabled
      || !/order by m\.created_at desc, m\.id desc\s+limit v_limit/i.test(functions.get_thread_definition)
      || !/jsonb_agg\([\s\S]*order by q\.created_at, q\.id/i.test(functions.get_thread_definition)) {
    throw new Error("selective_release_function_postcondition_failed");
  }
  const counts = (await client.query(`
    with expected_members as (
      select distinct t.id as thread_id, cp.id as profile_id
      from public.chat_threads t
      join public.community_walls w on w.id=t.community_id
      join public.community_profiles cp on (
        public.quata_chat_community_key(cp.neighborhood) in (public.quata_chat_community_key(w.normalized_name), public.quata_chat_community_key(w.name), public.quata_chat_community_key(w.slug), public.quata_chat_community_key(t.subject), public.quata_chat_community_key(t.title))
        or public.quata_chat_community_key(cp.barrio) in (public.quata_chat_community_key(w.normalized_name), public.quata_chat_community_key(w.name), public.quata_chat_community_key(w.slug), public.quata_chat_community_key(t.subject), public.quata_chat_community_key(t.title))
        or public.quata_chat_community_key(cp.barrio_normalized) in (public.quata_chat_community_key(w.normalized_name), public.quata_chat_community_key(w.name), public.quata_chat_community_key(w.slug), public.quata_chat_community_key(t.subject), public.quata_chat_community_key(t.title))
      )
      where t.type='wall' and t.deleted_at is null
    )
    select
      (select count(*)::int from public.conversation_user_state s where s.first_visible_message_id is null
        and exists(select 1 from public.chat_messages m where m.thread_id=s.conversation_id)) as missing_visibility,
      (select count(*)::int from public.chat_threads t where t.type='wall' and t.deleted_at is null and t.created_by_profile_id is not null
        and not exists(select 1 from public.chat_participants p where p.thread_id=t.id and p.profile_id=t.created_by_profile_id and p.left_at is null and not p.is_hidden and not p.is_deleted)) as missing_creators,
      (select count(*)::int from expected_members e where not exists(select 1 from public.chat_participants p where p.thread_id=e.thread_id and p.profile_id=e.profile_id and p.left_at is null and not p.is_hidden and not p.is_deleted)) as missing_members,
      (select count(*)::int from public.chat_private_threads cpt where not coalesce((select count(*)=2
        and bool_or(p.profile_id=cpt.profile_low_id) and bool_or(p.profile_id=cpt.profile_high_id)
        from public.chat_participants p where p.thread_id=cpt.thread_id and p.left_at is null), false)) as invalid_private_mappings
  `)).rows[0];
  if (counts.missing_visibility !== 0 || counts.missing_creators !== 0
      || counts.missing_members !== 0 || counts.invalid_private_mappings !== 0) {
    throw new Error("selective_release_data_postcondition_failed");
  }
}

async function writeReport(path, report) {
  if (!path) return;
  const destination = resolve(path);
  assertWithin(destination, resolve(root, "build-reports"), "selective_release_output_must_be_under_build_reports");
  await mkdir(dirname(destination), { recursive: true });
  await writeFile(destination, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

export async function run(argv = process.argv.slice(2)) {
  const args = parseArgs(argv);
  const pkg = await loadPackage(args.package, args["expected-source-commit"]);
  const config = await databaseConfig();
  const client = new Client(config);
  const report = {
    check: "SELECTIVE-DB-RELEASE",
    action: args.action,
    sourceCommit: pkg.manifest.sourceCommit,
    releaseManifestSha256: sha256(pkg.manifestBytes),
    status: "failed",
    databaseProjectFingerprint: null,
    anchorVersions: pkg.anchors.map(({ version }) => version),
    pendingVersions: pkg.selected.map(({ version }) => version),
    appliedVersions: [],
    commitStatus: "not_started",
    reconciliationLockWaitMs: null,
  };
  try {
    await client.connect();
    if (process.env.QUATA_SELECTIVE_RELEASE_TEST_MODE === "1" && config.identity.host !== "127.0.0.1") {
      throw new Error("selective_release_test_mode_requires_loopback");
    }
    if (args.action === "dry-run") await client.query("begin read only");
    report.databaseProjectFingerprint = await databaseFingerprint(client, config.identity);
    if (args.action === "apply") {
      await readAuthorization(pkg, args.authorization, report.databaseProjectFingerprint);
    }
    assertLedger(await ledgerRows(client), pkg.anchors, pkg.selected);
    if (args.action === "dry-run") {
      await client.query("rollback");
      report.status = "passed";
      return report;
    }
    const lock = await client.query("select pg_try_advisory_lock(hashtextextended($1, 0)) as acquired", [releaseLock]);
    if (!lock.rows[0]?.acquired) throw new Error("selective_release_lock_unavailable");
    await client.query("begin isolation level serializable");
    let commitStarted = false;
    try {
      await client.query("lock table supabase_migrations.schema_migrations in exclusive mode");
      assertLedger(await ledgerRows(client), pkg.anchors, pkg.selected);
      for (const migration of pkg.selected) {
        const source = pkg.sources.get(migration.version);
        await client.query(pkg.executableSources.get(migration.version));
        await client.query(
          "insert into supabase_migrations.schema_migrations(version, statements, name) values ($1, $2::text[], $3)",
          [migration.version, [source], expectedName(migration)],
        );
        report.appliedVersions.push(migration.version);
      }
      if (process.env.QUATA_SELECTIVE_RELEASE_TEST_MODE !== "1") await assertProductPostconditions(client);
      commitStarted = true;
      await client.query("commit");
      if (process.env.QUATA_SELECTIVE_RELEASE_TEST_MODE === "1"
          && process.env.QUATA_SELECTIVE_RELEASE_TEST_THROW_AFTER_COMMIT === "1") {
        throw new Error("selective_release_test_commit_ack_lost");
      }
      report.commitStatus = "committed";
    } catch (error) {
      if (!commitStarted) {
        await client.query("rollback").catch(() => {});
        report.appliedVersions = [];
        report.commitStatus = "rolled_back";
        throw error;
      }
      report.commitStatus = "uncertain";
      await client.end().catch(() => {});
      await startTestReconciliationBlocker(config);
      const reconciliation = await reconcileCommit(config, pkg, report.databaseProjectFingerprint);
      report.reconciliationLockWaitMs = reconciliation.lockWaitMs;
      const outcome = reconciliation.outcome;
      if (outcome === "applied") {
        report.commitStatus = "confirmed_after_reconnect";
      } else {
        report.appliedVersions = [];
        report.commitStatus = "not_applied_after_reconnect";
        throw new Error("selective_release_commit_not_applied_after_reconnect");
      }
    }
    report.status = "passed";
    return report;
  } finally {
    await client.end().catch(() => {});
    await writeReport(args.out, report);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  run().then((report) => console.log(JSON.stringify(report))).catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
