#!/usr/bin/env node
import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { spawn } from "node:child_process";
import pg from "pg";

const fixtureCount = 101;
const productionPageSize = 100;
const credentialsEnvironment = "QUATA_CHAT_GROUP_CREDENTIALS_FILE";
const cleanupAuthorizationEnvironment = "QUATA_CONVERSATIONS_DEEP_PAGINATION_CLEANUP_AUTHORIZATION";
const cleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CONVERSATIONS_DEEP_PAGINATION_CLEANUP";
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function parseArgs(argv) {
  if (argv.length === 2 && argv[0] === "--out" && argv[1]?.trim()) return { output: resolve(argv[1]) };
  throw new Error("invalid_arguments");
}

function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

function safeError(error) {
  const message = String(error?.message ?? "unknown");
  const known = [
    "invalid_arguments",
    "missing_credentials_file",
    "invalid_credentials_file",
    "missing_public_supabase_configuration",
    "invalid_auth_response",
    "deep_pagination_fixture_create_failed",
    "deep_pagination_first_page_invalid",
    "deep_pagination_second_page_invalid",
    "deep_pagination_fixture_boundary_invalid",
    "deep_pagination_cursor_invalid",
    "missing_cleanup_authorization",
    "cleanup_residue_detected",
  ].find((prefix) => message.startsWith(prefix));
  return known ?? "unexpected_deep_pagination_failure";
}

async function run(command, args) {
  return await new Promise((resolvePromise, reject) => {
    let stdout = "";
    let stderr = "";
    const child = spawn(command, args, { shell: false, stdio: ["ignore", "pipe", "pipe"] });
    child.stdout.on("data", (chunk) => { stdout += chunk.toString(); });
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolvePromise(stdout) : reject(new Error(`command_failed:${command}:${code}:${stderr.trim()}`)));
  });
}

async function gitMetadata() {
  const head = (await run("git", ["rev-parse", "HEAD"])).trim();
  const dirty = (await run("git", ["status", "--porcelain"])).trim().length > 0;
  return { head, workingTreeDirty: dirty };
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

async function credentials() {
  const file = process.env[credentialsEnvironment]?.trim();
  if (!file) throw new Error("missing_credentials_file");
  const parsed = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  const convert = (entry) => ({
    countryCode: String(entry?.country_code ?? entry?.countryCode ?? "").trim(),
    phone: String(entry?.phone ?? "").trim(),
    password: String(entry?.password ?? ""),
  });
  const users = [convert(parsed.a), convert(parsed.b)];
  if (users.some((user) => !user.countryCode || !user.phone || !user.password)) throw new Error("invalid_credentials_file");
  return users;
}

function headers(config, token) {
  return {
    apikey: config.key,
    "content-type": "application/json",
    "x-client-info": "quata-conversations-deep-pagination-evidence",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
}

async function requestJson(url, options, prefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(30_000) }); }
  catch { throw new Error(`${prefix}:network`); }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  try { return text ? JSON.parse(text) : {}; }
  catch { throw new Error(`${prefix}:invalid_json`); }
}

async function login(config, user, label) {
  const payload = await requestJson(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: headers(config),
    body: JSON.stringify({
      action: "web_login",
      country_code: user.countryCode,
      phone_local: user.phone,
      password: user.password,
      client_instance_id: `conversations-deep-pagination-${label}-${randomUUID()}`,
    }),
  }, "auth_failed");
  const profileId = payload?.profile?.id;
  const accessToken = payload?.session?.access_token;
  const webSessionToken = payload?.web_session?.token;
  if (!uuid.test(profileId ?? "") || !accessToken || !webSessionToken) throw new Error("invalid_auth_response");
  return { profileId, accessToken, webSessionToken };
}

async function rpc(config, session, name, body) {
  return await requestJson(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `rpc_failed:${name}`);
}

function threadId(payload) {
  const value = Number(payload?.thread_id ?? payload?.id ?? payload?.thread?.id);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("deep_pagination_fixture_create_failed:thread_id");
  return value;
}

function rowThreadId(row) {
  const value = Number(row?.thread_id ?? row?.id);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("deep_pagination_fixture_boundary_invalid:row_id");
  return value;
}

function nextCursor(payload) {
  const value = payload?.next_cursor;
  const thread = Number(value?.thread_id);
  if (!value || !Number.isSafeInteger(thread) || thread <= 0 || !value.updated_at) {
    throw new Error("deep_pagination_cursor_invalid");
  }
  return {
    lastMessageAt: value.last_message_at ?? null,
    updatedAt: value.updated_at,
    threadId: thread,
  };
}

async function databaseClient() {
  const dbUrlFile = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaFile = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlFile, "utf8"), readFile(tlsCaFile, "utf8")]);
  const parsed = new URL(connectionString.trim());
  parsed.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsed.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsed.hostname },
  });
  await client.connect();
  return client;
}

