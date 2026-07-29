import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  chmod,
  cp,
  mkdir,
  mkdtemp,
  readFile,
  rm,
  unlink,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { delimiter, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = new URL("../", import.meta.url);
const appManifestUrl = new URL("iosApp/iosApp/PrivacyInfo.xcprivacy", root);
const extensionManifestUrl = new URL("iosApp/iosShareExtension/PrivacyInfo.xcprivacy", root);
const frameworkManifestUrl = new URL("ios-shared/PrivacyInfo.xcprivacy", root);
const validEmptyPlist = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict/></plist>
`;
const [
  appManifest,
  extensionManifest,
  frameworkManifest,
  project,
  frameworkBuild,
  artifactValidator,
  xcFrameworkValidator,
  archiveScript,
  gitignore,
  workflow,
  independentWorkflow,
  packageJson,
  swiftHost,
  preferenceStore,
  whatsNewStore,
  profileRuntime,
  shareExtension,
  shareQueue,
  shareInbox,
] = await Promise.all([
  readPlist(appManifestUrl),
  readPlist(extensionManifestUrl),
  readPlist(frameworkManifestUrl),
  readFile(new URL("iosApp/project.yml", root), "utf8"),
  readFile(new URL("ios-shared/build.gradle.kts", root), "utf8"),
  readFile(new URL("scripts/validate-ios-privacy-artifacts.sh", root), "utf8"),
  readFile(new URL("scripts/validate-ios-xcframework-privacy-artifacts.sh", root), "utf8"),
  readFile(new URL("scripts/archive-ios-unsigned.sh", root), "utf8"),
  readFile(new URL(".gitignore", root), "utf8"),
  readFile(new URL(".github/workflows/ios-build.yml", root), "utf8"),
  readFile(new URL(".github/workflows/web-android-pr.yml", root), "utf8"),
  readFile(new URL("package.json", root), "utf8"),
  readFile(new URL("iosApp/iosApp/QuataIosApp.swift", root), "utf8"),
  readFile(new URL("core/src/iosMain/kotlin/com/quata/core/platform/IosPreferenceStore.kt", root), "utf8"),
  readFile(new URL("feature/whatsnew/src/iosMain/kotlin/com/quata/feature/whatsnew/data/IosWhatsNewSeenStateStore.kt", root), "utf8"),
  readFile(new URL("feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileSosRuntimeBootstrap.kt", root), "utf8"),
  readFile(new URL("iosApp/iosShareExtension/ShareViewController.swift", root), "utf8"),
  readFile(new URL("iosApp/iosShareQueue/ShareQueue.swift", root), "utf8"),
  readFile(new URL("feature/externalshare/src/iosMain/kotlin/com/quata/feature/externalshare/IosExternalShareInbox.kt", root), "utf8"),
]);

test("IOS-PRIVACY-001 assigns only justified reasons to each shipped bundle", () => {
  assert.deepEqual(apiReasons(appManifest), {
    NSPrivacyAccessedAPICategoryUserDefaults: ["CA92.1"],
  });
  assert.deepEqual(apiReasons(extensionManifest), {
    NSPrivacyAccessedAPICategoryFileTimestamp: ["C617.1"],
  });
  assert.deepEqual(apiReasons(frameworkManifest), {
    NSPrivacyAccessedAPICategoryFileTimestamp: ["C617.1"],
    NSPrivacyAccessedAPICategoryUserDefaults: ["CA92.1"],
  });

  assert.match(swiftHost, /UserDefaults\.standard\.(?:object|string|set)/);
  assert.match(preferenceStore, /NSUserDefaults\.standardUserDefaults/);
  assert.match(whatsNewStore, /NSUserDefaults/);
  assert.match(profileRuntime, /NSUserDefaults\.standardUserDefaults/);
  assert.match(shareExtension, /FileManager\.default\.containerURL/);
  assert.match(shareQueue, /resourceValues\(forKeys: \[/);
  assert.match(shareInbox, /attributesOfItemAtPath/);
});

test("IOS-PRIVACY-001 packages manifests in app, extension and every linked framework slice", () => {
  assert.match(project, /- path: iosApp\/PrivacyInfo\.xcprivacy\n\s+buildPhase: resources/);
  assert.match(project, /- path: iosShareExtension\/PrivacyInfo\.xcprivacy\n\s+buildPhase: resources/);
  assert.match(frameworkBuild, /inputs\.file\(privacyManifest\)/);
  assert.match(
    frameworkBuild,
    /privacyManifest\.asFile\.copyTo\([\s\S]*?outputFile\.get\(\)\.resolve\("PrivacyInfo\.xcprivacy"\)/,
  );
  assert.match(
    frameworkBuild,
    /tasks\.withType<FatFrameworkTask>\(\)\.configureEach \{[\s\S]*?inputs\.file\(privacyManifest\)[\s\S]*?outputs\.file\(destinationDirProperty\.file\("QuataShared\.framework\/PrivacyInfo\.xcprivacy"\)\)[\s\S]*?doLast \{[\s\S]*?fatFramework\.resolve\("PrivacyInfo\.xcprivacy"\)/,
  );
  assert.match(
    frameworkBuild,
    /assembleQuataSharedDebugXCFramework[\s\S]*?assembleQuataSharedReleaseXCFramework[\s\S]*?configureEach \{[\s\S]*?inputs\.file\(privacyManifest\)[\s\S]*?outputs\.dir\(xcFramework\)[\s\S]*?doLast \{[\s\S]*?walkTopDown\(\)[\s\S]*?it\.name == "QuataShared\.framework"[\s\S]*?framework\.resolve\("PrivacyInfo\.xcprivacy"\)/,
  );
});

test("IOS-PRIVACY-001 validates exact real bundle artifacts and fails closed", async () => {
  const temporaryRoot = await mkdtemp(join(tmpdir(), "quata-ios-privacy-"));
  try {
    const app = join(temporaryRoot, "QuataIos.app");
    const extension = join(app, "PlugIns", "QuataShareExtension.appex");
    const framework = join(app, "Frameworks", "QuataShared.framework");
    const bin = join(temporaryRoot, "bin");
    await Promise.all([
      mkdir(extension, { recursive: true }),
      mkdir(framework, { recursive: true }),
      mkdir(bin, { recursive: true }),
    ]);
    await Promise.all([
      cp(fileURLToPath(appManifestUrl), join(app, "PrivacyInfo.xcprivacy")),
      cp(fileURLToPath(extensionManifestUrl), join(extension, "PrivacyInfo.xcprivacy")),
      cp(fileURLToPath(frameworkManifestUrl), join(framework, "PrivacyInfo.xcprivacy")),
    ]);

    const fakePlutil = join(bin, "plutil");
    await writeFile(
      fakePlutil,
      "#!/usr/bin/env bash\n[[ \"$1\" == \"-lint\" && -f \"$2\" ]]\n",
      "utf8",
    );
    await chmod(fakePlutil, 0o755);
    const environment = { ...process.env, PATH: `${bin}${delimiter}${process.env.PATH ?? ""}` };
    assert.equal(runArtifactValidator(app, environment).status, 0);

    await unlink(join(framework, "PrivacyInfo.xcprivacy"));
    assert.notEqual(runArtifactValidator(app, environment).status, 0);

    await cp(fileURLToPath(frameworkManifestUrl), join(framework, "PrivacyInfo.xcprivacy"));
    await cp(fileURLToPath(extensionManifestUrl), join(app, "PrivacyInfo.xcprivacy"));
    assert.notEqual(
      runArtifactValidator(app, environment).status,
      0,
      "A valid plist with the wrong bundle declarations must fail.",
    );

    await cp(fileURLToPath(appManifestUrl), join(app, "PrivacyInfo.xcprivacy"));
    await writeFile(join(extension, "PrivacyInfo.xcprivacy"), validEmptyPlist, "utf8");
    assert.notEqual(
      runArtifactValidator(app, environment).status,
      0,
      "A valid but empty bundle plist must fail.",
    );

    await cp(fileURLToPath(extensionManifestUrl), join(extension, "PrivacyInfo.xcprivacy"));
    await cp(fileURLToPath(appManifestUrl), join(framework, "PrivacyInfo.xcprivacy"));
    assert.notEqual(
      runArtifactValidator(app, environment).status,
      0,
      "A valid plist from another bundle must fail in the embedded framework.",
    );

    await writeFile(join(framework, "PrivacyInfo.xcprivacy"), validEmptyPlist, "utf8");
    assert.notEqual(
      runArtifactValidator(app, environment).status,
      0,
      "A valid but empty embedded-framework plist must fail.",
    );
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test("IOS-PRIVACY-001 enumerates every real XCFramework slice and fails closed", async () => {
  const temporaryRoot = await mkdtemp(join(tmpdir(), "quata-ios-xcprivacy-"));
  try {
    const xcFramework = join(temporaryRoot, "QuataShared.xcframework");
    const firstFramework = join(xcFramework, "ios-device", "QuataShared.framework");
    const secondFramework = join(xcFramework, "simulator-universal", "QuataShared.framework");
    const bin = join(temporaryRoot, "bin");
    await Promise.all([
      mkdir(firstFramework, { recursive: true }),
      mkdir(secondFramework, { recursive: true }),
      mkdir(bin, { recursive: true }),
    ]);
    await Promise.all([
      cp(fileURLToPath(frameworkManifestUrl), join(firstFramework, "PrivacyInfo.xcprivacy")),
      cp(fileURLToPath(frameworkManifestUrl), join(secondFramework, "PrivacyInfo.xcprivacy")),
    ]);
    const fakePlutil = join(bin, "plutil");
    await writeFile(
      fakePlutil,
      "#!/usr/bin/env bash\n[[ \"$1\" == \"-lint\" && -f \"$2\" ]]\n",
      "utf8",
    );
    await chmod(fakePlutil, 0o755);
    const environment = { ...process.env, PATH: `${bin}${delimiter}${process.env.PATH ?? ""}` };
    const success = runShellValidator(
      new URL("scripts/validate-ios-xcframework-privacy-artifacts.sh", root),
      xcFramework,
      environment,
    );
    assert.equal(success.status, 0, success.stderr);
    assert.match(success.stdout, /2 QuataShared XCFramework slices/);

    await unlink(join(secondFramework, "PrivacyInfo.xcprivacy"));
    assert.notEqual(
      runShellValidator(
        new URL("scripts/validate-ios-xcframework-privacy-artifacts.sh", root),
        xcFramework,
        environment,
      ).status,
      0,
    );

    await cp(fileURLToPath(frameworkManifestUrl), join(secondFramework, "PrivacyInfo.xcprivacy"));
    await cp(fileURLToPath(appManifestUrl), join(firstFramework, "PrivacyInfo.xcprivacy"));
    assert.notEqual(
      runShellValidator(
        new URL("scripts/validate-ios-xcframework-privacy-artifacts.sh", root),
        xcFramework,
        environment,
      ).status,
      0,
      "A real slice with a valid but semantically wrong plist must fail.",
    );

    await cp(fileURLToPath(frameworkManifestUrl), join(firstFramework, "PrivacyInfo.xcprivacy"));
    await writeFile(join(secondFramework, "PrivacyInfo.xcprivacy"), validEmptyPlist, "utf8");
    assert.notEqual(
      runShellValidator(
        new URL("scripts/validate-ios-xcframework-privacy-artifacts.sh", root),
        xcFramework,
        environment,
      ).status,
      0,
      "A real slice with a valid but empty plist must fail.",
    );
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test("IOS-PRIVACY-001 gates built and archived artifacts in CI", () => {
  assert.deepEqual(
    [...artifactValidator.matchAll(/"\$app_path\/([^"]+)\/PrivacyInfo\.xcprivacy"/g)].map((match) => match[1]),
    ["PlugIns/QuataShareExtension.appex", "Frameworks/QuataShared.framework"],
  );
  assert.match(artifactValidator, /"\$app_path\/PrivacyInfo\.xcprivacy"/);
  assert.match(artifactValidator, /test -f "\$manifest"/);
  assert.match(artifactValidator, /plutil -lint "\$manifest"/);
  assert.match(artifactValidator, /python3 - "\$expected" "\$manifest"/);
  assert.match(artifactValidator, /if actual != expected:/);
  assert.match(xcFrameworkValidator, /find "\$xcframework_path" -type d -name 'QuataShared\.framework' -print0/);
  assert.match(xcFrameworkValidator, /test -f "\$manifest"/);
  assert.match(xcFrameworkValidator, /plutil -lint "\$manifest"/);
  assert.match(xcFrameworkValidator, /python3 - "\$expected" "\$manifest"/);
  assert.match(xcFrameworkValidator, /if actual != expected:/);
  assert.match(xcFrameworkValidator, /test "\$slice_count" -gt 0/);
  assert.match(
    workflow,
    /- name: Validate built privacy artifacts[\s\S]*?bash scripts\/validate-ios-privacy-artifacts\.sh "\$app_path"/,
  );
  assert.match(
    workflow,
    /bash scripts\/validate-ios-xcframework-privacy-artifacts\.sh \\\n\s+ios-shared\/build\/XCFrameworks\/debug\/QuataShared\.xcframework/,
  );
  assert.match(
    archiveScript,
    /bash scripts\/validate-ios-privacy-artifacts\.sh "\$app_path"/,
  );
  assert.match(
    archiveScript,
    /bash scripts\/validate-ios-xcframework-privacy-artifacts\.sh \\\n\s+ios-shared\/build\/XCFrameworks\/debug\/QuataShared\.xcframework/,
  );
});

test("IOS-PRIVACY-001 remains a mandatory CI and secret-ignore gate", () => {
  assert.equal(
    [...workflow.matchAll(/- "scripts\/ios-privacy-manifest-contract\.test\.mjs"/g)].length,
    2,
  );
  assert.equal([...workflow.matchAll(/- "\.gitignore"/g)].length, 2);
  for (const path of [
    "scripts/validate-ios-privacy-artifacts.sh",
    "scripts/validate-ios-xcframework-privacy-artifacts.sh",
    "scripts/archive-ios-unsigned.sh",
  ]) {
    const escaped = path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    assert.equal([...workflow.matchAll(new RegExp(`- "${escaped}"`, "g"))].length, 2);
    assert.match(independentWorkflow, new RegExp(`- "${escaped}"`));
  }
  assert.match(
    workflow,
    /- name: Validate iOS privacy manifest\n\s+run: node --test scripts\/ios-privacy-manifest-contract\.test\.mjs/,
  );
  assert.match(independentWorkflow, /- "scripts\/ios-privacy-manifest-contract\.test\.mjs"/);
  assert.match(independentWorkflow, /- "\.gitignore"/);
  assert.match(
    JSON.parse(packageJson).scripts["test:web-wave2-contracts"],
    /scripts\/ios-privacy-manifest-contract\.test\.mjs/,
  );
  for (const pattern of ["*.mobileprovision", "*.p8", "*.ipa", "*.cer"]) {
    assert.match(gitignore, new RegExp(`^${pattern.replace(".", "\\.").replace("*", "\\*")}$`, "m"));
  }
});

function apiReasons(plist) {
  return Object.fromEntries(
    plist.NSPrivacyAccessedAPITypes
      .map((entry) => [
        entry.NSPrivacyAccessedAPIType,
        entry.NSPrivacyAccessedAPITypeReasons,
      ])
      .sort(([left], [right]) => left.localeCompare(right)),
  );
}

function readPlist(url) {
  const python = process.platform === "win32" ? "python" : "python3";
  const code = [
    "import json, plistlib, sys",
    "with open(sys.argv[1], 'rb') as source:",
    "    json.dump(plistlib.load(source), sys.stdout, separators=(',', ':'))",
  ].join("\n");
  const result = spawnSync(python, ["-c", code, fileURLToPath(url)], {
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr);
  return JSON.parse(result.stdout);
}

function runArtifactValidator(app, environment) {
  return runShellValidator(
    new URL("scripts/validate-ios-privacy-artifacts.sh", root),
    app,
    environment,
  );
}

function runShellValidator(script, artifact, environment) {
  const bash = process.platform === "win32"
    ? "C:\\Program Files\\Git\\bin\\bash.exe"
    : "bash";
  return spawnSync(
    bash,
    [fileURLToPath(script), artifact],
    { encoding: "utf8", env: environment },
  );
}
