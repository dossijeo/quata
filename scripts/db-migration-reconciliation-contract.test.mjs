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
const pushTokenSingleActiveEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/push-token-single-active-supersession-20260922.json",
), "utf8"));
const contactDiscoveryEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/contact-discovery-semantics-20260922.json",
), "utf8"));
const attachmentPreviewsEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-attachment-previews-semantics-20260922.json",
), "utf8"));
const androidRuntimeSupportEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-android-runtime-support-semantics-20260922.json",
), "utf8"));
const officialAccountsEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/official-accounts-semantics-20260922.json",
), "utf8"));
const chatGetThreadPaginationEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-get-thread-pagination-20260922.json",
), "utf8"));
const chatCommunityMembersRepairEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-community-members-repair-20260922.json",
), "utf8"));
const accountDeactivationAuthLinkEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/account-deactivation-auth-link-20260922.json",
), "utf8"));
const conversationUserStateEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/conversation-user-state-semantics-20260922.json",
), "utf8"));
const chatRpcEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/chat-rpc-semantics-20260922.json",
), "utf8"));
const privateThreadMembershipEvidence = JSON.parse(readFileSync(resolve(
  root,
  "docs/runbooks/migration/evidence/private-thread-membership-reconciliation-20260922.json",
), "utf8"));

const sha256 = (path) => createHash("sha256").update(readFileSync(path)).digest("hex");
const statementSha256 = (path, startByte, endByte) => createHash("sha256")
  .update(readFileSync(path).subarray(startByte, endByte))
  .digest("hex");

