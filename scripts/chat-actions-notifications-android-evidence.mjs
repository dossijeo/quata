import { spawn } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import pg from "pg";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP";
const tempProfileHashAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH_AUTHORIZATION";
const tempProfileHashAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH";
const credentialsFileEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_CREDENTIALS_FILE";
const profileOnly = process.argv.includes("--profile-only");
const profileFollowOnly = process.argv.includes("--profile-follow-only");
const useAdjacentAuthorizedProfile = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_USE_ADJACENT_AUTHORIZED_PROFILE === "1";
const deviceCredentialsPath = "app-internal:chat-actions-notifications-credentials.json";
const deviceTempCredentialsPath = "/data/local/tmp/chat-actions-notifications-credentials.json";
const deviceEvidencePath = "files/chat-actions-notifications-evidence";
const evidenceFiles = [
  "android-chat-translation-before.png",
  "android-chat-translation-overlay.png",
  "android-chat-translation-result.png",
  "android-chat-translation-return.png",
  "android-chat-actions-thread-initial.png",
  "android-chat-composer-sent.png",
  "android-chat-composer-reply-sent.png",
  "android-chat-composer-edit-mode.png",
  "android-chat-composer-edit-filled.png",
  "android-chat-composer-edit-submitted.png",
  "android-chat-composer-edit-sent.png",
  "android-chat-actions-own-selected.png",
  "android-chat-forward-picker-selected.png",
  "android-chat-forward-submitted.png",
  "android-chat-profile-thread-initial.png",
  "android-chat-profile-open.png",
  "android-chat-profile-return.png",
  "android-chat-profile-follow-before.png",
  "android-chat-profile-follow-after.png",
  "android-chat-profile-follow-return.png",
  "android-chat-actions-notifications-evidence.json",
];
const translationOnly = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_TRANSLATION_ONLY === "1";
let lastThreadSnapshot = null;

function env(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`missing_environment:${name}`);
  return value;
}

async function authorizedUsers() {
  if (!useAdjacentAuthorizedProfile) {
    const file = process.env[credentialsFileEnvironment]?.trim();
    if (file) {
      const parsed = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
      const user = (entry, label) => ({
        label,
        countryCode: String(entry?.country_code ?? entry?.countryCode ?? "").trim(),
        phone: String(entry?.phone ?? "").trim(),
        password: String(entry?.password ?? ""),
      });
      return { a: user(parsed.a, "A"), b: user(parsed.b, "B") };
    }
    return {
      a: { label: "A", countryCode: env("QUATA_CHAT_EVIDENCE_A_COUNTRY_CODE"), phone: env("QUATA_CHAT_EVIDENCE_A_PHONE"), password: env("QUATA_CHAT_EVIDENCE_A_PASSWORD") },
      b: { label: "B", countryCode: env("QUATA_CHAT_EVIDENCE_B_COUNTRY_CODE"), phone: env("QUATA_CHAT_EVIDENCE_B_PHONE"), password: env("QUATA_CHAT_EVIDENCE_B_PASSWORD") },
    };
  }
  const host = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_SSH_HOST?.trim()
    || process.env.QUATA_CHAT_EVIDENCE_SSH_HOST?.trim();
  const file = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_SSH_CREDENTIALS_FILE?.trim()
    || process.env.QUATA_CHAT_EVIDENCE_SSH_CREDENTIALS_FILE?.trim();
  if (!host || !file) throw new Error("missing_adjacent_profile_credentials_source");
  const credentials = JSON.parse(await runSilent("ssh", [host, `cat ${file}`]));
  const phone = splitPhone(credentials.phone);
  const previousLocal = (BigInt(phone.localPhone) - 1n).toString().padStart(phone.localPhone.length, "0");
  if (previousLocal.length !== phone.localPhone.length) throw new Error("invalid_adjacent_profile_phone");
  return {
    a: { label: "A", countryCode: phone.countryCode, phone: previousLocal, password: credentials.password },
    b: { label: "B", countryCode: phone.countryCode, phone: phone.localPhone, password: credentials.password, adjacentPhoneKeys: adjacentRecipientPhones(phone) },
  };
}

function splitPhone(phone) {
  const digits = String(phone ?? "").replace(/\D/g, "");
  if (!digits.startsWith("240") || digits.length <= 3) throw new Error("invalid_adjacent_profile_phone");
  return { countryCode: "240", localPhone: digits.slice(3), phoneKey: digits };
}

