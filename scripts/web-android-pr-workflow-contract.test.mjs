import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const workflow = resolve(import.meta.dirname, '..', '.github', 'workflows', 'web-android-pr.yml');

function assertWorkflowContract(yaml) {
  assert.match(yaml, /^on:\n  pull_request:/m);
  assert.doesNotMatch(yaml, /^  (?:push|workflow_dispatch|schedule):/m);
  assert.match(yaml, /^permissions:\n  contents: read$/m);
  assert.match(
    yaml,
    /uses: actions\/checkout@v6\n        with:\n          fetch-depth: 0/,
    'the checkout must contain the pull request base commit',
  );
  assert.match(
    yaml,
    /node scripts\/wasm-bundle-report\.mjs[\s\S]*?--policy-base "\$\{\{ github\.event\.pull_request\.base\.sha \}\}"/,
  );
}

test('Web/Wasm pull-request workflow supplies its fetched trusted base SHA to the approved bundle gate', async () => {
  assertWorkflowContract(await readFile(workflow, 'utf8'));
});

test('workflow contract fails closed if base history, PR-only trigger, read permission, or quoted argument is weakened', async (t) => {
  const yaml = await readFile(workflow, 'utf8');
  const mutations = [
    ['shallow checkout', yaml.replace('fetch-depth: 0', 'fetch-depth: 1')],
    ['push trigger', yaml.replace('  pull_request:', '  push:')],
    ['write permission', yaml.replace('contents: read', 'contents: write')],
    ['missing policy base', yaml.replace(/\n\s+--policy-base "[^"]+" \\/, '')],
  ];

  for (const [name, mutation] of mutations) await t.test(name, () => {
    assert.throws(() => assertWorkflowContract(mutation));
  });
});
