import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const inventory = readFileSync("docs/SCREEN_MIGRATION_INVENTORY_V2.md", "utf8");

test("POST-COMPOSER-DRAFT records its integrated focal closure", () => {
  const row = inventory
    .split("\n")
    .find((line) => line.startsWith("| `POST-COMPOSER-DRAFT` |"));

  assert.ok(row, "missing POST-COMPOSER-DRAFT inventory row");
  assert.match(row, /GO focal integrado por #578–#580/);
  assert.ok(row.includes("Product SHA `212a5e9c`"));
  assert.ok(row.includes("post-composer-native-media-relaunch.json"));
  assert.doesNotMatch(row, /GO focal candidato/);
});
