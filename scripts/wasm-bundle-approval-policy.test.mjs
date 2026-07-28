import assert from 'node:assert/strict';
import test from 'node:test';
import { validatePullRequestApprovalPolicy } from './wasm-bundle-approval-policy.mjs';

const baseRevision = '0123456789abcdef0123456789abcdef01234567';
const inventory = 'a'.repeat(64);
const approvedBudget = budget('approved');
const proposedBudget = { state: 'proposed' };
const baseline = { capture: { sourceRevision: baseRevision, inventorySha256: inventory } };

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

test('approved baseline rejects a branch-selected SHA or a fabricated inventory', () => {
  assert.throws(() => policy({ baseline: { capture: { ...baseline.capture, sourceRevision: '0'.repeat(40) } } }), /trusted PR base/);
  assert.throws(() => policy({ baseline: { capture: { ...baseline.capture, inventorySha256: '0'.repeat(64) } } }), /distribution built/);
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
    currentInventorySha256: inventory,
    ...overrides,
  });
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
