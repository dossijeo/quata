import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";

export const FORMAT = "quata-e2e-macro";
export const VERSION = 1;

const STRENGTH = {
  testTag: 100,
  accessibilityIdentifier: 100,
  resourceId: 95,
  id: 90,
  ariaLabel: 80,
  contentDescription: 80,
  visibleText: 70,
  text: 70,
  roleName: 60,
  contextual: 45,
  geometry: 10,
};

export function createMacro({ flow, platform, startUrl = null, device = null }) {
  return {
    format: FORMAT,
    version: VERSION,
    flow,
    platform,
    startUrl,
    device,
    createdAt: new Date().toISOString(),
    steps: [],
  };
}

export async function readMacro(file) {
  const macro = JSON.parse(await readFile(file, "utf8"));
  assertMacro(macro, file);
  return macro;
}

export async function writeMacro(file, macro) {
  assertMacro(macro, file);
  await writeFile(file, `${JSON.stringify(macro, null, 2)}\n`, "utf8");
}

export function assertMacro(macro, origin = "macro") {
  if (!macro || macro.format !== FORMAT || macro.version !== VERSION) {
    throw new Error(`${origin}: expected ${FORMAT} v${VERSION}`);
  }
  if (!macro.flow || !macro.platform || !Array.isArray(macro.steps)) {
    throw new Error(`${origin}: missing flow, platform or steps`);
  }
}

export function normalizeTarget(raw = {}) {
  const candidates = [
    ["testTag", raw.testTag],
    ["accessibilityIdentifier", raw.accessibilityIdentifier],
    ["resourceId", raw.externalApp ? null : raw.resourceId],
    ["id", raw.id],
    ["ariaLabel", raw.ariaLabel],
    ["contentDescription", raw.contentDescription],
    ["visibleText", raw.visibleText],
    ["text", raw.text],
    ["roleName", raw.roleName],
    ["contextual", raw.contextual],
    ["geometry", raw.bounds || raw.relativeBounds || raw.coordinates],
  ].filter(([, value]) => hasValue(value));

  const [kind, value] = candidates.sort((a, b) => STRENGTH[b[0]] - STRENGTH[a[0]])[0] ?? ["geometry", null];
  const score = STRENGTH[kind] ?? 0;
  return {
    ...raw,
    preferred: value == null ? null : { kind, value, score },
    stable: score >= STRENGTH.visibleText,
  };
}

export function stepNeedsStableAnchor(step) {
  return ["tap", "click", "fill", "input", "assertVisible"].includes(step.action);
}

export function summarizeMacro(macro) {
  const steps = macro.steps.map((step, index) => {
    const target = normalizeTarget(step.target);
    return {
      index,
      action: step.action,
      target: target.preferred,
      stable: target.stable,
      missingStableAnchor: stepNeedsStableAnchor(step) && !target.stable,
    };
  });
  return {
    flow: macro.flow,
    platform: macro.platform,
    steps,
    stableSteps: steps.filter((step) => step.stable).length,
    fragileSteps: steps.filter((step) => step.missingStableAnchor).length,
  };
}

export function compileMacro(macro) {
  assertMacro(macro);
  const compiled = {
    flow: macro.flow,
    platform: macro.platform,
    startUrl: macro.startUrl,
    runnable: true,
    diagnostics: [],
    steps: [],
  };

  macro.steps.forEach((step, index) => {
    const target = normalizeTarget(step.target);
    if (stepNeedsStableAnchor(step) && !target.stable) {
      compiled.runnable = false;
      compiled.diagnostics.push({
        index,
        code: "missing_stable_anchor",
        message: `Step ${index} (${step.action}) did not resolve to a stable product anchor; add testTag/accessibilityIdentifier/resource-id/contentDescription/text.`,
        fallback: target.preferred,
        screenshotBefore: step.screenshotBefore,
      });
    }
    compiled.steps.push({
      ...step,
      target,
      replay: selectorFor(macro.platform, target),
    });
  });

  return compiled;
}

export function renderReplayArtifact(compiled) {
  if (!compiled.runnable) {
    throw new Error(`Cannot render replay artifact for ${compiled.flow}: unresolved stable anchors`);
  }
  if (compiled.platform === "web") return renderWebReplayArtifact(compiled);
  if (compiled.platform === "android") return renderAndroidReplayArtifact(compiled);
  if (compiled.platform === "ios") return renderIosReplayArtifact(compiled);
  throw new Error(`Unsupported platform ${compiled.platform}`);
}

export function selectorFor(platform, target) {
  if (!target.preferred) return null;
  if (platform === "web") return selectorForWeb(target);
  if (platform === "android") return selectorForAndroid(target);
  if (platform === "ios") return selectorForIos(target);
  return null;
}

export function selectorForWeb(target) {
  const { kind, value } = target.preferred;
  if (kind === "testTag") return { kind: "locator", value: `[data-testid="${cssAttr(value)}"], #${cssIdent(value)}` };
  if (kind === "id") return { kind: "locator", value: `#${cssIdent(value)}` };
  if (kind === "ariaLabel") return target.roleName ? { kind: "role", role: target.roleName, name: String(value) } : { kind: "aria", value: String(value) };
  if (kind === "visibleText" || kind === "text") return { kind: "text", value: String(value) };
  if (kind === "roleName") return { kind: "role", role: String(value) };
  return { kind: "geometry", value };
}

