import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const readJson = async (path) => JSON.parse(await read(path));
const sha256 = (value) => createHash("sha256").update(value).digest("hex");

const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const manifestNames = ["media-playback-recovery.json", "media-file-export.json"];
const manifests = await Promise.all(
  manifestNames.map((name) => readJson(`../docs/candidate-attestations/${name}`)),
);

test("OVR-MEDIA composes only passed Android, Web and iOS receipts", async () => {
  for (const [index, manifest] of manifests.entries()) {
    assert.deepEqual(manifest.units, ["OVR-MEDIA"], `unexpected unit in ${manifestNames[index]}`);
    assert.equal(manifest.status, "passed", `manifest is not passed: ${manifestNames[index]}`);
    assert.deepEqual(Object.keys(manifest.evidence).sort(), ["android", "ios", "web"]);
    for (const entry of Object.values(manifest.evidence)) {
      assert.equal(entry.status, "passed");
      assert.equal(entry.reportStatus, "passed");
      const report = await read(`../${entry.report}`);
      assert.equal(sha256(report), entry.reportSha256, `report hash mismatch: ${entry.report}`);
    }
  }
});

test("inventories retire recovered playback and file export as pending limits", () => {
  assert.match(screenInventory, /continuidad de vídeo, posición durable, Retry y Descargar\/Compartir/);
  assert.match(screenInventory, /\[recuperación\]\(\.\/candidate-attestations\/media-playback-recovery\.json\)/);
  assert.match(screenInventory, /media-playback-recovery\.json/);
  assert.match(screenInventory, /media-file-export\.json/);
  assert.match(migrationBoard, /El fallo recuperable y Retry de la misma fuente ya están acreditados/);
  assert.match(migrationBoard, /Descargar y Compartir PNG y MP4 también están acreditados/);
  assert.match(platformInventory, /`media-playback-recovery` añade fallo visible y Retry sobre la misma fuente/);
  assert.match(platformInventory, /`media-file-export` añade Descargar y Compartir archivo/);
});

test("material external and exhaustive boundaries remain explicit", () => {
  assert.match(screenInventory, /catálogo exhaustivo de codecs heredados\/terceros y transportes/);
  assert.match(screenInventory, /reproducción prolongada en segundo plano/);
  assert.match(screenInventory, /receptores externos/);
  assert.match(screenInventory, /activación\/validación futura de COEP/);
  assert.match(migrationBoard, /catálogo exhaustivo de codecs heredados\/terceros y transportes/);
  assert.match(migrationBoard, /receptores externos/);
  assert.match(migrationBoard, /activación\/validación futura de COEP/);
  assert.match(migrationBoard, /La deuda CORS histórica queda cerrada/);
});

test("the reconciliation closes the finite fullscreen producer catalog without absorbing thematic boundaries", () => {
  assert.match(screenInventory, /catálogo finito de productores fullscreen/);
  assert.doesNotMatch(screenInventory, /otros productores de media/);
  assert.match(migrationBoard, /ya no existe un productor fullscreen desconocido/);
  assert.match(platformInventory, /catálogo finito de productores fullscreen/);
  assert.doesNotMatch(platformInventory, /otros productores de media/);
  assert.match(screenInventory, /comentarios integrados, la restauración profunda de scroll/);
});
