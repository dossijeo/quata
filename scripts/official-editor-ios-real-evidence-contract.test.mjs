import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile(new URL("./official-editor-ios-real-evidence.mjs", import.meta.url), "utf8");
const shellRunner = await readFile(new URL("./run-ios-authenticated-official-editor-ui-test.sh", import.meta.url), "utf8");
const watchdog = await readFile(new URL("./run-ios-command-watchdog.py", import.meta.url), "utf8");
const uiTest = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedOfficialEditorUITests.swift", import.meta.url), "utf8");
const iosHost = await readFile(new URL("../feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/QuataOfficialViewController.kt", import.meta.url), "utf8");
const advancedFieldsContent = await readFile(new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialAdvancedTextFieldsContent.kt", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("iOS Official editor real evidence is explicit opt-in, marker-based and cleans exact backend rows", () => {
  assert.match(runner, /OFFICIAL-EDITOR-IOS-REAL-UI-001/);
  assert.match(runner, /I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION/);
  assert.match(runner, /I_ACCEPT_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE/);
  assert.match(runner, /--media/);
  assert.match(runner, /unsupported_media/);
  assert.match(runner, /\["none", "image", "video"\]/);
  assert.match(runner, /QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN/);
  assert.match(runner, /QUATA_IOS_SIMULATOR_UDID/);
  assert.match(runner, /QUATA_IOS_OFFICIAL_EDITOR_UI_RESULT_BUNDLE_DIR/);
  assert.match(runner, /remoteResultBundleDir/);
  assert.match(runner, /mktemp -t quata-ios-official-editor-credentials/);
  assert.doesNotMatch(runner, /mktemp \/tmp\/quata-ios-official-editor-credentials\.XXXXXX\.json/);
  assert.match(runner, /official-ios-ui-\$\{randomUUID\(\)\}/);
  assert.match(runner, /QUATA_IOS_OFFICIAL_EDITOR_MARKER/);
  assert.match(runner, /bash scripts\/run-ios-authenticated-official-editor-ui-test\.sh/);
  assert.match(runner, /const remoteHead = \(await runSshScript/);
  assert.match(runner, /phone: e164Phone\(config\.countryCode, options\.expectIneligible \? config\.nonOfficialPhone : config\.officialPhone\)/);
  assert.match(runner, /function e164Phone\(countryCode, phone\)/);
  assert.match(runner, /prepareOfficialProfile/);
  assert.match(runner, /forced_official_for_evidence/);
  assert.match(runner, /official_profile_role_prepared_reversibly/);
  assert.match(runner, /update public\.community_profiles set is_official = true where id = \$1::uuid/);
  assert.match(runner, /begin read only/);
  assert.match(runner, /select id, translation_group_id, media_url, title, summary, content_html/);
  assert.match(runner, /created_body_html_readback_missing/);
  assert.match(runner, /bodyHtmlVerified: true/);
  assert.match(runner, /created_media_readback_missing/);
  assert.match(runner, /created_video_readback_missing/);
  assert.match(runner, /where title like \$1 or content_html like \$1/);
  assert.match(runner, /cleanupStorageObjects/);
  assert.match(runner, /quata-demo-video\.mp4/);
  assert.match(runner, /wordpressVideoUrlsFromMediaUrls/);
  assert.match(runner, /cleanupWordpressVideoUrls/);
  assert.match(runner, /assertWordpressVideoUrlsAbsent/);
  assert.match(runner, /quqos_delete_post_video/);
  assert.match(runner, /wp-admin\/admin-ajax\.php/);
  assert.match(runner, /Range|range: "bytes=0-0"/);
  assert.match(runner, /response\.payload\?\.session \?\? response\.payload \?\? response\.session/);
  assert.match(runner, /storageCleanup = \{[\s\S]*state: "rollback_pending"[\s\S]*storagePaths/);
  assert.match(runner, /wordpressVideoCleanup = \{[\s\S]*state: "rollback_pending"[\s\S]*wordpressVideoUrls/);
  assert.match(runner, /assertStorageObjectsAbsent/);
  assert.match(runner, /storage\.objects/);
  assert.match(runner, /await copyRemoteEvidence\(options\);/);
  assert.match(runner, /ios_remote_evidence_copied_locally/);
  assert.doesNotMatch(runner, /copyWarning/);
  assert.match(runner, /community-posts/);
  assert.match(runner, /resolvedIds/);
  assert.match(runner, /delete from public\.official_post_likes/);
  assert.match(runner, /delete from public\.official_post_comments/);
  assert.match(runner, /delete from public\.official_posts/);
  assert.match(runner, /verified_absent/);
  assert.match(runner, /rollback_pending/);
  assert.match(runner, /rejectUnauthorized: true/);
  assert.doesNotMatch(runner, /supabase db push|migration repair|service_role|SUPABASE_DB_URL\s*=/i);
  assert.doesNotMatch(runner, /21085800|\+240|68024260/);
});

test("iOS shell runner patches a temporary xctestrun and requires the real publish XCTest when marker is present", () => {
  assert.match(shellRunner, /patched_xctestrun="\$\(dirname "\$xctestrun"\)\//);
  assert.match(shellRunner, /cp "\$xctestrun" "\$patched_xctestrun"/);
  assert.match(shellRunner, /env\['QUATA_IOS_AUTH_UI_E2E'\] = '1'/);
  assert.match(shellRunner, /env\['QUATA_IOS_OFFICIAL_EDITOR_MARKER'\] = marker/);
  assert.match(shellRunner, /env\['QUATA_IOS_OFFICIAL_EDITOR_REAL_PUBLISH_OPT_IN'\] = opt_in/);
  assert.match(shellRunner, /QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_OPT_IN/);
  assert.match(shellRunner, /QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_TYPE/);
  assert.match(shellRunner, /QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_PATH/);
  assert.match(shellRunner, /env\['QUATA_IOS_OFFICIAL_EDITOR_EXPECT_INELIGIBLE'\] = expect_ineligible/);
  assert.match(shellRunner, /QUATA_IOS_OFFICIAL_EDITOR_UI_TIMEOUT_SECONDS:=300/);
  assert.match(shellRunner, /QUATA_IOS_OFFICIAL_EDITOR_UI_RESULT_BUNDLE_DIR:=/);
  assert.match(shellRunner, /run_bounded bootstatus 120 "\$QUATA_IOS_OFFICIAL_EDITOR_UI_LOG_DIR\/bootstatus\.log"/);
  assert.match(shellRunner, /xcrun simctl bootstatus "\$QUATA_IOS_SIMULATOR_UDID" -b/);
  assert.match(shellRunner, /set \+e\nrun_bounded bootstatus 120/);
  assert.match(shellRunner, /bootstatus_status=\$\?\nset -e/);
  assert.match(shellRunner, /if \[\[ "\$bootstatus_status" -ne 0 \]\]/);
  assert.match(shellRunner, /run_bounded simctl-list 20 "\$devices_log" xcrun simctl list devices/);
  assert.match(watchdog, /timeout=5/);
  assert.match(watchdog, /ps timed out/);
  assert.match(shellRunner, /timeout-devices\.log/);
  assert.match(shellRunner, /timeout-simulator\.log/);
  assert.match(shellRunner, /--timeout-seconds 10 --log "\$devices_diag"/);
  assert.match(shellRunner, /--timeout-seconds 15 --log "\$sim_log_diag"/);
  assert.match(shellRunner, /pgrep -fl 'testmanager\|QuataIos\|xcodebuild\|simctl\|run-ios-command-watchdog'/);
  assert.doesNotMatch(shellRunner, /ps -axo/);
  assert.match(shellRunner, /bootstatus returned \$bootstatus_status but selected simulator is Booted: \$QUATA_IOS_SIMULATOR_UDID/);
  assert.match(shellRunner, /grep -F "\$QUATA_IOS_SIMULATOR_UDID" "\$devices_log" \| grep -Fq "\(Booted\)"/);
  assert.match(shellRunner, /-resultBundlePath "\$result_bundle"/);
  assert.match(shellRunner, /run_bounded "\$method" "\$QUATA_IOS_OFFICIAL_EDITOR_UI_TIMEOUT_SECONDS"/);
  assert.match(shellRunner, /testAuthenticatedSessionCannotOpenOfficialEditorWhenIneligible/);
  assert.match(shellRunner, /testAuthenticatedSessionPublishesRealOfficialPost/);
  assert.match(shellRunner, /check-ios-xctest-executed\.py/);
});

test("iOS permission evidence covers non-official sessions without requesting mutation", () => {
  assert.match(runner, /--expect-ineligible/);
  assert.match(runner, /QUATA_OFFICIAL_E2E_NON_OFFICIAL_PHONE/);
  assert.match(runner, /REQUIRED_ENV\.filter\(\(name\) => name !== "QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN"\)/);
  assert.match(runner, /!options\.expectIneligible && process\.env\.QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN/);
  assert.match(runner, /QUATA_IOS_OFFICIAL_EDITOR_EXPECT_INELIGIBLE=1/);
  assert.match(runner, /verified_ineligible_session_cannot_open_editor/);
  assert.match(runner, /mutation: "not_requested"/);
  assert.match(runner, /prepareNonOfficialProfile/);
  assert.match(runner, /select is_official from public\.community_profiles where id = \$1::uuid for update/);
  assert.match(runner, /update public\.community_profiles set is_official = false where id = \$1::uuid/);
  assert.match(runner, /restoreProfileOfficialRole/);
  assert.match(runner, /permissionProfileRestore/);
  assert.match(uiTest, /testAuthenticatedSessionCannotOpenOfficialEditorWhenIneligible/);
  assert.match(uiTest, /QUATA_IOS_OFFICIAL_EDITOR_EXPECT_INELIGIBLE/);
  assert.match(uiTest, /authenticated-official-editor-ineligible-blocked/);
  assert.match(uiTest, /must not expose Crear comunicado/);
  assert.match(uiTest, /must not mount the Official editor host/);
  assert.match(packageJson.scripts["evidence:ios-official-editor-permissions"], /--expect-ineligible/);
});

test("iOS UI test performs validation, edits the common rich text field, publishes and skips translation only when shown", () => {
  assert.match(uiTest, /testAuthenticatedSessionPublishesRealOfficialPost/);
  assert.match(uiTest, /QUATA_IOS_OFFICIAL_EDITOR_REAL_PUBLISH_OPT_IN/);
  assert.match(uiTest, /QUATA_IOS_OFFICIAL_EDITOR_MARKER/);
  assert.match(uiTest, /official-editor-feedback/);
  assert.match(uiTest, /official-editor-body-action/);
  assert.match(uiTest, /official-editor-long-body/);
  assert.match(uiTest, /official-editor-long-save/);
  assert.match(uiTest, /quata-portable-rich-text-field/);
  const initialSurfaceAssertion = uiTest.slice(
    uiTest.indexOf("private func assertSharedEditorSurface"),
    uiTest.indexOf("private func selectMediaIfRequested"),
  );
  assert.doesNotMatch(initialSurfaceAssertion, /bodyAction\.tap\(\)/);
  const richTextBody = uiTest.slice(
    uiTest.indexOf("private func typeRichTextBody"),
    uiTest.indexOf("private func typeIntoFocusedElement"),
  );
  assert.match(richTextBody, /bodyAction\.tap\(\)/);
  assert.match(richTextBody, /official-editor-long-body/);
  assert.match(richTextBody, /quata-portable-rich-text-field/);
  assert.match(richTextBody, /pasteText\(value, into: richTextField, in: app\)/);
  assert.match(richTextBody, /official-editor-long-save/);
  assert.match(uiTest, /import UIKit/);
  assert.match(uiTest, /private func pasteText\(_ value: String, into element: XCUIElement, in app: XCUIApplication\)/);
  assert.match(uiTest, /UIPasteboard\.general\.string = value/);
  assert.match(uiTest, /app\.menuItems\[label\]/);
  assert.match(uiTest, /private func bodyEditorAction\(in app: XCUIApplication\) -> XCUIElement/);
  assert.match(uiTest, /app\.buttons\s*\n\s*\.matching\(identifier: "official-editor-body-action"\)/);
  assert.match(uiTest, /Editar descripción larga/);
  assert.match(uiTest, /Edit long description/);
  assert.match(uiTest, /Modifier la description longue/);
  assert.match(uiTest, /private func swipeEditorContentUp\(in app: XCUIApplication\)/);
  assert.match(uiTest, /private func swipeEditorContentDown\(in app: XCUIApplication\)/);
  assert.match(uiTest, /dy: 0\.47/);
  assert.match(richTextBody, /swipeEditorContentUp\(in: app\)/);
  assert.match(richTextBody, /swipeEditorContentDown\(in: app\)/);
  const focusedTyping = uiTest.slice(
    uiTest.indexOf("private func typeIntoFocusedElement"),
    uiTest.indexOf("private func tapTranslationSkipIfShown"),
  );
  assert.doesNotMatch(focusedTyping, /app\.typeText\(value\)/);
  assert.match(focusedTyping, /hasKeyboardFocus == 1/);
  assert.match(focusedTyping, /fallback\.tap\(\)/);
  const dismissKeyboard = uiTest.slice(
    uiTest.indexOf("private func dismissKeyboardIfPresent"),
    uiTest.indexOf("private func tapPublish"),
  );
  assert.doesNotMatch(dismissKeyboard, /Return|Intro|Retorno|typeText\("\\n"\)/);
  assert.match(dismissKeyboard, /key\.exists, key\.isHittable/);
  const publishTest = uiTest.slice(
    uiTest.indexOf("func testAuthenticatedSessionPublishesRealOfficialPost"),
    uiTest.indexOf("private func openOfficialEditor"),
  );
  assert.match(uiTest, /"QUATA_IOS_AUTH_UI_E2E"/);
  assert.match(uiTest, /openOfficialEditor\(launchEnvironment:/);
  assert.match(uiTest, /switchToAdvancedMode\(in: app\)/);
  assert.match(uiTest, /typeText\(titleText, into: "official-editor-advanced-title", in: app\)/);
  assert.match(uiTest, /typeText\(summaryText, into: "official-editor-advanced-summary", in: app\)/);
  assert.match(uiTest, /let bodyText = "BODY-IOS \\\(marker\)"/);
  assert.match(uiTest, /typeRichTextBody\(bodyText, in: app\)/);
  assert.ok(
    publishTest.indexOf("typeRichTextBody(bodyText, in: app)") <
      publishTest.indexOf('typeText(titleText, into: "official-editor-advanced-title", in: app)'),
    "iOS evidence must open the shared rich-text body editor before focusing multiline advanced fields.",
  );
  assert.match(uiTest, /assertDraftReady\(in: app, marker: marker\)/);
  assert.doesNotMatch(publishTest, /app\.terminate\(\)/);
  assert.doesNotMatch(publishTest, /QUATA_IOS_OFFICIAL_EDITOR_PREFILL_/);
  assert.match(uiTest, /official-editor-common-root/);
  assert.match(uiTest, /\\"bodyLength\\":0/);
  assert.match(uiTest, /\\"canPublish\\":true/);
  assert.match(uiTest, /official-editor-mode-switch/);
  assert.match(uiTest, /for attempt in 0\.\.<14/);
  assert.match(uiTest, /modeSwitch\.isHittable \|\| isVisibleOnScreen\(modeSwitch, in: app\)/);
  assert.doesNotMatch(uiTest, /The common Official editor mode switch must exist/);
  assert.match(uiTest, /app\.swipeDown\(\)/);
  assert.match(uiTest, /official-editor-advanced-title/);
  assert.match(uiTest, /official-editor-advanced-summary/);
  assert.match(uiTest, /try selectMediaIfRequested\(in: app\)/);
  assert.match(uiTest, /OfficialEditorMediaEvidenceError/);
  assert.match(uiTest, /case previewMissing/);
  assert.match(uiTest, /official-editor-pick-image/);
  assert.match(uiTest, /official-editor-pick-video/);
  assert.match(uiTest, /official-editor-media-preview/);
  assert.match(uiTest, /official-create-action/);
  const createNotice = uiTest.slice(
    uiTest.indexOf("private func officialCreateNotice"),
    uiTest.indexOf("private func assertSharedEditorSurface"),
  );
  assert.match(createNotice, /identifier == %@ OR identifier BEGINSWITH %@/);
  assert.match(createNotice, /"official-create-action"/);
  assert.match(createNotice, /"official\.action\.publish\."/);
  assert.doesNotMatch(createNotice, /label CONTAINS\[c\]/);
  assert.match(uiTest, /authenticated-official-editor-real-image-preview/);
  assert.match(uiTest, /authenticated-official-editor-real-video-preview/);
  assert.match(uiTest, /mediaType == "image" \|\| mediaType == "video"/);
  assert.match(uiTest, /app\.launchEnvironment\[key\] = value/);
  assert.match(uiTest, /app\.keyboards\.count > 0/);
  assert.doesNotMatch(uiTest, /focused\.typeText\("\\n"\)/);
  assert.match(uiTest, /dismissKeyboardIfPresent\(in: app\)/);
  assert.match(uiTest, /for attempt in 0\.\.<14/);
  assert.match(uiTest, /attempt < 8/);
  assert.match(uiTest, /isVisibleOnScreen\(_ element: XCUIElement, in app: XCUIApplication\)/);
  assert.match(uiTest, /field\.isHittable \|\| isVisibleOnScreen\(field, in: app\)/);
  assert.match(uiTest, /app\.swipeDown\(\)/);
  assert.match(uiTest, /app\.swipeUp\(\)/);
  assert.match(uiTest, /hasKeyboardFocus == 1/);
  assert.match(uiTest, /focused\.typeText\(value\)/);
  assert.match(uiTest, /fallback\.typeText\(value\)/);
  assert.match(uiTest, /coordinate\(withNormalizedOffset: CGVector\(dx: 0\.5, dy: 0\.5\)\)\.tap\(\)/);
  assert.match(uiTest, /official-editor-publish/);
  assert.match(uiTest, /waitForPublishedPost\(in: app, marker: marker\)/);
  const publishWait = uiTest.slice(
    uiTest.indexOf("private func waitForPublishedPost"),
    uiTest.indexOf("private enum OfficialEditorMediaEvidenceError"),
  );
  assert.match(publishWait, /official\.exists && !editor\.exists && publishedPost\.exists/);
  assert.match(publishWait, /var probe = 0/);
  assert.match(publishWait, /probe % 4 == 0/);
  assert.match(publishWait, /app\.swipeDown\(\)/);
  assert.match(publishWait, /app\.swipeUp\(\)/);
  assert.match(uiTest, /String\(marker\.suffix\(8\)\)/);
  assert.match(runner, /marker_rows_still_present/);
  assert.match(uiTest, /authenticated-official-editor-real-publish-missing/);
  assert.match(uiTest, /Publicar solo este idioma/);
  assert.match(uiTest, /Publish only this language/);
  assert.match(iosHost, /officialEditorEvidenceInitialDraft/);
  assert.match(iosHost, /QUATA_IOS_AUTH_UI_E2E/);
  assert.match(iosHost, /exposeE2eStateSemantics = officialEditorEvidenceSemanticsEnabled\(\)/);
  assert.match(iosHost, /officialEditorEvidenceSemanticsEnabled/);
  assert.match(iosHost, /OfficialEditorMode\.Advanced/);
  assert.match(advancedFieldsContent, /modifier = Modifier\.fillMaxWidth\(\)\.testTag\(OfficialEditorAdvancedTitleTestTag\)/);
  assert.match(advancedFieldsContent, /modifier = Modifier\.fillMaxWidth\(\)\.testTag\(OfficialEditorAdvancedSummaryTestTag\)/);
  assert.doesNotMatch(uiTest, /SUPABASE_DB_URL|service_role|21085800|\+240|68024260/);
});

test("iOS video fixture exposes common media state before native thumbnail work", () => {
  const selectMedia = iosHost.slice(iosHost.indexOf("fun selectMedia("), iosHost.indexOf("LaunchedEffect("));
  const firstVideoPick = selectMedia.indexOf("onPicked(OfficialEditorMedia(file.reference, OfficialMediaType.Video))");
  const firstThumbnail = selectMedia.indexOf("scope.launch {\n                    videoThumbnail = (dependencies.videoThumbnails.createThumbnail(file)");
  const secondVideoPick = selectMedia.lastIndexOf("onPicked(OfficialEditorMedia(file.reference, OfficialMediaType.Video))");
  const secondThumbnail = selectMedia.lastIndexOf("videoThumbnail = (dependencies.videoThumbnails.createThumbnail(file)");

  assert.match(iosHost, /officialEditorEvidenceMediaFixture\(type\)\?\.let/);
  assert.ok(firstVideoPick >= 0 && firstThumbnail >= 0 && firstVideoPick < firstThumbnail);
  assert.ok(secondVideoPick >= 0 && secondThumbnail >= 0 && secondVideoPick < secondThumbnail);
});

test("iOS real Official editor evidence is part of the fast and wave2 contract suites", () => {
  assert.match(packageJson.scripts["evidence:ios-official-editor-real"], /scripts\/official-editor-ios-real-evidence\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/official-editor-ios-real-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/official-editor-ios-real-evidence-contract\.test\.mjs/);
});
