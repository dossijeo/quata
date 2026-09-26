import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

test("portable Conversations refreshes immediately on an observed offline-to-online transition", () => {
  const repository = read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/PostgrestChatRepository.kt");
  const regression = read("feature/chat/src/commonTest/kotlin/com/quata/feature/chat/data/ChatRealtimeGatewayContractTest.kt");

  assert.match(repository, /previousAvailable == false && available/);
  assert.match(repository, /recovered && _isAppForeground\.value\) refreshInbox\(\)/);
  assert.match(regression, /networkRecoveryRefreshesTheInboxImmediatelyWithoutWaitingForThePollingInterval/);
  assert.match(regression, /pollIntervalMillis = 60_000L/);
  assert.match(regression, /withTimeout\(5_000L\) \{ recovered\.await\(\) \}/);
});

test("Android refreshes the active thread as part of network recovery", () => {
  const repository = read("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt");
  const recovery = repository.slice(
    repository.indexOf("override fun setDeviceNetworkAvailable"),
    repository.indexOf("override fun currentUser"),
  );

  assert.match(recovery, /refreshAndConnectRealtime\(session\.userId\)/);
  assert.match(recovery, /_activeConversationId\.value\?\.let \{ conversationId ->[\s\S]*refreshMessages\(conversationId, force = true\)/);
});

test("Android focal evidence keeps one foreground activity and coordinates a real peer message", () => {
  const instrumented = read("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");
  const runner = read("scripts/chat-actions-notifications-android-evidence.mjs");

  assert.match(instrumented, /"network-recovery" -> runNetworkRecoveryStage/);
  assert.match(instrumented, /setDeviceNetworkAvailable\(false\)[\s\S]*network-recovery-ready[\s\S]*setDeviceNetworkAvailable\(true\)/);
  assert.match(instrumented, /matchingMessages\.size == 1/);
  assert.match(runner, /--network-recovery-only/);
  assert.match(runner, /runInstrumentationStage\("network-recovery"\)[\s\S]*quata_chat_send_message[\s\S]*network-recovery-restore[\s\S]*await instrumentationPromise/);
  assert.match(runner, /same_foreground_activity_refreshed_active_thread_on_network_restore/);
});

test("network recovery contract is mandatory in local fast suites", () => {
  const packageJson = read("package.json");
  const occurrences = packageJson.match(/scripts\/conversations-network-recovery-contract\.test\.mjs/g) ?? [];
  assert.equal(occurrences.length, 2);
});
