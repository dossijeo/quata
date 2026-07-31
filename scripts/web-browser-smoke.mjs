#!/usr/bin/env node
/**
 * Minimal browser smoke test for the already-built Compose/Wasm launcher.
 *
 * It intentionally has no npm dependency: the repository already targets browser Chrome for
 * Kotlin/Wasm and Windows developer machines provide Chrome with Android Studio. The process
 * starts a temporary static server and a disposable headless Chrome profile, then drives the
 * page through Chrome DevTools Protocol (CDP).
 *
 * Build the distribution first, for example:
 *   .\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
 *   node scripts/web-browser-smoke.mjs
 */
import { createServer } from 'node:http';
import { mkdir, mkdtemp, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { basename, dirname, extname, isAbsolute, join, normalize, relative, resolve } from 'node:path';
import { cpus, release, tmpdir, totalmem } from 'node:os';
import { execFileSync, spawn, spawnSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
import { deflateRawSync } from 'node:zlib';
import {
    classifyBrowserRequest,
    validateDocmentisPermitRequest,
} from './web-browser-network-policy.mjs';
import { isInvalidatedFetchInterceptionError } from './web-browser-smoke-interception.mjs';
import { removeChromeProfile } from './web-browser-smoke-cleanup.mjs';
import { SMOKE_ROUTE_CONTRACTS } from './web-browser-route-contract.mjs';

// Node 20 exposes the standards-compatible client behind this flag. Re-exec automatically so
// callers only need `node scripts/web-browser-smoke.mjs`; Node versions that expose WebSocket by
// default skip this branch.
if (typeof WebSocket === 'undefined' && process.env.QUATA_WEB_SMOKE_WEBSOCKET !== 'enabled') {
    const rerun = spawnSync(
        process.execPath,
        ['--experimental-websocket', process.argv[1], ...process.argv.slice(2)],
        { stdio: 'inherit', env: { ...process.env, QUATA_WEB_SMOKE_WEBSOCKET: 'enabled' } },
    );
    process.exit(rerun.status ?? 1);
}

const defaultDistribution = 'web/build/dist/wasmJs/productionExecutable';
const defaultChrome = process.platform === 'win32'
    ? 'C:/Program Files/Google/Chrome/Application/chrome.exe'
    : 'google-chrome';
// One exhaustive unauthenticated product-route contract. Private routes first resolve to the
// public Feed plus Android's common participation gate; only the gate action opens Auth.
const routeContracts = SMOKE_ROUTE_CONTRACTS;
// These routes remain public and safe to inspect without manufacturing a session.
const publicDeepLinks = [
    { fragment: 'post-publication-123', route: 'post/publication-123' },
    { fragment: 'official-bulletin-99', route: 'official/bulletin-99' },
    { fragment: 'unknown-route', route: 'feed' },
];
// Chat owns private conversations: preserve its requested fragment while the common participation
// gate remains over public Feed, then exercise the exact Login callback bound by Compose.
const privateDeepLinks = [
    { fragment: 'chat-sb%3Ateam%2F42?message=msg%209', returnRoute: 'chat/sb:team/42' },
];
const responsiveViewports = [
    { name: 'mobile', width: 360, height: 800 },
    { name: 'tablet', width: 768, height: 1024 },
    { name: 'desktop', width: 1440, height: 900 },
];

const options = parseArguments(process.argv.slice(2));
const distribution = resolve(options.dist ?? defaultDistribution);
const chromeExecutable = options.chrome ?? defaultChrome;
const browserMetrics = {
    schemaVersion: 2,
    sampleId: randomUUID(),
    generatedAt: new Date().toISOString(),
    // Resolve the local SHA as well as CI's explicit value so a report can be
    // tied to the exact distribution that the smoke just loaded.
    revision: process.env.GITHUB_SHA ?? repositoryRevision(),
    // Never persist an absolute workstation path in an evidence report.
    distribution: relativeProcessDirectory(distribution),
    distributionFingerprintSha256: null,
    browser: null,
    environment: measurementEnvironment(),
    smoke: {
        status: 'running',
        expectedRoutes: routeContracts.map(({ fragment }) => fragment),
        completedRoutes: [],
    },
    publicUx: {
        deepLinks: [],
        privateDeepLinks: [],
        viewports: [],
        compactKeyboard: null,
        reloadRecovered: false,
        keyboardAndAx: null,
        pushConsent: { mode: 'authenticated_required', skipped: 'settings_is_private' },
    },
    advisories: {
        chromeGpuReadPixelsWarnings: 0,
    },
    // Each sample uses a disposable Chrome profile on the current machine. It
    // is evidence for regressions, not a cross-machine performance SLO.
    navigations: [],
};
await requireDirectory(distribution, `Wasm distribution not found: ${distribution}`);
await requireFile(join(distribution, 'index.html'), 'The distribution must contain index.html.');
browserMetrics.distributionFingerprintSha256 = await fingerprintDirectory(distribution);

async function runSmoke() {
const failures = [];
const fixtureDirectory = options.docmentis
    ? await createDocmentisFixtures()
    : null;
const staticServer = await startStaticServer(distribution, fixtureDirectory?.sameOriginFiles ?? new Map());
const authenticatedStorage = fixtureDirectory
    ? await startAuthenticatedFixtureStorage(fixtureDirectory.crossOriginFiles, staticServer.origin)
    : null;
const profileDirectory = await mkdtemp(join(tmpdir(), 'quata-web-browser-smoke-'));
let chrome;
const browserLogs = [];
const networkFailures = [];
const unexpectedNetworkRequests = [];
const turnstileRequests = [];
const docmentisPermitRequests = [];

try {
    chrome = await launchChrome(chromeExecutable, profileDirectory);
        const target = await waitForPageTarget(chrome.debugPort);
        const cdp = await CdpClient.connect(target.webSocketDebuggerUrl);
        let closingCdp = false;
        try {
        const version = await cdp.send('Browser.getVersion');
        browserMetrics.browser = {
            product: version.product ?? null,
            userAgent: version.userAgent ?? null,
            jsVersion: version.jsVersion ?? null,
        };
        const pageErrors = [];
        cdp.on('Runtime.exceptionThrown', ({ exceptionDetails }) => {
            pageErrors.push(describeException(exceptionDetails));
        });
        cdp.on('Log.entryAdded', ({ entry }) => {
            const isChromeWebGlProbe = entry.level === 'warning' && entry.text.startsWith(
                'WebGL: INVALID_ENUM: getParameter: invalid parameter name, WEBGL_debug_renderer_info not enabled',
            );
            // The SDK emits this notice even on localhost, where its own message says free
            // development usage is exempt from verification. The network assertion below proves
            // that this smoke did not contact an external service.
            const isLocalDocmentisLicenseNotice = entry.level === 'warning' && entry.text.startsWith(
                '[@docMentis/udoc-viewer] This document is opened with free/unlicensed docMentis usage.',
            );
            const isChromeGpuReadPixelsPerformanceWarning = entry.level === 'warning' && /^\[\.WebGL-0x[0-9a-f]+\]GL Driver Message \(OpenGL, Performance, GL_CLOSE_PATH_NV, High\): GPU stall due to ReadPixels(?: \(this message will no longer repeat\))?$/i.test(entry.text);
            if (isChromeGpuReadPixelsPerformanceWarning) {
                browserMetrics.advisories.chromeGpuReadPixelsWarnings += 1;
            } else if ((entry.level === 'error' || entry.level === 'warning') && !isChromeWebGlProbe && !isLocalDocmentisLicenseNotice) {
                browserLogs.push(`${entry.level}: ${entry.text}`);
            }
        });
        cdp.on('Network.responseReceived', ({ response }) => {
            if (response.status >= 400) networkFailures.push(`${response.status} ${response.url}`);
        });
        cdp.on('Fetch.requestPaused', ({ requestId, request }) => {
            let command;
            const requestKind = classifyBrowserRequest(
                request,
                staticServer.origin,
                authenticatedStorage?.origin,
            );
            if (requestKind === 'turnstile-bootstrap') {
                turnstileRequests.push(request);
                // Registration is deliberately unconfigured in this smoke, so Turnstile is not
                // exercised. Fulfil its unconditional index.html script tag locally to keep the
                // six-route launcher contract independent from Cloudflare and the public network.
                command = cdp.send('Fetch.fulfillRequest', {
                    requestId,
                    responseCode: 200,
                    responseHeaders: [{ name: 'Content-Type', value: 'text/javascript; charset=utf-8' }],
                    body: Buffer.from('globalThis.turnstile = globalThis.turnstile ?? {};').toString('base64'),
                });
            } else if (requestKind === 'docmentis-permit-preflight' && options.docmentis) {
                docmentisPermitRequests.push(request);
                command = cdp.send('Fetch.fulfillRequest', {
                    requestId,
                    responseCode: 204,
                    responseHeaders: [
                        { name: 'Access-Control-Allow-Origin', value: staticServer.origin },
                        { name: 'Access-Control-Allow-Methods', value: 'POST, OPTIONS' },
                        { name: 'Access-Control-Allow-Headers', value: 'content-type' },
                    ],
                });
            } else if (requestKind === 'docmentis-permit' && options.docmentis) {
                docmentisPermitRequests.push(request);
                const permitContractFailures = validateDocmentisPermitRequest(request.postData);
                if (permitContractFailures.length > 0) {
                    failures.push(`DocMentis permit request contract changed:\n${permitContractFailures.join('\n')}`);
                }
                // SDK 0.7.9 verifies the short-lived permit signature inside Wasm. The repository
                // does not possess the vendor signing key, so the hermetic gate exercises the
                // documented unavailable/fail-closed path instead of fabricating a permit.
                command = cdp.send('Fetch.failRequest', { requestId, errorReason: 'BlockedByClient' });
            } else if (requestKind === 'unexpected') {
                unexpectedNetworkRequests.push(`${request.method} ${request.url}`);
                command = cdp.send('Fetch.failRequest', { requestId, errorReason: 'BlockedByClient' });
            } else {
                command = cdp.send('Fetch.continueRequest', { requestId });
            }
            command.catch(error => {
                if (!closingCdp && !isInvalidatedFetchInterceptionError(error)) {
                    failures.push(`Could not resolve intercepted request ${request.url}: ${error.message}`);
                }
            });
        });
        await cdp.send('Runtime.enable');
        await cdp.send('Log.enable');
        await cdp.send('Network.enable');
        await cdp.send('Fetch.enable', { patterns: [{ urlPattern: '*', requestStage: 'Request' }] });
        await cdp.send('Page.enable');
        await cdp.send('Performance.enable');

        for (const contract of routeContracts) {
            if (contract.kind === 'public') {
                browserMetrics.navigations.push(await navigateAndAssertPublicShell(cdp, staticServer.origin, contract, pageErrors));
            } else if (contract.kind === 'auth') {
                browserMetrics.navigations.push(await navigateAndAssertAuthBoundary(cdp, staticServer.origin, contract, pageErrors));
            } else {
                browserMetrics.navigations.push(await navigateAndAssertPrivateAuthBoundary(cdp, staticServer.origin, contract, pageErrors));
            }
        }

        await assertUnauthenticatedDeepLinkRecovery(cdp, staticServer.origin, pageErrors);
        browserMetrics.publicUx.deepLinks = publicDeepLinks.map(({ fragment, route }) => ({ fragment, route }));
        browserMetrics.publicUx.privateDeepLinks = privateDeepLinks.map(({ fragment, returnRoute }) => ({ fragment, returnRoute }));
        browserMetrics.publicUx.reloadRecovered = true;
        const responsiveUx = await assertResponsiveAuthShell(cdp, staticServer.origin, pageErrors);
        browserMetrics.publicUx.viewports = responsiveUx.viewports;
        browserMetrics.publicUx.compactKeyboard = responsiveUx.compactKeyboard;
        browserMetrics.publicUx.keyboardAndAx = responsiveUx.nativeControlsPresent
            ? await assertKeyboardAndAccessibility(cdp)
            : { mode: 'compose_canvas', nativeControlsPresent: false };
        // Settings is deliberately private. This anonymous route-matrix smoke must not forge a
        // session merely to click its push-consent control; that gesture belongs to the
        // authenticated product journey.

        // Keep the measured six-route series on one stable base document. DocMentis opts into its
        // localhost-only product bridge with a query parameter, so it runs after route metrics
        // rather than turning the following #feed transition into a full-document navigation.
        if (options.docmentis) {
            await navigateAndAssertDocmentisBridge(
                cdp,
                staticServer.origin,
                authenticatedStorage,
                docmentisPermitRequests,
            );
        }
        assertTurnstileBootstrapFlow(turnstileRequests);

        if (pageErrors.length > 0) {
            failures.push(`Uncaught browser exception(s):\n${pageErrors.join('\n')}`);
        }
        if (browserLogs.length > 0) failures.push(`Browser log(s):\n${browserLogs.join('\n')}`);
        if (networkFailures.length > 0) {
            failures.push(`Network failure(s):\n${networkFailures.join('\n')}`);
        }
        if (unexpectedNetworkRequests.length > 0) {
            failures.push(`Smoke made an external network request(s):\n${unexpectedNetworkRequests.join('\n')}`);
        }
    } finally {
        closingCdp = true;
        await cdp.send('Fetch.disable').catch(() => undefined);
        cdp.close();
    }
} catch (error) {
    failures.push(error instanceof Error ? error.stack ?? error.message : String(error));
    if (browserLogs.length > 0) failures.push(`Browser log(s):\n${browserLogs.join('\n')}`);
    if (networkFailures.length > 0) failures.push(`Network failure(s):\n${networkFailures.join('\n')}`);
    if (unexpectedNetworkRequests.length > 0) {
        failures.push(`Smoke made an external network request(s):\n${unexpectedNetworkRequests.join('\n')}`);
    }
} finally {
    if (chrome) await stopProcess(chrome.process);
    await staticServer.close();
    if (authenticatedStorage) await authenticatedStorage.close();
    if (fixtureDirectory) await rm(fixtureDirectory.path, { recursive: true, force: true });
    await removeChromeProfile(profileDirectory);
    browserMetrics.smoke.completedRoutes = browserMetrics.navigations.map(({ route }) => route);
    browserMetrics.smoke.status = failures.length === 0 ? 'passed' : 'failed';
    if (options.metricsReport) {
        await writeMetricsReport(options.metricsReport, browserMetrics).catch(error => {
            failures.push(`Could not write browser metrics: ${error instanceof Error ? error.message : String(error)}`);
        });
    }
}

if (failures.length > 0) {
    console.error(`Web browser smoke failed:\n${failures.join('\n\n')}`);
    process.exitCode = 1;
} else {
    console.log(`Web browser smoke passed for ${routeContracts.map(({ fragment }) => fragment).join(', ')}.`);
    console.log(`Advisory Chrome GPU ReadPixels warnings: ${browserMetrics.advisories.chromeGpuReadPixelsWarnings}.`);
}
}

function parseArguments(args) {
    const parsed = {};
    for (let index = 0; index < args.length; index += 1) {
        const argument = args[index];
        if (argument === '--dist' || argument === '--chrome' || argument === '--metrics-report') {
            const value = args[index + 1];
            if (!value || value.startsWith('--')) throw new Error(`Missing value for ${argument}.`);
            parsed[argument.slice(2).replace(/-([a-z])/g, (_, letter) => letter.toUpperCase())] = value;
            index += 1;
        } else if (argument === '--help' || argument === '-h') {
            console.log('Usage: node scripts/web-browser-smoke.mjs [--dist DIR] [--chrome PATH] [--docmentis] [--metrics-report PATH]');
            process.exit(0);
        } else if (argument === '--docmentis') {
            parsed.docmentis = true;
        } else {
            throw new Error(`Unknown argument: ${argument}`);
        }
    }
    return parsed;
}

async function navigateAndAssertDocmentisBridge(cdp, origin, _authenticatedStorage, permitRequests) {
    await cdp.send('Page.navigate', { url: `${origin}/?quata-docmentis-smoke=1#auth` });
    await waitForShell(cdp, 'auth');
    const fixture = '/__quata-smoke-fixtures/document';
    await cdp.evaluate(`(() => {
      const trace = globalThis.__quataDocmentisProductTrace = {
        mounted: false,
        removed: false,
        fallbacks: [],
        originalOpen: globalThis.open,
      };
      const containsViewer = node => node?.nodeType === 1 && (
        node.matches?.('[data-quata-docmentis-viewer]') ||
        node.querySelector?.('[data-quata-docmentis-viewer]')
      );
      trace.observer = new MutationObserver(records => {
        for (const record of records) {
          if ([...record.addedNodes].some(containsViewer)) trace.mounted = true;
          if ([...record.removedNodes].some(containsViewer)) trace.removed = true;
        }
      });
      trace.observer.observe(document.documentElement, { childList: true, subtree: true });
      globalThis.open = url => {
        trace.fallbacks.push(String(url));
        return { closed: false };
      };
    })()`);
    const productProbe = await cdp.evaluate(
        `globalThis.__quataDocmentisProductProbe?.open({
          reference: ${JSON.stringify(fixture)},
          displayName: 'permit-smoke',
          mimeType: 'application/pdf',
        })`,
    );
    const productResult = productProbe?.result?.value;
    const traceProbe = await cdp.evaluate(`(() => {
      const trace = globalThis.__quataDocmentisProductTrace;
      trace?.observer?.disconnect();
      if (trace?.originalOpen) globalThis.open = trace.originalOpen;
      return trace ? {
        mounted: trace.mounted,
        removed: trace.removed,
        fallbacks: trace.fallbacks,
        activeOverlay: document.querySelector('[data-quata-docmentis-viewer]') !== null,
      } : null;
    })()`);
    const trace = traceProbe?.result?.value;
    if (
        productResult?.state !== 'success' ||
        productResult?.reason !== null ||
        trace?.mounted !== true ||
        trace?.removed !== true ||
        trace?.activeOverlay !== false ||
        trace?.fallbacks?.length !== 1 ||
        trace.fallbacks[0] !== `${origin}${fixture}`
    ) {
        throw new Error(`DocMentis product open/fail-closed/fallback probe failed: ${JSON.stringify({
            result: productResult,
            trace,
            exception: productProbe?.exceptionDetails?.exception?.description ?? productProbe?.exceptionDetails?.text,
        })}`);
    }
    assertDocmentisPermitFlow(permitRequests);

    // Legacy Office and RTF never reach DocMentis. The browser fallback is deliberately tested
    // through a non-navigating link interceptor; no download is persisted on the workstation.
    const fallback = await cdp.evaluate(`(() => {
      const unsupported = ['legacy.doc', 'legacy.xls', 'legacy.ppt', 'letter.rtf'];
      return unsupported.every(name => !['pdf', 'docx', 'pptx', 'xlsx'].includes(name.split('.').pop()));
    })()`);
    if (fallback?.result?.value !== true) throw new Error('Legacy/RTF fallback contract changed unexpectedly.');
}

function assertTurnstileBootstrapFlow(requests) {
    if (requests.length < 1) {
        throw new Error('Expected the exact local Turnstile bootstrap fixture, observed none.');
    }
}

function assertDocmentisPermitFlow(requests) {
    const methods = requests.map(({ method }) => method);
    if (methods.length !== 2 || methods[0] !== 'OPTIONS' || methods[1] !== 'POST') {
        throw new Error(`DocMentis permit flow changed: ${methods.join(', ') || 'no requests'}.`);
    }
    const contractFailures = validateDocmentisPermitRequest(requests[1].postData);
    if (contractFailures.length > 0) {
        throw new Error(`DocMentis permit payload changed:\n${contractFailures.join('\n')}`);
    }
}

async function requireDirectory(path, message) {
    const information = await stat(path).catch(() => null);
    if (!information?.isDirectory()) throw new Error(message);
}

async function requireFile(path, message) {
    const information = await stat(path).catch(() => null);
    if (!information?.isFile()) throw new Error(message);
}

/**
 * Generates harmless PDF/DOCX fixtures at runtime and stages the versioned inert PPTX/XLSX
 * fixtures. Nothing is uploaded or served outside the two loopback servers used by this smoke.
 * This keeps the test reproducible without bundling sample user documents or downloading samples.
 */
async function createDocmentisFixtures() {
    const path = await mkdtemp(join(tmpdir(), 'quata-docmentis-fixtures-'));
    const sameOriginFiles = new Map();
    const crossOriginFiles = new Map();
    const files = {
        'document.pdf': createMinimalPdf(),
        'document.docx': createZip({
            '[Content_Types].xml': xml`<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`,
            '_rels/.rels': xml`<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>`,
            'word/document.xml': xml`<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Quata DocMentis smoke DOCX</w:t></w:r></w:p><w:sectPr/></w:body></w:document>`,
        }),
        'legacy.doc': Buffer.from('Quata legacy DOC fallback fixture\n'),
        'legacy.xls': Buffer.from('Quata legacy XLS fallback fixture\n'),
        'legacy.ppt': Buffer.from('Quata legacy PPT fallback fixture\n'),
        'letter.rtf': Buffer.from('{\\rtf1\\ansi Quata RTF fallback fixture}'),
    };
    // PPTX/XLSX need the full OOXML relationship graph. These tiny, versioned fixtures contain
    // only inert smoke text and are recompressed without directory entries for determinism.
    files['document.pptx'] = await readFile(new URL('./fixtures/docmentis/smoke.pptx', import.meta.url));
    files['document.xlsx'] = await readFile(new URL('./fixtures/docmentis/smoke.xlsx', import.meta.url));
    for (const [name, contents] of Object.entries(files)) {
        const file = join(path, name);
        await writeFile(file, contents);
        sameOriginFiles.set(`/__quata-smoke-fixtures/${name}`, file);
        crossOriginFiles.set(`/authenticated/${name}`, file);
    }
    sameOriginFiles.set('/__quata-smoke-fixtures/document', sameOriginFiles.get('/__quata-smoke-fixtures/document.pdf'));
    return { path, sameOriginFiles, crossOriginFiles };
}

async function startAuthenticatedFixtureStorage(files, allowedOrigin) {
    const token = `quata-local-${Math.random().toString(36).slice(2)}`;
    let requests = 0;
    const server = createServer(async (request, response) => {
        const requestUrl = new URL(request.url ?? '/', 'http://localhost');
        const fixture = files.get(requestUrl.pathname);
        if (
            !fixture ||
            requestUrl.searchParams.get('temporary_doc_token') !== token ||
            request.headers.origin !== allowedOrigin
        ) {
            response.writeHead(403).end();
            return;
        }
        // This models a signed, authenticated Storage URL without using a real project, user
        // session, bucket, or durable credential. The browser may fetch it only from this smoke.
        requests += 1;
        response.writeHead(200, {
            'Content-Type': contentType(fixture),
            'Access-Control-Allow-Origin': allowedOrigin,
            'Vary': 'Origin',
            'Cross-Origin-Resource-Policy': 'cross-origin',
            'Cache-Control': 'no-store',
        });
        response.end(await readFile(fixture));
    });
    await new Promise((resolveServer, rejectServer) => {
        server.once('error', rejectServer);
        server.listen(0, '127.0.0.1', resolveServer);
    });
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Could not start authenticated fixture storage.');
    return {
        origin: `http://127.0.0.1:${address.port}`,
        token,
        get requests() { return requests; },
        close: () => new Promise((resolveServer, rejectServer) => server.close(error => error ? rejectServer(error) : resolveServer())),
    };
}

const xml = (source) => Buffer.from(source, 'utf8');

function createMinimalPdf() {
    const objects = [
        '<< /Type /Catalog /Pages 2 0 R >>',
        '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>',
    ];
    let source = '%PDF-1.4\n';
    const offsets = [0];
    for (const [index, object] of objects.entries()) {
        offsets.push(Buffer.byteLength(source, 'ascii'));
        source += `${index + 1} 0 obj\n${object}\nendobj\n`;
    }
    const xrefOffset = Buffer.byteLength(source, 'ascii');
    source += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
    source += offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
    source += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
    return Buffer.from(source, 'ascii');
}

function createZip(entries) {
    const parts = [];
    const directory = [];
    let offset = 0;
    for (const [name, source] of Object.entries(entries)) {
        const nameBytes = Buffer.from(name, 'utf8');
        const body = Buffer.isBuffer(source) ? source : Buffer.from(source);
        const compressed = deflateRawSync(body);
        const crc = crc32(body);
        const header = Buffer.alloc(30);
        header.writeUInt32LE(0x04034b50, 0); header.writeUInt16LE(20, 4); header.writeUInt16LE(0, 6);
        header.writeUInt16LE(8, 8); header.writeUInt16LE(0, 10); header.writeUInt16LE(0, 12);
        header.writeUInt32LE(crc, 14); header.writeUInt32LE(compressed.length, 18); header.writeUInt32LE(body.length, 22);
        header.writeUInt16LE(nameBytes.length, 26); header.writeUInt16LE(0, 28);
        parts.push(header, nameBytes, compressed);
        directory.push({ nameBytes, crc, compressedSize: compressed.length, size: body.length, offset });
        offset += header.length + nameBytes.length + compressed.length;
    }
    const directoryStart = offset;
    for (const entry of directory) {
        const header = Buffer.alloc(46);
        header.writeUInt32LE(0x02014b50, 0); header.writeUInt16LE(20, 4); header.writeUInt16LE(20, 6);
        header.writeUInt16LE(0, 8); header.writeUInt16LE(8, 10); header.writeUInt16LE(0, 12); header.writeUInt16LE(0, 14);
        header.writeUInt32LE(entry.crc, 16); header.writeUInt32LE(entry.compressedSize, 20); header.writeUInt32LE(entry.size, 24);
        header.writeUInt16LE(entry.nameBytes.length, 28); header.writeUInt16LE(0, 30); header.writeUInt16LE(0, 32);
        header.writeUInt16LE(0, 34); header.writeUInt16LE(0, 36); header.writeUInt32LE(0, 38); header.writeUInt32LE(entry.offset, 42);
        parts.push(header, entry.nameBytes); offset += header.length + entry.nameBytes.length;
    }
    const end = Buffer.alloc(22);
    end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(0, 4); end.writeUInt16LE(0, 6);
    end.writeUInt16LE(directory.length, 8); end.writeUInt16LE(directory.length, 10);
    end.writeUInt32LE(offset - directoryStart, 12); end.writeUInt32LE(directoryStart, 16); end.writeUInt16LE(0, 20);
    return Buffer.concat([...parts, end]);
}

function crc32(buffer) {
    let value = 0xffffffff;
    for (const byte of buffer) {
        value ^= byte;
        for (let bit = 0; bit < 8; bit += 1) value = (value >>> 1) ^ ((value & 1) ? 0xedb88320 : 0);
    }
    return (value ^ 0xffffffff) >>> 0;
}

async function startStaticServer(rootDirectory, extraFiles) {
    const root = resolve(rootDirectory);
    const server = createServer(async (request, response) => {
        try {
            const requestPath = decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname);
            // Chrome requests a favicon even though the Kotlin/Wasm distribution does not ship
            // one. It is unrelated to launcher health, so make that implicit browser probe a
            // successful empty response instead of turning the smoke into a false negative.
            if (requestPath === '/favicon.ico') {
                response.writeHead(204).end();
                return;
            }
            const fixture = extraFiles.get(requestPath);
            const candidate = fixture ?? resolve(root, `.${requestPath === '/' ? '/index.html' : requestPath}`);
            if (fixture) {
                // Fixture routes are an explicit immutable allowlist. Do not turn this server
                // into a general filesystem endpoint merely to support an integration smoke.
                const information = await stat(candidate).catch(() => null);
                if (!information?.isFile()) {
                    response.writeHead(404).end();
                    return;
                }
                const bytes = await readFile(candidate);
                response.writeHead(200, {
                    'Content-Type': contentType(candidate),
                    'Cross-Origin-Opener-Policy': 'same-origin',
                    'Cross-Origin-Embedder-Policy': 'require-corp',
                    'Cache-Control': 'no-store',
                });
                response.end(bytes);
                return;
            }
            const relativeCandidate = relative(root, candidate);
            if (relativeCandidate === '..' || relativeCandidate.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`) || isAbsolute(relativeCandidate)) {
                response.writeHead(403).end();
                return;
            }
            const information = await stat(candidate).catch(() => null);
            if (!information?.isFile()) {
                response.writeHead(404).end();
                return;
            }
            const bytes = await readFile(candidate);
            response.writeHead(200, {
                'Content-Type': contentType(candidate),
                'Cross-Origin-Opener-Policy': 'same-origin',
                'Cross-Origin-Embedder-Policy': 'require-corp',
                'Cache-Control': 'no-store',
            });
            response.end(bytes);
        } catch (error) {
            response.writeHead(500).end(String(error));
        }
    });
    await new Promise((resolveServer, rejectServer) => {
        server.once('error', rejectServer);
        server.listen(0, '127.0.0.1', resolveServer);
    });
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Could not resolve the temporary web server port.');
    return {
        port: address.port,
        origin: `http://127.0.0.1:${address.port}`,
        close: () => new Promise((resolveServer, rejectServer) => server.close(error => error ? rejectServer(error) : resolveServer())),
    };
}

function contentType(path) {
    return new Map([
        ['.html', 'text/html; charset=utf-8'], ['.js', 'text/javascript; charset=utf-8'],
        ['.mjs', 'text/javascript; charset=utf-8'], ['.wasm', 'application/wasm'],
        ['.json', 'application/json; charset=utf-8'], ['.css', 'text/css; charset=utf-8'],
        ['.svg', 'image/svg+xml'], ['.png', 'image/png'], ['.webp', 'image/webp'],
    ]).get(extname(path).toLowerCase()) ?? 'application/octet-stream';
}

async function launchChrome(executable, profile) {
    const debugPort = await availablePort();
    const chromeStderr = [];
    const process = spawn(executable, [
        '--headless=new', '--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu',
        '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--no-first-run', '--no-default-browser-check',
        `--user-data-dir=${profile}`, '--remote-debugging-address=127.0.0.1', `--remote-debugging-port=${debugPort}`,
        'about:blank',
    ], { stdio: ['ignore', 'ignore', 'pipe'] });
    process.stderr.setEncoding('utf8');
    process.stderr.on('data', chunk => chromeStderr.push(chunk));
    const startupFailure = new Promise((_, reject) =>
        process.once('error', error => reject(new Error(`Could not start Chrome (${executable}): ${error.message}`))),
    );
    const prematureExit = new Promise((_, reject) =>
        process.once('exit', (code, signal) => reject(new Error(`Chrome exited before exposing DevTools (code=${code ?? 'null'}, signal=${signal ?? 'none'}).`))),
    );
    try {
        await Promise.race([waitForDebugger(debugPort), startupFailure, prematureExit]);
    } catch (error) {
        await stopProcess(process);
        const stderr = chromeStderr.join('').trim();
        throw new Error(`${error instanceof Error ? error.message : String(error)}${stderr ? ` Chrome stderr: ${stderr}` : ''}`);
    }
    return { process, debugPort };
}

async function availablePort() {
    const probe = createServer();
    await new Promise((resolveServer, rejectServer) => {
        probe.once('error', rejectServer);
        probe.listen(0, '127.0.0.1', resolveServer);
    });
    const address = probe.address();
    await new Promise(resolveServer => probe.close(resolveServer));
    if (!address || typeof address === 'string') throw new Error('Could not reserve a Chrome debugger port.');
    return address.port;
}

async function waitForDebugger(port) {
    for (let attempt = 0; attempt < 200; attempt += 1) {
        try {
            const response = await fetch(`http://127.0.0.1:${port}/json/version`);
            if (response.ok) return;
        } catch { /* Chrome is still starting. */ }
        await delay(100);
    }
    throw new Error('Chrome did not expose its DevTools endpoint within twenty seconds.');
}

async function waitForPageTarget(port) {
    for (let attempt = 0; attempt < 80; attempt += 1) {
        const response = await fetch(`http://127.0.0.1:${port}/json/list`);
        const targets = await response.json();
        const page = targets.find(target => target.type === 'page' && target.webSocketDebuggerUrl);
        if (page) return page;
        await delay(100);
    }
    throw new Error('Chrome did not create a page target.');
}

async function navigateAndAssertPublicShell(cdp, origin, contract, pageErrors) {
    const initialErrorCount = pageErrors.length;
    const startedAt = performance.now();
    // Keep the opt-in query stable for the complete route matrix so every transition is a
    // same-document hash navigation. Separate public deep-link recovery below remains query-free.
    const navigation = await cdp.send('Page.navigate', {
        url: `${origin}/?quata-auth-e2e=1#${contract.fragment}`,
    });
    await waitForShell(cdp, contract.fragment);
    await waitForNavigationRoute(cdp, contract.route);
    await waitForShellMarker(cdp, contract.route);
    if (pageErrors.length > initialErrorCount) {
        throw new Error(`Public route #${contract.fragment} produced an uncaught browser exception.`);
    }
    return collectNavigationMetrics(cdp, contract.fragment, performance.now() - startedAt, Boolean(navigation.loaderId));
}

async function navigateAndAssertAuthBoundary(cdp, origin, contract, pageErrors) {
    const initialErrorCount = pageErrors.length;
    const startedAt = performance.now();
    const navigation = await cdp.send('Page.navigate', {
        url: `${origin}/?quata-auth-e2e=1#${contract.fragment}`,
    });
    await waitForShell(cdp, contract.fragment);
    await waitForNavigationRoute(cdp, contract.route);
    await assertShellHidden(cdp, contract.fragment);
    await assertUnconfiguredAuthBoundary(cdp);
    if (pageErrors.length > initialErrorCount) throw new Error(`Auth route #${contract.fragment} produced an uncaught browser exception.`);
    return collectNavigationMetrics(cdp, contract.fragment, performance.now() - startedAt, Boolean(navigation.loaderId));
}

async function navigateAndAssertPrivateAuthBoundary(cdp, origin, contract, pageErrors) {
    const initialErrorCount = pageErrors.length;
    const startedAt = performance.now();
    const navigation = await cdp.send('Page.navigate', {
        url: `${origin}/?quata-auth-e2e=1#${contract.fragment}`,
    });
    await waitForPrivateDeepLinkAuthBoundary(cdp, contract.fragment, contract.returnRoute);
    if (pageErrors.length > initialErrorCount) throw new Error(`Private route #${contract.fragment} produced an uncaught browser exception.`);
    return collectNavigationMetrics(cdp, contract.fragment, performance.now() - startedAt, Boolean(navigation.loaderId));
}

async function assertUnconfiguredAuthBoundary(cdp) {
    let value;
    for (let attempt = 0; attempt < 30; attempt += 1) {
        const boundary = await cdp.evaluate(`(() => ({
            urlMeta: document.querySelector('meta[name="quata-supabase-url"]')?.getAttribute('content')?.trim() ?? null,
            publishableKeyMeta: document.querySelector('meta[name="quata-supabase-publishable-key"]')?.getAttribute('content')?.trim() ?? null,
            backendConfigured: localStorage.getItem('web.runtime.backend_configured'),
        }))()`);
        value = boundary?.result?.value;
        if (value?.backendConfigured !== null && value?.backendConfigured !== undefined) break;
        await delay(100);
    }
    if (value?.urlMeta || value?.publishableKeyMeta || value?.backendConfigured !== 'false') {
        throw new Error(`Unauthenticated smoke must remain an unconfigured runtime boundary, got ${JSON.stringify(value)}.`);
    }
}

/**
 * Real DOM/CDP gate for WEB-PUSH-CONSENT-001. CDP's mouse event is trusted, unlike element.click(),
 * so the mock observes the same transient user activation required by Safari and Firefox.
 */
async function assertPushConsentUsesTrustedSettingsClick(cdp, origin) {
    await cdp.evaluate(`(async () => {
        localStorage.setItem('web.auth.session_ready', 'true');
        localStorage.setItem('quata_web_access_token', 'browser-smoke-access');
        localStorage.setItem('quata_web_refresh_token', '');
        localStorage.setItem('quata_web_session_token', 'browser-smoke-session');
        localStorage.setItem('quata_web_user_id', 'browser-smoke-user');
        localStorage.setItem('quata_web_expires_at', String(Math.floor(Date.now() / 1000) + 3600));
        localStorage.setItem('web.push.consent.v1', 'disabled');
        globalThis.__quataPushPermissionProbe = null;
        Object.defineProperty(globalThis, 'Notification', {
          configurable: true,
          value: {
            permission: 'default',
            requestPermission: () => {
              globalThis.__quataPushPermissionProbe = {
                active: globalThis.navigator?.userActivation?.isActive === true,
              };
              return Promise.resolve('denied');
            },
          },
        });
        const source = await fetch('/index.html').then(response => response.text());
        const configured = source
          .replace('name="quata-supabase-url" content=""', 'name="quata-supabase-url" content="https://push-smoke.invalid"')
          .replace('name="quata-supabase-publishable-key" content=""', 'name="quata-supabase-publishable-key" content="public-smoke-key"');
        history.replaceState(null, '', '/#settings');
        document.open();
        document.write(configured);
        document.close();
    })()`);

    let control;
    for (let attempt = 0; attempt < 100; attempt += 1) {
        control = (await cdp.evaluate(`(() => {
          const button = [...(document.querySelector('#quata-root')?.shadowRoot?.querySelectorAll('button') ?? [])]
            .find(candidate => candidate.getAttribute('aria-label') === 'Activar notificaciones');
          if (!button) return null;
          const rect = button.getBoundingClientRect();
          return {
            tagName: button.tagName,
            ariaLabel: button.getAttribute('aria-label'),
            nativeRole: button.getAttribute('role') ?? 'button',
            x: rect.left + rect.width / 2,
            y: rect.top + rect.height / 2,
            visible: rect.width > 0 && rect.height > 0,
          };
        })()`))?.result?.value;
        if (control?.visible) break;
        await delay(100);
    }
    if (!control?.visible || control.tagName !== 'BUTTON' || control.nativeRole !== 'button') {
        throw new Error(`Push consent Settings control is not an accessible native HTML button: ${JSON.stringify(control)}.`);
    }
    await cdp.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: control.x, y: control.y, button: 'left', clickCount: 1 });
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: control.x, y: control.y, button: 'left', clickCount: 1 });
    let probe;
    for (let attempt = 0; attempt < 30; attempt += 1) {
        probe = (await cdp.evaluate(`globalThis.__quataPushPermissionProbe`))?.result?.value;
        if (probe) break;
        await delay(50);
    }
    if (probe?.active !== true) {
        throw new Error(`Notification.requestPermission did not start inside trusted Settings activation: ${JSON.stringify(probe)}.`);
    }
    const persisted = (await cdp.evaluate(`localStorage.getItem('web.push.consent.v1')`))?.result?.value;
    if (persisted !== 'disabled') {
        throw new Error(`Denied permission must preserve disabled consent, got ${persisted ?? 'null'}.`);
    }
}

