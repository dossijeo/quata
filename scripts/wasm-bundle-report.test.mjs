import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawnSync } from 'node:child_process';
import { copyFile, mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '..');
const script = join(root, 'scripts', 'wasm-bundle-report.mjs');
const approvalPolicyScript = join(root, 'scripts', 'wasm-bundle-approval-policy.mjs');
const fixtureRevision = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
const fixtureTreeSha256 = createHash('sha256').update(execFileSync('git', ['ls-tree', '-r', '--full-tree', '-z', fixtureRevision], { cwd: root })).digest('hex');

test('approved bundle budget accepts a source-tree and inventory attested baseline', async () => {
  const fixture = await createFixture();
  const report = await reportFor(fixture);
  await writeBudget(fixture, report, { maxGrowthBytes: 0, maxGrowthGzipBytes: 0 });

  assert.doesNotThrow(() => run(fixture, '--budget', fixture.budget));
});

test('approved bundle budget rejects absent, invalid, mismatched, and manipulated baselines', async (t) => {
  const cases = [
    ['absent baseline', async fixture => rm(fixture.baseline), /baseline is unavailable/],
    ['invalid revision', async fixture => mutateBaseline(fixture, baseline => { baseline.revision = 'not-a-sha'; }), /revision SHA/],
    ['source revision mismatch', async fixture => mutateBaseline(fixture, baseline => { baseline.capture.sourceTree.revision = '0'.repeat(40); }), /attestation/],
    ['source fingerprint mismatch', async fixture => mutateBaseline(fixture, baseline => { baseline.capture.sourceTree.sha256 = '0'.repeat(64); }), /source-tree fingerprint/],
    ['inventory hash bypass', async fixture => mutateBaseline(fixture, baseline => { baseline.files[0].sha256 = '0'.repeat(64); }), /inventory fingerprint/],
    ['totals bypass', async fixture => mutateBaseline(fixture, baseline => { baseline.totals.bytes += 1; }), /totals do not match/],
  ];

  for (const [name, mutate, expected] of cases) await t.test(name, async () => {
    const fixture = await preparedFixture();
    await mutate(fixture);
    assert.throws(() => run(fixture, '--budget', fixture.budget), expected);
  });
});

test('approved bundle budget enforces raw and gzip growth independently', async (t) => {
  await t.test('raw growth', async () => {
    const fixture = await preparedFixture({ maxGrowthBytes: 0, maxGrowthGzipBytes: 1_000_000 });
    await writeFile(join(fixture.dist, 'app.wasm'), Buffer.alloc(1024, 1));
    assert.throws(() => run(fixture, '--budget', fixture.budget), /growth 768 bytes exceeds explicit max 0/);
  });
  await t.test('gzip growth', async () => {
    const fixture = await preparedFixture({ maxGrowthBytes: 1_000_000, maxGrowthGzipBytes: 0 });
    await writeFile(join(fixture.dist, 'app.wasm'), Buffer.alloc(1024, 1));
    assert.throws(() => run(fixture, '--budget', fixture.budget), /gzip growth .* exceeds explicit max 0/);
  });
});

test('pull request CI requires an explicit policy base for an approved budget', async () => {
  const fixture = await preparedFixture();
  fixture.environment = { GITHUB_EVENT_NAME: 'pull_request' };
  assert.throws(
    () => run(fixture, '--budget', fixture.budget),
    /requires --policy-base for pull_request CI/,
  );
});

test('baseline capture cannot approve the current feature branch', async () => {
  const fixture = await createFeatureBranchFixture();
  assert.throws(
    () => run(fixture, '--write-baseline', fixture.baseline, '--trusted-ref', 'origin/main'),
    /HEAD must equal/,
  );
});

async function preparedFixture(limits = { maxGrowthBytes: 0, maxGrowthGzipBytes: 0 }) {
  const fixture = await createFixture();
  await writeBudget(fixture, await reportFor(fixture), limits);
  return fixture;
}

async function createFixture() {
  const directory = await mkdtemp(join(tmpdir(), 'quata-wasm-bundle-budget-'));
  const dist = join(directory, 'dist');
  await mkdir(dist);
  await writeFile(join(dist, 'app.wasm'), Buffer.alloc(256, 1));
  return { dist, report: join(directory, 'report.json'), baseline: join(directory, 'baseline.json'), budget: join(directory, 'budget.json') };
}

async function createFeatureBranchFixture() {
  const repositoryRoot = await mkdtemp(join(tmpdir(), 'quata-wasm-bundle-feature-'));
  const fixtureScript = join(repositoryRoot, 'scripts', 'wasm-bundle-report.mjs');
  await mkdir(join(repositoryRoot, 'scripts'));
  await copyFile(script, fixtureScript);
  await copyFile(approvalPolicyScript, join(repositoryRoot, 'scripts', 'wasm-bundle-approval-policy.mjs'));
  await writeFile(join(repositoryRoot, 'base.txt'), 'trusted base\n');
  git(repositoryRoot, 'init', '--quiet');
  git(repositoryRoot, 'config', 'user.name', 'Quata contract fixture');
  git(repositoryRoot, 'config', 'user.email', 'quata-contract@example.invalid');
  git(repositoryRoot, 'add', 'scripts/wasm-bundle-report.mjs', 'scripts/wasm-bundle-approval-policy.mjs', 'base.txt');
  git(repositoryRoot, 'commit', '--quiet', '-m', 'trusted base');
  git(repositoryRoot, 'update-ref', 'refs/remotes/origin/main', 'HEAD');
  await writeFile(join(repositoryRoot, 'feature.txt'), 'feature branch\n');
  git(repositoryRoot, 'add', 'feature.txt');
  git(repositoryRoot, 'commit', '--quiet', '-m', 'feature branch');

  const fixture = await createFixture();
  return { ...fixture, repositoryRoot, script: fixtureScript };
}

async function reportFor(fixture) {
  run(fixture);
  return JSON.parse(await readFile(fixture.report, 'utf8'));
}

async function writeBudget(fixture, report, limits) {
  const capture = {
    schemaVersion: 2,
    trustedRef: 'refs/tags/test-fixture',
    sourceRevision: fixtureRevision,
    sourceTree: { algorithm: 'git-ls-tree-sha256-v1', revision: fixtureRevision, sha256: fixtureTreeSha256 },
    inventorySha256: report.inventorySha256,
    command: ':web:wasmJsBrowserDistribution',
  };
  await writeFile(fixture.baseline, `${JSON.stringify({ ...report, baselineState: 'approved', revision: fixtureRevision, capture }, null, 2)}\n`);
  await writeFile(fixture.budget, `${JSON.stringify({ schemaVersion: 1, state: 'approved', baselineFile: fixture.baseline, ...limits }, null, 2)}\n`);
}

async function mutateBaseline(fixture, mutate) {
  const baseline = JSON.parse(await readFile(fixture.baseline, 'utf8'));
  mutate(baseline);
  await writeFile(fixture.baseline, `${JSON.stringify(baseline, null, 2)}\n`);
}

function run(fixture, ...args) {
  const environment = { ...process.env };
  delete environment.GITHUB_EVENT_NAME;
  const result = spawnSync(process.execPath, [fixture.script ?? script, '--dist', fixture.dist, '--report', fixture.report, ...args], {
    cwd: fixture.repositoryRoot ?? root,
    encoding: 'utf8',
    env: { ...environment, ...fixture.environment },
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr);
  return result.stdout;
}

function git(repositoryRoot, ...args) {
  execFileSync('git', args, { cwd: repositoryRoot, stdio: 'ignore' });
}
