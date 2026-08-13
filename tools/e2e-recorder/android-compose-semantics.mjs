#!/usr/bin/env node
import { execFile } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const options = parseArgs(process.argv.slice(2));
const adb = options.adb ?? process.env.ADB ?? "adb";
const gradle = options.gradle ?? (process.platform === "win32" ? "gradlew.bat" : "./gradlew");
const out = options.out ?? "build-reports/e2e-recorder/android-compose-semantics.json";
const devicePath = "/sdcard/Android/data/com.quata/files/e2e-recorder/android-compose-semantics.json";

if (!options["skip-build"]) {
  await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--no-daemon"], { cwd: path.resolve(".") });
}

await run(adb, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
await run(adb, ["install", "-r", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
await run(adb, ["shell", "rm", "-f", devicePath]).catch(() => {});
await run(adb, [
  "shell",
  "am",
  "instrument",
  "-w",
  "-e",
  "class",
  "com.quata.tools.e2erecorder.E2eRecorderSemanticsExportInstrumentedTest#exportsWhatsNewSemanticsForRecorder",
  "-e",
  "quataE2eRecorderOut",
  "android-compose-semantics.json",
  "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
]);
const { stdout } = await execFileAsync(adb, ["exec-out", "cat", devicePath], { encoding: "utf8", maxBuffer: 20_000_000 });
if (!/"source"\s*:\s*"compose-semantics"/.test(stdout)) {
  throw new Error("android_compose_semantics_export_failed");
}
await mkdir(path.dirname(out), { recursive: true });
await writeFile(out, stdout.endsWith("\n") ? stdout : `${stdout}\n`, "utf8");
console.log(JSON.stringify({ out, source: "compose-semantics" }, null, 2));

async function run(command, args, options = {}) {
  const { stdout, stderr } = await execFileAsync(command, args, {
    encoding: "utf8",
    maxBuffer: 30_000_000,
    ...options,
  });
  if (stdout.trim()) process.stdout.write(stdout);
  if (stderr.trim()) process.stderr.write(stderr);
}

function parseArgs(args) {
  const parsed = {};
  for (let i = 0; i < args.length; i += 1) {
    if (!args[i].startsWith("--")) continue;
    parsed[args[i].slice(2)] = args[i + 1]?.startsWith("--") ? "true" : args[++i] ?? "true";
  }
  return parsed;
}
