import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const workflow = resolve(import.meta.dirname, '..', '.github', 'workflows', 'ios-build.yml');
const independentWorkflow = resolve(
  import.meta.dirname,
  '..',
  '.github',
  'workflows',
  'web-android-pr.yml',
);
const daemonCriteria = resolve(import.meta.dirname, '..', 'gradle', 'gradle-daemon-jvm.properties');

function assertIosJavaContract(yaml) {
  assert.match(
    yaml,
    /- name: Set up JDK 17\n\s+uses: actions\/setup-java@v5\n\s+with:\n\s+distribution: temurin\n\s+java-version: "17"/,
    'iOS CI must preserve Temurin 17 as its default launcher',
  );
  assert.doesNotMatch(yaml, /- name: Set up JetBrains Runtime 21 for Gradle daemon/);
}

function assertIosWorkflowSelfCoverage(yaml) {
  const pullRequestStart = yaml.indexOf('  pull_request:');
  const pushStart = yaml.indexOf('  push:');
  const concurrencyStart = yaml.indexOf('\nconcurrency:');
  assert.ok(pullRequestStart >= 0 && pushStart > pullRequestStart && concurrencyStart > pushStart);

  const pullRequestTrigger = yaml.slice(pullRequestStart, pushStart);
  const pushTrigger = yaml.slice(pushStart, concurrencyStart);
  for (const trigger of [pullRequestTrigger, pushTrigger]) {
    assert.match(trigger, /- "\.github\/workflows\/ios-build\.yml"/);
    assert.match(trigger, /- "scripts\/ios-build-workflow-contract\.test\.mjs"/);
    assert.match(trigger, /- "scripts\/ios-public-runtime-contract\.test\.mjs"/);
    assert.match(trigger, /- "scripts\/check-ios-release-readiness\.sh"/);
  }

  const checkout = yaml.indexOf('      - name: Check out source');
  const contract = yaml.indexOf('      - name: Validate iOS workflow contract');
  const runtimeContract = yaml.indexOf('      - name: Validate iOS public runtime contract');
  const compilation = yaml.indexOf('      - name: Compile all Kotlin iOS targets');
  assert.ok(checkout >= 0 && contract > checkout && runtimeContract > contract && compilation > runtimeContract);
  assert.match(
    yaml,
    /- name: Validate iOS workflow contract\n\s+run: node --test scripts\/ios-build-workflow-contract\.test\.mjs/,
  );
  assert.match(
    yaml,
    /- name: Validate iOS public runtime contract\n\s+run: node --test scripts\/ios-public-runtime-contract\.test\.mjs/,
  );
}

function assertIosRuntimeFixtureAndUiIsolation(yaml) {
  const fixtureProbe = yaml.indexOf('      - name: Verify Xcode resolves public runtime fixture');
  const testStep = yaml.indexOf('      - name: Test Swift/Kotlin iOS host boundary');
  assert.ok(fixtureProbe >= 0 && testStep > fixtureProbe,
    'the valid xcconfig fixture probe must remain before the isolated UI test');

  const fixtureBlock = yaml.slice(fixtureProbe, testStep);
  assert.match(fixtureBlock, /QUATA_SUPABASE_URL = https:\/\/ios-ci\\\.invalid/,
    'the fixture probe must continue to prove Xcode resolves the valid CI URL');
  assert.doesNotMatch(fixtureBlock, /QUATA_SUPABASE_URL=\s*\\/,
    'the fixture probe must not be overridden into the unconfigured state');

  const uiTestBlock = yaml.slice(testStep, yaml.indexOf('      - name: Capture simulator diagnostics', testStep));
  assert.match(uiTestBlock, /xcodebuild[\s\S]*?QUATA_SUPABASE_URL=\s*\\\n\s*QUATA_SUPABASE_PUBLISHABLE_KEY=\s*\\\n\s*-parallel-testing-enabled NO/,
    'the app-under-test must receive empty public runtime settings before UI tests');
}

function assertIndependentWebCoverage(yaml) {
  const pullRequestStart = yaml.indexOf('  pull_request:');
  const concurrencyStart = yaml.indexOf('\nconcurrency:');
  assert.ok(pullRequestStart >= 0 && concurrencyStart > pullRequestStart);

  const pullRequestTrigger = yaml.slice(pullRequestStart, concurrencyStart);
  assert.match(pullRequestTrigger, /- "\.github\/workflows\/ios-build\.yml"/);
  assert.match(pullRequestTrigger, /- "scripts\/ios-build-workflow-contract\.test\.mjs"/);
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
  assertIosRuntimeFixtureAndUiIsolation(yaml);
});

test('iOS workflow self-coverage fails closed when a trigger or command is removed', async (t) => {
  const yaml = await readFile(workflow, 'utf8');
  const contractPath = '      - "scripts/ios-build-workflow-contract.test.mjs"\n';
  const runtimeContractPath = '      - "scripts/ios-public-runtime-contract.test.mjs"\n';
  const pushContractIndex = yaml.lastIndexOf(contractPath);
  const withoutPushTrigger =
    yaml.slice(0, pushContractIndex) + yaml.slice(pushContractIndex + contractPath.length);
  for (const [name, mutation] of [
    ['pull-request trigger removed', yaml.replace(contractPath, '')],
    ['push trigger removed', withoutPushTrigger],
    ['public runtime trigger removed', yaml.replace(runtimeContractPath, '')],
    [
      'contract command weakened',
      yaml.replace(
        'run: node --test scripts/ios-build-workflow-contract.test.mjs',
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
    ['UI runtime isolation removed', yaml.replace(/QUATA_SUPABASE_URL= \\\n\s*QUATA_SUPABASE_PUBLISHABLE_KEY= \\\n/, '')],
  ]) await t.test(name, () => {
    assert.throws(() => {
      assertIosWorkflowSelfCoverage(mutation);
      assertIosRuntimeFixtureAndUiIsolation(mutation);
    });
  });
});

test('independent Web/Android workflow covers iOS workflow changes and runs Wave 2 contracts', async () => {
  const yaml = await readFile(independentWorkflow, 'utf8');
  assertIndependentWebCoverage(yaml);
});

test('independent Web/Android coverage fails closed when an iOS path or contract command is removed', async (t) => {
  const yaml = await readFile(independentWorkflow, 'utf8');
  for (const [name, mutation] of [
    ['iOS workflow path removed', yaml.replace('      - ".github/workflows/ios-build.yml"\n', '')],
    [
      'iOS contract path removed',
      yaml.replace('      - "scripts/ios-build-workflow-contract.test.mjs"\n', ''),
    ],
    [
      'Wave 2 contract command weakened',
      yaml.replace('npm run test:web-wave2-contracts', 'npm run test:web'),
    ],
  ]) await t.test(name, () => {
    assert.throws(() => assertIndependentWebCoverage(mutation));
  });
});
