#!/usr/bin/env node
/**
 * Authenticated browser E2E for the already-built Compose/Wasm launcher.
 *
 * It authenticates an explicitly isolated account through the same public Web bridge, serves a
 * throw-away copy of the distribution with its public runtime metadata, restores the exact
 * localStorage shape used by WebAuthRepository, and verifies an authenticated PostgREST request
 * from Chrome. Credentials and tokens stay in memory and never enter the JSON report.
 */
import { createServer } from 'node:http';
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { dirname, extname, join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { spawn, spawnSync } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';

if (typeof WebSocket === 'undefined' && process.env.QUATA_WEB_E2E_WEBSOCKET !== 'enabled') {
    const child = spawnSync(process.execPath, ['--experimental-websocket', process.argv[1], ...process.argv.slice(2)], {
        stdio: 'inherit', env: { ...process.env, QUATA_WEB_E2E_WEBSOCKET: 'enabled' },
    });
    process.exit(child.status ?? 1);
}

const requiredEnvironment = [
    'QUATA_SUPABASE_URL', 'QUATA_SUPABASE_PUBLISHABLE_KEY',
    'QUATA_E2E_COUNTRY_CODE', 'QUATA_E2E_PHONE', 'QUATA_E2E_PASSWORD',
];
const defaultDistribution = 'web/build/dist/wasmJs/productionExecutable';
const defaultChrome = process.platform === 'win32'
    ? 'C:/Program Files/Google/Chrome/Application/chrome.exe'
    : 'google-chrome';

const options = parseArguments(process.argv.slice(2));
const startedAt = new Date().toISOString();
const report = {
    check: 'WEB-AUTH-BROWSER-01',
    status: 'failed',
    startedAt,
    mode: 'public_key_isolated_user_browser_session_restore',
    steps: [],
    cleanup: { state: 'not_started' },
};

let temporaryDistribution;
let staticServer;
let chrome;
let profileDirectory;
let cdp;
let activeSession;
let stage = 'initializing';

async function run() {
try {
    stage = 'reading_environment';
    const configuration = requireEnvironment();
    stage = 'public_login';
    activeSession = await login(configuration);
    report.steps.push('public_web_login');

    stage = 'configuring_temporary_distribution';
    temporaryDistribution = await copyConfiguredDistribution(options.distribution, configuration);
    stage = 'starting_static_server';
    staticServer = await startStaticServer(temporaryDistribution);
    stage = 'launching_chrome';
    profileDirectory = await mkdtemp(join(tmpdir(), 'quata-web-auth-e2e-chrome-'));
    chrome = await launchChrome(options.chrome, profileDirectory);
    stage = 'connecting_cdp';
    cdp = await connectToPage(chrome.debugPort);

    const browserFaults = [];
    cdp.on('Runtime.exceptionThrown', event => browserFaults.push(describeException(event.exceptionDetails)));
    // Chrome emits benign WebGL warnings under headless SwiftShader. Warnings do not indicate a
    // launcher fault; uncaught exceptions and console errors remain hard failures.
    cdp.on('Log.entryAdded', event => {
        if (event.entry?.level === 'error') browserFaults.push('console_error');
    });
    await cdp.send('Runtime.enable');
    await cdp.send('Log.enable');

    stage = 'mounting_auth_shell';
    await navigateAndWait(cdp, `${staticServer.origin}/#auth`, 'auth');
    stage = 'persisting_browser_session';
    await restoreBrowserSession(cdp, activeSession);
    report.steps.push('browser_session_persisted');
    stage = 'mounting_authenticated_shell';
    await navigateAndWait(cdp, `${staticServer.origin}/#feed`, 'feed');
    report.steps.push('authenticated_shell_restored');

    stage = 'authenticated_browser_read';
    const browserContract = await assertBrowserContract(cdp, configuration, activeSession.profileId);
    if (!browserContract) throw new Error('browser_authenticated_profile_read_failed');
    report.steps.push('browser_authenticated_profile_read');
    if (browserFaults.length) throw new Error('browser_runtime_fault');

    stage = 'web_logout';
    await webLogout(configuration, activeSession);
    await revokeSessions(configuration, activeSession);
    activeSession = null;
    report.cleanup = { state: 'sessions_revoked' };
    report.status = 'passed';
} catch (error) {
    report.error = safeErrorCode(error);
    report.failureStage = stage;
    if (activeSession) {
        try {
            await revokeSessions(requireEnvironment(), activeSession);
            report.cleanup = { state: 'sessions_revoked_after_failure' };
        } catch {
            report.cleanup = { state: 'rollback_pending', action: 'revoke_sessions_for_isolated_user' };
        }
    }
} finally {
    if (cdp) cdp.close();
    if (chrome) await stopProcess(chrome.process);
    if (staticServer) await staticServer.close();
    if (profileDirectory) await removeTemporaryDirectory(profileDirectory);
    if (temporaryDistribution) await removeTemporaryDirectory(temporaryDistribution);
    report.finishedAt = new Date().toISOString();
    await writeSafeReport(options.output, report);
}

if (report.status !== 'passed') {
    console.error(`Authenticated browser E2E failed: ${report.error ?? 'unknown_failure'}.`);
    process.exitCode = 1;
} else {
    console.log('Authenticated browser E2E passed: session restore and authenticated browser request verified.');
}
}

function parseArguments(argumentsList) {
    const parsed = { distribution: resolve(defaultDistribution), chrome: defaultChrome, output: 'build-reports/web/authenticated-browser-e2e.json' };
    for (let index = 0; index < argumentsList.length; index += 1) {
        const argument = argumentsList[index];
        if (argument === '--dist' || argument === '--chrome' || argument === '--out') {
            const value = argumentsList[++index];
            if (!value || value.startsWith('--')) throw new Error('invalid_arguments');
            parsed[argument === '--dist' ? 'distribution' : argument === '--chrome' ? 'chrome' : 'output'] = resolve(value);
        } else if (argument === '--help' || argument === '-h') {
            console.log('Usage: node scripts/web-authenticated-browser-e2e.mjs [--dist DIR] [--chrome PATH] [--out SAFE_REPORT]');
            process.exit(0);
        } else throw new Error('invalid_arguments');
    }
    return parsed;
}

function requireEnvironment() {
    const missing = requiredEnvironment.filter(name => !process.env[name]?.trim());
    if (missing.length) throw new Error('missing_environment');
    const baseUrl = process.env.QUATA_SUPABASE_URL.trim().replace(/\/+$/, '');
    if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error('invalid_public_supabase_url');
    return {
        baseUrl,
        publishableKey: process.env.QUATA_SUPABASE_PUBLISHABLE_KEY.trim(),
        countryCode: process.env.QUATA_E2E_COUNTRY_CODE.trim(),
        phone: process.env.QUATA_E2E_PHONE.trim(),
        password: process.env.QUATA_E2E_PASSWORD,
    };
}

async function login(configuration) {
    const payload = await jsonRequest(`${configuration.baseUrl}/functions/v1/quata-auth-bridge`, {
        method: 'POST', headers: publicHeaders(configuration.publishableKey),
        body: JSON.stringify({ action: 'web_login', country_code: configuration.countryCode, phone_local: configuration.phone, password: configuration.password, client_instance_id: `web-browser-e2e-${crypto.randomUUID()}` }),
    }, 'public_web_login_failed');
    const session = payload?.session;
    const profileId = payload?.profile?.id;
    const webSessionToken = payload?.web_session?.token;
    if (!isUuid(profileId) || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at) || !webSessionToken) {
        throw new Error('invalid_auth_response');
    }
    return { profileId, accessToken: session.access_token, refreshToken: session.refresh_token, expiresAt: session.expires_at, webSessionToken };
}

