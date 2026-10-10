import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

const transport = read("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosPostgrestChatTransport.kt");
const runtime = read("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosNotificationReplyRuntime.kt");
const boundary = read("feature/chat/src/iosTest/kotlin/com/quata/feature/chat/data/IosNotificationReplyHttpBoundaryTest.kt");
const inventory = read("docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const board = read("docs/MULTIPLATFORM_MIGRATION_BOARD.md");

test("notification Reply keeps the native delegate connected to the authenticated chat transport", () => {
  assert.match(runtime, /IosChatPostgrestTransport/);
  assert.match(runtime, /IosNotificationReplyRuntime/);
  assert.match(transport, /executeIosChatPostgrestRequest\(/);
  assert.match(transport, /executeIosChatRequestWithSingleRefresh\(/);
});

test("the iOS boundary builds the exact authenticated PostgREST request", () => {
  assert.match(transport, /restBaseUrl\(\)\}\/rpc\/\$functionName/);
  assert.match(transport, /setHTTPMethod\("POST"\)/);
  assert.match(transport, /setValue\(configuration\.publishableKey\(\), "apikey"\)/);
  assert.match(transport, /setValue\("Bearer \$\{session\.bearerToken\}", "Authorization"\)/);
  assert.match(transport, /setValue\("application\/json", "Accept"\)/);
  assert.match(transport, /setValue\("application\/json", "Content-Type"\)/);
  assert.match(transport, /setHTTPBody\(body\.encodeToByteArray\(\)\.toIosData\(\)\)/);
});

test("the focal XCTest scopes refresh and replay to one transport call", () => {
  assert.match(boundary, /unauthorizedReplyRefreshesTheSameActorAndReplaysExactlyOnce/);
  assert.match(boundary, /assertEquals\(1, refreshCalls\)/);
  assert.match(boundary, /replacementByAnotherActorFailsClosedBeforeAnyReplay/);
  assert.match(boundary, /forbiddenAndServerFailuresAreNotRefreshedOrReplayedInsideOneTransportCall/);
  assert.match(boundary, /listOf\(403, 500\)/);
  assert.match(boundary, /assertEquals\(0, refreshCalls\)/);
  assert.match(inventory, /NotificationReplySender` conserva sus tres intentos acotados/);
  assert.match(board, /acredita los tres intentos del sender/);
});

test("documentation closes only the exact HTTP boundary and preserves external and lifecycle limits", () => {
  const inventoryRow = inventory.split("\n").find((line) => line.startsWith("| `FLOW-NOTIFICATION-REPLY`"));
  const boardRow = board.split("\n").find((line) => line.startsWith("| Notification Reply iOS Simulator"));
  assert.ok(inventoryRow);
  assert.ok(boardRow);
  assert.doesNotMatch(inventoryRow, /no acredita[^|]*HTTP exacto/);
  assert.doesNotMatch(boardRow, /No acredita[^|]*HTTP exacto/);
  for (const preserved of ["APNs", "dispositivo físico", "distribución", "offline", "reinicio", "navegación"]) {
    assert.match(inventoryRow, new RegExp(preserved));
    assert.match(boardRow, new RegExp(preserved));
  }
});
