import { readFile } from "node:fs/promises";
import { normalizeTarget } from "./macro-core.mjs";

export async function readProbeTree(file) {
  const payload = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  if (!payload || typeof payload !== "object") throw new Error(`${file}: expected probe JSON object`);
  return payload;
}

export function targetFromProbePoint({ platform, tree, x, y, packageName = "com.quata" }) {
  if (platform === "android") return androidTargetFromPoint(tree, x, y, packageName);
  if (platform === "ios") return iosTargetFromPoint(tree, x, y);
  throw new Error(`Unsupported probe platform ${platform}`);
}

export function androidTargetFromPoint(tree, x, y, packageName = "com.quata") {
  const nodes = flattenNodes(tree).map(normalizeAndroidNode);
  const targets = containingNodes(nodes, x, y).map((node) => {
    const externalApp = Boolean(node.packageName && node.packageName !== packageName);
    if (externalApp) {
      return normalizeTarget({
        packageName: node.packageName,
        externalApp,
        bounds: node.bounds,
        coordinates: { x, y },
        contextual: node.contextual,
      });
    }
    return normalizeTarget({
      testTag: node.testTag,
      resourceId: node.resourceId,
      contentDescription: firstText(node.contentDescription),
      visibleText: firstText(node.text),
      roleName: node.roleName,
      packageName: node.packageName,
      externalApp,
      bounds: node.bounds,
      coordinates: { x, y },
      contextual: node.contextual,
    });
  });
  return strongestContainingTarget(targets) ?? missingTarget(x, y);
}

export function uiAutomatorXmlToTree(xml) {
  const nodes = [...String(xml).matchAll(/<node\b[^>]*>/g)]
    .map((match) => parseUiAutomatorNode(match[0]))
    .filter(Boolean);
  return { source: "uiautomator", children: nodes };
}

export function iosTargetFromPoint(tree, x, y) {
  const nodes = flattenNodes(tree).map(normalizeIosNode);
  const targets = containingNodes(nodes, x, y).map((node) => normalizeTarget({
    accessibilityIdentifier: node.accessibilityIdentifier,
    ariaLabel: node.label,
    visibleText: node.value || node.title,
    roleName: node.roleName,
    bounds: node.bounds,
    coordinates: { x, y },
    contextual: node.contextual,
  }));
  return strongestContainingTarget(targets) ?? missingTarget(x, y);
}

function flattenNodes(root) {
  const out = [];
  const stack = Array.isArray(root) ? [...root] : [root];
  while (stack.length) {
    const node = stack.shift();
    if (!node || typeof node !== "object") continue;
    out.push(node);
    const children = node.children || node.nodes || node.subviews || [];
    if (Array.isArray(children)) stack.unshift(...children);
  }
  return out;
}

function normalizeAndroidNode(node) {
  return {
    testTag: node.testTag || node.tag || node.semantics?.testTag || node.semantics?.TestTag || null,
    resourceId: node.resourceId || node["resource-id"] || null,
    contentDescription: node.contentDescription || node["content-desc"] || node.semantics?.contentDescription || node.semantics?.ContentDescription || null,
    text: node.text || node.semantics?.text || node.semantics?.Text || null,
    roleName: node.roleName || node.role || node.className || node.class || null,
    packageName: node.packageName || node.package || null,
    bounds: normalizeBounds(node.bounds || node.frame),
    contextual: node.screen || node.context || null,
  };
}

function parseUiAutomatorNode(raw) {
  const attrs = Object.fromEntries([...raw.matchAll(/([\w-]+)="([^"]*)"/g)].map(([, key, value]) => [key, decodeXml(value)]));
  const bounds = normalizeBounds(attrs.bounds);
  if (!bounds) return null;
  return {
    text: attrs.text || null,
    resourceId: attrs["resource-id"] || null,
    contentDescription: attrs["content-desc"] || null,
    roleName: attrs.class || null,
    packageName: attrs.package || null,
    bounds,
    contextual: attrs["display-id"] ? `display:${attrs["display-id"]}` : null,
  };
}

function normalizeIosNode(node) {
  return {
    accessibilityIdentifier: node.accessibilityIdentifier || node.identifier || null,
    label: node.label || node.accessibilityLabel || null,
    value: node.value || null,
    title: node.title || null,
    roleName: node.roleName || node.role || node.type || null,
    bounds: normalizeBounds(node.bounds || node.frame),
    contextual: node.screen || node.context || null,
  };
}

function normalizeBounds(bounds) {
  if (!bounds) return null;
  if (typeof bounds === "string") {
    const match = /^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/.exec(bounds);
    if (!match) return null;
    const [, left, top, right, bottom] = match.map(Number);
    return { x: left, y: top, width: right - left, height: bottom - top };
  }
  const x = Number(bounds.x ?? bounds.left ?? bounds.minX);
  const y = Number(bounds.y ?? bounds.top ?? bounds.minY);
  const width = Number(bounds.width ?? (bounds.right != null ? bounds.right - x : NaN));
  const height = Number(bounds.height ?? (bounds.bottom != null ? bounds.bottom - y : NaN));
  if (![x, y, width, height].every(Number.isFinite)) return null;
  return { x, y, width, height };
}

function containingNodes(nodes, x, y) {
  return nodes
    .filter((node) => node.bounds && x >= node.bounds.x && x <= node.bounds.x + node.bounds.width && y >= node.bounds.y && y <= node.bounds.y + node.bounds.height);
}

function strongestContainingTarget(targets) {
  return targets
    .filter((target) => target.preferred)
    .sort((a, b) => {
      const byScore = (b.preferred?.score ?? 0) - (a.preferred?.score ?? 0);
      if (byScore !== 0) return byScore;
      return area(a.bounds) - area(b.bounds);
    })[0] ?? null;
}

function area(bounds) {
  return bounds ? bounds.width * bounds.height : Number.POSITIVE_INFINITY;
}

function missingTarget(x, y) {
  return normalizeTarget({ coordinates: { x, y } });
}

function firstText(value) {
  if (Array.isArray(value)) return value.map((entry) => typeof entry === "string" ? entry : entry?.text).find(Boolean) ?? null;
  if (value && typeof value === "object") return value.text ?? null;
  return value ?? null;
}

function decodeXml(value) {
  return value.replaceAll("&quot;", '"').replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">");
}
