import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const workflow = resolve(import.meta.dirname, '..', '.github', 'workflows', 'ios-build.yml');
const independentWorkflow = resolve(
  import.meta.dirname,
  '..',
  '.github',
  'workflows',
  'web-android-pr.yml',
);
const daemonCriteria = resolve(import.meta.dirname, '..', 'gradle', 'gradle-daemon-jvm.properties');
const iosAppSource = resolve(import.meta.dirname, '..', 'iosApp', 'iosApp', 'QuataIosApp.swift');
const iosProfileHostSource = resolve(import.meta.dirname, '..', 'feature', 'profile', 'src', 'iosMain', 'kotlin', 'com', 'quata', 'feature', 'profile', 'presentation', 'IosProfileHost.kt');
const iosProfileBootstrapSource = resolve(import.meta.dirname, '..', 'feature', 'profile', 'src', 'iosMain', 'kotlin', 'com', 'quata', 'feature', 'profile', 'presentation', 'IosProfileSosRuntimeBootstrap.kt');
const ciLanePolicy = resolve(import.meta.dirname, '..', 'docs', 'CI_LANE_POLICY.md');
const finalGateScript = resolve(import.meta.dirname, '..', 'scripts', 'check-final-certification.sh');

function assertIosJavaContract(yaml) {
  assert.match(
    yaml,
    /- name: Set up JDK 17\n\s+uses: actions\/setup-java@v5\n\s+with:\n\s+distribution: temurin\n\s+java-version: "17"/,
    'iOS CI must preserve Temurin 17 as its default launcher',
  );
  assert.doesNotMatch(yaml, /- name: Set up JetBrains Runtime 21 for Gradle daemon/);
}

function assertIosConcurrencyContract(yaml) {
  const concurrencyBlock = yaml.slice(
    yaml.indexOf('\nconcurrency:'),
    yaml.indexOf('\npermissions:', yaml.indexOf('\nconcurrency:')),
  );
  assert.match(
    concurrencyBlock,
    /group: ios-compile-\$\{\{ github\.event_name == 'pull_request' && format\('pr-\{0\}', github\.event\.pull_request\.number\) \|\| format\('\{0\}-\{1\}', github\.event_name, github\.ref\) \}\}/,
    'PR runs must share a group by PR number while push and dispatch runs keep stable event/ref groups',
  );
  assert.match(
    concurrencyBlock, /cancel-in-progress: \$\{\{ github\.event_name == 'pull_request' \}\}/,
    'only superseded pull-request runs may be cancelled',
  );
}

