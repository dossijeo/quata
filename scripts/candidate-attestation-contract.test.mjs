import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { tmpdir } from "node:os";
import test from "node:test";

import { validateAttestation } from "./validate-candidate-attestation.mjs";

function git(directory, args, options = {}) {
  return execFileSync("git", args, { cwd: directory, encoding: "utf8", ...options }).trim();
}

function write(directory, relativePath, content) {
  const target = join(directory, ...relativePath.split("/"));
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, content);
}

function commit(directory, message) {
  git(directory, ["add", "-A"]);
  git(directory, ["commit", "-qm", message]);
  return git(directory, ["rev-parse", "HEAD"]);
}

function commitPaths(directory, message, paths) {
  git(directory, ["add", ...paths]);
  git(directory, ["commit", "-qm", message]);
  return git(directory, ["rev-parse", "HEAD"]);
}

function sha256(content) {
  return createHash("sha256").update(content).digest("hex");
}

function withRepository(callback) {
  const directory = mkdtempSync(join(tmpdir(), "quata-attestation-"));
  try {
    git(directory, ["init", "-q"]);
    git(directory, ["config", "user.email", "attestation@example.invalid"]);
    git(directory, ["config", "user.name", "Candidate Attestation Fixture"]);
    return callback(directory);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

function manifest(productSha) {
  return JSON.stringify({
    units: ["CHAT-ATTACHMENTS", "CHAT-AUDIO"],
    productSha,
    evidence: {
      web: { status: "passed", sha: productSha, report: "build-reports/web/evidence.json", cleanup: { verified: true } },
      android: { status: "passed", sha: productSha, report: "build-reports/android/evidence.json", cleanup: { verified: true } },
      ios: { status: "passed", sha: productSha, report: "build-reports/ios/evidence.json", cleanup: { verified: true } },
    },
  }, null, 2);
}

test("docs-only attestation after product SHA preserves evidence", () => withRepository((directory) => {
  write(directory, "feature/chat/src/commonMain/kotlin/Chat.kt", "product\n");
  const productSha = commit(directory, "product evidence");
  write(directory, "docs/candidate-attestations/chat-attachments-audio.json", manifest(productSha));
  write(directory, "docs/SCREEN_MIGRATION_INVENTORY_V2.md", `product ${productSha}\n`);
  write(directory, "docs/ACCOUNT_AVATAR_EVIDENCE.md", `avatar evidence ${productSha}\n`);
  write(directory, "docs/SUPABASE_E2E_SB06.md", "SB-06 attestation procedure\n");
  const head = commit(directory, "attest evidence");
  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat-attachments-audio.json", head, cwd: directory });
  assert.equal(result.ok, true);
  assert.equal(result.productSha, productSha);
  assert.deepEqual(result.invalidatingFiles, []);
}));

test("Kotlin changes after product SHA invalidate evidence", () => withRepository((directory) => {
  write(directory, "feature/chat/src/commonMain/kotlin/Chat.kt", "product\n");
  const productSha = commit(directory, "product evidence");
  write(directory, "docs/candidate-attestations/chat.json", manifest(productSha));
  commit(directory, "manifest");
  write(directory, "feature/chat/src/commonMain/kotlin/Chat.kt", "changed product\n");
  const head = commit(directory, "change product");
  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });
  assert.equal(result.ok, false);
  assert.match(result.invalidatingFiles.join("\n"), /feature\/chat\/src\/commonMain\/kotlin\/Chat\.kt/);
}));

test("runner and workflow changes after product SHA invalidate evidence", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  write(directory, "docs/candidate-attestations/chat.json", manifest(productSha));
  commit(directory, "manifest");
  write(directory, "scripts/chat-actions-notifications-web-evidence.mjs", "console.log('changed')\n");
  write(directory, ".github/workflows/web-android-pr.yml", "name: changed\n");
  const head = commit(directory, "change runners");
  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });
  assert.equal(result.ok, false);
  assert.match(result.invalidatingFiles.join("\n"), /scripts\/chat-actions-notifications-web-evidence\.mjs/);
  assert.match(result.invalidatingFiles.join("\n"), /\.github\/workflows\/web-android-pr\.yml/);
}));

test("build reports cannot be rewritten as attestation metadata", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  write(directory, "docs/candidate-attestations/chat.json", manifest(productSha));
  commit(directory, "manifest");
  write(directory, "build-reports/web/evidence.json", JSON.stringify({ status: "passed", sha: productSha }));
  const head = commit(directory, "rewrite evidence report");
  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });
  assert.equal(result.ok, false);
  assert.match(result.invalidatingFiles.join("\n"), /build-reports\/web\/evidence\.json/);
}));

test("candidate manifest metadata alone is attestation-only", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  write(directory, "docs/candidate-attestations/chat.json", manifest(productSha));
  const head = commit(directory, "manifest only");
  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });
  assert.equal(result.ok, true);
}));