async function webLogout(configuration, session) {
    await jsonRequest(`${configuration.baseUrl}/functions/v1/quata-web-push`, {
        method: 'POST', headers: { ...publicHeaders(configuration.publishableKey), authorization: `Bearer ${session.accessToken}`, 'x-quata-web-session': session.webSessionToken },
        body: JSON.stringify({ action: 'logout' }),
    }, 'web_logout_failed');
}

async function revokeSessions(configuration, session) {
    await jsonRequest(`${configuration.baseUrl}/auth/v1/logout`, {
        method: 'POST', headers: { ...publicHeaders(configuration.publishableKey), authorization: `Bearer ${session.accessToken}` }, body: JSON.stringify({ scope: 'global' }),
    }, 'session_revocation_failed');
}

function publicHeaders(key) { return { apikey: key, 'content-type': 'application/json', 'x-client-info': 'quata-web-browser-e2e' }; }
async function jsonRequest(url, options, prefix) {
    let response;
    try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(20_000) }); }
    catch { throw new Error(`${prefix}:network`); }
    if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
    try { return await response.json(); } catch { return {}; }
}

async function copyConfiguredDistribution(distribution, configuration) {
    if (!(await stat(distribution).catch(() => null))?.isDirectory()) throw new Error('distribution_missing');
    const target = await mkdtemp(join(tmpdir(), 'quata-web-auth-e2e-dist-'));
    await cp(distribution, target, { recursive: true });
    const index = join(target, 'index.html');
    let html = await readFile(index, 'utf8');
    html = html.replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${escapeHtml(configuration.baseUrl)}"`)
        .replace('name="quata-supabase-publishable-key" content=""', `name="quata-supabase-publishable-key" content="${escapeHtml(configuration.publishableKey)}"`);
    if (!html.includes('quata-supabase-url') || !html.includes(escapeHtml(configuration.publishableKey))) throw new Error('runtime_configuration_injection_failed');
    await writeFile(index, html, 'utf8');
    return target;
}
function escapeHtml(value) { return value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;'); }

async function startStaticServer(root) {
    const server = createServer(async (request, response) => {
        try {
            const pathname = decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname);
            if (pathname === '/favicon.ico') return response.writeHead(204).end();
            const file = resolve(root, `.${pathname === '/' ? '/index.html' : pathname}`);
            if (!file.startsWith(`${root}\\`) && file !== root) return response.writeHead(403).end();
            if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
            response.writeHead(200, { 'Content-Type': contentType(file), 'Cross-Origin-Opener-Policy': 'same-origin', 'Cross-Origin-Embedder-Policy': 'require-corp', 'Cache-Control': 'no-store' });
            response.end(await readFile(file));
        } catch { response.writeHead(500).end(); }
    });
    await new Promise((resolveServer, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolveServer); });
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('static_server_start_failed');
    return { origin: `http://127.0.0.1:${address.port}`, close: () => new Promise((resolveServer, reject) => server.close(error => error ? reject(error) : resolveServer())) };
}
function contentType(path) { return new Map([['.html', 'text/html; charset=utf-8'], ['.js', 'text/javascript; charset=utf-8'], ['.mjs', 'text/javascript; charset=utf-8'], ['.wasm', 'application/wasm'], ['.json', 'application/json'], ['.css', 'text/css'], ['.svg', 'image/svg+xml'], ['.webp', 'image/webp']]).get(extname(path).toLowerCase()) ?? 'application/octet-stream'; }