async function cleanupThreads({ actorProfileId, uniqueKeys, subjectPrefix }) {
  if (process.env[cleanupAuthorizationEnvironment]?.trim() !== cleanupAuthorizationValue) {
    throw new Error("missing_cleanup_authorization");
  }
  if (!subjectPrefix.startsWith("QADATA deep pagination ") || uniqueKeys.some((key) => !key.startsWith("qadata-conversations-deep-pagination-"))) {
    throw new Error("cleanup_residue_detected:unsafe_fixture_identity");
  }
  const client = await databaseClient();
  try {
    await client.query("begin");
    const owned = await client.query(
      `select id::bigint as id, unique_key
         from public.chat_threads
        where created_by_profile_id = $1::uuid
          and unique_key = any($2::text[])
          and subject like $3
        order by id
        for update`,
      [actorProfileId, uniqueKeys, `${subjectPrefix}%`],
    );
    const ownedIds = owned.rows.map((row) => Number(row.id));
    const deleted = ownedIds.length === 0 ? { rowCount: 0 } : await client.query(
      `delete from public.chat_threads
        where id = any($1::bigint[])
          and created_by_profile_id = $2::uuid
          and unique_key = any($3::text[])
        returning id`,
      [ownedIds, actorProfileId, uniqueKeys],
    );
    if (deleted.rowCount !== ownedIds.length) throw new Error("cleanup_residue_detected:thread_delete_count");
    const residue = await client.query(
      `select
        (select count(*)::int from public.chat_threads where unique_key = any($1::text[])) as chat_threads,
        (select count(*)::int from public.chat_messages where thread_id = any($2::bigint[])) as chat_messages,
        (select count(*)::int from public.chat_participants where thread_id = any($2::bigint[])) as chat_participants,
        (select count(*)::int from public.chat_attachments where thread_id = any($2::bigint[])) as chat_attachments,
        (select count(*)::int from public.chat_message_states where thread_id = any($2::bigint[])) as chat_message_states,
        (select count(*)::int from public.chat_profile_blocks where thread_id = any($2::bigint[])) as chat_profile_blocks,
        (select count(*)::int from public.chat_events where thread_id = any($2::bigint[])) as chat_events,
        (select count(*)::int from public.conversation_user_state where conversation_id = any($2::bigint[])) as conversation_user_state`,
      [uniqueKeys, ownedIds],
    );
    const residueCounts = residue.rows[0] ?? {};
    if (Object.values(residueCounts).some((value) => Number(value) !== 0)) {
      throw new Error("cleanup_residue_detected:physical_rows");
    }
    await client.query("commit");
    return { discoveredThreads: ownedIds.length, deletedThreads: deleted.rowCount, residueCounts };
  } catch (error) {
    await client.query("rollback").catch(() => {});
    throw error;
  } finally {
    await client.end().catch(() => {});
  }
}

async function logout(config, session) {
  if (!session) return;
  await requestJson(`${config.baseUrl}/functions/v1/quata-web-push`, {
    method: "POST",
    headers: { ...headers(config, session.accessToken), "x-quata-web-session": session.webSessionToken },
    body: JSON.stringify({ action: "logout" }),
  }, "web_logout_failed");
  const response = await fetch(`${config.baseUrl}/auth/v1/logout?scope=local`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: "{}",
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`auth_logout_failed:http_${response.status}`);
}