function assertIosFastFinalLaneContract(yaml) {
  assert.match(yaml, /pull_request:\n    types: \[opened, reopened, synchronize, labeled, unlabeled\]/,
    'candidate-final must be re-evaluated both when labelled and when synchronized');
  const fastStart = yaml.indexOf('  ios-fast-contracts:');
  const finalStart = yaml.indexOf('  compile-ios:');
  assert.ok(fastStart >= 0 && finalStart > fastStart, 'the iOS fast lane must remain separate from the final lane');
  const fastBlock = yaml.slice(fastStart, finalStart);
  assert.match(fastBlock, /name: iOS fast contracts/);
  assert.match(fastBlock, /needs: \[classify-impact\]/);
  assert.match(fastBlock, /if: \$\{\{ github\.event_name != 'pull_request' \|\| needs\.classify-impact\.outputs\.docs_only != 'true' \}\}/);
  assert.match(fastBlock, /git diff --check/);
  assert.match(fastBlock, /node --test scripts\/ios-build-workflow-contract\.test\.mjs/);
  assert.match(fastBlock, /node --test scripts\/candidate-attestation-contract\.test\.mjs/);
  assert.match(fastBlock, /node --test scripts\/e2e-fixtures-chat-attachments-contract\.test\.mjs/);
  assert.match(fastBlock, /node --test scripts\/codeql-workflow-contract\.test\.mjs/);
  assert.match(fastBlock, /node --test scripts\/whats-new-release-history-contract\.test\.mjs/);
  assert.doesNotMatch(fastBlock, /xcodebuild|assembleQuataSharedDebugXCFramework|simctl/,
    'the PR fast lane must not start the expensive Apple build matrix');
  const finalBlock = yaml.slice(finalStart);
  assert.match(finalBlock, /name: Kotlin iOS final host, simulator and archive/);
  assert.match(finalBlock, /if: \$\{\{ needs\.classify-impact\.outputs\.ios == 'true' && \(github\.event_name != 'pull_request' \|\| contains\(github\.event\.pull_request\.labels\.\*\.name, 'candidate-final'\)\) \}\}/,
    'the complete iOS lane must run only for main/dispatch or labelled final candidates');
  const gateStart = yaml.indexOf('  ios-final-certification-gate:');
  assert.ok(gateStart > finalStart, 'the iOS final gate must aggregate the final job');
  const gateBlock = yaml.slice(gateStart);
  assert.match(gateBlock, /name: iOS final certification gate\n    needs: \[classify-impact, compile-ios\]\n    if: \$\{\{ always\(\) \}\}/);
  assert.match(gateBlock, /steps:\n      - name: Fail closed unless this exact run is final-certified/,
    'the independent gate job must run without an external checkout action');
  assert.doesNotMatch(gateBlock, /uses: actions\/checkout@v6/,
    'the final gate must not depend on action downloads after all evidence jobs have completed');
  assert.match(gateBlock, /FINAL_CANDIDATE: \$\{\{ contains\(github\.event\.pull_request\.labels\.\*\.name, 'candidate-final'\) \}\}/);
  assert.match(gateBlock, /DOCS_ONLY: \$\{\{ needs\.classify-impact\.outputs\.docs_only \}\}/);
  assert.match(gateBlock, /IOS_FINAL_RESULT: \$\{\{ needs\.compile-ios\.result \}\}/);
  assert.match(gateBlock, /set -euo pipefail/);
  assert.match(gateBlock, /\[\[ "\$EVENT_NAME" == "pull_request" && "\$DOCS_ONLY" == "true" \]\]/);
  assert.match(gateBlock, /A pull request must carry candidate-final before final certification can pass\./);
  assert.match(gateBlock, /if \[\[ "\$result" != "\$expected_result" \]\]; then/);
  assert.match(gateBlock, /Final certification is incomplete: 'ios' expected '\$expected_result' but was '\$result'\./);
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

function assertIosWorkflowSelfCoverage(yaml) {
  const pullRequestStart = yaml.indexOf('  pull_request:');
  const pushStart = yaml.indexOf('  push:');
  const concurrencyStart = yaml.indexOf('\nconcurrency:');
  assert.ok(pullRequestStart >= 0 && pushStart > pullRequestStart && concurrencyStart > pushStart);

  const pullRequestTrigger = yaml.slice(pullRequestStart, pushStart);
  const pushTrigger = yaml.slice(pushStart, concurrencyStart);
  assert.doesNotMatch(pullRequestTrigger, /\bpaths:/,
    'every PR, including a candidate-final label outside old allow-lists, must reach the gate');
  assert.doesNotMatch(pushTrigger, /\bpaths:/,
    'every protected-branch push must run final iOS certification');
  assert.match(pushTrigger, /branches:\n\s+- main\n\s+- master/);

  const checkout = yaml.indexOf('      - name: Check out source');
  const contract = yaml.indexOf('      - name: Validate iOS workflow contract');
  const watchdogContract = yaml.indexOf('      - name: Validate iOS watchdog cleanup contract');
  const composeResourcesContract = yaml.indexOf('      - name: Validate iOS Compose resources contract');
  const authLaunchContract = yaml.indexOf('      - name: Validate iOS Auth launch fixture contract');
  const runtimeContract = yaml.indexOf('      - name: Validate iOS public runtime contract');
  const capabilityContract = yaml.indexOf('      - name: Validate platform capability matrix');
  const releaseHistoryContract = yaml.indexOf('      - name: Validate Release History parity contract');
  const matrixContract = yaml.indexOf('      - name: Validate iOS public simulator matrix contract');
  const backupContract = yaml.indexOf('      - name: Validate iOS public runtime backup contract');
  const compilation = yaml.indexOf('      - name: Compile all Kotlin iOS targets');
  assert.ok(
    checkout >= 0 &&
      contract > checkout &&
      watchdogContract > contract &&
      composeResourcesContract > watchdogContract &&
      authLaunchContract > composeResourcesContract &&
      runtimeContract > authLaunchContract &&
      matrixContract > runtimeContract &&
      backupContract > matrixContract &&
      capabilityContract > backupContract &&
      releaseHistoryContract > capabilityContract &&
      compilation > releaseHistoryContract,
  );
  assert.match(
    yaml,
    /- name: Validate iOS workflow contract\n\s+run: node --test scripts\/ios-build-workflow-contract\.test\.mjs/,
  );
  assert.match(
    yaml,
    /- name: Validate iOS watchdog cleanup contract\n\s+run: python3 -m unittest scripts\/test_run_ios_command_watchdog\.py scripts\/test_check_ios_simulator_booted\.py/,
  );
  assert.match(
    yaml,
    /- name: Validate iOS Compose resources contract\n\s+run: node --test scripts\/ios-compose-resources-contract\.test\.mjs/,
  );
  assert.match(
    yaml,
    /- name: Validate iOS Auth launch fixture contract\n\s+run: node --test scripts\/ios-auth-launch-fixture-contract\.test\.mjs/,
  );
  assert.match(
    yaml,
    /- name: Validate iOS public runtime contract\n\s+run: node --test scripts\/ios-public-runtime-contract\.test\.mjs/,
  );
  assert.match(yaml, /- name: Validate platform capability matrix\n\s+run: node --test scripts\/capability-matrix-contract\.test\.mjs/);
  assert.match(
    yaml,
    /- name: Validate Release History parity contract\n\s+run: node --test scripts\/whats-new-release-history-contract\.test\.mjs/,
  );
  assert.match(
    yaml,
    /- name: Validate iOS public simulator matrix contract\n\s+run: node --test scripts\/ios-public-simulator-matrix-contract\.test\.mjs/,
  );
  assert.match(
    yaml,
    /- name: Validate iOS public runtime backup contract\n\s+shell: bash\n\s+run: bash scripts\/test-ios-public-runtime-config-backup\.sh/,
  );

  const compilationBlock = yaml.slice(compilation, yaml.indexOf('      - name: Link QuataShared simulator framework', compilation));
  assert.ok(
    compilationBlock.includes(':core:iosSimulatorArm64Test \\'),
    'the iOS lane must execute core iosTest contracts on its simulator runtime',
  );
}

function assertIosRuntimeFixtureAndUiIsolation(yaml) {
  const fixtureProbe = yaml.indexOf('      - name: Verify Xcode resolves public runtime fixture');
  const bootSimulator = yaml.indexOf('      - name: Boot test simulator');
  const inboxFilesystemTest = yaml.indexOf('      - name: Run iOS external share inbox filesystem contract');
  const officialPublicReadTest = yaml.indexOf('      - name: Run iOS Official public read contract');
  const profileRuntimeTest = yaml.indexOf('      - name: Run iOS Profile runtime contract');
  const feedPlaybackTest = yaml.indexOf('      - name: Run iOS Feed playback public-runtime UI test');
  const testStep = yaml.indexOf('      - name: Test Swift/Kotlin iOS host boundary');
  assert.ok(fixtureProbe >= 0 && bootSimulator > fixtureProbe && inboxFilesystemTest > bootSimulator && officialPublicReadTest > inboxFilesystemTest && profileRuntimeTest > officialPublicReadTest && feedPlaybackTest > profileRuntimeTest && testStep > feedPlaybackTest,
    'the valid xcconfig fixture probe must remain before the isolated UI test');

  const fixtureBlock = yaml.slice(fixtureProbe, testStep);
  assert.match(fixtureBlock, /QUATA_SUPABASE_URL = https:\/\/ios-ci\\\.invalid/,
    'the fixture probe must continue to prove Xcode resolves the valid CI URL');
  assert.doesNotMatch(fixtureBlock, /QUATA_SUPABASE_URL=\s*\\/,
    'the fixture probe must not be overridden into the unconfigured state');

  const inboxTestBlock = yaml.slice(inboxFilesystemTest, testStep);
  assert.match(
    inboxTestBlock,
    /:feature:externalshare:iosSimulatorArm64Test/,
    'the external share inbox filesystem assertions must execute on the booted iOS simulator',
  );
  assert.match(
    inboxTestBlock,
    /xcrun simctl bootstatus "\$simulator_udid" -b/,
    'the external share inbox test must reuse the explicitly booted simulator',
  );

  const officialTestBlock = yaml.slice(officialPublicReadTest, testStep);
  assert.match(
    officialTestBlock,
    /:feature:official:iosSimulatorArm64Test/,
    'the Official anonymous read policy/factory assertions must execute on the booted iOS simulator',
  );
  assert.match(
    officialTestBlock,
    /xcrun simctl bootstatus "\$simulator_udid" -b/,
    'the Official test must reuse the explicitly booted simulator',
  );

  const profileTestBlock = yaml.slice(profileRuntimeTest, feedPlaybackTest);
  assert.match(
    profileTestBlock,
    /:feature:profile:iosSimulatorArm64Test/,
    'the Profile iosTest suite must execute on the Apple Silicon simulator target',
  );
  assert.match(profileTestBlock, /xcrun simctl bootstatus "\$simulator_udid" -b/);
  assert.doesNotMatch(profileTestBlock, /compileTestKotlin|continue-on-error|timeout-minutes/,
    'the focal Profile lane must execute without skipping or weakening the test gate');

  const feedPlaybackBlock = yaml.slice(feedPlaybackTest, testStep);
  assert.match(
    feedPlaybackBlock,
    /run_watchdog 420 build\/reports\/ios\/xcodebuild-feed-playback-tests\.log xcodebuild[\s\S]*?-only-testing:QuataIosUITests\/QuataIosFeedPlaybackUITests[\s\S]*?-parallel-testing-enabled NO[\s\S]*?test/,
    'the Feed playback UI test must run alone against the valid public runtime fixture',
  );
  assert.match(
    feedPlaybackBlock,
    /grep -F "QuataIosFeedPlaybackUITests testFeedMuteIconTogglesTheSharedAudioState" build\/reports\/ios\/xcodebuild-feed-playback-tests\.log[\s\S]*?grep -F "passed"[\s\S]*?Feed playback focal XCTest did not report a passed semantic execution/,
    'the Feed playback focal step must fail closed unless the expected XCTest reports a passed execution',
  );
  assert.match(
    feedPlaybackBlock,
    /grep -Ei "skipped\|disabled"[\s\S]*?Feed playback focal XCTest was skipped or disabled/,
    'the Feed playback focal step must reject skipped or disabled executions',
  );
  assert.doesNotMatch(feedPlaybackBlock, /QUATA_SUPABASE_URL=|QUATA_SUPABASE_PUBLISHABLE_KEY=/,
    'the Feed playback UI test must not blank the public runtime fixture');

  const uiTestBlock = yaml.slice(testStep, yaml.indexOf('      - name: Capture simulator diagnostics', testStep));
  const invocation = effectiveContinuedCommand(uiTestBlock, 'run_watchdog 1200');
  assert.match(
    invocation,
    /run_watchdog 1200 build\/reports\/ios\/xcodebuild-tests\.log xcodebuild .* QUATA_SUPABASE_URL= QUATA_SUPABASE_PUBLISHABLE_KEY= -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 test$/,
    'the effective xcodebuild test invocation must end with isolated runtime settings and serialized tests',
  );
}

function assertBootWatchdogRevalidation(yaml) {
  const bootStart = yaml.indexOf('      - name: Boot test simulator');
  const nextStep = yaml.indexOf('      - name: Run iOS external share inbox filesystem contract', bootStart);
  assert.ok(bootStart >= 0 && nextStep > bootStart, 'Boot simulator step must remain isolated');
  const bootBlock = yaml.slice(bootStart, nextStep);

  assert.match(
    bootBlock,
    /verify_selected_simulator_booted\(\)[\s\S]*?xcrun simctl list devices -j \| tee build\/reports\/ios\/simulator-devices-after-boot\.json[\s\S]*?python3 scripts\/check-ios-simulator-booted\.py \\\n+\s+--udid "\$simulator_udid"/,
    'timeout revalidation must preserve the authoritative JSON diagnostic',
  );
  assert.match(
    bootBlock,
    /scripts\/check-ios-simulator-booted\.py/,
    'revalidation must require the exact selected UDID, not any booted simulator',
  );
  assert.match(
    bootBlock,
    /if \[\[ "\$boot_status" -eq 124 \]\]; then[\s\S]*?if verify_selected_simulator_booted; then[\s\S]*?else\n\s+exit 124/,
    'an initial boot timeout must fail closed unless exact revalidation proves Booted',
  );
  assert.match(
    bootBlock,
    /if \[\[ "\$bootstatus_status" -eq 124 \]\] && verify_selected_simulator_booted; then[\s\S]*?else\n\s+exit "\$bootstatus_status"/,
    'a bootstatus timeout must fail closed unless exact revalidation proves Booted',
  );
}

function effectiveContinuedCommand(block, commandStart) {
  const lines = block.split(/\r?\n/);
  const start = lines.findIndex((line) => line.trimStart().startsWith(commandStart));
  assert.ok(start >= 0, 'missing command starting with ' + commandStart);

  const effective = [];
  for (let index = start; index < lines.length; index += 1) {
    const line = lines[index].trim();
    if (index > start && line.startsWith('#')) break;
    const continued = line.endsWith('\\');
    effective.push(continued ? line.slice(0, -1).trimEnd() : line);
    if (!continued) break;
  }
  return effective.join(' ').replace(/\s+/g, ' ').trim();
}

function assertIndependentWebCoverage(yaml) {
  const pullRequestStart = yaml.indexOf('  pull_request:');
  const concurrencyStart = yaml.indexOf('\nconcurrency:');
  assert.ok(pullRequestStart >= 0 && concurrencyStart > pullRequestStart);

  const pullRequestTrigger = yaml.slice(pullRequestStart, concurrencyStart);
  assert.doesNotMatch(pullRequestTrigger, /\bpaths:/,
    'the independent fast/final workflow must run for every PR diff');
  assert.match(
    yaml,
    /- name: Run Web Wave 2 Node contracts[\s\S]*?npm run test:web-wave2-contracts/,
  );
}

test('iOS build workflow preserves JDK 17 while Gradle resolves its daemon from the existing criteria', async () => {
  const [yaml, criteria] = await Promise.all([readFile(workflow, 'utf8'), readFile(daemonCriteria, 'utf8')]);
  assertIosJavaContract(yaml);
  assert.match(criteria, /^toolchainVendor=JETBRAINS$/m);
  assert.match(criteria, /^toolchainVersion=21$/m);
});

test('iOS workflow cancels only superseded pull-request runs', async (t) => {
  const yaml = await readFile(workflow, 'utf8');
  assertIosConcurrencyContract(yaml);

  for (const [name, mutation] of [
    ['PR group falls back to ref instead of PR number', yaml.replace("format('pr-{0}', github.event.pull_request.number)", 'github.ref')],
    ['manual dispatch can cancel', yaml.replace("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", 'cancel-in-progress: true')],
    ['all runs are kept serial', yaml.replace("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", 'cancel-in-progress: false')],
  ]) await t.test(name, () => {
    assert.throws(() => assertIosConcurrencyContract(mutation));
  });
});

test('iOS workflow separates fast PR coverage from final certification and documents the merge gate', async (t) => {
  const [yaml, policy] = await Promise.all([readFile(workflow, 'utf8'), readFile(ciLanePolicy, 'utf8')]);
  assertIosFastFinalLaneContract(yaml);
  assert.match(policy, /candidate-final/);
  assert.match(policy, /every affected final job[\s\S]*?successfully[\s\S]*?every unaffected job[\s\S]*?skipped[\s\S]*?never green evidence/i);
  assert.match(policy, /required status checks[\s\S]*?Web\/Android final certification gate[\s\S]*?iOS final certification gate[\s\S]*?Analyze java-kotlin[\s\S]*?Analyze\s+javascript-typescript/i);
  for (const [name, mutation] of [
    ['label event removed', yaml.replace(', labeled, unlabeled', '')],
    ['final label guard removed', yaml.replace(", 'candidate-final'", ", 'candidate-review'" )],
    ['fast lane invokes Xcode', yaml.replace('          node --test scripts/ios-build-workflow-contract.test.mjs', '          xcodebuild -version\n          node --test scripts/ios-build-workflow-contract.test.mjs')],
  ]) await t.test(name, () => assert.throws(() => assertIosFastFinalLaneContract(mutation)));
});

test('iOS final gate executes the shared fail-closed shell for every event/result combination', async () => {
  const script = await readFile(finalGateScript, 'utf8');
  for (const [name, input, expected] of [
    ['unlabelled PR with skipped final job', { event: 'pull_request', candidateFinal: false, results: ['ios:false:skipped'] }, false],
    ['docs-only PR with skipped final job', { event: 'pull_request', candidateFinal: false, docsOnly: true, results: ['ios:false:skipped'] }, true],
    ['labelled affected PR with skipped final job', { event: 'pull_request', candidateFinal: true, results: ['ios:true:skipped'] }, false],
    ['labelled affected PR with cancelled final job', { event: 'pull_request', candidateFinal: true, results: ['ios:true:cancelled'] }, false],
    ['unaffected iOS lane unexpectedly runs', { event: 'pull_request', candidateFinal: true, results: ['ios:false:success'] }, false],
    ['labelled affected PR with successful final job', { event: 'pull_request', candidateFinal: true, results: ['ios:true:success'] }, true],
    ['main push with unaffected iOS skipped', { event: 'push', candidateFinal: false, results: ['ios:false:skipped'] }, true],
    ['manual run with successful final job', { event: 'workflow_dispatch', candidateFinal: false, results: ['ios:true:success'] }, true],
  ]) assert.equal(executeFinalGate(script, input).status === 0, expected, name);

  const exitGuards = [...script.matchAll(/exit 1/g)];
  assert.ok(exitGuards.length >= 3, 'the shared gate needs independent candidate, classifier, and result failure exits');
});

test('iOS Java and daemon criteria contract fails closed when launcher or criteria are weakened', async (t) => {
  const [yaml, criteria] = await Promise.all([readFile(workflow, 'utf8'), readFile(daemonCriteria, 'utf8')]);
  for (const [name, workflowMutation, criteriaMutation] of [
    ['JDK 17 vendor changes', yaml.replace('distribution: temurin', 'distribution: jetbrains'), criteria],
    ['JBR bootstrap added', yaml.replace('      - name: Set up JDK 17', '      - name: Set up JetBrains Runtime 21 for Gradle daemon\n\n      - name: Set up JDK 17'), criteria],
    ['daemon vendor removed', yaml, criteria.replace('toolchainVendor=JETBRAINS\n', '')],
    ['daemon version removed', yaml, criteria.replace('toolchainVersion=21\n', '')],
  ]) await t.test(name, () => {
    assert.throws(() => {
      assertIosJavaContract(workflowMutation);
      assert.match(criteriaMutation, /^toolchainVendor=JETBRAINS$/m);
      assert.match(criteriaMutation, /^toolchainVersion=21$/m);
    });
  });
});

test('iOS workflow runs and triggers its own fail-closed contract before compilation', async () => {
  const yaml = await readFile(workflow, 'utf8');
  assertIosWorkflowSelfCoverage(yaml);
  assertIosFastFinalLaneContract(yaml);
  assertIosRuntimeFixtureAndUiIsolation(yaml);
  assertBootWatchdogRevalidation(yaml);
});

test('iOS workflow self-coverage fails closed when a trigger or command is removed', async (t) => {
  const yaml = await readFile(workflow, 'utf8');
  for (const [name, mutation] of [
    ['pull-request trigger removed', yaml.replace('  pull_request:\n', '')],
    ['main push trigger removed', yaml.replace('  push:\n    branches:\n      - main\n      - master\n', '')],
    ['candidate-final trigger removed', yaml.replace(', labeled, unlabeled', '')],
    ['final gate needs removed', yaml.replace('needs: [classify-impact, compile-ios]', 'needs: []')],
    ['final gate always removed', yaml.replace('if: ${{ always() }}', 'if: ${{ success() }}')],
    ['final gate external checkout added', yaml.replace('      - name: Fail closed unless this exact run is final-certified', '      - name: Check out final gate helper\n        uses: actions/checkout@v6\n\n      - name: Fail closed unless this exact run is final-certified')],
    ['final gate helper bypassed', yaml.replace('if [[ "$result" != "$expected_result" ]]; then', 'if false; then')],
    ['final result binding replaced', yaml.replace('IOS_FINAL_RESULT: ${{ needs.compile-ios.result }}', 'IOS_FINAL_RESULT: success')],
    ['candidate binding replaced', yaml.replace("FINAL_CANDIDATE: ${{ contains(github.event.pull_request.labels.*.name, 'candidate-final') }}", 'FINAL_CANDIDATE: true')],
    ['docs-only binding replaced', yaml.replace('DOCS_ONLY: ${{ needs.classify-impact.outputs.docs_only }}', 'DOCS_ONLY: true')],
    ['concurrency group weakened', yaml.replace("format('pr-{0}', github.event.pull_request.number)", 'github.ref')],
    ['concurrency cancellation weakened', yaml.replace("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", 'cancel-in-progress: true')],
    [
      'watchdog cleanup contract command weakened',
      yaml.replace(
        'run: python3 -m unittest scripts/test_run_ios_command_watchdog.py scripts/test_check_ios_simulator_booted.py',
        'run: python3 --version',
      ),
    ],
    ['Android-only trigger added', yaml.replace('      - "core/**"', '      - "app/**"\n      - "core/**"')],
    ['package-only trigger added', yaml.replace('      - "core/**"', '      - "package.json"\n      - "core/**"')],
    [
      'contract command weakened',
      yaml.replace(
        'run: node --test scripts/ios-build-workflow-contract.test.mjs',
        'run: node --version',
      ),
    ],
    [
      'Compose resources contract command weakened',
      yaml.replace(
        'run: node --test scripts/ios-compose-resources-contract.test.mjs',
        'run: node --version',
      ),
    ],
    [
      'public runtime contract command weakened',
      yaml.replace(
        'run: node --test scripts/ios-public-runtime-contract.test.mjs',
        'run: node --version',
      ),
    ],
    [
      'Auth launch fixture contract command weakened',
      yaml.replace(
        'run: node --test scripts/ios-auth-launch-fixture-contract.test.mjs',
        'run: node --version',
      ),
    ],
    [
      'capability matrix contract command weakened',
      yaml.replace('run: node --test scripts/capability-matrix-contract.test.mjs', 'run: node --version'),
    ],
    [
      'Release History contract command weakened',
      yaml.replaceAll('node --test scripts/whats-new-release-history-contract.test.mjs', 'node --version'),
    ],
    [
      'public simulator matrix contract command weakened',
      yaml.replace(
        'run: node --test scripts/ios-public-simulator-matrix-contract.test.mjs',
        'run: node --version',
      ),
    ],
    [
      'public runtime backup contract command weakened',
      yaml.replace(
        'run: bash scripts/test-ios-public-runtime-config-backup.sh',
        'run: bash --version',
      ),
    ],
    ['UI runtime isolation removed', yaml.replace(/QUATA_SUPABASE_URL= \\\n\s*QUATA_SUPABASE_PUBLISHABLE_KEY= \\\n/, '')],
    [
      'core native iOS assertions removed',
      yaml.replace(':core:iosSimulatorArm64Test \\\n', ':core:compileTestKotlinIosSimulatorArm64 \\\n'),
    ],
    [
      'external share inbox assertions removed',
      yaml.replace(':feature:externalshare:iosSimulatorArm64Test', ':feature:externalshare:compileTestKotlinIosSimulatorArm64'),
    ],
    [
      'Official public read assertions removed',
      yaml.replace(':feature:official:iosSimulatorArm64Test', ':feature:official:compileTestKotlinIosSimulatorArm64'),
    ],
    [
      'Profile runtime assertions removed',
      yaml.replace(':feature:profile:iosSimulatorArm64Test', ':feature:profile:compileTestKotlinIosSimulatorArm64'),
    ],
    [
      'boot watchdog accepts any booted simulator',
      yaml.replace(
        'scripts/check-ios-simulator-booted.py',
        'check-ios-any-booted.py',
      ),
    ],
    [
      'bootstatus timeout no longer fails closed',
      yaml.replace('else\n              exit "$bootstatus_status"', 'else\n              exit 0'),
    ],
    [
      'comment terminates the continued xcodebuild command',
      yaml.replace(
        '            -maximum-test-execution-time-allowance 180 \\\n            -skip-testing:QuataIosUITests/QuataIosFeedPlaybackUITests',
        '            -maximum-test-execution-time-allowance 180 \\\n            # misplaced continuation comment\n            -skip-testing:QuataIosUITests/QuataIosFeedPlaybackUITests',
      ),
    ],
    [
      'runtime override loses its continuation',
      yaml.replace('            QUATA_SUPABASE_URL= \\\n', '            QUATA_SUPABASE_URL=\n'),
    ],
  ].filter(([, mutation]) => mutation !== yaml)) await t.test(name, () => {
    assert.throws(() => {
      assertIosWorkflowSelfCoverage(mutation);
      assertIosFastFinalLaneContract(mutation);
      assertIosConcurrencyContract(mutation);
      assertIosRuntimeFixtureAndUiIsolation(mutation);
      assertBootWatchdogRevalidation(mutation);
    });
  });
});

test('independent Web/Android workflow covers iOS workflow changes and runs Wave 2 contracts', async () => {
  const yaml = await readFile(independentWorkflow, 'utf8');
  assertIndependentWebCoverage(yaml);
});

test('independent Web/Android coverage fails closed when final workflow commands are weakened', async (t) => {
  const yaml = await readFile(independentWorkflow, 'utf8');
  for (const [name, mutation] of [
    [
      'Wave 2 contract command weakened',
      yaml.replace('npm run test:web-wave2-contracts', 'npm run test:web'),
    ],
  ]) await t.test(name, () => {
    assert.throws(() => assertIndependentWebCoverage(mutation));
  });
});

test('public Official composition explicitly keeps its Kotlin bootstrap bearer-free', async () => {
  const swift = await readFile(iosAppSource, 'utf8');
  const bootstrap = swift.match(
    /private lazy var officialRuntimeBootstrap:[\s\S]*?\n    \}\(\)/,
  )?.[0];

  assert.ok(bootstrap, 'Official public bootstrap composition must remain present');
  assert.match(
    bootstrap,
    /IosOfficialRuntimeBootstrap\([\s\S]*?authSession:\s*nil(?:\s*,|\s*\))/,
    'Swift must pass the Kotlin-exported authSession argument explicitly and keep public reads anonymous',
  );
  assert.doesNotMatch(
    bootstrap,
    /authSessionForInteractiveLogin/,
    'Official public reads must never inherit the restored private bearer session',
  );
});

