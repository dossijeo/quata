const ApprovalFiles = new Set([
  'docs/wasm-bundle-baseline.json',
  'docs/wasm-bundle-budget.json',
]);

export function validatePullRequestApprovalPolicy({ budget, baseline, baseBudget, baseRevision, changedFiles, currentInventorySha256 }) {
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
  const nonApprovalFiles = normalized.filter(path => !ApprovalFiles.has(path));
  if (nonApprovalFiles.length > 0) {
    throw new Error(`Approved baseline must be reviewed separately with only baseline/budget artifacts: ${nonApprovalFiles.join(', ')}`);
  }
  validateBudgetTransition(baseBudget, budget);
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
