import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = (relative) => readFile(resolve(root, relative), 'utf8');

test('SimulatorSigned is isolated from Debug and Release production signing', async () => {
  const [project, script] = await Promise.all([
    source('iosApp/project.yml'),
    source('scripts/build-ios-intel-simulator-signed.sh'),
  ]);
  assert.match(project, /SimulatorSigned: debug/);
  assert.match(project, /SimulatorSigned: Configuration\/QuataPublicRuntime\.debug\.xcconfig/);
  assert.match(project, /SimulatorSigned:\n\s+# Ad-hoc simulator signing cannot authorize restricted entitlements\.\n\s+# Keep this local visual-gate lane entitlement-free so launchd accepts it\.\n\s+CODE_SIGN_ENTITLEMENTS: ""/);
  assert.match(project, /Release:\n\s+# These values are deliberately supplied only by the signing environment\.\n\s+# A signed Release build/);
  assert.match(script, /-configuration SimulatorSigned/);
  assert.match(script, /CODE_SIGN_IDENTITY=-/);
  assert.match(script, /AD_HOC_CODE_SIGNING_ALLOWED=YES/);
  assert.match(script, /codesign --verify --deep --strict/);
  assert.match(script, /-name '\*\.xctest'/);
  assert.match(script, /-name '\*\.appex'/);
  assert.match(script, /codesign --force --sign - "\$app"/);
  assert.doesNotMatch(script, /codesign --force --sign - --entitlements/);
  assert.match(script, /application-identifier/);
});
