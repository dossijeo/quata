import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile(new URL("./official-editor-ios-real-evidence.mjs", import.meta.url), "utf8");
const shellRunner = await readFile(new URL("./run-ios-authenticated-official-editor-ui-test.sh", import.meta.url), "utf8");
const uiTest = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedOfficialEditorUITests.swift", import.meta.url), "utf8");
const iosHost = await readFile(new URL("../feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/QuataOfficialViewController.kt", import.meta.url), "utf8");
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
  assert.match(runner, /official-ios-ui-\$\{randomUUID\(\)\}/);
  assert.match(runner, /QUATA_IOS_OFFICIAL_EDITOR_MARKER/);
  assert.match(runner, /bash scripts\/run-ios-authenticated-official-editor-ui-test\.sh/);
  assert.match(runner, /const remoteHead = \(await runSshScript/);
  assert.match(runner, /phone: e164Phone\(config\.countryCode, options\.expectIneligible \? config\.nonOfficialPhone : config\.officialPhone\)/);
  assert.match(runner, /function e164Phone\(countryCode, phone\)/);
  assert.match(runner, /begin read only/);
  assert.match(runner, /select id, translation_group_id, media_url/);
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
  assert.match(shellRunner, /env\['QUATA_IOS_OFFICIAL_EDITOR_MARKER'\] = marker/);
  assert.match(shellRunner, /env\['QUATA_IOS_OFFICIAL_EDITOR_REAL_PUBLISH_OPT_IN'\] = opt_in/);
  assert.match(shellRunner, /QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_OPT_IN/);
  assert.match(shellRunner, /QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_TYPE/);
  assert.match(shellRunner, /QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_PATH/);
  assert.match(shellRunner, /env\['QUATA_IOS_OFFICIAL_EDITOR_EXPECT_INELIGIBLE'\] = expect_ineligible/);
  assert.match(shellRunner, /QUATA_IOS_OFFICIAL_EDITOR_UI_TIMEOUT_SECONDS:=300/);
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
  assert.match(uiTest, /quata-portable-rich-text-field/);
  assert.match(uiTest, /switchToAdvancedMode\(in: app\)/);
  assert.match(uiTest, /official-editor-mode-switch/);
  assert.match(uiTest, /app\.swipeDown\(\)/);
  assert.match(uiTest, /modeSwitch\.isHittable/);
  assert.match(uiTest, /official-editor-advanced-title/);
  assert.match(uiTest, /official-editor-advanced-summary/);
  assert.match(uiTest, /try selectMediaIfRequested\(in: app\)/);
  assert.match(uiTest, /OfficialEditorMediaEvidenceError/);
  assert.match(uiTest, /case previewMissing/);
  assert.match(uiTest, /official-editor-pick-image/);
  assert.match(uiTest, /official-editor-pick-video/);
  assert.match(uiTest, /official-editor-media-preview/);
  assert.match(uiTest, /authenticated-official-editor-real-image-preview/);
  assert.match(uiTest, /authenticated-official-editor-real-video-preview/);
  assert.match(uiTest, /mediaType == "image" \|\| mediaType == "video"/);
  assert.match(uiTest, /app\.launchEnvironment\[key\] = value/);
  assert.match(uiTest, /app\.keyboards\.count > 0/);
  assert.match(uiTest, /attempt < 5/);
  assert.match(uiTest, /app\.swipeDown\(\)/);
  assert.match(uiTest, /app\.swipeUp\(\)/);
  assert.match(uiTest, /hasKeyboardFocus == 1/);
  assert.match(uiTest, /focused\.typeText\(value\)/);
  assert.match(uiTest, /fallback\.typeText\(value\)/);
  assert.match(uiTest, /coordinate\(withNormalizedOffset: CGVector\(dx: 0\.5, dy: 0\.5\)\)\.tap\(\)/);
  assert.match(uiTest, /official-editor-publish/);
  assert.match(uiTest, /waitForPublishedPost\(in: app, marker: marker\)/);
  assert.match(uiTest, /String\(marker\.suffix\(8\)\)/);
  assert.match(uiTest, /authenticated-official-editor-real-publish-missing/);
  assert.match(uiTest, /Publicar solo este idioma/);
  assert.match(uiTest, /Publish only this language/);
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
