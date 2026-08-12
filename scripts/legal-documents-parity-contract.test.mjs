import assert from 'node:assert/strict';
import test from 'node:test';
import { access, readFile } from 'node:fs/promises';

const legalDocument = await source('../core/src/commonMain/kotlin/com/quata/core/moderation/LegalDocument.kt');
const legalLinksContent = await source('../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataLegalDocumentLinksContent.kt');
const androidNav = await source('../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt');
const androidLegal = await source('../app/src/main/java/com/quata/core/moderation/LegalDocuments.kt');
const iosLegal = await source('../core/src/iosMain/kotlin/com/quata/core/moderation/IosLegalDocuments.kt');
const androidRegister = await source('../app/src/main/java/com/quata/feature/auth/presentation/register/RegisterScreen.kt');
const androidAuthEvidenceTest = await source('../app/src/androidTest/java/com/quata/feature/auth/presentation/AuthRecoveryProductBridgeInstrumentedTest.kt');
const androidEvidenceTest = await source('../app/src/androidTest/java/com/quata/feature/whatsnew/presentation/AboutReleaseHistoryInstrumentedTest.kt');
const androidProfileLegalEvidenceTest = await source('../app/src/androidTest/java/com/quata/feature/profile/presentation/ProfileLegalDocumentsInstrumentedTest.kt');
const web = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebWhatsNewHost.kt');
const webSettings = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebSettingsHost.kt');
const webProfile = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebProfileHost.kt');
  const webLogin = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebLoginHost.kt');
  const webNativeLegalLinks = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebNativeLegalDocumentLinksContent.kt');
const webMain = await source('../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt');
const webEvidenceRunner = await source('../scripts/about-release-history-web-evidence.mjs');
const webAuthEvidenceRunner = await source('../scripts/web-authenticated-browser-e2e.mjs');
const authProductHost = await source('../feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/AuthProductHostContent.kt');
const registerHost = await source('../feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/register/RegisterScreenHost.kt');
const registerForm = await source('../feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/register/RegisterForm.kt');
const settingsCommon = await source('../feature/settings/src/commonMain/kotlin/com/quata/feature/settings/presentation/SettingsAppearanceControls.kt');
const profileHost = await source('../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt');
const androidProfile = await source('../app/src/main/java/com/quata/feature/profile/presentation/ProfileScreen.kt');
const iosRuntime = await source('../feature/whatsnew/src/iosMain/kotlin/com/quata/feature/whatsnew/presentation/IosWhatsNewRuntimeBootstrap.kt');
const iosAuth = await source('../feature/auth/src/iosMain/kotlin/com/quata/feature/auth/presentation/IosAuthHost.kt');
const iosSettings = await source('../feature/settings/src/iosMain/kotlin/com/quata/feature/settings/presentation/IosSettingsHost.kt');
const iosProfile = await source('../feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileHost.kt');
const iosProfileBootstrap = await source('../feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileSosRuntimeBootstrap.kt');
const iosProfileLegalFixture = await source('../feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileLegalEvidenceFixture.kt');
const iosProject = await source('../iosApp/project.yml');
const iosSwift = await source('../iosApp/iosApp/QuataIosApp.swift');
const iosHostUiTests = await source('../iosApp/iosAppUITests/QuataIosHostUITests.swift');

test('legal documents have one shared catalog for labels, URLs and assets', () => {
  assert.match(legalDocument, /enum class LegalDocument \{\s*Privacy,\s*ChildSafety,?\s*\}/);
  assert.match(legalDocument, /fun legalDocumentLabels\(language: QuataLanguage\): LegalDocumentLabelSet/);
  assert.match(legalDocument, /fun LegalDocument\.label\(labels: LegalDocumentLabelSet\): String/);
  assert.match(legalDocument, /fun LegalDocument\.publicUrl\(\): String = when \(this\)/);
  assert.match(legalDocument, /LegalDocument\.Privacy -> LegalLinks\.Privacy/);
  assert.match(legalDocument, /LegalDocument\.ChildSafety -> LegalLinks\.ChildSafety/);
  assert.match(legalDocument, /fun LegalDocument\.assetName\(language: QuataLanguage\): String/);
});

