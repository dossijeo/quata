#!/usr/bin/env node
import { createHash, randomInt, randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, relative, resolve } from "node:path";
import pg from "pg";

const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";

function parseArgs(argv) {
  const previewMigration = argv.includes("--migration-preview");
  const filtered = argv.filter((value) => value !== "--migration-preview");
  if (filtered.length === 2 && filtered[0] === "--out" && filtered[1].trim()) {
    return { output: resolve(filtered[1]), previewMigration };
  }
  if (argv.length === 1 && argv[0] === "--help") {
    console.log("Usage: node scripts/profile-safety-permissions-backend-evidence.mjs --out <safe-local-report.json> [--migration-preview]");
    process.exit(0);
  }
  throw new Error("invalid_arguments");
}

function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

function safeFailure(error) {
  const message = String(error?.message ?? error);
  const known = [
    "invalid_arguments",
    "unsafe_report_path",
    "database_preflight_failed",
    "permission_contract_failed",
    "cleanup_residue_detected",
  ];
  return {
    error: known.find((prefix) => message.startsWith(prefix)) ?? "unexpected_profile_safety_permissions_failure",
    reason: message
      .replace(/postgres(?:ql)?:\/\/[^\s]+/gi, "[REDACTED_DB_URL]")
      .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/gi, "[uuid]")
      .slice(0, 300),
  };
}

async function connectionOptions() {
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlPath, "utf8"), readFile(tlsCaPath, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  return {
    connectionString: parsedConnection.toString(),
    application_name: "quata-profile-safety-permissions-evidence",
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  };
}

async function writeReport(output, payload) {
  const target = resolve(output);
  const workspace = resolve(process.cwd());
  const workspaceRelative = relative(workspace, target);
  if (workspaceRelative.startsWith("..") || isAbsolute(workspaceRelative)) throw new Error("unsafe_report_path");
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(payload, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`PROFILE-SAFETY-PERMISSIONS report written: ${target}`);
}

async function expectRejected(client, label, sql, params, expectedCodes) {
  const savepoint = `qadata_${label.replace(/[^a-z0-9_]/gi, "_")}`;
  await client.query(`savepoint ${savepoint}`);
  try {
    await client.query(sql, params);
    throw new Error(`permission_contract_failed:${label}_accepted`);
  } catch (error) {
    if (String(error?.message ?? "").startsWith("permission_contract_failed:")) throw error;
    if (!expectedCodes.includes(error?.code)) {
      throw new Error(`permission_contract_failed:${label}_unexpected_sqlstate_${error?.code ?? "unknown"}`);
    }
  } finally {
    await client.query(`rollback to savepoint ${savepoint}`).catch(() => {});
  }
}

