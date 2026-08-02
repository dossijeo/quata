import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const hostPath = resolve(root, 'web/src/wasmJsMain/kotlin/com/quata/web/WebProfileHost.kt');
const mainPath = resolve(root, 'web/src/wasmJsMain/kotlin/com/quata/web/Main.kt');

function assertProfileAppearanceWiring(host, main) {
  for (const required of [
    'touchFlowEnabled: Boolean,',
    'themeMode: QuataThemeMode,',
    'onTouchFlowEnabledChange: (Boolean) -> Unit,',
    'onThemeModeChange: (QuataThemeMode) -> Unit,',
  ]) assert.ok(host.includes(required), `WebProfileHost must require ${required}`);

  assert.doesNotMatch(host, /touchFlowEnabled:\s*Boolean\s*=|themeMode:\s*QuataThemeMode\s*=|onTouchFlowEnabledChange:[^\n]*=\s*\{\}|onThemeModeChange:[^\n]*=\s*\{\}/);
  assert.match(host, /ProfileScreenHost\([\s\S]*?touchFlowEnabled = touchFlowEnabled,[\s\S]*?onTouchFlowEnabledChange = onTouchFlowEnabledChange,[\s\S]*?themeMode = themeMode,[\s\S]*?onThemeModeChange = onThemeModeChange,/);
  assert.doesNotMatch(host, /touchFlowEnabled = false|themeMode = QuataThemeMode\.System|onTouchFlowEnabledChange = \{\}|onThemeModeChange = \{\}/);

  assert.match(main, /fun changeTouchFlowEnabled\(enabled: Boolean\) \{\s*touchFlowEnabled = enabled\s*scope\.launch \{ platformServices\.preferences\.putString\(WebTouchFlowEnabledKey, enabled\.toString\(\)\) \}\s*\}/);
  assert.match(main, /fun changeThemeMode\(mode: QuataThemeMode\) \{\s*themeMode = mode\s*scope\.launch \{ platformServices\.preferences\.putString\(WebThemeModeKey, mode\.storageValue\) \}\s*\}/);
  assert.match(main, /WebSettingsHost\([\s\S]*?touchFlowEnabled = touchFlowEnabled,[\s\S]*?themeMode = themeMode,[\s\S]*?onTouchFlowEnabledChange = ::changeTouchFlowEnabled,[\s\S]*?onThemeModeChange = ::changeThemeMode,/);
  assert.match(main, /WebProfileHost\([\s\S]*?touchFlowEnabled = touchFlowEnabled,[\s\S]*?themeMode = themeMode,[\s\S]*?onTouchFlowEnabledChange = ::changeTouchFlowEnabled,[\s\S]*?onThemeModeChange = ::changeThemeMode,/);
}

test('Web Profile shares live appearance state and persistence callbacks with Settings', async () => {
  const [host, main] = await Promise.all([readFile(hostPath, 'utf8'), readFile(mainPath, 'utf8')]);
  assertProfileAppearanceWiring(host, main);
});

test('Web Profile appearance contract fails closed for hardcodes, no-ops, or missing persistence', async (t) => {
  const [host, main] = await Promise.all([readFile(hostPath, 'utf8'), readFile(mainPath, 'utf8')]);
  const hostMutations = [
    ['touch flow hardcoded', host.replace('touchFlowEnabled = touchFlowEnabled,', 'touchFlowEnabled = false,')],
    ['theme hardcoded', host.replace('themeMode = themeMode,', 'themeMode = QuataThemeMode.System,')],
    ['host callback made no-op', host.replace('onThemeModeChange = onThemeModeChange,', 'onThemeModeChange = {},')],
  ];
  for (const [name, mutatedHost] of hostMutations) await t.test(name, () => {
    assert.throws(() => assertProfileAppearanceWiring(mutatedHost, main));
  });

  const mainMutations = [
    ['Profile callback made no-op', main.replace(/(WebProfileHost\([\s\S]*?)onTouchFlowEnabledChange = ::changeTouchFlowEnabled,/, '$1onTouchFlowEnabledChange = {},')],
    ['touch flow persistence removed', main.replace('scope.launch { platformServices.preferences.putString(WebTouchFlowEnabledKey, enabled.toString()) }', '')],
    ['theme persistence removed', main.replace('scope.launch { platformServices.preferences.putString(WebThemeModeKey, mode.storageValue) }', '')],
  ];
  for (const [name, mutatedMain] of mainMutations) await t.test(name, () => {
    assert.throws(() => assertProfileAppearanceWiring(host, mutatedMain));
  });
});
