import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
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
