import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { spawn } from "node:child_process";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  buildRecoveryJournal,
  cleanupRegistrationActivation,
  planRateLimitRestoration,
  processIsAlive,
  recoverRegistrationActivation,
  validateProductChannelResult,
} from "./e2e-fixtures/auth-register-activation.mjs";
import {
  classifyRegistrationWebResponse,
  isOpaqueAcceptedRegistrationResponse,
} from "./e2e-fixtures/auth-register-product-web.mjs";

const row = (scope_hash, attempts, updated = "2026-09-25T05:00:00.000Z") => ({
  scope_hash,
  window_started_at: "2026-09-25T05:00:00.000Z",
  attempts,
  updated_at: updated,
});

test("product channel result accepts one observed real submit without retaining inputs", () => {
  assert.deepEqual(validateProductChannelResult("web", {
    passed: true,
    exactSubmits: 1,
    authenticatedTransition: true,
    anchors: ["auth.register.submit", "auth.register.display-name", "auth.register.submit"],
    phone: "must-not-be-retained",
    password: "must-not-be-retained",
  }), {
    passed: true,
    exactSubmits: 1,
    authenticatedTransition: true,
    anchors: ["auth.register.display-name", "auth.register.submit"],
  });
});

test("product channel result rejects surrogate or ambiguous UI evidence", () => {
  for (const invalid of [
    { passed: true, exactSubmits: 0, authenticatedTransition: true, anchors: ["auth.register.submit"] },
    { passed: true, exactSubmits: 2, authenticatedTransition: true, anchors: ["auth.register.submit"] },
    { passed: true, exactSubmits: 1, authenticatedTransition: false, anchors: ["auth.register.submit"] },
    { passed: true, exactSubmits: 1, authenticatedTransition: true, anchors: ["fixture.submit"] },
  ]) {
    assert.throws(() => validateProductChannelResult("android", invalid), /product_ui_result_invalid/);
  }
});

test("Web product response diagnostics expose only bounded verification facts", () => {
  assert.equal(isOpaqueAcceptedRegistrationResponse({ version: 1, status: "accepted" }), true);
  assert.equal(isOpaqueAcceptedRegistrationResponse({ version: 1, status: "accepted", profile_id: "private" }), false);
  assert.equal(isOpaqueAcceptedRegistrationResponse({ accepted: true }), false);
  assert.equal(classifyRegistrationWebResponse({
    httpStatus: 202,
    accepted: true,
    exactRequests: 1,
    requestMatches: true,
  }), null);
  assert.equal(classifyRegistrationWebResponse({
    httpStatus: 403,
    accepted: false,
    exactRequests: 1,
    requestMatches: false,
  }), "registration_web_response_unverified_http-403_accepted-false_payload-mismatch");
  assert.equal(classifyRegistrationWebResponse({
    httpStatus: undefined,
    accepted: false,
    exactRequests: 2,
    requestMatches: true,
  }), "registration_web_response_unverified_http-unknown_accepted-false_request-count-2");
});

test("rate-limit cleanup restores only synthetic scopes and never rewrites a shared IP scope", () => {
  const baseline = [row("ip:shared", 2), row("phone:unrelated", 4)];
  const current = [
    row("ip:shared", 6, "2026-09-25T05:05:00.000Z"),
    row("phone:owned", 1, "2026-09-25T05:05:00.000Z"),
    row("client:owned", 1, "2026-09-25T05:05:00.000Z"),
    row("phone:unrelated", 5, "2026-09-25T05:05:00.000Z"),
  ];
  const plan = planRateLimitRestoration(baseline, current, ["phone:owned", "client:owned"]);
  assert.deepEqual(plan.ownedScopes, ["client:owned", "phone:owned"]);
  assert.equal(plan.concurrentChanges, true);
  assert.equal(plan.sharedIpChanges, 1);
  assert.equal(plan.ownedScopes.includes("phone:unrelated"), false);
});

test("rate-limit cleanup observes multiple IP changes without claiming or deleting them", () => {
  const plan = planRateLimitRestoration([], [row("ip:first", 1), row("ip:second", 1)], []);
  assert.deepEqual(plan.ownedScopes, []);
  assert.equal(plan.sharedIpChanges, 2);
});

