import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const files = {
  host: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt",
  settingsAction: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsSettingsActionContent.kt",
  frame: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsDialogFrameContent.kt",
  header: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsHeaderContent.kt",
  editor: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsEditorContent.kt",
  contactActions: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsContactActionsContent.kt",
  selection: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsSelectionContent.kt",
  row: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyUserRowContent.kt",
  save: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsPortraitSaveButtonContent.kt",
  repository: "../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/data/KmpProfileRepository.kt",
  androidGateway: "../app/src/main/java/com/quata/feature/profile/data/AndroidProfileKmpAdapters.kt",
  androidProfile: "../app/src/main/java/com/quata/feature/profile/presentation/ProfileScreen.kt",
  androidNav: "../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt",
  webGateway: "../web/src/wasmJsMain/kotlin/com/quata/web/WebProfileRemoteGateway.kt",
  webHost: "../web/src/wasmJsMain/kotlin/com/quata/web/WebProfileHost.kt",
  iosGateway: "../feature/profile/src/iosMain/kotlin/com/quata/feature/profile/data/IosProfilePostgrestGateway.kt",
  iosProfileHost: "../feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileHost.kt",
  iosProfileSosHost: "../feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileSosHost.kt",
  iosProfileBootstrap: "../feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileSosRuntimeBootstrap.kt",
  iosApp: "../iosApp/iosApp/QuataIosApp.swift",
  webSosBridge: "../web/src/wasmJsMain/kotlin/com/quata/web/WebProfileSosE2eBridge.kt",
  androidEvidence: "../app/src/androidTest/java/com/quata/feature/profile/presentation/ProfileSosContactsInstrumentedTest.kt",
  iosEvidence: "../iosApp/iosAppUITests/QuataIosHostUITests.swift",
  webEvidence: "../scripts/web-authenticated-browser-e2e.mjs",
  packageJson: "../package.json",
};

async function source(path) {
  return readFile(new URL(path, import.meta.url), "utf8");
}

const loaded = Object.fromEntries(await Promise.all(
  Object.entries(files).map(async ([key, path]) => [key, await source(path)]),
));
const packageJson = JSON.parse(loaded.packageJson);

