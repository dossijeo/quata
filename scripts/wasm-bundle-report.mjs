#!/usr/bin/env node
/*
 * Produces a deterministic inventory of a Kotlin/Wasm production distribution.
 * It intentionally has no default budget: gathering a baseline must not turn a
 * slow local wasm-opt execution into a misleading CI failure.  CI can opt in to
 * a hard limit with --max-total-bytes or --max-growth-bytes once a reviewed
 * baseline exists.
 */
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { extname, join, relative, resolve } from 'node:path';
import { gzipSync } from 'node:zlib';

const repositoryRoot = resolve(import.meta.dirname, '..');
const defaults = {
    dist: join(repositoryRoot, 'web', 'build', 'dist', 'wasmJs', 'productionExecutable'),
    report: join(repositoryRoot, 'build', 'reports', 'wasm-bundle', 'wasm-bundle-report.json'),
};

const options = parseArguments(process.argv.slice(2));
const distribution = resolve(options.dist ?? defaults.dist);
if (!existsSync(distribution)) {
    throw new Error(`Wasm distribution does not exist: ${distribution}. Run :web:wasmJsBrowserDistribution first.`);
}

const files = listFiles(distribution)
    .map(file => asset(distribution, file))
    .sort((left, right) => right.bytes - left.bytes || left.path.localeCompare(right.path));
const totals = files.reduce((result, file) => ({
    bytes: result.bytes + file.bytes,
    gzipBytes: result.gzipBytes + file.gzipBytes,
}), { bytes: 0, gzipBytes: 0 });
const report = {
    schemaVersion: 1,
    revision: repositoryRevision(),
    distribution: relative(repositoryRoot, distribution).replaceAll('\\', '/'),
    files,
    totals,
    contributors: contributors(files),
    notes: [
        'DocMentis is dynamically imported; its emitted chunk(s) are identified by file-name hints when webpack preserves them.',
        'The npm package source size is not a distribution-size proxy. Measure emitted chunks after wasmJsBrowserDistribution.',
    ],
};

writeJson(options.report ?? defaults.report, report);
console.log(`Wasm distribution: ${report.distribution}`);
console.log(`Assets: ${files.length}; total ${formatBytes(totals.bytes)}; gzip ${formatBytes(totals.gzipBytes)}`);
console.log('Largest emitted assets:');
for (const file of files.slice(0, 12)) console.log(`  ${formatBytes(file.bytes).padStart(10)}  ${file.path}`);
console.log('Contributor groups:');
for (const group of report.contributors) console.log(`  ${formatBytes(group.bytes).padStart(10)}  ${group.name} (${group.files} assets)`);
console.log(`JSON report: ${relative(repositoryRoot, resolve(options.report ?? defaults.report)).replaceAll('\\', '/')}`);

if (options.writeBaseline) {
    writeJson(options.writeBaseline, report);
    console.log(`Certified baseline candidate: ${relative(repositoryRoot, resolve(options.writeBaseline)).replaceAll('\\', '/')}`);
}

const budget = options.budget ? readBudget(options.budget) : undefined;
if (options.budget && budget.state !== 'approved') {
    throw new Error(`Bundle budget ${relative(repositoryRoot, resolve(options.budget)).replaceAll('\\', '/')} is ${budget?.state ?? 'invalid'}; a reviewed exact baseline must set state to approved before a gate can run.`);
}
const baselinePath = options.baseline ?? budget?.baselineFile;
const baseline = baselinePath ? JSON.parse(readFileSync(resolveBudgetPath(baselinePath, options.budget), 'utf8')) : undefined;
const maxGrowthBytes = options.maxGrowthBytes ?? budget?.maxGrowthBytes;
const maxGrowthGzipBytes = options.maxGrowthGzipBytes ?? budget?.maxGrowthGzipBytes;
const failures = [];
if (options.maxTotalBytes !== undefined && totals.bytes > options.maxTotalBytes) {
    failures.push(`total ${totals.bytes} bytes exceeds explicit max ${options.maxTotalBytes}`);
}
if (maxGrowthBytes !== undefined) {
    if (!Number.isSafeInteger(baseline?.totals?.bytes) || baseline.totals.bytes < 0) {
        throw new Error('--max-growth-bytes requires a baseline report with totals.bytes');
    }
    const growth = totals.bytes - baseline.totals.bytes;
    if (growth > maxGrowthBytes) failures.push(`growth ${growth} bytes exceeds explicit max ${maxGrowthBytes}`);
}
if (maxGrowthGzipBytes !== undefined) {
    if (!Number.isSafeInteger(baseline?.totals?.gzipBytes) || baseline.totals.gzipBytes < 0) {
        throw new Error('--max-growth-gzip-bytes requires a baseline report with totals.gzipBytes');
    }
    const growth = totals.gzipBytes - baseline.totals.gzipBytes;
    if (growth > maxGrowthGzipBytes) failures.push(`gzip growth ${growth} bytes exceeds explicit max ${maxGrowthGzipBytes}`);
}
if (failures.length > 0) throw new Error(`Wasm bundle budget failed: ${failures.join('; ')}`);

