import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { spawn } from "node:child_process";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  buildRecoveryJournal,
  closeProductBeforeBackendCleanup,
  cleanupRegistrationActivation,
  planRateLimitRestoration,
  processIsAlive,
  productRegistrationInput,
  registrationCustodyForPayload,
  recoverRegistrationActivation,
  validateProductChannelResult,
} from "./e2e-fixtures/auth-register-activation.mjs";
import {
  classifyRegistrationWebResponse,
  isOpaqueAcceptedRegistrationResponse,
  webRegistrationStorageSeed,
} from "./e2e-fixtures/auth-register-product-web.mjs";

const row = (scope_hash, attempts, updated = "2026-09-25T05:00:00.000Z") => ({
  scope_hash,
  window_started_at: "2026-09-25T05:00:00.000Z",
  attempts,
  updated_at: updated,
});

test("product channel closes before backend cleanup and exposes unsettled custody", async () => {
  const calls = [];
  let settled = false;
  const result = await closeProductBeforeBackendCleanup({
    closeProductChannel: async () => { calls.push("close"); settled = true; },
    productOperationsSettled: () => { calls.push("settled"); return settled; },
  });
  assert.deepEqual(calls, ["close", "settled"]);
  assert.deepEqual(result, { productCleanupFailure: undefined, productSettled: true });

  const failed = await closeProductBeforeBackendCleanup({
    closeProductChannel: async () => { throw new Error("registration_product_ios_cleanup_incomplete"); },
    productOperationsSettled: () => false,
  });
  assert.deepEqual(failed, {
    productCleanupFailure: "registration_product_ios_cleanup_incomplete",
    productSettled: false,
  });
});

