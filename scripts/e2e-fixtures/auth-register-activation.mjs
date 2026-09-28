import crypto from "node:crypto";
import { execFile, spawn } from "node:child_process";
import { access, mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { Client } from "pg";
import { acquireTurnstileToken } from "./turnstile-browser-token.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const WATCHDOG = resolve(HERE, "..", "auth-register-safety-watchdog.mjs");
const DB_CONNECTION_TIMEOUT_MS = 10_000;
const DB_QUERY_TIMEOUT_MS = 30_000;
const DB_LOCK_TIMEOUT_MS = 10_000;
const CLI = process.platform === "win32" ? "npx.cmd" : "npx";
const SUPABASE_VERSION = "supabase@2.109.1";
const TURNSTILE_SECRET_NAME = "QUATA_WEB_REGISTRATION_TURNSTILE_SECRET";
const TURNSTILE_TEST_MODE_NAME = "QUATA_REGISTRATION_TURNSTILE_TEST_MODE";
const ENABLED_NAME = "QUATA_WEB_REGISTRATION_ENABLED";
const JOURNAL_VERSION = 1;

export async function runRegistrationActivationEvidence(config, dependencies = {}) {
  const acquireToken = dependencies.acquireToken ?? acquireTurnstileToken;
  const cli = dependencies.cli ?? runSupabaseCli;
  const fetcher = dependencies.fetcher ?? fetch;
  const clock = dependencies.clock ?? (() => new Date());
  const db = dependencies.db ?? await openDatabase(config);
  const owned = { registrations: [], authUsers: [], profileIds: [], accessTokens: [], plans: [], rateScopes: [] };
  const report = {
    check: "AUTH-REGISTER-REAL-001",
    status: "running",
    productSha: config.productSha,
    startedAt: clock().toISOString(),
    server: { preflight: null, activation: null, restored: null },
    negative: {},
    channels: [],
    cleanup: { verified: false },
  };
  let baselineRateLimits;
  let watchdog;
  let journalPath;
  let activationEnvPath;
  let activationAttempted = false;
  try {
    const secretNames = await listSecretNames(config, cli);
    if (secretNames.has(TURNSTILE_SECRET_NAME) || secretNames.has(TURNSTILE_TEST_MODE_NAME)) {
      throw new Error("turnstile_activation_secret_already_present");
    }
    report.server.preflight = await probeDisabled(config, fetcher);
    baselineRateLimits = await readRateLimits(db);

    journalPath = await createRecoveryJournal(config, owned, baselineRateLimits, clock);
    activationEnvPath = resolve(config.privateDirectory, `.activation-${crypto.randomUUID()}.env`);
    await updateRecoveryJournal(journalPath, config, owned, baselineRateLimits, clock, {
      activationAttempted,
      activationEnvPath,
    });
    watchdog = await startWatchdog(config, journalPath);
    activationAttempted = true;
    await updateRecoveryJournal(journalPath, config, owned, baselineRateLimits, clock, { activationAttempted, activationEnvPath });
    await setActivationSecrets(config, cli, true, activationEnvPath);
    report.server.activation = await waitForProbe(config, fetcher, 403, "challenge_failed");

    const invalidPayload = await postRegistration(config, fetcher, {
      version: 1,
      channel: "web",
      challenge_token: "invalid",
    });
    assertResponse(invalidPayload, 400, "invalid_display_name", "invalid_payload_negative_failed");
    report.negative.invalidPayload = { httpStatus: invalidPayload.status, error: invalidPayload.body.error };

    const invalidChallengePlan = await registrationPlan(db, "web", config);
    owned.plans.push(invalidChallengePlan);
    owned.rateScopes.push(...invalidChallengePlan.rateScopes);
    await updateRecoveryJournal(journalPath, config, owned, baselineRateLimits, clock, { activationAttempted, activationEnvPath });
    const invalidChallenge = await postRegistration(config, fetcher, {
      ...invalidChallengePlan.payload,
      challenge_token: "invalid-token",
    });
    assertResponse(invalidChallenge, 403, "challenge_failed", "invalid_challenge_negative_failed");
    report.negative.invalidChallenge = { httpStatus: invalidChallenge.status, error: invalidChallenge.body.error };

    for (const channel of ["web", "android", "ios"]) {
      const plan = await registrationPlan(db, channel, config);
      owned.plans.push(plan);
      owned.rateScopes.push(...plan.rateScopes);
      await updateRecoveryJournal(journalPath, config, owned, baselineRateLimits, clock, { activationAttempted, activationEnvPath });
      const token = await acquireToken({
        siteKey: config.turnstileSiteKey,
        action: `register_${channel}`,
        pageUrl: config.turnstilePageUrl,
        executablePath: config.browserExecutablePath,
      });
      const created = await postRegistration(config, fetcher, { ...plan.payload, challenge_token: token });
      assertAccepted(created, `registration_${channel}_not_accepted`);
      const row = await findRegistration(db, plan);
      owned.registrations.push(row.id);
      owned.profileIds.push(row.profile_id);
      owned.authUsers.push(row.auth_user_id);
      await updateRecoveryJournal(journalPath, config, owned, baselineRateLimits, clock, { activationAttempted, activationEnvPath });
      const login = await login(config, fetcher, plan);
      if (login.profileId !== row.profile_id || login.authUserId !== row.auth_user_id) {
        throw new Error(`registration_${channel}_login_identity_mismatch`);
      }
      owned.accessTokens.push(login.accessToken);
      const question = await recoveryQuestion(config, fetcher, plan);
      if (question !== plan.payload.secret_question) throw new Error(`registration_${channel}_recovery_mismatch`);
      const channelReport = {
        channel,
        acceptedHttpStatus: created.status,
        opaqueAccepted: exactAccepted(created.body),
        loginVerified: true,
        recoveryQuestionVerified: true,
        idempotentReplayVerified: false,
      };
      if (channel === "web") {
        const replayToken = await acquireToken({
          siteKey: config.turnstileSiteKey,
          action: "register_web",
          pageUrl: config.turnstilePageUrl,
          executablePath: config.browserExecutablePath,
        });
        const replay = await postRegistration(config, fetcher, { ...plan.payload, challenge_token: replayToken });
        assertAccepted(replay, "registration_replay_not_accepted");
        const replayRow = await findRegistration(db, plan);
        if (replayRow.id !== row.id || replayRow.profile_id !== row.profile_id || replayRow.auth_user_id !== row.auth_user_id) {
          throw new Error("registration_replay_created_duplicate");
        }
        channelReport.idempotentReplayVerified = true;
      }
      report.channels.push(channelReport);
    }

    report.status = "passed";
    return report;
  } catch (error) {
    report.status = "failed";
    report.failureCode = safeCode(error);
    throw Object.assign(new Error(report.failureCode), { evidenceReport: report });
  } finally {
    const cleanup = await cleanupRegistrationActivation(config, db, cli, fetcher, owned, baselineRateLimits, {
      activationAttempted,
      activationEnvPath,
    })
      .catch((error) => ({ verified: false, failureCode: safeCode(error) }));
    report.cleanup = cleanup;
    report.server.restored = cleanup.serverRestored ?? false;
    report.finishedAt = clock().toISOString();
    if (cleanup.verified) {
      if (watchdog) await cancelWatchdog(watchdog).catch(() => {});
      if (journalPath) await rm(journalPath, { force: true }).catch(() => {});
    } else {
      report.cleanup.recoveryPending = Boolean(journalPath);
    }
    await db.end().catch(() => {});
    if (!cleanup.verified && report.status === "passed") {
      report.status = "failed";
      throw Object.assign(new Error(cleanup.failureCode || "registration_cleanup_failed"), { evidenceReport: report });
    }
  }
}

async function openDatabase(config) {
  const [connectionStringRaw, ca] = await Promise.all([
    readFile(config.dbUrlFile, "utf8"),
    readFile(config.dbTlsCaFile, "utf8"),
  ]);
  const url = new URL(connectionStringRaw.trim());
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  const client = new Client({
    connectionString: url.toString(),
    ssl: { ca, rejectUnauthorized: true },
    connectionTimeoutMillis: DB_CONNECTION_TIMEOUT_MS,
    query_timeout: DB_QUERY_TIMEOUT_MS,
    statement_timeout: DB_QUERY_TIMEOUT_MS,
    lock_timeout: DB_LOCK_TIMEOUT_MS,
  });
  await client.connect();
  return client;
}

async function registrationPlan(db, channel, config) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const phoneLocal = `79${crypto.randomInt(1000000, 9999999)}`;
    const existing = await db.query(
      "select count(*)::int as count from public.community_profiles where country_code=$1 and phone_local=$2",
      [config.countryCode, phoneLocal],
    );
    if (existing.rows[0].count !== 0) continue;
    const marker = crypto.randomBytes(6).toString("hex");
    const clientInstanceId = `registration-${channel}-${crypto.randomUUID()}`;
    const idempotencyKey = crypto.randomBytes(24).toString("hex");
    return {
      phoneLocal,
      requestKeyHash: sha256Hex(`${idempotencyKey}:${config.pepper}`),
      rateScopes: [
        `phone:${sha256Hex(`+${config.countryCode}${phoneLocal}:${config.pepper}`)}`,
        `client:${sha256Hex(`${clientInstanceId}:${config.pepper}`)}`,
      ],
      payload: {
        version: 1,
        display_name: `Quata Registration ${channel} ${marker}`,
        neighborhood: "Evidence",
        country_code: config.countryCode,
        phone_local: phoneLocal,
        password: `Qr-${crypto.randomBytes(12).toString("base64url")}7aA`,
        secret_question: "barrio",
        secret_answer: `Evidence ${marker}`,
        client_instance_id: clientInstanceId,
        idempotency_key: idempotencyKey,
        channel,
      },
    };
  }
  throw new Error("registration_unique_identity_unavailable");
}

