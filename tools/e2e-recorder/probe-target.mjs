#!/usr/bin/env node
import { targetFromProbePoint, readProbeTree } from "./lib/platform-probes.mjs";

const options = parseArgs(process.argv.slice(2));
if (!options.platform || !options.input || !options.point) {
  console.error("Usage: node tools/e2e-recorder/probe-target.mjs --platform android|ios --input <tree.json> --point x,y [--package com.quata]");
  process.exit(64);
}

const [x, y] = String(options.point).split(",").map(Number);
if (!Number.isFinite(x) || !Number.isFinite(y)) {
  console.error("--point must be x,y");
  process.exit(64);
}

const tree = await readProbeTree(options.input);
const target = targetFromProbePoint({
  platform: options.platform,
  tree,
  x,
  y,
  packageName: options.package ?? "com.quata",
});
console.log(JSON.stringify(target, null, 2));
if (!target.stable) process.exit(2);

function parseArgs(args) {
  const parsed = {};
  for (let i = 0; i < args.length; i += 1) {
    if (!args[i].startsWith("--")) continue;
    parsed[args[i].slice(2)] = args[i + 1]?.startsWith("--") ? "true" : args[++i] ?? "true";
  }
  return parsed;
}
