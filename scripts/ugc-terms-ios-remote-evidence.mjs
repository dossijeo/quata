#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import pg from "pg";

const VERSION = "2026-07";
const credentialsFile = process.env.QUATA_UGC_TERMS_CREDENTIALS_FILE?.trim() || "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const dbUrlFile = process.env.QUATA_SUPABASE_DB_URL_FILE?.trim() || "C:/Users/PC/.quata-supabase-db-url.txt";
const dbCaFile = process.env.QUATA_SUPABASE_DB_CA_FILE?.trim() || "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const options = {
  host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
  project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/Documents/Projects/quata",
  derivedData: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
  simulator: process.env.QUATA_IOS_SIMULATOR_UDID?.trim() || "",
  remoteLogDir: process.env.QUATA_IOS_UGC_TERMS_UI_LOG_DIR?.trim() || "build/reports/ios/UGC-TERMS-remote-ui",
  output: resolve(process.env.QUATA_IOS_UGC_TERMS_REPORT || "build-reports/ios/ugc-terms-remote-evidence.json"),
};
if (!options.simulator) throw new Error("missing_environment:QUATA_IOS_SIMULATOR_UDID");
const report = { check: "UGC-TERMS-IOS-REMOTE-001", status: "failed", startedAt: new Date().toISOString(), git: await gitMetadata(), steps: [], cleanup: { attempted: false, restored: false } };
let client, fixture, localCredentials, remoteCredentials, runtimeBackup;

try {
  const credentials = (JSON.parse(await readFile(credentialsFile, "utf8"))).a;
  requireFields(credentials, ["country_code", "phone", "password"]);
  const backend = await publicConfig();
  const session = await login(backend, credentials);
  client = new pg.Client(await pgConnectionConfig()); await client.connect();
  fixture = await prepareFixture(client, session.userId);
  report.steps.push("remote_acceptance_snapshotted_and_removed");

  const remoteState = JSON.parse((await runSshScript(`cd ${quote(options.project)}; printf '{"head":"%s","dirty":%s}\n' "$(git rev-parse HEAD)" "$([ -n "$(git status --porcelain)" ] && echo true || echo false)"`)).trim());
  if (remoteState.head !== report.git.head || remoteState.dirty !== false) throw new Error("ios_mac_checkout_not_exact_clean_head");
  report.steps.push("mac_checkout_exact_and_clean");

  localCredentials = join(tmpdir(), `quata-ios-ugc-terms-${randomUUID()}.json`);
  const e164 = `+${String(credentials.country_code).replace(/\D/g, "")}${localPhone(credentials.country_code, credentials.phone)}`;
  await writeFile(localCredentials, `${JSON.stringify({ country_code: credentials.country_code, phone: e164, password: credentials.password })}\n`, { mode: 0o600 });
  remoteCredentials = (await runCapture("ssh", [options.host, "mktemp /tmp/quata-ios-ugc-terms.XXXXXX"])).trim();
  await run("scp", [localCredentials, `${options.host}:${remoteCredentials}`]);
  runtimeBackup = await prepareRuntimeConfig();

  await runSshScript(`cd ${quote(options.project)}; scripts/build-ios-intel-simulator-signed.sh`);
  report.steps.push("ios_signed_simulator_build_succeeded");
  await runSshScript(`
cd ${quote(options.project)}
export QUATA_IOS_AUTH_E2E_FILE=${quote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${quote(options.derivedData)}
export QUATA_IOS_SIMULATOR_UDID=${quote(options.simulator)}
export QUATA_IOS_UGC_TERMS_PROFILE_ID=${quote(session.userId)}
export QUATA_IOS_UGC_TERMS_UI_LOG_DIR=${quote(options.remoteLogDir)}
bash scripts/run-ios-ugc-terms-remote-ui-test.sh
`);
  const remote = await waitForAcceptance(client, session.userId);
  if (!remote) throw new Error("ios_ugc_terms_remote_persist_timeout");
  report.product = { commonGateObserved: true, acceptedThroughProductUi: true, productionGatewayPersisted: true };
  report.steps.push("product_gate_accepted", "production_gateway_persisted", "remote_row_verified");
  report.status = "passed";
} catch (error) {
  report.error = { name: error?.name || "Error", message: redact(error?.message || String(error)).slice(-1600) };
} finally {
  report.cleanup.attempted = Boolean(client && fixture);
  if (client && fixture) {
    await restoreFixture(client, fixture).catch(error => report.cleanup.error = redact(error?.message || String(error)));
    report.cleanup.restored = await verifyRestored(client, fixture).catch(() => false);
  }
  if (client) await client.end().catch(() => {});
  if (runtimeBackup) await restoreRuntimeConfig(runtimeBackup).catch(error => report.cleanup.runtimeConfigError = redact(error?.message || String(error)));
  if (remoteCredentials) await run("ssh", [options.host, "rm", "-f", remoteCredentials]).catch(() => {});
  if (localCredentials) await rm(localCredentials, { force: true }).catch(() => {});
  if (report.cleanup.attempted && !report.cleanup.restored) report.status = "failed";
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`UGC terms iOS evidence written: ${options.output}`);
}
if (report.status !== "passed") process.exitCode = 1;

