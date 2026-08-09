import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const portable = await readFile(
  new URL("../designsystem/src/commonMain/kotlin/com/quata/core/ui/richtext/QuataPortableRichTextEditor.kt", import.meta.url),
  "utf8",
);
const web = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt", import.meta.url), "utf8");
const ios = await readFile(
  new URL("../feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/QuataOfficialViewController.kt", import.meta.url),
  "utf8",
);
const android = await readFile(
  new URL("../app/src/main/java/com/quata/feature/official/presentation/OfficialPostEditorScreen.kt", import.meta.url),
  "utf8",
);
const commonTranslator = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostEditorFangTranslator.kt", import.meta.url),
  "utf8",
);
const commonRoot = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostEditorRoot.kt", import.meta.url),
  "utf8",
);
const advancedFields = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialAdvancedTextFieldsContent.kt", import.meta.url),
  "utf8",
);
const modeSelector = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialEditorModeSelectorContent.kt", import.meta.url),
  "utf8",
);
const commonScreen = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialEditorScreenContent.kt", import.meta.url),
  "utf8",
);
const translationPrompt = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialTranslationPromptContent.kt", import.meta.url),
  "utf8",
);
const iosUiTest = await readFile(
  new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedOfficialEditorUITests.swift", import.meta.url),
  "utf8",
);