async function postRegistration(config, fetcher, payload) {
  return postJson(fetcher, `${config.supabaseUrl}/functions/v1/quata-register`, {
    apikey: config.registrationApiKey,
    origin: config.registrationOrigin,
    "content-type": "application/json",
    "x-client-info": "quata-auth-register-real-evidence",
  }, payload);
}

async function login(config, fetcher, plan) {
  const response = await postJson(fetcher, `${config.supabaseUrl}/functions/v1/quata-auth-bridge`, {
    apikey: config.publishableKey,
    "content-type": "application/json",
    "x-client-info": "quata-auth-register-real-evidence",
  }, {
    action: "login",
    country_code: config.countryCode,
    phone: plan.phoneLocal,
    password: plan.payload.password,
  });
  if (response.status !== 200) throw new Error(`registration_login_failed_http_${response.status}`);
  const accessToken = response.body?.session?.access_token;
  const authUserId = response.body?.user?.id;
  const profileId = response.body?.profile?.id;
  if (![accessToken, authUserId, profileId].every((value) => typeof value === "string" && value)) {
    throw new Error("registration_login_response_invalid");
  }
  return { accessToken, authUserId, profileId };
}

async function recoveryQuestion(config, fetcher, plan) {
  const response = await postJson(fetcher, `${config.supabaseUrl}/functions/v1/quata-auth-bridge`, {
    apikey: config.publishableKey,
    "content-type": "application/json",
    "x-client-info": "quata-auth-register-real-evidence",
  }, { action: "recovery_question", country_code: config.countryCode, phone: plan.phoneLocal });
  if (response.status !== 200) throw new Error("registration_recovery_question_failed");
  return response.body?.secret_question;
}

