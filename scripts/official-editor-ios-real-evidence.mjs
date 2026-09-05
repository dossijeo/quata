#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import pg from "pg";

const CHECK = "OFFICIAL-EDITOR-IOS-REAL-UI-001";
const OPT_IN = "I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION";
const MEDIA_FIXTURE_OPT_IN = "I_ACCEPT_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const REQUIRED_ENV = [
  "QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN",
  "QUATA_OFFICIAL_E2E_COUNTRY_CODE",
  "QUATA_OFFICIAL_E2E_OFFICIAL_PHONE",
  "QUATA_OFFICIAL_E2E_PASSWORD",
  "QUATA_IOS_SIMULATOR_UDID",
];

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};

class EvidenceComplete extends Error {
  constructor() {
    super("evidence_complete");
  }
}

let marker = `official-ios-ui-${randomUUID()}`;
let created = { ids: [], translationGroupIds: [] };
let cleanup = { state: "not_started" };
let localCredentials;
let remoteCredentials;
let localMediaFixture;
let remoteMediaFixture;
let runtimeConfig;
let permissionProfileRollback;
let officialProfileRollback;

try {
  const config = requireEnvironment();
  runtimeConfig = config;
  const backend = await publicConfig();
  await mkdir(dirname(options.output), { recursive: true });
  await mkdir(options.evidenceDir, { recursive: true });
  if (options.expectIneligible) {
    permissionProfileRollback = await prepareNonOfficialProfile(backend, config);
    report.evidence.permissionProfile = {
      state: "forced_non_official_for_evidence",
      profileId: permissionProfileRollback.profileId,
      previousIsOfficial: permissionProfileRollback.previousIsOfficial,
    };
    report.steps.push("non_official_profile_role_prepared_reversibly");
  } else {
    officialProfileRollback = await prepareOfficialProfile(backend, config);
    report.evidence.permissionProfile = {
      state: "forced_official_for_evidence",
      profileId: officialProfileRollback.profileId,
      previousIsOfficial: officialProfileRollback.previousIsOfficial,
    };
    report.steps.push("official_profile_role_prepared_reversibly");
  }

  localCredentials = join(
    await mkdtemp(join(tmpdir(), "quata-ios-official-editor-credentials-")),
    "credentials.json",
  );
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: config.countryCode,
      phone: e164Phone(config.countryCode, options.expectIneligible ? config.nonOfficialPhone : config.officialPhone),
      password: config.password,
    })}\n`,
    { mode: 0o600 },
  );

  remoteCredentials = (await runCapture("ssh", [
    options.host,
    "mktemp -t quata-ios-official-editor-credentials",
  ])).trim();
  await run("scp", [localCredentials, `${options.host}:${remoteCredentials}`]);
  report.steps.push("ios_real_credentials_copied_to_mac_tempfile_without_logging_contents");

  if (options.media !== "none") {
    localMediaFixture = join(
      await mkdtemp(join(tmpdir(), "quata-ios-official-editor-media-")),
      `${marker}.${options.media === "video" ? "mp4" : "png"}`,
    );
    if (options.media === "video") {
      await copyFile(resolve("play-store/05-assets/quata-demo-video.mp4"), localMediaFixture);
    } else {
      await writeFile(localMediaFixture, pngFixtureBuffer());
    }
    remoteMediaFixture = (await runCapture("ssh", [
      options.host,
      `python3 - <<'PY'\nimport os, tempfile\nfd, path = tempfile.mkstemp(prefix='quata-ios-official-editor-media-', suffix='.${options.media === "video" ? "mp4" : "png"}')\nos.close(fd)\nprint(path)\nPY`,
    ])).trim();
    await run("scp", [localMediaFixture, `${options.host}:${remoteMediaFixture}`]);
    report.steps.push(`ios_${options.media}_fixture_copied_to_mac_tempfile_without_sensitive_contents`);
  }

  const localHead = report.git.head;
  const remoteHead = (await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
git rev-parse HEAD
`)).trim();
  report.mac = { host: options.host, project: options.project, head: remoteHead };
  if (remoteHead !== localHead) throw new Error(`mac_checkout_sha_mismatch:${remoteHead}:${localHead}`);
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
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(config.simulatorUdid)}
export QUATA_IOS_OFFICIAL_EDITOR_UI_LOG_DIR=${shellQuote(options.remoteLogDir)}
export QUATA_IOS_OFFICIAL_EDITOR_UI_RESULT_BUNDLE_DIR=${shellQuote(options.remoteResultBundleDir)}
${options.expectIneligible ? "export QUATA_IOS_OFFICIAL_EDITOR_EXPECT_INELIGIBLE=1" : `export QUATA_IOS_OFFICIAL_EDITOR_REAL_PUBLISH_OPT_IN=${shellQuote(OPT_IN)}
export QUATA_IOS_OFFICIAL_EDITOR_MARKER=${shellQuote(marker)}`}
${remoteMediaFixture ? `export QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_OPT_IN=${shellQuote(MEDIA_FIXTURE_OPT_IN)}
export QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_TYPE=${shellQuote(options.media)}
export QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_PATH=${shellQuote(remoteMediaFixture)}` : ""}
bash scripts/run-ios-authenticated-official-editor-ui-test.sh
`);
  report.steps.push(options.expectIneligible
    ? "ios_xctest_real_official_editor_ineligible_permission_executed"
    : "ios_xctest_real_official_editor_publish_executed");

  if (options.expectIneligible) {
    report.evidence.permission = {
      state: "verified_ineligible_session_cannot_open_editor",
      mutation: "not_requested",
    };
    report.postCleanupReadback = await assertNoMarkerRows(config, marker, []);
    await copyRemoteEvidence(options).catch((error) => {
      report.evidence.copyWarning = safeFailure(error);
    });
    report.status = "passed";
    throw new EvidenceComplete();
  }

  created = await readCreatedRows(config, marker);
  if (created.ids.length < 1) throw new Error("created_post_readback_missing");
  const storagePaths = storagePathsFromMediaUrls(created.mediaUrls ?? []);
  const wordpressVideoUrls = wordpressVideoUrlsFromMediaUrls(created.mediaUrls ?? []);
  if (options.media === "image" && !storagePaths.length) throw new Error("created_media_readback_missing");
  if (options.media === "video" && !wordpressVideoUrls.length) throw new Error("created_video_readback_missing");
  report.evidence.created = {
    state: "verified_in_database",
    postIds: created.ids,
    translationGroupIds: created.translationGroupIds,
    media: options.media,
    storagePaths,
    wordpressVideoUrls,
  };
  const loginSession = storagePaths.length ? await login(backend, config, `official-editor-ios-real-${randomUUID()}`) : null;
  report.evidence.storageCleanup = loginSession
    ? await cleanupStorageObjects(backend, loginSession, storagePaths)
    : { state: "not_needed", deletedPaths: [] };
  report.evidence.storagePostCleanup = await assertStorageObjectsAbsent(config, storagePaths);
  report.evidence.wordpressVideoCleanup = await cleanupWordpressVideoUrls(wordpressVideoUrls);
  report.evidence.wordpressVideoPostCleanup = await assertWordpressVideoUrlsAbsent(wordpressVideoUrls);
  cleanup = await cleanupPosts(config, created.ids, created.translationGroupIds, marker);
  report.cleanup = cleanup;
  report.postCleanupReadback = await assertNoMarkerRows(config, marker, created.translationGroupIds);
  report.steps.push("created_ios_post_cleaned_by_exact_ids_and_marker_absence_verified");

  await copyRemoteEvidence(options).catch((error) => {
    report.evidence.copyWarning = safeFailure(error);
  });
  report.status = "passed";
} catch (error) {
  if (error instanceof EvidenceComplete) {
    // Report has already been populated and marked as passed by the ineligible-permission lane.
  } else {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  if (marker && cleanup.state === "not_started") {
    try {
      const config = requireEnvironment();
      const backend = await publicConfig();
      const found = created.ids.length ? created : await readCreatedRows(config, marker);
      const storagePaths = storagePathsFromMediaUrls(found.mediaUrls ?? []);
      const wordpressVideoUrls = wordpressVideoUrlsFromMediaUrls(found.mediaUrls ?? []);
      if (storagePaths.length) {
        try {
          const loginSession = await login(backend, config, `official-editor-ios-real-cleanup-${randomUUID()}`);
          report.evidence.storageCleanup = await cleanupStorageObjects(backend, loginSession, storagePaths);
          report.evidence.storagePostCleanup = await assertStorageObjectsAbsent(config, storagePaths);
        } catch (storageError) {
          report.evidence.storageCleanup = {
            state: "rollback_pending",
            storagePaths,
            error: safeFailure(storageError),
          };
        }
      }
      if (wordpressVideoUrls.length) {
        try {
          report.evidence.wordpressVideoCleanup = await cleanupWordpressVideoUrls(wordpressVideoUrls);
          report.evidence.wordpressVideoPostCleanup = await assertWordpressVideoUrlsAbsent(wordpressVideoUrls);
        } catch (wordpressError) {
          report.evidence.wordpressVideoCleanup = {
            state: "rollback_pending",
            wordpressVideoUrls,
            error: safeFailure(wordpressError),
          };
        }
      }
      cleanup = await cleanupPosts(config, found.ids, found.translationGroupIds, marker);
      report.cleanup = cleanup;
      report.postCleanupReadback = await assertNoMarkerRows(config, marker, found.translationGroupIds);
    } catch {
      report.cleanup = {
        state: "rollback_pending",
        action: "hard_delete_official_posts_by_recorded_ids_or_unique_marker",
        postIds: created.ids,
        translationGroupIds: created.translationGroupIds,
      };
    }
  }
  }
} finally {
  const roleRollback = permissionProfileRollback ?? officialProfileRollback;
  if (roleRollback && runtimeConfig) {
    try {
      report.evidence.permissionProfileRestore = await restoreProfileOfficialRole(runtimeConfig, roleRollback);
    } catch (restoreError) {
      report.evidence.permissionProfileRestore = {
        state: "rollback_pending",
        profileId: roleRollback.profileId,
        previousIsOfficial: roleRollback.previousIsOfficial,
        error: safeFailure(restoreError),
      };
      if (report.status === "passed") {
        report.status = "failed";
        report.error = "permission_profile_restore_failed";
      }
    }
  }
  if (remoteCredentials) {
    await run("ssh", [options.host, "rm", "-f", remoteCredentials]).catch(() => {});
  }
  if (remoteMediaFixture) {
    await run("ssh", [options.host, "rm", "-f", remoteMediaFixture]).catch(() => {});
  }
  if (localCredentials) {
    await rm(dirname(localCredentials), { recursive: true, force: true }).catch(() => {});
  }
  if (localMediaFixture) {
    await rm(dirname(localMediaFixture), { recursive: true, force: true }).catch(() => {});
  }
  report.finishedAt = new Date().toISOString();
  report.marker = marker;
  report.mac ??= { host: options.host, project: options.project };
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`Official editor iOS real evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Official editor iOS real evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Official editor iOS real evidence passed.");
}

