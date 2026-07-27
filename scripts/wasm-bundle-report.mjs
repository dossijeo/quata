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
import { validatePullRequestApprovalPolicy } from './wasm-bundle-approval-policy.mjs';

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
const revision = repositoryRevision();
const report = {
    schemaVersion: 1,
    revision,
    distribution: relative(repositoryRoot, distribution).replaceAll('\\', '/'),
    files,
    totals,
    inventorySha256: inventoryFingerprint(files),
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
    const capture = trustedBaselineCapture(options.trustedRef, files);
    writeJson(options.writeBaseline, { ...report, baselineState: 'candidate', revision: capture.sourceRevision, capture });
    console.log(`Trusted baseline candidate: ${relative(repositoryRoot, resolve(options.writeBaseline)).replaceAll('\\', '/')}`);
}

const budget = options.budget ? readBudget(options.budget) : undefined;
const baselinePath = options.baseline ?? budget?.baselineFile;
const baseline = baselinePath ? readBaseline(resolveBudgetPath(baselinePath, options.budget)) : undefined;
if (budget?.state === 'approved') validateApprovedBaseline(baseline);
if (options.policyBase) {
    validatePullRequestApprovalPolicy({
        budget,
        baseline,
        baseRevision: resolveRevision(options.policyBase),
        changedFiles: changedFiles(options.policyBase),
        currentInventorySha256: inventoryFingerprint(files),
    });
} else if (budget?.state === 'approved' && process.env.GITHUB_EVENT_NAME === 'pull_request') {
    throw new Error('Approved bundle budget requires --policy-base for pull_request CI');
}
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
if (budget?.state === 'proposed') {
    console.log(`Bundle budget is proposed; comparison is advisory until its baseline is reviewed and state becomes approved.`);
    for (const failure of failures) console.log(`Proposed-budget regression: ${failure}`);
} else if (failures.length > 0) {
    throw new Error(`Wasm bundle budget failed: ${failures.join('; ')}`);
}

