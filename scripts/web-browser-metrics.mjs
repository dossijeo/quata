#!/usr/bin/env node
/**
 * Summarises metrics emitted by web-browser-smoke.mjs. This intentionally
 * keeps the pass/fail decision in the smoke: measurements are evidence, not a
 * performance gate until stable hardware/CI baselines exist.
 */
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const options = parseArguments(process.argv.slice(2));
const report = JSON.parse(readFileSync(resolve(options.report), 'utf8'));
if (report.schemaVersion !== 1 || !Array.isArray(report.navigations)) {
    throw new Error('Browser metrics report must use schemaVersion 1 and contain navigations.');
}

console.log(`Browser metrics revision: ${report.revision ?? 'unknown'}`);
console.log(`Chrome: ${report.browser?.product ?? 'unknown'}`);
for (const navigation of report.navigations) {
    console.log(`${navigation.route}: mount ${navigation.mountElapsedMs ?? 'n/a'} ms; JS heap ${formatBytes(navigation.memory?.jsHeapUsedSize)}; DOM ${navigation.domContentLoadedMs ?? 'n/a'} ms; load ${navigation.loadMs ?? 'n/a'} ms`);
}

function parseArguments(args) {
    if (args.length === 0 || args.includes('--help') || args.includes('-h')) {
        console.log('Usage: node scripts/web-browser-metrics.mjs --report build/reports/web-browser-smoke-metrics.json');
        process.exit(args.length === 0 ? 1 : 0);
    }
    if (args.length !== 2 || args[0] !== '--report') throw new Error('Only --report PATH is supported.');
    return { report: args[1] };
}

function formatBytes(value) {
    if (!Number.isFinite(value)) return 'n/a';
    return `${(value / (1024 * 1024)).toFixed(2)} MiB`;
}
