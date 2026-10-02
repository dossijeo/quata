import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("shared message mutations serialize duplicate intent and retain one exact retry", async () => {
  const [repository, viewModel, state, event, host, tests] = await Promise.all([
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/domain/ChatRepository.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatUiState.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatUiEvent.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatScreenHost.kt"),
    read("feature/chat/src/commonTest/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModelComposerActionsTest.kt"),
  ]);

  assert.match(repository, /editMessage\([\s\S]*clientMutationId: String,[\s\S]*expectedActorId: String/);
  assert.match(repository, /deleteMessage\([\s\S]*clientMutationId: String,[\s\S]*expectedActorId: String/);
  assert.match(viewModel, /if \(activeEditMutation != null \|\| activeSelectedMessageMutation != null\) return/);
  assert.match(viewModel, /pendingEditMutationRetry[\s\S]*clientMutationId = newMessageMutationId\(\)/);
  assert.match(viewModel, /pendingSelectedMessageMutationRetry = mutation/);
  assert.match(viewModel, /repository\.currentActorId\(\)[\s\S]*== mutation\.actorId/);
  assert.match(viewModel, /repository\.currentActorId\(\)[\s\S]*== editMutation\.actorId[\s\S]*optimisticEditedMessages = optimisticEditedMessages - editMutation\.messageId[\s\S]*publishMessages\(isLoading = false\)[\s\S]*return@launch/);
  assert.match(state, /messageMutationRetry: ChatMessageMutationRetry\?/);
  assert.match(event, /RetryMessageMutation/);
  assert.match(host, /ChatUiEvent\.RetryMessageMutation[\s\S]*ChatMutationRetryTestTag/);
  for (const name of [
    "deleteFailureExposesExactRetryAndReusesItsMutationReceipt",
    "duplicateDeleteConfirmationIsSerializedWhileTheFirstRequestIsInFlight",
    "reportRetryKeepsTheExactFailedTargetAndPreservesANewerSelection",
    "editDoubleSubmitIsSerializedAndItsRetryReusesTheMutationReceipt",
    "editCompletionFromAReplacedActorCannotChangeTheNewSessionComposer",
    "editDeleteAndReportShareOneMutationLock",
    "completionFromAReplacedActorCannotClearSelectionOrOfferRetry",
  ]) assert.match(tests, new RegExp(name));
});

test("current product clients use authenticated v2 RPCs while legacy signatures remain available", async () => {
  const [common, androidRepository, androidRemote, androidApi, androidModels, boundary] = await Promise.all([
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/PostgrestChatRepository.kt"),
    read("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt"),
    read("app/src/main/java/com/quata/feature/chat/data/ChatRemoteDataSource.kt"),
    read("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt"),
    read("app/src/main/java/com/quata/data/supabase/SupabaseModels.kt"),
    read("supabase/migrations/20260927094500_chat_actor_auth_boundary.sql"),
  ]);

  assert.match(common, /quata_chat_edit_message_v2/);
  assert.match(common, /quata_chat_delete_messages_v2/);
  assert.match(common, /currentUserId\(expectedActorId\)/);
  assert.match(common, /put\("p_reason", "other"\)/);
  assert.doesNotMatch(common, /user_report/);
  assert.match(androidRepository, /check\(session\.userId == expectedActorId\)/);
  assert.match(androidRemote, /editChatMessageV2/);
  assert.match(androidRemote, /deleteChatMessagesV2/);
  assert.match(androidApi, /quata_chat_edit_message_v2/);
  assert.match(androidApi, /quata_chat_delete_messages_v2/);
  assert.match(androidModels, /p_client_mutation_id: String/);
  assert.match(boundary, /\/rpc\/quata_chat_edit_message/);
  assert.match(boundary, /\/rpc\/quata_chat_delete_messages/);
  assert.doesNotMatch(boundary, /\/rpc\/quata_chat_(?:edit_message|delete_messages)_v2/);
});

test("database receipts commit atomically, reject key reuse and roll back without touching v1", async () => {
  const [migration, rollback, fixture, runner, releaseExecutor] = await Promise.all([
    read("supabase/migrations/20261002003000_chat_message_mutation_idempotency.sql"),
    read("supabase/rollbacks/20261002003000_chat_message_mutation_idempotency.rollback.sql"),
    read("scripts/sql/chat-message-mutation-idempotency.test.sql"),
    read("scripts/test-chat-message-mutation-idempotency.ps1"),
    read("scripts/selective-db-release-executor.mjs"),
  ]);

  assert.match(migration, /create table if not exists public\.chat_message_mutation_receipts/);
  assert.match(migration, /primary key \(actor_profile_id, client_mutation_id\)/);
  assert.match(migration, /on conflict \(actor_profile_id, client_mutation_id\) do nothing/);
  assert.match(migration, /client mutation id was reused for a different request/);
  assert.match(migration, /create or replace function public\.quata_chat_edit_message_v2/);
  assert.match(migration, /create or replace function public\.quata_chat_delete_messages_v2/);
  assert.match(migration, /revoke all on function public\.quata_chat_edit_message_v2[\s\S]*from public, anon, authenticated/);
  assert.match(migration, /revoke all on function public\.quata_chat_delete_messages_v2[\s\S]*from public, anon, authenticated/);
  assert.match(migration, /grant execute[\s\S]*to authenticated/);
  assert.doesNotMatch(migration, /grant execute[\s\S]*to anon/);
  assert.doesNotMatch(migration, /create or replace function public\.quata_chat_(?:edit_message|delete_messages)\(/);
  assert.match(rollback, /drop function if exists public\.quata_chat_delete_messages_v2/);
  assert.match(rollback, /drop table if exists public\.chat_message_mutation_receipts/);
  assert.match(fixture, /edit replay repeated its event/);
  assert.match(fixture, /delete replay repeated its event/);
  assert.match(fixture, /legacy edit function changed/);
  assert.match(fixture, /actor mismatch was accepted/);
  assert.match(runner, /CHAT_MESSAGE_MUTATION_IDEMPOTENCY_POSTGRES_PASS/);
  assert.match(runner, /mutation_rollback_postcondition_failed/);
  assert.match(releaseExecutor, /20261002003000[\s\S]*89149300661e48f8a9ed210eff74d399f8949d34065bb7cecda98a094d59bf74/);
  assert.match(releaseExecutor, /selectedVersions\.includes\("20261002003000"\)/);
  assert.match(releaseExecutor, /p_message_id bigint, p_message text, p_client_mutation_id text/);
  assert.ok(releaseExecutor.includes("on conflict \\(actor_profile_id, client_mutation_id\\) do nothing"));
  assert.match(releaseExecutor, /selective_release_chat_mutation_boundary_missing/);
  assert.match(releaseExecutor, /selective_release_chat_mutation_security_failed/);
  assert.match(releaseExecutor, /selective_release_chat_mutation_definition_failed/);
});

test("message mutation idempotency contract is mandatory in both fast suites", async () => {
  const pkg = JSON.parse(await read("package.json"));
  for (const suite of ["test:ci-fast-contracts", "test:web-wave2-contracts"]) {
    assert.match(pkg.scripts[suite], /scripts\/chat-message-mutation-idempotency-contract\.test\.mjs/);
  }
});
