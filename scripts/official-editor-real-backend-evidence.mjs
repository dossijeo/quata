#!/usr/bin/env node
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { Client } from "pg";

const CHECK = "OFFICIAL-EDITOR-REAL-BACKEND-001";
const OPT_IN = "I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const REQUIRED_ENV = [
  "QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN",
  "QUATA_OFFICIAL_E2E_COUNTRY_CODE",
  "QUATA_OFFICIAL_E2E_OFFICIAL_PHONE",
  "QUATA_OFFICIAL_E2E_NONOFFICIAL_PHONE",
  "QUATA_OFFICIAL_E2E_PASSWORD",
];

function parseArgs(argv) {
  if (argv.length === 2 && argv[0] === "--out" && argv[1]?.trim()) return { output: argv[1] };
  if (argv.length === 1 && argv[0] === "--help") {
    console.log("Usage: node scripts/official-editor-real-backend-evidence.mjs --out <safe-local-report.json>");
    process.exit(0);
  }
  throw new Error("invalid_arguments");
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(url)) throw new Error("invalid_public_supabase_url");
  return { url, key };
}

function requireEnvironment() {
  const missing = REQUIRED_ENV.filter((name) => !process.env[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  if (process.env.QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN !== OPT_IN) throw new Error("mutation_opt_in_required");
  return {
    countryCode: process.env.QUATA_OFFICIAL_E2E_COUNTRY_CODE.trim(),
    officialPhone: process.env.QUATA_OFFICIAL_E2E_OFFICIAL_PHONE.trim(),
    nonofficialPhone: process.env.QUATA_OFFICIAL_E2E_NONOFFICIAL_PHONE.trim(),
    password: process.env.QUATA_OFFICIAL_E2E_PASSWORD,
    dbUrlFile: process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbTlsCaFile: process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE,
  };
}

async function pgConnectionConfig(config) {
  const raw = (await readFile(config.dbUrlFile, "utf8")).trim();
  const ca = await readFile(config.dbTlsCaFile, "utf8");
  const url = new URL(raw);
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true } };
}

async function withPg(config, action) {
  const client = new Client(await pgConnectionConfig(config));
  await client.connect();
  try {
    return await action(client);
  } finally {
    await client.end();
  }
}

