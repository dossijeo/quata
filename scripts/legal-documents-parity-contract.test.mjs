import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const legalDocument = await source('../core/src/commonMain/kotlin/com/quata/core/moderation/LegalDocument.kt');
const legalLinksContent = await source('../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataLegalDocumentLinksContent.kt');
const androidNav = await source('../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt');
const androidLegal = await source('../app/src/main/java/com/quata/core/moderation/LegalDocuments.kt');
const web = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebWhatsNewHost.kt');
const iosRuntime = await source('../feature/whatsnew/src/iosMain/kotlin/com/quata/feature/whatsnew/presentation/IosWhatsNewRuntimeBootstrap.kt');

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
  assert.match(web, /onOpenDocument = \{ document -> webOpenExternalUrl\(document\.publicUrl\(\)\) \}/);
  assert.doesNotMatch(web, /TextButton\(onClick = \{ webOpenExternalUrl\(LegalLinks\./);
  assert.doesNotMatch(web, /WebAboutLegalLabels/);

  assert.match(iosRuntime, /QuataLegalDocumentLinksContent\(/);
  assert.match(iosRuntime, /onOpenDocument = \{ document -> openIosExternalUrl\(document\.publicUrl\(\)\) \}/);
  assert.doesNotMatch(iosRuntime, /TextButton\(onClick = \{ openIosExternalUrl\(LegalLinks\./);
  assert.doesNotMatch(iosRuntime, /IosAboutLegalLabels/);
});

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8');
}
