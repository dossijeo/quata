import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");
const manifest = async (name) => JSON.parse(await source(`docs/candidate-attestations/${name}`));

const [
  composer,
  host,
  viewModel,
  draftStore,
  inventory,
  typing,
  typingSessions,
  outbox,
  drafts,
  crossTab,
  ime,
  attachments,
  emoji,
] = await Promise.all([
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerAndActionsContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBrowserHostContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerDraftStore.kt"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  manifest("chat-typing-presence.json"),
  manifest("chat-typing-session-multiparty.json"),
  manifest("chat-outbox-durable-remote-replay.json"),
  manifest("chat-composer-draft-restoration.json"),
  manifest("chat-composer-cross-tab-custody.json"),
  manifest("chat-ime-autoscroll.json"),
  manifest("chat-attachments-audio.json"),
  manifest("flow-emoji-mixed-links.json"),
]);

const passedPlatforms = (receipt, expected) => {
  assert.equal(receipt.status, "passed");
  assert.deepEqual(Object.keys(receipt.evidence).sort(), [...expected].sort());
  expected.forEach((platform) => assert.equal(receipt.evidence[platform].status, "passed", platform));
};

test("the product host mounts one common composer with every registered control", () => {
  assert.match(host, /ChatComposerContent\(/);
  [
    "ChatComposerRootTestTag", "ChatComposerInputTestTag", "ChatComposerSendTestTag",
    "ChatComposerEmojiTestTag", "ChatComposerAttachTestTag", "ChatComposerCameraTestTag",
    "ChatComposerRecordAudioTestTag", "ChatComposerRecordingStopTestTag",
    "ChatComposerRecordingCancelTestTag", "ChatComposerEditingBannerTestTag",
    "ChatComposerReplyBannerTestTag",
  ].forEach((tag) => assert.match(composer, new RegExp(`const val ${tag} =`), tag));
});

test("plain, reply, edit and attachment drafts share actor-bound durable custody", () => {
  assert.match(draftStore, /enum class ChatComposerDraftMode \{ Plain, Reply, Edit \}/);
  assert.match(draftStore, /data class ChatComposerDraftRecord\(/);
  assert.match(draftStore, /val attachment: ChatComposerDraftAttachment\?/);
  assert.match(draftStore, /suspend fun open\(actorId: String\): ChatComposerDraftLease/);
  assert.match(draftStore, /conversationId: String/);
  assert.match(viewModel, /ChatComposerDraftMode\.Plain/);
  assert.match(viewModel, /ChatComposerDraftMode\.Reply/);
  assert.match(viewModel, /ChatComposerDraftMode\.Edit/);
  assert.match(viewModel, /composerDraftAttachment\(\)/);
});

test("typing presence and session stress receipts cover Android, Web and iOS", () => {
  passedPlatforms(typing, ["web", "android", "ios"]);
  passedPlatforms(typingSessions, ["web", "android", "ios"]);
  assert.ok(typing.units.includes("CHAT-COMPOSER"));
  assert.ok(typingSessions.units.includes("CHAT-COMPOSER"));
});

test("durable accepted-send replay is remotely proved and cleaned", () => {
  passedPlatforms(outbox, ["web", "ios"]);
  assert.ok(outbox.units.includes("CHAT-COMPOSER"));
  for (const evidence of Object.values(outbox.evidence)) {
    assert.equal(evidence.cleanup.verified, true);
    assert.equal(evidence.requireCleanupResidueZero, true);
  }
  assert.match(outbox.notes.join("\n"), /Android keeps its previously integrated SQLite outbox/);
});

test("draft, cross-tab and IME lifecycle receipts are complete on all affected hosts", () => {
  passedPlatforms(drafts, ["web", "android", "ios"]);
  passedPlatforms(crossTab, ["web", "android", "ios"]);
  passedPlatforms(ime, ["web", "android", "ios"]);
});

test("emoji and attachment consumers remain delegated to their passed thematic receipts", () => {
  assert.ok(emoji.units.includes("CHAT-COMPOSER"));
  assert.deepEqual(Object.keys(emoji.evidence).sort(), ["android", "ios", "web"]);
  assert.ok(attachments.units.includes("CHAT-ATTACHMENTS"));
  assert.deepEqual(Object.keys(attachments.evidence).sort(), ["android", "ios", "web"]);
  Object.values(emoji.evidence).forEach((evidence) => assert.equal(evidence.status, "passed"));
  Object.values(attachments.evidence).forEach((evidence) => assert.equal(evidence.status, "passed"));
});

test("the inventory composes the root without stealing thematic failure boundaries", () => {
  const row = inventory.split("\n").find((line) => line.startsWith("| `CHAT-COMPOSER` |"));
  assert.ok(row, "CHAT-COMPOSER row missing");
  assert.match(row, /\*\*GO compuesto de compositor\.\*\*/);
  assert.match(row, /chat-composer-root-composition-reconciliation\.json/);
  assert.match(row, /fallos concretos de transporte, media y permisos permanecen/);
  assert.doesNotMatch(row, /No declara GO global de `SCR-CHAT` ni todos los tiempos de fallo de red/);
});