async function assertUnauthenticatedDeepLinkRecovery(cdp, origin, pageErrors) {
    for (const { fragment, route } of publicDeepLinks) {
        const initialErrorCount = pageErrors.length;
        await cdp.send('Page.navigate', { url: `${origin}/#${fragment}` });
        await waitForShell(cdp, fragment);
        await waitForNavigationRoute(cdp, route);
        if (pageErrors.length > initialErrorCount) {
            throw new Error(`Public deep link #${fragment} produced an uncaught browser exception.`);
        }
    }

    const { fragment, route } = publicDeepLinks.at(-1);
    await cdp.send('Page.reload', { ignoreCache: true });
    await waitForShell(cdp, fragment);
    const recoveredHash = await cdp.evaluate(`location.hash`);
    if (recoveredHash?.result?.value !== `#${fragment}`) {
        throw new Error(`Public deep-link reload changed location.hash: expected #${fragment}, got ${recoveredHash?.result?.value ?? 'null'}.`);
    }
    await waitForNavigationRoute(cdp, route);
    await assertUnconfiguredAuthBoundary(cdp);

    for (const { fragment, returnRoute } of privateDeepLinks) {
        const initialErrorCount = pageErrors.length;
        await cdp.send('Page.navigate', { url: `${origin}/?quata-auth-e2e=1#${fragment}` });
        await waitForPrivateDeepLinkAuthBoundary(cdp, fragment, returnRoute);
        if (pageErrors.length > initialErrorCount) {
            throw new Error(`Private deep link #${fragment} produced an uncaught browser exception.`);
        }
    }
}