test("cleanup attempts secret removal and database cleanup after a disable command failure", async () => {
  const cliCalls = [];
  const dbCalls = [];
  const privateDirectory = await mkdtemp(join(tmpdir(), "quata-registration-cleanup-"));
  const activationEnvPath = join(privateDirectory, ".activation-11111111-1111-4111-8111-111111111111.env");
  await import("node:fs/promises").then(({ writeFile }) => writeFile(activationEnvPath, "private"));
  const config = fixtureConfig(privateDirectory);
  const cleanup = await cleanupRegistrationActivation(
    config,
    fixtureDb(dbCalls, { profiles: 0, registrations: 0, auth_users: 0 }),
    async (args) => {
      cliCalls.push(args);
      if (args[0] === "secrets" && args[1] === "set") throw new Error("simulated");
      return "[]";
    },
    disabledProbe,
    emptyOwned(),
    null,
    { activationAttempted: true, activationEnvPath },
  );
  await assert.rejects(readFile(activationEnvPath), /ENOENT/);
  await rm(privateDirectory, { recursive: true, force: true });

  assert.equal(cleanup.verified, false);
  assert.equal(cleanup.serverRestored, true);
  assert.deepEqual(cleanup.failureCodes, ["registration_disable_failed"]);
  assert.equal(cliCalls.some((args) => args[0] === "secrets" && args[1] === "list"), true);
  assert.equal(cliCalls.some((args) => args[0] === "secrets" && args[1] === "unset"), false);
  assert.equal(dbCalls.some((query) => typeof query === "object" && query.text.includes("auth.users")), true);
});

test("cleanup preserves the request ledger when Auth deletion fails", async () => {
  const dbCalls = [];
  const owned = emptyOwned();
  owned.registrations.push("11111111-1111-4111-8111-111111111111");
  owned.profileIds.push("22222222-2222-4222-8222-222222222222");
  owned.authUsers.push("33333333-3333-4333-8333-333333333333");
  const cleanup = await cleanupRegistrationActivation(
    fixtureConfig("private"),
    fixtureDb(dbCalls, { profiles: 0, registrations: 1, auth_users: 1 }),
    async () => JSON.stringify([{ name: "service_role", api_key: "private-service-role" }]),
    async (url) => url.includes("/auth/v1/admin/users/")
      ? { ok: false, status: 500, async text() { return ""; } }
      : disabledProbe(),
    owned,
    null,
    { serverAlreadyClosed: true },
  );

  assert.equal(cleanup.verified, false);
  assert.equal(cleanup.failureCodes.includes("registration_auth_cleanup_failed"), true);
  assert.equal(dbCalls.some((query) => typeof query === "string" && query.includes("delete from public.web_registration_requests")), false);
});

test("cleanup preserves the request ledger when profile deletion fails", async () => {
  const dbCalls = [];
  const owned = emptyOwned();
  owned.registrations.push("11111111-1111-4111-8111-111111111111");
  owned.profileIds.push("22222222-2222-4222-8222-222222222222");
  const db = fixtureDb(dbCalls, { profiles: 1, registrations: 1, auth_users: 0 });
  const originalQuery = db.query;
  db.query = async (query) => {
    if (typeof query === "string" && query.includes("delete from public.community_profiles")) {
      dbCalls.push(query);
      throw new Error("simulated");
    }
    return originalQuery(query);
  };
  const cleanup = await cleanupRegistrationActivation(
    fixtureConfig("private"), db, async () => "[]", disabledProbe, owned, null, { serverAlreadyClosed: true },
  );

  assert.equal(cleanup.verified, false);
  assert.equal(cleanup.failureCodes.includes("registration_profile_cleanup_failed"), true);
  assert.equal(dbCalls.some((query) => typeof query === "string" && query.includes("delete from public.web_registration_requests")), false);
});

