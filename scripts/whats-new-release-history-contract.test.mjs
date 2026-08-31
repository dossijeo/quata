import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const releaseHistory = await source('../feature/whatsnew/src/commonMain/kotlin/com/quata/feature/whatsnew/presentation/ReleaseHistoryContent.kt');
const whatsNewContent = await source('../feature/whatsnew/src/commonMain/kotlin/com/quata/feature/whatsnew/presentation/WhatsNewContent.kt');
const whatsNewHost = await source('../feature/whatsnew/src/commonMain/kotlin/com/quata/feature/whatsnew/presentation/WhatsNewScreenHost.kt');
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
const androidEvidenceTest = await source('../app/src/androidTest/java/com/quata/feature/whatsnew/presentation/AboutReleaseHistoryInstrumentedTest.kt');
const androidWhatsNewEvidenceTest = await source('../app/src/androidTest/java/com/quata/feature/whatsnew/presentation/WhatsNewCommonInstrumentedTest.kt');
const webEvidenceRunner = await source('../scripts/about-release-history-web-evidence.mjs');
const androidEvidenceRunner = await source('../scripts/about-release-history-android-evidence.mjs');
const iosEvidenceRunner = await source('../scripts/about-release-history-ios-evidence.mjs');
const iosEvidenceShellRunner = await source('../scripts/run-ios-about-release-history-ui-test.sh');
const webWhatsNewEvidenceRunner = await source('../scripts/whats-new-web-evidence.mjs');
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

