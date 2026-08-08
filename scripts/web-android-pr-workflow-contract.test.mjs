import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const workflow = resolve(import.meta.dirname, '..', '.github', 'workflows', 'web-android-pr.yml');
const daemonCriteria = resolve(import.meta.dirname, '..', 'gradle', 'gradle-daemon-jvm.properties');
const finalGateScript = resolve(import.meta.dirname, '..', 'scripts', 'check-final-certification.sh');

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

  const finalGuard = /if: \$\{\{ needs\.classify-impact\.outputs\.(?:web|android) == 'true' && \(github\.event_name != 'pull_request' \|\| contains\(github\.event\.pull_request\.labels\.\*\.name, 'candidate-final'\)\) \}\}/;
  for (const job of ['web-wasm', 'web-unit-tests', 'android-unit-tests', 'android-debug']) {
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
  assert.match(gateBlock, /name: Web\/Android final certification gate\n    needs: \[classify-impact, web-wasm, web-unit-tests, android-unit-tests, android-debug\]\n    if: \$\{\{ always\(\) \}\}/);
  assert.match(gateBlock, /steps:\n      - name: Fail closed unless this exact run is final-certified/,
    'the independent gate job must run without an external checkout action');
  assert.doesNotMatch(gateBlock, /uses: actions\/checkout@v6/,
    'the final gate must not depend on action downloads after all evidence jobs have completed');
  assert.match(gateBlock, /FINAL_CANDIDATE: \$\{\{ contains\(github\.event\.pull_request\.labels\.\*\.name, 'candidate-final'\) \}\}/);
  assert.match(gateBlock, /DOCS_ONLY: \$\{\{ needs\.classify-impact\.outputs\.docs_only \}\}/);
  for (const result of ['WEB_FINAL_RESULT', 'WEB_UNIT_RESULT', 'ANDROID_UNIT_RESULT', 'ANDROID_FINAL_RESULT']) {
    assert.match(gateBlock, new RegExp(`${result}: \\$\\{\\{ needs\\.`));
  }
  assert.match(gateBlock, /set -euo pipefail/);
  assert.match(gateBlock, /\[\[ "\$EVENT_NAME" == "pull_request" && "\$DOCS_ONLY" == "true" \]\]/);
  assert.match(gateBlock, /A pull request must carry candidate-final before final certification can pass\./);
  assert.match(gateBlock, /verify_lane "web-wasm" "\$WEB_AFFECTED" "\$WEB_FINAL_RESULT"[\s\S]*?verify_lane "web-unit" "\$WEB_AFFECTED" "\$WEB_UNIT_RESULT"[\s\S]*?verify_lane "android-unit" "\$ANDROID_AFFECTED" "\$ANDROID_UNIT_RESULT"[\s\S]*?verify_lane "android-debug" "\$ANDROID_AFFECTED" "\$ANDROID_FINAL_RESULT"/);
}

