#!/usr/bin/env node

import { createHash, randomUUID } from "node:crypto";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const DEFAULT_DATABASE_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DATABASE_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const options = parseArgs(process.argv.slice(2));
const { Client } = createRequire(options.dependencyPackage)("pg");
if (process.env.QUATA_IOS_APNS_REMOTE_EVIDENCE !== "1") {
  throw new Error("ios_apns_remote_evidence_opt_in_required");
}

const report = {
  check: "IOS-APNS-LOGOUT-REMOTE-001",
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  evidence: {},
  steps: [],
  cleanup: { state: "pending", tokenResidue: null, sessionRevoked: false, temporaryCredentialsRemoved: false },
};

let client;
let session;
let deviceToken;
let localEnvironmentDirectory;
let remoteEnvironment;
try {
  if (report.git.workingTreeDirty) throw new Error("candidate_worktree_dirty");
  const config = backendConfig(await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8"));
  const credentials = credential(await readFile(options.credentialsFile, "utf8"));
  session = await login(config, credentials);
  report.evidence.profileSha256 = digest(session.profileId);
  report.steps.push("authorized_profile_session_created");

  client = await databaseClient(options);
  await client.connect();
  deviceToken = createHash("sha256").update(`quata-apns-logout-${randomUUID()}`).digest("hex");
  report.evidence.deviceTokenSha256 = digest(deviceToken);
  const before = await tokenRows(client, deviceToken);
  if (before.length !== 0) throw new Error("synthetic_apns_token_preexisted");
  report.steps.push("synthetic_apns_token_absent_before_execution");

  const remoteHead = await remoteSourceHead(options);
  if (remoteHead !== report.git.head) throw new Error("mac_checkout_sha_mismatch");
  report.steps.push("exact_candidate_sha_materialized_on_mac");

  localEnvironmentDirectory = await mkdtemp(join(tmpdir(), "quata-ios-apns-logout-"));
  const localEnvironment = join(localEnvironmentDirectory, "evidence.env");
  await writeFile(localEnvironment, environmentFile({
    backendUrl: config.baseUrl,
    publishableKey: config.publishableKey,
    profileId: session.profileId,
    authUserId: session.authUserId,
    accessToken: session.accessToken,
    deviceToken,
  }), { mode: 0o600 });
  remoteEnvironment = (await run("ssh", [options.host, "mktemp", "/tmp/quata-ios-apns-logout.XXXXXX.env"], { capture: true })).trim();
  await run("scp", [localEnvironment, `${options.host}:${remoteEnvironment}`], { capture: true });
  await run("ssh", [options.host, "chmod", "600", remoteEnvironment], { capture: true });
  report.steps.push("private_runtime_environment_copied_without_logging_contents");

  const remoteLog = `${options.project}/build/ios-apns-logout-remote/xctest.log`;
  const remoteSummary = `${options.project}/build/ios-apns-logout-remote/xctest-summary.json`;
  await ssh(options.host, `set -euo pipefail
cd ${shellQuote(options.project)}
mkdir -p build/ios-apns-logout-remote
source ${shellQuote(remoteEnvironment)}
source "$HOME/.config/quata/ios-intel.env"
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedData)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.udid)}
bash scripts/run-ios-apns-logout-remote-xctest.sh
`, false);
  report.steps.push("app_hosted_xctest_used_production_ios_apns_transport");

  const rows = await tokenRows(client, deviceToken);
  if (rows.length !== 1) throw new Error("synthetic_apns_token_row_missing");
  const row = rows[0];
  if (row.user_id !== session.profileId || row.auth_user_id !== session.authUserId || row.platform !== "ios" ||
      row.apns_environment !== "sandbox" || row.disabled !== true || row.last_error_text !== "Disabled on user logout") {
    throw new Error("synthetic_apns_token_remote_state_invalid");
  }
  report.steps.push("database_confirmed_owned_ios_token_disabled_on_logout");

  const summary = JSON.parse(await run("ssh", [options.host, "cat", remoteSummary], { capture: true }));
  if (summary?.method !== "testProductionTransportRegistersAndUnregistersOwnedSyntheticToken" ||
      summary?.status !== "passed" || summary?.terminalSuccess !== true ||
      !/^[0-9a-f]{64}$/.test(summary?.logSha256 ?? "")) {
    throw new Error("ios_apns_evidence_test_result_invalid");
  }
  const logHash = (await run("ssh", [options.host, "shasum", "-a", "256", remoteLog], { capture: true }))
    .trim().split(/\s+/)[0];
  if (logHash !== summary.logSha256) throw new Error("ios_apns_evidence_hash_mismatch");
  report.evidence.testResult = { tests: 1, skipped: 0, failures: 0, errors: 0, method: summary.method, logSha256: logHash };
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
} finally {
  if (client && deviceToken && session) {
    try {
      await client.query(
        "delete from public.push_tokens where token=$1 and user_id=$2::uuid and auth_user_id=$3::uuid and platform='ios'",
        [deviceToken, session.profileId, session.authUserId],
      );
      report.cleanup.tokenResidue = (await tokenRows(client, deviceToken)).length;
    } catch { report.cleanup.tokenResidue = "unknown"; }
  }
  if (session) {
    try {
      const response = await fetch(`${session.baseUrl}/auth/v1/logout`, {
        method: "POST",
        headers: { apikey: session.publishableKey, authorization: `Bearer ${session.accessToken}`, "content-type": "application/json" },
        body: "{}",
        signal: AbortSignal.timeout(20_000),
      });
      if (response.ok && client) {
        const active = await client.query(
          "select exists(select 1 from auth.sessions where id=$1::uuid and user_id=$2::uuid) as active",
          [session.sessionId, session.authUserId],
        );
        report.cleanup.sessionRevoked = active.rows[0]?.active === false;
      }
    } catch { report.cleanup.sessionRevoked = false; }
  }
  await client?.end().catch(() => {});
  if (remoteEnvironment) {
    await run("ssh", [options.host, "rm", "-f", remoteEnvironment], { capture: true }).then(
      () => { report.cleanup.remoteEnvironmentRemoved = true; },
      () => { report.cleanup.remoteEnvironmentRemoved = false; },
    );
  }
  if (localEnvironmentDirectory) {
    await rm(localEnvironmentDirectory, { recursive: true, force: true }).then(
      () => { report.cleanup.localEnvironmentRemoved = true; },
      () => { report.cleanup.localEnvironmentRemoved = false; },
    );
  }
  report.cleanup.temporaryCredentialsRemoved =
    (!remoteEnvironment || report.cleanup.remoteEnvironmentRemoved === true) &&
    (!localEnvironmentDirectory || report.cleanup.localEnvironmentRemoved === true);
  report.cleanup.state = report.cleanup.tokenResidue === 0 && report.cleanup.sessionRevoked && report.cleanup.temporaryCredentialsRemoved ? "completed" : "failed";
  if (report.cleanup.state !== "completed") report.status = "failed";
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`iOS APNs logout evidence written: ${options.output}`);
}