async function main() {
  const { output, previewMigration } = parseArgs(process.argv.slice(2));
  const candidateSha = execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim();
  const startedAt = new Date().toISOString();
  const runId = randomUUID();
  const marker = `qadata-profile-safety-${runId}`;
  const authUserA = randomUUID();
  const authUserB = randomUUID();
  const profileA = randomUUID();
  const profileB = randomUUID();
  const phoneSeed = String(randomInt(10_000_000, 99_999_999));
  const client = new pg.Client(await connectionOptions());
  let transactionOpen = false;
  let result;
  let stage = "connect";

  try {
    await client.connect();
    stage = "preflight";
    const preflight = await client.query("select current_database() as database, current_setting('server_version_num') as version");
    if (!preflight.rows[0]?.database || !preflight.rows[0]?.version) throw new Error("database_preflight_failed:fingerprint");

    await client.query("begin");
    transactionOpen = true;
    stage = "migration_preview";
    if (previewMigration) {
      const source = await readFile("supabase/migrations/20261009073000_profile_safety_actor_permissions.sql", "utf8");
      const transactionalBody = source.replace(/^\s*begin;\s*/i, "").replace(/\s*commit;\s*$/i, "");
      await client.query(transactionalBody);
    }
    stage = "fixture_setup";
    for (const [authUserId, profileId, suffix] of [[authUserA, profileA, "a"], [authUserB, profileB, "b"]]) {
      await client.query("insert into auth.users(id) values ($1)", [authUserId]);
      const phoneLocal = `${phoneSeed}${suffix === "a" ? "1" : "2"}`;
      await client.query(
        `insert into public.community_profiles
          (id, auth_user_id, display_name, phone, pass_hash, phone_normalized, country_code, phone_local, phone_e164, neighborhood, barrio, barrio_normalized, account_status)
         values ($1, $2, $3, $4, $5, $6, '240', $7, $8, 'QADATA', 'QADATA', 'qadata', 'active')`,
        [profileId, authUserId, `${marker}-${suffix}`, `+240 ${phoneLocal}`, `${marker}-no-login`, `240${phoneLocal}`, phoneLocal, `+240${phoneLocal}`],
      );
    }
    await client.query(
      "insert into public.chat_profile_blocks(thread_id, blocker_profile_id, blocked_profile_id) values (null, $1, $2)",
      [profileB, profileA],
    );

    stage = "acl_snapshot";
    const acl = await client.query(
      `select
         not has_function_privilege('anon', 'public.quata_ugc_report(uuid,text,text,text,text)', 'EXECUTE') as anon_report_denied,
         not has_function_privilege('anon', 'public.quata_profile_block(uuid,uuid)', 'EXECUTE') as anon_block_denied,
         not has_function_privilege('anon', 'public.quata_profile_unblock(uuid,uuid)', 'EXECUTE') as anon_unblock_denied,
         has_function_privilege('authenticated', 'public.quata_ugc_report(uuid,text,text,text,text)', 'EXECUTE') as authenticated_report_allowed,
         has_function_privilege('authenticated', 'public.quata_profile_block(uuid,uuid)', 'EXECUTE') as authenticated_block_allowed,
         has_function_privilege('authenticated', 'public.quata_profile_unblock(uuid,uuid)', 'EXECUTE') as authenticated_unblock_allowed,
         not has_table_privilege('anon', 'public.ugc_reports', 'INSERT,UPDATE,DELETE') as anon_report_table_mutation_denied,
         not has_table_privilege('authenticated', 'public.ugc_reports', 'INSERT,UPDATE,DELETE') as authenticated_report_table_mutation_denied,
         not has_table_privilege('anon', 'public.chat_profile_blocks', 'INSERT,UPDATE,DELETE') as anon_block_table_mutation_denied,
         not has_table_privilege('authenticated', 'public.chat_profile_blocks', 'INSERT,UPDATE,DELETE') as authenticated_block_table_mutation_denied,
         not has_sequence_privilege('anon', 'public.ugc_reports_id_seq', 'USAGE,SELECT,UPDATE') as anon_report_sequence_denied,
         not has_sequence_privilege('authenticated', 'public.ugc_reports_id_seq', 'USAGE,SELECT,UPDATE') as authenticated_report_sequence_denied`,
    );
    if (Object.values(acl.rows[0] ?? {}).some((value) => value !== true)) {
      throw new Error("permission_contract_failed:least_privilege_acl");
    }

    stage = "anonymous_denials";
    await client.query("set local role anon");
    await expectRejected(
      client,
      "anonymous_report",
      "select public.quata_ugc_report($1::uuid, 'profile', $2::text, 'other', null)",
      [profileA, profileB],
      ["42501"],
    );
    await expectRejected(
      client,
      "anonymous_block",
      "select public.quata_profile_block($1::uuid, $2::uuid)",
      [profileA, profileB],
      ["42501"],
    );

    stage = "authenticated_denials";
    await client.query("reset role");
    await client.query("set local role authenticated");
    await client.query("select set_config('request.jwt.claim.sub', $1::text, true), set_config('request.jwt.claim.role', 'authenticated', true)", [authUserA]);
    await expectRejected(
      client,
      "spoofed_report_actor",
      "select public.quata_ugc_report($1::uuid, 'profile', $2::text, 'other', null)",
      [profileB, profileB],
      ["42501"],
    );
    await expectRejected(
      client,
      "spoofed_block_actor",
      "select public.quata_profile_block($1::uuid, $2::uuid)",
      [profileB, profileB],
      ["42501"],
    );
    await expectRejected(
      client,
      "self_report",
      "select public.quata_ugc_report($1::uuid, 'profile', $1::text, 'other', null)",
      [profileA],
      ["P0001"],
    );
    await expectRejected(
      client,
      "self_block",
      "select public.quata_profile_block($1::uuid, $1::uuid)",
      [profileA],
      ["P0001"],
    );

    stage = "actor_owned_mutations";
    const acceptedReport = await client.query(
      "select public.quata_ugc_report($1::uuid, 'profile', $2::text, 'other', null) as payload",
      [profileA, profileB],
    );
    const acceptedBlock = await client.query(
      "select public.quata_profile_block($1::uuid, $2::uuid) as payload",
      [profileA, profileB],
    );
    if (acceptedReport.rows[0]?.payload?.ok !== true || acceptedBlock.rows[0]?.payload?.ok !== true) {
      throw new Error("permission_contract_failed:actor_owned_mutation");
    }
    await expectRejected(
      client,
      "spoofed_unblock_actor",
      "select public.quata_profile_unblock($1::uuid, $2::uuid)",
      [profileB, profileA],
      ["42501"],
    );
    const preservedAfterSpoof = await client.query(
      "select count(*)::int as count from public.chat_profile_blocks where thread_id is null and blocker_profile_id = $1 and blocked_profile_id = $2",
      [profileB, profileA],
    );
    if (Number(preservedAfterSpoof.rows[0]?.count) !== 1) {
      throw new Error("permission_contract_failed:spoofed_unblock_changed_foreign_edge");
    }
    const acceptedUnblock = await client.query(
      "select public.quata_profile_unblock($1::uuid, $2::uuid) as payload",
      [profileA, profileB],
    );
    if (acceptedUnblock.rows[0]?.payload?.ok !== true) {
      throw new Error("permission_contract_failed:actor_owned_unblock");
    }

    stage = "inside_transaction_snapshot";
    await client.query("reset role");
    const inside = await client.query(
      `select
         (select count(*)::int from public.ugc_reports where reporter_profile_id = $1 and target_type = 'profile' and target_id = $2::text) as reports,
         (select count(*)::int from public.chat_profile_blocks where thread_id is null and blocker_profile_id = $1 and blocked_profile_id = $2::uuid) as blocks`,
      [profileA, profileB],
    );
    if (Number(inside.rows[0]?.reports) !== 1 || Number(inside.rows[0]?.blocks) !== 0) {
      throw new Error("permission_contract_failed:actor_owned_rows");
    }

    stage = "rollback";
    await client.query("rollback");
    transactionOpen = false;
    const residue = await client.query(
      `select
         (select count(*)::int from public.community_profiles where id = any($1::uuid[])) as profiles,
         (select count(*)::int from auth.users where id = any($2::uuid[])) as users,
         (select count(*)::int from public.ugc_reports where reporter_profile_id = $3 or reported_profile_id = $4) as reports,
         (select count(*)::int from public.chat_profile_blocks where blocker_profile_id = $3 or blocked_profile_id = $4) as blocks`,
      [[profileA, profileB], [authUserA, authUserB], profileA, profileB],
    );
    if (Object.values(residue.rows[0] ?? {}).some((value) => Number(value) !== 0)) {
      throw new Error("cleanup_residue_detected:transactional_probe");
    }

    result = {
      status: "passed",
      candidateSha,
      startedAt,
      completedAt: new Date().toISOString(),
      databaseFingerprintSha256: sha256(`${preflight.rows[0].database}:${preflight.rows[0].version}`),
      fixtureMarkerSha256: sha256(marker),
      observations: {
        anonymousRpcDenied: true,
        spoofedActorDenied: true,
        selfTargetDenied: true,
        authenticatedActorOwnedReportAccepted: true,
        authenticatedActorOwnedBlockAccepted: true,
        spoofedActorUnblockDeniedAndForeignEdgePreserved: true,
        authenticatedActorOwnedUnblockAccepted: true,
        directReportTableMutationDenied: true,
      },
      cleanup: { state: "completed", transactionRolledBack: true, residueZero: true },
      migrationPreview: previewMigration,
    };
  } catch (error) {
    if (transactionOpen) await client.query("rollback").catch(() => {});
    result = { status: "failed", candidateSha, startedAt, completedAt: new Date().toISOString(), stage, ...safeFailure(error) };
  } finally {
    await client.end().catch(() => {});
  }

  await writeReport(output, result);
  if (result.status !== "passed") process.exitCode = 1;
}

await main();
