import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const inventory = readFileSync(
  new URL("../docs/MULTIPLATFORM_INVENTORY.md", import.meta.url),
  "utf8",
);

const statusRow = inventory
  .split("\n")
  .find((line) => line.startsWith("| Audio (grabación, reproducción y adjuntos) |"));

assert.ok(statusRow, "the chat audio status row must exist");
assert.match(statusRow, /CHAT-AUDIO` en GO compuesto focal/);
assert.match(statusRow, /seis recibos propietarios elimina el residual interno genérico/);
assert.match(statusRow, /chat-attachments-audio\.json/);
assert.match(statusRow, /chat-audio-transport-retry\.json/);
assert.match(statusRow, /chat-audio-retry-ui\.json/);
assert.match(statusRow, /chat-audio-terminal-matrix\.json/);
assert.match(statusRow, /chat-attachment-http-boundary\.json/);
assert.match(statusRow, /chat-audio-lifecycle-routing\.json/);
assert.match(statusRow, /permisos y la grabación físicos/);
assert.match(statusRow, /audio fuera de Chat/);
assert.doesNotMatch(statusRow, /\| KMP parcial;/);

console.log("chat audio status reconciliation contract: PASS");
