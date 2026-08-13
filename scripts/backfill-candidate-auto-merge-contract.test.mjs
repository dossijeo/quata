import assert from "node:assert/strict";
import test from "node:test";

import { requiredFinalChecks, validateBackfillCandidate } from "./backfill-candidate-auto-merge.mjs";

const repository = { allow_auto_merge: true, allow_squash_merge: true };

function check(name, overrides = {}) {
  return {
    name,
    status: "COMPLETED",
    conclusion: "SUCCESS",
    startedAt: "2026-08-13T18:00:00Z",
    completedAt: "2026-08-13T18:01:00Z",
    ...overrides,
  };
}

function pr(overrides = {}) {
  return {
    number: 247,
    isDraft: false,
    headRefName: "codex/chat-group-sos-evidence",
    headRefOid: "a".repeat(40),
    baseRefName: "main",
    labels: [{ name: "candidate-final" }],
    statusCheckRollup: requiredFinalChecks.map((name) => check(name)),
    mergeStateStatus: "CLEAN",
    mergeable: "MERGEABLE",
    autoMergeRequest: null,
    reviewDecision: "",
    ...overrides,
  };
}

test("backfill enables only valid candidate-final PRs missing native auto-merge", () => {
  const result = validateBackfillCandidate({ pullRequest: pr(), repository });
  assert.equal(result.ok, true);
  assert.deepEqual(result.failures, []);
  assert.equal(result.headRefOid, "a".repeat(40));
});

test("backfill skips conflicts, drafts, missing label and already armed candidates", () => {
  for (const [pullRequest, expected] of [
    [pr({ isDraft: true }), /draft_pr/],
    [pr({ labels: [] }), /missing_candidate_final/],
    [pr({ autoMergeRequest: { mergeMethod: "SQUASH" } }), /auto_merge_already_enabled/],
    [pr({ mergeStateStatus: "DIRTY", mergeable: "CONFLICTING" }), /merge_conflict/],
  ]) {
    assert.match(validateBackfillCandidate({ pullRequest, repository }).failures.join("\n"), expected);
  }
});

test("backfill fails closed when final gates are missing, pending or failed", () => {
  assert.match(validateBackfillCandidate({
    pullRequest: pr({ statusCheckRollup: [] }),
    repository,
  }).failures.join("\n"), /missing_required_check:PR fast contracts and focal imports/);

  assert.match(validateBackfillCandidate({
    pullRequest: pr({ statusCheckRollup: [check(requiredFinalChecks[0], { status: "IN_PROGRESS", conclusion: "" })] }),
    repository,
    requiredChecks: [requiredFinalChecks[0]],
  }).failures.join("\n"), /pending_required_check/);

  assert.match(validateBackfillCandidate({
    pullRequest: pr({ statusCheckRollup: [check(requiredFinalChecks[0], { conclusion: "FAILURE" })] }),
    repository,
    requiredChecks: [requiredFinalChecks[0]],
  }).failures.join("\n"), /required_check_not_successful/);
});

test("backfill uses the latest check per name so superseded cancelled runs do not block", () => {
  const result = validateBackfillCandidate({
    pullRequest: pr({
      statusCheckRollup: [
        check(requiredFinalChecks[0], { conclusion: "CANCELLED", completedAt: "2026-08-13T18:00:10Z" }),
        check(requiredFinalChecks[0], { conclusion: "SUCCESS", completedAt: "2026-08-13T18:02:00Z" }),
      ],
    }),
    repository,
    requiredChecks: [requiredFinalChecks[0]],
  });
  assert.equal(result.ok, true);
});
