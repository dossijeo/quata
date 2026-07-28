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
  for (const path of [
    'scripts/web-chat-exact-purge-gate.mjs',
    'scripts/web-chat-exact-purge-gate.test.mjs',
    'scripts/run-web-chat-exact-purge-gate.ps1',
    'scripts/attest-web-chat-exact-purge.mjs',
    'scripts/web-performance-repeatability.mjs',
    'scripts/web-performance-repeatability.test.mjs',
    'scripts/web-browser-smoke-cleanup.mjs',
    'scripts/web-browser-smoke-cleanup.test.mjs',
    'docs/WEB_PERFORMANCE_REPEATABILITY.md',
  ]) assert.match(yaml, new RegExp(`^      - "${path.replaceAll('.', '\\.') }"$`, 'm'), `missing Chat purge gate trigger: ${path}`);
  assert.match(yaml, /node scripts\/web-performance-repeatability\.mjs[\s\S]*?--docmentis[\s\S]*?--metrics-dir build\/reports\/web-performance-repeatability[\s\S]*?--out build\/reports\/web-performance-repeatability\.json/, 'repeatability evidence must be collected in CI');
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
    ...[
      'scripts/web-chat-exact-purge-gate.mjs',
      'scripts/web-chat-exact-purge-gate.test.mjs',
      'scripts/run-web-chat-exact-purge-gate.ps1',
      'scripts/attest-web-chat-exact-purge.mjs',
      'scripts/web-performance-repeatability.mjs',
      'scripts/web-performance-repeatability.test.mjs',
      'scripts/web-browser-smoke-cleanup.mjs',
      'scripts/web-browser-smoke-cleanup.test.mjs',
      'docs/WEB_PERFORMANCE_REPEATABILITY.md',
    ].map(path => [`missing Chat purge path ${path}`, yaml.replace(`      - "${path}"\n`, '')]),
  ];

  for (const [name, mutation] of mutations) await t.test(name, () => {
    assert.throws(() => assertWorkflowContract(mutation));
  });
});