export function selectorForAndroid(target) {
  const { kind, value } = target.preferred;
  if (kind === "testTag") return { kind: "composeTestTag", value: String(value) };
  if (kind === "resourceId") return { kind: "uiautomatorResourceId", value: String(value) };
  if (kind === "contentDescription" || kind === "ariaLabel") return { kind: "uiautomatorDescription", value: String(value) };
  if (kind === "visibleText" || kind === "text") return { kind: "uiautomatorText", value: String(value) };
  return { kind: "geometry", value };
}

export function selectorForIos(target) {
  const { kind, value } = target.preferred;
  if (kind === "accessibilityIdentifier" || kind === "testTag") return { kind: "xcuiIdentifier", value: String(value) };
  if (kind === "ariaLabel" || kind === "contentDescription" || kind === "visibleText" || kind === "text") return { kind: "xcuiLabel", value: String(value) };
  if (kind === "roleName") return { kind: "xcuiType", value: String(value) };
  return { kind: "geometry", value };
}

export function screenshotPath(evidenceDir, flow, platform, index, phase) {
  return path.join(evidenceDir, `${flow}-${platform}-${String(index).padStart(2, "0")}-${phase}.png`);
}

function renderWebReplayArtifact(compiled) {
  const lines = [
    `// Generated from ${compiled.flow}. Review before promoting to CI.`,
    `test("${compiled.flow}", async ({ page }) => {`,
  ];
  if (compiled.startUrl) lines.push(`  await page.goto(${jsString(compiled.startUrl)}, { waitUntil: "domcontentloaded" });`);
  for (const step of compiled.steps) {
    const locator = webLocatorExpression(step.replay);
    if (step.action === "click" || step.action === "tap") lines.push(`  await ${locator}.click({ timeout: 30_000 });`);
    if (step.action === "input" || step.action === "fill") lines.push(`  await ${locator}.fill(${jsString(step.value ?? step.valuePreview ?? "")}, { timeout: 30_000 });`);
    if (step.action === "assertVisible") lines.push(`  await ${locator}.waitFor({ state: "visible", timeout: 30_000 });`);
  }
  lines.push("});");
  return `${lines.join("\n")}\n`;
}

function renderAndroidReplayArtifact(compiled) {
  const lines = [
    `// Generated from ${compiled.flow}. Review before promoting to instrumentation/CI.`,
    "val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())",
  ];
  for (const step of compiled.steps) {
    const selector = androidSelectorExpression(step.replay);
    if (step.action === "tap" || step.action === "click") {
      lines.push(`device.findObject(${selector}).click()`);
    } else if (step.action === "assertVisible") {
      lines.push(`check(device.hasObject(${selector}))`);
    }
  }
  return `${lines.join("\n")}\n`;
}

function renderIosReplayArtifact(compiled) {
  const lines = [
    `// Generated from ${compiled.flow}. Review before promoting to XCTest/CI.`,
    "let app = XCUIApplication()",
  ];
  for (const step of compiled.steps) {
    const selector = iosSelectorExpression(step.replay);
    if (step.action === "tap" || step.action === "click") lines.push(`${selector}.tap()`);
    if (step.action === "assertVisible") lines.push(`XCTAssertTrue(${selector}.waitForExistence(timeout: 30))`);
  }
  return `${lines.join("\n")}\n`;
}

function webLocatorExpression(selector) {
  if (selector.kind === "locator") return `page.locator(${jsString(selector.value)}).first()`;
  if (selector.kind === "text") return `page.getByText(${jsString(selector.value)}, { exact: false }).first()`;
  if (selector.kind === "role") {
    return selector.name
      ? `page.getByRole(${jsString(selector.role)}, { name: ${jsString(selector.name)} }).first()`
      : `page.getByRole(${jsString(selector.role)}).first()`;
  }
  if (selector.kind === "aria") return `page.locator(${jsString(`[aria-label="${String(selector.value).replaceAll('"', '\\"')}"]`)}).first()`;
  throw new Error(`Unsupported web selector ${JSON.stringify(selector)}`);
}

function androidSelectorExpression(selector) {
  if (selector.kind === "uiautomatorResourceId") return `By.res(${kotlinString(selector.value)})`;
  if (selector.kind === "uiautomatorDescription") return `By.desc(${kotlinString(selector.value)})`;
  if (selector.kind === "uiautomatorText") return `By.text(${kotlinString(selector.value)})`;
  if (selector.kind === "composeTestTag") return `By.desc(${kotlinString(selector.value)}) /* composeTestTag exported through Compose semantics */`;
  throw new Error(`Unsupported Android selector ${JSON.stringify(selector)}`);
}

function iosSelectorExpression(selector) {
  if (selector.kind === "xcuiIdentifier") return `app.descendants(matching: .any).matching(identifier: "${swiftString(selector.value)}").firstMatch`;
  if (selector.kind === "xcuiLabel") return `app.staticTexts["${swiftString(selector.value)}"]`;
  throw new Error(`Unsupported iOS selector ${JSON.stringify(selector)}`);
}

function hasValue(value) {
  if (value == null) return false;
  if (typeof value === "string") return value.trim().length > 0;
  return true;
}

function cssAttr(value) {
  return String(value).replaceAll("\\", "\\\\").replaceAll('"', '\\"');
}

function cssIdent(value) {
  const text = String(value);
  if (globalThis.CSS?.escape) return globalThis.CSS.escape(text);
  return text.replace(/[^a-zA-Z0-9_-]/g, (char) => `\\${char.codePointAt(0).toString(16)} `);
}

function jsString(value) {
  return JSON.stringify(String(value));
}

function kotlinString(value) {
  return `"${String(value).replaceAll("\\", "\\\\").replaceAll('"', '\\"')}"`;
}

function swiftString(value) {
  return String(value).replaceAll("\\", "\\\\").replaceAll('"', '\\"');
}