async function launchChrome(executable, profile) {
    const port = await availablePort();
    const process = spawn(executable, ['--headless=new', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--no-first-run', '--no-default-browser-check', `--user-data-dir=${profile}`, `--remote-debugging-port=${port}`, 'about:blank'], { stdio: ['ignore', 'ignore', 'ignore'] });
    await waitForDebugger(port);
    return { process, debugPort: port };
}
async function availablePort() { const server = createServer(); await new Promise((resolveServer, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolveServer); }); const port = server.address().port; await new Promise(resolveServer => server.close(resolveServer)); return port; }
async function waitForDebugger(port) { for (let attempt = 0; attempt < 80; attempt += 1) { try { if ((await fetch(`http://127.0.0.1:${port}/json/version`)).ok) return; } catch {} await delay(100); } throw new Error('chrome_debugger_timeout'); }
async function connectToPage(port) { for (let attempt = 0; attempt < 80; attempt += 1) { const pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json(); const page = pages.find(item => item.type === 'page' && item.webSocketDebuggerUrl); if (page) return CdpClient.connect(page.webSocketDebuggerUrl); await delay(100); } throw new Error('chrome_page_timeout'); }

async function navigateAndWait(cdpClient, url, fragment) { await cdpClient.send('Page.navigate', { url }); await waitForShell(cdpClient, fragment); }
async function restoreBrowserSession(cdpClient, session) {
    const global = await cdpClient.send('Runtime.evaluate', { expression: 'globalThis', returnByValue: false });
    await cdpClient.send('Runtime.callFunctionOn', {
        objectId: global.result.objectId,
        functionDeclaration: `function(values) { for (const [key, value] of Object.entries(values)) localStorage.setItem(key, value); return true; }`,
        arguments: [{ value: {
            quata_web_access_token: session.accessToken, quata_web_refresh_token: session.refreshToken,
            quata_web_session_token: session.webSessionToken, quata_web_user_id: session.profileId,
            quata_web_expires_at: String(session.expiresAt), 'web.auth.session_ready': 'true',
            quata_web_client_instance_id: `web-browser-e2e-${crypto.randomUUID()}`,
        } }], returnByValue: true, awaitPromise: true,
    });
}
async function assertBrowserContract(cdpClient, configuration, profileId) {
    const response = await cdpClient.send('Runtime.evaluate', { expression: `fetch(${JSON.stringify(`${configuration.baseUrl}/rest/v1/community_profiles?select=id&id=eq.${profileId}`)}, { headers: { apikey: localStorage.getItem('quata_web_access_token') ? ${JSON.stringify(configuration.publishableKey)} : '', authorization: 'Bearer ' + localStorage.getItem('quata_web_access_token') } }).then(async response => ({ ok: response.ok, rows: response.ok ? (await response.json()).length : -1, configured: localStorage.getItem('web.runtime.backend_configured'), sessionReady: localStorage.getItem('web.auth.session_ready') }))`, returnByValue: true, awaitPromise: true });
    const value = response.result?.value;
    return value?.ok === true && value?.rows === 1 && value?.configured === 'true' && value?.sessionReady === 'true';
}
async function waitForShell(cdpClient, fragment) { for (let attempt = 0; attempt < 120; attempt += 1) { const result = await cdpClient.send('Runtime.evaluate', { expression: `(() => { const root = document.querySelector('#quata-root'); return { hash: location.hash, root: Boolean(root), children: root?.childElementCount ?? 0, canvases: root?.querySelectorAll('canvas').length ?? 0, shadowChildren: root?.shadowRoot?.childElementCount ?? 0, shadowCanvases: root?.shadowRoot?.querySelectorAll('canvas').length ?? 0 }; })()`, returnByValue: true }); const value = result.result?.value; if (value?.hash === `#${fragment}` && value.root && (value.children > 0 || value.canvases > 0 || value.shadowChildren > 0 || value.shadowCanvases > 0)) return; await delay(100); } throw new Error('compose_shell_mount_timeout'); }

function isUuid(value) { return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
function safeErrorCode(error) { const message = typeof error?.message === 'string' ? error.message : ''; return ['missing_environment', 'invalid_public_supabase_url', 'distribution_missing', 'runtime_configuration_injection_failed', 'public_web_login_failed', 'invalid_auth_response', 'chrome_debugger_timeout', 'chrome_page_timeout', 'cdp_connect_failed', 'cdp_command_failed', 'browser_authenticated_profile_read_failed', 'browser_runtime_fault', 'compose_shell_mount_timeout', 'web_logout_failed', 'session_revocation_failed'].find(code => message.startsWith(code)) ?? 'unexpected_browser_e2e_failure'; }
function describeException(details) { return details?.exception?.description ? 'uncaught_exception' : 'runtime_log'; }
async function writeSafeReport(output, value) { const target = resolve(output); await mkdir(dirname(target), { recursive: true }); await writeFile(target, `${JSON.stringify(value, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 }); console.log(`Authenticated browser report written: ${target}`); }
async function stopProcess(child) { if (process.platform === 'win32' && child.pid) { await new Promise(resolveProcess => spawn('taskkill', ['/pid', String(child.pid), '/t', '/f'], { stdio: 'ignore' }).once('exit', resolveProcess)); } else if (child.exitCode === null) child.kill(); }
async function removeTemporaryDirectory(path) { await rm(path, { recursive: true, force: true, maxRetries: 3, retryDelay: 150 }).catch(() => {}); }

class CdpClient {
    static async connect(url) { const socket = new WebSocket(url); await new Promise((resolveSocket, reject) => { socket.addEventListener('open', resolveSocket, { once: true }); socket.addEventListener('error', () => reject(new Error('cdp_connect_failed')), { once: true }); }); return new CdpClient(socket); }
    constructor(socket) { this.socket = socket; this.nextId = 1; this.pending = new Map(); this.listeners = new Map(); socket.addEventListener('message', event => this.receive(JSON.parse(event.data))); }
    send(method, params = {}) { const id = this.nextId++; const result = new Promise((resolveCommand, reject) => this.pending.set(id, { resolve: resolveCommand, reject })); this.socket.send(JSON.stringify({ id, method, params })); return result; }
    on(method, listener) { const values = this.listeners.get(method) ?? []; values.push(listener); this.listeners.set(method, values); }
    receive(message) { if (message.id) { const pending = this.pending.get(message.id); if (!pending) return; this.pending.delete(message.id); if (message.error) pending.reject(new Error('cdp_command_failed')); else pending.resolve(message.result); return; } for (const listener of this.listeners.get(message.method) ?? []) listener(message.params ?? {}); }
    close() { this.socket.close(); }
}

await run();
