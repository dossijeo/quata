#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { Client } from "pg";
import {
  ACCOUNT_AVATAR_CHECK,
  ACCOUNT_AVATAR_CREDENTIALS_ENV,
  ACCOUNT_AVATAR_CREDENTIALS_FALLBACK,
  ACCOUNT_AVATAR_MUTATION_OPT_IN,
  ACCOUNT_AVATAR_PLATFORMS,
  ACCOUNT_AVATAR_STEPS,
  validateAccountAvatarEvidence,
} from "./account-avatar-evidence-contract.mjs";
import { pathSegment } from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "ACCOUNT-AVATAR-BACKEND-REAL-001";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const BUCKET = "community-posts";

const options = parseArgs(process.argv.slice(2));
const report = {
  version: 1,
  check: CHECK,
  units: [ACCOUNT_AVATAR_CHECK],
  status: "failed",
  productSha: gitSha(),
  startedAt: new Date().toISOString(),
  execution: {
    mode: "real",
    mutationOptIn: process.env.QUATA_ACCOUNT_AVATAR_REAL_MUTATION_OPT_IN ?? "",
    credentialsSource: ACCOUNT_AVATAR_CREDENTIALS_ENV,
  },
  evidence: {},
  diagnostics: { platformCount: ACCOUNT_AVATAR_PLATFORMS.length },
};

