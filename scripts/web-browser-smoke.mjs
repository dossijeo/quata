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
const routeFragments = ['auth', 'feed', 'chat', 'official', 'settings', 'share-target'];

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
        expectedRoutes: routeFragments,
        completedRoutes: [],
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
                if (!closingCdp) failures.push(`Could not resolve intercepted request ${request.url}: ${error.message}`);
            });
        });
        await cdp.send('Runtime.enable');
        await cdp.send('Log.enable');
        await cdp.send('Network.enable');
        await cdp.send('Fetch.enable', { patterns: [{ urlPattern: '*', requestStage: 'Request' }] });
        await cdp.send('Page.enable');
        await cdp.send('Performance.enable');

        // The first probe is deliberately unauthenticated and therefore exercises the shared
        // Auth compose shell without requiring a Supabase instance.
        browserMetrics.navigations.push(await navigateAndAssertShell(cdp, staticServer.origin, 'auth', pageErrors));
        await assertWebTestContract(cdp, 'auth', 'auth');
        await assertUnconfiguredAuthBoundary(cdp);

        if (options.docmentis) {
            await navigateAndAssertDocmentisBridge(
                cdp,
                staticServer.origin,
                authenticatedStorage,
                docmentisPermitRequests,
            );
        }

        // No session is invented for the remaining hashes. The route contract still records the
        // requested host while the visible surface correctly stays at Auth until a real login.
        for (const fragment of routeFragments.slice(1)) {
            browserMetrics.navigations.push(await navigateAndAssertShell(cdp, staticServer.origin, fragment, pageErrors));
            // This smoke intentionally does not mint a backend session. It can verify the
            // requested hash route but remains on the Auth surface until a real login changes
            // Compose state; the authenticated surface belongs to the remote E2E runner.
            await assertWebTestContract(cdp, 'auth', fragment);
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
    console.log(`Web browser smoke passed for ${routeFragments.join(', ')}.`);
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

async function navigateAndAssertDocmentisBridge(cdp, origin, authenticatedStorage, permitRequests) {
    await cdp.send('Page.navigate', { url: `${origin}/?quata-docmentis-smoke=1#auth` });
    await waitForShell(cdp, 'auth');
    const mountProbe = await cdp.evaluate('globalThis.__quataDocmentisProbe?.mount()');
    const mountResult = mountProbe?.result?.value;
    if (
        mountResult?.package !== '@docmentis/udoc-viewer' ||
        mountResult?.clientCreated !== true ||
        mountResult?.viewerCreated !== true ||
        mountResult?.mounted !== true ||
        !mountResult?.version
    ) {
        throw new Error(`DocMentis local mount probe failed: ${JSON.stringify({ result: mountResult, exception: mountProbe?.exceptionDetails?.exception?.description ?? mountProbe?.exceptionDetails?.text })}`);
    }
    await assertDocmentisCleanup(cdp, 'local mount');

    const fixture = '/__quata-smoke-fixtures/document.pdf';
    const permitProbe = await cdp.evaluate(
        `globalThis.__quataDocmentisProbe?.expectPermitFailClosed(${JSON.stringify(fixture)})`,
    );
    const permitResult = permitProbe?.result?.value;
    if (
        permitResult?.package !== '@docmentis/udoc-viewer' ||
        permitResult?.clientCreated !== true ||
        permitResult?.blocked !== true ||
        permitResult?.phase !== 'permit' ||
        permitResult?.documentLoaded !== false ||
        !permitResult?.message?.includes('permit unavailable')
    ) {
        throw new Error(`DocMentis fail-closed permit probe failed: ${JSON.stringify({ result: permitResult, exception: permitProbe?.exceptionDetails?.exception?.description ?? permitProbe?.exceptionDetails?.text })}`);
    }
    await assertDocmentisCleanup(cdp, fixture);
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

async function assertDocmentisCleanup(cdp, fixture) {
    const cleanup = await cdp.evaluate("document.querySelector('[data-quata-docmentis-smoke]') === null");
    if (cleanup?.result?.value !== true) {
        throw new Error(`DocMentis ${fixture} probe leaked its temporary viewer host after load.`);
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

async function navigateAndAssertShell(cdp, origin, fragment, pageErrors) {
    const initialErrorCount = pageErrors.length;
    const startedAt = performance.now();
    const navigation = await cdp.send('Page.navigate', { url: `${origin}/#${fragment}` });
    await waitForShell(cdp, fragment);
    if (pageErrors.length > initialErrorCount) {
        throw new Error(`Route #${fragment} produced an uncaught browser exception.`);
    }
    return collectNavigationMetrics(cdp, fragment, performance.now() - startedAt, Boolean(navigation.loaderId));
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

async function assertWebTestContract(cdp, expectedSurface, expectedRoute) {
    let value;
    for (let attempt = 0; attempt < 30; attempt += 1) {
        const contract = await cdp.evaluate(`(() => {
        const host = document.querySelector('quata-test-contract');
        const root = host?.shadowRoot;
        const node = root?.querySelector('[data-testid="web-test-contract"]');
        return node ? {
            version: node.dataset.contractVersion,
            surface: node.dataset.surface,
            route: node.dataset.route,
            authSubmit: !!root.querySelector('[data-testid="auth-submit"]'),
            chatSend: !!root.querySelector('[data-testid="chat-send"]'),
        } : null;
        })()`);
        value = contract.result?.value;
        const observed = [value?.version, value?.surface, value?.route, value?.authSubmit, value?.chatSend];
        const expected = ['1', expectedSurface, expectedRoute, true, true];
        if (JSON.stringify(observed) === JSON.stringify(expected)) return;
        await delay(100);
    }
    const observed = [value?.version, value?.surface, value?.route, value?.authSubmit, value?.chatSend];
    const expected = ['1', expectedSurface, expectedRoute, true, true];
    throw new Error(`WEB-TEST-001 contract mismatch: ${JSON.stringify({ observed, expected })}.`);
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

async function removeChromeProfile(profileDirectory) {
    for (let attempt = 0; attempt < 5; attempt += 1) {
        try {
            await rm(profileDirectory, { recursive: true, force: true, maxRetries: 2, retryDelay: 100 });
            return;
        } catch (error) {
            if (attempt === 4) {
                // A retained Crashpad handle cannot change the browser result. Keep the path in
                // the warning so it can be cleaned later instead of converting a green smoke
                // into a false negative on Windows.
                console.warn(`Could not remove temporary Chrome profile ${profileDirectory}: ${error.message}`);
                return;
            }
            await delay(250);
        }
    }
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