function parseArgs(args) {
  const values = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    remoteLogDir: process.env.QUATA_IOS_OFFICIAL_EDITOR_UI_LOG_DIR?.trim() || "build/reports/ios/authenticated-official-editor-real-ui",
    remoteResultBundleDir: process.env.QUATA_IOS_OFFICIAL_EDITOR_UI_RESULT_BUNDLE_DIR?.trim() || "build/reports/ios/authenticated-official-editor-real-ui/xcresults",
    output: "build-reports/ios/official-editor-real-evidence.json",
    evidenceDir: "build-reports/ios/official-editor-real-evidence",
    buildFirst: process.env.QUATA_IOS_BUILD_FIRST === "1",
    media: process.env.QUATA_IOS_OFFICIAL_EDITOR_MEDIA?.trim() || "none",
    expectIneligible: process.env.QUATA_IOS_OFFICIAL_EDITOR_EXPECT_INELIGIBLE === "1",
  };
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    const next = () => {
      index += 1;
      if (index >= args.length) throw new Error(`missing_value:${arg}`);
      return args[index];
    };
    if (arg === "--host") values.host = next();
    else if (arg === "--project") values.project = next();
    else if (arg === "--derived-data") values.derivedDataPath = next();
    else if (arg === "--remote-log-dir") values.remoteLogDir = next();
    else if (arg === "--out") values.output = next();
    else if (arg === "--evidence-dir") values.evidenceDir = next();
    else if (arg === "--build-first") values.buildFirst = true;
    else if (arg === "--expect-ineligible") values.expectIneligible = true;
    else if (arg === "--media") values.media = next();
    else throw new Error(`unknown_argument:${arg}`);
  }
  if (!["none", "image", "video"].includes(values.media)) throw new Error(`unsupported_media:${values.media}`);
  if (values.expectIneligible && values.media !== "none") throw new Error("ineligible_media_not_supported");
  values.output = resolve(values.output);
  values.evidenceDir = resolve(values.evidenceDir);
  return values;
}

