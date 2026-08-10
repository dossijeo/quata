import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

const repoRoot = resolve(import.meta.dirname, "..");
const source = (path) => readFile(resolve(repoRoot, path), "utf8");

test("NOTIFICATIONS-COMMON-PARITY-001 shared root exposes stable evidence anchors", async () => {
  const content = await source("feature/notifications/src/commonMain/kotlin/com/quata/feature/notifications/presentation/NotificationsContent.kt");

  for (const anchor of [
    "NotificationsRootTestTag",
    "NotificationsLoadingTestTag",
    "NotificationsEmptyTestTag",
    "NotificationsErrorTestTag",
    "NotificationsRetryTestTag",
    "NotificationsBackTestTag",
    "NotificationItemTestTagPrefix",
  ]) {
    assert.match(content, new RegExp(`const val ${anchor}`));
  }

  assert.match(content, /testTag\(NotificationsRootTestTag\)/);
  assert.match(content, /testTag\(NotificationsLoadingTestTag\)/);
  assert.match(content, /testTag\(NotificationsEmptyTestTag\)/);
  assert.match(content, /testTag\(NotificationsErrorTestTag\)/);
  assert.match(content, /testTag\(NotificationsRetryTestTag\)/);
  assert.match(content, /testTag\(NotificationsBackTestTag\)/);
  assert.match(content, /testTag\("\$NotificationItemTestTagPrefix\$\{item\.conversationId\}"\)/);
});

test("NOTIFICATIONS-COMMON-PARITY-002 Web and iOS Spanish adapters keep Android-equivalent copy without mojibake", async () => {
  const web = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebNotificationsHost.kt");
  const ios = await source("feature/notifications/src/iosMain/kotlin/com/quata/feature/notifications/presentation/IosNotificationsHost.kt");

  for (const platform of [web, ios]) {
    assert.match(platform, /loadingLabel = "Cargando avisos\\u2026"/);
    assert.match(platform, /emptyTitle = "A\\u00fan no hay avisos"/);
    assert.match(platform, /emptyMessage = "La actividad nueva aparecer\\u00e1 aqu\\u00ed\."/);
    assert.match(platform, /errorTitle = "Los avisos no est\\u00e1n disponibles"/);
    assert.match(platform, /videoPreview = "\\uD83C\\uDFA5 V\\u00eddeo"/);
    assert.match(platform, /oneYearAgo = "hace 1 a\\u00f1o"/);
    assert.match(platform, /yearsAgo = \{ "hace \$it a\\u00f1os" \}/);
    assert.doesNotMatch(platform, /Ã|â|ðŸ|ï¸/);
  }
});

test("NOTIFICATIONS-COMMON-PARITY-003 Web notifications derive from the mounted Chat source", async () => {
  const main = await source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt");
  const fixture = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatE2eFixture.kt");

  assert.match(main, /val chatFixtureRepository = remember \{ webChatE2eFixtureOrNull\(\) \}/);
  assert.match(main, /val chatHostRepository = chatFixtureRepository \?: chatRepository/);
  assert.match(main, /WebNotificationsRepository\(chatHostRepository\)/);
  assert.match(main, /val shouldObserveNotifications = \(isSessionReady && runtimeConfiguration\.isBackendConfigured\) \|\| isLocalChatFixture/);
  assert.match(main, /canMutate = isSessionReady \|\| isLocalChatFixture/);
  assert.match(fixture, /unreadCount = 2/);
  assert.match(fixture, /override suspend fun markConversationRead\(conversationId: String\): Result<Unit>/);
  assert.match(fixture, /conversation\.copy\(unreadCount = 0\)/);
});