test("Official editor Web and iOS use the common portable rich text editor", () => {
  assert.match(portable, /fun QuataPortableRichTextEditorBox\(/);
  assert.match(portable, /QuataPortableRichTextFieldTestTag/);
  assert.match(portable, /quata-portable-rich-text-field/);
  assert.match(portable, /QuataPortableRichTextFieldFocusTargetTestTag/);
  assert.match(portable, /quata-portable-rich-text-focus-target/);
  assert.match(portable, /QuataRichTextEditorState\(initialHtml\)/);
  assert.match(portable, /state\.updateBlockText\(block\.id, value\)/);
  assert.match(portable, /FocusRequester\(\)/);
  assert.match(portable, /\.focusRequester\(focusRequester\)/);
  assert.match(portable, /LocalSoftwareKeyboardController\.current/);
  assert.match(portable, /keyboardController\?\.show\(\)/);
  assert.match(portable, /\.testTag\(QuataPortableRichTextFieldFocusTargetTestTag\)[\s\S]*\.clickable/);
  assert.match(portable, /\.testTag\(QuataPortableRichTextFieldTestTag\)[\s\S]*\.focusRequester\(focusRequester\)[\s\S]*\.clickable/);
  assert.match(portable, /import androidx\.compose\.foundation\.layout\.heightIn/);
  assert.match(portable, /\.heightIn\(min = 44\.dp\)/);
  assert.match(portable, /state(?:::|\.)toggleBold/);
  assert.match(portable, /state(?:::|\.)toggleItalic/);
  assert.match(portable, /state(?:::|\.)toggleUnderline/);
  assert.match(portable, /state\.toggleList\("bullet"\)/);
  assert.match(portable, /state\.toggleList\("numbered"\)/);
  assert.match(portable, /state\.html/);

  assert.match(web, /QuataPortableRichTextEditorBox\(/);
  assert.match(ios, /QuataPortableRichTextEditorBox\(/);
  assert.match(iosUiTest, /official-editor-body-action/);
  assert.match(iosUiTest, /quata-portable-rich-text-field/);
  assert.match(iosUiTest, /official-editor-preview/);
  assert.match(iosUiTest, /Vista previa/);
});

test("Official editor translation prompt exposes common evidence anchors", () => {
  assert.match(translationPrompt, /OfficialEditorTranslationPromptTestTag = "official-editor-translation-prompt"/);
  assert.match(translationPrompt, /OfficialEditorTranslationConfirmTestTag = "official-editor-translation-confirm"/);
  assert.match(translationPrompt, /OfficialEditorTranslationSkipTestTag = "official-editor-translation-skip"/);
  assert.match(translationPrompt, /Modifier\.testTag\(OfficialEditorTranslationSkipTestTag\)/);
  assert.match(iosUiTest, /official-editor-translation-skip/);
});

test("Official editor publish remains usable while the software keyboard is open", () => {
  assert.match(commonScreen, /import androidx\.compose\.foundation\.layout\.imePadding/);
  assert.match(commonScreen, /\.fillMaxSize\(\)[\s\S]*\.imePadding\(\)[\s\S]*\.verticalScroll/);
  assert.match(commonRoot, /LocalFocusManager\.current/);
  assert.match(commonRoot, /localFeedback = null[\s\S]*val draft = draftState\.buildDraft/);
  assert.match(commonRoot, /onClick = \{[\s\S]*focusManager\.clearFocus\(force = true\)[\s\S]*requestPublication\(\)[\s\S]*\}/);
});

test("Official editor advanced text controls expose common evidence anchors", () => {
  assert.match(modeSelector, /OfficialEditorModeSwitchTestTag = "official-editor-mode-switch"/);
  assert.match(modeSelector, /Modifier\.testTag\(OfficialEditorModeSwitchTestTag\)/);
  assert.match(advancedFields, /OfficialEditorAdvancedTitleTestTag = "official-editor-advanced-title"/);
  assert.match(advancedFields, /OfficialEditorAdvancedSummaryTestTag = "official-editor-advanced-summary"/);
  assert.match(advancedFields, /Modifier\.fillMaxWidth\(\)\.testTag\(OfficialEditorAdvancedTitleTestTag\)/);
  assert.match(advancedFields, /Modifier\.fillMaxWidth\(\)\.testTag\(OfficialEditorAdvancedSummaryTestTag\)/);
});

test("Official editor no longer accepts browser prompt or plain iOS text field as product rich text editor", () => {
  assert.doesNotMatch(web, /webPromptForOfficialHtml|globalThis\.prompt/);
  const bodyEditor = ios.slice(ios.indexOf("bodyEditorAction ="), ios.indexOf("imagePicker ="));
  assert.doesNotMatch(bodyEditor, /OutlinedTextField/);
  assert.doesNotMatch(bodyEditor, /var editing by remember/);
  assert.doesNotMatch(iosUiTest, /app\.buttons\["Editar descripci[oó]n"\]|compact shared body editor action/);
});

test("Official editor rich text parity contract stays hermetic", () => {
  for (const source of [portable, web, ios, iosUiTest, commonTranslator]) {
    assert.doesNotMatch(source, /SUPABASE_DB_URL|SERVICE_ROLE|21085800|\+240|68024260/);
  }
});

test("Official editor translation is shared for Web and iOS", () => {
  assert.match(commonTranslator, /class OfficialPostEditorFangTranslator\(/);
  assert.match(commonTranslator, /: OfficialPostEditorTranslator/);
  assert.match(commonTranslator, /private val translator: TextTranslator/);
  assert.match(commonTranslator, /officialHtmlBlockRegex\.findAll\(html\)/);
  assert.match(web, /OfficialPostEditorFangTranslator\(FangTranslationService\(transport = BrowserTranslationHttpTransport\(\)\)\)/);
  assert.match(ios, /OfficialPostEditorFangTranslator\(FangTranslationService\(transport = IosTranslationHttpTransport\(\)\)\)/);
  assert.match(web, /translator = translator/);
  assert.match(ios, /translator = translator/);
  assert.doesNotMatch(ios, /translator = null/);
});

test("Official editor source language detection is shared and FastText-backed on Web and iOS", () => {
  assert.match(commonRoot, /suspend fun detectOfficialPostLanguage\([\s\S]*identifier: TextLanguageIdentifier/);
  assert.match(commonRoot, /officialPostEditorDetectionText\(draft\)/);
  assert.match(commonRoot, /QuataDetectedLanguage\.Spanish -> OfficialPostLanguage\.Spanish/);
  assert.match(commonRoot, /QuataDetectedLanguage\.English -> OfficialPostLanguage\.English/);
  assert.match(commonRoot, /QuataDetectedLanguage\.French -> OfficialPostLanguage\.French/);
  assert.match(commonRoot, /QuataDetectedLanguage\.Fang,[\s\S]*QuataDetectedLanguage\.Unknown -> null/);
  assert.match(commonRoot, /translationSourceLanguage = null/);
  assert.match(commonRoot, /getOrDefault\(fallbackOfficialPostEditorLanguageDetection\(language\)\)/);
  assert.match(commonRoot, /translator == null \|\| sourceLanguage == null/);
  assert.match(android, /detectOfficialPostLanguage\(/);
  assert.match(android, /TextLanguageIdentifier \{ text -> QuataLanguageIdentifier\.detect\(context, text\) \}/);
  assert.match(web, /identifier = BrowserFastTextLanguageIdentifier/);
  assert.match(ios, /identifier = IosFastTextLanguageIdentifier/);
  assert.doesNotMatch(web, /detectLanguage = \{\s*webOfficialPostLanguage\(\)\s*\}/);
  assert.doesNotMatch(ios, /detectLanguage = \{\s*iosOfficialPostLanguage\(dependencies\.preferredLanguageTag\)\s*\}/);
});
