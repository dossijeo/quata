import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("../", import.meta.url);
const source = async (relativePath) => readFile(new URL(relativePath, root), "utf8");

async function kotlinFiles(relativeDirectory) {
  const entries = await readdir(new URL(`${relativeDirectory}/`, root), { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const relative = `${relativeDirectory}/${entry.name}`;
    if (entry.isDirectory()) {
      if (["build", ".gradle"].includes(entry.name)) return [];
      return kotlinFiles(relative);
    }
    return entry.isFile() && entry.name.endsWith(".kt") ? [relative] : [];
  }));
  return nested.flat();
}

function extractsCall(file, text, symbol) {
  const calls = [...text.matchAll(new RegExp(`${symbol}\\(`, "g"))].length;
  const definitions = [...text.matchAll(new RegExp(`fun\\s+${symbol}\\(`, "g"))].length;
  return calls > definitions ? file : null;
}

async function callersOf(symbol) {
  const files = (await Promise.all([
    kotlinFiles("app/src/main"),
    kotlinFiles("feature"),
    kotlinFiles("designsystem/src/commonMain"),
    kotlinFiles("web/src"),
    kotlinFiles("ios-shared/src"),
  ])).flat();
  const callers = [];
  for (const file of files) {
    if (file.includes("/commonTest/") || file.includes("/androidTest/") || file.includes("/iosTest/")) continue;
    const caller = extractsCall(file, await source(file), symbol);
    if (caller) callers.push(caller);
  }
  return callers.sort();
}

const manifestNames = [
  "feed-media-viewer.json",
  "feed-video-viewer.json",
  "post-detail-media.json",
  "official-video-viewer.json",
  "profile-detail-media.json",
  "chat-attachment-media.json",
  "account-avatar-preview.json",
  "media-playback-recovery.json",
  "media-file-export.json",
  "ovr-media-inventory-reconciliation.json",
];

const [screenInventory, board, ...manifestTexts] = await Promise.all([
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  source("docs/MULTIPLATFORM_MIGRATION_BOARD.md"),
  ...manifestNames.map((name) => source(`docs/candidate-attestations/${name}`)),
]);

test("the fullscreen media overlay adapter catalog is finite and exact", async () => {
  assert.deepEqual(await callersOf("QuataFullscreenMediaOverlayContent"), [
    "app/src/main/java/com/quata/core/ui/components/AttachmentMediaViewer.kt",
    "app/src/main/java/com/quata/feature/official/presentation/OfficialFeedScreen.kt",
    "feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBrowserHostContent.kt",
    "feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt",
    "feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt",
    "feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/IosOfficialPlatformSlots.kt",
    "feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt",
    "web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt",
  ]);
});

test("the shared Android attachment viewer has only its registered product callers", async () => {
  assert.deepEqual(await callersOf("AttachmentViewerDialog"), [
    "app/src/main/java/com/quata/feature/chat/presentation/chat/ChatScreen.kt",
    "app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt",
  ]);
});

test("the exact catalog guards reject unregistered fullscreen and attachment producers", async () => {
  const overlayCallers = await callersOf("QuataFullscreenMediaOverlayContent");
  const attachmentCallers = await callersOf("AttachmentViewerDialog");
  const syntheticOverlay = extractsCall("feature/detached/DetachedMedia.kt", "fun x() { QuataFullscreenMediaOverlayContent() }", "QuataFullscreenMediaOverlayContent");
  const syntheticAttachment = extractsCall("feature/detached/DetachedAttachment.kt", "fun x() { AttachmentViewerDialog() }", "AttachmentViewerDialog");
  assert.throws(() => assert.deepEqual([...overlayCallers, syntheticOverlay].sort(), overlayCallers), assert.AssertionError);
  assert.throws(() => assert.deepEqual([...attachmentCallers, syntheticAttachment].sort(), attachmentCallers), assert.AssertionError);
});

test("all composed focal media receipts remain passed", () => {
  manifestTexts.forEach((text, index) => {
    const manifest = JSON.parse(text);
    const statuses = Object.values(manifest.evidence ?? {}).map((entry) => entry.status);
    assert.ok(statuses.length > 0, `${manifestNames[index]} evidence`);
    assert.ok(statuses.every((status) => status === "passed"), `${manifestNames[index]} status`);
    assert.ok(manifest.productSha, `${manifestNames[index]} productSha`);
  });
});

test("inventories close the generic producer residual and preserve external boundaries", () => {
  const row = screenInventory.split("\n").find((line) => line.startsWith("| `OVR-MEDIA` |"));
  assert.ok(row, "OVR-MEDIA row missing");
  assert.match(row, /catálogo finito de productores fullscreen/);
  assert.match(row, /Feed, Official, perfil público, adjuntos de Chat y preview del avatar de Cuenta/);
  assert.doesNotMatch(row, /otros productores/);
  assert.match(row, /catálogo exhaustivo de codecs heredados\/terceros y transportes/);
  assert.match(row, /receptores externos/);
  assert.match(row, /COEP/);
  assert.match(board, /OVR-MEDIA[^\n]*catálogo finito de productores fullscreen/);
  assert.match(board, /Feed, Official, perfil público, adjuntos de Chat y preview de avatar de Cuenta/);
});
