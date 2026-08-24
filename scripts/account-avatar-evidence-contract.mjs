#!/usr/bin/env node

import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

export const ACCOUNT_AVATAR_CHECK = "ACCOUNT-AVATAR";
export const ACCOUNT_AVATAR_PLATFORMS = ["web", "android", "ios"];
export const ACCOUNT_AVATAR_STEPS = [
  "avatar_selected",
  "avatar_editor_confirmed",
  "avatar_uploaded",
  "avatar_persisted",
  "avatar_persisted_after_reload",
  "avatar_rollback_verified",
  "avatar_retry_persisted",
  "avatar_cleanup_verified",
];
export const ACCOUNT_AVATAR_MUTATION_OPT_IN = "I_ACCEPT_REVERSIBLE_ACCOUNT_AVATAR_MUTATION";
export const ACCOUNT_AVATAR_CREDENTIALS_ENV = "QUATA_ACCOUNT_AVATAR_CREDENTIALS_FILE";
export const ACCOUNT_AVATAR_CREDENTIALS_FALLBACK = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";

const SECRET_KEY = /(access.?token|api.?key|authorization|password|secret|service.?role|refresh.?token)/i;
const SAFE_REPORT_PATH = /^(?![A-Za-z]:)(?![\\/])(?!.*\\\.\\.)(?!.*(?:^|[\\/])\.\.(?:[\\/]|$)).+$/;

function fail(message) {
  throw new Error(`account_avatar_evidence_invalid:${message}`);
}

function assertString(value, name) {
  if (typeof value !== "string" || value.trim() === "") fail(`${name}_required`);
}

function assertSha(value, name) {
  if (typeof value !== "string" || !/^[0-9a-f]{40}$/i.test(value)) fail(`${name}_must_be_git_sha`);
}

function assertSafeReportPath(value, name) {
  assertString(value, name);
  if (!SAFE_REPORT_PATH.test(value)) fail(`${name}_must_be_repository_relative`);
}

function assertNoSecrets(value, path = "report") {
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoSecrets(item, `${path}[${index}]`));
    return;
  }
  if (!value || typeof value !== "object") return;
  for (const [key, child] of Object.entries(value)) {
    if (SECRET_KEY.test(key)) fail(`${path}.${key}_must_not_be_recorded`);
    assertNoSecrets(child, `${path}.${key}`);
  }
}

function validatePlatform(platform, value, productSha) {
  if (!value || typeof value !== "object") fail(`${platform}_evidence_required`);
  if (value.status !== "passed") fail(`${platform}_status_must_be_passed`);
  assertSha(value.sha, `${platform}.sha`);
  if (value.sha !== productSha) fail(`${platform}_sha_mismatch`);
  assertSafeReportPath(value.report, `${platform}.report`);
  if (!Array.isArray(value.steps) || !ACCOUNT_AVATAR_STEPS.every((step) => value.steps.includes(step))) {
    fail(`${platform}_steps_incomplete`);
  }
  if (value.rollback?.triggered !== true || value.rollback?.verified !== true) {
    fail(`${platform}_rollback_not_verified`);
  }
  if (value.rollback.physicalResidue !== 0) fail(`${platform}_rollback_residue_present`);
  if (value.cleanup?.verified !== true || value.cleanup.physicalResidue !== 0) {
    fail(`${platform}_cleanup_not_verified`);
  }
}

export function validateAccountAvatarEvidence(report) {
  if (!report || typeof report !== "object") fail("report_required");
  if (report.version !== 1) fail("unsupported_version");
  if (!Array.isArray(report.units) || !report.units.includes(ACCOUNT_AVATAR_CHECK)) fail("unit_missing");
  assertSha(report.productSha, "productSha");
  if (report.execution?.mode !== "fixture" && report.execution?.mode !== "real") fail("execution_mode_invalid");
  if (report.execution.mode === "real" && report.execution.mutationOptIn !== ACCOUNT_AVATAR_MUTATION_OPT_IN) {
    fail("real_mode_requires_explicit_mutation_opt_in");
  }
  if (report.execution?.credentialsSource !== ACCOUNT_AVATAR_CREDENTIALS_ENV) {
    fail("credentials_source_must_be_explicit_env_name");
  }
  for (const platform of ACCOUNT_AVATAR_PLATFORMS) validatePlatform(platform, report.evidence?.[platform], report.productSha);
  assertNoSecrets(report);
  return { check: ACCOUNT_AVATAR_CHECK, status: "passed", productSha: report.productSha, platforms: ACCOUNT_AVATAR_PLATFORMS };
}

async function main(argv) {
  const reportPath = argv[0];
  if (!reportPath) throw new Error("usage: node scripts/account-avatar-evidence-contract.mjs <report.json>");
  const report = JSON.parse(await readFile(resolve(reportPath), "utf8"));
  const result = validateAccountAvatarEvidence(report);
  process.stdout.write(`${JSON.stringify(result)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2)).catch((error) => {
    process.stderr.write(`FAIL\n${String(error?.message ?? error)}\n`);
    process.exitCode = 1;
  });
}
