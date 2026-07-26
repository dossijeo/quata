#!/usr/bin/env node
/**
 * Read-only database release evidence for Quata.
 *
 * This script never accepts a connection string on the command line, never
 * queries business-row values and forces PostgreSQL read-only transactions.
 */
import { createRequire } from "node:module";
import { createHash } from "node:crypto";
import { readdir, readFile, mkdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { buildSb01TlsConnection, loadSb01CertificateAuthority } from "./supabase-e2e-sb01-tls.mjs";

const require = createRequire(import.meta.url);
let Client;
try {
  ({ Client } = require("pg"));
} catch {
  console.error("db-release-safety requires pg; use scripts/run-db-release-safety.ps1.");
  process.exit(2);
}

const ROOT = resolve(import.meta.dirname, "..");
const MIGRATIONS = join(ROOT, "supabase", "migrations");
const ANDROID_TABLES = [
  "chat_attachments", "chat_messages", "chat_participants", "chat_threads",
  "community_comments", "community_post_likes", "community_posts",
  "community_profiles", "official_post_likes", "official_posts",
];
const ANDROID_RPCS = [
  "quata_chat_add_participants", "quata_chat_block_participant",
  "quata_chat_change_subject", "quata_chat_check_new",
  "quata_chat_cleanup_empty_private_thread", "quata_chat_delete_messages",
  "quata_chat_delete_thread", "quata_chat_demote_moderator",
  "quata_chat_edit_message", "quata_chat_forward_message",
  "quata_chat_get_favorites", "quata_chat_get_inbox",
  "quata_chat_get_or_create_private_thread", "quata_chat_get_thread",
  "quata_chat_leave_thread", "quata_chat_list_attachments",
  "quata_chat_list_shared_attachments", "quata_chat_mark_messages_state",
  "quata_chat_mark_thread_read", "quata_chat_match_registered_contacts",
  "quata_chat_open_community_thread", "quata_chat_promote_moderator",
  "quata_chat_register_attachment", "quata_chat_remove_participant",
  "quata_chat_restore_thread", "quata_chat_search_conversation_candidates",
  "quata_chat_send_message", "quata_chat_send_sos",
  "quata_chat_set_favorite", "quata_chat_set_member_invites_enabled",
  "quata_chat_set_muted", "quata_chat_start_thread",
];
const FEED_TABLES = ["community_posts", "community_profiles", "community_walls"];
const RELEASE_TABLES = [
  "community_comments", "community_post_likes", "community_posts", "community_profiles",
  "official_post_comments", "official_post_likes", "official_posts",
];

function parseArgs(argv) {
  let phase = "preflight";
  let output = "build-reports/db-release-safety/preflight.json";
  let expected = [];
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === "--phase") phase = argv[++index];
    else if (value === "--out") output = argv[++index];
    else if (value === "--expected-migration") expected.push(argv[++index]);
    else throw new Error("invalid_arguments");
  }
  if (!["snapshot", "preflight", "postflight"].includes(phase)) throw new Error("invalid_phase");
  if (!output?.trim()) throw new Error("missing_output");
  if (phase === "postflight" && expected.length === 0) throw new Error("postflight_requires_expected_migration");
  return { phase, output, expected };
}

async function localMigrationInventory() {
  const names = (await readdir(MIGRATIONS)).filter((name) => name.endsWith(".sql")).sort();
  return Promise.all(names.map(async (name) => {
    const bytes = await readFile(join(MIGRATIONS, name));
    const stem = name.replace(/\.sql$/, "");
    return {
      file: name,
      version: stem,
      cliVersion: stem.split("_", 1)[0],
      sha256: createHash("sha256").update(bytes).digest("hex"),
    };
  }));
}

function namesMissing(found, expected) {
  const set = new Set(found);
  return expected.filter((name) => !set.has(name));
}

