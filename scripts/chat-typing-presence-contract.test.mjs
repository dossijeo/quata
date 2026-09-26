import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("shared chat exposes a stable remote typing semantic anchor", async () => {
  const [indicator, browserHost] = await Promise.all([
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBubbleIndicatorsContent.kt"),
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBrowserHostContent.kt"),
  ]);
  assert.match(indicator, /ChatRemoteTypingIndicatorTestTag\s*=\s*"chat\.typing\.remote"/);
  assert.match(indicator, /semantics\s*\{\s*testTag\s*=\s*ChatRemoteTypingIndicatorTestTag\s*}/);
  assert.match(browserHost, /typingIndicator\s*=\s*\{\s*typing\s*->/);
  assert.match(browserHost, /testTag\s*=\s*ChatRemoteTypingIndicatorTestTag/);
});

test("all platform transports bind typing to the visible conversation and expire remote state", async () => {
  const [android, web, ios] = await Promise.all([
    read("app/src/main/java/com/quata/feature/chat/data/ChatTypingIndicatorManager.kt"),
    read("web/src/wasmJsMain/kotlin/com/quata/web/WebChatRealtimeGateway.kt"),
    read("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatRealtimeGateway.kt"),
  ]);
  for (const source of [android, web, ios]) {
    assert.match(source, /profile_id/);
    assert.match(source, /is_typing/);
    assert.match(source, /remoteTypingAt/);
    assert.match(source, /3_000L/);
  }
  assert.match(android, /realtime:quata-typing-/);
  assert.match(web, /chatTypingTopic\(conversationId\)/);
  assert.match(ios, /chatTypingTopic\(conversationId\)/);
  assert.match(android, /activeConversationId\s*!=\s*conversationId/);
  assert.match(web, /visibleConversationId\s*!=\s*conversationId/);
  assert.match(ios, /visibleConversationId\s*!=\s*conversationId/);
});

test("the ephemeral evidence peer authenticates the exact topic without persisting secrets", async () => {
  const peer = await read("scripts/e2e-fixtures/chat-typing-peer.mjs");
  assert.match(peer, /realtime:quata-typing-\$\{conversation}/);
  assert.match(peer, /access_token:\s*token/);
  assert.match(peer, /event:\s*"typing"/);
  assert.match(peer, /profile_id:\s*actor/);
  assert.doesNotMatch(peer, /console\.(?:log|error|warn)/);
  assert.match(peer, /Credentials remain inside that context/);
  assert.match(peer, /setInterval\([\s\S]*"phoenix",\s*"heartbeat"[\s\S]*25_000/);
  assert.match(peer, /clearInterval\(heartbeatTimer\)/);
});

test("Android reconnects one typing channel and cancels retries outside foreground", async () => {
  const [manager, client, reconnectTest, evidence] = await Promise.all([
    read("app/src/main/java/com/quata/feature/chat/data/ChatTypingIndicatorManager.kt"),
    read("app/src/main/java/com/quata/data/supabase/SupabaseRealtimeClient.kt"),
    read("app/src/test/java/com/quata/feature/chat/data/ChatTypingIndicatorManagerReconnectTest.kt"),
    read("scripts/chat-typing-presence-android-evidence.mjs"),
  ]);
  assert.match(client, /interface RealtimeBroadcastClient/);
  assert.match(manager, /RealtimeStatus\.Closed, RealtimeStatus\.Error -> handleConnectionLoss\(generation\)/);
  assert.match(manager, /reconnectJob\?\.isActive == true/);
  assert.match(manager, /connectionGeneration \+= 1/);
  assert.match(manager, /reconnectJob\?\.cancel\(\)/);
  assert.match(reconnectTest, /errorAndFailureScheduleOneReconnect/);
  assert.match(reconnectTest, /leavingForegroundCancelsPendingReconnect/);
  assert.match(evidence, /android_typing_test_ended_before_\$\{stage}/);
  assert.match(evidence, /if \(error\?\.message !== "android_typing_stage_timeout:REMOTE_VISIBLE"\) throw error/);
});

test("iOS serializes URLSession callbacks and proves stop plus independent expiry", async () => {
  const [gateway, uiTest, runner] = await Promise.all([
    read("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatRealtimeGateway.kt"),
    read("iosApp/iosAppUITests/QuataIosRemoteTypingPresenceUITests.swift"),
    read("scripts/chat-typing-presence-ios-evidence.mjs"),
  ]);
  assert.match(gateway, /SupervisorJob\(\) \+ Dispatchers\.Main\.immediate/);
  assert.match(gateway, /onEvent\s*=\s*\{ event, payload ->\s*scope\.launch typingEvent@/);
  assert.match(gateway, /onDisconnected\s*=\s*\{\s*scope\.launch/);
  const expiry = uiTest.indexOf('phase("ready-expiry"');
  const stop = uiTest.indexOf('phase("ready-stop"');
  const local = uiTest.indexOf('phase("local-typed"');
  assert.ok(expiry > 0 && expiry < stop && stop < local);
  assert.match(runner, /expiry-signals-stopped\.phase/);
  assert.match(runner, /messageSetUnchanged:\s*true/);
  assert.match(runner, /databaseMutation:\s*false/);
});

test("web evidence rejects semantic observations hidden behind the startup splash", async () => {
  const [runner, webMain] = await Promise.all([
    read("scripts/chat-typing-presence-web-evidence.mjs"),
    read("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
  ]);
  const splashGate = runner.indexOf("chat_typing_splash_unsettled");
  const composerGate = runner.indexOf("chat.composer.input");
  const indicatorGate = runner.indexOf("chat.typing.remote");
  assert.ok(splashGate > 0 && splashGate < composerGate && composerGate < indicatorGate);
  assert.match(runner, /messageSetUnchanged:\s*true/);
  assert.match(runner, /databaseMutation:\s*false/);
  assert.match(runner, /--use-angle=swiftshader/);
  assert.match(runner, /--force-renderer-accessibility/);
  assert.match(
    webMain,
    /if \(!splashAnimationFinished \|\| !isSessionResolved\) \{[\s\S]*QuataSplashScreen\([\s\S]*} else \{[\s\S]*Box\(Modifier\.fillMaxSize\(\)\.fluidTouchEffect/,
  );
});
