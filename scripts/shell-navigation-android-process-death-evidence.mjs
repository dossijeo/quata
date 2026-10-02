#!/usr/bin/env node
import { execFile, spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { promisify } from "node:util";
import { parsePidObservation } from "./shell-navigation-android-process-death-utils.mjs";
import {
  authenticateExactChatActor,
  fetchNoRedirect,
  jsonRequestNoRedirect,
} from "./shell-navigation-local-backend-utils.mjs";

const execFileAsync = promisify(execFile);
const CHECK = "FLOW-SHELL-NAV-ANDROID-PROCESS-DEATH-001";
const PACKAGE = "com.quata";
const TEST_PACKAGE = "com.quata.test";
const INTERACTION_TEST_PACKAGE = "com.quata.deeplinksender.test";
const INTERACTION_APK = "scripts/android-external-link-sender/build/outputs/apk/debug/QuataExternalLinkSender-debug.apk";
const INTERACTION_TEST_APK = "scripts/android-external-link-sender/build/outputs/apk/androidTest/debug/QuataExternalLinkSender-debug-androidTest.apk";
const DEVICE_CREDENTIAL = "/data/user/0/com.quata/files/shell-process-death-credentials.json";
const DEVICE_SESSION = "/data/user/0/com.quata/files/shell-process-death-session.json";
const PRIMARY_ROOTS = [
  { route: "neighborhoods", resource: "neighborhood.directory.root", launchRoute: "conversations", launchResource: "conversations.root" },
  { route: "conversations", resource: "conversations.root", launchRoute: "official", launchResource: "official-feed-common-root" },
  { route: "official", resource: "official-feed-common-root", launchRoute: "feed", launchResource: "feed.root" },
  { route: "feed", resource: "feed.root", launchRoute: "profile", launchResource: "profile.save" },
  { route: "profile", resource: "profile.save", launchRoute: "neighborhoods", launchResource: "neighborhood.directory.root" },
];
const options = parseArgs(process.argv.slice(2));
const adb = process.env.ADB?.trim() || "adb";
let appTouched = false;
let exactChatSession = null;
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  serialSha256: sha256(options.serial),
  steps: [],
  screenshots: [],
  cleanup: {
    state: "pending",
    environmentAcquired: false,
    credentialsRemoved: false,
    appDataCleared: false,
    auxiliarySessionRevoked: false,
  },
};

