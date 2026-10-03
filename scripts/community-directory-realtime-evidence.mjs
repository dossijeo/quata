#!/usr/bin/env node

import { createHash, randomUUID } from "node:crypto";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { createInterface } from "node:readline";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { buildCommunityRealtimeSuccessReport } from "./community-directory-realtime-report.mjs";

const require = createRequire(import.meta.url);
const { chromium } = require("playwright-core");
const publicTables = ["community_profiles", "community_walls", "community_members", "community_posts"];
const authenticatedTables = [...publicTables, "community_messages"];
const hash = (value) => createHash("sha256").update(String(value)).digest("hex");
let evidenceOptions;
let evidenceState;

function args(argv) {
  const value = {
    credentialsFile: process.env.QUATA_CHAT_GROUP_CREDENTIALS_FILE,
    dbUrlFile: process.env.QUATA_SUPABASE_DB_URL_FILE,
    out: "build-reports/community-directory-realtime-evidence.json",
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    publicOnly: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--credentials-file") value.credentialsFile = argv[++index];
    else if (arg === "--db-url-file") value.dbUrlFile = argv[++index];
    else if (arg === "--out") value.out = argv[++index];
    else if (arg === "--chrome") value.chrome = argv[++index];
    else if (arg === "--public-only") value.publicOnly = true;
    else throw new Error(`unknown_argument:${arg}`);
  }
  if ((!value.publicOnly && !value.credentialsFile) || !value.dbUrlFile) throw new Error("community_realtime_private_inputs_missing");
  return { ...value, credentialsFile: value.credentialsFile ? resolve(value.credentialsFile) : null, dbUrlFile: resolve(value.dbUrlFile), out: resolve(value.out) };
}

async function config() {
  const source = await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8");
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const publishableKey = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !publishableKey || publishableKey.startsWith("sb_secret_")) throw new Error("community_realtime_public_config_missing");
  return { baseUrl, publishableKey };
}

async function assertRealtimeQuotaAvailable(configuration) {
  const response = await fetch(
    `${configuration.baseUrl}/realtime/v1/websocket?apikey=${encodeURIComponent(configuration.publishableKey)}&vsn=2.0.0`,
    { redirect: "manual", signal: AbortSignal.timeout(20_000) },
  );
  const status = response.status;
  await response.body?.cancel();
  if (status === 402) throw new Error("community_realtime_external_limit:http_402_exceed_egress_quota");
}

async function credentials(path) {
  const parsed = JSON.parse((await readFile(path, "utf8")).replace(/^\uFEFF/, ""));
  const entry = parsed?.a;
  const value = {
    countryCode: String(entry?.country_code ?? "").trim(),
    phone: String(entry?.phone ?? "").trim(),
    password: String(entry?.password ?? ""),
  };
  if (!value.countryCode || !value.phone || !value.password) throw new Error("community_realtime_credentials_invalid");
  return value;
}

