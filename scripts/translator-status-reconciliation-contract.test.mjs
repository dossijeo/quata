import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const platformInventory = readFileSync("docs/MULTIPLATFORM_INVENTORY.md", "utf8");

test("Fang translator records the integrated failure/retry closure", () => {
  const row = platformInventory
    .split("\n")
    .find((line) => line.startsWith("| Traductor Fang |"));

  assert.ok(row, "missing Traductor Fang inventory row");
  assert.match(row, /#546 integra el fallo\/retry focal/);
  assert.ok(row.includes("translator-failure-retry.json"));
  assert.doesNotMatch(row, /El candidato focal de fallo\/retry/);
});
