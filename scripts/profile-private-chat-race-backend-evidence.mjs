#!/usr/bin/env node
import { createHash, randomInt, randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, relative, resolve } from "node:path";
import pg from "pg";

const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";

function parseArgs(argv) {
  if (argv.length === 2 && argv[0] === "--out" && argv[1].trim()) return { output: resolve(argv[1]) };
  if (argv.length === 1 && argv[0] === "--help") {
    console.log("Usage: node scripts/profile-private-chat-race-backend-evidence.mjs --out <safe-local-report.json>");
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
    "fixture_create_failed",
    "race_barrier_failed",
    "race_contract_failed",
    "cleanup_residue_detected",
  ];
  return {
    error: known.find((prefix) => message.startsWith(prefix)) ?? "unexpected_private_chat_race_failure",
    reason: message
      .replace(/postgres(?:ql)?:\/\/[^\s]+/gi, "[REDACTED_DB_URL]")
      .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/gi, "[uuid]")
      .slice(0, 300),
  };
}

async function connectionOptions(applicationName) {
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlPath, "utf8"), readFile(tlsCaPath, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  return {
    connectionString: parsedConnection.toString(),
    application_name: applicationName,
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  };
}

async function client(applicationName) {
  const connection = new pg.Client(await connectionOptions(applicationName));
  await connection.connect();
  return connection;
}

function threadId(payload) {
  const value = Number(payload?.thread?.id ?? payload?.threads?.[0]?.id ?? payload?.thread_id ?? payload?.id);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("race_contract_failed:thread_id");
  return value;
}

async function writeReport(output, payload) {
  const target = resolve(output);
  const workspace = resolve(process.cwd());
  if (relative(workspace, target).startsWith("..")) throw new Error("unsafe_report_path");
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(payload, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`PROFILE-PRIVATE-CHAT-RACE report written: ${target}`);
}

async function waitForBlockedWorkers(control, workerPids) {
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    const waiting = await control.query(
      `select count(*)::int as count
         from pg_stat_activity
        where pid = any($1::int[])
          and state = 'active'
          and wait_event_type = 'Lock'`,
      [workerPids],
    );
    if (Number(waiting.rows[0]?.count) === workerPids.length) return workerPids.length;
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 50));
  }
  throw new Error("race_barrier_failed:workers_not_blocked");
}

