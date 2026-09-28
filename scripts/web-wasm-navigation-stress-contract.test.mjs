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
  assert.match(runner, /navigationStressFailure = \{ pageErrors, unexpectedConsoleErrors \}/);
  assert.match(runner, /entry === "console:error:Failed to load resource: net::ERR_BLOCKED_BY_CLIENT\.Inspector"/);
  assert.match(runner, /knownInspectorBlockedClientErrors: knownInspectorBlockedClientErrors\.length/);
  assert.match(runner, /unexpectedConsoleErrors: unexpectedConsoleErrors\.length, uncaughtExceptions: pageErrors\.length/);
  assert.match(runner, /data-quata-shell-route/);
  assert.match(runner, /data-quata-primary-selected-route/);
  assert.match(runner, /captureShellScreenshot/);
});

test("paged inbox budget measures the ordered navigation-stress delta", () => {
  assert.match(runner, /const NAVIGATION_STRESS_CYCLES = 50/);
  assert.match(runner, /const PAGED_INBOX_READS_PER_CHAT_MOUNT = 3/);
  assert.match(runner, /const MAX_AUTHENTICATED_PAGED_INBOX_READS =\s+NAVIGATION_STRESS_CYCLES \* 18 \+ PAGED_INBOX_READS_PER_CHAT_MOUNT/);
  assert.match(
    runner,
    /stage = "authenticated_navigation_stress_prepare_history";\s+await prepareAuthenticatedNavigationStress\(page\);\s+stage = "authenticated_navigation_stress_baseline";\s+const pagedInboxReadsBeforeNavigationStress = await waitForCounterQuiescence\(\s*\(\) => productReadEvidence\.pagedInboxReads,?\s*\);\s+stage = "authenticated_navigation_stress";\s+report\.navigationStress = await runAuthenticatedNavigationStress\(page, browserDiagnostics\);\s+const navigationStressPagedInboxReads =\s+productReadEvidence\.pagedInboxReads - pagedInboxReadsBeforeNavigationStress;\s+report\.navigationStress\.pagedInboxReads = navigationStressPagedInboxReads;[\s\S]*?if \(navigationStressPagedInboxReads > MAX_AUTHENTICATED_PAGED_INBOX_READS\)/,
  );
  assert.match(runner, /const MAX_AUTHENTICATED_PAGED_INBOX_READS =\s+NAVIGATION_STRESS_CYCLES \* 18 \+ PAGED_INBOX_READS_PER_CHAT_MOUNT/);
  assert.match(runner, /async function prepareAuthenticatedNavigationStress\(page\)[\s\S]*?seedStressHistoryFragment/);
  assert.doesNotMatch(runner, /if \(cycle === 1\)[\s\S]*?seedStressHistoryFragment/);
});

test("the shared feed pager never indexes an empty post list", () => {
  assert.match(feedPager, /if \(!canRenderFeedPager\(posts\)\) return/);
  assert.match(feedPager, /internal fun canRenderFeedPager\(posts: List<Post>\): Boolean = posts\.isNotEmpty\(\)/);
  assert.match(feedPager, /val post = posts\[page\]/);
});
