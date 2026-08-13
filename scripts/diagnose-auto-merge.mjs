#!/usr/bin/env node

export function diagnoseAutoMergeState({ pullRequest, requiredChecks = [] }) {
  const labels = new Set((pullRequest?.labels ?? []).map((label) => label.name));
  const checks = pullRequest?.statusCheckRollup ?? [];
  const byName = new Map();
  for (const check of checks) {
    if (!byName.has(check.name) || check.status !== "COMPLETED") byName.set(check.name, check);
  }
  const failures = checks
    .filter((check) => check.status === "COMPLETED" && ["FAILURE", "TIMED_OUT", "ACTION_REQUIRED"].includes(check.conclusion))
    .map((check) => check.name);
  const missingRequired = requiredChecks.filter((name) => !byName.has(name));
  const pendingRequired = requiredChecks.filter((name) => {
    const check = byName.get(name);
    return check && check.status !== "COMPLETED";
  });
  const failedRequired = requiredChecks.filter((name) => failures.includes(name));
  const requiredNotSuccessful = requiredChecks.filter((name) => {
    const check = byName.get(name);
    return check && check.status === "COMPLETED" && !["SUCCESS", "NEUTRAL", "SKIPPED"].includes(check.conclusion);
  });

  const reasons = [];
  if (pullRequest?.state === "MERGED") reasons.push("merged");
  if (pullRequest?.isDraft) reasons.push("draft");
  if (!labels.has("candidate-final")) reasons.push("missing_candidate_final");
  if (!pullRequest?.autoMergeRequest) reasons.push("auto_merge_not_enabled");
  if (pullRequest?.mergeStateStatus === "DIRTY") reasons.push("merge_conflict");
  if (pullRequest?.mergeStateStatus === "BEHIND") reasons.push("branch_behind_strict_base");
  if (pullRequest?.reviewDecision === "CHANGES_REQUESTED") reasons.push("changes_requested");
  if (pullRequest?.reviewDecision === "REVIEW_REQUIRED") reasons.push("review_required");
  if (missingRequired.length) reasons.push(`missing_required_check:${missingRequired.join(",")}`);
  if (pendingRequired.length) reasons.push(`pending_required_check:${pendingRequired.join(",")}`);
  if (failedRequired.length) reasons.push(`failed_required_check:${failedRequired.join(",")}`);
  if (requiredNotSuccessful.length) reasons.push(`required_check_not_successful:${requiredNotSuccessful.join(",")}`);
  if (!reasons.length && pullRequest?.mergeStateStatus === "BLOCKED") reasons.push("blocked_by_branch_protection_or_conversations");
  if (!reasons.length) reasons.push("waiting_for_github_auto_merge");

  return {
    state: pullRequest?.state ?? "UNKNOWN",
    mergeStateStatus: pullRequest?.mergeStateStatus ?? "UNKNOWN",
    autoMergeEnabled: Boolean(pullRequest?.autoMergeRequest),
    candidateFinal: labels.has("candidate-final"),
    missingRequired,
    pendingRequired,
    failedRequired,
    reasons,
  };
}

if (import.meta.url === `file:///${process.argv[1].replaceAll("\\", "/")}`) {
  console.error("diagnose-auto-merge.mjs is a library; query GitHub state from the orchestrator or a Spark anomaly watcher.");
  process.exit(2);
}
