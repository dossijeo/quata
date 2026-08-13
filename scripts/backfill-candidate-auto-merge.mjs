#!/usr/bin/env node
import { execFileSync } from "node:child_process";

export const requiredFinalChecks = [
  "PR fast contracts and focal imports",
  "iOS fast contracts",
  "Web/Android final certification gate",
  "iOS final certification gate",
  "CodeQL final security gate",
];

function usage() {
  return [
    "Usage: node scripts/backfill-candidate-auto-merge.mjs [--pr <number>] [--dry-run]",
    "",
    "Enables GitHub native auto-merge for already-frozen candidate-final PRs that are still valid.",
  ].join("\n");
}

function parseArgs(argv) {
  const args = { dryRun: false };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === "--dry-run") args.dryRun = true;
    else if (value === "--pr") args.pr = argv[++index];
    else throw new Error(`unknown_argument:${value}`);
  }
  if (args.pr !== undefined && !/^[1-9][0-9]*$/.test(String(args.pr))) throw new Error("invalid_pr");
  return args;
}

function gh(args, options = {}) {
  return execFileSync("gh", args, {
    encoding: "utf8",
    stdio: options.stdio ?? ["ignore", "pipe", "pipe"],
  });
}

function repoState() {
  return JSON.parse(gh([
    "api",
    "repos/dossijeo/quata",
    "--jq",
    "{allow_auto_merge,allow_squash_merge}",
  ]));
}

function candidatePrs(number) {
  if (number) {
    return [JSON.parse(gh([
      "pr",
      "view",
      String(number),
      "--json",
      "id,number,isDraft,headRefName,headRefOid,baseRefName,labels,statusCheckRollup,mergeStateStatus,mergeable,autoMergeRequest,reviewDecision",
    ]))];
  }
  return JSON.parse(gh([
    "pr",
    "list",
    "--state",
    "open",
    "--label",
    "candidate-final",
    "--json",
    "id,number,isDraft,headRefName,headRefOid,baseRefName,labels,statusCheckRollup,mergeStateStatus,mergeable,autoMergeRequest,reviewDecision",
  ]));
}

function latestChecksByName(checks) {
  const byName = new Map();
  for (const check of checks ?? []) {
    const previous = byName.get(check.name);
    if (!previous || checkTimestamp(check) >= checkTimestamp(previous)) byName.set(check.name, check);
  }
  return byName;
}

function checkTimestamp(check) {
  const stamp = Date.parse(check?.completedAt || check?.startedAt || "");
  return Number.isFinite(stamp) ? stamp : 0;
}

export function validateBackfillCandidate({ pullRequest, repository, requiredChecks = requiredFinalChecks }) {
  const labels = new Set((pullRequest?.labels ?? []).map((label) => label.name));
  const checks = latestChecksByName(pullRequest?.statusCheckRollup);
  const failures = [];
  if (!repository?.allow_auto_merge) failures.push("repository_auto_merge_disabled");
  if (!repository?.allow_squash_merge) failures.push("repository_squash_merge_disabled");
  if (!labels.has("candidate-final")) failures.push("missing_candidate_final");
  if (pullRequest?.isDraft) failures.push("draft_pr");
  if (pullRequest?.autoMergeRequest) failures.push("auto_merge_already_enabled");
  if (pullRequest?.baseRefName !== "main") failures.push(`unexpected_base:${pullRequest?.baseRefName ?? "missing"}`);
  if (pullRequest?.mergeStateStatus === "DIRTY" || pullRequest?.mergeable === "CONFLICTING") failures.push("merge_conflict");
  if (pullRequest?.mergeStateStatus === "UNKNOWN" || pullRequest?.mergeable === "UNKNOWN") failures.push("mergeability_unknown");
  if (pullRequest?.reviewDecision === "CHANGES_REQUESTED") failures.push("changes_requested");

  for (const name of requiredChecks) {
    const check = checks.get(name);
    if (!check) {
      failures.push(`missing_required_check:${name}`);
    } else if (check.status !== "COMPLETED") {
      failures.push(`pending_required_check:${name}`);
    } else if (!["SUCCESS", "NEUTRAL", "SKIPPED"].includes(check.conclusion)) {
      failures.push(`required_check_not_successful:${name}:${check.conclusion ?? "missing"}`);
    }
  }

  return {
    ok: failures.length === 0,
    failures,
    number: pullRequest?.number,
    headRefName: pullRequest?.headRefName,
    headRefOid: pullRequest?.headRefOid,
  };
}

function enableAutoMerge(prId, dryRun) {
  if (dryRun) return;
  gh([
    "api",
    "graphql",
    "-f",
    "query=mutation($pullRequestId:ID!){enablePullRequestAutoMerge(input:{pullRequestId:$pullRequestId,mergeMethod:SQUASH}){pullRequest{number,autoMergeRequest{enabledAt,mergeMethod}}}}",
    "-F",
    `pullRequestId=${prId}`,
  ], { stdio: "inherit" });
}

export function backfillCandidateAutoMerge({ number, dryRun = false } = {}) {
  const repository = repoState();
  const results = [];
  for (const pullRequest of candidatePrs(number)) {
    const validation = validateBackfillCandidate({ pullRequest, repository });
    if (validation.ok) enableAutoMerge(pullRequest.id, dryRun);
    results.push({ ...validation, action: validation.ok ? "auto_merge_enabled" : "skipped", dryRun });
  }
  return results;
}

if (import.meta.url === `file:///${process.argv[1].replaceAll("\\", "/")}`) {
  try {
    const args = parseArgs(process.argv.slice(2));
    const results = backfillCandidateAutoMerge({ number: args.pr ? Number(args.pr) : undefined, dryRun: args.dryRun });
    console.log(JSON.stringify(results, null, 2));
    process.exit(results.every((result) => result.ok || result.failures.includes("auto_merge_already_enabled")) ? 0 : 1);
  } catch (error) {
    console.error(error?.message ?? error);
    console.error(usage());
    process.exit(2);
  }
}
