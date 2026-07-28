#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import {
  DOCMENTIS_STAGING_GATE_REPORT_SCHEMA_VERSION,
  validateDocmentisStagingLifecycleArtifact,
} from './web-docmentis-staging-lifecycle-gate.mjs';

const FIXED_FAILURE = Object.freeze({
  schemaVersion: DOCMENTIS_STAGING_GATE_REPORT_SCHEMA_VERSION,
  status: 'failed',
  evidenceKind: 'docmentis_lifecycle',
  code: 'external_evidence_unavailable',
  visualEvidence: 'not_evaluated',
});

function parseArgs(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index];
    const value = argv[index + 1];
    if (!['--evidence', '--report'].includes(key) || !value) return null;
    args[key] = value;
  }
  return Object.keys(args).length === 2 ? args : null;
}

async function writeReport(path, report) {
  const absolutePath = resolve(path);
  await mkdir(dirname(absolutePath), { recursive: true });
  await writeFile(absolutePath, `${JSON.stringify(report, null, 2)}\n`, {
    encoding: 'utf8',
    flag: 'w',
  });
}

const args = parseArgs(process.argv.slice(2));
if (!args) {
  process.stderr.write('usage: web-docmentis-staging-lifecycle-cli --evidence <path> --report <path>\n');
  process.exitCode = 2;
} else {
  let report = FIXED_FAILURE;
  try {
    const serialized = await readFile(resolve(args['--evidence']), 'utf8');
    report = validateDocmentisStagingLifecycleArtifact(JSON.parse(serialized));
  } catch {
    // Missing, unreadable and malformed external evidence all fail closed.
  }

  try {
    await writeReport(args['--report'], report);
  } catch {
    process.stderr.write('unable to write sanitized DocMentis gate report\n');
    process.exitCode = 2;
  }

  if (report.status !== 'passed' && process.exitCode === undefined) {
    process.stderr.write(`DocMentis staging evidence gate failed: ${report.code}\n`);
    process.exitCode = 1;
  }
}
