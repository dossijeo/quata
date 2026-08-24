import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');

async function source(path) {
  return readFile(resolve(root, path), 'utf8');
}

const settingsCommon = await source('feature/settings/src/commonMain/kotlin/com/quata/feature/settings/presentation/SettingsAppearanceControls.kt');
const webSettings = await source('web/src/wasmJsMain/kotlin/com/quata/web/WebSettingsHost.kt');
const iosSettings = await source('feature/settings/src/iosMain/kotlin/com/quata/feature/settings/presentation/IosSettingsHost.kt');
const iosSwift = await source('iosApp/iosApp/QuataIosApp.swift');
const androidProfile = await source('app/src/main/java/com/quata/feature/profile/presentation/ProfileScreen.kt');
const profileCommon = await source('feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt');

test('Settings route has one common host for shared UX blocks', () => {
  assert.match(settingsCommon, /fun SettingsScreenHost\(/);
  assert.match(settingsCommon, /data class SettingsScreenStrings\(/);
  assert.match(settingsCommon, /data class SettingsNotificationsStrings\(/);
  assert.match(settingsCommon, /data class SettingsAccountLifecycleStrings\(/);
  assert.match(settingsCommon, /data class SettingsAccountLifecycleActions\(/);
  assert.match(settingsCommon, /AppearanceSettingsSectionContent\(/);
  assert.match(settingsCommon, /SettingsLegalDocumentsSectionContent\(/);
  assert.match(settingsCommon, /SettingsNotificationsSectionContent\(/);
  assert.match(settingsCommon, /SettingsAccountLifecycleSectionContent\(/);
  assert.match(settingsCommon, /QuataAccountLifecycleConfirmationDialogContent\(/);
  assert.match(settingsCommon, /SettingsScreenTestTags\.Root/);
  assert.match(settingsCommon, /SettingsScreenTestTags\.Notifications/);
  assert.match(settingsCommon, /SettingsScreenTestTags\.AccountLifecycle/);
  assert.match(settingsCommon, /SettingsScreenTestTags\.Logout/);
});

test('Web Settings uses common UX and only keeps native browser edges', () => {
  assert.match(webSettings, /SettingsScreenHost\(/);
  assert.match(webSettings, /notifications = SettingsNotificationsStrings\(/);
  assert.match(webSettings, /accountLifecycle = accountLifecycleActions\?\.let/);
  assert.match(webSettings, /SettingsAccountLifecycleActions\(/);
  assert.match(webSettings, /notificationAction = \{ enabled, strings, onEnabledChange ->[\s\S]*?WebNativeButton\(/);
  assert.match(webSettings, /documentOpener\.openWithViewerState\(file\)\.completed/);
  assert.doesNotMatch(webSettings, /ProfileAccountManagementContent\(/);
  assert.doesNotMatch(webSettings, /QuataAccountLifecycleConfirmationDialogContent\(/);
});

test('iOS Settings uses common UX and injects UIKit-only logout/document edges', () => {
  assert.match(iosSettings, /SettingsScreenHost\(/);
  assert.match(iosSettings, /logout = "Log out"/);
  assert.match(iosSettings, /onLogout = dependencies\.onLogout/);
  assert.match(iosSettings, /iosLegalDocumentFile\(document, dependencies\.language\)/);
  assert.match(iosSwift, /onLogout: \{ \[weak self\] in\s*self\?\.authenticatedHost\.performLogout\(\)\s*\}/);
  assert.doesNotMatch(iosSettings, /AppearanceSettingsSectionContent\(/);
  assert.doesNotMatch(iosSettings, /SettingsLegalDocumentsSectionContent\(/);
});

test('Android account surface keeps the same common settings blocks integrated in Cuenta', () => {
  assert.match(profileCommon, /AppearanceSettingsSectionContent\(/);
  assert.match(profileCommon, /OutlinedButton\(onClick = onLogout/);
  assert.match(androidProfile, /SettingsLegalDocumentsSectionContent\(/);
  assert.match(androidProfile, /LegalDocuments\.platformFile\(context, document\)/);
  assert.match(androidProfile, /documentOpenService\.openWithViewerState\(file\.value\)\.completed/);
});
