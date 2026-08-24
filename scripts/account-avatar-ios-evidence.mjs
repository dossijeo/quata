#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { setTimeout as delay } from "node:timers/promises";
import { validPngFixture } from "./e2e-fixtures/chat-attachments.mjs";
import { assertStorageObjectAbsent } from "./e2e-fixtures/supabase-storage-cleanup.mjs";

const CHECK = "ACCOUNT-AVATAR-IOS-REAL-001";
const PICKER_OPT_IN = "I_ACCEPT_IOS_ACCOUNT_AVATAR_PICKER_FIXTURE";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  attempts: [],
  evidence: {},
  cleanup: { attempted: false, storageDeleted: false, profileRestored: false },
  steps: [],
};

let localCredentials;
let remoteCredentials;
let localFixture;
let remoteFixture;
let session;
let original;
let backend;
let uploadedAvatarUrl;

try {
  backend = await publicConfig();
  const credentials = (await loadCredentials()).a;
  session = await login(backend, credentials, `ACCOUNT-AVATAR-ios-${randomUUID()}`);
  original = await fetchProfile(backend, session);

  localCredentials = join(await mkdirTemp("quata-ios-account-avatar-credentials-"), "credentials.json");
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: credentials.country_code,
      phone: e164Phone(credentials.country_code, credentials.phone),
      password: credentials.password,
    })}\n`,
    { mode: 0o600 },
  );
  remoteCredentials = (await runCapture("ssh", [options.host, "mktemp /tmp/quata-ios-account-avatar-credentials.XXXXXX.json"])).trim();
  await run("scp", [localCredentials, `${options.host}:${remoteCredentials}`]);
  report.steps.push("ios_real_credentials_copied_to_mac_tempfile_without_logging_contents");

  localFixture = join(await mkdirTemp("quata-ios-account-avatar-fixture-"), "ACCOUNT-AVATAR-fixture.png");
  await writeFile(localFixture, validPngFixture(), { mode: 0o600 });
  remoteFixture = (await runCapture("ssh", [options.host, "mktemp /tmp/quata-ios-account-avatar-fixture.XXXXXX.png"])).trim();
  await run("scp", [localFixture, `${options.host}:${remoteFixture}`]);
  report.steps.push("ios_account_avatar_fixture_copied_to_mac_tempfile");

  const remoteState = JSON.parse((await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
node -e 'const {execFileSync}=require("node:child_process"); const head=execFileSync("git",["rev-parse","HEAD"],{encoding:"utf8"}).trim(); const status=execFileSync("git",["status","--porcelain"],{encoding:"utf8"}).trim(); process.stdout.write(JSON.stringify({head,workingTreeDirty:status.length>0})+"\\n")'
`)).trim());
  report.mac = { host: options.host, project: options.project, head: remoteState.head, workingTreeDirty: remoteState.workingTreeDirty };
  if (remoteState.head !== report.git.head) throw new Error(`mac_checkout_sha_mismatch:${remoteState.head}:${report.git.head}`);
  if (remoteState.workingTreeDirty !== false) throw new Error("mac_checkout_dirty");
  report.steps.push("mac_checkout_sha_matches_local_candidate");
  report.steps.push("mac_checkout_clean");

  if (options.buildFirst) {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
scripts/build-ios-intel-simulator-signed.sh
`);
    report.steps.push("ios_simulator_signed_build_succeeded_on_mac");
  }

  report.attempts.push(await runAttempt());
  const failedAttempt = report.attempts.find((attempt) => attempt.status !== "passed");
  if (failedAttempt) throw new Error(`ios_attempt_failed:${failedAttempt.error ?? "unknown"}`);

  uploadedAvatarUrl = await waitForRemoteAvatarChange(backend, session, original.avatar_url ?? null);
  const publicProbe = await probePublicAvatar(uploadedAvatarUrl);
  if (!publicProbe.ok) throw new Error(`ios_account_avatar_public_probe_failed:${publicProbe.status}`);
  const storagePath = storagePathFromPublicUrl(backend.url, session.userId, uploadedAvatarUrl);
  report.evidence.remote = {
    originalAvatarPresent: Boolean(original.avatar_url),
    uploadedAvatarChanged: true,
    uploadedStoragePath: storagePath,
    publicProbe,
  };
  report.accountAvatarSteps = [
    "avatar_selected",
    "avatar_editor_confirmed",
    "avatar_uploaded",
    "avatar_persisted",
  ];
  await cleanupUploadedAvatar(backend, session, original.avatar_url ?? null, uploadedAvatarUrl);
  const afterCleanup = await fetchProfile(backend, session);
  if ((afterCleanup.avatar_url ?? null) !== (original.avatar_url ?? null)) throw new Error("ios_account_avatar_profile_not_restored");
  report.accountAvatarSteps.push("avatar_rollback_verified", "avatar_cleanup_verified");
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  if (backend && session) {
    if (!uploadedAvatarUrl) {
      uploadedAvatarUrl = await waitForRemoteAvatarChange(backend, session, original?.avatar_url ?? null, 2_000).catch(() => null);
    }
    if (uploadedAvatarUrl) {
      await cleanupUploadedAvatar(backend, session, original?.avatar_url ?? null, uploadedAvatarUrl).catch((cleanupError) => {
        report.cleanup.error = safeFailure(cleanupError);
      });
    }
  }
} finally {
  await copyRemoteEvidence(options).catch((error) => {
    report.evidence.copyWarning = safeFailure(error);
  });
  if (remoteCredentials) await run("ssh", [options.host, "rm", "-f", remoteCredentials]).catch(() => {});
  if (remoteFixture) await run("ssh", [options.host, "rm", "-f", remoteFixture]).catch(() => {});
  if (localCredentials) await rm(dirname(localCredentials), { recursive: true, force: true }).catch(() => {});
  if (localFixture) await rm(dirname(localFixture), { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Account avatar iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Account avatar iOS evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Account avatar iOS evidence passed.");
}

async function runAttempt() {
  try {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
export QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
export QUATA_IOS_ACCOUNT_AVATAR_UI_LOG_DIR=${shellQuote(options.remoteLogDir)}
export QUATA_IOS_ACCOUNT_AVATAR_UI_RESULT_BUNDLE_DIR=${shellQuote(`${options.remoteLogDir}/xcresults`)}
export QUATA_IOS_ACCOUNT_AVATAR_PICKER_FIXTURE_OPT_IN=${shellQuote(PICKER_OPT_IN)}
export QUATA_IOS_ACCOUNT_AVATAR_PICKER_PATH=${shellQuote(remoteFixture)}
export QUATA_IOS_ACCOUNT_AVATAR_PICKER_NAME='ACCOUNT-AVATAR-fixture.png'
export QUATA_IOS_ACCOUNT_AVATAR_PICKER_MIME='image/png'
bash scripts/run-ios-account-avatar-ui-test.sh
`);
    return { source: "gallery-avatar", outcome: "success", status: "passed", remoteLogDir: options.remoteLogDir };
  } catch (error) {
    return { source: "gallery-avatar", outcome: "success", status: "failed", remoteLogDir: options.remoteLogDir, error: safeFailure(error) };
  }
}

