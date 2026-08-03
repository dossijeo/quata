import assert from 'node:assert/strict';
import test from 'node:test';
import { lstat, readFile, realpath } from 'node:fs/promises';
import { resolve } from 'node:path';
import { loadAndValidateCapabilityMatrix, validateCapabilityMatrix } from './capability-matrix-contract.mjs';

const matrixPath = resolve(import.meta.dirname, '..', 'capabilities/platform-capability-matrix.json');
const matrix = () => readFile(matrixPath, 'utf8').then(JSON.parse);

test('CAPABILITY-DRIFT-001 emits the mandatory operation-complete Web/iOS/Android catalogue', async () => {
  const emitted = await loadAndValidateCapabilityMatrix();
  assert.equal(emitted.length, 13);
  assert.deepEqual(emitted.find(({ id }) => id === 'feed.read').platforms, { android: 'implemented', web: 'implemented', ios: 'implemented' });
  assert.deepEqual(emitted.find(({ id }) => id === 'feed.mutate').platforms, { android: 'implemented', web: 'implemented', ios: 'implemented' });
  assert.deepEqual(emitted.find(({ id }) => id === 'official.read').platforms, { android: 'implemented', web: 'implemented', ios: 'implemented' });
  assert.deepEqual(emitted.find(({ id }) => id === 'official.interact').platforms, { android: 'implemented', web: 'implemented', ios: 'implemented' });
  assert.deepEqual(emitted.find(({ id }) => id === 'official.publish').platforms, { android: 'implemented', web: 'blocked', ios: 'blocked' });
  assert.deepEqual(emitted.find(({ id }) => id === 'communities.community-chat.open').platforms, { android: 'implemented', web: 'implemented', ios: 'implemented' });
  assert.deepEqual(emitted.find(({ id }) => id === 'communities.private-chat.open').platforms, { android: 'implemented', web: 'implemented', ios: 'implemented' });
  assert.deepEqual(emitted.find(({ id }) => id === 'communities.read').platforms, { android: 'implemented', web: 'implemented', ios: 'implemented' });
  assert.deepEqual(emitted.find(({ id }) => id === 'communities.mutate').platforms, { android: 'implemented', web: 'implemented', ios: 'implemented' });
  assert.deepEqual(emitted.find(({ id }) => id === 'composer.publish').platforms, { android: 'implemented', web: 'contract-only', ios: 'blocked' });
});

test('CAPABILITY-DRIFT-001 fails closed for catalogue, state, schema and source drift', async () => {
  const original = await matrix();
  const cases = [
    ['one-ID matrix', (value) => { value.capabilities = value.capabilities.slice(0, 1); }],
    ['unknown ID', (value) => { value.capabilities[0].id = 'unknown.flow'; }],
    ['missing operation', (value) => { value.capabilities[1].operations.pop(); }],
    ['implemented Web mutation with dead blocker source intact', (value) => { value.capabilities[1].platforms.web.evidence[1].role = 'explicit-block'; }],
    ['evidence role incompatible with implemented state', (value) => { value.capabilities[1].platforms.web.evidence[1].role = 'read-only-adapter'; }],
    ['source hash replaced', (value) => { value.capabilities[1].platforms.web.evidence[1].sha256 = '0'.repeat(64); }],
    ['path traversal', (value) => { value.capabilities[1].platforms.web.evidence[1].path = '../outside.kt'; }],
    ['root marker', (value) => { value.generated = true; }],
    ['available marker', (value) => { value.capabilities[1].platforms.web.available = true; }],
    ['desktop platform', (value) => { value.capabilities[1].platforms.desktop = structuredClone(value.capabilities[1].platforms.web); }],
    ['unknown evidence property', (value) => { value.capabilities[1].platforms.web.evidence[0].note = 'trusted'; }],
    ['missing composition', (value) => { value.capabilities[1].platforms.web.evidence = value.capabilities[1].platforms.web.evidence.filter(({ role }) => role !== 'composition'); }],
    ['duplicate implementation evidence', (value) => { value.capabilities[0].platforms.android.evidence.push(structuredClone(value.capabilities[0].platforms.android.evidence[1])); }],
  ];
  for (const [name, mutate] of cases) await test(name, async () => {
    const candidate = structuredClone(original);
    mutate(candidate);
    await assert.rejects(() => validateCapabilityMatrix(candidate));
  });
});

test('decisive source changes cannot land without a reviewed matrix hash update', async () => {
  const original = await matrix();
  for (const decisiveSource of ['FeedRemoteDataSource.kt', 'PostgrestChatRepository.kt', 'PostComposerRepositoryImpl.kt']) {
    let changed = false;
    await assert.rejects(() => validateCapabilityMatrix(original, {
      readFile: async (path) => {
        const bytes = await readFile(path);
        if (!changed && String(path).endsWith(decisiveSource)) {
          changed = true;
          return Buffer.concat([bytes, Buffer.from('\n// simulated decisive source drift\n')]);
        }
        return bytes;
      },
    }), /source drift/);
    assert.equal(changed, true, `${decisiveSource} must be exercised by evidence validation`);
  }
});

test('a no-op APNs lifecycle bridge is detected as iOS push capability drift', async () => {
  const original = await matrix();
  let bridgeExercised = false;
  await assert.rejects(() => validateCapabilityMatrix(original, {
    readFile: async (path) => {
      const bytes = await readFile(path);
      if (String(path).endsWith('IosApnsLifecycleBridge.swift')) {
        bridgeExercised = true;
        return Buffer.from(bytes.toString('utf8').replace(
          'UIApplication.shared.registerForRemoteNotifications()',
          '// simulated no-op registration bridge',
        ));
      }
      return bytes;
    },
  }), /source drift/);
  assert.equal(bridgeExercised, true);
});

test('evidence paths reject symlinks and canonical paths outside the repository', async () => {
  const original = await matrix();
  await assert.rejects(() => validateCapabilityMatrix(original, {
    lstat: async (path) => {
      const stat = await lstat(path);
      return String(path).endsWith('FeedRepository.kt') ? { ...stat, isSymbolicLink: () => true } : stat;
    },
  }), /symlinked source/);
  await assert.rejects(() => validateCapabilityMatrix(original, {
    realpath: async (path) => String(path).endsWith('FeedRepository.kt') ? resolve(import.meta.dirname, '..', '..', 'outside.kt') : realpath(path),
  }), /resolved source escapes repository/);
});