test("rebased equivalent product SHA fails closed until evidence is rerun", () => withRepository((directory) => {
  write(directory, "feature/chat/src/commonMain/kotlin/Chat.kt", "product\n");
  const productSha = commit(directory, "product evidence");
  write(directory, "docs/candidate-attestations/chat.json", manifest(productSha));
  commit(directory, "attest original evidence");
  git(directory, ["checkout", "--orphan", "rebased"]);
  write(directory, "feature/chat/src/commonMain/kotlin/Chat.kt", "product\n");
  const rebasedProductSha = commit(directory, "rebased equivalent product");
  write(directory, "docs/candidate-attestations/chat.json", manifest(productSha));
  write(directory, "docs/SCREEN_MIGRATION_INVENTORY_V2.md", `stale product ${productSha}\n`);
  const head = commit(directory, "reuse stale attestation");

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.notEqual(rebasedProductSha, productSha);
  assert.equal(result.ok, false);
  assert.equal(result.productShaIsAncestor, false);
  assert.match(result.invalidatingFiles.join("\n"), /candidate-product-sha-not-ancestor/);
}));

test("missing or dirty evidence entries fail closed", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  const bad = JSON.parse(manifest(productSha));
  bad.evidence.ios.cleanup.verified = false;
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(bad, null, 2));
  const head = commit(directory, "bad manifest");
  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });
  assert.equal(result.ok, false);
  assert.deepEqual(result.incompleteEvidence, ["ios"]);
}));

test("local evidence report hash and required steps are audited when declared", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  const report = JSON.stringify({
    status: "passed",
    git: { head: productSha, workingTreeDirty: false },
    steps: ["flow_verified"],
    cleanup: {
      state: "completed",
      hardCleanup: { residueCounts: { chat_threads: 0 } },
    },
  });
  write(directory, "build-reports/web/evidence.json", report);
  const parsed = JSON.parse(manifest(productSha));
  parsed.evidence.web.reportSha256 = sha256(report);
  parsed.evidence.web.reportStatus = "passed";
  parsed.evidence.web.reportGitHead = productSha;
  parsed.evidence.web.reportWorkingTreeDirty = false;
  parsed.evidence.web.requireCleanupState = "completed";
  parsed.evidence.web.requireCleanupResidueZero = true;
  parsed.evidence.web.requiredSteps = ["flow_verified"];
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(parsed, null, 2));
  const head = commitPaths(directory, "manifest with report contract", ["docs/candidate-attestations/chat.json"]);

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.equal(result.ok, true);
  assert.deepEqual(result.evidenceArtifactFailures, []);
}));

test("required evidence report artifacts fail closed when absent", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  const parsed = JSON.parse(manifest(productSha));
  parsed.evidence.web.requireReportArtifact = true;
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(parsed, null, 2));
  const head = commitPaths(directory, "manifest requiring report artifact", ["docs/candidate-attestations/chat.json"]);

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.equal(result.ok, false);
  assert.match(result.evidenceArtifactFailures.join("\n"), /web:report_missing:build-reports\/web\/evidence\.json/);
}));

test("required xcresult evidence artifact directories fail closed when incomplete", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  mkdirSync(join(directory, "build-reports/ios/startup.xcresult"), { recursive: true });
  const parsed = JSON.parse(manifest(productSha));
  parsed.evidence.ios.report = "build-reports/ios/startup.xcresult";
  parsed.evidence.ios.requireReportArtifact = true;
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(parsed, null, 2));
  const head = commitPaths(directory, "manifest requiring xcresult artifact", ["docs/candidate-attestations/chat.json"]);

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.equal(result.ok, false);
  assert.match(result.evidenceArtifactFailures.join("\n"), /ios:xcresult_incomplete:build-reports\/ios\/startup\.xcresult/);
}));

test("required xcresult evidence artifacts must be directories", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  write(directory, "build-reports/ios/startup.xcresult", JSON.stringify({ status: "passed" }));
  const parsed = JSON.parse(manifest(productSha));
  parsed.evidence.ios.report = "build-reports/ios/startup.xcresult";
  parsed.evidence.ios.requireReportArtifact = true;
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(parsed, null, 2));
  const head = commitPaths(directory, "manifest requiring xcresult artifact", ["docs/candidate-attestations/chat.json"]);

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.equal(result.ok, false);
  assert.match(result.evidenceArtifactFailures.join("\n"), /ios:xcresult_not_directory:build-reports\/ios\/startup\.xcresult/);
}));

test("required xcresult evidence artifact entries must have the expected types", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  mkdirSync(join(directory, "build-reports/ios/startup.xcresult/Info.plist"), { recursive: true });
  write(directory, "build-reports/ios/startup.xcresult/Data", "not a directory\n");
  const parsed = JSON.parse(manifest(productSha));
  parsed.evidence.ios.report = "build-reports/ios/startup.xcresult";
  parsed.evidence.ios.requireReportArtifact = true;
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(parsed, null, 2));
  const head = commitPaths(directory, "manifest requiring xcresult artifact", ["docs/candidate-attestations/chat.json"]);

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.equal(result.ok, false);
  assert.match(result.evidenceArtifactFailures.join("\n"), /ios:xcresult_incomplete:build-reports\/ios\/startup\.xcresult/);
}));

