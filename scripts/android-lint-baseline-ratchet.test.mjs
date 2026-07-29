import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { issueOccurrences, occurrenceFingerprint, verifyPolicy } from './android-lint-baseline-ratchet.mjs';

const baseline = issues => `<?xml version="1.0" encoding="UTF-8"?>\n<issues format="6" by="lint">\n${issues.map(({ id, message = '', file, line = '1' }) => `  <issue id="${id}" message="${message}">\n    <location file="${file}" line="${line}"/>\n  </issue>`).join('\n')}\n</issues>\n`;
const appOriginal = [{ id: 'OldIssue', file: 'src/main/old.kt' }];
const readerOriginal = [{ id: 'ReaderIssue', file: 'src/main/reader.kt' }];

async function fixture({
  appIssues = appOriginal,
  readerIssues = readerOriginal,
  policyBaselines,
} = {}) {
  const root = await mkdtemp(join(tmpdir(), 'quata-lint-ratchet-'));
  const docs = join(root, 'docs');
  const app = join(root, 'app');
  const reader = join(root, 'document-reader');
  await Promise.all([mkdir(docs), mkdir(app), mkdir(reader)]);
  await Promise.all([
    writeFile(join(app, 'lint-baseline.xml'), baseline(appIssues)),
    writeFile(join(reader, 'lint-baseline.xml'), baseline(readerIssues)),
  ]);
  const defaults = [
    {
      path: '../app/lint-baseline.xml',
      allowedOccurrenceFingerprints: [...issueOccurrences(baseline(appOriginal))].map(occurrenceFingerprint),
    },
    {
      path: '../document-reader/lint-baseline.xml',
      allowedOccurrenceFingerprints: [...issueOccurrences(baseline(readerOriginal))].map(occurrenceFingerprint),
    },
  ];
  const policyPath = join(docs, 'policy.json');
  await writeFile(policyPath, JSON.stringify({
    schemaVersion: 1,
    baselines: policyBaselines ?? defaults,
  }));
  return policyPath;
}

test('ratchet accepts a baseline that only removes known occurrences', async () => {
  await verifyPolicy(await fixture({ appIssues: [] }));
});

test('ratchet rejects a same-ID same-count occurrence swap', async () => {
  const policyPath = await fixture({
    appIssues: [{ id: 'OldIssue', file: 'src/main/replacement.kt' }],
  });
  await assert.rejects(() => verifyPolicy(policyPath), /new or moved lint occurrence/);
});

test('ratchet rejects an empty baseline list', async () => {
  const policyPath = await fixture({ policyBaselines: [] });
  await assert.rejects(() => verifyPolicy(policyPath), /declare each required baseline exactly once/);
});

test('ratchet rejects an empty occurrence snapshot', async () => {
  const policyPath = await fixture({
    policyBaselines: [
      { path: '../app/lint-baseline.xml', allowedOccurrenceFingerprints: [] },
      {
        path: '../document-reader/lint-baseline.xml',
        allowedOccurrenceFingerprints: [...issueOccurrences(baseline(readerOriginal))].map(occurrenceFingerprint),
      },
    ],
  });
  await assert.rejects(() => verifyPolicy(policyPath), /Missing non-empty occurrence snapshot/);
});

test('ratchet rejects a missing required baseline path', async () => {
  const policyPath = await fixture({
    policyBaselines: [{
      path: '../app/lint-baseline.xml',
      allowedOccurrenceFingerprints: [...issueOccurrences(baseline(appOriginal))].map(occurrenceFingerprint),
    }],
  });
  await assert.rejects(() => verifyPolicy(policyPath), /declare each required baseline exactly once/);
});

test('ratchet rejects duplicate baseline paths', async () => {
  const occurrenceSnapshot = [...issueOccurrences(baseline(appOriginal))].map(occurrenceFingerprint);
  const policyPath = await fixture({
    policyBaselines: [
      { path: '../app/lint-baseline.xml', allowedOccurrenceFingerprints: occurrenceSnapshot },
      { path: '../app/lint-baseline.xml', allowedOccurrenceFingerprints: occurrenceSnapshot },
    ],
  });
  await assert.rejects(() => verifyPolicy(policyPath), /declare each required baseline exactly once/);
});
