import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = (relative) => readFile(resolve(root, relative), 'utf8');

test('iOS public runtime has empty versioned defaults and an optional ignored local override', async () => {
  const [project, defaults, example, gitignore] = await Promise.all([
    source('iosApp/project.yml'),
    source('iosApp/Configuration/QuataPublicRuntime.xcconfig'),
    source('iosApp/Configuration/QuataPublicRuntime.local.xcconfig.example'),
    source('.gitignore'),
  ]);
  for (const config of ['Debug: Configuration/QuataPublicRuntime.debug.xcconfig', 'Release: Configuration/QuataPublicRuntime.release.xcconfig']) {
    assert.match(project, new RegExp(config.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
  assert.match(project, /QuataIos:\n(?:.*\n)*?    configFiles:\n(?:.*\n)*?      Debug: Configuration\/QuataPublicRuntime\.debug\.xcconfig/);
  assert.match(defaults, /^QUATA_SUPABASE_URL\s*=\s*$/m);
  assert.match(defaults, /^QUATA_SUPABASE_PUBLISHABLE_KEY\s*=\s*$/m);
  assert.match(defaults, /#include\? "QuataPublicRuntime\.local\.xcconfig"/);
  assert.match(example, /https:\/\/local-public-runtime\.invalid/);
  assert.doesNotMatch(example, /^\s*(?:QUATA_\w+)\s*=\s*.*(?:service[_-]?role|jwt|eyJ)/im);
  assert.match(gitignore, /^iosApp\/Configuration\/QuataPublicRuntime\.local\.xcconfig$/m);
});

test('Info.plist passes public settings to Swift and Swift fails closed for unexpanded, URL and CRLF input', async () => {
  const [plist, swift] = await Promise.all([
    source('iosApp/iosApp/Info.plist'),
    source('iosApp/iosApp/QuataIosApp.swift'),
  ]);
  for (const key of ['QUATA_SUPABASE_URL', 'QUATA_SUPABASE_PUBLISHABLE_KEY']) {
    assert.match(plist, new RegExp(`<key>${key}</key>\\s*<string>\\$\\(${key}\\)<\\/string>`));
  }
  assert.match(swift, /configuredURL\(for: supabaseUrlKey/);
  assert.match(swift, /url\.scheme\?\.lowercased\(\) == "https"/);
  assert.match(swift, /rangeOfCharacter\(from: \.newlines\)/);
});

test('iOS CI installs a hermetic .invalid public fixture and validates it before project generation', async () => {
  const [workflow, readiness] = await Promise.all([
    source('.github/workflows/ios-build.yml'),
    source('scripts/check-ios-release-readiness.sh'),
  ]);
  assert.match(workflow, /Install hermetic public runtime fixture/);
  assert.match(workflow, /QUATA_SUPABASE_URL = https:\/\/ios-ci\.invalid/);
  assert.match(workflow, /QUATA_SUPABASE_PUBLISHABLE_KEY = fixture-public-key/);
  assert.match(workflow, /check-ios-release-readiness\.sh --require-public-runtime/);
  assert.ok(workflow.indexOf('check-ios-release-readiness.sh --require-public-runtime') < workflow.indexOf('xcodegen generate'));
  assert.match(readiness, /public runtime fixture\/local override must exist before building/);
  assert.match(readiness, /service_role/);
  assert.match(readiness, /"jwt"/);
});