test("iOS watchdog stops a launched child when initial state publication fails", async () => {
  const watchdogPath = fileURLToPath(new URL("./run-ios-command-watchdog.py", import.meta.url));
  const source = `
import importlib.util, pathlib, tempfile
spec = importlib.util.spec_from_file_location("watchdog", ${JSON.stringify(watchdogPath)})
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
class Process:
    pid = 4242
process = Process()
calls = []
module.write_state = lambda *args: (_ for _ in ()).throw(OSError("simulated"))
module.stop_process_group = lambda child, log: calls.append(child.pid) or True
with tempfile.TemporaryDirectory() as directory:
    result = module.publish_initial_state_or_stop(pathlib.Path(directory) / "state.json", process, pathlib.Path(directory) / "log")
assert result is False
assert calls == [4242]
`;
  const code = await new Promise((resolvePromise) => {
    const child = spawn("python", ["-c", source], { windowsHide: true, stdio: "ignore" });
    child.once("error", () => resolvePromise(-1));
    child.once("close", resolvePromise);
  });
  assert.equal(code, 0);
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

test("Web product trial pre-seeds and verifies the exact journaled custody identifiers", () => {
  const payload = {
    country_code: "34",
    phone_local: "791234567",
    display_name: "Evidence",
    neighborhood: "Evidence",
    password: "private",
    secret_question: "barrio",
    secret_answer: "private",
    client_instance_id: "registration-web-owned-client",
    idempotency_key: "a".repeat(48),
  };
  const input = productRegistrationInput(payload);
  assert.equal(input.clientInstanceId, payload.client_instance_id);
  assert.equal(input.idempotencyKey, payload.idempotency_key);
  assert.deepEqual(webRegistrationStorageSeed(input), {
    quata_web_client_instance_id: payload.client_instance_id,
    "web.auth.registration.identity": "34:791234567",
    "web.auth.registration.idempotency_key": payload.idempotency_key,
  });
  const custody = registrationCustodyForPayload(payload, "pepper");
  assert.match(custody.requestKeyHash, /^[0-9a-f]{64}$/);
  assert.equal(custody.rateScopes.length, 2);
  assert.equal(custody.rateScopes.every((scope) => /^(phone|client):[0-9a-f]{64}$/.test(scope)), true);
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

test("recovery after product POST discovers and removes the exact journaled request and rate scopes", async () => {
  const directory = await mkdtemp(join(tmpdir(), "quata-registration-post-crash-"));
  const journalPath = join(directory, "recovery.json");
  const payload = {
    country_code: "34",
    phone_local: "791234567",
    client_instance_id: "registration-web-owned-client",
    idempotency_key: "b".repeat(48),
  };
  const custody = registrationCustodyForPayload(payload, "private-pepper");
  const owned = emptyOwned();
  owned.plans.push({ requestKeyHash: custody.requestKeyHash });
  owned.rateScopes.push(...custody.rateScopes);
  const journal = buildRecoveryJournal(
    { ...fixtureConfig(directory), productSha: "2".repeat(40) },
    owned,
    [],
    "2026-09-25T00:00:00.000Z",
    true,
  );
  await import("node:fs/promises").then(({ writeFile }) => writeFile(journalPath, JSON.stringify(journal)));

  const registrationId = "11111111-1111-4111-8111-111111111111";
  const profileId = "22222222-2222-4222-8222-222222222222";
  const authUserId = "33333333-3333-4333-8333-333333333333";
  const discoveryHashes = [];
  const deletedRateScopes = [];
  let rateRows = custody.rateScopes.map((scope) => row(scope, 1));
  const db = {
    async query(query, values = []) {
      const text = typeof query === "string" ? query : query.text;
      if (text.includes("where request_key_hash=any")) {
        discoveryHashes.push(...values[0]);
        return { rows: [{ id: registrationId, profile_id: profileId, auth_user_id: authUserId }] };
      }
      if (text.startsWith("select scope_hash,window_started_at")) return { rows: rateRows };
      if (text.includes("delete from public.web_registration_rate_limits where scope_hash=$1")) {
        deletedRateScopes.push(values[0]);
        rateRows = rateRows.filter((item) => item.scope_hash !== values[0]);
        return { rows: [], rowCount: 1 };
      }
      if (typeof query === "object" && text.includes("auth.users")) {
        return { rows: [{ profiles: 0, registrations: 0, auth_users: 0 }] };
      }
      return { rows: [], rowCount: 0 };
    },
    async end() {},
  };
  const cleanup = await recoverRegistrationActivation(
    { journalPath, serverAlreadyClosed: true },
    {
      db,
      cli: async (args) => args[0] === "projects"
        ? JSON.stringify([{ name: "service_role", api_key: "private-service-role" }])
        : "[]",
      fetcher: async (url) => url.includes("/auth/v1/admin/users/")
        ? { ok: true, status: 200 }
        : disabledProbe(),
    },
  );

  await assert.rejects(readFile(journalPath), /ENOENT/);
  await rm(directory, { recursive: true, force: true });
  assert.equal(cleanup.verified, true);
  assert.deepEqual(discoveryHashes, [custody.requestKeyHash]);
  assert.deepEqual(deletedRateScopes.sort(), [...custody.rateScopes].sort());
  assert.equal(cleanup.concurrentRateLimitChanges, false);
  assert.equal(cleanup.sharedIpRateLimitChanges, 0);
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

test("Android product registration runs one private-input product journey inside existing custody", async () => {
  const [entrypoint, runner, instrumentedTest, mainActivity] = await Promise.all([
    readFile(new URL("./auth-register-real-evidence.mjs", import.meta.url), "utf8"),
    readFile(new URL("./e2e-fixtures/auth-register-product-android.mjs", import.meta.url), "utf8"),
    readFile(new URL("../app/src/androidTest/java/com/quata/feature/auth/presentation/AuthRegisterRealInstrumentedTest.kt", import.meta.url), "utf8"),
    readFile(new URL("../app/src/main/java/com/quata/MainActivity.kt", import.meta.url), "utf8"),
  ]);
  assert.match(entrypoint, /await trial\.prepare\(\)[\s\S]*productChannels:\s*\["android"\]/);
  assert.match(entrypoint, /QUATA_TURNSTILE_SITE_KEY:\s*config\.turnstileSiteKey/);
  assert.match(entrypoint, /QUATA_REGISTRATION_API_KEY:\s*config\.registrationApiKey/);
  assert.doesNotMatch(runner, /shell", "pm", "clear", APPLICATION_ID/);
  assert.match(runner, /run-as", APPLICATION_ID, "mkdir", "-p", "files"/);
  assert.match(runner, /run-as", APPLICATION_ID, "tee", path/);
  assert.match(runner, /privateRemove\(adb, serial, INPUT_FILE\)/);
  assert.match(runner, /registration_product_android_device_serial_required/);
  assert.match(runner, /QUATA_ANDROID_DEVICE_EPHEMERAL/);
  assert.match(runner, /registration_product_android_ephemeral_opt_in_required/);
  assert.match(runner, /getprop", "ro\.kernel\.qemu/);
  assert.match(runner, /registration_product_android_physical_device_forbidden/);
  assert.match(runner, /"-e", "class", TEST_CLASS/);
  assert.doesNotMatch(runner, /console\.(?:log|error)|stdio:\s*"inherit"/);
  assert.match(instrumentedTest, /assertTrue\("registration_private_input_not_removed", inputFile\.delete\(\)\)/);
  assert.match(instrumentedTest, /putString\("client_instance_id", input\.getString\("clientInstanceId"\)\)/);
  assert.match(instrumentedTest, /val pendingKey = "pending_\$identityDigits"/);
  assert.match(instrumentedTest, /putString\(pendingKey, input\.getString\("idempotencyKey"\)\)/);
  assert.doesNotMatch(instrumentedTest, /deleteSharedPreferences\("registration_security"\)/);
  assert.match(instrumentedTest, /hadClientInstanceId/);
  assert.match(instrumentedTest, /previousClientInstanceId/);
  assert.match(instrumentedTest, /hadPendingKey/);
  assert.match(instrumentedTest, /previousPendingKey/);
  assert.match(instrumentedTest, /registration_security_fixture_not_restored/);
  assert.match(instrumentedTest, /sessionManager\.clearSession\(\)/);
  assert.match(instrumentedTest, /performClick\(\)[\s\S]*waitUntil\(120_000\)[\s\S]*FeedRootTestTag/);
  assert.match(instrumentedTest, /writeResult\(JSONObject\(\)\.put\("passed", false\)\.put\("failureStage", stage\)\)/);
  assert.doesNotMatch(mainActivity, /AppDestinations\.Register\.route,[\s\S]*AppDestinations\.OfficialPostEditor\.route/);
  assert.match(instrumentedTest, /AppNavGraph\([\s\S]*startDestinationOverride = AppDestinations\.Register\.route/);
  assert.match(instrumentedTest, /MainActivityTurnstileHost\(compose\.activity, challengeOutcome::set\)/);
  assert.match(instrumentedTest, /challengeOutcome\.compareAndSet\("pending", "started"\)/);
  assert.match(instrumentedTest, /assertIsEnabled\(\)[\s\S]*performClick\(\)/);
  assert.match(instrumentedTest, /onNodeWithTag\(RegisterTestTags\.Submit\)\s*[\s\S]*assertIsEnabled\(\)/);
  const turnstileHost = await readFile(new URL("../app/src/main/java/com/quata/core/auth/MainActivityTurnstileHost.kt", import.meta.url), "utf8");
  assert.match(turnstileHost, /WindowManager\.LayoutParams\.MATCH_PARENT[\s\S]*WindowManager\.LayoutParams\.MATCH_PARENT/);
  assert.match(instrumentedTest, /product-error-\$\{challengeOutcome\.get\(\)\}/);
});

test("iOS product registration uses an exact clean checkout and a disposable simulator", async () => {
  const [entrypoint, runner, shellRunner, launcher, uiTest, identityStore, activation] = await Promise.all([
    readFile(new URL("./auth-register-real-evidence.mjs", import.meta.url), "utf8"),
    readFile(new URL("./e2e-fixtures/auth-register-product-ios.mjs", import.meta.url), "utf8"),
    readFile(new URL("./run-ios-auth-register-real-ui-test.sh", import.meta.url), "utf8"),
    readFile(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8"),
    readFile(new URL("../iosApp/iosAppUITests/QuataIosHostUITests.swift", import.meta.url), "utf8"),
    readFile(new URL("../feature/auth/src/iosMain/kotlin/com/quata/feature/auth/data/IosTurnstileChallengeProvider.kt", import.meta.url), "utf8"),
    readFile(new URL("./e2e-fixtures/auth-register-activation.mjs", import.meta.url), "utf8"),
  ]);
  assert.match(entrypoint, /options\.productUi === "ios"/);
  assert.match(entrypoint, /productChannels:\s*\["ios"\]/);
  assert.match(entrypoint, /QUATA_SUPABASE_PUBLISHABLE_KEY:\s*config\.publishableKey/);
  assert.match(runner, /state\.head !== expectedHead \|\| state\.dirty !== false/);
  assert.match(runner, /xcrun simctl create/);
  assert.match(runner, /ConnectHardwareKeyboard -bool false/);
  assert.match(runner, /SimRuntime\.iOS-18-3/);
  assert.match(runner, /xcrun simctl delete/);
  assert.match(runner, /QuataPublicRuntime\.local\.xcconfig/);
  assert.match(runner, /await restoreRuntime\(\)/);
  assert.match(runner, /registration_product_ios_runner_failed:\[a-z0-9_-\]\+/);
  assert.match(runner, /mktemp \/tmp\/quata-ios-register-input/);
  assert.match(runner, /rm -f \$\{shellQuote\(remoteInput\)\}/);
  assert.match(entrypoint, /closeProductChannel:\s*trial\.close/);
  assert.match(entrypoint, /productOperationsSettled:\s*trial\.operationsSettled/);
  assert.match(activation, /closeProductBeforeBackendCleanup[\s\S]*cleanupRegistrationActivation/);
  assert.match(runner, /start_new_session=True/);
  assert.match(runner, /if \(!remoteRunState \|\| settled\) return/);
  assert.match(runner, /kill -TERM -- "-\$pid"/);
  assert.match(runner, /test ! -e \$\{shellQuote\(remoteInput\)\}/);
  assert.match(runner, /registration_product_ios_cleanup_incomplete_\$\{failures\.join\("_"\)\}/);
  assert.match(runner, /QUATA_IOS_AUTH_REGISTER_STAGE_FILE="\$state\/stage"/);
  assert.match(runner, /QUATA_IOS_AUTH_REGISTER_WATCHDOG_STATE_DIR="\$state"/);
  assert.match(runner, /watchdog_manifest[\s\S]*test -f "\$watchdog_state"/);
  assert.match(runner, /watchdog_settled[\s\S]*kill -TERM -- "-\$watchdog_pid"[\s\S]*kill -KILL -- "-\$watchdog_pid"/);
  assert.match(runner, /xcrun simctl list devices -j > "\$inventory"[\s\S]*json\.load\(handle\)[\s\S]*! grep -F/);
  assert.match(shellRunner, /QUATA_IOS_AUTH_REGISTER_STAGE_FILE/);
  assert.match(shellRunner, /watchdog-manifest[\s\S]*state_args=\(--state-file "\$state_file"\)/);
  assert.match(entrypoint, /productHarnessOwnsClose = true[\s\S]*if \(!productHarnessOwnsClose\) await trial\.close\(\)/);
  assert.doesNotMatch(runner, /restoreRuntime\(\)\.catch\(\(\) => \{\}\)/);
  assert.doesNotMatch(runner, /console\.(?:log|error)|stdio:\s*\[?"inherit"/);
  assert.match(shellRunner, /get_app_container.*com\.quata\.ios data/);
  assert.match(shellRunner, /killall Simulator/);
  assert.match(
    shellRunner,
    /killall Simulator[\s\S]*simctl boot "\$QUATA_IOS_SIMULATOR_UDID"[\s\S]*simctl bootstatus "\$QUATA_IOS_SIMULATOR_UDID"[\s\S]*-CurrentDeviceUDID "\$QUATA_IOS_SIMULATOR_UDID"/,
  );
  assert.match(shellRunner, /-CurrentDeviceUDID "\$QUATA_IOS_SIMULATOR_UDID"/);
  assert.match(shellRunner, /Connect Hardware Keyboard/);
  assert.match(shellRunner, /AXMenuItemMarkChar/);
  assert.match(shellRunner, /KeyboardsCurrentAndNext/);
  assert.match(shellRunner, /runner_stage="copy_private_input"/);
  assert.match(shellRunner, /runner_stage="execute_xctest"/);
  assert.match(shellRunner, /registration_product_ios_runner_failed:%s/);
  assert.match(shellRunner, /capture_failure_diagnostics/);
  assert.match(shellRunner, /run_bounded "\$method" 900/);
  assert.match(shellRunner, /auth-register-product-input\.json/);
  assert.match(shellRunner, /testRealAuthRegistrationSubmitsOnceAndRestoresAuthenticatedFeed/);
  assert.match(shellRunner, /-resultBundlePath "\$result_bundle"/);
  assert.match(shellRunner, /IOS_AUTH_REGISTER_REAL_UI_GATE_PASSED/);
  assert.match(launcher, /case "auth-register-real"/);
  assert.match(launcher, /consumeRegistrationEvidenceInput\(\)/);
  assert.match(launcher, /removeItem\(at: input\)/);
  assert.match(launcher, /seedIosRegistrationEvidenceIdentity/);
  assert.match(launcher, /QuataRegistrationViewController/);
  assert.match(launcher, /quata-ios-auth-register-success/);
  assert.match(uiTest, /tapAfterDismissingKeyboard\("auth\.register\.submit"/);
  assert.match(uiTest, /RunLoop\.current\.run\(until: Date\(\)\.addingTimeInterval\(95\)\)[\s\S]*waitForExistence\(timeout: 55\)/);
  assert.match(uiTest, /enterText\(input\.countryCode, into: "auth\.register\.country-prefix\.search"/);
  assert.match(uiTest, /dismissKeyboardWithReturn\(from: "auth\.register\.country-prefix\.search"/);
  assert.match(uiTest, /typePrivatePhone\(input\.phone/);
  assert.match(uiTest, /typePrivatePhone[\s\S]*ensurePrivateKeyboardMode\(\.numbers/);
  assert.match(uiTest, /typePrivateText\(input\.password/);
  assert.match(uiTest, /private func typePrivateText[\s\S]{0,800}tapAfterDismissingKeyboard\(identifier, in: app\)/);
  assert.match(uiTest, /keyboard\.keys\.allElementsBoundByIndex/);
  assert.match(uiTest, /app\.coordinate\(withNormalizedOffset/);
  assert.match(uiTest, /labels\.lazy\.compactMap\(\{ keyFrames\[\$0\.lowercased\(\)\] \}\)\.first/);
  assert.match(uiTest, /tapPrivateKeyboardShift\([\s\S]*keyFrames\["z"\][\s\S]*firstLetterOnRow\.midY/);
  assert.match(uiTest, /dismissKeyboardOnboardingIfNeeded/);
  assert.match(uiTest, /assertSoftwareKeyboardIsOnScreen/);
  assert.match(uiTest, /tapVisibleElement\("auth\.register\.country-prefix\.option/);
  assert.doesNotMatch(uiTest, /pastePrivateText|UIPasteboard|\.typeText\(input\.(?:displayName|neighborhood|phone|password|secretAnswer)/);
  assert.doesNotMatch(uiTest, /Expected one private secure character|Expected the complete private (?:secure |phone )?input/);
  assert.match(entrypoint, /evidenceChannels: \["ios"\]/);
  assert.match(activation, /channel === "ios"[\s\S]*randomAlphabeticToken\(24\)[\s\S]*: `Qr-/);
  assert.match(runner, /QUATA_IOS_REGISTRATION_ENABLED = true/);
  assert.match(runner, /registration_product_ios_built_runtime_invalid/);
  assert.match(runner, /enabled != "true"/);
  assert.match(runner, /"\\\\n" not in value/);
  assert.match(runner, /QUATA_IOS_TURNSTILE_ALLOWED_ORIGIN/);
  assert.match(uiTest, /XCTFail\([\s\S]*authenticated callback/);
  assert.match(uiTest, /tapAfterDismissingKeyboard\(identifier, in: app\)[\s\S]*privateKeyboardFrameCache\.removeAll\(\)[\s\S]*ensurePrivateKeyboardMode\(\.numbers/);
  assert.match(uiTest, /\[element\.label, element\.identifier\]/);
  assert.match(launcher, /platformServices\.attachPresenter\(controller: container\)[\s\S]*return container/);
  assert.doesNotMatch(uiTest, /auth-register-real-filled/);
  assert.match(uiTest, /quata-ios-authenticated-top-chrome/);
  assert.match(uiTest, /navigation\.primary\.feed/);
  assert.match(uiTest, /auth-register-real-relaunch-failed/);
  assert.match(identityStore, /seedEvidenceIdentity/);
  assert.match(identityStore, /iosRegistrationPayloadFingerprint\(request\)/);
  assert.match(identityStore, /private var activeSession: IosTurnstileSession\?/);
  assert.match(identityStore, /private class IosTurnstileSession/);
  assert.match(identityStore, /retainedDelegate\?\.invalidate\(\)[\s\S]*navigationDelegate = null[\s\S]*removeScriptMessageHandlerForName/);
  assert.match(identityStore, /dismissViewControllerAnimated\(flag = true, completion = release\)/);
  assert.match(identityStore, /private fun complete[\s\S]*dispatch_async\(dispatch_get_main_queue\(\)\)[\s\S]*completion\(result\)/);
  assert.match(identityStore, /dispatch_after\([\s\S]*TurnstileTimeoutMillis \* NSEC_PER_MSEC\.toLong\(\)[\s\S]*ios_registration_challenge_failed/);
  assert.doesNotMatch(identityStore, /withTimeout\(TurnstileTimeoutMillis\)/);
  assert.match(identityStore, /clientInstanceId\.length in 8\.\.200/);
  assert.match(identityStore, /\^\[A-Za-z0-9_-\]\{16,200\}\$/);
  assert.match(identityStore, /\$PendingRecordVersion\|\$fingerprint\|\$idempotencyKey/);
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