async function waitForPrivateDeepLinkAuthBoundary(cdp, fragment, returnRoute) {
    let lastProbe = null;
    for (let attempt = 0; attempt < 120; attempt += 1) {
        const probe = await cdp.evaluate(`(() => ({
            hash: location.hash,
            route: localStorage.getItem('web.navigation.route'),
            shellRoute: document.documentElement.getAttribute('data-quata-shell-route'),
            prompt: document.documentElement.getAttribute('data-quata-auth-required-prompt'),
            pendingRoute: document.documentElement.getAttribute('data-quata-auth-pending-route'),
            gateBridgeVersion: globalThis.__quataAuthGateE2eProduct?.version ?? null,
        }))()`);
        lastProbe = probe?.result?.value ?? null;
        if (
            lastProbe?.hash === '' &&
            lastProbe.route === 'feed' &&
            lastProbe.shellRoute === 'feed' &&
            lastProbe.prompt === 'visible' &&
            lastProbe.pendingRoute === fragment &&
            lastProbe.gateBridgeVersion === 1
        ) break;
        await delay(100);
    }
    if (
        lastProbe?.hash !== '' ||
        lastProbe.route !== 'feed' ||
        lastProbe.shellRoute !== 'feed' ||
        lastProbe.prompt !== 'visible' ||
        lastProbe.pendingRoute !== fragment ||
        lastProbe.gateBridgeVersion !== 1
    ) {
        throw new Error(`Private deep link #${fragment} did not reach public Feed with the common participation gate for return route ${returnRoute}: ${JSON.stringify(lastProbe)}.`);
    }

    const action = await cdp.evaluate(`(() => {
      const bridge = globalThis.__quataAuthGateE2eProduct;
      if (bridge?.version !== 1 || typeof bridge.chooseLogin !== 'function') return false;
      bridge.chooseLogin();
      return true;
    })()`);
    if (action?.result?.value !== true) {
        throw new Error(`Private deep link #${fragment} could not invoke the real Compose Login callback.`);
    }

    for (let attempt = 0; attempt < 60; attempt += 1) {
        const auth = await cdp.evaluate(`(() => ({
          hash: location.hash,
          route: localStorage.getItem('web.navigation.route'),
          shellRoute: document.documentElement.getAttribute('data-quata-shell-route'),
          prompt: document.documentElement.getAttribute('data-quata-auth-required-prompt'),
          destination: document.documentElement.getAttribute('data-quata-auth-destination'),
        }))()`);
        const value = auth?.result?.value ?? null;
        if (
            value?.hash === '#auth' &&
            value.route === 'auth' &&
            !value.shellRoute &&
            !value.prompt &&
            value.destination === 'login'
        ) return;
        lastProbe = value;
        await delay(100);
    }
    throw new Error(`Private deep link #${fragment} did not open shell-free full-screen Login after its real gate callback: ${JSON.stringify(lastProbe)}.`);
}

