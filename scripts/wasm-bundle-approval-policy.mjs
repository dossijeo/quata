const ApprovalFiles = new Set([
  'docs/wasm-bundle-baseline.json',
  'docs/wasm-bundle-budget.json',
]);

const BundleInputPrefixes = [
  'build-logic/', 'core/', 'designsystem/', 'feature/', 'gradle/', 'kotlin-js-store/', 'web/',
];

const BundleInputFiles = new Set([
  'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'package.json',
  'package-lock.json', 'settings.gradle.kts', 'scripts/wasm-bundle-approval-policy.mjs',
  'scripts/wasm-bundle-approval-policy.test.mjs', 'scripts/wasm-bundle-report.mjs',
  'scripts/wasm-bundle-report.test.mjs', 'scripts/run-wasm-production-observed.ps1',
  'scripts/web-android-pr-workflow-contract.test.mjs',
  '.github/workflows/web-android-pr.yml',
]);

export function validatePullRequestApprovalPolicy({ budget, baseline, baseRevision, changedFiles, currentInventorySha256 }) {
  if (budget?.state !== 'approved') return;
  const normalized = changedFiles.map(path => path.replaceAll('\\', '/'));
  if (!normalized.some(path => ApprovalFiles.has(path))) return;

  if (typeof baseRevision !== 'string' || !/^[0-9a-f]{40}$/i.test(baseRevision)) {
    throw new Error('Approved baseline PR policy requires the trusted base revision SHA');
  }
  if (baseline?.capture?.sourceRevision !== baseRevision) {
    throw new Error('Approved baseline sourceRevision must equal the trusted PR base revision');
  }
  if (baseline?.capture?.inventorySha256 !== currentInventorySha256) {
    throw new Error('Approved baseline inventory must equal the distribution built by the approval PR');
  }
  const mixedInputs = normalized.filter(isBundleOrGateInput);
  if (mixedInputs.length > 0) {
    throw new Error(`Approved baseline must be reviewed separately from bundle/gate inputs: ${mixedInputs.join(', ')}`);
  }
}

export function isBundleOrGateInput(path) {
  const normalized = path.replaceAll('\\', '/');
  return BundleInputFiles.has(normalized) || BundleInputPrefixes.some(prefix => normalized.startsWith(prefix));
}
