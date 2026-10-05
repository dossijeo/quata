import { createHash, createHmac, randomBytes, randomUUID, timingSafeEqual } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import pg from "pg";

// Opt-in local evidence harness for periods when the hosted Auth service is unavailable.
// The JSON config is intentionally external and must provide: dbUrlFile, dbCaFile,
// jwtSecret, controlSecret, allowedOrigins, postgrestUrl, receiptPath and port. Never
// commit that config or either secret.
const configPath = process.env.QUATA_LOCAL_FACADE_CONFIG;
if (!configPath) throw new Error("missing_local_facade_config");
const config = JSON.parse(await readFile(configPath, "utf8"));
if (typeof config.jwtSecret !== "string" || config.jwtSecret.length < 32) {
  throw new Error("invalid_local_facade_jwt_secret");
}
if (typeof config.controlSecret !== "string" || config.controlSecret.length < 32) {
  throw new Error("invalid_local_facade_control_secret");
}
if (!Array.isArray(config.allowedOrigins) || config.allowedOrigins.length !== 1) {
  throw new Error("missing_local_facade_allowed_origins");
}
const allowedOrigins = new Set(config.allowedOrigins.map((value) => {
  const parsed = new URL(String(value));
  if (
    parsed.protocol !== "http:" ||
    !(parsed.hostname === "127.0.0.1" || parsed.hostname === "localhost") ||
    parsed.origin !== String(value)
  ) {
    throw new Error("local_facade_origin_must_be_exact_loopback_origin");
  }
  return parsed.origin;
}));
const postgrestEndpoint = new URL(config.postgrestUrl);
if (!(postgrestEndpoint.hostname === "127.0.0.1" || postgrestEndpoint.hostname === "localhost")) {
  throw new Error("local_facade_postgrest_must_be_loopback");
}
const parsedConnection = new URL((await readFile(config.dbUrlFile, "utf8")).trim());
parsedConnection.searchParams.delete("sslmode");
const ca = await readFile(config.dbCaFile, "utf8");
const client = new pg.Client({
  connectionString: parsedConnection.toString(),
  ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
});
await client.connect();

const createdWebSessionIds = new Set();
const receipt = {
  version: 1,
  status: "running",
  startedAt: new Date().toISOString(),
  authLogins: 0,
  proxiedRestRequests: 0,
  createdWebSessions: 0,
  removedWebSessions: 0,
  cleanupVerified: false,
};

const base64url = (value) => Buffer.from(value).toString("base64url");
const sha256 = (value) => createHash("sha256").update(value).digest("hex");

function jwtFor(authUserId) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64url(JSON.stringify({
    aud: "authenticated",
    exp: now + 7200,
    iat: now,
    iss: "supabase",
    role: "authenticated",
    sub: authUserId,
    session_id: randomUUID(),
    aal: "aal1",
  }));
  const signature = createHmac("sha256", config.jwtSecret).update(`${header}.${payload}`).digest("base64url");
  return { accessToken: `${header}.${payload}.${signature}`, expiresAt: now + 7200 };
}

function digits(value) {
  return String(value ?? "").replace(/\D/g, "");
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
}

function corsOrigin(request) {
  const origin = request.headers.origin;
  return typeof origin === "string" && allowedOrigins.has(origin) ? origin : null;
}

function json(request, response, status, body) {
  const headers = {
    "content-type": "application/json",
  };
  const origin = corsOrigin(request);
  if (origin) {
    headers["access-control-allow-origin"] = origin;
    headers.vary = "Origin";
  }
  response.writeHead(status, headers);
  response.end(JSON.stringify(body));
}

async function login(request, response) {
  const payload = await readJson(request);
  if (payload.action !== "web_login") return json(request, response, 400, { error: "unsupported_action" });
  const countryCode = digits(payload.country_code);
  const phone = digits(payload.phone_local ?? payload.phone);
  const password = String(payload.password ?? "");
  const profiles = await client.query(`
    select id, auth_user_id, display_name, neighborhood, barrio, avatar_url, avatar,
           phone_local, phone_normalized, telefono, country_code, code, is_official,
           pass_hash, account_status
      from public.community_profiles
     where regexp_replace(coalesce(country_code, code, ''), '[^0-9]', '', 'g') = $1
       and regexp_replace(coalesce(phone_local, phone_normalized, telefono, ''), '[^0-9]', '', 'g') = $2
     limit 2
  `, [countryCode, phone]);
  if (profiles.rows.length !== 1) return json(request, response, 401, { error: "invalid_credentials" });
  const profile = profiles.rows[0];
  const passwordMatches = profile.pass_hash === sha256(password);
  if (!passwordMatches || profile.account_status !== "active" || !profile.auth_user_id) {
    return json(request, response, 401, { error: "invalid_credentials" });
  }
  const clientInstanceId = String(payload.client_instance_id ?? "").trim();
  if (clientInstanceId.length < 8 || clientInstanceId.length > 200) {
    return json(request, response, 400, { error: "invalid_client_instance_id" });
  }
  const webToken = randomBytes(32).toString("base64url");
  const webSession = await client.query(`
    insert into public.web_client_sessions(
      profile_id, auth_user_id, client_instance_id, token_hash, updated_at, last_seen_at, revoked_at
    ) values ($1, $2, $3, $4, now(), now(), null)
    on conflict (profile_id, client_instance_id) do nothing
    returning id
  `, [profile.id, profile.auth_user_id, clientInstanceId, sha256(webToken)]);
  if (webSession.rowCount !== 1) throw new Error("local_facade_client_session_conflict");
  createdWebSessionIds.add(String(webSession.rows[0].id));
  const session = jwtFor(profile.auth_user_id);
  receipt.authLogins += 1;
  receipt.createdWebSessions = createdWebSessionIds.size;
  return json(request, response, 200, {
    version: 1,
    profile: {
      id: profile.id,
      auth_user_id: profile.auth_user_id,
      display_name: profile.display_name,
      neighborhood: profile.neighborhood ?? profile.barrio ?? null,
      avatar_url: profile.avatar_url ?? profile.avatar ?? null,
      phone_local: profile.phone_local ?? profile.phone_normalized ?? profile.telefono ?? null,
      country_code: profile.country_code ?? profile.code ?? null,
      is_official: profile.is_official === true,
    },
    session: {
      access_token: session.accessToken,
      refresh_token: randomBytes(32).toString("base64url"),
      expires_at: session.expiresAt,
      expires_in: 7200,
      token_type: "bearer",
    },
    user: { id: profile.auth_user_id },
    client_type: "web",
    web_session: { id: webSession.rows[0].id, token: webToken },
  });
}