test("SOS contacts editor exposes shared semantic anchors from commonMain", () => {
  for (const [source, expected] of [
    [loaded.settingsAction, 'ProfileSosOpenTestTag = "profile.sos.open"'],
    [loaded.frame, 'ProfileSosRootTestTag = "profile.sos.root"'],
    [loaded.header, 'ProfileSosBackTestTag = "profile.sos.back"'],
    [loaded.header, 'ProfileSosContactsTabTestTag = "profile.sos.tab.contacts"'],
    [loaded.header, 'ProfileSosMessageTabTestTag = "profile.sos.tab.message"'],
    [loaded.selection, 'ProfileSosContactsListTestTag = "profile.sos.contacts.list"'],
    [loaded.selection, 'ProfileSosSearchTestTag = "profile.sos.search"'],
    [loaded.selection, 'ProfileSosErrorTestTag = "profile.sos.error"'],
    [loaded.contactActions, 'ProfileSosContactActionsTestTag = "profile.sos.contacts.actions"'],
    [loaded.contactActions, 'ProfileSosContactImportTestTag = "profile.sos.contacts.import"'],
    [loaded.contactActions, 'ProfileSosContactPermissionTestTag = "profile.sos.contacts.permission"'],
    [loaded.contactActions, 'ProfileSosContactStatusTestTag = "profile.sos.contacts.status"'],
    [loaded.row, 'ProfileSosContactRowTestTagPrefix = "profile.sos.contact."'],
    [loaded.row, 'ProfileSosContactToggleTestTagPrefix = "profile.sos.contact.toggle."'],
    [loaded.editor, 'ProfileSosMessageInputTestTag = "profile.sos.message.input"'],
    [loaded.save, 'ProfileSosSaveTestTag = "profile.sos.save"'],
  ]) {
    assert.match(source, new RegExp(expected.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
  assert.match(loaded.host, /EmergencyContactsSettingsActionContent\(strings\.configureEmergency, profile\.emergencyContactIds\.size, onClick = onSos\)/);
  assert.match(loaded.host, /EmergencyContactsDialogContent\(/);
  for (const source of [loaded.settingsAction, loaded.frame, loaded.header, loaded.editor, loaded.selection, loaded.contactActions, loaded.row, loaded.save]) {
    assert.match(source, /contentDescription = ProfileSos|contentDescription = tag|contentDescription = testTag/);
  }
});

test("SOS contacts import and permission actions stay shared across Android, Web and iOS", () => {
  assert.match(loaded.contactActions, /fun EmergencyContactsContactActionsContent\(/);
  assert.match(loaded.contactActions, /contacts\.pickContacts\(\)/);
  assert.match(loaded.contactActions, /permissions\.request\(PlatformPermission\.Contacts\)/);
  assert.match(loaded.contactActions, /contactsPermissionMessage\(status\)/);
  assert.match(loaded.editor, /val importContacts: String/);
  assert.match(loaded.editor, /val requestContactsPermission: String/);
  assert.match(loaded.androidProfile, /EmergencyContactsContactActionsContent\(/);
  assert.match(loaded.androidProfile, /contactPickerService: ContactPickerService = UnsupportedContactPickerService/);
  assert.match(loaded.androidProfile, /permissionService: PermissionService\? = null/);
  assert.match(loaded.androidNav, /contactPickerService = container\.platformServices\.contacts/);
  assert.match(loaded.androidNav, /permissionService = container\.permissionService/);
  assert.match(loaded.webHost, /EmergencyContactsContactActionsContent\(/);
  assert.match(loaded.webHost, /contacts = platformServices\.contacts/);
  assert.match(loaded.webHost, /permissions = platformServices\.permissions/);
  assert.match(loaded.iosProfileHost, /EmergencyContactsContactActionsContent\(/);
  assert.match(loaded.iosProfileSosHost, /EmergencyContactsContactActionsContent\(/);
  assert.match(loaded.iosProfileBootstrap, /contacts: ContactPickerService/);
  assert.match(loaded.iosProfileBootstrap, /permissions: PermissionService/);
  assert.match(loaded.iosApp, /contacts: self\.platformServices\.services\.contacts/);
  assert.match(loaded.iosApp, /permissions: self\.platformServices\.services\.permissions/);
});

test("SOS contacts save path stays common and normalized before platform gateways", () => {
  assert.match(loaded.repository, /override suspend fun saveEmergencySettings\(/);
  assert.match(loaded.repository, /val normalizedIds = normalizeEmergencyContactIds\(contactIds\)/);
  assert.match(loaded.repository, /remote\.saveEmergencyContacts\(session\.profileId, normalizedIds\)/);
  assert.match(loaded.repository, /emergencyContacts\.save\(session\.profileId, normalizedIds\)/);
  assert.match(loaded.repository, /emergencyMessages\.save\(session\.profileId, message, messageIsDefault\)/);
  assert.match(loaded.repository, /internal fun normalizeEmergencyContactIds\(contactIds: List<String>\): List<String> =/);
  assert.match(loaded.repository, /\.distinct\(\)\.take\(MaxEmergencyContacts\)/);
  assert.match(loaded.host, /onSosSelectionChanged/);
  assert.match(loaded.host, /onSosErrorChanged\(state\.errorMessage\)/);
});

test("SOS contacts gateways remain implemented on Android, Web and iOS", () => {
  assert.match(loaded.androidGateway, /override suspend fun saveEmergencyContacts\(profileId: String, contactIds: List<String>\)\s*=\s*source\.saveEmergencyContacts\(profileId, contactIds\)/);
  assert.match(loaded.webGateway, /override suspend fun saveEmergencyContacts\(/);
  assert.match(loaded.webGateway, /community_emergency_contacts/);
  assert.match(loaded.iosGateway, /override suspend fun saveEmergencyContacts\(/);
  assert.match(loaded.iosGateway, /CommunityEmergencyContactsTable/);
});

test("SOS contacts evidence runners exercise the shared anchors on Android, Web and iOS", () => {
  assert.match(loaded.androidEvidence, /ProfileSosContactsInstrumentedTest/);
  assert.match(loaded.androidEvidence, /ProfileSosRootTestTag/);
  assert.match(loaded.androidEvidence, /ProfileSosContactToggleTestTagPrefix/);
  assert.match(loaded.androidEvidence, /ProfileSosMessageInputTestTag/);
  assert.match(loaded.androidEvidence, /ProfileSosErrorTestTag/);
  assert.match(loaded.androidEvidence, /toggleEmergencyContactSelection\(selectedIds, contact\.id\)/);
  assert.match(loaded.iosEvidence, /testProfileSosFixtureRendersSharedContactsEditorAnchors/);
  assert.match(loaded.iosEvidence, /testProfileSosSaveFailureKeepsSharedErrorInDialog/);
  assert.match(loaded.iosEvidence, /"profile\.sos\.error"/);
  assert.match(loaded.iosEvidence, /-quata-ui-test-profile-sos-save-error/);
  assert.match(loaded.iosApp, /forceSosSaveError: arguments\.contains\("-quata-ui-test-profile-sos-save-error"\)/);
  assert.match(loaded.iosEvidence, /"profile\.sos\.contact\.toggle\.sos-fixture-6"/);
  assert.match(loaded.iosEvidence, /"profile\.sos\.tab\.message"/);
  assert.match(loaded.iosEvidence, /"profile\.sos\.message\.input"/);
  assert.match(loaded.iosEvidence, /coordinate\(withNormalizedOffset: CGVector\(dx: 0\.5, dy: 0\.5\)\)/);
  assert.match(loaded.webEvidence, /PROFILE_SOS_CANDIDATES/);
  assert.match(loaded.webEvidence, /selectFiveProfileSosContacts/);
  assert.match(loaded.webEvidence, /data-quata-profile-sos-selected-count/);
  assert.match(loaded.webEvidence, /assertAccountSosContactsEditor/);
  assert.match(loaded.webEvidence, /profile\.sos\.contacts\.actions/);
  assert.match(loaded.webEvidence, /profile\.sos\.contacts\.import/);
  assert.match(loaded.webEvidence, /profile\.sos\.contacts\.permission/);
  assert.match(loaded.webEvidence, /--profile-sos-save-error/);
  assert.match(loaded.webEvidence, /assertProfileSosSaveError/);
  assert.match(loaded.webEvidence, /waitForProfileSosCandidates/);
  assert.match(loaded.webEvidence, /quata-profile-sos-save-error-e2e/);
  assert.match(loaded.webEvidence, /quata\.profile\.sos\.save_error_e2e/);
  assert.match(loaded.webEvidence, /data-quata-profile-sos-error-visible/);
  assert.match(loaded.webEvidence, /data-quata-profile-sos-save-error-e2e/);
  assert.match(loaded.webEvidence, /profile_sos_save_error_opt_in_missing/);
  assert.match(loaded.webEvidence, /__quataProfileSosE2eProduct\?\.saveErrorE2e/);
  assert.match(loaded.webEvidence, /profile_sos_candidates_missing/);
  assert.match(loaded.webHost, /webProfileSosSaveErrorE2eEnabled/);
  assert.match(loaded.webHost, /quata-profile-sos-save-error-e2e/);
  assert.match(loaded.webHost, /quata\.profile\.sos\.save_error_e2e/);
  assert.match(loaded.webHost, /data-quata-profile-sos-save-error-e2e/);
  assert.match(loaded.webHost, /web_profile_sos_save_failed/);
  assert.match(loaded.webEvidence, /profileSosSaveError: options\.profileSosSaveError/);
  assert.match(loaded.webEvidence, /report\.accountSosContacts = await assertAccountSosContactsEditor/);
  assert.match(loaded.webEvidence, /missingDomAnchorReason/);
  assert.match(loaded.webEvidence, /wasm_canvas_semantics_not_dom_exposed/);
  assert.match(loaded.webEvidence, /__quataProfileSosE2eProduct/);
  assert.match(loaded.webEvidence, /landscape_split_visible/);
  assert.match(loaded.webEvidence, /wasm_canvas_relative_tab_fallback/);
  assert.match(loaded.webEvidence, /wasm_viewport_relative_save_button_fallback/);
  assert.match(loaded.webSosBridge, /data-quata-profile-sos-tab/);
  assert.match(loaded.webSosBridge, /data-quata-profile-sos-selected-count/);
  assert.match(loaded.webSosBridge, /data-quata-profile-sos-error-visible/);
  assert.match(loaded.webSosBridge, /data-quata-profile-sos-save-error-e2e/);
  assert.match(loaded.webSosBridge, /saveErrorE2e/);
  assert.match(loaded.webSosBridge, /quata\.profile\.sos\.save_error_e2e/);
});

test("SOS contacts contract is part of local fast contract suites", () => {
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/profile-sos-contacts-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/profile-sos-contacts-contract\.test\.mjs/);
});
