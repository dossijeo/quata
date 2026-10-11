import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const readJson = async (path) => JSON.parse(await read(path));
const sha256 = (value) => createHash("sha256").update(value).digest("hex");

const receiptNames = [
  "chat-attachments-audio.json",
  "chat-audio-transport-retry.json",
  "chat-audio-retry-ui.json",
  "chat-audio-terminal-matrix.json",
  "chat-attachment-http-boundary.json",
  "chat-audio-lifecycle-routing.json",
];

const expectedHashes = new Map([
  ["chat-attachments-audio.json", "daec7ac5e5460e8b28e12fccf475e8102ed048030f639c91b2d66e135768fd0b"],
  ["chat-audio-transport-retry.json", "13a59c89554e5134faacd6892df8596b3c15519fc8fce06b21e36ab73732508a"],
  ["chat-audio-retry-ui.json", "63cac2c98b979d53bda3a2006cde3f0a2c369fce0b3892f61643c35097641345"],
  ["chat-audio-terminal-matrix.json", "a08ebde08b19a3665460df865a8eedaa2e1131856c8d1a5dd27b3119c86dd9c6"],
  ["chat-attachment-http-boundary.json", "c92b05c0b5800e29fc9f3754f35c8e86f76dd40a8a15ce8ed11328d718f16f8e"],
  ["chat-audio-lifecycle-routing.json", "0eef16c034ad137143fa3835b60b564a620f08b9f5fc9c3e515a3afa430d02b8"],
]);

const receipts = await Promise.all(receiptNames.map(async (name) => {
  const source = await read(`../docs/candidate-attestations/${name}`);
  return { name, source, value: JSON.parse(source) };
}));
const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");

test("CHAT-AUDIO composition uses the six exact passed owner receipts", () => {
  assert.equal(receipts.length, 6);
  for (const { name, source, value } of receipts) {
    assert.equal(sha256(source), expectedHashes.get(name), `${name} hash drift`);
    assert.ok(value.units?.includes("CHAT-AUDIO"), `${name} does not own CHAT-AUDIO`);
    const evidenceStatuses = Object.values(value.evidence ?? {})
      .map((entry) => entry?.status)
      .filter(Boolean);
    assert.ok(
      value.status === "passed" ||
        (evidenceStatuses.length > 0 && evidenceStatuses.every((status) => status === "passed")),
      `${name} is not passed`,
    );
  }
});

test("CHAT-AUDIO inventories remove the generic internal residual only", () => {
  const row = screenInventory.split(/\r?\n/).find((line) => line.startsWith("| `CHAT-AUDIO` |"));
  assert.ok(row);
  assert.match(row, /GO compuesto focal con límites externos explícitos/);
  assert.match(row, /No queda un límite interno residual/);
  assert.match(row, /permiso\/grabación física/);
  assert.match(row, /routing Bluetooth\/cableado/);
  assert.match(row, /reproducción prolongada en segundo plano/);
  assert.match(row, /topologías OS reales/);
  assert.match(row, /audio fuera de Chat/);
  assert.match(platformInventory, /`CHAT-AUDIO` queda en GO compuesto focal/);
  assert.match(platformInventory, /fronteras físicas y externas permanecen explícitas/);
});

test("migration board records composition without claiming new platform execution", () => {
  assert.match(migrationBoard, /Chat audio compuesto/);
  assert.match(migrationBoard, /[Ss]eis recibos propietarios aprobados/);
  assert.match(migrationBoard, /No atribuye una nueva ejecución Android, Web\/Wasm o iOS/);
  assert.match(migrationBoard, /no amplía audio fuera de Chat/);
});

