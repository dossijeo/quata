import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root = new URL("../", import.meta.url);
const read = (path) => readFile(new URL(path, root), "utf8");

test("inbox pagination is additive, actor-bound, keyset ordered, and least-privilege", async () => {
  const [sql, selectiveRelease] = await Promise.all([
    read("supabase/migrations/20260925113000_chat_inbox_cursor_pagination.sql"),
    read("scripts/selective-db-release-executor.mjs"),
  ]);

  assert.match(sql, /create or replace function public\.quata_chat_get_inbox_page\s*\(/);
  assert.doesNotMatch(sql, /create or replace function public\.quata_chat_get_inbox\s*\(/);
  assert.match(sql, /quata_chat_actor_profile_id\(p_actor_profile_id\)/);
  assert.match(sql, /order by t\.last_message_at desc nulls last, t\.updated_at desc, t\.id desc/);
  assert.match(sql, /limit v_limit \+ 1/);
  assert.match(sql, /t\.last_message_at < p_before_last_message_at/);
  assert.match(sql, /t\.updated_at < p_before_updated_at/);
  assert.match(sql, /t\.id < p_before_thread_id/);
  assert.match(sql, /'has_more', v_has_more/);
  assert.match(sql, /'next_cursor'/);
  assert.match(sql, /revoke all on function public\.quata_chat_get_inbox_page[\s\S]*from public/);
  assert.match(sql, /from public, anon/);
  assert.match(sql, /grant execute on function public\.quata_chat_get_inbox_page[\s\S]*to authenticated/);
  assert.doesNotMatch(sql, /to anon/);
  assert.match(selectiveRelease, /20260925113000/);
  assert.match(selectiveRelease, /9b1a2e6b668ec6f4cd99a07a5d155d619fdb5ee16a097da4490871f1b0136f28/);
  assert.match(selectiveRelease, /selective_release_inbox_pagination_function_missing/);
  assert.match(selectiveRelease, /selective_release_inbox_pagination_function_security_failed/);
  assert.match(selectiveRelease, /selective_release_inbox_pagination_authenticated_execute_missing/);
  assert.match(selectiveRelease, /selective_release_inbox_pagination_anon_execute_present/);
  assert.match(selectiveRelease, /selective_release_inbox_pagination_function_definition_failed/);
  assert.match(selectiveRelease, /selective_release_inbox_pagination_first_page_postcondition_failed/);
  assert.match(selectiveRelease, /selective_release_inbox_pagination_second_page_postcondition_failed/);
});

test("all runtime repositories use the additive page RPC and keep the legacy RPC contract available", async () => {
  const [common, androidRepository, androidApi, legacyMigration, releaseSafety] = await Promise.all([
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/PostgrestChatRepository.kt"),
    read("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt"),
    read("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt"),
    read("supabase/migrations/20260714_0001_chat_conversation_user_state.sql"),
    read("scripts/db-release-safety.mjs"),
  ]);

  assert.match(common, /quata_chat_get_inbox_page/);
  assert.match(common, /p_before_last_message_at/);
  assert.match(common, /loadedInboxPageCount > 1/);
  assert.match(androidRepository, /getChatInboxPage/);
  assert.match(androidRepository, /_conversations\.value\.size > INBOX_PAGE_SIZE/);
  assert.match(androidApi, /"quata_chat_get_inbox_page"/);
  assert.match(legacyMigration, /create or replace function public\.quata_chat_get_inbox\s*\(/);
  assert.match(releaseSafety, /pendingLocalRpcs/);
  assert.match(releaseSafety, /args\.phase === "snapshot"/);
  assert.match(releaseSafety, /compatibilityRpcMissing/);
});

test("shared conversation UI exposes retryable deep-page state", async () => {
  const [state, host, viewModel] = await Promise.all([
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsUiState.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsScreenHost.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsViewModel.kt"),
  ]);

  assert.match(state, /conversationHasMore/);
  assert.match(state, /conversationNextCursor/);
  assert.match(state, /conversationPageError/);
  assert.match(host, /conversation\.loadMore/);
  assert.match(host, /conversation\.pageError/);
  assert.match(viewModel, /loadMoreConversations/);
  assert.match(viewModel, /distinctBy\(Conversation::id\)/);
});

test("each platform focal custodian proves two real backend cursor pages", async () => {
  const [fixture, web, android, androidUi, ios, iosUi, repositoryTest] = await Promise.all([
    read("scripts/e2e-fixtures/chat-inbox-pagination.mjs"),
    read("scripts/chat-actions-notifications-web-evidence.mjs"),
    read("scripts/chat-actions-notifications-android-evidence.mjs"),
    read("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
    read("scripts/chat-actions-notifications-ios-evidence.mjs"),
    read("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
    read("feature/chat/src/commonTest/kotlin/com/quata/feature/chat/data/PostgrestChatRepositoryTest.kt"),
  ]);

  assert.match(fixture, /quata_chat_get_inbox_page/);
  assert.match(fixture, /p_limit: 1/);
  assert.match(fixture, /new Set\(observed\)\.size !== 2/);
  for (const runner of [web, android, ios]) {
    assert.match(runner, /verifyChatInboxCursorPagination/);
    assert.match(runner, /real_backend_inbox_cursor_crossed_two_distinct_pages/);
  }
  assert.match(web, /__quataSetConversationVisibility\("hidden"\)/);
  assert.match(web, /__quataSetConversationVisibility\("visible"\)/);
  assert.match(web, /controlled_document_visibility_api_with_real_backend_refresh/);
  assert.match(web, /conversations_visibility_resume_inbox_rpc_missing/);
  assert.match(androidUi, /device\.pressHome\(\)/);
  assert.match(androidUi, /android-conversations-background-resumed/);
  assert.match(iosUi, /XCUIApplication\.State\.runningBackground/);
  assert.match(iosUi, /ios-conversations-background-resumed/);
  assert.match(ios, /--conversations-pagination-lifecycle-only/);
  assert.match(iosUi, /QUATA_IOS_CONVERSATIONS_LIFECYCLE_ONLY/);
  assert.match(repositoryTest, /inboxCursorAppendsDeepPageAndForegroundRefreshPreservesIt/);
  assert.match(repositoryTest, /repository\.setAppForeground\(false\)[\s\S]*repository\.setAppForeground\(true\)/);
});
