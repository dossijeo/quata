import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  buildCleanupPlan,
  parsePorcelainWorktrees,
  validateLocalCleanupCandidate,
} from "./cleanup-merged-worktrees.mjs";

function pr(overrides = {}) {
  return {
    number: 250,
    state: "MERGED",
    headRefName: "codex/profile-content-fixtures",
    baseRefName: "main",
    url: "https://github.com/dossijeo/quata/pull/250",
    ...overrides,
  };
}

function candidate(overrides = {}) {
  return validateLocalCleanupCandidate({
    branch: "codex/profile-content-fixtures",
    worktree: { path: "C:/repo-profile-content", branch: "codex/profile-content-fixtures" },
    pullRequests: [pr()],
    status: { dirty: false, untracked: false, unpublished: false, ambiguous: false },
    localBranches: [{ branch: "codex/profile-content-fixtures", upstream: "origin/codex/profile-content-fixtures" }],
    allPullRequests: [],
    remoteBranchState: "already_deleted",
    ...overrides,
  });
}

test("merged PR with clean worktree and no dependents is cleanable", () => {
  const result = candidate();
  assert.equal(result.action, "clean");
  assert.deepEqual(result.failures, []);
});

test("open PR is skipped", () => {
  const result = candidate({ pullRequests: [pr({ state: "OPEN" })] });
  assert.equal(result.action, "skip");
  assert.match(result.failures.join("\n"), /pr_not_merged:OPEN/);
  assert.match(result.failures.join("\n"), /open_pr_uses_branch/);
});

test("dirty and untracked worktrees are skipped", () => {
  assert.match(candidate({ status: { dirty: true, untracked: false, unpublished: false, ambiguous: false } }).failures.join("\n"), /uncommitted_changes/);
  assert.match(candidate({ status: { dirty: false, untracked: true, unpublished: false, ambiguous: false } }).failures.join("\n"), /untracked_work/);
});

test("unpublished or ambiguous git state is skipped", () => {
  assert.match(candidate({ status: { dirty: false, untracked: false, unpublished: true, ambiguous: false } }).failures.join("\n"), /unpublished_commits/);
  assert.match(candidate({ status: { dirty: false, untracked: false, unpublished: false, ambiguous: true } }).failures.join("\n"), /git_status_ambiguous/);
});

test("branch used as another local upstream is skipped to protect stacked work", () => {
  const result = candidate({
    localBranches: [
      { branch: "codex/profile-content-fixtures", upstream: "origin/codex/profile-content-fixtures" },
      { branch: "codex/profile-entry-evidence", upstream: "codex/profile-content-fixtures" },
    ],
  });
  assert.equal(result.action, "skip");
  assert.match(result.failures.join("\n"), /active_stack_dependent/);
});

test("branch contained by another local branch is skipped to protect stacked ancestry", () => {
  const result = candidate({
    localBranches: [
      { branch: "codex/profile-content-fixtures", upstream: "origin/codex/profile-content-fixtures" },
      { branch: "codex/profile-entry-evidence", upstream: null, dependsOnBranches: ["codex/profile-content-fixtures"] },
    ],
  });
  assert.equal(result.action, "skip");
  assert.match(result.failures.join("\n"), /active_stack_dependent/);
});

test("branch already represented in main is not kept only because another branch contains it", () => {
  const result = candidate({
    localBranches: [
      { branch: "codex/profile-content-fixtures", upstream: "origin/codex/profile-content-fixtures", mergedIntoMain: true },
      { branch: "codex/profile-entry-evidence", upstream: null, dependsOnBranches: ["codex/profile-content-fixtures"] },
    ],
  });
  assert.equal(result.action, "clean");
});

test("branch used as base of an open PR is skipped to protect stacked PRs", () => {
  const result = candidate({
    allPullRequests: [pr({ number: 251, state: "OPEN", headRefName: "codex/profile-entry-evidence", baseRefName: "codex/profile-content-fixtures" })],
  });
  assert.equal(result.action, "skip");
  assert.match(result.failures.join("\n"), /active_stack_dependent/);
});

test("local branch head must match the merged PR head", () => {
  const result = candidate({
    pullRequests: [pr({ headRefOid: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })],
    status: {
      dirty: false,
      untracked: false,
      unpublished: false,
      ambiguous: false,
      head: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    },
  });
  assert.equal(result.action, "skip");
  assert.match(result.failures.join("\n"), /local_head_differs_from_pr_head/);
});

test("remote ambiguity is skipped while already deleted remote can still clean local state", () => {
  assert.match(candidate({ remoteBranchState: "ambiguous" }).failures.join("\n"), /remote_branch_ambiguous/);
  assert.equal(candidate({ remoteBranchState: "already_deleted" }).action, "clean");
});

test("missing or non-merged associated PR is skipped", () => {
  assert.match(candidate({ pullRequests: [] }).failures.join("\n"), /no_associated_pr/);
  assert.match(candidate({ pullRequests: [pr({ state: "CLOSED" })] }).failures.join("\n"), /pr_not_merged:CLOSED/);
});

test("duplicate branch candidates produce one cleanup decision", () => {
  const worktrees = parsePorcelainWorktrees([
    "worktree C:/repo",
    "HEAD aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "branch refs/heads/main",
    "",
    "worktree C:/repo-profile",
    "HEAD bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "branch refs/heads/codex/profile-content-fixtures",
    "",
  ].join("\n"));
  const plan = buildCleanupPlan({
    worktrees,
    localBranches: [
      { branch: "codex/profile-content-fixtures", upstream: "origin/codex/profile-content-fixtures" },
      { branch: "codex/profile-content-fixtures", upstream: "origin/codex/profile-content-fixtures" },
    ],
    pullRequestsByBranch: { "codex/profile-content-fixtures": [pr()] },
    statusesByPath: { "C:/repo-profile": { dirty: false, untracked: false, unpublished: false, ambiguous: false } },
    openPullRequests: [],
    remoteStates: { "codex/profile-content-fixtures": "already_deleted" },
  });
  assert.equal(plan.length, 1);
  assert.equal(plan[0].action, "clean");
});

test("diagnostic skip-fetch is documented as dry-run only", async () => {
  const { readFileSync } = await import("node:fs");
  const source = readFileSync(new URL("./cleanup-merged-worktrees.mjs", import.meta.url), "utf8");
  assert.match(source, /skip_fetch_not_allowed_with_apply/);
  assert.match(source, /skipped_diagnostics_only/);
});

test("apply with diagnostic skip-fetch fails closed as JSON", () => {
  const script = fileURLToPath(new URL("./cleanup-merged-worktrees.mjs", import.meta.url));
  const result = spawnSync(process.execPath, [script, "--apply", "--skip-fetch"], { encoding: "utf8" });
  assert.equal(result.status, 2);
  const payload = JSON.parse(result.stderr);
  assert.equal(payload.status, "failed");
  assert.match(payload.error, /skip_fetch_not_allowed_with_apply/);
});

test("gh calls receive safe.directory through environment", async () => {
  const { readFileSync } = await import("node:fs");
  const source = readFileSync(new URL("./cleanup-merged-worktrees.mjs", import.meta.url), "utf8");
  assert.match(source, /GIT_CONFIG_KEY_0:\s*"safe\.directory"/);
  assert.match(source, /GIT_CONFIG_VALUE_0:\s*process\.env\.QUATA_GIT_SAFE_DIRECTORY/);
});
