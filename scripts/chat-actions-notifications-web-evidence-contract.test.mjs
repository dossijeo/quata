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

test("chat actions/notifications Android evidence keeps backend fixture reversible", async () => {
  const runner = await source("scripts/chat-actions-notifications-android-evidence.mjs");
  const testSource = await source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");
  assert.match(runner, /QUATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP_AUTHORIZATION/);
  assert.match(runner, /MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP/);
  assert.match(runner, /QUATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH_AUTHORIZATION/);
  assert.match(runner, /MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH/);
  assert.match(runner, /action: "login"/);
  assert.doesNotMatch(runner, /action: "web_login"/);
  assert.doesNotMatch(runner, /680242607|680242608|21085800/);
  assert.match(runner, /qadata-chat-actions-notifications-android-/);
  assert.match(runner, /select id, pass_hash, pass_plain[\s\S]*for update/);
  assert.match(runner, /update public\.community_profiles set pass_hash = \$1, pass_plain = null where id = \$2/);
  assert.match(runner, /update public\.community_profiles set pass_hash = \$1, pass_plain = \$2 where id = \$3/);
  assert.match(runner, /delete from public\.chat_threads where id = \$1 and unique_key = \$2 returning id/);
  assert.match(runner, /cleanup_verified_physical_residue_absent/);
  assert.match(runner, /ChatActionsNotificationsInstrumentedTest/);
  assert.match(runner, /composer_text_sent_by_shared_ui_and_verified_by_rpc/);
  assert.match(runner, /composer_reply_sent_by_shared_ui_and_verified_by_rpc/);
  assert.match(runner, /composer_edit_sent_by_shared_ui_and_verified_by_rpc/);
  assert.match(runner, /favorite_toggled_and_verified_by_rpc/);
  assert.match(testSource, /ChatComposerInputTestTag/);
  assert.match(testSource, /ChatComposerSendTestTag/);
  assert.match(testSource, /chat\.action\.reply/);
  assert.match(testSource, /chat\.action\.edit/);
  assert.match(testSource, /chat\.action\.favorite/);
  assert.match(testSource, /android-chat-actions-own-selected/);
});

