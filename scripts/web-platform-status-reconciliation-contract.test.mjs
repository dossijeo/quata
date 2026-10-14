import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const board = readFileSync(
  new URL("../docs/MULTIPLATFORM_MIGRATION_BOARD.md", import.meta.url),
  "utf8",
);

const webRow = board
  .split("\n")
  .find((line) => line.startsWith("| Web/Wasm |"));

assert.ok(webRow, "the Web/Wasm platform status row must exist");
assert.match(webRow, /GO funcional acumulado por flujos focales/);
assert.match(webRow, /postflights autenticados controlados/);
assert.match(webRow, /account-avatar\.json/);
assert.doesNotMatch(webRow, /Faltan postflights autenticados por flujo/);
assert.doesNotMatch(webRow, /Avatar Web se acredita por contratos/);

console.log("web platform status reconciliation contract: PASS");
