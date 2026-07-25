#!/usr/bin/env node
/*
 * Records Kotlin/Wasm compiler warnings without making the historical warning
 * budget a build gate. The only optional enforcement is growth in direct
 * ExperimentalWasmJsInterop diagnostics, which must be explicitly requested.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { relative, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const options = parseArguments(process.argv.slice(2));
const log = resolve(options.log ?? 'build/reports/wasm-warning-budget-core-rerun.log');
if (!existsSync(log)) throw new Error(`Wasm warning log does not exist: ${relative(root, log)}. Run the documented compile first.`);

const warnings = readText(log).split(/\r?\n/).filter(line => line.startsWith('w: ')).map(normalizeWarning);
const report = {
    schemaVersion: 1,
    command: options.command ?? null,
    log: relative(root, log).replaceAll('\\', '/'),
    warnings: classify(warnings),
};

const output = resolve(options.report ?? 'build/reports/wasm-warning-budget.json');
writeJson(output, report);
console.log(`Wasm warning report: ${relative(root, output).replaceAll('\\', '/')}`);
for (const [kind, value] of Object.entries(report.warnings)) console.log(`${kind}: ${value.count}`);

if (options.baseline) {
    const baselinePath = resolve(options.baseline);
    if (!existsSync(baselinePath)) throw new Error(`Warning baseline does not exist: ${relative(root, baselinePath)}`);
    const baseline = JSON.parse(readFileSync(baselinePath, 'utf8'));
    const previous = baseline.warnings?.experimentalWasmJsInterop?.count;
    if (!Number.isSafeInteger(previous) || previous < 0) throw new Error('Baseline lacks warnings.experimentalWasmJsInterop.count');
    const growth = report.warnings.experimentalWasmJsInterop.count - previous;
    console.log(`ExperimentalWasmJsInterop growth: ${growth}`);
    if (options.maxNewOptIns !== undefined && growth > options.maxNewOptIns) throw new Error(`ExperimentalWasmJsInterop warnings grew by ${growth}; explicit maximum is ${options.maxNewOptIns}`);
}

function classify(lines) {
    const groups = { experimentalWasmJsInterop: [], expectActualClassesBeta: [], deprecations: [], other: [] };
    for (const line of lines) {
        if (line.includes('ExperimentalWasmJsInterop')) groups.experimentalWasmJsInterop.push(line);
        else if (line.includes("'expect'/'actual' classes")) groups.expectActualClassesBeta.push(line);
        else if (/\bdeprecated\b/i.test(line)) groups.deprecations.push(line);
        else groups.other.push(line);
    }
    return Object.fromEntries(Object.entries(groups).map(([name, entries]) => [name, { count: entries.length, samples: entries.slice(0, 8) }]));
}

function parseArguments(args) {
    const parsed = {};
    for (let index = 0; index < args.length; index += 1) {
        const token = args[index];
        if (token === '--help') {
            console.log('Usage: node scripts/wasm-warning-report.mjs [--log PATH] [--report PATH] [--command TEXT] [--baseline PATH] [--max-new-opt-ins N]');
            process.exit(0);
        }
        const key = { '--log': 'log', '--report': 'report', '--command': 'command', '--baseline': 'baseline', '--max-new-opt-ins': 'maxNewOptIns' }[token];
        if (!key || index + 1 >= args.length) throw new Error(`Unknown or incomplete argument: ${token}`);
        const value = args[++index];
        parsed[key] = key === 'maxNewOptIns' ? nonNegativeInteger(value, token) : value;
    }
    return parsed;
}

function nonNegativeInteger(value, flag) {
    const parsed = Number(value);
    if (!Number.isSafeInteger(parsed) || parsed < 0) throw new Error(`${flag} must be a non-negative integer`);
    return parsed;
}

function readText(path) {
    const bytes = readFileSync(path);
    // Windows PowerShell 5's Tee-Object writes UTF-16LE. Supporting it keeps
    // the documented capture command usable on the supported Windows host.
    if (bytes.length >= 2 && bytes[0] === 0xff && bytes[1] === 0xfe) return bytes.subarray(2).toString('utf16le');
    return bytes.toString('utf8');
}

function normalizeWarning(line) {
    // A checked-in baseline must not embed a developer's worktree path.
    const fileRoot = `file:///${root.replaceAll('\\', '/')}/`;
    return line.replaceAll(fileRoot, 'file:///quata/');
}

function writeJson(path, value) {
    mkdirSync(resolve(path, '..'), { recursive: true });
    writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}