async function findRegistration(db, plan) {
  const result = await db.query({
    text: `select r.id,r.profile_id,r.auth_user_id,r.status
             from public.web_registration_requests r
             join public.community_profiles p on p.id=r.profile_id
            where p.country_code=$1 and p.phone_local=$2`,
    values: [plan.payload.country_code, plan.phoneLocal],
  });
  if (result.rowCount !== 1 || result.rows[0].status !== "completed" || !result.rows[0].auth_user_id) {
    throw new Error("registration_ledger_not_completed");
  }
  return result.rows[0];
}

async function postJson(fetcher, url, headers, payload) {
  let response;
  try {
    response = await fetcher(url, {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(30_000),
    });
  } catch {
    throw new Error("registration_public_request_failed");
  }
  let body = null;
  try { body = JSON.parse(await response.text()); } catch { /* fail below with a sanitized code */ }
  return { status: response.status, body };
}

async function probeDisabled(config, fetcher) {
  const probe = await postRegistration(config, fetcher, {});
  assertResponse(probe, 503, "registration_unavailable", "registration_preflight_not_disabled");
  return { httpStatus: probe.status, error: probe.body.error };
}

async function waitForProbe(config, fetcher, expectedStatus, expectedError) {
  for (let attempt = 0; attempt < 24; attempt += 1) {
    const probe = await postRegistration(config, fetcher, {
      version: 1, display_name: "Probe", neighborhood: "Probe", country_code: "34",
      phone_local: "799999999", password: "ProbePassword7", secret_question: "barrio",
      secret_answer: "Probe", client_instance_id: "registration-probe-client",
      idempotency_key: "registration_probe_0123456789", challenge_token: "invalid", channel: "web",
    });
    if (probe.status === expectedStatus && probe.body?.error === expectedError) {
      return { httpStatus: probe.status, error: probe.body.error };
    }
    await new Promise((resolve) => setTimeout(resolve, 2_500));
  }
  throw new Error("registration_secret_propagation_timeout");
}