async function publicConfig() { const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8"); const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, ""); const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1]; if (!url || !key) throw new Error("missing_public_supabase_configuration"); return { url, key }; }
async function login(backend, credentials) { const response = await fetch(`${backend.url}/functions/v1/quata-auth-bridge`, { method: "POST", headers: { apikey: backend.key, "content-type": "application/json", "x-client-info": "quata-ugc-terms-ios-evidence" }, body: JSON.stringify({ action: "web_login", country_code: String(credentials.country_code).replace(/\D/g, ""), phone_local: localPhone(credentials.country_code, credentials.phone), password: credentials.password, client_instance_id: `UGC-TERMS-ios-${randomUUID()}` }), signal: AbortSignal.timeout(30_000) }); const payload = JSON.parse(await response.text()); if (!response.ok || typeof payload?.profile?.id !== "string") throw new Error(`ugc_terms_login_failed:${response.status}`); return { userId: payload.profile.id }; }
function localPhone(country, phone) { const c=String(country).replace(/\D/g,""); const p=String(phone).replace(/\D/g,""); return p.startsWith(c)?p.slice(c.length):p; }
async function pgConnectionConfig() { const url=new URL((await readFile(dbUrlFile,"utf8")).trim()); for(const key of ["sslmode","sslrootcert","sslcert","sslkey"])url.searchParams.delete(key); return {connectionString:url.toString(),ssl:{ca:await readFile(dbCaFile,"utf8"),rejectUnauthorized:true,servername:url.hostname}}; }
async function readAcceptance(db,id){return (await db.query("select accepted_at from public.ugc_terms_acceptances where profile_id=$1::uuid and terms_version=$2",[id,VERSION])).rows[0]||null;}
async function prepareFixture(db,id){const row=await readAcceptance(db,id);await db.query("delete from public.ugc_terms_acceptances where profile_id=$1::uuid and terms_version=$2",[id,VERSION]);return{profileId:id,original:row?.accepted_at?.toISOString?.()||null};}
async function restoreFixture(db,state){if(state.original)await db.query("insert into public.ugc_terms_acceptances(profile_id,terms_version,accepted_at) values($1::uuid,$2,$3::timestamptz) on conflict(profile_id,terms_version) do update set accepted_at=excluded.accepted_at",[state.profileId,VERSION,state.original]);else await db.query("delete from public.ugc_terms_acceptances where profile_id=$1::uuid and terms_version=$2",[state.profileId,VERSION]);}
async function verifyRestored(db,state){const row=await readAcceptance(db,state.profileId);return state.original?row?.accepted_at?.toISOString?.()===state.original:!row;}
async function waitForAcceptance(db,id){for(let i=0;i<40;i++){const row=await readAcceptance(db,id);if(row)return row;await new Promise(resolve=>setTimeout(resolve,500));}return null;}
async function prepareRuntimeConfig(){const backup=(await runCapture("ssh",[options.host,"mktemp /tmp/quata-ios-ugc-runtime.XXXXXX"])).trim();await runSshScript(`cd ${quote(options.project)}; runtime_config=iosApp/Configuration/QuataPublicRuntime.local.xcconfig; backup_config=${quote(backup)}; QUATA_RUNTIME_CONFIG_HAD=0; QUATA_RUNTIME_CONFIG_MODE=''; source scripts/ios-public-runtime-config-backup.sh; quata_backup_runtime_config "$runtime_config" "$backup_config"; printf 'had=%s\nmode=%s\n' "$QUATA_RUNTIME_CONFIG_HAD" "$QUATA_RUNTIME_CONFIG_MODE" > "$backup_config.meta"; python3 scripts/ios-public-client-config.py --source core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt --output "$runtime_config"; chmod 600 "$runtime_config"`);return backup;}
async function restoreRuntimeConfig(backup){await runSshScript(`cd ${quote(options.project)}; runtime_config=iosApp/Configuration/QuataPublicRuntime.local.xcconfig; backup_config=${quote(backup)}; had=0; mode=''; [ ! -f "$backup_config.meta" ] || . "$backup_config.meta"; QUATA_RUNTIME_CONFIG_HAD="$had"; QUATA_RUNTIME_CONFIG_MODE="$mode"; source scripts/ios-public-runtime-config-backup.sh; quata_restore_runtime_config "$runtime_config" "$backup_config"; rm -f "$backup_config.meta"`);}
async function gitMetadata(){return{head:(await runCapture("git",["rev-parse","HEAD"])).trim(),branch:(await runCapture("git",["branch","--show-current"])).trim(),workingTreeDirty:(await runCapture("git",["status","--porcelain"])).trim().length>0};}
function requireFields(value,fields){for(const field of fields)if(!value?.[field])throw new Error(`credentials_missing:a.${field}`);}
function quote(value){return `'${String(value).replace(/'/g,"'\\''")}'`;}
function redact(value){return String(value).replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi,"$1[REDACTED]").replace(/\b\d{7,}\b/g,"[digits]");}
function runSshScript(script){return runCapture("ssh",[options.host,"bash","-s"],{input:script});}
function run(command,args){return runCapture(command,args).then(()=>undefined);}
function runCapture(command,args,{input=null}={}){return new Promise((resolvePromise,reject)=>{const child=spawn(command,args,{stdio:["pipe","pipe","pipe"],shell:process.platform==="win32"});let stdout="",stderr="";child.stdout.on("data",c=>stdout+=c);child.stderr.on("data",c=>stderr+=c);child.on("close",code=>code===0?resolvePromise(stdout):reject(new Error(`${command} failed:${code}\n${redact(stderr||stdout)}`)));if(input)child.stdin.end(input);else child.stdin.end();});}