function requireEnvironment() {
  const required = options.expectIneligible
    ? REQUIRED_ENV.filter((name) => name !== "QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN")
    : [...REQUIRED_ENV];
  if (options.expectIneligible) required.push("QUATA_OFFICIAL_E2E_NON_OFFICIAL_PHONE");
  const missing = required.filter((name) => !process.env[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  if (!options.expectIneligible && process.env.QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN !== OPT_IN) {
    throw new Error("mutation_opt_in_required");
  }
  return {
    countryCode: process.env.QUATA_OFFICIAL_E2E_COUNTRY_CODE.trim(),
    officialPhone: process.env.QUATA_OFFICIAL_E2E_OFFICIAL_PHONE.trim(),
    nonOfficialPhone: process.env.QUATA_OFFICIAL_E2E_NON_OFFICIAL_PHONE?.trim() ?? "",
    password: process.env.QUATA_OFFICIAL_E2E_PASSWORD,
    simulatorUdid: process.env.QUATA_IOS_SIMULATOR_UDID.trim(),
    dbUrlFile: process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbTlsCaFile: process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE,
  };
}

function e164Phone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  if (!country || !digits) throw new Error("ios_e164_credentials_required");
  const local = digits.startsWith(country) ? digits.slice(country.length) : digits;
  if (!local) throw new Error("ios_e164_local_phone_required");
  return `+${country}${local}`;
}

async function gitMetadata() {
  const head = (await runCapture("git", ["rev-parse", "HEAD"])).trim();
  const status = await runCapture("git", ["status", "--porcelain"]);
  return { head, workingTreeDirty: status.trim().length > 0 };
}

async function pgConnectionConfig(config) {
  const raw = (await readFile(config.dbUrlFile, "utf8")).trim();
  const ca = await readFile(config.dbTlsCaFile, "utf8");
  const url = new URL(raw);
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true, servername: url.hostname } };
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(url)) throw new Error("invalid_public_supabase_url");
  return { url, key };
}

