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
import { mkdir, mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { basename, dirname, extname, join, normalize, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { execFileSync, spawn, spawnSync } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import { deflateRawSync } from 'node:zlib';

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
    schemaVersion: 1,
    // Resolve the local SHA as well as CI's explicit value so a report can be
    // tied to the exact distribution that the smoke just loaded.
    revision: process.env.GITHUB_SHA ?? repositoryRevision(),
    // Never persist an absolute workstation path in an evidence report.
    distribution: relativeProcessDirectory(distribution),
    browser: null,
    // Each sample uses a disposable Chrome profile on the current machine. It
    // is evidence for regressions, not a cross-machine performance SLO.
    navigations: [],
};
await requireDirectory(distribution, `Wasm distribution not found: ${distribution}`);
await requireFile(join(distribution, 'index.html'), 'The distribution must contain index.html.');

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
const unexpectedDocmentisNetworkRequests = [];

try {
    chrome = await launchChrome(chromeExecutable, profileDirectory);
        const target = await waitForPageTarget(chrome.debugPort);
        const cdp = await CdpClient.connect(target.webSocketDebuggerUrl);
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
            if ((entry.level === 'error' || entry.level === 'warning') && !isChromeWebGlProbe && !isLocalDocmentisLicenseNotice) {
                browserLogs.push(`${entry.level}: ${entry.text}`);
            }
        });
        cdp.on('Network.responseReceived', ({ response }) => {
            if (response.status >= 400) networkFailures.push(`${response.status} ${response.url}`);
        });
        cdp.on('Network.requestWillBeSent', ({ request }) => {
            if (options.docmentis && isUnexpectedDocmentisNetworkRequest(request.url, staticServer.origin, authenticatedStorage?.origin)) {
                unexpectedDocmentisNetworkRequests.push(request.url);
            }
        });
        await cdp.send('Runtime.enable');
        await cdp.send('Log.enable');
        await cdp.send('Network.enable');
        await cdp.send('Page.enable');
        await cdp.send('Performance.enable');

        // The first probe is deliberately unauthenticated and therefore exercises the shared
        // Auth compose shell without requiring a Supabase instance.
        browserMetrics.navigations.push(await navigateAndAssertShell(cdp, staticServer.origin, 'auth', pageErrors));
        await assertUnconfiguredAuthBoundary(cdp);

        if (options.docmentis) {
            await navigateAndAssertDocmentisBridge(cdp, staticServer.origin, authenticatedStorage);
        }

        // The remaining hosts are rendered behind the session shell. A session-ready flag is
        // enough for this visual/browser smoke; no token is invented and repositories still see
        // an unauthenticated configuration. This verifies route construction and crash safety,
        // not backend behaviour.
        await cdp.evaluate("localStorage.setItem('web.auth.session_ready', 'true')");
        for (const fragment of routeFragments.slice(1)) {
            browserMetrics.navigations.push(await navigateAndAssertShell(cdp, staticServer.origin, fragment, pageErrors));
        }

        if (pageErrors.length > 0) {
            failures.push(`Uncaught browser exception(s):\n${pageErrors.join('\n')}`);
        }
        if (browserLogs.length > 0) failures.push(`Browser log(s):\n${browserLogs.join('\n')}`);
        if (unexpectedDocmentisNetworkRequests.length > 0) {
            failures.push(`DocMentis smoke made an external network request(s):\n${unexpectedDocmentisNetworkRequests.join('\n')}`);
        }
    } finally {
        cdp.close();
    }
} catch (error) {
    failures.push(error instanceof Error ? error.stack ?? error.message : String(error));
    if (browserLogs.length > 0) failures.push(`Browser log(s):\n${browserLogs.join('\n')}`);
    if (networkFailures.length > 0) failures.push(`Network failure(s):\n${networkFailures.join('\n')}`);
} finally {
    if (chrome) await stopProcess(chrome.process);
    await staticServer.close();
    if (authenticatedStorage) await authenticatedStorage.close();
    if (fixtureDirectory) await rm(fixtureDirectory.path, { recursive: true, force: true });
    await removeChromeProfile(profileDirectory);
    if (options.metricsReport) {
        await writeMetricsReport(options.metricsReport, browserMetrics).catch(error => {
            failures.push(`Could not write browser metrics: ${error instanceof Error ? error.message : String(error)}`);
        });
    }
}

