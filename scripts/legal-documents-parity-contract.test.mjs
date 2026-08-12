import assert from 'node:assert/strict';
import test from 'node:test';
import { access, readFile } from 'node:fs/promises';

const legalDocument = await source('../core/src/commonMain/kotlin/com/quata/core/moderation/LegalDocument.kt');
const legalLinksContent = await source('../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataLegalDocumentLinksContent.kt');
const androidNav = await source('../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt');
const androidLegal = await source('../app/src/main/java/com/quata/core/moderation/LegalDocuments.kt');
const androidEvidenceTest = await source('../app/src/androidTest/java/com/quata/feature/whatsnew/presentation/AboutReleaseHistoryInstrumentedTest.kt');
const web = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebWhatsNewHost.kt');
const webEvidenceRunner = await source('../scripts/about-release-history-web-evidence.mjs');
const iosRuntime = await source('../feature/whatsnew/src/iosMain/kotlin/com/quata/feature/whatsnew/presentation/IosWhatsNewRuntimeBootstrap.kt');
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
  assert.match(iosRuntime, /NSBundle\.mainBundle\.pathForResource/);
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

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8');
}
