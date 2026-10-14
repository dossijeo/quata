import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const inventory = readFileSync(
  new URL("../docs/MULTIPLATFORM_INVENTORY.md", import.meta.url),
  "utf8",
);

const statusRow = inventory
  .split("\n")
  .find((line) => line.startsWith("| Componentes de comunidades |"));

assert.ok(statusRow, "the community components status row must exist");
assert.match(statusRow, /Componentes comunes con cierres focales por consumidor/);
assert.match(statusRow, /catálogo localizado y los estados del selector emoji/);
assert.match(statusRow, /incluido cierre exterior\/Back/);
assert.match(statusRow, /FLOW-EMOJI/);
assert.match(statusRow, /OVR-COMMENTS/);
assert.match(statusRow, /OVR-LIVE-RANKING/);
assert.match(statusRow, /no queda un adaptador Android genérico pendiente/);
assert.doesNotMatch(statusRow, /\| KMP parcial \|/);
assert.doesNotMatch(statusRow, /catálogo localizado, cierre por toque exterior, traducción visual y contenedor Android siguen como adaptadores/);

console.log("community components status reconciliation contract: PASS");