export async function cleanupRegistrationActivation(
  config,
  db,
  cli,
  fetcher,
  owned,
  baselineRateLimits,
  { activationAttempted = false, serverAlreadyClosed = false, activationEnvPath = null } = {},
) {
  const failures = [];
  let serverRestored = !activationAttempted && !serverAlreadyClosed;
  let rateCleanup = { restored: baselineRateLimits == null, concurrentChanges: false, sharedIpChanges: 0 };
  let serviceRole = null;
  let authCleanupSucceeded = true;
  let ownedDiscoverySucceeded = true;
  let profileCleanupSucceeded = true;

  if (activationAttempted && !serverAlreadyClosed) {
    await attempt("registration_disable_failed", failures, () => setActivationSecrets(config, cli, false));
    await attempt("registration_secret_unset_failed", failures, () => unsetActivationSecrets(config, cli));
  }
  if (activationAttempted || serverAlreadyClosed) {
    serverRestored = await attempt("registration_disabled_probe_failed", failures, async () => {
      await waitForProbe(config, fetcher, 503, "registration_unavailable");
      return true;
    }, false);
    await attempt("registration_secret_absence_not_verified", failures, async () => {
      const secretNames = await listSecretNames(config, cli);
      if (secretNames.has(TURNSTILE_SECRET_NAME) || secretNames.has(TURNSTILE_TEST_MODE_NAME)) {
        throw new Error("registration_secret_still_present");
      }
    });
  }
  if (activationEnvPath) {
    await attempt("registration_activation_file_cleanup_failed", failures, async () => {
      await rm(activationEnvPath, { force: true });
      try {
        await access(activationEnvPath);
      } catch {
        return;
      }
      throw new Error("registration_activation_file_cleanup_failed");
    });
  }

  ownedDiscoverySucceeded = await attempt("registration_owned_row_discovery_failed", failures, async () => {
    await discoverOwnedRows(db, owned);
    return true;
  }, false);
  if (owned.authUsers.length) {
    serviceRole = await attempt("service_role_key_unavailable", failures, () => serviceRoleKey(config, cli), null);
    if (!serviceRole) authCleanupSucceeded = false;
  }
  for (const accessToken of owned.accessTokens) {
    await fetcher(`${config.supabaseUrl}/auth/v1/logout`, {
      method: "POST",
      headers: { apikey: config.publishableKey, authorization: `Bearer ${accessToken}` },
      signal: AbortSignal.timeout(20_000),
    }).catch(() => {});
  }

  profileCleanupSucceeded = await attempt("registration_profile_cleanup_failed", failures, async () => {
    await db.query("begin");
    try {
      if (owned.profileIds.length) {
        await db.query("delete from public.web_client_sessions where profile_id=any($1::uuid[])", [owned.profileIds]);
        await db.query("delete from public.community_profiles where id=any($1::uuid[])", [owned.profileIds]);
      }
      await db.query("commit");
      return true;
    } catch (error) {
      await db.query("rollback").catch(() => {});
      throw error;
    }
  });

  if (serviceRole) {
    for (const authUserId of owned.authUsers) {
      const removed = await attempt("registration_auth_cleanup_failed", failures, async () => {
        const response = await fetcher(`${config.supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(authUserId)}`, {
          method: "DELETE",
          headers: { apikey: serviceRole, authorization: `Bearer ${serviceRole}` },
          signal: AbortSignal.timeout(20_000),
        });
        if (!response.ok && response.status !== 404) throw new Error("registration_auth_cleanup_failed");
        return true;
      }, false);
      authCleanupSucceeded = authCleanupSucceeded && removed;
    }
  }

  await attempt("registration_ledger_or_rate_cleanup_failed", failures, async () => {
    await db.query("begin");
    try {
      // Keep the durable request ledger when Auth cleanup fails. It is the
      // recovery key that lets a later retry rediscover every owned row.
      if (ownedDiscoverySucceeded && profileCleanupSucceeded && authCleanupSucceeded && owned.registrations.length) {
        await db.query("delete from public.web_registration_cleanup_events where registration_id=any($1::uuid[])", [owned.registrations]);
        await db.query("delete from public.web_registration_requests where id=any($1::uuid[])", [owned.registrations]);
      }
      if (baselineRateLimits) {
        rateCleanup = await restoreOwnedRateLimits(db, baselineRateLimits, owned.rateScopes);
      }
      await db.query("commit");
    } catch (error) {
      await db.query("rollback").catch(() => {});
      throw error;
    }
  });

  let remaining = { profiles: null, registrations: null, auth_users: null };
  await attempt("registration_cleanup_residue_query_failed", failures, async () => {
    const residue = await db.query({
      text: `select
        (select count(*)::int from public.community_profiles where id=any($1::uuid[])) as profiles,
        (select count(*)::int from public.web_registration_requests where id=any($2::uuid[])) as registrations,
        (select count(*)::int from auth.users where id=any($3::uuid[])) as auth_users`,
      values: [owned.profileIds, owned.registrations, owned.authUsers],
    });
    remaining = residue.rows[0];
    if (Object.values(remaining).some((value) => value !== 0)) throw new Error("registration_cleanup_residue");
  });

  const verified = failures.length === 0 && serverRestored && rateCleanup.restored;
  return {
    verified,
    serverRestored,
    profilesRemaining: remaining.profiles,
    registrationsRemaining: remaining.registrations,
    authUsersRemaining: remaining.auth_users,
    rateLimitsRestored: rateCleanup.restored,
    concurrentRateLimitChanges: rateCleanup.concurrentChanges,
    sharedIpRateLimitChanges: rateCleanup.sharedIpChanges,
    ...(failures.length ? { failureCode: failures[0], failureCodes: [...new Set(failures)] } : {}),
  };
}

