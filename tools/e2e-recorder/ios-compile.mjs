#!/usr/bin/env node
import { compileMacro, readMacro, renderReplayArtifact } from "./lib/macro-core.mjs";

const file = process.argv[2];
if (!file) {
  console.error("Usage: node tools/e2e-recorder/ios-compile.mjs <macro.json>");
  process.exit(64);
}

const macro = await readMacro(file);
const compiled = compileMacro(macro);
if (!compiled.runnable) {
  console.error(JSON.stringify(compiled.diagnostics, null, 2));
  process.exit(2);
}

console.log(renderReplayArtifact(compiled));