async function waitForShellMarker(cdp, expectedRoute) {
    let lastMarker = null;
    for (let attempt = 0; attempt < 60; attempt += 1) {
        const probe = await cdp.evaluate(`document.documentElement.getAttribute('data-quata-shell-route')`);
        lastMarker = probe?.result?.value ?? null;
        if (lastMarker === expectedRoute) return;
        await delay(100);
    }
    throw new Error(`Public route shell marker did not resolve to ${expectedRoute}, got ${lastMarker ?? 'null'}.`);
}

async function assertShellHidden(cdp, fragment) {
    const marker = (await cdp.evaluate(`document.documentElement.getAttribute('data-quata-shell-route')`))?.result?.value ?? null;
    if (marker) throw new Error(`Auth route #${fragment} unexpectedly retained shell marker ${marker}.`);
}

async function waitForNavigationRoute(cdp, expectedRoute) {
    let lastRoute = null;
    for (let attempt = 0; attempt < 60; attempt += 1) {
        const probe = await cdp.evaluate(`localStorage.getItem('web.navigation.route')`);
        lastRoute = probe?.result?.value ?? null;
        if (lastRoute === expectedRoute) return;
        await delay(100);
    }
    throw new Error(`Public route did not recover its canonical navigation state (${expectedRoute}), got ${lastRoute ?? 'null'}.`);
}

