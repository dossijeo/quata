import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { execFileSync, spawnSync } from 'node:child_process';
import { chmodSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { relative, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = (relative) => readFile(resolve(root, relative), 'utf8');

function parseAndExpandXcconfig(...contents) {
  const values = {};
  for (const content of contents) {
    for (const line of content.split(/\r?\n/)) {
      const match = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
      if (match) values[match[1]] = match[2];
    }
  }
  const expand = (value, resolving = new Set()) => value.replace(/\$\(([^)]+)\)/g, (token, key) => {
    if (resolving.has(key) || !(key in values)) return token;
    return expand(values[key], new Set([...resolving, key]));
  });
  return Object.fromEntries(Object.entries(values).map(([key, value]) => [key, expand(value, new Set([key]))]));
}

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
  assert.match(defaults, /^QUATA_XCCONFIG_SLASH\s*=\s*\/$/m);
  assert.match(example, /^QUATA_SUPABASE_URL\s*=\s*https:\$\(QUATA_XCCONFIG_SLASH\)\$\(QUATA_XCCONFIG_SLASH\)local-public-runtime\.invalid$/m);
  assert.doesNotMatch(example, /^QUATA_SUPABASE_URL\s*=\s*https:\/\//m);
  assert.equal(
    parseAndExpandXcconfig(defaults, example).QUATA_SUPABASE_URL,
    'https://local-public-runtime.invalid',
    'the .xcconfig URL composition must expand to the literal runtime URL',
  );
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

test('the primary iOS app Info.plist declares the modern launch-screen dictionary', async (t) => {
  const plist = await source('iosApp/iosApp/Info.plist');
  const launchScreen = /<key>UILaunchScreen<\/key>\s*<dict\s*\/>/;

  assert.match(
    plist,
    launchScreen,
    'the primary app must opt into the modern iOS launch-screen metadata',
  );
  assert.doesNotMatch(plist, /<key>UILaunchStoryboardName<\/key>/);

  await t.test('fails closed if launch-screen metadata is removed', () => {
    assert.throws(() => assert.match(plist.replace(launchScreen, ''), launchScreen));
  });
});

test('iOS routes media permissions to native services and treats document access as picker-scoped', async () => {
  const [composite, camera, photos, microphone, plist] = await Promise.all([
    source('core/src/iosMain/kotlin/com/quata/core/platform/IosCoreLocationHost.kt'),
    source('core/src/iosMain/kotlin/com/quata/core/platform/IosCameraPermissionService.kt'),
    source('core/src/iosMain/kotlin/com/quata/core/platform/IosPhotosPermissionService.kt'),
    source('core/src/iosMain/kotlin/com/quata/core/platform/IosMicrophonePermissionService.kt'),
    source('iosApp/iosApp/Info.plist'),
  ]);

  for (const operation of ['status', 'request']) {
    assert.match(composite, new RegExp(`PlatformPermission\\.Camera -> camera\\.${operation}\\(permission\\)`));
    assert.match(composite, new RegExp(`PlatformPermission\\.Photos,[\\s\\S]*PlatformPermission\\.Videos -> photos\\.${operation}\\(permission\\)`));
    assert.match(composite, new RegExp(`PlatformPermission\\.Microphone -> microphone\\.${operation}\\(permission\\)`));
  }
  assert.equal((composite.match(/PlatformPermission\.Files -> PermissionStatus\.Granted/g) ?? []).length, 2);
  assert.match(camera, /requestAccessForMediaType\(AVMediaTypeVideo/);
  assert.match(photos, /requestAuthorizationForAccessLevel\(PHAccessLevelReadWrite/);
  assert.match(microphone, /requestRecordPermission/);
  for (const key of ['NSCameraUsageDescription', 'NSMicrophoneUsageDescription', 'NSPhotoLibraryUsageDescription']) {
    assert.match(plist, new RegExp(`<key>${key}<\\/key>\\s*<string>[^<]+<\\/string>`));
  }
});

test('iOS media permission runtime probe preserves native Simulator transitions and the Photos grant limitation', async () => {
  const [swift, runner, classifier] = await Promise.all([
    source('iosApp/iosAppTests/IosMediaPermissionRuntimeTests.swift'),
    source('scripts/run-ios-media-permissions-runtime-test.sh'),
    source('scripts/classify-ios-media-permission-photo-grant.py'),
  ]);

  assert.match(swift, /IosCompositePermissionService\(/);
  for (const permission of ['camera', 'microphone', 'photos', 'videos', 'files']) {
    assert.match(swift, new RegExp(`assertStatus\\(\\.[a-z]+, for: \\.${permission}\\)`));
  }
  assert.match(runner, /QUATA_IOS_SIMULATOR_UDID:\?Set QUATA_IOS_SIMULATOR_UDID/);
  assert.match(runner, /QUATA_IOS_XCTESTRUN:\?Set QUATA_IOS_XCTESTRUN/);
  assert.match(runner, /simctl privacy "\$udid" reset all/);
  assert.match(runner, /simctl privacy "\$udid" grant microphone/);
  assert.match(runner, /simctl privacy "\$udid" grant photos/);
  assert.match(runner, /simctl privacy "\$udid" revoke microphone/);
  assert.match(runner, /simctl privacy "\$udid" revoke photos/);
  assert.match(runner, /xcresulttool get test-results summary/);
  assert.match(runner, /xcresulttool get test-results tests/);
  assert.match(runner, /python3 scripts\/classify-ios-media-permission-photo-grant\.py/);
  assert.match(runner, /trap on_exit EXIT/);
  assert.match(runner, /"\$report_dir\/cleanup\.json"/);
  assert.match(runner, /"cleanup": "passed"/);
  assert.doesNotMatch(runner, /reset all[^\n]*\|\| true/);
  assert.ok(runner.indexOf('if ! cleanup; then') < runner.indexOf('"overall": "go"'),
    'cleanup must pass before the runner can write an overall GO result');
  assert.match(classifier, /simulator_read_write_grant_unavailable/);
  assert.match(classifier, /len\(failure_messages\) != 2/);
});

test('iOS media permission runner attempts both cleanup resets and fails closed', async () => {
  const directory = mkdtempSync(resolve(root, '.tmp-ios-media-cleanup-'));
  const relativeDirectory = relative(root, directory).replaceAll('\\', '/');
  const binDirectory = resolve(directory, 'bin');
  const xcrun = resolve(binDirectory, 'xcrun');
  const xcodebuild = resolve(binDirectory, 'xcodebuild');
  mkdirSync(binDirectory, { recursive: true });
  writeFileSync(resolve(directory, 'fixture.xctestrun'), 'fixture\n');
  writeFileSync(xcrun, `#!/usr/bin/env bash
set -u
if [[ "$1 $2 $3" == "simctl list devices" ]]; then
  printf '%s\\n' '{"devices":{"runtime":[{"udid":"TEST-UDID","state":"Booted"}]}}'
  exit 0
fi
if [[ "$1 $2 $3" == "simctl privacy TEST-UDID" ]]; then
  if [[ "$4" == "reset" ]]; then
    count=0
    [[ ! -f "$FAKE_RESET_COUNT" ]] || count="$(cat "$FAKE_RESET_COUNT")"
    count=$((count + 1))
    printf '%s\\n' "$count" > "$FAKE_RESET_COUNT"
    [[ "$count" -ne 3 ]] || exit 41
  fi
  exit 0
fi
exit 2
`);
  writeFileSync(xcodebuild, '#!/usr/bin/env bash\nexit 0\n');
  chmodSync(xcrun, 0o755);
  chmodSync(xcodebuild, 0o755);

  try {
    const command = [
      `PATH="$PWD/${relativeDirectory}/bin:$PATH"`,
      `FAKE_RESET_COUNT="$PWD/${relativeDirectory}/reset-count"`,
      'QUATA_IOS_SIMULATOR_UDID=TEST-UDID',
      `QUATA_IOS_XCTESTRUN="$PWD/${relativeDirectory}/fixture.xctestrun"`,
      `QUATA_IOS_MEDIA_PERMISSION_REPORT_DIR="$PWD/${relativeDirectory}/report"`,
      'bash scripts/run-ios-media-permissions-runtime-test.sh',
    ].join(' ');
    const run = spawnSync('bash', ['-lc', command], { cwd: root, encoding: 'utf8' });
    assert.notEqual(run.status, 0, `cleanup failure must fail the runner: ${run.stdout}\n${run.stderr}`);
    const cleanup = JSON.parse(await readFile(resolve(directory, 'report', 'cleanup.json'), 'utf8'));
    assert.deepEqual(cleanup, {
      overall: 'failed',
      resets: [
        { bundleId: 'com.quata.ios', exitCode: 41, status: 'failed' },
        { bundleId: 'com.quata.ios.tests', exitCode: 0, status: 'passed' },
      ],
    });
    await assert.rejects(readFile(resolve(directory, 'report', 'result.json'), 'utf8'));
    assert.equal(Number((await readFile(resolve(directory, 'reset-count'), 'utf8')).trim()), 4,
      'both final cleanup resets must be attempted even when the first one fails');
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

function photoGrantClassifierFixture(extraFailures = []) {
  const testName = 'testGrantedPhotoReadWritePermissionReflectsSimulatorPrivacyState()';
  return {
    summary: {
      result: 'Failed',
      totalTestCount: 1,
      failedTests: 1,
      passedTests: 0,
      skippedTests: 0,
      expectedFailures: 0,
      testFailures: [{
        targetName: 'QuataIosTests',
        testName,
        failureText: 'XCTAssertTrue failed - Expected Granted, received Denied',
        testIdentifierString: `IosMediaPermissionRuntimeTests/${testName}`,
      }],
    },
    tests: {
      testNodes: [{
        nodeType: 'Test Plan',
        children: [{
          nodeType: 'Test Case',
          name: testName,
          nodeIdentifier: `IosMediaPermissionRuntimeTests/${testName}`,
          result: 'Failed',
          children: [
            { nodeType: 'Failure Message', name: 'IosMediaPermissionRuntimeTests.swift:32: XCTAssertTrue failed - Expected Granted, received Denied' },
            { nodeType: 'Failure Message', name: 'IosMediaPermissionRuntimeTests.swift:33: XCTAssertTrue failed - Expected Granted, received Denied' },
            ...extraFailures,
          ],
        }],
      }],
    },
  };
}

function classifyPhotoGrantResult(summary, tests) {
  const directory = mkdtempSync(resolve(tmpdir(), 'quata-photo-classifier-'));
  try {
    const summaryPath = resolve(directory, 'summary.json');
    const testsPath = resolve(directory, 'tests.json');
    writeFileSync(summaryPath, JSON.stringify(summary));
    writeFileSync(testsPath, JSON.stringify(tests));
    return JSON.parse(execFileSync('python3', [
      resolve(root, 'scripts/classify-ios-media-permission-photo-grant.py'),
      '--summary', summaryPath,
      '--tests', testsPath,
    ], { encoding: 'utf8' }));
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test('iOS Photos grant classifier accepts only the two structured read/write assertions', () => {
  const { summary, tests } = photoGrantClassifierFixture();
  assert.deepEqual(classifyPhotoGrantResult(summary, tests), {
    classification: 'simulator_read_write_grant_unavailable',
    expectedAssertions: 2,
    unexpectedFailures: 0,
  });
});

test('iOS Photos grant classifier rejects an additional failure after the known assertions', () => {
  const { summary, tests } = photoGrantClassifierFixture([
    { nodeType: 'Failure Message', name: 'The test runner crashed after the assertions' },
  ]);
  assert.throws(() => classifyPhotoGrantResult(summary, tests), /Command failed/);
});

test('iOS CI installs a hermetic .invalid public fixture and validates it before project generation', async () => {
  const [workflow, readiness] = await Promise.all([
    source('.github/workflows/ios-build.yml'),
    source('scripts/check-ios-release-readiness.sh'),
  ]);
  assert.match(workflow, /Install hermetic public runtime fixture/);
  assert.match(workflow, /QUATA_SUPABASE_URL = https:\$\(QUATA_XCCONFIG_SLASH\)\$\(QUATA_XCCONFIG_SLASH\)ios-ci\.invalid/);
  assert.match(workflow, /QUATA_SUPABASE_PUBLISHABLE_KEY = fixture-public-key/);
  assert.match(workflow, /check-ios-release-readiness\.sh --require-public-runtime/);
  assert.ok(workflow.indexOf('check-ios-release-readiness.sh --require-public-runtime') < workflow.indexOf('xcodegen generate'));
  assert.match(workflow, /- name: Verify Xcode resolves public runtime fixture[\s\S]*?-showBuildSettings[\s\S]*?QUATA_SUPABASE_URL = https:\/\/ios-ci\\\.invalid/);
  assert.ok(workflow.indexOf('xcodegen generate') < workflow.indexOf('Verify Xcode resolves public runtime fixture'));
  assert.ok(workflow.indexOf('Verify Xcode resolves public runtime fixture') < workflow.indexOf('Build Swift iOS host'));
  assert.match(workflow, /Run iOS Feed playback public-runtime UI test[\s\S]*?-only-testing:QuataIosUITests\/QuataIosFeedPlaybackUITests/);
  assert.ok(workflow.indexOf('Build Swift iOS host') < workflow.indexOf('Run iOS Feed playback public-runtime UI test'));
  assert.ok(workflow.indexOf('Run iOS Feed playback public-runtime UI test') < workflow.indexOf('Test Swift/Kotlin iOS host boundary'));
  assert.match(workflow, /Test Swift\/Kotlin iOS host boundary[\s\S]*?-skip-testing:QuataIosUITests\/QuataIosFeedPlaybackUITests[\s\S]*?-skip-testing:QuataIosTests\/IosMediaPermissionRuntimeTests[\s\S]*?QUATA_SUPABASE_URL=/);
  assert.match(readiness, /public runtime fixture\/local override must exist before building/);
  assert.match(readiness, /\^\\s\*QUATA_SUPABASE_URL\\s\*=\\s\*https:\/\//,
    'the readiness guard must also reject an indented literal https:// assignment');
  assert.match(readiness, /service_role/);
  assert.match(readiness, /"jwt"/);
});

test('iOS Feed playback UI test mounts the deterministic shared Feed fixture before asserting controls', async () => {
  const testSource = await source('iosApp/iosAppUITests/QuataIosFeedPlaybackUITests.swift');
  const launchIndex = testSource.indexOf('app.launch()');
  const fixtureIndex = testSource.indexOf('"-quata-ui-test-fixture", "feed-playback"');
  const muteIndex = testSource.indexOf('"Silenciar"');
  const appSource = await source('iosApp/iosApp/QuataIosApp.swift');

  assert.ok(launchIndex >= 0, 'the Feed playback UI test must launch the iOS app');
  assert.ok(fixtureIndex >= 0 && fixtureIndex < launchIndex,
    'the Feed playback UI test must request the deterministic shared Feed playback fixture before launch');
  assert.ok(launchIndex < muteIndex, 'the Feed playback UI test must launch before looking for Feed controls');
  assert.match(appSource, /case "feed-playback":\s*return IosFeedPlaybackFixtureHostKt\.QuataIosFeedPlaybackFixtureViewController/);
  assert.match(testSource, /feed-mute-control-missing/);
});

test('iOS Feed playback fixture uses the shared Compose Feed host without remote reads', async () => {
  const [fixture, workflow] = await Promise.all([
    source('feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/IosFeedPlaybackFixtureHost.kt'),
    source('.github/workflows/ios-build.yml'),
  ]);

  assert.match(fixture, /QuataIosFeedPlaybackFixtureViewController\(mediaFactory: IosFeedMediaFactory\): UIViewController/);
  assert.match(fixture, /QuataFeedViewController\(/);
  assert.match(fixture, /IosFeedHostDependencies\(/);
  assert.match(fixture, /mediaFactory = IosFeedPlaybackFixtureMediaFactory/);
  assert.match(fixture, /private class IosFeedPlaybackFixtureMediaSurface : IosFeedMediaSurface/);
  assert.match(fixture, /videoUrl = "https:\/\/egquata\.com\/wp-content\/uploads\/2026\/08\/feed-playback-fixture\.mp4"/);
  assert.doesNotMatch(fixture, /RemoteFeedReadRepository|IosFeedReadTransport|IosFeedRuntimeConfiguration/);
  assert.match(workflow, /playback must not depend on remote Feed rows in this lane/);
});