async function proxyRest(request, response, requestUrl) {
  const upstreamPath = requestUrl.pathname.replace(/^\/rest\/v1/, "") || "/";
  const target = new URL(`${config.postgrestUrl}${upstreamPath}${requestUrl.search}`);
  const headers = {};
  for (const [key, value] of Object.entries(request.headers)) {
    if (value !== undefined && !["host", "connection", "content-length"].includes(key.toLowerCase())) headers[key] = value;
  }
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const body = chunks.length ? Buffer.concat(chunks) : undefined;
  const upstream = await fetch(target, { method: request.method, headers, body });
  const responseHeaders = {};
  for (const [key, value] of upstream.headers.entries()) {
    if (!["connection", "transfer-encoding", "content-encoding", "content-length"].includes(key.toLowerCase())) responseHeaders[key] = value;
  }
  const origin = corsOrigin(request);
  if (origin) {
    responseHeaders["access-control-allow-origin"] = origin;
    responseHeaders.vary = "Origin";
  }
  receipt.proxiedRestRequests += 1;
  response.writeHead(upstream.status, responseHeaders);
  response.end(Buffer.from(await upstream.arrayBuffer()));
}

const server = createServer(async (request, response) => {
  try {
    const requestUrl = new URL(request.url ?? "/", `http://127.0.0.1:${config.port}`);
    const requestOrigin = request.headers.origin;
    if (requestOrigin !== undefined && (typeof requestOrigin !== "string" || !allowedOrigins.has(requestOrigin))) {
      return json(request, response, 403, { error: "origin_not_allowed" });
    }
    if (request.method === "OPTIONS") {
      const origin = corsOrigin(request);
      if (!origin) return json(request, response, 403, { error: "origin_not_allowed" });
      response.writeHead(204, {
        "access-control-allow-origin": origin,
        "access-control-allow-headers": "authorization,apikey,content-type,x-client-info,prefer,range",
        "access-control-allow-methods": "GET,POST,PATCH,PUT,DELETE,OPTIONS",
        vary: "Origin",
      });
      return response.end();
    }
    if (requestUrl.pathname === "/__health") return json(request, response, 200, { ok: true });
    if (requestUrl.pathname === "/__shutdown" && request.method === "POST") {
      const supplied = String(request.headers["x-quata-facade-control"] ?? "");
      const expectedHash = createHash("sha256").update(config.controlSecret).digest();
      const suppliedHash = createHash("sha256").update(supplied).digest();
      if (!timingSafeEqual(expectedHash, suppliedHash)) {
        return json(request, response, 403, { error: "invalid_control_secret" });
      }
      json(request, response, 202, { stopping: true });
      setImmediate(() => void stop());
      return;
    }
    if (requestUrl.pathname === "/functions/v1/quata-auth-bridge") return await login(request, response);
    if (requestUrl.pathname.startsWith("/rest/v1/")) return await proxyRest(request, response, requestUrl);
    return json(request, response, 404, { error: "route_not_supported" });
  } catch (error) {
    return json(request, response, 500, { error: "local_facade_failure", code: error?.code ?? null });
  }
});

let stopping = false;
async function stop() {
  if (stopping) return;
  stopping = true;
  await new Promise((resolve) => server.close(resolve));
  let removed = 0;
  for (const id of createdWebSessionIds) {
    const result = await client.query("delete from public.web_client_sessions where id = $1 returning id", [id]);
    removed += result.rowCount;
  }
  receipt.removedWebSessions = removed;
  receipt.cleanupVerified = removed === createdWebSessionIds.size;
  receipt.status = receipt.cleanupVerified ? "completed" : "cleanup_failed";
  receipt.finishedAt = new Date().toISOString();
  await writeFile(config.receiptPath, `${JSON.stringify(receipt, null, 2)}\n`, "utf8");
  await client.end();
  process.exit(receipt.cleanupVerified ? 0 : 1);
}

process.on("SIGINT", () => void stop());
process.on("SIGTERM", () => void stop());
await new Promise((resolve, reject) => server.listen(config.port, "127.0.0.1", (error) => error ? reject(error) : resolve()));
console.log(JSON.stringify({ ok: true, port: config.port }));