if (report.status !== "passed") process.exitCode = 1;

function parseArgs(argv) {
  const value = {
    credentialsFile: process.env.QUATA_CHAT_GROUP_CREDENTIALS_FILE || DEFAULT_CREDENTIALS_FILE,
    databaseUrlFile: process.env.QUATA_SUPABASE_DB_URL_FILE || DEFAULT_DATABASE_URL_FILE,
    databaseCaFile: process.env.QUATA_SUPABASE_DB_CA_FILE || DEFAULT_DATABASE_CA_FILE,
    dependencyPackage: process.env.QUATA_E2E_DEPENDENCY_PACKAGE || resolve("package.json"),
    host: process.env.QUATA_IOS_SSH_HOST || "gabriel@192.168.1.109",
    project: process.env.QUATA_IOS_MAC_PROJECT || "",
    derivedData: process.env.QUATA_IOS_DERIVED_DATA_PATH || "",
    udid: process.env.QUATA_IOS_SIMULATOR_UDID || "",
    output: "build-reports/ios/ios-apns-logout-remote-evidence.json",
  };
  const mapping = { "--credentials-file": "credentialsFile", "--database-url-file": "databaseUrlFile", "--database-ca-file": "databaseCaFile", "--dependency-package": "dependencyPackage", "--host": "host", "--project": "project", "--derived-data": "derivedData", "--udid": "udid", "--out": "output" };
  for (let index = 0; index < argv.length; index += 1) {
    const key = mapping[argv[index]];
    if (!key || !argv[index + 1]) throw new Error(`invalid_argument:${argv[index]}`);
    value[key] = argv[++index];
  }
  if (!value.project || !value.project.startsWith("/") || value.project.includes("..") || /[\r\n\0]/.test(value.project)) throw new Error("invalid_mac_project");
  if (!value.derivedData || !value.derivedData.startsWith("/") || value.derivedData.includes("..") || /[\r\n\0]/.test(value.derivedData)) throw new Error("invalid_ios_derived_data");
  if (!/^[0-9A-F]{8}(?:-[0-9A-F]{4}){3}-[0-9A-F]{12}$/i.test(value.udid)) throw new Error("invalid_ios_simulator_udid");
  for (const key of ["credentialsFile", "databaseUrlFile", "databaseCaFile", "dependencyPackage", "output"]) value[key] = resolve(value[key]);
  return value;
}