const sqlFunctionDefinition = (sql, signature) => {
  const start = sql.indexOf(signature);
  assert.notEqual(start, -1, `missing SQL function: ${signature}`);
  const plainEnd = sql.indexOf("\n$$;", start);
  const namedEnd = sql.indexOf("\n$function$;", start);
  const end = plainEnd === -1 ? namedEnd : plainEnd;
  assert.notEqual(end, -1, `unterminated SQL function: ${signature}`);
  return sql.slice(start, end + (plainEnd === -1 ? 12 : 4));
};

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
    if (semanticAudit?.kind === "catalog-data-function-successor-auth-incomplete") {
      assert.equal(result.exitCode, 3);
      assert.equal(result.outcome, "incomplete_missing_auth_rows");
      assert.equal(semanticAudit.rawReplayExitCode, 3);
      assert.equal(semanticAudit.rawReplayOutcome, "incomplete_missing_auth_rows");
      assert.equal(semanticAudit.dataChanged, null);
      assert.equal(semanticAudit.allEffectsExact, true);
      assert.match(decision.evidence, /official-accounts-semantics-20260922\.json/);
      continue;
    }
    if (semanticAudit?.kind === "function-grant-storage-policy-supersession") {
      assert.equal(result.exitCode, 3);
      assert.equal(result.outcome, "incomplete_missing_storage_schema");
      assert.equal(semanticAudit.rawReplayExitCode, 3);
      assert.equal(semanticAudit.rawReplayOutcome, "incomplete_missing_storage_schema");
      assert.equal(semanticAudit.dataChanged, null);
      assert.equal(semanticAudit.allEffectsExact, true);
      assert.match(decision.evidence, /chat-android-runtime-support-semantics-20260922\.json/);
      continue;
    }
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
    if ([
      "function-acl-data-supersession",
      "function-trigger-backfill-maintenance",
    ].includes(semanticAudit?.kind)) {
      assert.equal(result.dataChanged, true);
    } else {
      assert.equal(result.dataChanged, false);
    }
    if (semanticAudit) {
      const semanticEvidence = JSON.parse(readFileSync(resolve(root, semanticAudit.evidence), "utf8"));
      assert.equal(
        semanticAudit.auditQuerySha256,
        semanticEvidence.auditQuery.sha256,
        `aggregate audit hash mismatch for ${decision.file}`,
      );
      assert.equal(
        sha256(resolve(root, semanticEvidence.auditQuery.file)),
        semanticEvidence.auditQuery.sha256,
        `audit file hash mismatch for ${decision.file}`,
      );
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
      } else if (semanticAudit.kind === "function-acl-data-supersession") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_and_data_change");
        assert.equal(semanticAudit.dataChanged, true);
        assert.match(decision.evidence, /push-token-single-active-supersession-20260922\.json/);
      } else if (semanticAudit.kind === "catalog-data-postcondition-maintenance") {
        assert.equal(result.schemaChanged, true);
        assert.equal(result.outcome, "schema_change");
        assert.equal(semanticAudit.dataChanged, false);
        assert.match(decision.evidence, /contact-discovery-semantics-20260922\.json/);
      } else if (semanticAudit.kind === "function-trigger-backfill-maintenance") {
        assert.equal(result.schemaChanged, false);
        assert.equal(result.outcome, "data_change");
        assert.equal(semanticAudit.dataChanged, true);
        assert.equal(semanticAudit.onlyUpdatedAtChanged, true);
        assert.match(decision.evidence, /chat-attachment-previews-semantics-20260922\.json/);
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

test("Chat thread pagination repair restores the versioned latest bounded page", () => {
  const repairPath = resolve(root, chatGetThreadPaginationEvidence.repairCandidate.file);
  const rollbackPath = resolve(root, chatGetThreadPaginationEvidence.rollbackCandidate.file);
  const sourcePath = resolve(root, chatGetThreadPaginationEvidence.repairCandidate.sourceDefinitionFile);
  assert.equal(sha256(repairPath), chatGetThreadPaginationEvidence.repairCandidate.sha256);
  assert.equal(sha256(rollbackPath), chatGetThreadPaginationEvidence.rollbackCandidate.sha256);
  assert.equal(sha256(sourcePath), chatGetThreadPaginationEvidence.repairCandidate.sourceDefinitionFileSha256);
  const signature = "create or replace function public.quata_chat_get_thread(";
  const repairDefinition = sqlFunctionDefinition(readFileSync(repairPath, "utf8"), signature);
  const sourceDefinition = sqlFunctionDefinition(readFileSync(sourcePath, "utf8"), signature);
  assert.equal(repairDefinition, sourceDefinition);
  assert.match(repairDefinition, /order by m\.created_at desc, m\.id desc\s+limit v_limit/);
  assert.match(repairDefinition, /jsonb_agg\([^\n]+order by q\.created_at, q\.id\)/);
  assert.doesNotMatch(repairDefinition, /order by m\.created_at asc, m\.id asc\s+limit v_limit/);
  const rollbackDefinition = sqlFunctionDefinition(readFileSync(rollbackPath, "utf8"), signature);
  assert.equal(
    rollbackDefinition.replace("order by m.created_at asc, m.id asc", "order by m.created_at desc, m.id desc"),
    repairDefinition,
  );
  assert.equal(chatGetThreadPaginationEvidence.rollbackCandidate.executed, false);
  assert.equal(chatGetThreadPaginationEvidence.remoteBefore.selectsOldestBeforeLimit, true);
  assert.equal(chatGetThreadPaginationEvidence.remoteBefore.selectsLatestBeforeLimit, false);
  assert.equal(chatGetThreadPaginationEvidence.focalTrial.classification, "oldest_page");
  assert.equal(chatGetThreadPaginationEvidence.focalTrial.expectedVersionedClassification, "latest_page");
  assert.equal(chatGetThreadPaginationEvidence.cleanup.status, "passed");
  assert.equal(chatGetThreadPaginationEvidence.cleanup.residue, false);
  assert.equal(chatGetThreadPaginationEvidence.repairCandidate.deployed, false);
  assert.equal(chatGetThreadPaginationEvidence.historicalReconciliation.classificationChanged, false);
  assert.equal(chatGetThreadPaginationEvidence.historicalReconciliation.selectivePackageEligible, false);
});

test("Community member repair restores transliteration and repeats only the original backfills", () => {
  const repairPath = resolve(root, chatCommunityMembersRepairEvidence.repairCandidate.file);
  const rollbackPath = resolve(root, chatCommunityMembersRepairEvidence.rollbackCandidate.file);
  const sourcePath = resolve(root, chatCommunityMembersRepairEvidence.sourceMigration.file);
  assert.equal(sha256(repairPath), chatCommunityMembersRepairEvidence.repairCandidate.sha256);
  assert.equal(sha256(rollbackPath), chatCommunityMembersRepairEvidence.rollbackCandidate.sha256);
  assert.equal(sha256(sourcePath), chatCommunityMembersRepairEvidence.sourceMigration.sha256);
  const repairSql = readFileSync(repairPath, "utf8");
  const sourceSql = readFileSync(sourcePath, "utf8");
  const signature = "create or replace function public.quata_chat_community_key(";
  assert.equal(sqlFunctionDefinition(repairSql, signature), sqlFunctionDefinition(sourceSql, signature));
  const sourceBackfills = sourceSql.slice(sourceSql.indexOf(
    "insert into public.chat_participants(thread_id, profile_id, role)\nselect t.id",
  )).trim();
  assert.equal(repairSql.slice(repairSql.indexOf(
    "insert into public.chat_participants(thread_id, profile_id, role)\nselect t.id",
  )).trim(), sourceBackfills);
  assert.equal((repairSql.match(/insert into public\.chat_participants/g) ?? []).length, 2);
  assert.equal(readFileSync(rollbackPath, "utf8").match(/\?{50}/)?.[0].length, 50);
  assert.equal(chatCommunityMembersRepairEvidence.remoteBefore.accentedNormalizationSample, null);
  assert.equal(chatCommunityMembersRepairEvidence.remoteBefore.creatorPostconditionViolations, 1);
  assert.equal(chatCommunityMembersRepairEvidence.remoteBefore.memberPostconditionViolations, 3);
  assert.equal(chatCommunityMembersRepairEvidence.repairCandidate.statementCount, 3);
  assert.equal(chatCommunityMembersRepairEvidence.repairCandidate.deployed, false);
  assert.equal(chatCommunityMembersRepairEvidence.rollbackCandidate.revertsParticipantData, false);
  assert.equal(chatCommunityMembersRepairEvidence.historicalReconciliation.classificationChanged, false);
});

test("Account deactivation successor versions the deployed Auth-link preservation", () => {
  const repairPath = resolve(root, accountDeactivationAuthLinkEvidence.repairCandidate.file);
  const rollbackPath = resolve(root, accountDeactivationAuthLinkEvidence.rollbackCandidate.file);
  const sourcePath = resolve(root, accountDeactivationAuthLinkEvidence.sourceMigration.file);
  assert.equal(sha256(repairPath), accountDeactivationAuthLinkEvidence.repairCandidate.sha256);
  assert.equal(sha256(rollbackPath), accountDeactivationAuthLinkEvidence.rollbackCandidate.sha256);
  assert.equal(sha256(sourcePath), accountDeactivationAuthLinkEvidence.sourceMigration.sha256);
  const signature = "CREATE OR REPLACE FUNCTION public.quata_account_deactivate(";
  const repairDefinition = sqlFunctionDefinition(readFileSync(repairPath, "utf8"), signature);
  const rollbackDefinition = sqlFunctionDefinition(readFileSync(rollbackPath, "utf8"), signature);
  assert.equal(repairDefinition, rollbackDefinition);
  assert.match(
    repairDefinition,
    /deactivated_auth_user_id = p_auth_user_id,\s+auth_user_id = null/,
  );
  assert.doesNotMatch(
    sqlFunctionDefinition(readFileSync(sourcePath, "utf8"), "create or replace function public.quata_account_deactivate("),
    /deactivated_auth_user_id/,
  );
  for (const sql of [readFileSync(repairPath, "utf8"), readFileSync(rollbackPath, "utf8")]) {
    assert.match(sql, /revoke all on function public\.quata_account_deactivate\(uuid, uuid\) from public, anon, authenticated;/i);
    assert.match(sql, /grant execute on function public\.quata_account_deactivate\(uuid, uuid\) to service_role;/i);
  }
  assert.equal(accountDeactivationAuthLinkEvidence.remoteBefore.deactivateDefinitionMd5, "d2504acfb2095176289fb99a939f7621");
  assert.deepEqual(accountDeactivationAuthLinkEvidence.remoteBefore.deactivateAcl, [
    "postgres=X/postgres",
    "service_role=X/postgres",
  ]);
  assert.equal(accountDeactivationAuthLinkEvidence.repairCandidate.semanticNoOpAgainstObservedRemote, true);
  assert.equal(accountDeactivationAuthLinkEvidence.repairCandidate.deployed, false);
  assert.equal(accountDeactivationAuthLinkEvidence.historicalReconciliation.classificationChanged, false);
});

test("Conversation user state binds its catalogue, function divergence and bounded repair", () => {
  const sourcePath = resolve(root, conversationUserStateEvidence.sourceMigration.file);
  const auditPath = resolve(root, conversationUserStateEvidence.auditQuery.file);
  const repairPath = resolve(root, conversationUserStateEvidence.visibilityRepairCandidate.file);
  const rollbackPath = resolve(root, conversationUserStateEvidence.rollbackCandidate.file);
  const paginationPath = resolve(root, conversationUserStateEvidence.paginationSuccessor.file);

  assert.equal(sha256(sourcePath), conversationUserStateEvidence.sourceMigration.sha256);
  assert.equal(sha256(auditPath), conversationUserStateEvidence.auditQuery.sha256);
  assert.equal(sha256(repairPath), conversationUserStateEvidence.visibilityRepairCandidate.sha256);
  assert.equal(sha256(rollbackPath), conversationUserStateEvidence.rollbackCandidate.sha256);
  assert.equal(sha256(paginationPath), conversationUserStateEvidence.paginationSuccessor.sha256);
  assert.equal(
    statementSha256(
      repairPath,
      conversationUserStateEvidence.visibilityRepairCandidate.statementStartByte,
      conversationUserStateEvidence.visibilityRepairCandidate.statementEndByte,
    ),
    conversationUserStateEvidence.visibilityRepairCandidate.statementSha256,
  );
  assert.equal(conversationUserStateEvidence.sourceMigration.statementCount, 34);
  assert.deepEqual(conversationUserStateEvidence.sourceMigration.statementKinds, {
    AlterTableStmt: 1,
    CreateFunctionStmt: 15,
    CreatePolicyStmt: 1,
    CreateStmt: 1,
    CreateTrigStmt: 3,
    DoStmt: 1,
    DropStmt: 5,
    GrantStmt: 3,
    IndexStmt: 3,
    InsertStmt: 1,
  });

  const catalog = conversationUserStateEvidence.observedRemote.catalog;
  assert.equal(catalog.table.rls, true);
  assert.equal(catalog.table.forceRls, false);
  assert.equal(catalog.columns.length, 8);
  assert.equal(catalog.constraints.length, 6);
  assert.equal(catalog.indexes.length, 5);
  assert.equal(catalog.policy.length, 1);
  assert.equal(catalog.triggers.length, 3);
  assert.equal(catalog.functions.length, 15);
  assert.equal(catalog.missingParticipantStates, 0);
  assert.equal(catalog.missingVisibilityBoundaries, 75);
  assert.deepEqual(
    Object.fromEntries(catalog.functions.map(({ name, md5 }) => [name, md5])),
    conversationUserStateEvidence.observedRemote.functionDefinitionMd5,
  );
  assert.deepEqual(catalog.policy, [{
    name: "conversation_user_state_select_thread_participants",
    roles: ["authenticated"],
    using: "quata_chat_is_thread_participant(conversation_id, quata_chat_auth_profile_id())",
    command: "SELECT",
    withCheck: null,
  }]);
  assert.deepEqual(catalog.triggers.map(({ name, enabled }) => ({ name, enabled })), [
    { name: "aa_chat_messages_after_insert_reactivate_user_state", enabled: "O" },
    { name: "chat_participants_sync_conversation_user_state", enabled: "O" },
    { name: "conversation_user_state_touch_updated_at", enabled: "O" },
  ]);

  const canonical = conversationUserStateEvidence.isolatedReplay.canonicalFunctions;
  assert.equal(canonical.functionCount, 15);
  assert.equal(canonical.exactMatchCount, 14);
  assert.equal(canonical.onlyMismatch, "quata_chat_get_thread");
  assert.equal(canonical.sourceGetThreadMd5, "c562a976373fe60fdc554094ceb3bbe8");
  assert.equal(canonical.remoteGetThreadMd5, "f0516fd6c639b607623d3bd6d3dc8339");
  assert.equal(
    conversationUserStateEvidence.paginationSuccessor.evidence,
    "docs/runbooks/migration/evidence/chat-get-thread-pagination-20260922.json",
  );
  assert.equal(conversationUserStateEvidence.paginationSuccessor.deployed, false);
  assert.equal(chatGetThreadPaginationEvidence.repairCandidate.deployed, false);
  assert.equal(
    chatGetThreadPaginationEvidence.repairCandidate.sha256,
    conversationUserStateEvidence.paginationSuccessor.sha256,
  );

  assert.equal(conversationUserStateEvidence.observedRemote.missingVisibilityBoundaries, 75);
  assert.equal(conversationUserStateEvidence.isolatedReplay.snapshotMissingVisibilityBoundariesBefore, 76);
  assert.equal(conversationUserStateEvidence.isolatedReplay.liveRemoteMissingVisibilityBoundaries, 75);
  assert.equal(conversationUserStateEvidence.isolatedReplay.snapshotAndLiveRemoteMeasuredAtDifferentTimes, true);
  assert.deepEqual(conversationUserStateEvidence.isolatedReplay.snapshotDataDelta, {
    rowsBefore: 618,
    rowsAfter: 618,
    insertedRows: 0,
    changedRows: 618,
    updatedAtChangedRows: 618,
    firstVisibleMessageIdChangedRows: 76,
    otherColumnsChangedRows: 0,
    missingParticipantStatesAfter: 0,
  });
  assert.deepEqual(conversationUserStateEvidence.visibilityRepairCandidate.targetedReplay, {
    rowsBefore: 618,
    rowsAfter: 618,
    changedRows: 76,
    firstVisibleMessageIdChangedRows: 76,
    updatedAtChangedRows: 76,
    otherColumnsChangedRows: 0,
    remainingMissingVisibilityBoundaries: 0,
  });
  assert.equal(conversationUserStateEvidence.visibilityRepairCandidate.statementCount, 1);
  assert.equal(conversationUserStateEvidence.visibilityRepairCandidate.deployed, false);
  assert.equal(conversationUserStateEvidence.rollbackCandidate.genericSqlRollbackSafe, false);
  assert.equal(conversationUserStateEvidence.historicalReconciliation.classificationChanged, false);
  assert.equal(conversationUserStateEvidence.historicalReconciliation.selectivePackageEligible, false);
  assert.equal(conversationUserStateEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(conversationUserStateEvidence.guarantees.remoteDdlExecuted, false);
  assert.equal(conversationUserStateEvidence.guarantees.remoteDmlExecuted, false);
});

test("Chat RPC binds all 31 functions, grants and exact versioned successors", () => {
  const sourcePath = resolve(root, chatRpcEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), chatRpcEvidence.sourceMigration.sha256);
  assert.equal(chatRpcEvidence.sourceMigration.statementCount, 62);
  assert.equal(chatRpcEvidence.sourceMigration.statements.filter(({ kind }) => kind === "CreateFunctionStmt").length, 31);
  assert.equal(chatRpcEvidence.sourceMigration.statements.filter(({ kind }) => kind === "GrantStmt").length, 31);
  for (const statement of chatRpcEvidence.sourceMigration.statements) {
    assert.equal(
      statementSha256(sourcePath, statement.startByte, statement.endByte),
      statement.sha256,
    );
  }
  assert.equal(
    sha256(resolve(root, chatRpcEvidence.auditQuery.file)),
    chatRpcEvidence.auditQuery.sha256,
  );
  assert.equal(chatRpcEvidence.observedRemote.functionCount, 31);
  assert.equal(chatRpcEvidence.observedRemote.functions.length, 31);
  assert.equal(chatRpcEvidence.observedRemote.allAnonExecute, true);
  assert.equal(chatRpcEvidence.observedRemote.allAuthenticatedExecute, true);
  assert.ok(chatRpcEvidence.observedRemote.functions.every(
    ({ anonExecute, authenticatedExecute }) => anonExecute && authenticatedExecute,
  ));

  const remoteMd5 = Object.fromEntries(chatRpcEvidence.observedRemote.functions.map(
    ({ name, md5 }) => [name, md5],
  ));
  const replay = chatRpcEvidence.isolatedReplay;
  assert.equal(Object.keys(replay.sourceCanonicalMd5).length, 31);
  assert.equal(Object.keys(replay.latestVersionedCanonicalMd5).length, 31);
  assert.equal(replay.sourceExactRemoteCount, 19);
  assert.equal(replay.sourceSupersededCount, 12);
  assert.equal(replay.latestExactRemoteCount, 30);
  assert.deepEqual(replay.latestRemoteMismatches, [{
    function: "quata_chat_get_thread",
    versionedMd5: "c562a976373fe60fdc554094ceb3bbe8",
    remoteMd5: "f0516fd6c639b607623d3bd6d3dc8339",
  }]);
  assert.equal(
    Object.entries(replay.latestVersionedCanonicalMd5).filter(
      ([name, md5]) => remoteMd5[name] === md5,
    ).length,
    30,
  );
  assert.equal(chatRpcEvidence.versionedSuccessors.length, 13);
  for (const successor of chatRpcEvidence.versionedSuccessors) {
    const successorPath = resolve(root, successor.file);
    assert.equal(sha256(successorPath), successor.fileSha256);
    assert.equal(
      statementSha256(successorPath, successor.startByte, successor.endByte),
      successor.statementSha256,
    );
    assert.equal(
      replay.latestVersionedCanonicalMd5[successor.function],
      successor.canonicalMd5,
    );
  }
  assert.equal(chatRpcEvidence.paginationSuccessor.deployed, false);
  assert.equal(chatRpcEvidence.paginationSuccessor.sha256, chatGetThreadPaginationEvidence.repairCandidate.sha256);
  assert.equal(chatRpcEvidence.historicalReconciliation.classificationChanged, false);
  assert.equal(chatRpcEvidence.historicalReconciliation.selectivePackageEligible, false);
  assert.equal(chatRpcEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(chatRpcEvidence.guarantees.functionsExecutedRemotely, false);
  assert.equal(chatRpcEvidence.guarantees.remoteDdlExecuted, false);
  assert.equal(chatRpcEvidence.guarantees.remoteDmlExecuted, false);
});

test("Private thread membership is re-established without claiming deleted history", () => {
  const source = privateThreadMembershipEvidence.sourceMigration;
  const repair = privateThreadMembershipEvidence.repairCandidate;
  const sourcePath = resolve(root, source.file);
  const repairPath = resolve(root, repair.file);
  assert.equal(sha256(sourcePath), source.sha256);
  assert.equal(sha256(repairPath), repair.sha256);
  assert.equal(source.statementCount, 5);
  assert.equal(repair.statementCount, 5);
  for (const item of [source, repair]) {
    const itemPath = resolve(root, item.file);
    for (const statement of item.statements) {
      assert.equal(statementSha256(itemPath, statement.startByte, statement.endByte), statement.sha256);
    }
  }
  assert.deepEqual(
    repair.statements.map(({ kind, sha256: hash }) => ({ kind, hash })),
    source.statements.map(({ kind, sha256: hash }) => ({ kind, hash })),
  );
  assert.equal(
    sha256(resolve(root, privateThreadMembershipEvidence.auditQuery.file)),
    privateThreadMembershipEvidence.auditQuery.sha256,
  );
  assert.equal(
    sha256(resolve(root, privateThreadMembershipEvidence.rollbackCandidate.file)),
    privateThreadMembershipEvidence.rollbackCandidate.sha256,
  );
  assert.equal(privateThreadMembershipEvidence.observedRemote.function.md5, "e857da171d692c6b9e128d8d259a8db1");
  assert.equal(privateThreadMembershipEvidence.observedRemote.function.securityDefiner, true);
  assert.deepEqual(privateThreadMembershipEvidence.observedRemote.function.config, ["search_path=public"]);
  assert.equal(privateThreadMembershipEvidence.observedRemote.trigger.enabled, "O");
  assert.equal(privateThreadMembershipEvidence.observedRemote.trigger.deferrable, true);
  assert.equal(privateThreadMembershipEvidence.observedRemote.trigger.initiallyDeferred, true);
  assert.equal(privateThreadMembershipEvidence.observedRemote.currentMappings, 152);
  assert.equal(privateThreadMembershipEvidence.observedRemote.invalidCurrentMappings, 0);
  assert.equal(privateThreadMembershipEvidence.isolatedReplay.functionMd5, "e857da171d692c6b9e128d8d259a8db1");
  assert.equal(privateThreadMembershipEvidence.isolatedReplay.mappingsBefore, 152);
  assert.equal(privateThreadMembershipEvidence.isolatedReplay.mappingsAfter, 152);
  assert.equal(privateThreadMembershipEvidence.isolatedReplay.deletedMappings, 0);
  assert.equal(privateThreadMembershipEvidence.isolatedReplay.changedThreads, 0);
  assert.equal(privateThreadMembershipEvidence.isolatedReplay.invalidAfter, 0);
  assert.equal(repair.copiesAllSourceStatementsExactly, true);
  assert.equal(repair.deployed, false);
  assert.equal(privateThreadMembershipEvidence.rollbackCandidate.genericDataRollbackSafe, false);
  assert.equal(privateThreadMembershipEvidence.historicalReconciliation.classificationChanged, false);
  assert.equal(privateThreadMembershipEvidence.historicalReconciliation.selectivePackageEligible, false);
  assert.equal(privateThreadMembershipEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(privateThreadMembershipEvidence.guarantees.remoteDmlExecuted, false);
});

test("Official Accounts binds all catalogue, role, DML and successor effects", () => {
  assert.equal(officialAccountsEvidence.remoteMutation, false);
  assert.equal(officialAccountsEvidence.sourceMigration.statementCount, 45);
  const sourcePath = resolve(root, officialAccountsEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), officialAccountsEvidence.sourceMigration.sha256);
  for (const statement of officialAccountsEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  assert.equal(
    sha256(resolve(root, officialAccountsEvidence.auditQuery.file)),
    officialAccountsEvidence.auditQuery.sha256,
  );
  assert.equal(officialAccountsEvidence.isolatedReplay.sourceExitCode, 3);
  assert.equal(officialAccountsEvidence.isolatedReplay.sourceOutcome, "incomplete_missing_auth_rows");
  assert.equal(
    sha256(resolve(root, officialAccountsEvidence.officialPostsGuardSuccessor.file)),
    officialAccountsEvidence.officialPostsGuardSuccessor.sha256,
  );
  assert.deepEqual(
    Object.fromEntries(officialAccountsEvidence.observedRemote.metadata.functions.map(
      ({ name, md5 }) => [name, md5],
    )),
    {
      quata_current_profile_id: "2e2f606090972cc93b4a102067c989db",
      quata_current_profile_is_admin: "4bcc5307e8823bc0e89ccfbb420d3b11",
      quata_current_role_is_service: "83b1867136924831868fbacfe72f30a1",
      quata_guard_official_post_comments: "e47fc93ceca327db95f007731436e38c",
      quata_guard_official_post_likes: "a7a42ed79f6f245516ebf9b15aa304c3",
      quata_guard_official_posts: officialAccountsEvidence.officialPostsGuardSuccessor.definitionMd5,
      quata_guard_profile_roles: "46e4af7a3707a4cdcee909a2aabbd3fb",
    },
  );
  assert.deepEqual(officialAccountsEvidence.observedRemote.digests, {
    tablesMd5: "f43f74ddf31a716e78fead128d8b20ab",
    columnsMd5: "d106ad79e741d72d24b8813f433c9bd4",
    indexesMd5: "0fb1fa28a0ee4e4764f11479a025a2d9",
    triggersMd5: "11d1d0adf88f7621326d7748335a7d06",
    extensionMd5: "04b815c392d8e44b2a1d82787b5ad06a",
    functionsMd5: "5c5718f1a5dd7a3d73345cae94a77d4d",
    privilegesMd5: "2c89014c4f432e1f7fa67a554957c473",
    constraintsMd5: "13d74a98903ad3dbb8f4ec0d5d6b677f",
    publicationMd5: "96caccf260f5d892a98ef7b937555cfa",
    profileColumnsMd5: "084400c6e76e03ac81c02c39961f5b45",
    profileIndexesMd5: "8260abce5621506784f3a8f2e108d853",
    dataPostconditionMd5: "70eb626f242fcc98c103258a7362bb65",
    profileRoleColumnPrivilegesMd5: "c38db2590bf4a6853e3549d3ba367c15",
  });
  assert.equal(officialAccountsEvidence.observedRemote.metadata.tables.length, 3);
  assert.equal(officialAccountsEvidence.observedRemote.metadata.columns.length, 26);
  assert.equal(officialAccountsEvidence.observedRemote.metadata.constraints.length, 13);
  assert.equal(officialAccountsEvidence.observedRemote.metadata.indexes.length, 4);
  assert.equal(officialAccountsEvidence.observedRemote.metadata.functions.length, 7);
  assert.equal(officialAccountsEvidence.observedRemote.metadata.triggers.length, 4);
  assert.equal(officialAccountsEvidence.observedRemote.metadata.publication.length, 3);
  assert.deepEqual(
    officialAccountsEvidence.observedRemote.metadata.privileges
      .filter(({ role }) => role === "anon")
      .map(({ table, maintain }) => ({ table, maintain })),
    [
      { table: "official_post_comments", maintain: false },
      { table: "official_post_likes", maintain: false },
      { table: "official_posts", maintain: false },
    ],
  );
  assert.deepEqual(
    officialAccountsEvidence.observedRemote.metadata.profileRoleColumnPrivileges,
    [
      {
        acl: ["authenticated=w/postgres"],
        column: "is_admin",
        directAnonUpdate: false,
        effectiveAnonUpdate: true,
        directAuthenticatedUpdate: true,
        effectiveAuthenticatedUpdate: true,
      },
      {
        acl: ["authenticated=w/postgres"],
        column: "is_official",
        directAnonUpdate: false,
        effectiveAnonUpdate: true,
        directAuthenticatedUpdate: true,
        effectiveAuthenticatedUpdate: true,
      },
    ],
  );
  assert.equal(officialAccountsEvidence.observedRemote.metadata.dataPostcondition.allAdminCandidatesSatisfied, true);
  assert.equal(officialAccountsEvidence.observedRemote.metadata.dataPostcondition.allOfficialCandidatesSatisfied, true);
  assert.equal(officialAccountsEvidence.observedRemote.allEffectsExact, true);
  assert.equal(officialAccountsEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(officialAccountsEvidence.guarantees.businessValuesEmitted, false);
  assert.equal(officialAccountsEvidence.guarantees.functionsExecuted, false);
  assert.equal(officialAccountsEvidence.guarantees.triggersFired, false);
  assert.equal(officialAccountsEvidence.guarantees.deployed, false);
});

test("Android runtime support binds the function/grant chain and four Storage policies", () => {
  assert.equal(androidRuntimeSupportEvidence.remoteMutation, false);
  assert.equal(androidRuntimeSupportEvidence.sourceMigration.statementCount, 10);
  const sourcePath = resolve(root, androidRuntimeSupportEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), androidRuntimeSupportEvidence.sourceMigration.sha256);
  for (const statement of androidRuntimeSupportEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  assert.equal(androidRuntimeSupportEvidence.functionSuccessorChain.length, 2);
  for (const successor of androidRuntimeSupportEvidence.functionSuccessorChain) {
    const successorPath = resolve(root, successor.file);
    assert.equal(sha256(successorPath), successor.sha256);
    assert.equal(
      statementSha256(
        successorPath,
        successor.functionStatement.startByte,
        successor.functionStatement.endByte,
      ),
      successor.functionStatement.sha256,
    );
    if (successor.grantStatement) {
      assert.equal(
        statementSha256(
          successorPath,
          successor.grantStatement.startByte,
          successor.grantStatement.endByte,
        ),
        successor.grantStatement.sha256,
      );
    }
  }
  assert.equal(
    sha256(resolve(root, androidRuntimeSupportEvidence.auditQuery.file)),
    androidRuntimeSupportEvidence.auditQuery.sha256,
  );
  assert.equal(androidRuntimeSupportEvidence.isolatedReplay.sourceExitCode, 3);
  assert.equal(androidRuntimeSupportEvidence.isolatedReplay.sourceOutcome, "incomplete_missing_storage_schema");
  assert.equal(androidRuntimeSupportEvidence.observedRemote.policies.length, 4);
  assert.deepEqual(
    Object.fromEntries(androidRuntimeSupportEvidence.observedRemote.policies.map(
      ({ name, roles }) => [name, roles],
    )),
    {
      chat_attachments_storage_delete_own: ["authenticated"],
      chat_attachments_storage_insert: ["authenticated"],
      chat_attachments_storage_read: ["anon", "authenticated"],
      chat_attachments_storage_update_own: ["authenticated"],
    },
  );
  assert.equal(androidRuntimeSupportEvidence.observedRemote.policiesExact, true);
  assert.equal(androidRuntimeSupportEvidence.observedRemote.functionAndGrantExact, true);
  assert.equal(androidRuntimeSupportEvidence.observedRemote.allEffectsExact, true);
  assert.equal(androidRuntimeSupportEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(androidRuntimeSupportEvidence.guarantees.storageRowsRead, false);
  assert.equal(androidRuntimeSupportEvidence.guarantees.functionsExecuted, false);
  assert.equal(androidRuntimeSupportEvidence.guarantees.deployed, false);
});

test("attachment previews bind both functions, trigger and maintenance-only replay delta", () => {
  assert.equal(attachmentPreviewsEvidence.remoteMutation, false);
  assert.equal(attachmentPreviewsEvidence.sourceMigration.statementCount, 5);
  const sourcePath = resolve(root, attachmentPreviewsEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), attachmentPreviewsEvidence.sourceMigration.sha256);
  for (const statement of attachmentPreviewsEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  assert.equal(
    sha256(resolve(root, attachmentPreviewsEvidence.auditQuery.file)),
    attachmentPreviewsEvidence.auditQuery.sha256,
  );
  assert.equal(
    sha256(resolve(root, attachmentPreviewsEvidence.isolatedReplay.deltaAnalyzer.file)),
    attachmentPreviewsEvidence.isolatedReplay.deltaAnalyzer.sha256,
  );
  assert.equal(attachmentPreviewsEvidence.isolatedReplay.sourceOutcome, "data_change");
  assert.equal(attachmentPreviewsEvidence.isolatedReplay.sourceSchemaChanged, false);
  assert.equal(attachmentPreviewsEvidence.isolatedReplay.sourceDataChanged, true);
  assert.deepEqual(attachmentPreviewsEvidence.isolatedReplay.deltaAnalyzer.result, {
    changedRows: 1,
    selectedRows: 1,
    previewChangedRows: 0,
    updatedAtChangedRows: 1,
    otherColumnsChangedRows: 0,
    lastMessageAtChangedRows: 0,
  });
  assert.equal(attachmentPreviewsEvidence.observedRemote.functionsExact, true);
  assert.equal(attachmentPreviewsEvidence.observedRemote.triggerExact, true);
  assert.equal(attachmentPreviewsEvidence.observedRemote.allSelectedSummariesCurrent, true);
  assert.equal(attachmentPreviewsEvidence.observedRemote.allEffectsExact, true);
  assert.equal(attachmentPreviewsEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(attachmentPreviewsEvidence.guarantees.functionsExecuted, false);
  assert.equal(attachmentPreviewsEvidence.guarantees.triggersFired, false);
  assert.equal(attachmentPreviewsEvidence.guarantees.deployed, false);
});

test("Contact Discovery binds catalogue, backfill postcondition and maintenance", () => {
  assert.equal(contactDiscoveryEvidence.remoteMutation, false);
  assert.equal(contactDiscoveryEvidence.sourceMigration.statementCount, 11);
  const sourcePath = resolve(root, contactDiscoveryEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), contactDiscoveryEvidence.sourceMigration.sha256);
  for (const statement of contactDiscoveryEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  assert.equal(
    sha256(resolve(root, contactDiscoveryEvidence.auditQuery.file)),
    contactDiscoveryEvidence.auditQuery.sha256,
  );
  assert.equal(contactDiscoveryEvidence.isolatedReplay.sourceOutcome, "schema_change");
  assert.equal(contactDiscoveryEvidence.isolatedReplay.sourceSchemaChanged, true);
  assert.equal(contactDiscoveryEvidence.isolatedReplay.sourceDataChanged, false);
  assert.deepEqual(
    Object.fromEntries(contactDiscoveryEvidence.observedRemote.functions.map(
      ({ name, normalizedDefinitionMd5 }) => [name, normalizedDefinitionMd5],
    )),
    contactDiscoveryEvidence.isolatedReplay.canonicalization.functionDefinitionMd5,
  );
  assert.deepEqual(contactDiscoveryEvidence.observedRemote.digests, {
    columnsMd5: "ed209d4e1c4b439aaf08203b1d332c59",
    relationMd5: "02c2cd528e87b2b7f65b10e14c17f0f8",
    triggersMd5: "dd31603ebdbf8ce8f341f3c7ea4c1859",
    functionsMd5: "8c5fc97b33d6c4aec9a27d08d94388c0",
    constraintsMd5: "87a1856a7afd90584c4dbc70938a80fe",
  });
  assert.equal(contactDiscoveryEvidence.observedRemote.columns.length, 2);
  assert.equal(contactDiscoveryEvidence.observedRemote.constraints.length, 3);
  assert.equal(contactDiscoveryEvidence.observedRemote.functions.length, 3);
  assert.equal(contactDiscoveryEvidence.observedRemote.triggers.length, 1);
  assert.ok(Object.values(contactDiscoveryEvidence.observedRemote.relation.anonPrivileges).every((value) => !value));
  assert.ok(Object.values(contactDiscoveryEvidence.observedRemote.relation.authenticatedPrivileges).every((value) => !value));
  assert.equal(contactDiscoveryEvidence.observedRemote.allExpectedKeysPresent, true);
  assert.equal(contactDiscoveryEvidence.observedRemote.plannerStatisticsObserved, true);
  assert.equal(contactDiscoveryEvidence.observedRemote.allEffectsExact, true);
  assert.equal(contactDiscoveryEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(contactDiscoveryEvidence.guarantees.businessValuesEmitted, false);
  assert.equal(contactDiscoveryEvidence.guarantees.pureNormalizerEvaluated, true);
  assert.equal(contactDiscoveryEvidence.guarantees.rpcsExecuted, false);
  assert.equal(contactDiscoveryEvidence.guarantees.triggersFired, false);
  assert.equal(contactDiscoveryEvidence.guarantees.providerInvoked, false);
  assert.equal(contactDiscoveryEvidence.guarantees.deployed, false);
});

test("single-active push-token rule is exactly superseded by multidevice semantics", () => {
  assert.equal(pushTokenSingleActiveEvidence.remoteMutation, false);
  assert.equal(pushTokenSingleActiveEvidence.sourceMigration.statementCount, 4);
  const sourcePath = resolve(root, pushTokenSingleActiveEvidence.sourceMigration.file);
  assert.equal(sha256(sourcePath), pushTokenSingleActiveEvidence.sourceMigration.sha256);
  for (const statement of pushTokenSingleActiveEvidence.sourceMigration.statements) {
    assert.equal(statementSha256(sourcePath, statement.startByte, statement.endByte), statement.sha256);
  }
  assert.equal(pushTokenSingleActiveEvidence.supersedingMigration.statementCount, 18);
  const successorPath = resolve(root, pushTokenSingleActiveEvidence.supersedingMigration.file);
  assert.equal(sha256(successorPath), pushTokenSingleActiveEvidence.supersedingMigration.sha256);
  for (const statement of pushTokenSingleActiveEvidence.supersedingMigration.statements) {
    assert.equal(statementSha256(successorPath, statement.startByte, statement.endByte), statement.sha256);
  }
  assert.equal(
    sha256(resolve(root, pushTokenSingleActiveEvidence.auditQuery.file)),
    pushTokenSingleActiveEvidence.auditQuery.sha256,
  );
  assert.equal(pushTokenSingleActiveEvidence.isolatedReplay.sourceOutcome, "schema_and_data_change");
  assert.equal(pushTokenSingleActiveEvidence.isolatedReplay.sourceSchemaChanged, true);
  assert.equal(pushTokenSingleActiveEvidence.isolatedReplay.sourceDataChanged, true);
  assert.equal(
    pushTokenSingleActiveEvidence.observedRemote.function.definitionMd5,
    pushTokenSingleActiveEvidence.isolatedReplay.successorCanonicalization.definitionMd5,
  );
  assert.equal(pushTokenSingleActiveEvidence.observedRemote.function.publicExecute, false);
  assert.equal(pushTokenSingleActiveEvidence.observedRemote.function.authenticatedExecute, true);
  assert.equal(pushTokenSingleActiveEvidence.observedRemote.legacySingleActiveMarkerAbsent, true);
  assert.equal(pushTokenSingleActiveEvidence.observedRemote.allEffectsSuperseded, true);
  assert.equal(pushTokenSingleActiveEvidence.allSourceEffectsAccountedFor, true);
  assert.equal(pushTokenSingleActiveEvidence.guarantees.functionsExecuted, false);
  assert.equal(pushTokenSingleActiveEvidence.guarantees.providerInvoked, false);
  assert.equal(pushTokenSingleActiveEvidence.guarantees.deployed, false);
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
