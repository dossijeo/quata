import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const inventory = readFileSync(
  new URL("../docs/MULTIPLATFORM_INVENTORY.md", import.meta.url),
  "utf8",
);

const statusRow = inventory
  .split("\n")
  .find((line) => line.startsWith("| Editor de imagen |"));

assert.ok(statusRow, "the image editor status row must exist");
assert.match(statusRow, /Superficie común con cierres focales de post y avatar/);
assert.match(statusRow, /POST-IMAGE-EDITOR/);
assert.match(statusRow, /JPEG legible de 1080×1920/);
assert.match(statusRow, /ACCOUNT-AVATAR/);
assert.match(statusRow, /residuo local cero/);
assert.match(statusRow, /no forman un residual KMP genérico/);
assert.match(statusRow, /post-image-editor\.json/);
assert.match(statusRow, /account-avatar\.json/);
assert.doesNotMatch(statusRow, /\| KMP parcial;/);

console.log("image editor status reconciliation contract: PASS");