async function waitForRemoteAvatarChange(backend, session, originalAvatarUrl, timeoutMillis = 60_000) {
  const deadline = Date.now() + timeoutMillis;
  while (Date.now() < deadline) {
    const profile = await fetchProfile(backend, session);
    const current = profile.avatar_url ?? null;
    if (current && current !== originalAvatarUrl && current.includes(`/storage/v1/object/public/community-posts/avatars/${session.userId}/`)) {
      return current;
    }
    await delay(1_000);
  }
  throw new Error("ios_account_avatar_remote_change_timeout");
}

async function cleanupUploadedAvatar(backend, session, originalAvatarUrl, uploadedAvatarUrl) {
  report.cleanup.attempted = true;
  await patchProfileAvatar(backend, session, originalAvatarUrl);
  report.cleanup.profileRestored = true;
  const path = storagePathFromPublicUrl(backend.url, session.userId, uploadedAvatarUrl);
  await deleteStorageObject(backend, session, path);
  report.cleanup.storageDeleted = true;
  report.cleanup.storagePath = path;
  report.cleanup.physicalResidue = await assertStorageObjectAbsent({ storagePath: path });
}

async function fetchProfile(backend, session) {
  const response = await fetch(`${backend.url}/rest/v1/community_profiles?id=eq.${encodeURIComponent(session.userId)}&select=id,avatar_url`, {
    headers: authHeaders(backend.key, session.accessToken),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`profile_fetch_failed:${response.status}`);
  const rows = await response.json();
  return rows?.[0] ?? {};
}

async function patchProfileAvatar(backend, session, avatarUrl) {
  const response = await fetch(`${backend.url}/rest/v1/community_profiles?id=eq.${encodeURIComponent(session.userId)}`, {
    method: "PATCH",
    headers: { ...authHeaders(backend.key, session.accessToken), "content-type": "application/json", prefer: "return=minimal" },
    body: JSON.stringify({ avatar_url: avatarUrl }),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`profile_restore_failed:${response.status}`);
}

async function deleteStorageObject(backend, session, path) {
  const response = await fetch(`${backend.url}/storage/v1/object/community-posts/${path}`, {
    method: "DELETE",
    headers: authHeaders(backend.key, session.accessToken),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok && response.status !== 404) throw new Error(`avatar_storage_delete_failed:${response.status}`);
}

async function probePublicAvatar(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(20_000) });
  const contentType = response.headers.get("content-type") ?? "";
  return { ok: response.ok && contentType.startsWith("image/"), status: response.status, contentType };
}

function storagePathFromPublicUrl(baseUrl, profileId, publicUrl) {
  const marker = `${baseUrl.replace(/\/+$/, "")}/storage/v1/object/public/community-posts/`;
  const path = String(publicUrl ?? "").startsWith(marker) ? String(publicUrl).slice(marker.length) : "";
  if (!path.startsWith(`avatars/${profileId}/`) || path.includes("..")) throw new Error("avatar_storage_path_invalid");
  return path;
}

function authHeaders(key, accessToken) {
  return { apikey: key, authorization: `Bearer ${accessToken}`, "x-client-info": "quata-account-avatar-ios-evidence" };
}

function parseArgs(args) {
  const parsed = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    remoteLogDir: process.env.QUATA_IOS_ACCOUNT_AVATAR_UI_LOG_DIR?.trim() || "build/reports/ios/ACCOUNT-AVATAR-ui",
    output: join("build-reports", "ios", "account-avatar-evidence.json"),
    evidenceDir: join("build-reports", "ios", "account-avatar-evidence"),
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

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_ACCOUNT_AVATAR_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE, "utf8"));
  for (const profile of ["a"]) {
    for (const field of ["country_code", "phone", "password"]) {
      if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
    }
  }
  return credentials;
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
}

