import assert from "node:assert/strict";
import test from "node:test";
import { access, readFile, mkdtemp, rm, writeFile } from "node:fs/promises";
import { execFile } from "node:child_process";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { compileMacro, createMacro, readMacro, renderReplayArtifact, selectorForAndroid, selectorForIos, selectorForWeb, summarizeMacro, writeMacro } from "../tools/e2e-recorder/lib/macro-core.mjs";
import { androidTargetFromPoint, iosTargetFromPoint, uiAutomatorXmlToTree } from "../tools/e2e-recorder/lib/platform-probes.mjs";

const execFileAsync = promisify(execFile);

test("macro compiler prefers stable anchors over coordinates", () => {
  const web = selectorForWeb({
    preferred: { kind: "testTag", value: "legal-document-link-privacy", score: 100 },
  });
  assert.deepEqual(web, { kind: "locator", value: '[data-testid="legal-document-link-privacy"], #legal-document-link-privacy' });

  const android = selectorForAndroid({
    preferred: { kind: "contentDescription", value: "Privacy policy", score: 80 },
  });
  assert.deepEqual(android, { kind: "uiautomatorDescription", value: "Privacy policy" });

  const ios = selectorForIos({
    preferred: { kind: "accessibilityIdentifier", value: "legal-document-link-privacy", score: 100 },
  });
  assert.deepEqual(ios, { kind: "xcuiIdentifier", value: "legal-document-link-privacy" });
});

test("macro compiler fails closed on coordinate-only actionable steps", () => {
  const macro = createMacro({ flow: "fragile", platform: "web" });
  macro.steps.push({
    action: "click",
    target: { coordinates: { x: 10, y: 20 }, bounds: { x: 0, y: 0, width: 100, height: 40 } },
  });

  const compiled = compileMacro(macro);
  assert.equal(compiled.runnable, false);
  assert.equal(compiled.diagnostics[0].code, "missing_stable_anchor");
  assert.equal(summarizeMacro(macro).fragileSteps, 1);
});

test("android recorder does not promote external app resource ids as stable anchors", () => {
  const macro = createMacro({ flow: "external-app", platform: "android" });
  macro.steps.push({
    action: "tap",
    target: {
      resourceId: "com.google.android.apps.nexuslauncher:id/workspace_page_container",
      packageName: "com.google.android.apps.nexuslauncher",
      externalApp: true,
      coordinates: { x: 500, y: 1000 },
      bounds: { x: 38, y: 88, width: 1004, height: 1494 },
    },
  });

  const compiled = compileMacro(macro);
  assert.equal(compiled.runnable, false);
  assert.equal(compiled.diagnostics[0].code, "missing_stable_anchor");
});


