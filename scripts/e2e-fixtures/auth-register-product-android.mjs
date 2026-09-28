import { execFile } from "node:child_process";
import { access, readFile } from "node:fs/promises";
import { resolve } from "node:path";

const APPLICATION_ID = "com.quata";
const TEST_APPLICATION_ID = "com.quata.test";
const TEST_CLASS = "com.quata.feature.auth.presentation.AuthRegisterRealInstrumentedTest";
const INPUT_FILE = "files/auth-register-product-input.json";
const RESULT_FILE = "files/auth-register-product-result.json";

export function createRegistrationAndroidTrial({ adb = "adb", root = process.cwd(), buildEnvironment = process.env } = {}) {
  let serial;
  let running = false;
  let settled = true;

  return Object.freeze({
    async prepare() {
      serial = await selectAndroidDevice(adb, buildEnvironment.QUATA_ANDROID_DEVICE_SERIAL);
      const gradle = process.platform === "win32" ? resolve(root, "gradlew.bat") : resolve(root, "gradlew");
      await command(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--no-daemon"], {
        cwd: root,
        env: buildEnvironment,
        timeout: 12 * 60_000,
      });
      const appApk = resolve(root, "app/build/outputs/apk/debug/app-debug.apk");
      const testApk = resolve(root, "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk");
      await Promise.all([access(appApk), access(testApk)]);
      await command(adb, ["-s", serial, "install", "-r", "-t", appApk], { timeout: 120_000 });
      await command(adb, ["-s", serial, "install", "-r", "-t", testApk], { timeout: 120_000 });
    },
    async run({ channel, input }) {
      if (channel !== "android" || running) throw new Error("registration_product_android_state_invalid");
      running = true;
      settled = false;
      try {
        await privateWrite(adb, serial, INPUT_FILE, JSON.stringify(input));
        const instrumentation = await command(adb, [
          "-s", serial, "shell", "am", "instrument", "-w", "-r",
          "-e", "class", TEST_CLASS,
          `${TEST_APPLICATION_ID}/androidx.test.runner.AndroidJUnitRunner`,
        ], { timeout: 4 * 60_000 });
        const report = JSON.parse(await privateRead(adb, serial, RESULT_FILE));
        await privateRemove(adb, serial, RESULT_FILE);
        if (!/OK \(1 test\)/.test(instrumentation) || report.passed !== true) {
          const stage = String(report.failureStage ?? "instrumentation").replace(/[^a-z0-9_-]/g, "").slice(0, 64);
          throw new Error(`registration_product_android_${stage || "instrumentation"}_failed`);
        }
        return report;
      } finally {
        await privateRemove(adb, serial, INPUT_FILE).catch(() => {});
        await command(adb, ["-s", serial, "shell", "am", "force-stop", APPLICATION_ID], { timeout: 30_000 }).catch(() => {});
        running = false;
        settled = true;
      }
    },
    operationsSettled() { return settled && !running; },
    async close() {
      if (!serial) return;
      await privateRemove(adb, serial, INPUT_FILE).catch(() => {});
      await privateRemove(adb, serial, RESULT_FILE).catch(() => {});
      await command(adb, ["-s", serial, "shell", "am", "force-stop", APPLICATION_ID], { timeout: 30_000 }).catch(() => {});
    },
  });
}

export async function selectAndroidDevice(adb, requestedSerial) {
  const output = await command(adb, ["devices"], { timeout: 30_000 });
  const devices = output.split(/\r?\n/).map((line) => /^(\S+)\s+device$/.exec(line)?.[1]).filter(Boolean);
  if (requestedSerial) {
    if (!devices.includes(requestedSerial)) throw new Error("registration_product_android_device_unavailable");
    return requestedSerial;
  }
  if (devices.length !== 1) throw new Error("registration_product_android_device_ambiguous");
  return devices[0];
}

async function privateWrite(adb, serial, path, value) {
  await command(adb, ["-s", serial, "shell", "run-as", APPLICATION_ID, "mkdir", "-p", "files"], { timeout: 30_000 });
  await command(adb, ["-s", serial, "shell", "run-as", APPLICATION_ID, "tee", path], {
    input: value,
    timeout: 30_000,
  });
}

async function privateRead(adb, serial, path) {
  return command(adb, ["-s", serial, "shell", "run-as", APPLICATION_ID, "cat", path], { timeout: 30_000 });
}

async function privateRemove(adb, serial, path) {
  await command(adb, ["-s", serial, "shell", "run-as", APPLICATION_ID, "rm", "-f", path], { timeout: 30_000 });
}

function command(executable, args, { cwd, env, input, timeout = 60_000 } = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = execFile(executable, args, {
      cwd,
      env,
      windowsHide: true,
      timeout,
      killSignal: "SIGKILL",
      maxBuffer: 8 * 1024 * 1024,
    }, (error, stdout) => {
      if (error) reject(new Error("registration_product_android_command_failed"));
      else resolvePromise(stdout);
    });
    if (input !== undefined) child.stdin.end(input);
  });
}
