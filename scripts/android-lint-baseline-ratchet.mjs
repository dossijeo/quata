#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

function usage() {
  throw new Error('Usage: node scripts/android-lint-baseline-ratchet.mjs <policy.json>');
}

const requiredBaselinePaths = new Set([
  '../app/lint-baseline.xml',
  '../document-reader/lint-baseline.xml',
]);

function attribute(attributes, name) {
  const match = attributes.match(new RegExp(`\\b${name}="([^"]*)"`));
  return match?.[1]?.replaceAll('&quot;', '"').replaceAll('&amp;', '&') ?? '';
}

export function issueOccurrences(xml) {
  const occurrences = new Set();
  for (const match of xml.matchAll(/<issue\b([^>]*)>([\s\S]*?)<\/issue>/g)) {
    const [, attributes, body] = match;
    const id = attribute(attributes, 'id');
    const message = attribute(attributes, 'message');
    const locations = [...body.matchAll(/<location\b([^>]*)\/?\s*>/g)];
    if (locations.length === 0) {
      occurrences.add(JSON.stringify([id, message, '', '']));
      continue;
    }
    for (const [, locationAttributes] of locations) {
      occurrences.add(JSON.stringify([
        id,
        message,
        attribute(locationAttributes, 'file'),
        attribute(locationAttributes, 'line'),
      ]));
    }
  }
  return occurrences;
}

export function occurrenceFingerprint(occurrence) {
  return createHash('sha256').update(occurrence).digest('hex');
}

export async function verifyPolicy(policyPath) {
  const policy = JSON.parse(await readFile(policyPath, 'utf8'));
  if (policy.schemaVersion !== 1 || !Array.isArray(policy.baselines)) {
    throw new Error(`Invalid Android lint ratchet policy: ${policyPath}`);
  }
  const declaredPaths = policy.baselines.map(baseline => baseline.path);
  if (declaredPaths.length !== requiredBaselinePaths.size || new Set(declaredPaths).size !== declaredPaths.length) {
    throw new Error('Android lint ratchet must declare each required baseline exactly once');
  }
  for (const requiredPath of requiredBaselinePaths) {
    if (!declaredPaths.includes(requiredPath)) {
      throw new Error(`Android lint ratchet is missing required baseline ${requiredPath}`);
    }
  }

  for (const baseline of policy.baselines) {
    const baselinePath = resolve(resolve(policyPath, '..'), baseline.path);
    const actual = issueOccurrences(await readFile(baselinePath, 'utf8'));
    if (!Array.isArray(baseline.allowedOccurrenceFingerprints) || baseline.allowedOccurrenceFingerprints.length === 0) {
      throw new Error(`Missing non-empty occurrence snapshot for ${baseline.path}`);
    }
    const allowed = new Set(baseline.allowedOccurrenceFingerprints);
    if (allowed.size !== baseline.allowedOccurrenceFingerprints.length || [...allowed].some(fingerprint => !/^[a-f0-9]{64}$/.test(fingerprint))) {
      throw new Error(`Invalid occurrence snapshot for ${baseline.path}`);
    }
    for (const occurrence of actual) {
      if (!allowed.has(occurrenceFingerprint(occurrence))) {
        throw new Error(`${baseline.path}: new or moved lint occurrence ${occurrence}`);
      }
    }
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  if (process.argv.length !== 3) usage();
  await verifyPolicy(resolve(process.argv[2]));
  console.log('Android lint baseline ratchet passed.');
}
