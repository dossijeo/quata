import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile(new URL("./official-editor-android-real-evidence.mjs", import.meta.url), "utf8");
const androidTest = await readFile(
  new URL("../app/src/androidTest/java/com/quata/feature/official/presentation/OfficialEditorRealInstrumentedTest.kt", import.meta.url),
  "utf8",
);
const androidPermissionTest = await readFile(
  new URL("../app/src/androidTest/java/com/quata/feature/official/presentation/OfficialEditorPermissionInstrumentedTest.kt", import.meta.url),
  "utf8",
);
const androidNavGraph = await readFile(
  new URL("../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt", import.meta.url),
  "utf8",
);
const androidAuthRepository = await readFile(
  new URL("../app/src/main/java/com/quata/feature/auth/data/AuthRepositoryImpl.kt", import.meta.url),
  "utf8",
);
const androidPortableRichTextEditor = await readFile(
  new URL("../designsystem/src/commonMain/kotlin/com/quata/core/ui/richtext/QuataPortableRichTextEditor.kt", import.meta.url),
  "utf8",
);
const mainActivity = await readFile(new URL("../app/src/main/java/com/quata/MainActivity.kt", import.meta.url), "utf8");
const supabaseModels = await readFile(
  new URL("../app/src/main/java/com/quata/data/supabase/SupabaseModels.kt", import.meta.url),
  "utf8",
);
const officialPublishButton = await readFile(
  new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPublishButtonContent.kt", import.meta.url),
  "utf8",
);
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("Official editor Android real evidence is opt-in, redacted and reversible", () => {
  assert.match(runner, /OFFICIAL-EDITOR-ANDROID-REAL-UI-001/);
  assert.match(runner, /--expect-ineligible/);
  assert.match(runner, /OFFICIAL-EDITOR-ANDROID-PERMISSIONS-001/);
  assert.match(runner, /I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION/);
  assert.match(runner, /QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN/);
  assert.match(runner, /app-internal:official-editor-real-credentials\.json/);
  assert.match(runner, /delete from public\.official_posts/);
  assert.match(runner, /delete from public\.official_post_comments/);
  assert.match(runner, /delete from public\.official_post_likes/);
  assert.match(runner, /marker_cleanup_verification_failed/);
  assert.match(runner, /function resolveAdbCommand\(\)/);
  assert.match(runner, /platform-tools/);
  assert.match(runner, /android-official-editor-after-publish-tap\.png/);
  assert.match(runner, /android-official-editor-ineligible-blocked\.png/);
  assert.match(runner, /OfficialEditorPermissionInstrumentedTest/);
  assert.match(runner, /quataOfficialEditorExpectIneligible/);
  assert.match(runner, /prepareOfficialProfile/);
  assert.match(runner, /forced_official_for_evidence/);
  assert.match(runner, /official_profile_role_prepared_reversibly/);
  assert.match(runner, /prepareNonOfficialProfile/);
  assert.match(runner, /quata-auth-bridge/);
  assert.match(runner, /phone_local: config\.officialPhone/);
  assert.match(runner, /select id, is_official from public\.community_profiles where id = \$1::uuid for update/);
  assert.match(runner, /update public\.community_profiles set is_official = true where id = \$1::uuid/);
  assert.match(runner, /update public\.community_profiles set is_official = false where id = \$1::uuid/);
  assert.match(runner, /restoreProfileOfficialRole/);
  assert.match(runner, /adb.*run-as|run-as", "com\.quata"/);
  assert.doesNotMatch(runner, /SERVICE_ROLE|21085800|\+240|68024260/);

  assert.match(androidTest, /OfficialEditorRealInstrumentedTest/);
  assert.match(androidTest, /UiDevice\.getInstance/);
  assert.match(androidTest, /createEmptyComposeRule/);
  assert.match(androidTest, /START_DESTINATION_FOR_EVIDENCE", "official\/editor"/);
  assert.match(androidTest, /OfficialEditorRootTestTag/);
  assert.match(androidTest, /OfficialLongTextEditorBodyTestTag/);
  assert.match(androidTest, /OfficialLongTextEditorSaveTestTag/);
  assert.match(androidTest, /QuataPortableRichTextFieldTestTag/);
  assert.match(androidTest, /authRepository\.login/);
  assert.match(androidTest, /isSupabaseAuthenticated\(\) == true/);
  assert.match(androidTest, /session\.isOfficial/);
  assert.match(androidTest, /assertTextContains\("Add text"/);
  assert.match(androidTest, /saveScreenshot\("android-official-editor-after-publish-tap"\)/);
  assert.match(androidTest, /Publicar solo este idioma/);
  assert.match(androidTest, /By\.res\(targetContext\.packageName, OfficialEditorPublishTestTag\)/);
  assert.match(androidTest, /performTouchInput \{ click\(center\) \}/);
  assert.match(androidTest, /device\.displayHeight \* 0\.66f/);
  assert.match(androidTest, /device\.click\(device\.displayWidth \/ 2, bounds\.centerY\(\)\)/);
  assert.doesNotMatch(androidTest, /device\.pressBack\(\)/);
  assert.doesNotMatch(androidTest, /SERVICE_ROLE|21085800|\+240|68024260/);

  assert.match(androidPermissionTest, /OfficialEditorPermissionInstrumentedTest/);
  assert.match(androidPermissionTest, /OFFICIAL-EDITOR-ANDROID-PERMISSIONS-001/);
  assert.match(androidPermissionTest, /quataOfficialEditorExpectIneligible/);
  assert.match(androidPermissionTest, /START_DESTINATION_FOR_EVIDENCE", "official\/editor"/);
  assert.match(androidPermissionTest, /session\?\.isOfficial == true/);
  assert.match(androidPermissionTest, /OfficialEditorRootTestTag/);
  assert.match(androidPermissionTest, /OfficialCreateActionTestTag/);
  assert.match(androidPermissionTest, /android-official-editor-ineligible-blocked/);
  assert.doesNotMatch(androidPermissionTest, /SERVICE_ROLE|21085800|\+240|68024260/);

  assert.match(androidNavGraph, /officialEditorSession\?\.isOfficial == true/);
  assert.match(androidNavGraph, /requestAuthentication\(\)/);
  assert.match(androidNavGraph, /navController\.navigate\(AppDestinations\.Official\.route\)/);

  assert.match(mainActivity, /testTagsAsResourceId = true/);
  assert.match(mainActivity, /BuildConfig\.DEBUG/);
  assert.match(mainActivity, /EvidenceStartDestinations = setOf\([\s\S]*AppDestinations\.OfficialPostEditor\.route[\s\S]*\)/);
  assert.match(supabaseModels, /val is_official: Boolean\? = null/);
  assert.match(androidAuthRepository, /SupabaseCacheMode\.NETWORK_ONLY/);
  assert.match(androidAuthRepository, /fallbackProfile = profile/);
  assert.match(androidAuthRepository, /isOfficial = \(profile\.is_official \?: fallbackProfile\?\.is_official\) == true/);
  assert.match(androidPortableRichTextEditor, /const val QuataPortableRichTextFieldTestTag = "quata-portable-rich-text-field"/);
  assert.match(androidPortableRichTextEditor, /\.testTag\(QuataPortableRichTextFieldTestTag\)/);
  assert.match(officialPublishButton, /\.clickable\(enabled = clickEnabled, onClick = onClick\)/);
  assert.match(officialPublishButton, /role = Role\.Button/);
  assert.doesNotMatch(officialPublishButton, /import androidx\.compose\.material3\.Button/);
});

test("Official editor Android real evidence is callable but not automatic in fast CI", () => {
  assert.match(packageJson.scripts["evidence:android-official-editor-real"], /scripts\/official-editor-android-real-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:android-official-editor-permissions"], /--expect-ineligible/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/official-editor-android-real-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/official-editor-android-real-evidence-contract\.test\.mjs/);
  assert.doesNotMatch(packageJson.scripts["test:ci-fast-contracts"], /official-editor-android-real-evidence\.mjs/);
});
