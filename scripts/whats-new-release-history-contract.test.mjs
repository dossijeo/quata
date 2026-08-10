import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const releaseHistory = await source('../feature/whatsnew/src/commonMain/kotlin/com/quata/feature/whatsnew/presentation/ReleaseHistoryContent.kt');
const aboutDialog = await source('../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataAboutDialogContent.kt');
const android = await source('../app/src/main/java/com/quata/feature/whatsnew/presentation/ReleaseHistoryScreen.kt');
const androidNav = await source('../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt');
const web = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebWhatsNewHost.kt');
const webMain = await source('../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt');
const ios = await source('../feature/whatsnew/src/iosMain/kotlin/com/quata/feature/whatsnew/presentation/QuataWhatsNewViewController.kt');
const iosRuntime = await source('../feature/whatsnew/src/iosMain/kotlin/com/quata/feature/whatsnew/presentation/IosWhatsNewRuntimeBootstrap.kt');
const iosSwift = await source('../iosApp/iosApp/QuataIosApp.swift');
const iosSwiftTests = await source('../iosApp/iosAppTests/QuataFeedFrameworkTests.swift');
const iosHostUiTests = await source('../iosApp/iosAppUITests/QuataIosHostUITests.swift');
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

test('About opens the common dialog and links to Release History on Android, Web and iOS', () => {
  assert.match(aboutDialog, /fun QuataAboutDialogContent\(/);
  assert.match(aboutDialog, /const val QuataAboutRootTestTag = "about-common-root"/);
  assert.match(aboutDialog, /const val QuataAboutBodyTestTag = "about-common-body"/);
  assert.match(aboutDialog, /const val QuataAboutReleaseHistoryTestTag = "about-release-history"/);
  assert.match(aboutDialog, /const val QuataAboutCloseTestTag = "about-close"/);
  assert.match(aboutDialog, /modifier = Modifier\.testTag\(QuataAboutRootTestTag\)/);
  assert.match(aboutDialog, /testTag\(QuataAboutBodyTestTag\)/);
  assert.match(aboutDialog, /testTag\(QuataAboutReleaseHistoryTestTag\)/);
  assert.match(aboutDialog, /testTag\(QuataAboutCloseTestTag\)/);
  assert.match(aboutDialog, /TextButton\([\s\S]*?onClick = onOpenReleaseHistory,[\s\S]*?modifier = Modifier\.testTag\(QuataAboutReleaseHistoryTestTag\),[\s\S]*?\) \{ Text\(releaseHistoryLabel\) \}/);
  assert.doesNotMatch(aboutDialog, /onOpenReleaseHistory\s*=\s*\{\s*(?:Unit)?\s*\}|onOpenReleaseHistory\s*=\s*(?:noop|Noop|NOOP)/);

  assert.match(androidNav, /AboutQuataDialog\([\s\S]*?onOpenReleaseHistory = \{/);
  assert.match(androidNav, /QuataAboutDialogContent\([\s\S]*?onOpenReleaseHistory = onOpenReleaseHistory,/);

  assert.match(web, /enum class WebWhatsNewDestination \{ PendingReleases, About, ReleaseHistory \}/);
  assert.match(web, /"about" -> WebWhatsNewDestination\.About/);
  assert.doesNotMatch(web, /"about", "release-history" -> WebWhatsNewDestination\.ReleaseHistory/);
  assert.match(web, /WebWhatsNewDestination\.About -> QuataAboutDialogContent\(/);
  assert.match(web, /onOpenReleaseHistory = \{ webSetBrowserFragment\("release-history"\) \}/);
  assert.match(webMain, /var whatsNewReturnFragment by remember \{ mutableStateOf<String\?>\(null\) \}/);
  assert.match(webMain, /onLogoClick = \{[\s\S]*?if \(webWhatsNewDestination\(navigation\.route\) == null\) \{[\s\S]*?whatsNewReturnFragment = navigation\.fragment[\s\S]*?\}[\s\S]*?navigation\.navigate\("about"\)/);

  assert.match(ios, /fun QuataAboutViewController\(dependencies: IosAboutHostDependencies\): UIViewController/);
  assert.match(iosRuntime, /fun QuataIosAboutViewController\(/);
  assert.match(iosRuntime, /onOpenReleaseHistory = onOpenReleaseHistory/);
  assert.match(iosRuntime, /legalLinks = \{ IosAboutLegalLinks\(runtime\.languageTags\) \}/);
  assert.match(iosSwift, /private var aboutFactory: \(\(\) -> UIViewController\)\?/);
  assert.match(iosSwift, /case "about":\s+IosAuthenticatedRouteDispatcher\(host: router\)\.openAbout\(\)/);
  assert.match(iosSwift, /onLogoClick: \{ \[weak self\] in self\?\.showAbout\(\) \}/);
  assert.match(iosSwift, /func installAboutFactory\(_ factory: @escaping \(\) -> UIViewController\)/);
  assert.match(iosSwift, /func showAbout\(\) \{ route\(\.about\) \}/);
  assert.match(iosSwift, /case \.about:\s+return aboutFactory\?\(\)/);
  assert.match(iosSwift, /case \.about:\s+presentation = \("quata-ios-about-host", "Quata iOS About", nil\)/);
  assert.doesNotMatch(iosSwift, /onLogoClick:\s*\{\s*\}/);

  assert.match(iosSwiftTests, /testAuthenticatedRouteMenuExposesWhatsNewAndAboutOnlyAfterTheirLocalFactoriesAreInstalled/);
  assert.match(iosHostUiTests, /"https:\/\/egquata\.com\/#about", "quata-ios-about-host", "Quata iOS About", "fixture-about"/);
  assert.match(iosSwiftTests, /dispatcher\.handleUrl\(url: "https:\/\/egquata\.com\/#about"\)[\s\S]*?XCTAssertEqual\(host\.route, \.about\)/);
  assert.match(iosSwiftTests, /router\.installWhatsNewFactory \{ UIViewController\(\) \}[\s\S]*?XCTAssertFalse\(afterInstall\.actions\.contains \{ \$0\.title == "Acerca de Quata" \}\)[\s\S]*?router\.installAboutFactory \{ UIViewController\(\) \}[\s\S]*?XCTAssertTrue\(afterAboutInstall\.actions\.contains \{ \$0\.title == "Acerca de Quata" \}\)/);
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
