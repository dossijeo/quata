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
const adminDeleteEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/admin-delete-posts-semantics-20260922.json",
), "utf8"));
const chatPushFunctionEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-push-function-supersession-20260922.json",
), "utf8"));
const openCommunityEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-open-community-thread-supersession-20260922.json",
), "utf8"));
const sharedAttachmentSenderEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-shared-attachment-sender-supersession-20260922.json",
), "utf8"));
const pushTokenDisableEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/push-token-disable-invalid-supersession-20260922.json",
), "utf8"));
const chatMessageIdempotencyEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-message-idempotency-supersession-20260922.json",
), "utf8"));
const chatPushTriggerEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-push-attachment-trigger-supersession-20260922.json",
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
      } else if (semanticAudit.kind === "policy-supersession") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.equal(semanticAudit.broaderCommunityDeleteDivergencePreserved, true);
        assert.match(decision.evidence, /admin-delete-posts-semantics-20260922\.json/);
      } else if (semanticAudit.kind === "function-supersession") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /chat-push-function-supersession-20260922\.json/);
      } else if (semanticAudit.kind === "function-grant-supersession") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /chat-.*-supersession-20260922\.json/);
      } else if (semanticAudit.kind === "catalog-function-acl-supersession") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /push-token-disable-invalid-supersession-20260922\.json/);
      } else if (semanticAudit.kind === "catalog-multi-function-supersession") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /chat-message-idempotency-supersession-20260922\.json/);
      } else if (semanticAudit.kind === "function-trigger-supersession") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /chat-push-attachment-trigger-supersession-20260922\.json/);
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

