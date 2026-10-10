import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = await readFile(
  new URL('../app/src/androidTest/java/com/quata/feature/auth/presentation/AuthRecoveryProductBridgeInstrumentedTest.kt', import.meta.url),
  'utf8',
);

const start = source.indexOf('fun sharedLoginOpensSpanishRecoveryAndReturnsToLogin()');
const end = source.indexOf('\n    @Test', start + 1);
assert.notEqual(start, -1, 'Android focal Login-to-Recovery test must exist');
const focal = source.slice(start, end === -1 ? source.length : end);

test('Android starts from the normal shared Login in Spanish', () => {
  assert.match(focal, /AuthProductHostContent\(/);
  assert.match(focal, /AuthCatalog\.copy\(AuthCatalogLocale\.Spanish\)/);
  assert.doesNotMatch(focal, /initialDestination\s*=/);
});

test('Android uses the visible Spanish recovery action', () => {
  assert.match(focal, /"auth\.forgot-password"/);
  assert.match(focal, /"Olvidé mi contraseña"/);
  assert.match(focal, /performTouchInput \{ click\(center\) \}/);
});

test('Android verifies the shared Recovery root and observed Back label', () => {
  assert.match(focal, /ForgotPasswordTestTags\.Root/);
  assert.match(focal, /ForgotPasswordTestTags\.Back/);
  assert.match(focal, /"Volver"/);
});

test('Android returns through the visible Back action to Login', () => {
  assert.match(focal, /onNodeWithTag\("auth\.submit"/);
  assert.match(focal, /ForgotPasswordTestTags\.Root[\s\S]*assertDoesNotExist\(\)/);
});

test('Android focal remains hermetic and never submits recovery', () => {
  assert.doesNotMatch(focal, /ForgotPasswordTestTags\.Submit/);
  assert.doesNotMatch(focal, /resetPassword\(/);
});
