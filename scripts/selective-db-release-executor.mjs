#!/usr/bin/env node
import { createHash } from "node:crypto";
import { createRequire } from "node:module";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import process from "node:process";
import { setTimeout as delay } from "node:timers/promises";
import { accountDeactivateDefinitionMd5 } from "./selective-db-release-postconditions.mjs";

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
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260726171004", "f60d2bbafc994215aaeb6a38c6f18ae16e97d6e12cbc1ce83778878e33a45606"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260925113000", "9b1a2e6b668ec6f4cd99a07a5d155d619fdb5ee16a097da4490871f1b0136f28"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260927094500", "98078e6003a3c3360ffd48a4b6700d827ffa777cb1a74f21a0f7b306670965e0"],
      ["20260927100000", "37719a5e32cf647aecfbfebb01d66db2d689b3854041515e5bdad0ad4284ce23"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260927113000", "58931f23c8217217feb0f14e28f4f2c394dd7b07faf55743c9692162f6cbeca2"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260927120000", "13ccf9e628c8e25577bde88bc1754e96a9aff520b8a0b4708aecc93df1c624e1"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260927123000", "a46636a62762f85e6b2d72b3b3526f12caaf2266a1848228a8a3bda5c0151038"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260927133000", "ee8f2859da5892f88e65e0e0a0b76ecd5c5bfe8e1ce4841901ea26c311a4092a"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20260928013000", "99313b28d697d4e4a3aa672e23df9309e184bb6232684a98a56ef2c5d2b95bf4"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20261001211000", "d5e9ed9b0f78dc434918044c41f73d7bb3f1717629aba03f1052e3dd30f3b76b"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20261001211500", "50ef988f6b843ae921de5e41e69e739da443c737844159b2a8d5b064eea54f71"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20261002003000", "89149300661e48f8a9ed210eff74d399f8949d34065bb7cecda98a094d59bf74"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20261002010000", "749ff3d6f7748be355e4b7f88f77db1f4bdeb015689590b42c449f1d1e60753c"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20261002013000", "64241db48e63ee41c599ed0c2ef53030c6857bd3b942dc44fbf86a991f04eb63"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20261004113000", "96a05a158a4faaa206c9c3ba7683f9df7a0ecd7b79e84f2b62dc82b98c0dee20"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20261007090000", "f7d3f63da639fafb4162b5057195bb7db65bcf9d8cfbc771270023b5005b8756"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20261009070000", "71bba05c73b6f1af7284c279e6ebb40b0e3deb2e13a55d82e6a4d6622ee26495"],
    ]),
  },
  {
    dependencyMode: "none",
    migrations: new Map([
      ["20261009073000", "089e1a720afda2c6f6a38951b179293106f8dfad719060d79c1413cb55f64011"],
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
  const outer = source.match(
    /^(?:\s|--[^\r\n]*(?:\r?\n|$)|\/\*[\s\S]*?\*\/)*begin\s*;\s*([\s\S]*?)\s*commit\s*;\s*(?:(?:--[^\r\n]*(?:\r?\n|$))|(?:\/\*[\s\S]*?\*\/\s*))*$/i,
  );
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

async function assertProductPostconditions(client, selectedVersions, installedVersions) {
  const expectedAccountDeactivateMd5 = accountDeactivateDefinitionMd5(installedVersions);
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
  if (functions.deactivate_md5 !== expectedAccountDeactivateMd5
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
      (select count(*)::int from public.conversation_user_state s where s.first_visible_message_id is null and s.deleted_at is null
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
  if (selectedVersions.includes("20261001211000")) {
    const recurrence = (await client.query(`
      select
        pg_get_functiondef('public.quata_chat_reactivate_user_state_for_message()'::regprocedure) as definition,
        (select count(*)::int
           from public.conversation_user_state state
          where state.first_visible_message_id is null
            and state.deleted_at is null
            and exists(select 1 from public.chat_messages message where message.thread_id=state.conversation_id)) as active_missing_boundaries
    `)).rows[0];
    if (!/or state\.first_visible_message_id is null/i.test(recurrence.definition)
        || recurrence.active_missing_boundaries !== 0) {
      throw new Error("selective_release_visibility_boundary_recurrence_postcondition_failed");
    }
  }
  if (selectedVersions.includes("20260726171004")) {
    const registrationPostconditions = await readFile(
      resolve(root, "scripts/sql/web-registration-release-postconditions.sql"),
      "utf8",
    );
    const registration = (await client.query(registrationPostconditions)).rows[0];
    if (registration.ledger_name !== "web_registration_contract"
        || !registration.requests_table || !registration.limits_table || !registration.cleanup_table
        || !registration.claim_function || !registration.auth_lookup_function
        || !registration.cleanup_claim_function || !registration.cleanup_finish_function
        || !registration.secret_answer_hash || !registration.all_rls_enabled
        || !registration.service_table_acl_complete || !registration.untrusted_table_acl_denied
        || !registration.service_function_acl_complete || !registration.untrusted_function_acl_denied
        || registration.request_rows !== 0 || registration.rate_limit_rows !== 0
        || registration.cleanup_event_rows !== 0) {
      throw new Error("selective_release_registration_postcondition_failed");
    }
  }
  if (selectedVersions.includes("20260925113000")) {
    const inboxFunction = (await client.query(`
      select
        p.prosecdef as security_definer,
        p.proconfig as configuration,
        pg_get_functiondef(p.oid) as definition,
        has_function_privilege('authenticated', p.oid, 'execute') as authenticated_execute,
        has_function_privilege('anon', p.oid, 'execute') as anon_execute
      from pg_proc p
      join pg_namespace n on n.oid=p.pronamespace
      where n.nspname='public'
        and p.proname='quata_chat_get_inbox_page'
        and pg_get_function_identity_arguments(p.oid)='p_actor_profile_id uuid, p_limit integer, p_before_last_message_at timestamp with time zone, p_before_updated_at timestamp with time zone, p_before_thread_id bigint'
    `)).rows[0];
    if (!inboxFunction) throw new Error("selective_release_inbox_pagination_function_missing");
    if (!inboxFunction.security_definer
        || !inboxFunction.configuration?.includes("search_path=public")) {
      throw new Error("selective_release_inbox_pagination_function_security_failed");
    }
    if (!inboxFunction.authenticated_execute) {
      throw new Error("selective_release_inbox_pagination_authenticated_execute_missing");
    }
    if (inboxFunction.anon_execute) {
      throw new Error("selective_release_inbox_pagination_anon_execute_present");
    }
    if (!/order by t\.last_message_at desc nulls last, t\.updated_at desc, t\.id desc/i.test(inboxFunction.definition)
        || !/limit v_limit \+ 1/i.test(inboxFunction.definition)) {
      throw new Error("selective_release_inbox_pagination_function_definition_failed");
    }
    const actor = (await client.query(`
      select p.profile_id
      from public.chat_participants p
      where p.left_at is null
      order by p.thread_id, p.profile_id
      limit 1
    `)).rows[0]?.profile_id;
    if (!actor) throw new Error("selective_release_inbox_pagination_fixture_missing");
    const first = (await client.query(
      "select public.quata_chat_get_inbox_page($1::uuid, 1, null, null, null) as payload",
      [actor],
    )).rows[0]?.payload;
    if (!first || !Array.isArray(first.threads) || first.threads.length > 1
        || typeof first.has_more !== "boolean"
        || (first.has_more && (!first.next_cursor || !first.next_cursor.updated_at || !first.next_cursor.thread_id))) {
      throw new Error("selective_release_inbox_pagination_first_page_postcondition_failed");
    }
    if (first.has_more) {
      const cursor = first.next_cursor;
      const second = (await client.query(
        "select public.quata_chat_get_inbox_page($1::uuid, 1, $2::timestamptz, $3::timestamptz, $4::bigint) as payload",
        [actor, cursor.last_message_at, cursor.updated_at, cursor.thread_id],
      )).rows[0]?.payload;
      const firstIds = new Set(first.threads.map(({ id, thread_id: threadId }) => String(threadId ?? id)));
      if (!second || !Array.isArray(second.threads) || second.threads.length > 1
          || second.threads.some(({ id, thread_id: threadId }) => firstIds.has(String(threadId ?? id)))) {
        throw new Error("selective_release_inbox_pagination_second_page_postcondition_failed");
      }
    }
  }
  if (selectedVersions.includes("20260927094500")) {
    const boundary = (await client.query(`
      select
        actor.prosecdef as actor_security_definer,
        actor.provolatile as actor_volatility,
        actor.proconfig as actor_configuration,
        pg_get_functiondef(actor.oid) as actor_definition,
        compatibility.prosecdef as compatibility_security_definer,
        compatibility.provolatile as compatibility_volatility,
        compatibility.proconfig as compatibility_configuration,
        pg_get_functiondef(compatibility.oid) as compatibility_definition,
        has_function_privilege('anon', compatibility.oid, 'execute') as anon_execute,
        has_function_privilege('authenticated', compatibility.oid, 'execute') as authenticated_execute,
        exists (
          select 1
          from aclexplode(coalesce(compatibility.proacl, acldefault('f', compatibility.proowner))) acl
          where acl.grantee = 0 and acl.privilege_type = 'EXECUTE'
        ) as public_execute
      from pg_proc actor
      join pg_namespace actor_ns on actor_ns.oid=actor.pronamespace
      cross join pg_proc compatibility
      join pg_namespace compatibility_ns on compatibility_ns.oid=compatibility.pronamespace
      where actor_ns.nspname='public'
        and actor.proname='quata_chat_actor_profile_id'
        and pg_get_function_identity_arguments(actor.oid)='p_actor_profile_id uuid'
        and compatibility_ns.nspname='public'
        and compatibility.proname='quata_legacy_android_v32_chat_request_allowed'
        and pg_get_function_identity_arguments(compatibility.oid)=''
    `)).rows[0];
    if (!boundary) throw new Error("selective_release_chat_actor_boundary_function_missing");
    if (!boundary.actor_security_definer || boundary.actor_volatility !== "s"
        || !boundary.actor_configuration?.includes("search_path=public, auth")
        || !boundary.compatibility_security_definer || boundary.compatibility_volatility !== "s"
        || !boundary.compatibility_configuration?.includes("search_path=pg_catalog, public")) {
      throw new Error("selective_release_chat_actor_boundary_security_failed");
    }
    if (!boundary.anon_execute || !boundary.authenticated_execute || boundary.public_execute) {
      throw new Error("selective_release_chat_actor_boundary_acl_failed");
    }
    if (!/quata_legacy_android_v32_chat_request_allowed\(\)/i.test(boundary.actor_definition)
        || !/authenticated chat actor is required/i.test(boundary.actor_definition)
        || !/okhttp\/4\.12\.0/i.test(boundary.compatibility_definition)
        || !/x-quata-client-generation/i.test(boundary.compatibility_definition)
        || !/quata_legacy_android_v32_compatibility/i.test(boundary.compatibility_definition)) {
      throw new Error("selective_release_chat_actor_boundary_definition_failed");
    }
    const actor = (await client.query(`
      select id
      from public.community_profiles
      where account_status='active'
      order by id
      limit 1
    `)).rows[0]?.id;
    if (!actor) throw new Error("selective_release_chat_actor_boundary_fixture_missing");
    const legacyHeaders = JSON.stringify({
      apikey: "legacy-v32-public-key",
      authorization: "Bearer legacy-v32-public-key",
      "content-profile": "public",
      "user-agent": "okhttp/4.12.0",
    });
    await client.query("select set_config('request.jwt.claim.role', 'anon', true)");
    await client.query("select set_config('request.method', 'POST', true)");
    await client.query("select set_config('request.path', '/rpc/quata_chat_open_community_thread', true)");
    await client.query("select set_config('request.headers', $1, true)", [legacyHeaders]);
    const legacyActor = (await client.query(
      "select public.quata_chat_actor_profile_id($1::uuid) as actor",
      [actor],
    )).rows[0]?.actor;
    if (legacyActor !== actor) throw new Error("selective_release_chat_actor_boundary_legacy_v32_failed");

    const modernHeaders = JSON.stringify({
      apikey: "legacy-v32-public-key",
      authorization: "Bearer legacy-v32-public-key",
      "content-profile": "public",
      "user-agent": "okhttp/4.12.0",
      "x-quata-client-generation": "android-auth-boundary-v1",
    });
    await client.query("select set_config('request.headers', $1, true)", [modernHeaders]);
    await client.query("savepoint chat_actor_boundary_modern_anonymous");
    let modernRejected = false;
    try {
      await client.query("select public.quata_chat_actor_profile_id($1::uuid)", [actor]);
    } catch (error) {
      modernRejected = error?.code === "42501";
      await client.query("rollback to savepoint chat_actor_boundary_modern_anonymous");
    }
    if (!modernRejected) throw new Error("selective_release_chat_actor_boundary_modern_anonymous_not_rejected");
    await client.query("release savepoint chat_actor_boundary_modern_anonymous");
  }
  if (selectedVersions.includes("20261001211500")) {
    const functions = (await client.query(`
      select
        p.prosecdef as security_definer,
        p.proconfig as configuration,
        pg_get_functiondef(p.oid) as definition,
        has_function_privilege('authenticated', p.oid, 'execute') as authenticated_execute,
        has_function_privilege('anon', p.oid, 'execute') as anon_execute,
        has_function_privilege('public', p.oid, 'execute') as public_execute
      from pg_proc p
      join pg_namespace n on n.oid=p.pronamespace
      where n.nspname='public'
        and p.proname='quata_chat_get_favorites_page'
        and pg_get_function_identity_arguments(p.oid)='p_actor_profile_id uuid, p_limit integer, p_before_created_at timestamp with time zone, p_before_message_id bigint'
    `)).rows;
    if (functions.length !== 1) throw new Error("selective_release_favorites_pagination_function_missing");
    const favorites = functions[0];
    if (!favorites.security_definer
        || !favorites.configuration?.includes("search_path=public")
        || !favorites.authenticated_execute || favorites.anon_execute || favorites.public_execute) {
      throw new Error("selective_release_favorites_pagination_security_failed");
    }
    if (!/order by m\.created_at desc, m\.id desc[\s\S]*limit v_limit \+ 1/i.test(favorites.definition)
        || !/m\.created_at < p_before_created_at/i.test(favorites.definition)
        || !/m\.id < p_before_message_id/i.test(favorites.definition)) {
      throw new Error("selective_release_favorites_pagination_definition_failed");
    }
    const legacy = (await client.query(`
      select has_function_privilege('anon', p.oid, 'execute') as anon_execute
      from pg_proc p join pg_namespace n on n.oid=p.pronamespace
      where n.nspname='public' and p.proname='quata_chat_get_favorites'
        and pg_get_function_identity_arguments(p.oid)='p_actor_profile_id uuid, p_limit integer'
    `)).rows;
    if (legacy.length !== 1 || !legacy[0].anon_execute) {
      throw new Error("selective_release_favorites_pagination_legacy_contract_changed");
    }
    const actor = (await client.query(`
      select cp.id as profile_id, cp.auth_user_id
      from public.chat_message_favorites f
      join public.community_profiles cp on cp.id=f.profile_id
      where cp.auth_user_id is not null and cp.account_status='active'
      group by cp.id, cp.auth_user_id
      order by count(*) desc, cp.id
      limit 1
    `)).rows[0];
    if (actor) {
      await client.query("select set_config('request.jwt.claim.sub', $1, true), set_config('request.jwt.claim.role', 'authenticated', true)", [actor.auth_user_id]);
      const first = (await client.query(
        "select public.quata_chat_get_favorites_page($1::uuid, 1, null, null) as payload",
        [actor.profile_id],
      )).rows[0]?.payload;
      if (!first || !Array.isArray(first.messages) || first.messages.length > 1
          || typeof first.has_more !== "boolean"
          || (first.has_more && (!first.next_cursor?.created_at || !first.next_cursor?.message_id))) {
        throw new Error("selective_release_favorites_pagination_first_page_postcondition_failed");
      }
      if (first.has_more) {
        const cursor = first.next_cursor;
        const second = (await client.query(
          "select public.quata_chat_get_favorites_page($1::uuid, 1, $2::timestamptz, $3::bigint) as payload",
          [actor.profile_id, cursor.created_at, cursor.message_id],
        )).rows[0]?.payload;
        const firstIds = new Set(first.messages.map(({ id }) => String(id)));
        if (!second || !Array.isArray(second.messages) || second.messages.length > 1
            || second.messages.some(({ id }) => firstIds.has(String(id)))) {
          throw new Error("selective_release_favorites_pagination_second_page_postcondition_failed");
        }
      }
    }
  }
  if (selectedVersions.includes("20261002003000")) {
    const mutationBoundary = (await client.query(`
      select
        relation.relrowsecurity as rls_enabled,
        not has_table_privilege('anon', relation.oid, 'SELECT,INSERT,UPDATE,DELETE') as anon_table_denied,
        not has_table_privilege('authenticated', relation.oid, 'SELECT,INSERT,UPDATE,DELETE') as authenticated_table_denied,
        edit.prosecdef as edit_security_definer,
        edit.proconfig as edit_configuration,
        delete_function.prosecdef as delete_security_definer,
        delete_function.proconfig as delete_configuration,
        has_function_privilege('authenticated', edit.oid, 'EXECUTE') as authenticated_edit_execute,
        has_function_privilege('authenticated', delete_function.oid, 'EXECUTE') as authenticated_delete_execute,
        not has_function_privilege('anon', edit.oid, 'EXECUTE') as anon_edit_denied,
        not has_function_privilege('anon', delete_function.oid, 'EXECUTE') as anon_delete_denied,
        not exists (
          select 1
            from pg_proc function,
                 lateral aclexplode(coalesce(function.proacl, acldefault('f', function.proowner))) acl
           where function.oid in (edit.oid, delete_function.oid)
             and acl.grantee=0
             and acl.privilege_type='EXECUTE'
        ) as public_execute_absent,
        pg_get_functiondef(edit.oid) as edit_definition,
        pg_get_functiondef(delete_function.oid) as delete_definition
      from pg_class relation
      join pg_namespace relation_namespace on relation_namespace.oid=relation.relnamespace
      cross join pg_proc edit
      cross join pg_proc delete_function
      where relation_namespace.nspname='public'
        and relation.relname='chat_message_mutation_receipts'
        and edit.oid='public.quata_chat_edit_message_v2(uuid,bigint,bigint,text,text)'::regprocedure
        and delete_function.oid='public.quata_chat_delete_messages_v2(uuid,bigint,bigint[],text)'::regprocedure
    `)).rows;
    if (mutationBoundary.length !== 1) throw new Error("selective_release_chat_mutation_boundary_missing");
    const mutation = mutationBoundary[0];
    if (!mutation.rls_enabled || !mutation.anon_table_denied || !mutation.authenticated_table_denied
        || !mutation.edit_security_definer || !mutation.delete_security_definer
        || !mutation.edit_configuration?.includes("search_path=public")
        || !mutation.delete_configuration?.includes("search_path=public")
        || !mutation.authenticated_edit_execute || !mutation.authenticated_delete_execute
        || !mutation.anon_edit_denied || !mutation.anon_delete_denied || !mutation.public_execute_absent) {
      throw new Error("selective_release_chat_mutation_security_failed");
    }
    for (const definition of [mutation.edit_definition, mutation.delete_definition]) {
      if (!/insert into public\.chat_message_mutation_receipts/i.test(definition)
          || !/client mutation id was reused for a different request/i.test(definition)
          || !/on conflict \(actor_profile_id, client_mutation_id\) do nothing/i.test(definition)
          || !/completed_at/i.test(definition)) {
        throw new Error("selective_release_chat_mutation_definition_failed");
      }
    }
  }
  if (selectedVersions.includes("20261002010000")) {
    const relation = (await client.query(`
      select c.relrowsecurity as rls_enabled,
             count(t.oid) filter (where not t.tgisinternal)::int as user_trigger_count
        from pg_class c
        join pg_namespace n on n.oid=c.relnamespace
        left join pg_trigger t on t.tgrelid=c.oid
       where n.nspname='public' and c.relname='community_post_likes'
       group by c.oid
    `)).rows;
    if (relation.length !== 1 || !relation[0].rls_enabled || relation[0].user_trigger_count !== 0) {
      throw new Error("selective_release_community_post_likes_relation_postcondition_failed");
    }

    const policies = (await client.query(`
      select policyname, roles::text, cmd, qual, with_check
        from pg_catalog.pg_policies
       where schemaname='public' and tablename='community_post_likes'
       order by policyname
    `)).rows;
    const policyByName = new Map(policies.map((row) => [row.policyname, row]));
    const normalizeSql = (value) => String(value ?? "").replace(/\s+/g, " ").trim();
    const actorExpression = "((( SELECT quata_chat_auth_profile_id() AS quata_chat_auth_profile_id) IS NOT NULL) AND (profile_id = ( SELECT quata_chat_auth_profile_id() AS quata_chat_auth_profile_id)))";
    const readPolicy = policyByName.get("community_post_likes_public_read");
    const insertPolicy = policyByName.get("community_post_likes_insert_own");
    const deletePolicy = policyByName.get("community_post_likes_delete_own");
    if (policies.length !== 3
        || readPolicy?.roles !== "{public}" || readPolicy?.cmd !== "SELECT"
        || normalizeSql(readPolicy?.qual) !== "true" || readPolicy?.with_check !== null
        || insertPolicy?.roles !== "{authenticated}" || insertPolicy?.cmd !== "INSERT"
        || normalizeSql(insertPolicy?.with_check) !== actorExpression || insertPolicy?.qual !== null
        || deletePolicy?.roles !== "{authenticated}" || deletePolicy?.cmd !== "DELETE"
        || normalizeSql(deletePolicy?.qual) !== actorExpression || deletePolicy?.with_check !== null) {
      throw new Error("selective_release_community_post_likes_policy_postcondition_failed");
    }

    const grantRows = (await client.query(`
      select grantee, privilege_type
        from information_schema.role_table_grants
       where table_schema='public' and table_name='community_post_likes'
         and grantee in ('PUBLIC', 'anon', 'authenticated')
       order by grantee, privilege_type
    `)).rows;
    const grants = new Map();
    for (const row of grantRows) {
      if (!grants.has(row.grantee)) grants.set(row.grantee, []);
      grants.get(row.grantee).push(row.privilege_type);
    }
    if (JSON.stringify(grants.get("anon") ?? []) !== JSON.stringify(["SELECT"])
        || JSON.stringify(grants.get("authenticated") ?? []) !== JSON.stringify(["DELETE", "INSERT", "SELECT"])
        || (grants.get("PUBLIC") ?? []).length !== 0) {
      throw new Error("selective_release_community_post_likes_grant_postcondition_failed");
    }

    const resolverRows = (await client.query(`
      select l.lanname as language, p.provolatile as volatility,
             p.prosecdef as security_definer, p.proconfig as configuration, p.prosrc as source,
             exists (
               select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
                where acl.grantee=0 and acl.privilege_type='EXECUTE'
             ) as public_execute,
             has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
             has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute
        from pg_proc p
        join pg_namespace n on n.oid=p.pronamespace
        join pg_language l on l.oid=p.prolang
       where n.nspname='public' and p.proname='quata_chat_auth_profile_id' and p.pronargs=0
    `)).rows;
    const resolver = resolverRows[0];
    const resolverSource = "select cp.id from public.community_profiles cp where auth.uid() is not null and cp.account_status = 'active' and (cp.id = auth.uid() or cp.auth_user_id = auth.uid()) limit 1";
    if (resolverRows.length !== 1 || resolver.language !== "sql" || resolver.volatility !== "s"
        || !resolver.security_definer
        || JSON.stringify(resolver.configuration) !== JSON.stringify(["search_path=public, auth"])
        || normalizeSql(resolver.source) !== resolverSource || !resolver.public_execute
        || !resolver.anon_execute || !resolver.authenticated_execute) {
      throw new Error("selective_release_community_post_likes_resolver_postcondition_failed");
    }
  }
  if (selectedVersions.includes("20261002013000")) {
    const functionRows = (await client.query(`
      select l.lanname as language,
             p.provolatile as volatility,
             p.prosecdef as security_definer,
             p.proretset as returns_set,
             p.prorettype='public.official_posts'::regtype as returns_official_posts,
             p.proconfig as configuration,
             pg_get_functiondef(p.oid) as definition,
             exists (
               select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
                where acl.grantee=0 and acl.privilege_type='EXECUTE'
             ) as public_execute,
             has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
             has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute
        from pg_proc p
        join pg_namespace n on n.oid=p.pronamespace
        join pg_language l on l.oid=p.prolang
       where n.nspname='public'
         and p.proname='quata_official_feed_page'
         and pg_get_function_identity_arguments(p.oid)='p_limit integer, p_before_sort_at timestamp with time zone, p_before_created_at timestamp with time zone, p_before_id uuid'
    `)).rows;
    const pageFunction = functionRows[0];
    const definition = String(pageFunction?.definition ?? "").replace(/\s+/g, " ");
    if (functionRows.length !== 1
        || pageFunction.language !== "plpgsql" || pageFunction.volatility !== "s"
        || pageFunction.security_definer || !pageFunction.returns_set || !pageFunction.returns_official_posts
        || JSON.stringify(pageFunction.configuration) !== JSON.stringify(["search_path=public, pg_temp"])
        || pageFunction.public_execute || !pageFunction.anon_execute || !pageFunction.authenticated_execute
        || !/cursor_values not in \(0, 3\)/i.test(definition)
        || !/partition by op\.translation_group_id/i.test(definition)
        || !/coalesce\(chosen\.published_at, chosen\.created_at\).*chosen\.created_at.*chosen\.id.*<.*p_before_sort_at.*p_before_created_at.*p_before_id/is.test(definition)
        || !/order by coalesce\(chosen\.published_at, chosen\.created_at\) desc, chosen\.created_at desc, chosen\.id desc/i.test(definition)) {
      throw new Error("selective_release_official_feed_function_postcondition_failed");
    }

    const indexRows = (await client.query(`
      select i.indisvalid as valid,
             i.indisready as ready,
             i.indisunique as unique,
             pg_get_indexdef(i.indexrelid) as definition,
             pg_get_expr(i.indpred, i.indrelid) as predicate
        from pg_index i
        join pg_class index_relation on index_relation.oid=i.indexrelid
        join pg_namespace n on n.oid=index_relation.relnamespace
       where n.nspname='public' and index_relation.relname='official_posts_public_total_order_idx'
    `)).rows;
    const feedIndex = indexRows[0];
    const indexDefinition = String(feedIndex?.definition ?? "").replace(/\s+/g, " ");
    const predicate = String(feedIndex?.predicate ?? "").replace(/\s+/g, " ");
    if (indexRows.length !== 1 || !feedIndex.valid || !feedIndex.ready || feedIndex.unique
        || !/\(language, COALESCE\(published_at, created_at\) DESC, created_at DESC, id DESC\) INCLUDE \(translation_group_id\)/i.test(indexDefinition)
        || !/is_published = true/i.test(predicate) || !/deleted_at is null/i.test(predicate)) {
      throw new Error("selective_release_official_feed_index_postcondition_failed");
    }
  }
  if (selectedVersions.includes("20261004113000")) {
    const functionRows = (await client.query(`
      select l.lanname as language,
             p.provolatile as volatility,
             p.prosecdef as security_definer,
             p.proretset as returns_set,
             p.prorettype='public.community_posts'::regtype as returns_community_posts,
             p.proconfig as configuration,
             pg_get_functiondef(p.oid) as definition,
             exists (
               select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
                where acl.grantee=0 and acl.privilege_type='EXECUTE'
             ) as public_execute,
             has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
             has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute
        from pg_proc p
        join pg_namespace n on n.oid=p.pronamespace
        join pg_language l on l.oid=p.prolang
       where n.nspname='public'
         and p.proname='quata_community_feed_page'
         and pg_get_function_identity_arguments(p.oid)='p_limit integer, p_before_created_at timestamp with time zone, p_before_id uuid'
    `)).rows;
    const pageFunction = functionRows[0];
    const definition = String(pageFunction?.definition ?? "").replace(/\s+/g, " ");
    if (functionRows.length !== 1
        || pageFunction.language !== "plpgsql" || pageFunction.volatility !== "s"
        || pageFunction.security_definer || !pageFunction.returns_set || !pageFunction.returns_community_posts
        || JSON.stringify(pageFunction.configuration) !== JSON.stringify(["search_path=public, pg_temp"])
        || pageFunction.public_execute || !pageFunction.anon_execute || !pageFunction.authenticated_execute
        || !/cursor_values not in \(0, 2\)/i.test(definition)
        || !/\(post\.created_at, post\.id\).*<.*\(p_before_created_at, p_before_id\)/is.test(definition)
        || !/order by post\.created_at desc, post\.id desc/i.test(definition)) {
      throw new Error("selective_release_community_feed_function_postcondition_failed");
    }

    const indexRows = (await client.query(`
      select i.indisvalid as valid,
             i.indisready as ready,
             i.indisunique as unique,
             pg_get_indexdef(i.indexrelid) as definition
        from pg_index i
        join pg_class index_relation on index_relation.oid=i.indexrelid
        join pg_namespace n on n.oid=index_relation.relnamespace
       where n.nspname='public' and index_relation.relname='community_posts_public_total_order_idx'
    `)).rows;
    const feedIndex = indexRows[0];
    const indexDefinition = String(feedIndex?.definition ?? "").replace(/\s+/g, " ");
    if (indexRows.length !== 1 || !feedIndex.valid || !feedIndex.ready || feedIndex.unique
        || !/\(created_at DESC, id DESC\)/i.test(indexDefinition)) {
      throw new Error("selective_release_community_feed_index_postcondition_failed");
    }
  }
  if (selectedVersions.includes("20260927100000")
      || selectedVersions.includes("20260927113000")
      || selectedVersions.includes("20260927120000")) {
    const visibilityDeleteRepair = (await client.query(`
      select
        trigger.tgenabled as trigger_enabled,
        pg_get_triggerdef(trigger.oid) as trigger_definition,
        function.prosecdef as security_definer,
        function.provolatile as volatility,
        function.proconfig as configuration,
        function.proacl as acl,
        pg_get_functiondef(function.oid) as function_definition
      from pg_trigger trigger
      join pg_proc function on function.oid=trigger.tgfoid
      where trigger.tgrelid='public.chat_messages'::regclass
        and trigger.tgname='chat_messages_repoint_visibility_before_delete'
        and not trigger.tgisinternal
        and function.oid='public.quata_chat_repoint_visibility_before_message_delete()'::regprocedure
    `)).rows[0];
    if (!visibilityDeleteRepair) {
      throw new Error("selective_release_visibility_delete_repair_trigger_missing");
    }
    if (visibilityDeleteRepair.trigger_enabled !== "O"
        || !/before delete on public\.chat_messages/i.test(visibilityDeleteRepair.trigger_definition)
        || !visibilityDeleteRepair.security_definer
        || visibilityDeleteRepair.volatility !== "v"
        || !visibilityDeleteRepair.configuration?.includes("search_path=public")) {
      throw new Error("selective_release_visibility_delete_repair_security_failed");
    }
    if (!/first_visible_message_id\s*=\s*\(/i.test(visibilityDeleteRepair.function_definition)
        && !/first_visible_message_id\s*=\s*v_next_message_id/i.test(visibilityDeleteRepair.function_definition)) {
      throw new Error("selective_release_visibility_delete_repair_definition_failed");
    }
    if (!/message\.id\s*>\s*old\.id/i.test(visibilityDeleteRepair.function_definition)) {
      throw new Error("selective_release_visibility_delete_repair_definition_failed");
    }
    if (selectedVersions.includes("20260927120000")
        && (!/deleted_at\s*=\s*case/i.test(visibilityDeleteRepair.function_definition)
          || !/when\s+v_next_message_id\s+is\s+null\s+then\s+coalesce\(state\.deleted_at,\s*now\(\)\)/i.test(visibilityDeleteRepair.function_definition))) {
      throw new Error("selective_release_visibility_delete_exhausted_boundary_failed");
    }
    const publicExecute = (await client.query(`
      select exists (
        select 1
        from pg_proc function,
             lateral aclexplode(coalesce(function.proacl, acldefault('f', function.proowner))) acl
        where function.oid='public.quata_chat_repoint_visibility_before_message_delete()'::regprocedure
          and acl.grantee=0
          and acl.privilege_type='EXECUTE'
      ) as granted
    `)).rows[0]?.granted;
    if (publicExecute) throw new Error("selective_release_visibility_delete_repair_acl_failed");
  }
  if (selectedVersions.includes("20260927123000") || selectedVersions.includes("20260927133000")) {
    const participantGuards = (await client.query(`
      select
        pg_get_functiondef('public.quata_chat_promote_moderator(uuid,bigint,uuid)'::regprocedure) as promote_definition,
        pg_get_functiondef('public.quata_chat_demote_moderator(uuid,bigint,uuid)'::regprocedure) as demote_definition,
        pg_get_functiondef('public.quata_chat_remove_participant(uuid,bigint,uuid)'::regprocedure) as remove_definition,
        pg_get_functiondef('public.quata_chat_block_participant(uuid,bigint,uuid)'::regprocedure) as block_definition,
        has_function_privilege('anon', 'public.quata_chat_promote_moderator(uuid,bigint,uuid)', 'execute') as anon_promote,
        has_function_privilege('authenticated', 'public.quata_chat_promote_moderator(uuid,bigint,uuid)', 'execute') as authenticated_promote,
        has_function_privilege('anon', 'public.quata_chat_block_participant(uuid,bigint,uuid)', 'execute') as anon_block,
        has_function_privilege('authenticated', 'public.quata_chat_block_participant(uuid,bigint,uuid)', 'execute') as authenticated_block,
        exists (
          select 1
            from pg_proc function,
                 lateral aclexplode(coalesce(function.proacl, acldefault('f', function.proowner))) acl
           where function.oid = 'public.quata_chat_promote_moderator(uuid,bigint,uuid)'::regprocedure
             and acl.grantee = 0
             and acl.privilege_type = 'EXECUTE'
        ) as public_promote,
        exists (
          select 1
            from pg_proc function,
                 lateral aclexplode(coalesce(function.proacl, acldefault('f', function.proowner))) acl
           where function.oid = 'public.quata_chat_block_participant(uuid,bigint,uuid)'::regprocedure
             and acl.grantee = 0
             and acl.privilege_type = 'EXECUTE'
        ) as public_block
    `)).rows[0];
    for (const definition of [
      participantGuards.promote_definition,
      participantGuards.demote_definition,
      participantGuards.remove_definition,
    ]) {
      if (!/select\s+role[\s\S]*for update/i.test(definition)
          || !/target participant does not exist/i.test(definition)
          || !/v_target_role\s*=\s*'owner'/i.test(definition)) {
        throw new Error("selective_release_chat_group_target_guard_failed");
      }
    }
    if (!/p_profile_id\s*=\s*v_actor/i.test(participantGuards.block_definition)
        || !/target participant does not exist/i.test(participantGuards.block_definition)) {
      throw new Error("selective_release_chat_group_block_guard_failed");
    }
    if (selectedVersions.includes("20260927133000")
        && !/select\s+role[\s\S]*left_at\s+is\s+null[\s\S]*for update/i.test(participantGuards.block_definition)) {
      throw new Error("selective_release_chat_group_block_target_lock_failed");
    }
    if (!participantGuards.anon_promote || !participantGuards.authenticated_promote || participantGuards.public_promote
        || !participantGuards.anon_block || !participantGuards.authenticated_block || participantGuards.public_block) {
      throw new Error("selective_release_chat_group_guard_acl_failed");
    }
  }
  if (selectedVersions.includes("20260928013000")) {
    const lifecycle = (await client.query(`
      with expected_functions(identity, expected_search_path, service_required) as (
        values
          ('public.quata_guard_active_delivery_owner()', 'search_path=public', false),
          ('public.quata_guard_deactivation_reactivation()', 'search_path=public', false),
          ('public.quata_account_deactivate(uuid,uuid)', 'search_path=public, auth', true),
          ('public.quata_account_deactivate(uuid,uuid,uuid)', 'search_path=public, auth', true),
          ('public.quata_account_deactivation_complete(uuid,uuid,uuid)', 'search_path=public, auth', true),
          ('public.quata_account_deactivation_compensate(uuid,uuid,uuid)', 'search_path=public, auth', true),
          ('public.quata_account_reactivation_begin(uuid,uuid)', 'search_path=public, auth', true),
          ('public.quata_account_reactivation_complete(uuid,uuid,uuid)', 'search_path=public, auth', true),
          ('public.quata_account_reactivation_cancel(uuid,uuid,uuid)', 'search_path=public, auth', true)
      ), function_surface as (
        select expected.*, function.oid, function.prosecdef, function.proconfig,
               case when function.oid is null then null else pg_get_functiondef(function.oid) end as definition
          from expected_functions expected
          left join pg_proc function on function.oid=to_regprocedure(expected.identity)
      ), relation_surface as (
        select relation.oid, relation.relrowsecurity, relation.relacl, relation.relowner
          from pg_class relation
         where relation.oid=to_regclass('public.account_deactivation_operations')
      )
      select
        exists(select 1 from relation_surface) as operation_table_exists,
        coalesce((select relrowsecurity from relation_surface), false) as operation_table_rls,
        coalesce((select
          has_table_privilege('anon', oid, 'select')
          or has_table_privilege('anon', oid, 'insert')
          or has_table_privilege('anon', oid, 'update')
          or has_table_privilege('anon', oid, 'delete')
          or has_table_privilege('authenticated', oid, 'select')
          or has_table_privilege('authenticated', oid, 'insert')
          or has_table_privilege('authenticated', oid, 'update')
          or has_table_privilege('authenticated', oid, 'delete')
          from relation_surface), true) as untrusted_table_access,
        coalesce((select exists(
          select 1
            from relation_surface relation,
                 lateral aclexplode(coalesce(relation.relacl, acldefault('r', relation.relowner))) acl
           where acl.grantee=0
             and acl.privilege_type in ('SELECT', 'INSERT', 'UPDATE', 'DELETE')
        )), true) as public_table_access,
        (select count(*)::int from function_surface where oid is not null) as function_count,
        coalesce((select bool_and(
          oid is not null
          and prosecdef
          and expected_search_path=any(coalesce(proconfig, array[]::text[]))
        ) from function_surface), false) as functions_secured,
        coalesce((select bool_and(
          oid is not null
          and (not service_required or has_function_privilege('service_role', oid, 'execute'))
          and not has_function_privilege('anon', oid, 'execute')
          and not has_function_privilege('authenticated', oid, 'execute')
          and not exists (
            select 1
              from aclexplode(coalesce(
                (select proacl from pg_proc where pg_proc.oid=function_surface.oid),
                acldefault('f', (select proowner from pg_proc where pg_proc.oid=function_surface.oid))
              )) acl
             where acl.grantee=0 and acl.privilege_type='EXECUTE'
          )
        ) from function_surface), false) as function_acl_exact,
        (select jsonb_object_agg(identity, definition) from function_surface) as definitions
    `)).rows[0];
    if (!lifecycle.operation_table_exists || !lifecycle.operation_table_rls
        || lifecycle.untrusted_table_access || lifecycle.public_table_access) {
      throw new Error("selective_release_account_lifecycle_table_postcondition_failed");
    }
    if (lifecycle.function_count !== 9 || !lifecycle.functions_secured || !lifecycle.function_acl_exact) {
      throw new Error("selective_release_account_lifecycle_function_postcondition_failed");
    }
    const compatibility = lifecycle.definitions?.["public.quata_account_deactivate(uuid,uuid)"] ?? "";
    const successor = lifecycle.definitions?.["public.quata_account_deactivate(uuid,uuid,uuid)"] ?? "";
    if (!/quata_account_deactivate\(gen_random_uuid\(\), p_profile_id, p_auth_user_id\)/i.test(compatibility)
        || !/update public\.web_push_subscriptions/i.test(successor)
        || !/update public\.web_client_sessions/i.test(successor)
        || !/update public\.push_tokens/i.test(successor)
        || !/insert into public\.account_deactivation_operations/i.test(successor)) {
      throw new Error("selective_release_account_lifecycle_definition_postcondition_failed");
    }
    const guards = (await client.query(`
      with expected(trigger_name, relation_name, function_identity) as (
        values
          ('quata_community_profiles_deactivation_guard', 'public.community_profiles', 'public.quata_guard_deactivation_reactivation()'),
          ('quata_push_tokens_active_owner_guard', 'public.push_tokens', 'public.quata_guard_active_delivery_owner()'),
          ('quata_web_client_sessions_active_owner_guard', 'public.web_client_sessions', 'public.quata_guard_active_delivery_owner()'),
          ('quata_web_push_subscriptions_active_owner_guard', 'public.web_push_subscriptions', 'public.quata_guard_active_delivery_owner()')
      )
      select
        count(trigger.oid)::int as trigger_count,
        coalesce(bool_and(
          trigger.oid is not null
          and trigger.tgenabled='O'
          and trigger.tgfoid=to_regprocedure(expected.function_identity)
        ), false) as triggers_exact
        from expected
        left join pg_trigger trigger
          on trigger.tgname=expected.trigger_name
         and trigger.tgrelid=to_regclass(expected.relation_name)
         and not trigger.tgisinternal
    `)).rows[0];
    if (guards.trigger_count !== 4 || !guards.triggers_exact) {
      throw new Error("selective_release_account_lifecycle_trigger_postcondition_failed");
    }
    const openTransitions = (await client.query(`
      select count(*)::int as count
        from public.account_deactivation_operations
       where state in ('database_applied', 'compensating', 'reactivating')
    `)).rows[0]?.count;
    if (openTransitions !== 0) {
      throw new Error("selective_release_account_lifecycle_open_transition_postcondition_failed");
    }
  }
  if (selectedVersions.includes("20261007090000")) {
    const rows = (await client.query(`
      select p.prosecdef as security_definer,
             p.provolatile as volatility,
             p.proconfig as configuration,
             pg_get_functiondef(p.oid) as definition,
             has_function_privilege('service_role', p.oid, 'EXECUTE') as service_execute,
             has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
             has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute,
             exists (
               select 1
                 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
                where acl.grantee=0 and acl.privilege_type='EXECUTE'
             ) as public_execute
        from pg_proc p
        join pg_namespace n on n.oid=p.pronamespace
       where n.nspname='public'
         and p.oid=to_regprocedure('public.quata_retire_all_device_endpoints(uuid)')
    `)).rows;
    const retirement = rows[0];
    if (rows.length !== 1 || !retirement.security_definer || retirement.volatility !== "v"
        || JSON.stringify(retirement.configuration) !== JSON.stringify(["search_path=public"])
        || !retirement.service_execute || retirement.anon_execute
        || retirement.authenticated_execute || retirement.public_execute) {
      throw new Error("selective_release_auth_global_logout_security_postcondition_failed");
    }
    const definition = retirement.definition ?? "";
    if (!/update public\.push_tokens/i.test(definition)
        || !/update public\.web_push_subscriptions/i.test(definition)
        || !/update public\.web_client_sessions/i.test(definition)
        || !/where auth_user_id = p_auth_user_id/i.test(definition)) {
      throw new Error("selective_release_auth_global_logout_definition_postcondition_failed");
    }
  }
  if (selectedVersions.includes("20261009070000")) {
    const rows = (await client.query(`
      select p.prosecdef as security_definer,
             p.provolatile as volatility,
             p.proconfig as configuration,
             pg_get_functiondef(p.oid) as definition
        from pg_proc p
        join pg_namespace n on n.oid=p.pronamespace
       where n.nspname='public'
         and p.oid=to_regprocedure('public.quata_chat_get_or_create_private_thread(uuid,uuid)')
    `)).rows;
    const privateOpen = rows[0];
    if (rows.length !== 1 || !privateOpen.security_definer || privateOpen.volatility !== "v"
        || JSON.stringify(privateOpen.configuration) !== JSON.stringify(["search_path=public"])) {
      throw new Error("selective_release_private_open_identity_postcondition_failed");
    }
    const definition = privateOpen.definition ?? "";
    if (!/pg_advisory_xact_lock/i.test(definition)
        || !/v_created boolean := false/i.test(definition)
        || !/v_created := true/i.test(definition)
        || !/if v_created then[\s\S]*private_thread_opened[\s\S]*end if/i.test(definition)) {
      throw new Error("selective_release_private_open_idempotency_postcondition_failed");
    }
  }
  if (selectedVersions.includes("20261009073000")) {
    const privileges = (await client.query(`
      select
        not has_function_privilege('anon', 'public.quata_ugc_report(uuid,text,text,text,text)', 'EXECUTE') as anon_report_denied,
        not has_function_privilege('anon', 'public.quata_profile_block(uuid,uuid)', 'EXECUTE') as anon_block_denied,
        not has_function_privilege('anon', 'public.quata_profile_unblock(uuid,uuid)', 'EXECUTE') as anon_unblock_denied,
        has_function_privilege('authenticated', 'public.quata_ugc_report(uuid,text,text,text,text)', 'EXECUTE') as authenticated_report_allowed,
        has_function_privilege('authenticated', 'public.quata_profile_block(uuid,uuid)', 'EXECUTE') as authenticated_block_allowed,
        has_function_privilege('authenticated', 'public.quata_profile_unblock(uuid,uuid)', 'EXECUTE') as authenticated_unblock_allowed,
        not has_table_privilege('anon', 'public.ugc_reports', 'INSERT,UPDATE,DELETE') as anon_report_table_denied,
        not has_table_privilege('authenticated', 'public.ugc_reports', 'INSERT,UPDATE,DELETE') as authenticated_report_table_denied,
        not has_table_privilege('anon', 'public.chat_profile_blocks', 'INSERT,UPDATE,DELETE') as anon_block_table_denied,
        not has_table_privilege('authenticated', 'public.chat_profile_blocks', 'INSERT,UPDATE,DELETE') as authenticated_block_table_denied,
        not has_sequence_privilege('anon', 'public.ugc_reports_id_seq', 'USAGE,SELECT,UPDATE') as anon_report_sequence_denied,
        not has_sequence_privilege('authenticated', 'public.ugc_reports_id_seq', 'USAGE,SELECT,UPDATE') as authenticated_report_sequence_denied
    `)).rows[0] ?? {};
    if (Object.values(privileges).some((value) => value !== true)) {
      throw new Error("selective_release_profile_safety_permissions_postcondition_failed");
    }
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
      if (process.env.QUATA_SELECTIVE_RELEASE_TEST_MODE !== "1") {
        const selectedVersions = pkg.selected.map(({ version }) => version);
        const installedVersions = [...pkg.anchors, ...pkg.selected].map(({ version }) => version);
        await assertProductPostconditions(client, selectedVersions, installedVersions);
      }
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
