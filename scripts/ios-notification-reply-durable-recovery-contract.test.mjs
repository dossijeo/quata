import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

const runtime = read("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosNotificationReplyRuntime.kt");
const recoveryTest = read("feature/chat/src/iosTest/kotlin/com/quata/feature/chat/data/IosNotificationReplyDurableRecoveryTest.kt");
const delegate = read("iosApp/iosApp/IosNotificationTapDelegate.swift");
const app = read("iosApp/iosApp/QuataIosApp.swift");
const action = read("iosApp/iosApp/IosNotificationReplyAction.swift");
const inventory = read("docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const board = read("docs/MULTIPLATFORM_MIGRATION_BOARD.md");

test("iOS notification replies enter the actor-bound durable outbox before transport", () => {
  assert.match(runtime, /outgoingStore\.insert\(pending\)/);
  assert.match(runtime, /actorProvider\(\) != recipientProfileId/);
  assert.match(runtime, /clientMessageId = clientMessageId/);
  assert.match(runtime, /directReply\?\.invoke\(conversationId, recipientProfileId, text, clientMessageId\)/);
  assert.match(runtime, /NotificationReplyOutcome\.Queued/);
});

test("transport failure and cancellation preserve a replayable claim for the same actor", () => {
  assert.match(runtime, /releaseClaim\(claimed, leaseToken, "notification_reply_transport_cancelled"\)/);
  assert.match(runtime, /notification_reply_actor_or_session_changed/);
  assert.match(runtime, /notification_reply_transport_failed/);
  assert.match(runtime, /chatRepository\.flushPendingMessages\(\)/);
  assert.match(runtime, /currentActor != actorId/);
  assert.match(runtime, /outgoingStore\.load\(actorId\)\.any/);
  assert.doesNotMatch(runtime, /chatRepository\.isMessagePending\(clientMessageId\)/);
  assert.match(runtime, /schedulePostLeaseRecoveryFlush\(actorId\)/);
  assert.match(runtime, /delay\(ReplyLeaseMillis \+ PendingObservationMillis\)/);
  assert.match(recoveryTest, /failedDirectSendRemainsActorBoundAndUsesTheSameIdempotencyKey/);
  assert.match(recoveryTest, /restoredDifferentActorCannotReplayOrDismissThePreviousActorsReply/);
  assert.match(recoveryTest, /successfulDirectSendLeavesNoReplayableOutboxEntry/);
  assert.match(recoveryTest, /actorReplacementDuringDirectSendKeepsThePreviousActorsReplyRecoverable/);
  assert.match(recoveryTest, /watcherUsesThePersistentStoreWhenRepositoryCacheReportsMissing/);
  assert.match(recoveryTest, /restoredReplyRetriesAfterAnInFlightCrashLeaseExpires/);
});

test("restored session recovery removes only the routing notification after delivery", () => {
  assert.match(runtime, /resumeAfterSessionValidation\(\)/);
  assert.match(runtime, /recoverPendingReplies\(\)/);
  assert.match(runtime, /pendingReplyDelivered\?\.invoke\(clientMessageId\)/);
  assert.match(app, /setPendingReplyDeliveredHandler/);
  assert.match(app, /removeDeliveredNotifications\(withIdentifiers: \[clientID\]\)/);
  assert.match(recoveryTest, /restoredSameActorFlushesOnceAndRemovesOnlyItsQueuedNotification/);
});

test("queued notification exposes exact-chat routing without retaining typed text", () => {
  assert.match(delegate, /"conversation_id": target\.conversationId/);
  assert.match(delegate, /"recipient_profile_id": recipient/);
  assert.match(delegate, /queuedContent\(userInfo: routing\)/);
  assert.doesNotMatch(delegate, /queuedContent\([^)]*text:/);
  assert.match(action, /notification_reply_queued_title/);
  assert.match(action, /notification_reply_queued_body/);
});

test("documentation closes the durable lifecycle only and preserves external Apple limits", () => {
  const inventoryRow = inventory.split("\n").find((line) => line.startsWith("| `FLOW-NOTIFICATION-REPLY`"));
  const boardRow = board.split("\n").find((line) => line.startsWith("| Notification Reply iOS Simulator"));
  assert.ok(inventoryRow);
  assert.ok(boardRow);
  for (const closed of ["outbox", "reinicio", "Chat exacto"]) {
    assert.match(inventoryRow, new RegExp(closed));
    assert.match(boardRow, new RegExp(closed));
  }
  for (const preserved of ["APNs", "dispositivo físico", "distribución"]) {
    assert.match(inventoryRow, new RegExp(preserved));
    assert.match(boardRow, new RegExp(preserved));
  }
});