test("required non-json xcresult evidence artifact directories are audited without parsing", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  mkdirSync(join(directory, "build-reports/ios/startup.xcresult/Data"), { recursive: true });
  write(directory, "build-reports/ios/startup.xcresult/Info.plist", "plist marker\n");
  const parsed = JSON.parse(manifest(productSha));
  parsed.evidence.ios.report = "build-reports/ios/startup.xcresult";
  parsed.evidence.ios.requireReportArtifact = true;
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(parsed, null, 2));
  const head = commitPaths(directory, "manifest requiring xcresult artifact", ["docs/candidate-attestations/chat.json"]);

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.equal(result.ok, true);
  assert.deepEqual(result.evidenceArtifactFailures, []);
}));

test("declared evidence log markers are audited", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  const report = JSON.stringify({
    status: "passed_with_post_xcodebuild_interrupt",
    git: { head: productSha, workingTreeDirty: false },
    steps: ["ios_fixture_passed"],
    cleanup: { state: "not_applicable" },
  });
  write(directory, "build-reports/ios/evidence.json", report);
  write(directory, "build-reports/ios/UGC-TERMS-ui/ui.log", "Executed 1 test, with 0 failures\nPASS_EXECUTED:testUgcTerms\n");
  const parsed = JSON.parse(manifest(productSha));
  parsed.evidence.ios.reportSha256 = sha256(report);
  parsed.evidence.ios.reportStatus = "passed_with_post_xcodebuild_interrupt";
  parsed.evidence.ios.reportGitHead = productSha;
  parsed.evidence.ios.reportWorkingTreeDirty = false;
  parsed.evidence.ios.requiredSteps = ["ios_fixture_passed"];
  parsed.evidence.ios.requiredLog = {
    path: "build-reports/ios/UGC-TERMS-ui/ui.log",
    sha256: sha256("Executed 1 test, with 0 failures\nPASS_EXECUTED:testUgcTerms\n"),
    contains: ["Executed 1 test, with 0 failures", "PASS_EXECUTED:testUgcTerms"],
  };
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(parsed, null, 2));
  const head = commitPaths(directory, "manifest with required log", ["docs/candidate-attestations/chat.json"]);

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.equal(result.ok, true);
  assert.deepEqual(result.evidenceArtifactFailures, []);
}));

test("declared evidence log hash mismatch fails closed", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  write(directory, "build-reports/ios/UGC-TERMS-ui/ui.log", "Executed 1 test, with 0 failures\nPASS_EXECUTED:testUgcTerms\n");
  const parsed = JSON.parse(manifest(productSha));
  parsed.evidence.ios.requiredLog = {
    path: "build-reports/ios/UGC-TERMS-ui/ui.log",
    sha256: sha256("different log\n"),
    contains: ["PASS_EXECUTED:testUgcTerms"],
  };
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(parsed, null, 2));
  const head = commitPaths(directory, "manifest with bad required log hash", ["docs/candidate-attestations/chat.json"]);

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.equal(result.ok, false);
  assert.match(result.evidenceArtifactFailures.join("\n"), /ios:required_log_sha256_mismatch:build-reports\/ios\/UGC-TERMS-ui\/ui\.log/);
}));

test("local evidence report mismatch fails closed when declared", () => withRepository((directory) => {
  write(directory, "README.md", "base\n");
  const productSha = commit(directory, "product evidence");
  const report = JSON.stringify({
    status: "passed",
    git: { head: productSha, workingTreeDirty: false },
    steps: ["flow_verified"],
    cleanup: {
      state: "completed",
      hardCleanup: { residueCounts: { chat_threads: 0 } },
    },
  });
  write(directory, "build-reports/web/evidence.json", report);
  const parsed = JSON.parse(manifest(productSha));
  parsed.evidence.web.reportSha256 = sha256(`${report}\nchanged`);
  parsed.evidence.web.requiredSteps = ["flow_verified", "missing_step"];
  write(directory, "docs/candidate-attestations/chat.json", JSON.stringify(parsed, null, 2));
  const head = commitPaths(directory, "manifest with bad report contract", ["docs/candidate-attestations/chat.json"]);

  const result = validateAttestation({ manifestPath: "docs/candidate-attestations/chat.json", head, cwd: directory });

  assert.equal(result.ok, false);
  assert.match(result.evidenceArtifactFailures.join("\n"), /web:report_sha256_mismatch/);
  assert.match(result.evidenceArtifactFailures.join("\n"), /web:report_missing_step:missing_step/);
}));
