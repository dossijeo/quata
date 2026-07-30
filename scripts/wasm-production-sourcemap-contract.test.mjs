import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import test from 'node:test';

const webBuild = resolve(import.meta.dirname, '..', 'web', 'build.gradle.kts');

test('production Wasm distribution disables webpack source maps and verifies its published files', async () => {
  const build = await readFile(webBuild, 'utf8');

  assert.match(
    build,
    /tasks\.withType<KotlinWebpack>\(\)\.configureEach\s*\{\s*if \(name == "wasmJsBrowserProductionWebpack"\)\s*\{\s*sourceMaps = false\s*\}\s*\}/s,
  );
  assert.match(
    build,
    /tasks\.register\("verifyWasmJsProductionDistributionNoSourceMaps"\)\s*\{[\s\S]*?dependsOn\("wasmJsBrowserDistribution"\)[\s\S]*?it\.extension\.equals\("map", ignoreCase = true\)/,
  );
});