test('common legal links content renders all platforms from the shared catalog', () => {
  assert.match(legalLinksContent, /fun QuataLegalDocumentLinksContent\(/);
  assert.match(legalLinksContent, /documents: List<LegalDocument> = listOf\(LegalDocument\.Privacy, LegalDocument\.ChildSafety\)/);
  assert.match(legalLinksContent, /val labels = legalDocumentLabels\(language\)/);
  assert.match(legalLinksContent, /Text\(document\.label\(labels\)/);
  assert.match(legalLinksContent, /QuataLegalDocumentLinkTestTagPrefix \+ document\.name\.lowercase\(\)/);
});

test('Android, Web and iOS About links use common legal document content', () => {
  assert.match(androidNav, /QuataLegalDocumentLinksContent\(/);
  assert.match(androidNav, /QuataLegalDocumentLinksColumnContent\(/);
  assert.match(androidNav, /LegalDocuments\.platformFile\(context, document\)/);
  assert.match(androidNav, /container\.documentOpenService\.open\(file\.value\)/);
  assert.doesNotMatch(androidNav, /LegalDocumentLinkButton\(R\.string\.legal_privacy/);
  assert.doesNotMatch(androidNav, /LegalDocumentLinkButton\(R\.string\.legal_child_safety/);
  assert.match(androidLegal, /fun platformFile\(context: Context, document: LegalDocument\): PlatformResult<PlatformFile>/);
  assert.match(androidLegal, /document\.assetName\(QuataLanguageManager\.currentLanguage\)/);
  assert.doesNotMatch(androidLegal, /QuataDocumentReader\.open/);

  assert.match(web, /QuataLegalDocumentLinksContent\(/);
  assert.match(web, /documentOpener\.open\(webLegalDocumentFile\(document, language\)\)/);
  assert.match(web, /reference = webLegalDocumentUrl\(assetName\)/);
  assert.doesNotMatch(web, /TextButton\(onClick = \{ webOpenExternalUrl\(LegalLinks\./);
  assert.doesNotMatch(web, /webOpenExternalUrl\(document\.publicUrl\(\)\)/);
  assert.doesNotMatch(web, /WebAboutLegalLabels/);

  assert.match(iosRuntime, /QuataLegalDocumentLinksContent\(/);
  assert.match(iosRuntime, /iosLegalDocumentFile\(document, language\)\?\.let \{ documentOpener\.open\(it\) \}/);
  assert.match(iosLegal, /NSBundle\.mainBundle\.pathForResource/);
  assert.match(iosSwift, /documentOpener: platformServices\.services\.documentOpener/);
  assert.doesNotMatch(iosRuntime, /TextButton\(onClick = \{ openIosExternalUrl\(LegalLinks\./);
  assert.doesNotMatch(iosRuntime, /openIosExternalUrl\(document\.publicUrl\(\)\)/);
  assert.doesNotMatch(iosRuntime, /IosAboutLegalLabels/);
});

test('Web packages the same legal document assets as Android', async () => {
  for (const language of ['en', 'es', 'fr']) {
    await access(new URL(`../web/src/wasmJsMain/resources/legal/privacy_${language}.docx`, import.meta.url));
    await access(new URL(`../web/src/wasmJsMain/resources/legal/child_safety_${language}.docx`, import.meta.url));
  }
});

test('iOS packages the same legal document bundle directory as Android', async () => {
  assert.match(iosProject, /\.\.\/app\/src\/main\/assets\/legal[\s\S]*buildPhase: resources/);
  for (const language of ['en', 'es', 'fr']) {
    await access(new URL(`../app/src/main/assets/legal/privacy_${language}.docx`, import.meta.url));
    await access(new URL(`../app/src/main/assets/legal/child_safety_${language}.docx`, import.meta.url));
  }
});

test('About evidence runners exercise both shared legal document actions', () => {
  assert.match(androidEvidenceTest, /QuataLegalDocumentLinksContent\(/);
  assert.match(androidEvidenceTest, /privacy_es\.docx/);
  assert.match(androidEvidenceTest, /child_safety_es\.docx/);

  assert.match(webEvidenceRunner, /clickAndCaptureDownload\(page, \/Política de privacidad\|Privacy policy\/, "privacy_es\.docx"\)/);
  assert.match(webEvidenceRunner, /clickAndCaptureDownload\(page, \/Seguridad de menores\|Child safety\/, "child_safety_es\.docx"\)/);

  assert.match(iosRuntime, /fun QuataIosAboutLegalEvidenceViewController\(/);
  assert.match(iosSwift, /QuataIosAboutLegalEvidenceViewController\(/);
  assert.match(iosHostUiTests, /"legal-document-link-privacy"/);
  assert.match(iosHostUiTests, /"legal-document-link-childsafety"/);
  assert.match(iosHostUiTests, /"legal-document-opened-privacy_es\.docx"/);
  assert.match(iosHostUiTests, /"legal-document-opened-child_safety_es\.docx"/);
});

test('Account and Settings surfaces expose the shared legal document section', () => {
  assert.match(settingsCommon, /fun SettingsLegalDocumentsSectionContent\(/);
  assert.match(settingsCommon, /QuataLegalDocumentLinksContent\(/);

  assert.match(profileHost, /slots\.legalDocuments\?\.invoke\(\)/);
  assert.match(androidProfile, /SettingsLegalDocumentsSectionContent\(/);
  assert.match(androidProfile, /LegalDocuments\.platformFile\(context, document\)/);
  assert.match(androidProfile, /documentOpenService\.open\(file\.value\)/);
  assert.match(androidProfileLegalEvidenceTest, /ACCOUNT-LEGAL-DOCUMENTS-ANDROID-COMMON-001/);
  assert.match(androidProfileLegalEvidenceTest, /SettingsLegalDocumentsSectionContent\(/);
  assert.match(androidProfileLegalEvidenceTest, /privacy_es\.docx/);
  assert.match(androidProfileLegalEvidenceTest, /child_safety_es\.docx/);

  assert.match(webProfile, /legalDocuments = \{/);
  assert.match(webProfile, /listOfNotNull\(webProfileLanguageTag\(\)\)\.toQuataLanguage\(\)/);
  assert.match(webProfile, /SettingsLegalDocumentsSectionContent\(/);
  assert.match(webProfile, /platformServices\.documentOpener\.open\(webLegalDocumentFile\(document, language\)\)/);
  assert.match(webSettings, /SettingsLegalDocumentsSectionContent\(/);
  assert.match(webSettings, /documentOpener\.open\(webLegalDocumentFile\(document, language\)\)/);
  assert.match(webMain, /documentOpener = platformServices\.documentOpener/);
  assert.match(webAuthEvidenceRunner, /account_settings_shared_legal_documents_opened_from_local_assets/);
  assert.match(webAuthEvidenceRunner, /assertAccountSettingsLegalDocumentViewer\(page/);

  assert.match(iosProfile, /legalDocuments = \{/);
  assert.match(iosProfile, /SettingsLegalDocumentsSectionContent\(/);
  assert.match(iosProfile, /dependencies\.openLegalDocument\(document, opener\)/);
  assert.match(iosProfileBootstrap, /openLegalDocument: \(LegalDocument, DocumentOpenService\) -> Unit/);
  assert.match(iosProfileLegalFixture, /fun QuataIosProfileLegalEvidenceViewController\(/);
  assert.match(iosProfileLegalFixture, /RecordingIosProfileLegalDocumentOpenService/);
  assert.match(iosSettings, /SettingsLegalDocumentsSectionContent\(/);
  assert.match(iosSettings, /openLegalDocument\(document, opener\)/);
  assert.match(iosRuntime, /fun openIosLegalDocumentForSettings\(/);
  assert.match(iosSwift, /openIosLegalDocumentForSettings\(/);
  assert.match(iosSwift, /IosProfileLegalEvidenceFixtureKt\.QuataIosProfileLegalEvidenceViewController\(/);
  assert.match(iosHostUiTests, /testProfileLegalFixtureRendersSharedAccountLegalLinks/);
});

test('Auth registration exposes legal documents through the shared common slot', () => {
  assert.match(authProductHost, /registerLegalLinks: @Composable \(\(\) -> Unit\)\? = null/);
  assert.match(authProductHost, /legalLinks = registerLegalLinks/);
  assert.match(authProductHost, /LaunchedEffect\(initialDestination\)[\s\S]*destination = initialDestination/);
  assert.match(registerHost, /legalLinks: @Composable \(\(\) -> Unit\)\? = null/);
  assert.match(registerForm, /legalLinks\?\.let/);

  assert.match(androidRegister, /QuataLegalDocumentLinksContent\(/);
  assert.match(androidRegister, /scope\.launch \{ openLegalDocument\(document\) \}/);
  assert.match(androidNav, /openLegalDocument = \{ document -> openLegalDocument\(appContext, container, document\) \}/);
  assert.match(androidAuthEvidenceTest, /sharedRegisterSurfaceExposesLegalDocumentsAndDispatchesClicks/);

  assert.match(webLogin, /registerLegalLinks = \{/);
  assert.match(webLogin, /WebNativeLegalDocumentLinksContent\(/);
  assert.match(webLogin, /documentOpener\.open\(webLegalDocumentFile\(document, QuataLanguage\.Spanish\)\)/);
  assert.match(webNativeLegalLinks, /legalDocumentLabels\(language\)/);
  assert.match(webNativeLegalLinks, /legalDocument\.label\(labels\)/);
  assert.match(webNativeLegalLinks, /HTMLButtonElement/);
  assert.match(webMain, /documentOpener = platformServices\.documentOpener/);
  assert.match(webAuthEvidenceRunner, /register_shared_legal_documents_opened_from_local_assets/);
  assert.match(webAuthEvidenceRunner, /clickAndCaptureDocumentViewer\(page, \/privacidad\|Privacy policy\/i, "privacy_es\.docx", 0\)/);
  assert.match(webAuthEvidenceRunner, /clickAndCaptureDocumentViewer\(page, \/Seguridad infantil\|Seguridad de menores\|Child safety\/i, "child_safety_es\.docx", 1\)/);
  assert.match(webAuthEvidenceRunner, /__quataDocumentOpenEvidence/);
  assert.match(webAuthEvidenceRunner, /data-quata-docmentis-viewer/);
  assert.match(webAuthEvidenceRunner, /viewer: "docmentis-overlay"/);
  assert.match(webAuthEvidenceRunner, /renderReady/);

  assert.match(iosAuth, /registerLegalLinks = \{ IosAuthRegisterLegalLinks\(dependencies\.locale, dependencies\.documentOpener\) \}/);
  assert.match(iosAuth, /fun openIosAuthLegalDocument\(/);
  assert.match(iosAuth, /iosLegalDocumentFile\(document, language\)\?\.let \{ documentOpener\.open\(it\) \}/);
  assert.match(iosSwift, /createIosAuthHostDependencies[\s\S]*documentOpener: platformServices\.services\.documentOpener/);
  assert.match(iosHostUiTests, /testAuthLaunchFixtureCanColdStartSharedRegisterLegalLinks/);
  assert.match(iosHostUiTests, /auth-launch-register-legal/);
});

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8');
}
