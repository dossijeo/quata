import assert from "node:assert/strict";
import test from "node:test";
import { requireUnlinkedAuthEmailAvailable } from "./auth-user-link.mjs";

test("an unlinked profile never reuses an existing auth email", () => {
  assert.doesNotThrow(() => requireUnlinkedAuthEmailAvailable(null));
  assert.throws(
    () => requireUnlinkedAuthEmailAvailable({ id: "auth-1", user_metadata: { profile_id: "profile-1" } }),
    /auth_user_email_collision/,
  );
  assert.throws(
    () => requireUnlinkedAuthEmailAvailable({ id: "auth-2", user_metadata: {} }),
    /auth_user_email_collision/,
  );
});
