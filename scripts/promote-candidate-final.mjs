#!/usr/bin/env node
import { execFileSync } from "node:child_process";

const expectedStableGates = [
  "PR fast contracts and focal imports",
  "iOS fast contracts",
  "Web/Android final certification gate",
  "iOS final certification gate",
  "CodeQL final security gate",
];

function usage() {
  return [
    "Usage: node scripts/promote-candidate-final.mjs --pr <number> --sha <frozen-head-sha> [--dry-run]",
    "",
    "Freezes a PR operationally by applying candidate-final and enabling GitHub native auto-merge.",
  ].join("\n");
}

function parseArgs(argv) {
  const args = { dryRun: false };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === "--dry-run") {
      args.dryRun = true;
    } else if (value === "--pr") {
      args.pr = argv[++index];
    } else if (value === "--sha") {
      args.sha = argv[++index];
    } else {
      throw new Error(`unknown_argument:${value}`);
    }
  }
  if (!/^[1-9][0-9]*$/.test(String(args.pr ?? ""))) throw new Error("missing_or_invalid_pr");
  if (!/^[0-9a-f]{40}$/i.test(String(args.sha ?? ""))) throw new Error("missing_or_invalid_sha");
  return args;
}

function gh(args, options = {}) {
  return execFileSync("gh", args, {
    encoding: "utf8",
    stdio: options.stdio ?? ["ignore", "pipe", "pipe"],
  });
}

function prState(number) {
  return JSON.parse(gh([
    "pr",
    "view",
    String(number),
    "--json",
    "id,number,isDraft,headRefOid,labels,statusCheckRollup,mergeStateStatus",
  ]));
}

function repoState() {
  return JSON.parse(gh([
    "api",
    "repos/dossijeo/quata",
    "--jq",
    "{allow_auto_merge,allow_squash_merge,allow_merge_commit,allow_rebase_merge}",
  ]));
}

export function validatePromotionState({ pullRequest, repository, frozenSha }) {
  const failures = [];
  if (!repository?.allow_auto_merge) failures.push("repository_auto_merge_disabled");
  if (!repository?.allow_squash_merge) failures.push("repository_squash_merge_disabled");
  if (pullRequest?.isDraft) failures.push("draft_pr_cannot_be_candidate_final");
  if (pullRequest?.headRefOid !== frozenSha) failures.push(`frozen_sha_mismatch:${pullRequest?.headRefOid ?? "missing"}`);
  const labels = new Set((pullRequest?.labels ?? []).map((label) => label.name));
  return {
    ok: failures.length === 0,
    failures,
    alreadyCandidateFinal: labels.has("candidate-final"),
    expectedStableGates,
  };
}

function enableAutoMergePullRequestId(prId, dryRun) {
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

function applyCandidateFinal(number, dryRun) {
  if (dryRun) return;
  gh(["pr", "edit", String(number), "--add-label", "candidate-final"], { stdio: "inherit" });
}

export function promoteCandidateFinal({ number, frozenSha, dryRun = false }) {
  const repository = repoState();
  const pullRequest = prState(number);
  const validation = validatePromotionState({ pullRequest, repository, frozenSha });
  if (!validation.ok) {
    return { ok: false, failures: validation.failures, number, frozenSha };
  }
  if (!validation.alreadyCandidateFinal) applyCandidateFinal(number, dryRun);
  enableAutoMergePullRequestId(pullRequest.id, dryRun);
  return {
    ok: true,
    number,
    frozenSha,
    candidateFinalApplied: !validation.alreadyCandidateFinal,
    autoMergeRequested: true,
    mergeMethod: "SQUASH",
    dryRun,
  };
}

if (import.meta.url === `file:///${process.argv[1].replaceAll("\\", "/")}`) {
  try {
    const args = parseArgs(process.argv.slice(2));
    const result = promoteCandidateFinal({
      number: Number(args.pr),
      frozenSha: args.sha,
      dryRun: args.dryRun,
    });
    console.log(JSON.stringify(result, null, 2));
    process.exit(result.ok ? 0 : 1);
  } catch (error) {
    console.error(error?.message ?? error);
    console.error(usage());
    process.exit(2);
  }
}
