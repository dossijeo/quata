import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const workflow = resolve(import.meta.dirname, '..', '.github', 'workflows', 'wasm-baseline-capture.yml');

function assertCaptureContract(yaml) {
  assert.match(yaml, /^on:\n  workflow_dispatch:\s*$/m, 'capture must be manual only');
  assert.doesNotMatch(yaml, /^  (?:push|pull_request|schedule):/m, 'capture must not accept branch, PR, tag, or scheduled triggers');
  assert.match(yaml, /^permissions:\n  contents: read$/m);
  assert.match(yaml, /runs-on: ubuntu-24\.04/);
  assert.match(yaml, /ref: \$\{\{ github\.sha \}\}\n          fetch-depth: 0/);
  assert.match(yaml, /git fetch --no-tags origin \+refs\/heads\/main:refs\/remotes\/origin\/main/);
  assert.match(yaml, /test "\$GITHUB_REF" = "refs\/heads\/main"/);
  assert.match(yaml, /test "\$GITHUB_SHA" = "\$origin_main"/);
  assert.match(yaml, /git checkout --detach "\$GITHUB_SHA"/);
  assert.match(yaml, /git status --porcelain --untracked-files=all/);
  assert.match(yaml, /distribution: jetbrains\n          java-version: "21\.0\.8"\n          check-latest: false\n          set-default: false/);
  assert.match(yaml, /distribution: temurin\n          java-version: "17"/);
  assert.match(yaml, /node-version: "20\.19\.0"/);
  assert.match(yaml, /gradle\/actions\/setup-gradle@v6/);
  assert.match(yaml, /"platforms;android-36\.1" "build-tools;36\.1\.0"/);
  assert.match(yaml, /npm ci --ignore-scripts/);
  assert.match(yaml, /:web:wasmJsBrowserDistribution --no-daemon --stacktrace --console=plain/);
  assert.match(yaml, /--write-baseline build\/reports\/wasm-bundle\/wasm-bundle-baseline-candidate\.json[\s\S]*?--trusted-ref origin\/main/);
  assert.match(yaml, /candidate\.capture\?\.sourceTree\?\.revision !== sha/);
  assert.match(yaml, /candidate\.inventorySha256 !== candidate\.capture\?\.inventorySha256 \|\| candidate\.inventorySha256 !== report\.inventorySha256/);
  assert.match(yaml, /candidate\.files\.length < 14/);
  assert.match(yaml, /if: always\(\)/);
  assert.match(yaml, /actions\/upload-artifact@v6/);
  assert.doesNotMatch(yaml, /--write-baseline docs\//, 'capture must never write a versioned baseline');
}

test('canonical Linux Wasm baseline capture is fail-closed and artifact-only', async () => {
  assertCaptureContract(await readFile(workflow, 'utf8'));
});

test('capture contract rejects weakened provenance or candidate evidence', async (t) => {
  const yaml = await readFile(workflow, 'utf8');
  for (const [name, mutation] of [
    ['branch trigger', yaml.replace('  workflow_dispatch:', '  push:')],
    ['arbitrary checkout ref', yaml.replace('${{ github.sha }}', '${{ inputs.ref }}')],
    ['shallow history', yaml.replace('fetch-depth: 0', 'fetch-depth: 1')],
    ['missing main ref guard', yaml.replace('            test "$GITHUB_REF" = "refs/heads/main"\n', '')],
    ['missing event SHA guard', yaml.replace('            test "$GITHUB_SHA" = "$origin_main"\n', '')],
    ['attached checkout', yaml.replace('git checkout --detach "$GITHUB_SHA"', 'git checkout "$GITHUB_SHA"')],
    ['missing clean check', yaml.replace('            test -z "$(git status --porcelain --untracked-files=all)"\n', '')],
    ['untrusted candidate ref', yaml.replace('--trusted-ref origin/main', '--trusted-ref refs/tags/v1')],
    ['candidate writes docs', yaml.replace('build/reports/wasm-bundle/wasm-bundle-baseline-candidate.json', 'docs/wasm-bundle-baseline.json')],
    ['missing artifact', yaml.replace('actions/upload-artifact@v6', 'actions/cache@v4')],
  ]) await t.test(name, () => assert.throws(() => assertCaptureContract(mutation)));
});
