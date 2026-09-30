#!/usr/bin/env node
import { execFile, spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { promisify } from "node:util";
import { parsePidObservation } from "./shell-navigation-android-process-death-utils.mjs";

const execFileAsync = promisify(execFile);
const CHECK = "FLOW-SHELL-NAV-ANDROID-PROCESS-DEATH-001";
const PACKAGE = "com.quata";
const TEST_PACKAGE = "com.quata.test";
const DEVICE_CREDENTIAL = "/data/user/0/com.quata/files/shell-process-death-credentials.json";
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

  if (!options.skipBuild) {
    const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
    await run(gradle, [
      ":app:assembleDebug",
      ":app:assembleDebugAndroidTest",
      ":vosk_model_en:assembleDebug",
      "--console=plain",
    ]);
    report.steps.push("affected_android_apks_built");
  }

  await runAdb(["install-multiple", "-r", "app/build/outputs/apk/debug/app-debug.apk", "vosk_model_en/build/outputs/apk/debug/vosk_model_en-debug.apk"]);
  appTouched = true;
  report.cleanup.environmentAcquired = true;
  await runAdb(["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  report.steps.push("base_split_and_test_apks_installed");

  await runAdb(["shell", "pm", "clear", PACKAGE]);
  await runAdb(["shell", "run-as", PACKAGE, "mkdir", "-p", `/data/user/0/${PACKAGE}/files`]);
  await writeDeviceCredential(credentials);
  report.steps.push("private_credentials_staged_without_logging");

  const instrumentation = await captureAdb([
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.core.navigation.ShellNavigationPolicyInstrumentedTest#authenticateForProcessDeathProbe",
    "-e", "quataShellNavigationCredentialsFile", "app-internal:shell-process-death-credentials.json",
    "-e", "quataShellNavigationProcessDeathEvidence", "1",
    `${TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner`,
  ], { timeout: 60_000 });
  if (!/OK \(1 test\)/.test(instrumentation) || /FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentation)) {
    throw new Error("shell_process_death_authentication_preflight_failed");
  }
  report.steps.push("real_authenticated_session_persisted");

  if (options.exactChatOnly) {
    const target = await findExactChatTarget(credentials);
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
    try {
      report.cleanup.credentialsRemoved = !(await deviceCredentialExists());
    } catch {}
    let clearSucceeded = false;
    try {
      const { stdout } = await runAdb(["shell", "pm", "clear", PACKAGE]);
      clearSucceeded = String(stdout).trim() === "Success";
      report.cleanup.appDataCleared = clearSucceeded
        && !(await deviceCredentialExists())
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

async function findExactChatTarget(credentials) {
  const source = await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8");
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const key = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !key || !key.startsWith("sb_publishable_")) throw new Error("public_backend_configuration_invalid");
  exactChatSession = {
    baseUrl,
    key,
    accessToken: null,
    refreshToken: null,
    webSessionToken: null,
  };
  const auth = await jsonRequest(`${baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: { apikey: key, "content-type": "application/json" },
    body: JSON.stringify({
      action: "web_login",
      country_code: credentials.country_code,
      phone_local: credentials.phone,
      password: credentials.password,
      client_instance_id: `shell-process-death-${Date.now()}`,
    }),
  }, "exact_chat_auth");
  const profileId = auth?.profile?.id;
  const token = auth?.session?.access_token;
  const refreshToken = auth?.session?.refresh_token;
  const webSessionToken = auth?.web_session?.token;
  exactChatSession = {
    ...exactChatSession,
    accessToken: token ?? null,
    refreshToken: refreshToken ?? null,
    webSessionToken: webSessionToken ?? null,
  };
  if (!profileId || !token || !refreshToken || !webSessionToken) throw new Error("exact_chat_auth_response_invalid");
  const favorites = await jsonRequest(`${baseUrl}/rest/v1/rpc/quata_chat_get_favorites`, {
    method: "POST",
    headers: { apikey: key, authorization: `Bearer ${token}`, "content-type": "application/json" },
    body: JSON.stringify({ p_actor_profile_id: profileId, p_limit: 100 }),
  }, "exact_chat_favorites");
  const messages = Array.isArray(favorites)
    ? favorites
    : Array.isArray(favorites?.favorites)
      ? favorites.favorites
      : Array.isArray(favorites?.data?.favorites)
        ? favorites.data.favorites
        : Array.isArray(favorites?.messages)
          ? favorites.messages
          : [];
  const target = messages
    .map((message) => ({
      messageId: String(message?.id ?? ""),
      threadId: String(message?.thread_id ?? message?.conversation_id ?? ""),
    }))
    .find((candidate) =>
      /^[1-9]\d{0,15}$/.test(candidate.messageId) &&
      /^[1-9]\d{0,15}$/.test(candidate.threadId)
    );
  if (!target) throw new Error("exact_chat_favorite_target_missing");
  return {
    conversationId: `sb:${target.threadId}`,
    messageId: target.messageId,
  };
}

async function jsonRequest(url, init, failure) {
  let response;
  try {
    response = await fetch(url, { ...init, signal: AbortSignal.timeout(20_000) });
  } catch {
    throw new Error(`${failure}_network_failed`);
  }
  if (!response.ok) throw new Error(`${failure}_http_${response.status}`);
  try {
    return await response.json();
  } catch {
    throw new Error(`${failure}_invalid_json`);
  }
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
    const logout = await fetch(`${session.baseUrl}/auth/v1/logout?scope=local`, {
      method: "POST",
      headers: {
        apikey: session.key,
        authorization: `Bearer ${accessToken}`,
        "content-type": "application/json",
      },
      body: "{}",
      signal: AbortSignal.timeout(20_000),
    });
    if (!logout.ok) throw new Error(`exact_chat_auth_logout_http_${logout.status}`);
    const verification = await fetch(`${session.baseUrl}/auth/v1/token?grant_type=refresh_token`, {
      method: "POST",
      headers: { apikey: session.key, "content-type": "application/json" },
      body: JSON.stringify({ refresh_token: refreshToken }),
      signal: AbortSignal.timeout(20_000),
    });
    if (![400, 401].includes(verification.status)) {
      throw new Error(`exact_chat_session_still_refreshable_http_${verification.status}`);
    }
  };
  const webCleanup = async () => {
    if (!session.accessToken || !session.webSessionToken) {
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
  const favoritesUrl = "https://egquata.com/#chat-__favorite_messages__";
  const targetIntentMarker = `message=${encodeURIComponent(target.messageId)}`;
  const favoriteResource = `chat.message.${target.messageId}`;
  const selectedResource = `chat.message.${target.messageId}.selected`;
  await runAdb(["shell", "am", "force-stop", PACKAGE]);
  await runAdb(["shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", favoritesUrl, "-p", PACKAGE]);
  await waitForResource(favoriteResource);
  await clickResource(favoriteResource);
  await waitForResource("chat.conversation.titlebar");
  await waitForResource(selectedResource);
  const activityState = await captureAdb(["shell", "dumpsys", "activity", "activities"], { timeout: 15_000 });
  const baseIntentLine = activityState.split(/\r?\n/).find((line) =>
    line.includes("intent={") && line.includes(PACKAGE) && line.includes("chat-__favorite_messages__")
  );
  if (!baseIntentLine || baseIntentLine.includes(targetIntentMarker)) {
    throw new Error("exact_chat_differential_base_intent_not_preserved");
  }
  await captureScreenshot("exact-chat-message-before-process-death");
  report.steps.push("exact_chat_target_selected_after_distinct_task_base_intent");
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

async function writeDeviceCredential(credentials) {
  const child = spawnWithInput(adb, [
    "-s", options.serial, "shell",
    `run-as ${PACKAGE} sh -c 'cat > ${DEVICE_CREDENTIAL}'`,
  ]);
  child.stdin.end(`${JSON.stringify(credentials)}\n`);
  await child.completed;
  if (child.code !== 0) throw new Error("device_credential_stage_failed");
  await runAdb(["shell", "run-as", PACKAGE, "chmod", "600", DEVICE_CREDENTIAL]);
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

async function deviceCredentialExists() {
  const output = (await captureAdb([
    "shell",
    `run-as ${PACKAGE} sh -c 'if [ -e ${DEVICE_CREDENTIAL} ]; then echo __QUATA_CREDENTIAL_PRESENT__; else echo __QUATA_CREDENTIAL_ABSENT__; fi'`,
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
  const message = String(error?.message ?? error).replaceAll(options.credentialsFile, "[credentials-file]");
  return { message: message.slice(0, 500) };
}

function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

function delay(milliseconds) { return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds)); }
