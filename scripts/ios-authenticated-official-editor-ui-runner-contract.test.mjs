import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const runner = await readFile(new URL('./run-ios-authenticated-official-editor-ui-test.sh', import.meta.url), 'utf8');

test('iOS authenticated Official editor UI runner seeds a real session and executes the opt-in host test', () => {
  assert.match(runner, /QUATA_IOS_AUTH_E2E_FILE/);
  assert.match(runner, /QUATA_IOS_DERIVED_DATA_PATH/);
  assert.match(runner, /QUATA_IOS_SIMULATOR_UDID/);
  assert.match(runner, /run-ios-command-watchdog\.py/);
  assert.match(runner, /patched_xctestrun="\$\(dirname "\$xctestrun"\)\//);
  assert.match(runner, /xcrun simctl bootstatus "\$QUATA_IOS_SIMULATOR_UDID" -b/);
  assert.match(runner, /env\['QUATA_IOS_AUTH_E2E_FILE'\] = credentials/);
  assert.match(runner, /env\['QUATA_IOS_AUTH_UI_E2E'\] = '1'/);
  assert.match(runner, /QuataIosAuthenticatedSessionSeederTests\/testSeedAuthenticatedSessionForVisualGates/);
  assert.match(runner, /QuataIosAuthenticatedOfficialEditorUITests\/testAuthenticatedSessionOpensRealOfficialEditor/);
  assert.match(runner, /check-ios-xctest-executed\.py/);
  assert.match(runner, /PASS_EXECUTED:%s/);
  assert.match(runner, /IOS_AUTHENTICATED_OFFICIAL_EDITOR_UI_GATE_PASSED/);
  assert.match(runner, /redact_diagnostics/);
  assert.match(runner, /\[REDACTED\]/);
  assert.doesNotMatch(runner, /21085800|\+240|68024260/);
});
