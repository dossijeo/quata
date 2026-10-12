import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const sha256 = (value) => createHash("sha256").update(value).digest("hex");

const receiptHashes = new Map([
  ["media-producer-catalog.json", "d77c866855b7bf47f811ea6c2f0f14e86b7748170ece5a46f066986387ec26d7"],
  ["account-avatar-preview.json", "52d2967061c6a53cf8ad0a4230645d918a74b68e931c77433c84092e2ad80c9b"],
  ["feed-media-viewer.json", "38e645dce8eb3bdf7925a6e1435c89720b7803f85ff8c10defd95f83a3710bbf"],
  ["feed-video-viewer.json", "b368292d297b0506edae5c95206dca4c2e469ac1ea8aace3cbb7856c92c0e66b"],
  ["post-detail-media.json", "0b354eb61a2b6bfc048d7c0d149dadc5812d157e399aec2c778ae971ca0ef469"],
  ["official-video-viewer.json", "880dda26c29609f743861c0cc120c8ad7c49ba4827d93e1bf542f4fd3bf63834"],
  ["profile-detail-media.json", "3049230e9b6874a25ed4e91c87678f1ef1715184892de2faf6758e420615f4ca"],
  ["chat-attachment-media.json", "0f68a430f3550a04cf1fa5ee973d0318187adf3244bde5d161eb0f6fe0376f3b"],
  ["media-playback-recovery.json", "03d306a149e18e84bcc6be98af4b6c0c3b39ff9d0d033d32818ac2618f347dab"],
  ["media-file-export.json", "4304be5bc94cdc932115651ec314ea6237bc8e2e33dd24a2cc83eb562fd78b20"],
  ["media-historical-cors.json", "4bfd0178d82360d96c2a57623b2b90f1c48831fe9733be47725b5d0a5268fd4b"],
  ["post-video-web-cross-platform-codec.json", "adaf7a1d2cf91b8f82745c6bdad797746655b55115a918e48805127a78c1390d"],
  ["ovr-media-inventory-reconciliation.json", "f67301d50f7fafde7db0551fc5a1317b3d3c7c0f51f11b02472e61028e04536e"],
]);

const receipts = await Promise.all([...receiptHashes].map(async ([name, expectedHash]) => {
  const source = await read(`../docs/candidate-attestations/${name}`);
  return { name, expectedHash, source, value: JSON.parse(source) };
}));
const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");
const packageJson = JSON.parse(await read("../package.json"));

test("OVR-MEDIA composition binds the exact passed catalog and owner receipts", () => {
  assert.equal(receipts.length, 13);
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
});

test("OVR-MEDIA removes only the generic overlay residual", () => {
  const row = screenInventory.split(/\r?\n/).find((line) => line.startsWith("| `OVR-MEDIA` |"));
  assert.ok(row);
  assert.match(row, /GO compuesto de overlay/);
  assert.match(row, /cinco productores lógicos/);
  assert.match(row, /No queda un residual interno genérico/);
  assert.match(platformInventory, /OVR-MEDIA queda en GO compuesto/);
  assert.match(migrationBoard, /Media overlay compuesto/);
  assert.match(migrationBoard, /Once recibos propietarios, el catálogo finito y su reconciliación histórica/);
  assert.match(migrationBoard, /No atribuye ejecución nueva/);
});

test("OVR-MEDIA keeps thematic and external boundaries owned", () => {
  for (const source of [screenInventory, platformInventory, migrationBoard]) {
    assert.match(source, /codecs/);
    assert.match(source, /segundo plano/);
    assert.match(source, /receptores externos/);
    assert.match(source, /COEP/);
  }
  assert.match(screenInventory, /comentarios y el scroll gobernados por sus unidades propietarias/);
});

test("OVR-MEDIA composition contract is wired exactly once into each affected aggregate", () => {
  const script = "scripts/ovr-media-root-composition-reconciliation-contract.test.mjs";
  for (const aggregate of ["test:web-wave2-contracts", "test:ci-fast-contracts"]) {
    const count = packageJson.scripts[aggregate].split(script).length - 1;
    assert.equal(count, 1, `${aggregate} wiring count`);
  }
});
