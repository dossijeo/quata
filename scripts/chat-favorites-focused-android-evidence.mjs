import { spawn } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import pg from "pg";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_FAVORITES_FOCUSED_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_FAVORITES_FOCUSED_HARD_CLEANUP";
const useAdjacentAuthorizedProfile = process.env.QUATA_CHAT_FAVORITES_FOCUSED_USE_ADJACENT_AUTHORIZED_PROFILE === "1";
const deviceCredentialsPath = "app-internal:chat-favorites-focused-credentials.json";
const deviceTempCredentialsPath = "/data/local/tmp/chat-favorites-focused-credentials.json";
const deviceEvidencePath = "files/chat-favorites-focused-evidence";
const evidenceFiles = [
  "android-favorites-list.png",
  "android-favorites-open-source.png",
  "android-focused-message.png",
  "android-favorites-empty.png",
  "android-chat-favorites-focused-evidence.json",
];

function env(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`missing_environment:${name}`);
  return value;
}

async function authorizedUsers() {
  if (!useAdjacentAuthorizedProfile) {
    return {
      a: { label: "A", countryCode: env("QUATA_CHAT_EVIDENCE_A_COUNTRY_CODE"), phone: env("QUATA_CHAT_EVIDENCE_A_PHONE"), password: env("QUATA_CHAT_EVIDENCE_A_PASSWORD") },
      b: { label: "B", countryCode: env("QUATA_CHAT_EVIDENCE_B_COUNTRY_CODE"), phone: env("QUATA_CHAT_EVIDENCE_B_PHONE"), password: env("QUATA_CHAT_EVIDENCE_B_PASSWORD") },
    };
  }
  const host = process.env.QUATA_CHAT_EVIDENCE_SSH_HOST?.trim();
  const file = process.env.QUATA_CHAT_EVIDENCE_SSH_CREDENTIALS_FILE?.trim();
  if (!host || !file) throw new Error("missing_adjacent_profile_credentials_source");
  const credentials = JSON.parse(await runSilent("ssh", [host, `cat ${file}`]));
  const phone = splitPhone(credentials.phone);
  return {
    a: { label: "A", countryCode: phone.countryCode, phone: phone.localPhone, password: credentials.password },
    b: { label: "B", adjacentPhoneKeys: adjacentRecipientPhones(phone) },
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
    "x-client-info": "quata-chat-favorites-focused-android-evidence",
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
      action: "web_login",
      country_code: user.countryCode,
      phone_local: user.phone,
      password: user.password,
      client_instance_id: `chat-fav-focus-android-${user.label.toLowerCase()}-${randomUUID()}`,
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

async function pollMessage(config, session, thread, predicate, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const detail = await rpc(config, session, "quata_chat_get_thread", {
      p_actor_profile_id: session.profileId,
      p_thread_id: thread,
      p_known_message_ids: [],
      p_limit: 250,
    });
    const match = rows(detail, "messages").find(predicate);
    if (match) return match;
    await new Promise((resolve) => setTimeout(resolve, 1_500));
  }
  throw new Error("chat_backend_poll_timeout");
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

async function logicalCleanup(config, state) {
  const actions = [];
  if (state.thread && state.message && state.a) {
    await rpc(config, state.a, "quata_chat_set_favorite", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_id: state.message,
      p_favorite: false,
    });
    actions.push("favorite_removed");
  }
  if (state.thread && state.message && state.a) {
    await rpc(config, state.a, "quata_chat_delete_messages", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_ids: [state.message],
    });
    actions.push("test_message_deleted");
  }
  return actions;
}

