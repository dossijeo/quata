import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("../", import.meta.url);
const source = async (relativePath) => readFile(new URL(relativePath, root), "utf8");

async function kotlinFiles(relativeDirectory) {
  const absoluteDirectory = new URL(`${relativeDirectory}/`, root);
  const entries = await readdir(absoluteDirectory, { withFileTypes: true });
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

const entryPoints = [
  "CommunityProfileCommentsDialogContent",
  "FeedCommentsDialog",
  "OfficialCommentsPanelEntryContent",
];

function extractCommentsOwner(file, text) {
  const ownsEntry = entryPoints.some((entryPoint) => {
    const calls = [...text.matchAll(new RegExp(`${entryPoint}\\(`, "g"))].length;
    const definitions = [...text.matchAll(new RegExp(`fun\\s+${entryPoint}\\(`, "g"))].length;
    return calls > definitions;
  });
  return ownsEntry ? file : null;
}

async function commentsOwners() {
  const files = (await Promise.all([
    kotlinFiles("app/src/main"),
    kotlinFiles("feature"),
    kotlinFiles("designsystem/src/commonMain"),
    kotlinFiles("web/src"),
    kotlinFiles("ios-shared/src"),
  ])).flat();
  const owners = [];
  for (const file of files) {
    if (file.includes("/commonTest/") || file.includes("/androidTest/") || file.includes("/iosTest/")) continue;
    const owner = extractCommentsOwner(file, await source(file));
    if (owner) owners.push(owner);
  }
  return owners.sort();
}

const manifestNames = [
  "comments-deep-pagination.json",
  "comments-replies-pagination.json",
  "comments-profile-translator.json",
  "profile-comments-translator-postflight.json",
  "feed-official-comments-emoji.json",
  "feed-official-comments-translator.json",
  "flow-emoji-profile-comments.json",
  "flow-emoji-error-rollback.json",
  "flow-emoji-selector-states.json",
  "flow-emoji-visual-comparison.json",
  "ios-comment-keyboard-opacity.json",
];

const [feed, official, profile, profilePager, screenInventory, board, ...manifestTexts] = await Promise.all([
  source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt"),
  source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfilePostsPagerContent.kt"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  source("docs/MULTIPLATFORM_MIGRATION_BOARD.md"),
  ...manifestNames.map((name) => source(`docs/candidate-attestations/${name}`)),
]);

test("the product comments owner catalog is finite and exact", async () => {
  assert.deepEqual(await commentsOwners(), [
    "feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt",
    "feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt",
    "feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt",
  ]);
});

test("each catalog owner opens its comments surface from the exact selected post", () => {
  assert.match(feed, /onOpenComments = \{ commentsPostId = post\.id \}/);
  assert.match(feed, /state\.posts\.firstOrNull \{ it\.id == commentsPostId \}[\s\S]*?FeedCommentsDialog\([\s\S]*?post = post/);
  assert.match(official, /OfficialCommentsPanelEntryContent\(state\.posts\.firstOrNull \{ it\.id == commentsPost \}/);
  assert.match(profile, /commentsDialog = \{ post, addComment, dismiss ->[\s\S]*?CommunityProfileCommentsDialogContent\([\s\S]*?post = post/);
  assert.match(profilePager, /commentsPostId = post\.id[\s\S]*?commentsPostSnapshot = post/);
  assert.match(profilePager, /commentsPostId\?\.let \{ postId ->[\s\S]*?posts\.firstOrNull \{ it\.id == postId \}[\s\S]*?commentsDialog\(/);
});

test("the exact owner catalog rejects a fourth product comments surface through the real extractor", async () => {
  const owners = await commentsOwners();
  const syntheticPath = "feature/detached/src/commonMain/DetachedCommentsHost.kt";
  const syntheticOwner = extractCommentsOwner(
    syntheticPath,
    "@Composable fun DetachedCommentsHost() { FeedCommentsDialog() }",
  );
  assert.equal(syntheticOwner, syntheticPath);
  assert.throws(
    () => assert.deepEqual([...owners, syntheticOwner].sort(), owners),
    assert.AssertionError,
  );
});

test("all composed focal comments receipts remain passed", () => {
  manifestTexts.forEach((text, index) => {
    const manifest = JSON.parse(text);
    const statuses = Object.values(manifest.evidence ?? {}).map((entry) => entry.status);
    assert.ok(statuses.length > 0, `${manifestNames[index]} evidence`);
    assert.ok(statuses.every((status) => status === "passed"), `${manifestNames[index]} status`);
    assert.ok(manifest.productSha, `${manifestNames[index]} productSha`);
  });
});

test("inventories close the generic origin residual and preserve thematic boundaries", () => {
  const row = screenInventory.split("\n").find((line) => line.startsWith("| `OVR-COMMENTS` |"));
  assert.ok(row, "OVR-COMMENTS row missing");
  assert.match(row, /GO compuesto de overlay/);
  assert.match(row, /Feed, Official y perfil público/);
  assert.doesNotMatch(row, /orígenes ajenos a Feed\/Official\/perfil público/);
  assert.match(row, /fallos de red no focales/);
  assert.match(row, /operaciones de comentario no incluidas/);
  assert.match(board, /OVR-COMMENTS[^\n]*GO compuesto de overlay/);
});
