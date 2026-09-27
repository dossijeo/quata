import assert from "node:assert/strict";
import test from "node:test";
import { prepareReversibleProfileFollow } from "./reversible-profile-follow.mjs";

test("a committed follow insert is restored when the presence poll fails", async () => {
  let edgePresent = false;
  let retainedSnapshot = null;
  const failure = new Error("poll_failed_after_insert");

  await assert.rejects(
    prepareReversibleProfileFollow({
      exists: async () => edgePresent,
      insert: async () => { edgePresent = true; },
      pollPresent: async () => { throw failure; },
      restore: async (initiallyFollowing) => { edgePresent = initiallyFollowing; },
      retainSnapshot: (snapshot) => { retainedSnapshot = snapshot; },
    }),
    failure,
  );

  assert.deepEqual(retainedSnapshot, { initiallyFollowing: false });
  assert.equal(edgePresent, false);
});

test("the retained snapshot lets the outer finally retry after immediate restore also fails", async () => {
  let retainedSnapshot = null;
  await assert.rejects(
    prepareReversibleProfileFollow({
      exists: async () => false,
      insert: async () => {},
      pollPresent: async () => { throw new Error("poll_failed_after_insert"); },
      restore: async () => { throw new Error("transient_restore_failure"); },
      retainSnapshot: (snapshot) => { retainedSnapshot = snapshot; },
    }),
    /poll_failed_after_insert/,
  );
  assert.deepEqual(retainedSnapshot, { initiallyFollowing: false });
});