test("cleanup remains pending when the Turnstile secret is still installed", async () => {
  const cleanup = await cleanupRegistrationActivation(
    fixtureConfig("private"),
    fixtureDb([], { profiles: 0, registrations: 0, auth_users: 0 }),
    async () => JSON.stringify([{ name: "QUATA_WEB_REGISTRATION_TURNSTILE_SECRET" }]),
    disabledProbe,
    emptyOwned(),
    null,
    { serverAlreadyClosed: true },
  );
  assert.equal(cleanup.verified, false);
  assert.equal(cleanup.failureCodes.includes("registration_secret_absence_not_verified"), true);
});

test("cleanup remains pending when the temporary Turnstile test-mode flag is still installed", async () => {
  const cleanup = await cleanupRegistrationActivation(
    fixtureConfig("private"),
    fixtureDb([], { profiles: 0, registrations: 0, auth_users: 0 }),
    async () => JSON.stringify([{ name: "QUATA_REGISTRATION_TURNSTILE_TEST_MODE" }]),
    disabledProbe,
    emptyOwned(),
    null,
    { serverAlreadyClosed: true },
  );
  assert.equal(cleanup.verified, false);
  assert.equal(cleanup.failureCodes.includes("registration_secret_absence_not_verified"), true);
});

test("entrypoint writes a redacted failure report when private configuration is unavailable", async () => {
  const directory = await mkdtemp(join(tmpdir(), "quata-registration-report-"));
  const output = join(directory, "report.json");
  const env = { ...process.env };
  delete env.QUATA_AUTH_REGISTER_REAL_OPT_IN;
  const code = await runNode([fileURLToPath(new URL("./auth-register-real-evidence.mjs", import.meta.url)), "--out", output], env);
  const report = JSON.parse(await readFile(output, "utf8"));
  await rm(directory, { recursive: true, force: true });

  assert.equal(code, 1);
  assert.equal(report.status, "failed");
  assert.equal(report.failureCode, "registration_mutation_opt_in_required");
  assert.deepEqual(report.cleanup, { verified: false });
});

test("durable recovery journal excludes credentials and completes a detached cleanup retry", async () => {
  const directory = await mkdtemp(join(tmpdir(), "quata-registration-journal-"));
  const journalPath = join(directory, "recovery.json");
  const config = {
    ...fixtureConfig(directory),
    productSha: "1".repeat(40),
    dbUrlFile: join(directory, "db-url"),
    dbTlsCaFile: join(directory, "ca"),
    turnstileSecret: "must-never-enter-journal",
  };
  const owned = emptyOwned();
  owned.accessTokens.push("must-never-enter-journal-access-token");
  owned.plans.push({ requestKeyHash: "a".repeat(64), payload: { password: "must-never-enter-journal-password" } });
  const activationEnvPath = join(directory, ".activation-22222222-2222-4222-8222-222222222222.env");
  const journal = buildRecoveryJournal(
    config,
    owned,
    [],
    "2026-09-25T00:00:00.000Z",
    true,
    activationEnvPath,
  );
  const serialized = JSON.stringify(journal);
  assert.equal(serialized.includes("must-never-enter-journal"), false);
  assert.equal(journal.config.registrationOrigin, "https://egquata.com");
  await import("node:fs/promises").then(async ({ writeFile }) => {
    await writeFile(journalPath, serialized);
    await writeFile(activationEnvPath, "must-never-enter-journal");
  });

  const dbCalls = [];
  const db = fixtureDb(dbCalls, { profiles: 0, registrations: 0, auth_users: 0 });
  db.end = async () => {};
  const cleanup = await recoverRegistrationActivation(
    { journalPath, serverAlreadyClosed: true },
    {
      db,
      cli: async () => "[]",
      fetcher: async (_url, options) => {
        assert.equal(options.headers.origin, "https://egquata.com");
        return disabledProbe();
      },
    },
  );
  await assert.rejects(readFile(journalPath), /ENOENT/);
  await assert.rejects(readFile(activationEnvPath), /ENOENT/);
  await rm(directory, { recursive: true, force: true });
  assert.equal(cleanup.verified, true);
});

