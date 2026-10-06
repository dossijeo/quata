#!/usr/bin/env node
import { spawn } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import pg from "pg";

const check = "CHAT-OUTBOX-DURABLE-IOS-001";
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_OUTBOX_DURABLE_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_OUTBOX_DURABLE_HARD_CLEANUP";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const options = parseArgs(process.argv.slice(2));
const report = {
  check,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};

let localCredentials;
let remoteCredentials;
let config;
const state = { a: null, b: null, thread: null, message: null, marker: null, uniqueKey: null };

try {
  config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");

  const users = usersFromEnvironment();
  [state.a, state.b] = await Promise.all([login(config, users[0]), login(config, users[1])]);
  report.steps.push("two_authorized_profiles_logged_in");

  const runId = randomUUID();
  state.uniqueKey = `qadata-chat-outbox-durable-ios-${runId}`;
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId,
    p_recipient_profile_ids: [state.b.profileId],
    p_subject: `QADATA chat durable outbox iOS ${runId}`,
    p_type: "group",
    p_message: "",
    p_unique_key: state.uniqueKey,
    p_community_id: null,
  }));
  state.marker = `chat-outbox-durable-ios-${randomUUID()}`;
  report.steps.push("isolated_group_thread_ready");

  localCredentials = join(await mkdtemp(join(tmpdir(), "quata-ios-chat-outbox-durable-")), "credentials.json");
  await writeFile(localCredentials, `${JSON.stringify({
    country_code: users[0].countryCode,
    phone: e164Phone(users[0].countryCode, users[0].phone),
    password: users[0].password,
  })}\n`, { mode: 0o600 });
  remoteCredentials = (await runCapture("ssh", [options.host, "mktemp /tmp/quata-ios-chat-outbox-durable-credentials.XXXXXX.json"])).trim();
  await run("scp", [localCredentials, `${options.host}:${remoteCredentials}`]);
  report.steps.push("ios_real_credentials_copied_to_mac_tempfile_without_logging_contents");

  const remoteHead = (await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
git rev-parse HEAD
`)).trim();
  report.mac = { host: options.host, project: options.project, head: remoteHead };
  if (remoteHead !== report.git.head) throw new Error(`mac_checkout_sha_mismatch:${remoteHead}:${report.git.head}`);
  report.steps.push("mac_checkout_sha_matches_local_candidate");

  if (options.buildFirst) {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
scripts/build-ios-intel-simulator-signed.sh
`);
    report.steps.push("ios_simulator_signed_build_succeeded_on_mac");
  }

  await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
export QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
export QUATA_IOS_CHAT_E2E_CONVERSATION_ID=${shellQuote(`sb:${state.thread}`)}
export QUATA_IOS_CHAT_OUTBOX_DURABLE_MARKER=${shellQuote(state.marker)}
export QUATA_IOS_CHAT_OUTBOX_DURABLE_LOG_DIR=${shellQuote(options.remoteLogDir)}
export QUATA_IOS_CHAT_OUTBOX_DURABLE_RESULT_BUNDLE_DIR=${shellQuote(options.remoteResultBundleDir)}
bash scripts/run-ios-chat-outbox-durable-ui-test.sh
`);
  report.steps.push("ios_product_queued_offline_message_recreated_app_and_recovered_network");

  const delivered = await pollMessage(
    config,
    state.b,
    state.thread,
    (message) => (message?.body ?? message?.text ?? message?.message) === state.marker,
    60_000,
  );
  state.message = messageId(delivered);
  const all = rows(await rpc(config, state.b, "quata_chat_get_thread", {
    p_actor_profile_id: state.b.profileId,
    p_thread_id: state.thread,
    p_known_message_ids: [],
    p_limit: 250,
  }), "messages").filter((message) => (message?.body ?? message?.text ?? message?.message) === state.marker);
  if (all.length !== 1) throw new Error("chat_outbox_duplicate_remote_effect");
  report.steps.push("backend_observed_exact_message_once_after_ios_replay");

  await copyRemoteEvidence(options);
  report.status = "passed";
  report.fixture = {
    threadId: state.thread,
    conversationId: `sb:${state.thread}`,
    messageId: state.message,
    exactRemoteCount: all.length,
    markerSha256: sha256(state.marker),
  };
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = safeFailure(error);
} finally {
  const cleanup = { state: "completed", actions: [] };
  let cleanupFailed = false;
  if (config && state.thread && state.message && state.a) {
    try {
      await rpc(config, state.a, "quata_chat_delete_messages", {
        p_actor_profile_id: state.a.profileId,
        p_thread_id: state.thread,
        p_message_ids: [state.message],
      });
      cleanup.actions.push("exact_test_message_deleted");
    } catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
  }
  if (config && state.thread && state.uniqueKey) {
    try {
      const hardCleanup = await hardDeleteTemporaryThread(state.thread, state.uniqueKey);
      cleanup.actions.push("hard_deleted_temporary_thread");
      cleanup.actions.push("cleanup_verified_physical_residue_absent");
      cleanup.hardCleanup = hardCleanup;
    } catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
  }
  for (const session of [state.a, state.b]) {
    if (!config || !session) continue;
    try {
      await logout(config, session);
      cleanup.actions.push(`session_${session.label.toLowerCase()}_revoked`);
    } catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
  }
  if (cleanupFailed) {
    cleanup.state = "failed_or_incomplete";
    if (report.status === "passed") { report.status = "failed"; report.error = cleanup.error ?? "cleanup_residue_detected"; }
  }
  report.cleanup = cleanup;
  if (remoteCredentials) await run("ssh", [options.host, "rm", "-f", remoteCredentials]).catch(() => {});
  if (localCredentials) await rm(dirname(localCredentials), { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Chat outbox durable iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Chat outbox durable iOS evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat outbox durable iOS evidence passed.");
}

function parseArgs(argv) {
  const result = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/StudioProjects/quata-chat-outbox-durable",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    simulatorUdid: process.env.QUATA_IOS_SIMULATOR_UDID?.trim(),
    remoteLogDir: process.env.QUATA_IOS_CHAT_OUTBOX_DURABLE_LOG_DIR?.trim() || "build/reports/ios/chat-outbox-durable",
    remoteResultBundleDir: process.env.QUATA_IOS_CHAT_OUTBOX_DURABLE_RESULT_BUNDLE_DIR?.trim() || "build/reports/ios/chat-outbox-durable/xcresults",
    output: resolve("build-reports/ios/chat-outbox-durable-evidence.json"),
    evidenceDir: resolve("build-reports/ios/chat-outbox-durable-evidence"),
    buildFirst: process.env.QUATA_IOS_BUILD_FIRST === "1",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === "--build-first") {
      result.buildFirst = true;
      continue;
    }
    if (!["--host", "--project", "--derived-data", "--simulator", "--remote-log-dir", "--remote-result-bundle-dir", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) {
      throw new Error("invalid_arguments");
    }
    index += 1;
    if (key === "--host") result.host = value;
    if (key === "--project") result.project = value;
    if (key === "--derived-data") result.derivedDataPath = value;
    if (key === "--simulator") result.simulatorUdid = value;
    if (key === "--remote-log-dir") result.remoteLogDir = value;
    if (key === "--remote-result-bundle-dir") result.remoteResultBundleDir = value;
    if (key === "--out") result.output = resolve(value);
    if (key === "--evidence-dir") result.evidenceDir = resolve(value);
  }
  if (!result.simulatorUdid) throw new Error("missing_environment:QUATA_IOS_SIMULATOR_UDID");
  return result;
}

function usersFromEnvironment() {
  const users = ["A", "B"].map((label) => ({
    label,
    countryCode: env(`QUATA_CHAT_EVIDENCE_${label}_COUNTRY_CODE`),
    phone: env(`QUATA_CHAT_EVIDENCE_${label}_PHONE`),
    password: process.env[`QUATA_CHAT_EVIDENCE_${label}_PASSWORD`],
  }));
  if (users.some((user) => !user.password)) throw new Error("missing_chat_evidence_credentials");
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) {
    throw new Error("chat_evidence_users_must_differ");
  }
  return users;
}

function env(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`missing_environment:${name}`);
  return value;
}

function e164Phone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  if (!country || !digits) throw new Error("ios_e164_credentials_required");
  const local = digits.startsWith(country) ? digits.slice(country.length) : digits;
  if (!local) throw new Error("ios_e164_local_phone_required");
  return `+${country}${local}`;
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

function headers(config, token) {
  return {
    apikey: config.key,
    "content-type": "application/json",
    "x-client-info": "quata-chat-outbox-durable-ios-evidence",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
}

async function jsonRequest(url, requestOptions, prefix) {
  let response;
  try { response = await fetch(url, { ...requestOptions, signal: AbortSignal.timeout(20_000) }); }
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
      client_instance_id: `chat-outbox-durable-ios-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session;
  const profileId = payload?.profile?.id;
  const webSessionToken = payload?.web_session?.token;
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at)) {
    throw new Error(`invalid_auth_response:${user.label}`);
  }
  return { label: user.label, profileId, accessToken: session.access_token, refreshToken: session.refresh_token, expiresAt: session.expires_at, webSessionToken };
}

