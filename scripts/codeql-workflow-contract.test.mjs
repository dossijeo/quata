import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

const workflow = resolve(import.meta.dirname, "..", ".github", "workflows", "codeql.yml");

function jobBlock(yaml, job) {
  const start = yaml.indexOf(`  ${job}:`);
  assert.ok(start >= 0, `missing job ${job}`);
  const next = yaml.slice(start + 1).search(/\n  [a-z][a-z-]*:/);
  return yaml.slice(start, next < 0 ? undefined : start + 1 + next);
}

function assertCodeQlWorkflow(yaml) {
  assert.match(yaml, /^on:\n  push:\n    branches: \[main\]\n  pull_request:\n    branches: \[main\]\n  schedule:/m);
  assert.match(
    yaml,
    /group: codeql-\$\{\{ github\.event_name == 'pull_request' && format\('pr-\{0\}', github\.event\.pull_request\.number\) \|\| format\('\{0\}-\{1\}', github\.event_name, github\.ref\) \}\}\n  cancel-in-progress: \$\{\{ github\.event_name == 'pull_request' \}\}/,
    "only superseded PR CodeQL runs may be cancelled",
  );

  const classify = jobBlock(yaml, "classify-impact");
  assert.match(classify, /uses: actions\/checkout@v6[\s\S]*?fetch-depth: 0/);
  assert.match(classify, /outputs:\n      docs_only: \$\{\{ steps\.impact\.outputs\.docs_only \}\}/);
  assert.match(classify, /node scripts\/classify-ci-impact\.mjs --base "\$BASE_SHA" --head "\$HEAD_SHA" --github-output "\$GITHUB_OUTPUT"/);
  assert.match(classify, /"\$\{\{ github\.event_name \}\}" == "schedule"[\s\S]*?--all --github-output "\$GITHUB_OUTPUT"/);

  const analyze = jobBlock(yaml, "analyze");
  assert.match(analyze, /needs: \[classify-impact\]/);
  assert.match(analyze, /if: \$\{\{ github\.event_name != 'pull_request' \|\| needs\.classify-impact\.outputs\.docs_only != 'true' \}\}/);
  assert.match(analyze, /language: java-kotlin[\s\S]*?language: javascript-typescript/);
  assert.match(analyze, /Install Android SDK used by the app[\s\S]*?if: matrix\.language == 'java-kotlin'/);
  assert.match(analyze, /Build Android sources for CodeQL[\s\S]*?if: matrix\.language == 'java-kotlin'/);
}

test("CodeQL classifies docs-only PRs before expensive setup and cancels only superseded PR runs", async () => {
  assertCodeQlWorkflow(await readFile(workflow, "utf8"));
});

test("CodeQL workflow contract fails closed if docs-only or concurrency guards are weakened", async () => {
  const yaml = await readFile(workflow, "utf8");
  const mutations = [
    yaml.replace("needs: [classify-impact]", "needs: []"),
    yaml.replace("needs.classify-impact.outputs.docs_only != 'true'", "true"),
    yaml.replace("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", "cancel-in-progress: true"),
    yaml.replace("format('pr-{0}', github.event.pull_request.number)", "github.ref"),
    yaml.replace(/  classify-impact:[\s\S]*?(?=\n  analyze:)/, ""),
    yaml.replace(/elif \[\[ "\$\{\{ github\.event_name \}\}" == "schedule" \]\]; then[\s\S]*?--all --github-output "\$GITHUB_OUTPUT"\n\s+else/, "else"),
  ].filter((mutation) => mutation !== yaml);
  assert.ok(mutations.length >= 6);
  for (const mutation of mutations) {
    assert.throws(() => assertCodeQlWorkflow(mutation));
  }
});
