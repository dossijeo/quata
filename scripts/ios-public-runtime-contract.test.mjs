import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

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
  assert.match(readiness, /public runtime fixture\/local override must exist before building/);
  assert.match(readiness, /\^\\s\*QUATA_SUPABASE_URL\\s\*=\\s\*https:\/\//,
    'the readiness guard must also reject an indented literal https:// assignment');
  assert.match(readiness, /service_role/);
  assert.match(readiness, /"jwt"/);
});

test('the common community profile keeps attachment and status failures recoverable across hosts', async () => {
  const [models, rootSource, viewModel, iosRepository, iosHost, webRepository, webHost, androidRepository, androidHost] = await Promise.all([
    source('feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/domain/NeighborhoodModels.kt'),
    source('feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileRoot.kt'),
    source('feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModel.kt'),
    source('feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt'),
    source('feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/presentation/IosNeighborhoodsHost.kt'),
    source('web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt'),
    source('web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsHost.kt'),
    source('app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt'),
    source('app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt'),
  ]);

  assert.match(models, /attachmentAvailability: ProfileAttachmentAvailability = ProfileAttachmentAvailability\.Available/);
  assert.match(models, /enum class ProfileAttachmentAvailability \{ Available, AuthenticationRequired, Unavailable \}/);
  assert.match(models, /fun profileAttachmentAvailability\([\s\S]*?!hasAuthenticatedSession -> ProfileAttachmentAvailability\.AuthenticationRequired[\s\S]*?loadSucceeded -> ProfileAttachmentAvailability\.Available[\s\S]*?else -> ProfileAttachmentAvailability\.Unavailable/);
  for (const state of ['Available', 'AuthenticationRequired', 'Unavailable']) {
    assert.match(rootSource, new RegExp(`ProfileAttachmentAvailability\\.${state}`));
  }
  assert.match(rootSource, /onProfileAvatarClick\?\.let \{ openAvatar -> \{ openAvatar\(profile\.user\) \} \}/);

  assert.match(iosRepository, /val attachmentResult = signedInId\?\.let[\s\S]*?runCatching \{ loadSharedAttachments/);
  assert.match(iosRepository, /attachmentAvailability = profileAttachmentAvailability\([\s\S]*?hasAuthenticatedSession = signedInId != null[\s\S]*?loadSucceeded = attachmentResult\?\.isSuccess == true/);
  assert.match(webRepository, /attachmentAvailability = profileAttachmentAvailability\([\s\S]*?hasAuthenticatedSession = currentId != null[\s\S]*?loadSucceeded = attachmentResult\?\.isSuccess == true/);
  assert.match(webRepository, /loadSharedAttachments[\s\S]*?Result<List<ProfileAttachment>> = runCatching/);
  assert.match(androidRepository, /val attachmentResult = currentUserId\?\.let[\s\S]*?runCatching \{ loadSharedSupabaseAttachments/);
  assert.match(androidRepository, /attachmentAvailability = profileAttachmentAvailability\([\s\S]*?hasAuthenticatedSession = currentUserId != null/);

  assert.match(iosHost, /if \(profile == null\)[\s\S]*?TextButton\(onClick = onDismiss\) \{ Text\(dependencies\.profileStrings\.back\) \}/);
  assert.match(webHost, /if \(profile == null\)[\s\S]*?TextButton\(onClick = \{ model\.closeUserProfile\(\); onDismiss\(\) \}\)/);
  assert.match(androidHost, /onProfileAvatarClick = \{ user ->[\s\S]*?selectedAttachment = AttachmentPreview/);

  assert.match(viewModel, /fun reportProfile\(profileId: String, onCompleted: \(Boolean\) -> Unit = \{\}\)/);
  assert.match(iosHost, /if \(success\) reportNotice = dependencies\.profileStrings\.runtime\.reportSuccess/);
  assert.match(webHost, /if \(success\) reportNotice = profileStrings\.runtime\.reportSuccess/);
});