test("chat actions/notifications web evidence exercises real shared chat controls", async () => {
  const runner = await source("scripts/chat-actions-notifications-web-evidence.mjs");
  assert.match(runner, /quata_chat_start_thread/);
  assert.match(runner, /quata_chat_send_message/);
  assert.match(runner, /quata_chat_set_muted/);
  assert.match(runner, /quata_chat_get_inbox/);
  assert.match(runner, /quata_chat_get_favorites/);
  assert.match(runner, /async function clickMessage\(page, marker, error\)/);
  assert.match(runner, /async function openMessageActions\(page, marker, expectedPatterns, targetError, actionError\)/);
  assert.match(runner, /if \(marker\.startsWith\("chat-edit-ui-"\)\) \{/);
  assert.match(runner, /async function longPressMessage\(page, marker\)/);
  assert.match(runner, /async function waitMessageVisible\(page, marker, error, timeout = 45_000\)/);
  assert.match(runner, /async function clickMessageProbe\(page, probe\)/);
  assert.match(runner, /marker\.slice\(0, 28\), marker\.slice\(0, 20\), marker\.slice\(0, 16\)/);
  assert.match(runner, /marker\.startsWith\("chat-edit-ui-"\)/);
  assert.match(runner, /page\.mouse\.click\(Math\.round\(viewport\.width \* 0\.62\), 214\)/);
  assert.match(runner, /async function visibleTextBox\(page, probe\)/);
  assert.match(runner, /async function clickNativeButtonByLabel\(page, patterns\)/);
  assert.match(runner, /async function visibleAriaLocator\(page, patterns, timeout\)/);
  assert.match(runner, /async function clickOptionsMenu\(page\)/);
  assert.match(runner, /async function clickFavoriteAction\(page\)/);
  assert.match(runner, /page\.mouse\.click\(Math\.max\(1, viewport\.width - 26\), 104\)/);
  assert.match(runner, /page\.locator\("\[aria-label\]"\)/);
  assert.match(runner, /getByRole\("button", \{ name: pattern \}/);
  assert.match(runner, /await text\.scrollIntoViewIfNeeded\(\{ timeout: 5_000 \}\)/);
  assert.match(runner, /const box = await text\.boundingBox\(\)/);
  assert.match(runner, /page\.mouse\.click\(Math\.max\(1, box\.x - 12\), box\.y \+ \(box\.height \/ 2\)\)/);
  assert.match(runner, /const textBox = await visibleTextBox\(page, probe\)/);
  assert.match(runner, /sort\(\(left, right\) => left\.area - right\.area \|\| left\.textLength - right\.textLength \|\| left\.y - right\.y\)/);
  assert.match(runner, /fillComposerAndSend\(page, composerMarker\)/);
  assert.match(runner, /await waitMessageVisible\(page, composerMarker, "composer_message_not_visible"\)/);
  assert.match(runner, /const input = await visibleAriaLocator\(page, \[\/Mensaje\|Message\|Composer\/i\], 10_000\)/);
  assert.match(runner, /const deadline = Date\.now\(\) \+ 10_000/);
  assert.match(runner, /let sawSend = false/);
  assert.match(runner, /const send = await visibleAriaLocator\(page, \[\/Enviar\|Send\/i\], 1_000\)/);
  assert.match(runner, /if \(!sawSend\) throw new Error\("composer_send_not_visible"\)/);
  assert.match(runner, /input\.inputValue\(\)\.then\(\(current\) => current === value\)/);
  assert.match(runner, /page\.mouse\.click\(box\.x \+ \(box\.width \/ 2\), box\.y \+ \(box\.height \/ 2\)\)/);
  assert.match(runner, /await clickNativeButtonByLabel\(page, \[\/Enviar\|Send\/i\]\)/);
  assert.match(runner, /throw new Error\("composer_send_not_dispatched"\)/);
  assert.match(runner, /await fillComposerAndSend\(page, editMarker\)/);
  assert.match(runner, /composer_text_sent_by_shared_ui_and_verified_by_rpc/);
  assert.match(runner, /report\.evidence\.replySent = await attachScreenshot\(page, options\.evidenceDir, "web-chat-composer-reply-sent"\)/);
  assert.match(runner, /const replyTargetMessageId = state\.peerMessage \?\? state\.ownMessage/);
  assert.match(runner, /messageReplyToId\(message\) === Number\(replyTargetMessageId\)/);
  assert.match(runner, /composer_reply_sent_by_shared_ui_and_verified_by_rpc/);
  assert.match(runner, /safeErrorMessage: error\.message/);
  assert.match(runner, /state\.editableUiMessage = composerMessageId/);
  assert.match(runner, /await openMessageActions\(page, ownMarker, \[\/Editar\|Edit\/i\], "message_action_target_not_clickable:edit", "action_bar_not_visible:edit"\)/);
  assert.match(runner, /Number\(state\.ownMessage\) && messageText\(message\) === editMarker/);
  assert.match(runner, /report\.evidence\.editSent = await attachScreenshot\(page, options\.evidenceDir, "web-chat-composer-edit-sent"\)/);
  assert.match(runner, /await openMessageActions\(page, editMarker, \[\/Copiar mensaje\|Copiar texto\|Copy message\|Copy text\/i\], "message_action_target_not_clickable:own_actions", "action_bar_not_visible:copy"\)/);
  assert.match(runner, /report\.evidence\.ownActions = await attachScreenshot\(page, options\.evidenceDir, "web-chat-actions-own-selected"\)/);
  assert.match(runner, /await clickFavoriteAction\(page\)/);
  assert.match(runner, /page\.mouse\.click\(Math\.max\(1, viewport\.width - 66\), 98\)/);
  assert.match(runner, /Number\(message\?\.id\) === Number\(state\.ownMessage\)/);
  assert.match(runner, /composer_edit_sent_by_shared_ui_and_verified_by_rpc/);
  assert.match(runner, /clickOptionsMenu\(page\)/);
  assert.match(runner, /getByText\(\/Silenciar conversaci\[oó\]n\|Mute conversation\/i\)/);
  assert.match(runner, /favorite_toggled_and_verified_by_rpc/);
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
