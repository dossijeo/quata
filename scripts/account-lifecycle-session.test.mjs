import assert from "node:assert/strict";
import test from "node:test";
import { loginAccountLifecycleSession } from "./e2e-fixtures/account-lifecycle-session.mjs";

function scenario() {
  const record = { runId: "11111111-1111-4111-8111-111111111111", profileId: "22222222-2222-4222-8222-222222222222",
    authUserId: "33333333-3333-4333-8333-333333333333", countryCode: "240", phone: "12345678901" };
  const ticket = { ...record, purpose: "account_lifecycle", clientInstanceId: "client-instance-1" };
  let saved = { ...record, state: { sessions: [structuredClone(ticket)] } };
  const events = [];
  const client = { query: async () => ({ rowCount: 1, rows: [{ owned: true, unique_active: true, phone_matches: true, no_sessions: true }] }) };
  const journal = { read: async () => structuredClone(saved), checkpoint: async (state) => {
    events.push("checkpoint"); saved = { ...saved, state: structuredClone(state) };
  } };
  const body = { profile: { id: record.profileId }, session: { access_token: "access", refresh_token: "refresh", expires_at: 123 },
    web_session: { token: "web" } };
  const args = { client, journal, record, ticket, password: "synthetic-password-only-123",
    backendUrl: "https://example.invalid", publicKey: "sb_publishable_fixture",
    fetchImpl: async () => { events.push("fetch"); return { status: 200, json: async () => body }; },
    recordReceipt: async () => true };
  return { args, events, state: () => saved };
}

test("login proves exclusive ownership and journals intent before transport", async () => {
  const value = scenario();
  const session = await loginAccountLifecycleSession(value.args);
  assert.equal(session.profileId, value.args.record.profileId);
  assert.ok(value.events.indexOf("checkpoint") < value.events.indexOf("fetch"));
  await assert.rejects(loginAccountLifecycleSession(value.args), /ticket_unavailable/);
});

test("uncertain login response remains non-repeatable", async () => {
  const value = scenario();
  value.args.fetchImpl = async () => { throw new Error("network detail"); };
  await assert.rejects(loginAccountLifecycleSession(value.args), /response_uncertain/);
  assert.equal(value.state().state.sessions[0].requestStarted, true);
  await assert.rejects(loginAccountLifecycleSession(value.args), /ticket_unavailable/);
});

test("foreign or previously used actor is rejected before transport", async () => {
  for (const key of ["owned", "unique_active", "phone_matches", "no_sessions"]) {
    const value = scenario();
    value.args.client.query = async () => ({ rowCount: 1, rows: [{ owned: true, unique_active: true, phone_matches: true,
      no_sessions: true, [key]: false }] });
    await assert.rejects(loginAccountLifecycleSession(value.args), /exclusive_fixture/);
    assert.ok(!value.events.includes("fetch"));
  }
});

test("non-success response is journaled privately and never retried", async () => {
  const value = scenario();
  value.args.fetchImpl = async () => ({ status: 401, json: async () => ({ error: "invalid_credentials" }) });
  await assert.rejects(loginAccountLifecycleSession(value.args), /unresolved_response/);
  assert.equal(value.state().state.sessions[0].privateLoginResponse.status, 401);
  await assert.rejects(loginAccountLifecycleSession(value.args), /ticket_unavailable/);
});
