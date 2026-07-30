import { createHash } from 'node:crypto';
import { gzipSync } from 'node:zlib';

const ApprovalFiles = new Set([
  'docs/wasm-bundle-baseline.json',
  'docs/wasm-bundle-budget.json',
]);
const RevisionMarkerPath = 'quata-source-revision.txt';

export function validatePullRequestApprovalPolicy({ budget, baseline, baseBudget, baseRevision, changedFiles, currentFiles, currentRevision }) {
  if (budget?.state !== 'approved') return;
  const normalized = changedFiles.map(path => path.replaceAll('\\', '/'));
  if (!normalized.some(path => ApprovalFiles.has(path))) return;

  if (typeof baseRevision !== 'string' || !/^[0-9a-f]{40}$/i.test(baseRevision)) {
    throw new Error('Approved baseline PR policy requires the trusted base revision SHA');
  }
  if (baseline?.capture?.sourceRevision !== baseRevision) {
    throw new Error('Approved baseline sourceRevision must equal the trusted PR base revision');
  }
  const nonApprovalFiles = normalized.filter(path => !ApprovalFiles.has(path));
  if (nonApprovalFiles.length > 0) {
    throw new Error(`Approved baseline must be reviewed separately with only baseline/budget artifacts: ${nonApprovalFiles.join(', ')}`);
  }
  validateApprovalDistribution(baseline, currentFiles, currentRevision);
  validateBudgetTransition(baseBudget, budget);
}

function validateApprovalDistribution(baseline, currentFiles, currentRevision) {
  const baselineFiles = baseline?.files;
  const sourceRevision = baseline?.capture?.sourceRevision;
  assertRevisionMarker(baselineFiles, sourceRevision, 'baseline');
  assertRevisionMarker(currentFiles, currentRevision, 'current distribution');

  const baselineInventory = inventoryWithoutRevisionMarker(baselineFiles);
  const currentInventory = inventoryWithoutRevisionMarker(currentFiles);
  if (baselineInventory !== currentInventory) {
    throw new Error('Approved baseline inventory must equal the distribution built by the approval PR except for its revision marker');
  }
}

function assertRevisionMarker(files, revision, label) {
  if (typeof revision !== 'string' || !/^[0-9a-f]{40}$/i.test(revision)) {
    throw new Error(`${label} requires a valid compiled revision SHA`);
  }
  if (!Array.isArray(files)) throw new Error(`${label} requires an asset inventory`);
  const markers = files.filter(file => file?.path === RevisionMarkerPath);
  if (markers.length !== 1) throw new Error(`${label} requires exactly one ${RevisionMarkerPath} asset`);
  const expected = revisionMarker(revision);
  if (JSON.stringify(canonicalAsset(markers[0])) !== JSON.stringify(canonicalAsset(expected))) {
    throw new Error(`${label} ${RevisionMarkerPath} must exactly encode its revision SHA`);
  }
}

function inventoryWithoutRevisionMarker(files) {
  return JSON.stringify(files
    .filter(file => file?.path !== RevisionMarkerPath)
    .map(canonicalAsset)
    .sort((left, right) => left.path.localeCompare(right.path)));
}

function revisionMarker(revision) {
  const content = Buffer.from(`${revision}\n`);
  return {
    path: RevisionMarkerPath,
    extension: '.txt',
    bytes: content.length,
    gzipBytes: gzipSync(content).length,
    sha256: createHash('sha256').update(content).digest('hex'),
    contributor: 'Static/runtime assets',
  };
}

function canonicalAsset({ path, extension, bytes, gzipBytes, sha256, contributor } = {}) {
  return { path, extension, bytes, gzipBytes, sha256, contributor };
}

function validateBudgetTransition(baseBudget, budget) {
  if (baseBudget?.state === 'proposed') {
    if (budget.state !== 'approved' || canonicalBudget(baseBudget, true) !== canonicalBudget(budget, true)) {
      throw new Error('Approved baseline may only transition the trusted proposed budget state to approved');
    }
    return;
  }
  if (baseBudget?.state === 'approved' && canonicalBudget(baseBudget) === canonicalBudget(budget)) return;
  throw new Error('Approved baseline requires a semantically identical trusted approved budget');
}

function canonicalBudget(budget, omitState = false) {
  const value = omitState ? Object.fromEntries(Object.entries(budget).filter(([key]) => key !== 'state')) : budget;
  return JSON.stringify(canonicalize(value));
}

function canonicalize(value) {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonicalize(value[key])]));
  }
  return value;
}
