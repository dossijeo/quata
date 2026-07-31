import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile(new URL("./web-authenticated-browser-e2e.mjs", import.meta.url), "utf8");
const feedPager = await readFile(new URL("../feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedReelPagerContent.kt", import.meta.url), "utf8");

test("authenticated Wasm navigation stress covers every contract sequence for fifty cycles", () => {
  assert.match(runner, /const NAVIGATION_STRESS_CYCLES = 50/);
  for (const sequence of ["primary_forward", "primary_reverse", "feed_official_toggle", "communities_chat_toggle", "browser_back_forward", "direct_fragments"]) {
    assert.match(runner, new RegExp(`name: "${sequence}"`));
  }
  assert.match(runner, /page\.goBack\(\)/);
  assert.match(runner, /page\.goForward\(\)/);
  assert.match(runner, /assertHealthyAuthenticatedShell/);
  assert.match(runner, /unexpectedConsoleErrors: unexpectedConsoleErrors\.length, uncaughtExceptions: pageErrors\.length/);
  assert.match(runner, /data-quata-shell-route/);
  assert.match(runner, /data-quata-primary-selected-route/);
  assert.match(runner, /captureShellScreenshot/);
});

test("the shared feed pager never indexes an empty post list", () => {
  assert.match(feedPager, /if \(!canRenderFeedPager\(posts\)\) return/);
  assert.match(feedPager, /internal fun canRenderFeedPager\(posts: List<Post>\): Boolean = posts\.isNotEmpty\(\)/);
  assert.match(feedPager, /val post = posts\[page\]/);
});
