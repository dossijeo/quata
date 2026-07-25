#!/usr/bin/env node
/**
 * SB-03: public-key, read-only Feed/Official contract check.
 *
 * The runner deliberately does not create, update or delete posts. The approved
 * E2E rows and their visibility contract are supplied by the operator through
 * environment variables, never command-line arguments. This keeps a missing
 * creation/deletion API visible as a prerequisite instead of inventing one.
 */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const REQUIRED_ENV = [
  "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_COUNTRY_CODE", "QUATA_E2E_PHONE", "QUATA_E2E_PASSWORD",
  "QUATA_E2E_FEED_POST_ID", "QUATA_E2E_FEED_PUBLIC_EXPECTED",
  "QUATA_E2E_OFFICIAL_POST_ID", "QUATA_E2E_OFFICIAL_PUBLIC_EXPECTED",
];
const identifier = /^[A-Za-z0-9_-]+$/;
const visibility = new Set(["visible", "denied"]);

function parseArgs(argv) {
  if (argv.length === 2 && argv[0] === "--out" && argv[1].trim()) return { output: argv[1] };
  if (argv.length === 1 && argv[0] === "--help") {
    console.log("Usage: node scripts/supabase-e2e-sb03.mjs --out <safe-local-report.json>");
    process.exit(0);
  }
  throw new Error("invalid_arguments");
}

