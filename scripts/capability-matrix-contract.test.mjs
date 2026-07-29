import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { loadAndValidateCapabilityMatrix, validateCapabilityMatrix } from './capability-matrix-contract.mjs';

const matrixPath = resolve(import.meta.dirname, '..', 'capabilities/platform-capability-matrix.json');
const matrix = () => readFile(matrixPath, 'utf8').then(JSON.parse);

test('CAPABILITY-DRIFT-001 emits a complete code-evidenced Web/iOS/Android matrix', async () => {
  const emitted = await loadAndValidateCapabilityMatrix();
  assert.equal(emitted.length, 8);
  assert.deepEqual(emitted.find(({ id }) => id === 'feed.mutate').platforms, { android: 'implemented', web: 'blocked', ios: 'read-only' });
  assert.deepEqual(emitted.find(({ id }) => id === 'official.mutate').platforms, { android: 'implemented', web: 'blocked', ios: 'blocked' });
  assert.deepEqual(emitted.find(({ id }) => id === 'composer.publish').platforms, { android: 'implemented', web: 'contract-only', ios: 'contract-only' });
});

test('CAPABILITY-DRIFT-001 fails closed for malformed, incomplete and unsupported mutation declarations', async () => {
  const original = await matrix();
  const cases = [
    ['unknown state', (value) => { value.capabilities[0].platforms.web.state = 'available'; }],
    ['missing platform', (value) => { delete value.capabilities[0].platforms.ios; }],
    ['missing source anchor', (value) => { value.capabilities[1].platforms.web.evidence[0].anchor = 'not-a-real-code-anchor'; }],
    ['path traversal', (value) => { value.capabilities[1].platforms.web.evidence[0].path = '../outside.kt'; }],
    ['external mutation', (value) => { value.capabilities[1].platforms.web.state = 'external'; }],
    ['duplicate identifier', (value) => { value.capabilities[1].id = value.capabilities[0].id; }],
  ];
  for (const [name, mutate] of cases) await test(name, async () => {
    const candidate = structuredClone(original);
    mutate(candidate);
    await assert.rejects(() => validateCapabilityMatrix(candidate));
  });
});
