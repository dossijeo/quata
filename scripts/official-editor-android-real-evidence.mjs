#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import pg from "pg";

const CHECK = "OFFICIAL-EDITOR-ANDROID-REAL-UI-001";
const PERMISSION_CHECK = "OFFICIAL-EDITOR-ANDROID-PERMISSIONS-001";
const OPT_IN = "I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const REQUIRED_ENV = [
  "QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN",
  "QUATA_OFFICIAL_E2E_COUNTRY_CODE",
  "QUATA_OFFICIAL_E2E_OFFICIAL_PHONE",
  "QUATA_OFFICIAL_E2E_PASSWORD",
];
const deviceCredentialsPath = "app-internal:official-editor-real-credentials.json";
const deviceTempCredentialsPath = "/data/local/tmp/official-editor-real-credentials.json";
const deviceEvidencePath = "files/official-editor-real-evidence";
const evidenceFiles = [
  "android-official-editor-opened.png",
  "android-official-editor-validation.png",
  "android-official-editor-before-long-save.png",
  "android-official-editor-after-long-save.png",
  "android-official-editor-preview.png",
  "android-official-editor-after-publish-tap.png",
  "android-official-editor-after-translation-skip.png",
  "android-official-editor-published.png",
  "android-official-editor-real-evidence.json",
];
const permissionEvidenceFiles = [
  "android-official-editor-ineligible-blocked.png",
  "android-official-editor-permissions-evidence.json",
];

const options = parseArgs(process.argv.slice(2));

const report = {
  check: options.expectIneligible ? PERMISSION_CHECK : CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};

const adb = resolveAdbCommand();
let marker = `official-editor-android-${randomUUID()}`;
let created = { ids: [], groupIds: [] };
let localCredentials;
let runtimeConfig;
let permissionProfileRollback;

try {
  const config = requireEnvironment();
  runtimeConfig = config;
  if (options.expectIneligible) {
    permissionProfileRollback = await prepareNonOfficialProfile(config);
    report.evidence.permissionProfile = {
      state: "forced_non_official_for_evidence",
      profileId: permissionProfileRollback.profileId,
      previousIsOfficial: permissionProfileRollback.previousIsOfficial,
    };
    report.steps.push("non_official_profile_role_prepared_reversibly");
  }
  localCredentials = join("build-reports", "android", `official-editor-credentials-${randomUUID()}.json`);
  await mkdir(dirname(localCredentials), { recursive: true });
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: config.countryCode,
      phone: options.expectIneligible ? config.nonOfficialPhone : config.officialPhone,
      password: config.password,
    })}\n`,
    { mode: 0o600 },
  );

  const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"], gradleEnvironment());
  report.steps.push("android_debug_and_test_apks_built");

  await run(adb, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
  await run(adb, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  await run(adb, ["push", localCredentials, deviceTempCredentialsPath]);
  await run(adb, ["shell", "chmod", "644", deviceTempCredentialsPath]);
  await run(adb, ["shell", "run-as", "com.quata", "cp", deviceTempCredentialsPath, `files/${deviceCredentialsPath.replace("app-internal:", "")}`]);
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]);
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);

  const instrumentationClass = options.expectIneligible
    ? "com.quata.feature.official.presentation.OfficialEditorPermissionInstrumentedTest"
    : "com.quata.feature.official.presentation.OfficialEditorRealInstrumentedTest";
  const instrumentationArgs = [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", instrumentationClass,
    "-e", "quataOfficialEditorCredentialsFile", deviceCredentialsPath,
  ];
  if (options.expectIneligible) {
    instrumentationArgs.push("-e", "quataOfficialEditorExpectIneligible", "1");
  } else {
    instrumentationArgs.push("-e", "quataOfficialEditorMarker", marker);
  }
  instrumentationArgs.push("com.quata.test/androidx.test.runner.AndroidJUnitRunner");
  const instrumentationOutput = await runCapture(adb, instrumentationArgs);
  report.instrumentationTail = redactedTail(instrumentationOutput);
  if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) throw new Error("android_instrumentation_not_ok");
  if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentationOutput)) {
    throw new Error("android_instrumentation_semantic_failure");
  }
  report.steps.push(options.expectIneligible
    ? "android_real_official_editor_ineligible_permission_passed"
    : "android_real_official_editor_flow_passed");

  const evidenceDir = join("build-reports", "android", options.expectIneligible ? "official-editor-permissions-evidence" : "official-editor-real-evidence");
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  for (const file of options.expectIneligible ? permissionEvidenceFiles : evidenceFiles) {
    await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file));
  }
  report.evidence.directory = resolve(evidenceDir);

  if (options.expectIneligible) {
    report.evidence.permission = {
      state: "verified_ineligible_session_cannot_open_editor",
      mutation: "not_requested",
    };
    report.postCleanupReadback = await assertNoMarkerRows(config, marker, []);
    report.status = "passed";
    throw new EvidenceComplete();
  }

  created = await readCreatedRows(config, marker);
  if (created.ids.length < 1) throw new Error("created_post_readback_missing");
  report.evidence.created = { state: "verified_in_database", postIds: created.ids, translationGroupIds: created.groupIds };
  report.cleanup = await cleanupPosts(config, created.ids, created.groupIds, marker);
  report.postCleanupReadback = await assertNoMarkerRows(config, marker, created.groupIds);
  report.steps.push("created_post_cleaned_by_exact_ids_and_marker_absence_verified");
  report.status = "passed";
} catch (error) {
  if (error instanceof EvidenceComplete) {
    // Report has already been populated and marked as passed by the ineligible-permission lane.
  } else {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  await copyEvidenceIfPresent().catch(() => {});
  if (marker && report.cleanup.state === "not_started") {
    try {
      const config = requireEnvironment();
      const found = created.ids.length ? created : await readCreatedRows(config, marker);
      report.cleanup = await cleanupPosts(config, found.ids, found.groupIds, marker);
      report.postCleanupReadback = await assertNoMarkerRows(config, marker, found.groupIds);
    } catch {
      report.cleanup = {
        state: "rollback_pending",
        action: "hard_delete_official_posts_by_recorded_ids_or_unique_marker",
        postIds: created.ids,
        translationGroupIds: created.groupIds,
      };
    }
  }
  }
} finally {
  if (permissionProfileRollback && runtimeConfig) {
    try {
      report.evidence.permissionProfileRestore = await restoreProfileOfficialRole(runtimeConfig, permissionProfileRollback);
    } catch (restoreError) {
      report.evidence.permissionProfileRestore = {
        state: "rollback_pending",
        profileId: permissionProfileRollback.profileId,
        previousIsOfficial: permissionProfileRollback.previousIsOfficial,
        error: safeFailure(restoreError),
      };
      if (report.status === "passed") {
        report.status = "failed";
        report.error = "permission_profile_restore_failed";
      }
    }
  }
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-f", `files/${deviceCredentialsPath.replace("app-internal:", "")}`]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]).catch(() => {});
  await rm(localCredentials ?? "", { force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  report.marker = marker;
  const output = join("build-reports", "android", options.expectIneligible ? "official-editor-permissions-evidence.json" : "official-editor-real-evidence.json");
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Official editor Android real evidence written: ${output}`);
}

