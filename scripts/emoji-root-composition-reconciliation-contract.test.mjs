import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const sha256 = (value) => createHash("sha256").update(value).digest("hex");

const receiptHashes = new Map([
  ["flow-emoji-visual-comparison.json", "74841ba1d523e0e3cdb02789ffa6ade0fe717c02746737e1d1ce4710f7d91b17"],
  ["flow-emoji-mixed-links.json", "8acc0ff87262ebb426a723079fbad4e4505f0b7ae2c5fd7a2a404b64cddba4d5"],
  ["flow-emoji-profile-comments.json", "cc86a8734b51791834661849e3c57428e8edecc5572fd97ab8b2561e052f7094"],
  ["flow-emoji-error-rollback.json", "0adec726dc5d8253a1d320f406cb42e02800293c6ae3b7a7981be602c1d35b27"],
  ["flow-emoji-selector-states.json", "83c94670f447e5da933c4293bc7317d826f5b2ba7ccf023f809b448c15a5553d"],
  ["flow-emoji-post-composer.json", "cceaef6f05a3ddf4457a905d217f2d0f9e9bcc8aab6471580881fb02d8dc007b"],
]);

const receipts = await Promise.all([...receiptHashes].map(async ([name, expectedHash]) => {
  const source = await read(`../docs/candidate-attestations/${name}`);
  return { name, expectedHash, source, value: JSON.parse(source) };
}));
const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");
const packageJson = JSON.parse(await read("../package.json"));
const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const trackedKotlinFiles = execFileSync("git", ["ls-files", "--", "*.kt"], {
  cwd: repositoryRoot,
  encoding: "utf8",
}).split(/\r?\n/).filter(Boolean);
const productionKotlinFiles = trackedKotlinFiles.filter((path) =>
  /(?:^|\/)src\/(?:main|[^/]+Main)\//.test(path) &&
  !path.endsWith("CommunityEmojiPanelContent.kt")
);
const invokesCommunityEmojiPanel = (source) => {
  const aliases = [...source.matchAll(/import\s+[^\n]*\.CommunityEmojiPanelContent\s+as\s+(\w+)/g)]
    .map((match) => match[1]);
  return /\bCommunityEmojiPanelContent\s*\(/m.test(source) ||
    aliases.some((alias) => new RegExp(`\\b${alias}\\s*\\(`, "m").test(source));
};
const panelCallsites = [];
for (const path of productionKotlinFiles) {
  const source = await readFile(resolve(repositoryRoot, path), "utf8");
  if (invokesCommunityEmojiPanel(source)) panelCallsites.push(path.replaceAll("\\", "/"));
}
panelCallsites.sort();

test("FLOW-EMOJI composition binds the six exact passed owner receipts", () => {
  assert.equal(receipts.length, 6);
  for (const { name, expectedHash, source, value } of receipts) {
    assert.equal(sha256(source), expectedHash, `${name} hash drift`);
    assert.ok(value.units.includes("FLOW-EMOJI"), `${name} unit`);
    const statuses = Object.values(value.evidence ?? {}).map((entry) => entry?.status).filter(Boolean);
    assert.ok(statuses.length >= 3, `${name} evidence count`);
    assert.ok(statuses.every((status) => status === "passed"), `${name} evidence status`);
  }
});

test("FLOW-EMOJI source extraction closes every product callsite", () => {
  assert.deepEqual(panelCallsites, [
    "app/src/main/java/com/quata/core/ui/components/CommunityEmojiPanel.kt",
    "feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerAndActionsContent.kt",
    "feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt",
    "feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentsDialogContent.kt",
    "feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialCommentsPanelContent.kt",
    "feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/CreatePostRoot.kt",
  ]);
});

test("FLOW-EMOJI source extraction recognizes formatting and import aliases", () => {
  assert.equal(invokesCommunityEmojiPanel(`CommunityEmojiPanelContent
    (state)`), true);
  assert.equal(invokesCommunityEmojiPanel(`
    import com.quata.core.ui.components.CommunityEmojiPanelContent as EmojiPanel
    fun render() = EmojiPanel\n(state)
  `), true);
  assert.equal(invokesCommunityEmojiPanel("fun render() = OtherPanel(state)"), false);
});

test("FLOW-EMOJI closes its finite product-consumer catalog", () => {
  const row = screenInventory.split(/\r?\n/).find((line) => line.startsWith("| `FLOW-EMOJI` |"));
  assert.ok(row);
  assert.match(row, /GO compuesto de flujo/);
  assert.match(row, /Chat, los comentarios de Feed, Official y perfil público, y Post Composer forman el catálogo finito/);
  assert.match(row, /No queda un residual interno genérico/);
  assert.match(platformInventory, /`FLOW-EMOJI` queda en GO compuesto/);
  assert.match(migrationBoard, /Emoji compuesto/);
  assert.match(migrationBoard, /Seis recibos propietarios/);
  assert.match(migrationBoard, /No atribuye ejecución nueva/);
});

test("FLOW-EMOJI preserves transport boundaries without inventing consumers", () => {
  const row = screenInventory.split(/\r?\n/).find((line) => line.startsWith("| `FLOW-EMOJI` |"));
  assert.match(row, /fallos de proveedor\/red, publicación o comentarios/);
  assert.doesNotMatch(migrationBoard, /No declara GO global de `FLOW-EMOJI`/);
  assert.doesNotMatch(migrationBoard, /queda comparativa visual más amplia/);
});

test("FLOW-EMOJI composition contract is wired exactly once into each affected aggregate", () => {
  const script = "scripts/emoji-root-composition-reconciliation-contract.test.mjs";
  for (const aggregate of ["test:web-wave2-contracts", "test:ci-fast-contracts"]) {
    assert.equal(packageJson.scripts[aggregate].split(script).length - 1, 1, `${aggregate} wiring count`);
  }
});
