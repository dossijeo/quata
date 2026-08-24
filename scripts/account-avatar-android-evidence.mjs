#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { assertStorageObjectAbsent } from "./e2e-fixtures/supabase-storage-cleanup.mjs";

const CHECK = "ACCOUNT-AVATAR-ANDROID-REAL-001";
const DEFAULT_CREDENTIALS_FILE = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const deviceCredentialsPath = "app-internal:account-avatar-credentials.json";
const deviceTempCredentialsPath = "/data/local/tmp/account-avatar-credentials.json";
const deviceEvidencePath = "files/account-avatar-evidence";

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
  localCredentials = join("build-reports", "android", `account-avatar-credentials-${randomUUID()}.json`);
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
  await run(adb, ["push", localCredentials, deviceTempCredentialsPath]);
  await run(adb, ["shell", "chmod", "644", deviceTempCredentialsPath]);
  await run(adb, ["shell", "run-as", "com.quata", "mkdir", "-p", "files"]);
  await run(adb, ["shell", "run-as", "com.quata", "cp", deviceTempCredentialsPath, `files/${deviceCredentialsPath.replace("app-internal:", "")}`]);
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]);
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);

  const instrumentationOutput = await runCapture(adb, [
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", "com.quata.feature.profile.presentation.ProfileAvatarRealInstrumentedTest#authenticatedUserChangesProfileAvatarFromCommonAccount",
    "-e", "quataAccountAvatarCredentialsFile", deviceCredentialsPath,
    "-e", "quataAccountAvatarEvidence", "1",
    "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
  ]);
  const attempt = { source: "profile-gallery-avatar", outcome: "success", instrumentationTail: redactedTail(instrumentationOutput) };
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
  report.evidence.directory = evidenceDir;
  await verifyAndroidPhysicalCleanup(evidenceDir);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.errorDetail = typeof error?.message === "string" ? error.message : String(error);
  await copyDeviceEvidence(resolve(options.evidenceDir)).catch(() => {});
} finally {
  await run(adb, ["shell", "rm", "-f", deviceTempCredentialsPath]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-f", `files/${deviceCredentialsPath.replace("app-internal:", "")}`]).catch(() => {});
  await run(adb, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]).catch(() => {});
  await rm(localCredentials ?? "", { force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Account avatar Android evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Account avatar Android evidence failed: ${report.error?.message ?? report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Account avatar Android evidence passed.");
}

function parseArgs(args) {
  const parsed = {
    output: resolve(join("build-reports", "android", "account-avatar-evidence.json")),
    evidenceDir: resolve(join("build-reports", "android", "account-avatar-evidence")),
    credentialsFile: process.env.QUATA_ACCOUNT_AVATAR_CREDENTIALS_FILE?.trim() || DEFAULT_CREDENTIALS_FILE,
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

async function verifyAndroidPhysicalCleanup(evidenceDir) {
  const platformReportPath = join(evidenceDir, "android-account-avatar-evidence.json");
  const platformReport = JSON.parse(await readFile(platformReportPath, "utf8"));
  const storagePath = platformReport?.cleanup?.storagePath;
  if (!storagePath) throw new Error("android_account_avatar_cleanup_storage_path_missing");
  const physicalResidue = await assertStorageObjectAbsent({ storagePath });
  platformReport.cleanup.physicalResidue = physicalResidue;
  platformReport.cleanup.storagePhysicallyAbsent = physicalResidue === 0;
  await writeFile(platformReportPath, `${JSON.stringify(platformReport, null, 2)}\n`, { mode: 0o600 });
  report.accountAvatarSteps = Array.isArray(platformReport.accountAvatarSteps) ? platformReport.accountAvatarSteps : [];
  report.cleanup = {
    attempted: true,
    profileRestored: platformReport.cleanup.profileRestored === true,
    storageDeleted: platformReport.cleanup.storageDeleted === true,
    physicalResidue,
    storagePath,
  };
  if (report.cleanup.profileRestored !== true || report.cleanup.storageDeleted !== true || physicalResidue !== 0) {
    throw new Error("android_account_avatar_cleanup_not_verified");
  }
}

async function adbRunAsCat(devicePath, localPath) {
  const output = await runBuffer(adb, ["exec-out", "run-as", "com.quata", "cat", devicePath]);
  await writeFile(localPath, output);
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