if (report.status !== "passed") {
  console.error(`Official editor Android real evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Official editor Android real evidence passed.");
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
    dbUrlFile: process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbTlsCaFile: process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE,
  };
}

function parseArgs(args) {
  const parsed = {
    expectIneligible: process.env.QUATA_OFFICIAL_EDITOR_EXPECT_INELIGIBLE === "1",
  };
  for (const arg of args) {
    if (arg === "--expect-ineligible") parsed.expectIneligible = true;
    else throw new Error(`unknown_argument:${arg}`);
  }
  return parsed;
}

function gradleEnvironment() {
  return {
    env: {
      ...process.env,
      JAVA_HOME: process.env.JAVA_HOME || "C:\\Program Files\\Android\\Android Studio\\jbr",
      ANDROID_HOME: process.env.ANDROID_HOME || `${process.env.LOCALAPPDATA}\\Android\\Sdk`,
      ANDROID_SDK_ROOT: process.env.ANDROID_SDK_ROOT || `${process.env.LOCALAPPDATA}\\Android\\Sdk`,
    },
  };
}

function resolveAdbCommand() {
  const explicit = process.env.ADB?.trim();
  if (explicit) return explicit;
  const sdkRoot = process.env.ANDROID_HOME?.trim()
    || process.env.ANDROID_SDK_ROOT?.trim()
    || (process.env.LOCALAPPDATA ? `${process.env.LOCALAPPDATA}\\Android\\Sdk` : "");
  const executable = process.platform === "win32" ? "adb.exe" : "adb";
  if (sdkRoot) {
    const candidate = join(sdkRoot, "platform-tools", executable);
    if (existsSync(candidate)) return candidate;
  }
  return "adb";
}

async function gitMetadata() {
  const head = (await runSilent("git", ["rev-parse", "HEAD"])).trim();
  const status = await runSilent("git", ["status", "--porcelain"]);
  return { head, workingTreeDirty: status.trim().length > 0 };
}

async function pgConnectionConfig(config) {
  const raw = (await readFile(config.dbUrlFile, "utf8")).trim();
  const ca = await readFile(config.dbTlsCaFile, "utf8");
  const url = new URL(raw);
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true, servername: url.hostname } };
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

async function prepareNonOfficialProfile(config) {
  return withPg(config, async (client) => {
    await client.query("begin");
    try {
      const profile = await client.query({
        text: `select cp.id, cp.is_official
               from public.community_profiles cp
               join auth.users au on au.id = cp.id
               where au.phone = $1
               limit 1
               for update of cp`,
        values: [`+${config.countryCode}${config.nonOfficialPhone}`],
      });
      if (profile.rowCount !== 1) throw new Error("permission_profile_missing");
      const profileId = profile.rows[0].id;
      const previousIsOfficial = profile.rows[0]?.is_official === true;
      await client.query({
        text: "update public.community_profiles set is_official = false where id = $1::uuid",
        values: [profileId],
      });
      await client.query("commit");
      return { profileId, previousIsOfficial };
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
        text: `select id, translation_group_id
               from public.official_posts
               where title like $1 or content_html like $1`,
        values: [`%${uniqueMarker}%`],
      });
      await client.query("rollback");
      return {
        ids: rows.map((row) => row.id).filter(Boolean),
        groupIds: [...new Set(rows.map((row) => row.translation_group_id).filter(Boolean))],
      };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function cleanupPosts(config, ids, groupIds, uniqueMarker) {
  if (!ids.length && !groupIds.length) return { state: "not_needed", deletedRows: 0, remainingRows: 0 };
  return withPg(config, async (client) => {
    await client.query("begin");
    try {
      await client.query({ text: "delete from public.official_post_likes where official_post_id = any($1::uuid[])", values: [ids] });
      await client.query({ text: "delete from public.official_post_comments where official_post_id = any($1::uuid[])", values: [ids] });
      const deleted = await client.query({
        text: `delete from public.official_posts
               where id = any($1::uuid[])
                  or translation_group_id = any($2::uuid[])
                  or title like $3
                  or content_html like $3`,
        values: [ids, groupIds, `%${uniqueMarker}%`],
      });
      const remaining = await client.query({
        text: `select count(*)::int as count
               from public.official_posts
               where id = any($1::uuid[])
                  or translation_group_id = any($2::uuid[])
                  or title like $3
                  or content_html like $3`,
        values: [ids, groupIds, `%${uniqueMarker}%`],
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

async function assertNoMarkerRows(config, uniqueMarker, groupIds) {
  return withPg(config, async (client) => {
    await client.query("begin read only");
    try {
      const { rows } = await client.query({
        text: `select count(*)::int as count
               from public.official_posts
               where translation_group_id = any($1::uuid[])
                  or title like $2
                  or content_html like $2`,
        values: [groupIds, `%${uniqueMarker}%`],
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

async function adbRunAsCat(remotePath, localPath) {
  const chunks = [];
  await new Promise((resolve, reject) => {
    const child = spawn(adb, ["exec-out", "run-as", "com.quata", "cat", remotePath], { stdio: ["ignore", "pipe", "pipe"], shell: false });
    let stderr = "";
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolve() : reject(new Error(`adb_exec_out_failed:${code}:${stderr.trim()}`)));
  });
  await writeFile(localPath, Buffer.concat(chunks));
}

async function copyEvidenceIfPresent() {
  const evidenceDir = join("build-reports", "android", "official-editor-real-evidence");
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  let copied = 0;
  for (const file of [...evidenceFiles, ...permissionEvidenceFiles]) {
    try {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file));
      copied += 1;
    } catch {
      // Evidence is best-effort on failed UI runs; cleanup verification remains authoritative.
    }
  }
  if (copied > 0) report.evidence.directory = resolve(evidenceDir);
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

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  return [
    "missing_environment",
    "mutation_opt_in_required",
    "android_instrumentation_not_ok",
    "android_instrumentation_semantic_failure",
    "created_post_readback_missing",
    "cleanup_verification_failed",
    "marker_cleanup_verification_failed",
    "command_failed",
    "adb_exec_out_failed",
    "permission_profile_missing",
    "permission_profile_restore_failed",
    "permission_profile_restore_verification_failed",
  ].find((prefix) => message.startsWith(prefix)) ?? "unexpected_official_editor_android_real_failure";
}

function redactedTail(value) {
  let output = String(value ?? "");
  for (const secret of [process.env.QUATA_OFFICIAL_E2E_PASSWORD, process.env.QUATA_OFFICIAL_E2E_OFFICIAL_PHONE]) {
    if (secret) output = output.replaceAll(secret, "[redacted]");
  }
  return output.slice(-4000);
}

class EvidenceComplete extends Error {
  constructor() {
    super("evidence_complete");
  }
}
