import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const sha256 = (value) => createHash("sha256").update(value).digest("hex");

const exactSources = new Map([
  ["docs/candidate-attestations/live-ranking-android-postflight.json", "6891a1671504af0cfdbf0b490ae75035d33a53a8770cbe7323ecfbd4eee81eb6"],
  ["docs/candidate-attestations/official-live-ranking-navigation.json", "6409ff5404318f4d9b9d0e701a6742470d5c2b924dc95f4b72ca5b1546483805"],
  ["docs/candidate-attestations/official-ranking-completeness.json", "444074fc5335a844365004c538805314c71c99852d33d6bd4482ac01b495d6a1"],
  ["docs/candidate-attestations/official-native-deep-pagination.json", "6711f21956e5a97d6aceeb22ea2dc2dd6a9a4cba130834bb51d774f66d6916ee"],
  ["scripts/community-feed-ranking-completeness-contract.test.mjs", "427a2bce5a1273471b57e21117a4d2f554173e4e14894b39045374995606ed1b"],
  ["scripts/official-feed-total-order-contract.test.mjs", "4331e9a9f7663903ba0050af458820fa0091c64d3e13e5ad16c4c086d94cff52"],
  ["scripts/live-ranking-native-e2e-contract.test.mjs", "c6b5394baabd9c994db2088f837011c8b822fe85620766020246af613a6d90cd"],
  ["scripts/verify-live-ranking-android-results.test.mjs", "ad4c67eac6a57fcc5743f7ef7dbfb07412925d57bee1d38dfbd16556cf670eb8"],
]);

const sources = await Promise.all([...exactSources].map(async ([path, expectedHash]) => {
  const source = await read(`../${path}`);
  return { path, expectedHash, source };
}));
const manifests = sources
  .filter(({ path }) => path.endsWith(".json"))
  .map(({ path, source }) => ({ path, value: JSON.parse(source) }));
const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");
const packageJson = JSON.parse(await read("../package.json"));

test("OVR-LIVE-RANKING composition binds exact receipts and executable contracts", () => {
  assert.equal(sources.length, 8);
  for (const { path, expectedHash, source } of sources) {
    assert.equal(sha256(source), expectedHash, `${path} hash drift`);
  }
  for (const { path, value } of manifests) {
    assert.ok(value.units.includes("OVR-LIVE-RANKING"), `${path} unit`);
    assert.equal(value.status, "passed", `${path} status`);
    const statuses = Object.values(value.evidence ?? {}).map((entry) => entry?.status).filter(Boolean);
    assert.ok(statuses.length > 0 && statuses.every((status) => status === "passed"), `${path} evidence`);
  }
});

test("OVR-LIVE-RANKING closes the finite functional catalog without absorbing media", () => {
  const row = screenInventory.split(/\r?\n/).find((line) => line.startsWith("| `OVR-LIVE-RANKING` |"));
  assert.ok(row);
  assert.match(row, /GO compuesto de overlay/);
  assert.match(row, /catálogo funcional finito/);
  assert.match(row, /completitud remota fail-closed de Feed y Official/);
  assert.match(row, /paneles nativos y destino profundo exacto/);
  assert.match(row, /Media permanece en `OVR-MEDIA`/);
  assert.match(platformInventory, /`OVR-LIVE-RANKING` queda en GO compuesto/);
  assert.match(migrationBoard, /Live\/Ranking compuesto/);
  assert.match(migrationBoard, /No atribuye ejecución nueva/);
});

test("OVR-LIVE-RANKING preserves superseded failure and exact owner boundaries", () => {
  const row = screenInventory.split(/\r?\n/).find((line) => line.startsWith("| `OVR-LIVE-RANKING` |"));
  assert.match(row, /402 exceed_egress_quota/);
  assert.match(row, /ni cuenta como evidencia positiva/);
  assert.match(row, /#539/);
  assert.match(row, /#544/);
  assert.match(row, /#545/);
  assert.match(row, /#551/);
  assert.match(row, /#560/);
  assert.match(row, /no queda un residual interno genérico/);
});

test("OVR-LIVE-RANKING composition contract is wired exactly once into each affected aggregate", () => {
  const script = "scripts/live-ranking-root-composition-reconciliation-contract.test.mjs";
  for (const aggregate of ["test:web-wave2-contracts", "test:ci-fast-contracts"]) {
    assert.equal(packageJson.scripts[aggregate].split(script).length - 1, 1, `${aggregate} wiring count`);
  }
});