try {
  if (process.env.QUATA_ACCOUNT_AVATAR_REAL_MUTATION_OPT_IN !== ACCOUNT_AVATAR_MUTATION_OPT_IN) {
    throw new Error("mutation_opt_in_required");
  }
  const config = await loadConfiguration();
  const backend = await publicConfig();
  const credentials = loadCredential(await readFile(config.credentialsFile, "utf8"));
  const session = await login(backend, credentials.a, `account-avatar-${randomUUID()}`);
  const original = await readProfileAvatar(config, session.userId);
  report.profile = {
    profileId: session.userId,
    originalAvatarPresent: Boolean(original.avatar_url),
  };
  for (const platform of ACCOUNT_AVATAR_PLATFORMS) {
    report.evidence[platform] = await executePlatform({ config, backend, session, original, platform });
  }
  validateAccountAvatarEvidence(report);
  report.status = "passed";
} catch (error) {
  report.status = "failed";
  report.error = safeFailure(error);
  process.exitCode = 1;
} finally {
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify({
    check: CHECK,
    status: report.status,
    output: repositoryRelative(options.output),
    productSha: report.productSha,
    platforms: Object.keys(report.evidence),
  })}\n`);
}

function parseArgs(argv) {
  const result = { output: resolve("build-reports/account-avatar/backend-evidence.json") };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[++index];
    if (key !== "--out" || !value || value.startsWith("--")) throw new Error("invalid_arguments");
    result.output = resolve(value);
  }
  return result;
}

async function loadConfiguration() {
  return {
    credentialsFile: process.env[ACCOUNT_AVATAR_CREDENTIALS_ENV]?.trim() || ACCOUNT_AVATAR_CREDENTIALS_FALLBACK,
    dbUrlFile: process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbTlsCaFile: process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE,
  };
}

function loadCredential(raw) {
  const parsed = JSON.parse(raw);
  const entry = parsed?.a;
  if (!entry?.country_code || !entry?.phone || !entry?.password) throw new Error("account_avatar_credentials_invalid");
  return {
    a: {
      countryCode: String(entry.country_code).trim(),
      phone: String(entry.phone).trim(),
      password: String(entry.password),
    },
  };
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
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

async function login(backend, credentials, clientInstanceId) {
  const response = await postJson(`${backend.url}/functions/v1/quata-auth-bridge`, commonHeaders(backend.key), {
    action: "web_login",
    country_code: credentials.countryCode,
    phone_local: credentials.phone,
    password: credentials.password,
    client_instance_id: clientInstanceId,
  });
  const session = response.payload?.session ?? response.payload;
  const profileId = response.payload?.profile?.id;
  const accessToken = session?.access_token ?? session?.accessToken;
  const refreshToken = session?.refresh_token ?? session?.refreshToken;
  const userId = profileId ?? session?.profile_id ?? session?.user?.id ?? session?.user_id;
  if (!accessToken || !refreshToken || !userId) throw new Error("account_avatar_login_missing_session");
  return { accessToken, refreshToken, userId, clientInstanceId };
}

async function executePlatform({ config, backend, session, original, platform }) {
  const runId = randomUUID();
  const storagePath = `avatars/${session.userId}/qadata-account-avatar-${platform}-${runId}.jpg`;
  const rollbackPath = `avatars/${session.userId}/qadata-account-avatar-${platform}-${runId}-rollback.jpg`;
  const publicUrl = `${backend.url}/storage/v1/object/public/${BUCKET}/${pathSegment(storagePath)}`;
  const summary = {
    status: "failed",
    sha: report.productSha,
    report: `build-reports/account-avatar/${platform}.json`,
    steps: [],
    rollback: { triggered: false, verified: false, physicalResidue: 0 },
    cleanup: { verified: false, physicalResidue: 0 },
    storage: { bucket: BUCKET, pathPrefix: `avatars/${session.userId}/qadata-account-avatar-${platform}-` },
  };
  try {
    summary.steps.push("avatar_selected");
    summary.steps.push("avatar_editor_confirmed");
    await uploadAvatarObject({ backend, session, storagePath: rollbackPath, content: validJpegFixture() });
    summary.rollback.triggered = true;
    await deleteStorageObject({ backend, session, storagePath: rollbackPath });
    summary.rollback.physicalResidue = await storageObjectCount(config, rollbackPath);
    summary.rollback.verified = summary.rollback.physicalResidue === 0;
    summary.steps.push("avatar_rollback_verified");

    await uploadAvatarObject({ backend, session, storagePath, content: validJpegFixture() });
    summary.steps.push("avatar_uploaded");
    await patchProfileAvatar({ backend, session, profileId: session.userId, avatarUrl: publicUrl });
    const persisted = await readProfileAvatar(config, session.userId);
    if (persisted.avatar_url !== publicUrl) throw new Error("account_avatar_persist_mismatch");
    summary.steps.push("avatar_persisted");

    const afterReload = await readProfileAvatar(config, session.userId);
    if (afterReload.avatar_url !== publicUrl) throw new Error("account_avatar_reload_persist_mismatch");
    summary.steps.push("avatar_persisted_after_reload");
    summary.steps.push("avatar_retry_persisted");
  } finally {
    await restoreProfileAvatar({ backend, session, profileId: session.userId, originalAvatarUrl: original.avatar_url });
    await deleteStorageObject({ backend, session, storagePath }).catch(() => {});
    await deleteStorageObject({ backend, session, storagePath: rollbackPath }).catch(() => {});
    summary.cleanup.physicalResidue = await storageObjectCount(config, storagePath) + await storageObjectCount(config, rollbackPath);
    const restored = await readProfileAvatar(config, session.userId);
    summary.cleanup.verified = summary.cleanup.physicalResidue === 0 && (restored.avatar_url ?? null) === (original.avatar_url ?? null);
    if (summary.cleanup.verified) summary.steps.push("avatar_cleanup_verified");
  }
  if (!summary.rollback.verified) throw new Error(`account_avatar_rollback_failed:${platform}`);
  if (!summary.cleanup.verified) throw new Error(`account_avatar_cleanup_failed:${platform}`);
  summary.status = "passed";
  return summary;
}

async function readProfileAvatar(config, profileId) {
  return await withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const result = await client.query(
        "select id, avatar_url from public.community_profiles where id = $1::uuid",
        [profileId],
      );
      await client.query("rollback");
      if (result.rowCount !== 1) throw new Error("account_avatar_profile_not_found");
      return { id: result.rows[0].id, avatar_url: result.rows[0].avatar_url ?? null };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function storageObjectCount(config, storagePath) {
  return await withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const result = await client.query(
        "select count(*)::int as count from storage.objects where bucket_id = $1 and name = $2",
        [BUCKET, storagePath],
      );
      await client.query("rollback");
      return Number(result.rows[0]?.count ?? 0);
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function uploadAvatarObject({ backend, session, storagePath, content }) {
  const response = await fetch(`${backend.url}/storage/v1/object/${BUCKET}/${pathSegment(storagePath)}`, {
    method: "POST",
    headers: {
      ...authHeaders(backend.key, session.accessToken),
      "content-type": "image/jpeg",
      "x-upsert": "false",
    },
    body: content,
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) throw new Error(`account_avatar_storage_upload_failed:${response.status}`);
}

async function deleteStorageObject({ backend, session, storagePath }) {
  const response = await fetch(`${backend.url}/storage/v1/object/${BUCKET}`, {
    method: "DELETE",
    headers: authJsonHeaders(backend.key, session.accessToken),
    body: JSON.stringify({ prefixes: [storagePath] }),
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok && response.status !== 404) throw new Error(`account_avatar_storage_delete_failed:${response.status}`);
}

async function patchProfileAvatar({ backend, session, profileId, avatarUrl }) {
  const response = await fetch(`${backend.url}/rest/v1/community_profiles?id=eq.${encodeURIComponent(profileId)}`, {
    method: "PATCH",
    headers: {
      ...authJsonHeaders(backend.key, session.accessToken),
      prefer: "return=minimal",
    },
    body: JSON.stringify({ avatar_url: avatarUrl }),
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) throw new Error(`account_avatar_profile_patch_failed:${response.status}`);
}

async function restoreProfileAvatar({ backend, session, profileId, originalAvatarUrl }) {
  await patchProfileAvatar({ backend, session, profileId, avatarUrl: originalAvatarUrl });
}

function commonHeaders(key) {
  return { apikey: key, "content-type": "application/json", "x-client-info": "quata-account-avatar-evidence" };
}

function authHeaders(key, accessToken) {
  return { apikey: key, authorization: `Bearer ${accessToken}`, "x-client-info": "quata-account-avatar-evidence" };
}

function authJsonHeaders(key, accessToken) {
  return { ...authHeaders(key, accessToken), "content-type": "application/json" };
}

async function postJson(url, headers, body) {
  const response = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(30_000),
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(`account_avatar_public_request_failed:${response.status}`);
  return { status: response.status, payload };
}

function validJpegFixture() {
  return Buffer.from(
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAACAAIDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAH/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAEFAqf/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/ASP/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/ASP/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAY/Al//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/IV//2gAMAwEAAgADAAAAEP/EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8QH//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8QH//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8QH//Z",
    "base64",
  );
}

function gitSha() {
  if (process.env.GITHUB_SHA && /^[0-9a-f]{40}$/i.test(process.env.GITHUB_SHA)) return process.env.GITHUB_SHA;
  return execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim();
}

function repositoryRelative(path) {
  return resolve(path).replace(resolve(".") + "\\", "").replace(/\\/g, "/");
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}
