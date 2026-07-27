#!/usr/bin/env node
/**
 * Browser-level PWA Share Target contract.
 *
 * This starts the production Wasm distribution, lets Chrome register the real
 * service worker, and submits multipart requests through that worker. It does
 * not call Supabase or create a user: receiving a share must be safe before an
 * authenticated destination is selected.
 */
import { createServer as createSecureServer } from "node:https";
import { execFile as execFileCallback } from "node:child_process";
import { mkdtemp, readFile, rm, stat } from "node:fs/promises";
import { promisify } from "node:util";
import { extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { tmpdir } from "node:os";
import { chromium } from "playwright-core";
import { shareTargetNetworkDecision } from "./web-share-target-network-policy.mjs";

const distribution = resolve(process.argv[2] ?? "web/build/dist/wasmJs/productionExecutable");
const chrome = process.env.QUATA_CHROME_PATH ?? "C:/Program Files/Google/Chrome/Application/chrome.exe";
const openssl = process.env.QUATA_OPENSSL_PATH ?? "openssl";
const execFile = promisify(execFileCallback);
await requireFile(join(distribution, "index.html"));
await requireFile(join(distribution, "quata-sw.js"));
await requireFile(join(distribution, "manifest.webmanifest"));

const profile = await mkdtemp(join(tmpdir(), "quata-web-share-target-"));
const certificateDirectory = await mkdtemp(join(tmpdir(), "quata-web-share-target-tls-"));
const server = await startHttpsServer(distribution, certificateDirectory);
let context;
const unexpectedNetworkRequests = [];
let turnstileRequests = 0;
const report = {
  check: "WEB-SHARE-001",
  mode: "https_persistent_profile_service_worker_hermetic_network",
  status: "failed",
  checks: [],
};
try {
  context = await launchPersistentHttpsContext(profile);
  await configureNetworkFence(context, server, unexpectedNetworkRequests, value => { turnstileRequests += value; });
  await installAndColdStart(context, server.origin);
  await context.close();
  context = await launchPersistentHttpsContext(profile);
  const page = context.pages()[0] ?? await context.newPage();
  await configureNetworkFence(context, server, unexpectedNetworkRequests, value => { turnstileRequests += value; });
  await addShareInspectionBoundary(page);
  await page.goto(server.origin, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, undefined, { timeout: 30_000 });
  assert(await page.evaluate(() => isSecureContext), "https_must_be_a_secure_context");
  report.checks.push("https_launcher_service_worker_survives_cold_browser_restart");

  const manifest = await page.evaluate(async () => {
    const response = await fetch("/manifest.webmanifest");
    return response.json();
  });
  assert(manifest.share_target?.action === "/share-target", "manifest_must_declare_share_target");
  report.checks.push("https_launcher_has_controller_and_manifest_share_target");

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
    const entries = await globalThis.__quataIncomingShareEntries();
    const entry = entries[0] ?? null;
    return { count: entries.length, url: location.href, entry: entry && { id: entry.id, text: entry.text, name: entry.attachments?.[0]?.name, size: entry.attachments?.[0]?.blob?.size } };
  });
  report.validPayload = accepted;
  assert(accepted.url.endsWith("/#share-target"), "valid_share_must_navigate_to_share_target");
  assert(accepted.count === 1, "single_share_must_create_exactly_one_claimable_entry");
  assert(accepted.entry?.text === "A title\nHello PWA\nhttps://example.test/share", "valid_share_must_persist_normalized_text");
  assert(accepted.entry?.name === "photo.png" && accepted.entry.size === 11, "valid_share_must_persist_file_blob");
  report.checks.push("valid_multipart_share_persists_one_claimable_blob_entry_and_redirects_to_launcher");

  await page.reload({ waitUntil: "domcontentloaded" });
  const retainedAfterColdReload = await page.evaluate(async () => {
    const entries = await globalThis.__quataIncomingShareEntries();
    return { count: entries.length, id: entries[0]?.id ?? null };
  });
  assert(retainedAfterColdReload.count === 1 && retainedAfterColdReload.id === accepted.entry.id, "cold_start_must_retain_exactly_one_claimable_entry");
  await page.evaluate(id => globalThis.__quataDiscardIncomingShare(id), accepted.entry.id);
  assert(await page.evaluate(() => globalThis.__quataIncomingShareEntries().then(entries => entries.length)) === 0, "discard_must_clean_indexeddb_entry");
  report.checks.push("cold_start_retains_one_claim_and_discard_cleans_indexeddb");

  for (const invalid of ["empty", "too-many", "too-large"]) {
    const sharesBefore = await page.evaluate(() => globalThis.__quataIncomingShareEntries().then(entries => entries.length));
    await page.goto(`${server.origin}/?invalid-share=${invalid}`, { waitUntil: "domcontentloaded" });
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
  await rm(certificateDirectory, { recursive: true, force: true });
  console.log(JSON.stringify(report, null, 2));
}

