#!/usr/bin/env node
/** SB-02: public-key Auth/session contract for one externally provisioned E2E user. */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const REQUIRED_ENV = ["QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY", "QUATA_E2E_COUNTRY_CODE", "QUATA_E2E_PHONE", "QUATA_E2E_PASSWORD"];

function parseArgs(argv) {
  if (argv.length === 2 && argv[0] === "--out" && argv[1].trim()) return { output: argv[1] };
  if (argv.length === 1 && argv[0] === "--help") { console.log("Usage: node scripts/supabase-e2e-sb02.mjs --out <safe-local-report.json>"); process.exit(0); }
  throw new Error("invalid_arguments");
}

function requireEnvironment() {
  const missing = REQUIRED_ENV.filter((name) => !process.env[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  const baseUrl = process.env.QUATA_SUPABASE_URL.trim().replace(/\/+$/, "");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("invalid_public_supabase_url");
  return { baseUrl, publishableKey: process.env.QUATA_SUPABASE_PUBLISHABLE_KEY.trim(), countryCode: process.env.QUATA_E2E_COUNTRY_CODE.trim(), phone: process.env.QUATA_E2E_PHONE.trim(), password: process.env.QUATA_E2E_PASSWORD };
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "unknown";
  const code = ["invalid_arguments", "invalid_public_supabase_url", "public_auth_request_failed", "invalid_auth_response", "missing_environment"].find((item) => message.startsWith(item)) ?? "unexpected_auth_runner_failure";
  return { status: "failed", error: code };
}

async function post(url, headers, body) {
  let response;
  try { response = await fetch(url, { method: "POST", headers, body: JSON.stringify(body), signal: AbortSignal.timeout(15_000) }); }
  catch { throw new Error("public_auth_request_failed:network"); }
  if (!response.ok) throw new Error(`public_auth_request_failed:http_${response.status}`);
  try { return await response.json(); } catch { throw new Error("invalid_auth_response:json"); }
}

function sessionFrom(payload, webSessionToken = null) {
  const session = payload?.session ?? payload;
  const { access_token: accessToken, refresh_token: refreshToken, expires_at: expiresAt } = session ?? {};
  if (typeof accessToken !== "string" || !accessToken || typeof refreshToken !== "string" || !refreshToken || !Number.isFinite(expiresAt)) throw new Error("invalid_auth_response:session");
  const token = webSessionToken ?? payload?.web_session?.token;
  if (typeof token !== "string" || !token) throw new Error("invalid_auth_response:web_session");
  return { accessToken, refreshToken, expiresAt, webSessionToken: token };
}

function persistenceShape(session) {
  // Mirrors WebAuthStorage fields without writing a bearer token to disk.
  const restored = JSON.parse(JSON.stringify({ accessToken: session.accessToken, refreshToken: session.refreshToken, expiresAt: session.expiresAt }));
  if (typeof restored.accessToken !== "string" || typeof restored.refreshToken !== "string" || !Number.isFinite(restored.expiresAt)) throw new Error("invalid_auth_response:persistence");
  return "memory_serialization_verified";
}

function commonHeaders(key) { return { apikey: key, "content-type": "application/json", "x-client-info": "quata-e2e-sb02" }; }
function bridgeLogin(config) {
  return post(`${config.baseUrl}/functions/v1/quata-auth-bridge`, commonHeaders(config.publishableKey), {
    action: "web_login", country_code: config.countryCode, phone_local: config.phone, password: config.password, client_instance_id: `e2e-sb02-${crypto.randomUUID()}`,
  }).then((payload) => sessionFrom(payload));
}
function refresh(config, session) {
  return post(`${config.baseUrl}/auth/v1/token?grant_type=refresh_token`, commonHeaders(config.publishableKey), { refresh_token: session.refreshToken })
    .then((payload) => sessionFrom(payload, session.webSessionToken));
}
function webLogout(config, session) {
  return post(`${config.baseUrl}/functions/v1/quata-web-push`, {
    ...commonHeaders(config.publishableKey),
    authorization: `Bearer ${session.accessToken}`,
    "x-quata-web-session": session.webSessionToken,
  }, { action: "logout" });
}
async function revokeSessions(config, session) {
  let response;
  try {
    response = await fetch(`${config.baseUrl}/auth/v1/logout`, {
      method: "POST",
      headers: { ...commonHeaders(config.publishableKey), authorization: `Bearer ${session.accessToken}` },
      body: JSON.stringify({ scope: "global" }),
      signal: AbortSignal.timeout(15_000),
    });
  } catch { throw new Error("public_auth_request_failed:network"); }
  if (!response.ok) throw new Error(`public_auth_request_failed:http_${response.status}`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const startedAt = new Date().toISOString();
  let config; let activeSession = null; let cleanup = { state: "not_needed" }; const steps = [];
  try {
    config = requireEnvironment();
    activeSession = await bridgeLogin(config); steps.push("login");
    const persistence = persistenceShape(activeSession); steps.push("session_persistence_shape");
    activeSession = await refresh(config, activeSession); steps.push("refresh");
    await webLogout(config, activeSession); activeSession = null; steps.push("web_logout");
    activeSession = await bridgeLogin(config); steps.push("login_after_logout");
    await webLogout(config, activeSession); steps.push("final_web_logout");
    await revokeSessions(config, activeSession); activeSession = null; steps.push("final_session_revocation"); cleanup = { state: "sessions_revoked" };
    const report = { check: "SB-02", status: "passed", startedAt, finishedAt: new Date().toISOString(), mode: "public_key_existing_isolated_user", steps, persistence, cleanup, mutationPolicy: "Uses only the public key. It creates no user or business data; it globally revokes sessions belonging to the approved isolated user." };
    const output = resolve(args.output); await mkdir(dirname(output), { recursive: true }); await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 }); console.log(`SB-02 report written: ${output}`);
  } catch (error) {
    if (config && activeSession) {
      try { await revokeSessions(config, activeSession); cleanup = { state: "sessions_revoked_after_failure" }; }
      catch { cleanup = { state: "rollback_pending", action: "revoke_sessions_for_the_isolated_e2e_user" }; }
    }
    console.error(JSON.stringify({ check: "SB-02", startedAt, finishedAt: new Date().toISOString(), cleanup, ...safeFailure(error) })); process.exitCode = 1;
  }
}
main();
