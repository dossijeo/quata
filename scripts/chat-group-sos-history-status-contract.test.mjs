import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const board = readFileSync(
  new URL("../docs/MULTIPLATFORM_MIGRATION_BOARD.md", import.meta.url),
  "utf8",
);

const historicalStart = board.indexOf("PR #247 reduce `CHAT-GROUP`");
const historicalEnd = board.indexOf("`FLOW-TRANSLATOR` sigue fuera de Chat.", historicalStart);
assert.ok(historicalStart >= 0, "the PR #247 history entry must exist");
assert.ok(historicalEnd > historicalStart, "the PR #247 history entry must remain bounded");

const historicalEntry = board.slice(historicalStart, historicalEnd);
assert.match(historicalEntry, /en ese corte/);
assert.match(historicalEntry, /`CHAT-GROUP` a GO focal/);
assert.match(historicalEntry, /`CHAT-LOCATION-SOS` a GO focal de recepción/);
assert.match(historicalEntry, /matrices físicas exhaustivas de permisos/);
assert.match(historicalEntry, /proveedores externos/);
assert.doesNotMatch(historicalEntry, /faltan administración UI profunda/);
assert.match(board, /`CHAT-LOCATION-SOS` ya cubre la apertura\/fallo portable del mapa/);

console.log("Chat group/SOS historical status reconciliation contract: PASS");
