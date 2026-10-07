import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8');

const [androidFeed, androidOfficial, androidOfficialPagination, iosFeed, iosOfficial, swiftHost, swiftTest, iosWorkflow, webAndroidWorkflow, androidVerifier, androidRunner] = await Promise.all([
  read('app/src/androidTest/java/com/quata/feature/feed/presentation/FeedRemoteRankingInstrumentedTest.kt'),
  read('app/src/androidTest/java/com/quata/feature/official/presentation/OfficialRemoteRankingInstrumentedTest.kt'),
  read('app/src/androidTest/java/com/quata/feature/official/presentation/OfficialDeepPaginationInstrumentedTest.kt'),
  read('feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/IosFeedLiveRankingFixtureHost.kt'),
  read('feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/IosOfficialLiveRankingFixtureHost.kt'),
  read('iosApp/iosApp/QuataIosApp.swift'),
  read('iosApp/iosAppUITests/QuataIosLiveRankingUITests.swift'),
  read('.github/workflows/ios-build.yml'),
  read('.github/workflows/web-android-pr.yml'),
  read('scripts/verify-live-ranking-android-results.mjs'),
  read('scripts/run-live-ranking-android-e2e.sh'),
]);

test('Android native hosts execute complete remote Ranking through real ViewModels', () => {
  for (const [source, viewModel, host, errorTag, retryTag, target] of [
    [androidFeed, 'FeedViewModel(repository)', 'FeedScreenHost(', 'FeedRankingErrorTestTag', 'FeedRankingRetryTestTag', 'AndroidFeedRemoteTargetId'],
    [androidOfficial, 'OfficialFeedViewModel(repository)', 'OfficialFeedScreenHost(', 'OfficialRankingErrorTestTag', 'OfficialRankingRetryTestTag', 'AndroidOfficialRemoteTargetId'],
  ]) {
    assert.ok(source.includes(viewModel));
    assert.ok(source.includes(host));
    assert.ok(source.includes('onAllNodesWithContentDescription("LIVE")[0].performClick()'));
    assert.ok(source.includes('uiState.value.posts.size == 50'));
    assert.ok(source.includes('uiState.value.rankingError != null'));
    assert.ok(source.includes(`onNodeWithTag(${errorTag}).assertIsDisplayed()`));
    assert.ok(source.includes(`onNodeWithTag(${retryTag}).performClick()`));
    assert.ok(source.includes('uiState.value.rankingPosts?.size == 101'));
    assert.ok(source.includes(`live.ranking.open.$${target}`));
    assert.ok(source.includes('assertEquals(3, '));
    assert.match(source, /private val first = allPosts\.subList\(0, 50\)[\s\S]*?private val second = allPosts\.subList\(50, 100\)[\s\S]*?private val final = allPosts\.subList\(100, 101\)/);
    assert.match(source, /private var failOnce = true[\s\S]*?failOnce = false[\s\S]*?Result\.failure/);
  }
});

test('Android and iOS native Official pagers preserve content, retry and reach the deep target', () => {
  assert.match(androidOfficialPagination, /repeat\(43\)[\s\S]*?performTouchInput \{ swipeUp\(\) \}/);
  assert.match(androidOfficialPagination, /olderPageError != null[\s\S]*?OfficialOlderPostsErrorTestTag/);
  assert.match(androidOfficialPagination, /assertEquals\(50, model\.uiState\.value\.posts\.size\)[\s\S]*?OfficialOlderPostsRetryTestTag[\s\S]*?posts\.size == 100/);
  assert.match(androidOfficialPagination, /AndroidOfficialRemoteTargetTitle[\s\S]*?assertIsDisplayed\(\)/);
  assert.ok(swiftTest.includes('testOfficialNativePagerPreservesFirstPageRetriesAndReachesDeepTarget'));
  assert.match(swiftTest, /testOfficialNativePagerPreservesFirstPageRetriesAndReachesDeepTarget\(\)[\s\S]*?executionTimeAllowance = 180/);
  assert.ok(swiftTest.includes('official-feed-common-state.created.none.count.50'));
  assert.ok(swiftTest.includes('official-feed-common-state.created.none.count.100'));
  assert.match(swiftTest, /official-older-posts-error[\s\S]*?official-older-posts-retry[\s\S]*?retry\.tap\(\)/);
  assert.match(swiftTest, /Official remote ranking target loaded exactly[\s\S]*?advancePager\(pager\)/);
  assert.match(swiftTest, /private func advancePager[\s\S]*?withNormalizedOffset[\s\S]*?thenDragTo: end[\s\S]*?withVelocity: \.fast/);
});