async function attempt(code, failures, operation, fallback = undefined) {
  try {
    return await operation();
  } catch {
    failures.push(code);
    return fallback;
  }
}

async function discoverOwnedRows(db, owned) {
  if (!owned.plans.length) return;
  const requestHashes = owned.plans.map((plan) => plan.requestKeyHash);
  const result = await db.query(
    `select id,profile_id,auth_user_id from public.web_registration_requests
      where request_key_hash=any($1::text[])`,
    [requestHashes],
  );
  for (const row of result.rows) {
    if (!owned.registrations.includes(row.id)) owned.registrations.push(row.id);
    if (row.profile_id && !owned.profileIds.includes(row.profile_id)) owned.profileIds.push(row.profile_id);
    if (row.auth_user_id && !owned.authUsers.includes(row.auth_user_id)) owned.authUsers.push(row.auth_user_id);
  }
}

async function readRateLimits(db) {
  const { rows } = await db.query(
    "select scope_hash,window_started_at,attempts,updated_at from public.web_registration_rate_limits order by scope_hash,window_started_at",
  );
  return rows.map((row) => ({ ...row, window_started_at: new Date(row.window_started_at).toISOString(), updated_at: new Date(row.updated_at).toISOString() }));
}

async function restoreOwnedRateLimits(db, baseline, knownScopes) {
  const current = await readRateLimits(db);
  const plan = planRateLimitRestoration(baseline, current, knownScopes);
  for (const scope of plan.ownedScopes) {
    await db.query("delete from public.web_registration_rate_limits where scope_hash=$1", [scope]);
    for (const row of baseline.filter((item) => item.scope_hash === scope)) {
      await db.query(
        "insert into public.web_registration_rate_limits(scope_hash,window_started_at,attempts,updated_at) values($1,$2,$3,$4)",
        [row.scope_hash, row.window_started_at, row.attempts, row.updated_at],
      );
    }
  }
  const restored = await readRateLimits(db);
  const before = new Map(baseline.map((row) => [rateKey(row), row]));
  const restoredMap = new Map(restored.map((row) => [rateKey(row), row]));
  for (const key of new Set([...before.keys(), ...restoredMap.keys()])) {
    const scope = (restoredMap.get(key) ?? before.get(key))?.scope_hash;
    if (plan.ownedScopes.includes(scope) && JSON.stringify(before.get(key) ?? null) !== JSON.stringify(restoredMap.get(key) ?? null)) {
      throw new Error("registration_rate_limit_restore_failed");
    }
  }
  return {
    restored: true,
    concurrentChanges: plan.concurrentChanges,
    sharedIpChanges: plan.sharedIpChanges,
  };
}

