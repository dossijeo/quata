import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const sha256 = (value) => createHash("sha256").update(value).digest("hex");

const receiptHashes = new Map([
  ["chat-attachments-audio.json", "daec7ac5e5460e8b28e12fccf475e8102ed048030f639c91b2d66e135768fd0b"],
  ["chat-attachment-picker.json", "29808dcde36a8653b6b06d3aa13b1ccc72a4d5fd85eed5ffc093b4ef59c8d318"],
  ["chat-attachment-picker-negative-paths.json", "c76970d113921c9d01bd072f97f59a28e9208a100c12bf45329c268483c0a8bd"],
  ["chat-attachment-rollback.json", "5efb626b1fe1f98aca31b3edaea752f0ffcba5f0f34305c851ae355022ee303b"],
  ["chat-attachment-media.json", "0f68a430f3550a04cf1fa5ee973d0318187adf3244bde5d161eb0f6fe0376f3b"],
  ["document-viewer-actions.json", "27205870ecdcbbcdf047de802f04d2ba0c994d47bbc6ce19b0674e724882cd88"],
  ["flow-document-viewer-retry.json", "91f2bb6fbaf8093343ca8105ce1012d5e30f3f36c1643bcaf49689f4efff8a6c"],
  ["chat-attachment-http-boundary.json", "c92b05c0b5800e29fc9f3754f35c8e86f76dd40a8a15ce8ed11328d718f16f8e"],
  ["prof-shared-chat-documents-confidentiality.json", "e8764ced7a8e732495bcfabeaa6dff1aac6b4b66b547f3ec4a45b76051674e18"],
]);

const receipts = await Promise.all([...receiptHashes].map(async ([name, expectedHash]) => {
  const source = await read(`../docs/candidate-attestations/${name}`);
  return { name, expectedHash, source, value: JSON.parse(source) };
}));
const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");

test("CHAT-ATTACHMENTS composition binds the nine exact passed owner and confidentiality receipts", () => {
  assert.equal(receipts.length, 9);
  for (const { name, expectedHash, source, value } of receipts) {
    assert.equal(sha256(source), expectedHash, `${name} hash drift`);
    const evidenceStatuses = Object.values(value.evidence ?? {})
      .map((entry) => entry?.status)
      .filter(Boolean);
    assert.ok(
      value.status === "passed" ||
        (evidenceStatuses.length > 0 && evidenceStatuses.every((status) => status === "passed")),
      `${name} is not passed`,
    );
  }
  assert.equal(receipts.filter(({ value }) => value.units?.includes("CHAT-ATTACHMENTS")).length, 8);
  assert.ok(receipts.find(({ name }) => name === "prof-shared-chat-documents-confidentiality.json")
    ?.value.units.includes("PROF-SHARED-CHAT-DOCUMENTS"));
});

test("CHAT-ATTACHMENTS removes only the generic internal residual", () => {
  const row = screenInventory.split(/\r?\n/).find((line) => line.startsWith("| `CHAT-ATTACHMENTS` |"));
  assert.ok(row);
  assert.match(row, /GO compuesto focal con límites externos explícitos/);
  assert.match(row, /No queda un límite interno residual/);
  assert.match(row, /Android v32/);
  assert.match(row, /permisos OS físicos/);
  assert.match(row, /destinos externos/);
  assert.match(row, /formatos\/proveedores/);
  assert.match(row, /topologías OS/);
  assert.match(platformInventory, /`CHAT-ATTACHMENTS` queda en GO compuesto focal/);
  assert.match(platformInventory, /compatibilidad Android v32 permanece distinguible/);
});

test("migration board records attachment composition without new platform evidence", () => {
  assert.match(migrationBoard, /Chat attachments compuesto/);
  assert.match(migrationBoard, /Ocho recibos propietarios más la confidencialidad de Storage privado/);
  assert.match(migrationBoard, /No atribuye una nueva ejecución Android, Web\/Wasm, iOS ni base de datos/);
  assert.match(migrationBoard, /Android v32/);
});
