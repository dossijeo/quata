#!/usr/bin/env node
import { createHmac } from "node:crypto";

const [phase] = process.argv.slice(2);
const baseUrl = process.env.OFFICIAL_LIKES_POSTGREST_URL;
const jwtSecret = process.env.OFFICIAL_LIKES_POSTGREST_JWT_SECRET;
if (!baseUrl || !jwtSecret || !["baseline", "secured"].includes(phase)) {
  throw new Error("usage: OFFICIAL_LIKES_POSTGREST_URL=... OFFICIAL_LIKES_POSTGREST_JWT_SECRET=... node scripts/official-likes-rls-postgrest-contract.mjs <baseline|secured>");
}

const ids = {
  profileA: "10000000-0000-4000-8000-000000000001",
  profileB: "10000000-0000-4000-8000-000000000002",
  authA: "20000000-0000-4000-8000-000000000001",
  authB: "20000000-0000-4000-8000-000000000002",
  post: "30000000-0000-4000-8000-000000000001",
};
const b64url = (value) => Buffer.from(value).toString("base64url");
function token(sub) {
  const header = b64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64url(JSON.stringify({ role: "authenticated", sub, exp: Math.floor(Date.now() / 1000) + 300 }));
  return `${header}.${payload}.${createHmac("sha256", jwtSecret).update(`${header}.${payload}`).digest("base64url")}`;
}
async function request(path, { method = "GET", actor, body } = {}) {
  const response = await fetch(new URL(path, baseUrl), {
    method,
    headers: {
      ...(actor ? { Authorization: `Bearer ${token(actor)}` } : {}),
      ...(body ? { "content-type": "application/json", Prefer: "return=representation" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await response.text();
  let json;
  try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  return { response, json };
}
function requireOk(result, label) {
  if (!result.response.ok) throw new Error(`${label}:http_${result.response.status}:${JSON.stringify(result.json)}`);
}
function requireDenied(result, label) {
  if (result.response.ok || result.json?.code !== "42501") {
    throw new Error(`${label}:expected_42501_got_${result.response.status}:${JSON.stringify(result.json)}`);
  }
}
async function create(actor, profile, label) {
  const result = await request("official_post_likes", { method: "POST", actor, body: { official_post_id: ids.post, profile_id: profile } });
  requireOk(result, label);
  const id = result.json?.[0]?.id;
  if (!id) throw new Error(`${label}:missing_id`);
  return id;
}
async function remove(actor, id, label) {
  const result = await request(`official_post_likes?id=eq.${id}`, { method: "DELETE", actor });
  requireOk(result, label);
}

const anonymous = await request("official_post_likes?select=id&id=eq.40000000-0000-4000-8000-000000000009");
requireOk(anonymous, `${phase}_anonymous_read`);
if (!Array.isArray(anonymous.json) || anonymous.json.length !== 1) throw new Error(`${phase}_anonymous_read:existing_like_hidden`);

if (phase === "baseline") {
  const spoofId = await create(ids.authA, ids.profileB, "baseline_spoof_is_accepted");
  await remove(ids.authA, spoofId, "baseline_spoof_cleanup");
  console.log("PostgREST baseline contract confirmed: historical spoof remains reproducible.");
} else {
  const ownA = await create(ids.authA, ids.profileA, "secured_a_own_like");
  const spoof = await request("official_post_likes", { method: "POST", actor: ids.authA, body: { official_post_id: ids.post, profile_id: ids.profileB } });
  requireDenied(spoof, "secured_spoof_blocked");
  const ownB = await create(ids.authB, ids.profileB, "secured_b_own_like");
  const crossDelete = await request(`official_post_likes?id=eq.${ownB}`, { method: "DELETE", actor: ids.authA });
  requireDenied(crossDelete, "secured_cross_delete_blocked");
  await remove(ids.authA, ownA, "secured_a_cleanup");
  await remove(ids.authB, ownB, "secured_b_cleanup");
  console.log("PostgREST secured contract confirmed: spoof and cross-delete return 42501.");
}