try {
  if (process.env.QUATA_SHELL_PROCESS_DEATH_EVIDENCE !== "1") {
    throw new Error("shell_process_death_opt_in_required");
  }
  const emulator = await captureAdb(["shell", "getprop", "ro.kernel.qemu"]);
  if (emulator.trim() !== "1") throw new Error("shell_process_death_requires_emulator");
  const credentials = await loadCredentials(options.credentialsFile);
  const backend = options.backendConfigFile ? await loadBackendConfig(options.backendConfigFile) : null;
  if (backend && !options.exactChatOnly) throw new Error("evidence_backend_override_requires_exact_chat_mode");
  if (backend && options.skipBuild) throw new Error("evidence_backend_override_requires_fresh_build");
  const exactChatTarget = options.exactChatOnly ? await findExactChatTarget(credentials, backend) : null;
  if (backend) {
    report.backend = {
      mode: backend.mode,
      configSha256: backend.configSha256,
      sourceBackupPlaintextSha256: backend.sourceBackupPlaintextSha256,
      deviceOrigin: "android_emulator_loopback",
    };
    report.steps.push("restored_local_supabase_auth_and_postgrest_preflight_passed");
  }

  if (!options.skipBuild) {
    const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
    const gradleArguments = [
      ":app:assembleDebug",
      ":app:assembleDebugAndroidTest",
      ":vosk_model_en:assembleDebug",
      "--console=plain",
    ];
    if (backend) {
      gradleArguments.push(
        "-Pquata.evidenceBackendOverride=true",
        `-Pquata.evidenceSupabaseUrl=${backend.deviceBaseUrl}`,
        `-Pquata.evidenceSupabasePublishableKey=${backend.publishableKey}`,
      );
    }
    await run(gradle, gradleArguments);
    if (exactChatTarget?.marker) {
      await run(gradle, [
        "-p", "scripts/android-external-link-sender",
        "assembleDebug", "assembleDebugAndroidTest", "--console=plain",
      ]);
    }
    report.steps.push("affected_android_apks_built");
  }

  await runAdb(["install-multiple", "-r", "app/build/outputs/apk/debug/app-debug.apk", "vosk_model_en/build/outputs/apk/debug/vosk_model_en-debug.apk"]);
  appTouched = true;
  report.cleanup.environmentAcquired = true;
  await runAdb(["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  if (exactChatTarget?.marker) {
    await runAdb(["install", "-r", INTERACTION_APK]);
    await runAdb(["install", "-r", "-t", INTERACTION_TEST_APK]);
  }
  report.steps.push("base_split_and_test_apks_installed");

  await runAdb(["shell", "pm", "clear", PACKAGE]);
  await runAdb(["shell", "run-as", PACKAGE, "mkdir", "-p", `/data/user/0/${PACKAGE}/files`]);
  const authenticationArguments = [];
  if (backend) {
    await writeDeviceJson(DEVICE_SESSION, exactChatSession.deviceSession);
    authenticationArguments.push("-e", "quataShellNavigationSessionFile", "app-internal:shell-process-death-session.json");
    report.steps.push("private_real_session_staged_without_logging");
  } else {
    await writeDeviceJson(DEVICE_CREDENTIAL, credentials);
    authenticationArguments.push("-e", "quataShellNavigationCredentialsFile", "app-internal:shell-process-death-credentials.json");
    report.steps.push("private_credentials_staged_without_logging");
  }
  const instrumentation = await captureAdb([
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.core.navigation.ShellNavigationPolicyInstrumentedTest#authenticateForProcessDeathProbe",
    ...authenticationArguments,
    "-e", "quataShellNavigationProcessDeathEvidence", "1",
    `${TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner`,
  ], { timeout: 60_000 });
  if (!/OK \(1 test\)/.test(instrumentation) || /FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentation)) {
    throw new Error("shell_process_death_authentication_preflight_failed");
  }
  report.steps.push("real_authenticated_session_persisted");

  if (options.exactChatOnly) {
    await runAdb(["shell", "am", "force-stop", PACKAGE]);
    await runAdb(["shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", "https://egquata.com/#chat-__favorite_messages__", "-p", PACKAGE]);
    await waitForResource(`chat.message.${exactChatTarget.messageId}`);
    if (exactChatTarget.marker) {
      const interaction = await captureAdb([
        "shell", "am", "instrument", "-w", "-r",
        "-e", "class", "com.quata.deeplinksender.PublicLinkTest#openExactFavoriteForProcessDeathProbe",
        "-e", "quataShellNavigationTargetMessageId", exactChatTarget.messageId,
        "-e", "quataShellNavigationTargetMarker", exactChatTarget.marker,
        "-e", "quataShellNavigationProcessDeathEvidence", "1",
        `${INTERACTION_TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner`,
      ], { timeout: 120_000 });
      if (!/OK \(1 test\)/.test(interaction) || /FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(interaction)) {
        throw new Error("shell_process_death_favorite_interaction_preflight_failed");
      }
      report.favoriteMessageInteraction = { accessibilityAction: true, targetValidated: true };
      report.steps.push("exact_chat_favorite_opened_by_accessibility_action");
    } else {
      report.favoriteMessageInteraction = await clickResource(`chat.message.${exactChatTarget.messageId}`);
      report.steps.push("exact_chat_favorite_opened_by_existing_coordinate_probe");
    }
    const target = exactChatTarget ?? await findExactChatTarget(credentials);
    const process = await verifyExactChatProcessDeath(target);
    report.process = process;
    report.status = "passed";
  } else {

    await runAdb([
      "shell", "am", "start", "-W", "-n", `${PACKAGE}/.MainActivity`,
      "--ez", "com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", "true",
      "--es", "com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "profile",
    ]);
    try {
      await waitForResource("profile.save");
    } catch (error) {
      await captureScreenshot("unexpected-initial-shell").catch(() => {});
      throw error;
    }
    report.steps.push("authenticated_profile_root_visible_before_process_death");

    await clickResource("profile.details.open");
    await waitForResource("profile.details.root");
    await captureScreenshot("profile-details-before-process-death");
    report.steps.push("profile_details_nested_destination_visible_before_process_death");

    const pidBefore = await currentPid();
    if (!pidBefore) throw new Error("shell_process_death_pid_missing_before_kill");
    await runAdb(["shell", "input", "keyevent", "KEYCODE_HOME"]);
    await delay(1_500);
    await runAdb(["shell", "am", "kill", PACKAGE]);
    await waitForPidAbsent();
    report.steps.push("background_process_killed_by_android_activity_manager");

    await runAdb(["shell", "am", "start", "-W", "-n", `${PACKAGE}/.MainActivity`]);
    await waitForResource("profile.details.root");
    const pidAfter = await currentPid();
    if (!pidAfter || pidAfter === pidBefore) throw new Error("shell_process_death_pid_not_replaced");
    await captureScreenshot("profile-details-after-process-death");
    report.steps.push("nested_profile_details_restored_in_new_process");

    await runAdb(["shell", "input", "keyevent", "KEYCODE_BACK"]);
    await waitForResource("profile.save");
    await waitForResourceAbsent("profile.details.root");
    await captureScreenshot("profile-root-after-restored-back");
    report.steps.push("restored_back_stack_returned_to_profile_root");

    const restoredPrimaryRoots = [];
    for (const primary of PRIMARY_ROOTS) {
      await verifyPrimaryRootProcessDeath(primary);
      restoredPrimaryRoots.push(primary.route);
    }
    report.process = { pidChanged: true, restoredPrimaryRoots };
    report.status = "passed";
  }
} catch (error) {
  if (appTouched) {
    report.uiDiagnostics = await collectSafeUiDiagnostics().catch(() => ({ unavailable: true }));
  }
  report.error = safeFailure(error);
} finally {
  if (exactChatSession) {
    try {
      await revokeExactChatSession(exactChatSession);
      report.cleanup.auxiliarySessionRevoked = true;
    } catch (error) {
      report.error ??= safeFailure(error);
      report.status = "failed";
    }
  } else {
    report.cleanup.auxiliarySessionRevoked = true;
  }
  if (appTouched) {
    await runAdb(["shell", "run-as", PACKAGE, "rm", "-f", DEVICE_CREDENTIAL]).catch(() => {});
    await runAdb(["shell", "run-as", PACKAGE, "rm", "-f", DEVICE_SESSION]).catch(() => {});
    try {
      report.cleanup.credentialsRemoved = !(await devicePrivateFileExists(DEVICE_CREDENTIAL))
        && !(await devicePrivateFileExists(DEVICE_SESSION));
    } catch {}
    let clearSucceeded = false;
    try {
      const { stdout } = await runAdb(["shell", "pm", "clear", PACKAGE]);
      clearSucceeded = String(stdout).trim() === "Success";
      report.cleanup.appDataCleared = clearSucceeded
        && !(await devicePrivateFileExists(DEVICE_CREDENTIAL))
        && !(await devicePrivateFileExists(DEVICE_SESSION))
        && !(await currentPid());
    } catch {}
    report.cleanup.state = report.cleanup.credentialsRemoved
      && report.cleanup.appDataCleared
      && report.cleanup.auxiliarySessionRevoked
      ? "completed"
      : "failed";
  } else {
    report.cleanup.state = "not-required";
  }
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Shell navigation Android process-death evidence written: ${options.output}`);
}

if (report.status !== "passed" || report.cleanup.state !== "completed") process.exitCode = 1;
else console.log("Shell navigation Android process-death evidence passed.");

function parseArgs(args) {
  const parsed = {
    serial: process.env.QUATA_ANDROID_SERIAL?.trim(),
    credentialsFile: process.env.QUATA_CHAT_GROUP_CREDENTIALS_FILE?.trim(),
    output: resolve("build-reports/android/shell-navigation-process-death-evidence.json"),
    screenshotDir: resolve("build-reports/android/shell-navigation-process-death"),
    backendConfigFile: process.env.QUATA_SHELL_PROCESS_DEATH_BACKEND_CONFIG_FILE?.trim(),
    skipBuild: false,
    exactChatOnly: false,
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    if (key === "--skip-build") { parsed.skipBuild = true; continue; }
    if (key === "--exact-chat-only") { parsed.exactChatOnly = true; continue; }
    const value = args[++index];
    if (!value || value.startsWith("--")) throw new Error(`invalid_argument:${key}`);
    if (key === "--serial") parsed.serial = value;
    else if (key === "--credentials-file") parsed.credentialsFile = value;
    else if (key === "--backend-config-file") parsed.backendConfigFile = resolve(value);
    else if (key === "--out") parsed.output = resolve(value);
    else if (key === "--screenshot-dir") parsed.screenshotDir = resolve(value);
    else throw new Error(`invalid_argument:${key}`);
  }
  if (!parsed.serial) throw new Error("explicit_android_serial_required");
  if (!parsed.credentialsFile) throw new Error("credentials_file_required");
  return parsed;
}

async function loadCredentials(path) {
  const source = JSON.parse(await readFile(path, "utf8"));
  const actor = source?.a;
  for (const field of ["country_code", "phone", "password"]) {
    if (!actor?.[field]) throw new Error(`credentials_missing:a.${field}`);
  }
  return { country_code: actor.country_code, phone: actor.phone, password: actor.password };
}

async function loadBackendConfig(path) {
  const source = await readFile(path, "utf8");
  const value = JSON.parse(source);
  const allowed = [
    "version", "mode", "hostBaseUrl", "deviceBaseUrl", "publishableKey",
    "sourceBackupPlaintextSha256",
  ];
  if (
    Object.keys(value).some((key) => !allowed.includes(key)) ||
    value.version !== 1 ||
    value.mode !== "restored_local_supabase" ||
    !/^sb_publishable_[A-Za-z0-9_-]{20,}$/.test(value.publishableKey ?? "") ||
    !/^[a-f0-9]{64}$/.test(value.sourceBackupPlaintextSha256 ?? "")
  ) {
    throw new Error("evidence_backend_configuration_invalid");
  }
  const host = new URL(value.hostBaseUrl);
  const device = new URL(value.deviceBaseUrl);
  if (
    host.protocol !== "http:" || host.hostname !== "127.0.0.1" || !host.port || host.username || host.password || host.search || host.hash ||
    device.protocol !== "http:" || device.hostname !== "10.0.2.2" || !device.port || device.username || device.password || device.search || device.hash ||
    host.port !== device.port || host.pathname !== "/" || device.pathname !== "/"
  ) {
    throw new Error("evidence_backend_origin_invalid");
  }
  return {
    ...value,
    hostBaseUrl: host.toString().replace(/\/$/, ""),
    deviceBaseUrl: device.toString(),
    configSha256: sha256(source),
  };
}

async function findExactChatTarget(credentials, backend = null) {
  const source = backend
    ? null
    : await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8");
  const baseUrl = backend?.hostBaseUrl
    ?? source?.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const key = backend?.publishableKey
    ?? source?.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !key || !key.startsWith("sb_publishable_")) throw new Error("public_backend_configuration_invalid");
  exactChatSession = {
    baseUrl,
    key,
    accessToken: null,
    refreshToken: null,
    webSessionToken: null,
  };
  const authenticated = await authenticateExactChatActor({
    backend,
    baseUrl,
    key,
    credentials,
    request: jsonRequest,
    captureCustody: (custody) => {
      exactChatSession = { ...exactChatSession, ...custody };
    },
  });
  const { auth, accessToken: token, refreshToken, webSessionToken, profileId, phoneEmail } = authenticated;
  const authUserId = auth?.user?.id;
  let deviceSession = null;
  if (backend) {
    const profiles = await jsonRequest(
      `${baseUrl}/rest/v1/community_profiles?id=eq.${encodeURIComponent(profileId)}&select=id,display_name,is_official`,
      { headers: { apikey: key, authorization: `Bearer ${token}` } },
      "exact_chat_profile",
    );
    const profile = Array.isArray(profiles) && profiles.length === 1 ? profiles[0] : null;
    const expiresAt = Number(auth?.expires_at ?? (Math.floor(Date.now() / 1000) + Number(auth?.expires_in ?? 0)));
    if (!profile || !authUserId || !Number.isSafeInteger(expiresAt) || expiresAt <= Math.floor(Date.now() / 1000)) {
      throw new Error("exact_chat_direct_auth_binding_invalid");
    }
    deviceSession = {
      token,
      userId: profileId,
      authUserId,
      accessToken: token,
      refreshToken,
      expiresAt,
      email: auth?.user?.email ?? phoneEmail,
      displayName: String(profile.display_name ?? "Usuario"),
      isOfficial: profile.is_official === true,
    };
  }
  exactChatSession = {
    ...exactChatSession,
    accessToken: token ?? null,
    refreshToken: refreshToken ?? null,
    webSessionToken: webSessionToken ?? null,
    deviceSession,
  };
  const authenticatedHeaders = { apikey: key, authorization: `Bearer ${token}`, "content-type": "application/json" };
  let favorites = await jsonRequest(
    `${baseUrl}/rest/v1/rpc/${backend ? "quata_chat_get_favorites_page" : "quata_chat_get_favorites"}`,
    {
      method: "POST",
      headers: authenticatedHeaders,
      body: JSON.stringify(backend
        ? { p_actor_profile_id: profileId, p_limit: 100, p_before_created_at: null, p_before_message_id: null }
        : { p_actor_profile_id: profileId, p_limit: 100 }),
    },
    "exact_chat_favorites",
  );
  if (backend) {
    const seedTarget = exactChatTargetFromFavorites(favorites);
    if (!seedTarget) throw new Error("exact_chat_favorite_target_missing");
    const fixtureMarker = `QUATA_SHELL_NAV_LOCAL_${report.git.head.slice(0, 12)}`;
    const sent = await jsonRequest(`${baseUrl}/rest/v1/rpc/quata_chat_send_message`, {
      method: "POST",
      headers: authenticatedHeaders,
      body: JSON.stringify({
        p_actor_profile_id: profileId,
        p_thread_id: Number(seedTarget.threadId),
        p_message: fixtureMarker,
        p_file_ids: [],
        p_reply_to_message_id: null,
        p_client_message_id: `shell-nav-local-${report.git.head.slice(0, 24)}`,
      }),
    }, "exact_chat_local_fixture_send");
    const fixtureMessageId = String(sent?.message_id ?? "");
    if (!/^[1-9]\d{0,15}$/.test(fixtureMessageId)) throw new Error("exact_chat_local_fixture_send_invalid");
    await jsonRequest(`${baseUrl}/rest/v1/rpc/quata_chat_set_favorite`, {
      method: "POST",
      headers: authenticatedHeaders,
      body: JSON.stringify({
        p_actor_profile_id: profileId,
        p_thread_id: Number(seedTarget.threadId),
        p_message_id: Number(fixtureMessageId),
        p_favorite: true,
      }),
    }, "exact_chat_local_fixture_favorite");
    favorites = await jsonRequest(`${baseUrl}/rest/v1/rpc/quata_chat_get_favorites_page`, {
      method: "POST",
      headers: authenticatedHeaders,
      body: JSON.stringify({
        p_actor_profile_id: profileId,
        p_limit: 100,
        p_before_created_at: null,
        p_before_message_id: null,
      }),
    }, "exact_chat_local_fixture_verify");
    const verifiedTarget = exactChatTargetFromFavorites(favorites, fixtureMessageId);
    if (!verifiedTarget) throw new Error("exact_chat_local_fixture_not_visible");
    report.steps.push("synthetic_local_chat_fixture_staged_via_product_rpcs");
    return {
      conversationId: `sb:${verifiedTarget.threadId}`,
      messageId: verifiedTarget.messageId,
      marker: fixtureMarker,
    };
  }
  const target = exactChatTargetFromFavorites(favorites);
  if (!target) throw new Error("exact_chat_favorite_target_missing");
  return {
    conversationId: `sb:${target.threadId}`,
    messageId: target.messageId,
  };
}

function exactChatTargetFromFavorites(favorites, requiredMessageId = null) {
  const messages = Array.isArray(favorites)
    ? favorites
    : Array.isArray(favorites?.favorites)
      ? favorites.favorites
      : Array.isArray(favorites?.data?.favorites)
        ? favorites.data.favorites
        : Array.isArray(favorites?.messages)
          ? favorites.messages
          : [];
  return messages
    .map((message) => ({
      messageId: String(message?.id ?? ""),
      threadId: String(message?.thread_id ?? message?.conversation_id ?? ""),
    }))
    .find((candidate) =>
      /^[1-9]\d{0,15}$/.test(candidate.messageId) &&
      /^[1-9]\d{0,15}$/.test(candidate.threadId) &&
      (requiredMessageId === null || candidate.messageId === requiredMessageId)
    );
}

async function jsonRequest(url, init, failure) {
  return jsonRequestNoRedirect(url, init, failure);
}

async function revokeExactChatSession(session) {
  const authCleanup = async () => {
    let accessToken = session.accessToken;
    let refreshToken = session.refreshToken;
    if (!accessToken && refreshToken) {
      const renewal = await jsonRequest(`${session.baseUrl}/auth/v1/token?grant_type=refresh_token`, {
        method: "POST",
        headers: { apikey: session.key, "content-type": "application/json" },
        body: JSON.stringify({ refresh_token: refreshToken }),
      }, "exact_chat_cleanup_refresh");
      accessToken = renewal?.access_token;
      refreshToken = renewal?.refresh_token ?? refreshToken;
    }
    if (!accessToken || !refreshToken) throw new Error("exact_chat_auth_session_custody_incomplete");
    const logout = await fetchNoRedirect(`${session.baseUrl}/auth/v1/logout?scope=local`, {
      method: "POST",
      headers: {
        apikey: session.key,
        authorization: `Bearer ${accessToken}`,
        "content-type": "application/json",
      },
      body: "{}",
    });
    if (!logout.ok) throw new Error(`exact_chat_auth_logout_http_${logout.status}`);
    const verification = await fetchNoRedirect(`${session.baseUrl}/auth/v1/token?grant_type=refresh_token`, {
      method: "POST",
      headers: { apikey: session.key, "content-type": "application/json" },
      body: JSON.stringify({ refresh_token: refreshToken }),
    });
    if (![400, 401].includes(verification.status)) {
      throw new Error(`exact_chat_session_still_refreshable_http_${verification.status}`);
    }
  };
  const webCleanup = async () => {
    if (!session.webSessionToken) return;
    if (!session.accessToken) {
      throw new Error("exact_chat_web_session_custody_incomplete");
    }
    await jsonRequest(`${session.baseUrl}/functions/v1/quata-web-push`, {
      method: "POST",
      headers: {
        apikey: session.key,
        authorization: `Bearer ${session.accessToken}`,
        "content-type": "application/json",
        "x-quata-web-session": session.webSessionToken,
      },
      body: JSON.stringify({ action: "logout" }),
    }, "exact_chat_web_session_logout");
  };
  const [webResult, authResult] = await Promise.allSettled([webCleanup(), authCleanup()]);
  if (webResult.status === "rejected" || authResult.status === "rejected") {
    throw new Error([
      webResult.status === "rejected" ? "web" : null,
      authResult.status === "rejected" ? "auth" : null,
    ].filter(Boolean).join("+") + "_exact_chat_session_cleanup_failed");
  }
}

async function verifyExactChatProcessDeath(target) {
  const selectedResource = `chat.message.${target.messageId}.selected`;
  await waitForResource("chat.conversation.titlebar");
  await waitForResource(selectedResource);
  const activityState = await captureAdb(["shell", "dumpsys", "activity", "activities"], { timeout: 15_000 });
  const baseIntentLine = activityState.split(/\r?\n/).find((line) =>
    /intent/i.test(line) && line.includes(PACKAGE) && line.includes("chat-__favorite_messages__")
  );
  const targetIntentMarker = `message=${encodeURIComponent(target.messageId)}`;
  if (!baseIntentLine || baseIntentLine.includes(targetIntentMarker)) {
    throw new Error("exact_chat_differential_base_intent_not_preserved");
  }
  await captureScreenshot("exact-chat-message-before-process-death");
  report.steps.push("exact_chat_target_selected_by_uiautomator_after_distinct_task_base_intent");
  const pidBefore = await currentPid();
  if (!pidBefore) throw new Error("exact_chat_pid_missing_before_kill");
  await runAdb(["shell", "input", "keyevent", "KEYCODE_HOME"]);
  await delay(1_500);
  await runAdb(["shell", "am", "kill", PACKAGE]);
  await waitForPidAbsent();
  report.steps.push("exact_chat_background_process_killed_by_android_activity_manager");
  await runAdb(["shell", "am", "start", "-W", "-n", `${PACKAGE}/.MainActivity`]);
  await waitForResource("chat.conversation.titlebar");
  await waitForResource(selectedResource);
  const pidAfter = await currentPid();
  if (!pidAfter || pidAfter === pidBefore) throw new Error("exact_chat_process_not_replaced");
  await captureScreenshot("exact-chat-message-after-process-death");
  report.steps.push("exact_chat_conversation_and_message_restored_in_new_process");
  return {
    pidChanged: true,
    exactConversationRestored: true,
    exactMessageRestored: true,
    differentialBaseIntentVerified: true,
    conversationIdSha256: sha256(target.conversationId),
    messageIdSha256: sha256(target.messageId),
  };
}

async function writeDeviceJson(path, value) {
  const child = spawnWithInput(adb, [
    "-s", options.serial, "shell",
    `run-as ${PACKAGE} sh -c 'cat > ${path}'`,
  ]);
  child.stdin.end(`${JSON.stringify(value)}\n`);
  await child.completed;
  if (child.code !== 0) throw new Error("device_private_file_stage_failed");
  await runAdb(["shell", "run-as", PACKAGE, "chmod", "600", path]);
}

function spawnWithInput(command, args) {
  const child = spawn(command, args, { stdio: ["pipe", "pipe", "pipe"], windowsHide: true });
  const output = [];
  const errors = [];
  let code = null;
  child.stdout.on("data", (chunk) => output.push(chunk));
  child.stderr.on("data", (chunk) => errors.push(chunk));
  const completed = new Promise((resolveCompleted, rejectCompleted) => {
    child.once("error", rejectCompleted);
    child.once("close", (exitCode) => { code = exitCode; resolveCompleted(); });
  });
  return { stdin: child.stdin, completed, output, errors, get code() { return code; } };
}

async function run(command, args, extra = {}) {
  return execFileAsync(command, args, { cwd: process.cwd(), windowsHide: true, timeout: extra.timeout ?? 600_000, maxBuffer: 20 * 1024 * 1024 });
}

async function runAdb(args, extra = {}) {
  return run(adb, ["-s", options.serial, ...args], extra);
}

async function captureAdb(args, extra = {}) {
  const { stdout } = await runAdb(args, extra);
  return String(stdout);
}

async function currentPid() {
  const output = await captureAdb([
    "shell", `pidof ${PACKAGE}; status=$?; echo __QUATA_PID_STATUS__:$status`,
  ], { timeout: 10_000 });
  return parsePidObservation(output);
}

async function waitForPidAbsent() {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    if (!(await currentPid())) return;
    await delay(500);
  }
  throw new Error("shell_process_death_process_still_alive");
}

async function verifyPrimaryRootProcessDeath({ route, resource, launchRoute, launchResource }) {
  await runAdb(["shell", "am", "force-stop", PACKAGE]);
  if (await currentPid()) throw new Error(`primary_root_force_stop_failed:${route}`);
  await runAdb([
    "shell", "am", "start", "-W", "-f", "0x10008000", "-n", `${PACKAGE}/.MainActivity`,
    "--ez", "com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", "true",
    "--es", "com.quata.extra.START_DESTINATION_FOR_EVIDENCE", launchRoute,
  ]);
  await waitForResource(launchResource);
  await clickResource(`navigation.primary.${route}`);
  await waitForResource(resource);
  report.steps.push(`${route}_primary_root_selected_from_${launchRoute}_before_process_death`);
  const pidBefore = await currentPid();
  if (!pidBefore) throw new Error(`primary_root_pid_missing_before_kill:${route}`);

  await runAdb(["shell", "input", "keyevent", "KEYCODE_HOME"]);
  await delay(1_500);
  await runAdb(["shell", "am", "kill", PACKAGE]);
  await waitForPidAbsent();
  await runAdb(["shell", "am", "start", "-W", "-n", `${PACKAGE}/.MainActivity`]);
  await waitForResource(resource);
  const pidAfter = await currentPid();
  if (!pidAfter || pidAfter === pidBefore) throw new Error(`primary_root_pid_not_replaced:${route}`);
  await captureScreenshot(`primary-${route}-after-process-death`);
  report.steps.push(`${route}_primary_root_restored_in_new_process`);
}

async function dumpHierarchy() {
  const path = "/sdcard/quata-shell-process-death.xml";
  await runAdb(["shell", "uiautomator", "dump", path], { timeout: 15_000 });
  try { return await captureAdb(["exec-out", "cat", path], { timeout: 10_000 }); }
  finally { await runAdb(["shell", "rm", "-f", path]).catch(() => {}); }
}

function resourceNode(xml, resourceId) {
  const marker = `resource-id=\"${resourceId}\"`;
  return xml.match(/<node\b[^>]*>/g)?.find((node) => node.includes(marker)) ?? null;
}

