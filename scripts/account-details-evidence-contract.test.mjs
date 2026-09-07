import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
const profileHost = await readFile(
  new URL("../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt", import.meta.url),
  "utf8",
);
const profileRepository = await readFile(
  new URL("../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/data/KmpProfileRepository.kt", import.meta.url),
  "utf8",
);
const profileLifecycleTest = await readFile(
  new URL("../feature/profile/src/commonTest/kotlin/com/quata/feature/profile/presentation/ProfileViewModelLifecycleTest.kt", import.meta.url),
  "utf8",
);
const androidInstrumentedTest = await readFile(
  new URL("../app/src/androidTest/java/com/quata/feature/profile/presentation/ProfileDetailsRealInstrumentedTest.kt", import.meta.url),
  "utf8",
);
const androidRunner = await readFile(new URL("./account-details-android-evidence.mjs", import.meta.url), "utf8");
const webRunner = await readFile(new URL("./account-details-web-evidence.mjs", import.meta.url), "utf8");
const iosRunner = await readFile(new URL("./account-details-ios-evidence.mjs", import.meta.url), "utf8");
const iosShellRunner = await readFile(new URL("./run-ios-account-details-ui-test.sh", import.meta.url), "utf8");
const iosUiTest = await readFile(
  new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedAccountDetailsUITests.swift", import.meta.url),
  "utf8",
);
const webDetailsBridge = await readFile(
  new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebProfileDetailsE2eBridge.kt", import.meta.url),
  "utf8",
);
const webProfileHost = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebProfileHost.kt", import.meta.url), "utf8");

test("ACCOUNT-DETAILS exposes stable common anchors for three-platform replay", () => {
  for (const tag of [
    "profile.details.open",
    "profile.details.root",
    "profile.details.back",
    "profile.details.name",
    "profile.details.neighborhood",
    "profile.details.country-code",
    "profile.details.phone",
    "profile.details.secret-question",
    "profile.details.secret-answer",
    "profile.details.save",
    "profile.feedback.error",
    "profile.feedback.success",
  ]) {
    assert.match(profileHost, new RegExp(tag.replace(/[.]/g, "\\.")));
  }
});

test("ACCOUNT-DETAILS persists profile fields through the shared repository contract", () => {
  assert.match(profileRepository, /remote\.saveProfile\(session\.profileId, update\.copy\(avatarUri = avatarUrl\)\.toRemotePatch\(\)\)/);
  for (const column of [
    '"display_name"',
    '"nombre"',
    '"neighborhood"',
    '"barrio"',
    '"country_code"',
    '"code"',
    '"phone_local"',
    '"phone"',
    '"telefono"',
  ]) {
    assert.match(profileRepository, new RegExp(column));
  }
  assert.match(profileRepository, /sessions\.updateDisplayName\(session, update\.displayName\)/);
});

test("ACCOUNT-DETAILS stays separate from password legacy, avatar and SOS scopes", () => {
  assert.match(profileRepository, /requireProfilePasswordUpdateSupported\(update\.newPassword\)/);
  assert.match(profileRepository, /require\(newPassword\.isBlank\(\)\) \{ "profile_password_update_unavailable" \}/);
  assert.match(profileRepository, /remote\.saveEmergencyContacts\(session\.profileId, normalizedIds\)/);
  assert.match(profileHost, /ProfileAvatarChangeTestTag/);
  assert.match(profileHost, /ProfileDetailsOpenTestTag/);
});

test("ACCOUNT-DETAILS has deterministic common failure coverage", () => {
  assert.match(profileLifecycleTest, /account_details_save_failure_keeps_local_edits_without_success/);
  assert.match(profileLifecycleTest, /remote_profile_save_failed/);
  assert.match(profileLifecycleTest, /assertFalse\(viewModel\.uiState\.value\.successMessageTriggersProfileSaved\)/);
  assert.match(profileLifecycleTest, /savedProfileUpdates\.single\(\)/);
});

test("ACCOUNT-DETAILS Android evidence mutates real UI fields and restores profile", () => {
  assert.match(androidInstrumentedTest, /authenticatedUserUpdatesAccountDetailsFromCommonProfile/);
  for (const tag of [
    "ProfileDetailsOpenTestTag",
    "ProfileDetailsRootTestTag",
    "ProfileDetailsNameInputTestTag",
    "ProfileDetailsNeighborhoodInputTestTag",
    "ProfileDetailsPhoneInputTestTag",
    "ProfileDetailsSaveTestTag",
  ]) {
    assert.match(androidInstrumentedTest, new RegExp(tag));
  }
  assert.match(androidInstrumentedTest, /performTextReplacement\(value\)/);
  assert.match(androidInstrumentedTest, /device\.pressBack\(\)/);
  assert.match(androidInstrumentedTest, /waitForProfile\(profileId, update\)/);
  assert.match(androidInstrumentedTest, /restoreProfile\(profileId, original\)/);
  assert.match(androidInstrumentedTest, /cleanupRestored/);
  assert.doesNotMatch(androidInstrumentedTest, /NewPasswordChanged|ProfileAvatarChangeTestTag|SaveEmergencySettings/);
});

