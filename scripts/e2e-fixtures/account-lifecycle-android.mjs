import { spawn } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";

export function createAccountLifecycleAndroidTrial({ root, outputDirectory, privateDirectory, adb = "adb" }) {
  let pending = 0;
  let uncertain = false;
  let completedActions = 0;

  async function run({ action, record, password }) {
    if (!["deactivate", "delete"].includes(action) || typeof record?.countryCode !== "string" ||
        typeof record?.phone !== "string" || typeof password !== "string" || password.length < 20) {
      throw new Error("account_lifecycle_android_input_invalid");
    }
    await mkdir(outputDirectory, { recursive: true });
    await mkdir(privateDirectory, { recursive: true });
    const credentialsFile = path.join(privateDirectory, `android-${action}-credentials.json`);
    const reportFile = path.join(outputDirectory, `android-${action}-runner.json`);
    const evidenceDirectory = path.join(outputDirectory, `android-${action}`);
    await writeFile(credentialsFile, `${JSON.stringify({ a: {
      country_code: record.countryCode, phone: record.phone, password,
    } })}\n`, { mode: 0o600 });
    pending += 1;
    try {
      const args = ["scripts/account-postflight-android-evidence.mjs", "--lifecycle-action", action,
        "--credentials-file", credentialsFile, "--out", reportFile, "--evidence-dir", evidenceDirectory];
      if (completedActions > 0) args.push("--skip-build");
      await runChild(process.execPath, args, { cwd: root, env: { ...process.env, ADB: adb } });
      completedActions += 1;
      const runner = JSON.parse(await readFile(reportFile, "utf8"));
      const platformPath = path.join(evidenceDirectory, `android-account-lifecycle-${action}-evidence.json`);
      const platform = JSON.parse(await readFile(platformPath, "utf8"));
      if (runner.status !== "passed" || platform.status !== "passed" || platform.action !== action ||
          platform.productControlActivations !== 1 || platform.sessionCleared !== true) {
        throw new Error("account_lifecycle_android_product_result_invalid");
      }
      return { passed: true, action, exactProductActivations: 1, productSessionCleared: true,
        platformReport: path.relative(root, platformPath).replaceAll("\\", "/") };
    } finally {
      pending -= 1;
      await rm(credentialsFile, { force: true }).catch(() => { uncertain = true; });
    }
  }

  return Object.freeze({
    run,
    operationsSettled: () => pending === 0 && !uncertain,
    async close() {},
  });
}

function runChild(command, args, options) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { ...options, stdio: ["ignore", "pipe", "pipe"], windowsHide: true });
    let tail = "";
    const collect = (chunk) => { tail = `${tail}${chunk}`.split(/\r?\n/).slice(-80).join("\n"); };
    child.stdout.on("data", collect);
    child.stderr.on("data", collect);
    child.once("error", () => reject(new Error("account_lifecycle_android_process_start_failed")));
    child.once("close", (code) => code === 0 ? resolve() :
      reject(new Error(`account_lifecycle_android_process_failed:${code}:${redact(tail)}`)));
  });
}

function redact(value) {
  return String(value)
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi,
      "$1[REDACTED]").slice(-2_000);
}