async function assertResponsiveAuthShell(cdp, origin, pageErrors) {
    const observations = [];
    try {
        for (const viewport of responsiveViewports) {
            await cdp.send('Emulation.setDeviceMetricsOverride', {
                width: viewport.width,
                height: viewport.height,
                deviceScaleFactor: 1,
                mobile: false,
            });
            const initialErrorCount = pageErrors.length;
            await cdp.send('Page.navigate', { url: `${origin}/#auth` });
            await waitForShell(cdp, 'auth');
            const layout = await waitForStableResponsiveLayout(cdp, viewport);
            if (pageErrors.length > initialErrorCount) {
                throw new Error(`Responsive ${viewport.name} auth shell produced an uncaught browser exception.`);
            }
            observations.push({
                ...viewport,
                renderer: layout.renderer,
                controls: layout.controls.length,
                stableSamples: layout.stableSamples,
            });
        }

        const nativeControlsPresent = observations.every(({ renderer }) => renderer === 'native_controls');
        if (!nativeControlsPresent) {
            return {
                viewports: observations,
                compactKeyboard: { mode: 'compose_canvas', skipped: 'native_controls_absent' },
                nativeControlsPresent: false,
            };
        }

        const compactViewport = { name: 'compact-keyboard', width: 360, height: 320 };
        await cdp.send('Emulation.setDeviceMetricsOverride', {
            width: compactViewport.width,
            height: compactViewport.height,
            deviceScaleFactor: 1,
            mobile: false,
        });
        const initialErrorCount = pageErrors.length;
        await cdp.send('Page.navigate', { url: `${origin}/#auth` });
        await waitForShell(cdp, 'auth');
        const compactKeyboard = await assertCompactKeyboardReachability(cdp, compactViewport);
        if (pageErrors.length > initialErrorCount) {
            throw new Error('Compact keyboard Auth shell produced an uncaught browser exception.');
        }
        return { viewports: observations, compactKeyboard, nativeControlsPresent: true };
    } finally {
        await cdp.send('Emulation.clearDeviceMetricsOverride');
    }
}

