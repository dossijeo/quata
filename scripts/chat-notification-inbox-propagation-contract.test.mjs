import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

const [
  repositoryTest,
  webRunner,
  androidRunner,
  androidUi,
  iosRunner,
  iosUi,
  iosShell,
  inventory,
  packageJson,
] = await Promise.all([
  source("feature/notifications/src/commonTest/kotlin/com/quata/feature/notifications/data/ConversationNotificationsRepositoryTest.kt"),
  source("scripts/chat-actions-notifications-web-evidence.mjs"),
  source("scripts/chat-actions-notifications-android-evidence.mjs"),
  source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  source("scripts/chat-actions-notifications-ios-evidence.mjs"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  source("package.json"),
]);

test("the shared inbox remaps mute changes on its bounded product poll", () => {
  assert.match(repositoryTest, /muteChangesPropagateToTheSharedInboxOnTheNextBoundedPoll/);
  assert.match(repositoryTest, /muted = true[\s\S]*advanceTimeBy\(15_000\)[\s\S]*assertEquals\(emptyList\(\), emissions\.last\(\)\)/);
  assert.match(repositoryTest, /muted = false[\s\S]*advanceTimeBy\(15_000\)[\s\S]*sb:owned-thread/);
});

test("Web proves mute hide and unmute reveal with new peer messages", () => {
  assert.match(webRunner, /--notification-inbox-propagation-only/);
  assert.match(webRunner, /verifyChatNotificationInboxPropagation/);
  assert.match(webRunner, /quata_chat_send_message[\s\S]*chat-notification-inbox-muted-/);
  assert.match(webRunner, /notification_inbox_muted_conversation_visible/);
  assert.match(webRunner, /notification_inbox_product_load_failed/);
  assert.match(webRunner, /unreadCount\(mutedInboxThread\) < 1/);
  assert.match(webRunner, /await delay\(18_000\)/);
  assert.match(webRunner, /chat-notification-inbox-unmuted-[\s\S]*waitFor\(\{ state: "visible", timeout: 30_000 \}\)/);
  assert.match(webRunner, /peerEvidenceMessages[\s\S]*state\.b/);
});

test("Android drives the real mute action and shared Notifications repository", () => {
  assert.match(androidRunner, /--notification-inbox-propagation-only/);
  for (const stage of ["notification-inbox-mute", "notification-inbox-hidden", "notification-inbox-unmute", "notification-inbox-visible"]) {
    assert.match(androidRunner, new RegExp(stage));
  }
  assert.match(androidRunner, /muted_conversation_with_new_peer_message_absent_from_shared_inbox/);
  assert.match(androidRunner, /unmuted_conversation_with_new_peer_message_visible_in_shared_inbox/);
  assert.match(androidRunner, /am", "force-stop", "com\.quata/);
  assert.match(androidRunner, /unreadCount\(mutedInboxThread\) < 1/);
  assert.match(androidUi, /runMenuMutePropagationStage/);
  assert.match(androidUi, /hasContentDescription\(label, substring = true\)/);
  assert.match(androidUi, /notificationsRepository\.getNotifications\(\)\.getOrThrow\(\)/);
  assert.match(androidUi, /NotificationItemTestTagPrefix/);
  assert.match(androidUi, /notification_inbox_repository_visibility_mismatch/);
});

test("iOS runs four bounded native UI stages around host-side peer messages", () => {
  assert.match(iosRunner, /--notification-inbox-propagation-only/);
  assert.match(iosRunner, /runIosNotificationInboxStage\("hidden"\)/);
  assert.match(iosRunner, /runIosNotificationInboxStage\("unmute"\)/);
  assert.match(iosRunner, /runIosNotificationInboxStage\("visible"\)/);
  assert.match(iosRunner, /pollConversationMuted\(true\)/);
  assert.match(iosRunner, /pollConversationMuted\(false\)/);
  assert.match(iosRunner, /terminateIosApp\(\)/);
  assert.match(iosRunner, /unreadCount\(mutedInboxThread\) < 1/);
  for (const method of [
    "testNotificationInboxPropagationMutesConversation",
    "testNotificationInboxPropagationHidesMutedConversation",
    "testNotificationInboxPropagationUnmutesConversation",
    "testNotificationInboxPropagationShowsUnmutedConversation",
  ]) {
    assert.match(iosUi, new RegExp(method));
    assert.match(iosShell, new RegExp(method));
  }
  assert.match(iosUi, /QUATA_IOS_CONVERSATIONS_SUBJECT/);
  assert.match(iosShell, /QUATA_IOS_CHAT_NOTIFICATION_INBOX_STAGE/);
});

test("the focal lane remains mandatory while the inventory stays honest until runtime evidence passes", () => {
  assert.match(packageJson, /chat-notification-inbox-propagation-contract\.test\.mjs/);
  const row = inventory.split(/\r?\n/).find((line) => line.startsWith("| `CHAT-NOTIFICATIONS` |"));
  assert.ok(row);
  assert.match(row, /Propagación inbox sigue pendiente/);
  assert.doesNotMatch(row, /GO focal.*propagación/i);
});
