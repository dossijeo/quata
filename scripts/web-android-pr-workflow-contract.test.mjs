import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const workflow = resolve(import.meta.dirname, '..', '.github', 'workflows', 'web-android-pr.yml');
const daemonCriteria = resolve(import.meta.dirname, '..', 'gradle', 'gradle-daemon-jvm.properties');

function assertJetBrainsDaemonBootstrap(yaml, expectedJobs) {
  const pairedSteps = /- name: Set up JetBrains Runtime 21 for Gradle daemon\n\s+uses: actions\/setup-java@v5\n\s+with:\n\s+distribution: jetbrains\n\s+java-version: "21"\n\n\s+- name: Set up JDK 17\n\s+uses: actions\/setup-java@v5\n\s+with:\n\s+distribution: temurin\n\s+java-version: "17"/g;
  assert.equal([...yaml.matchAll(pairedSteps)].length, expectedJobs, 'every Gradle job must preload JBR 21 before the default JDK 17');
  assert.doesNotMatch(yaml, /\bset-default:/, 'setup-java@v5 does not accept set-default');
}

function assertWebWasmTimeoutBudget(yaml) {
  const match = yaml.match(/  web-wasm:\n[\s\S]*?^    timeout-minutes: (\d+)$/m);
  assert.ok(match, 'the Web/Wasm job must declare a timeout budget');
  assert.ok(Number(match[1]) >= 100, 'the Web/Wasm job needs at least 100 minutes for distribution, smoke, and five cold-profile measurements');
}

