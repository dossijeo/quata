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
import { mkdtemp, readFile, rm, stat } from 'node:fs/promises';
import { basename, dirname, extname, join, normalize, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { spawn, spawnSync } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';

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

await requireDirectory(distribution, `Wasm distribution not found: ${distribution}`);
await requireFile(join(distribution, 'index.html'), 'The distribution must contain index.html.');

async function runSmoke() {
const failures = [];
const staticServer = await startStaticServer(distribution);
const profileDirectory = await mkdtemp(join(tmpdir(), 'quata-web-browser-smoke-'));
let chrome;
const browserLogs = [];
const networkFailures = [];

try {
    chrome = await launchChrome(chromeExecutable, profileDirectory, staticServer.port);
    const target = await waitForPageTarget(chrome.debugPort);
    const cdp = await CdpClient.connect(target.webSocketDebuggerUrl);
    try {
        const pageErrors = [];
        cdp.on('Runtime.exceptionThrown', ({ exceptionDetails }) => {
            pageErrors.push(describeException(exceptionDetails));
        });
        cdp.on('Log.entryAdded', ({ entry }) => {
            const isChromeWebGlProbe = entry.level === 'warning' && entry.text.startsWith(
                'WebGL: INVALID_ENUM: getParameter: invalid parameter name, WEBGL_debug_renderer_info not enabled',
            );
            if ((entry.level === 'error' || entry.level === 'warning') && !isChromeWebGlProbe) {
                browserLogs.push(`${entry.level}: ${entry.text}`);
            }
        });
        cdp.on('Network.responseReceived', ({ response }) => {
            if (response.status >= 400) networkFailures.push(`${response.status} ${response.url}`);
        });
        await cdp.send('Runtime.enable');
        await cdp.send('Log.enable');
        await cdp.send('Network.enable');
        await cdp.send('Page.enable');

        // The first probe is deliberately unauthenticated and therefore exercises the shared
        // Auth compose shell without requiring a Supabase instance.
        await navigateAndAssertShell(cdp, staticServer.origin, 'auth', pageErrors);

        if (options.docmentis) {
            await navigateAndAssertDocmentisBridge(cdp, staticServer.origin);
        }

        // The remaining hosts are rendered behind the session shell. A session-ready flag is
        // enough for this visual/browser smoke; no token is invented and repositories still see
        // an unauthenticated configuration. This verifies route construction and crash safety,
        // not backend behaviour.
        await cdp.evaluate("localStorage.setItem('web.auth.session_ready', 'true')");
        for (const fragment of routeFragments.slice(1)) {
            await navigateAndAssertShell(cdp, staticServer.origin, fragment, pageErrors);
        }

        if (pageErrors.length > 0) {
            failures.push(`Uncaught browser exception(s):\n${pageErrors.join('\n')}`);
        }
        if (browserLogs.length > 0) failures.push(`Browser log(s):\n${browserLogs.join('\n')}`);
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
    await removeChromeProfile(profileDirectory);
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
        if (argument === '--dist' || argument === '--chrome') {
            const value = args[index + 1];
            if (!value || value.startsWith('--')) throw new Error(`Missing value for ${argument}.`);
            parsed[argument.slice(2)] = value;
            index += 1;
        } else if (argument === '--help' || argument === '-h') {
            console.log('Usage: node scripts/web-browser-smoke.mjs [--dist DIR] [--chrome PATH] [--docmentis]');
            process.exit(0);
        } else if (argument === '--docmentis') {
            parsed.docmentis = true;
        } else {
            throw new Error(`Unknown argument: ${argument}`);
        }
    }
    return parsed;
}

async function navigateAndAssertDocmentisBridge(cdp, origin) {
    await cdp.send('Page.navigate', { url: `${origin}/?quata-docmentis-smoke=1#auth` });
    await waitForShell(cdp, 'auth');
    const probe = await cdp.evaluate('globalThis.__quataDocmentisProbe?.()');
    const result = probe?.result?.value;
    if (result?.package !== '@docmentis/udoc-viewer' || result?.clientCreated !== true || !result?.version) {
        throw new Error(`DocMentis dynamic import/client lifecycle probe failed: ${JSON.stringify(result)}`);
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

async function startStaticServer(rootDirectory) {
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
            const candidate = resolve(root, `.${requestPath === '/' ? '/index.html' : requestPath}`);
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

async function launchChrome(executable, profile, serverPort) {
    const debugPort = await availablePort();
    const process = spawn(executable, [
        '--headless=new', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--no-first-run', '--no-default-browser-check',
        `--user-data-dir=${profile}`, `--remote-debugging-port=${debugPort}`,
        `http://127.0.0.1:${serverPort}/#auth`,
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
    for (let attempt = 0; attempt < 80; attempt += 1) {
        try {
            const response = await fetch(`http://127.0.0.1:${port}/json/version`);
            if (response.ok) return;
        } catch { /* Chrome is still starting. */ }
        await delay(100);
    }
    throw new Error('Chrome did not expose its DevTools endpoint within eight seconds.');
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
    await cdp.send('Page.navigate', { url: `${origin}/#${fragment}` });
    await waitForShell(cdp, fragment);
    if (pageErrors.length > initialErrorCount) {
        throw new Error(`Route #${fragment} produced an uncaught browser exception.`);
    }
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
