import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

const root = resolve(import.meta.dirname, "..");
const manifest = JSON.parse(readFileSync(resolve(root, "supabase/migration-reconciliation.json"), "utf8"));
const evidencePath = resolve(
  root,
  "docs/runbooks/migration/evidence/migration-ledger-replay-20260922.json",
);
const evidence = JSON.parse(readFileSync(evidencePath, "utf8"));
const authBridgeEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/auth-bridge-semantics-20260922.json",
), "utf8"));
const softDeletePolicyEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/official-soft-delete-policy-semantics-20260922.json",
), "utf8"));
const actorGuardEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/official-actor-guard-semantics-20260922.json",
), "utf8"));
const readMoreLabelEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/official-read-more-label-semantics-20260922.json",
), "utf8"));

const sha256 = (path) => createHash("sha256").update(readFileSync(path)).digest("hex");
const statementSha256 = (path, startByte, endByte) => createHash("sha256")
  .update(readFileSync(path).subarray(startByte, endByte))
  .digest("hex");

test("verified migration decisions are bound to replay evidence and exact SQL", () => {
  assert.equal(evidence.remoteMutation, false);
  assert.equal(evidence.check, "DB-HISTORICAL-MIGRATION-REPLAY");
  assert.equal(
    sha256(resolve(root, evidence.digest.script)),
    evidence.digest.scriptSha256,
  );
  assert.equal(
    sha256(resolve(root, evidence.statementInventory.script)),
    evidence.statementInventory.scriptSha256,
  );
  assert.equal(evidence.statementInventory.deploymentHistoryProven, false);
  assert.equal(evidence.statementInventory.semanticEquivalenceProven, false);

  const verified = manifest.migrations.filter(
    ({ classification }) => classification === "verified_applied_semantics",
  );
  assert.deepEqual(
    verified.map(({ file }) => file).sort(),
    [...evidence.verifiedAppliedSemantics].sort(),
  );

  const results = new Map(evidence.results.map((result) => [result.file, result]));
  const additionalChecks = new Map(
    evidence.additionalChecks.map((result) => [result.file, result]),
  );
  const semanticAudits = new Map(
    evidence.semanticAudits.map((audit) => [audit.file, audit]),
  );
  for (const decision of verified) {
    const semanticAudit = semanticAudits.get(decision.file);
    const result = results.get(decision.file) ?? additionalChecks.get(decision.file);
    assert.ok(result, `missing replay result for ${decision.file}`);
    if (semanticAudit?.kind === "non-idempotent-policy-package") {
      assert.equal(result.exitCode, 3);
      assert.equal(result.outcome, "incomplete_existing_policy_conflict");
      assert.equal(semanticAudit.rawReplayExitCode, 3);
      assert.equal(semanticAudit.controlledReplayExitCode, 0);
      assert.equal(semanticAudit.dataChanged, false);
      assert.equal(semanticAudit.allEffectsExact, true);
      assert.match(decision.evidence, /official-actor-guard-semantics-20260922\.json/);
      continue;
    }
    assert.equal(result.exitCode, 0);
    assert.equal(result.dataChanged, false);
    if (semanticAudit) {
      assert.equal(semanticAudit.outcome, "verified_applied_semantics");
      assert.equal(semanticAudit.allEffectsExact, true);
      if (semanticAudit.kind === "conditional-indexes") {
        assert.equal(result.schemaChanged, false);
        assert.equal(result.outcome, "replay_no_change");
        assert.equal(semanticAudit.allConditionalBranchesObserved, true);
        assert.match(decision.evidence, /auth-bridge-semantics-20260922\.json/);
      } else if (semanticAudit.kind === "policy") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(result.normalizedPolicyDefinitionChanged, false);
        assert.equal(semanticAudit.normalizedReplayChange, false);
        assert.match(decision.evidence, /official-soft-delete-policy-semantics-20260922\.json/);
      } else if (semanticAudit.kind === "versioned-supersession") {
        assert.equal(result.schemaChanged, false);
        assert.equal(result.outcome, "replay_no_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /official-read-more-label-semantics-20260922\.json/);
      } else {
        assert.fail(`unsupported semantic audit kind for ${decision.file}`);
      }
      continue;
    }
    assert.equal(result.schemaChanged, false);
    assert.equal(result.outcome, "verified_applied_semantics");
    assert.ok(result.topLevelStatementKinds.length > 0);
    assert.ok(result.topLevelStatementKinds.every(
      (kind) => ["CreateFunctionStmt", "GrantStmt"].includes(kind),
    ));
    assert.equal(
      sha256(resolve(root, "supabase/migrations", decision.file)),
      result.sourceSha256,
    );
    assert.match(decision.evidence, /migration-ledger-replay-20260922\.json/);
  }
});

