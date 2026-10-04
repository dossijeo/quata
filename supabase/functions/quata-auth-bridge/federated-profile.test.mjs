import assert from "node:assert/strict";
import test from "node:test";
import { resolveFederatedProfile } from "./federated-profile.mjs";

test("federated login accepts exactly one active profile", () => {
  const profile = { id: "profile-1", auth_user_id: "auth-1", account_status: "active" };
  assert.deepEqual(resolveFederatedProfile([profile]), { profile });
});

test("federated login cannot create, guess or choose a profile", () => {
  assert.deepEqual(resolveFederatedProfile([]), {
    error: "federated_profile_not_linked",
    status: 403,
  });
  assert.deepEqual(resolveFederatedProfile([{ id: "one" }, { id: "two" }]), {
    error: "federated_profile_ambiguous",
    status: 403,
  });
});

test("federated login rejects a deactivated profile", () => {
  assert.deepEqual(resolveFederatedProfile([{ id: "profile-1", account_status: "deactivated" }]), {
    error: "account_deactivated",
    status: 403,
  });
  assert.deepEqual(resolveFederatedProfile([{ id: "profile-1", deactivated_at: "2026-10-03T00:00:00Z" }]), {
    error: "account_deactivated",
    status: 403,
  });
});