function configuration() {
  const missing = REQUIRED_ENV.filter((name) => !process.env[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  const baseUrl = process.env.QUATA_SUPABASE_URL.trim().replace(/\/+$/, "");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("invalid_public_supabase_url");
  const feedId = process.env.QUATA_E2E_FEED_POST_ID.trim();
  const officialId = process.env.QUATA_E2E_OFFICIAL_POST_ID.trim();
  if (!identifier.test(feedId) || !identifier.test(officialId)) throw new Error("invalid_e2e_post_identifier");
  const feedPublic = process.env.QUATA_E2E_FEED_PUBLIC_EXPECTED.trim().toLowerCase();
  const officialPublic = process.env.QUATA_E2E_OFFICIAL_PUBLIC_EXPECTED.trim().toLowerCase();
  if (!visibility.has(feedPublic) || !visibility.has(officialPublic)) throw new Error("invalid_public_visibility_contract");
  return {
    baseUrl,
    key: process.env.QUATA_SUPABASE_PUBLISHABLE_KEY.trim(),
    countryCode: process.env.QUATA_E2E_COUNTRY_CODE.trim(),
    phone: process.env.QUATA_E2E_PHONE.trim(),
    password: process.env.QUATA_E2E_PASSWORD,
    targets: [
      {
        name: "feed", table: "community_posts", id: feedId, publicExpected: feedPublic,
        select: "id,wall_id,profile_id,body,image_url,video_url,created_at,community_id,author_id,content",
        fragmentPrefix: "post-",
      },
      {
        name: "official", table: "official_posts", id: officialId, publicExpected: officialPublic,
        select: "id,profile_id,title,summary,post_type,content_html,read_more_label,language,translation_group_id,media_url,media_type,link_url,is_live,published_at,created_at",
        fragmentPrefix: "official-",
      },
    ],
  };
}

function commonHeaders(key) {
  return { apikey: key, "content-type": "application/json", "x-client-info": "quata-e2e-sb03" };
}

async function jsonPost(url, headers, body) {
  let response;
  try { response = await fetch(url, { method: "POST", headers, body: JSON.stringify(body), signal: AbortSignal.timeout(15_000) }); }
  catch { throw new Error("public_auth_request_failed:network"); }
  if (!response.ok) throw new Error(`public_auth_request_failed:http_${response.status}`);
  try { return await response.json(); } catch { throw new Error("invalid_auth_response:json"); }
}

async function login(config) {
  const payload = await jsonPost(`${config.baseUrl}/functions/v1/quata-auth-bridge`, commonHeaders(config.key), {
    action: "web_login", country_code: config.countryCode, phone_local: config.phone,
    password: config.password, client_instance_id: `e2e-sb03-${crypto.randomUUID()}`,
  });
  const session = payload?.session ?? payload;
  if (typeof session?.access_token !== "string" || !session.access_token) throw new Error("invalid_auth_response:session");
  return session.access_token;
}

async function logout(config, accessToken) {
  let response;
  try {
    response = await fetch(`${config.baseUrl}/auth/v1/logout`, {
      method: "POST", headers: { ...commonHeaders(config.key), authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({ scope: "global" }), signal: AbortSignal.timeout(15_000),
    });
  } catch { throw new Error("public_auth_request_failed:network"); }
  if (!response.ok) throw new Error(`public_auth_request_failed:http_${response.status}`);
}

async function readTarget(config, target, accessToken = null) {
  const url = new URL(`${config.baseUrl}/rest/v1/${target.table}`);
  url.searchParams.set("select", target.select);
  url.searchParams.set("id", `eq.${target.id}`);
  url.searchParams.set("limit", "1");
  const headers = { apikey: config.key, accept: "application/json", "x-client-info": "quata-e2e-sb03" };
  if (accessToken) headers.authorization = `Bearer ${accessToken}`;
  let response;
  try { response = await fetch(url, { headers, signal: AbortSignal.timeout(15_000) }); }
  catch { throw new Error(`postgrest_read_failed:${target.name}:network`); }
  const body = await response.text();
  if (!response.ok) return { kind: "http_failure", status: response.status };
  let rows;
  try { rows = JSON.parse(body); } catch { throw new Error(`postgrest_read_failed:${target.name}:invalid_json`); }
  if (!Array.isArray(rows)) throw new Error(`postgrest_read_failed:${target.name}:not_array`);
  if (rows.length > 1 || (rows.length === 1 && rows[0]?.id !== target.id)) throw new Error(`postgrest_read_failed:${target.name}:unexpected_row`);
  return { kind: rows.length === 1 ? "visible" : "denied", status: response.status };
}

function assertDeepLink(target) {
  const url = `https://egquata.com/#${target.fragmentPrefix}${target.id}`;
  const fragment = new URL(url).hash.slice(1);
  if (fragment !== `${target.fragmentPrefix}${target.id}`) throw new Error(`deep_link_contract_failed:${target.name}`);
  return "shared_fragment_shape_verified";
}

function expectedPublicResult(result, target) {
  if (result.kind === "http_failure") throw new Error(`public_read_failed:${target.name}:http_${result.status}`);
  if (result.kind !== target.publicExpected) throw new Error(`public_visibility_contract_failed:${target.name}`);
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "unknown";
  const known = ["invalid_arguments", "missing_environment", "invalid_public_supabase_url", "invalid_e2e_post_identifier", "invalid_public_visibility_contract", "public_auth_request_failed", "invalid_auth_response", "postgrest_read_failed", "authenticated_read_failed", "public_read_failed", "public_visibility_contract_failed", "deep_link_contract_failed"];
  return { status: "failed", error: known.find((prefix) => message.startsWith(prefix)) ?? "unexpected_feed_official_runner_failure" };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const startedAt = new Date().toISOString();
  let config; let accessToken = null; let cleanup = { state: "not_needed" }; const steps = [];
  try {
    config = configuration();
    accessToken = await login(config); steps.push("isolated_user_login");
    const results = [];
    for (const target of config.targets) {
      const authenticated = await readTarget(config, target, accessToken);
      if (authenticated.kind !== "visible") throw new Error(`authenticated_read_failed:${target.name}`);
      steps.push(`${target.name}_authenticated_read`);
      const publicRead = await readTarget(config, target);
      expectedPublicResult(publicRead, target); steps.push(`${target.name}_public_${target.publicExpected}`);
      results.push({ target: target.name, authenticated: authenticated.kind, public: publicRead.kind, deepLink: assertDeepLink(target) });
      steps.push(`${target.name}_deep_link_shape`);
    }
    await logout(config, accessToken); accessToken = null; cleanup = { state: "sessions_revoked" }; steps.push("final_session_revocation");
    const report = {
      check: "SB-03", status: "passed", startedAt, finishedAt: new Date().toISOString(),
      mode: "public_key_read_only_existing_approved_rows", steps, results, cleanup,
      mutationPolicy: "No DDL, RPC, storage or business-data mutation. The runner creates no post and deletes no post; the operator owns creation and cleanup of the approved isolated rows.",
      missingContract: "A safe public Feed/Official create/delete endpoint is not implemented. SB-03 therefore requires pre-provisioned isolated rows and records their public visibility explicitly.",
    };
    const output = resolve(args.output); await mkdir(dirname(output), { recursive: true });
    await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
    console.log(`SB-03 report written: ${output}`);
  } catch (error) {
    if (config && accessToken) {
      try { await logout(config, accessToken); cleanup = { state: "sessions_revoked_after_failure" }; }
      catch { cleanup = { state: "rollback_pending", action: "revoke_sessions_for_the_isolated_e2e_user" }; }
    }
    console.error(JSON.stringify({ check: "SB-03", startedAt, finishedAt: new Date().toISOString(), cleanup, ...safeFailure(error) }));
    process.exitCode = 1;
  }
}
main();
