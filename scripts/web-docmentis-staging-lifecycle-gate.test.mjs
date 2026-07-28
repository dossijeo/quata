import assert from 'node:assert/strict';
import test from 'node:test';
import {
  AUTHORIZED_DOCMENTIS_STAGING_FORMATS,
  validateDocmentisStagingLifecycle,
} from './web-docmentis-staging-lifecycle-gate.mjs';

function transcript(fixtureId, format) {
  return {
    fixtureId,
    format,
    events: [
      { type: 'document:load', sequence: 1 },
      { type: 'customPageOverlay', sequence: 2 },
      { type: 'isLoaded', sequence: 3, value: true },
      { type: 'pageCount', sequence: 4, value: 1 },
      { type: 'cleanup', sequence: 5 },
    ],
  };
}

test('accepts only the four authorized staging fixture formats and required lifecycle', () => {
  assert.deepEqual(AUTHORIZED_DOCMENTIS_STAGING_FORMATS, ['PDF', 'DOCX', 'PPTX', 'XLSX']);
  for (const [fixtureId, format] of Object.entries({
    'staging-pdf': 'PDF', 'staging-docx': 'DOCX', 'staging-pptx': 'PPTX', 'staging-xlsx': 'XLSX',
  })) {
    const report = validateDocmentisStagingLifecycle(transcript(fixtureId, format));
    assert.equal(report.status, 'passed');
    assert.equal(report.visualEvidence, 'not_evaluated');
    assert.deepEqual(report.lifecycle, {
      documentLoad: true, customPageOverlay: true, isLoaded: true, pageCount: true, cleanup: true,
    });
  }
});

test('fails closed for missing, reordered, or unsuccessful lifecycle evidence', () => {
  const cases = [
    (() => { const value = transcript('staging-pdf', 'PDF'); value.events.pop(); return value; })(),
    (() => { const value = transcript('staging-pdf', 'PDF'); value.events[2].value = false; return value; })(),
    (() => { const value = transcript('staging-pdf', 'PDF'); value.events[3].value = 0; return value; })(),
    (() => { const value = transcript('staging-pdf', 'PDF'); [value.events[0], value.events[1]] = [value.events[1], value.events[0]]; return value; })(),
  ];
  for (const evidence of cases) assert.equal(validateDocmentisStagingLifecycle(evidence).status, 'failed');
});

test('rejects unknown fixtures, format mismatches, and unredacted event fields without reflecting them', () => {
  const secret = 'https://example.invalid/private?token=secret-value&email=person@example.invalid';
  const invalid = [
    transcript('staging-rtf', 'RTF'),
    transcript('staging-pdf', 'DOCX'),
    (() => { const value = transcript('staging-pdf', 'PDF'); value.events[0].url = secret; return value; })(),
    (() => { const value = transcript('staging-pdf', 'PDF'); value.events[0].storagePath = 'private/person/document.pdf'; return value; })(),
  ];
  for (const evidence of invalid) {
    const serializedReport = JSON.stringify(validateDocmentisStagingLifecycle(evidence));
    assert.doesNotMatch(serializedReport, /secret-value|person@example|private\/person|example\.invalid/);
    assert.match(serializedReport, /"status":"failed"/);
  }
});

test('is a hermetic lifecycle gate, never a render or pixel assertion', () => {
  const report = validateDocmentisStagingLifecycle(transcript('staging-pdf', 'PDF'));
  assert.equal(report.evidenceKind, 'docmentis_lifecycle');
  assert.equal(report.visualEvidence, 'not_evaluated');
  assert.equal(Object.hasOwn(report, 'rendered'), false);
  assert.equal(Object.hasOwn(report, 'pixels'), false);
});
