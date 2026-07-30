import assert from 'node:assert/strict';
import test from 'node:test';
import { execFile as execFileCallback } from 'node:child_process';
import { mkdir, mkdtemp, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import { resolve, join } from 'node:path';

const execFile = promisify(execFileCallback);
const root = resolve(import.meta.dirname, '..');
const source = (relative) => readFile(resolve(root, relative), 'utf8');
const canonicalDestination =
  'compose-resources/composeResources/quata.designsystem.generated.resources';
const resources = [
  'font/quata_header_logo_q_subset.ttf',
  'drawable/quata_feed_emoji_sos.png',
  'drawable/quata_feed_emoji_rank.png',
  'drawable/quata_feed_emoji_location.png',
  'drawable/quata_feed_emoji_note.png',
  'drawable/quata_feed_emoji_document.png',
];
const drawableResources = resources.filter((resource) => resource.startsWith('drawable/'));

const bashPathEnvironment = () => {
  if (process.platform !== 'win32') return 'posix';
  return process.env.MSYSTEM ? 'git-bash' : 'wsl';
};

const toBashPath = (path, environment = bashPathEnvironment()) => {
  const windowsPath = /^([A-Za-z]):[\\/](.*)$/.exec(path);
  if (!windowsPath) return path;

  const [, drive, remainder] = windowsPath;
  const normalizedRemainder = remainder.replaceAll('\\', '/');
  if (environment === 'git-bash') return `/${drive.toLowerCase()}/${normalizedRemainder}`;
  if (environment === 'wsl') return `/mnt/${drive.toLowerCase()}/${normalizedRemainder}`;
  throw new Error(`Unsupported Bash path environment: ${environment}`);
};

test('bash paths preserve POSIX app bundles unchanged', () => {
  const appBundle = '/Users/runner/work/quata/build/QuataIos.app';
  assert.equal(toBashPath(appBundle, 'posix'), appBundle);
});

test('bash paths convert Windows drive-letter app bundles fail-closed by environment', () => {
  const appBundle = 'C:\\Users\\PC\\quata\\build\\QuataIos.app';
  assert.equal(toBashPath(appBundle, 'git-bash'), '/c/Users/PC/quata/build/QuataIos.app');
  assert.equal(toBashPath(appBundle, 'wsl'), '/mnt/c/Users/PC/quata/build/QuataIos.app');
  assert.throws(() => toBashPath(appBundle, 'unknown'), /Unsupported Bash path environment/);
});

test('QuataIos synchronizes the exact common Compose resource set before signing', async () => {
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
  assert.match(synchronizer, /rm -rf "\$destination_resources"/);
  assert.doesNotMatch(synchronizer, /quata_feed_emoji_subset\.ttf/);

  for (const resource of resources) {
    assert.match(synchronizer, new RegExp(resource.replaceAll('/', '\\/')));
    assert.match(project, new RegExp(`${canonicalDestination}/${resource}`.replaceAll('/', '\\/')));
    await stat(resolve(root, 'designsystem', 'src', 'commonMain', 'composeResources', resource));
  }
  assert.equal((project.match(/quata_feed_emoji_.*\.png/g) ?? []).length, drawableResources.length);
  assert.match(unsignedLane, /sync-ios-compose-resources\.sh --verify "\$app"/);
  assert.match(signedLane, /sync-ios-compose-resources\.sh --verify "\$app"/);
  assert.ok(
    signedLane.indexOf('sync-ios-compose-resources.sh --verify "$app"') <
      signedLane.indexOf('codesign --force --sign - "$app"'),
    'the signed lane must verify Compose resources before final signing',
  );
});

test('synchronizer produces and verifies exactly the six expected app-bundle resources', async (t) => {
  const temporaryParent = resolve(root, 'build');
  await mkdir(temporaryParent, { recursive: true });
  const temporaryRoot = await mkdtemp(resolve(temporaryParent, 'ios-compose-resources-contract-'));
  t.after(() => rm(temporaryRoot, { recursive: true, force: true }));
  const appBundle = join(temporaryRoot, 'QuataIos.app');
  const bashAppBundle = toBashPath(appBundle);
  const staleResource = join(appBundle, canonicalDestination, 'font', 'quata_feed_emoji_subset.ttf');

  await mkdir(appBundle, { recursive: true });
  await execFile('bash', ['scripts/sync-ios-compose-resources.sh', bashAppBundle], { cwd: root });
  const destination = join(appBundle, canonicalDestination);
  const actualDrawables = (await readdir(join(destination, 'drawable'))).sort();
  assert.deepEqual(actualDrawables, drawableResources.map((resource) => resource.slice('drawable/'.length)).sort());
  await Promise.all(resources.map((resource) => stat(join(destination, resource))));

  await writeFile(staleResource, 'stale COLR font');
  await assert.rejects(
    execFile('bash', ['scripts/sync-ios-compose-resources.sh', '--verify', bashAppBundle], { cwd: root }),
    /unexpected Compose resources/,
  );
});