async function waitForStableResponsiveLayout(cdp, viewport) {
    const requiredStableSamples = 3;
    let stableSamples = 0;
    let previous = null;
    const recent = [];
    for (let attempt = 0; attempt < 120; attempt += 1) {
        const layout = await cdp.evaluate(`new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => {
            const root = document.querySelector('#quata-root');
            const controls = [...(root?.shadowRoot?.querySelectorAll('input[aria-label], button[aria-label]') ?? [])].map(element => {
                const rect = element.getBoundingClientRect();
                return { tag: element.tagName, label: element.getAttribute('aria-label'), type: element.getAttribute('type'), disabled: element.disabled, x: rect.x, y: rect.y, width: rect.width, height: rect.height };
            });
            const canvases = [...(root?.querySelectorAll('canvas') ?? []), ...(root?.shadowRoot?.querySelectorAll('canvas') ?? [])].map(canvas => {
                const rect = canvas.getBoundingClientRect();
                return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
            });
            resolve({
                clientWidth: document.documentElement.clientWidth,
                clientHeight: document.documentElement.clientHeight,
                scrollWidth: document.documentElement.scrollWidth,
                scrollHeight: document.documentElement.scrollHeight,
                root: root ? { width: root.getBoundingClientRect().width, height: root.getBoundingClientRect().height } : null,
                controls,
                canvases,
            });
        })))`);
        const value = layout?.result?.value;
        const visibleControls = value?.controls?.filter(control => control.width > 0 && control.height > 0) ?? [];
        const inputs = visibleControls.filter(control => control.tag === 'INPUT');
        const buttons = visibleControls.filter(control => control.tag === 'BUTTON');
        const canvasMatchesViewport = value?.canvases?.some(canvas =>
            canvas.x >= -1 && canvas.y >= -1 &&
            canvas.width >= viewport.width - 1 && canvas.height >= viewport.height - 1,
        ) === true;
        const allControlsFit = visibleControls.every(control =>
            control.x >= -1 && control.y >= -1 &&
            control.x + control.width <= viewport.width + 1 &&
            control.y + control.height <= viewport.height + 1,
        );
        const stableShell =
            value?.clientWidth !== viewport.width ||
            value?.clientHeight !== viewport.height ||
            value?.scrollWidth !== viewport.width ||
            value?.scrollHeight !== viewport.height ||
            !value.root || value.root.width < viewport.width - 1 || value.root.height < viewport.height - 1;
        const nativeControls = visibleControls.length > 0;
        const invalid = stableShell || (nativeControls
            ? inputs.length < 2 || !inputs.some(control => control.type === 'password') || buttons.length < 1 ||
                !visibleControls.every(control => typeof control.label === 'string' && control.label.trim().length > 0) ||
                !allControlsFit
            : !canvasMatchesViewport);
        const renderer = nativeControls ? 'native_controls' : 'compose_canvas';
        const current = {
            ...value,
            controls: visibleControls,
            renderer,
        };
        recent.push(current);
        if (recent.length > 5) recent.shift();
        if (!invalid && responsiveLayoutsEquivalent(previous, current)) stableSamples += 1;
        else stableSamples = invalid ? 0 : 1;
        previous = current;
        if (stableSamples >= requiredStableSamples) {
            return { ...current, stableSamples };
        }
        await delay(50);
    }
    throw new Error(`Responsive ${viewport.name} Auth layout did not become valid and stable: ${JSON.stringify(recent)}.`);
}

function responsiveLayoutsEquivalent(left, right) {
    if (!left || !right || left.renderer !== right.renderer || left.controls.length !== right.controls.length || left.canvases.length !== right.canvases.length) return false;
    const scalarKeys = ['clientWidth', 'clientHeight', 'scrollWidth', 'scrollHeight'];
    if (scalarKeys.some(key => left[key] !== right[key])) return false;
    if (Math.abs(left.root.width - right.root.width) > 0.5 || Math.abs(left.root.height - right.root.height) > 0.5) return false;
    const sameControls = left.controls.every((control, index) => {
        const other = right.controls[index];
        return control.tag === other.tag && control.type === other.type && control.label === other.label &&
            ['x', 'y', 'width', 'height'].every(key => Math.abs(control[key] - other[key]) <= 0.5);
    });
    return sameControls && left.canvases.every((canvas, index) => {
        const other = right.canvases[index];
        return ['x', 'y', 'width', 'height'].every(key => Math.abs(canvas[key] - other[key]) <= 0.5);
    });
}

async function assertCompactKeyboardReachability(cdp, viewport) {
    const password = await focusAndRevealCompactControl(cdp, 'password');
    if (!password.active || !compactControlFits(password, viewport) || password.pageScrollY !== 0) {
        throw new Error(`Compact password control is not keyboard-visible without page scrolling: ${JSON.stringify(password)}.`);
    }
    await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9, nativeVirtualKeyCode: 9 });
    await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9, nativeVirtualKeyCode: 9 });
    const submit = await focusAndRevealCompactControl(cdp, 'submit', false);
    if (!submit.active || !compactControlFits(submit, viewport) || submit.pageScrollY !== 0) {
        throw new Error(`Compact submit control is not keyboard-visible without page scrolling: ${JSON.stringify(submit)}.`);
    }
    return { ...viewport, password, submit };
}

