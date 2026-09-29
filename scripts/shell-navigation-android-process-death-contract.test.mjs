import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { parsePidObservation } from "./shell-navigation-android-process-death-utils.mjs";

const runner = await readFile(new URL("./shell-navigation-android-process-death-evidence.mjs", import.meta.url), "utf8");
const instrumentation = await readFile(
  new URL("../app/src/androidTest/java/com/quata/core/navigation/ShellNavigationPolicyInstrumentedTest.kt", import.meta.url),
  "utf8",
);
const conversations = await readFile(
  new URL("../feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsScreenHost.kt", import.meta.url),
  "utf8",
);

test("Android shell process-death evidence is explicit, emulator-only and credential-safe", () => {
  assert.match(runner, /QUATA_SHELL_PROCESS_DEATH_EVIDENCE !== "1"/);
  assert.match(runner, /explicit_android_serial_required/);
  assert.match(runner, /getprop", "ro\.kernel\.qemu/);
  assert.match(runner, /shell_process_death_requires_emulator/);
  assert.match(runner, /QUATA_CHAT_GROUP_CREDENTIALS_FILE/);
  assert.doesNotMatch(runner, /QUATA_CHAT_GROUP_CREDENTIALS_FILE\.txt/);
  assert.match(runner, /private_credentials_staged_without_logging/);
  assert.match(runner, /let appTouched = false/);
  assert.match(runner, /if \(appTouched\)/);
  assert.match(runner, /environmentAcquired = true/);
  assert.match(runner, /pm", "clear", PACKAGE/);
});

test("Android shell process-death evidence proves a new process and restored nested back stack", () => {
  assert.match(runner, /input", "keyevent", "KEYCODE_HOME/);
  assert.match(runner, /"am", "kill", PACKAGE/);
  assert.match(runner, /pidAfter === pidBefore/);
  assert.match(runner, /waitForResource\("profile\.details\.root"\)/);
  assert.match(runner, /input", "keyevent", "KEYCODE_BACK/);
  assert.match(runner, /waitForResource\("profile\.save"\)/);
  assert.match(runner, /waitForResourceAbsent\("profile\.details\.root"\)/);
  assert.match(runner, /__QUATA_PID_STATUS__/);
  assert.doesNotMatch(runner, /pidof[^\n]+\|\|/);
  assert.match(runner, /__QUATA_CREDENTIAL_ABSENT__/);
  assert.match(runner, /device_credential_observation_invalid/);
  for (const [route, resource, launchRoute] of [
    ["neighborhoods", "neighborhood.directory.root", "conversations"],
    ["conversations", "conversations.root", "official"],
    ["official", "official-feed-common-root", "feed"],
    ["feed", "feed.root", "profile"],
    ["profile", "profile.save", "neighborhoods"],
  ]) {
    assert.match(
      runner,
      new RegExp(`route: "${route}", resource: "${resource.replaceAll(".", "\\.")}", launchRoute: "${launchRoute}"`),
    );
    assert.notEqual(route, launchRoute);
  }
  assert.match(runner, /verifyPrimaryRootProcessDeath/);
  assert.match(runner, /clickResource\(`navigation\.primary\.\$\{route\}`\)/);
  assert.match(runner, /primary_root_selected_from_\$\{launchRoute\}_before_process_death/);
  assert.match(runner, /primary_root_restored_in_new_process/);
});

test("PID observation accepts only remote status 0 or the exact no-process status 1", () => {
  assert.equal(parsePidObservation("4321\n__QUATA_PID_STATUS__:0\n"), "4321");
  assert.equal(parsePidObservation("__QUATA_PID_STATUS__:1\n"), null);
  assert.throws(() => parsePidObservation("__QUATA_PID_STATUS__:127\n"), /pid_command_failed:127/);
  assert.throws(() => parsePidObservation("pidof: not found\n__QUATA_PID_STATUS__:127\n"), /pid_command_failed:127/);
  assert.throws(() => parsePidObservation(""), /pid_status_missing/);
});

test("authentication setup remains opt-in and verifies a real Supabase session", () => {
  assert.match(instrumentation, /fun authenticateForProcessDeathProbe\(\) = runBlocking/);
  assert.match(instrumentation, /quataShellNavigationProcessDeathEvidence/);
  assert.match(instrumentation, /authRepository\.login/);
  assert.match(instrumentation, /isSupabaseAuthenticated\(\) == true/);
});

test("Conversations exposes a route-specific root instead of relying on the shared navigation control", () => {
  assert.match(conversations, /const val ConversationsRootTestTag = "conversations\.root"/);
  assert.match(conversations, /testTag = ConversationsRootTestTag/);
  assert.match(conversations, /contentDescription = ConversationsRootTestTag/);
});