async function readProfiles(config) {
  return withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query({
        text: `select id, display_name, is_official
               from public.community_profiles
               where country_code = $1 and phone_local = any($2::text[])
               order by phone_local`,
        values: [config.countryCode, [config.officialPhone, config.nonofficialPhone]],
      });
      await client.query("rollback");
      const official = rows.find((row) => row.is_official === true);
      const nonofficial = rows.find((row) => row.is_official === false);
      if (!official || !nonofficial) throw new Error("profile_permission_fixture_unavailable");
      return { official, nonofficial };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function cleanupPosts(config, ids, groupId) {
  if (!ids.length && !groupId) return { state: "not_needed", deletedRows: 0, remainingRows: 0 };
  return withPg(config, async (client) => {
    await client.query("begin");
    try {
      await client.query({
        text: "delete from public.official_post_likes where official_post_id = any($1::uuid[])",
        values: [ids],
      });
      await client.query({
        text: "delete from public.official_post_comments where official_post_id = any($1::uuid[])",
        values: [ids],
      });
      const deleted = await client.query({
        text: `delete from public.official_posts
               where id = any($1::uuid[]) or translation_group_id = $2::uuid`,
        values: [ids, groupId],
      });
      const remaining = await client.query({
        text: `select count(*)::int as count
               from public.official_posts
               where id = any($1::uuid[]) or translation_group_id = $2::uuid`,
        values: [ids, groupId],
      });
      if (remaining.rows[0]?.count !== 0) throw new Error("cleanup_verification_failed");
      await client.query("commit");
      return { state: "hard_deleted_verified", deletedRows: deleted.rowCount, remainingRows: 0 };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function assertNoMarkerRows(config, marker, groupId) {
  return withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query({
        text: `select count(*)::int as count
               from public.official_posts
               where translation_group_id = $1::uuid
                  or title like $2
                  or content_html like $2`,
        values: [groupId, `%${marker}%`],
      });
      await client.query("rollback");
      if (rows[0]?.count !== 0) throw new Error("marker_cleanup_verification_failed");
      return { state: "verified_absent" };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

function commonHeaders(key) {
  return { apikey: key, "content-type": "application/json", "x-client-info": "quata-official-editor-real-evidence" };
}

async function postJson(url, headers, body, expectedOk = true) {
  let response;
  try {
    response = await fetch(url, { method: "POST", headers, body: JSON.stringify(body), signal: AbortSignal.timeout(20_000) });
  } catch {
    throw new Error("public_request_failed:network");
  }
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (expectedOk && !response.ok) {
    const backendCode = typeof payload?.code === "string" ? `:${payload.code.replace(/[^A-Z0-9_]/gi, "")}` : "";
    throw new Error(`public_request_failed:http_${response.status}${backendCode}`);
  }
  return { status: response.status, ok: response.ok, payload };
}

async function login(backend, config, phone) {
  const response = await postJson(`${backend.url}/functions/v1/quata-auth-bridge`, commonHeaders(backend.key), {
    action: "web_login",
    country_code: config.countryCode,
    phone_local: phone,
    password: config.password,
    client_instance_id: `official-editor-real-${crypto.randomUUID()}`,
  });
  const session = response.payload?.session ?? response.payload;
  const accessToken = session?.access_token;
  if (typeof accessToken !== "string" || !accessToken) throw new Error("invalid_auth_response");
  return { accessToken };
}

function postgrestHeaders(backend, session) {
  return {
    ...commonHeaders(backend.key),
    authorization: `Bearer ${session.accessToken}`,
    prefer: "return=representation",
  };
}

async function createOfficialPost(backend, session, post) {
  const response = await postJson(`${backend.url}/rest/v1/official_posts`, postgrestHeaders(backend, session), post, true);
  if (!Array.isArray(response.payload) || response.payload.length !== 1 || response.payload[0]?.id !== post.id) {
    throw new Error("invalid_official_post_create_response");
  }
  return response.payload[0];
}

async function tryCreateForbiddenOfficialPost(backend, session, post) {
  const response = await postJson(`${backend.url}/rest/v1/official_posts`, postgrestHeaders(backend, session), post, false);
  if (response.ok) throw new Error("nonofficial_publish_unexpectedly_allowed");
  return { status: response.status };
}

async function readPublicOfficialPost(backend, id) {
  const url = new URL(`${backend.url}/rest/v1/official_posts`);
  url.searchParams.set("select", "id,title,content_html,translation_group_id,is_published,deleted_at");
  url.searchParams.set("id", `eq.${id}`);
  const response = await fetch(url, { headers: { apikey: backend.key }, signal: AbortSignal.timeout(20_000) });
  if (!response.ok) throw new Error(`public_read_failed:http_${response.status}`);
  const payload = await response.json();
  if (!Array.isArray(payload) || payload.length !== 1) throw new Error("public_read_missing_created_post");
  return payload[0];
}

function postPayload(profileId, marker, groupId, id = crypto.randomUUID()) {
  return {
    id,
    profile_id: profileId,
    title: `QUATA official editor E2E ${marker}`,
    summary: `Reversible Official editor evidence ${marker}`,
    post_type: "news",
    content_html: `<p>QUATA Official editor reversible evidence ${marker}</p>`,
    read_more_label: "Leer más",
    language: "es",
    translation_group_id: groupId,
    media_url: null,
    media_type: null,
    link_url: null,
    is_live: false,
    is_published: true,
    published_at: new Date().toISOString(),
  };
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "unknown";
  const code = [
    "invalid_arguments",
    "missing_environment",
    "mutation_opt_in_required",
    "missing_public_supabase_configuration",
    "invalid_public_supabase_url",
    "profile_permission_fixture_unavailable",
    "invalid_auth_response",
    "public_request_failed",
    "nonofficial_publish_unexpectedly_allowed",
    "invalid_official_post_create_response",
    "public_read_missing_created_post",
    "cleanup_verification_failed",
    "marker_cleanup_verification_failed",
  ].find((item) => message.startsWith(item)) ?? "unexpected_official_editor_real_backend_failure";
  const httpStatus = /http_(\d{3})/.exec(message)?.[1];
  const backendErrorCode = /http_\d{3}:([A-Z0-9_]+)/i.exec(message)?.[1];
  return { status: "failed", error: code, ...(httpStatus ? { httpStatus: Number(httpStatus) } : {}), ...(backendErrorCode ? { backendErrorCode } : {}) };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const startedAt = new Date().toISOString();
  const config = requireEnvironment();
  const backend = await publicConfig();
  const marker = `official-editor-${crypto.randomUUID()}`;
  const translationGroupId = crypto.randomUUID();
  const createdIds = [];
  let cleanup = { state: "not_started" };
  let stage = "initializing";
  try {
    stage = "read_permission_fixtures";
    const profiles = await readProfiles(config);
    stage = "login_official";
    const officialSession = await login(backend, config, config.officialPhone);
    stage = "login_nonofficial";
    const nonofficialSession = await login(backend, config, config.nonofficialPhone);
    stage = "assert_nonofficial_denied";
    const nonofficialAttempt = await tryCreateForbiddenOfficialPost(
      backend,
      nonofficialSession,
      postPayload(profiles.nonofficial.id, marker, translationGroupId),
    );
    stage = "create_official_post";
    const officialPost = postPayload(profiles.official.id, marker, translationGroupId);
    const created = await createOfficialPost(backend, officialSession, officialPost);
    createdIds.push(created.id);
    stage = "public_readback";
    const publicRead = await readPublicOfficialPost(backend, created.id);
    if (publicRead.title !== officialPost.title || publicRead.translation_group_id !== translationGroupId || publicRead.is_published !== true || publicRead.deleted_at !== null) {
      throw new Error("public_read_missing_created_post");
    }
    stage = "cleanup_created_posts";
    cleanup = await cleanupPosts(config, createdIds, translationGroupId);
    stage = "post_cleanup_readback";
    const postCleanupReadback = await assertNoMarkerRows(config, marker, translationGroupId);
    const report = {
      check: CHECK,
      status: "passed",
      startedAt,
      finishedAt: new Date().toISOString(),
      marker,
      mutationPolicy: "Creates one reversible text-only official post through authenticated PostgREST, verifies public readback, hard-deletes by exact IDs in a parameterized transaction, and verifies absence by marker.",
      fixtures: { officialProfile: "verified_official", nonofficialProfile: "verified_nonofficial" },
      evidence: {
        nonofficialPublish: { state: "denied_by_backend", status: nonofficialAttempt.status },
        officialPublish: { state: "created_by_backend", postIds: createdIds, translationGroupId },
        publicReadback: { state: "verified", postId: created.id, markerObserved: publicRead.title.includes(marker) },
      },
      cleanup,
      postCleanupReadback,
    };
    const output = resolve(args.output);
    await mkdir(dirname(output), { recursive: true });
    await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
    console.log(`Official editor real backend evidence written: ${output}`);
  } catch (error) {
    try {
      cleanup = await cleanupPosts(config, createdIds, translationGroupId);
    } catch {
      cleanup = { state: "rollback_pending", action: "hard_delete_official_posts_by_recorded_ids_or_translation_group_id" };
    }
    const output = args?.output ? resolve(args.output) : null;
    const report = { check: CHECK, startedAt, finishedAt: new Date().toISOString(), marker, createdIds, translationGroupId, failureStage: stage, cleanup, ...safeFailure(error) };
    if (output) {
      await mkdir(dirname(output), { recursive: true });
      await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
    }
    console.error(JSON.stringify({ check: CHECK, cleanup, ...safeFailure(error) }));
    process.exitCode = 1;
  }
}

main();
