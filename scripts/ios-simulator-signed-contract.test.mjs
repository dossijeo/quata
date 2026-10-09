import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = (relative) => readFile(resolve(root, relative), 'utf8');

test('SimulatorSigned is isolated from Debug and Release production signing', async () => {
  const [packageJson, project, script] = await Promise.all([
    source('package.json'),
    source('iosApp/project.yml'),
    source('scripts/build-ios-intel-simulator-signed.sh'),
  ]);
  const packageScripts = JSON.parse(packageJson).scripts;
  assert.match(packageScripts['test:ci-fast-contracts'], /scripts\/ios-simulator-signed-contract\.test\.mjs/);
  assert.match(project, /SimulatorSigned: debug/);
  assert.match(project, /SimulatorSigned: Configuration\/QuataPublicRuntime\.debug\.xcconfig/);
  assert.match(project, /SimulatorSigned:\n\s+# Ad-hoc simulator signing cannot authorize restricted entitlements\.\n\s+# Keep this local visual-gate lane entitlement-free so launchd accepts it\.\n\s+CODE_SIGN_ENTITLEMENTS: ""/);
  assert.match(project, /Release:\n\s+# These values are deliberately supplied only by the signing environment\.\n\s+# A signed Release build/);
  assert.match(script, /-configuration SimulatorSigned/);
  assert.match(script, /CODE_SIGN_IDENTITY=-/);
  assert.match(script, /AD_HOC_CODE_SIGNING_ALLOWED=YES/);
  assert.match(script, /raster_init_target="\$gradle_user_home\/init\.d\/hyperv-compose-raster\.init\.gradle"/);
  assert.match(script, /cp "\$raster_init_source" "\$raster_init_target"/);
  assert.match(script, /HYPERV_RASTER_REPOSITORY="\$raster_repository"/);
  assert.match(script, /:ios-shared:dependencyInsight/);
  assert.match(script, /--configuration iosX64CompileKlibraries/);
  assert.match(script, /org\.jetbrains\.skiko:skiko-iosx64:0\.9\.37\.3-hyperv-raster\.1-SNAPSHOT/);
  assert.match(script, /grep -Fqx 'org\.jetbrains\.skiko:skiko-iosx64:0\.9\.37\.3-hyperv-raster\.1-SNAPSHOT'/);
  assert.doesNotMatch(script, /grep -qx 'org\.jetbrains\.skiko:skiko-iosx64:/);
  assert.match(script, /codesign --verify --deep --strict/);
  assert.match(script, /-name '\*\.xctest'/);
  assert.match(script, /-name '\*\.appex'/);
  assert.match(script, /codesign --force --sign - "\$app"/);
  assert.doesNotMatch(script, /codesign --force --sign - --entitlements/);
  assert.match(script, /application-identifier/);
});
