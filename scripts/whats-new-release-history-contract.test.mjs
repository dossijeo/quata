import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const releaseHistory = await source('../feature/whatsnew/src/commonMain/kotlin/com/quata/feature/whatsnew/presentation/ReleaseHistoryContent.kt');
const android = await source('../app/src/main/java/com/quata/feature/whatsnew/presentation/ReleaseHistoryScreen.kt');
const web = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebWhatsNewHost.kt');
const ios = await source('../feature/whatsnew/src/iosMain/kotlin/com/quata/feature/whatsnew/presentation/QuataWhatsNewViewController.kt');
const iosRuntime = await source('../feature/whatsnew/src/iosMain/kotlin/com/quata/feature/whatsnew/presentation/IosWhatsNewRuntimeBootstrap.kt');
const packageJson = JSON.parse(await source('../package.json'));
const webAndroidWorkflow = await source('../.github/workflows/web-android-pr.yml');
const iosWorkflow = await source('../.github/workflows/ios-build.yml');

test('Release History stays common, inspectable and scrollable across hosts', () => {
  assert.match(releaseHistory, /const val ReleaseHistoryRootTestTag = "release-history-common-root"/);
  assert.match(releaseHistory, /const val ReleaseHistoryCloseTestTag = "release-history-close"/);
  assert.match(releaseHistory, /const val ReleaseHistoryPreviousTestTag = "release-history-previous"/);
  assert.match(releaseHistory, /const val ReleaseHistoryNextTestTag = "release-history-next"/);
  assert.match(releaseHistory, /const val ReleaseHistoryPageTestTag = "release-history-page"/);
  assert.match(releaseHistory, /const val ReleaseHistoryPageTestTagPrefix = "\$ReleaseHistoryPageTestTag-"/);
  assert.match(releaseHistory, /modifier\.fillMaxSize\(\)\.testTag\(ReleaseHistoryRootTestTag\)/);
  assert.match(releaseHistory, /testTag\(ReleaseHistoryCloseTestTag\)/);
  assert.match(releaseHistory, /testTag\(ReleaseHistoryPreviousTestTag\)/);
  assert.match(releaseHistory, /testTag\(ReleaseHistoryNextTestTag\)/);
  assert.match(releaseHistory, /HorizontalPager\(pagerState, Modifier\.weight\(1f\)\) \{ page -> ReleaseHistoryPage\(releases\[page\], strings, page, Modifier\.fillMaxSize\(\)\) \}/);
  assert.match(releaseHistory, /Column\(modifier\.verticalScroll\(rememberScrollState\(\)\)\.padding\(horizontal = 4\.dp\)\.testTag\("\$ReleaseHistoryPageTestTagPrefix\$page"\)\)/);
  assert.doesNotMatch(releaseHistory, /testTag\(ReleaseHistoryPageTestTag\)/);

  for (const [name, content] of Object.entries({ android, web, ios })) {
    assert.match(content, /ReleaseHistoryContent\(/, `${name} must mount the common ReleaseHistoryContent`);
    assert.doesNotMatch(content, /onBack\s*=\s*\{\s*(?:Unit)?\s*\}|onBack\s*=\s*(?:noop|Noop|NOOP)/, `${name} must not wire inert close/back`);
  }
  assert.match(android, /ReleaseHistoryContent\(repository, languageTags, strings, onBack,/);
  assert.match(web, /WebWhatsNewDestination\.ReleaseHistory -> ReleaseHistoryContent\([\s\S]*?onBack = onBack,/);
  assert.match(ios, /ReleaseHistoryContent\([\s\S]*?onBack = dependencies\.onBack,/);
  assert.match(iosRuntime, /QuataIosReleaseHistoryViewController\(/);
  assert.match(iosRuntime, /onClose: \(\) -> Unit/);
  assert.match(iosRuntime, /onBack = onClose/);
  assert.doesNotMatch(iosRuntime, /onClose\s*=\s*\{\s*(?:Unit)?\s*\}|onClose\s*=\s*(?:noop|Noop|NOOP)/);
});

test('Release History parity contract runs in local scripts and CI workflows', () => {
  for (const scriptName of ['test:ci-fast-contracts', 'test:web-wave2-contracts']) {
    assert.match(packageJson.scripts[scriptName], /scripts\/whats-new-release-history-contract\.test\.mjs/);
  }
  assert.match(webAndroidWorkflow, /node --test scripts\/whats-new-release-history-contract\.test\.mjs/);
  assert.match(iosWorkflow, /node --test scripts\/whats-new-release-history-contract\.test\.mjs/);
});

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8');
}