async function addShareInspectionBoundary(page) {
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
    globalThis.__quataDiscardIncomingShare = async (id) => {
      const database = await new Promise((resolveDb, rejectDb) => {
        const request = indexedDB.open("quata-web", 2);
        request.onsuccess = () => resolveDb(request.result);
        request.onerror = () => rejectDb(request.error);
      });
      await new Promise((resolveDelete, rejectDelete) => {
        const transaction = database.transaction("incoming-shares", "readwrite");
        transaction.objectStore("incoming-shares").delete(id);
        transaction.oncomplete = resolveDelete;
        transaction.onerror = () => rejectDelete(transaction.error);
        transaction.onabort = () => rejectDelete(transaction.error);
      });
    };
  });
}

async function launchPersistentHttpsContext(profileDirectory) {
  return chromium.launchPersistentContext(profileDirectory, {
    executablePath: chrome,
    headless: true,
    args: [
      "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--no-first-run",
      "--ignore-certificate-errors", "--allow-insecure-localhost",
      // Context routing rejects every non-allowlisted request. The closed local proxy is an
      // independent transport backstop for service-worker traffic Chromium does not route.
      "--proxy-server=http://127.0.0.1:9",
      "--proxy-bypass-list=127.0.0.1;localhost",
    ],
  });
}

async function configureNetworkFence(context, server, unexpectedNetworkRequests, incrementTurnstile) {
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
      incrementTurnstile(1);
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
}

function assert(condition, code) { if (!condition) throw new Error(code); }
async function requireFile(path) { if (!(await stat(path).catch(() => null))?.isFile()) throw new Error(`Missing required file: ${path}`); }
async function installAndColdStart(context, origin) {
  const page = context.pages()[0] ?? await context.newPage();
  await page.goto(origin, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => navigator.serviceWorker?.ready, undefined, { timeout: 30_000 });
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, undefined, { timeout: 30_000 });
  assert(await page.evaluate(() => isSecureContext), "https_must_be_a_secure_context");
}
async function startHttpsServer(rootDirectory, tlsDirectory) {
  const key = join(tlsDirectory, "localhost-key.pem");
  const certificate = join(tlsDirectory, "localhost-cert.pem");
  const pfx = join(tlsDirectory, "localhost.pfx");
  let tlsOptions;
  try {
    await execFile(openssl, ["req", "-x509", "-newkey", "rsa:2048", "-sha256", "-nodes", "-keyout", key, "-out", certificate, "-days", "1", "-subj", "/CN=127.0.0.1", "-addext", "subjectAltName=IP:127.0.0.1,DNS:localhost"]);
    tlsOptions = { key: await readFile(key), cert: await readFile(certificate) };
  } catch (error) {
    if (process.platform !== "win32") throw new Error(`Unable to generate the ephemeral HTTPS certificate with ${openssl}: ${error.message}`);
    await generateWindowsEphemeralPfx(pfx);
    tlsOptions = { pfx: await readFile(pfx), passphrase: "quata-ephemeral" };
  }
  const root = resolve(rootDirectory);
  const server = createSecureServer(tlsOptions, async (request, response) => {
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
  return { origin: `https://127.0.0.1:${address.port}`, close: () => new Promise((resolveServer, rejectServer) => server.close(error => error ? rejectServer(error) : resolveServer())) };
}
async function generateWindowsEphemeralPfx(pfx) {
  const escapedPfx = pfx.replaceAll("'", "''");
  const command = [
    "$ErrorActionPreference = 'Stop'",
    "$certificate = New-SelfSignedCertificate -DnsName 'localhost', '127.0.0.1' -CertStoreLocation 'Cert:\\CurrentUser\\My'",
    "try {",
    "  $password = ConvertTo-SecureString -String 'quata-ephemeral' -AsPlainText -Force",
    `  Export-PfxCertificate -Cert $certificate -FilePath '${escapedPfx}' -Password $password | Out-Null`,
    "} finally {",
    "  Remove-Item -Path ('Cert:\\CurrentUser\\My\\' + $certificate.Thumbprint) -Force",
    "}",
  ].join("; ");
  try {
    await execFile("powershell.exe", ["-NoProfile", "-NonInteractive", "-Command", command], { windowsHide: true });
  } catch (error) {
    throw new Error(`Unable to generate an ephemeral Windows HTTPS certificate: ${error.message}`);
  }
}
function contentType(path) { return new Map([[".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"], [".webmanifest", "application/manifest+json"], [".json", "application/json"]]).get(extname(path)) ?? "application/octet-stream"; }
