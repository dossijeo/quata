import { spawn } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";

export function createAccountLifecycleIosTrial({ root, outputDirectory, privateDirectory, host, project,
  simulatorUdid }) {
  let pending = 0;
  let uncertain = false;
  let completedActions = 0;

  async function run({ action, record, password }) {
    if (!["deactivate", "delete"].includes(action) || typeof record?.countryCode !== "string" ||
        typeof record?.phone !== "string" || typeof password !== "string" || password.length < 20) {
      throw new Error("account_lifecycle_ios_input_invalid");
    }
    await mkdir(outputDirectory, { recursive: true });
    await mkdir(privateDirectory, { recursive: true });
    const credentialsFile = path.join(privateDirectory, `ios-${action}-credentials.json`);
    const reportFile = path.join(outputDirectory, `ios-${action}-runner.json`);
    const evidenceDirectory = path.join(outputDirectory, `ios-${action}`);
    const remoteLogDirectory = `build/reports/ios/ACCOUNT-LIFECYCLE-${action}-ui`;
    await writeFile(credentialsFile, `${JSON.stringify({ a: {
      country_code: record.countryCode, phone: record.phone, password,
    } })}\n`, { mode: 0o600 });
    pending += 1;
    try {
      const args = ["scripts/account-postflight-ios-evidence.mjs", "--lifecycle-action", action,
        "--host", host, "--project", project, "--simulator", simulatorUdid,
        "--remote-log-dir", remoteLogDirectory, "--out", reportFile, "--evidence-dir", evidenceDirectory];
      if (completedActions === 0) args.push("--build-first");
      await runChild(process.execPath, args, { cwd: root,
        env: { ...process.env, QUATA_ACCOUNT_POSTFLIGHT_CREDENTIALS_FILE: credentialsFile } });
      completedActions += 1;
      const runner = JSON.parse(await readFile(reportFile, "utf8"));
      const attempt = runner.attempts?.find((entry) => entry.source === `account-lifecycle-${action}`);
      if (runner.status !== "passed" || attempt?.status !== "passed" || attempt.productControlActivations !== 1 ||
          runner.cleanup?.temporaryCredentialsRemoved !== true || runner.cleanup?.runtimeConfigRestored !== true) {
        throw new Error("account_lifecycle_ios_product_result_invalid");
      }
      return { passed: true, action, exactProductActivations: 1, productSessionCleared: true,
        naturalRelaunch: true, platformReport: path.relative(root, reportFile).replaceAll("\\", "/") };
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
    child.once("error", () => reject(new Error("account_lifecycle_ios_process_start_failed")));
    child.once("close", (code) => code === 0 ? resolve() :
      reject(new Error(`account_lifecycle_ios_process_failed:${code}:${redact(tail)}`)));
  });
}

function redact(value) {
  return String(value)
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi,
      "$1[REDACTED]").slice(-2_000);
}
