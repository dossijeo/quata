#!/usr/bin/env node
import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

export const CHECK = "OVR-MEDIA-HISTORICAL-CORS-MIGRATION-001";
export const OPT_IN = "I_ACCEPT_REVERSIBLE_HISTORICAL_MEDIA_MIGRATION";
export const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
export const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
export const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
export const SOURCE_PATTERN = /^https:\/\/egquata\.com\/wp-content\/uploads\/.+\.mp4(?:[?#].*)?$/i;
export const TARGET_PREFIX = "historical-cors-v1";

const TABLES = Object.freeze({
  community_posts: { column: "video_url", bucket: "community-posts" },
  official_posts: { column: "media_url", bucket: "official-media" },
});

export function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

export function safeRunId(value) {
  const normalized = String(value ?? "").trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9-]{7,63}$/.test(normalized)) throw new Error("invalid_run_id");
  return normalized;
}

export function encodeStoragePath(value) {
  return String(value).split("/").map(encodeURIComponent).join("/");
}

export function buildPlan(rows, runId) {
  const safeId = safeRunId(runId);
  const entries = rows.map((row) => {
    const rule = TABLES[row.table];
    if (!rule || !row.id || !SOURCE_PATTERN.test(row.sourceUrl ?? "")) throw new Error("invalid_source_row");
    const sourceHash = sha256(row.sourceUrl);
    const objectPath = `${TARGET_PREFIX}/${safeId}/${sourceHash}.mp4`;
    return {
      table: row.table,
      column: rule.column,
      id: String(row.id),
      sourceUrl: row.sourceUrl,
      sourceHash,
      bucket: rule.bucket,
      objectPath,
      targetKey: `${rule.bucket}/${objectPath}`,
    };
  });
  const targets = new Map();
  for (const entry of entries) {
    const current = targets.get(entry.targetKey) ?? {
      bucket: entry.bucket,
      objectPath: entry.objectPath,
      sourceUrl: entry.sourceUrl,
      sourceHash: entry.sourceHash,
      rows: [],
    };
    if (current.sourceUrl !== entry.sourceUrl) throw new Error("target_collision");
    current.rows.push({ table: entry.table, column: entry.column, id: entry.id });
    targets.set(entry.targetKey, current);
  }
  return { runId: safeId, entries, targets: [...targets.values()] };
}

export function sanitizedPlan(plan) {
  const byTable = Object.fromEntries(Object.keys(TABLES).map((table) => [table, 0]));
  const byBucket = {};
  for (const entry of plan.entries) byTable[entry.table] += 1;
  for (const target of plan.targets) byBucket[target.bucket] = (byBucket[target.bucket] ?? 0) + 1;
  return {
    runId: plan.runId,
    rowCount: plan.entries.length,
    distinctTargetCount: plan.targets.length,
    byTable,
    byBucket,
    mappingSha256: sha256(plan.entries
      .map(({ table, id, sourceHash, targetKey }) => `${table}\u001f${id}\u001f${sourceHash}\u001f${targetKey}`)
      .sort()
      .join("\n")),
  };
}

export function validateBackupAuthorization({ manifest, manifestSha256, restoreProof, now = new Date() }) {
  if (manifest?.format !== "quata-logical-backup-v1" || manifest?.scope !== "Full" || manifest?.encryption !== "AES-256-GCM") {
    throw new Error("full_encrypted_backup_required");
  }
  if (manifest?.containsConnectionData !== false || manifest?.grantsIncluded !== true) throw new Error("backup_manifest_invalid");
  const created = Date.parse(manifest.createdAt);
  if (!Number.isFinite(created) || Math.abs(now.getTime() - created) > 24 * 60 * 60 * 1000) throw new Error("fresh_backup_required");
  if (restoreProof?.status !== "passed" || restoreProof?.backupManifestSha256 !== manifestSha256) {
    throw new Error("matching_restore_drill_required");
  }
  const tables = new Set(restoreProof.tables ?? []);
  if (!tables.has("public.community_posts") || !tables.has("public.official_posts")) {
    throw new Error("media_restore_scope_required");
  }
  return true;
}

