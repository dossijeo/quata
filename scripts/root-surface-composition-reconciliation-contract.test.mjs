import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const readJson = async (path) => JSON.parse(await read(path));

const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");

const feedReceipts = await Promise.all([
  "feed-root-postflight.json",
  "feed-native-deep-pagination.json",
  "official-live-ranking-navigation.json",
  "create-post-postflight.json",
  "post-detail-actions.json",
  "feed-media-viewer.json",
  "feed-video-viewer.json",
  "comments-deep-pagination.json",
  "comments-replies-pagination.json",
  "flow-emoji-error-rollback.json",
  "auth-action-restore-feed.json",
].map((name) => readJson(`../docs/candidate-attestations/${name}`)));

const conversationReceipts = await Promise.all([
  "conversations-postflight.json",
  "conversations-root-postflight.json",
  "conversations-pagination-lifecycle.json",
  "conversations-deep-pagination.json",
  "conversations-cold-search-persistence.json",
  "conversation-create.json",
  "conversation-create-race.json",
  "conversation-create-error-rollback.json",
  "conversation-create-rendered-retry.json",
  "conversation-create-backend-atomicity.json",
  "conversation-create-uncertain-response.json",
  "conversation-invites-parity.json",
  "conversation-invites-web-contact-picker.json",
].map((name) => readJson(`../docs/candidate-attestations/${name}`)));

test("Feed and Conversations roots only compose passed thematic receipts", () => {
  for (const receipt of [...feedReceipts, ...conversationReceipts]) {
    const evidenceStatuses = Object.values(receipt.evidence ?? {})
      .map((entry) => entry?.status)
      .filter(Boolean);
    assert.ok(
      receipt.status === "passed" ||
        (evidenceStatuses.length > 0 && evidenceStatuses.every((status) => status === "passed")),
    );
    assert.ok(Array.isArray(receipt.units) && receipt.units.length > 0);
  }
});

test("screen inventory closes both roots without erasing thematic limits", () => {
  assert.match(screenInventory, /`SCR-FEED` \| GO compuesto de raíz;/);
  assert.match(screenInventory, /La raíz no conserva un límite residual genérico: media, detalle, comentarios, perfiles y mutaciones/);
  assert.match(screenInventory, /`SCR-CONVERSATIONS` \| GO compuesto de raíz;/);
  assert.match(screenInventory, /La raíz no conserva un límite residual genérico: creación, invitaciones, perfil y recuperación de red/);
  assert.doesNotMatch(screenInventory, /`SCR-FEED` \| COMÚN con límites/);
  assert.doesNotMatch(screenInventory, /`SCR-CONVERSATIONS` \| COMÚN con límites/);
});

test("supporting inventories reject a new non-focal global matrix requirement", () => {
  assert.match(platformInventory, /La raíz Feed queda en GO compuesto/);
  assert.match(migrationBoard, /`SCR-CONVERSATIONS` queda en GO compuesto de raíz/);
  assert.match(migrationBoard, /La raíz `SCR-FEED` queda en GO compuesto/);
  assert.doesNotMatch(platformInventory, /No hay GO global de Feed/);
  assert.doesNotMatch(migrationBoard, /`SCR-CONVERSATIONS` no es GO global/);
  assert.match(platformInventory, /conservan el alcance exacto de sus unidades propietarias/);
  assert.match(migrationBoard, /cada subflujo conserva su propia evidencia y sus límites concretos/);
});
