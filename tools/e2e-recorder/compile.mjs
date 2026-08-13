#!/usr/bin/env node
import { compileMacro, readMacro, summarizeMacro } from "./lib/macro-core.mjs";

const file = process.argv[2];
if (!file) {
  console.error("Usage: node tools/e2e-recorder/compile.mjs <macro.json>");
  process.exit(64);
}

const macro = await readMacro(file);
const compiled = compileMacro(macro);
console.log(JSON.stringify({ summary: summarizeMacro(macro), compiled }, null, 2));
if (!compiled.runnable) process.exit(2);