async function postJson(url, headers, body) {
  const response = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(30_000),
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`invalid_json_response:${response.status}`);
  }
  if (!response.ok) throw new Error(`http_${response.status}:${safeFailure(JSON.stringify(json))}`);
  return json;
}

async function login(backend, config, clientInstanceId) {
  const response = await postJson(`${backend.url}/functions/v1/quata-auth-bridge`, {
    apikey: backend.key,
    "content-type": "application/json",
    "x-client-info": "quata-official-editor-ios-real-evidence",
  }, {
    action: "web_login",
    country_code: config.countryCode,
    phone_local: config.officialPhone,
    password: config.password,
    client_instance_id: clientInstanceId,
  });
  const session = response.payload?.session ?? response.payload ?? response.session;
  const profile = response.payload?.profile ?? response.profile;
  if (typeof session?.access_token !== "string") throw new Error("invalid_auth_response");
  return {
    accessToken: session.access_token,
    profileId: typeof profile?.id === "string" ? profile.id : null,
  };
}

async function withPg(config, action) {
  const client = new pg.Client(await pgConnectionConfig(config));
  await client.connect();
  try {
    return await action(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function prepareOfficialProfile(backend, config) {
  const session = await login(backend, config, `official-editor-ios-role-${randomUUID()}`);
  if (!session.profileId) throw new Error("official_profile_id_missing");
  return withPg(config, async (client) => {
    await client.query("begin");
    try {
      const current = await client.query({
        text: "select is_official from public.community_profiles where id = $1::uuid for update",
        values: [session.profileId],
      });
      if (current.rowCount !== 1) throw new Error("official_profile_missing");
      const previousIsOfficial = current.rows[0]?.is_official === true;
      await client.query({
        text: "update public.community_profiles set is_official = true where id = $1::uuid",
        values: [session.profileId],
      });
      await client.query("commit");
      return { profileId: session.profileId, previousIsOfficial };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function prepareNonOfficialProfile(backend, config) {
  const session = await login(backend, {
    ...config,
    officialPhone: config.nonOfficialPhone,
  }, `official-editor-ios-permission-${randomUUID()}`);
  if (!session.profileId) throw new Error("permission_profile_id_missing");
  return withPg(config, async (client) => {
    await client.query("begin");
    try {
      const current = await client.query({
        text: "select is_official from public.community_profiles where id = $1::uuid for update",
        values: [session.profileId],
      });
      if (current.rowCount !== 1) throw new Error("permission_profile_missing");
      const previousIsOfficial = current.rows[0]?.is_official === true;
      await client.query({
        text: "update public.community_profiles set is_official = false where id = $1::uuid",
        values: [session.profileId],
      });
      await client.query("commit");
      return { profileId: session.profileId, previousIsOfficial };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function restoreProfileOfficialRole(config, rollback) {
  return withPg(config, async (client) => {
    await client.query("begin");
    try {
      await client.query({
        text: "update public.community_profiles set is_official = $2 where id = $1::uuid",
        values: [rollback.profileId, rollback.previousIsOfficial],
      });
      const restored = await client.query({
        text: "select is_official from public.community_profiles where id = $1::uuid",
        values: [rollback.profileId],
      });
      if (restored.rows[0]?.is_official !== rollback.previousIsOfficial) {
        throw new Error("permission_profile_restore_verification_failed");
      }
      await client.query("commit");
      return {
        state: "restored",
        profileId: rollback.profileId,
        restoredIsOfficial: rollback.previousIsOfficial,
      };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function readCreatedRows(config, uniqueMarker) {
  return withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query({
        text: `select id, translation_group_id, media_url
               from public.official_posts
               where title like $1 or content_html like $1`,
        values: [`%${uniqueMarker}%`],
      });
      await client.query("rollback");
      return {
        ids: rows.map((row) => row.id).filter(Boolean),
        translationGroupIds: [...new Set(rows.map((row) => row.translation_group_id).filter(Boolean))],
        mediaUrls: [...new Set(rows.map((row) => row.media_url).filter(Boolean))],
      };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function cleanupStorageObjects(backend, session, storagePaths) {
  if (!storagePaths.length) return { state: "not_needed", deletedPaths: [] };
  const response = await fetch(`${backend.url}/storage/v1/object/community-posts`, {
    method: "DELETE",
    headers: {
      apikey: backend.key,
      authorization: `Bearer ${session.accessToken}`,
      "content-type": "application/json",
      "x-client-info": "quata-official-editor-ios-real-evidence",
    },
    body: JSON.stringify({ prefixes: storagePaths }),
    signal: AbortSignal.timeout(20_000),
  }).catch(() => null);
  if (!response) throw new Error("storage_cleanup_failed:network");
  if (!response.ok) throw new Error(`storage_cleanup_failed:http_${response.status}`);
  return { state: "deleted", deletedPaths: storagePaths };
}

async function cleanupWordpressVideoUrls(videoUrls) {
  if (!videoUrls.length) return { state: "not_needed", deletedUrls: 0 };
  const endpoint = wordpressAdminAjaxUrl(videoUrls[0]);
  for (const url of videoUrls) {
    if (wordpressAdminAjaxUrl(url) !== endpoint) throw new Error("wordpress_cleanup_failed:mixed_origin");
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "content-type": "application/x-www-form-urlencoded",
        "x-client-info": "quata-official-editor-ios-real-evidence",
      },
      body: new URLSearchParams({ action: "quqos_delete_post_video", url }).toString(),
      signal: AbortSignal.timeout(20_000),
    }).catch(() => null);
    if (!response) throw new Error("wordpress_cleanup_failed:network");
    const text = await response.text();
    if (!response.ok) throw new Error(`wordpress_cleanup_failed:http_${response.status}`);
    if (/"success"\s*:\s*false/i.test(text)) throw new Error("wordpress_cleanup_failed:success_false");
  }
  return { state: "delete_requested", deletedUrls: videoUrls.length };
}

async function assertWordpressVideoUrlsAbsent(videoUrls) {
  if (!videoUrls.length) return { state: "not_needed" };
  const checked = [];
  for (const url of videoUrls) {
    const probe = new URL(url);
    probe.searchParams.set("quata_cleanup_probe", randomUUID());
    const response = await fetch(probe, {
      method: "GET",
      headers: {
        range: "bytes=0-0",
        "cache-control": "no-store",
        "x-client-info": "quata-official-editor-ios-real-evidence",
      },
      signal: AbortSignal.timeout(20_000),
    }).catch(() => null);
    if (!response) throw new Error("wordpress_post_cleanup_verification_failed:network");
    checked.push({ urlKind: wordpressVideoUrlKind(url), status: response.status });
    if (![404, 410].includes(response.status)) {
      throw new Error(`wordpress_post_cleanup_verification_failed:http_${response.status}`);
    }
  }
  return { state: "verified_absent", checked };
}

async function assertStorageObjectsAbsent(config, storagePaths) {
  if (!storagePaths.length) return { state: "not_needed" };
  return withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query({
        text: `select count(*)::int as count
               from storage.objects
               where bucket_id = 'community-posts'
                 and name = any($1::text[])`,
        values: [storagePaths],
      });
      await client.query("rollback");
      if (rows[0]?.count !== 0) throw new Error("storage_post_cleanup_verification_failed:object_metadata_remains");
      return { state: "verified_absent", checkedPaths: storagePaths };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

function storagePathsFromMediaUrls(mediaUrls) {
  return [...new Set(mediaUrls.map(storagePathFromMediaUrl).filter(Boolean))];
}

function wordpressVideoUrlsFromMediaUrls(mediaUrls) {
  return [...new Set(mediaUrls.filter((url) => {
    if (storagePathFromMediaUrl(url)) return false;
    try {
      const parsed = new URL(url);
      return /^https?:$/i.test(parsed.protocol)
        && parsed.hostname.toLowerCase().endsWith("egquata.com")
        && parsed.pathname.includes("/wp-content/uploads/")
        && parsed.pathname.toLowerCase().endsWith(".mp4");
    } catch {
      return false;
    }
  }))];
}

function wordpressAdminAjaxUrl(value) {
  const parsed = new URL(value);
  return `${parsed.origin}/wp-admin/admin-ajax.php`;
}

function wordpressVideoUrlKind(value) {
  const parsed = new URL(value);
  return `${parsed.hostname}/wp-content/uploads/${parsed.pathname.split("/").pop()}`;
}

function storagePathFromMediaUrl(value) {
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    return null;
  }
  const markerPath = "/storage/v1/object/public/community-posts/";
  const index = parsed.pathname.indexOf(markerPath);
  if (index < 0 || parsed.search || parsed.hash) return null;
  return parsed.pathname
    .slice(index + markerPath.length)
    .split("/")
    .map((part) => decodeURIComponent(part))
    .join("/")
    .trim()
    || null;
}

function pngFixtureBuffer() {
  return Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFUlEQVR42mP8z8Dwn4GBgYGJAQoAHxcCAns1m2AAAAAASUVORK5CYII=",
    "base64",
  );
}

async function cleanupPosts(config, ids, groupIds, uniqueMarker) {
  return withPg(config, async (client) => {
    await client.query("begin");
    try {
      const resolved = await client.query({
        text: `select id, translation_group_id
               from public.official_posts
               where id = any($1::uuid[])
                  or translation_group_id = any($2::uuid[])
                  or title like $3
                  or content_html like $3`,
        values: [ids, groupIds, `%${uniqueMarker}%`],
      });
      const resolvedIds = [...new Set(resolved.rows.map((row) => row.id).filter(Boolean))];
      const resolvedGroupIds = [...new Set([...groupIds, ...resolved.rows.map((row) => row.translation_group_id).filter(Boolean)])];
      if (!resolvedIds.length && !resolvedGroupIds.length) {
        await client.query("commit");
        return { state: "not_needed", deletedRows: 0, remainingRows: 0 };
      }
      await client.query({ text: "delete from public.official_post_likes where official_post_id = any($1::uuid[])", values: [resolvedIds] });
      await client.query({ text: "delete from public.official_post_comments where official_post_id = any($1::uuid[])", values: [resolvedIds] });
      const deleted = await client.query({
        text: `delete from public.official_posts
               where id = any($1::uuid[])
                  or translation_group_id = any($2::uuid[])
                  or title like $3
                  or content_html like $3`,
        values: [resolvedIds, resolvedGroupIds, `%${uniqueMarker}%`],
      });
      const remaining = await client.query({
        text: `select count(*)::int as count
               from public.official_posts
               where id = any($1::uuid[])
                  or translation_group_id = any($2::uuid[])
                  or title like $3
                  or content_html like $3`,
        values: [resolvedIds, resolvedGroupIds, `%${uniqueMarker}%`],
      });
      if (remaining.rows[0]?.count !== 0) throw new Error("cleanup_verification_failed");
      await client.query("commit");
      return {
        state: "hard_deleted_verified",
        deletedRows: deleted.rowCount,
        remainingRows: 0,
        resolvedPostIds: resolvedIds,
        resolvedTranslationGroupIds: resolvedGroupIds,
      };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function assertNoMarkerRows(config, uniqueMarker, groupIds) {
  return withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query({
        text: `select count(*)::int as count
               from public.official_posts
               where title like $1
                  or content_html like $1
                  or translation_group_id = any($2::uuid[])`,
        values: [`%${uniqueMarker}%`, groupIds],
      });
      await client.query("rollback");
      return { state: "verified_absent", remainingRows: rows[0]?.count ?? 0 };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function copyRemoteEvidence(values) {
  const target = join(values.evidenceDir, "mac-ui-report");
  await rm(target, { recursive: true, force: true });
  await mkdir(dirname(target), { recursive: true });
  await run("scp", ["-r", `${values.host}:${values.project}/${values.remoteLogDir}`, target]);
  report.evidence.uiReportDirectory = target;
}

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}

async function runSshScript(host, script) {
  return run("ssh", [host, "bash", "-s"], { input: script });
}

async function run(command, args, options = {}) {
  const output = await runCapture(command, args, options);
  if (output.trim()) report.lastCommandOutputTail = redactedTail(output);
  return output;
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
      if (code === 0) {
        resolvePromise(combined);
      } else {
        rejectPromise(new Error(`command_failed:${command}:${code}:${redactedTail(combined)}`));
      }
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