function safeFailure(error) {
  const code = typeof error?.code === "string" ? error.code : "unknown";
  return `database_release_safety_failed:${code}`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const startedAt = new Date().toISOString();
  let client;
  try {
    if (!process.env.SUPABASE_DB_URL) throw new Error("missing_database_url");
    const ca = await loadSb01CertificateAuthority();
    client = new Client({
      ...buildSb01TlsConnection(process.env.SUPABASE_DB_URL, ca),
      application_name: `quata-db-release-${args.phase}`,
      connectionTimeoutMillis: 15_000,
      query_timeout: 20_000,
      options: "-c default_transaction_read_only=on -c statement_timeout=20000",
    });
    await client.connect();
    await client.query("BEGIN TRANSACTION READ ONLY");
    await client.query("SET LOCAL TRANSACTION READ ONLY");

    const serverVersion = (await client.query(
      "SELECT current_setting('server_version') AS version",
    )).rows[0]?.version ?? "unknown";
    const remoteMigrations = (await client.query(
      "SELECT version::text AS version, coalesce(name, '') AS name FROM supabase_migrations.schema_migrations ORDER BY version",
    )).rows;
    const tables = (await client.query(
      "SELECT c.relname AS name, c.relrowsecurity AS rls_enabled FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p') AND c.relname = ANY($1::text[]) ORDER BY c.relname",
      [ANDROID_TABLES],
    )).rows;
    const rpcs = (await client.query(
      "SELECT p.proname AS name, pg_get_function_identity_arguments(p.oid) AS arguments, pg_get_function_result(p.oid) AS result FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname = ANY($1::text[]) ORDER BY p.proname, arguments",
      [ANDROID_RPCS],
    )).rows;
    const feedPolicies = (await client.query(
      "SELECT tablename, policyname, roles, cmd, qual FROM pg_catalog.pg_policies WHERE schemaname='public' AND tablename = ANY($1::text[]) ORDER BY tablename, policyname",
      [FEED_TABLES],
    )).rows;
    const feedGrants = (await client.query(
      "SELECT table_name, privilege_type FROM information_schema.role_table_grants WHERE table_schema='public' AND grantee='anon' AND table_name = ANY($1::text[]) ORDER BY table_name, privilege_type",
      [FEED_TABLES],
    )).rows;
    const releasePolicies = (await client.query(
      "SELECT tablename, policyname, roles, cmd, permissive, qual, with_check FROM pg_catalog.pg_policies WHERE schemaname='public' AND tablename = ANY($1::text[]) ORDER BY tablename, policyname",
      [RELEASE_TABLES],
    )).rows;
    const releaseGrants = (await client.query(
      "SELECT table_name, grantee, privilege_type, is_grantable FROM information_schema.role_table_grants WHERE table_schema='public' AND table_name = ANY($1::text[]) ORDER BY table_name, grantee, privilege_type",
      [RELEASE_TABLES],
    )).rows;
    const releaseTriggers = (await client.query(
      "SELECT c.relname AS table_name, t.tgname AS trigger_name, pg_get_triggerdef(t.oid, true) AS definition, p.proname AS function_name, pg_get_function_identity_arguments(p.oid) AS function_arguments, p.prosecdef AS security_definer, position('current_user' in lower(pg_get_functiondef(p.oid))) > 0 AS uses_current_user, position('quata_current_role_is_service' in lower(pg_get_functiondef(p.oid))) > 0 AS calls_service_role_check, pg_get_functiondef(p.oid) AS function_definition FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid=t.tgrelid JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace JOIN pg_catalog.pg_proc p ON p.oid=t.tgfoid WHERE n.nspname='public' AND c.relname = ANY($1::text[]) AND NOT t.tgisinternal ORDER BY c.relname, t.tgname",
      [RELEASE_TABLES],
    )).rows;

    await client.query("SET LOCAL ROLE anon");
    const anonymousFeed = (await client.query(
      "SELECT EXISTS (SELECT 1 FROM public.community_posts LIMIT 1) AS has_visible_post",
    )).rows[0];
    await client.query("RESET ROLE");
    await client.query("ROLLBACK");

    const localMigrations = await localMigrationInventory();
    const localByCliVersion = localMigrations.reduce((groups, migration) => {
      (groups[migration.cliVersion] ??= []).push(migration);
      return groups;
    }, {});
    const cliVersionCollisions = Object.entries(localByCliVersion)
      .filter(([, migrations]) => migrations.length > 1)
      .map(([cliVersion, migrations]) => ({
        cliVersion,
        files: migrations.map((migration) => migration.file),
      }));
    const remoteStems = new Set(remoteMigrations.map((row) => `${row.version}_${row.name}`.replace(/_$/, "")));
    const untrackedLocal = localMigrations
      .filter((migration) => !remoteStems.has(migration.version))
      .map((migration) => migration.file);
    const tableMissing = namesMissing(tables.map((row) => row.name), ANDROID_TABLES);
    const rpcMissing = namesMissing(rpcs.map((row) => row.name), ANDROID_RPCS);
    const feedSelectTables = new Set(
      feedGrants.filter((row) => row.privilege_type === "SELECT").map((row) => row.table_name),
    );
    const feedGrantMissing = FEED_TABLES.filter((name) => !feedSelectTables.has(name));
    const remoteVersions = new Set(remoteMigrations.flatMap((row) => [row.version, row.name].filter(Boolean)));
    const expectedMissing = args.expected.filter((version) => !remoteVersions.has(version));
    const actorContextRisks = releaseTriggers
      .filter((trigger) => trigger.security_definer
        && (trigger.uses_current_user || trigger.calls_service_role_check))
      .map((trigger) => ({
        tableName: trigger.table_name,
        triggerName: trigger.trigger_name,
        functionName: trigger.function_name,
      }));
    const unrestrictedMutationPolicies = releasePolicies
      .filter((policy) => ["INSERT", "UPDATE", "DELETE", "ALL"].includes(policy.cmd))
      .filter((policy) => policy.roles.includes("public") || policy.roles.includes("anon"))
      .filter((policy) => policy.qual === "true"
        || policy.with_check === "true"
        || (policy.cmd === "INSERT" && policy.with_check === null)
        || (policy.cmd === "UPDATE" && policy.qual === "true" && policy.with_check === null))
      .map((policy) => ({
        tableName: policy.tablename,
        policyName: policy.policyname,
        roles: policy.roles,
        command: policy.cmd,
        using: policy.qual,
        withCheck: policy.with_check,
      }));
    const officialLikeGuardStillRisky = actorContextRisks
      .some((risk) => risk.tableName === "official_post_likes");
    const officialLikePostflightFailed = args.phase === "postflight"
      && args.expected.includes("20260726171002")
      && officialLikeGuardStillRisky;
    const compatibilityPassed = tableMissing.length === 0
      && rpcMissing.length === 0
      && feedGrantMissing.length === 0
      && expectedMissing.length === 0
      && anonymousFeed?.has_visible_post === true
      && !officialLikePostflightFailed;
    const historySafeForPush = cliVersionCollisions.length === 0 && untrackedLocal.length === 0;
    const status = compatibilityPassed && (args.phase === "snapshot" || historySafeForPush)
      ? "passed"
      : compatibilityPassed
        ? "blocked_history_reconciliation"
        : "blocked_compatibility";
    const report = {
      check: "DB-RELEASE-SAFETY",
      phase: args.phase,
      startedAt,
      finishedAt: new Date().toISOString(),
      status,
      database: { serverVersion },
      migrationHistory: {
        remote: remoteMigrations,
        local: localMigrations,
        cliVersionCollisions,
        untrackedLocal,
        safeForSupabaseDbPush: historySafeForPush,
        deploymentConstraint: historySafeForPush
          ? "No migration-ledger drift detected."
          : "Do not run supabase db push: it could re-execute untracked historical SQL.",
        expected: args.expected,
        expectedMissing,
      },
      androidCompatibility: {
        requiredTables: ANDROID_TABLES,
        missingTables: tableMissing,
        requiredRpcs: ANDROID_RPCS,
        missingRpcs: rpcMissing,
        signatures: rpcs,
      },
      anonymousFeedGate: {
        requiredSelectTables: FEED_TABLES,
        missingSelectGrants: feedGrantMissing,
        policies: feedPolicies,
        hasVisiblePost: anonymousFeed?.has_visible_post === true,
        navigationGate: "Run the versioned Web browser smoke on #feed; database evidence alone cannot prove browser navigation.",
      },
      releaseContractSnapshot: {
        tables: RELEASE_TABLES,
        policies: releasePolicies,
        grants: releaseGrants,
        triggers: releaseTriggers.map((trigger) => ({
          tableName: trigger.table_name,
          triggerName: trigger.trigger_name,
          definition: trigger.definition,
          functionName: trigger.function_name,
          functionArguments: trigger.function_arguments,
          securityDefiner: trigger.security_definer,
          usesCurrentUser: trigger.uses_current_user,
          callsServiceRoleCheck: trigger.calls_service_role_check,
          actorContextRisk: trigger.security_definer
            && (trigger.uses_current_user || trigger.calls_service_role_check),
          functionDefinitionSha256: createHash("sha256").update(trigger.function_definition).digest("hex"),
        })),
        note: "Function bodies and business rows are deliberately excluded. Rollback SQL must be reviewed and versioned with each migration.",
      },
      triggerActorContextAudit: {
        risks: actorContextRisks,
        rule: "SECURITY DEFINER trigger function that reads current_user directly or through quata_current_role_is_service.",
        officialLikePostflightFailed,
        scope: "Inventory only. A finding does not authorize expanding the deployment.",
      },
      publicMutationAudit: {
        unrestrictedPolicies: unrestrictedMutationPolicies,
        rule: "Public/anon mutation policy with unconditional USING/WITH CHECK or a missing INSERT/UPDATE check.",
        scope: "Metadata-only exploitability assessment; no production DML was attempted.",
      },
      guarantees: {
        tls: "verify-full with one explicit CA",
        transaction: "read-only",
        businessValuesEmitted: false,
        secretsEmitted: false,
        deployed: false,
      },
    };
    const output = resolve(args.output);
    await mkdir(dirname(output), { recursive: true });
    await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
    console.log(`DB release ${args.phase}: ${status}; report=${output}`);
    process.exitCode = status === "passed" ? 0 : 1;
  } catch (error) {
    console.error(JSON.stringify({
      check: "DB-RELEASE-SAFETY",
      phase: args?.phase ?? "unknown",
      startedAt,
      finishedAt: new Date().toISOString(),
      status: "failed",
      error: safeFailure(error),
    }));
    process.exitCode = 1;
  } finally {
    await client?.end().catch(() => undefined);
  }
}

main();
