import { readFile } from "node:fs/promises";
import test from "node:test";
import assert from "node:assert/strict";

const source = (path) => readFile(path, "utf8");

test("chat actions/notifications web evidence keeps credentials private and reversible", async () => {
  const runner = await source("scripts/chat-actions-notifications-web-evidence.mjs");
  assert.match(runner, /QUATA_CHAT_ACTIONS_NOTIFICATIONS_CREDENTIALS_FILE/);
  assert.match(runner, /QUATA_CHAT_ACTIONS_NOTIFICATIONS_USE_ADJACENT_AUTHORIZED_PROFILE/);
  assert.match(runner, /QUATA_CHAT_ACTIONS_NOTIFICATIONS_SSH_CREDENTIALS_FILE/);
  assert.doesNotMatch(runner, /680242607|680242608|21085800/);
  assert.match(runner, /qadata-chat-actions-notifications-/);
  assert.match(runner, /QUATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP_AUTHORIZATION/);
  assert.match(runner, /MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP/);
  assert.match(runner, /begin/);
  assert.match(runner, /for update/);
  assert.match(runner, /delete from public\.chat_threads where id = \$1 and unique_key = \$2 returning id/);
  assert.match(runner, /cleanup_verified_physical_residue_absent/);
});

test("chat actions/notifications web evidence exercises real shared chat controls", async () => {
  const runner = await source("scripts/chat-actions-notifications-web-evidence.mjs");
  assert.match(runner, /quata_chat_start_thread/);
  assert.match(runner, /quata_chat_send_message/);
  assert.match(runner, /quata_chat_set_muted/);
  assert.match(runner, /quata_chat_get_inbox/);
  assert.match(runner, /quata_chat_get_favorites/);
  assert.match(runner, /async function clickMessage\(page, marker, error\)/);
  assert.match(runner, /async function visibleAriaLocator\(page, patterns, timeout\)/);
  assert.match(runner, /page\.locator\("\[aria-label\]"\)/);
  assert.match(runner, /getByRole\("button", \{ name: pattern \}/);
  assert.match(runner, /const box = await text\.boundingBox\(\)/);
  assert.match(runner, /page\.mouse\.click\(Math\.max\(1, box\.x - 12\), box\.y \+ \(box\.height \/ 2\)\)/);
  assert.match(runner, /fillComposerAndSend\(page, composerMarker\)/);
  assert.match(runner, /composer_text_sent_by_shared_ui_and_verified_by_rpc/);
  assert.match(runner, /const replyTargetMessageId = state\.peerMessage \?\? state\.ownMessage/);
  assert.match(runner, /messageReplyToId\(message\) === Number\(replyTargetMessageId\)/);
  assert.match(runner, /composer_reply_sent_by_shared_ui_and_verified_by_rpc/);
  assert.match(runner, /await clickMessage\(page, ownMarker, "message_action_target_not_clickable:edit"\)[\s\S]*?await clickLabel\(page, \[\/Editar\|Edit\/i\]/);
  assert.match(runner, /await clickMessage\(page, editMarker, "message_action_target_not_clickable:own_actions"\)[\s\S]*?await waitLabel\(page, \[\/Copiar mensaje\|Copiar texto\|Copy message\|Copy text\/i\]/);
  assert.match(runner, /composer_edit_sent_by_shared_ui_and_verified_by_rpc/);
  assert.match(runner, /getByText\(\/Silenciar conversaci\[oó\]n\|Mute conversation\/i\)/);
  for (const label of ["Copiar texto", "Responder", "Reenviar", "Editar", "Favorito", "Eliminar", "Report"]) {
    assert.match(runner, new RegExp(label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
  for (const screenshot of [
    "web-chat-actions-thread-initial",
    "web-chat-composer-sent",
    "web-chat-composer-reply-sent",
    "web-chat-composer-edit-sent",
    "web-chat-actions-muted",
    "web-chat-actions-own-selected",
    "web-chat-actions-peer-selected",
  ]) {
    assert.match(runner, new RegExp(screenshot));
  }
});
