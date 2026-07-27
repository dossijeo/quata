import assert from 'node:assert/strict';
import test from 'node:test';
import { validatePullRequestApprovalPolicy } from './wasm-bundle-approval-policy.mjs';

const baseRevision = '0123456789abcdef0123456789abcdef01234567';
const inventory = 'a'.repeat(64);
const approvedBudget = { state: 'approved' };
const proposedBudget = { state: 'proposed' };
const baseline = { capture: { sourceRevision: baseRevision, inventorySha256: inventory } };

test('dedicated approved baseline PR accepts documentation-only changes', () => {
  assert.doesNotThrow(() => policy({
    changedFiles: ['docs/wasm-bundle-baseline.json', 'docs/wasm-bundle-budget.json', 'docs/WASM_BUNDLE_OBSERVABILITY.md'],
  }));
});

test('approved baseline cannot be auto-approved with payload, runtime, build, or gate inputs', async (t) => {
  for (const path of [
    'web/src/wasmJsMain/kotlin/com/quata/web/Main.kt',
    'feature/feed/src/commonMain/kotlin/Feed.kt',
    'gradle/libs.versions.toml',
    'package-lock.json',
    'scripts/wasm-bundle-report.mjs',
  ]) await t.test(path, () => {
    assert.throws(() => policy({ changedFiles: ['docs/wasm-bundle-baseline.json', path] }), /reviewed separately/);
  });
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
    baseline,
    baseRevision,
    changedFiles: ['docs/wasm-bundle-baseline.json'],
    currentInventorySha256: inventory,
    ...overrides,
  });
}
