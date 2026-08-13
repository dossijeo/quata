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

test("candidate-final promotion requires frozen SHA, non-draft PR, stable gates and native auto-merge", () => {
  assert.deepEqual(validatePromotionState({
    pullRequest: pr(),
    repository,
    frozenSha: "a".repeat(40),
  }), { ok: true, failures: [], alreadyCandidateFinal: false });

  assert.match(script, /enablePullRequestAutoMerge/);
  assert.match(script, /mergeMethod:SQUASH/);
  assert.match(script, /--add-label", "candidate-final"/);
  assert.doesNotMatch(script, /gh", \["pr", "merge"/);
});

test("promotion fails closed for SHA drift, drafts, disabled auto-merge or missing final gates", () => {
  const cases = [
    [pr({ headRefOid: "b".repeat(40) }), repository, /frozen_sha_mismatch/],
    [pr({ isDraft: true }), repository, /draft_pr_cannot_be_candidate_final/],
    [pr(), { allow_auto_merge: false, allow_squash_merge: true }, /repository_auto_merge_disabled/],
    [pr(), { allow_auto_merge: true, allow_squash_merge: false }, /repository_squash_merge_disabled/],
    [pr({ statusCheckRollup: [] }), repository, /missing_required_check:PR fast contracts and focal imports/],
    [pr({ statusCheckRollup: [{ name: "Analyze java-kotlin" }] }), repository, /missing_required_check:CodeQL final security gate/],
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
