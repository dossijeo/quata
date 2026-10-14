import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const board = readFileSync(
  new URL("../docs/MULTIPLATFORM_MIGRATION_BOARD.md", import.meta.url),
  "utf8",
);

const iosCiRow = board
  .split("\n")
  .find((line) => line.startsWith("| iOS CI |"));

assert.ok(iosCiRow, "the iOS CI status row must exist");
assert.match(iosCiRow, /CI integrado con validación fail-closed/);
assert.match(iosCiRow, /check-ios-xctest-executed\.py/);
assert.match(iosCiRow, /rechaza `SKIPPED`, fallo o ausencia/);
assert.match(iosCiRow, /modo terminal estricto usado por Auth/);
assert.match(iosCiRow, /runner APNs conserva su excepción explícita y acotada/);
assert.match(iosCiRow, /no prueban IPA\/TestFlight/);
assert.doesNotMatch(iosCiRow, /runner auth debe rechazar/);

console.log("iOS CI status reconciliation contract: PASS");
