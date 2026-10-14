import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const inventory = readFileSync(
  new URL("../docs/SCREEN_MIGRATION_INVENTORY_V2.md", import.meta.url),
  "utf8",
);
const board = readFileSync(
  new URL("../docs/MULTIPLATFORM_MIGRATION_BOARD.md", import.meta.url),
  "utf8",
);
const attestation = JSON.parse(
  readFileSync(
    new URL("../docs/candidate-attestations/chat-group-sos.json", import.meta.url),
    "utf8",
  ),
);

const statusRow = inventory
  .split("\n")
  .find((line) => line.startsWith("| `CHAT-LOCATION-SOS` |"));

assert.ok(statusRow, "the CHAT-LOCATION-SOS status row must exist");
assert.match(statusRow, /GO focal de recepción, representación y apertura de mapa/);
assert.match(statusRow, /ChatSosLocationContent/);
assert.match(statusRow, /ChatMapOpenResult/);
assert.match(statusRow, /Web\/Wasm, Android e iOS/);
assert.match(statusRow, /permission_denied/);
assert.match(statusRow, /FLOW-SOS-DISPATCH/);
assert.match(statusRow, /chat-group-sos\.json/);
assert.match(statusRow, /matrices físicas exhaustivas de permisos/);
assert.doesNotMatch(statusRow, /\| COMÚN para recepción\/representación y mapa;/);

assert.ok(attestation.units.includes("CHAT-LOCATION-SOS"));
for (const platform of ["web", "android", "ios"]) {
  assert.equal(attestation.evidence[platform].status, "passed");
  assert.equal(attestation.evidence[platform].cleanup.physicalResidue, 0);
}
assert.match(board, /`CHAT-LOCATION-SOS` ya cubre la apertura\/fallo portable del mapa/);
assert.match(board, /permanecen fuera únicamente matrices físicas exhaustivas de permisos/);

console.log("CHAT-LOCATION-SOS status reconciliation contract: PASS");
