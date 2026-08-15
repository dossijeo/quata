#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

function usage() {
  return [
    "Usage: node scripts/cleanup-merged-worktrees.mjs [--apply] [--json] [--skip-fetch]",
    "",
    "Safely removes local codex/* worktrees and branches whose PR is confirmed merged.",
    "Default mode is dry-run. Use --apply only after reviewing the JSON plan.",
    "--skip-fetch is diagnostics-only and is rejected with --apply.",
  ].join("\n");
}

function parseArgs(argv) {
  const args = { apply: false, json: true, skipFetch: false };
  for (const value of argv) {
    if (value === "--apply") args.apply = true;
    else if (value === "--json") args.json = true;
    else if (value === "--skip-fetch") args.skipFetch = true;
    else if (value === "--help" || value === "-h") args.help = true;
    else throw new Error(`unknown_argument:${value}`);
  }
  if (args.apply && args.skipFetch) throw new Error("skip_fetch_not_allowed_with_apply");
  return args;
}

function run(command, args, options = {}) {
  return execFileSync(command, args, {
    encoding: "utf8",
    env: options.env ?? process.env,
    stdio: options.stdio ?? ["ignore", "pipe", "pipe"],
  });
}

function safeDirectoryArgs(...paths) {
  const candidates = [process.cwd(), process.env.QUATA_GIT_SAFE_DIRECTORY, ...paths]
    .filter(Boolean)
    .map((path) => String(path).replaceAll("\\", "/"));
  return [...new Set(candidates)].flatMap((path) => ["-c", `safe.directory=${path}`]);
}

function git(args, options) {
  return run("git", [...safeDirectoryArgs(), ...args], options);
}

function gitInPath(path, args, options) {
  return run("git", [...safeDirectoryArgs(path), "-C", path, ...args], options);
}

function gh(args, options) {
  return run("gh", args, {
    ...options,
    env: {
      ...process.env,
      GIT_CONFIG_COUNT: "1",
      GIT_CONFIG_KEY_0: "safe.directory",
      GIT_CONFIG_VALUE_0: process.env.QUATA_GIT_SAFE_DIRECTORY ?? process.cwd(),
    },
  });
}