function executeFinalGate(script, { event, candidateFinal, docsOnly = false, results }) {
  const quote = value => `'${String(value).replaceAll("'", "'\\\\''")}'`;
  const harness = `set -euo pipefail\nEVENT_NAME=${quote(event)}\nFINAL_CANDIDATE=${quote(candidateFinal)}\nDOCS_ONLY=${quote(docsOnly)}\nset -- ${results.map(quote).join(' ')}`;
  const command = script.replace('set -euo pipefail', harness);
  assert.notEqual(command, script, 'the final-gate shell must retain its strict-mode anchor for executable testing');
  const bash = process.platform === 'win32' ? 'C:\\Program Files\\Git\\bin\\bash.exe' : 'bash';
  return spawnSync(bash, ['-c', command], {
    encoding: 'utf8',
    env: process.env,
  });
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

function assertOfficialEditorEvidenceCoverage(yaml) {
  const evidenceStep = yaml.indexOf('      - name: Capture Official editor Web evidence');
  const repeatabilityStep = yaml.indexOf('      - name: Collect five cold Chrome measurements and advisory baseline proposal');
  assert.ok(evidenceStep >= 0 && repeatabilityStep > evidenceStep, 'the Web/Wasm final lane must capture Official editor evidence before repeatability');
  const block = yaml.slice(evidenceStep, repeatabilityStep);
  assert.match(block, /timeout-minutes: 5/);
  assert.match(block, /GITHUB_PR_NUMBER: \$\{\{ github\.event\.pull_request\.number \}\}/);
  assert.match(block, /GITHUB_BASE_SHA: \$\{\{ github\.event\.pull_request\.base\.sha \}\}/);
  assert.match(block, /GITHUB_HEAD_SHA: \$\{\{ github\.event\.pull_request\.head\.sha \}\}/);
  assert.match(block, /GITHUB_MERGE_SHA: \$\{\{ github\.sha \}\}/);
  assert.match(block, /npm run evidence:web-official-editor -- --require-pr-identity/);
  assert.match(yaml, /build-reports\/web\/official-editor-evidence\.json/);
  assert.match(yaml, /build-reports\/web\/official-editor-evidence\//);
}

function assertWorkflowContract(yaml) {
  assert.match(yaml, /^on:\n  workflow_dispatch:\n  pull_request:/m);
  assert.match(yaml, /^permissions:\n  contents: read$/m);
  assert.match(
    yaml,
    /uses: actions\/checkout@v6\n        with:\n          fetch-depth: 0/,
    'the checkout must contain the pull request base commit',
  );
  assertJetBrainsDaemonBootstrap(yaml, 5);
  assertWebWasmTimeoutBudget(yaml);
  assertBrowserTestCoverage(yaml);
  assertOfficialEditorEvidenceCoverage(yaml);
  assertFastAndFinalLaneContract(yaml);
  assert.match(
    yaml,
    /node scripts\/wasm-bundle-report\.mjs[\s\S]*?--policy-base "\$\{\{ github\.event\.pull_request\.base\.sha \|\| github\.event\.before \|\| github\.sha \}\}"/,
  );
  const pullRequestStart = yaml.indexOf('  pull_request:');
  const pushStart = yaml.indexOf('  push:');
  assert.doesNotMatch(yaml.slice(pullRequestStart, pushStart), /\bpaths:/,
    'all PR changes must reach the fast and aggregate gates');
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
  ]) assert.doesNotMatch(yaml, new RegExp(`^      - "${path.replaceAll('.', '\\.')}"$`, 'm'), `the workflow must not restore a path allow-list for ${path}`);
  assert.match(yaml, /node scripts\/web-performance-repeatability\.mjs[\s\S]*?--docmentis[\s\S]*?--metrics-dir build\/reports\/web-performance-repeatability[\s\S]*?--out build\/reports\/web-performance-repeatability\.json/, 'repeatability evidence must be collected in CI');
  assert.match(yaml, /Collect five cold Chrome measurements and advisory baseline proposal/, 'CI must collect the five-sample advisory baseline proposal');
}

test('Web/Wasm workflow supplies its fetched trusted base SHA and deterministic daemon runtime', async () => {
  const [yaml, criteria] = await Promise.all([readFile(workflow, 'utf8'), readFile(daemonCriteria, 'utf8')]);
  assertWorkflowContract(yaml);
  assert.match(criteria, /^toolchainVendor=JETBRAINS$/m);
  assert.match(criteria, /^toolchainVersion=21$/m);
});

test('Web/Android final gate executes the shared fail-closed shell for every event/result combination', async () => {
  const script = await readFile(finalGateScript, 'utf8');
  for (const [name, input, expected] of [
    ['unlabelled PR skips all final jobs', { event: 'pull_request', candidateFinal: false, results: ['web:false:skipped', 'android:false:skipped'] }, false],
    ['docs-only PR skips all final jobs', { event: 'pull_request', candidateFinal: false, docsOnly: true, results: ['web:false:skipped', 'android:false:skipped'] }, true],
    ['affected Web lane is cancelled', { event: 'pull_request', candidateFinal: true, results: ['web:true:cancelled', 'android:false:skipped'] }, false],
    ['unaffected Android lane unexpectedly runs', { event: 'pull_request', candidateFinal: true, results: ['web:true:success', 'android:false:success'] }, false],
    ['Web-only final jobs pass while Android skips', { event: 'pull_request', candidateFinal: true, results: ['web:true:success', 'android:false:skipped'] }, true],
    ['Android-only final jobs pass while Web skips', { event: 'push', candidateFinal: false, results: ['web:false:skipped', 'android:true:success'] }, true],
    ['manual final jobs all pass', { event: 'workflow_dispatch', candidateFinal: false, results: ['web:true:success', 'android:true:success'] }, true],
  ]) assert.equal(executeFinalGate(script, input).status === 0, expected, name);

  const exitGuards = [...script.matchAll(/exit 1/g)];
  assert.ok(exitGuards.length >= 3, 'the shared gate needs independent candidate, classifier, and result failure exits');
});

