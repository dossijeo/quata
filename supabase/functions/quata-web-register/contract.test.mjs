import test from "node:test";
import assert from "node:assert/strict";
import {
  RegistrationContractError,
  runRegistration,
  validateRegistrationPayload,
} from "./contract.mjs";
import {
  hashRecoveryAnswer,
  hashRegistrationPassword,
  verifyRegistrationPassword,
} from "../_shared/web-registration-security.mjs";

const validPayload = {
  display_name: "Gabriela",
  neighborhood: "Centro",
  country_code: "34",
  phone_local: "600100200",
  password: "LongPassword7",
  secret_question: "barrio",
  secret_answer: "Malasaña",
  client_instance_id: "browser-instance-123",
  idempotency_key: "0123456789abcdef0123456789abcdef",
};

test("validates and canonicalizes the public allowlist", () => {
  const result = validateRegistrationPayload({
    ...validPayload,
    display_name: "  Gabriela   Test  ",
    phone_local: "600 100 200",
  });
  assert.equal(result.displayName, "Gabriela Test");
  assert.equal(result.phoneLocal, "600100200");
  assert.equal(result.phoneE164, "+34600100200");
});

test("rejects privileged client-controlled fields", () => {
  assert.throws(
    () => validateRegistrationPayload({ ...validPayload, is_admin: true }),
    (error) => error instanceof RegistrationContractError && error.code === "forbidden_field",
  );
});

test("creates Auth, profile and Web session in order", async () => {
  const events = [];
  const result = await runRegistration(validPayload, dependencies(events));
  assert.deepEqual(result, { ok: true, profileId: "profile-1" });
  assert.deepEqual(events, [
    "prepare",
    "claim",
    "find-profile",
    "find-auth",
    "create-auth",
    "record-auth",
    "create-profile",
    "record-profile",
    "create-session",
    "complete",
  ]);
});

test("returns a generic duplicate result without creating identities", async () => {
  const events = [];
  await assert.rejects(
    runRegistration(validPayload, dependencies(events, {
      claim: async () => ({ kind: "conflict", record: null }),
    })),
    (error) => error instanceof RegistrationContractError &&
      error.code === "registration_unavailable" &&
      error.status === 409,
  );
  assert.deepEqual(events, ["prepare"]);
});

test("surfaces durable rate limiting with retry metadata", async () => {
  await assert.rejects(
    runRegistration(validPayload, dependencies([], {
      claim: async () => ({ kind: "rate_limited", retryAfterSeconds: 120, record: null }),
    })),
    (error) => error instanceof RegistrationContractError &&
      error.code === "rate_limited" &&
      error.retryAfterSeconds === 120,
  );
});

test("compensates a newly-created Auth user when profile creation fails", async () => {
  const events = [];
  await assert.rejects(
    runRegistration(validPayload, dependencies(events, {
      createProfile: async () => {
        events.push("create-profile");
        throw new Error("profile insert failed");
      },
    })),
    (error) => error instanceof RegistrationContractError && error.code === "registration_failed",
  );
  assert.deepEqual(events.slice(-2), ["delete-auth", "fail:compensated"]);
});

test("deletes the profile before Auth when session creation fails", async () => {
  const events = [];
  await assert.rejects(
    runRegistration(validPayload, dependencies(events, {
      createAuthenticatedResult: async () => {
        events.push("create-session");
        throw new Error("session failed");
      },
    })),
  );
  assert.deepEqual(events.slice(-3), ["delete-profile", "delete-auth", "fail:compensated"]);
});

test("records cleanup_required when Auth compensation fails", async () => {
  const events = [];
  await assert.rejects(
    runRegistration(validPayload, dependencies(events, {
      createProfile: async () => {
        events.push("create-profile");
        throw new Error("profile insert failed");
      },
      deleteAuthUser: async () => {
        events.push("delete-auth");
        throw new Error("admin unavailable");
      },
    })),
  );
  assert.deepEqual(events.slice(-2), ["delete-auth", "cleanup:auth_cleanup_failed"]);
});

test("replays a completed idempotency key without creating another user", async () => {
  const events = [];
  const result = await runRegistration(validPayload, dependencies(events, {
    claim: async () => ({
      kind: "replay",
      record: { id: "request-1", profileId: "profile-1", authUserId: "auth-1" },
    }),
  }));
  assert.deepEqual(result, { ok: true, replay: true });
  assert.deepEqual(events, ["prepare", "restore"]);
});

test("uses a slow salted password hash and peppered recovery hash", async () => {
  const passwordHash = await hashRegistrationPassword("LongPassword7", "00112233445566778899aabbccddeeff");
  assert.match(passwordHash, /^pbkdf2_sha256\$210000\$/);
  assert.equal(await verifyRegistrationPassword(passwordHash, "LongPassword7"), true);
  assert.equal(await verifyRegistrationPassword(passwordHash, "WrongPassword7"), false);
  assert.equal(
    await hashRecoveryAnswer("  Malasaña ", "p".repeat(32)),
    await hashRecoveryAnswer("malasaña", "p".repeat(32)),
  );
});

function dependencies(events, overrides = {}) {
  const record = { id: "request-1", profileId: "profile-1", authUserId: null };
  return {
    prepare: async (input) => {
      events.push("prepare");
      return input;
    },
    claim: async () => {
      events.push("claim");
      return { kind: "new", record };
    },
    findProfile: async () => {
      events.push("find-profile");
      return null;
    },
    findAuthUser: async () => {
      events.push("find-auth");
      return null;
    },
    createAuthUser: async () => {
      events.push("create-auth");
      return { id: "auth-1", profileId: "profile-1" };
    },
    recordAuthUser: async () => events.push("record-auth"),
    createProfile: async () => {
      events.push("create-profile");
      return { id: "profile-1" };
    },
    recordProfile: async () => events.push("record-profile"),
    createAuthenticatedResult: async () => {
      events.push("create-session");
      return { ok: true, profileId: "profile-1" };
    },
    restoreCompleted: async () => {
      events.push("restore");
      return { ok: true, replay: true };
    },
    complete: async () => events.push("complete"),
    deleteProfile: async () => events.push("delete-profile"),
    deleteAuthUser: async () => events.push("delete-auth"),
    fail: async (_record, code) => events.push(`fail:${code}`),
    requireCleanup: async (_record, _authId, code) => events.push(`cleanup:${code}`),
    ...overrides,
  };
}
