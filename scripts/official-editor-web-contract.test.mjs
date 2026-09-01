import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const webOfficialHost = await readFile(
  new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt", import.meta.url),
  "utf8",
);
const webMain = await readFile(
  new URL("../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt", import.meta.url),
  "utf8",
);
const webAuthRepository = await readFile(
  new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebAuthRepository.kt", import.meta.url),
  "utf8",
);
const webOfficialRepository = await readFile(
  new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialRepository.kt", import.meta.url),
  "utf8",
);
const webOfficialE2eBridge = await readFile(
  new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialE2eBridge.kt", import.meta.url),
  "utf8",
);
const commonOfficialEditor = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostEditorRoot.kt", import.meta.url),
  "utf8",
);
const iosOfficialRepository = await readFile(
  new URL("../feature/official/src/iosMain/kotlin/com/quata/feature/official/data/IosOfficialReadRepository.kt", import.meta.url),
  "utf8",
);
const webRealEvidence = await readFile(
  new URL("./official-editor-web-real-evidence.mjs", import.meta.url),
  "utf8",
);
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("Web Official surface exposes the shared editor action for official users", () => {
  assert.match(webOfficialHost, /fun WebOfficialHost\(/);
  assert.match(webOfficialHost, /onCreateOfficialPost: \(\) -> Unit/);
  assert.match(webOfficialHost, /canCreateOfficialPost: Boolean/);
  assert.match(webOfficialHost, /canCreateOfficialPost = canCreateOfficialPost/);
  assert.match(webOfficialHost, /installWebOfficialFeedE2eBridge/);
  assert.doesNotMatch(webOfficialHost, /canCreateOfficialPost = true/);
});

test("Web Official editor evidence bridge is localhost opt-in and invokes common editor actions", () => {
  assert.match(webOfficialE2eBridge, /quata-official-editor-e2e/);
  assert.match(webOfficialE2eBridge, /location\?\.hostname === 'localhost' \|\| location\?\.hostname === '127\.0\.0\.1'/);
  assert.match(webOfficialE2eBridge, /__quataOfficialFeedE2eProduct/);
  assert.match(webOfficialE2eBridge, /__quataOfficialEditorE2eProduct/);
  assert.match(webOfficialE2eBridge, /data-quata-official-editor-e2e/);
  assert.match(commonOfficialEditor, /class OfficialPostEditorE2eActions/);
  assert.match(commonOfficialEditor, /e2eBridgeInstaller: \(\(OfficialPostEditorE2eActions\) -> \(\(\) -> Unit\)\)\? = null/);
  assert.match(commonOfficialEditor, /publish = \{ requestPublication\(\) \}/);
  assert.match(commonOfficialEditor, /skipTranslation = \{ skipPendingTranslation\(\) \}/);
  assert.match(webOfficialHost, /e2eBridgeInstaller = \{ actions: OfficialPostEditorE2eActions ->/);
});

test("Official publish eligibility remains owned by commonMain state", async () => {
  const commonHost = await readFile(
    new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt", import.meta.url),
    "utf8",
  );
  assert.match(commonHost, /val canPublish = state\.currentUser\?\.isOfficial == true && slots\.canCreateOfficialPost/);
  assert.match(commonHost, /LaunchedEffect\(repository, currentUserId\)[\s\S]*viewModel\.refreshCurrentUser\(\)/);
  assert.doesNotMatch(webOfficialHost, /rememberWebOfficialCreatePermission/);
});

test("Web Official editor route and CTA use the restored session official role", () => {
  assert.match(webMain, /var currentUserIsOfficial by remember \{ mutableStateOf\(false\) \}/);
  assert.match(webMain, /currentUserIsOfficial = session\?\.isOfficial == true/);
  assert.match(webMain, /currentUserIsOfficial = restored\.isOfficial/);
  assert.match(webMain, /currentUserIsOfficial = restoredSession\?\.isOfficial == true/);
  assert.match(webMain, /if \(currentUserIsOfficial\) \{[\s\S]*?WebOfficialEditorHost\(/);
  assert.match(webMain, /LaunchedEffect\(navigation\.route, currentUserIsOfficial\)[\s\S]*?navigation\.navigate\("official"\)/);
  assert.match(webMain, /canCreateOfficialPost = currentUserIsOfficial/);
});

test("Web real evidence covers non-official permission denial without backend mutation", () => {
  assert.match(webRealEvidence, /--expect-ineligible/);
  assert.match(webRealEvidence, /QUATA_OFFICIAL_E2E_NON_OFFICIAL_PHONE/);
  assert.match(webRealEvidence, /REQUIRED_ENV\.filter\(\(name\) => name !== "QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN"\)/);
  assert.match(webRealEvidence, /!options\.expectIneligible && process\.env\.QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN/);
  assert.match(webRealEvidence, /official_create_cta_visible_for_non_official_profile/);
  assert.match(webRealEvidence, /official_editor_mounted_for_non_official_profile/);
  assert.match(webRealEvidence, /#official-editor/);
  assert.match(webRealEvidence, /localStorage\.getItem\("web\.navigation\.route"\) === "official"/);
  assert.match(webRealEvidence, /#official-editor-common-root/);
  assert.match(webRealEvidence, /non_official_session_cannot_open_common_official_editor/);
  assert.match(webRealEvidence, /prepareNonOfficialProfile/);
  assert.match(webRealEvidence, /select is_official from public\.community_profiles where id = \$1::uuid for update/);
  assert.match(webRealEvidence, /update public\.community_profiles set is_official = false where id = \$1::uuid/);
  assert.match(webRealEvidence, /restoreProfileOfficialRole/);
  assert.match(webRealEvidence, /permissionProfileRestore/);
  assert.match(packageJson.scripts["evidence:web-official-editor-permissions"], /--expect-ineligible/);
});

test("Web Official editor enables the shared Fang translator instead of publishing a single-language fallback", () => {
  assert.match(webOfficialHost, /import com\.quata\.core\.language\.BrowserTranslationHttpTransport/);
  assert.match(webOfficialHost, /import com\.quata\.core\.language\.FangTranslationService/);
  assert.match(webOfficialHost, /OfficialPostEditorFangTranslator\(FangTranslationService\(transport = BrowserTranslationHttpTransport\(\)\)\)/);
  assert.match(webOfficialHost, /translator = translator/);
  assert.doesNotMatch(webOfficialHost, /translator = null/);
});

test("Web Official editor detects the source language with the shared FastText model", () => {
  assert.match(webOfficialHost, /detectOfficialPostLanguage\(/);
  assert.match(webOfficialHost, /identifier = BrowserFastTextLanguageIdentifier/);
  assert.doesNotMatch(webOfficialHost, /detectLanguage = \{\s*webOfficialPostLanguage\(\)\s*\}/);
});

test("Web exact Official post reads use the restored session before public fallback", () => {
  assert.match(webOfficialRepository, /override suspend fun getOfficialPost\(postId: String\)[\s\S]*authMode = exactPostReadAuthMode\(\)/);
  assert.match(webOfficialRepository, /private suspend fun exactPostReadAuthMode\(\): WebPostgrestAuthMode =[\s\S]*authRepository\.currentWebPushCredentials\(\) != null[\s\S]*WebPostgrestAuthMode\.SessionRequired[\s\S]*WebPostgrestAuthMode\.Public/);
  assert.match(webOfficialRepository, /override fun observeOfficialFeed\(\): Flow<Result<List<OfficialPostItem>>> = flow \{[\s\S]*emit\(loadFeed\(limit = FeedPageSize\)\)/);
});

test("iOS exact Official post reads mirror Web session-first behavior without authenticating public feed snapshots", () => {
  assert.match(iosOfficialRepository, /private enum class IosOfficialReadAuthMode[\s\S]*Public[\s\S]*SessionRequired/);
  assert.match(iosOfficialRepository, /override suspend fun getOfficialPost\(postId: String\)[\s\S]*authMode = exactPostReadAuthMode\(\)/);
  assert.match(iosOfficialRepository, /private suspend fun exactPostReadAuthMode\(\): IosOfficialReadAuthMode =[\s\S]*authSession\?\.currentSession\(\)\?\.bearerToken\?\.isNotBlank\(\) == true[\s\S]*IosOfficialReadAuthMode\.SessionRequired[\s\S]*IosOfficialReadAuthMode\.Public/);
  assert.match(iosOfficialRepository, /IosOfficialReadAuthMode\.SessionRequired -> authenticatedRows\(/);
  assert.match(iosOfficialRepository, /override fun observeOfficialFeed\(\): Flow<Result<List<OfficialPostItem>>> = flow \{[\s\S]*emit\(loadFeed\(limit = FeedPageSize\)\)/);
  assert.match(iosOfficialRepository, /override suspend fun getOfficialFeed\(\): Result<List<OfficialPostItem>> = loadFeed\(limit = FeedPageSize\)/);
  assert.match(iosOfficialRepository, /Public Official feed reads use only the Supabase publishable key/);
});

test("Web Official editor media preview stays in Compose canvas under common dialogs", () => {
  const preview = webOfficialHost.match(/private fun BrowserOfficialEditorMedia[\s\S]*?\n}\n/)?.[0] ?? "";
  assert.match(preview, /BrowserCanvasImage\(media\.url/);
  assert.match(preview, /BrowserOfficialVideoThumbnail\(media\.url/);
  assert.doesNotMatch(preview, /WebElementView|document\.createElement\("(img|video)"\)/);
});

test("Web local sessions persist the official role for restore and refresh", () => {
  assert.match(webAuthRepository, /val isOfficial: Boolean = false/);
  assert.match(webAuthRepository, /const val IsOfficial = "quata_web_is_official"/);
  assert.match(webAuthRepository, /preferences\.putString\(WebAuthStorage\.IsOfficial, isOfficial\.toString\(\)\)/);
  assert.match(webAuthRepository, /preferences\.getString\(WebAuthStorage\.IsOfficial\)\.toBoolean\(\)/);
  assert.match(webAuthRepository, /isOfficial = session\.isOfficial/);
  assert.match(webAuthRepository, /fetchAuthenticatedProfileIsOfficial\(rawSession\.bearerToken, rawSession\.userId\)/);
  assert.match(webAuthRepository, /\/rest\/v1\/community_profiles\?select=is_official&id=eq\.\$profileId&limit=1/);
  assert.match(webAuthRepository, /Authorization: `Bearer/);
});
