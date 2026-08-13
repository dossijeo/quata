import assert from "node:assert/strict";
import test from "node:test";

import { diagnoseAutoMergeState } from "./diagnose-auto-merge.mjs";

const required = [
  "PR fast contracts and focal imports",
  "iOS fast contracts",
  "Web/Android final certification gate",
  "iOS final certification gate",
  "CodeQL final security gate",
];

function pr(overrides = {}) {
  return {
    state: "OPEN",
    isDraft: false,
    labels: [{ name: "candidate-final" }],
    autoMergeRequest: { mergeMethod: "SQUASH" },
    mergeStateStatus: "BLOCKED",
    reviewDecision: "",
    statusCheckRollup: required.map((name) => ({ name, status: "COMPLETED", conclusion: "SUCCESS" })),
    ...overrides,
  };
}

test("auto-merge anomaly diagnosis distinguishes happy waiting from missing authorization", () => {
  assert.deepEqual(diagnoseAutoMergeState({
    pullRequest: pr({ mergeStateStatus: "CLEAN" }),
    requiredChecks: required,
  }).reasons, ["waiting_for_github_auto_merge"]);

  assert.match(diagnoseAutoMergeState({
    pullRequest: pr({ autoMergeRequest: null }),
    requiredChecks: required,
  }).reasons.join("\n"), /auto_merge_not_enabled/);
});

test("auto-merge anomaly diagnosis reports missing, pending and failed required checks", () => {
  const missing = diagnoseAutoMergeState({
    pullRequest: pr({ statusCheckRollup: [] }),
    requiredChecks: required,
  });
  assert.match(missing.reasons.join("\n"), /missing_required_check:PR fast contracts and focal imports/);

  const pending = diagnoseAutoMergeState({
    pullRequest: pr({ statusCheckRollup: [{ name: required[0], status: "IN_PROGRESS", conclusion: "" }] }),
    requiredChecks: [required[0]],
  });
  assert.match(pending.reasons.join("\n"), /pending_required_check:PR fast contracts and focal imports/);

  const failed = diagnoseAutoMergeState({
    pullRequest: pr({ statusCheckRollup: [{ name: required[2], status: "COMPLETED", conclusion: "FAILURE" }] }),
    requiredChecks: [required[2]],
  });
  assert.match(failed.reasons.join("\n"), /failed_required_check:Web\/Android final certification gate/);
});

test("auto-merge anomaly diagnosis reports review, draft, conflict and strict-base blockers", () => {
  for (const [pullRequest, expected] of [
    [pr({ isDraft: true }), /draft/],
    [pr({ labels: [] }), /missing_candidate_final/],
    [pr({ mergeStateStatus: "DIRTY" }), /merge_conflict/],
    [pr({ mergeStateStatus: "BEHIND" }), /branch_behind_strict_base/],
    [pr({ reviewDecision: "REVIEW_REQUIRED" }), /review_required/],
    [pr({ reviewDecision: "CHANGES_REQUESTED" }), /changes_requested/],
  ]) {
    assert.match(diagnoseAutoMergeState({ pullRequest, requiredChecks: required }).reasons.join("\n"), expected);
  }
});