test("Official read-more label decision accounts for its exact versioned default supersession", () => {
  assert.equal(readMoreLabelEvidence.remoteMutation, false);
  assert.equal(readMoreLabelEvidence.transaction, "read-only");
  assert.equal(readMoreLabelEvidence.classification, "verified_applied_semantics");
  assert.equal(readMoreLabelEvidence.sourceMigration.statementCount, 2);
  const sourcePath = resolve(root, readMoreLabelEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), readMoreLabelEvidence.sourceMigration.sha256);
  for (const statement of readMoreLabelEvidence.sourceMigration.statements) {
    assert.equal(
      statementSha256(sourcePath, statement.startByte, statement.endByte),
      statement.sha256,
    );
  }
  const supersedingPath = resolve(root, readMoreLabelEvidence.supersededEffect.file);
  assert.equal(sha256(supersedingPath), readMoreLabelEvidence.supersededEffect.fileSha256);
  assert.equal(readMoreLabelEvidence.supersededEffect.statementOrdinal, 3);
  assert.equal(
    statementSha256(
      supersedingPath,
      readMoreLabelEvidence.supersededEffect.statementStartByte,
      readMoreLabelEvidence.supersededEffect.statementEndByte,
    ),
    readMoreLabelEvidence.supersededEffect.statementSha256,
  );
  assert.equal(
    sha256(resolve(root, readMoreLabelEvidence.auditQuery.file)),
    readMoreLabelEvidence.auditQuery.sha256,
  );
  assert.equal(readMoreLabelEvidence.isolatedReplay.exitCode, 0);
  assert.equal(readMoreLabelEvidence.isolatedReplay.catalogChanged, false);
  assert.equal(readMoreLabelEvidence.isolatedReplay.dataChanged, false);
  assert.equal(readMoreLabelEvidence.observedRemote.columnCount, 1);
  assert.equal(readMoreLabelEvidence.observedRemote.columnMismatchCount, 0);
  assert.equal(readMoreLabelEvidence.observedRemote.column.defaultExpression, "'read_more'::text");
  assert.equal(readMoreLabelEvidence.observedRemote.column.storageKind, "x");
  assert.equal(readMoreLabelEvidence.observedRemote.column.compressionKind, "");
  assert.equal(readMoreLabelEvidence.observedRemote.column.statisticsTarget, null);
  assert.equal(readMoreLabelEvidence.observedRemote.column.arrayDimensions, 0);
  assert.equal(readMoreLabelEvidence.observedRemote.column.hasNoColumnAcl, true);
  assert.equal(readMoreLabelEvidence.observedRemote.allEffectsExact, true);
  assert.equal(readMoreLabelEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(readMoreLabelEvidence.guarantees.deployed, false);
});

test("Official actor guard decision binds the non-idempotent replay to exact remote semantics", () => {
  assert.equal(actorGuardEvidence.remoteMutation, false);
  assert.equal(actorGuardEvidence.transaction, "read-only");
  assert.equal(actorGuardEvidence.classification, "verified_applied_semantics");
  assert.equal(actorGuardEvidence.sourceMigration.statementCount, 25);
  assert.equal(
    sha256(resolve(root, actorGuardEvidence.sourceMigration.file)),
    actorGuardEvidence.sourceMigration.sha256,
  );
  assert.equal(
    sha256(resolve(root, actorGuardEvidence.auditQuery.file)),
    actorGuardEvidence.auditQuery.sha256,
  );
  assert.equal(actorGuardEvidence.remoteDeploymentRecord.pullRequest, 195);
  assert.equal(actorGuardEvidence.remoteDeploymentRecord.remoteLedgerRowPresent, false);
  assert.equal(actorGuardEvidence.isolatedReplay.rawReplay.exitCode, 3);
  assert.equal(actorGuardEvidence.isolatedReplay.controlledReplayExitCode, 0);
  assert.equal(actorGuardEvidence.isolatedReplay.dataChanged, false);
  assert.equal(
    actorGuardEvidence.isolatedReplay.baselineData,
    actorGuardEvidence.isolatedReplay.afterData,
  );
  assert.equal(actorGuardEvidence.isolatedReplay.focusedAuditAfterReplay.functionMismatchCount, 0);
  assert.equal(actorGuardEvidence.isolatedReplay.focusedAuditAfterReplay.policyMismatchCount, 0);
  assert.equal(actorGuardEvidence.isolatedReplay.focusedAuditAfterReplay.oldPolicyCount, 0);
  assert.equal(actorGuardEvidence.isolatedReplay.focusedAuditAfterReplay.allEffectsExact, true);
  assert.equal(actorGuardEvidence.observedRemote.functionMismatchCount, 0);
  assert.equal(actorGuardEvidence.observedRemote.policyMismatchCount, 0);
  assert.equal(actorGuardEvidence.observedRemote.oldPolicyCount, 0);
  assert.equal(actorGuardEvidence.observedRemote.functions.length, 3);
  assert.equal(actorGuardEvidence.observedRemote.policies.length, 4);
  assert.equal(actorGuardEvidence.observedRemote.tableState.anonTruncate, false);
  assert.equal(actorGuardEvidence.observedRemote.tableState.anonReferences, false);
  assert.equal(actorGuardEvidence.observedRemote.tableState.anonTrigger, false);
  assert.equal(actorGuardEvidence.observedRemote.tableState.anonMaintain, false);
  assert.equal(actorGuardEvidence.allEffectsExact, true);
});