test("ACCOUNT-DETAILS Web evidence uses semantic replay and reversible profile cleanup", () => {
  assert.match(webRunner, /ACCOUNT-DETAILS-WEB-REAL-001/);
  for (const id of [
    "profile.details.open",
    "profile.details.root",
    "profile.details.name",
    "profile.details.neighborhood",
    "profile.details.phone",
    "profile.details.save",
  ]) {
    assert.match(webRunner, new RegExp(id.replace(/[.]/g, "\\.")));
  }
  assert.match(webRunner, /quata-account-details-e2e=1#profile/);
  assert.match(webRunner, /quata_account_details_e2e_opt_in/);
  assert.match(webRunner, /invokeAccountDetailsBridge\(page, "openDetails"/);
  assert.match(webRunner, /invokeAccountDetailsBridge\(page, "updateDetails"/);
  assert.match(webRunner, /invokeAccountDetailsBridge\(page, "saveProfile"/);
  assert.match(webRunner, /waitForAccountDetailsState\(page, update\)/);
  assert.match(webRunner, /waitForRemoteProfile\(backend, session, update\)/);
  assert.match(webRunner, /page\.reload/);
  assert.match(webRunner, /restoreProfile\(backend, session, original\)/);
  assert.match(webRunner, /profileRestored/);
  assert.doesNotMatch(webRunner, /__quataAccountAvatarE2EProduct|data-quata-account-avatar-bridge|ProfileAvatar/);
});

test("ACCOUNT-DETAILS Web bridge is localhost opt-in and exposes UI state markers", () => {
  assert.match(webDetailsBridge, /installWebProfileDetailsE2eBridge/);
  assert.match(webDetailsBridge, /hostname === 'localhost' \|\| location\?\.hostname === '127\.0\.0\.1'/);
  assert.match(webDetailsBridge, /quata-account-details-e2e'\) === '1'/);
  assert.match(webDetailsBridge, /I_ACCEPT_WEB_ACCOUNT_DETAILS_FIXTURE/);
  assert.match(webDetailsBridge, /__quataAccountDetailsE2EProduct/);
  assert.match(webDetailsBridge, /data-quata-account-details-display-name/);
  assert.match(webDetailsBridge, /data-quata-account-details-neighborhood/);
  assert.match(webDetailsBridge, /data-quata-account-details-country-code/);
  assert.match(webDetailsBridge, /data-quata-account-details-phone/);
  assert.match(webProfileHost, /installWebProfileDetailsE2eBridge\(openDetails, updateDetails, saveProfile, snapshotDetails\)/);
  assert.match(webProfileHost, /updateWebProfileDetailsE2eState\(visible, displayName, neighborhood, countryCode, phone\)/);
});

test("ACCOUNT-DETAILS iOS evidence uses shared identifiers, reload verification and cleanup", () => {
  assert.match(iosUiTest, /QuataIosAuthenticatedAccountDetailsUITests/);
  assert.match(iosUiTest, /QUATA_IOS_ACCOUNT_DETAILS_UI_E2E/);
  for (const identifier of [
    "navigation.primary.profile",
    "quata-ios-profile-sos-host",
    "profile.details.open",
    "profile.details.root",
    "profile.details.name",
    "profile.details.neighborhood",
    "profile.details.phone",
    "profile.details.save",
  ]) {
    assert.match(iosUiTest, new RegExp(identifier.replace(/[.]/g, "\\.")));
  }
  assert.match(iosUiTest, /app\.terminate\(\)/);
  assert.match(iosUiTest, /ios-account-details-reloaded/);
  assert.match(iosShellRunner, /QUATA_IOS_ACCOUNT_DETAILS_DISPLAY_NAME/);
  assert.match(iosShellRunner, /testSeedAuthenticatedSessionForVisualGates/);
  assert.match(iosShellRunner, /testAuthenticatedSessionChangesAccountDetailsFromCommonProfile/);
  assert.match(iosShellRunner, /run-ios-command-watchdog\.py/);
  assert.match(iosRunner, /ACCOUNT-DETAILS-IOS-REAL-001/);
  assert.match(iosRunner, /waitForRemoteProfile\(backend, session, update\)/);
  assert.match(iosRunner, /restoreProfile\(backend, session, original\)/);
  assert.match(iosRunner, /profileRestored/);
  assert.match(iosRunner, /mac_checkout_sha_matches_local_candidate/);
  assert.doesNotMatch(iosRunner, /QUATA_IOS_ACCOUNT_AVATAR|PICKER_FIXTURE|avatar/i);
});

test("ACCOUNT-DETAILS contract is part of fast local CI contracts", () => {
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/account-details-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/account-details-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["evidence:account-details-web"], /scripts\/account-details-web-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-details-android"], /scripts\/account-details-android-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-details-ios"], /scripts\/account-details-ios-evidence\.mjs/);
  assert.match(webRunner, /QUATA_ACCOUNT_DETAILS_CREDENTIALS_FILE/);
  assert.match(webRunner, /build-reports\/web\/account-details-evidence\.json/);
  assert.match(androidRunner, /ProfileDetailsRealInstrumentedTest#authenticatedUserUpdatesAccountDetailsFromCommonProfile/);
  assert.match(androidRunner, /QUATA_ACCOUNT_DETAILS_CREDENTIALS_FILE/);
  assert.match(androidRunner, /account-details-evidence/);
  assert.match(iosRunner, /QUATA_ACCOUNT_DETAILS_CREDENTIALS_FILE/);
  assert.match(iosRunner, /account-details-evidence/);
});
