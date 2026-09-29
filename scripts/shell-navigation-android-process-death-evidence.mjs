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
const options = parseArgs(process.argv.slice(2));
const adb = process.env.ADB?.trim() || "adb";
let appTouched = false;
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  serialSha256: sha256(options.serial),
  steps: [],
  screenshots: [],
  cleanup: { state: "pending", environmentAcquired: false, credentialsRemoved: false, appDataCleared: false },
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
  report.process = { pidChanged: true };
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
} finally {
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
    report.cleanup.state = report.cleanup.credentialsRemoved && report.cleanup.appDataCleared ? "completed" : "failed";
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
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    if (key === "--skip-build") { parsed.skipBuild = true; continue; }
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