async function waitForResource(resourceId) {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const xml = await dumpHierarchy();
    if (resourceNode(xml, resourceId)) return;
    await delay(750);
  }
  throw new Error(`resource_not_visible:${resourceId}`);
}

async function waitForResourceAbsent(resourceId) {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    const xml = await dumpHierarchy();
    if (!resourceNode(xml, resourceId)) return;
    await delay(500);
  }
  throw new Error(`resource_still_visible:${resourceId}`);
}

async function clickResource(resourceId) {
  const xml = await dumpHierarchy();
  const node = resourceNode(xml, resourceId);
  if (!node) throw new Error(`resource_not_visible:${resourceId}`);
  const bounds = node.match(/bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"/);
  if (!bounds) throw new Error(`resource_bounds_missing:${resourceId}`);
  const x = Math.floor((Number(bounds[1]) + Number(bounds[3])) / 2);
  const y = Math.floor((Number(bounds[2]) + Number(bounds[4])) / 2);
  await runAdb(["shell", "input", "tap", String(x), String(y)]);
  return {
    clickable: /clickable="true"/.test(node),
    enabled: /enabled="true"/.test(node),
    width: Number(bounds[3]) - Number(bounds[1]),
    height: Number(bounds[4]) - Number(bounds[2]),
  };
}

async function collectSafeUiDiagnostics() {
  const xml = await dumpHierarchy().catch(() => "");
  const resourceIds = [...xml.matchAll(/resource-id="([^"]+)"/g)].map((match) => match[1]);
  const activity = await captureAdb(["shell", "dumpsys", "activity", "activities"], { timeout: 15_000 }).catch(() => "");
  return {
    hierarchyAvailable: xml.length > 0,
    activityStateAvailable: activity.length > 0,
    favoriteMessageCount: resourceIds.filter((id) => id.startsWith("chat.message.") && !id.endsWith(".selected")).length,
    selectedMessageCount: resourceIds.filter((id) => id.startsWith("chat.message.") && id.endsWith(".selected")).length,
    conversationTitleBarVisible: resourceIds.includes("chat.conversation.titlebar"),
    favoritesHeaderVisible: resourceIds.some((id) => id.startsWith("chat.favorites.")),
    conversationsRootVisible: resourceIds.includes("conversations.root"),
    retryControlVisible: resourceIds.some((id) => id.includes("retry")),
    packageResumed: activity.includes(`mResumedActivity`) && activity.includes(PACKAGE),
    favoritesIntentPresent: activity.includes("chat-__favorite_messages__"),
  };
}

async function captureScreenshot(name) {
  const path = resolve(options.screenshotDir, `${name}.png`);
  await mkdir(dirname(path), { recursive: true });
  const { stdout } = await execFileAsync(adb, ["-s", options.serial, "exec-out", "screencap", "-p"], {
    encoding: "buffer", windowsHide: true, timeout: 20_000, maxBuffer: 20 * 1024 * 1024,
  });
  await writeFile(path, stdout, { mode: 0o600 });
  report.screenshots.push(`${name}.png`);
}

async function devicePrivateFileExists(path) {
  const output = (await captureAdb([
    "shell",
    `run-as ${PACKAGE} sh -c 'if [ -e ${path} ]; then echo __QUATA_CREDENTIAL_PRESENT__; else echo __QUATA_CREDENTIAL_ABSENT__; fi'`,
  ], { timeout: 10_000 })).trim();
  if (output === "__QUATA_CREDENTIAL_PRESENT__") return true;
  if (output === "__QUATA_CREDENTIAL_ABSENT__") return false;
  throw new Error("device_credential_observation_invalid");
}

async function gitMetadata() {
  const head = (await execFileAsync("git", ["rev-parse", "HEAD"], { windowsHide: true })).stdout.trim();
  const status = (await execFileAsync("git", ["status", "--porcelain"], { windowsHide: true })).stdout.trim();
  return { head, workingTreeDirty: status.length > 0 };
}

function safeFailure(error) {
  let message = String(error?.message ?? error);
  for (const path of [options.credentialsFile, options.backendConfigFile].filter(Boolean)) {
    message = message.replaceAll(path, "[private-file]");
  }
  message = message.replace(/sb_publishable_[A-Za-z0-9_-]{20,}/g, "[publishable-key]");
  return { message: message.slice(0, 500) };
}

function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

function delay(milliseconds) { return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds)); }
