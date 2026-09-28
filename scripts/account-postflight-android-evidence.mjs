#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const LOGOUT_MODE = process.argv.slice(2).includes("--logout");
const LIFECYCLE_ACTION = argumentValue(process.argv.slice(2), "--lifecycle-action");
if (LIFECYCLE_ACTION != null && !["deactivate", "delete"].includes(LIFECYCLE_ACTION)) {
  throw new Error("invalid_lifecycle_action");
}
const LIFECYCLE_MODE = LIFECYCLE_ACTION != null;
const CHECK = LOGOUT_MODE ? "AUTH-LOGOUT-ANDROID-001"
  : LIFECYCLE_MODE ? "ACCOUNT-LIFECYCLE-ANDROID-REAL-001" : "ACCOUNT-POSTFLIGHT-ANDROID-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const deviceCredentialsFileName = "account-postflight-credentials.json";
const deviceCredentialsPath = `app-internal:${deviceCredentialsFileName}`;
const appFilesDir = "files";
const deviceEvidencePath = `${appFilesDir}/account-postflight-evidence`;
const devicePrivateLogoutPath = `${appFilesDir}/account-postflight-private/android-auth-logout-private.json`;

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  attempts: [],
  evidence: {},
  steps: [],
};

const adb = process.env.ADB?.trim() || "adb";
let localCredentials;