test("Official soft-delete policy decision normalizes identity and role order only", () => {
  assert.equal(softDeletePolicyEvidence.remoteMutation, false);
  assert.equal(softDeletePolicyEvidence.transaction, "read-only");
  assert.equal(softDeletePolicyEvidence.classification, "verified_applied_semantics");
  assert.deepEqual(
    softDeletePolicyEvidence.sourceMigration.topLevelStatementKinds,
    ["DropStmt", "CreatePolicyStmt"],
  );
  assert.equal(
    sha256(resolve(root, softDeletePolicyEvidence.sourceMigration.file)),
    softDeletePolicyEvidence.sourceMigration.sha256,
  );
  assert.equal(
    sha256(resolve(root, softDeletePolicyEvidence.auditQuery.file)),
    softDeletePolicyEvidence.auditQuery.sha256,
  );
  assert.equal(softDeletePolicyEvidence.isolatedReplay.catalogChanged, true);
  assert.equal(softDeletePolicyEvidence.isolatedReplay.dataChanged, false);
  assert.equal(softDeletePolicyEvidence.isolatedReplay.policyOidChanged, true);
  assert.equal(softDeletePolicyEvidence.isolatedReplay.orderedRoleOidArrayChanged, true);
  assert.equal(softDeletePolicyEvidence.isolatedReplay.normalizedPolicyDefinitionChanged, false);
  assert.equal(
    softDeletePolicyEvidence.isolatedReplay.baselineData,
    softDeletePolicyEvidence.isolatedReplay.afterData,
  );
  assert.equal(softDeletePolicyEvidence.observedPolicy.policyCount, 1);
  assert.deepEqual(softDeletePolicyEvidence.observedPolicy.roles, ["anon", "authenticated"]);
  assert.equal(softDeletePolicyEvidence.observedPolicy.command, "r");
  assert.equal(softDeletePolicyEvidence.observedPolicy.permissive, true);
  assert.equal(softDeletePolicyEvidence.observedPolicy.withCheckExpression, null);
  assert.equal(softDeletePolicyEvidence.allEffectsExact, true);
});

test("conditional Auth bridge decision covers every exact catalogue effect", () => {
  assert.equal(authBridgeEvidence.remoteMutation, false);
  assert.equal(authBridgeEvidence.transaction, "read-only");
  assert.equal(authBridgeEvidence.classification, "verified_applied_semantics");
  assert.deepEqual(authBridgeEvidence.sourceMigration.topLevelStatementKinds, ["DoStmt"]);
  assert.equal(
    sha256(resolve(root, authBridgeEvidence.sourceMigration.file)),
    authBridgeEvidence.sourceMigration.sha256,
  );
  assert.equal(
    sha256(resolve(root, authBridgeEvidence.auditQuery.file)),
    authBridgeEvidence.auditQuery.sha256,
  );
  assert.equal(authBridgeEvidence.replayEvidence.outcome, "replay_no_change");
  assert.equal(authBridgeEvidence.replayEvidence.schemaChanged, false);
  assert.equal(authBridgeEvidence.replayEvidence.dataChanged, false);
  assert.equal(authBridgeEvidence.allConditionalBranchesObserved, true);
  assert.equal(authBridgeEvidence.allEffectsExact, true);
  assert.deepEqual(
    authBridgeEvidence.conditionalBranches.map(({ column }) => column).sort(),
    ["code", "country_code", "phone_local", "phone_normalized", "telefono"],
  );
  assert.ok(authBridgeEvidence.conditionalBranches.every(
    ({ columnExists, exactIndexExists }) => columnExists && exactIndexExists,
  ));
});

test("replay evidence keeps unresolved migrations out of verified decisions", () => {
  const verified = new Set(evidence.verifiedAppliedSemantics);
  assert.ok(evidence.unresolved.length > 0);
  assert.ok(evidence.unresolved.every((file) => !verified.has(file)));
  assert.ok(evidence.replayNoChange.some((file) => evidence.unresolved.includes(file)));
  assert.equal(evidence.additionalChecks[0].outcome, "incomplete_existing_policy_conflict");
});