function adjacentRecipientPhones(primaryPhone) {
  return [1, -1].map((delta) => {
    const value = Number(primaryPhone.localPhone) + delta;
    const localPhone = String(value).padStart(primaryPhone.localPhone.length, "0");
    return `${primaryPhone.countryCode}${localPhone}`;
  });
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
  if (value.startsWith("sb_publishable_")) return true;
  const parts = value.split(".");
  if (parts.length !== 3) return false;
  try { return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"))?.role === "anon"; } catch { return false; }
}

function headers(config, token) {
  return {
    apikey: config.key,
    "content-type": "application/json",
    "x-client-info": "quata-chat-actions-notifications-android-evidence",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
}

async function jsonRequest(url, options, prefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(20_000) }); }
  catch { throw new Error(`${prefix}:network`); }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  try { return text ? JSON.parse(text) : {}; } catch { throw new Error(`${prefix}:invalid_json`); }
}

async function login(config, user) {
  const payload = await jsonRequest(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: headers(config),
    body: JSON.stringify({
      action: "login",
      country_code: user.countryCode,
      phone: user.phone,
      password: user.password,
      client_instance_id: `chat-actions-notifications-android-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session;
  const profileId = payload?.profile?.id;
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at)) {
    throw new Error(`invalid_auth_response:${user.label}`);
  }
  return { label: user.label, profileId, accessToken: session.access_token };
}

async function rpc(config, session, name, body) {
  const payload = await jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
  return payload;
}

function rows(payload, key) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.[key])) return payload[key];
  if (Array.isArray(payload?.data?.[key])) return payload.data[key];
  if (Array.isArray(payload?.messages)) return payload.messages;
  if (Array.isArray(payload?.favorites)) return payload.favorites;
  if (Array.isArray(payload?.data?.messages)) return payload.data.messages;
  if (Array.isArray(payload?.data?.favorites)) return payload.data.favorites;
  return [];
}

function threadId(payload) {
  const raw = payload?.thread_id ?? payload?.id ?? payload?.thread?.id ?? rows(payload, "threads")[0]?.id;
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) throw new Error("chat_contract_invalid:thread_id");
  return value;
}

function messageId(payload) {
  const raw = payload?.message_id ?? payload?.id ?? payload?.message?.id ?? rows(payload, "messages")[0]?.id;
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) throw new Error("chat_contract_invalid:message_id");
  return value;
}

function messageText(row) {
  return String(row?.body ?? row?.text ?? row?.message ?? "");
}

function messageReplyToId(row) {
  const raw = row?.reply_to_message_id ?? row?.replyToMessageId ?? row?.reply?.id;
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function favoriteMessageId(row) {
  const raw = row?.id ?? row?.message_id ?? row?.messageId ?? row?.message?.id ?? row?.favorite?.message_id;
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function messageNumericId(row) {
  const raw = row?.id ?? row?.message_id ?? row?.messageId ?? row?.message?.id;
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function snapshotThread(detail) {
  return rows(detail, "messages").map((row) => {
    const text = messageText(row);
    return {
      id: messageNumericId(row),
      textSha256: sha256(text),
      textPrefix: text.slice(0, 40),
      replyToMessageId: messageReplyToId(row),
      isEdited: row?.is_edited === true || row?.isEdited === true,
    };
  });
}

async function pollMessage(config, session, thread, predicate, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const detail = await rpc(config, session, "quata_chat_get_thread", {
      p_actor_profile_id: session.profileId,
      p_thread_id: thread,
      p_known_message_ids: [],
      p_limit: 250,
    });
    lastThreadSnapshot = snapshotThread(detail);
    const match = rows(detail, "messages").find(predicate);
    if (match) return match;
    await new Promise((resolve) => setTimeout(resolve, 1_500));
  }
  throw new Error("chat_backend_poll_timeout");
}

async function favorites(config, session) {
  return rows(await rpc(config, session, "quata_chat_get_favorites", {
    p_actor_profile_id: session.profileId,
    p_limit: 100,
  }), "messages");
}

async function pollForwardDestinationThread(config, session, profileId, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const payload = await rpc(config, session, "quata_chat_get_inbox", {
      p_actor_profile_id: session.profileId,
      p_limit: 100,
    });
    const allRows = [
      payload?.thread,
      payload?.conversation,
      ...(Array.isArray(payload?.threads) ? payload.threads : []),
      ...(Array.isArray(payload?.conversations) ? payload.conversations : []),
      ...(Array.isArray(payload?.data?.threads) ? payload.data.threads : []),
      ...(Array.isArray(payload?.data?.conversations) ? payload.data.conversations : []),
      ...(Array.isArray(payload?.update?.threads) ? payload.update.threads : []),
      ...(Array.isArray(payload?.update?.conversations) ? payload.update.conversations : []),
    ].filter(Boolean);
    const match = allRows.find((row) => JSON.stringify(row).includes(profileId));
    if (match) return { threadId: threadId(match), row: match };
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  throw new Error("chat_contract_invalid:forward_destination_thread");
}

function chatUrl(conversationId, messageIdValue) {
  const encodedConversation = encodeURIComponent(conversationId);
  const suffix = messageIdValue ? `?message=${encodeURIComponent(messageIdValue)}` : "";
  return `https://egquata.com/#chat-${encodedConversation}${suffix}`;
}

async function run(command, args, options = {}) {
  await new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: "inherit", shell: false, ...options });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolve() : reject(new Error(`command_failed:${command}:${code}`)));
  });
}

