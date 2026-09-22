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
const chatPushReliabilityEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-push-reliability-20260922.json",
), "utf8"));
const ugcModerationEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/ugc-moderation-semantics-20260922.json",
), "utf8"));
const chatMessageStatesEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-message-states-semantics-20260922.json",
), "utf8"));
const officialPostLanguagesEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/official-post-languages-semantics-20260922.json",
), "utf8"));
const chatPushBaseEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-push-base-semantics-20260922.json",
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
      } else if (semanticAudit.kind === "function-acl-package") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /chat-push-reliability-20260922\.json/);
      } else if (semanticAudit.kind === "catalog-package") {
        assert.equal(result.schemaChanged, false);
        assert.equal(result.outcome, "replay_no_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /ugc-moderation-semantics-20260922\.json/);
      } else if (semanticAudit.kind === "catalog-function-supersession-package") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /chat-message-states-semantics-20260922\.json/);
      } else if (semanticAudit.kind === "catalog-data-policy-supersession") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /official-post-languages-semantics-20260922\.json/);
      } else if (semanticAudit.kind === "push-catalog-function-trigger-supersession") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /chat-push-base-semantics-20260922\.json/);
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

test("base Chat push binds catalogue, IDENTITY, functions and trigger successors", () => {
  assert.equal(chatPushBaseEvidence.remoteMutation, false);
  assert.equal(chatPushBaseEvidence.sourceMigration.statementCount, 20);
  const sourcePath = resolve(root, chatPushBaseEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), chatPushBaseEvidence.sourceMigration.sha256);
  for (const statement of chatPushBaseEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  for (const successor of chatPushBaseEvidence.versionedSuccessors) {
    const successorPath = resolve(root, successor.file);
    assert.equal(sha256(successorPath), successor.fileSha256);
    assert.equal(statementSha256(successorPath, successor.startByte, successor.endByte), successor.sha256);
  }
  assert.equal(
    sha256(resolve(root, chatPushBaseEvidence.auditQuery.file)),
    chatPushBaseEvidence.auditQuery.sha256,
  );
  assert.equal(chatPushBaseEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(chatPushBaseEvidence.isolatedReplay.sourceSchemaChanged, true);
  assert.equal(chatPushBaseEvidence.isolatedReplay.sourceDataChanged, false);
  assert.deepEqual(
    Object.fromEntries(chatPushBaseEvidence.observedRemote.metadata.functions.map(
      ({ name, md5 }) => [name, md5],
    )),
    chatPushBaseEvidence.isolatedReplay.canonicalization.functionDefinitionMd5,
  );
  assert.deepEqual(chatPushBaseEvidence.observedRemote.digests, {
    extensionMd5: "96addb61d73e984884cb0952cd804fd7",
    tablesMd5: "9e17c8ed2398a88c02c1f45ed7cbe8f8",
    columnsMd5: "72f61f126757151d6ed4ccd5e02e84ce",
    constraintsMd5: "10933f4cbdae9b4a01f7457014c088e3",
    indexesMd5: "daa4f8c89aea0b9d4ba5996b78a46dfe",
    identitySequencesMd5: "1fbac4444d6729a2ce9eca59b7784d0f",
    policiesMd5: "60b19956ccd226b4878c8db3d6af5d73",
    functionsMd5: "0739ff80b769a862b588ff5cd33252f7",
    triggersMd5: "f1720ed7e3290565412dc7152285bd49",
  });
  assert.equal(chatPushBaseEvidence.observedRemote.extensionCount, 1);
  assert.equal(chatPushBaseEvidence.observedRemote.tableCount, 2);
  assert.equal(chatPushBaseEvidence.observedRemote.columnCount, 17);
  assert.equal(chatPushBaseEvidence.observedRemote.constraintCount, 11);
  assert.equal(chatPushBaseEvidence.observedRemote.indexCount, 9);
  assert.equal(chatPushBaseEvidence.observedRemote.identitySequenceCount, 1);
  assert.equal(chatPushBaseEvidence.observedRemote.policyCount, 2);
  assert.equal(chatPushBaseEvidence.observedRemote.functionCount, 2);
  assert.equal(chatPushBaseEvidence.observedRemote.triggerCount, 1);
  assert.deepEqual(chatPushBaseEvidence.observedRemote.metadata.identitySequences, [{
    type: "bigint",
    cache: "1",
    cycle: false,
    start: "1",
    table: "push_delivery_log",
    column: "id",
    maximum: "9223372036854775807",
    minimum: "1",
    increment: "1",
    sequenceName: "push_delivery_log_id_seq",
    dependencyType: "i",
    sequenceSchema: "public",
  }]);
  assert.ok(chatPushBaseEvidence.observedRemote.metadata.tables.every(({ rls }) => rls));
  assert.equal(chatPushBaseEvidence.observedRemote.allEffectsExact, true);
  assert.equal(chatPushBaseEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(chatPushBaseEvidence.guarantees.functionsExecuted, false);
  assert.equal(chatPushBaseEvidence.guarantees.providerInvoked, false);
  assert.equal(chatPushBaseEvidence.guarantees.vaultRead, false);
});

test("Official languages bind catalogue, data no-op and policy successors", () => {
  assert.equal(officialPostLanguagesEvidence.remoteMutation, false);
  assert.equal(officialPostLanguagesEvidence.sourceMigration.statementCount, 25);
  const sourcePath = resolve(root, officialPostLanguagesEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), officialPostLanguagesEvidence.sourceMigration.sha256);
  for (const statement of officialPostLanguagesEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  for (const successor of officialPostLanguagesEvidence.isolatedReplay.policySuccessorChain) {
    const successorPath = resolve(root, successor.file);
    assert.equal(statementSha256(successorPath, successor.startByte, successor.endByte), successor.sha256);
  }
  assert.equal(
    sha256(resolve(root, officialPostLanguagesEvidence.auditQuery.file)),
    officialPostLanguagesEvidence.auditQuery.sha256,
  );
  assert.equal(officialPostLanguagesEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(officialPostLanguagesEvidence.isolatedReplay.sourceSchemaChanged, true);
  assert.equal(officialPostLanguagesEvidence.isolatedReplay.sourceDataChanged, false);
  assert.equal(officialPostLanguagesEvidence.isolatedReplay.dataEffectsCurrentNoOp, true);
  assert.deepEqual(
    Object.fromEntries(officialPostLanguagesEvidence.observedRemote.metadata.functions.map(
      ({ name, md5 }) => [name, md5],
    )),
    officialPostLanguagesEvidence.isolatedReplay.canonicalization.functionDefinitionMd5,
  );
  assert.deepEqual(officialPostLanguagesEvidence.observedRemote.digests, {
    extensionMd5: "04b815c392d8e44b2a1d82787b5ad06a",
    tableMd5: "ca52b31d4a66146ed7354c48cb508347",
    columnsMd5: "6a256fc055a06db393d0fe36a1e6a723",
    constraintsMd5: "007fa4d71b0238258d5f4609bca7f5ba",
    indexesMd5: "afc925abdfaf1e5a375565b1087653db",
    triggersMd5: "d0fda67631e0a3c01db2bf4fb00d3b15",
    functionsMd5: "f52f51541ed72c06b39add1e590d68ec",
    policiesMd5: "514cbc699267aa9416ab7a05cc646b98",
  });
  assert.equal(officialPostLanguagesEvidence.observedRemote.extensionCount, 1);
  assert.equal(officialPostLanguagesEvidence.observedRemote.tableCount, 1);
  assert.equal(officialPostLanguagesEvidence.observedRemote.columnCount, 3);
  assert.equal(officialPostLanguagesEvidence.observedRemote.constraintCount, 1);
  assert.equal(officialPostLanguagesEvidence.observedRemote.indexCount, 3);
  assert.equal(officialPostLanguagesEvidence.observedRemote.triggerCount, 1);
  assert.equal(officialPostLanguagesEvidence.observedRemote.functionCount, 2);
  assert.equal(officialPostLanguagesEvidence.observedRemote.policyCount, 4);
  assert.deepEqual(officialPostLanguagesEvidence.observedRemote.metadata.extension, [{
    name: "pgcrypto",
    schema: "extensions",
    version: "1.3",
    relocatable: true,
  }]);
  const policyNames = officialPostLanguagesEvidence.observedRemote.metadata.policies.map(
    ({ name }) => name,
  );
  assert.deepEqual(policyNames, [
    "official_posts_authenticated_delete_author_or_admin",
    "official_posts_authenticated_insert_official_own",
    "official_posts_authenticated_update_author_or_admin",
    "official_posts_public_read_language",
  ]);
  assert.equal(officialPostLanguagesEvidence.observedRemote.sourceMutationPoliciesAbsent, true);
  assert.equal(officialPostLanguagesEvidence.observedRemote.allEffectsExact, true);
  assert.equal(officialPostLanguagesEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(officialPostLanguagesEvidence.guarantees.functionsExecuted, false);
});

test("chat message states bind all source effects and final function successors", () => {
  assert.equal(chatMessageStatesEvidence.remoteMutation, false);
  assert.equal(chatMessageStatesEvidence.sourceMigration.statementCount, 18);
  const sourcePath = resolve(root, chatMessageStatesEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), chatMessageStatesEvidence.sourceMigration.sha256);
  for (const statement of chatMessageStatesEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  for (const successor of chatMessageStatesEvidence.isolatedReplay.canonicalization.successorStatements) {
    const successorPath = resolve(root, successor.file);
    assert.equal(statementSha256(successorPath, successor.startByte, successor.endByte), successor.sha256);
  }
  assert.equal(
    sha256(resolve(root, chatMessageStatesEvidence.auditQuery.file)),
    chatMessageStatesEvidence.auditQuery.sha256,
  );
  assert.equal(chatMessageStatesEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(chatMessageStatesEvidence.isolatedReplay.sourceSchemaChanged, true);
  assert.equal(chatMessageStatesEvidence.isolatedReplay.sourceDataChanged, false);
  assert.deepEqual(
    Object.fromEntries(chatMessageStatesEvidence.observedRemote.metadata.functions.map(
      ({ name, md5 }) => [name, md5],
    )),
    chatMessageStatesEvidence.isolatedReplay.canonicalization.functionDefinitionMd5,
  );
  assert.deepEqual(chatMessageStatesEvidence.observedRemote.digests, {
    tableMd5: "859f1ba425a396e24efa4738bf071783",
    columnsMd5: "0c2b4b8bdca6aebc301b4f142ba4ec47",
    constraintsMd5: "b357aee237a292ca753fb04b72d87b1e",
    indexesMd5: "6e74a6bb5f3e91f5a7deda32c1274740",
    policiesMd5: "16f0845157ebf13b1b29a9268308bf1e",
    triggersMd5: "d9452ead80b1cd0e1176a2e6c2d9952b",
    functionsMd5: "4f8c9a5bb3536c4dc6d17c180a09daf7",
    publicationMd5: "512e62442ddcf84fba958598a3e46fdb",
  });
  assert.equal(chatMessageStatesEvidence.observedRemote.tableCount, 1);
  assert.equal(chatMessageStatesEvidence.observedRemote.columnCount, 7);
  assert.equal(chatMessageStatesEvidence.observedRemote.constraintCount, 5);
  assert.equal(chatMessageStatesEvidence.observedRemote.indexCount, 4);
  assert.equal(chatMessageStatesEvidence.observedRemote.policyCount, 1);
  assert.equal(chatMessageStatesEvidence.observedRemote.triggerCount, 1);
  assert.equal(chatMessageStatesEvidence.observedRemote.functionCount, 5);
  assert.equal(chatMessageStatesEvidence.observedRemote.publicationCount, 1);
  assert.equal(chatMessageStatesEvidence.observedRemote.metadata.table[0].rls, true);
  assert.equal(chatMessageStatesEvidence.observedRemote.metadata.table[0].authenticatedSelect, true);
  assert.deepEqual(chatMessageStatesEvidence.observedRemote.metadata.publication, [{
    table: "chat_message_states",
    schema: "public",
    columns: [
      "message_id",
      "thread_id",
      "profile_id",
      "status",
      "source",
      "recorded_at",
      "updated_at",
    ],
    rowFilter: null,
    publication: "supabase_realtime",
  }]);
  assert.equal(chatMessageStatesEvidence.observedRemote.allEffectsExact, true);
  assert.equal(chatMessageStatesEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(chatMessageStatesEvidence.guarantees.functionsExecuted, false);
});

test("UGC moderation binds every durable catalogue effect", () => {
  assert.equal(ugcModerationEvidence.remoteMutation, false);
  assert.equal(ugcModerationEvidence.sourceMigration.statementCount, 27);
  assert.equal(ugcModerationEvidence.sourceMigration.durableStatementCount, 25);
  assert.deepEqual(ugcModerationEvidence.sourceMigration.transactionControlOrdinals, [1, 27]);
  assert.equal(
    sha256(resolve(root, ugcModerationEvidence.sourceMigration.file)),
    ugcModerationEvidence.sourceMigration.sha256,
  );
  assert.equal(
    sha256(resolve(root, ugcModerationEvidence.auditQuery.file)),
    ugcModerationEvidence.auditQuery.sha256,
  );
  assert.equal(ugcModerationEvidence.isolatedReplay.sourceOutcome, "replay_no_change");
  assert.equal(ugcModerationEvidence.isolatedReplay.sourceSchemaChanged, false);
  assert.equal(ugcModerationEvidence.isolatedReplay.sourceDataChanged, false);
  assert.deepEqual(
    ugcModerationEvidence.observedRemote.digests,
    {
      tablesMd5: "4e5d3c6ab9bcb19075524601ae672f3b",
      columnsMd5: "e00aa07fd80d6317fd2caec25eb2933d",
      identitySequencesMd5: "f32a76fd9c780d8eee944e77d9e91e57",
      constraintsMd5: "c3b15887bf1d3800dd2c406ba498a6b3",
      indexesMd5: "608e5333c6709f2b9861fa93d8378845",
      policiesMd5: "88455a5794bcdab780cfc5834b201c19",
      functionsMd5: "5c8cd393f255364f508282051630d553",
    },
  );
  assert.equal(ugcModerationEvidence.observedRemote.tableCount, 2);
  assert.equal(ugcModerationEvidence.observedRemote.columnCount, 14);
  assert.equal(ugcModerationEvidence.observedRemote.constraintCount, 13);
  assert.equal(ugcModerationEvidence.observedRemote.indexCount, 5);
  assert.equal(ugcModerationEvidence.observedRemote.policyCount, 2);
  assert.equal(ugcModerationEvidence.observedRemote.functionCount, 5);
  assert.equal(ugcModerationEvidence.observedRemote.identitySequenceCount, 1);
  assert.equal(ugcModerationEvidence.observedRemote.metadata.tables.length, 2);
  assert.equal(ugcModerationEvidence.observedRemote.metadata.columns.length, 14);
  assert.equal(ugcModerationEvidence.observedRemote.metadata.constraints.length, 13);
  assert.equal(ugcModerationEvidence.observedRemote.metadata.indexes.length, 5);
  assert.equal(ugcModerationEvidence.observedRemote.metadata.policies.length, 2);
  assert.equal(ugcModerationEvidence.observedRemote.metadata.functions.length, 5);
  assert.deepEqual(ugcModerationEvidence.observedRemote.metadata.identitySequences, [{
    type: "bigint",
    cache: "1",
    cycle: false,
    start: "1",
    table: "ugc_reports",
    column: "id",
    maximum: "9223372036854775807",
    minimum: "1",
    increment: "1",
    sequenceName: "ugc_reports_id_seq",
    dependencyType: "i",
    sequenceSchema: "public",
  }]);
  assert.ok(ugcModerationEvidence.observedRemote.metadata.tables.every(({ rls }) => rls));
  assert.ok(ugcModerationEvidence.observedRemote.metadata.functions.every(
    ({ publicExecute, authenticatedExecute }) => !publicExecute && authenticatedExecute,
  ));
  assert.deepEqual(
    Object.fromEntries(ugcModerationEvidence.observedRemote.metadata.functions.map(
      ({ name, md5 }) => [name, md5],
    )),
    ugcModerationEvidence.isolatedReplay.canonicalization.functionDefinitionMd5,
  );
  assert.equal(ugcModerationEvidence.observedRemote.rlsEnabledOnBothTables, true);
  assert.equal(ugcModerationEvidence.observedRemote.publicExecuteRevokedOnAllFunctions, true);
  assert.equal(ugcModerationEvidence.observedRemote.authenticatedDirectExecuteOnAllFunctions, true);
  assert.equal(ugcModerationEvidence.observedRemote.allEffectsExact, true);
  assert.equal(ugcModerationEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(ugcModerationEvidence.guarantees.functionsExecuted, false);
});

test("chat push reliability binds both functions and the unregister ACL", () => {
  assert.equal(chatPushReliabilityEvidence.remoteMutation, false);
  assert.equal(chatPushReliabilityEvidence.sourceMigration.statementCount, 5);
  const sourcePath = resolve(root, chatPushReliabilityEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), chatPushReliabilityEvidence.sourceMigration.sha256);
  for (const statement of chatPushReliabilityEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  assert.equal(
    sha256(resolve(root, chatPushReliabilityEvidence.auditQuery.file)),
    chatPushReliabilityEvidence.auditQuery.sha256,
  );
  assert.equal(chatPushReliabilityEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(chatPushReliabilityEvidence.isolatedReplay.sourceDataChanged, false);
  assert.equal(
    chatPushReliabilityEvidence.isolatedReplay.canonicalization.enqueueDefinitionMd5,
    chatPushFunctionEvidence.observedRemote.function.definitionMd5,
  );
  assert.equal(chatPushReliabilityEvidence.observedRemote.functionCount, 2);
  assert.equal(chatPushReliabilityEvidence.observedRemote.functionMismatchCount, 0);
  assert.equal(chatPushReliabilityEvidence.observedRemote.unregisterPublicExecute, false);
  assert.equal(chatPushReliabilityEvidence.observedRemote.unregisterAnonExecute, false);
  assert.equal(chatPushReliabilityEvidence.observedRemote.unregisterAuthenticatedExecute, true);
  assert.deepEqual(
    chatPushReliabilityEvidence.observedRemote.unregisterDirectExecuteAcl,
    ["authenticated", "service_role"],
  );
  assert.equal(chatPushReliabilityEvidence.observedRemote.allEffectsExact, true);
  assert.equal(chatPushReliabilityEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(chatPushReliabilityEvidence.guarantees.providerInvoked, false);
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