test("macro files round-trip with the common format", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "quata-e2e-macro-"));
  try {
    const file = path.join(dir, "macro.json");
    const macro = createMacro({ flow: "round-trip", platform: "ios" });
    macro.steps.push({
      action: "assertVisible",
      target: { accessibilityIdentifier: "document-viewer-status-root" },
    });
    await writeMacro(file, macro);
    const reread = await readMacro(file);
    assert.equal(reread.format, "quata-e2e-macro");
    assert.equal(compileMacro(reread).runnable, true);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("compiler renders reviewed replay snippets for CI promotion", () => {
  const macro = createMacro({ flow: "legal-web", platform: "web", startUrl: "http://localhost/#about" });
  macro.steps.push(
    { action: "click", target: { testTag: "legal-document-link-privacy" } },
    { action: "assertVisible", target: { testTag: "document-viewer-status-root" } },
  );

  const artifact = renderReplayArtifact(compileMacro(macro));
  assert.match(artifact, /Generated from legal-web/);
  assert.match(artifact, /page\.locator\("\[data-testid=\\\"legal-document-link-privacy\\\"\], #legal-document-link-privacy"\)\.first\(\)\.click/);
  assert.match(artifact, /document-viewer-status-root/);
});

test("compiler refuses to emit runner artifacts with missing stable anchors", () => {
  const macro = createMacro({ flow: "fragile-runner", platform: "ios" });
  macro.steps.push({ action: "tap", target: { coordinates: { x: 5, y: 10 } } });

  assert.throws(() => renderReplayArtifact(compileMacro(macro)), /unresolved stable anchors/);
});

test("platform probes resolve Android Compose semantics under a point", () => {
  const target = androidTargetFromPoint({
    packageName: "com.quata",
    bounds: { x: 0, y: 0, width: 390, height: 840 },
    children: [
      {
        packageName: "com.quata",
        bounds: { x: 24, y: 500, width: 342, height: 56 },
        semantics: { testTag: "legal-document-link-privacy", ContentDescription: ["Política de privacidad"] },
      },
    ],
  }, 100, 520);

  assert.equal(target.preferred.kind, "testTag");
  assert.equal(target.preferred.value, "legal-document-link-privacy");
  assert.equal(target.stable, true);
});

test("platform probes prefer the strongest stable anchor under a visual point", () => {
  const target = androidTargetFromPoint({
    packageName: "com.quata",
    bounds: { x: 0, y: 0, width: 1080, height: 1920 },
    children: [
      {
        packageName: "com.quata",
        testTag: "whats-new-next",
        contentDescription: ["next_whats_new"],
        roleName: "Button",
        bounds: { x: 714, y: 1764, width: 300, height: 110 },
      },
      {
        packageName: "com.quata",
        text: ["Continuar"],
        bounds: { x: 780, y: 1792, width: 168, height: 55 },
      },
    ],
  }, 864, 1819);

  assert.equal(target.preferred.kind, "testTag");
  assert.equal(target.preferred.value, "whats-new-next");
  assert.equal(target.stable, true);
});

test("platform probes convert UIAutomator XML into probe trees", () => {
  const tree = uiAutomatorXmlToTree(`
    <hierarchy>
      <node index="0" text="Privacy policy" resource-id="com.quata:id/privacy" class="android.widget.TextView" package="com.quata" content-desc="privacy_policy" bounds="[24,500][366,556]" />
    </hierarchy>
  `);
  const target = androidTargetFromPoint(tree, 100, 520);

  assert.equal(tree.children.length, 1);
  assert.equal(target.preferred.kind, "resourceId");
  assert.equal(target.preferred.value, "com.quata:id/privacy");
  assert.equal(target.stable, true);
});

test("platform probes reject Android nodes owned by another package", () => {
  const target = androidTargetFromPoint({
    packageName: "com.google.android.apps.nexuslauncher",
    bounds: { x: 0, y: 0, width: 1080, height: 2028 },
    resourceId: "com.google.android.apps.nexuslauncher:id/workspace",
    contentDescription: "Launcher workspace",
    text: "QÜATA",
  }, 500, 1000);

  assert.equal(target.stable, false);
  assert.equal(target.externalApp, true);
  assert.equal(target.preferred.kind, "geometry");
  assert.equal(target.contentDescription, undefined);
  assert.equal(target.visibleText, undefined);
});

test("append-step fails closed on unknown actions", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "quata-e2e-append-action-"));
  try {
    const probe = path.join(dir, "android-tree.json");
    const macro = path.join(dir, "android.macro.json");
    await writeFile(probe, JSON.stringify({
      children: [
        {
          packageName: "com.quata",
          resourceId: "com.quata:id/privacy",
          bounds: "[24,500][366,556]",
        },
      ],
    }), "utf8");

    await assert.rejects(
      execFileAsync(process.execPath, [
        "tools/e2e-recorder/append-step.mjs",
        "--macro", macro,
        "--flow", "append-android",
        "--platform", "android",
        "--action", "assert-visible",
        "--probe", probe,
        "--point", "100,520",
      ], { cwd: path.resolve("."), encoding: "utf8" }),
      /--action must be tap or assertVisible/,
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("platform probes resolve iOS AX identifiers under a point", () => {
  const target = iosTargetFromPoint({
    frame: { x: 0, y: 0, width: 390, height: 844 },
    children: [
      {
        identifier: "document-viewer-status-root",
        label: "Visor de documento",
        role: "AXGroup",
        frame: { x: 24, y: 512, width: 342, height: 284 },
      },
    ],
  }, 100, 540);

  assert.equal(target.preferred.kind, "accessibilityIdentifier");
  assert.equal(target.preferred.value, "document-viewer-status-root");
  assert.equal(target.stable, true);
});

test("append-step builds a macro from a platform probe", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "quata-e2e-append-"));
  try {
    const probe = path.join(dir, "android-tree.json");
    const macro = path.join(dir, "android.macro.json");
    await writeFile(probe, JSON.stringify({
      children: [
        {
          packageName: "com.quata",
          resourceId: "com.quata:id/privacy",
          bounds: "[24,500][366,556]",
        },
      ],
    }), "utf8");

    await execFileAsync(process.execPath, [
      "tools/e2e-recorder/append-step.mjs",
      "--macro", macro,
      "--flow", "append-android",
      "--platform", "android",
      "--action", "tap",
      "--probe", probe,
      "--point", "100,520",
    ], { cwd: path.resolve("."), encoding: "utf8" });

    const saved = await readMacro(macro);
    assert.equal(saved.steps.length, 1);
    assert.equal(saved.steps[0].target.preferred.kind, "resourceId");
    assert.equal(compileMacro(saved).runnable, true);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("recorder tooling and persistent operating docs describe the workflow", async () => {
  for (const file of [
    "tools/e2e-recorder/web-recorder.mjs",
    "tools/e2e-recorder/android-recorder.mjs",
    "tools/e2e-recorder/android-compose-semantics.mjs",
    "tools/e2e-recorder/android-dump-tree.mjs",
    "tools/e2e-recorder/append-step.mjs",
    "tools/e2e-recorder/ios-ax-probe.swift",
    "tools/e2e-recorder/ios-compile.mjs",
    "tools/e2e-recorder/README.md",
  ]) {
    await access(new URL(`../${file}`, import.meta.url));
  }

  const docs = await readFile(new URL("../docs/MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md", import.meta.url), "utf8");
  assert.match(docs, /grabaci[oó]n de macro visual/i);
  assert.match(docs, /missing stable anchor|missing_stable_anchor/i);
  assert.match(docs, /CI[\s\S]*certificaci[oó]n final/i);
});
test("Android recorder pipeline documents Compose semantics before UIAutomator fallback", async () => {
  const readme = await readFile(new URL("../tools/e2e-recorder/README.md", import.meta.url), "utf8");
  const exporter = await readFile(new URL("../tools/e2e-recorder/android-compose-semantics.mjs", import.meta.url), "utf8");
  const instrumented = await readFile(new URL("../app/src/androidTest/java/com/quata/tools/e2erecorder/E2eRecorderSemanticsExportInstrumentedTest.kt", import.meta.url), "utf8");

  assert.match(readme, /android-compose-semantics\.mjs/);
  assert.ok(readme.indexOf("android-compose-semantics.mjs") < readme.indexOf("android-dump-tree.mjs"));
  assert.match(exporter, /:app:assembleDebugAndroidTest/);
  assert.match(exporter, /compose-semantics/);
  assert.match(instrumented, /SemanticsProperties\.TestTag/);
  assert.match(instrumented, /SemanticsProperties\.ContentDescription/);
  assert.match(instrumented, /SemanticsProperties\.Text/);
  assert.match(instrumented, /WhatsNewContent/);
});

test("Android recorder tools fail closed before persisting missing stable anchors", async () => {
  const recorder = await readFile(new URL("../tools/e2e-recorder/android-recorder.mjs", import.meta.url), "utf8");
  const appendStep = await readFile(new URL("../tools/e2e-recorder/append-step.mjs", import.meta.url), "utf8");

  assert.match(recorder, /code: "missing_stable_anchor"/);
  assert.match(recorder, /if \(!target\.stable\)[\s\S]*process\.exit\(2\)[\s\S]*adbText\(adb, \["shell", "input", "tap"/);
  assert.match(appendStep, /code: "missing_stable_anchor"/);
  assert.match(appendStep, /if \(!target\.stable\)[\s\S]*process\.exit\(2\)[\s\S]*macro\.steps\.push/);
});

test("append-step does not write a macro when the probe only resolves geometry", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "quata-e2e-append-fragile-"));
  try {
    const probe = path.join(dir, "android-tree.json");
    const macro = path.join(dir, "fragile.macro.json");
    await writeFile(probe, JSON.stringify({
      children: [
        {
          packageName: "com.quata",
          bounds: "[24,500][366,556]",
        },
      ],
    }), "utf8");

    await assert.rejects(
      execFileAsync(process.execPath, [
        "tools/e2e-recorder/append-step.mjs",
        "--macro", macro,
        "--flow", "fragile-android",
        "--platform", "android",
        "--action", "tap",
        "--probe", probe,
        "--point", "100,520",
      ], { cwd: path.resolve("."), encoding: "utf8" }),
      /missing_stable_anchor/,
    );
    await assert.rejects(readMacro(macro), /ENOENT/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("Android replay implements composeTestTag and real assertVisible steps", async () => {
  const replay = await readFile(new URL("../tools/e2e-recorder/android-replay.mjs", import.meta.url), "utf8");
  const artifact = renderReplayArtifact(compileMacro({
    format: "quata-e2e-macro",
    version: 1,
    flow: "android-compose",
    platform: "android",
    startUrl: null,
    device: null,
    createdAt: new Date().toISOString(),
    steps: [
      { action: "assertVisible", target: { testTag: "whats-new-next" } },
    ],
  }));

  assert.match(replay, /else if \(step\.action === "assertVisible"\) await assertVisible\(adb, step\)/);
  assert.match(replay, /selector\.kind === "composeTestTag"/);
  assert.match(replay, /unsupported_android_action/);
  assert.match(artifact, /composeTestTag exported through Compose semantics/);
});

test("Android replay resolves Compose testTags from UIAutomator view-id-resource-name", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "quata-e2e-replay-compose-"));
  try {
    const adb = path.join(dir, process.platform === "win32" ? "adb.cmd" : "adb");
    const macro = path.join(dir, "compose.macro.json");
    await writeFile(macro, JSON.stringify({
      format: "quata-e2e-macro",
      version: 1,
      flow: "compose",
      platform: "android",
      createdAt: new Date().toISOString(),
      steps: [{ action: "assertVisible", target: { testTag: "whats-new-next" } }],
    }), "utf8");
    await writeFakeAdb(adb, `<hierarchy><node package="com.quata" view-id-resource-name="whats-new-next" content-desc="next_whats_new" visible-to-user="true" bounds="[714,1764][1014,1874]" /></hierarchy>`);

    const { stdout } = await execFileAsync(process.execPath, [
      "tools/e2e-recorder/android-replay.mjs",
      "--macro", macro,
      "--adb", adb,
    ], { cwd: path.resolve("."), encoding: "utf8" });
    assert.match(stdout, /"ok": true/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("Android replay fails assertVisible for invisible nodes", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "quata-e2e-replay-invisible-"));
  try {
    const adb = path.join(dir, process.platform === "win32" ? "adb.cmd" : "adb");
    const macro = path.join(dir, "compose.macro.json");
    await writeFile(macro, JSON.stringify({
      format: "quata-e2e-macro",
      version: 1,
      flow: "compose",
      platform: "android",
      createdAt: new Date().toISOString(),
      steps: [{ action: "assertVisible", target: { testTag: "whats-new-next" } }],
    }), "utf8");
    await writeFakeAdb(adb, `<hierarchy><node package="com.quata" view-id-resource-name="whats-new-next" visible-to-user="false" bounds="[714,1764][1014,1874]" /></hierarchy>`);

    await assert.rejects(
      async () => {
        try {
          await execFileAsync(process.execPath, [
        "tools/e2e-recorder/android-replay.mjs",
        "--macro", macro,
        "--adb", adb,
          ], { cwd: path.resolve("."), encoding: "utf8" });
        } catch (error) {
          assert.match(error.stdout, /selector_not_visible/);
          throw error;
        }
      },
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

async function writeFakeAdb(file, xml) {
  if (process.platform === "win32") {
    const escaped = xml.replaceAll("^", "^^").replaceAll("&", "^&").replaceAll("<", "^<").replaceAll(">", "^>");
    await writeFile(file, `@echo off\r\nif "%1"=="shell" echo UI hierchary dumped to: /sdcard/quata-window.xml\r\nif "%1"=="exec-out" echo ${escaped}\r\n`, "utf8");
  } else {
    await writeFile(file, `#!/usr/bin/env sh\nif [ "$1" = "shell" ]; then echo "UI hierchary dumped to: /sdcard/quata-window.xml"; fi\nif [ "$1" = "exec-out" ]; then echo '${xml}'; fi\n`, { mode: 0o755 });
  }
}