export function buildSwitchStatements(plan) {
  return plan.entries.map((entry) => {
    assertEntryShape(entry);
    return {
      text: `update public.${entry.table} set ${entry.column} = $3 where id = $1::uuid and ${entry.column} = $2`,
      values: [entry.id, entry.sourceUrl, entry.publicUrl],
    };
  });
}

export function buildRollbackStatements(journal) {
  return journal.entries.map((entry) => {
    assertEntryShape(entry);
    return {
      text: `update public.${entry.table} set ${entry.column} = $3 where id = $1::uuid and ${entry.column} = $2`,
      values: [entry.id, entry.publicUrl, entry.sourceUrl],
    };
  });
}

function assertEntryShape(entry) {
  const rule = TABLES[entry?.table];
  if (!rule || entry.column !== rule.column || entry.bucket !== rule.bucket) throw new Error("journal_entry_scope_invalid");
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(entry.id ?? "")) throw new Error("journal_entry_id_invalid");
  if (!SOURCE_PATTERN.test(entry.sourceUrl ?? "") || typeof entry.publicUrl !== "string" || !entry.publicUrl) throw new Error("journal_entry_url_invalid");
  return true;
}

export function validateJournal(journal) {
  if (journal?.check !== CHECK || !Array.isArray(journal.entries) || !Array.isArray(journal.targets)) throw new Error("journal_invalid");
  const runId = safeRunId(journal?.plan?.runId);
  for (const entry of journal.entries) assertEntryShape(entry);
  const targetKeys = new Set();
  for (const target of journal.targets) {
    if (!["community-posts", "official-media"].includes(target?.bucket)) throw new Error("journal_target_bucket_invalid");
    const pattern = new RegExp(`^${TARGET_PREFIX}/${runId}/[a-f0-9]{64}\\.mp4$`);
    if (!pattern.test(target?.objectPath ?? "")) throw new Error("journal_target_path_invalid");
    if (!/^[a-f0-9]{64}$/.test(target?.contentSha256 ?? "") || !Number.isInteger(target?.size) || target.size <= 0 || target.size > 100 * 1024 * 1024) {
      throw new Error("journal_target_content_invalid");
    }
    targetKeys.add(`${target.bucket}/${target.objectPath}`);
  }
  if (journal.entries.some((entry) => !targetKeys.has(entry.targetKey))) throw new Error("journal_target_mapping_invalid");
  return journal;
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
}

async function pgConnectionConfig(options) {
  const raw = (await readFile(options.dbUrlFile, "utf8")).trim();
  const ca = await readFile(options.dbTlsCaFile, "utf8");
  const url = new URL(raw);
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true } };
}

async function withPg(options, action) {
  const { Client } = await import("pg");
  const client = new Client(await pgConnectionConfig(options));
  await client.connect();
  try { return await action(client); } finally { await client.end(); }
}

