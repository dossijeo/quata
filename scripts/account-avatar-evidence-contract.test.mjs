import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import {
  ACCOUNT_AVATAR_CHECK,
  ACCOUNT_AVATAR_CREDENTIALS_ENV,
  ACCOUNT_AVATAR_MUTATION_OPT_IN,
  ACCOUNT_AVATAR_STEPS,
  validateAccountAvatarEvidence,
} from "./account-avatar-evidence-contract.mjs";

const sha = "0123456789abcdef0123456789abcdef01234567";
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
const backendRunner = await readFile(new URL("./account-avatar-backend-evidence.mjs", import.meta.url), "utf8");

function evidence(overrides = {}) {
  const platform = {
    status: "passed",
    sha,
    report: "build-reports/account-avatar/web.json",
    steps: [...ACCOUNT_AVATAR_STEPS],
    rollback: { triggered: true, verified: true, physicalResidue: 0 },
    cleanup: { verified: true, physicalResidue: 0 },
  };
  return {
    version: 1,
    units: [ACCOUNT_AVATAR_CHECK],
    productSha: sha,
    execution: { mode: "fixture", credentialsSource: ACCOUNT_AVATAR_CREDENTIALS_ENV },
    evidence: {
      web: structuredClone(platform),
      android: structuredClone(platform),
      ios: structuredClone(platform),
    },
    ...overrides,
  };
}

test("accepts the bounded three-platform account-avatar contract", () => {
  assert.deepEqual(validateAccountAvatarEvidence(evidence()).platforms, ["web", "android", "ios"]);
});

test("requires persistence, rollback and cleanup evidence on every platform", () => {
  const report = evidence();
  report.evidence.ios.steps = report.evidence.ios.steps.filter((step) => step !== "avatar_rollback_verified");
  assert.throws(() => validateAccountAvatarEvidence(report), /ios_steps_incomplete/);
});

test("fails closed on residue, absolute paths and recorded secrets", () => {
  const residue = evidence();
  residue.evidence.android.cleanup.physicalResidue = 1;
  assert.throws(() => validateAccountAvatarEvidence(residue), /android_cleanup_not_verified/);

  const absolutePath = evidence();
  absolutePath.evidence.web.report = "C:/tmp/evidence.json";
  assert.throws(() => validateAccountAvatarEvidence(absolutePath), /web\.report_must_be_repository_relative/);

  const secret = evidence();
  secret.accessToken = "must never be written";
  assert.throws(() => validateAccountAvatarEvidence(secret), /accessToken_must_not_be_recorded/);
});

test("real mode requires the explicit reversible mutation opt-in", () => {
  const report = evidence();
  report.execution = { mode: "real", credentialsSource: ACCOUNT_AVATAR_CREDENTIALS_ENV };
  assert.throws(() => validateAccountAvatarEvidence(report), /real_mode_requires_explicit_mutation_opt_in/);
  report.execution.mutationOptIn = ACCOUNT_AVATAR_MUTATION_OPT_IN;
  assert.equal(validateAccountAvatarEvidence(report).status, "passed");
});

test("ACCOUNT-AVATAR contract is part of local fast contract suites", () => {
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/account-avatar-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/account-avatar-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["evidence:account-avatar-backend"], /scripts\/account-avatar-backend-evidence\.mjs/);
});

test("backend runner is opt-in, reversible and avoids recorded secrets", () => {
  assert.match(backendRunner, /QUATA_ACCOUNT_AVATAR_REAL_MUTATION_OPT_IN/);
  assert.match(backendRunner, /ACCOUNT_AVATAR_MUTATION_OPT_IN/);
  assert.match(backendRunner, /ACCOUNT_AVATAR_CREDENTIALS_ENV/);
  assert.match(backendRunner, /ACCOUNT_AVATAR_CREDENTIALS_FALLBACK/);
  assert.match(backendRunner, /const BUCKET = "community-posts"/);
  assert.match(backendRunner, /avatars\/\$\{session\.userId\}\/qadata-account-avatar-\$\{platform\}-/);
  assert.match(backendRunner, /uploadAvatarObject/);
  assert.match(backendRunner, /deleteStorageObject/);
  assert.match(backendRunner, /storage\.objects where bucket_id = \$1 and name = \$2/);
  assert.match(backendRunner, /restoreProfileAvatar/);
  assert.match(backendRunner, /validateAccountAvatarEvidence\(report\)/);
  assert.doesNotMatch(backendRunner, /680242607|680242608|21085800|ghp_|service_role|SUPABASE_DB_URL=/);
});
