import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const board = readFileSync(
  new URL("../docs/MULTIPLATFORM_MIGRATION_BOARD.md", import.meta.url),
  "utf8",
);

const androidRow = board
  .split("\n")
  .find((line) => line.startsWith("| Android |"));

assert.ok(androidRow, "the Android platform status row must exist");
assert.match(androidRow, /GO funcional acumulado por flujos focales/);
assert.match(androidRow, /postflights autenticados controlados/);
assert.match(androidRow, /auth-login-logout-postflight\.json/);
assert.match(androidRow, /create-post-postflight\.json/);
assert.match(androidRow, /account-postflight\.json/);
assert.doesNotMatch(androidRow, /Falta matriz autenticada controlada/);

console.log("android platform status reconciliation contract: PASS");
