#!/usr/bin/env node
import { execFile } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";
import { uiAutomatorXmlToTree } from "./lib/platform-probes.mjs";

const execFileAsync = promisify(execFile);
const options = parseArgs(process.argv.slice(2));
if (!options.out) {
  console.error("Usage: node tools/e2e-recorder/android-dump-tree.mjs --out <tree.json> [--adb <adb>] [--raw-xml <window.xml>]");
  process.exit(64);
}

const adb = options.adb ?? process.env.ADB ?? "adb";
let xml;
if (options["raw-xml"]) {
  xml = await import("node:fs/promises").then((fs) => fs.readFile(options["raw-xml"], "utf8"));
} else {
  await adbText(adb, ["shell", "rm", "-f", "/sdcard/quata-window.xml"]).catch(() => {});
  const dumpOutput = await adbText(adb, ["shell", "uiautomator", "dump", "/sdcard/quata-window.xml"]);
  if (/ERROR|null root node|exception|failed/i.test(dumpOutput)) {
    throw new Error(`android_uiautomator_dump_failed:${dumpOutput.slice(0, 200)}`);
  }
  xml = await adbText(adb, ["exec-out", "cat", "/sdcard/quata-window.xml"]);
}

if (!/<hierarchy\b/.test(xml)) {
  throw new Error("android_uiautomator_dump_failed: missing hierarchy");
}

const tree = {
  ...uiAutomatorXmlToTree(xml),
  capturedAt: new Date().toISOString(),
  device: await adbText(adb, ["get-serialno"]).catch(() => null),
};
await mkdir(path.dirname(options.out), { recursive: true });
await writeFile(options.out, `${JSON.stringify(tree, null, 2)}\n`, "utf8");
console.log(JSON.stringify({ out: options.out, nodes: tree.children.length }, null, 2));

async function adbText(adb, args) {
  const { stdout } = await execFileAsync(adb, args, { encoding: "utf8", maxBuffer: 20_000_000 });
  return stdout.trim();
}

function parseArgs(args) {
  const parsed = {};
  for (let i = 0; i < args.length; i += 1) {
    if (!args[i].startsWith("--")) continue;
    parsed[args[i].slice(2)] = args[i + 1]?.startsWith("--") ? "true" : args[++i] ?? "true";
  }
  return parsed;
}