async function writeReport(output, report) {
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const startedAt = new Date().toISOString();
  const runId = randomUUID();
  const subjectPrefix = `QADATA deep pagination ${runId} `;
  const uniqueKeyPrefix = `qadata-conversations-deep-pagination-${runId}-`;
  const uniqueKeys = Array.from({ length: fixtureCount }, (_, index) => `${uniqueKeyPrefix}${String(index).padStart(3, "0")}`);
  const steps = [];
  let config;
  let actor;
  let recipient;
  let cleanup = { state: "not_started" };
  let failure = null;
  let result = null;
  let git = null;
  try {
    git = await gitMetadata();
    if (git.workingTreeDirty) throw new Error("deep_pagination_fixture_create_failed:dirty_tree");
    config = await publicBackendConfig();
    const [actorCredentials, recipientCredentials] = await credentials();
    actor = await login(config, actorCredentials, "actor");
    recipient = await login(config, recipientCredentials, "recipient");
    steps.push("two_authorized_profiles_logged_in");

    const createdThreadIds = [];
    for (let index = 0; index < fixtureCount; index += 1) {
      const marker = `qadata-deep-pagination-${runId}-${String(index).padStart(3, "0")}`;
      const created = await rpc(config, actor, "quata_chat_start_thread", {
        p_actor_profile_id: actor.profileId,
        p_recipient_profile_ids: [recipient.profileId],
        p_subject: `${subjectPrefix}${String(index).padStart(3, "0")}`,
        p_type: "group",
        p_message: marker,
        p_unique_key: uniqueKeys[index],
        p_community_id: null,
      });
      createdThreadIds.push(threadId(created));
      if ((index + 1) % 10 === 0 || index + 1 === fixtureCount) {
        process.stderr.write(`deep-pagination fixture ${index + 1}/${fixtureCount}\n`);
      }
    }
    if (new Set(createdThreadIds).size !== fixtureCount) throw new Error("deep_pagination_fixture_create_failed:duplicate_thread");
    steps.push("one_hundred_and_one_owned_threads_created_through_authenticated_product_rpc");

    const first = await rpc(config, actor, "quata_chat_get_inbox_page", {
      p_actor_profile_id: actor.profileId,
      p_limit: productionPageSize,
      p_before_last_message_at: null,
      p_before_updated_at: null,
      p_before_thread_id: null,
    });
    const firstRows = Array.isArray(first?.threads) ? first.threads : [];
    if (firstRows.length !== productionPageSize || first?.has_more !== true) {
      throw new Error("deep_pagination_first_page_invalid");
    }
    const cursor = nextCursor(first);
    const second = await rpc(config, actor, "quata_chat_get_inbox_page", {
      p_actor_profile_id: actor.profileId,
      p_limit: productionPageSize,
      p_before_last_message_at: cursor.lastMessageAt,
      p_before_updated_at: cursor.updatedAt,
      p_before_thread_id: cursor.threadId,
    });
    const secondRows = Array.isArray(second?.threads) ? second.threads : [];
    if (secondRows.length < 1) throw new Error("deep_pagination_second_page_invalid");
    const observedIds = [...firstRows, ...secondRows].map(rowThreadId);
    const fixtureIds = new Set(createdThreadIds);
    const observedFixtureIds = new Set(observedIds.filter((id) => fixtureIds.has(id)));
    if (observedFixtureIds.size !== fixtureCount || new Set(observedIds).size !== observedIds.length) {
      throw new Error("deep_pagination_fixture_boundary_invalid");
    }
    steps.push("real_backend_crossed_production_page_size_with_all_fixture_threads_exactly_once");
    result = {
      rpc: "quata_chat_get_inbox_page",
      productionPageSize,
      fixtureThreads: fixtureCount,
      pagesObserved: 2,
      firstPageRows: firstRows.length,
      secondPageRows: secondRows.length,
      firstPageHasMore: true,
      cursorFields: ["last_message_at", "updated_at", "thread_id"],
      fixtureThreadsObservedExactlyOnce: true,
      actorProfileSha256: sha256(actor.profileId),
    };
  } catch (error) {
    failure = safeError(error);
  } finally {
    if (actor) {
      try {
        const details = await cleanupThreads({ actorProfileId: actor.profileId, uniqueKeys, subjectPrefix });
        cleanup = { state: "completed", ...details };
        steps.push("owned_fixture_threads_deleted_with_zero_physical_residue");
      } catch (error) {
        cleanup = { state: "failed_or_incomplete", error: safeError(error) };
        failure ??= cleanup.error;
      }
    }
    if (config) {
      const sessionCleanup = [];
      for (const session of [recipient, actor]) {
        if (!session) continue;
        try { await logout(config, session); sessionCleanup.push("revoked"); }
        catch { sessionCleanup.push("failed"); failure ??= "session_cleanup_failed"; }
      }
      cleanup.sessions = sessionCleanup;
      if (sessionCleanup.some((state) => state !== "revoked")) cleanup.state = "failed_or_incomplete";
    }
  }

  const passed = failure == null && cleanup.state === "completed" && result?.fixtureThreadsObservedExactlyOnce === true;
  const report = {
    check: "CONVERSATIONS-DEEP-PAGINATION",
    status: passed ? "passed" : "failed",
    startedAt,
    finishedAt: new Date().toISOString(),
    git,
    mode: "real_backend_reversible_authenticated_product_rpc",
    mutationPolicy: "Creates 101 uniquely keyed QADATA group threads through the authenticated product RPC, reads across the production page-size boundary, then hard-deletes only the owned key set and verifies zero physical residue.",
    steps,
    result,
    cleanup,
    ...(failure ? { error: failure } : {}),
  };
  await writeReport(args.output, report);
  process.stdout.write(`${passed ? "PASS" : "FAIL"} ${args.output}\n`);
  if (!passed) process.exitCode = 1;
}

main().catch((error) => {
  process.stderr.write(`${safeError(error)}\n`);
  process.exitCode = 1;
});
