import { readFile } from "node:fs/promises";
import test from "node:test";
import assert from "node:assert/strict";

const source = (path) => readFile(path, "utf8");

test("chat actions/notifications web evidence keeps credentials private and reversible", async () => {
  const runner = await source("scripts/chat-actions-notifications-web-evidence.mjs");
  assert.match(runner, /QUATA_CHAT_ACTIONS_NOTIFICATIONS_CREDENTIALS_FILE/);
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
  assert.match(runner, /getByText\(\/Silenciar conversaci\[oó\]n\|Mute conversation\/i\)/);
  for (const label of ["Copiar mensaje", "Responder", "Reenviar", "Editar", "Favorito", "Eliminar", "Report"]) {
    assert.match(runner, new RegExp(label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
  for (const screenshot of [
    "web-chat-actions-thread-initial",
    "web-chat-actions-muted",
    "web-chat-actions-own-selected",
    "web-chat-actions-peer-selected",
  ]) {
    assert.match(runner, new RegExp(screenshot));
  }
});
