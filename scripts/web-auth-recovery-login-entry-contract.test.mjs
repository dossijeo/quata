import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = await readFile(
  new URL('../feature/auth/src/commonTest/kotlin/com/quata/feature/auth/presentation/RecoverySecretUiPilotTest.kt', import.meta.url),
  'utf8',
);

const start = source.indexOf('fun commonNavigationOpensRecoveryAndReturnsToLogin()');
const end = source.indexOf('\n    private class ', start + 1);
assert.notEqual(start, -1, 'common Web/Wasm Login-to-Recovery test must exist');
const focal = source.slice(start, end === -1 ? source.length : end);

test('Web/Wasm starts from the normal shared Login in Spanish', () => {
  assert.match(focal, /AuthProductHostContent\(/);
  assert.match(focal, /AuthCatalog\.copy\(AuthCatalogLocale\.Spanish\)/);
  assert.doesNotMatch(focal, /initialDestination\s*=/);
});

test('Web/Wasm observes and activates the Spanish recovery action', () => {
  assert.match(focal, /onNodeWithTag\("auth\.forgot-password"\)[\s\S]*assertTextEquals\("Olvidé mi contraseña"\)[\s\S]*performClick\(\)/);
});

test('Web/Wasm observes Recovery and returns through the visible Back action', () => {
  assert.match(focal, /onNodeWithTag\("auth\.recovery\.root"\)\.assertExists\(\)/);
  assert.match(focal, /onNodeWithTag\("auth\.recovery\.back"\)[\s\S]*assertTextEquals\("Volver"\)[\s\S]*performClick\(\)/);
  assert.match(focal, /onNodeWithTag\("auth\.submit"\)\.assertExists\(\)/);
  assert.match(focal, /onNodeWithTag\("auth\.recovery\.root"\)\.assertDoesNotExist\(\)/);
});

test('Web/Wasm focal remains hermetic and never submits recovery', () => {
  assert.doesNotMatch(focal, /"auth\.recovery\.submit"/);
  assert.doesNotMatch(focal, /resetPassword\(/);
});