async function readSourceRows(options) {
  return withPg(options, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query(`
        select 'community_posts'::text as table, id::text, video_url as source_url
          from public.community_posts
         where lower(video_url) like '%egquata.com/wp-content/%'
        union all
        select 'official_posts'::text as table, id::text, media_url as source_url
          from public.official_posts
         where lower(media_url) like '%egquata.com/wp-content/%'
        order by 1, 2`);
      await client.query("rollback");
      return rows.map((row) => ({ table: row.table, id: row.id, sourceUrl: row.source_url }));
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

function headers(backend, token) {
  return { apikey: backend.key, authorization: `Bearer ${token}` };
}

async function postJson(url, requestHeaders, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { ...requestHeaders, "content-type": "application/json" },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(30_000),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`http_${response.status}`);
  return payload;
}

function loadCredentials(raw) {
  const parsed = JSON.parse(raw);
  const entries = Object.values(parsed ?? {}).map((entry) => ({
    countryCode: String(entry?.country_code ?? "").trim(),
    phone: String(entry?.phone ?? "").trim(),
    password: String(entry?.password ?? ""),
  })).filter((entry) => entry.countryCode && entry.phone && entry.password);
  if (!entries.length) throw new Error("credentials_invalid");
  return entries;
}

async function loginOfficial(options, backend) {
  const credentials = loadCredentials(await readFile(options.credentialsFile, "utf8"));
  for (const credential of credentials) {
    const payload = await postJson(`${backend.url}/functions/v1/quata-auth-bridge`, { apikey: backend.key }, {
      action: "web_login",
      country_code: credential.countryCode,
      phone_local: credential.phone,
      password: credential.password,
      client_instance_id: `historical-cors-${randomUUID()}`,
    }).catch(() => null);
    const session = payload?.payload?.session ?? payload?.payload ?? payload?.session ?? payload;
    const profileId = payload?.payload?.profile?.id ?? session?.profile_id ?? session?.user?.id ?? session?.user_id;
    const accessToken = session?.access_token ?? session?.accessToken;
    if (!profileId || !accessToken) continue;
    const authorized = await withPg(options, async (client) => {
      const result = await client.query(
        "select count(*)::int = 1 as ok from public.community_profiles where id = $1::uuid and account_status = 'active' and (is_official or is_admin)",
        [profileId],
      );
      return result.rows[0]?.ok === true;
    });
    if (authorized) return { profileId, accessToken };
  }
  throw new Error("official_session_unavailable");
}

async function downloadSource(url) {
  const response = await fetch(url, { headers: { "user-agent": "QuataMigration/1.0" }, signal: AbortSignal.timeout(120_000) });
  if (!response.ok) throw new Error(`source_download_failed:${response.status}`);
  const contentType = (response.headers.get("content-type") ?? "").split(";", 1)[0].toLowerCase();
  if (contentType !== "video/mp4") throw new Error("source_content_type_mismatch");
  const bytes = Buffer.from(await response.arrayBuffer());
  if (!bytes.length || bytes.length > 100 * 1024 * 1024) throw new Error("source_size_out_of_bounds");
  return { bytes, contentSha256: sha256(bytes), size: bytes.length };
}

async function storageUpload({ backend, session, target, bytes }) {
  const response = await fetch(`${backend.url}/storage/v1/object/${target.bucket}/${encodeStoragePath(target.objectPath)}`, {
    method: "POST",
    headers: { ...headers(backend, session.accessToken), "content-type": "video/mp4", "x-upsert": "false" },
    body: bytes,
    signal: AbortSignal.timeout(120_000),
  });
  if (!response.ok) throw new Error(`storage_upload_failed:${response.status}`);
}

async function storageDelete({ backend, session, target }) {
  const response = await fetch(`${backend.url}/storage/v1/object/${target.bucket}`, {
    method: "DELETE",
    headers: { ...headers(backend, session.accessToken), "content-type": "application/json" },
    body: JSON.stringify({ prefixes: [target.objectPath] }),
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) throw new Error(`storage_delete_failed:${response.status}`);
}

async function publicObjectBytes(backend, target) {
  const publicUrl = `${backend.url}/storage/v1/object/public/${target.bucket}/${encodeStoragePath(target.objectPath)}`;
  const response = await fetch(publicUrl, { signal: AbortSignal.timeout(120_000) });
  if (!response.ok) throw new Error(`storage_verify_failed:${response.status}`);
  if (response.headers.get("access-control-allow-origin") !== "*") throw new Error("storage_cors_missing");
  return { publicUrl, bytes: Buffer.from(await response.arrayBuffer()) };
}

async function assertTargetsAbsent(options, plan) {
  return withPg(options, async (client) => {
    const result = await client.query(
      "select bucket_id, name from storage.objects where (bucket_id, name) in (select * from unnest($1::text[], $2::text[]))",
      [plan.targets.map((target) => target.bucket), plan.targets.map((target) => target.objectPath)],
    );
    if (result.rowCount !== 0) throw new Error("target_object_preexists");
  });
}

async function switchDatabase(options, entries, direction) {
  return withPg(options, async (client) => {
    await client.query("begin");
    try {
      for (const entry of entries) {
        assertEntryShape(entry);
        const from = direction === "forward" ? entry.sourceUrl : entry.publicUrl;
        const to = direction === "forward" ? entry.publicUrl : entry.sourceUrl;
        const locked = await client.query(`select ${entry.column} as value from public.${entry.table} where id = $1::uuid for update`, [entry.id]);
        if (locked.rowCount !== 1 || locked.rows[0]?.value !== from) throw new Error(`database_source_drift:${entry.table}`);
        const updated = await client.query(
          `update public.${entry.table} set ${entry.column} = $2 where id = $1::uuid and ${entry.column} = $3`,
          [entry.id, to, from],
        );
        if (updated.rowCount !== 1) throw new Error(`database_switch_failed:${entry.table}`);
      }
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function verifyDatabase(options, entries, expectedField) {
  return withPg(options, async (client) => {
    for (const entry of entries) {
      assertEntryShape(entry);
      const result = await client.query(`select ${entry.column} as value from public.${entry.table} where id = $1::uuid`, [entry.id]);
      if (result.rowCount !== 1 || result.rows[0]?.value !== entry[expectedField]) throw new Error(`database_postflight_failed:${entry.table}`);
    }
  });
}

async function cleanupOwnedTargets({ backend, session, targets }) {
  const failures = [];
  for (const target of [...targets].reverse()) {
    try { await storageDelete({ backend, session, target }); } catch (error) { failures.push(String(error?.message ?? error)); }
  }
  if (failures.length) throw new Error(`storage_cleanup_failed:${failures.length}`);
}

function parseArgs(argv) {
  const options = {
    mode: "audit",
    output: resolve("build-reports/migration/media-historical-cors-run.json"),
    journal: resolve("build-reports/migration/private/media-historical-cors-journal.json"),
    dbUrlFile: DEFAULT_DB_URL_FILE,
    dbTlsCaFile: DEFAULT_DB_TLS_CA_FILE,
    credentialsFile: DEFAULT_CREDENTIALS_FILE,
    runId: `cors-${new Date().toISOString().slice(0, 10).replaceAll("-", "")}-${randomUUID().slice(0, 8)}`,
  };
  const valueOptions = new Set(["--mode", "--out", "--journal", "--db-url-file", "--db-tls-ca-file", "--credentials-file", "--run-id", "--authorization", "--backup-manifest", "--restore-proof"]);
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (!valueOptions.has(key)) throw new Error("invalid_arguments");
    const value = argv[++index];
    if (!value || value.startsWith("--")) throw new Error("invalid_arguments");
    const property = ({
      "--mode": "mode", "--out": "output", "--journal": "journal", "--db-url-file": "dbUrlFile",
      "--db-tls-ca-file": "dbTlsCaFile", "--credentials-file": "credentialsFile", "--run-id": "runId",
      "--authorization": "authorization", "--backup-manifest": "backupManifest", "--restore-proof": "restoreProof",
    })[key];
    options[property] = ["output", "journal", "dbUrlFile", "dbTlsCaFile", "credentialsFile", "backupManifest", "restoreProof"].includes(property) ? resolve(value) : value;
  }
  if (!["audit", "dry-run", "apply", "postflight", "rollback"].includes(options.mode)) throw new Error("invalid_mode");
  options.runId = safeRunId(options.runId);
  return options;
}

async function writeJson(path, value) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
}

async function readAuthorization(options) {
  if (options.authorization !== OPT_IN) throw new Error("mutation_opt_in_required");
  if (!options.backupManifest || !options.restoreProof) throw new Error("backup_and_restore_proof_required");
  const manifestBytes = await readFile(options.backupManifest);
  const manifest = JSON.parse(manifestBytes.toString("utf8"));
  const restoreProof = JSON.parse(await readFile(options.restoreProof, "utf8"));
  validateBackupAuthorization({ manifest, manifestSha256: sha256(manifestBytes), restoreProof });
}

async function run(options) {
  const report = { version: 1, check: CHECK, mode: options.mode, status: "failed", startedAt: new Date().toISOString(), mutationsPerformed: false, steps: [] };
  const backend = await publicConfig();
  let session;
  let journal;
  let switched = false;
  const uploaded = [];
  try {
    if (["postflight", "rollback"].includes(options.mode)) {
      journal = validateJournal(JSON.parse(await readFile(options.journal, "utf8")));
      report.plan = journal.plan;
      if (options.mode === "postflight") {
        await verifyDatabase(options, journal.entries, "publicUrl");
        for (const target of journal.targets) {
          const verified = await publicObjectBytes(backend, target);
          if (sha256(verified.bytes) !== target.contentSha256 || verified.bytes.length !== target.size) throw new Error("postflight_content_mismatch");
        }
        report.steps.push("database_targets_verified", "storage_bytes_and_cors_verified");
      } else {
        await readAuthorization(options);
        session = await loginOfficial(options, backend);
        await switchDatabase(options, journal.entries, "rollback");
        await verifyDatabase(options, journal.entries, "sourceUrl");
        await cleanupOwnedTargets({ backend, session, targets: journal.targets });
        report.mutationsPerformed = true;
        report.steps.push("database_rollback_verified", "owned_storage_cleanup_verified");
      }
    } else {
      const rows = await readSourceRows(options);
      if (!rows.length) throw new Error("historical_media_sources_absent");
      const plan = buildPlan(rows, options.runId);
      report.plan = sanitizedPlan(plan);
      report.steps.push("production_rows_snapshotted_read_only");
      const sourceSummaries = [];
      for (const target of plan.targets) {
        const source = await downloadSource(target.sourceUrl);
        target.contentSha256 = source.contentSha256;
        target.size = source.size;
        sourceSummaries.push({ sourceHash: target.sourceHash, contentSha256: source.contentSha256, size: source.size });
      }
      report.source = { distinct: sourceSummaries.length, totalBytes: sourceSummaries.reduce((sum, item) => sum + item.size, 0), setSha256: sha256(sourceSummaries.map((item) => `${item.sourceHash}:${item.contentSha256}:${item.size}`).sort().join("\n")) };
      report.steps.push("all_sources_downloaded_and_hashed");
      await assertTargetsAbsent(options, plan);
      report.steps.push("target_namespace_verified_empty");
      if (options.mode === "apply") {
        await readAuthorization(options);
        session = await loginOfficial(options, backend);
        for (const target of plan.targets) {
          const source = await downloadSource(target.sourceUrl);
          if (source.contentSha256 !== target.contentSha256 || source.size !== target.size) throw new Error("source_changed_during_apply");
          await storageUpload({ backend, session, target, bytes: source.bytes });
          uploaded.push(target);
          const verified = await publicObjectBytes(backend, target);
          if (sha256(verified.bytes) !== target.contentSha256 || verified.bytes.length !== target.size) throw new Error("uploaded_content_mismatch");
          target.publicUrl = verified.publicUrl;
        }
        for (const entry of plan.entries) entry.publicUrl = plan.targets.find((target) => target.bucket === entry.bucket && target.objectPath === entry.objectPath)?.publicUrl;
        journal = {
          version: 1, check: CHECK, state: "uploads_verified", createdAt: new Date().toISOString(), plan: sanitizedPlan(plan),
          entries: plan.entries, targets: plan.targets.map(({ sourceUrl: _sourceUrl, ...target }) => target),
        };
        await writeJson(options.journal, journal);
        report.steps.push("target_uploads_verified_byte_exact", "private_rollback_journal_written");
        await switchDatabase(options, plan.entries, "forward");
        switched = true;
        await verifyDatabase(options, plan.entries, "publicUrl");
        journal.state = "applied_and_verified";
        journal.appliedAt = new Date().toISOString();
        await writeJson(options.journal, journal);
        report.mutationsPerformed = true;
        report.steps.push("database_urls_switched_transactionally", "database_postflight_verified");
      }
    }
    report.status = "passed";
  } catch (error) {
    report.error = String(error?.message ?? error).replace(/https?:\/\/\S+/g, "<redacted-url>");
    if (options.mode === "apply" && uploaded.length && session) {
      try {
        if (switched && journal?.entries) await switchDatabase(options, journal.entries, "rollback");
        if (!switched || journal?.entries) await cleanupOwnedTargets({ backend, session, targets: uploaded });
        report.failureRollback = "verified";
      } catch (rollbackError) {
        report.failureRollback = `failed:${String(rollbackError?.message ?? rollbackError)}`;
      }
    }
    process.exitCode = 1;
  } finally {
    report.finishedAt = new Date().toISOString();
    await writeJson(options.output, report);
    process.stdout.write(`${JSON.stringify({ check: CHECK, status: report.status, mode: options.mode, output: options.output, steps: report.steps.length })}\n`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  await run(parseArgs(process.argv.slice(2)));
}
