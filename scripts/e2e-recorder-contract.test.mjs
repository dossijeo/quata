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
  }, 500, 1000);

  assert.equal(target.stable, false);
  assert.equal(target.externalApp, true);
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