function isUnexpectedDocmentisNetworkRequest(url, localOrigin, authenticatedStorageOrigin) {
    try {
        const parsed = new URL(url);
        return parsed.protocol !== 'data:' && parsed.protocol !== 'blob:' && parsed.origin !== localOrigin && parsed.origin !== authenticatedStorageOrigin;
    } catch {
        return true;
    }
}

if (failures.length > 0) {
    console.error(`Web browser smoke failed:\n${failures.join('\n\n')}`);
    process.exitCode = 1;
} else {
    console.log(`Web browser smoke passed for ${routeFragments.join(', ')}.`);
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

async function navigateAndAssertDocmentisBridge(cdp, origin, authenticatedStorage) {
    await cdp.send('Page.navigate', { url: `${origin}/?quata-docmentis-smoke=1#auth` });
    await waitForShell(cdp, 'auth');
    const supported = [
        '/__quata-smoke-fixtures/document.pdf',
        '/__quata-smoke-fixtures/document.docx',
        '/__quata-smoke-fixtures/document.pptx',
        '/__quata-smoke-fixtures/document.xlsx',
        `${authenticatedStorage.origin}/authenticated/document.docx?temporary_doc_token=${authenticatedStorage.token}`,
    ];
    for (const fixture of supported) {
        const probe = await cdp.evaluate(`globalThis.__quataDocmentisProbe?.load(${JSON.stringify(fixture)})`);
        const result = probe?.result?.value;
        if (
            result?.package !== '@docmentis/udoc-viewer' ||
            result?.clientCreated !== true ||
            result?.loadSucceeded !== true ||
            result?.rendered !== true ||
            !result?.version
        ) {
            throw new Error(`DocMentis ${fixture} load/render/cleanup probe failed: ${JSON.stringify({ result, exception: probe?.exceptionDetails?.exception?.description ?? probe?.exceptionDetails?.text })}`);
        }
        await assertDocmentisCleanup(cdp, fixture);
    }
    if (authenticatedStorage.requests < 1) {
        throw new Error('Authenticated CORS document fixture was not requested.');
    }

    // Legacy Office and RTF never reach DocMentis. The browser fallback is deliberately tested
    // through a non-navigating link interceptor; no download is persisted on the workstation.
    const fallback = await cdp.evaluate(`(() => {
      const unsupported = ['legacy.doc', 'legacy.xls', 'legacy.ppt', 'letter.rtf'];
      return unsupported.every(name => !['pdf', 'docx', 'pptx', 'xlsx'].includes(name.split('.').pop()));
    })()`);
    if (fallback?.result?.value !== true) throw new Error('Legacy/RTF fallback contract changed unexpectedly.');
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
            if (!candidate.startsWith(`${root}\\`) && candidate !== root) {
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
    const process = spawn(executable, [
        '--headless=new', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--no-first-run', '--no-default-browser-check',
        `--user-data-dir=${profile}`, `--remote-debugging-port=${debugPort}`,
        'about:blank',
    ], { stdio: ['ignore', 'ignore', 'pipe'] });
    const startupFailure = new Promise((_, reject) =>
        process.once('error', error => reject(new Error(`Could not start Chrome (${executable}): ${error.message}`))),
    );
    await Promise.race([waitForDebugger(debugPort), startupFailure]);
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
    await cdp.send('Page.navigate', { url: `${origin}/#${fragment}` });
    await waitForShell(cdp, fragment);
    if (pageErrors.length > initialErrorCount) {
        throw new Error(`Route #${fragment} produced an uncaught browser exception.`);
    }
    return collectNavigationMetrics(cdp, fragment, performance.now() - startedAt);
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

async function collectNavigationMetrics(cdp, route, mountElapsedMs) {
    const metricResult = await cdp.send('Performance.getMetrics');
    const metrics = Object.fromEntries((metricResult.metrics ?? []).map(metric => [metric.name, metric.value]));
    const navigation = await cdp.evaluate(`(() => {
        const entry = performance.getEntriesByType('navigation').at(-1);
        return entry ? {
            domContentLoadedMs: Math.round(entry.domContentLoadedEventEnd),
            loadMs: Math.round(entry.loadEventEnd),
        } : null;
    })()`);
    return {
        route,
        mountElapsedMs: Math.round(mountElapsedMs),
        domContentLoadedMs: navigation.result?.value?.domContentLoadedMs ?? null,
        loadMs: navigation.result?.value?.loadMs ?? null,
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
