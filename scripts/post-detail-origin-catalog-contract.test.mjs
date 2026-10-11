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

function extractDetailChromeOwner(file, text) {
  const calls = [...text.matchAll(/QuataPostDetailChromeContent\(/g)].length;
  const definitions = [...text.matchAll(/fun\s+QuataPostDetailChromeContent\(/g)].length;
  return calls > definitions ? file : null;
}

async function detailChromeOwners() {
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
    const owner = extractDetailChromeOwner(file, await source(file));
    if (owner) owners.push(owner);
  }
  return owners.sort();
}

const manifestNames = [
  "post-detail-parity.json",
  "post-detail-profile-actions.json",
  "post-detail-actions.json",
  "post-detail-media.json",
  "feed-media-viewer.json",
  "feed-video-viewer.json",
  "official-video-viewer.json",
  "public-profile-post-detail.json",
  "official-post-detail-scroll-restoration.json",
  "feed-text-reader-scroll-restoration.json",
  "public-profile-post-detail-scroll-restoration.json",
  "feed-video-position-restoration.json",
  "official-video-position-restoration.json",
];

const [feed, official, profile, pager, screenInventory, board, ...manifestTexts] = await Promise.all([
  source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt"),
  source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfilePostsPagerContent.kt"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  source("docs/MULTIPLATFORM_MIGRATION_BOARD.md"),
  ...manifestNames.map((name) => source(`docs/candidate-attestations/${name}`)),
]);

test("the product post-detail host catalog is finite and exact", async () => {
  assert.deepEqual(await detailChromeOwners(), [
    "feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt",
    "feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt",
    "feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt",
  ]);
});

test("Feed and Official deep links, Live and Ranking converge on their existing focused-post hosts", () => {
  for (const host of [feed, official]) {
    assert.match(host, /var localFocusedPostId by rememberSaveable\(focusedPostId\)/);
    assert.match(host, /val activeFocusedPostId = localFocusedPostId/);
    assert.match(host, /val detailPost = activeFocusedPostId\?\.let/);
    assert.match(host, /QuataPostDetailChromeContent\(/);
    assert.match(host, /rankingTargetPostId/);
    assert.match(host, /liveOpen/);
  }
  assert.match(feed, /onOpenPost = \{ post ->[\s\S]*?localFocusedPostId = post\.id[\s\S]*?onFocusedPostChanged\(post\.id\)[\s\S]*?rankingTargetPostId = post\.id/);
  assert.match(official, /dispatchOfficialLiveSelection\([\s\S]*?focusedPostId = activeFocusedPostId[\s\S]*?onFocusedDetail = \{ postId ->[\s\S]*?localFocusedPostId = postId[\s\S]*?onFocusedPostChanged\(postId\)[\s\S]*?rankingTargetPostId = postId/);
});

test("public-profile detail is the third owner and retains exact post and scroll identity", () => {
  assert.match(profile, /detailChrome = \{ post, back ->[\s\S]*?QuataPostDetailChromeContent\(/);
  assert.match(pager, /var detailPostId by rememberSaveable/);
  assert.match(pager, /posts\.firstOrNull \{ it\.id == id \}/);
  assert.match(pager, /detailChrome\(detailPost\) \{ detailPostId = null \}/);
  assert.match(pager, /PublicProfilePostDetailScrollAnchor\(detailPost\.id, offset\)/);
});

test("the exact owner catalog guard rejects a fourth product detail host through the real extractor", async () => {
  const owners = await detailChromeOwners();
  const syntheticPath = "feature/detached/src/commonMain/DetachedDetailHost.kt";
  const extractedSyntheticOwner = extractDetailChromeOwner(
    syntheticPath,
    "@Composable fun DetachedDetailHost() { QuataPostDetailChromeContent() }",
  );
  assert.equal(extractedSyntheticOwner, syntheticPath);
  assert.throws(
    () => assert.deepEqual([...owners, extractedSyntheticOwner].sort(), owners),
    assert.AssertionError,
  );
});

test("all composed focal receipts remain passed", () => {
  manifestTexts.forEach((text, index) => {
    const manifest = JSON.parse(text);
    assert.equal(manifest.status, "passed", `${manifestNames[index]} status`);
    assert.ok(manifest.productSha, `${manifestNames[index]} productSha`);
  });
});

test("inventories close the generic origin residual and preserve thematic boundaries", () => {
  const row = screenInventory.split("\n").find((line) => line.startsWith("| `OVR-POST-DETAIL` |"));
  assert.ok(row, "OVR-POST-DETAIL row missing");
  assert.match(row, /GO compuesto de overlay/);
  assert.match(row, /Feed, Official y perfil público/);
  assert.doesNotMatch(row, /los demás orígenes conservan su frontera|otros orígenes de restauración profunda/);
  assert.match(row, /comentarios integrados/);
  assert.match(row, /codecs exhaustivos/);
  assert.match(board, /OVR-POST-DETAIL[^\n]*GO compuesto de overlay/);
  assert.doesNotMatch(board, /No declara GO global de `OVR-POST-DETAIL`/);
});