test('iOS fixtures replace only the read boundary and retain product hosts', () => {
  assert.match(iosFeed, /QuataFeedViewController\([\s\S]*?iosReadOnlyFeedHostDependencies\([\s\S]*?readRepository = repository/);
  assert.match(iosOfficial, /QuataOfficialViewController\([\s\S]*?IosOfficialHostDependencies\([\s\S]*?repository = IosOfficialLiveRankingFixtureRepository/);
  for (const source of [iosFeed, iosOfficial]) {
    assert.match(source, /private val allPosts = \(0\.\.100\)/);
    assert.match(source, /subList\(0, 50\)[\s\S]*?subList\(50, 100\)[\s\S]*?subList\(100, 101\)/);
    assert.match(source, /check\(limit == 50\)/);
    assert.match(source, /shouldFailOlderPage = false[\s\S]*?Result\.failure/);
    assert.match(source, /index == 50/);
    assert.match(source, /likesCount = if \(isRemoteTarget\) 10_000/);
  }
  assert.match(iosFeed, /refreshPost\(postId: String\)[\s\S]*?allPosts\.firstOrNull \{ it\.id == postId \}/);
  assert.match(iosOfficial, /getOfficialPost\(postId: String\)[\s\S]*?allPosts\.firstOrNull \{ it\.id == postId \}/);
  assert.doesNotMatch(iosOfficial, /Result\.success\([^)]*(createPost|deletePost|toggleLike|addComment)/);
});

test('Swift mounts both opt-in fixtures and verifies fail-closed retry and exact target', () => {
  for (const fixture of ['live-ranking-feed', 'live-ranking-official']) {
    assert.ok(swiftHost.includes(`case "${fixture}":`));
    assert.ok(swiftTest.includes(`fixture: "${fixture}"`));
  }
  assert.ok(swiftTest.includes('"-quata-live-ranking-fail-first-page"'));
  assert.ok(swiftTest.includes('XCTAssertFalse(targetOpen.exists'));
  assert.ok(swiftTest.includes('retry.tap()'));
  assert.ok(swiftTest.includes('targetOpen.tap()'));
  assert.ok(swiftTest.includes('XCTAssertFalse(error.exists'));
  assert.ok(swiftTest.includes('liveActionIdentifier: "feed.action.live.feed-ranking-fixture-0"'));
  assert.ok(swiftTest.includes('liveActionIdentifier: "official.action.live.official-ranking-fixture-0"'));
  assert.match(swiftTest, /let liveAction = element\(liveActionIdentifier, in: app\)[\s\S]*?XCTAssertTrue\(liveAction\.isHittable[\s\S]*?liveAction\.tap\(\)/);
  const rankingScenario = swiftTest.match(/private func runRemoteRankingScenario[\s\S]*?private func element/)?.[0] ?? '';
  assert.doesNotMatch(rankingScenario, /coordinate\(|CGVector|press\(forDuration/);
});

test('iOS CI runs the focal class once and requires both named XCTest passes', () => {
  assert.match(iosWorkflow, /run_watchdog 420 build\/reports\/ios\/xcodebuild-live-ranking-tests\.log xcodebuild[\s\S]*?-only-testing:QuataIosUITests\/QuataIosLiveRankingUITests/);
  for (const name of [
    'testFeedRemoteRankingFailsClosedRetriesAndOpensExactTarget',
    'testOfficialRemoteRankingFailsClosedRetriesAndOpensExactTarget',
    'testOfficialNativePagerPreservesFirstPageRetriesAndReachesDeepTarget',
  ]) assert.ok(iosWorkflow.includes(name));
  assert.match(iosWorkflow, /grep -F "QuataIosLiveRankingUITests \$test_name"[\s\S]*?grep -F "passed"/);
  assert.match(iosWorkflow, /grep -Ei "skipped\|disabled"/);
  assert.match(iosWorkflow, /-skip-testing:QuataIosUITests\/QuataIosLiveRankingUITests/);
});

test('Android CI runs both focal classes and verifies their exact JUnit passes', () => {
  assert.match(webAndroidWorkflow, /name: Enable Android emulator hardware acceleration[\s\S]*?if \[\[ -e \/dev\/kvm \]\]; then[\s\S]*?sudo chmod 0666 \/dev\/kvm[\s\S]*?test -r \/dev\/kvm[\s\S]*?test -w \/dev\/kvm/);
  assert.match(webAndroidWorkflow, /name: Run native live Ranking focal instrumentation\n\s+timeout-minutes: 45[\s\S]*?uses: reactivecircus\/android-emulator-runner@v2/);
  assert.match(webAndroidWorkflow, /api-level: 35[\s\S]*?disable-animations: true\n\s+emulator-boot-timeout: 900/);
  assert.match(webAndroidWorkflow, /script: bash scripts\/run-live-ranking-android-e2e\.sh/);
  assert.doesNotMatch(webAndroidWorkflow, /script: \|/);
  assert.match(androidRunner, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
  assert.match(androidRunner, /:app:connectedDebugAndroidTest[\s\S]*?FeedRemoteRankingInstrumentedTest,com\.quata\.feature\.official\.presentation\.OfficialRemoteRankingInstrumentedTest,com\.quata\.feature\.official\.presentation\.OfficialDeepPaginationInstrumentedTest/);
  assert.match(androidRunner, /node scripts\/verify-live-ranking-android-results\.mjs[\s\S]*?app\/build\/outputs\/androidTest-results\/connected\/debug/);
  assert.match(webAndroidWorkflow, /node --test scripts\/live-ranking-native-e2e-contract\.test\.mjs scripts\/verify-live-ranking-android-results\.test\.mjs/);
  assert.match(androidVerifier, /live_ranking_android_junit_missing/);
  assert.match(androidVerifier, /live_ranking_android_not_passed/);
  assert.match(androidVerifier, /live_ranking_android_missing/);
  assert.match(androidVerifier, /live_ranking_android_duplicate/);
});