async function logout(config, session) {
  if (!session) return;
  const failures = [];
  let alreadyRevoked = false;
  try {
    const response = await fetch(`${config.baseUrl}/functions/v1/quata-web-push`, {
      method: "POST",
      headers: { ...headers(config, session.accessToken), "x-quata-web-session": session.webSessionToken },
      body: JSON.stringify({ action: "logout" }),
      signal: AbortSignal.timeout(20_000),
    });
    if ([401, 403].includes(response.status)) alreadyRevoked = true;
    else if (!response.ok) throw new Error(`web_logout_failed:http_${response.status}`);
  } catch (error) { failures.push(error); }
  try {
    const response = await fetch(`${config.baseUrl}/auth/v1/logout?scope=local`, {
      method: "POST",
      headers: headers(config, session.accessToken),
      body: "{}",
      signal: AbortSignal.timeout(20_000),
    });
    if ([401, 403].includes(response.status)) alreadyRevoked = true;
    else if (!response.ok) throw new Error(`auth_logout_failed:http_${response.status}`);
  } catch (error) { failures.push(error); }
  if (failures.length && !alreadyRevoked) throw new Error("session_logout_incomplete");
}

function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
}

function rows(payload, key) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.[key])) return payload[key];
  if (Array.isArray(payload?.data?.[key])) return payload.data[key];
  if (Array.isArray(payload?.messages)) return payload.messages;
  if (payload?.message) return [payload.message];
  return [];
}

function threadId(payload) {
  const raw = payload?.thread_id ?? payload?.id ?? payload?.thread?.id ?? rows(payload, "threads")[0]?.id;
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("chat_contract_invalid:thread_id");
  return value;
}

function messageId(payload) {
  const raw = payload?.message_id ?? payload?.id ?? payload?.message?.id ?? rows(payload, "messages")[0]?.id;
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("chat_contract_invalid:message_id");
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
    const found = rows(detail, "messages").find(predicate);
    if (found) return found;
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  throw new Error("chat_backend_poll_timeout");
}

async function hardDeleteTemporaryThread(thread, uniqueKey) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) {
    throw new Error("missing_hard_cleanup_authorization");
  }
  if (!uniqueKey.startsWith("qadata-chat-outbox-durable-ios-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
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
      "select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-outbox-durable-ios-%' for update",
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

async function copyRemoteEvidence(values) {
  const target = join(values.evidenceDir, "mac-ui-report");
  const resultTarget = join(values.evidenceDir, "xcresults");
  await rm(values.evidenceDir, { recursive: true, force: true });
  await mkdir(values.evidenceDir, { recursive: true });
  await run("scp", ["-r", `${values.host}:${values.project}/${values.remoteLogDir}`, target]);
  await run("scp", ["-r", `${values.host}:${values.project}/${values.remoteResultBundleDir}`, resultTarget]).catch((error) => {
    report.evidence.resultBundleCopyWarning = safeFailure(error);
  });
  report.evidence.uiReportDirectory = target;
  report.evidence.resultBundleDirectory = resultTarget;
}

async function gitMetadata() {
  const head = (await runSilent("git", ["rev-parse", "HEAD"])).trim();
  const status = await runSilent("git", ["status", "--porcelain"]);
  return { head, workingTreeDirty: status.trim().length > 0 };
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}

async function runSshScript(host, script) {
  return run("ssh", [host, "bash", "-s"], { input: script, timeoutMs: 30 * 60 * 1000 });
}

async function run(command, args, options = {}) {
  const output = await runCapture(command, args, options);
  if (output.trim()) report.lastCommandOutputTail = redactedTail(output);
  return output;
}

function runSilent(command, args, options = {}) {
  return runCapture(command, args, { ...options, silent: true });
}

function runCapture(command, args, options = {}) {
  if (!existsSync(process.cwd())) throw new Error("working_directory_missing");
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, {
      cwd: process.cwd(),
      env: process.env,
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true,
    });
    let stdout = "";
    let stderr = "";
    const timeout = setTimeout(() => {
      child.kill("SIGTERM");
      rejectPromise(new Error(`command_timeout:${command}`));
    }, options.timeoutMs ?? 15 * 60 * 1000);
    child.stdout.on("data", (chunk) => { stdout += chunk; });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("error", (error) => {
      clearTimeout(timeout);
      rejectPromise(error);
    });
    child.on("close", (code) => {
      clearTimeout(timeout);
      const combined = `${stdout}${stderr}`;
      if (code === 0) resolvePromise(combined);
      else rejectPromise(new Error(`command_failed:${command}:${code}:${redactedTail(combined)}`));
    });
    if (options.input) child.stdin.end(options.input);
    else child.stdin.end();
  });
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

function redactedTail(text) {
  return safeFailure(String(text).split(/\r?\n/).slice(-40).join("\n"));
}
