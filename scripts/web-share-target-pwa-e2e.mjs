#!/usr/bin/env node
/**
 * Browser-level PWA Share Target contract.
 *
 * This starts the production Wasm distribution, lets Chrome register the real
 * service worker, and submits multipart requests through that worker. It does
 * not call Supabase or create a user: receiving a share must be safe before an
 * authenticated destination is selected.
 */
import { createServer } from "node:http";
import { mkdtemp, readFile, rm, stat } from "node:fs/promises";
import { extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { tmpdir } from "node:os";
import { chromium } from "playwright-core";
import { shareTargetNetworkDecision } from "./web-share-target-network-policy.mjs";

const distribution = resolve(process.argv[2] ?? "web/build/dist/wasmJs/productionExecutable");
const chrome = process.env.QUATA_CHROME_PATH ?? "C:/Program Files/Google/Chrome/Application/chrome.exe";
await requireFile(join(distribution, "index.html"));
await requireFile(join(distribution, "quata-sw.js"));
await requireFile(join(distribution, "manifest.webmanifest"));

const profile = await mkdtemp(join(tmpdir(), "quata-web-share-target-"));
const server = await startServer(distribution);
let context;
const unexpectedNetworkRequests = [];
let turnstileRequests = 0;
const report = {
  check: "WEB-SHARE-001",
  mode: "installed_pwa_service_worker_hermetic_network",
  status: "failed",
  checks: [],
};
try {
  context = await chromium.launchPersistentContext(profile, {
    executablePath: chrome,
    headless: true,
    args: [
      "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--no-first-run",
      // Context routing rejects every non-allowlisted request. The closed local proxy is an
      // independent transport backstop for service-worker traffic Chromium does not route.
      "--proxy-server=http://127.0.0.1:9",
      "--proxy-bypass-list=127.0.0.1;localhost",
    ],
  });
  const recordUnexpected = descriptor => {
    const value = `${descriptor.method} ${descriptor.url}`;
    if (!unexpectedNetworkRequests.includes(value)) unexpectedNetworkRequests.push(value);
  };
  context.on("request", request => {
    const descriptor = { method: request.method(), url: request.url() };
    if (shareTargetNetworkDecision(descriptor, server.origin) === "block-unexpected") {
      recordUnexpected(descriptor);
    }
  });
  await context.route("**/*", async route => {
    const request = route.request();
    const descriptor = { method: request.method(), url: request.url() };
    const decision = shareTargetNetworkDecision(descriptor, server.origin);
    if (decision === "continue-local") {
      await route.continue();
    } else if (decision === "stub-turnstile") {
      turnstileRequests += 1;
      await route.fulfill({
        status: 200,
        contentType: "text/javascript; charset=utf-8",
        body: "globalThis.turnstile = globalThis.turnstile ?? {};",
      });
    } else {
      recordUnexpected(descriptor);
      await route.abort("blockedbyclient");
    }
  });
  const page = context.pages()[0] ?? await context.newPage();
  await page.addInitScript(() => {
    globalThis.__quataIncomingShareEntries = async () => {
      const database = await new Promise((resolveDb, rejectDb) => {
        const request = indexedDB.open("quata-web", 2);
        request.onsuccess = () => resolveDb(request.result);
        request.onerror = () => rejectDb(request.error);
      });
      return new Promise((resolveEntries, rejectEntries) => {
        const request = database.transaction("incoming-shares", "readonly").objectStore("incoming-shares").getAll();
        request.onsuccess = () => resolveEntries(request.result);
        request.onerror = () => rejectEntries(request.error);
      });
    };
  });
  await page.goto(server.origin, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => navigator.serviceWorker?.ready, undefined, { timeout: 30_000 });
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, undefined, { timeout: 30_000 });
  const manifest = await page.evaluate(async () => {
    const response = await fetch("/manifest.webmanifest");
    return response.json();
  });
  assert(manifest.share_target?.action === "/share-target", "manifest_must_declare_share_target");
  report.checks.push("installed_launcher_has_controller_and_manifest_share_target");

  const anonymousGet = await page.evaluate(async () => {
    const response = await fetch("/share-target", { method: "GET", redirect: "manual" });
    return { status: response.status, shares: (await globalThis.__quataIncomingShareEntries()).length };
  });
  assert(anonymousGet.status === 404 && anonymousGet.shares === 0, "anonymous_get_must_not_be_intercepted_or_persist_a_share");
  report.checks.push("anonymous_get_navigation_is_not_intercepted");

  await Promise.all([
    page.waitForURL(/#share-target$/, { timeout: 30_000 }),
    page.evaluate(() => {
      const form = document.createElement("form");
      form.method = "POST";
      form.action = "/share-target";
      form.enctype = "multipart/form-data";
      for (const [name, value] of [["title", "A title"], ["text", "Hello PWA"], ["url", "https://example.test/share"]]) {
        const input = document.createElement("input");
        input.name = name;
        input.value = value;
        form.append(input);
      }
      const fileInput = document.createElement("input");
      fileInput.name = "files";
      fileInput.type = "file";
      const transfer = new DataTransfer();
      transfer.items.add(new File(["image-bytes"], "photo.png", { type: "image/png" }));
      fileInput.files = transfer.files;
      form.append(fileInput);
      document.body.append(form);
      form.submit();
    }),
  ]);
  const accepted = await page.evaluate(async () => {
    const entry = (await globalThis.__quataIncomingShareEntries())[0] ?? null;
    return { url: location.href, entry: entry && { text: entry.text, name: entry.attachments?.[0]?.name, size: entry.attachments?.[0]?.blob?.size } };
  });
  report.validPayload = accepted;
  assert(accepted.url.endsWith("/#share-target"), "valid_share_must_navigate_to_share_target");
  assert(accepted.entry?.text === "A title\nHello PWA\nhttps://example.test/share", "valid_share_must_persist_normalized_text");
  assert(accepted.entry?.name === "photo.png" && accepted.entry.size === 11, "valid_share_must_persist_file_blob");
  report.checks.push("valid_multipart_share_persists_blob_and_redirects_to_launcher");

  for (const invalid of ["empty", "too-many", "too-large"]) {
    const sharesBefore = await page.evaluate(() => globalThis.__quataIncomingShareEntries().then(entries => entries.length));
    await page.goto(server.origin, { waitUntil: "domcontentloaded" });
    await Promise.all([
      page.waitForURL(/#share-target-error$/, { timeout: 30_000 }),
      page.evaluate((kind) => {
        const form = document.createElement("form");
        form.method = "POST"; form.action = "/share-target"; form.enctype = "multipart/form-data";
        const input = document.createElement("input"); input.name = "files"; input.type = "file";
        const transfer = new DataTransfer();
        if (kind === "too-many") for (let index = 0; index < 9; index += 1) transfer.items.add(new File(["x"], `${index}.txt`, { type: "text/plain" }));
        if (kind === "too-large") transfer.items.add(new File([new Uint8Array(25 * 1024 * 1024 + 1)], "large.bin", { type: "application/octet-stream" }));
        input.files = transfer.files; form.append(input); document.body.append(form); form.submit();
      }, invalid),
    ]);
    assert(page.url().endsWith("/#share-target-error"), `${invalid}_share_must_fail_closed_to_error_route`);
    assert(await page.evaluate(() => globalThis.__quataIncomingShareEntries().then(entries => entries.length)) === sharesBefore, `${invalid}_share_must_not_persist_a_new_entry`);
    report.checks.push(`${invalid}_share_fails_closed_to_error_route`);
  }
  assert(turnstileRequests > 0, "turnstile_bootstrap_stub_not_exercised");
  assert(unexpectedNetworkRequests.length === 0, "unexpected_external_network_request");
  report.checks.push("all_external_network_is_blocked_except_exact_turnstile_stub");
  report.status = "passed";
} finally {
  await context?.close().catch(() => undefined);
  await server.close();
  await rm(profile, { recursive: true, force: true });
  console.log(JSON.stringify(report, null, 2));
}

function assert(condition, code) { if (!condition) throw new Error(code); }
async function requireFile(path) { if (!(await stat(path).catch(() => null))?.isFile()) throw new Error(`Missing required file: ${path}`); }
async function startServer(rootDirectory) {
  const root = resolve(rootDirectory);
  const server = createServer(async (request, response) => {
    const pathname = decodeURIComponent(new URL(request.url ?? "/", "http://localhost").pathname);
    const candidate = resolve(root, `.${pathname === "/" ? "/index.html" : pathname}`);
    const relativeCandidate = relative(root, candidate);
    if (
      relativeCandidate === ".." ||
      relativeCandidate.startsWith(`..${sep}`) ||
      isAbsolute(relativeCandidate) ||
      !(await stat(candidate).catch(() => null))?.isFile()
    ) return response.writeHead(404).end();
    response.writeHead(200, { "Content-Type": contentType(candidate), "Cache-Control": "no-store" });
    response.end(await readFile(candidate));
  });
  await new Promise((resolveServer, rejectServer) => { server.once("error", rejectServer); server.listen(0, "127.0.0.1", resolveServer); });
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("Unable to create web server.");
  return { origin: `http://127.0.0.1:${address.port}`, close: () => new Promise((resolveServer, rejectServer) => server.close(error => error ? rejectServer(error) : resolveServer())) };
}
function contentType(path) { return new Map([[".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"], [".webmanifest", "application/manifest+json"], [".json", "application/json"]]).get(extname(path)) ?? "application/octet-stream"; }
