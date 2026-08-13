import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import { validatePromotionState } from "./promote-candidate-final.mjs";

const script = await readFile(new URL("./promote-candidate-final.mjs", import.meta.url), "utf8");

function pr(overrides = {}) {
  return {
    isDraft: false,
    headRefOid: "a".repeat(40),
    labels: [],
    statusCheckRollup: [
      "PR fast contracts and focal imports",
      "iOS fast contracts",
      "Web/Android final certification gate",
      "iOS final certification gate",
      "CodeQL final security gate",
    ].map((name) => ({ name })),
    ...overrides,
  };
}

const repository = { allow_auto_merge: true, allow_squash_merge: true };

test("candidate-final promotion requires frozen SHA, non-draft PR and native auto-merge", () => {
  const result = validatePromotionState({
    pullRequest: pr(),
    repository,
    frozenSha: "a".repeat(40),
  });
  assert.equal(result.ok, true);
  assert.deepEqual(result.failures, []);
  assert.equal(result.alreadyCandidateFinal, false);
  assert.deepEqual(result.expectedStableGates, [
    "PR fast contracts and focal imports",
    "iOS fast contracts",
    "Web/Android final certification gate",
    "iOS final certification gate",
    "CodeQL final security gate",
  ]);

  assert.match(script, /enablePullRequestAutoMerge/);
  assert.match(script, /mergeMethod:SQUASH/);
  assert.match(script, /--add-label", "candidate-final"/);
  assert.doesNotMatch(script, /gh", \["pr", "merge"/);
});

test("promotion fails closed for SHA drift, drafts or disabled auto-merge", () => {
  const cases = [
    [pr({ headRefOid: "b".repeat(40) }), repository, /frozen_sha_mismatch/],
    [pr({ isDraft: true }), repository, /draft_pr_cannot_be_candidate_final/],
    [pr(), { allow_auto_merge: false, allow_squash_merge: true }, /repository_auto_merge_disabled/],
    [pr(), { allow_auto_merge: true, allow_squash_merge: false }, /repository_squash_merge_disabled/],
  ];

  for (const [pullRequest, repo, expected] of cases) {
    const result = validatePromotionState({ pullRequest, repository: repo, frozenSha: "a".repeat(40) });
    assert.equal(result.ok, false);
    assert.match(result.failures.join("\n"), expected);
  }
});

test("candidate-final label is not re-applied when already present", () => {
  const result = validatePromotionState({
    pullRequest: pr({ labels: [{ name: "candidate-final" }] }),
    repository,
    frozenSha: "a".repeat(40),
  });
  assert.equal(result.ok, true);
  assert.equal(result.alreadyCandidateFinal, true);
});
