#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import pg from "pg";

const CHECK = "UGC-TERMS-ANDROID-REMOTE-001";
const VERSION = "2026-07";
const CREDENTIALS_FILE = process.env.QUATA_UGC_TERMS_CREDENTIALS_FILE?.trim() || "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const DB_URL_FILE = process.env.QUATA_SUPABASE_DB_URL_FILE?.trim() || "C:/Users/PC/.quata-supabase-db-url.txt";
const DB_CA_FILE = process.env.QUATA_SUPABASE_DB_CA_FILE?.trim() || "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const output = resolve(process.env.QUATA_UGC_TERMS_ANDROID_REPORT || "build-reports/android/ugc-terms-remote-evidence.json");
const evidenceDir = resolve(process.env.QUATA_UGC_TERMS_ANDROID_EVIDENCE_DIR || "build-reports/android/ugc-terms-remote-evidence");
const report = { check: CHECK, status: "failed", startedAt: new Date().toISOString(), steps: [], cleanup: { attempted: false, restored: false } };
const adb = process.env.ADB?.trim() || "adb";
let client;
let fixture;
let localCredentials;

try {
  const credentials = (JSON.parse(await readFile(CREDENTIALS_FILE, "utf8"))).a;
  requireFields(credentials, ["country_code", "phone", "password"]);
  const backend = await publicConfig();
  const session = await login(backend, credentials);
  client = new pg.Client(await pgConnectionConfig());
  await client.connect();
  fixture = await prepareFixture(client, session.userId);
  report.steps.push("remote_acceptance_snapshotted_and_removed");

  localCredentials = resolve(join("build-reports", "android", `ugc-terms-credentials-${randomUUID()}.json`));
  await mkdir(dirname(localCredentials), { recursive: true });
  await writeFile(localCredentials, `${JSON.stringify({ country_code: credentials.country_code, phone: credentials.phone, password: credentials.password })}\n`, { mode: 0o600 });
  if (process.env.QUATA_UGC_TERMS_ANDROID_SKIP_BUILD !== "1") {
    await run(process.platform === "win32" ? "gradlew.bat" : "./gradlew", [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain", "--no-parallel"]);
  }
  await run(adb, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
  await run(adb, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  await run(adb, ["shell", "run-as", "com.quata", "mkdir", "-p", "files"]);
  await runWithInput(adb, ["shell", "run-as", "com.quata", "dd", "of=files/ugc-terms-credentials.json", "bs=4096"], await readFile(localCredentials));
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", "files/ugc-terms-remote-evidence"]);

  const instrumentation = await runCapture(adb, [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.core.moderation.UgcTermsRemoteAcceptanceInstrumentedTest#authenticatedUserAcceptsTermsThroughProductGateAndPersistsRemotely",
    "-e", "quataUgcTermsCredentialsFile", "app-internal:ugc-terms-credentials.json",
    "-e", "quataUgcTermsRemoteEvidence", "1",
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  if (!/OK \(1 test\)/.test(instrumentation) || /FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentation)) {
    throw new Error(`android_instrumentation_failed:${redact(instrumentation)}`);
  }
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  const deviceEvidence = await runBuffer(adb, ["exec-out", "run-as", "com.quata", "cat", "files/ugc-terms-remote-evidence/android-ugc-terms-remote-evidence.json"]);
  await writeFile(join(evidenceDir, "android-ugc-terms-remote-evidence.json"), deviceEvidence);
  const productReport = JSON.parse(deviceEvidence.toString("utf8"));
  const remote = await readAcceptance(client, session.userId);
  if (productReport.status !== "passed" || productReport.remotePersisted !== true || !remote) throw new Error("ugc_terms_android_remote_assertion_failed");
  report.steps.push("product_gate_accepted", "production_gateway_persisted", "remote_row_verified");
  report.product = { gateObserved: true, acceptedThroughProductUi: true, remotePersisted: true };
  report.status = "passed";
} catch (error) {
  report.error = { name: error?.name || "Error", message: redact(error?.message || String(error)).slice(-1200) };
} finally {
  report.cleanup.attempted = Boolean(client && fixture);
  if (client && fixture) {
    await restoreFixture(client, fixture).catch((error) => { report.cleanup.error = redact(error?.message || String(error)); });
    report.cleanup.restored = await verifyRestored(client, fixture).catch(() => false);
  }
  if (client) await client.end().catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-f", "files/ugc-terms-credentials.json"]).catch(() => {});
  if (localCredentials) await rm(localCredentials, { force: true }).catch(() => {});
  if (report.cleanup.attempted && !report.cleanup.restored) report.status = "failed";
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`UGC terms Android evidence written: ${output}`);
}
if (report.status !== "passed") process.exitCode = 1;

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
}
async function login(backend, credentials) {
  const country = String(credentials.country_code).replace(/\D/g, "");
  const digits = String(credentials.phone).replace(/\D/g, "");
  const phone = digits.startsWith(country) ? digits.slice(country.length) : digits;
  const response = await fetch(`${backend.url}/functions/v1/quata-auth-bridge`, { method: "POST", headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-ugc-terms-android-evidence" }, body: JSON.stringify({ action: "web_login", country_code: country, phone_local: phone, password: credentials.password, client_instance_id: `UGC-TERMS-android-${randomUUID()}` }), signal: AbortSignal.timeout(30_000) });
  const payload = JSON.parse(await response.text());
  if (!response.ok || typeof payload?.profile?.id !== "string") throw new Error(`ugc_terms_login_failed:${response.status}`);
  return { userId: payload.profile.id };
}
async function pgConnectionConfig() {
  const url = new URL((await readFile(DB_URL_FILE, "utf8")).trim());
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return { connectionString: url.toString(), ssl: { ca: await readFile(DB_CA_FILE, "utf8"), rejectUnauthorized: true, servername: url.hostname } };
}
async function readAcceptance(db, profileId) { return (await db.query("select accepted_at from public.ugc_terms_acceptances where profile_id=$1::uuid and terms_version=$2", [profileId, VERSION])).rows[0] || null; }
async function prepareFixture(db, profileId) { const original = await readAcceptance(db, profileId); await db.query("delete from public.ugc_terms_acceptances where profile_id=$1::uuid and terms_version=$2", [profileId, VERSION]); return { profileId, original: original?.accepted_at?.toISOString?.() || null }; }
async function restoreFixture(db, state) { if (state.original) await db.query("insert into public.ugc_terms_acceptances(profile_id,terms_version,accepted_at) values($1::uuid,$2,$3::timestamptz) on conflict(profile_id,terms_version) do update set accepted_at=excluded.accepted_at", [state.profileId, VERSION, state.original]); else await db.query("delete from public.ugc_terms_acceptances where profile_id=$1::uuid and terms_version=$2", [state.profileId, VERSION]); }
async function verifyRestored(db, state) { const row = await readAcceptance(db, state.profileId); return state.original ? row?.accepted_at?.toISOString?.() === state.original : !row; }
function requireFields(value, fields) { for (const field of fields) if (!value?.[field]) throw new Error(`credentials_missing:a.${field}`); }
function redact(value) { return String(value).replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]").replace(/\b\d{7,}\b/g, "[digits]"); }
function run(command, args) { return runCapture(command, args).then(() => undefined); }
function runCapture(command, args) { return new Promise((resolvePromise, reject) => { const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32" }); let output = ""; child.stdout.on("data", chunk => output += chunk); child.stderr.on("data", chunk => output += chunk); child.on("close", code => code === 0 ? resolvePromise(output) : reject(new Error(`${command} failed:${code}\n${redact(output.slice(-8000))}`))); }); }
function runBuffer(command, args) { return new Promise((resolvePromise, reject) => { const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: false }); const chunks = []; let error = ""; child.stdout.on("data", chunk => chunks.push(chunk)); child.stderr.on("data", chunk => error += chunk); child.on("close", code => code === 0 ? resolvePromise(Buffer.concat(chunks)) : reject(new Error(redact(error)))); }); }
function runWithInput(command, args, input) { return new Promise((resolvePromise, reject) => { const child = spawn(command, args, { stdio: ["pipe", "pipe", "pipe"], shell: false }); let output = ""; child.stdout.on("data", chunk => output += chunk); child.stderr.on("data", chunk => output += chunk); child.on("close", code => code === 0 ? resolvePromise(output) : reject(new Error(redact(output)))); child.stdin.end(input); }); }
