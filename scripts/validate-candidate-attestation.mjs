#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { parseNameStatusZ } from "./classify-ci-impact.mjs";

const DEFAULT_MANIFEST = "docs/candidate-attestations/current.json";
const ATTESTATION_ALLOWED = [
  /^docs\/candidate-attestations\/[\s\S]+\.json$/i,
  /^docs\/SCREEN_MIGRATION_INVENTORY_V2\.md$/i,
  /^docs\/MULTIPLATFORM_MIGRATION_BOARD\.md$/i,
  /^docs\/MULTIPLATFORM_MIGRATION_OPERATING_MODEL\.md$/i,
  /^docs\/MULTIPLATFORM_VALIDATION_EVIDENCE\.md$/i,
  /^docs\/MULTIPLATFORM_EVIDENCE_AUDIT\.md$/i,
  /^docs\/ACCOUNT_AVATAR_EVIDENCE\.md$/i,
  /^docs\/SUPABASE_E2E_SB06\.md$/i,
  /^docs\/wiki\/[\s\S]+\.md$/i,
];
const UNTRUSTED_STATUS = new Set(["U", "X", "B"]);

const normalize = (value) => String(value ?? "").replaceAll("\\", "/").replace(/^\.\//, "");

function git(args, cwd = process.cwd(), encoding = "utf8") {
  return execFileSync("git", args, { cwd, encoding, stdio: ["ignore", "pipe", "pipe"] });
}

function resolveCommit(revision, cwd = process.cwd()) {
  return git(["rev-parse", "--verify", `${revision}^{commit}`], cwd).trim();
}

function loadManifest(path = DEFAULT_MANIFEST, cwd = process.cwd()) {
  const resolvedPath = resolve(cwd, path);
  if (!existsSync(resolvedPath)) throw new Error(`candidate_manifest_missing:${path}`);
  const manifest = JSON.parse(readFileSync(resolvedPath, "utf8").replace(/^\uFEFF/, ""));
  if (!manifest.productSha || !/^[0-9a-f]{40}$/i.test(manifest.productSha)) throw new Error("candidate_manifest_missing_product_sha");
  if (!Array.isArray(manifest.units) || manifest.units.length === 0) throw new Error("candidate_manifest_missing_units");
  if (!manifest.evidence || typeof manifest.evidence !== "object") throw new Error("candidate_manifest_missing_evidence");
  return manifest;
}

function diffEntries(base, head, cwd = process.cwd()) {
  return parseNameStatusZ(git([
    "diff", "--name-status", "-z", "--find-renames", "--find-copies-harder", base, head,
  ], cwd, "buffer"));
}

function isAncestor(base, head, cwd = process.cwd()) {
  try {
    execFileSync("git", ["merge-base", "--is-ancestor", base, head], { cwd, stdio: "ignore" });
    return true;
  } catch {
    return false;
  }
}

function changedPaths(entries) {
  return entries.flatMap((entry) => entry.paths).map(normalize);
}

function sha256(buffer) {
  return createHash("sha256").update(buffer).digest("hex");
}

function localEvidenceFailures(manifest, productSha, cwd = process.cwd()) {
  return Object.entries(manifest.evidence).flatMap(([platform, item]) => {
    const failures = [];
    const reportPath = item?.report;
    const absoluteReport = reportPath ? resolve(cwd, reportPath) : null;
    if (!absoluteReport || !existsSync(absoluteReport)) return failures;

    const reportBytes = readFileSync(absoluteReport);
    if (item.reportSha256 && sha256(reportBytes) !== item.reportSha256) {
      failures.push(`${platform}:report_sha256_mismatch:${reportPath}`);
    }

    let report = null;
    try {
      report = JSON.parse(reportBytes.toString("utf8").replace(/^\uFEFF/, ""));
    } catch {
      failures.push(`${platform}:report_not_json:${reportPath}`);
      return failures;
    }

    if (item.reportStatus && report.status !== item.reportStatus) {
      failures.push(`${platform}:report_status_mismatch:${reportPath}`);
    }
    if (item.reportGitHead && report.git?.head !== item.reportGitHead) {
      failures.push(`${platform}:report_git_head_mismatch:${reportPath}`);
    } else if (item.reportGitHead === undefined && report.git?.head && report.git.head !== productSha) {
      failures.push(`${platform}:report_git_head_mismatch:${reportPath}`);
    }
    if (item.reportWorkingTreeDirty !== undefined && report.git?.workingTreeDirty !== item.reportWorkingTreeDirty) {
      failures.push(`${platform}:report_working_tree_dirty_mismatch:${reportPath}`);
    }
    if (item.requireCleanupState && report.cleanup?.state !== item.requireCleanupState) {
      failures.push(`${platform}:report_cleanup_state_mismatch:${reportPath}`);
    }
    if (item.requireCleanupResidueZero === true) {
      const residueGroups = [
        report.cleanup?.hardCleanup?.residueCounts,
        report.cleanup?.feedOfficialComments?.residueCounts,
      ].filter(Boolean);
      const residue = residueGroups.flatMap((counts) => Object.entries(counts));
      const nonZero = residue.filter(([, value]) => Number(value) !== 0);
      if (residue.length === 0 || nonZero.length > 0) {
        failures.push(`${platform}:report_cleanup_residue_not_zero:${reportPath}`);
      }
    }
    for (const step of item.requiredSteps ?? []) {
      if (!Array.isArray(report.steps) || !report.steps.includes(step)) {
        failures.push(`${platform}:report_missing_step:${step}`);
      }
    }
    if (item.requiredLog) {
      const logPath = resolve(cwd, item.requiredLog.path ?? "");
      if (!item.requiredLog.path || !existsSync(logPath)) {
        failures.push(`${platform}:required_log_missing:${item.requiredLog.path ?? ""}`);
      } else {
        const log = readFileSync(logPath, "utf8");
        for (const marker of item.requiredLog.contains ?? []) {
          if (!log.includes(marker)) failures.push(`${platform}:required_log_missing_marker:${marker}`);
        }
      }
    }
    return failures;
  });
}

export function isAttestationPath(path) {
  return ATTESTATION_ALLOWED.some((pattern) => pattern.test(normalize(path)));
}

export function validateAttestation({ manifestPath = DEFAULT_MANIFEST, head = "HEAD", cwd = process.cwd() } = {}) {
  const manifest = loadManifest(manifestPath, cwd);
  const productSha = resolveCommit(manifest.productSha, cwd);
  const headSha = resolveCommit(head, cwd);
  const entries = diffEntries(productSha, headSha, cwd);
  const files = changedPaths(entries);
  const invalid = files.filter((path) => !isAttestationPath(path));
  const productShaIsAncestor = isAncestor(productSha, headSha, cwd);
  if (!productShaIsAncestor) invalid.push("candidate-product-sha-not-ancestor");
  for (const entry of entries) {
    if (UNTRUSTED_STATUS.has(entry.status)) invalid.push(`untrusted-git-status:${entry.status}`);
  }
  const evidence = Object.entries(manifest.evidence);
  const incompleteEvidence = evidence.filter(([, item]) =>
    item?.status !== "passed" ||
    item?.sha !== productSha ||
    !item?.report ||
    item?.cleanup?.verified !== true
  );
  const evidenceArtifactFailures = localEvidenceFailures(manifest, productSha, cwd);
  return {
    ok: invalid.length === 0 && incompleteEvidence.length === 0 && evidenceArtifactFailures.length === 0,
    productSha,
    headSha,
    productShaIsAncestor,
    attestationOnlyCommits: Number(git(["rev-list", "--count", `${productSha}..${headSha}`], cwd).trim()),
    changedFiles: files,
    invalidatingFiles: invalid,
    incompleteEvidence: incompleteEvidence.map(([platform]) => platform),
    evidenceArtifactFailures,
    manifest,
  };
}

function parseArgs(argv) {
  const options = { manifestPath: DEFAULT_MANIFEST, head: "HEAD", json: false };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--manifest") options.manifestPath = argv[++index];
    else if (arg === "--head") options.head = argv[++index];
    else if (arg === "--json") options.json = true;
    else throw new Error(`Unknown argument: ${arg}`);
  }
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseArgs(process.argv.slice(2));
    const result = validateAttestation(options);
    if (options.json) {
      process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    } else if (result.ok) {
      process.stdout.write(`PASS

Product SHA:
${result.productSha}

HEAD:
${result.headSha}

Attestation-only commits:
${result.attestationOnlyCommits}

Changed files:
${result.changedFiles.length ? result.changedFiles.join("\n") : "(none)"}

Evidence remains valid.
`);
    } else {
      process.stdout.write(`FAIL

Evidence invalidated after ${result.productSha}.

${result.invalidatingFiles.length ? `Executable or untrusted change detected:\n${result.invalidatingFiles.join("\n")}\n\n` : ""}${result.incompleteEvidence.length ? `Incomplete evidence:\n${result.incompleteEvidence.join("\n")}\n\n` : ""}${result.evidenceArtifactFailures.length ? `Evidence artifact mismatch:\n${result.evidenceArtifactFailures.join("\n")}\n\n` : ""}Re-run affected evidence.
`);
      process.exitCode = 1;
    }
  } catch (error) {
    process.stderr.write(`FAIL\n\n${String(error?.message ?? error)}\n`);
    process.exitCode = 1;
  }
}
