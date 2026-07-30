import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import test from 'node:test';
import { gzipSync } from 'node:zlib';
import { validatePullRequestApprovalPolicy } from './wasm-bundle-approval-policy.mjs';

const baseRevision = '0123456789abcdef0123456789abcdef01234567';
const currentRevision = '89abcdef0123456789abcdef0123456789abcdef';
const approvedBudget = budget('approved');
const proposedBudget = { state: 'proposed' };
const baseline = { capture: { sourceRevision: baseRevision }, files: [applicationAsset(), revisionMarker(baseRevision)] };

test('dedicated approved baseline PR accepts only baseline and budget artifacts', () => {
  assert.doesNotThrow(() => policy({
    changedFiles: ['docs/wasm-bundle-baseline.json', 'docs/wasm-bundle-budget.json'],
  }));
});

test('approved baseline rejects every non-artifact path', async (t) => {
  for (const path of [
    'docs/WASM_BUNDLE_OBSERVABILITY.md',
    'unknown/path.txt',
    '.github/actions/alter-gate/action.yml',
    '.github/workflows/web-android-pr.yml',
    'scripts/wasm-bundle-report.mjs',
    'app/src/main/AndroidManifest.xml',
    'document-reader/src/commonMain/kotlin/Reader.kt',
    'iosApp/iosApp.swift',
    'vosk_model_es/model.bin',
    'docs/renamed-baseline.json',
  ]) await t.test(path, () => {
    assert.throws(() => policy({ changedFiles: ['docs/wasm-bundle-baseline.json', path] }), /only baseline\/budget artifacts/);
  });
});

test('approved baseline permits only a proposed-to-approved state transition', async (t) => {
  const proposed = budget('proposed');
  assert.doesNotThrow(() => policy({ budget: budget('approved'), baseBudget: proposed }));
  for (const [name, mutate] of [
    ['raw margin', value => { value.maxGrowthBytes += 1; }],
    ['gzip margin', value => { value.maxGrowthGzipBytes += 1; }],
    ['baseline file', value => { value.baselineFile = 'other.json'; }],
    ['rationale', value => { value.rationale = 'changed'; }],
    ['extra field', value => { value.extra = true; }],
  ]) await t.test(name, () => {
    const head = budget('approved');
    mutate(head);
    assert.throws(() => policy({ budget: head, baseBudget: proposed }), /only transition/);
  });
});

test('approved baseline requires an identical approved budget at the trusted base', () => {
  assert.doesNotThrow(() => policy({ baseBudget: budget('approved') }));
  const head = budget('approved');
  head.rationale = 'changed';
  assert.throws(() => policy({ budget: head, baseBudget: budget('approved') }), /semantically identical/);
  assert.throws(() => policy({ baseBudget: undefined }), /semantically identical/);
});

test('approved baseline rejects a branch-selected SHA', () => {
  assert.throws(() => policy({ baseline: { capture: { ...baseline.capture, sourceRevision: '0'.repeat(40) } } }), /trusted PR base/);
});

test('approved baseline permits only the expected revision marker difference', () => {
  assert.doesNotThrow(() => policy());
});

test('approved baseline rejects an incorrect revision marker', () => {
  const files = [applicationAsset(), revisionMarker(baseRevision)];
  assert.throws(() => policy({ currentFiles: files }), /current distribution quata-source-revision\.txt must exactly encode/);
  assert.throws(() => policy({ baseline: { capture: baseline.capture, files: [applicationAsset(), revisionMarker(currentRevision)] } }), /baseline quata-source-revision\.txt must exactly encode/);
});

test('approved baseline rejects a different non-marker asset', () => {
  const files = [applicationAsset({ sha256: 'b'.repeat(64) }), revisionMarker(currentRevision)];
  assert.throws(() => policy({ currentFiles: files }), /except for its revision marker/);
});

test('approved baseline rejects an absent or extra revision marker', () => {
  assert.throws(() => policy({ currentFiles: [applicationAsset()] }), /requires exactly one quata-source-revision\.txt asset/);
  assert.throws(() => policy({ currentFiles: [applicationAsset(), revisionMarker(currentRevision), revisionMarker(currentRevision)] }), /requires exactly one quata-source-revision\.txt asset/);
});

test('ordinary payload PR and proposed policy preparation do not masquerade as approval', () => {
  assert.doesNotThrow(() => policy({ changedFiles: ['web/src/wasmJsMain/kotlin/Main.kt'] }));
  assert.doesNotThrow(() => policy({ budget: proposedBudget, changedFiles: ['docs/wasm-bundle-baseline.json', 'web/src/wasmJsMain/kotlin/Main.kt'] }));
});

function policy(overrides = {}) {
  return validatePullRequestApprovalPolicy({
    budget: approvedBudget,
    baseBudget: approvedBudget,
    baseline,
    baseRevision,
    changedFiles: ['docs/wasm-bundle-baseline.json'],
    currentFiles: [applicationAsset(), revisionMarker(currentRevision)],
    currentRevision,
    ...overrides,
  });
}

function applicationAsset(overrides = {}) {
  return {
    path: 'web.js',
    extension: '.js',
    bytes: 7,
    gzipBytes: 27,
    sha256: 'a'.repeat(64),
    contributor: 'JavaScript runtime/chunks',
    ...overrides,
  };
}

function revisionMarker(revision) {
  const content = Buffer.from(`${revision}\n`);
  return {
    path: 'quata-source-revision.txt',
    extension: '.txt',
    bytes: content.length,
    gzipBytes: gzipSync(content).length,
    sha256: createHash('sha256').update(content).digest('hex'),
    contributor: 'Static/runtime assets',
  };
}

function budget(state) {
  return {
    schemaVersion: 1,
    state,
    baselineFile: 'wasm-bundle-baseline.json',
    maxGrowthBytes: 1048576,
    maxGrowthGzipBytes: 262144,
    rationale: 'reviewed budget',
  };
}