async function main() {
  const { output } = parseArgs(process.argv.slice(2));
  const startedAt = new Date().toISOString();
  const candidateSha = execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim();
  const runId = randomUUID();
  const marker = `qadata-private-race-${runId}`;
  const profileA = randomUUID();
  const profileB = randomUUID();
  const phoneSeed = String(randomInt(10_000_000, 99_999_999));
  const applicationNames = [`quata-race-a-${runId.slice(0, 8)}`, `quata-race-b-${runId.slice(0, 8)}`];
  const connections = [];
  let created = false;
  let control;
  let controlTransactionOpen = false;
  let calls;
  let result;
  let cleanup = { state: "not_started" };

  try {
    const setup = await client(`quata-race-setup-${runId.slice(0, 8)}`);
    connections.push(setup);
    const preflight = await setup.query("select current_database() as database, current_setting('server_version_num') as version");
    if (!preflight.rows[0]?.database || !preflight.rows[0]?.version) throw new Error("database_preflight_failed:fingerprint");

    await setup.query("begin");
    try {
      const values = [
        [profileA, `${marker}-a`, `${phoneSeed}1`],
        [profileB, `${marker}-b`, `${phoneSeed}2`],
      ];
      for (const [id, displayName, phoneLocal] of values) {
        await setup.query(
          `insert into public.community_profiles
            (id, display_name, phone, pass_hash, phone_normalized, country_code, phone_local, phone_e164, neighborhood, barrio, barrio_normalized, account_status)
           values ($1, $2, $3, $4, $5, '240', $6, $7, 'QADATA', 'QADATA', 'qadata', 'active')`,
          [id, displayName, `+240 ${phoneLocal}`, `${marker}-no-login`, `240${phoneLocal}`, phoneLocal, `+240${phoneLocal}`],
        );
      }
      await setup.query("commit");
      created = true;
    } catch (error) {
      await setup.query("rollback").catch(() => {});
      throw new Error(`fixture_create_failed:${error?.code ?? "unknown"}`);
    }

    control = await client(`quata-race-control-${runId.slice(0, 8)}`);
    const workerA = await client(applicationNames[0]);
    const workerB = await client(applicationNames[1]);
    connections.push(control, workerA, workerB);
    const workerPidRows = await Promise.all([
      workerA.query("select pg_backend_pid()::int as pid"),
      workerB.query("select pg_backend_pid()::int as pid"),
    ]);
    const workerPids = workerPidRows.map((response) => Number(response.rows[0]?.pid));
    if (workerPids.some((pid) => !Number.isSafeInteger(pid) || pid <= 0)) throw new Error("database_preflight_failed:worker_pid");

    await control.query("begin");
    controlTransactionOpen = true;
    await control.query("lock table public.chat_private_threads in access exclusive mode");
    const call = (connection, actor, peer) => connection.query(
      "select public.quata_chat_get_or_create_private_thread($1::uuid, $2::uuid) as payload",
      [actor, peer],
    );
    calls = Promise.all([call(workerA, profileA, profileB), call(workerB, profileB, profileA)]);
    const blockedWorkers = await waitForBlockedWorkers(control, workerPids);
    await control.query("commit");
    controlTransactionOpen = false;
    const responses = await calls;
    const ids = responses.map((response) => threadId(response.rows[0]?.payload));
    if (new Set(ids).size !== 1) throw new Error("race_contract_failed:divergent_thread_ids");

    const snapshot = await setup.query(
      `select
         (select count(*)::int from public.chat_private_threads where profile_low_id = least($1::uuid, $2::uuid) and profile_high_id = greatest($1::uuid, $2::uuid)) as private_threads,
         (select count(*)::int from public.chat_participants where thread_id = $3) as participants,
         (select count(*)::int from public.chat_events where thread_id = $3 and event_type = 'private_thread_opened') as open_events`,
      [profileA, profileB, ids[0]],
    );
    const counts = snapshot.rows[0] ?? {};
    if (Number(counts.private_threads) !== 1 || Number(counts.participants) !== 2 || Number(counts.open_events) !== 2) {
      throw new Error("race_contract_failed:database_cardinality");
    }
    result = {
      status: "passed",
      candidateSha,
      startedAt,
      completedAt: new Date().toISOString(),
      databaseFingerprintSha256: sha256(`${preflight.rows[0].database}:${preflight.rows[0].version}`),
      fixtureMarkerSha256: sha256(marker),
      barrier: { blockedWorkers, releasedTogether: true },
      observations: {
        calls: 2,
        distinctActors: 2,
        distinctReturnedThreadIds: new Set(ids).size,
        privateThreadRows: Number(counts.private_threads),
        participantRows: Number(counts.participants),
        privateThreadOpenedEvents: Number(counts.open_events),
        threadIdSha256: sha256(String(ids[0])),
      },
    };
  } catch (error) {
    if (controlTransactionOpen) {
      await control?.query("rollback").catch(() => {});
      controlTransactionOpen = false;
    }
    if (calls) await calls.catch(() => {});
    result = { status: "failed", candidateSha, startedAt, completedAt: new Date().toISOString(), ...safeFailure(error) };
  } finally {
    if (created) {
      try {
        const cleanupClient = connections[0] ?? await client(`quata-race-cleanup-${runId.slice(0, 8)}`);
        await cleanupClient.query("begin");
        const ownedProfiles = await cleanupClient.query(
          "select id from public.community_profiles where id = any($1::uuid[]) and display_name like $2 for update",
          [[profileA, profileB], `${marker}%`],
        );
        if (ownedProfiles.rowCount !== 2) throw new Error("cleanup_residue_detected:fixture_ownership");
        const threads = await cleanupClient.query(
          "select thread_id from public.chat_private_threads where profile_low_id = least($1::uuid, $2::uuid) and profile_high_id = greatest($1::uuid, $2::uuid) for update",
          [profileA, profileB],
        );
        for (const row of threads.rows) await cleanupClient.query("delete from public.chat_threads where id = $1", [row.thread_id]);
        await cleanupClient.query("delete from public.community_profiles where id = any($1::uuid[])", [[profileA, profileB]]);
        const residue = await cleanupClient.query(
          `select
             (select count(*)::int from public.community_profiles where id = any($1::uuid[])) as profiles,
             (select count(*)::int from public.chat_private_threads where profile_low_id = any($1::uuid[]) or profile_high_id = any($1::uuid[])) as private_threads,
             (select count(*)::int from public.chat_participants where profile_id = any($1::uuid[])) as participants`,
          [[profileA, profileB]],
        );
        const counts = residue.rows[0] ?? {};
        if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:physical_rows");
        await cleanupClient.query("commit");
        cleanup = { state: "passed", residueCounts: Object.fromEntries(Object.entries(counts).map(([key, value]) => [key, Number(value)])) };
      } catch (error) {
        cleanup = { state: "failed", ...safeFailure(error) };
        try { await connections[0]?.query("rollback"); } catch {}
      }
    }
    await Promise.all(connections.map((connection) => connection.end().catch(() => {})));
  }

  const finalReport = { ...result, cleanup };
  if (cleanup.state !== "passed") finalReport.status = "failed";
  await writeReport(output, finalReport);
  if (finalReport.status !== "passed") process.exitCode = 1;
}

main().catch(async (error) => {
  const parsed = process.argv.slice(2);
  const outputIndex = parsed.indexOf("--out");
  const fallback = outputIndex >= 0 && parsed[outputIndex + 1] ? parsed[outputIndex + 1] : "build-reports/profile-private-chat-race-fatal.json";
  await writeReport(fallback, { status: "failed", ...safeFailure(error), cleanup: { state: "not_started" } }).catch(() => {});
  process.exitCode = 1;
});