async function runCapture(command, args, options = {}) {
  return await new Promise((resolve, reject) => {
    let output = "";
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: false, ...options });
    child.stdout.on("data", (chunk) => { output += chunk.toString(); process.stdout.write(chunk); });
    child.stderr.on("data", (chunk) => { output += chunk.toString(); process.stderr.write(chunk); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolve(output) : reject(new Error(`command_failed:${command}:${code}`)));
  });
}

async function runSilent(command, args, options = {}) {
  return await new Promise((resolve, reject) => {
    let output = "";
    let stderr = "";
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: false, ...options });
    child.stdout.on("data", (chunk) => { output += chunk.toString(); });
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolve(output) : reject(new Error(`command_failed:${command}:${code}:${stderr.trim()}`)));
  });
}

async function gitMetadata() {
  const head = (await runSilent("git", ["rev-parse", "HEAD"])).trim();
  const status = await runSilent("git", ["status", "--porcelain"]);
  return {
    head,
    workingTreeDirty: status.trim().length > 0,
  };
}

async function adbRunAsCat(remotePath, localPath) {
  const chunks = [];
  await new Promise((resolve, reject) => {
    const child = spawn("adb", ["exec-out", "run-as", "com.quata", "cat", remotePath], { stdio: ["ignore", "pipe", "pipe"], shell: false });
    let stderr = "";
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolve() : reject(new Error(`adb_exec_out_failed:${code}:${stderr.trim()}`)));
  });
  await writeFile(localPath, Buffer.concat(chunks));
}

async function collectAvailableDeviceEvidence(destination) {
  await rm(destination, { recursive: true, force: true });
  await mkdir(destination, { recursive: true });
  const copied = [];
  for (const file of evidenceFiles) {
    try {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(destination, file));
      copied.push(file);
    } catch {}
  }
  return copied;
}

async function logicalCleanup(config, state) {
  const actions = [];
  const favoriteMessageId = state.favoriteMessage ?? state.editedMessage ?? state.message;
  if (state.thread && favoriteMessageId && state.a) {
    await rpc(config, state.a, "quata_chat_set_favorite", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_id: favoriteMessageId,
      p_favorite: false,
    });
    actions.push("favorite_removed");
  }
  const messageIds = [state.message, state.peerMessage, state.editedMessage, ...state.uiMessages]
    .filter((id, index, all) => Number.isInteger(Number(id)) && all.indexOf(id) === index);
  if (state.thread && messageIds.length && state.a) {
    await rpc(config, state.a, "quata_chat_delete_messages", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_ids: messageIds,
    });
    actions.push("test_messages_deleted");
  }
  return actions;
}

