import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("SOS cooldown is atomic, actor scoped, structured and reversible", async () => {
  const [forward, rollback] = await Promise.all([
    read("supabase/migrations/20260917193000_chat_sos_rate_limit.sql"),
    read("supabase/rollbacks/20260917193000_chat_sos_rate_limit.rollback.sql"),
  ]);

  assert.match(forward, /pg_advisory_xact_lock\s*\(\s*hashtextextended\s*\(\s*'quata-chat-sos:'\s*\|\|\s*v_actor::text/i);
  assert.match(forward, /from\s+public\.chat_sos_events[\s\S]*where\s+e\.profile_id\s*=\s*v_actor/i);
  assert.match(forward, /interval\s+'5 minutes'/i);
  assert.match(forward, /'rate_limited'\s*,\s*true/i);
  assert.match(forward, /'remaining_millis'\s*,\s*v_remaining_millis/i);
  assert.match(forward, /'rate_limited'\s*,\s*false/i);
  assert.doesNotMatch(rollback, /rate_limited|remaining_millis|pg_advisory_xact_lock/i);
  assert.match(rollback, /create or replace function public\.quata_chat_send_sos/i);
});

test("portable repositories and launchers preserve the real SOS transaction", async () => {
  const [portableRepository, androidRepository, coordinator, web, ios, profileHost] = await Promise.all([
    read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/PostgrestChatRepository.kt"),
    read("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt"),
    read("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/domain/SosDispatchCoordinator.kt"),
    read("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
    read("iosApp/iosApp/QuataIosApp.swift"),
    read("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt"),
  ]);

  assert.match(portableRepository, /throw SosRateLimitException/);
  assert.match(androidRepository, /if \(result\.exceptionOrNull\(\) is SosRateLimitException\) result/);
  for (const token of [
    "getProfileEditModel()",
    "IgnoredWhileSending",
    "actorProvider.currentActorId() != actor",
    "sendInitial(",
    "sendLocationUpdate(",
    "pendingConfigurationActor",
    "activeDispatch?.cancel()",
    "coroutineScope",
    "currentCoroutineContext().ensureActive()",
    "withContext(NonCancellable) { operation.join() }",
    "NoPendingConfiguration",
    "clientMessageId = \"sos-location:",
    "expectedActorId = actor",
  ]) assert.ok(coordinator.includes(token), `missing coordinator contract: ${token}`);
  assert.match(portableRepository, /currentUserId\(expectedActorId\)/);
  assert.match(portableRepository, /expectedActorId == null \|\| id == expectedActorId/);
  assert.match(androidRepository, /expectedActorId == null \|\| session\.userId == expectedActorId/);
  assert.match(web, /sosCoordinator\.dispatch\(\)/);
  assert.match(web, /resumeAfterConfigurationSaved\(\)/);
  assert.match(ios, /sosDispatchRuntime/);
  assert.match(ios, /resumeAfterConfigurationSaved/);
  assert.match(profileHost, /onEmergencySettingsSaved\(\)/);
});
