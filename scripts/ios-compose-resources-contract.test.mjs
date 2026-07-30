import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile, stat } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = (relative) => readFile(resolve(root, relative), 'utf8');
const canonicalDestination =
  'compose-resources/composeResources/quata.designsystem.generated.resources';
const resources = [
  'font/quata_feed_emoji_subset.ttf',
  'font/quata_header_logo_q_subset.ttf',
];

test('QuataIos copies common Compose resources to the canonical app-bundle path before signing', async () => {
  const [project, synchronizer, unsignedLane, signedLane] = await Promise.all([
    source('iosApp/project.yml'),
    source('scripts/sync-ios-compose-resources.sh'),
    source('scripts/build-ios-intel-simulator.sh'),
    source('scripts/build-ios-intel-simulator-signed.sh'),
  ]);

  assert.match(project, /ENABLE_USER_SCRIPT_SANDBOXING: "NO"/);
  assert.match(project, /postBuildScripts:/);
  assert.match(project, /Synchronize Compose resources into QuataIos\.app/);
  assert.match(project, /\$\{SRCROOT\}\/\.\.\/scripts\/sync-ios-compose-resources\.sh/);
  assert.match(project, /\$\{TARGET_BUILD_DIR\}\/\$\{UNLOCALIZED_RESOURCES_FOLDER_PATH\}/);
  assert.match(synchronizer, /designsystem\/src\/commonMain\/composeResources/);
  assert.match(synchronizer, new RegExp(canonicalDestination.replaceAll('/', '\\/')));
  assert.match(synchronizer, /cp -R "\$source_resources\/\." "\$destination_resources\/"/);

  for (const resource of resources) {
    assert.match(synchronizer, new RegExp(resource.replaceAll('/', '\\/')));
    assert.match(project, new RegExp(`${canonicalDestination}/${resource}`.replaceAll('/', '\\/')));
    assert.match(unsignedLane, /sync-ios-compose-resources\.sh --verify "\$app"/);
    assert.match(signedLane, /sync-ios-compose-resources\.sh --verify "\$app"/);
    await stat(resolve(root, 'designsystem', 'src', 'commonMain', 'composeResources', resource));
  }

  assert.ok(
    signedLane.indexOf('sync-ios-compose-resources.sh --verify "$app"') <
      signedLane.indexOf('codesign --force --sign - "$app"'),
    'the signed lane must verify Compose resources before final signing',
  );
});