export function planRateLimitRestoration(baseline, current, knownScopes) {
  const before = new Map(baseline.map((row) => [rateKey(row), row]));
  const after = new Map(current.map((row) => [rateKey(row), row]));
  const changed = new Set([...before.keys(), ...after.keys()].filter((key) =>
    JSON.stringify(before.get(key) ?? null) !== JSON.stringify(after.get(key) ?? null)));
  const ipScopes = new Set([...changed]
    .map((key) => (after.get(key) ?? before.get(key))?.scope_hash)
    .filter((scope) => scope?.startsWith("ip:")));
  // IP scopes are shared by every caller behind the same egress address. Even
  // one changed IP row cannot be attributed exclusively to this evidence run,
  // so it is observed but never rewritten.
  const ownedScopes = new Set(knownScopes);
  const concurrentChanges = [...changed].some((key) => {
    const scope = (after.get(key) ?? before.get(key))?.scope_hash;
    return scope && !ownedScopes.has(scope) && !scope.startsWith("ip:");
  });
  return { ownedScopes: [...ownedScopes].sort(), concurrentChanges, sharedIpChanges: ipScopes.size };
}

function rateKey(row) {
  return `${row.scope_hash}|${row.window_started_at}`;
}

export async function recoverRegistrationActivation({ journalPath, serverAlreadyClosed = false }, dependencies = {}) {
  const journal = JSON.parse(await readFile(journalPath, "utf8"));
  validateRecoveryJournal(journal);
  const cli = dependencies.cli ?? runSupabaseCli;
  const fetcher = dependencies.fetcher ?? fetch;
  const db = dependencies.db ?? await openDatabase(journal.config);
  try {
    const cleanup = await cleanupRegistrationActivation(
      journal.config,
      db,
      cli,
      fetcher,
      normalizeOwned(journal.owned),
      journal.baselineRateLimits,
      {
        activationAttempted: journal.activationAttempted,
        serverAlreadyClosed,
        activationEnvPath: journal.config.activationEnvPath,
      },
    );
    if (cleanup.verified) await rm(journalPath, { force: true });
    return cleanup;
  } finally {
    await db.end().catch(() => {});
  }
}

export function processIsAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return error?.code === "EPERM";
  }
}

async function createRecoveryJournal(config, owned, baselineRateLimits, clock) {
  await mkdir(config.privateDirectory, { recursive: true });
  const journalPath = resolve(config.privateDirectory, `.auth-register-recovery-${crypto.randomUUID()}.json`);
  await updateRecoveryJournal(journalPath, config, owned, baselineRateLimits, clock, { activationAttempted: false, create: true });
  return journalPath;
}