async function hardDeleteTemporaryThread(thread, uniqueKey) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) {
    throw new Error("missing_hard_cleanup_authorization");
  }
  if (!uniqueKey.startsWith("qadata-chat-actions-notifications-android-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
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
  try {
    await client.query("begin");
    const owned = await client.query(
      "select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-actions-notifications-android-%' for update",
      [thread, uniqueKey],
    );
    if (owned.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_not_owned");
    const deleted = await client.query("delete from public.chat_threads where id = $1 and unique_key = $2 returning id", [thread, uniqueKey]);
    if (deleted.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_delete_failed");
    const residue = await client.query(
      `select
        (select count(*)::int from public.chat_threads where id = $1 or unique_key = $2) as chat_threads,
        (select count(*)::int from public.chat_messages where thread_id = $1) as chat_messages,
        (select count(*)::int from public.chat_participants where thread_id = $1) as chat_participants,
        (select count(*)::int from public.chat_attachments where thread_id = $1) as chat_attachments,
        (select count(*)::int from public.chat_message_states where thread_id = $1) as chat_message_states,
        (select count(*)::int from public.chat_events where thread_id = $1) as chat_events,
        (select count(*)::int from public.conversation_user_state where conversation_id = $1) as conversation_user_state`,
      [thread, uniqueKey],
    );
    const counts = residue.rows[0] ?? {};
    if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:physical_rows");
    await client.query("commit");
    return { threadId: thread, uniqueKeySha256: sha256(uniqueKey), residueCounts: counts };
  } catch (error) {
    await client.query("rollback").catch(() => {});
    throw error;
  } finally {
    await client.end().catch(() => {});
  }
}

async function withPoolerClient(callback) {
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
  try {
    return await callback(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function profileFollowExists(actorProfileId, targetProfileId) {
  return await withPoolerClient(async (client) => {
    const result = await client.query(
      `select exists (
         select 1 from public.community_profile_follows
         where follower_profile_id = $1 and followed_profile_id = $2
       ) as exists`,
      [actorProfileId, targetProfileId],
    );
    return result.rows[0]?.exists === true;
  });
}

async function prepareProfileFollowAbsent(actorProfileId, targetProfileId) {
  return await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      const existing = await client.query(
        `select id from public.community_profile_follows
         where follower_profile_id = $1 and followed_profile_id = $2
         for update`,
        [actorProfileId, targetProfileId],
      );
      if (existing.rowCount > 0) {
        await client.query(
          `delete from public.community_profile_follows
           where follower_profile_id = $1 and followed_profile_id = $2`,
          [actorProfileId, targetProfileId],
        );
      }
      await client.query("commit");
      return { initiallyFollowing: existing.rowCount > 0 };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function restoreProfileFollowEdge(actorProfileId, targetProfileId, initiallyFollowing) {
  await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      if (initiallyFollowing) {
        await client.query(
          `insert into public.community_profile_follows (follower_profile_id, followed_profile_id)
           values ($1, $2)
           on conflict do nothing`,
          [actorProfileId, targetProfileId],
        );
      } else {
        await client.query(
          `delete from public.community_profile_follows
           where follower_profile_id = $1 and followed_profile_id = $2`,
          [actorProfileId, targetProfileId],
        );
      }
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  const restored = await profileFollowExists(actorProfileId, targetProfileId);
  if (restored !== initiallyFollowing) throw new Error("cleanup_residue_detected:profile_follow_edge");
}

async function pollProfileFollowEdge(actorProfileId, targetProfileId, expected, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await profileFollowExists(actorProfileId, targetProfileId) === expected) return;
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 750));
  }
  throw new Error(`profile_follow_backend_poll_timeout:${expected ? "created" : "removed"}`);
}

async function createTemporaryForwardProfile(runId) {
  const id = randomUUID();
  const phoneLocal = `999${Date.now().toString().slice(-6)}`;
  const displayName = `QADATA Forward ${phoneLocal}`;
  await withDatabase(async (client) => {
    await client.query("begin");
    try {
      await client.query(
        `insert into public.community_profiles
          (id, display_name, phone, pass_hash, phone_normalized, country_code, phone_local, phone_e164, neighborhood, barrio, barrio_normalized, account_status)
         values ($1, $2, $3, $4, $5, '240', $6, $7, 'QADATA', 'QADATA', 'qadata', 'active')`,
        [id, displayName, `+240 ${phoneLocal}`, `qadata-chat-forward-no-login-${runId}`, `240${phoneLocal}`, phoneLocal, `+240${phoneLocal}`],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  return { id, phoneLocal, displayName };
}

async function hardDeleteTemporaryForwardDestination(profile, threadId) {
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const owned = await client.query(
        "select id from public.community_profiles where id = $1 and display_name = $2 and phone_local = $3 for update",
        [profile.id, profile.displayName, profile.phoneLocal],
      );
      if (owned.rowCount !== 1) throw new Error("cleanup_residue_detected:forward_profile_not_owned");
      if (threadId) {
        const participant = await client.query(
          "select 1 from public.chat_participants where thread_id = $1 and profile_id = $2 for update",
          [threadId, profile.id],
        );
        if (participant.rowCount !== 1) throw new Error("cleanup_residue_detected:forward_thread_not_owned");
        await client.query("delete from public.chat_threads where id = $1", [threadId]);
      }
      const deleted = await client.query(
        "delete from public.community_profiles where id = $1 and display_name = $2 and phone_local = $3 returning id",
        [profile.id, profile.displayName, profile.phoneLocal],
      );
      if (deleted.rowCount !== 1) throw new Error("cleanup_residue_detected:forward_profile_delete_failed");
      const residue = await client.query(
        `select
          (select count(*)::int from public.community_profiles where id = $1) as community_profiles,
          (select count(*)::int from public.chat_threads where id = $2) as chat_threads,
          (select count(*)::int from public.chat_messages where thread_id = $2) as chat_messages,
          (select count(*)::int from public.chat_participants where profile_id = $1 or thread_id = $2) as chat_participants,
          (select count(*)::int from public.chat_private_threads where thread_id = $2 or profile_low_id = $1 or profile_high_id = $1) as chat_private_threads`,
        [profile.id, threadId ?? -1],
      );
      const counts = residue.rows[0] ?? {};
      if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:forward_physical_rows");
      await client.query("commit");
      return { profileIdSha256: sha256(profile.id), threadId, residueCounts: counts };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
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
  try {
    return await callback(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function resolveAdjacentRecipientProfile(phoneKeys) {
  return await withDatabase(async (client) => {
    const result = await client.query(
      "select profile_id from public.quata_profile_phone_directory where phone_key = any($1::text[]) order by profile_id limit 1",
      [phoneKeys],
    );
    const profileId = result.rows[0]?.profile_id;
    if (!uuid.test(profileId ?? "")) throw new Error("missing_adjacent_recipient_profile");
    return profileId;
  });
}

async function verifyRecipientParticipant(thread, recipientProfileId) {
  await withDatabase(async (client) => {
    const result = await client.query(
      "select 1 from public.chat_participants where thread_id = $1 and profile_id = $2 limit 1",
      [thread, recipientProfileId],
    );
    if (result.rowCount !== 1) throw new Error("chat_contract_invalid:recipient_participant_missing");
  });
}

async function openTemporaryProfileHashWindow(users) {
  if (process.env[tempProfileHashAuthorizationEnvironment]?.trim() !== tempProfileHashAuthorizationValue) {
    return { state: "not_requested", restored: true, restore: async () => {} };
  }
  const opened = await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const rowsToRestore = [];
      for (const user of users) {
        const found = await client.query(
          `select id, pass_hash, pass_plain
             from public.community_profiles
            where (
              regexp_replace(coalesce(country_code, ''), '\\D', '', 'g') = $1
              and regexp_replace(coalesce(phone_local, ''), '\\D', '', 'g') = $2
            ) or regexp_replace(coalesce(phone, ''), '\\D', '', 'g') = any($3::text[])
            order by created_at desc nulls last, id
            limit 1
            for update`,
          [user.countryCode, user.phone, [`${user.countryCode}${user.phone}`, user.phone]],
        );
        if (found.rowCount !== 1) throw new Error("temporary_profile_hash_window:profile_not_found");
        const row = found.rows[0];
        rowsToRestore.push({ id: row.id, pass_hash: row.pass_hash, pass_plain: row.pass_plain });
        await client.query(
          "update public.community_profiles set pass_hash = $1, pass_plain = null where id = $2",
          [sha256(user.password), row.id],
        );
      }
      await client.query("commit");
      return rowsToRestore;
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  let restored = false;
  return {
    state: "opened",
    restored,
    count: opened.length,
    restore: async () => {
      if (restored) return;
      await withDatabase(async (client) => {
        await client.query("begin");
        try {
          for (const row of opened) {
            await client.query(
              "update public.community_profiles set pass_hash = $1, pass_plain = $2 where id = $3",
              [row.pass_hash, row.pass_plain, row.id],
            );
          }
          await client.query("commit");
          restored = true;
        } catch (error) {
          await client.query("rollback").catch(() => {});
          throw error;
        }
      });
    },
  };
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  const prefix = [
    "missing_environment", "missing_public_supabase_configuration", "invalid_public_supabase_url",
    "invalid_or_privileged_supabase_key", "public_auth_request_failed", "invalid_auth_response",
    "chat_rpc_failed", "chat_contract_invalid", "chat_backend_poll_timeout", "command_failed",
    "missing_hard_cleanup_authorization", "cleanup_residue_detected",
    "missing_adjacent_profile_credentials_source", "invalid_adjacent_profile_phone",
    "missing_adjacent_recipient_profile", "temporary_profile_hash_window",
    "android_instrumentation_not_ok", "android_instrumentation_semantic_failure", "adb_exec_out_failed",
  ].find((candidate) => message.startsWith(candidate));
  return prefix ? message : "unexpected_chat_actions_notifications_android_failure";
}

class EvidenceCompleted extends Error {}

const report = {
  check: "CHAT-ACTIONS-NOTIFICATIONS-ANDROID-001",
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};
const state = { a: null, b: null, thread: null, message: null, peerMessage: null, editableMessage: null, editedMessage: null, uiMessages: [], uniqueKey: null, forwardProfile: null, forwardThread: null, forwardedMessage: null, profileFollow: null };
let profileHashWindow = { state: "not_started", restored: true, restore: async () => {} };
const localCredentials = join("build-reports", "android", `chat-actions-notifications-credentials-${randomUUID()}.json`);
const evidenceDir = join("build-reports", "android", "chat-actions-notifications-evidence");
try {
  const config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");
  const users = await authorizedUsers();
  const userA = users.a;
  const userB = users.b;
  const usersForTemporaryHash = [userA, userB].filter((user) => user?.countryCode && user?.phone && user?.password);
  profileHashWindow = await openTemporaryProfileHashWindow(usersForTemporaryHash);
  if (profileHashWindow.state === "opened") {
    report.steps.push("temporary_profile_hash_window_opened");
  }
  state.a = await login(config, userA);
  if (useAdjacentAuthorizedProfile && !userB.password) {
    state.b = { label: "B", profileId: await resolveAdjacentRecipientProfile(userB.adjacentPhoneKeys) };
    report.steps.push("authorized_profile_logged_in_and_recipient_resolved");
  } else {
    state.b = await login(config, userB);
    report.steps.push("two_authorized_profiles_logged_in");
  }

  const runId = randomUUID();
  state.uniqueKey = `qadata-chat-actions-notifications-android-${runId}`;
  if (!translationOnly && !profileOnly && !profileFollowOnly) {
    state.forwardProfile = await createTemporaryForwardProfile(runId);
    report.steps.push("temporary_forward_destination_profile_created");
  }
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId,
    p_recipient_profile_ids: [state.b.profileId],
    p_subject: `QADATA chat actions notifications Android ${runId}`,
    p_type: "group",
    p_message: "",
    p_unique_key: state.uniqueKey,
    p_community_id: null,
  }));
  const marker = translationOnly ? "Mbolo" : `chat-actions-notifications-android-${randomUUID()}`;
  const markerProbe = marker.slice(0, 24);
  const peerMarker = `chat-profile-peer-android-${randomUUID()}`;
  const peerProbe = peerMarker.slice(0, 24);
  const composerMarker = `chat-compose-ui-android-${randomUUID()}`;
  const replyMarker = `chat-reply-ui-android-${randomUUID()}`;
  const editMarker = `chat-edit-ui-android-${randomUUID()}`;
  state.message = messageId(await rpc(config, state.a, "quata_chat_send_message", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message: marker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-actions-notifications-android-${randomUUID()}`,
  }));
  if (state.b.accessToken) {
    await pollMessage(config, state.b, state.thread, (message) => Number(message?.id) === state.message && messageText(message) === marker);
    state.peerMessage = messageId(await rpc(config, state.b, "quata_chat_send_message", {
      p_actor_profile_id: state.b.profileId,
      p_thread_id: state.thread,
      p_message: peerMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-profile-peer-android-${randomUUID()}`,
    }));
    await pollMessage(config, state.a, state.thread, (message) => Number(message?.id) === state.peerMessage && messageText(message) === peerMarker);
    report.steps.push("unique_own_and_peer_messages_visible");
  } else {
    await verifyRecipientParticipant(state.thread, state.b.profileId);
    report.steps.push("adjacent_recipient_participant_verified");
  }
  report.steps.push("isolated_thread_and_own_message_ready");

  await mkdir(dirname(localCredentials), { recursive: true });
  await writeFile(localCredentials, `${JSON.stringify({ country_code: userA.countryCode, phone: userA.phone, password: userA.password })}\n`, { mode: 0o600 });
  const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"], {
    env: {
      ...process.env,
      JAVA_HOME: process.env.JAVA_HOME || "C:\\Program Files\\Android\\Android Studio\\jbr",
      ANDROID_HOME: process.env.ANDROID_HOME || `${process.env.LOCALAPPDATA}\\Android\\Sdk`,
      ANDROID_SDK_ROOT: process.env.ANDROID_SDK_ROOT || `${process.env.LOCALAPPDATA}\\Android\\Sdk`,
    },
  });
  await run("adb", ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
  await run("adb", ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  await run("adb", ["shell", "pm", "clear", "com.quata"]);
  await run("adb", ["push", localCredentials, deviceTempCredentialsPath]);
  await run("adb", ["shell", "chmod", "644", deviceTempCredentialsPath]);
  await run("adb", ["shell", "run-as", "com.quata", "mkdir", "-p", "files"]);
  await run("adb", ["shell", "run-as", "com.quata", "cp", deviceTempCredentialsPath, `files/${deviceCredentialsPath.replace("app-internal:", "")}`]);
  await run("adb", ["shell", "rm", "-f", deviceTempCredentialsPath]);
  await run("adb", ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);
  const runInstrumentationStage = async (stage) => await runCapture("adb", [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.feature.chat.presentation.chat.ChatActionsNotificationsInstrumentedTest",
    "-e", "quataChatActionsStage", stage,
    "-e", "quataChatActionsCredentialsFile", deviceCredentialsPath,
    "-e", "quataChatActionsUrl", chatUrl(`sb:${state.thread}`),
    "-e", "quataChatActionsOwnProbe", markerProbe,
    "-e", "quataChatActionsPeerProbe", peerProbe,
    "-e", "quataChatActionsProfileId", state.b.profileId,
    "-e", "quataChatActionsComposerMarker", composerMarker,
    "-e", "quataChatActionsReplyMarker", replyMarker,
    "-e", "quataChatActionsEditMarker", editMarker,
    "-e", "quataChatActionsForwardQuery", state.forwardProfile?.phoneLocal ?? "translation-only",
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  const assertInstrumentationPassed = (stage, instrumentationOutput) => {
    if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) throw new Error(`android_instrumentation_not_ok:${stage}`);
    if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentationOutput)) {
      throw new Error(`android_instrumentation_semantic_failure:${stage}`);
    }
  }

  if (translationOnly) {
    assertInstrumentationPassed("translation", await runInstrumentationStage("translation"));
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((candidate) => candidate.startsWith("android-chat-translation-") || candidate.endsWith(".json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
    }
    report.status = "passed";
    report.steps.push("chat_translation_common_overlay_translated_fang_message_and_returned");
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      translatedMessageId: state.message,
      markerSha256: sha256(marker),
    };
    throw new EvidenceCompleted();
  }

  if (state.b.accessToken) {
    if (profileFollowOnly) {
      state.profileFollow = await prepareProfileFollowAbsent(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_initial_state_snapshot_and_absent_prepared");
    }
    assertInstrumentationPassed(profileFollowOnly ? "profile-follow" : "profile", await runInstrumentationStage(profileFollowOnly ? "profile-follow" : "profile"));
    if (profileFollowOnly) {
      await pollProfileFollowEdge(state.a.profileId, state.b.profileId, true);
      report.steps.push("profile_follow_toggled_and_verified_by_db");
    }
    report.steps.push("peer_avatar_opened_public_profile_and_returned_to_chat");
  }

  if (profileOnly || profileFollowOnly) {
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((name) => name.includes("profile") || name.endsWith("evidence.json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file));
    }
    report.status = "passed";
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      seedMessageId: state.message,
      peerMessageId: state.peerMessage,
      peerProfileIdSha256: sha256(state.b.profileId),
      markerSha256: sha256(marker),
      peerMarkerSha256: sha256(peerMarker),
      profileFollowInitialState: state.profileFollow?.initiallyFollowing ?? null,
    };
    throw new Error("profile_only_completed");
  }

  assertInstrumentationPassed("send-reply", await runInstrumentationStage("send-reply"));
  state.favoriteMessage = state.message;
  const favoriteRowsAfterSeedToggle = await favorites(config, state.a);
  if (!favoriteRowsAfterSeedToggle.some((message) => favoriteMessageId(message) === Number(state.favoriteMessage))) {
    throw new Error("chat_contract_invalid:favorite_not_persisted");
  }
  report.steps.push("favorite_toggled_and_verified_by_rpc");
  const composerMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === composerMarker);
  state.editableMessage = messageId(composerMessage);
  state.uiMessages.push(messageId(composerMessage));
  report.steps.push("composer_text_sent_by_shared_ui_and_verified_by_rpc");
  const replyMessage = await pollMessage(config, state.a, state.thread, (message) =>
    messageText(message) === replyMarker && messageReplyToId(message) === Number(state.message),
  );
  state.uiMessages.push(messageId(replyMessage));
  report.steps.push("composer_reply_sent_by_shared_ui_and_verified_by_rpc");

  assertInstrumentationPassed("edit-favorite", await runInstrumentationStage("edit-favorite"));
  const editedMessage = await pollMessage(config, state.a, state.thread, (message) =>
    messageText(message) === editMarker &&
      Number(message?.id ?? message?.message_id) === Number(state.editableMessage),
  );
  state.editedMessage = messageId(editedMessage);
  report.steps.push("composer_edit_sent_by_shared_ui_and_verified_by_rpc");

  assertInstrumentationPassed("forward", await runInstrumentationStage("forward"));
  const forwardDestination = await pollForwardDestinationThread(config, state.a, state.forwardProfile.id);
  state.forwardThread = forwardDestination.threadId;
  const forwardedMessage = await pollMessage(config, state.a, state.forwardThread, (message) =>
    messageText(message) === editMarker &&
      Number(message?.forwarded_from_message_id) === Number(state.editedMessage),
  );
  state.forwardedMessage = messageId(forwardedMessage);
  report.steps.push("message_forwarded_by_shared_ui_and_verified_by_rpc");

  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  for (const file of evidenceFiles) {
    await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file));
  }
  report.status = "passed";
  report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
  report.fixture = {
    threadId: state.thread,
    conversationId: `sb:${state.thread}`,
    seedMessageId: state.message,
    peerMessageId: state.peerMessage,
    peerProfileIdSha256: sha256(state.b.profileId),
    composerMessageId: messageId(composerMessage),
    replyMessageId: messageId(replyMessage),
    editedMessageId: state.editedMessage,
    forwardThreadId: state.forwardThread,
    forwardedMessageId: state.forwardedMessage,
    forwardProfileIdSha256: sha256(state.forwardProfile.id),
    markerSha256: sha256(marker),
    composerMarkerSha256: sha256(composerMarker),
    replyMarkerSha256: sha256(replyMarker),
    editMarkerSha256: sha256(editMarker),
  };
} catch (error) {
  if (error instanceof EvidenceCompleted || error?.message === "profile_only_completed") {
    // Focal modes finished successfully; cleanup and report writing still happen in finally.
  } else {
    report.error = safeFailure(error);
    if (lastThreadSnapshot) report.diagnostics = { lastThreadSnapshot };
    try {
      const copied = await collectAvailableDeviceEvidence(evidenceDir);
      if (copied.length) {
        report.evidence.failureDirectory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
        report.evidence.failureFiles = copied;
      }
    } catch {}
  }
} finally {
  const cleanup = { state: "completed", actions: [] };
  let cleanupFailed = false;
  try { await run("adb", ["shell", "rm", "-f", deviceTempCredentialsPath]); } catch {}
  try { await run("adb", ["shell", "run-as", "com.quata", "rm", "-f", `files/${deviceCredentialsPath.replace("app-internal:", "")}`]); } catch {}
  try { await run("adb", ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]); } catch {}
  try { await run("adb", ["uninstall", "com.quata.test"]); } catch {}
  await rm(localCredentials, { force: true }).catch(() => {});
  try {
    await profileHashWindow.restore();
    if (profileHashWindow.state === "opened") {
      cleanup.actions.push("temporary_profile_hash_window_restored");
      report.profileHashWindow = { state: "restored", count: profileHashWindow.count };
    }
  } catch (error) {
    cleanupFailed = true;
    cleanup.error = safeFailure(error);
    report.profileHashWindow = { state: "restore_failed" };
  }
  if (state.thread) {
    const config = await publicBackendConfig().catch(() => null);
    if (config) {
      try { cleanup.actions.push(...await logicalCleanup(config, state)); }
      catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
      if (state.profileFollow && state.a && state.b) {
        try {
          await restoreProfileFollowEdge(state.a.profileId, state.b.profileId, state.profileFollow.initiallyFollowing);
          cleanup.actions.push("profile_follow_edge_restored_to_initial_state");
        } catch (error) {
          cleanupFailed = true;
          cleanup.error = safeFailure(error);
        }
      }
    }
    if (state.uniqueKey) {
      try {
        const hardCleanup = await hardDeleteTemporaryThread(state.thread, state.uniqueKey);
        cleanup.actions.push("hard_deleted_temporary_thread");
        cleanup.actions.push("cleanup_verified_physical_residue_absent");
        cleanup.hardCleanup = hardCleanup;
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
  }
  if (state.forwardProfile) {
    try {
      cleanup.forwardDestination = await hardDeleteTemporaryForwardDestination(state.forwardProfile, state.forwardThread);
      cleanup.actions.push("temporary_forward_destination_deleted");
      cleanup.actions.push("forward_destination_cleanup_verified_physical_residue_absent");
    } catch (error) {
      cleanupFailed = true;
      cleanup.error = safeFailure(error);
    }
  }
  if (cleanupFailed) {
    cleanup.state = "failed_or_incomplete";
    if (report.status === "passed") {
      report.status = "failed";
      report.error = cleanup.error ?? "cleanup_residue_detected";
    }
  }
  report.cleanup = cleanup;
  report.finishedAt = new Date().toISOString();
  const output = join("build-reports", "android", "chat-actions-notifications-evidence.json");
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Chat actions/notifications Android evidence written: ${output}`);
}
if (report.status !== "passed") {
  console.error(`Chat actions/notifications Android evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat actions/notifications Android evidence passed.");
}