function parseArguments(argumentsList) {
    const parsed = {};
    for (let index = 0; index < argumentsList.length; index += 1) {
        const token = argumentsList[index];
        if (token === '--help') {
            console.log('Usage: node scripts/wasm-bundle-report.mjs [--dist DIR] [--report FILE] [--write-baseline FILE] [--baseline FILE] [--budget FILE] [--max-total-bytes N] [--max-growth-bytes N] [--max-growth-gzip-bytes N]');
            process.exit(0);
        }
        const key = {
            '--dist': 'dist', '--report': 'report', '--write-baseline': 'writeBaseline', '--baseline': 'baseline', '--budget': 'budget',
            '--max-total-bytes': 'maxTotalBytes', '--max-growth-bytes': 'maxGrowthBytes', '--max-growth-gzip-bytes': 'maxGrowthGzipBytes',
        }[token];
        if (!key || index + 1 >= argumentsList.length) throw new Error(`Unknown or incomplete argument: ${token}`);
        const value = argumentsList[++index];
        parsed[key] = key.startsWith('max') ? positiveInteger(value, token) : value;
    }
    return parsed;
}

function readBudget(path) {
    const budget = JSON.parse(readFileSync(resolve(path), 'utf8'));
    if (budget.schemaVersion !== 1 || !['proposed', 'approved'].includes(budget.state)) {
        throw new Error('Bundle budget must declare schemaVersion 1 and state proposed or approved');
    }
    for (const key of ['maxGrowthBytes', 'maxGrowthGzipBytes']) {
        if (!Number.isSafeInteger(budget[key]) || budget[key] < 0) throw new Error(`Bundle budget lacks a non-negative ${key}`);
    }
    if (typeof budget.baselineFile !== 'string' || budget.baselineFile.length === 0) {
        throw new Error('Bundle budget lacks baselineFile');
    }
    return budget;
}

function resolveBudgetPath(path, budgetPath) {
    return budgetPath && !path.match(/^[A-Za-z]:[\\/]/) && !path.startsWith('/')
        ? resolve(resolve(budgetPath, '..'), path)
        : resolve(path);
}

function repositoryRevision() {
    try {
        return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repositoryRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    } catch {
        return null;
    }
}

function positiveInteger(value, flag) {
    const parsed = Number(value);
    if (!Number.isSafeInteger(parsed) || parsed < 0) throw new Error(`${flag} must be a non-negative integer`);
    return parsed;
}

function listFiles(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
        const path = join(directory, entry.name);
        return entry.isDirectory() ? listFiles(path) : entry.isFile() ? [path] : [];
    });
}

function asset(root, path) {
    const content = readFileSync(path);
    const normalizedPath = relative(root, path).replaceAll('\\', '/');
    return {
        path: normalizedPath,
        extension: extname(path).toLowerCase() || '<none>',
        bytes: statSync(path).size,
        gzipBytes: gzipSync(content).length,
        sha256: createHash('sha256').update(content).digest('hex'),
        contributor: contributorFor(normalizedPath, content),
    };
}

function contributorFor(path, content) {
    const lower = path.toLowerCase();
    // Webpack content-addresses assets, so production filenames alone cannot
    // identify DocMentis/Skiko. Their emitted WASM and JS retain stable marker
    // strings; use them before filename hints and keep the raw asset list for
    // auditability when a future dependency changes its markers.
    if (lower.endsWith('.map')) return 'Source maps';
    if (lower.endsWith('.wasm')) {
        if (contains(content, 'udoc') || /(docmentis|udoc)/.test(lower)) return 'DocMentis';
        if (contains(content, 'Quata')) return 'Kotlin/Wasm application';
        if (contains(content, 'skiko') || /skiko/.test(lower)) return 'Skiko';
    }
    if (lower.endsWith('.js') && contains(content, 'UDocClient:()=>')) return 'DocMentis';
    if (/skiko/.test(lower)) return 'Skiko';
    if (/(material.?icons|icons)/.test(lower)) return 'Compose icons';
    if (/\.wasm$/.test(lower)) return 'Kotlin/Wasm application';
    if (/\.js$/.test(lower)) return 'JavaScript runtime/chunks';
    return 'Static/runtime assets';
}

function contains(content, marker) {
    return content.includes(Buffer.from(marker));
}

function contributors(assets) {
    const groups = new Map();
    for (const file of assets) {
        const current = groups.get(file.contributor) ?? { name: file.contributor, files: 0, bytes: 0, gzipBytes: 0 };
        current.files += 1;
        current.bytes += file.bytes;
        current.gzipBytes += file.gzipBytes;
        groups.set(file.contributor, current);
    }
    return [...groups.values()].sort((left, right) => right.bytes - left.bytes || left.name.localeCompare(right.name));
}

function writeJson(path, value) {
    const output = resolve(path);
    mkdirSync(resolve(output, '..'), { recursive: true });
    writeFileSync(output, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    const units = ['KiB', 'MiB', 'GiB'];
    let value = bytes;
    let unit = -1;
    do { value /= 1024; unit += 1; } while (value >= 1024 && unit < units.length - 1);
    return `${value.toFixed(2)} ${units[unit]}`;
}
