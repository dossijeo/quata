import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const CLI = new URL('./web-docmentis-staging-lifecycle-cli.mjs', import.meta.url);
const SCHEMA_VERSION = 'quata.docmentis-staging-lifecycle-evidence/v1';

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

function completeArtifact() {
  return {
    schemaVersion: SCHEMA_VERSION,
    transcripts: [
      transcript('staging-pdf', 'PDF'),
      transcript('staging-docx', 'DOCX'),
      transcript('staging-pptx', 'PPTX'),
      transcript('staging-xlsx', 'XLSX'),
    ],
  };
}

async function runCli(directory, evidencePath) {
  const reportPath = join(directory, 'report.json');
  const result = spawnSync(
    process.execPath,
    [fileURLToPath(CLI), '--evidence', evidencePath, '--report', reportPath],
    { encoding: 'utf8' },
  );
  return { result, report: JSON.parse(await readFile(reportPath, 'utf8')) };
}

test('CLI consumes a complete external v1 artifact and writes a sanitized non-visual report', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'quata-docmentis-gate-'));
  try {
    const evidencePath = join(directory, 'external.json');
    await writeFile(evidencePath, JSON.stringify(completeArtifact()), 'utf8');
    const { result, report } = await runCli(directory, evidencePath);
    assert.equal(result.status, 0);
    assert.equal(report.status, 'passed');
    assert.equal(report.visualEvidence, 'not_evaluated');
    assert.equal(report.evaluatedFixtures.length, 4);
    assert.equal(Object.hasOwn(report, 'rendered'), false);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('CLI fails closed and still writes a fixed report when evidence is absent or incomplete', async () => {
  for (const mode of ['missing', 'incomplete']) {
    const directory = await mkdtemp(join(tmpdir(), 'quata-docmentis-gate-'));
    try {
      const evidencePath = join(directory, 'external.json');
      if (mode === 'incomplete') {
        const artifact = completeArtifact();
        artifact.transcripts.pop();
        await writeFile(evidencePath, JSON.stringify(artifact), 'utf8');
      }
      const { result, report } = await runCli(directory, evidencePath);
      assert.equal(result.status, 1);
      assert.equal(report.status, 'failed');
      assert.equal(report.visualEvidence, 'not_evaluated');
      assert.match(report.code, /external_evidence_unavailable|incomplete_artifact/);
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  }
});

test('versioned schema and manual workflow require external artifact, CLI enforcement and report upload', async () => {
  const [schemaText, workflow, prWorkflow, packageText] = await Promise.all([
    readFile(new URL('../docs/schemas/docmentis-staging-lifecycle-evidence-v1.schema.json', import.meta.url), 'utf8'),
    readFile(new URL('../.github/workflows/web-docmentis-staging-evidence.yml', import.meta.url), 'utf8'),
    readFile(new URL('../.github/workflows/web-android-pr.yml', import.meta.url), 'utf8'),
    readFile(new URL('../package.json', import.meta.url), 'utf8'),
  ]);
  const schema = JSON.parse(schemaText);
  const packageJson = JSON.parse(packageText);

  assert.equal(schema.properties.schemaVersion.const, SCHEMA_VERSION);
  assert.equal(schema.properties.transcripts.minItems, 4);
  assert.match(workflow, /^on:\r?\n  workflow_dispatch:/m);
  assert.doesNotMatch(workflow, /^\s+pull_request:/m);
  assert.match(workflow, /actions\/download-artifact@v4/);
  assert.match(workflow, /web-docmentis-staging-lifecycle-cli\.mjs/);
  assert.match(workflow, /--evidence staging-input\/docmentis-staging-lifecycle\.v1\.json/);
  assert.match(workflow, /actions\/upload-artifact@v4/);
  assert.match(workflow, /steps\.download\.outcome/);
  assert.match(workflow, /steps\.gate\.outcome/);
  assert.doesNotMatch(prWorkflow, /\bpaths:/,
    'the PR workflow must reach its gate regardless of which file changed');
  assert.match(prWorkflow, /npm run test:web-wave2-contracts/);
  assert.match(packageJson.scripts['test:web-wave2-contracts'], /web-docmentis-staging-lifecycle-cli\.test\.mjs/);
});
