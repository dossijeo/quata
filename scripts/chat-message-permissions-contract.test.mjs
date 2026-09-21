import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("CHAT-MESSAGE-ACTIONS ownership guards stay aligned from common UI to real backend evidence", async () => {
  const [actions, viewModel, tests, runner] = await Promise.all([
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerAndActionsContent.kt"),
    source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
    source("feature/chat/src/commonTest/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModelComposerActionsTest.kt"),
    source("scripts/chat-actions-notifications-web-evidence.mjs"),
  ]);

  assert.match(actions, /message\.isMine && !message\.isDeleted[\s\S]*chat\.action\.edit/);
  assert.match(actions, /!message\.isMine && !message\.isDeleted[\s\S]*chat\.action\.report/);
  assert.match(actions, /message\.isMine && !message\.isDeleted[\s\S]*chat\.action\.delete/);
  assert.match(viewModel, /selectedMessage\(\)\?\.takeIf \{ it\.isMine && !it\.isDeleted && !it\.isLocalEcho \}[\s\S]*repository\.deleteMessage/);
  assert.match(viewModel, /selectedMessage\(\)\?\.takeIf \{ !it\.isMine && !it\.isDeleted && !it\.isLocalEcho \}[\s\S]*repository\.reportMessage/);
  assert.match(tests, /messageActionGuardsRejectLocalEchoDeletedAndWrongOwnerTargets/);
  assert.match(tests, /assertTrue\(repository\.deleteMessageCalls\.isEmpty\(\)\)/);

  assert.match(runner, /--message-permissions-only/);
  assert.match(runner, /message_permissions_peer_edit_visible/);
  assert.match(runner, /message_permissions_peer_delete_visible/);
  assert.match(runner, /message_permissions_own_report_visible/);
  assert.match(runner, /visibleNativeControl\(page, \[new RegExp\(escapeRegExp\(probe\)\)\], 500\)/);
  assert.match(runner, /if \(await visibleAriaLocator\(page, expectedPatterns, 5_000\)\) return;[\s\S]*throw new Error\(actionError\)/);
  assert.match(runner, /quata_chat_edit_message/);
  assert.match(runner, /quata_chat_delete_messages/);
  assert.match(runner, /peer_edit_and_delete_rejected_by_authenticated_backend/);
});
