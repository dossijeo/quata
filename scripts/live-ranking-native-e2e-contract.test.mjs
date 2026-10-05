import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8');

const [androidFeed, androidOfficial, iosFeed, iosOfficial, swiftHost, swiftTest, iosWorkflow, webAndroidWorkflow, androidVerifier] = await Promise.all([
  read('app/src/androidTest/java/com/quata/feature/feed/presentation/FeedRemoteRankingInstrumentedTest.kt'),
  read('app/src/androidTest/java/com/quata/feature/official/presentation/OfficialRemoteRankingInstrumentedTest.kt'),
  read('feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/IosFeedLiveRankingFixtureHost.kt'),
  read('feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/IosOfficialLiveRankingFixtureHost.kt'),
  read('iosApp/iosApp/QuataIosApp.swift'),
  read('iosApp/iosAppUITests/QuataIosLiveRankingUITests.swift'),
  read('.github/workflows/ios-build.yml'),
  read('.github/workflows/web-android-pr.yml'),
  read('scripts/verify-live-ranking-android-results.mjs'),
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
  assert.match(swiftTest, /label BEGINSWITH %@.*LIVE/);
  assert.match(swiftTest, /for _ in 0\.\.<4[\s\S]*?allElementsBoundByIndex\.first\(where: \\.isHittable\)/);
  assert.doesNotMatch(swiftTest, /coordinate\(|CGVector|press\(forDuration/);
});

test('iOS CI runs the focal class once and requires both named XCTest passes', () => {
  assert.match(iosWorkflow, /run_watchdog 420 build\/reports\/ios\/xcodebuild-live-ranking-tests\.log xcodebuild[\s\S]*?-only-testing:QuataIosUITests\/QuataIosLiveRankingUITests/);
  for (const name of [
    'testFeedRemoteRankingFailsClosedRetriesAndOpensExactTarget',
    'testOfficialRemoteRankingFailsClosedRetriesAndOpensExactTarget',
  ]) assert.ok(iosWorkflow.includes(name));
  assert.match(iosWorkflow, /grep -F "QuataIosLiveRankingUITests \$test_name"[\s\S]*?grep -F "passed"/);
  assert.match(iosWorkflow, /grep -Ei "skipped\|disabled"/);
  assert.match(iosWorkflow, /-skip-testing:QuataIosUITests\/QuataIosLiveRankingUITests/);
});

test('Android CI runs both focal classes and verifies their exact JUnit passes', () => {
  assert.match(webAndroidWorkflow, /name: Run native live Ranking focal instrumentation[\s\S]*?uses: reactivecircus\/android-emulator-runner@v2/);
  assert.match(webAndroidWorkflow, /api-level: 35[\s\S]*?disable-animations: true/);
  assert.match(webAndroidWorkflow, /:app:connectedDebugAndroidTest[\s\S]*?FeedRemoteRankingInstrumentedTest,com\.quata\.feature\.official\.presentation\.OfficialRemoteRankingInstrumentedTest/);
  assert.match(webAndroidWorkflow, /node scripts\/verify-live-ranking-android-results\.mjs[\s\S]*?app\/build\/outputs\/androidTest-results\/connected\/debug/);
  assert.match(webAndroidWorkflow, /node --test scripts\/live-ranking-native-e2e-contract\.test\.mjs scripts\/verify-live-ranking-android-results\.test\.mjs/);
  assert.match(androidVerifier, /live_ranking_android_junit_missing/);
  assert.match(androidVerifier, /live_ranking_android_not_passed/);
  assert.match(androidVerifier, /live_ranking_android_missing/);
  assert.match(androidVerifier, /live_ranking_android_duplicate/);
});