try {
  const credentials = await loadCredentials(options.credentialsFile);
  checkpoint("credentials_loaded");
  localCredentials = join("build-reports", "android", `account-postflight-credentials-${randomUUID()}.json`);
  await mkdir(dirname(localCredentials), { recursive: true });
  await writeFile(
    localCredentials,
    `${JSON.stringify({
      country_code: credentials.country_code,
      phone: credentials.phone,
      password: credentials.password,
    })}\n`,
    { mode: 0o600 },
  );

  const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  if (!options.skipBuild) {
    await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"]);
    report.steps.push("android_debug_and_test_apks_built");
    checkpoint("apks_built");
    await run(adb, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
    checkpoint("target_installed");
    await run(adb, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
    checkpoint("test_installed");
  }
  if (LOGOUT_MODE || LIFECYCLE_MODE) {
    await run(adb, ["shell", "pm", "grant", "com.quata", "android.permission.POST_NOTIFICATIONS"]);
    report.steps.push("android_notification_permission_pregranted_for_logout_evidence");
    checkpoint("notification_permission_granted");
    // A freshly installed debug APK can exceed the emulator's startup watchdog while ART compiles
    // it at the same time as instrumentation attaches. Warm and AOT-compile exactly the app/test
    // processes first so the focal test measures logout rather than that one-time emulator cost.
    await run(adb, ["shell", "am", "start", "-W", "-n", "com.quata/.MainActivity"]);
    await delay(5_000);
    await run(adb, ["shell", "am", "force-stop", "com.quata"]);
    await run(adb, ["shell", "cmd", "package", "compile", "-m", "speed", "-f", "com.quata"]);
    await run(adb, ["shell", "cmd", "package", "compile", "-m", "speed", "-f", "com.quata.test"]);
    report.steps.push(LIFECYCLE_MODE
      ? "android_app_warmed_before_lifecycle_instrumentation"
      : "android_app_warmed_before_logout_instrumentation");
    report.steps.push("android_target_apk_precompiled_for_instrumentation");
    report.steps.push("android_test_apk_precompiled_for_instrumentation");
    checkpoint("target_warmed");
  }
  await run(adb, ["shell", "run-as", "com.quata", "mkdir", "-p", appFilesDir]);
  await adbRunAsWrite(
    `${appFilesDir}/${deviceCredentialsFileName}`,
    await readFile(localCredentials),
  );
  checkpoint("credentials_staged");
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);

  const testMethod = LOGOUT_MODE ? "authenticatedLogoutReturnsToPublicFeedAndClearsOwnedSession"
    : LIFECYCLE_MODE ? "authenticatedAccountLifecycleActionExecutesFromProductUi"
      : "authenticatedAccountRootNavigatesAndCancelsLifecycleActions";
  const evidenceOptIn = LOGOUT_MODE ? "quataAuthLogoutEvidence"
    : LIFECYCLE_MODE ? "quataAccountLifecycleEvidence" : "quataAccountPostflightEvidence";
  const instrumentationArgs = [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", `com.quata.feature.profile.presentation.ProfilePostflightInstrumentedTest#${testMethod}`,
    "-e", "quataAccountPostflightCredentialsFile", deviceCredentialsPath,
    "-e", evidenceOptIn, "1",
  ];
  if (LIFECYCLE_MODE) instrumentationArgs.push("-e", "quataAccountLifecycleAction", LIFECYCLE_ACTION);
  instrumentationArgs.push("com.quata.test/androidx.test.runner.AndroidJUnitRunner");
  const instrumentationOutput = await runCapture(adb, instrumentationArgs);
  checkpoint("instrumentation_returned");
  const attempt = { source: LOGOUT_MODE ? "auth-logout-postflight"
    : LIFECYCLE_MODE ? `account-lifecycle-${LIFECYCLE_ACTION}` : "profile-account-postflight",
  outcome: "success", instrumentationTail: redactedTail(instrumentationOutput) };
  if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) {
    report.attempts.push({ ...attempt, status: "failed" });
    throw new Error("android_instrumentation_not_ok");
  }
  if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentationOutput)) {
    report.attempts.push({ ...attempt, status: "failed" });
    throw new Error("android_instrumentation_semantic_failure");
  }
  report.attempts.push({ ...attempt, status: "passed" });

  const evidenceDir = resolve(options.evidenceDir);
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  await copyDeviceEvidence(evidenceDir);
  await verifyAndroidPostflight(evidenceDir, { logoutMode: LOGOUT_MODE, lifecycleAction: LIFECYCLE_ACTION });
  if (LOGOUT_MODE) {
    const privateReceipt = await runBuffer(adb, ["exec-out", "run-as", "com.quata", "cat", devicePrivateLogoutPath]);
    const remoteOutput = await runWithInput(options.python, [
      "scripts/android-logout-remote-verification.py",
      "--db-url-file", options.dbUrlFile,
      "--db-ca-file", options.dbCaFile,
    ], privateReceipt);
    const remote = JSON.parse(remoteOutput);
    if (remote?.status !== "passed" || remote.exactAuthSessionRevoked !== true ||
        remote.exactPushTokenDisabled !== true || remote.noActiveExactPushToken !== true) {
      throw new Error("android_auth_logout_remote_effects_unverified");
    }
    const remoteReportPath = join(evidenceDir, "android-auth-logout-remote.json");
    await writeFile(remoteReportPath, `${JSON.stringify(remote, null, 2)}\n`);
    report.steps.push(
      "exact_auth_session_revoked_remotely",
      "exact_android_push_token_disabled_on_logout",
      "no_active_android_push_token_after_logout",
    );
    report.evidence.remoteReport = remoteReportPath;
    checkpoint("remote_effects_verified");
  }
  checkpoint("evidence_verified");
  report.evidence.directory = evidenceDir;
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? redactedTail(error.message) : String(error);
  await copyDeviceEvidence(resolve(options.evidenceDir)).catch(() => {});
} finally {
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-f", `${appFilesDir}/${deviceCredentialsFileName}`]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", `${appFilesDir}/account-postflight-private`]).catch(() => {});
  await rm(localCredentials ?? "", { force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Account postflight Android evidence written: ${options.output}`);
}

function checkpoint(name) {
  console.log(`[account-postflight] ${name}`);
}

function delay(milliseconds) {
  return new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
}

if (report.status !== "passed") {
  console.error(`Account postflight Android evidence failed: ${report.error?.message ?? report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Account postflight Android evidence passed.");
}

function parseArgs(args) {
  const suffix = LOGOUT_MODE ? "auth-login-logout" : LIFECYCLE_MODE ? `account-lifecycle-${LIFECYCLE_ACTION}` : "account-postflight";
  const parsed = {
    output: resolve(join("build-reports", "android", `${suffix}-evidence.json`)),
    evidenceDir: resolve(join("build-reports", "android", `${suffix}-evidence`)),
    credentialsFile: process.env.QUATA_ACCOUNT_POSTFLIGHT_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE,
    skipBuild: args.includes("--skip-build"),
    dbUrlFile: process.env.QUATA_SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbCaFile: process.env.QUATA_SUPABASE_DB_CA_FILE?.trim() || DEFAULT_DB_CA_FILE,
    python: process.env.QUATA_PYTHON?.trim() || "python",
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    if (["--logout", "--skip-build"].includes(key)) continue;
    const value = args[index + 1];
    if (!["--out", "--evidence-dir", "--credentials-file", "--lifecycle-action", "--db-url-file", "--db-ca-file", "--python"].includes(key) || !value || value.startsWith("--")) {
      throw new Error(`invalid_argument:${key}`);
    }
    index += 1;
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
    if (key === "--credentials-file") parsed.credentialsFile = value;
    if (key === "--db-url-file") parsed.dbUrlFile = value;
    if (key === "--db-ca-file") parsed.dbCaFile = value;
    if (key === "--python") parsed.python = value;
  }
  return parsed;
}

async function loadCredentials(path) {
  const credentials = JSON.parse(await readFile(path, "utf8"));
  for (const field of ["country_code", "phone", "password"]) {
    if (!credentials?.a?.[field]) throw new Error(`credentials_missing:a.${field}`);
  }
  return credentials.a;
}

function argumentValue(args, name) {
  const index = args.indexOf(name);
  return index === -1 ? null : args[index + 1];
}

async function copyDeviceEvidence(evidenceDir) {
  await mkdir(evidenceDir, { recursive: true });
  const listing = await runCapture(adb, ["shell", "run-as", "com.quata", "ls", deviceEvidencePath]).catch(() => "");
  for (const name of listing.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)) {
    await adbRunAsCat(`${deviceEvidencePath}/${name}`, join(evidenceDir, name)).catch(() => {});
  }
}

async function verifyAndroidPostflight(evidenceDir, { logoutMode, lifecycleAction }) {
  const platformReportPath = join(evidenceDir, logoutMode ? "android-auth-logout-evidence.json"
    : lifecycleAction ? `android-account-lifecycle-${lifecycleAction}-evidence.json`
      : "android-account-postflight-evidence.json");
  const platformReport = JSON.parse(await readFile(platformReportPath, "utf8"));
  const expectedSteps = lifecycleAction ? [
    "authenticated_owned_synthetic_actor_logged_in",
    "shared_account_management_opened",
    "profile_danger_confirmation_accepted_once",
    "shared_password_confirmation_completed",
    "lifecycle_confirm_activated_once",
    "owned_session_cleared_after_success",
    "public_feed_visible_after_relaunch",
  ] : logoutMode ? [
    "owned_push_token_registered_before_logout",
    "authenticated_profile_logout_control_activated",
    "public_feed_visible_after_logout",
    "owned_session_absent_after_logout",
    "owned_session_absent_after_relaunch",
  ] : [
    "account_overview_shared_entries_visible",
    "account_details_opened_and_returned",
    "account_deactivate_confirmation_cancelled",
    "account_delete_confirmation_cancelled",
    "account_management_returned_to_overview",
  ];
  if (platformReport?.status !== "passed") throw new Error("android_account_postflight_platform_report_failed");
  if (lifecycleAction) {
    if (platformReport?.action !== lifecycleAction || platformReport?.sessionCleared !== true ||
        platformReport?.productControlActivations !== 1) throw new Error("android_account_lifecycle_product_result_invalid");
  } else if (logoutMode) {
    if (platformReport?.sessionCleared !== true) throw new Error("android_auth_logout_session_not_cleared");
  } else {
    if (platformReport?.destructiveCallbacksInvoked !== false) throw new Error("android_account_postflight_destructive_callback_invoked");
    if (platformReport?.sessionPreserved !== true) throw new Error("android_account_postflight_session_not_preserved");
  }
  for (const step of expectedSteps) {
    if (!platformReport?.steps?.includes(step)) throw new Error(`android_account_postflight_step_missing:${step}`);
  }
  report.steps.push(...expectedSteps);
  report.evidence.platformReport = platformReportPath;
}

async function adbRunAsCat(devicePath, localPath) {
  const output = await runBuffer(adb, ["exec-out", "run-as", "com.quata", "cat", devicePath]);
  await writeFile(localPath, output);
}

async function adbRunAsWrite(devicePath, bytes) {
  await runWithInput(adb, [
    "shell", "run-as", "com.quata", "dd", `of=${devicePath}`, "bs=4096",
  ], bytes);
  await run(adb, ["shell", "run-as", "com.quata", "chmod", "600", devicePath]);
}

async function gitMetadata() {
  const head = (await runCapture("git", ["rev-parse", "HEAD"])).trim();
  const branch = (await runCapture("git", ["branch", "--show-current"])).trim();
  const status = await runCapture("git", ["status", "--porcelain"]);
  return { head, branch, workingTreeDirty: status.trim().length > 0 };
}

function run(command, args, options = {}) {
  return runCapture(command, args, options).then(() => undefined);
}

function runCapture(command, args, options = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, {
      stdio: ["ignore", "pipe", "pipe"],
      shell: process.platform === "win32" && /(?:^|[\\/])[^\\/]+\.bat$/i.test(command),
      ...options,
    });
    let output = "";
    let settled = false;
    child.stdout.on("data", (chunk) => { output += chunk; });
    child.stderr.on("data", (chunk) => { output += chunk; });
    const finish = (code) => {
      if (settled) return;
      settled = true;
      code === 0 ? resolvePromise(output) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${redactedTail(output)}`));
    };
    child.on("close", finish);
    // On Windows, adb/cmd descendants can retain inherited pipe handles after the direct child has
    // exited. Do not leave an otherwise completed evidence run waiting forever for `close`.
    child.on("exit", (code) => setTimeout(() => finish(code), 250));
  });
}

function runBuffer(command, args, options = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32", ...options });
    const chunks = [];
    let stderr = "";
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("close", (code) => code === 0 ? resolvePromise(Buffer.concat(chunks)) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${redactedTail(stderr)}`)));
  });
}

function runWithInput(command, args, input, options = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: ["pipe", "pipe", "pipe"], shell: false, ...options });
    let output = "";
    child.stdout.on("data", (chunk) => { output += chunk; });
    child.stderr.on("data", (chunk) => { output += chunk; });
    child.on("close", (code) => code === 0 ? resolvePromise(output) : reject(new Error(`private_verifier_failed:${code}\n${redactedTail(output)}`)));
    child.stdin.end(input);
  });
}

function safeFailure(error) {
  return {
    name: error?.name ?? "Error",
    message: redactedTail(error?.message ?? String(error)).slice(0, 500),
  };
}

function redactedTail(value) {
  return String(value)
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .split(/\r?\n/)
    .slice(-80)
    .join("\n");
}
