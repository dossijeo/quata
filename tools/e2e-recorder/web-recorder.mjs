#!/usr/bin/env node
import { mkdir, stat } from "node:fs/promises";
import path from "node:path";
import http from "node:http";
import { chromium } from "playwright-core";
import { createMacro, normalizeTarget, screenshotPath, writeMacro } from "./lib/macro-core.mjs";

const options = parseArgs(process.argv.slice(2));
if (!options.out || !options.flow) {
  console.error("Usage: node tools/e2e-recorder/web-recorder.mjs --flow <name> --out <macro.json> [--dist <dir>] [--url <url>] [--demo legal]");
  process.exit(64);
}

const evidenceDir = options.evidenceDir ?? path.join(path.dirname(options.out), `${options.flow}-evidence`);
await mkdir(evidenceDir, { recursive: true });
const server = options.url ? null : await serveDist(options.dist);
const startUrl = appendFragment(options.url ?? server.url, options.fragment);

const browser = await chromium.launch({ headless: options.headed !== "true" });
const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
const page = await context.newPage();
const macro = createMacro({ flow: options.flow, platform: "web", startUrl });
await installRecorder(page, macro, evidenceDir);
await page.goto(startUrl, { waitUntil: "domcontentloaded" });

if (options.demo === "legal") {
  await runLegalDemo(page, macro, evidenceDir);
} else {
  console.log("Recorder armed. Interact in the browser, then press Ctrl+C to finish.");
  await page.waitForTimeout(Number(options.durationMs ?? 30_000));
}

await writeMacro(options.out, macro);
await browser.close();
await server?.close();
console.log(JSON.stringify({ out: options.out, steps: macro.steps.length, evidenceDir }, null, 2));

async function installRecorder(page, macro, evidenceDir) {
  await page.exposeFunction("__quataRecordEvent", async (event) => {
    const index = macro.steps.length;
    const before = screenshotPath(evidenceDir, macro.flow, "web", index, "before");
    const after = screenshotPath(evidenceDir, macro.flow, "web", index, "after");
    await page.screenshot({ path: before, fullPage: false }).catch(() => {});
    await page.waitForTimeout(150).catch(() => {});
    await page.screenshot({ path: after, fullPage: false }).catch(() => {});
    const state = await observableState(page).catch((error) => ({ error: String(error) }));
    macro.steps.push({
      timestamp: new Date().toISOString(),
      platform: "web",
      action: event.action,
      screen: state.route,
      coordinates: event.coordinates,
      target: normalizeTarget(event.target),
      screenshotBefore: before,
      screenshotAfter: after,
      observableAfter: state,
    });
  });
  await page.addInitScript(() => {
    const textOf = (element) => (element?.innerText || element?.textContent || "").replace(/\s+/g, " ").trim().slice(0, 160);
    const boundsOf = (element) => {
      const r = element.getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height };
    };
    const targetOf = (element, clientX, clientY) => {
      let current = element;
      for (let i = 0; current && i < 4; i += 1) {
        const target = {
          tagName: current.tagName,
          id: current.id || null,
          testTag: current.getAttribute("data-testid") || current.getAttribute("data-testtag") || current.id || null,
          roleName: current.getAttribute("role") || current.tagName?.toLowerCase() || null,
          ariaLabel: current.getAttribute("aria-label") || current.getAttribute("title") || null,
          visibleText: textOf(current),
          bounds: boundsOf(current),
          relativeBounds: {
            x: boundsOf(current).x / Math.max(1, window.innerWidth),
            y: boundsOf(current).y / Math.max(1, window.innerHeight),
            width: boundsOf(current).width / Math.max(1, window.innerWidth),
            height: boundsOf(current).height / Math.max(1, window.innerHeight),
          },
          coordinates: {
            x: clientX,
            y: clientY,
            relativeX: clientX / Math.max(1, window.innerWidth),
            relativeY: clientY / Math.max(1, window.innerHeight),
          },
        };
        if (target.testTag || target.ariaLabel || target.visibleText) return target;
        current = current.parentElement;
      }
      return {
        roleName: element?.tagName?.toLowerCase() ?? null,
        bounds: element ? boundsOf(element) : null,
        coordinates: {
          x: clientX,
          y: clientY,
          relativeX: clientX / Math.max(1, window.innerWidth),
          relativeY: clientY / Math.max(1, window.innerHeight),
        },
      };
    };
    window.__quataResolveElement = (element) => {
      const r = element.getBoundingClientRect();
      return targetOf(element, r.x + r.width / 2, r.y + r.height / 2);
    };
    window.addEventListener("click", (event) => {
      if (!window.__quataRecordEvent) return;
      window.__quataRecordEvent({
        action: "click",
        coordinates: { x: event.clientX, y: event.clientY, relativeX: event.clientX / window.innerWidth, relativeY: event.clientY / window.innerHeight },
        target: targetOf(document.elementFromPoint(event.clientX, event.clientY), event.clientX, event.clientY),
      });
    }, true);
    window.addEventListener("input", (event) => {
      if (!window.__quataRecordEvent) return;
      const element = event.target;
      window.__quataRecordEvent({
        action: "input",
        coordinates: null,
        valuePreview: String(element?.value ?? "").slice(0, 80),
        target: targetOf(element, 0, 0),
      });
    }, true);
    window.addEventListener("keydown", (event) => {
      if (!window.__quataRecordEvent || event.key.length !== 1) return;
      window.__quataRecordEvent({ action: "keyboard", key: event.key, target: { roleName: "document", visibleText: document.title } });
    }, true);
    window.addEventListener("popstate", () => window.__quataRecordEvent?.({ action: "navigation", target: { visibleText: location.href } }), true);
  });
}