test("recovery rejects a non-origin registration URL before external access", async () => {
  const directory = await mkdtemp(join(tmpdir(), "quata-registration-origin-"));
  const journalPath = join(directory, "recovery.json");
  const journal = buildRecoveryJournal(
    { ...fixtureConfig(directory), productSha: "1".repeat(40) },
    emptyOwned(),
    [],
    "2026-09-25T00:00:00.000Z",
    false,
  );
  journal.config.registrationOrigin = "https://egquata.com/path";
  await import("node:fs/promises").then(({ writeFile }) => writeFile(journalPath, JSON.stringify(journal)));
  await assert.rejects(
    recoverRegistrationActivation({ journalPath }, { db: { end: async () => {} } }),
    /registration_recovery_journal_invalid/,
  );
  await rm(directory, { recursive: true, force: true });
});

test("watchdog owner liveness distinguishes this process from a missing PID", () => {
  assert.equal(processIsAlive(process.pid), true);
  assert.equal(processIsAlive(2_147_483_647), false);
});

test("owner and watchdog custody and external calls are time-bounded", async () => {
  const [owner, watchdog, packageJson, registrationSuite] = await Promise.all([
    readFile(new URL("./e2e-fixtures/auth-register-activation.mjs", import.meta.url), "utf8"),
    readFile(new URL("./auth-register-safety-watchdog.mjs", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8").then(JSON.parse),
    readFile(new URL("./auth-register-foundation-rollout-contract.test.mjs", import.meta.url), "utf8"),
  ]);
  assert.match(owner, /timeout:\s*60_000/);
  assert.match(owner, /connectionTimeoutMillis:\s*DB_CONNECTION_TIMEOUT_MS/);
  assert.match(owner, /query_timeout:\s*DB_QUERY_TIMEOUT_MS/);
  assert.match(owner, /statement_timeout:\s*DB_QUERY_TIMEOUT_MS/);
  assert.match(owner, /lock_timeout:\s*DB_LOCK_TIMEOUT_MS/);
  assert.match(owner, /String\(process\.pid\)/);
  assert.match(watchdog, /timeout:\s*60_000/);
  assert.match(watchdog, /processIsAlive\(ownerPid\)/);
  assert.match(owner, /process\.kill\(pid, 0\)/);
  assert.match(watchdog, /verified owner process disappeared/);
  assert.match(watchdog, /serverAlreadyClosed:\s*disableSucceeded\s*&&\s*unsetSucceeded/);
  assert.match(owner, /QUATA_REGISTRATION_TURNSTILE_TEST_MODE/);
  assert.doesNotMatch(owner, /const\s+login\s*=\s*await\s+login\s*\(/);
  assert.match(watchdog, /QUATA_REGISTRATION_TURNSTILE_TEST_MODE/);
  for (const suite of ["test:ci-fast-contracts", "test:web-wave2-contracts"]) {
    assert.match(packageJson.scripts[suite], /scripts\/auth-register-foundation-rollout-contract\.test\.mjs/);
  }
  assert.match(registrationSuite, /import "\.\/auth-register-activation\.test\.mjs"/);
  assert.match(registrationSuite, /import "\.\/turnstile-browser-token\.test\.mjs"/);
});

function fixtureConfig(privateDirectory) {
  return {
    projectRef: "yrrlankpwmhluexshxnw",
    supabaseUrl: "https://yrrlankpwmhluexshxnw.supabase.co",
    registrationOrigin: "https://egquata.com",
    publishableKey: "public-key",
    registrationApiKey: "registration-key",
    privateDirectory,
    dbUrlFile: "db-url",
    dbTlsCaFile: "db-ca",
  };
}

function emptyOwned() {
  return { registrations: [], authUsers: [], profileIds: [], accessTokens: [], plans: [], rateScopes: [] };
}

function fixtureDb(calls, residue) {
  return {
    async query(query) {
      calls.push(query);
      if (typeof query === "object" && query.text.includes("auth.users")) return { rows: [residue] };
      return { rows: [], rowCount: 0 };
    },
  };
}

async function disabledProbe() {
  return { status: 503, async text() { return JSON.stringify({ error: "registration_unavailable" }); } };
}

function runNode(args, env) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, args.map(String), { env, stdio: "ignore", windowsHide: true });
    child.once("error", reject);
    child.once("exit", resolve);
  });
}
