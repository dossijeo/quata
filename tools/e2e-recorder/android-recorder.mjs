#!/usr/bin/env node
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { createMacro, normalizeTarget, writeMacro } from "./lib/macro-core.mjs";

const execFileAsync = promisify(execFile);
const options = parseArgs(process.argv.slice(2));
if (!options.flow || !options.out || !options.tap) {
  console.error("Usage: node tools/e2e-recorder/android-recorder.mjs --flow <name> --out <macro.json> --tap x,y [--adb <adb>]");
  process.exit(64);
}

const adb = options.adb ?? process.env.ADB ?? "adb";
const packageName = options.package ?? "com.quata";
const macro = createMacro({ flow: options.flow, platform: "android", device: await adbText(adb, ["get-serialno"]).catch(() => null) });
const evidenceDir = options.evidenceDir ?? path.join(path.dirname(options.out), `${options.flow}-android-evidence`);
await mkdir(evidenceDir, { recursive: true });

for (const tap of String(options.tap).split(";")) {
  const [x, y] = tap.split(",").map(Number);
  const before = path.join(evidenceDir, `${macro.flow}-${macro.steps.length}-before.png`);
  const after = path.join(evidenceDir, `${macro.flow}-${macro.steps.length}-after.png`);
  await screencap(adb, before).catch(() => {});
  const nodes = await dumpUi(adb);
  const node = deepestNodeAt(nodes, x, y);
  const target = normalizeTarget({
    resourceId: node?.resourceId,
    contentDescription: node?.contentDescription,
    visibleText: node?.text,
    roleName: node?.className,
    packageName: node?.packageName,
    externalApp: Boolean(node?.packageName && node.packageName !== packageName),
    bounds: node?.bounds,
    coordinates: { x, y },
    relativeBounds: node?.bounds ? relativeBounds(node.bounds) : null,
  });
  await adbText(adb, ["shell", "input", "tap", String(x), String(y)]);
  await new Promise((resolve) => setTimeout(resolve, 500));
  await screencap(adb, after).catch(() => {});
  macro.steps.push({
    timestamp: new Date().toISOString(),
    platform: "android",
    action: "tap",
    coordinates: { x, y },
    target,
    screenshotBefore: before,
    screenshotAfter: after,
    observableAfter: { nodeText: node?.text ?? null, nodeResourceId: node?.resourceId ?? null },
  });
}

await writeMacro(options.out, macro);
console.log(JSON.stringify({ out: options.out, steps: macro.steps.length }, null, 2));

async function dumpUi(adb) {
  const dumpResult = await adbText(adb, ["shell", "uiautomator", "dump", "/sdcard/quata-window.xml"]);
  if (/ERROR|null root node/i.test(dumpResult)) {
    throw new Error(`android_uiautomator_dump_failed: ${dumpResult}`);
  }
  const xml = await adbText(adb, ["exec-out", "cat", "/sdcard/quata-window.xml"]);
  if (!/<hierarchy\b/.test(xml)) {
    throw new Error("android_uiautomator_dump_failed: missing hierarchy");
  }
  return [...xml.matchAll(/<node\b[^>]*>/g)].map((match) => parseNode(match[0])).filter(Boolean);
}

function parseNode(raw) {
  const attrs = Object.fromEntries([...raw.matchAll(/([\w-]+)="([^"]*)"/g)].map(([, key, value]) => [key, decodeXml(value)]));
  const bounds = parseBounds(attrs.bounds);
  if (!bounds) return null;
  return {
    text: attrs.text || null,
    resourceId: attrs["resource-id"] || null,
    contentDescription: attrs["content-desc"] || null,
    className: attrs.class || null,
    packageName: attrs.package || null,
    bounds,
  };
}

function deepestNodeAt(nodes, x, y) {
  return nodes
    .filter((node) => x >= node.bounds.x && x <= node.bounds.x + node.bounds.width && y >= node.bounds.y && y <= node.bounds.y + node.bounds.height)
    .sort((a, b) => (a.bounds.width * a.bounds.height) - (b.bounds.width * b.bounds.height))[0] ?? null;
}

function parseBounds(value) {
  const match = /^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/.exec(value ?? "");
  if (!match) return null;
  const [, left, top, right, bottom] = match.map(Number);
  return { x: left, y: top, width: right - left, height: bottom - top };
}

function relativeBounds(bounds) {
  return { x: bounds.x / 1080, y: bounds.y / 2400, width: bounds.width / 1080, height: bounds.height / 2400 };
}

async function screencap(adb, out) {
  const { stdout } = await execFileAsync(adb, ["exec-out", "screencap", "-p"], { encoding: "buffer", maxBuffer: 20_000_000 });
  await import("node:fs/promises").then((fs) => fs.writeFile(out, stdout));
}

async function adbText(adb, args) {
  const { stdout } = await execFileAsync(adb, args, { encoding: "utf8", maxBuffer: 10_000_000 });
  return stdout.trim();
}

function decodeXml(value) {
  return value.replaceAll("&quot;", '"').replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">");
}

function parseArgs(args) {
  const parsed = {};
  for (let i = 0; i < args.length; i += 1) {
    if (!args[i].startsWith("--")) continue;
    parsed[args[i].slice(2)] = args[i + 1]?.startsWith("--") ? "true" : args[++i] ?? "true";
  }
  return parsed;
}
