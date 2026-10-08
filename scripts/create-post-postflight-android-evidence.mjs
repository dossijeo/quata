#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const CHECK = "CREATE-POST-POSTFLIGHT-ANDROID-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const deviceCredentialsFileName = "create-post-postflight-credentials.json";
const deviceCredentialsPath = `app-internal:${deviceCredentialsFileName}`;
const appFilesDir = "files";
const deviceEvidencePath = `${appFilesDir}/create-post-postflight-evidence`;

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  attempts: [],
  evidence: {},
  steps: [],
  cleanup: { appDataCleared: false, localCredentialsRemoved: false },
};

const adb = process.env.ADB?.trim() || "adb";
let localCredentials;

try {
  const credentials = await loadCredentials(options.credentialsFile);
  localCredentials = join("build-reports", "android", `create-post-postflight-credentials-${randomUUID()}.json`);
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
  await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"]);
  report.steps.push("android_debug_and_test_apks_built");

  await run(adb, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
  await run(adb, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  await run(adb, ["shell", "pm", "clear", "com.quata"]);
  report.steps.push("android_app_data_cleared_before_evidence");
  await run(adb, ["shell", "cmd", "package", "compile", "-m", "speed", "-f", "com.quata"]);
  report.steps.push("android_target_apk_precompiled_for_instrumentation");
  await run(adb, ["shell", "run-as", "com.quata", "mkdir", "-p", appFilesDir]);
  await adbRunAsWrite(
    `${appFilesDir}/${deviceCredentialsFileName}`,
    await readFile(localCredentials),
  );
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);

  const draftMarker = `QUATA-DRAFT-ANDROID-${randomUUID()}`;
  const seedOutput = await runCapture(adb, [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.feature.postcomposer.presentation.CreatePostPostflightInstrumentedTest#seedAuthenticatedTextDraftForProcessRestart",
    "-e", "quataCreatePostPostflightCredentialsFile", deviceCredentialsPath,
    "-e", "quataCreatePostPostflightEvidence", "1",
    "-e", "quataCreatePostDraftMarker", draftMarker,
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  const seedAttempt = { source: "draft-seed", outcome: "completed", status: "failed", instrumentationTail: redactedTail(seedOutput) };
  report.attempts.push(seedAttempt);
  requireInstrumentationSuccess(seedOutput, "draft-seed");
  seedAttempt.status = "passed";
  await run(adb, ["shell", "am", "force-stop", "com.quata"]);
  report.steps.push("target_process_force_stopped");

  const instrumentationOutput = await runCapture(adb, [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.feature.postcomposer.presentation.CreatePostPostflightInstrumentedTest#restoreAuthenticatedTextDraftAfterProcessRestartAndDiscard",
    "-e", "quataCreatePostPostflightCredentialsFile", deviceCredentialsPath,
    "-e", "quataCreatePostPostflightEvidence", "1",
    "-e", "quataCreatePostDraftMarker", draftMarker,
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  const attempt = { source: "draft-restore", outcome: "completed", status: "failed", instrumentationTail: redactedTail(instrumentationOutput) };
  report.attempts.push(attempt);
  requireInstrumentationSuccess(instrumentationOutput, "draft-restore");
  attempt.status = "passed";

  const evidenceDir = resolve(options.evidenceDir);
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  await copyDeviceEvidence(evidenceDir);
  await verifyAndroidCreatePostPostflight(evidenceDir);
  report.evidence.directory = evidenceDir;
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? redactedTail(error.message) : String(error);
  await copyDeviceEvidence(resolve(options.evidenceDir)).catch(() => {});
} finally {
  try {
    await run(adb, ["shell", "pm", "clear", "com.quata"]);
    await run(adb, ["shell", "run-as", "com.quata", "test", "!", "-e", `${appFilesDir}/${deviceCredentialsFileName}`]);
    await run(adb, ["shell", "run-as", "com.quata", "test", "!", "-e", deviceEvidencePath]);
    report.cleanup.appDataCleared = true;
  } catch (error) {
    report.cleanup.appDataCleanupError = safeFailure(error);
    report.status = "failed";
  }
  try {
    if (localCredentials) {
      await rm(localCredentials, { force: true });
      await assertMissing(localCredentials);
    }
    report.cleanup.localCredentialsRemoved = true;
  } catch (error) {
    report.cleanup.localCredentialsCleanupError = safeFailure(error);
    report.status = "failed";
  }
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Create Post postflight Android evidence written: ${options.output}`);
}

async function assertMissing(path) {
  try {
    await stat(path);
  } catch (error) {
    if (error?.code === "ENOENT") return;
    throw error;
  }
  throw new Error(`cleanup_path_still_exists:${path}`);
}

if (report.status !== "passed") {
  console.error(`Create Post postflight Android evidence failed: ${report.error?.message ?? report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Create Post postflight Android evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    output: resolve(join("build-reports", "android", "create-post-postflight-evidence.json")),
    evidenceDir: resolve(join("build-reports", "android", "create-post-postflight-evidence")),
    credentialsFile: process.env.QUATA_CREATE_POST_POSTFLIGHT_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE,
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (!["--out", "--evidence-dir", "--credentials-file"].includes(key) || !value || value.startsWith("--")) {
      throw new Error(`invalid_argument:${key}`);
    }
    index += 1;
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
    if (key === "--credentials-file") parsed.credentialsFile = value;
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

async function copyDeviceEvidence(evidenceDir) {
  await mkdir(evidenceDir, { recursive: true });
  const listing = await runCapture(adb, ["shell", "run-as", "com.quata", "ls", deviceEvidencePath]).catch(() => "");
  for (const name of listing.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)) {
    await adbRunAsCat(`${deviceEvidencePath}/${name}`, join(evidenceDir, name)).catch(() => {});
  }
}

async function verifyAndroidCreatePostPostflight(evidenceDir) {
  const platformReportPath = join(evidenceDir, "android-create-post-postflight-evidence.json");
  const platformReport = JSON.parse(await readFile(platformReportPath, "utf8"));
  const expectedSteps = [
    "authenticated_feed_entry_visible",
    "create_post_opened_from_feed_publish_action",
    "common_create_post_types_visible",
    "exclusive_text_draft_entered_without_publish",
    "target_process_force_stopped",
    "exact_text_draft_restored_after_process_restart",
    "restored_draft_explicitly_discarded",
    "restored_draft_persistent_record_cleared_after_discard",
    "create_post_returned_to_feed_without_publish",
    "authenticated_session_preserved_after_relaunch",
  ];
  if (platformReport?.status !== "passed") throw new Error("android_create_post_postflight_platform_report_failed");
  if (platformReport?.publishCallbacksInvoked !== false) throw new Error("android_create_post_postflight_publish_callback_invoked");
  if (platformReport?.sessionPreserved !== true) throw new Error("android_create_post_postflight_session_not_preserved");
  for (const step of expectedSteps) {
    if (!platformReport?.steps?.includes(step)) throw new Error(`android_create_post_postflight_step_missing:${step}`);
  }
  report.steps.push(...expectedSteps);
  report.evidence.platformReport = platformReportPath;
}

function requireInstrumentationSuccess(output, source) {
  if (!/OK \(\d+ tests?\)/.test(output)) throw new Error(`android_instrumentation_not_ok:${source}`);
  if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(output)) {
    throw new Error(`android_instrumentation_semantic_failure:${source}`);
  }
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
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: process.platform === "win32", ...options });
    let output = "";
    child.stdout.on("data", (chunk) => { output += chunk; });
    child.stderr.on("data", (chunk) => { output += chunk; });
    child.on("close", (code) => code === 0 ? resolvePromise(output) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${redactedTail(output)}`)));
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
    child.on("close", (code) => code === 0 ? resolvePromise(output) : reject(new Error(`${command} ${args.join(" ")} failed:${code}\n${redactedTail(output)}`)));
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
