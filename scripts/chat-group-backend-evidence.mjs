#!/usr/bin/env node
import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, relative, resolve } from "node:path";
import pg from "pg";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const credentialsFileEnvironment = "QUATA_CHAT_GROUP_CREDENTIALS_FILE";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_GROUP_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_GROUP_HARD_CLEANUP";

function parseArgs(argv) {
  if (argv.length === 2 && argv[0] === "--out" && argv[1].trim()) return { output: resolve(argv[1]) };
  if (argv.length === 1 && argv[0] === "--help") {
    console.log("Usage: node scripts/chat-group-backend-evidence.mjs --out <safe-local-report.json>");
    process.exit(0);
  }
  throw new Error("invalid_arguments");
}

function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

async function publicBackendConfig() {
  const configuredUrl = process.env.QUATA_SUPABASE_URL?.trim();
  const configuredKey = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY?.trim();
  if (configuredUrl && configuredKey) return { baseUrl: configuredUrl.replace(/\/+$/, ""), key: configuredKey };
  const source = await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8");
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const key = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !key) throw new Error("missing_public_supabase_configuration");
  return { baseUrl, key };
}

function isPublicKey(value) {
  if (!value || value.startsWith("sb_secret_") || value.toLowerCase().includes("service_role")) return false;
  if (value.startsWith("sb_publishable_")) return true;
  const parts = value.split(".");
  if (parts.length !== 3) return false;
  try { return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"))?.role === "anon"; } catch { return false; }
}

async function usersFromPrivateFile() {
  const file = process.env[credentialsFileEnvironment]?.trim();
  if (!file) throw new Error("missing_chat_group_credentials_file");
  const parsed = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  const user = (entry, label) => ({
    label,
    countryCode: String(entry?.country_code ?? entry?.countryCode ?? "").trim(),
    phone: String(entry?.phone ?? "").trim(),
    password: String(entry?.password ?? ""),
  });
  const users = [user(parsed.a, "A"), user(parsed.b, "B")];
  if (users.some((candidate) => !candidate.countryCode || !candidate.phone || !candidate.password)) throw new Error("missing_chat_group_credentials");
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) throw new Error("chat_group_users_must_differ");
  return users;
}

function headers(config, token) {
  return {
    apikey: config.key,
    "content-type": "application/json",
    "x-client-info": "quata-chat-group-backend-evidence",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
}

async function jsonRequest(url, options, prefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(20_000) }); } catch { throw new Error(`${prefix}:network`); }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  try { return text ? JSON.parse(text) : {}; } catch { throw new Error(`${prefix}:invalid_json`); }
}