test('iOS Profile appearance state is mandatory, persisted by Swift and applied by Compose', async () => {
  const [swift, host, bootstrap] = await Promise.all([
    readFile(iosAppSource, 'utf8'),
    readFile(iosProfileHostSource, 'utf8'),
    readFile(iosProfileBootstrapSource, 'utf8'),
  ]);

  assert.doesNotMatch(host, /touchFlowEnabled:\s*Boolean\s*=|onTouchFlowEnabledChange:[^\n]*=\s*\{\}|themeMode:[^\n]*=|onThemeModeChange:[^\n]*=\s*\{\}/);
  assert.match(host, /var touchFlowEnabled by remember \{ mutableStateOf\(dependencies\.touchFlowEnabled\) \}/);
  assert.match(host, /var themeMode by remember \{ mutableStateOf\(dependencies\.themeMode\) \}/);
  assert.match(host, /QuataTheme\(mode = themeMode\)/);
  for (const required of ['touchFlowEnabled: Boolean', 'themeModeStorageValue: String?', 'onTouchFlowEnabledChange: (Boolean) -> Unit', 'onThemeModeStorageValueChange: (String) -> Unit']) {
    assert.ok(bootstrap.includes(required), `Profile bootstrap must require ${required}`);
  }
  assert.match(bootstrap, /themeMode = QuataThemeMode\.fromStorageValue\(themeModeStorageValue\)/);
  assert.match(swift, /final class IosAppearancePreferences[\s\S]*?UserDefaults[\s\S]*?touchFlowEnabled[\s\S]*?themeModeStorageValue/);
  assert.match(swift, /appearancePreferences\.applyTheme\(to: window\)/);
  assert.match(swift, /profileHostDependencies\([\s\S]*?touchFlowEnabled: appearancePreferences\.touchFlowEnabled[\s\S]*?themeModeStorageValue: appearancePreferences\.themeModeStorageValue/);
});
