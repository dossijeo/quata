import test from "node:test";
import assert from "node:assert/strict";
import { cleanupRegistration } from "./cleanup-contract.mjs";
const record = { id: "r", profileId: "p", authUserId: "a" };

test("cleanup purges session, profile and Auth in safe order and audits", async () => {
  const calls = [];
  const deps = {
    revokeWebSessions: async () => calls.push("sessions"), deleteProfile: async () => calls.push("profile"),
    deleteAuthUser: async () => calls.push("auth"), markCompleted: async () => calls.push("ledger"),
    markCleanupRequired: async () => calls.push("retry"), alert: async (kind) => calls.push(kind),
  };
  await cleanupRegistration(record, deps);
  assert.deepEqual(calls, ["sessions", "profile", "auth", "ledger", "registration_cleanup_completed"]);
});

test("cleanup failure stays quarantined and alerts", async () => {
  const calls = [];
  const deps = {
    revokeWebSessions: async () => calls.push("sessions"), deleteProfile: async () => { throw Error("fail"); },
    deleteAuthUser: async () => calls.push("auth"), markCompleted: async () => calls.push("ledger"),
    markCleanupRequired: async () => calls.push("retry"), alert: async (kind) => calls.push(kind),
  };
  await assert.rejects(cleanupRegistration(record, deps));
  assert.deepEqual(calls, ["sessions", "retry", "registration_cleanup_required"]);
});
