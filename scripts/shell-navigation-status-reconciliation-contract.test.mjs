import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const board = await readFile(new URL("../docs/MULTIPLATFORM_MIGRATION_BOARD.md", import.meta.url), "utf8");
const inventory = await readFile(new URL("../docs/SCREEN_MIGRATION_INVENTORY_V2.md", import.meta.url), "utf8");

test("migration board reflects the integrated finite shell route catalog", () => {
  const row = board.split("\n").find((line) => line.startsWith("| Shell navegacion / FLOW-SHELL-NAV"));
  assert.ok(row, "FLOW-SHELL-NAV board row missing");
  assert.match(row, /GO de catálogo y restauración focal/);
  assert.match(row, /#613 cierra el catálogo finito de destinos productivos/);
  assert.match(row, /shell-navigation-route-catalog\.json/);
  assert.doesNotMatch(row, /límite residual reducido/);
  assert.doesNotMatch(row, /backstacks secundarios arbitrarios no enumerados/);
});

test("the iOS exact-post row no longer reports pending final CI", () => {
  const row = board.split("\n").find((line) => line.startsWith("| Shell iOS: rutas exactas Feed/Official"));
  assert.ok(row, "iOS exact-post board row missing");
  assert.match(row, /GO focal integrado por #542; CI final SUCCESS/);
  assert.doesNotMatch(row, /CI final pendiente/);
});

test("screen inventory and board agree on the finite product catalog", () => {
  const row = inventory.split("\n").find((line) => line.startsWith("| `FLOW-SHELL-NAV` |"));
  assert.ok(row, "FLOW-SHELL-NAV inventory row missing");
  assert.match(row, /GO de catálogo y restauración focal/);
  assert.match(row, /catálogo finito de destinos productivos/);
});
