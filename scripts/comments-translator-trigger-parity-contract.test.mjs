import assert from "node:assert/strict";
import { readFile, stat } from "node:fs/promises";
import test from "node:test";

const files = {
  feed: await source("../feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt"),
  commentRow: await source("../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataCommentRowContent.kt"),
  official: await source("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt"),
  officialComments: await source("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialCommentsPanelContent.kt"),
  profileHost: await source("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt"),
  profileComments: await source("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentsDialogContent.kt"),
  profileCommentRow: await source("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentRowContent.kt"),
  feedIos: await source("../feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/QuataFeedViewController.kt"),
  profileIos: await source("../feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/presentation/IosNeighborhoodsHost.kt"),
  officialIos: await source("../feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/IosOfficialPlatformSlots.kt"),
  feedIosBootstrap: await source("../feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/IosFeedRuntimeBootstrap.kt"),
  iosApp: await source("../iosApp/iosApp/QuataIosApp.swift"),
  iosProject: await source("../iosApp/project.yml"),
  webFeed: await source("../web/src/wasmJsMain/kotlin/com/quata/web/WebFeedHost.kt"),
  webOfficial: await source("../web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt"),
  webMain: await source("../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
  androidFeed: await source("../app/src/main/java/com/quata/feature/feed/presentation/FeedScreen.kt"),
  androidOfficial: await source("../app/src/main/java/com/quata/feature/official/presentation/OfficialFeedScreen.kt"),
  androidNeighborhoods: await source("../app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt"),
  androidFastTextIdentifier: await source("../app/src/main/java/com/quata/core/language/QuataLanguageIdentifier.kt"),
  overlay: await source("../designsystem/src/commonMain/kotlin/com/quata/designsystem/translation/QuataTranslatorOverlayContent.kt"),
  fastTextDetector: await source("../core/src/commonMain/kotlin/com/quata/core/language/FastTextLanguageDetector.kt"),
  fastTextIdentifier: await source("../core/src/commonMain/kotlin/com/quata/core/language/FastTextTextLanguageIdentifier.kt"),
  iosFastTextIdentifier: await source("../core/src/iosMain/kotlin/com/quata/core/language/IosFastTextLanguageIdentifier.kt"),
  browserFastTextIdentifier: await source("../web/src/wasmJsMain/kotlin/com/quata/web/BrowserFastTextLanguageIdentifier.kt"),
};

test("Feed and Official comments translator triggers have a common non-inert fallback", () => {
  for (const [name, sourceText] of Object.entries({ feed: files.feed, official: files.official, profile: files.profileHost })) {
    assert.match(sourceText, /commentsTranslatorTrigger: @Composable \(String, Modifier, \(\) -> Unit, Boolean\) -> Unit/);
    assert.match(sourceText, /FangTranslatorTriggerContent\(contentDescription = contentDescription, onClick = onClick, enabled = enabled/);
    assert.doesNotMatch(sourceText, /commentsTranslatorTrigger:[\s\S]{0,240}onClick = \{\}/, `${name} must not default to an inert visible trigger`);
    assert.match(sourceText, /commentsTranslationGateway: QuataTranslatorGateway\? = null/);
  }
  assert.match(files.feed, /QuataTranslatorOverlayContent/);
  assert.match(files.feed, /testTag\("feed\.comments\.translator"\)/);
  assert.match(files.official, /translatorGateway = slots\.commentsTranslationGateway/);
  assert.match(files.officialComments, /QuataTranslatorOverlayContent/);
  assert.match(files.officialComments, /testTag\("official\.comments\.translator"\)/);
  assert.match(files.profileHost, /commentsTranslatorTrigger: @Composable \(String, Modifier, \(\) -> Unit, Boolean\) -> Unit/);
  assert.match(files.profileComments, /QuataTranslatorOverlayContent/);
  assert.match(files.profileComments, /public-profile-comment:\$\{comment\.id\}/);
  assert.match(files.profileComments, /testTag\("public-profile\.comments\.translator"\)/);
});

test("Web and iOS inject platform transports and FastText identifiers while Android keeps the native controller", () => {
  for (const sourceText of [files.webFeed, files.webOfficial]) {
    assert.match(sourceText, /FangTextTranslatorGateway\([\s\S]*identifier = BrowserFastTextLanguageIdentifier[\s\S]*FangTranslationService\(transport = BrowserTranslationHttpTransport\(\)\)[\s\S]*preferredLanguage = quataTranslatorPreferredLanguage/);
    assert.match(sourceText, /commentsTranslationGateway = commentsTranslationGateway/);
  }
  assert.match(files.webMain, /webCommunityProfileCommentsTranslationGateway = FangTextTranslatorGateway/);
  assert.match(files.webMain, /identifier = BrowserFastTextLanguageIdentifier/);
  assert.match(files.webMain, /commentsTranslationGateway = webCommunityProfileCommentsTranslationGateway/);
  for (const sourceText of [files.feedIos, files.officialIos, files.profileIos]) {
    assert.match(sourceText, /FangTextTranslatorGateway\([\s\S]*identifier = IosFastTextLanguageIdentifier[\s\S]*FangTranslationService\(transport = IosTranslationHttpTransport\(\)\)[\s\S]*preferredLanguage = quataTranslatorPreferredLanguage/);
    assert.match(sourceText, /commentsTranslationGateway = /);
  }
  assert.match(files.feedIos, /preferredLanguageTag: String\? = null/);
  assert.match(files.feedIos, /quataTranslatorStringsForLanguage\(dependencies\.preferredLanguageTag\)/);
  assert.match(files.feedIosBootstrap, /preferredLanguageTag: String\? = null/);
  assert.match(files.iosApp, /preferredLanguageTag: Locale\.preferredLanguages\.first/);
  for (const sourceText of [files.androidFeed, files.androidOfficial, files.androidNeighborhoods]) {
    assert.match(sourceText, /translatorModeController\.activate\(view, QuataTranslatorOverlaySource\.Comments\)/);
  }
});

test("Comment author rows expose stable profile navigation anchors across Feed, Official and public profile", () => {
  assert.match(files.feed, /authorProfileTestTagPrefix = "feed\.comments\.author\."/);
  assert.match(files.feed, /onOpenAuthorProfile = onOpenAuthorProfile/);
  assert.match(files.commentRow, /authorProfileTag/);
  assert.match(files.commentRow, /contentDescription = listOfNotNull\("\$\{comment\.authorName\} profile", authorProfileTag\)\.joinToString\(" "\)/);
  assert.match(files.officialComments, /authorProfileTestTagPrefix = "official\.comments\.author\."/);
  assert.match(files.officialComments, /onOpenAuthorProfile = onOpenUserProfile/);
  assert.match(files.profileCommentRow, /PublicProfileCommentAuthorTestTagPrefix = "public-profile\.comments\.author\."/);
  assert.match(files.profileCommentRow, /onOpenAuthorProfile\(authorId\)/);
  assert.match(files.profileCommentRow, /contentDescription = listOfNotNull\("\$\{comment\.authorName\} profile", authorProfileTag\)\.joinToString\(" "\)/);
  assert.match(files.profileComments, /onOpenAuthorProfile = onOpenUserProfile/);
  assert.doesNotMatch(files.profileComments, /onOpenAuthorProfile = \{\}/);
});

test("Web and iOS use the existing shared FastText language detector, not a heuristic fallback", async () => {
  assert.match(files.fastTextDetector, /class FastTextLanguageDetector/);
  assert.match(files.fastTextDetector, /fun fromByteArray\(bytes: ByteArray\): FastTextLanguageDetector/);
  assert.match(files.fastTextDetector, /FastTextMagic = 793712314/);
  assert.doesNotMatch(files.fastTextDetector, /android\.content\.Context|java\.nio\.charset/);
  assert.match(files.fastTextIdentifier, /class FastTextTextLanguageIdentifier/);
  assert.match(files.fastTextIdentifier, /FastTextLanguageDetector\.fromByteArray\(modelBytes\(\)\)/);
  assert.match(files.iosFastTextIdentifier, /object IosFastTextLanguageIdentifier : TextLanguageIdentifier/);
  assert.match(files.iosFastTextIdentifier, /NSBundle\.mainBundle\.pathForResource/);
  assert.match(files.browserFastTextIdentifier, /object BrowserFastTextLanguageIdentifier : TextLanguageIdentifier/);
  assert.match(files.browserFastTextIdentifier, /globalThis\.fetch\(path/);
  assert.match(files.androidFastTextIdentifier, /assets[\s\S]*open\(FastTextLanguageDetector\.ModelAssetName\)/);
  assert.match(files.iosProject, /lang_id_fasttext\.bin[\s\S]*buildPhase: resources/);
  assert.doesNotMatch(files.overlay, /CommonTextLanguageIdentifier/);
  await stat(new URL("../app/src/main/assets/lang_id_fasttext.bin", import.meta.url));
  await stat(new URL("../web/src/wasmJsMain/resources/lang_id_fasttext.bin", import.meta.url));
});

test("The shared comments overlay remains in designsystem instead of coupling Feed to Chat", () => {
  assert.match(files.overlay, /fun QuataTranslatorOverlayContent/);
  assert.match(files.overlay, /Dialog\(/);
  assert.match(files.overlay, /DialogProperties\(usePlatformDefaultWidth = false\)/);
  assert.match(files.overlay, /QuataTranslatorBackdrop\(background = null, modifier = Modifier\.fillMaxSize\(\)\)/);
  assert.doesNotMatch(files.overlay, /consumeTranslatorBackdropGestures/);
  assert.doesNotMatch(files.overlay, /event\.changes\.forEach \{ change -> change\.consume\(\) \}/);
  assert.match(files.overlay, /displayText\.replaceFirst\(originalText, translatedText\)/);
  assert.match(files.overlay, /translated\?\.let \{ TranslatorBoxUiState\(translation = it\) \}/);
  assert.match(files.overlay, /\?: TranslatorBoxUiState\(failed = true\)/);
  assert.doesNotMatch(files.overlay, /translation = it \?: TranslatorBoxState/);
  assert.doesNotMatch(files.overlay, /translatedText \?: failedText \?: displayText/);
  assert.match(files.overlay, /QuataTranslatableTextRegistry/);
  assert.match(files.overlay, /FangOverlayTranslationUseCase/);
  assert.match(files.overlay, /QuataTranslatorOverlayTestTag = "translator\.overlay"/);
  assert.match(files.overlay, /QuataTranslatorExitTestTag = "translator\.exit"/);
  assert.match(files.overlay, /QuataTranslatorMessageTestTagPrefix = "translator\.message\."/);
  assert.match(files.overlay, /testTag\(QuataTranslatorOverlayTestTag\)/);
  assert.match(files.overlay, /testTag\("\$QuataTranslatorMessageTestTagPrefix\$\{box\.id\}"\)/);
  assert.doesNotMatch(files.overlay, /contentDescription = QuataTranslatorOverlayTestTag/);
  assert.match(files.overlay, /contentDescription = "\$QuataTranslatorMessageTestTagPrefix\$\{box\.id\}"/);
  assert.match(files.overlay, /testTag\(QuataTranslatorExitTestTag\)/);
  assert.doesNotMatch(files.feed, /feature\.chat/);
  assert.doesNotMatch(files.official, /feature\.chat/);
  assert.doesNotMatch(files.officialComments, /feature\.chat/);
  assert.doesNotMatch(files.profileHost, /feature\.chat/);
  assert.doesNotMatch(files.profileComments, /feature\.chat/);
});

test("Web focal evidence translates Feed and Official comments through exact common anchors", async () => {
  const runner = await source("../scripts/chat-actions-notifications-web-evidence.mjs");
  assert.match(runner, /--feed-official-comments-translation-only/);
  assert.match(runner, /async function verifyFeedOfficialCommentsTranslationWeb/);
  assert.match(runner, /translator\.message\.\$\{surface\.name\}-comment:\$\{surface\.commentId\}/);
  assert.match(runner, /waitMessageVisible\(page, "pan de trigo"/);
  assert.match(runner, /waitMessageVisible\(page, "FAN->ES"/);
  assert.match(runner, /feed_and_official_comments_translation_result_direction_and_return_verified/);
  assert.match(runner, /comments_original_not_visible_after_translation_return/);
});

async function source(path) {
  return readFile(new URL(path, import.meta.url), "utf8");
}