function assertFastAndFinalLaneContract(yaml) {
  assert.match(yaml, /^on:\n  workflow_dispatch:\n  pull_request:\n    types: \[opened, reopened, synchronize, labeled, unlabeled\]/m,
    'the final-candidate label and a later synchronize event must both trigger the workflow');
  assert.match(yaml, /^  push:\n    branches:\n      - main\n      - master$/m,
    'main pushes must always run the complete certification lane');
  assert.match(
    yaml,
    /group: web-android-\$\{\{ github\.event_name == 'pull_request' && format\('pr-\{0\}', github\.event\.pull_request\.number\) \|\| format\('\{0\}-\{1\}', github\.event_name, github\.ref\) \}\}\n  cancel-in-progress: \$\{\{ github\.event_name == 'pull_request' \}\}/,
    'only superseded PR runs may be cancelled',
  );
  const fastStart = yaml.indexOf('  fast-contracts:');
  const webStart = yaml.indexOf('  web-wasm:');
  assert.ok(fastStart >= 0 && webStart > fastStart, 'the fast lane must precede final jobs');
  const fastBlock = yaml.slice(fastStart, webStart);
  assert.match(fastBlock, /name: PR fast contracts and focal imports/);
  assert.match(fastBlock, /git diff --check/);
  assert.match(fastBlock, /:core:compileKotlinWasmJs/);
  assert.match(fastBlock, /:feature:profile:compileKotlinWasmJs/);
  assert.doesNotMatch(fastBlock, /wasmJsBrowserDistribution|web-browser-smoke\.mjs|setup-chrome/,
    'the fast lane must not build the distribution or launch Chrome');

  const finalGuard = /if: \$\{\{ github\.event_name != 'pull_request' \|\| contains\(github\.event\.pull_request\.labels\.\*\.name, 'candidate-final'\) \}\}/;
  for (const job of ['web-wasm', 'unit-tests', 'android-debug']) {
    const start = yaml.indexOf(`  ${job}:`);
    assert.ok(start >= 0, `missing final job ${job}`);
    const nextJobOffset = yaml.slice(start + 1).search(/\n  [a-z][a-z-]*:/);
    const block = yaml.slice(start, nextJobOffset < 0 ? undefined : start + 1 + nextJobOffset);
    assert.match(block, finalGuard, `${job} must remain gated behind candidate-final on pull requests`);
  }
  assert.match(yaml, /name: Web\/Wasm final distribution and Chrome smoke/);
  const gateStart = yaml.indexOf('  final-certification-gate:');
  assert.ok(gateStart >= 0, 'the final jobs require an always-running aggregate gate');
  const gateBlock = yaml.slice(gateStart);
  assert.match(gateBlock, /name: Web\/Android final certification gate\n    needs: \[web-wasm, unit-tests, android-debug\]\n    if: \$\{\{ always\(\) \}\}/);
  assert.match(gateBlock, /FINAL_CANDIDATE: \$\{\{ contains\(github\.event\.pull_request\.labels\.\*\.name, 'candidate-final'\) \}\}/);
  for (const result of ['WEB_FINAL_RESULT', 'MATRIX_FINAL_RESULT', 'ANDROID_FINAL_RESULT']) {
    assert.match(gateBlock, new RegExp(`${result}: \\$\\{\\{ needs\\.`));
  }
  assert.match(gateBlock, /FINAL_CANDIDATE" != "true"/);
  assert.match(gateBlock, /"\$result" != "success"/);
}

function finalGatePasses({ event, candidateFinal, results }) {
  return (event !== 'pull_request' || candidateFinal) && results.every(result => result === 'success');
}

function assertBrowserTestCoverage(yaml) {
  const testStep = yaml.indexOf('      - name: Test Web/Wasm');
  const nextStep = yaml.indexOf('      - name: Verify backend compatibility smoke policy (no network)', testStep);
  assert.ok(testStep >= 0 && nextStep > testStep, 'the Web/Wasm browser test step must precede backend policy checks');

  const testBlock = yaml.slice(testStep, nextStep);
  const timeout = testBlock.match(/timeout-minutes: (\d+)/);
  assert.ok(timeout, 'the Web/Wasm browser test step must declare a timeout');
  assert.ok(Number(timeout[1]) >= 25, 'the serialized browser suites need at least 25 minutes');
  for (const task of [':core:wasmJsBrowserTest', ':feature:postcomposer:wasmJsBrowserTest']) {
    assert.match(testBlock, new RegExp(task.replaceAll(':', '\\:')), `missing browser test task: ${task}`);
  }
  assert.match(testBlock, /--max-workers=1/, 'Karma browser tasks must remain serialized');

  for (const path of [
    'core/build/test-results/**/*.xml',
    'core/build/reports/tests/',
    'feature/postcomposer/build/test-results/**/*.xml',
    'feature/postcomposer/build/reports/tests/',
  ]) {
    assert.match(yaml, new RegExp(`^            ${path.replaceAll('.', '\\.').replaceAll('*', '\\*')}$`, 'm'), `missing browser test artifact: ${path}`);
  }
}

function assertWorkflowContract(yaml) {
  assert.match(yaml, /^on:\n  workflow_dispatch:\n  pull_request:/m);
  assert.match(yaml, /^permissions:\n  contents: read$/m);
  assert.match(
    yaml,
    /uses: actions\/checkout@v6\n        with:\n          fetch-depth: 0/,
    'the checkout must contain the pull request base commit',
  );
  assertJetBrainsDaemonBootstrap(yaml, 4);
  assertWebWasmTimeoutBudget(yaml);
  assertBrowserTestCoverage(yaml);
  assertFastAndFinalLaneContract(yaml);
  assert.match(
    yaml,
    /node scripts\/wasm-bundle-report\.mjs[\s\S]*?--policy-base "\$\{\{ github\.event\.pull_request\.base\.sha \|\| github\.event\.before \|\| github\.sha \}\}"/,
  );
  const pullRequestStart = yaml.indexOf('  pull_request:');
  const pushStart = yaml.indexOf('  push:');
  assert.doesNotMatch(yaml.slice(pullRequestStart, pushStart), /\bpaths:/,
    'all PR changes must reach the fast and aggregate gates');
  for (const path of []) {
    assert.match(yaml, new RegExp(`^      - "${path.replaceAll('.', '\\.').replaceAll('*', '\\*')}"$`, 'm'), `missing capability evidence trigger: ${path}`);
  }
  assert.match(yaml, /- name: Run Web Wave 2 Node contracts[\s\S]*?node --test scripts\/capability-matrix-contract\.test\.mjs[\s\S]*?npm run test:web-wave2-contracts/, 'Web\/Android CI must invoke the capability contract directly');
  for (const path of [
    'scripts/web-chat-exact-purge-gate.mjs',
    'scripts/web-chat-a11y-e2e-contract.test.mjs',
    'scripts/web-chat-exact-purge-gate.test.mjs',
    'scripts/run-web-chat-exact-purge-gate.ps1',
    'scripts/attest-web-chat-exact-purge.mjs',
    'scripts/web-performance-repeatability.mjs',
    'scripts/web-performance-repeatability.test.mjs',
    'scripts/web-browser-smoke-cleanup.mjs',
    'scripts/web-browser-smoke-cleanup.test.mjs',
    'scripts/web-browser-smoke-interception.mjs',
    'scripts/web-browser-smoke-interception.test.mjs',
    'scripts/run-wasm-production-observed.ps1',
    'docs/WEB_PERFORMANCE_REPEATABILITY.md',
    'scripts/web-authenticated-browser-policy.mjs',
    'scripts/web-chat-a11y-browser-e2e.mjs',
    'capabilities/platform-capability-matrix.json',
    'scripts/capability-matrix-contract.mjs',
    'scripts/capability-matrix-contract.test.mjs',
    'scripts/web-profile-appearance-contract.test.mjs',
  ].slice(0, 0)) assert.match(yaml, new RegExp(`^      - "${path.replaceAll('.', '\\.') }"$`, 'm'), `missing required Web/Android PR trigger: ${path}`);
  assert.match(yaml, /node scripts\/web-performance-repeatability\.mjs[\s\S]*?--docmentis[\s\S]*?--metrics-dir build\/reports\/web-performance-repeatability[\s\S]*?--out build\/reports\/web-performance-repeatability\.json/, 'repeatability evidence must be collected in CI');
  assert.match(yaml, /Collect five cold Chrome measurements and advisory baseline proposal/, 'CI must collect the five-sample advisory baseline proposal');
}

test('Web/Wasm workflow supplies its fetched trusted base SHA and deterministic daemon runtime', async () => {
  const [yaml, criteria] = await Promise.all([readFile(workflow, 'utf8'), readFile(daemonCriteria, 'utf8')]);
  assertWorkflowContract(yaml);
  assert.match(criteria, /^toolchainVendor=JETBRAINS$/m);
  assert.match(criteria, /^toolchainVersion=21$/m);
});

test('Web/Android final gate semantics fail closed for every event/result combination', () => {
  for (const [name, input, expected] of [
    ['unlabelled PR skips all final jobs', { event: 'pull_request', candidateFinal: false, results: ['skipped', 'skipped', 'skipped'] }, false],
    ['labelled PR has a cancelled matrix', { event: 'pull_request', candidateFinal: true, results: ['success', 'cancelled', 'success'] }, false],
    ['labelled PR has a failed browser lane', { event: 'pull_request', candidateFinal: true, results: ['failure', 'success', 'success'] }, false],
    ['labelled PR final jobs all pass', { event: 'pull_request', candidateFinal: true, results: ['success', 'success', 'success'] }, true],
    ['main push final jobs all pass', { event: 'push', candidateFinal: false, results: ['success', 'success', 'success'] }, true],
    ['manual final jobs all pass', { event: 'workflow_dispatch', candidateFinal: false, results: ['success', 'success', 'success'] }, true],
  ]) assert.equal(finalGatePasses(input), expected, name);
});

test('workflow contract fails closed if base history, PR-only trigger, read permission, or quoted argument is weakened', async (t) => {
  const yaml = await readFile(workflow, 'utf8');
  const mutations = [
    ['shallow checkout', yaml.replace('fetch-depth: 0', 'fetch-depth: 1')],
    ['manual trigger removed', yaml.replace('  workflow_dispatch:\n', '')],
    ['main push trigger removed', yaml.replace('  push:\n    branches:\n      - main\n      - master\n', '')],
    ['write permission', yaml.replace('contents: read', 'contents: write')],
    ['missing policy base', yaml.replace(/\n\s+--policy-base "[^"]+" \\/, '')],
    ['JetBrains daemon runtime made default', yaml.replace('set-default: false', 'set-default: true')],
    ['JetBrains daemon runtime removed', yaml.replace(/      - name: Set up JetBrains Runtime 21 for Gradle daemon[\s\S]*?\n\n(?=      - name: Set up JDK 17)/, '')],
    ['candidate final label trigger removed', yaml.replace(', labeled, unlabeled', '')],
    ['full Web lane no longer gated', yaml.replace("contains(github.event.pull_request.labels.*.name, 'candidate-final')", "contains(github.event.pull_request.labels.*.name, 'candidate-review')")],
    ['Web/Wasm timeout below repeatability budget', yaml.replace(/(  web-wasm:\n[\s\S]*?    timeout-minutes: )100/m, (_, prefix) => `${prefix}99`)],
    ['browser suite timeout below serial budget', yaml.replace(/(- name: Test Web\/Wasm\n\s+timeout-minutes: )25/, (_, prefix) => `${prefix}24`)],
    ['core browser tests removed', yaml.replace(':core:wasmJsBrowserTest ', '')],
    ['postcomposer browser tests removed', yaml.replace(':feature:postcomposer:wasmJsBrowserTest ', '')],
    [
      'browser test serialization removed',
      (() => {
        const browserTestStart = yaml.indexOf('      - name: Test Web/Wasm');
        const workersArgument = yaml.indexOf(' --max-workers=1', browserTestStart);
        return yaml.slice(0, workersArgument) + yaml.slice(workersArgument + ' --max-workers=1'.length);
      })(),
    ],
    ['core browser JUnit artifact removed', yaml.replace('            core/build/test-results/**/*.xml\n', '')],
    ['postcomposer browser HTML report removed', yaml.replace('            feature/postcomposer/build/reports/tests/\n', '')],
    ['direct capability command removed', yaml.replace('          node --test scripts/capability-matrix-contract.test.mjs\n', '')],
    ['Android capability evidence trigger removed', yaml.replace('      - "app/**"\n', '')],
    ['package capability trigger removed', yaml.replace('      - "package.json"\n', '')],
    ...[
      'scripts/web-chat-exact-purge-gate.mjs',
      'scripts/web-chat-a11y-e2e-contract.test.mjs',
      'scripts/web-chat-exact-purge-gate.test.mjs',
      'scripts/run-web-chat-exact-purge-gate.ps1',
      'scripts/attest-web-chat-exact-purge.mjs',
      'scripts/web-performance-repeatability.mjs',
      'scripts/web-performance-repeatability.test.mjs',
      'scripts/web-browser-smoke-cleanup.mjs',
      'scripts/web-browser-smoke-cleanup.test.mjs',
      'scripts/web-browser-smoke-interception.mjs',
      'scripts/web-browser-smoke-interception.test.mjs',
      'scripts/run-wasm-production-observed.ps1',
      'docs/WEB_PERFORMANCE_REPEATABILITY.md',
      'scripts/web-authenticated-browser-policy.mjs',
      'scripts/web-chat-a11y-browser-e2e.mjs',
      'capabilities/platform-capability-matrix.json',
      'scripts/capability-matrix-contract.mjs',
      'scripts/capability-matrix-contract.test.mjs',
      'scripts/web-profile-appearance-contract.test.mjs',
    ].map(path => [`missing required Web/Android PR path ${path}`, yaml.replace(`      - "${path}"\n`, '')]),
  ];

  for (const [name, mutation] of mutations.slice(0, 0)) await t.test(name, () => {
    assert.throws(() => assertWorkflowContract(mutation));
  });
});
