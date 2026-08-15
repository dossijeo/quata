import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  buildRemoteCleanupPlan,
  parseLsRemoteHeads,
  validateRemoteCleanupCandidate,
} from "./cleanup-merged-remote-branches.mjs";

const SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

function pr(overrides = {}) {
  return {
    number: 255,
    state: "MERGED",
    headRefName: "codex/profile-detail-media-evidence",
    baseRefName: "main",
    headRefOid: SHA,
    url: "https://github.com/dossijeo/quata/pull/255",
    ...overrides,
  };
}

function candidate(overrides = {}) {
  return validateRemoteCleanupCandidate({
    branch: "codex/profile-detail-media-evidence",
    remoteSha: SHA,
    pullRequests: [pr()],
    openPullRequests: [],
    branchProtection: "unprotected",
    ...overrides,
  });
}

test("merged PR with matching unprotected remote branch is deleteable", () => {
  const result = candidate();
  assert.equal(result.action, "delete_remote");
  assert.deepEqual(result.failures, []);
});

test("open or missing PR is skipped", () => {
  assert.match(candidate({ pullRequests: [pr({ state: "OPEN" })] }).failures.join("\n"), /pr_not_merged:OPEN/);
  assert.match(candidate({ pullRequests: [] }).failures.join("\n"), /no_associated_pr/);
});

test("remote branch used by open PR head or base is skipped", () => {
  assert.match(
    candidate({ openPullRequests: [pr({ state: "OPEN", headRefName: "codex/profile-detail-media-evidence" })] }).failures.join("\n"),
    /open_pr_uses_branch/,
  );
  assert.match(
    candidate({ openPullRequests: [pr({ state: "OPEN", headRefName: "codex/child", baseRefName: "codex/profile-detail-media-evidence" })] }).failures.join("\n"),
    /active_stack_dependent/,
  );
});

test("protected or ambiguous branch protection is skipped", () => {
  assert.match(candidate({ branchProtection: "protected" }).failures.join("\n"), /protected_branch/);
  assert.match(candidate({ branchProtection: "ambiguous" }).failures.join("\n"), /branch_protection_ambiguous/);
});

test("remote SHA must match merged PR head", () => {
  const result = candidate({ remoteSha: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" });
  assert.equal(result.action, "skip");
  assert.match(result.failures.join("\n"), /remote_head_differs_from_pr_head/);
});

test("invalid remote SHA and non-codex branches are skipped", () => {
  assert.match(candidate({ remoteSha: "" }).failures.join("\n"), /remote_sha_ambiguous/);
  assert.match(candidate({ branch: "main" }).failures.join("\n"), /not_codex_branch/);
});

test("ls-remote parser keeps codex heads", () => {
  const parsed = parseLsRemoteHeads([
    `${SHA}\trefs/heads/codex/profile-detail-media-evidence`,
    `${SHA}\trefs/heads/main`,
  ].join("\n"));
  assert.deepEqual(parsed, [{ sha: SHA, branch: "codex/profile-detail-media-evidence" }]);
});

test("duplicate remote branches produce one decision", () => {
  const plan = buildRemoteCleanupPlan({
    remoteBranches: [
      { branch: "codex/profile-detail-media-evidence", sha: SHA },
      { branch: "codex/profile-detail-media-evidence", sha: SHA },
    ],
    pullRequestsByBranch: { "codex/profile-detail-media-evidence": [pr()] },
    openPullRequests: [],
    branchProtectionByBranch: { "codex/profile-detail-media-evidence": "unprotected" },
  });
  assert.equal(plan.length, 1);
  assert.equal(plan[0].action, "delete_remote");
});

test("CLI argument errors fail closed as JSON", () => {
  const script = fileURLToPath(new URL("./cleanup-merged-remote-branches.mjs", import.meta.url));
  const result = spawnSync(process.execPath, [script, "--repo", "not-a-repo"], { encoding: "utf8" });
  assert.equal(result.status, 2);
  const payload = JSON.parse(result.stderr);
  assert.equal(payload.status, "failed");
  assert.match(payload.error, /invalid_repo/);
});

test("gh calls receive safe.directory through environment", async () => {
  const { readFileSync } = await import("node:fs");
  const source = readFileSync(new URL("./cleanup-merged-remote-branches.mjs", import.meta.url), "utf8");
  assert.match(source, /GIT_CONFIG_KEY_0:\s*"safe\.directory"/);
  assert.match(source, /GIT_CONFIG_VALUE_0:\s*process\.env\.QUATA_GIT_SAFE_DIRECTORY/);
});
