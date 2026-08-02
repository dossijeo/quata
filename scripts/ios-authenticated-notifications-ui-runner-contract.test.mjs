import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');

test('authenticated Notifications UI gate seeds Keychain then normal-launches the real host', async () => {
  const ui = await readFile(resolve(root, 'iosApp/iosAppUITests/QuataIosAuthenticatedNotificationsUITests.swift'), 'utf8');
  const runner = await readFile(resolve(root, 'scripts/run-ios-authenticated-notifications-ui-test.sh'), 'utf8');
  assert.match(ui, /QUATA_IOS_AUTH_UI_E2E/);
  assert.match(ui, /app\.launch\(\)/);
  assert.doesNotMatch(ui, /quata-ui-test-fixture|launchArguments/);
  assert.match(ui, /quata-ios-notifications-host/);
  assert.match(ui, /authenticated-notifications-real-host/);
  assert.match(ui, /label == %@ OR label MATCHES %@/);
  assert.match(ui, /\^Avisos, \[0-9\]\+\$/);
  assert.match(ui, /XCTAssertEqual\(alerts\.count, 1/);
  assert.match(ui, /alerts\.element\(boundBy: 0\)/);
  assert.doesNotMatch(ui, /app\.buttons\["Avisos"\]/);
  assert.doesNotMatch(ui, /alerts\.firstMatch/);
  assert.match(runner, /QUATA_IOS_AUTH_E2E_FILE/);
  assert.match(runner, /QUATA_IOS_AUTH_UI_E2E/);
  assert.match(runner, /xctestrun/);
  assert.match(runner, /xctestruns=\(\)/);
  assert.match(runner, /while IFS= read -r xctestrun_path/);
  assert.doesNotMatch(runner, /\bmapfile\b/);
  assert.doesNotMatch(runner, /find .*\| head/);
  assert.match(runner, /for key, target in data\.items\(\)/);
  assert.match(runner, /run-ios-command-watchdog\.py/);
  assert.match(runner, /run_bounded bootstatus 120/);
  assert.match(runner, /run_bounded "\$method" 120/);
  assert.match(runner, /timeout_diagnostics/);
  assert.match(runner, /testmanagerd/);
  assert.match(runner, /\[REDACTED\]/);
  assert.match(runner, /check-ios-xctest-executed\.py/);
  assert.match(runner, /--require-terminal-success-marker/);
  assert.match(runner, /PASS_EXECUTED:%s/);
  assert.match(runner, /HOST_SHELL_ONLY/);
  assert.match(runner, /testSeedAuthenticatedSessionForVisualGates/);
  assert.match(runner, /testAuthenticatedSessionOpensRealNotificationsFromFeed/);
});
test('runner rejects a green xcodebuild invocation without executed test evidence', async () => {
  const runner = await readFile(resolve(root, 'scripts/run-ios-authenticated-notifications-ui-test.sh'), 'utf8');
  const weakened = runner
    .replace(/\/usr\/bin\/python3 scripts\/check-ios-xctest-executed\.py[^\n]*\n/, '')
    .replace(/printf 'PASS_EXECUTED:%s\\n'[^\n]*\n/, '');
  assert.notEqual(weakened, runner);
  assert.doesNotMatch(weakened, /check-ios-xctest-executed\.py/);
  assert.doesNotMatch(weakened, /PASS_EXECUTED/);
  assert.match(weakened, /xcodebuild test-without-building/);
  assert.match(runner, /run_and_require/);
});

test('runner delegates only exact Xcode success markers to the XCTest evidence checker', async () => {
  const checker = await readFile(resolve(root, 'scripts/check-ios-xctest-executed.py'), 'utf8');
  assert.match(checker, /\*\* TEST SUCCEEDED \*\*/);
  assert.match(checker, /\*\* TEST EXECUTE SUCCEEDED \*\*/);
  assert.match(checker, /--require-terminal-success-marker/);
  assert.doesNotMatch(checker, /TEST \(EXECUTE \)\?SUCCEEDED/);
});

test('iOS notification factory ignores stale settings callbacks and refreshes the visible inbox', async () => {
  const swift = await readFile(resolve(root, 'iosApp/iosApp/QuataIosApp.swift'), 'utf8');
  assert.match(swift, /notificationsFactoryGeneration/);
  assert.match(swift, /notificationsFactoryGeneration == generation/);
  assert.match(swift, /isNotificationsVisible/);
  assert.match(swift, /authenticatedHost\.showNotifications\(\)/);
  assert.match(swift, /DispatchQueue\.main\.async/);
  assert.match(swift, /UIApplication\.didBecomeActiveNotification/);
  assert.match(swift, /UIApplication\.didEnterBackgroundNotification/);
  assert.match(swift, /hasValidatedAuthenticatedSession/);
  assert.match(swift, /setAppForeground\(isForeground: true\)/);
  assert.match(swift, /setAppForeground\(isForeground: false\)/);

  const sessionInstallation = swift.slice(
    swift.indexOf('private func installRestoredFeedSessionIfAvailable()'),
    swift.indexOf('private func validateRestoredFeedSessionAsynchronously()'),
  );
  const foregroundSeed = sessionInstallation.indexOf('UIApplication.shared.applicationState == .active');
  const installChat = sessionInstallation.indexOf('installAuthenticatedChatIfAvailable()');
  const installNotifications = sessionInstallation.indexOf('installNotificationsIfAvailable()');
  assert.ok(foregroundSeed >= 0, 'restored sessions must seed the Chat repository from UIKit state');
  assert.ok(foregroundSeed < installChat, 'foreground seed must precede private Chat installation');
  assert.ok(foregroundSeed < installNotifications, 'foreground seed must precede Notifications installation');
});