function credential(bytes) {
  const value = JSON.parse(bytes.toString("utf8").replace(/^\uFEFF/, ""))?.a;
  const countryCode = String(value?.country_code ?? "").replace(/\D/g, "");
  let phone = String(value?.phone ?? "").replace(/\D/g, "");
  if (countryCode && phone.startsWith(countryCode)) phone = phone.slice(countryCode.length);
  const password = String(value?.password ?? "");
  if (!countryCode || !phone || !password) throw new Error("invalid_authorized_credentials");
  return { countryCode, phone, password };
}

function backendConfig(source) {
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const publishableKey = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !publishableKey || publishableKey.startsWith("sb_secret_")) throw new Error("missing_public_backend_configuration");
  return { baseUrl, publishableKey };
}

async function login(config, user) {
  const response = await fetch(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: { apikey: config.publishableKey, "content-type": "application/json" },
    body: JSON.stringify({ action: "login", country_code: user.countryCode, phone: user.phone, password: user.password }),
    signal: AbortSignal.timeout(20_000),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`authorized_login_failed:http_${response.status}`);
  const profileId = body?.profile?.id;
  const authUserId = body?.profile?.auth_user_id ?? body?.user?.id;
  const accessToken = body?.session?.access_token;
  const sessionId = accessToken ? jwtSessionId(accessToken) : null;
  if (!UUID.test(profileId ?? "") || !UUID.test(authUserId ?? "") || !accessToken || !UUID.test(sessionId ?? "")) {
    throw new Error("authorized_login_response_invalid");
  }
  return { profileId, authUserId, accessToken, sessionId, baseUrl: config.baseUrl, publishableKey: config.publishableKey };
}

function jwtSessionId(token) {
  try { return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"))?.session_id ?? null; }
  catch { return null; }
}

async function databaseClient(value) {
  const connection = new URL((await readFile(value.databaseUrlFile, "utf8")).trim());
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) connection.searchParams.delete(key);
  return new Client({ connectionString: connection.toString(), ssl: { ca: await readFile(value.databaseCaFile, "utf8"), rejectUnauthorized: true }, connectionTimeoutMillis: 8_000, statement_timeout: 8_000 });
}

async function tokenRows(db, token) {
  return (await db.query(`select user_id::text, auth_user_id::text, platform, apns_environment,
    disabled_at is not null as disabled, last_error_text from public.push_tokens where token=$1`, [token])).rows;
}

async function remoteSourceHead(value) {
  const command = `cd ${shellQuote(value.project)} && if test -f .quata-product-sha; then cat .quata-product-sha; else git rev-parse HEAD; fi`;
  return (await ssh(value.host, command, true)).trim();
}

function environmentFile(value) {
  return [
    "export QUATA_IOS_APNS_REMOTE_EVIDENCE='1'",
    `export QUATA_IOS_APNS_BACKEND_URL=${shellQuote(value.backendUrl)}`,
    `export QUATA_IOS_APNS_PUBLISHABLE_KEY=${shellQuote(value.publishableKey)}`,
    `export QUATA_IOS_APNS_PROFILE_ID=${shellQuote(value.profileId)}`,
    `export QUATA_IOS_APNS_AUTH_USER_ID=${shellQuote(value.authUserId)}`,
    `export QUATA_IOS_APNS_ACCESS_TOKEN=${shellQuote(value.accessToken)}`,
    `export QUATA_IOS_APNS_DEVICE_TOKEN=${shellQuote(value.deviceToken)}`,
    "",
  ].join("\n");
}

function shellQuote(value) { return `'${String(value).replaceAll("'", "'\\''")}'`; }
function digest(value) { return createHash("sha256").update(String(value)).digest("hex"); }
function safeFailure(error) { return String(error?.message ?? error).split(":")[0].replace(/[^a-zA-Z0-9_.-]/g, "_").slice(0, 100); }

function run(command, args, { capture = false, input = null } = {}) {
  return new Promise((resolveRun, reject) => {
    let output = "";
    const child = spawn(command, args, { shell: false, windowsHide: true, stdio: [input == null ? "ignore" : "pipe", capture ? "pipe" : "inherit", capture ? "pipe" : "inherit"] });
    if (input != null) child.stdin.end(input);
    if (capture) {
      child.stdout.on("data", (chunk) => { output += chunk.toString(); });
      child.stderr.on("data", (chunk) => { output += chunk.toString(); });
    }
    child.once("error", reject);
    child.once("exit", (code) => code === 0 ? resolveRun(output) : reject(new Error(`command_failed:${command}:${code}`)));
  });
}

function ssh(host, script, capture = true) { return run("ssh", [host, "bash", "-s"], { capture, input: script }); }

async function gitMetadata() {
  const head = (await run("git", ["rev-parse", "HEAD"], { capture: true })).trim();
  const branch = (await run("git", ["branch", "--show-current"], { capture: true })).trim();
  const workingTreeDirty = (await run("git", ["status", "--porcelain"], { capture: true })).trim().length > 0;
  return { head, branch, workingTreeDirty };
}