async function focusAndRevealCompactControl(cdp, kind, focus = true) {
    let last = null;
    for (let attempt = 0; attempt < 60; attempt += 1) {
        const probe = await cdp.evaluate(`new Promise(resolve => {
            const root = document.querySelector('#quata-root');
            const app = root?.shadowRoot;
            const candidates = kind => kind === 'password'
                ? [...(app?.querySelectorAll('input[aria-label]') ?? [])].filter(input => input.type === 'password')
                : [...(app?.querySelectorAll('button[aria-label]') ?? [])];
            const control = candidates(${JSON.stringify(kind)}).find(element => {
                const rect = element.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0;
            });
            if (${focus ? 'true' : 'false'}) control?.focus();
            control?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
            requestAnimationFrame(() => requestAnimationFrame(() => {
                const rect = control?.getBoundingClientRect();
                resolve({
                    active: app?.activeElement === control,
                    tag: control?.tagName ?? null,
                    label: control?.getAttribute('aria-label') ?? null,
                    rect: rect ? { x: rect.x, y: rect.y, width: rect.width, height: rect.height } : null,
                    pageScrollY: globalThis.scrollY,
                    rootScrollTop: root?.scrollTop ?? null,
                });
            }));
        })`);
        last = probe?.result?.value;
        if (last?.active && last.rect?.width > 0 && last.rect?.height > 0) return last;
        await delay(50);
    }
    throw new Error(`Compact ${kind} control did not become focusable: ${JSON.stringify(last)}.`);
}

function compactControlFits(observation, viewport) {
    const rect = observation?.rect;
    return rect &&
        rect.x >= -1 && rect.y >= -1 &&
        rect.x + rect.width <= viewport.width + 1 &&
        rect.y + rect.height <= viewport.height + 1;
}

async function assertKeyboardAndAccessibility(cdp) {
    const controlsProbe = await cdp.evaluate(`(() => {
        const app = document.querySelector('#quata-root')?.shadowRoot;
        const isVisible = element => {
            const rect = element.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0;
        };
        const inputs = [...(app?.querySelectorAll('input[aria-label]') ?? [])].filter(isVisible);
        const phone = inputs.find(input => input.type !== 'password');
        const password = inputs.find(input => input.type === 'password');
        const submit = [...(app?.querySelectorAll('button[aria-label]') ?? [])].find(isVisible);
        const describe = element => element && ({
            tag: element.tagName,
            type: element.getAttribute('type'),
            label: element.getAttribute('aria-label'),
            name: element.getAttribute('name'),
        });
        return { phone: describe(phone), password: describe(password), submit: describe(submit) };
    })()`);
    const controls = controlsProbe?.result?.value;
    if (
        controls?.phone?.tag !== 'INPUT' || controls.phone.type === 'password' ||
        controls?.password?.tag !== 'INPUT' || controls.password.type !== 'password' ||
        controls?.submit?.tag !== 'BUTTON' ||
        ![controls.phone, controls.password, controls.submit].every(control => control.label?.trim())
    ) {
        throw new Error(`Visible native Auth control identity/labels are invalid: ${JSON.stringify(controls)}.`);
    }

    await focusExactAuthControl(cdp, 'phone');

    const pressTab = async () => {
        await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9, nativeVirtualKeyCode: 9 });
        await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9, nativeVirtualKeyCode: 9 });
    };
    await pressTab();
    await waitForExactAuthFocus(cdp, 'password', 'Keyboard Tab did not move focus from the phone/user input to the password input.');
    await pressTab();
    await waitForExactAuthFocus(cdp, 'submit', 'Keyboard Tab did not move focus from the password input to the submit button.');

    const focused = await cdp.evaluate(`(() => {
        const active = document.querySelector('#quata-root')?.shadowRoot?.activeElement;
        return active ? { tag: active.tagName, label: active.getAttribute('aria-label') } : null;
    })()`);
    const focus = focused?.result?.value;
    if (focus?.tag !== 'BUTTON' || focus.label !== controls.submit.label) throw new Error(`Submit focus identity changed unexpectedly: ${JSON.stringify(focus)}.`);

    const identities = [
        ['phone', 'INPUT', controls.phone.type, 'textbox', controls.phone.label],
        ['password', 'INPUT', 'password', 'textbox', controls.password.label],
        ['submit', 'BUTTON', null, 'button', controls.submit.label],
    ];
    const axControls = [];
    for (const [kind, tagName, type, expectedRole, expectedName] of identities) {
        const axNode = await readExactNativeAxNode(cdp, { kind, tagName, type, expectedRole, expectedName });
        const name = axNode.name.value;
        axControls.push({ kind, role: axNode.role.value, name });
    }
    return { tabFocus: focus, controls, axControls };
}

async function waitForExactAuthFocus(cdp, kind, failureMessage) {
    let stableSamples = 0;
    let last = null;
    for (let attempt = 0; attempt < 60; attempt += 1) {
        const probe = await cdp.evaluate(`new Promise(resolve => requestAnimationFrame(() => {
            const root = document.querySelector('#quata-root');
            const app = root?.shadowRoot;
            const kind = ${JSON.stringify(kind)};
            const expected = kind === 'phone'
                ? [...(app?.querySelectorAll('input[aria-label]') ?? [])].find(input => input.type !== 'password' && input.getBoundingClientRect().width > 0 && input.getBoundingClientRect().height > 0)
                : kind === 'password'
                    ? [...(app?.querySelectorAll('input[aria-label]') ?? [])].find(input => input.type === 'password' && input.getBoundingClientRect().width > 0 && input.getBoundingClientRect().height > 0)
                    : [...(app?.querySelectorAll('button[aria-label]') ?? [])].find(button => button.getBoundingClientRect().width > 0 && button.getBoundingClientRect().height > 0);
            const active = app?.activeElement;
            resolve({
                matches: active === expected,
                active: active ? { tag: active.tagName, label: active.getAttribute('aria-label') } : null,
                expected: expected ? { tag: expected.tagName, label: expected.getAttribute('aria-label') } : null,
            });
        }))`);
        last = probe?.result?.value;
        stableSamples = last?.matches ? stableSamples + 1 : 0;
        if (stableSamples >= 2) return last;
        await delay(50);
    }
    throw new Error(`${failureMessage} Last focus: ${JSON.stringify(last)}.`);
}

async function focusExactAuthControl(cdp, kind) {
    let last = null;
    let stableSamples = 0;
    for (let attempt = 0; attempt < 60; attempt += 1) {
        const probe = await cdp.evaluate(`new Promise(resolve => requestAnimationFrame(() => {
            const root = document.querySelector('#quata-root');
            const app = root?.shadowRoot;
            const kind = ${JSON.stringify(kind)};
            const control = kind === 'phone'
                ? [...(app?.querySelectorAll('input[aria-label]') ?? [])].find(input => input.type !== 'password' && input.getBoundingClientRect().width > 0 && input.getBoundingClientRect().height > 0)
                : [...(app?.querySelectorAll('input[aria-label]') ?? [])].find(input => input.type === 'password' && input.getBoundingClientRect().width > 0 && input.getBoundingClientRect().height > 0);
            if (app?.activeElement !== control) control?.focus();
            resolve({
                matches: app?.activeElement === control,
                active: app?.activeElement ? { tag: app.activeElement.tagName, label: app.activeElement.getAttribute('aria-label') } : null,
                control: control ? { tag: control.tagName, label: control.getAttribute('aria-label') } : null,
            });
        }))`);
        last = probe?.result?.value;
        stableSamples = last?.matches ? stableSamples + 1 : 0;
        if (stableSamples >= 3) return last;
        await delay(50);
    }
    throw new Error(`Native Auth ${kind} control cannot retain keyboard focus: ${JSON.stringify(last)}.`);
}

async function readExactNativeAxNode(cdp, identity) {
    let last = null;
    for (let attempt = 0; attempt < 30; attempt += 1) {
        const documentRoot = await cdp.send('DOM.getDocument', { depth: -1, pierce: true });
        const node = findPiercedDomNode(documentRoot.root, candidate => {
            const attributes = domNodeAttributes(candidate);
            return candidate.nodeName === identity.tagName &&
                attributes.get('aria-label') === identity.expectedName &&
                (identity.type === null || attributes.get('type') === identity.type);
        });
        if (!node?.nodeId) {
            last = 'missing_pierced_dom_node';
            await delay(50);
            continue;
        }
        try {
            const partial = await cdp.send('Accessibility.getPartialAXTree', { nodeId: node.nodeId, fetchRelatives: false });
            const axNode = (partial.nodes ?? []).find(candidate => candidate.backendDOMNodeId === node.backendNodeId);
            last = axNode ?? 'missing_associated_ax_node';
            const name = axNode?.name?.value;
            if (
                axNode?.role?.value === identity.expectedRole &&
                typeof name === 'string' && name.trim() && name === identity.expectedName
            ) return axNode;
            // A stable but incorrect role/name is a product failure, not a transient render.
            if (axNode) break;
        } catch (error) {
            if (!/Could not find node with given id/i.test(error instanceof Error ? error.message : String(error))) throw error;
            last = 'dom_node_replaced_before_ax_query';
        }
        await delay(50);
    }
    throw new Error(`Native Auth ${identity.kind} AX role/name mismatch: ${JSON.stringify({
        expectedRole: identity.expectedRole,
        expectedName: identity.expectedName,
        last,
    })}.`);
}