async function updateRecoveryJournal(
  journalPath,
  config,
  owned,
  baselineRateLimits,
  clock,
  { activationAttempted, activationEnvPath = null, create = false },
) {
  const journal = buildRecoveryJournal(
    config,
    owned,
    baselineRateLimits,
    clock().toISOString(),
    activationAttempted,
    activationEnvPath,
  );
  const serialized = `${JSON.stringify(journal, null, 2)}\n`;
  if (create) {
    await writeFile(journalPath, serialized, { mode: 0o600, flag: "wx" });
    return;
  }
  const temporaryPath = `${journalPath}.${crypto.randomUUID()}.tmp`;
  await writeFile(temporaryPath, serialized, { mode: 0o600, flag: "wx" });
  try {
    await rename(temporaryPath, journalPath);
  } catch (error) {
    await rm(temporaryPath, { force: true }).catch(() => {});
    throw error;
  }
}

export function buildRecoveryJournal(config, owned, baselineRateLimits, updatedAt, activationAttempted, activationEnvPath = null) {
  return {
    version: JOURNAL_VERSION,
    check: "AUTH-REGISTER-REAL-001",
    updatedAt,
    activationAttempted,
    config: {
      productSha: config.productSha,
      projectRef: config.projectRef,
      supabaseUrl: config.supabaseUrl,
      registrationOrigin: config.registrationOrigin,
      publishableKey: config.publishableKey,
      registrationApiKey: config.registrationApiKey,
      dbUrlFile: config.dbUrlFile,
      dbTlsCaFile: config.dbTlsCaFile,
      privateDirectory: config.privateDirectory,
      activationEnvPath,
    },
    owned: {
      registrations: [...new Set(owned.registrations)],
      authUsers: [...new Set(owned.authUsers)],
      profileIds: [...new Set(owned.profileIds)],
      plans: owned.plans.map(({ requestKeyHash }) => ({ requestKeyHash })),
      rateScopes: [...new Set(owned.rateScopes)],
    },
    baselineRateLimits,
  };
}

function validateRecoveryJournal(journal) {
  if (journal?.version !== JOURNAL_VERSION || journal?.check !== "AUTH-REGISTER-REAL-001") {
    throw new Error("registration_recovery_journal_invalid");
  }
  const config = journal.config;
  if (!/^[a-z0-9]{20}$/.test(config?.projectRef || "") || !/^https:\/\//.test(config?.supabaseUrl || "")) {
    throw new Error("registration_recovery_journal_invalid");
  }
  try {
    const registrationOrigin = new URL(config.registrationOrigin);
    if (registrationOrigin.protocol !== "https:" || registrationOrigin.origin !== config.registrationOrigin) {
      throw new Error("registration_recovery_journal_invalid");
    }
  } catch {
    throw new Error("registration_recovery_journal_invalid");
  }
  for (const key of ["registrationApiKey", "dbUrlFile", "dbTlsCaFile", "privateDirectory"]) {
    if (typeof config[key] !== "string" || !config[key]) throw new Error("registration_recovery_journal_invalid");
  }
  if (config.activationEnvPath != null) {
    if (typeof config.activationEnvPath !== "string" || !/^\.activation-[0-9a-f-]+\.env$/.test(relative(config.privateDirectory, config.activationEnvPath))) {
      throw new Error("registration_recovery_journal_invalid");
    }
  }
  if (!Array.isArray(journal.baselineRateLimits) || typeof journal.activationAttempted !== "boolean") {
    throw new Error("registration_recovery_journal_invalid");
  }
  normalizeOwned(journal.owned);
}

function normalizeOwned(value) {
  const owned = {
    registrations: arrayOfStrings(value?.registrations),
    authUsers: arrayOfStrings(value?.authUsers),
    profileIds: arrayOfStrings(value?.profileIds),
    accessTokens: [],
    plans: Array.isArray(value?.plans) ? value.plans.map(({ requestKeyHash }) => ({ requestKeyHash })) : [],
    rateScopes: arrayOfStrings(value?.rateScopes),
  };
  if (owned.plans.some(({ requestKeyHash }) => typeof requestKeyHash !== "string" || !/^[0-9a-f]{64}$/.test(requestKeyHash))) {
    throw new Error("registration_recovery_journal_invalid");
  }
  return owned;
}

