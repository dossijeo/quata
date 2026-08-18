#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { setTimeout as delay } from "node:timers/promises";
import pg from "pg";
import {
  cleanupPostPublishFixture,
  createPostPublishFixture,
  pollPostPublishFixture,
  selectPostPublishDestinationFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "POST-PUBLISH-IOS-REAL-001";
const OPT_IN = "I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";

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

let fixture;
let localCredentials;
let remoteCredentials;

try {
  if (process.env.QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN !== OPT_IN) throw new Error("mutation_opt_in_required");
  const config = await loadConfiguration();
  const backend = await publicConfig();
  const credentials = config.credentials.a;
  const session = await login(backend, credentials, `post-publish-ios-${randomUUID()}`);
  const destination = await selectPostPublishDestinationFixture({
    actorSession: { profileId: session.userId },
    withDatabase: (callback) => withPg(config, callback),
  });
  fixture = createPostPublishFixture({
    actorSession: { profileId: session.userId },
    platformLabel: "ios",
    runId: randomUUID(),
    destination,
  });

  localCredentials = join(await mkdirTemp("quata-ios-post-publish-credentials-"), "credentials.json");
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: credentials.country_code,
      phone: e164Phone(credentials.country_code, credentials.phone),
      password: credentials.password,
    })}\n`,
    { mode: 0o600 },
  );
  remoteCredentials = (await runCapture("ssh", [
    options.host,
    "mktemp /tmp/quata-ios-post-publish-credentials.XXXXXX.json",
  ])).trim();
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
export QUATA_IOS_POST_PUBLISH_UI_LOG_DIR=${shellQuote(options.remoteLogDir)}
export QUATA_IOS_POST_PUBLISH_UI_RESULT_BUNDLE_DIR=${shellQuote(options.remoteResultBundleDir)}
export QUATA_IOS_POST_PUBLISH_REAL_MUTATION_OPT_IN=${shellQuote(OPT_IN)}
export QUATA_IOS_POST_PUBLISH_MARKER=${shellQuote(fixture.marker)}
export QUATA_IOS_POST_PUBLISH_DESTINATION_WALL_ID=${shellQuote(fixture.destination.wallId)}
bash scripts/run-ios-post-publish-ui-test.sh
`);
  report.steps.push("ios_xctest_real_text_post_published_from_common_composer");

  await copyRemoteEvidence(options).catch((error) => {
    report.evidence.copyWarning = safeFailure(error);
  });
  const published = await pollPostPublishFixture({
    fixture,
    withDatabase: (callback) => withPg(config, callback),
    delay,
  });
  report.evidence.published = {
    state: "verified_in_database",
    postId: published.postId,
    wallId: published.wallId,
    expectedWallId: fixture.destination.wallId,
    mediaUrls: published.mediaUrls,
  };
  report.cleanup = await cleanupPostPublishFixture({ fixture, withDatabase: (callback) => withPg(config, callback) });
  report.steps.push("post_publish_cleanup_verified_residue_absent");
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  await copyRemoteEvidence(options).catch(() => {});
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
  if (remoteCredentials) await run("ssh", [options.host, "rm", "-f", remoteCredentials]).catch(() => {});
  if (localCredentials) await rm(dirname(localCredentials), { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  if (fixture) report.marker = fixture.marker;
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Post publish iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Post publish iOS evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post publish iOS evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    remoteLogDir: process.env.QUATA_IOS_POST_PUBLISH_UI_LOG_DIR?.trim() || "build/reports/ios/post-publish-ui",
    remoteResultBundleDir: process.env.QUATA_IOS_POST_PUBLISH_UI_RESULT_BUNDLE_DIR?.trim() || "build/reports/ios/post-publish-ui/xcresults",
    output: join("build-reports", "ios", "post-publish-evidence.json"),
    evidenceDir: join("build-reports", "ios", "post-publish-evidence"),
    simulatorUdid: process.env.QUATA_IOS_SIMULATOR_UDID?.trim() || "",
    buildFirst: process.env.QUATA_IOS_BUILD_FIRST === "1",
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (["--host", "--project", "--derived-data", "--remote-log-dir", "--out", "--evidence-dir", "--simulator"].includes(key)) {
      if (!value || value.startsWith("--")) throw new Error(`missing_value:${key}`);
      index += 1;
      if (key === "--host") parsed.host = value;
      if (key === "--project") parsed.project = value;
      if (key === "--derived-data") parsed.derivedDataPath = value;
      if (key === "--remote-log-dir") parsed.remoteLogDir = value;
      if (key === "--out") parsed.output = value;
      if (key === "--evidence-dir") parsed.evidenceDir = value;
      if (key === "--simulator") parsed.simulatorUdid = value;
    } else if (key === "--build-first") {
      parsed.buildFirst = true;
    } else {
      throw new Error(`unknown_argument:${key}`);
    }
  }
  if (!parsed.simulatorUdid) throw new Error("missing_environment:QUATA_IOS_SIMULATOR_UDID");
  parsed.output = resolve(parsed.output);
  parsed.evidenceDir = resolve(parsed.evidenceDir);
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
    "x-client-info": "quata-post-publish-ios-evidence",
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

function e164Phone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const local = localPhone(countryCode, phone);
  if (!country || !local) throw new Error("ios_e164_credentials_required");
  return `+${country}${local}`;
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

async function copyRemoteEvidence({ host, remoteLogDir, evidenceDir }) {
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  const source = remoteLogDir.startsWith("/") ? remoteLogDir : `${options.project}/${remoteLogDir}`;
  await run("scp", ["-r", `${host}:${source}/.`, evidenceDir]);
  report.evidence.directory = resolve(evidenceDir);
}

async function mkdirTemp(prefix) {
  const path = join(tmpdir(), `${prefix}${randomUUID()}`);
  await mkdir(path, { recursive: true, mode: 0o700 });
  return path;
}

async function gitMetadata() {
  const head = (await runCapture("git", ["rev-parse", "HEAD"])).trim();
  const status = await runCapture("git", ["status", "--porcelain"]);
  return { head, workingTreeDirty: status.trim().length > 0 };
}

async function runSshScript(host, script) {
  return runCapture("ssh", [host, "bash", "-s"], { input: script });
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, "'\\''")}'`;
}

function run(command, args, options = {}) {
  return runCapture(command, args, options).then(() => {});
}

function runCapture(command, args, { input = null, allowFailure = false } = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, { stdio: ["pipe", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => { stdout += chunk; });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("error", rejectPromise);
    child.on("close", (code) => {
      if (code !== 0 && !allowFailure) {
        rejectPromise(new Error(`${command} exited ${code}: ${redactedTail(stderr || stdout)}`));
      } else {
        resolvePromise(stdout);
      }
    });
    if (input) child.stdin.end(input);
    else child.stdin.end();
  });
}

function redactedTail(value) {
  return String(value ?? "")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .split(/\r?\n/)
    .slice(-80)
    .join("\n");
}

function safeFailure(error) {
  const message = error?.message ?? String(error);
  return redactedTail(message);
}
