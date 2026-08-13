import assert from "node:assert/strict";
import test from "node:test";
import { access, readFile, mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { compileMacro, createMacro, readMacro, renderReplayArtifact, selectorForAndroid, selectorForIos, selectorForWeb, summarizeMacro, writeMacro } from "../tools/e2e-recorder/lib/macro-core.mjs";

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

test("recorder tooling and persistent operating docs describe the workflow", async () => {
  for (const file of [
    "tools/e2e-recorder/web-recorder.mjs",
    "tools/e2e-recorder/android-recorder.mjs",
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
