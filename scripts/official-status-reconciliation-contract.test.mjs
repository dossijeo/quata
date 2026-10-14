import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const inventory = readFileSync(
  new URL("../docs/MULTIPLATFORM_INVENTORY.md", import.meta.url),
  "utf8",
);

const statusRow = inventory
  .split("\n")
  .find((line) => line.startsWith("| Official |"));

assert.ok(statusRow, "the Official status row must exist");
assert.match(statusRow, /Raíz común con cierres focales por subflujo/);
assert.match(statusRow, /SCR-OFFICIAL/);
assert.match(statusRow, /SCR-OFFICIAL-EDITOR/);
assert.match(statusRow, /OVR-LIVE-RANKING/);
assert.match(statusRow, /no se exige una matriz global no focal/);
assert.match(statusRow, /ni queda un residual KMP genérico de la raíz/);
assert.match(statusRow, /official-root-postflight\.json/);
assert.match(statusRow, /official-native-deep-pagination\.json/);
assert.match(statusRow, /official-ranking-completeness\.json/);
assert.match(statusRow, /official-rich-text-toolbar\.json/);
assert.doesNotMatch(statusRow, /\| KMP parcial \|/);
assert.doesNotMatch(statusRow, /continúan en adaptación progresiva/);

console.log("Official status reconciliation contract: PASS");
