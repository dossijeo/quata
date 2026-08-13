#!/usr/bin/env node
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { createMacro, readMacro, writeMacro } from "./lib/macro-core.mjs";
import { readProbeTree, targetFromProbePoint } from "./lib/platform-probes.mjs";

const options = parseArgs(process.argv.slice(2));
if (!options.macro || !options.flow || !options.platform || !options.action || !options.point || !options.probe) {
  console.error("Usage: node tools/e2e-recorder/append-step.mjs --macro <macro.json> --flow <name> --platform android|ios --action tap|assertVisible --probe <tree.json> --point x,y");
  process.exit(64);
}

const [x, y] = String(options.point).split(",").map(Number);
if (!Number.isFinite(x) || !Number.isFinite(y)) {
  console.error("--point must be x,y");
  process.exit(64);
}
if (!["tap", "assertVisible"].includes(options.action)) {
  console.error("--action must be tap or assertVisible");
  process.exit(64);
}

let macro;
try {
  macro = await readMacro(options.macro);
} catch (error) {
  if (error?.code !== "ENOENT") throw error;
  macro = createMacro({ flow: options.flow, platform: options.platform });
}

if (macro.flow !== options.flow || macro.platform !== options.platform) {
  throw new Error(`${options.macro}: expected flow/platform ${options.flow}/${options.platform}, found ${macro.flow}/${macro.platform}`);
}

const tree = await readProbeTree(options.probe);
const target = targetFromProbePoint({
  platform: options.platform,
  tree,
  x,
  y,
  packageName: options.package ?? "com.quata",
});

if (!target.stable) {
  console.error(JSON.stringify({
    code: "missing_stable_anchor",
    point: { x, y },
    fallback: target.preferred,
    probe: options.probe,
  }, null, 2));
  process.exit(2);
}

macro.steps.push({
  timestamp: new Date().toISOString(),
  platform: options.platform,
  action: options.action,
  screen: options.screen ?? null,
  coordinates: { x, y },
  target,
  screenshotBefore: options["screenshot-before"] ?? null,
  screenshotAfter: options["screenshot-after"] ?? null,
  observableAfter: {
    probe: options.probe,
    stable: target.stable,
    preferred: target.preferred,
  },
});

await mkdir(path.dirname(options.macro), { recursive: true });
await writeMacro(options.macro, macro);
console.log(JSON.stringify({ macro: options.macro, steps: macro.steps.length, stable: target.stable, preferred: target.preferred }, null, 2));

function parseArgs(args) {
  const parsed = {};
  for (let i = 0; i < args.length; i += 1) {
    if (!args[i].startsWith("--")) continue;
    parsed[args[i].slice(2)] = args[i + 1]?.startsWith("--") ? "true" : args[++i] ?? "true";
  }
  return parsed;
}
