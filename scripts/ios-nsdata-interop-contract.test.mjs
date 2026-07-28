import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import test from 'node:test';

const unsafeBridge = /CFDataCreate\s*\([\s\S]{0,500}?\)\s*!!\s*as\s+NSData/g;
const migratedSources = [
  'core/src/iosMain/kotlin/com/quata/core/session/IosSupabaseAuthSessionRefresher.kt',
  'feature/auth/src/iosMain/kotlin/com/quata/feature/auth/data/IosAuthRepository.kt',
  'feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatAttachmentDownloader.kt',
  'feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosPostgrestChatTransport.kt',
  'feature/feed/src/iosMain/kotlin/com/quata/feature/feed/data/IosFeedReadTransport.kt',
  'feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt',
  'feature/official/src/iosMain/kotlin/com/quata/feature/official/data/IosOfficialReadRepository.kt',
  'feature/profile/src/iosMain/kotlin/com/quata/feature/profile/data/IosProfilePostgrestGateway.kt',
];

async function kotlinFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return kotlinFiles(path);
    return entry.isFile() && entry.name.endsWith('.kt') ? [path] : [];
  }));
  return nested.flat();
}

test('iOS Foundation data interop never casts a CFData pointer to NSData', async () => {
  const sources = await kotlinFiles('core/src').then(async (core) => [
    ...core,
    ...(await kotlinFiles('feature')),
    ...(await kotlinFiles('ios-shared/src')),
  ]);
  const offenders = [];
  for (const source of sources) {
    const text = await readFile(source, 'utf8');
    if (unsafeBridge.test(text)) offenders.push(source);
    unsafeBridge.lastIndex = 0;
  }
  assert.deepEqual(offenders, []);
});

test('all owned NSURLSession data paths use the shared Foundation-copy bridge', async () => {
  for (const source of migratedSources) {
    const text = await readFile(source, 'utf8');
    assert.match(text, /import com\.quata\.core\.data\.toFoundationData/);
    assert.doesNotMatch(text, /platform\.CoreFoundation\.CFDataCreate/);
  }

  const interop = await readFile('core/src/iosMain/kotlin/com/quata/core/data/FoundationDataInterop.kt', 'utf8');
  assert.match(interop, /NSData\.dataWithBytes\(pinned\.addressOf\(0\), length = size\.toULong\(\)\)/);
  assert.match(interop, /if \(isEmpty\(\)\) NSData\(\)/);
});

test('Keychain keeps its direct CFDataRef-to-Security ownership path', async () => {
  const keychain = await readFile('core/src/iosMain/kotlin/com/quata/core/preferences/IosKeychainSessionStorage.kt', 'utf8');
  assert.match(keychain, /CFDataCreate\(/);
  assert.match(keychain, /CFRelease\(data\)/);
  assert.doesNotMatch(keychain, /as\s+NSData/);
});
