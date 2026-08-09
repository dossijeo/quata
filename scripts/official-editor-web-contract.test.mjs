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

test("Web Official surface exposes the shared editor action for official users", () => {
  assert.match(webOfficialHost, /fun WebOfficialHost\(/);
  assert.match(webOfficialHost, /onCreateOfficialPost: \(\) -> Unit/);
  assert.match(webOfficialHost, /canCreateOfficialPost: Boolean/);
  assert.match(webOfficialHost, /canCreateOfficialPost = canCreateOfficialPost/);
  assert.doesNotMatch(webOfficialHost, /canCreateOfficialPost = true/);
});

test("Official publish eligibility remains owned by commonMain state", async () => {
  const commonHost = await readFile(
    new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt", import.meta.url),
    "utf8",
  );
  assert.match(commonHost, /val canPublish = state\.currentUser\?\.isOfficial == true && slots\.canCreateOfficialPost/);
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