async function login(config, user) {
  const payload = await jsonRequest(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: headers(config),
    body: JSON.stringify({
      action: "web_login",
      country_code: user.countryCode,
      phone_local: user.phone,
      password: user.password,
      client_instance_id: `chat-group-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const profileId = payload?.profile?.id;
  const accessToken = payload?.session?.access_token;
  if (!uuid.test(profileId ?? "") || typeof accessToken !== "string" || !accessToken) throw new Error(`invalid_auth_response:${user.label}`);
  return { label: user.label, profileId, accessToken };
}

async function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
}

function threadId(payload) {
  const value = Number(payload?.thread?.id ?? payload?.threads?.[0]?.id ?? payload?.thread_id ?? payload?.id);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("chat_group_contract_invalid:thread_id");
  return value;
}

async function withDatabase(callback) {
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlPath, "utf8"), readFile(tlsCaPath, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try { return await callback(client); } finally { await client.end().catch(() => {}); }
}

async function createTemporaryParticipant(runId) {
  const id = randomUUID();
  const phoneLocal = String(930000000 + Math.floor(Math.random() * 50000000));
  const displayName = `QADATA Chat Group ${runId.slice(0, 8)}`;
  await withDatabase(async (client) => {
    await client.query("begin");
    try {
      await client.query(
        `insert into public.community_profiles
          (id, display_name, phone, pass_hash, phone_normalized, country_code, phone_local, phone_e164, neighborhood, barrio, barrio_normalized, account_status)
         values ($1, $2, $3, $4, $5, '240', $6, $7, 'QADATA', 'QADATA', 'qadata', 'active')`,
        [id, displayName, `+240 ${phoneLocal}`, `qadata-chat-group-no-login-${runId}`, `240${phoneLocal}`, phoneLocal, `+240${phoneLocal}`],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  return { id, displayName, phoneLocal };
}

async function participantSnapshot(thread) {
  return await withDatabase(async (client) => {
    const result = await client.query(
      `select profile_id::text, role, left_at is not null as left
         from public.chat_participants
        where thread_id = $1
        order by profile_id::text`,
      [thread],
    );
    return result.rows;
  });
}

function assertParticipant(snapshot, profileId, role, left = false) {
  const row = snapshot.find((entry) => entry.profile_id === profileId);
  if (!row || row.role !== role || row.left !== left) throw new Error(`chat_group_contract_invalid:participant:${role}:${left}`);
}

async function hardCleanup(state) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) throw new Error("missing_hard_cleanup_authorization");
  if (!state.uniqueKey.startsWith("qadata-chat-group-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const ownedThread = await client.query(
        "select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-group-%' for update",
        [state.thread, state.uniqueKey],
      );
      if (ownedThread.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_not_owned");
      const ownedProfile = await client.query(
        "select id from public.community_profiles where id = $1 and display_name = $2 and phone_local = $3 for update",
        [state.tempProfile.id, state.tempProfile.displayName, state.tempProfile.phoneLocal],
      );
      if (ownedProfile.rowCount !== 1) throw new Error("cleanup_residue_detected:temp_profile_not_owned");
      const deletedThread = await client.query("delete from public.chat_threads where id = $1 and unique_key = $2 returning id", [state.thread, state.uniqueKey]);
      if (deletedThread.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_delete_failed");
      const deletedProfile = await client.query(
        "delete from public.community_profiles where id = $1 and display_name = $2 and phone_local = $3 returning id",
        [state.tempProfile.id, state.tempProfile.displayName, state.tempProfile.phoneLocal],
      );
      if (deletedProfile.rowCount !== 1) throw new Error("cleanup_residue_detected:temp_profile_delete_failed");
      const residue = await client.query(
        `select
          (select count(*)::int from public.chat_threads where id = $1 or unique_key = $2) as chat_threads,
          (select count(*)::int from public.chat_messages where thread_id = $1) as chat_messages,
          (select count(*)::int from public.chat_participants where thread_id = $1 or profile_id = $3) as chat_participants,
          (select count(*)::int from public.chat_attachments where thread_id = $1) as chat_attachments,
          (select count(*)::int from public.chat_message_states where thread_id = $1) as chat_message_states,
          (select count(*)::int from public.chat_events where thread_id = $1) as chat_events,
          (select count(*)::int from public.chat_profile_blocks where thread_id = $1 or blocked_profile_id = $3 or blocker_profile_id = $3) as chat_profile_blocks,
          (select count(*)::int from public.conversation_user_state where conversation_id = $1) as conversation_user_state,
          (select count(*)::int from public.community_profiles where id = $3) as community_profiles`,
        [state.thread, state.uniqueKey, state.tempProfile.id],
      );
      const counts = residue.rows[0] ?? {};
      if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:physical_rows");
      await client.query("commit");
      return { threadId: state.thread, uniqueKeySha256: sha256(state.uniqueKey), tempProfileIdSha256: sha256(state.tempProfile.id), residueCounts: counts };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function report(output, payload) {
  const target = resolve(output);
  const workspace = resolve(process.cwd());
  if (relative(workspace, target).startsWith("..")) throw new Error("unsafe_report_path");
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(payload, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`CHAT-GROUP report written: ${target}`);
}

async function main() {
  const { output } = parseArgs(process.argv.slice(2));
  const startedAt = new Date().toISOString();
  const steps = [];
  const state = {};
  let config;
  try {
    config = await publicBackendConfig();
    if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
    if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");
    const users = await usersFromPrivateFile();
    state.a = await login(config, users[0]);
    state.b = await login(config, users[1]);
    steps.push("two_authorized_profiles_logged_in");
    const runId = randomUUID();
    state.uniqueKey = `qadata-chat-group-${runId}`;
    state.tempProfile = await createTemporaryParticipant(runId);
    steps.push("temporary_group_participant_created");
    state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
      p_actor_profile_id: state.a.profileId,
      p_recipient_profile_ids: [state.b.profileId],
      p_subject: `QADATA chat group ${runId}`,
      p_type: "group",
      p_message: "",
      p_unique_key: state.uniqueKey,
      p_community_id: null,
    }));
    let snapshot = await participantSnapshot(state.thread);
    assertParticipant(snapshot, state.a.profileId, "owner");
    assertParticipant(snapshot, state.b.profileId, "member");
    steps.push("owned_group_thread_created");
    await rpc(config, state.a, "quata_chat_set_member_invites_enabled", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_enabled: false });
    await rpc(config, state.a, "quata_chat_set_member_invites_enabled", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_enabled: true });
    steps.push("member_invites_toggled");
    await rpc(config, state.a, "quata_chat_add_participants", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_participant_profile_ids: [state.tempProfile.id] });
    snapshot = await participantSnapshot(state.thread);
    assertParticipant(snapshot, state.tempProfile.id, "member");
    steps.push("participants_added");
    await rpc(config, state.a, "quata_chat_promote_moderator", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_profile_id: state.tempProfile.id });
    snapshot = await participantSnapshot(state.thread);
    assertParticipant(snapshot, state.tempProfile.id, "moderator");
    await rpc(config, state.a, "quata_chat_demote_moderator", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_profile_id: state.tempProfile.id });
    snapshot = await participantSnapshot(state.thread);
    assertParticipant(snapshot, state.tempProfile.id, "member");
    steps.push("temporary_participant_promoted_and_demoted");
    await rpc(config, state.a, "quata_chat_remove_participant", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_profile_id: state.tempProfile.id });
    snapshot = await participantSnapshot(state.thread);
    assertParticipant(snapshot, state.tempProfile.id, "member", true);
    steps.push("temporary_participant_removed");
    await rpc(config, state.a, "quata_chat_block_participant", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_profile_id: state.tempProfile.id });
    steps.push("temporary_participant_blocked");
    await rpc(config, state.b, "quata_chat_leave_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread });
    await rpc(config, state.a, "quata_chat_delete_thread", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread });
    steps.push("peer_left_and_actor_deleted_thread_from_inbox");
    const cleanup = await hardCleanup(state);
    steps.push("cleanup_verified_physical_residue_absent");
    await report(output, {
      check: "CHAT-GROUP-BACKEND-001",
      status: "passed",
      startedAt,
      finishedAt: new Date().toISOString(),
      steps,
      cleanup,
      fixture: { threadId: state.thread, uniqueKeySha256: sha256(state.uniqueKey), tempProfileIdSha256: sha256(state.tempProfile.id) },
      mutationPolicy: "Public authenticated Chat RPCs for product mutations; pooler SQL only for uniquely-owned qadata-chat-group hard cleanup and residue verification.",
    });
  } catch (error) {
    console.error(JSON.stringify({ check: "CHAT-GROUP-BACKEND-001", status: "failed", error: String(error?.message ?? error), steps, startedAt, finishedAt: new Date().toISOString() }));
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(JSON.stringify({ check: "CHAT-GROUP-BACKEND-001", status: "failed", error: String(error?.message ?? error) }));
  process.exitCode = 1;
});
