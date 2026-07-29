import assert from 'node:assert/strict';
import test from 'node:test';
import { lstat, readFile, realpath } from 'node:fs/promises';
import { resolve } from 'node:path';
import { loadAndValidateCapabilityMatrix, validateCapabilityMatrix } from './capability-matrix-contract.mjs';

const matrixPath = resolve(import.meta.dirname, '..', 'capabilities/platform-capability-matrix.json');
const matrix = () => readFile(matrixPath, 'utf8').then(JSON.parse);

test('CAPABILITY-DRIFT-001 emits the mandatory operation-complete Web/iOS/Android catalogue', async () => {
  const emitted = await loadAndValidateCapabilityMatrix();
  assert.equal(emitted.length, 11);
  assert.deepEqual(emitted.find(({ id }) => id === 'feed.mutate').platforms, { android: 'implemented', web: 'blocked', ios: 'read-only' });
  assert.deepEqual(emitted.find(({ id }) => id === 'community-chat.open').platforms, { android: 'implemented', web: 'blocked', ios: 'implemented' });
  assert.deepEqual(emitted.find(({ id }) => id === 'communities.mutate').platforms, { android: 'implemented', web: 'blocked', ios: 'blocked' });
  assert.deepEqual(emitted.find(({ id }) => id === 'composer.publish').platforms, { android: 'implemented', web: 'contract-only', ios: 'blocked' });
});

test('CAPABILITY-DRIFT-001 fails closed for catalogue, state, proof and source drift', async () => {
  const original = await matrix();
  const cases = [
    ['one-ID matrix', (value) => { value.capabilities = value.capabilities.slice(0, 1); }],
    ['unknown ID', (value) => { value.capabilities[0].id = 'unknown.flow'; }],
    ['missing operation', (value) => { value.capabilities[1].operations.pop(); }],
    ['implemented Web mutation with dead blocker source intact', (value) => { value.capabilities[1].platforms.web.state = 'implemented'; value.capabilities[1].platforms.web.proof = 'implementation'; }],
    ['proof incompatible with blocked state', (value) => { value.capabilities[1].platforms.web.proof = 'implementation'; }],
    ['source hash replaced', (value) => { value.capabilities[1].platforms.web.evidence.sha256 = '0'.repeat(64); }],
    ['path traversal', (value) => { value.capabilities[1].platforms.web.evidence.path = '../outside.kt'; }],
  ];
  for (const [name, mutate] of cases) await test(name, async () => {
    const candidate = structuredClone(original);
    mutate(candidate);
    await assert.rejects(() => validateCapabilityMatrix(candidate));
  });
});

test('source changes cannot land without a reviewed matrix hash update', async () => {
  const original = await matrix();
  let changed = false;
  await assert.rejects(() => validateCapabilityMatrix(original, {
    readFile: async (path) => {
      const bytes = await readFile(path);
      if (!changed && String(path).endsWith('FeedRepository.kt')) {
        changed = true;
        return Buffer.concat([bytes, Buffer.from('\n// simulated source drift\n')]);
      }
      return bytes;
    },
  }), /source drift/);
  assert.equal(changed, true);
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
