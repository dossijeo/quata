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
  assert.match(runner, /navigateHistory\(page, "back", index, expected\)/);
  assert.match(runner, /navigateHistory\(page, "forward", index, expected\)/);
  assert.match(runner, /assertHealthyAuthenticatedShell/);
  assert.match(runner, /unexpectedConsoleErrors: unexpectedConsoleErrors\.length, uncaughtExceptions: pageErrors\.length/);
  assert.match(runner, /data-quata-shell-route/);
  assert.match(runner, /data-quata-primary-selected-route/);
  assert.match(runner, /captureShellScreenshot/);
});

test("paged inbox budget measures the ordered navigation-stress delta", () => {
  assert.match(runner, /const NAVIGATION_STRESS_CYCLES = 50/);
  assert.match(runner, /const MAX_AUTHENTICATED_PAGED_INBOX_READS = NAVIGATION_STRESS_CYCLES \* 18/);
  assert.match(
    runner,
    /stage = "authenticated_navigation_stress";\s+const pagedInboxReadsBeforeNavigationStress = productReadEvidence\.pagedInboxReads;\s+report\.navigationStress = await runAuthenticatedNavigationStress\(page, browserDiagnostics\);\s+const navigationStressPagedInboxReads =\s+productReadEvidence\.pagedInboxReads - pagedInboxReadsBeforeNavigationStress;\s+report\.navigationStress\.pagedInboxReads = navigationStressPagedInboxReads;[\s\S]*?if \(navigationStressPagedInboxReads > MAX_AUTHENTICATED_PAGED_INBOX_READS\)/,
  );
});

test("the shared feed pager never indexes an empty post list", () => {
  assert.match(feedPager, /if \(!canRenderFeedPager\(posts\)\) return/);
  assert.match(feedPager, /internal fun canRenderFeedPager\(posts: List<Post>\): Boolean = posts\.isNotEmpty\(\)/);
  assert.match(feedPager, /val post = posts\[page\]/);
});
