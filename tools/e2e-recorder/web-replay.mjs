#!/usr/bin/env node
import { mkdir } from "node:fs/promises";
import path from "node:path";
import http from "node:http";
import { chromium } from "playwright-core";
import { compileMacro, readMacro } from "./lib/macro-core.mjs";

const options = parseArgs(process.argv.slice(2));
if (!options.macro) {
  console.error("Usage: node tools/e2e-recorder/web-replay.mjs --macro <macro.json> [--url <url>] [--evidence-dir <dir>]");
  process.exit(64);
}

const macro = await readMacro(options.macro);
const compiled = compileMacro(macro);
if (!compiled.runnable) {
  console.error(JSON.stringify(compiled.diagnostics, null, 2));
  process.exit(2);
}

const evidenceDir = options.evidenceDir ?? path.join(path.dirname(options.macro), `${macro.flow}-replay`);
await mkdir(evidenceDir, { recursive: true });
const browser = await chromium.launch({ headless: options.headed !== "true" });
const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
const page = await context.newPage();
await page.goto(options.url ?? macro.startUrl, { waitUntil: "domcontentloaded" });

const result = { macro: options.macro, steps: [], ok: true };
for (const [index, step] of compiled.steps.entries()) {
  const entry = { index, action: step.action, selector: step.replay, ok: false };
  try {
    await replayStep(page, step);
    await page.screenshot({ path: path.join(evidenceDir, `${String(index).padStart(2, "0")}-${step.action}.png`) });
    entry.ok = true;
  } catch (error) {
    entry.error = String(error);
    entry.url = page.url();
    entry.visibleText = await page.locator("body").innerText({ timeout: 500 }).then((t) => t.slice(0, 800)).catch(() => "");
    result.ok = false;
    result.steps.push(entry);
    break;
  }
  result.steps.push(entry);
}

await browser.close();
console.log(JSON.stringify(result, null, 2));
if (!result.ok) process.exit(1);

async function replayStep(page, step) {
  if (step.action === "navigation" || step.action === "keyboard") return;
  const locator = locatorFor(page, step.replay);
  if (step.action === "click" || step.action === "tap") return locator.click({ timeout: 30_000 });
  if (step.action === "input" || step.action === "fill") return locator.fill(step.value ?? step.valuePreview ?? "", { timeout: 30_000 });
  if (step.action === "assertVisible") return locator.waitFor({ state: "visible", timeout: 30_000 });
  throw new Error(`unsupported action ${step.action}`);
}

function locatorFor(page, selector) {
  if (selector.kind === "locator") return page.locator(selector.value).first();
  if (selector.kind === "text") return page.getByText(selector.value, { exact: false }).first();
  if (selector.kind === "role") return selector.name ? page.getByRole(selector.role, { name: selector.name }).first() : page.getByRole(selector.role).first();
  if (selector.kind === "aria") return page.locator(`[aria-label="${String(selector.value).replaceAll('"', '\\"')}"]`).first();
  if (selector.kind === "geometry") {
    const { x, y } = selector.value?.coordinates ?? selector.value ?? {};
    return { click: () => page.mouse.click(x, y), waitFor: async () => {} };
  }
  throw new Error(`unsupported selector ${JSON.stringify(selector)}`);
}

function parseArgs(args) {
  const parsed = {};
  for (let i = 0; i < args.length; i += 1) {
    if (!args[i].startsWith("--")) continue;
    parsed[args[i].slice(2)] = args[i + 1]?.startsWith("--") ? "true" : args[++i] ?? "true";
  }
  return parsed;
}