test("What's New stays common, inspectable and monotonic across evidence hosts", () => {
  assert.match(whatsNewContent, /const val WhatsNewRootTestTag = "whats-new-common-root"/);
  assert.match(whatsNewContent, /const val WhatsNewDismissTestTag = "whats-new-dismiss"/);
  assert.match(whatsNewContent, /const val WhatsNewPreviousTestTag = "whats-new-previous"/);
  assert.match(whatsNewContent, /const val WhatsNewNextTestTag = "whats-new-next"/);
  assert.match(whatsNewContent, /const val WhatsNewPageTestTagPrefix = "whats-new-page-"/);
  assert.match(whatsNewContent, /modifier\.fillMaxSize\(\)\.testTag\(WhatsNewRootTestTag\)/);
  assert.match(whatsNewContent, /testTag\(WhatsNewDismissTestTag\)/);
  assert.match(whatsNewContent, /testTag\(WhatsNewPreviousTestTag\)/);
  assert.match(whatsNewContent, /testTag\(WhatsNewNextTestTag\)/);
  assert.match(whatsNewContent, /testTag\("\$WhatsNewPageTestTagPrefix\$page"\)/);
  assert.match(whatsNewHost, /repository\.markReleasesSeen\(/);
  assert.match(whatsNewHost, /releases\.isNullOrEmpty\(\) -> LaunchedEffect\(onClose\) \{ onClose\(\) \}/);

  assert.match(androidWhatsNewEvidenceTest, /class WhatsNewCommonInstrumentedTest/);
  assert.match(androidWhatsNewEvidenceTest, /WhatsNewScreenHost\(/);
  assert.match(androidWhatsNewEvidenceTest, /EvidenceWhatsNewRepository/);
  assert.match(androidWhatsNewEvidenceTest, /WhatsNewNextTestTag/);
  assert.match(androidWhatsNewEvidenceTest, /android-whats-new-common-evidence\.json/);
  assert.match(androidWhatsNewEvidenceTest, /whats_new_second_mount_closed_without_repeating/);

  assert.match(web, /WebWhatsNewDestination\.PendingReleases -> WhatsNewScreenHost\(/);
  assert.match(web, /createWebWhatsNewRepository\(\): WhatsNewRepository = LocalWhatsNewRepository/);
  assert.match(web, /QuataLocalWhatsNewCatalog\.webReleases\(\)/);
  assert.match(webWhatsNewEvidenceRunner, /WHATS-NEW-WEB-COMMON-001/);
  assert.match(webWhatsNewEvidenceRunner, /page\.goto\(`\$\{server\.origin\}\/#whats-new`\)/);
  assert.match(webWhatsNewEvidenceRunner, /resetWhatsNewState\(page\)/);
  assert.match(webWhatsNewEvidenceRunner, /assertSeenState\(page\)/);
  assert.match(webWhatsNewEvidenceRunner, /whats_new_second_open_closed_without_repeating/);

  assert.match(iosSwift, /case "whats-new-real":/);
  assert.match(iosSwift, /QuataIosManagedWhatsNewViewController\(/);
  assert.match(iosSwift, /makeWhatsNewClosedFixtureViewController\(\)/);
  assert.match(iosHostUiTests, /testWhatsNewFixtureRendersMarksSeenAndDoesNotRepeat/);
  assert.match(iosHostUiTests, /"whats-new-common-root"/);
  assert.match(iosHostUiTests, /"whats-new-page-0"/);
  assert.match(iosHostUiTests, /"quata-ios-whats-new-closed"/);
  assert.match(iosHostUiTests, /resetWhatsNew: true/);
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
  assert.match(web, /title = webAboutTitle\(languageTags\)/);
  assert.match(web, /releaseHistoryLabel = webAboutReleaseHistoryLabel\(languageTags\)/);
  assert.match(web, /onOpenReleaseHistory = \{ webSetBrowserFragment\("release-history"\) \}/);
  assert.doesNotMatch(web, /title = webReleaseHistoryStrings\(languageTags\)\.title/);
  assert.doesNotMatch(web, /releaseHistoryLabel = webReleaseHistoryStrings\(languageTags\)\.subtitle/);
  assert.match(webMain, /var whatsNewReturnFragment by remember \{ mutableStateOf<String\?>\(null\) \}/);
  assert.match(webMain, /onLogoClick = \{[\s\S]*?if \(webWhatsNewDestination\(navigation\.route\) == null\) \{[\s\S]*?whatsNewReturnFragment = navigation\.fragment[\s\S]*?\}[\s\S]*?navigation\.navigate\("about"\)/);

  assert.match(ios, /fun QuataAboutViewController\(dependencies: IosAboutHostDependencies\): UIViewController/);
  assert.match(iosRuntime, /fun QuataIosAboutViewController\(/);
  assert.match(iosRuntime, /title = iosAboutTitle\(runtime\.languageTags\)/);
  assert.match(iosRuntime, /releaseHistoryLabel = iosAboutReleaseHistoryLabel\(runtime\.languageTags\)/);
  assert.match(iosRuntime, /onOpenReleaseHistory = onOpenReleaseHistory/);
  assert.match(iosRuntime, /legalLinks = \{ IosAboutLegalLinks\(runtime\.languageTags, documentOpener\) \}/);
  assert.doesNotMatch(iosRuntime, /title = runtime\.releaseHistoryStrings\(\)\.title/);
  assert.doesNotMatch(iosRuntime, /releaseHistoryLabel = runtime\.releaseHistoryStrings\(\)\.subtitle/);
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

test('About and Release History evidence runners exercise real common anchors', () => {
  assert.match(androidEvidenceTest, /class AboutReleaseHistoryCommonBridgeInstrumentedTest/);
  assert.match(androidEvidenceTest, /QuataAboutDialogContent\(/);
  assert.match(androidEvidenceTest, /onOpenReleaseHistory = \{[\s\S]*?showingHistory = true[\s\S]*?\}/);
  assert.match(androidEvidenceTest, /ReleaseHistoryContent\(/);
  assert.match(androidEvidenceTest, /ReleaseHistoryNextTestTag/);
  assert.match(androidEvidenceTest, /ReleaseHistoryPreviousTestTag/);
  assert.match(androidEvidenceTest, /ReleaseHistoryCloseTestTag/);
  assert.match(androidEvidenceTest, /android-about-release-history-common-evidence\.json/);
  assert.match(androidEvidenceRunner, /ABOUT-RELEASE-HISTORY-ANDROID-COMMON-001/);
  assert.match(androidEvidenceRunner, /AboutReleaseHistoryCommonBridgeInstrumentedTest/);
  assert.match(androidEvidenceRunner, /android_debug_and_test_apks_built/);
  assert.match(androidEvidenceRunner, /copyDeviceEvidence/);

  assert.match(webEvidenceRunner, /ABOUT-RELEASE-HISTORY-WEB-001/);
  assert.match(webEvidenceRunner, /page\.goto\(aboutUrl\(\)\)/);
  assert.match(webEvidenceRunner, /clickVisibleText\(page, \/Historial de versiones\|Release history\/\)/);
  assert.match(webEvidenceRunner, /waitForHash\(page, "#release-history"\)/);
  assert.match(webEvidenceRunner, /page\.goto\(releaseHistoryUrl\(\)\)/);
  assert.match(webEvidenceRunner, /about_legal_documents_opened_from_local_assets/);
  assert.doesNotMatch(webEvidenceRunner, /location\.hash\s*=\s*["']release-history["']/);

  assert.match(iosSwift, /case "about-release-history":/);
  assert.match(iosSwift, /QuataIosAbout(?:LegalEvidence)?ViewController\([\s\S]*?onOpenReleaseHistory: \{ router\?\.showReleaseHistory\(\) \}/);
  assert.match(iosSwift, /QuataIosReleaseHistoryViewController\([\s\S]*?onClose: \{ router\?\.showAbout\(\) \}/);
  assert.match(iosHostUiTests, /testAboutReleaseHistoryFixtureRendersRealSharedComposeSurfaces/);
  assert.match(iosHostUiTests, /"about-common-root"/);
  assert.match(iosHostUiTests, /"about-release-history"/);
  assert.match(iosHostUiTests, /"release-history-common-root"/);
  assert.match(iosHostUiTests, /"release-history-page-0"/);
  assert.match(iosEvidenceRunner, /ABOUT-RELEASE-HISTORY-IOS-COMMON-001/);
  assert.match(iosEvidenceRunner, /mac_checkout_sha_matches_local_candidate/);
  assert.match(iosEvidenceRunner, /bash scripts\/run-ios-about-release-history-ui-test\.sh/);
  assert.match(iosEvidenceShellRunner, /QuataIosUITests\/QuataIosHostUITests\/testAboutReleaseHistoryFixtureRendersRealSharedComposeSurfaces/);
  assert.match(iosEvidenceShellRunner, /check-ios-xctest-executed\.py/);
  assert.match(iosEvidenceShellRunner, /xcode_status=\$\?/);
  assert.match(iosEvidenceShellRunner, /\\\*\\\* TEST EXECUTE SUCCEEDED \\\*\\\*/);
});

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8');
}
