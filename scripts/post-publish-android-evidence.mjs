#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import pg from "pg";
import {
  cleanupPostPublishFixture,
  createPostPublishFixture,
  pollPostPublishFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "POST-PUBLISH-ANDROID-REAL-001";
const OPT_IN = "I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const deviceCredentialsPath = "app-internal:post-publish-credentials.json";
const deviceTempCredentialsPath = "/data/local/tmp/post-publish-credentials.json";
const deviceEvidencePath = "files/post-publish-evidence";
const evidenceFiles = [
  "android-post-publish-composer-opened.png",
  "android-post-publish-composer-filled.png",
  "android-post-publish-after-publish-tap.png",
  "android-post-publish-published.png",
  "android-post-publish-evidence.json",
];

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  evidence: {},
  cleanup: { state: "not_started" },
};

const adb = resolveAdbCommand();
let fixture;
let localCredentials;

try {
  if (process.env.QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN !== OPT_IN) throw new Error("mutation_opt_in_required");
  const config = await loadConfiguration();
  const backend = await publicConfig();
  const credentials = config.credentials.a;
  const session = await login(backend, credentials, `post-publish-android-${randomUUID()}`);
  fixture = createPostPublishFixture({
    actorSession: { profileId: session.userId },
    platformLabel: "android",
    runId: randomUUID(),
  });
  localCredentials = join("build-reports", "android", `post-publish-credentials-${randomUUID()}.json`);
  await mkdir(dirname(localCredentials), { recursive: true });
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: credentials.country_code,
      phone: credentials.phone,
      password: credentials.password,
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

  const instrumentationOutput = await runCapture(adb, [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.feature.postcomposer.presentation.PostPublishRealInstrumentedTest",
    "-e", "quataPostPublishCredentialsFile", deviceCredentialsPath,
    "-e", "quataPostPublishMarker", fixture.marker,
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  report.instrumentationTail = redactedTail(instrumentationOutput);
  if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) throw new Error("android_instrumentation_not_ok");
  if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentationOutput)) {
    throw new Error("android_instrumentation_semantic_failure");
  }
  report.steps.push("android_real_text_post_published_from_common_composer");

  const evidenceDir = join("build-reports", "android", "post-publish-evidence");
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  for (const file of evidenceFiles) {
    await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file));
  }
  report.evidence.directory = resolve(evidenceDir);

  const published = await pollPostPublishFixture({
    fixture,
    withDatabase: (callback) => withPg(config, callback),
    delay,
  });
  report.evidence.published = {
    state: "verified_in_database",
    postId: published.postId,
    mediaUrls: published.mediaUrls,
  };
  report.cleanup = await cleanupPostPublishFixture({ fixture, withDatabase: (callback) => withPg(config, callback) });
  report.steps.push("post_publish_cleanup_verified_residue_absent");
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  await copyEvidenceIfPresent().catch(() => {});
  if (fixture && report.cleanup.state === "not_started") {
    try {
      const config = await loadConfiguration();
      report.cleanup = await cleanupPostPublishFixture({ fixture, withDatabase: (callback) => withPg(config, callback) });
    } catch (cleanupError) {
      report.cleanup = {
        state: "rollback_pending",
        marker: fixture.marker,
        postId: fixture.publishedPostId ?? null,
        error: safeFailure(cleanupError),
      };
    }
  }
} finally {
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-f", `files/${deviceCredentialsPath.replace("app-internal:", "")}`]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]).catch(() => {});
  await rm(localCredentials ?? "", { force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  if (fixture) report.marker = fixture.marker;
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Post publish Android evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post publish Android evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post publish Android evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    output: join("build-reports", "android", "post-publish-evidence.json"),
    evidenceDir: join("build-reports", "android", "post-publish-evidence"),
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) throw new Error("invalid_arguments");
    index += 1;
    if (key === "--out") parsed.output = value;
    if (key === "--evidence-dir") parsed.evidenceDir = value;
  }
  return parsed;
}

async function loadConfiguration() {
  const credentialsPath = process.env.QUATA_POST_PUBLISH_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE;
  const credentials = JSON.parse(await readFile(credentialsPath, "utf8"));
  for (const profile of ["a"]) {
    for (const field of ["country_code", "phone", "password"]) {
      if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
    }
  }
  return {
    credentials,
    dbUrlFile: process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbTlsCaFile: process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE,
  };
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
}

async function login(backend, credentials, clientInstanceId) {
  const response = await postJson(`${backend.url}/functions/v1/quata-auth-bridge`, {
    apikey: backend.key,
    "content-type": "application/json",
    "x-client-info": "quata-post-publish-android-evidence",
  }, {
    action: "web_login",
    country_code: String(credentials.country_code),
    phone_local: localPhone(credentials.country_code, credentials.phone),
    password: String(credentials.password),
    client_instance_id: clientInstanceId,
  });
  const profile = response.payload?.profile;
  if (typeof profile?.id !== "string") throw new Error("invalid_auth_response");
  return { userId: profile.id };
}

function localPhone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  return digits.startsWith(country) ? digits.slice(country.length) : digits;
}

async function postJson(url, headers, body) {
  const response = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(30_000),
  }).catch(() => null);
  if (!response) throw new Error("public_request_failed:network");
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(`public_request_failed:http_${response.status}`);
  return { status: response.status, payload };
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

async function copyEvidenceIfPresent() {
  const evidenceDir = join("build-reports", "android", "post-publish-evidence");
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  for (const file of evidenceFiles) {
    await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
  }
  report.evidence.directory = resolve(evidenceDir);
}

async function adbRunAsCat(devicePath, localPath) {
  const output = await runCapture(adb, ["exec-out", "run-as", "com.quata", "cat", devicePath], { allowBinary: true });
  await writeFile(localPath, output, typeof output === "string" ? { mode: 0o600 } : { mode: 0o600 });
}

function resolveAdbCommand() {
  const fromEnv = process.env.ADB?.trim();
  if (fromEnv && existsSync(fromEnv)) return fromEnv;
  const androidHome = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  const candidate = androidHome ? join(androidHome, "platform-tools", process.platform === "win32" ? "adb.exe" : "adb") : null;
  if (candidate && existsSync(candidate)) return candidate;
  return "adb";
}

function gradleEnvironment() {
  return { ...process.env, JAVA_HOME: process.env.JAVA_HOME || "C:/Program Files/Android/Android Studio/jbr" };
}

function run(command, args, env = process.env) {
  return runCapture(command, args, { env }).then(() => {});
}

function runCapture(command, args, { env = process.env, allowBinary = false } = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, { env, stdio: ["ignore", "pipe", "pipe"] });
    const chunks = [];
    const errors = [];
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => errors.push(chunk));
    child.on("error", rejectPromise);
    child.on("close", (code) => {
      const stdout = allowBinary ? Buffer.concat(chunks) : Buffer.concat(chunks).toString("utf8");
      const stderr = Buffer.concat(errors).toString("utf8");
      if (code === 0) resolvePromise(allowBinary ? stdout : `${stdout}${stderr}`);
      else rejectPromise(new Error(`command_failed:${command}:${code}:${redactedTail(`${stdout}${stderr}`)}`));
    });
  });
}

async function gitMetadata() {
  const head = await runCapture("git", ["rev-parse", "HEAD"]);
  const dirty = await runCapture("git", ["status", "--porcelain", "--untracked-files=no"]);
  return { head: head.trim(), workingTreeDirty: dirty.trim().length > 0 };
}

function redactedTail(value) {
  return String(value)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .split(/\r?\n/)
    .slice(-80)
    .join("\n")
    .slice(-4000);
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}
