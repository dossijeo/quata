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
const iosAppSource = resolve(import.meta.dirname, '..', 'iosApp', 'iosApp', 'QuataIosApp.swift');

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
  const publicMatrixPaths = [
    'scripts/ios-compose-resources-contract.test.mjs',
    'scripts/sync-ios-compose-resources.sh',
    'scripts/build-ios-intel-simulator.sh',
    'scripts/build-ios-intel-simulator-signed.sh',
    'scripts/ios-auth-launch-fixture-contract.test.mjs',
    'scripts/ios-public-client-config.py',
    'scripts/ios-public-log-evidence.py',
    'scripts/ios-public-runtime-config-backup.sh',
    'scripts/ios-public-screenshot-classifier.swift',
    'scripts/ios-public-screenshot-classifier-fixtures.swift',
    'scripts/test-ios-public-screenshot-classifier.sh',
    'scripts/ios-public-simulator-matrix-contract.test.mjs',
    'scripts/run-ios-public-simulator-matrix.sh',
    'scripts/test-ios-public-runtime-config-backup.sh',
  ];
  const composeResourcePath = 'designsystem/src/commonMain/composeResources/**';
  for (const trigger of [pullRequestTrigger, pushTrigger]) {
    assert.match(trigger, /- "\.github\/workflows\/ios-build\.yml"/);
    assert.match(trigger, /- "scripts\/ios-build-workflow-contract\.test\.mjs"/);
    assert.match(trigger, /- "scripts\/ios-public-runtime-contract\.test\.mjs"/);
    assert.match(trigger, /- "scripts\/capability-matrix-contract\.test\.mjs"/);
    assert.match(trigger, /- "scripts\/capability-matrix-contract\.mjs"/);
    assert.match(trigger, /- "capabilities\/platform-capability-matrix\.json"/);
    assert.match(trigger, /- "scripts\/check-ios-release-readiness\.sh"/);
    assert.doesNotMatch(trigger, /- "(?:app|web)\/\*\*"/);
    assert.doesNotMatch(trigger, /- "package(?:-lock)?\.json"/);
    assert.ok(
      trigger.includes(`- "${composeResourcePath}"`),
      `iOS workflow trigger must cover ${composeResourcePath}`,
    );
    for (const path of publicMatrixPaths) {
      assert.ok(
        trigger.includes(`- "${path}"`),
        `iOS workflow trigger must cover ${path}`,
      );
    }
  }

  const checkout = yaml.indexOf('      - name: Check out source');
  const contract = yaml.indexOf('      - name: Validate iOS workflow contract');
  const composeResourcesContract = yaml.indexOf('      - name: Validate iOS Compose resources contract');
  const authLaunchContract = yaml.indexOf('      - name: Validate iOS Auth launch fixture contract');
  const runtimeContract = yaml.indexOf('      - name: Validate iOS public runtime contract');
  const capabilityContract = yaml.indexOf('      - name: Validate platform capability matrix');
  const matrixContract = yaml.indexOf('      - name: Validate iOS public simulator matrix contract');
  const backupContract = yaml.indexOf('      - name: Validate iOS public runtime backup contract');
  const compilation = yaml.indexOf('      - name: Compile all Kotlin iOS targets');
  assert.ok(
    checkout >= 0 &&
      contract > checkout &&
      composeResourcesContract > contract &&
      authLaunchContract > composeResourcesContract &&
      runtimeContract > authLaunchContract &&
      matrixContract > runtimeContract &&
      backupContract > matrixContract &&
      capabilityContract > backupContract &&
      compilation > capabilityContract,
  );
  assert.match(
    yaml,
    /- name: Validate iOS workflow contract\n\s+run: node --test scripts\/ios-build-workflow-contract\.test\.mjs/,
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
  const testStep = yaml.indexOf('      - name: Test Swift/Kotlin iOS host boundary');
  assert.ok(fixtureProbe >= 0 && bootSimulator > fixtureProbe && inboxFilesystemTest > bootSimulator && officialPublicReadTest > inboxFilesystemTest && testStep > officialPublicReadTest,
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

  const uiTestBlock = yaml.slice(testStep, yaml.indexOf('      - name: Capture simulator diagnostics', testStep));
  const invocation = effectiveContinuedCommand(uiTestBlock, 'run_watchdog 1200');
  assert.match(
    invocation,
    /run_watchdog 1200 build\/reports\/ios\/xcodebuild-tests\.log xcodebuild .* QUATA_SUPABASE_URL= QUATA_SUPABASE_PUBLISHABLE_KEY= -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 test$/,
    'the effective xcodebuild test invocation must end with isolated runtime settings and serialized tests',
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
  assert.match(pullRequestTrigger, /- "\.github\/workflows\/ios-build\.yml"/);
  assert.match(pullRequestTrigger, /- "scripts\/ios-build-workflow-contract\.test\.mjs"/);
  assert.match(pullRequestTrigger, /- "scripts\/capability-matrix-contract\.test\.mjs"/);
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
  const composeResourcesContractPath = '      - "scripts/ios-compose-resources-contract.test.mjs"\n';
  const composeResourcesSynchronizerPath = '      - "scripts/sync-ios-compose-resources.sh"\n';
  const composeResourcesUnsignedLanePath = '      - "scripts/build-ios-intel-simulator.sh"\n';
  const composeResourcesSignedLanePath = '      - "scripts/build-ios-intel-simulator-signed.sh"\n';
  const authLaunchContractPath = '      - "scripts/ios-auth-launch-fixture-contract.test.mjs"\n';
  const runtimeContractPath = '      - "scripts/ios-public-runtime-contract.test.mjs"\n';
  const capabilityContractPath = '      - "scripts/capability-matrix-contract.test.mjs"\n';
  const capabilityImplementationPath = '      - "scripts/capability-matrix-contract.mjs"\n';
  const matrixContractPath = '      - "scripts/ios-public-simulator-matrix-contract.test.mjs"\n';
  const pushContractIndex = yaml.lastIndexOf(contractPath);
  const withoutPushTrigger =
    yaml.slice(0, pushContractIndex) + yaml.slice(pushContractIndex + contractPath.length);
  for (const [name, mutation] of [
    ['pull-request trigger removed', yaml.replace(contractPath, '')],
    ['push trigger removed', withoutPushTrigger],
    ['Auth launch fixture pull-request trigger removed', yaml.replace(authLaunchContractPath, '')],
    [
      'Auth launch fixture push trigger removed',
      (() => {
        const index = yaml.lastIndexOf(authLaunchContractPath);
        return yaml.slice(0, index) + yaml.slice(index + authLaunchContractPath.length);
      })(),
    ],
    ['Compose resources contract pull-request trigger removed', yaml.replace(composeResourcesContractPath, '')],
    [
      'Compose resources contract push trigger removed',
      (() => {
        const index = yaml.lastIndexOf(composeResourcesContractPath);
        return yaml.slice(0, index) + yaml.slice(index + composeResourcesContractPath.length);
      })(),
    ],
    ['Compose resources synchronizer pull-request trigger removed', yaml.replace(composeResourcesSynchronizerPath, '')],
    [
      'Compose resources synchronizer push trigger removed',
      (() => {
        const index = yaml.lastIndexOf(composeResourcesSynchronizerPath);
        return yaml.slice(0, index) + yaml.slice(index + composeResourcesSynchronizerPath.length);
      })(),
    ],
    ['Compose resources unsigned lane pull-request trigger removed', yaml.replace(composeResourcesUnsignedLanePath, '')],
    [
      'Compose resources unsigned lane push trigger removed',
      (() => {
        const index = yaml.lastIndexOf(composeResourcesUnsignedLanePath);
        return yaml.slice(0, index) + yaml.slice(index + composeResourcesUnsignedLanePath.length);
      })(),
    ],
    ['Compose resources signed lane pull-request trigger removed', yaml.replace(composeResourcesSignedLanePath, '')],
    [
      'Compose resources signed lane push trigger removed',
      (() => {
        const index = yaml.lastIndexOf(composeResourcesSignedLanePath);
        return yaml.slice(0, index) + yaml.slice(index + composeResourcesSignedLanePath.length);
      })(),
    ],
    ['public runtime trigger removed', yaml.replace(runtimeContractPath, '')],
    ['capability matrix trigger removed', yaml.replace(capabilityContractPath, '')],
    ['capability implementation trigger removed', yaml.replace(capabilityImplementationPath, '')],
    ['Android-only trigger added', yaml.replace('      - "core/**"', '      - "app/**"\n      - "core/**"')],
    ['package-only trigger added', yaml.replace('      - "core/**"', '      - "package.json"\n      - "core/**"')],
    ['public matrix pull-request trigger removed', yaml.replace(matrixContractPath, '')],
    [
      'public matrix push trigger removed',
      (() => {
        const index = yaml.lastIndexOf(matrixContractPath);
        return yaml.slice(0, index) + yaml.slice(index + matrixContractPath.length);
      })(),
    ],
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
      'comment terminates the continued xcodebuild command',
      yaml.replace(
        '            CODE_SIGNING_REQUIRED=NO \\\n            -test-timeouts-enabled YES',
        '            CODE_SIGNING_REQUIRED=NO \\\n            # misplaced continuation comment\n            -test-timeouts-enabled YES',
      ),
    ],
    [
      'runtime override loses its continuation',
      yaml.replace('            QUATA_SUPABASE_URL= \\\n', '            QUATA_SUPABASE_URL=\n'),
    ],
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
      'capability matrix path removed',
      yaml.replace('      - "scripts/capability-matrix-contract.test.mjs"\n', ''),
    ],
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
    /IosOfficialRuntimeBootstrap\([\s\S]*?authSession:\s*nil\s*\)/,
    'Swift must pass the Kotlin-exported authSession argument explicitly and keep public reads anonymous',
  );
  assert.doesNotMatch(
    bootstrap,
    /authSessionForInteractiveLogin/,
    'Official public reads must never inherit the restored private bearer session',
  );
});
