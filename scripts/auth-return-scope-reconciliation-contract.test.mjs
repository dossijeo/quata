import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url));
const readText = async (path) => (await read(path)).toString("utf8");
const readJson = async (path) => JSON.parse(await readText(path));
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

const receiptNames = [
  "auth-register-login-return.json",
  "account-recovery-login-entry-web.json",
  "account-recovery-login-entry-android.json",
  "account-recovery-login-entry-ios.json",
];
const receipts = await Promise.all(
  receiptNames.map((name) => readJson(`../docs/candidate-attestations/${name}`)),
);
const nativeRetryManifests = await Promise.all([
  "auth-login-failure-retry.json",
  "auth-register-failure-retry.json",
  "auth-recovery-failure-retry.json",
].map((name) => readJson(`../docs/candidate-attestations/${name}`)));
const inventory = await readText("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");

test("normal auth return claims use four passed immutable receipts", async () => {
  assert.equal(receipts.length, 4);
  for (const [receiptIndex, receipt] of receipts.entries()) {
    const evidence = Object.values(receipt.evidence ?? {});
    assert.ok(evidence.length > 0, `receipt has no evidence: ${receiptNames[receiptIndex]}`);
    for (const entry of evidence) {
      assert.equal(entry.status, "passed");
      assert.equal(entry.executedTests, 1);
      assert.equal(entry.failedTests, 0);
      assert.equal(entry.requireReportArtifact, true);
      const reportBytes = await read(`../${entry.report}`);
      assert.equal(sha256(reportBytes), entry.reportSha256);
    }
  }
});

test("receipts cover Register return and Recovery return on all three hosts", () => {
  assert.deepEqual(Object.keys(receipts[0].evidence).sort(), ["android", "ios", "web"]);
  assert.deepEqual(Object.keys(receipts[1].evidence), ["web"]);
  assert.deepEqual(Object.keys(receipts[2].evidence), ["android"]);
  assert.deepEqual(Object.keys(receipts[3].evidence), ["ios"]);
  assert.ok(receipts[0].units.includes("SCR-AUTH-LOGIN"));
  assert.ok(receipts[0].units.includes("SCR-AUTH-REGISTER"));
});

test("inventory retires the normal Auth return residuals", () => {
  assert.match(inventory, /Las rutas normales Login → Recovery → Login quedan acreditadas/);
  assert.match(inventory, /Las rutas normales de retorno desde Registro y Recovery/);
  assert.doesNotMatch(inventory, /las rutas de retorno aún no certificadas/);
});

test("native failure/retry inventory references the current manifest Products", () => {
  for (const manifest of nativeRetryManifests) {
    assert.equal(manifest.status, "passed");
    assert.match(manifest.productSha, /^[0-9a-f]{40}$/);
    assert.ok(inventory.includes(`Product SHA \`${manifest.productSha.slice(0, 8)}\``));
  }
});

test("external and non-return Auth boundaries remain explicit", () => {
  assert.match(inventory, /`native-auth-refresh-http-boundary\.json` acredita la frontera HTTP exacta de refresh Android\/iOS/);
  assert.match(inventory, /sin atribuir captura TLS descifrada/);
  assert.match(inventory, /Siguen fuera la traza HTTP de paquete, el device token emitido por Apple, la entrega APNs y una recertificación global equivalente Android\/Web\/iOS/);
  assert.match(inventory, /Google OAuth conserva su frontera externa en `SCR-AUTH-GOOGLE`/);
  assert.match(inventory, /sin submit de recuperación ni nueva mutación backend/);
});
