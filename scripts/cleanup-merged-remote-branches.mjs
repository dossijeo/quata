#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

function usage() {
  return [
    "Usage: node scripts/cleanup-merged-remote-branches.mjs [--apply] [--json] [--repo owner/name]",
    "",
    "Safely removes remote codex/* branches whose PR is confirmed merged.",
    "Default mode is dry-run. Use --apply only after reviewing the JSON plan.",
  ].join("\n");
}

function parseArgs(argv) {
  const args = { apply: false, json: true, repo: "dossijeo/quata" };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === "--apply") args.apply = true;
    else if (value === "--json") args.json = true;
    else if (value === "--repo") args.repo = argv[++index];
    else if (value === "--help" || value === "-h") args.help = true;
    else throw new Error(`unknown_argument:${value}`);
  }
  if (!args.repo || !/^[^/]+\/[^/]+$/.test(args.repo)) throw new Error("invalid_repo");
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

export function normalizeBranchName(branch) {
  return String(branch ?? "").replace(/^refs\/heads\//, "").replace(/^origin\//, "");
}

export function parseLsRemoteHeads(value) {
  return value
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => {
      const [sha, ref] = line.split(/\s+/);
      return { sha, branch: normalizeBranchName(ref) };
    })
    .filter((item) => item.branch.startsWith("codex/"));
}

export function selectLatestPullRequest(pullRequests) {
  const sorted = [...(pullRequests ?? [])].sort((a, b) => Number(b.number ?? 0) - Number(a.number ?? 0));
  return sorted[0] ?? null;
}

export function validateRemoteCleanupCandidate({
  branch,
  remoteSha,
  pullRequests = [],
  openPullRequests = [],
  branchProtection = "unknown",
}) {
  const failures = [];
  const cleanBranch = normalizeBranchName(branch);
  const pr = selectLatestPullRequest(pullRequests);

  if (!cleanBranch.startsWith("codex/")) failures.push("not_codex_branch");
  if (!remoteSha || !/^[0-9a-f]{40}$/i.test(remoteSha)) failures.push("remote_sha_ambiguous");
  if (!pr) failures.push("no_associated_pr");
  if (pr && pr.state !== "MERGED") failures.push(`pr_not_merged:${pr.state}`);
  if (pr?.headRefOid && remoteSha && pr.headRefOid !== remoteSha) failures.push("remote_head_differs_from_pr_head");
  if ((pullRequests ?? []).some((item) => item.state === "OPEN")) failures.push("open_pr_uses_branch");
  if (openPullRequests.some((item) => normalizeBranchName(item.headRefName) === cleanBranch)) failures.push("open_pr_uses_branch");
  if (openPullRequests.some((item) => normalizeBranchName(item.baseRefName) === cleanBranch)) failures.push("active_stack_dependent");
  if (branchProtection === "protected") failures.push("protected_branch");
  if (branchProtection === "ambiguous" || branchProtection === "unknown") failures.push("branch_protection_ambiguous");

  return {
    branch: cleanBranch,
    remoteSha,
    pr: pr ? { number: pr.number, state: pr.state, url: pr.url ?? null } : null,
    branchProtection,
    action: failures.length === 0 ? "delete_remote" : "skip",
    failures,
  };
}

export function buildRemoteCleanupPlan({ remoteBranches, pullRequestsByBranch, openPullRequests, branchProtectionByBranch }) {
  const seen = new Set();
  return remoteBranches
    .filter((item) => {
      if (seen.has(item.branch)) return false;
      seen.add(item.branch);
      return true;
    })
    .map((item) =>
      validateRemoteCleanupCandidate({
        branch: item.branch,
        remoteSha: item.sha,
        pullRequests: pullRequestsByBranch[item.branch] ?? [],
        openPullRequests,
        branchProtection: branchProtectionByBranch[item.branch] ?? "unknown",
      }),
    );
}

function listRemoteCodexBranches() {
  return parseLsRemoteHeads(git(["ls-remote", "--heads", "origin", "refs/heads/codex/*"]));
}

function prListForBranch(branch, repo) {
  const output = gh([
    "pr",
    "list",
    "--repo",
    repo,
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

function allOpenPullRequests(repo) {
  return JSON.parse(gh([
    "pr",
    "list",
    "--repo",
    repo,
    "--state",
    "open",
    "--json",
    "number,state,headRefName,baseRefName,url,headRefOid",
    "--limit",
    "100",
  ]));
}

function branchProtectionState(branch, repo) {
  const [owner, name] = repo.split("/");
  const encodedBranch = branch.split("/").map(encodeURIComponent).join("/");
  try {
    const output = gh(["api", `repos/${owner}/${name}/branches/${encodedBranch}`, "--jq", ".protected"]);
    return output.trim() === "true" ? "protected" : "unprotected";
  } catch {
    return "ambiguous";
  }
}

function deleteRemoteBranch(branch, repo) {
  const [owner, name] = repo.split("/");
  const encodedRef = `heads/${branch}`.split("/").map(encodeURIComponent).join("/");
  gh(["api", "-X", "DELETE", `repos/${owner}/${name}/git/refs/${encodedRef}`]);
}

function cleanupMergedRemoteBranches({ apply = false, repo = "dossijeo/quata" } = {}) {
  const remoteBranches = listRemoteCodexBranches();
  const openPullRequests = allOpenPullRequests(repo);
  const pullRequestsByBranch = Object.fromEntries(remoteBranches.map((item) => [item.branch, prListForBranch(item.branch, repo)]));
  const branchProtectionByBranch = Object.fromEntries(remoteBranches.map((item) => [item.branch, branchProtectionState(item.branch, repo)]));
  const plan = buildRemoteCleanupPlan({ remoteBranches, pullRequestsByBranch, openPullRequests, branchProtectionByBranch });
  const deleted = [];
  const applyErrors = [];

  if (apply) {
    for (const item of plan.filter((candidate) => candidate.action === "delete_remote")) {
      try {
        deleteRemoteBranch(item.branch, repo);
        deleted.push(item.branch);
      } catch (error) {
        applyErrors.push({
          branch: item.branch,
          reason: "delete_failed",
          error: error?.message ?? String(error),
        });
      }
    }
  }

  return {
    mode: apply ? "apply" : "dry-run",
    repo,
    deleted,
    applyErrors,
    deleteable: plan.filter((item) => item.action === "delete_remote"),
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
    console.log(JSON.stringify(cleanupMergedRemoteBranches({ apply: args.apply, repo: args.repo }), null, 2));
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