export function parsePorcelainWorktrees(value) {
  const records = [];
  let current = null;
  for (const line of value.split(/\r?\n/)) {
    if (!line.trim()) {
      if (current) records.push(current);
      current = null;
      continue;
    }
    const [key, ...rest] = line.split(" ");
    const text = rest.join(" ");
    if (key === "worktree") current = { path: text };
    else if (current && key === "HEAD") current.head = text;
    else if (current && key === "branch") current.branchRef = text;
    else if (current && key === "detached") current.detached = true;
  }
  if (current) records.push(current);
  return records.map((record) => ({
    ...record,
    branch: record.branchRef?.replace(/^refs\/heads\//, "") ?? null,
  }));
}

export function normalizeBranchName(branch) {
  return String(branch ?? "").replace(/^refs\/heads\//, "").replace(/^origin\//, "");
}

function uniqueByBranch(candidates) {
  const seen = new Set();
  return candidates.filter((candidate) => {
    const branch = normalizeBranchName(candidate.branch);
    if (seen.has(branch)) return false;
    seen.add(branch);
    return true;
  });
}

export function selectLatestPullRequest(pullRequests) {
  const sorted = [...(pullRequests ?? [])].sort((a, b) => Number(b.number ?? 0) - Number(a.number ?? 0));
  return sorted[0] ?? null;
}

export function hasDependentActiveBranch({ branch, branches = [], pullRequests = [] }) {
  const clean = normalizeBranchName(branch);
  const target = branches.find((candidate) => normalizeBranchName(candidate.branch) === clean);
  if (target?.mergedIntoMain) return false;
  const dependentBranch = branches.some((candidate) => {
    const candidateBranch = normalizeBranchName(candidate.branch);
    return (
      candidateBranch !== clean &&
      (normalizeBranchName(candidate.upstream) === clean ||
        (candidate.dependsOnBranches ?? []).map(normalizeBranchName).includes(clean))
    );
  });
  const dependentPr = pullRequests.some((pr) => {
    if (pr.state !== "OPEN") return false;
    return normalizeBranchName(pr.baseRefName) === clean || normalizeBranchName(pr.baseRefName) === `origin/${clean}`;
  });
  return dependentBranch || dependentPr;
}

export function validateLocalCleanupCandidate({
  branch,
  worktree,
  pullRequests,
  status,
  localBranches = [],
  allPullRequests = [],
  remoteBranchState = "unknown",
}) {
  const failures = [];
  const cleanBranch = normalizeBranchName(branch);
  const pr = selectLatestPullRequest(pullRequests);
  if (!cleanBranch.startsWith("codex/")) failures.push("not_codex_branch");
  if (!pr) failures.push("no_associated_pr");
  if (pr && pr.state !== "MERGED") failures.push(`pr_not_merged:${pr.state}`);
  if (pr?.headRefOid && status?.head && pr.headRefOid !== status.head) failures.push("local_head_differs_from_pr_head");
  if ((pullRequests ?? []).some((item) => item.state === "OPEN")) failures.push("open_pr_uses_branch");
  if (worktree?.path && status?.dirty) failures.push("uncommitted_changes");
  if (worktree?.path && status?.untracked) failures.push("untracked_work");
  if (status?.unpublished) failures.push("unpublished_commits");
  if (status?.ambiguous) failures.push("git_status_ambiguous");
  if (remoteBranchState === "ambiguous") failures.push("remote_branch_ambiguous");
  if (hasDependentActiveBranch({ branch: cleanBranch, branches: localBranches, pullRequests: allPullRequests })) {
    failures.push("active_stack_dependent");
  }
  return {
    branch: cleanBranch,
    worktree: worktree?.path ?? null,
    pr: pr ? { number: pr.number, state: pr.state, url: pr.url ?? null } : null,
    remoteBranchState,
    action: failures.length === 0 ? "clean" : "skip",
    failures,
  };
}

function listWorktrees() {
  return parsePorcelainWorktrees(git(["worktree", "list", "--porcelain"]));
}

function listLocalBranches() {
  const branches = git(["for-each-ref", "--format=%(refname:short)%09%(upstream:short)%09%(objectname)", "refs/heads/codex"])
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => {
      const [branch, upstream, head] = line.split("\t");
      return { branch, upstream: upstream || null, head };
    });
  return branches.map((branch) => ({
    ...branch,
    mergedIntoMain: branchContainsAncestor("origin/main", branch.branch),
    dependsOnBranches: branches
      .filter((candidate) => candidate.branch !== branch.branch)
      .filter((candidate) => !branchContainsAncestor("origin/main", candidate.branch))
      .filter((candidate) => branchContainsAncestor(branch.branch, candidate.branch))
      .map((candidate) => candidate.branch),
  }));
}

function prListForBranch(branch) {
  const output = gh([
    "pr",
    "list",
    "--state",
    "all",
    "--head",
    branch,
    "--json",
    "number,state,headRefName,baseRefName,url,headRefOid",
    "--limit",
    "20",
  ]);
  return JSON.parse(output);
}

function allOpenPullRequests() {
  return JSON.parse(gh([
    "pr",
    "list",
    "--state",
    "open",
    "--json",
    "number,state,headRefName,baseRefName,url,headRefOid",
    "--limit",
    "100",
  ]));
}

function worktreeStatus(path) {
  try {
    const porcelain = gitInPath(path, ["status", "--porcelain=v1", "--branch"]);
    const head = gitInPath(path, ["rev-parse", "HEAD"]).trim();
    const lines = porcelain.split(/\r?\n/).filter(Boolean);
    const branchLine = lines.find((line) => line.startsWith("## ")) ?? "";
    const changes = lines.filter((line) => !line.startsWith("## "));
    return {
      dirty: changes.some((line) => !line.startsWith("?? ")),
      untracked: changes.some((line) => line.startsWith("?? ")),
      unpublished: /\[ahead \d+/.test(branchLine) || /\[ahead \d+, behind \d+/.test(branchLine),
      ambiguous: false,
      head,
    };
  } catch {
    return { dirty: false, untracked: false, unpublished: false, ambiguous: true };
  }
}

function branchContainsAncestor(branch, ancestor) {
  try {
    git(["merge-base", "--is-ancestor", ancestor, branch]);
    return true;
  } catch {
    return false;
  }
}

function remoteBranchState(branch) {
  try {
    const output = git(["ls-remote", "--heads", "origin", `refs/heads/${branch}`]).trim();
    return output ? "exists" : "already_deleted";
  } catch {
    return "ambiguous";
  }
}

function removeWorktree(path) {
  git(["worktree", "remove", path]);
}

function deleteLocalBranch(branch) {
  git(["branch", "-D", branch]);
}

export function buildCleanupPlan({ worktrees, localBranches, pullRequestsByBranch, statusesByPath, openPullRequests, remoteStates }) {
  const worktreeByBranch = new Map(
    worktrees
      .filter((worktree) => worktree.branch?.startsWith("codex/"))
      .map((worktree) => [normalizeBranchName(worktree.branch), worktree]),
  );
  const branches = uniqueByBranch([
    ...localBranches.map((branch) => ({ branch: normalizeBranchName(branch.branch) })),
    ...worktrees.filter((worktree) => worktree.branch?.startsWith("codex/")).map((worktree) => ({ branch: worktree.branch })),
  ]);
  return branches.map(({ branch }) => {
    const cleanBranch = normalizeBranchName(branch);
    const worktree = worktreeByBranch.get(cleanBranch) ?? null;
    return validateLocalCleanupCandidate({
      branch: cleanBranch,
      worktree,
      pullRequests: pullRequestsByBranch[cleanBranch] ?? [],
      status: worktree
        ? statusesByPath[worktree.path]
        : { dirty: false, untracked: false, unpublished: false, ambiguous: false, head: localBranches.find((item) => normalizeBranchName(item.branch) === cleanBranch)?.head },
      localBranches,
      allPullRequests: openPullRequests,
      remoteBranchState: remoteStates[cleanBranch] ?? "unknown",
    });
  });
}

function cleanupMergedWorktrees({ apply = false, skipFetch = false } = {}) {
  if (!skipFetch) git(["fetch", "--prune", "origin"], { stdio: "inherit" });
  const worktrees = listWorktrees();
  const localBranches = listLocalBranches();
  const openPullRequests = allOpenPullRequests();
  const branches = uniqueByBranch([
    ...localBranches.map((branch) => ({ branch: branch.branch })),
    ...worktrees.filter((worktree) => worktree.branch?.startsWith("codex/")).map((worktree) => ({ branch: worktree.branch })),
  ]).map((candidate) => normalizeBranchName(candidate.branch));
  const pullRequestsByBranch = Object.fromEntries(branches.map((branch) => [branch, prListForBranch(branch)]));
  const statusesByPath = Object.fromEntries(worktrees.filter((worktree) => worktree.path).map((worktree) => [worktree.path, worktreeStatus(worktree.path)]));
  const remoteStates = Object.fromEntries(branches.map((branch) => [branch, remoteBranchState(branch)]));
  const plan = buildCleanupPlan({ worktrees, localBranches, pullRequestsByBranch, statusesByPath, openPullRequests, remoteStates });
  const cleaned = [];
  const applyErrors = [];
  if (apply) {
    for (const item of plan.filter((candidate) => candidate.action === "clean")) {
      try {
        if (item.worktree) removeWorktree(item.worktree);
        deleteLocalBranch(item.branch);
        cleaned.push(item.branch);
      } catch (error) {
        applyErrors.push({
          branch: item.branch,
          worktree: item.worktree,
          reason: "apply_failed",
          error: error?.message ?? String(error),
        });
      }
    }
    git(["worktree", "prune"]);
    git(["fetch", "--prune", "origin"], { stdio: "inherit" });
  }
  return {
    mode: apply ? "apply" : "dry-run",
    fetch: skipFetch ? "skipped_diagnostics_only" : "completed",
    cleaned,
    applyErrors,
    cleanable: plan.filter((item) => item.action === "clean"),
    skipped: plan.filter((item) => item.action === "skip"),
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  let args = { json: true };
  try {
    args = parseArgs(process.argv.slice(2));
    if (args.help) {
      console.log(usage());
      process.exit(0);
    }
    console.log(JSON.stringify(cleanupMergedWorktrees({ apply: args.apply, skipFetch: args.skipFetch }), null, 2));
  } catch (error) {
    const message = error?.message ?? String(error);
    if (args.json) console.error(JSON.stringify({ status: "failed", error: message }, null, 2));
    else {
      console.error(message);
      console.error(usage());
    }
    process.exit(2);
  }
}