async function observableState(page) {
  const ax = await page.context().newCDPSession(page).then((cdp) => cdp.send("Accessibility.getFullAXTree")).catch(() => ({ nodes: [] }));
  return {
    route: page.url(),
    title: await page.title(),
    visibleText: await page.locator("body").innerText({ timeout: 500 }).then((t) => t.replace(/\s+/g, " ").slice(0, 600)).catch(() => ""),
    axNames: (ax.nodes ?? []).map((node) => node.name?.value).filter(Boolean).slice(0, 40),
  };
}

async function runLegalDemo(page, macro, evidenceDir) {
  const privacy = page.locator('[data-testid="legal-document-link-privacy"], #legal-document-link-privacy').first()
    .or(page.getByText(/Política de privacidad|Privacy policy|Politique de confidentialité/i).first());
  await privacy.click({ timeout: 30_000 });
  await page.waitForTimeout(600);
  const viewer = page.locator('[data-testid="document-viewer-status-root"], #document-viewer-status-root').first();
  await viewer.waitFor({ state: "visible", timeout: 30_000 });
  const index = macro.steps.length;
  const after = screenshotPath(evidenceDir, macro.flow, "web", index, "after");
  await page.screenshot({ path: after, fullPage: false }).catch(() => {});
  const target = await viewer.evaluate((element) => window.__quataResolveElement(element));
  macro.steps.push({
    timestamp: new Date().toISOString(),
    platform: "web",
    action: "assertVisible",
    screen: page.url(),
    target: normalizeTarget(target),
    screenshotBefore: after,
    screenshotAfter: after,
    observableAfter: await observableState(page).catch((error) => ({ error: String(error) })),
  });
}

async function serveDist(dist) {
  if (!dist) throw new Error("--dist is required when --url is not provided");
  await stat(dist);
  const root = path.resolve(dist);
  const server = http.createServer(async (request, response) => {
    const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
    const rel = pathname === "/" ? "index.html" : pathname.slice(1);
    const target = path.normalize(path.join(root, rel));
    if (!target.startsWith(root)) {
      response.writeHead(403).end();
      return;
    }
    const file = await import("node:fs/promises").then((fs) => fs.readFile(target).catch(() => fs.readFile(path.join(root, "index.html"))));
    response.writeHead(200);
    response.end(file);
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  return { url: `http://127.0.0.1:${port}/`, close: () => new Promise((resolve) => server.close(resolve)) };
}

function parseArgs(args) {
  const parsed = {};
  for (let i = 0; i < args.length; i += 1) {
    if (!args[i].startsWith("--")) continue;
    parsed[args[i].slice(2)] = args[i + 1]?.startsWith("--") ? "true" : args[++i] ?? "true";
  }
  return parsed;
}

function appendFragment(url, fragment) {
  if (!fragment) return url;
  const clean = String(fragment).replace(/^#/, "");
  return `${url.replace(/#.*$/, "")}#${clean}`;
}
