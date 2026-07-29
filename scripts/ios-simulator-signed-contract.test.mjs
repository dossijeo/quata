import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = (relative) => readFile(resolve(root, relative), 'utf8');

test('SimulatorSigned is isolated from Debug and Release production signing', async () => {
  const [project, entitlements, script] = await Promise.all([
    source('iosApp/project.yml'),
    source('iosApp/iosApp/QuataIosSimulatorSigned.entitlements'),
    source('scripts/build-ios-intel-simulator-signed.sh'),
  ]);
  assert.match(project, /SimulatorSigned: debug/);
  assert.match(project, /SimulatorSigned: Configuration\/QuataPublicRuntime\.debug\.xcconfig/);
  assert.match(project, /SimulatorSigned:\n\s+CODE_SIGN_ENTITLEMENTS: iosApp\/QuataIosSimulatorSigned\.entitlements/);
  assert.match(project, /Release:\n\s+# These values are deliberately supplied only by the signing environment\.\n\s+# A signed Release build/);
  assert.match(entitlements, /<key>keychain-access-groups<\/key>\s*<array>\s*<string>com\.quata\.ios<\/string>/);
  assert.doesNotMatch(entitlements, /aps-environment|application-groups/);
  assert.match(script, /-configuration SimulatorSigned/);
  assert.match(script, /CODE_SIGN_IDENTITY=-/);
  assert.match(script, /AD_HOC_CODE_SIGNING_ALLOWED=YES/);
  assert.match(script, /codesign --verify --deep --strict/);
  assert.match(script, /keychain-access-groups/);
});
