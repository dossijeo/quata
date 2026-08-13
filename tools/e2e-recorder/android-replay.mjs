#!/usr/bin/env node
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { compileMacro, readMacro } from "./lib/macro-core.mjs";

const execFileAsync = promisify(execFile);
const options = parseArgs(process.argv.slice(2));
if (!options.macro) {
  console.error("Usage: node tools/e2e-recorder/android-replay.mjs --macro <macro.json> [--adb <adb>]");
  process.exit(64);
}

const adb = options.adb ?? process.env.ADB ?? "adb";
const packageName = options.package ?? "com.quata";
const macro = await readMacro(options.macro);
const compiled = compileMacro(macro);
if (!compiled.runnable) {
  console.error(JSON.stringify(compiled.diagnostics, null, 2));
  process.exit(2);
}

const result = { macro: options.macro, steps: [], ok: true };
for (const [index, step] of compiled.steps.entries()) {
  const entry = { index, action: step.action, selector: step.replay, ok: false };
  try {
    if (step.action === "tap" || step.action === "click") await replayTap(adb, step);
    else if (step.action === "assertVisible") await assertVisible(adb, step);
    else throw new Error(`unsupported_android_action ${step.action}`);
    entry.ok = true;
  } catch (error) {
    entry.error = String(error);
    result.ok = false;
    result.steps.push(entry);
    break;
  }
  result.steps.push(entry);
}
console.log(JSON.stringify(result, null, 2));
if (!result.ok) process.exit(1);

async function assertVisible(adb, step) {
  const nodes = await dumpUi(adb);
  const node = findNode(nodes, step.replay);
  if (!node) throw new Error(`selector_not_found ${JSON.stringify(step.replay)}`);
}

async function replayTap(adb, step) {
  const nodes = await dumpUi(adb);
  const node = findNode(nodes, step.replay);
  if (!node) throw new Error(`selector_not_found ${JSON.stringify(step.replay)}`);
  const x = Math.round(node.bounds.x + node.bounds.width / 2);
  const y = Math.round(node.bounds.y + node.bounds.height / 2);
  await adbText(adb, ["shell", "input", "tap", String(x), String(y)]);
}

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

function findNode(nodes, selector) {
  const appNodes = nodes.filter((node) => !node.packageName || node.packageName === packageName);
  if (selector.kind === "uiautomatorResourceId") return appNodes.find((node) => node.resourceId === selector.value);
  if (selector.kind === "uiautomatorDescription") return appNodes.find((node) => node.contentDescription === selector.value);
  if (selector.kind === "uiautomatorText") return appNodes.find((node) => node.text === selector.value);
  if (selector.kind === "composeTestTag") return appNodes.find((node) => node.contentDescription === selector.value || node.resourceId?.endsWith(`:id/${selector.value}`));
  if (selector.kind === "geometry") {
    const { x, y } = selector.value?.coordinates ?? selector.value ?? {};
    return nodes.find((node) => x >= node.bounds.x && x <= node.bounds.x + node.bounds.width && y >= node.bounds.y && y <= node.bounds.y + node.bounds.height);
  }
  throw new Error(`unsupported_android_selector ${JSON.stringify(selector)}`);
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

function parseBounds(value) {
  const match = /^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/.exec(value ?? "");
  if (!match) return null;
  const [, left, top, right, bottom] = match.map(Number);
  return { x: left, y: top, width: right - left, height: bottom - top };
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