function headers(configuration, token) {
  return {
    apikey: configuration.publishableKey,
    "content-type": "application/json",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
}

async function requestJson(url, init, label) {
  const response = await fetch(url, { ...init, signal: AbortSignal.timeout(20_000) });
  const text = await response.text();
  if (!response.ok) throw new Error(`${label}:http_${response.status}`);
  return text ? JSON.parse(text) : {};
}

async function login(configuration, user, takeCustody) {
  try {
    const body = await requestJson(`${configuration.baseUrl}/functions/v1/quata-auth-bridge`, {
      method: "POST",
      headers: headers(configuration),
      body: JSON.stringify({
        action: "web_login",
        country_code: user.countryCode,
        phone_local: user.phone,
        password: user.password,
        client_instance_id: `community-realtime-${randomUUID()}`,
      }),
    }, "community_realtime_login");
    if (body?.session?.access_token) {
      takeCustody({
        accessToken: body.session.access_token,
        profileId: body?.profile?.id ?? null,
        webSessionToken: body?.web_session?.token ?? null,
        source: "auth_bridge",
      });
    }
    if (!body?.session?.access_token || !body?.profile?.id || !body?.web_session?.token) {
      throw new Error("community_realtime_login_invalid_response");
    }
    return { accessToken: body.session.access_token, profileId: body.profile.id, webSessionToken: body.web_session.token, source: "auth_bridge" };
  } catch (error) {
    if (!(error instanceof Error) || error.message !== "community_realtime_login:http_402") throw error;
  }
  const digits = (value) => String(value).replace(/\D/g, "");
  const auth = await requestJson(`${configuration.baseUrl}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: headers(configuration),
    body: JSON.stringify({ email: `${digits(user.countryCode)}${digits(user.phone)}@phone.quata.app`, password: user.password }),
  }, "community_realtime_direct_auth");
  if (!auth?.access_token) throw new Error("community_realtime_direct_auth_invalid_response");
  takeCustody({ accessToken: auth.access_token, profileId: null, webSessionToken: null, source: "auth_password_fallback" });
  const profileId = await requestJson(`${configuration.baseUrl}/rest/v1/rpc/quata_chat_auth_profile_id`, {
    method: "POST",
    headers: headers(configuration, auth.access_token),
    body: "{}",
  }, "community_realtime_profile_resolver");
  if (typeof profileId !== "string" || !profileId) throw new Error("community_realtime_profile_resolver_invalid_response");
  return { accessToken: auth.access_token, profileId, webSessionToken: null, source: "auth_password_fallback" };
}

async function logout(configuration, session) {
  if (!session) return;
  const failures = [];
  if (session.webSessionToken) {
    try {
      const response = await fetch(`${configuration.baseUrl}/functions/v1/quata-web-push`, {
        method: "POST",
        headers: { ...headers(configuration, session.accessToken), "x-quata-web-session": session.webSessionToken },
        body: JSON.stringify({ action: "logout" }),
        signal: AbortSignal.timeout(20_000),
      });
      if (!response.ok && response.status !== 401) throw new Error(`community_realtime_web_logout:http_${response.status}`);
    } catch (error) {
      failures.push(error instanceof Error ? error.message : "community_realtime_web_logout_failed");
    }
  }
  try {
    const response = await fetch(`${configuration.baseUrl}/auth/v1/logout?scope=local`, {
      method: "POST", headers: headers(configuration, session.accessToken), body: "{}", signal: AbortSignal.timeout(20_000),
    });
    if (!response.ok && response.status !== 401) throw new Error(`community_realtime_auth_logout:http_${response.status}`);
  } catch (error) {
    failures.push(error instanceof Error ? error.message : "community_realtime_auth_logout_failed");
  }
  if (failures.length) throw new Error(`community_realtime_logout_incomplete:${failures.join(",")}`);
}

async function connect(page, configuration, accessToken, tables) {
  await page.goto("about:blank");
  await page.evaluate(({ baseUrl, publishableKey, accessToken, tables }) => {
    const state = { joined: false, failure: null, events: [], ref: 0, joinRef: null };
    const topic = "realtime:public";
    const url = `${baseUrl.replace(/^https:/, "wss:").replace(/^http:/, "ws:")}/realtime/v1/websocket?apikey=${encodeURIComponent(publishableKey)}&vsn=2.0.0`;
    const socket = new WebSocket(url);
    const nextRef = () => String(++state.ref);
    socket.onopen = () => {
      state.joinRef = nextRef();
      const payload = { config: { broadcast: { ack: false, self: false }, presence: { enabled: false }, postgres_changes: tables.map((table) => ({ event: "*", schema: "public", table })), private: false } };
      if (accessToken) payload.access_token = accessToken;
      socket.send(JSON.stringify([null, state.joinRef, topic, "phx_join", payload]));
    };
    socket.onmessage = (message) => {
      let frame;
      try { frame = JSON.parse(String(message.data ?? "")); } catch { return; }
      if (!Array.isArray(frame) || frame.length < 5 || frame[2] !== topic) return;
      const [, ref, , event, payload] = frame;
      if (event === "phx_reply" && ref === state.joinRef) {
        if (payload?.status === "ok") state.joined = true;
        else state.failure = "join_failed";
        return;
      }
      if (event !== "postgres_changes") return;
      const data = payload?.data ?? payload;
      state.events.push({ table: data?.table ?? null, id: data?.record?.id ?? data?.old_record?.id ?? null });
    };
    socket.onerror = () => { state.failure = state.failure ?? "socket_error"; };
    socket.onclose = () => { if (!state.failure && !state.joined) state.failure = "socket_closed"; };
    globalThis.__quataCommunityRealtime = { state, close: () => socket.close(1000, "evidence-complete") };
  }, { ...configuration, accessToken, tables });
  await page.waitForFunction(() => {
    const state = globalThis.__quataCommunityRealtime?.state;
    return state?.joined || state?.failure;
  }, null, { timeout: 20_000 });
  const ready = await page.evaluate(() => globalThis.__quataCommunityRealtime.state);
  if (!ready.joined) throw new Error(`community_realtime_join_failed:${ready.failure ?? "unknown"}`);
}

async function waitForWall(page, wallId) {
  await page.waitForFunction((expected) => globalThis.__quataCommunityRealtime?.state?.events
    ?.some((entry) => entry.table === "community_walls" && entry.id === expected), wallId, { timeout: 20_000 });
  return page.evaluate(() => globalThis.__quataCommunityRealtime.state.events.length);
}

function trigger(dbUrlFile, journal) {
  const child = spawn("python", ["scripts/community-directory-realtime-trigger.py", "--db-url-file", dbUrlFile, "--journal", journal], {
    stdio: ["pipe", "pipe", "pipe"], windowsHide: true,
  });
  const lines = createInterface({ input: child.stdout, crlfDelay: Infinity });
  const errors = [];
  child.stderr.on("data", (chunk) => errors.push(String(chunk)));
  const iterator = lines[Symbol.asyncIterator]();
  return {
    child,
    async next() {
      const value = await iterator.next();
      if (value.done) throw new Error(`community_realtime_trigger_ended:${errors.join("").trim()}`);
      return JSON.parse(value.value);
    },
    restore() { child.stdin.write("restore\n"); child.stdin.end(); },
    exit: new Promise((done) => child.once("exit", (code) => done({ code, errors: errors.join("").trim() }))),
  };
}

async function main() {
  const options = args(process.argv.slice(2));
  evidenceOptions = options;
  evidenceState = {
    realtimePreflightPassed: false,
    sessionCreated: false,
    mutationStarted: false,
    databaseFixtureRemoved: false,
    authenticationSessionRevoked: false,
  };
  const configuration = await config();
  await assertRealtimeQuotaAvailable(configuration);
  evidenceState.realtimePreflightPassed = true;
  const user = options.publicOnly ? null : await credentials(options.credentialsFile);
  let session;
  let browser;
  let mutation;
  try {
    session = options.publicOnly ? null : await login(configuration, user, (custody) => {
      session = custody;
      evidenceState.sessionCreated = true;
    });
    browser = await chromium.launch({ executablePath: options.chrome, headless: true });
    const publicPage = await browser.newPage();
    const authenticatedPage = session ? await browser.newPage() : null;
    await connect(publicPage, configuration, null, publicTables);
    if (authenticatedPage) await connect(authenticatedPage, configuration, session.accessToken, authenticatedTables);
    mutation = trigger(options.dbUrlFile, `${options.out}.custody.json`);
    const changed = await mutation.next();
    evidenceState.mutationStarted = true;
    const publicCount = await waitForWall(publicPage, changed.id);
    const authenticatedCount = authenticatedPage ? await waitForWall(authenticatedPage, changed.id) : null;
    mutation.restore();
    const restored = await mutation.next();
    const exit = await mutation.exit;
    if (restored?.ok !== true || exit.code !== 0) throw new Error(`community_realtime_cleanup_failed:${exit.errors || exit.code}`);
    evidenceState.databaseFixtureRemoved = true;
    await publicPage.evaluate(() => globalThis.__quataCommunityRealtime.close());
    if (authenticatedPage) await authenticatedPage.evaluate(() => globalThis.__quataCommunityRealtime.close());
    const authenticatedSession = session
      ? { source: session.source, actorSha256: hash(session.profileId) }
      : null;
    await logout(configuration, session);
    evidenceState.authenticationSessionRevoked = true;
    session = undefined;
    const report = buildCommunityRealtimeSuccessReport({
      checkedAt: new Date().toISOString(),
      publicTables,
      authenticatedTables,
      publicCount,
      authenticatedCount,
      authenticatedSession,
      triggerTable: changed.table,
      triggerRowSha256: hash(changed.id),
    });
    await mkdir(dirname(options.out), { recursive: true });
    await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
    console.log(JSON.stringify({ ok: true, publicEvent: true, authenticatedEvent: authenticatedSession != null, cleanup: true, report: options.out }));
  } finally {
    if (mutation && mutation.child.exitCode === null) {
      try { mutation.restore(); } catch {}
      const exit = await mutation.exit.catch(() => null);
      if (exit?.code === 0) evidenceState.databaseFixtureRemoved = true;
    }
    await browser?.close().catch(() => {});
    if (session) {
      try {
        await logout(configuration, session);
        evidenceState.authenticationSessionRevoked = true;
        session = undefined;
      } catch {}
    }
  }
}

main().catch(async (error) => {
  const message = error instanceof Error ? error.message : String(error);
  if (evidenceOptions?.out && message.includes("http_402")) {
    const report = {
      check: "COMMUNITY-DIRECTORY-REALTIME-PUBLIC-AUTHENTICATED",
      checkedAt: new Date().toISOString(),
      ok: false,
      blockedByExternalService: true,
      limitation: "supabase_realtime_http_402_exceed_egress_quota",
      publicTables,
      authenticatedTables,
      realtimePreflightPassed: evidenceState?.realtimePreflightPassed ?? false,
      mutationStarted: evidenceState?.mutationStarted ?? false,
      sessionCreated: evidenceState?.sessionCreated ?? false,
      cleanup: {
        databaseFixtureRemovalRequired: evidenceState?.mutationStarted ?? false,
        databaseFixtureRemoved: evidenceState?.mutationStarted ? evidenceState.databaseFixtureRemoved : null,
        authenticationSessionRevocationRequired: evidenceState?.sessionCreated ?? false,
        authenticationSessionRevoked: evidenceState?.sessionCreated ? evidenceState.authenticationSessionRevoked : null,
      },
      secretsPersisted: false,
    };
    await mkdir(dirname(evidenceOptions.out), { recursive: true });
    await writeFile(evidenceOptions.out, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  }
  console.error(message.includes("http_402") ? "community_realtime_external_limit:http_402_exceed_egress_quota" : message);
  process.exitCode = 1;
});
