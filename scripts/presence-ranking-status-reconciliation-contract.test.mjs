import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const inventory = readFileSync(
  new URL("../docs/MULTIPLATFORM_INVENTORY.md", import.meta.url),
  "utf8",
);

const statusRow = inventory
  .split("\n")
  .find((line) => line.startsWith("| Presencia, proximidad y ranking |"));

assert.ok(statusRow, "the presence, proximity and ranking status row must exist");
assert.match(statusRow, /Cierres focales compuestos con bordes de plataforma explícitos/);
assert.match(statusRow, /FLOW-CONNECTIVITY-PRESENCE/);
assert.match(statusRow, /OVR-LIVE-RANKING/);
assert.match(statusRow, /adaptador físico acotado a SOS\/audio/);
assert.match(statusRow, /no constituyen un pendiente genérico de presencia o ranking/);
assert.doesNotMatch(statusRow, /\| KMP parcial \|/);
assert.doesNotMatch(statusRow, /UI de ranking requieren/);

console.log("presence and ranking status reconciliation contract: PASS");