function parseArguments(argumentsList) {
    const parsed = {};
    for (let index = 0; index < argumentsList.length; index += 1) {
        const token = argumentsList[index];
        if (token === '--help') {
            console.log('Usage: node scripts/wasm-bundle-report.mjs [--dist DIR] [--report FILE] [--write-baseline FILE --trusted-ref origin/main|refs/tags/TAG] [--baseline FILE] [--budget FILE] [--policy-base REV] [--max-total-bytes N] [--max-growth-bytes N] [--max-growth-gzip-bytes N]');
            process.exit(0);
        }
        const key = {
            '--dist': 'dist', '--report': 'report', '--write-baseline': 'writeBaseline', '--trusted-ref': 'trustedRef', '--baseline': 'baseline', '--budget': 'budget', '--policy-base': 'policyBase',
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

function validateApprovedBaseline(baseline) {
    if (baseline?.schemaVersion !== 1 || baseline?.baselineState !== 'approved') {
        throw new Error('An approved bundle budget requires a schemaVersion 1 baselineState approved baseline');
    }
    if (typeof baseline.revision !== 'string' || !/^[0-9a-f]{40}$/i.test(baseline.revision)) {
        throw new Error('An approved bundle budget requires a baseline revision SHA');
    }
    const capture = baseline.capture;
    if (capture?.schemaVersion !== 2 || capture?.sourceRevision !== baseline.revision ||
        !isTrustedRef(capture?.trustedRef) || capture?.sourceTree?.revision !== baseline.revision ||
        !/^[0-9a-f]{64}$/i.test(capture?.sourceTree?.sha256 ?? '') ||
        !/^[0-9a-f]{64}$/i.test(capture?.inventorySha256 ?? '')) {
        throw new Error('An approved bundle budget requires a reproducible source-tree and inventory attestation');
    }
    const expectedSourceTree = sourceTreeFingerprint(baseline.revision);
    if (capture.sourceTree.sha256 !== expectedSourceTree.sha256) {
        throw new Error('Approved baseline source-tree fingerprint does not match its revision');
    }
    if (capture.inventorySha256 !== inventoryFingerprint(baseline.files)) {
        throw new Error('Approved baseline inventory fingerprint does not match its files');
    }
    const computedTotals = sumTotals(baseline.files);
    if (baseline.totals?.bytes !== computedTotals.bytes || baseline.totals?.gzipBytes !== computedTotals.gzipBytes) {
        throw new Error('Approved baseline totals do not match its attested inventory');
    }
}

function readBaseline(path) {
    try {
        return JSON.parse(readFileSync(path, 'utf8'));
    } catch (error) {
        throw new Error(`Bundle baseline is unavailable or invalid: ${path}`, { cause: error });
    }
}

function repositoryRevision() {
    try {
        return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repositoryRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    } catch {
        return null;
    }
}

function trustedBaselineCapture(trustedRef, files) {
    if (!isTrustedRef(trustedRef)) throw new Error('--write-baseline requires --trusted-ref origin/main or refs/tags/TAG');
    const sourceRevision = resolveRevision(trustedRef);
    const headRevision = resolveRevision('HEAD');
    if (headRevision !== sourceRevision) throw new Error('Baseline capture HEAD must equal the trusted ref revision');
    if (isAttachedCheckout()) throw new Error('Baseline capture requires a detached checkout');
    if (gitOutput(['status', '--porcelain', '--untracked-files=all']).length > 0) {
        throw new Error('Baseline capture requires a clean checkout');
    }
    return {
        schemaVersion: 2,
        trustedRef,
        sourceRevision,
        sourceTree: sourceTreeFingerprint(sourceRevision),
        inventorySha256: inventoryFingerprint(files),
        command: ':web:wasmJsBrowserDistribution',
    };
}

function isTrustedRef(ref) {
    return ref === 'origin/main' || ref === 'refs/remotes/origin/main' || /^refs\/tags\/[A-Za-z0-9._/-]+$/.test(ref ?? '');
}

function isAttachedCheckout() {
    try {
        return gitOutput(['symbolic-ref', '-q', 'HEAD']).trim().length > 0;
    } catch {
        return false;
    }
}

function resolveRevision(ref) {
    const revision = gitOutput(['rev-parse', `${ref}^{commit}`]).trim();
    if (!/^[0-9a-f]{40}$/i.test(revision)) throw new Error(`Cannot resolve trusted revision: ${ref}`);
    return revision;
}

function changedFiles(baseRevision) {
    return gitOutput(['diff', '--name-only', `${baseRevision}...HEAD`]).split(/\r?\n/).filter(Boolean);
}

function gitOutput(argumentsList) {
    try {
        return execFileSync('git', argumentsList, { cwd: repositoryRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
    } catch (error) {
        throw new Error(`Git command failed while verifying bundle provenance: git ${argumentsList.join(' ')}`, { cause: error });
    }
}

function sourceTreeFingerprint(revision) {
    if (typeof revision !== 'string' || !/^[0-9a-f]{40}$/i.test(revision)) {
        throw new Error('Cannot attest a bundle without a Git revision SHA');
    }
    try {
        const tree = execFileSync('git', ['ls-tree', '-r', '--full-tree', '-z', revision], {
            cwd: repositoryRoot,
            encoding: null,
            stdio: ['ignore', 'pipe', 'ignore'],
        });
        return { algorithm: 'git-ls-tree-sha256-v1', revision, sha256: createHash('sha256').update(tree).digest('hex') };
    } catch (error) {
        throw new Error(`Cannot verify the source tree for baseline revision ${revision}`, { cause: error });
    }
}

function inventoryFingerprint(files) {
    const inventory = [...files]
        .sort((left, right) => left.path.localeCompare(right.path))
        .map(({ path, extension, bytes, gzipBytes, sha256, contributor }) => ({ path, extension, bytes, gzipBytes, sha256, contributor }));
    return createHash('sha256').update(JSON.stringify(inventory)).digest('hex');
}

function sumTotals(files) {
    if (!Array.isArray(files) || files.length === 0 || files.some(file =>
        typeof file?.path !== 'string' || file.path.length === 0 || file.path.includes('..') ||
        !Number.isSafeInteger(file.bytes) || file.bytes < 0 || !Number.isSafeInteger(file.gzipBytes) || file.gzipBytes < 0 ||
        !/^[0-9a-f]{64}$/i.test(file.sha256 ?? ''),
    )) throw new Error('Approved baseline has an invalid asset inventory');
    return files.reduce((total, file) => ({ bytes: total.bytes + file.bytes, gzipBytes: total.gzipBytes + file.gzipBytes }), { bytes: 0, gzipBytes: 0 });
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