test("chat attachment push decision binds its function successor and both triggers", () => {
  assert.equal(chatPushTriggerEvidence.remoteMutation, false);
  assert.equal(chatPushTriggerEvidence.sourceMigration.statementCount, 5);
  const sourcePath = resolve(root, chatPushTriggerEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), chatPushTriggerEvidence.sourceMigration.sha256);
  for (const statement of chatPushTriggerEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  const successorPath = resolve(root, chatPushTriggerEvidence.functionSuccessor.file);
  assert.equal(sha256(successorPath), chatPushTriggerEvidence.functionSuccessor.fileSha256);
  const successor = chatPushTriggerEvidence.functionSuccessor.statement;
  assert.equal(statementSha256(successorPath, successor.startByte, successor.endByte), successor.sha256);
  assert.equal(
    chatPushTriggerEvidence.functionSuccessor.definitionMd5,
    chatPushFunctionEvidence.observedRemote.function.definitionMd5,
  );
  assert.equal(
    sha256(resolve(root, chatPushTriggerEvidence.auditQuery.file)),
    chatPushTriggerEvidence.auditQuery.sha256,
  );
  assert.equal(chatPushTriggerEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(chatPushTriggerEvidence.isolatedReplay.sourceDataChanged, false);
  assert.equal(chatPushTriggerEvidence.observedRemote.functionMismatchCount, 0);
  assert.equal(chatPushTriggerEvidence.observedRemote.triggerCount, 2);
  assert.equal(chatPushTriggerEvidence.observedRemote.triggerMismatchCount, 0);
  assert.equal(chatPushTriggerEvidence.observedRemote.triggersEnabled, true);
  assert.equal(chatPushTriggerEvidence.observedRemote.allEffectsExact, true);
  assert.equal(chatPushTriggerEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(chatPushTriggerEvidence.guarantees.providerInvoked, false);
});

test("chat message idempotency binds all eight source effects", () => {
  assert.equal(chatMessageIdempotencyEvidence.remoteMutation, false);
  assert.equal(chatMessageIdempotencyEvidence.sourceMigration.statementCount, 8);
  const sourcePath = resolve(root, chatMessageIdempotencyEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), chatMessageIdempotencyEvidence.sourceMigration.sha256);
  for (const statement of chatMessageIdempotencyEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  for (const definition of chatMessageIdempotencyEvidence.versionedDefinitions) {
    const path = resolve(root, definition.file);
    assert.equal(sha256(path), definition.fileSha256);
    assert.equal(
      statementSha256(path, definition.statement.startByte, definition.statement.endByte),
      definition.statement.sha256,
    );
  }
  assert.equal(
    sha256(resolve(root, chatMessageIdempotencyEvidence.auditQuery.file)),
    chatMessageIdempotencyEvidence.auditQuery.sha256,
  );
  const auditSql = readFileSync(resolve(root, chatMessageIdempotencyEvidence.auditQuery.file), "utf8");
  assert.match(
    auditSql,
    /to_regprocedure\(\s*'public\.quata_chat_send_message\(uuid,bigint,text,bigint\[\],bigint\)'\s*\)/,
  );
  assert.equal(chatMessageIdempotencyEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(chatMessageIdempotencyEvidence.isolatedReplay.sourceDataChanged, false);
  assert.equal(chatMessageIdempotencyEvidence.observedRemote.columnMismatchCount, 0);
  assert.equal(chatMessageIdempotencyEvidence.observedRemote.indexMismatchCount, 0);
  assert.equal(chatMessageIdempotencyEvidence.observedRemote.functionCount, 3);
  assert.equal(chatMessageIdempotencyEvidence.observedRemote.functionMismatchCount, 0);
  assert.equal(chatMessageIdempotencyEvidence.observedRemote.oldSignatureCount, 0);
  assert.equal(chatMessageIdempotencyEvidence.observedRemote.requiredAnonAndAuthenticatedExecute, true);
  assert.equal(chatMessageIdempotencyEvidence.observedRemote.allEffectsExact, true);
  assert.equal(chatMessageIdempotencyEvidence.allSourceEffectsAccountedFor, true);
});

test("push-token disable decision binds columns, index, successor function and ACL", () => {
  assert.equal(pushTokenDisableEvidence.remoteMutation, false);
  assert.equal(pushTokenDisableEvidence.sourceMigration.statementCount, 5);
  const sourcePath = resolve(root, pushTokenDisableEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), pushTokenDisableEvidence.sourceMigration.sha256);
  for (const statement of pushTokenDisableEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  const successorPath = resolve(root, pushTokenDisableEvidence.supersedingMigration.file);
  assert.equal(sha256(successorPath), pushTokenDisableEvidence.supersedingMigration.sha256);
  for (const statement of pushTokenDisableEvidence.supersedingMigration.statements) {
    assert.equal(statementSha256(successorPath, statement.startByte, statement.endByte), statement.sha256);
  }
  assert.equal(
    sha256(resolve(root, pushTokenDisableEvidence.auditQuery.file)),
    pushTokenDisableEvidence.auditQuery.sha256,
  );
  assert.equal(pushTokenDisableEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(pushTokenDisableEvidence.isolatedReplay.sourceDataChanged, false);
  assert.equal(
    pushTokenDisableEvidence.isolatedReplay.successorCanonicalization.definitionMd5,
    pushTokenDisableEvidence.observedRemote.functionDefinitionMd5,
  );
  assert.equal(pushTokenDisableEvidence.observedRemote.columnCount, 2);
  assert.equal(pushTokenDisableEvidence.observedRemote.columnMismatchCount, 0);
  assert.equal(pushTokenDisableEvidence.observedRemote.indexCount, 1);
  assert.equal(pushTokenDisableEvidence.observedRemote.indexMismatchCount, 0);
  assert.equal(pushTokenDisableEvidence.observedRemote.functionCount, 1);
  assert.equal(pushTokenDisableEvidence.observedRemote.functionMismatchCount, 0);
  assert.equal(pushTokenDisableEvidence.observedRemote.publicExecute, false);
  assert.equal(pushTokenDisableEvidence.observedRemote.authenticatedExecute, true);
  assert.deepEqual(pushTokenDisableEvidence.observedRemote.additionalDirectExecuteAcl, ["anon", "service_role"]);
  assert.equal(pushTokenDisableEvidence.observedRemote.allEffectsExact, true);
  assert.equal(pushTokenDisableEvidence.allSourceEffectsAccountedFor, true);
});

test("shared-attachment-sender decision binds its later function and preserved grant", () => {
  assert.equal(sharedAttachmentSenderEvidence.remoteMutation, false);
  assert.equal(sharedAttachmentSenderEvidence.sourceMigration.statementCount, 2);
  const sourcePath = resolve(root, sharedAttachmentSenderEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), sharedAttachmentSenderEvidence.sourceMigration.sha256);
  for (const statement of sharedAttachmentSenderEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  const successorPath = resolve(root, sharedAttachmentSenderEvidence.supersedingMigration.file);
  assert.equal(sha256(successorPath), sharedAttachmentSenderEvidence.supersedingMigration.sha256);
  const successor = sharedAttachmentSenderEvidence.supersedingMigration.functionStatement;
  assert.equal(statementSha256(successorPath, successor.startByte, successor.endByte), successor.sha256);
  assert.equal(
    sha256(resolve(root, sharedAttachmentSenderEvidence.auditQuery.file)),
    sharedAttachmentSenderEvidence.auditQuery.sha256,
  );
  assert.equal(sharedAttachmentSenderEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(sharedAttachmentSenderEvidence.isolatedReplay.sourceDataChanged, false);
  assert.equal(
    sharedAttachmentSenderEvidence.isolatedReplay.successorCanonicalization.definitionMd5,
    sharedAttachmentSenderEvidence.observedRemote.function.definitionMd5,
  );
  assert.equal(sharedAttachmentSenderEvidence.observedRemote.functionCount, 1);
  assert.equal(sharedAttachmentSenderEvidence.observedRemote.functionMismatchCount, 0);
  assert.equal(sharedAttachmentSenderEvidence.observedRemote.function.anonExecute, true);
  assert.equal(sharedAttachmentSenderEvidence.observedRemote.function.authenticatedExecute, true);
  assert.equal(sharedAttachmentSenderEvidence.observedRemote.allEffectsExact, true);
  assert.equal(sharedAttachmentSenderEvidence.allSourceEffectsAccountedFor, true);
});

test("open-community-thread decision binds its function and grant successors", () => {
  assert.equal(openCommunityEvidence.remoteMutation, false);
  assert.equal(openCommunityEvidence.sourceMigration.statementCount, 2);
  const sourcePath = resolve(root, openCommunityEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), openCommunityEvidence.sourceMigration.sha256);
  for (const statement of openCommunityEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  const successorPath = resolve(root, openCommunityEvidence.supersedingMigration.file);
  assert.equal(sha256(successorPath), openCommunityEvidence.supersedingMigration.sha256);
  for (const statement of openCommunityEvidence.supersedingMigration.statements) {
    assert.equal(statementSha256(successorPath, statement.startByte, statement.endByte), statement.sha256);
  }
  assert.equal(
    sha256(resolve(root, openCommunityEvidence.auditQuery.file)),
    openCommunityEvidence.auditQuery.sha256,
  );
  assert.equal(openCommunityEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(openCommunityEvidence.isolatedReplay.sourceDataChanged, false);
  assert.equal(
    openCommunityEvidence.isolatedReplay.successorCanonicalization.definitionMd5,
    openCommunityEvidence.observedRemote.function.definitionMd5,
  );
  assert.equal(openCommunityEvidence.observedRemote.functionCount, 1);
  assert.equal(openCommunityEvidence.observedRemote.functionMismatchCount, 0);
  assert.equal(openCommunityEvidence.observedRemote.function.anonExecute, true);
  assert.equal(openCommunityEvidence.observedRemote.function.authenticatedExecute, true);
  assert.equal(openCommunityEvidence.observedRemote.allEffectsExact, true);
  assert.equal(openCommunityEvidence.allSourceEffectsAccountedFor, true);
});

test("pg_net push function decision binds its complete versioned successor chain", () => {
  assert.equal(chatPushFunctionEvidence.remoteMutation, false);
  assert.equal(chatPushFunctionEvidence.transaction, "read-only");
  assert.equal(chatPushFunctionEvidence.sourceMigration.statementCount, 1);
  const sourcePath = resolve(root, chatPushFunctionEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), chatPushFunctionEvidence.sourceMigration.sha256);
  assert.equal(
    statementSha256(
      sourcePath,
      chatPushFunctionEvidence.sourceMigration.statement.startByte,
      chatPushFunctionEvidence.sourceMigration.statement.endByte,
    ),
    chatPushFunctionEvidence.sourceMigration.statement.sha256,
  );
  for (const successor of chatPushFunctionEvidence.versionedSuccessors) {
    const successorPath = resolve(root, successor.file);
    assert.equal(sha256(successorPath), successor.fileSha256);
    assert.equal(
      statementSha256(successorPath, successor.statement.startByte, successor.statement.endByte),
      successor.statement.sha256,
    );
  }
  assert.equal(
    sha256(resolve(root, chatPushFunctionEvidence.auditQuery.file)),
    chatPushFunctionEvidence.auditQuery.sha256,
  );
  assert.equal(chatPushFunctionEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(chatPushFunctionEvidence.isolatedReplay.sourceDataChanged, false);
  assert.equal(
    chatPushFunctionEvidence.isolatedReplay.finalSuccessorCanonicalization.definitionMd5,
    chatPushFunctionEvidence.observedRemote.function.definitionMd5,
  );
  assert.equal(chatPushFunctionEvidence.observedRemote.functionCount, 1);
  assert.equal(chatPushFunctionEvidence.observedRemote.functionMismatchCount, 0);
  assert.equal(chatPushFunctionEvidence.observedRemote.allEffectsExact, true);
  assert.equal(chatPushFunctionEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(chatPushFunctionEvidence.guarantees.deployed, false);
});

test("admin-delete policy decision preserves exact successors and the wider Community divergence", () => {
  assert.equal(adminDeleteEvidence.remoteMutation, false);
  assert.equal(adminDeleteEvidence.transaction, "read-only");
  assert.equal(adminDeleteEvidence.classification, "verified_applied_semantics");
  assert.equal(adminDeleteEvidence.sourceMigration.statementCount, 6);
  const sourcePath = resolve(root, adminDeleteEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), adminDeleteEvidence.sourceMigration.sha256);
  for (const statement of adminDeleteEvidence.sourceMigration.statements) {
    assert.equal(
      statementSha256(sourcePath, statement.startByte, statement.endByte),
      statement.sha256,
    );
  }
  const successorPath = resolve(root, adminDeleteEvidence.supersedingMigration.file);
  assert.equal(sha256(successorPath), adminDeleteEvidence.supersedingMigration.sha256);
  for (const statement of adminDeleteEvidence.supersedingMigration.statements) {
    assert.equal(
      statementSha256(successorPath, statement.startByte, statement.endByte),
      statement.sha256,
    );
  }
  assert.equal(
    sha256(resolve(root, adminDeleteEvidence.auditQuery.file)),
    adminDeleteEvidence.auditQuery.sha256,
  );
  assert.equal(adminDeleteEvidence.isolatedReplay.exitCode, 0);
  assert.equal(adminDeleteEvidence.isolatedReplay.catalogChanged, true);
  assert.equal(adminDeleteEvidence.isolatedReplay.dataChanged, false);
  assert.equal(adminDeleteEvidence.observedRemote.policyCount, 3);
  assert.equal(adminDeleteEvidence.observedRemote.policyMismatchCount, 0);
  assert.equal(adminDeleteEvidence.observedRemote.supersededPolicyCount, 0);
  assert.equal(adminDeleteEvidence.observedRemote.communityDeletePolicyCount, 4);
  assert.equal(adminDeleteEvidence.observedRemote.communityTableState.anonDelete, true);
  assert.equal(adminDeleteEvidence.observedRemote.additionalCommunityDeletePolicies.length, 3);
  assert.equal(
    adminDeleteEvidence.observedRemote.additionalCommunityDeletePolicies
      .filter(({ appliesToPublic }) => appliesToPublic).length,
    2,
  );
  assert.equal(adminDeleteEvidence.observedRemote.allEffectsExact, true);
  assert.equal(adminDeleteEvidence.allSourceEffectsAccountedFor, true);
  assert.match(adminDeleteEvidence.limits.join("\n"), /does not claim effective owner-only authorization/);
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