async function login(backend, credentials, clientInstanceId) {
  const response = await fetch(`${backend.url}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-account-avatar-ios-evidence" },
    body: JSON.stringify({
      action: "web_login",
      country_code: String(credentials.country_code),
      phone_local: localPhone(credentials.country_code, credentials.phone),
      password: String(credentials.password),
      client_instance_id: clientInstanceId,
    }),
    signal: AbortSignal.timeout(30_000),
  }).catch(() => null);
  if (!response) throw new Error("public_request_failed:network");
  const payload = JSON.parse(await response.text());
  if (!response.ok) throw new Error(`public_request_failed:http_${response.status}`);
  const session = payload?.session;
  const profile = payload?.profile;
  if (typeof session?.access_token !== "string" || typeof profile?.id !== "string") throw new Error("invalid_auth_response");
  return { accessToken: session.access_token, userId: profile.id, clientInstanceId };
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

async function copyRemoteEvidence({ host, project, remoteLogDir, evidenceDir }) {
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  const source = remoteLogDir.startsWith("/") ? remoteLogDir : `${project}/${remoteLogDir}`;
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
  const branch = (await runCapture("git", ["branch", "--show-current"])).trim();
  const status = await runCapture("git", ["status", "--porcelain"]);
  return { head, branch, workingTreeDirty: status.trim().length > 0 };
}

async function runSshScript(host, script) {
  return runCapture("ssh", [host, "bash", "-s"], { input: script });
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, "'\\''")}'`;
}

function run(command, args, options = {}) {
  return runCapture(command, args, options).then(() => undefined);
}

function runCapture(command, args, { input = null, allowFailure = false } = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, { stdio: ["pipe", "pipe", "pipe"], shell: process.platform === "win32" });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => { stdout += chunk; });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("error", rejectPromise);
    child.on("close", (code) => {
      if (code !== 0 && !allowFailure) rejectPromise(new Error(`${command} exited ${code}: ${redactedTail(stderr || stdout)}`));
      else resolvePromise(stdout);
    });
    if (input) child.stdin.end(input);
    else child.stdin.end();
  });
}

function redactedTail(value) {
  return String(value ?? "")
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .split(/\r?\n/)
    .slice(-80)
    .join("\n");
}

function safeFailure(error) {
  return redactedTail(error?.message ?? String(error));
}
