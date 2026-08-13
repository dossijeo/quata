#!/usr/bin/env node
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { compileMacro, readMacro, renderReplayArtifact, summarizeMacro } from "./lib/macro-core.mjs";

const options = parseArgs(process.argv.slice(2));
const file = options._[0];
if (!file) {
  console.error("Usage: node tools/e2e-recorder/compile.mjs <macro.json> [--emit <replay-file>]");
  process.exit(64);
}

const macro = await readMacro(file);
const compiled = compileMacro(macro);
console.log(JSON.stringify({ summary: summarizeMacro(macro), compiled }, null, 2));
if (!compiled.runnable) process.exit(2);
if (options.emit) {
  await mkdir(path.dirname(options.emit), { recursive: true });
  await writeFile(options.emit, renderReplayArtifact(compiled), "utf8");
}

function parseArgs(args) {
  const parsed = { _: [] };
  for (let i = 0; i < args.length; i += 1) {
    if (!args[i].startsWith("--")) {
      parsed._.push(args[i]);
      continue;
    }
    parsed[args[i].slice(2)] = args[i + 1]?.startsWith("--") ? "true" : args[++i] ?? "true";
  }
  return parsed;
}