function domNodeAttributes(node) {
    const attributes = new Map();
    for (let index = 0; index < (node?.attributes?.length ?? 0); index += 2) {
        attributes.set(node.attributes[index], node.attributes[index + 1]);
    }
    return attributes;
}

function findPiercedDomNode(node, predicate) {
    if (!node) return null;
    if (predicate(node)) return node;
    for (const child of [
        ...(node.children ?? []),
        ...(node.shadowRoots ?? []),
        ...(node.pseudoElements ?? []),
        ...(node.contentDocument ? [node.contentDocument] : []),
    ]) {
        const match = findPiercedDomNode(child, predicate);
        if (match) return match;
    }
    return null;
}

async function collectNavigationMetrics(cdp, route, mountElapsedMs, fullDocumentNavigation) {
    const metricResult = await cdp.send('Performance.getMetrics');
    const metrics = Object.fromEntries((metricResult.metrics ?? []).map(metric => [metric.name, metric.value]));
    const documentLifecycle = fullDocumentNavigation ? await cdp.evaluate(`(() => {
        const entry = performance.getEntriesByType('navigation').at(-1);
        return entry ? {
            domContentLoadedMs: Math.round(entry.domContentLoadedEventEnd),
            loadMs: Math.round(entry.loadEventEnd),
        } : null;
    })()`) : null;
    return {
        route,
        navigationKind: fullDocumentNavigation ? 'full-document' : 'same-document-hash',
        mountElapsedMs: Math.round(mountElapsedMs),
        documentLifecycle: documentLifecycle?.result?.value ?? null,
        memory: {
            jsHeapUsedSize: finiteOrNull(metrics.JSHeapUsedSize),
            jsHeapTotalSize: finiteOrNull(metrics.JSHeapTotalSize),
            processPrivateMemory: finiteOrNull(metrics.ProcessPrivateMemory),
        },
    };
}

function finiteOrNull(value) {
    return Number.isFinite(value) ? Math.round(value) : null;
}

async function writeMetricsReport(path, report) {
    const output = resolve(path);
    await mkdir(dirname(output), { recursive: true });
    await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
    console.log(`Browser metrics: ${output}`);
}

function repositoryRevision() {
    try {
        return execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    } catch {
        return null;
    }
}

function relativeProcessDirectory(path) {
    const workingDirectory = resolve('.');
    const normalized = path.startsWith(workingDirectory) ? path.slice(workingDirectory.length).replace(/^[\\/]+/, '') : path;
    return normalized.replaceAll('\\', '/');
}

async function fingerprintDirectory(root) {
    const hash = createHash('sha256');
    async function visit(directory) {
        const entries = await readdir(directory, { withFileTypes: true });
        entries.sort((left, right) => left.name.localeCompare(right.name, 'en'));
        for (const entry of entries) {
            const absolute = join(directory, entry.name);
            const relative = absolute.slice(root.length).replaceAll('\\', '/');
            if (entry.isDirectory()) {
                await visit(absolute);
            } else if (entry.isFile()) {
                hash.update(relative);
                hash.update('\0');
                hash.update(await readFile(absolute));
                hash.update('\0');
            }
        }
    }
    await visit(root);
    return hash.digest('hex');
}

function measurementEnvironment() {
    const processor = cpus()[0];
    return {
        platform: process.platform,
        architecture: process.arch,
        osRelease: release(),
        node: process.version,
        cpuModel: processor?.model?.trim() || null,
        logicalCpuCount: cpus().length,
        totalMemoryBytes: totalmem(),
        ci: process.env.GITHUB_ACTIONS === 'true' ? 'github-actions' : process.env.CI ? 'generic' : 'local',
        runnerOs: process.env.RUNNER_OS ?? null,
        runnerArchitecture: process.env.RUNNER_ARCH ?? null,
    };
}

async function waitForShell(cdp, fragment) {
    let lastProbe;
    for (let attempt = 0; attempt < 120; attempt += 1) {
        const probe = await cdp.evaluate(`(() => ({
            hash: location.hash,
            root: document.querySelector('#quata-root'),
            childCount: document.querySelector('#quata-root')?.childElementCount ?? 0,
            canvasCount: document.querySelectorAll('#quata-root canvas').length,
            allCanvasCount: document.querySelectorAll('canvas').length,
            shadowChildCount: document.querySelector('#quata-root')?.shadowRoot?.childElementCount ?? 0,
            shadowCanvasCount: document.querySelector('#quata-root')?.shadowRoot?.querySelectorAll('canvas').length ?? 0,
            rootHtml: document.querySelector('#quata-root')?.innerHTML ?? null,
            launcherState: document.documentElement.dataset.quataLauncher ?? null,
            bundleType: typeof globalThis.web,
            bundleThen: typeof globalThis.web?.then,
            bundleKeys: globalThis.web && typeof globalThis.web === 'object' ? Object.keys(globalThis.web) : [],
            resources: performance.getEntriesByType('resource').map(({ name, initiatorType, duration }) => ({
                name,
                initiatorType,
                duration: Math.round(duration),
            })),
        }))()`);
        const value = probe?.result?.value;
        lastProbe = value;
        if (value?.hash === `#${fragment}` && value.root && (
            value.childCount > 0 || value.canvasCount > 0 ||
            value.shadowChildCount > 0 || value.shadowCanvasCount > 0
        )) return;
        await delay(100);
    }
    throw new Error(`The Compose shell did not mount for route #${fragment} within 12 seconds. Last probe: ${JSON.stringify(lastProbe)}`);
}

function describeException(details) {
    const message = details.exception?.description ?? details.text ?? 'Unknown browser exception';
    return `${message}${details.url ? ` (${details.url}:${details.lineNumber ?? 0})` : ''}`;
}

async function stopProcess(child) {
    if (process.platform === 'win32' && child.pid) {
        const taskkill = spawn('taskkill', ['/pid', String(child.pid), '/t', '/f'], { stdio: 'ignore' });
        await new Promise(resolveProcess => taskkill.once('exit', resolveProcess));
        return;
    }
    if (child.exitCode !== null) return;
    child.kill();
    await Promise.race([new Promise(resolveProcess => child.once('exit', resolveProcess)), delay(3_000)]);
}

class CdpClient {
    static async connect(url) {
        const socket = new WebSocket(url);
        await new Promise((resolveSocket, rejectSocket) => {
            socket.addEventListener('open', resolveSocket, { once: true });
            socket.addEventListener('error', () => rejectSocket(new Error('Could not connect to Chrome DevTools Protocol.')), { once: true });
        });
        return new CdpClient(socket);
    }

    constructor(socket) {
        this.socket = socket;
        this.nextId = 1;
        this.pending = new Map();
        this.listeners = new Map();
        socket.addEventListener('message', event => this.receive(JSON.parse(event.data)));
        socket.addEventListener('close', () => this.rejectPending(new Error('Chrome DevTools connection closed.')));
    }

    send(method, params = {}) {
        const id = this.nextId++;
        const result = new Promise((resolveCommand, rejectCommand) => this.pending.set(id, { resolve: resolveCommand, reject: rejectCommand }));
        this.socket.send(JSON.stringify({ id, method, params }));
        return result;
    }

    evaluate(expression) {
        return this.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    }

    on(method, listener) {
        const listeners = this.listeners.get(method) ?? [];
        listeners.push(listener);
        this.listeners.set(method, listeners);
    }

    receive(message) {
        if (message.id) {
            const pending = this.pending.get(message.id);
            if (!pending) return;
            this.pending.delete(message.id);
            if (message.error) pending.reject(new Error(`${message.error.message}: ${message.error.data ?? ''}`));
            else pending.resolve(message.result);
            return;
        }
        for (const listener of this.listeners.get(message.method) ?? []) listener(message.params ?? {});
    }

    rejectPending(error) {
        for (const { reject } of this.pending.values()) reject(error);
        this.pending.clear();
    }

    close() { this.socket.close(); }
}

await runSmoke();
