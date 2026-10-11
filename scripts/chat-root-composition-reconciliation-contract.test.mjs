import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const readJson = async (path) => JSON.parse(await read(path));

const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");

const chatReceipts = await Promise.all([
  "translator-failure-retry.json",
  "chat-messages-lifecycle.json",
  "chat-messages-deep-pagination.json",
  "chat-message-network-recovery.json",
  "chat-message-session-recovery.json",
  "chat-outbox-durable-remote-replay.json",
  "chat-composer-draft-restoration.json",
  "chat-composer-cross-tab-custody.json",
  "chat-viewport-restoration.json",
  "chat-ime-autoscroll.json",
  "chat-typing-presence.json",
  "chat-typing-session-multiparty.json",
  "chat-message-permissions.json",
  "chat-message-mutation-rollback.json",
  "chat-message-mutation-idempotency.json",
  "chat-favorites-deep-pagination.json",
  "chat-notification-inbox-propagation.json",
  "chat-notifications-mute-rollback.json",
  "chat-attachment-picker.json",
  "chat-attachment-picker-negative-paths.json",
  "chat-attachment-rollback.json",
  "chat-attachment-media.json",
  "chat-attachments-audio.json",
  "chat-attachment-http-boundary.json",
  "chat-audio-transport-retry.json",
  "chat-audio-retry-ui.json",
  "chat-audio-terminal-matrix.json",
  "chat-audio-lifecycle-routing.json",
  "chat-forward-negative.json",
  "chat-forward-atomicity.json",
  "chat-group-admin.json",
  "chat-group-moderation.json",
  "chat-group-participant-guards.json",
  "chat-group-sos.json",
  "chat-profile-error-deep-navigation.json",
  "chat-keyboard-menu.json",
].map((name) => readJson(`../docs/candidate-attestations/${name}`)));

test("Chat root only composes passed thematic receipts", () => {
  assert.equal(chatReceipts.length, 36);
  const coveredUnits = new Set(chatReceipts.flatMap((receipt) => receipt.units ?? []));
  for (const requiredUnit of [
    "CHAT-MESSAGES",
    "CHAT-COMPOSER",
    "CHAT-MESSAGE-ACTIONS",
    "CHAT-FAVORITES",
    "CHAT-NOTIFICATIONS",
    "CHAT-ATTACHMENTS",
    "CHAT-AUDIO",
    "CHAT-FORWARD",
    "CHAT-GROUP",
    "CHAT-LOCATION-SOS",
    "CHAT-TRANSLATION",
    "CHAT-PROFILE",
    "FLOW-CONNECTIVITY-PRESENCE",
  ]) {
    assert.ok(coveredUnits.has(requiredUnit), `missing thematic receipt for ${requiredUnit}`);
  }
  for (const receipt of chatReceipts) {
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

test("screen inventory closes the Chat root while retaining child boundaries", () => {
  assert.match(screenInventory, /`SCR-CHAT` \| \*\*GO compuesto de raíz\.\*\*/);
  assert.match(screenInventory, /La raíz no conserva un límite residual genérico/);
  assert.match(screenInventory, /permisos y routing físicos, proveedores y destinos externos, topologías OS de red/);
  assert.match(screenInventory, /`FLOW-PUSH-LIFECYCLE` mantiene la propiedad del push del sistema/);
  assert.doesNotMatch(screenInventory, /`SCR-CHAT` \| \*\*COMÚN con límites\.\*\*/);
});

test("supporting inventories compose the root without broadening thematic evidence", () => {
  assert.match(platformInventory, /\| Chat \| GO compuesto de raíz; límites por capacidad y subflujo \|/);
  assert.match(platformInventory, /La raíz `SCR-CHAT` queda en GO compuesto/);
  assert.match(migrationBoard, /Chat raíz compuesta/);
  assert.match(migrationBoard, /Treinta y seis recibos temáticos aprobados/);
  assert.match(migrationBoard, /No atribuye una nueva ejecución Android, Web\/Wasm o iOS/);
  assert.doesNotMatch(platformInventory, /No hay GO global de `SCR-CHAT`/);
});