function arrayOfStrings(value) {
  if (!Array.isArray(value) || value.some((item) => typeof item !== "string" || !item)) {
    throw new Error("registration_recovery_journal_invalid");
  }
  return [...new Set(value)];
}

async function listSecretNames(config, cli) {
  const output = await cli(["secrets", "list", "--project-ref", config.projectRef, "--output", "json"]);
  let entries;
  try { entries = JSON.parse(output); } catch { throw new Error("supabase_secret_inventory_invalid"); }
  return new Set(entries.map((entry) => entry.name));
}

async function serviceRoleKey(config, cli) {
  const output = await cli(["projects", "api-keys", "--project-ref", config.projectRef, "--output", "json"]);
  let entries;
  try { entries = JSON.parse(output); } catch { throw new Error("supabase_api_key_inventory_invalid"); }
  const value = entries.find((entry) => entry.name === "service_role")?.api_key;
  if (typeof value !== "string" || !value) throw new Error("service_role_key_unavailable");
  return value;
}

async function setActivationSecrets(config, cli, enabled, reservedPath = null) {
  if (!enabled) {
    await cli(["secrets", "set", `${ENABLED_NAME}=false`, "--project-ref", config.projectRef]);
    return;
  }
  const path = reservedPath || resolve(config.privateDirectory, `.activation-${crypto.randomUUID()}.env`);
  if (!/^\.activation-[0-9a-f-]+\.env$/.test(relative(config.privateDirectory, path))) {
    throw new Error("registration_activation_path_invalid");
  }
  await mkdir(config.privateDirectory, { recursive: true });
  const values = `${ENABLED_NAME}=true\n${TURNSTILE_SECRET_NAME}=${config.turnstileSecret}\n${TURNSTILE_TEST_MODE_NAME}=${config.turnstileTestMode === true ? "true" : "false"}\n`;
  await writeFile(path, values, { mode: 0o600, flag: "wx" });
  try {
    await cli(["secrets", "set", "--env-file", path, "--project-ref", config.projectRef]);
  } finally {
    await rm(path, { force: true });
  }
}

async function unsetActivationSecrets(config, cli) {
  const names = await listSecretNames(config, cli);
  for (const name of [TURNSTILE_SECRET_NAME, TURNSTILE_TEST_MODE_NAME]) {
    if (names.has(name)) await cli(["secrets", "unset", name, "--project-ref", config.projectRef]);
  }
}

async function startWatchdog(config, journalPath) {
  const cancellationFile = resolve(config.privateDirectory, `.watchdog-cancel-${crypto.randomUUID()}`);
  const child = spawn(process.execPath, [
    WATCHDOG,
    config.projectRef,
    cancellationFile,
    journalPath,
    String(config.watchdogDelayMs),
    String(process.pid),
  ], {
    detached: true,
    stdio: "ignore",
    windowsHide: true,
  });
  child.unref();
  return { cancellationFile, pid: child.pid };
}

async function cancelWatchdog(watchdog) {
  await writeFile(watchdog.cancellationFile, "cancelled\n", { mode: 0o600, flag: "wx" });
}

function runSupabaseCli(args) {
  return new Promise((resolvePromise, reject) => {
    execFile(CLI, ["--yes", SUPABASE_VERSION, ...args], {
      windowsHide: true,
      maxBuffer: 4 * 1024 * 1024,
      timeout: 60_000,
      killSignal: "SIGKILL",
    }, (error, stdout) => {
      if (error) reject(new Error("supabase_cli_command_failed"));
      else resolvePromise(stdout);
    });
  });
}

function assertAccepted(response, code) {
  if (response.status !== 202 || !exactAccepted(response.body)) throw new Error(code);
}

function exactAccepted(body) {
  return body?.version === 1 && body?.status === "accepted" && Object.keys(body).sort().join(",") === "status,version";
}

function assertResponse(response, status, error, code) {
  if (response.status !== status || response.body?.error !== error) throw new Error(code);
}

function safeCode(error) {
  const raw = typeof error?.message === "string" ? error.message : "unknown_failure";
  return /^[a-z0-9_:.-]{1,120}$/i.test(raw) ? raw : "sanitized_failure";
}

function sha256Hex(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}
