import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const workflow = resolve(import.meta.dirname, '..', '.github', 'workflows', 'web-android-pr.yml');
const daemonCriteria = resolve(import.meta.dirname, '..', 'gradle', 'gradle-daemon-jvm.properties');

function assertJetBrainsDaemonBootstrap(yaml, expectedJobs) {
  const pairedSteps = /- name: Set up JetBrains Runtime 21 for Gradle daemon\n\s+uses: actions\/setup-java@v5\n\s+with:\n\s+distribution: jetbrains\n\s+java-version: "21"\n\s+set-default: false\n\n\s+- name: Set up JDK 17\n\s+uses: actions\/setup-java@v5\n\s+with:\n\s+distribution: temurin\n\s+java-version: "17"/g;
  assert.equal([...yaml.matchAll(pairedSteps)].length, expectedJobs, 'every Gradle job must preload JBR 21 before the default JDK 17');
}

function assertWebWasmTimeoutBudget(yaml) {
  const match = yaml.match(/  web-wasm:\n[\s\S]*?^    timeout-minutes: (\d+)$/m);
  assert.ok(match, 'the Web/Wasm job must declare a timeout budget');
  assert.ok(Number(match[1]) >= 100, 'the Web/Wasm job needs at least 100 minutes for distribution, smoke, and five cold-profile measurements');
}

function assertWorkflowContract(yaml) {
  assert.match(yaml, /^on:\n  pull_request:/m);
  assert.doesNotMatch(yaml, /^  (?:push|workflow_dispatch|schedule):/m);
  assert.match(yaml, /^permissions:\n  contents: read$/m);
  assert.match(
    yaml,
    /uses: actions\/checkout@v6\n        with:\n          fetch-depth: 0/,
    'the checkout must contain the pull request base commit',
  );
  assertJetBrainsDaemonBootstrap(yaml, 3);
  assertWebWasmTimeoutBudget(yaml);
  assert.match(
    yaml,
    /node scripts\/wasm-bundle-report\.mjs[\s\S]*?--policy-base "\$\{\{ github\.event\.pull_request\.base\.sha \}\}"/,
  );
  for (const path of ['app/**', 'package.json', 'package-lock.json']) {
    assert.match(yaml, new RegExp(`^      - "${path.replaceAll('.', '\\.').replaceAll('*', '\\*')}"$`, 'm'), `missing capability evidence trigger: ${path}`);
  }
  assert.match(yaml, /- name: Run Web Wave 2 Node contracts[\s\S]*?node --test scripts\/capability-matrix-contract\.test\.mjs[\s\S]*?npm run test:web-wave2-contracts/, 'Web\/Android CI must invoke the capability contract directly');
  for (const path of [
    'scripts/web-chat-exact-purge-gate.mjs',
    'scripts/web-chat-exact-purge-gate.test.mjs',
    'scripts/run-web-chat-exact-purge-gate.ps1',
    'scripts/attest-web-chat-exact-purge.mjs',
    'scripts/web-performance-repeatability.mjs',
    'scripts/web-performance-repeatability.test.mjs',
    'scripts/web-browser-smoke-cleanup.mjs',
    'scripts/web-browser-smoke-cleanup.test.mjs',
    'scripts/run-wasm-production-observed.ps1',
    'docs/WEB_PERFORMANCE_REPEATABILITY.md',
    'capabilities/platform-capability-matrix.json',
    'scripts/capability-matrix-contract.mjs',
    'scripts/capability-matrix-contract.test.mjs',
  ]) assert.match(yaml, new RegExp(`^      - "${path.replaceAll('.', '\\.') }"$`, 'm'), `missing required Web/Android PR trigger: ${path}`);
  assert.match(yaml, /node scripts\/web-performance-repeatability\.mjs[\s\S]*?--docmentis[\s\S]*?--metrics-dir build\/reports\/web-performance-repeatability[\s\S]*?--out build\/reports\/web-performance-repeatability\.json/, 'repeatability evidence must be collected in CI');
  assert.match(yaml, /Collect five cold Chrome measurements and advisory baseline proposal/, 'CI must collect the five-sample advisory baseline proposal');
}

test('Web/Wasm pull-request workflow supplies its fetched trusted base SHA and deterministic daemon runtime', async () => {
  const [yaml, criteria] = await Promise.all([readFile(workflow, 'utf8'), readFile(daemonCriteria, 'utf8')]);
  assertWorkflowContract(yaml);
  assert.match(criteria, /^toolchainVendor=JETBRAINS$/m);
  assert.match(criteria, /^toolchainVersion=21$/m);
});

test('workflow contract fails closed if base history, PR-only trigger, read permission, or quoted argument is weakened', async (t) => {
  const yaml = await readFile(workflow, 'utf8');
  const mutations = [
    ['shallow checkout', yaml.replace('fetch-depth: 0', 'fetch-depth: 1')],
    ['push trigger', yaml.replace('  pull_request:', '  push:')],
    ['write permission', yaml.replace('contents: read', 'contents: write')],
    ['missing policy base', yaml.replace(/\n\s+--policy-base "[^"]+" \\/, '')],
    ['JetBrains daemon runtime made default', yaml.replace('set-default: false', 'set-default: true')],
    ['JetBrains daemon runtime removed', yaml.replace(/      - name: Set up JetBrains Runtime 21 for Gradle daemon[\s\S]*?\n\n(?=      - name: Set up JDK 17)/, '')],
    ['Web/Wasm timeout below repeatability budget', yaml.replace(/(  web-wasm:\n[\s\S]*?    timeout-minutes: )100/m, (_, prefix) => `${prefix}99`)],
    ['direct capability command removed', yaml.replace('          node --test scripts/capability-matrix-contract.test.mjs\n', '')],
    ['Android capability evidence trigger removed', yaml.replace('      - "app/**"\n', '')],
    ['package capability trigger removed', yaml.replace('      - "package.json"\n', '')],
    ...[
      'scripts/web-chat-exact-purge-gate.mjs',
      'scripts/web-chat-exact-purge-gate.test.mjs',
      'scripts/run-web-chat-exact-purge-gate.ps1',
      'scripts/attest-web-chat-exact-purge.mjs',
      'scripts/web-performance-repeatability.mjs',
      'scripts/web-performance-repeatability.test.mjs',
      'scripts/web-browser-smoke-cleanup.mjs',
      'scripts/web-browser-smoke-cleanup.test.mjs',
      'scripts/run-wasm-production-observed.ps1',
      'docs/WEB_PERFORMANCE_REPEATABILITY.md',
      'capabilities/platform-capability-matrix.json',
      'scripts/capability-matrix-contract.mjs',
      'scripts/capability-matrix-contract.test.mjs',
    ].map(path => [`missing required Web/Android PR path ${path}`, yaml.replace(`      - "${path}"\n`, '')]),
  ];

  for (const [name, mutation] of mutations) await t.test(name, () => {
    assert.throws(() => assertWorkflowContract(mutation));
  });
});
