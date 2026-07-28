import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const workflow = resolve(import.meta.dirname, '..', '.github', 'workflows', 'ios-build.yml');
const daemonCriteria = resolve(import.meta.dirname, '..', 'gradle', 'gradle-daemon-jvm.properties');

function assertIosDaemonBootstrap(yaml) {
  assert.match(
    yaml,
    /- name: Set up JetBrains Runtime 21 for Gradle daemon\n\s+uses: actions\/setup-java@v5\n\s+with:\n\s+distribution: jetbrains\n\s+java-version: "21"\n\s+set-default: false\n\n\s+- name: Set up JDK 17\n\s+uses: actions\/setup-java@v5\n\s+with:\n\s+distribution: temurin\n\s+java-version: "17"/,
    'iOS CI must preload JBR 21 for the Gradle daemon without replacing its default JDK 17',
  );
}

test('iOS build workflow preloads the daemon runtime while preserving JDK 17 as default', async () => {
  const [yaml, criteria] = await Promise.all([readFile(workflow, 'utf8'), readFile(daemonCriteria, 'utf8')]);
  assertIosDaemonBootstrap(yaml);
  assert.match(criteria, /^toolchainVendor=JETBRAINS$/m);
  assert.match(criteria, /^toolchainVersion=21$/m);
});

test('iOS daemon runtime contract fails closed when its bootstrap or criteria are weakened', async (t) => {
  const [yaml, criteria] = await Promise.all([readFile(workflow, 'utf8'), readFile(daemonCriteria, 'utf8')]);
  for (const [name, workflowMutation, criteriaMutation] of [
    ['JBR becomes default', yaml.replace('set-default: false', 'set-default: true'), criteria],
    ['JBR vendor changes', yaml.replace('distribution: jetbrains', 'distribution: temurin'), criteria],
    ['daemon vendor removed', yaml, criteria.replace('toolchainVendor=JETBRAINS\n', '')],
    ['daemon version removed', yaml, criteria.replace('toolchainVersion=21\n', '')],
  ]) await t.test(name, () => {
    assert.throws(() => {
      assertIosDaemonBootstrap(workflowMutation);
      assert.match(criteriaMutation, /^toolchainVendor=JETBRAINS$/m);
      assert.match(criteriaMutation, /^toolchainVersion=21$/m);
    });
  });
});