test('workflow contract fails closed if base history, PR-only trigger, read permission, or quoted argument is weakened', async (t) => {
  const yaml = await readFile(workflow, 'utf8');
  const mutations = [
    ['shallow checkout', yaml.replaceAll('fetch-depth: 0', 'fetch-depth: 1')],
    ['manual trigger removed', yaml.replace('  workflow_dispatch:\n', '')],
    ['main push trigger removed', yaml.replace('  push:\n    branches:\n      - main\n      - master\n', '')],
    ['write permission', yaml.replace('contents: read', 'contents: write')],
    ['missing policy base', yaml.replace(/\n\s+--policy-base "[^"]+" \\/, '')],
    ['JetBrains daemon runtime made default', yaml.replace('set-default: false', 'set-default: true')],
    ['JetBrains daemon runtime removed', yaml.replace(/      - name: Set up JetBrains Runtime 21 for Gradle daemon[\s\S]*?\n\n(?=      - name: Set up JDK 17)/, '')],
    ['candidate final label trigger removed', yaml.replace(', labeled, unlabeled', '')],
    ['full Web lane no longer gated', yaml.replace("contains(github.event.pull_request.labels.*.name, 'candidate-final')", "contains(github.event.pull_request.labels.*.name, 'candidate-review')")],
    ['PR concurrency group weakened', yaml.replace("format('pr-{0}', github.event.pull_request.number)", 'github.ref')],
    ['PR concurrency cancellation weakened', yaml.replace("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", 'cancel-in-progress: true')],
    ['final gate needs removed', yaml.replace('needs: [classify-impact, web-wasm, web-unit-tests, android-unit-tests, android-debug]', 'needs: []')],
    ['final gate always removed', yaml.replace('if: ${{ always() }}', 'if: ${{ success() }}')],
    ['final gate external checkout added', yaml.replace('      - name: Fail closed unless this exact run is final-certified', '      - name: Check out final gate helper\n        uses: actions/checkout@v6\n\n      - name: Fail closed unless this exact run is final-certified')],
    ['final gate shell bypassed', yaml.replace('verify_lane "web-wasm" "$WEB_AFFECTED" "$WEB_FINAL_RESULT"', 'echo bypass')],
    ['candidate-final binding replaced', yaml.replace("FINAL_CANDIDATE: ${{ contains(github.event.pull_request.labels.*.name, 'candidate-final') }}", 'FINAL_CANDIDATE: true')],
    ['docs-only binding replaced', yaml.replace('DOCS_ONLY: ${{ needs.classify-impact.outputs.docs_only }}', 'DOCS_ONLY: true')],
    ['Web result binding replaced', yaml.replace('WEB_FINAL_RESULT: ${{ needs.web-wasm.result }}', 'WEB_FINAL_RESULT: success')],
    ['Web unit result binding replaced', yaml.replace('WEB_UNIT_RESULT: ${{ needs.web-unit-tests.result }}', 'WEB_UNIT_RESULT: success')],
    ['Android result binding replaced', yaml.replace('ANDROID_FINAL_RESULT: ${{ needs.android-debug.result }}', 'ANDROID_FINAL_RESULT: success')],
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
    ['Official editor evidence step removed', yaml.replace(/      - name: Capture Official editor Web evidence[\s\S]*?(?=\n      - name: Collect five cold Chrome measurements and advisory baseline proposal)/, '')],
    ['Official editor PR identity removed', yaml.replace(' -- --require-pr-identity', '')],
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

  const effectiveMutations = mutations.filter(([, mutation]) => mutation !== yaml);
  assert.ok(effectiveMutations.length > 0, 'at least one adversarial mutation must execute');
  for (const [name, mutation] of effectiveMutations) await t.test(name, () => {
    assert.throws(() => assertWorkflowContract(mutation));
  });
});
