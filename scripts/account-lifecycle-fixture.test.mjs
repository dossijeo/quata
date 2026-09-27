import assert from "node:assert/strict";
import test from "node:test";
import {
  createAccountLifecycleFixture,
  retireAccountLifecycleFixture,
  verifyAccountDeactivated,
  verifyAccountDeleted,
} from "./e2e-fixtures/account-lifecycle.mjs";

function fixture() {
  const record = {
    runId: "11111111-1111-4111-8111-111111111111",
    profileId: "22222222-2222-4222-8222-222222222222",
    authUserId: "33333333-3333-4333-8333-333333333333",
    email: "account-lifecycle-33333333-3333-4333-8333-333333333333@example.invalid",
    countryCode: "240",
    phone: "12345678901",
  };
  let saved = { ...record, state: {} };
  const events = [];
  const row = (rows) => ({ rowCount: rows.length, rows });
  const absent = { auth: true, profile: true, legacy_profile: true, identities: true, sessions: true,
    web_sessions: true, push_tokens: true, deletion_request: true, storage: true };
  const client = { query: async (sql) => {
    events.push(sql);
    if (sql.includes("as auth_absent")) return row([{ auth_absent: true, profile_absent: true }]);
    if (sql.includes("select id from auth.users")) return row([{ id: record.authUserId }]);
    if (sql.includes("as banned_auth_count")) return row([{ profile_count: 1, banned_auth_count: 1, push_count: 0, web_session_count: 0 }]);
    if (sql.includes("as legacy_profile")) return row([absent]);
    if (sql.includes("select id,email")) return row([{ id: record.authUserId, email: record.email,
      owner: { unit: "ACCOUNT-LIFECYCLE", run_id: record.runId } }]);
    if (sql.includes("select id,auth_user_id,deactivated_auth_user_id")) return row([{ id: record.profileId,
      auth_user_id: null, deactivated_auth_user_id: record.authUserId, account_status: "deactivated" }]);
    if (sql.includes("from public.community_posts")) return row([{ count: 0 }]);
    return row([]);
  } };
  const journal = {
    read: async () => structuredClone(saved),
    checkpoint: async (state) => { events.push("checkpoint"); saved = { ...saved, state: structuredClone(state) }; },
  };
  const args = {
    client,
    journal,
    record,
    password: "synthetic-password-only-123",
    operationsSettled: async () => true,
    adminRequest: async ({ body }) => {
      events.push("admin-request");
      assert.equal(saved.state.fixtureCreationStarted, true);
      return { status: 200, body: { id: body.id } };
    },
  };
  return { args, client, events, record, state: () => saved };
}

test("creation journals intent before exact owned Auth and profile creation", async () => {
  const f = fixture();
  await createAccountLifecycleFixture(f.args);
  assert.ok(f.events.indexOf("checkpoint") < f.events.indexOf("admin-request"));
  assert.equal(f.state().state.fixtureCreated, true);
  await assert.rejects(createAccountLifecycleFixture(f.args), /already_started/);
});

test("uncertain Auth creation is never retried", async () => {
  const f = fixture();
  f.args.adminRequest = async () => { throw new Error("private detail"); };
  await assert.rejects(createAccountLifecycleFixture(f.args), /auth_creation_uncertain/);
  assert.equal(f.state().state.fixtureCreationStarted, true);
  await assert.rejects(createAccountLifecycleFixture(f.args), /already_started/);
});

test("deactivation requires database, ban, session and protected-action proof", async () => {
  const f = fixture();
  assert.deepEqual(await verifyAccountDeactivated({ client: f.client, record: f.record,
    sessionRejected: async () => true, protectedActionRejected: async () => true }),
  { deactivated: true, sessionRejected: true, protectedActionRejected: true });
  await assert.rejects(verifyAccountDeactivated({ client: f.client, record: f.record,
    sessionRejected: async () => false, protectedActionRejected: async () => true }), /not_verified/);
});

test("deletion requires every owned residue class to be absent", async () => {
  const f = fixture();
  assert.equal((await verifyAccountDeleted({ client: f.client, record: f.record })).deleted, true);
  const query = f.client.query;
  f.client.query = async (sql, args) => sql.includes("as legacy_profile")
    ? { rows: [{ auth: true, profile: true, legacy_profile: true, identities: true, sessions: true,
      web_sessions: true, push_tokens: true, deletion_request: false, storage: true }] }
    : query(sql, args);
  await assert.rejects(verifyAccountDeleted({ client: f.client, record: f.record }), /deletion_residue/);
});

test("deactivated synthetic fixture retires only after ownership and dependency proof", async () => {
  const f = fixture();
  await createAccountLifecycleFixture(f.args);
  f.events.length = 0;
  assert.deepEqual(await retireAccountLifecycleFixture(f.args), { retired: true });
  assert.equal(f.state().state.fixtureRetired, true);
  assert.equal(f.events.filter((event) => event.startsWith?.("delete ")).length, 2);
});

test("an active fixture left by a failed product attempt is also recoverable", async () => {
  const f = fixture();
  await createAccountLifecycleFixture(f.args);
  const query = f.client.query;
  f.client.query = async (sql, args) => sql.includes("select id,auth_user_id,deactivated_auth_user_id")
    ? { rowCount: 1, rows: [{ id: f.record.profileId, auth_user_id: f.record.authUserId,
      deactivated_auth_user_id: null, account_status: "active" }] }
    : query(sql, args);
  assert.deepEqual(await retireAccountLifecycleFixture(f.args), { retired: true });
});

test("only a journaled login permits the exact bridge-normalized Auth email", async () => {
  for (const journaled of [true, false]) {
    const f = fixture();
    await createAccountLifecycleFixture(f.args);
    if (journaled) {
      const value = await f.args.journal.read();
      value.state.sessions = [{ runId: f.record.runId, profileId: f.record.profileId,
        authUserId: f.record.authUserId, requestStarted: true }];
      await f.args.journal.checkpoint(value.state);
    }
    const query = f.client.query;
    f.client.query = async (sql, args) => sql.includes("select id,email")
      ? { rowCount: 1, rows: [{ id: f.record.authUserId, email: `${f.record.countryCode}${f.record.phone}@phone.quata.app`,
        owner: { unit: "ACCOUNT-LIFECYCLE", run_id: f.record.runId } }] }
      : query(sql, args);
    if (journaled) assert.deepEqual(await retireAccountLifecycleFixture(f.args), { retired: true });
    else await assert.rejects(retireAccountLifecycleFixture(f.args), /retirement_unresolved/);
  }
});

test("foreign ownership, unsettled transport or dependency aborts before deletion", async () => {
  for (const scenario of ["owner", "transport", "dependency"]) {
    const f = fixture();
    await createAccountLifecycleFixture(f.args);
    f.events.length = 0;
    const query = f.client.query;
    if (scenario === "transport") f.args.operationsSettled = async () => false;
    else f.client.query = async (sql, args) => {
      if (scenario === "owner" && sql.includes("select id,email")) return { rowCount: 0, rows: [] };
      if (scenario === "dependency" && sql.includes("from public.community_posts")) return { rows: [{ count: 1 }] };
      return query(sql, args);
    };
    await assert.rejects(retireAccountLifecycleFixture(f.args));
    assert.ok(!f.events.some((event) => event.startsWith?.("delete ")));
  }
});