async function hardDeleteTemporaryThread(thread, uniqueKey) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) {
    throw new Error("missing_hard_cleanup_authorization");
  }
  if (!uniqueKey.startsWith("qadata-chat-fav-focus-android-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
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
      "select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-fav-focus-android-%' for update",
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

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  return [
    "missing_environment", "missing_public_supabase_configuration", "invalid_public_supabase_url",
    "invalid_or_privileged_supabase_key", "public_auth_request_failed", "invalid_auth_response",
    "chat_rpc_failed", "chat_contract_invalid", "chat_backend_poll_timeout", "command_failed",
    "missing_hard_cleanup_authorization", "cleanup_residue_detected",
    "missing_adjacent_profile_credentials_source", "invalid_adjacent_profile_phone",
    "missing_adjacent_recipient_profile",
  ].find((prefix) => message.startsWith(prefix)) ?? "unexpected_chat_favorites_focused_android_failure";
}

const report = {
  check: "CHAT-FAVORITES-FOCUSED-ANDROID-001",
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};
const state = { a: null, b: null, thread: null, message: null, uniqueKey: null };
const localCredentials = join("build-reports", "android", `chat-favorites-focused-credentials-${randomUUID()}.json`);
try {
  const config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");
  const users = await authorizedUsers();
  const userA = users.a;
  const userB = users.b;
  state.a = await login(config, userA);
  if (useAdjacentAuthorizedProfile) {
    state.b = { label: "B", profileId: await resolveAdjacentRecipientProfile(userB.adjacentPhoneKeys) };
    report.steps.push("authorized_profile_logged_in_and_recipient_resolved");
  } else {
    state.b = await login(config, userB);
    report.steps.push("two_authorized_profiles_logged_in");
  }

  const runId = randomUUID();
  state.uniqueKey = `qadata-chat-fav-focus-android-${runId}`;
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId,
    p_recipient_profile_ids: [state.b.profileId],
    p_subject: `QADATA chat favorite focus Android ${runId}`,
    p_type: "group",
    p_message: "",
    p_unique_key: state.uniqueKey,
    p_community_id: null,
  }));
  const marker = `chat-fav-focus-android-${randomUUID()}`;
  const markerProbe = marker.slice(0, 24);
  state.message = messageId(await rpc(config, state.a, "quata_chat_send_message", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message: marker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-fav-focus-android-${randomUUID()}`,
  }));
  if (state.b.accessToken) {
    await pollMessage(config, state.b, state.thread, (message) => Number(message?.id) === state.message && message?.body === marker);
    report.steps.push("unique_message_visible_to_peer");
  } else {
    await verifyRecipientParticipant(state.thread, state.b.profileId);
    report.steps.push("adjacent_recipient_participant_verified");
  }
  await rpc(config, state.a, "quata_chat_set_favorite", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message_id: state.message,
    p_favorite: true,
  });
  report.steps.push("isolated_favorite_message_ready");

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
  await run("adb", ["push", localCredentials, deviceTempCredentialsPath]);
  await run("adb", ["shell", "chmod", "644", deviceTempCredentialsPath]);
  await run("adb", ["shell", "run-as", "com.quata", "cp", deviceTempCredentialsPath, `files/${deviceCredentialsPath.replace("app-internal:", "")}`]);
  await run("adb", ["shell", "rm", "-f", deviceTempCredentialsPath]);
  await run("adb", ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);
  const instrumentationOutput = await runCapture("adb", [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.feature.chat.presentation.chat.ChatFavoritesFocusedDeepLinkInstrumentedTest",
    "-e", "quataChatEvidenceCredentialsFile", deviceCredentialsPath,
    "-e", "quataChatEvidenceFavoritesUrl", chatUrl("__favorite_messages__"),
    "-e", "quataChatEvidenceFocusedUrl", chatUrl(`sb:${state.thread}`, String(state.message)),
    "-e", "quataChatEvidenceMarkerProbe", markerProbe,
    "-e", "quataChatEvidenceMessageId", String(state.message),
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) throw new Error("android_instrumentation_not_ok");
  if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentationOutput)) {
    throw new Error("android_instrumentation_semantic_failure");
  }
  report.steps.push("android_favorites_source_and_focus_verified");
  await rpc(config, state.a, "quata_chat_set_favorite", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message_id: state.message,
    p_favorite: false,
  });
  report.steps.push("favorite_removed_by_rpc_for_android_empty_state");
  await run("adb", ["shell", "am", "force-stop", "com.quata"]);
  report.steps.push("android_process_restarted_before_empty_state_verification");
  const emptyInstrumentationOutput = await runCapture("adb", [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.feature.chat.presentation.chat.ChatFavoritesFocusedDeepLinkInstrumentedTest",
    "-e", "quataChatEvidenceCredentialsFile", deviceCredentialsPath,
    "-e", "quataChatEvidenceFavoritesUrl", chatUrl("__favorite_messages__"),
    "-e", "quataChatEvidenceFocusedUrl", chatUrl(`sb:${state.thread}`, String(state.message)),
    "-e", "quataChatEvidenceMarkerProbe", markerProbe,
    "-e", "quataChatEvidenceMessageId", String(state.message),
    "-e", "quataChatEvidenceExpectFavoritesEmpty", "1",
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  if (!/OK \(\d+ tests?\)/.test(emptyInstrumentationOutput)) throw new Error("android_empty_instrumentation_not_ok");
  if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(emptyInstrumentationOutput)) {
    throw new Error("android_empty_instrumentation_semantic_failure");
  }
  report.steps.push("android_favorites_empty_state_verified_after_unfavorite");
  const evidenceDir = join("build-reports", "android", "chat-favorites-focused-evidence");
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  for (const file of evidenceFiles) {
    await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file));
  }
  report.status = "passed";
  report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
  report.fixture = { threadId: state.thread, conversationId: `sb:${state.thread}`, messageId: state.message, markerSha256: sha256(marker) };
} catch (error) {
  report.error = safeFailure(error);
} finally {
  const cleanup = { state: "completed", actions: [] };
  let cleanupFailed = false;
  try { await run("adb", ["shell", "rm", "-f", deviceTempCredentialsPath]); } catch {}
  try { await run("adb", ["shell", "run-as", "com.quata", "rm", "-f", `files/${deviceCredentialsPath.replace("app-internal:", "")}`]); } catch {}
  try { await run("adb", ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]); } catch {}
  try { await run("adb", ["uninstall", "com.quata.test"]); } catch {}
  await rm(localCredentials, { force: true }).catch(() => {});
  if (state.thread) {
    const config = await publicBackendConfig().catch(() => null);
    if (config) {
      try { cleanup.actions.push(...await logicalCleanup(config, state)); }
      catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
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
  if (cleanupFailed) {
    cleanup.state = "failed_or_incomplete";
    if (report.status === "passed") {
      report.status = "failed";
      report.error = cleanup.error ?? "cleanup_residue_detected";
    }
  }
  report.cleanup = cleanup;
  report.finishedAt = new Date().toISOString();
  const output = join("build-reports", "android", "chat-favorites-focused-evidence.json");
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Chat favorites/focused Android evidence written: ${output}`);
}
if (report.status !== "passed") {
  console.error(`Chat favorites/focused Android evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat favorites/focused Android evidence passed.");
}
