import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { parsePidObservation } from "./shell-navigation-android-process-death-utils.mjs";
import {
  authenticateExactChatActor,
  jsonRequestNoRedirect,
} from "./shell-navigation-local-backend-utils.mjs";

const runner = await readFile(new URL("./shell-navigation-android-process-death-evidence.mjs", import.meta.url), "utf8");
const backendUtils = await readFile(new URL("./shell-navigation-local-backend-utils.mjs", import.meta.url), "utf8");
const instrumentation = await readFile(
  new URL("../app/src/androidTest/java/com/quata/core/navigation/ShellNavigationPolicyInstrumentedTest.kt", import.meta.url),
  "utf8",
);
const interactionInstrumentation = await readFile(
  new URL("./android-external-link-sender/src/androidTest/java/com/quata/deeplinksender/PublicLinkTest.java", import.meta.url),
  "utf8",
);
const conversations = await readFile(
  new URL("../feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsScreenHost.kt", import.meta.url),
  "utf8",
);
const appNavGraph = await readFile(
  new URL("../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt", import.meta.url),
  "utf8",
);
const appConfig = await readFile(
  new URL("../app/src/main/java/com/quata/core/config/AppConfig.kt", import.meta.url),
  "utf8",
);
const appGradle = await readFile(new URL("../app/build.gradle.kts", import.meta.url), "utf8");
const debugManifest = await readFile(new URL("../app/src/debug/AndroidManifest.xml", import.meta.url), "utf8");
const debugNetworkPolicy = await readFile(
  new URL("../app/src/debug/res/xml/quata_debug_network_security_config.xml", import.meta.url),
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

test("the focal mode proves exact Chat conversation and message restoration without rerunning the root matrix", () => {
  assert.match(runner, /--exact-chat-only/);
  assert.match(runner, /if \(options\.exactChatOnly\)/);
  assert.match(runner, /quata_chat_get_favorites/);
  assert.match(runner, /quataShellNavigationTargetMessageId/);
  assert.match(interactionInstrumentation, /openExactFavoriteForProcessDeathProbe/);
  assert.match(interactionInstrumentation, /assertEquals\("com\.quata", device\.getCurrentPackageName\(\)\)/);
  assert.match(interactionInstrumentation, /quataShellNavigationTargetMarker/);
  assert.match(interactionInstrumentation, /candidate = candidate\.getParent\(\)/);
  assert.match(interactionInstrumentation, /performAction\(AccessibilityNodeInfo\.ACTION_CLICK\)/);
  assert.match(interactionInstrumentation, /hasSelectedMarker\(root, marker\)/);
  assert.match(runner, /chat-__favorite_messages__/);
  assert.match(runner, /chat\.message\.\$\{target\.messageId\}\.selected/);
  assert.match(runner, /exact_chat_target_selected_by_uiautomator_after_distinct_task_base_intent/);
  assert.match(runner, /\/intent\/i\.test\(line\)/);
  assert.match(runner, /baseIntentLine\.includes\(targetIntentMarker\)/);
  assert.match(runner, /exact_chat_differential_base_intent_not_preserved/);
  assert.match(runner, /exact_chat_conversation_and_message_restored_in_new_process/);
  assert.match(runner, /exact_chat_session_still_refreshable/);
  assert.match(runner, /Promise\.allSettled\(\[webCleanup\(\), authCleanup\(\)\]\)/);
  assert.match(runner, /conversationIdSha256: sha256\(target\.conversationId\)/);
  assert.match(runner, /messageIdSha256: sha256\(target\.messageId\)/);
  assert.match(appNavGraph, /var persistedChatFocusConversationId by rememberSaveable/);
  assert.match(appNavGraph, /var persistedChatFocusedMessageId by rememberSaveable/);
  assert.match(appNavGraph, /var activeChatFocusConversationId by remember \{ mutableStateOf\(persistedChatFocusConversationId\) \}/);
  assert.match(appNavGraph, /var activeChatFocusedMessageId by remember \{ mutableStateOf\(persistedChatFocusedMessageId\) \}/);
  assert.match(appNavGraph, /focusedMessageId = activeChatFocusedMessageId\.takeIf \{/);
  assert.match(appNavGraph, /onFocusedMessageHandled = \{[\s\S]*activeChatFocusConversationId = null[\s\S]*activeChatFocusedMessageId = null/);
  assert.doesNotMatch(appNavGraph, /onFocusedMessageHandled = \{ persistedChatFocusedMessageId = null \}/);
});

test("the focal runner can use a restored local Supabase without weakening release configuration", () => {
  assert.match(runner, /--backend-config-file/);
  assert.match(runner, /restored_local_supabase/);
  assert.match(runner, /evidence_backend_override_requires_fresh_build/);
  assert.match(runner, /hostname !== "127\.0\.0\.1"/);
  assert.match(runner, /hostname !== "10\.0\.2\.2"/);
  assert.match(backendUtils, /grant_type=password/);
  assert.match(backendUtils, /quata_chat_auth_profile_id/);
  assert.match(backendUtils, /redirect: "error"/);
  assert.match(backendUtils, /captureCustody\(\{/);
  assert.match(runner, /quata_chat_get_favorites_page/);
  assert.match(runner, /quata_chat_send_message/);
  assert.match(runner, /quata_chat_set_favorite/);
  assert.match(runner, /synthetic_local_chat_fixture_staged_via_product_rpcs/);
  assert.match(runner, /INTERACTION_TEST_PACKAGE = "com\.quata\.deeplinksender\.test"/);
  assert.match(runner, /private_real_session_staged_without_logging/);
  assert.match(runner, /quataShellNavigationSessionFile/);
  assert.match(runner, /exact_chat_session_still_refreshable/);
  assert.match(runner, /fetchNoRedirect/);
  assert.match(runner, /replace\(\/sb_publishable_/);
  assert.doesNotMatch(runner, /report\.backend[\s\S]{0,300}publishableKey/);

  assert.match(appGradle, /quata\.evidenceBackendOverride/);
  assert.match(appGradle, /uri\.host == "10\.0\.2\.2"/);
  assert.match(appGradle, /EVIDENCE_BACKEND_OVERRIDE_ENABLED", "false"/);
  assert.match(appGradle, /debug \{[\s\S]*EVIDENCE_BACKEND_OVERRIDE_ENABLED", "true"/);
  assert.match(appConfig, /BuildConfig\.EVIDENCE_BACKEND_OVERRIDE_ENABLED/);
  assert.match(appConfig, /QuataPublicBackendConfig\.SUPABASE_URL/);
  assert.match(appConfig, /QuataPublicBackendConfig\.SUPABASE_PUBLISHABLE_KEY/);
  assert.match(debugManifest, /networkSecurityConfig="@xml\/quata_debug_network_security_config"/);
  assert.match(debugNetworkPolicy, /<domain includeSubdomains="false">10\.0\.2\.2<\/domain>/);
  assert.doesNotMatch(debugNetworkPolicy, /cleartextTrafficPermitted="true"[\s\S]*<base-config/);
});

test("local authentication custody is captured before a resolver failure", async () => {
  const accessToken = "access-token";
  const refreshToken = "refresh-token";
  let custody = null;
  let requestCount = 0;
  await assert.rejects(
    authenticateExactChatActor({
      backend: {},
      baseUrl: "http://127.0.0.1:54321",
      key: "sb_publishable_test-only-not-a-secret",
      credentials: { country_code: "+34", phone: "600000000", password: "fixture" },
      request: async () => {
        requestCount += 1;
        if (requestCount === 1) return { access_token: accessToken, refresh_token: refreshToken, user: { id: "auth-user" } };
        throw new Error("resolver_failed");
      },
      captureCustody: (value) => { custody = value; },
    }),
    /resolver_failed/,
  );
  assert.equal(requestCount, 2);
  assert.deepEqual(custody, { accessToken, refreshToken, webSessionToken: null });
});

test("local backend requests reject redirects without contacting the target", async (t) => {
  let targetRequests = 0;
  const target = createServer((request, response) => {
    targetRequests += 1;
    response.writeHead(200, { "content-type": "application/json" });
    response.end("{}");
  });
  await new Promise((resolve) => target.listen(0, "127.0.0.1", resolve));
  t.after(() => target.close());
  const targetPort = target.address().port;
  const redirect = createServer((request, response) => {
    response.writeHead(307, { location: `http://127.0.0.1:${targetPort}/captured` });
    response.end();
  });
  await new Promise((resolve) => redirect.listen(0, "127.0.0.1", resolve));
  t.after(() => redirect.close());
  const redirectPort = redirect.address().port;
  await assert.rejects(
    jsonRequestNoRedirect(`http://127.0.0.1:${redirectPort}/auth`, {
      method: "POST",
      headers: { authorization: "Bearer fixture" },
      body: JSON.stringify({ password: "fixture" }),
    }, "redirect_probe"),
    /redirect_probe_network_failed/,
  );
  assert.equal(targetRequests, 0);
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
  assert.match(instrumentation, /sessionManager\.setSession\(sessionFromFile\(sessionFile\)\)/);
  assert.match(instrumentation, /android_shell_process_death_session_shape_invalid/);
  assert.match(instrumentation, /android_shell_process_death_actor_binding_missing/);
  assert.match(instrumentation, /isSupabaseAuthenticated\(\) == true/);
});

test("Conversations exposes a route-specific root instead of relying on the shared navigation control", () => {
  assert.match(conversations, /const val ConversationsRootTestTag = "conversations\.root"/);
  assert.match(conversations, /testTag = ConversationsRootTestTag/);
  assert.match(conversations, /contentDescription = ConversationsRootTestTag/);
});
