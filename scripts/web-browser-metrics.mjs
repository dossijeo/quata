#!/usr/bin/env node
/**
 * Summarises metrics emitted by web-browser-smoke.mjs. This intentionally
 * keeps the pass/fail decision in the smoke: measurements are evidence, not a
 * performance gate until stable hardware/CI baselines exist.
 */
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';

const options = parseArguments(process.argv.slice(2));
const expectedRoutes = ['auth', 'feed', 'chat', 'official', 'settings', 'share-target'];
const maxClockSkewMs = 5 * 60 * 1000;
const reports = options.reports.map(path => JSON.parse(readFileSync(resolve(path), 'utf8')));
reports.forEach(validateReport);
if (reports.length === 1) printSample(reports[0]);
else printSeries(reports);

function printSample(report) {
    printIdentity(report);
    for (const navigation of report.navigations) {
        const lifecycle = navigation.documentLifecycle
            ? `; DOM ${navigation.documentLifecycle.domContentLoadedMs ?? 'n/a'} ms; load ${navigation.documentLifecycle.loadMs ?? 'n/a'} ms`
            : '';
        console.log(`${navigation.route}: ${navigation.navigationKind}; mount ${navigation.mountElapsedMs ?? 'n/a'} ms; JS heap ${formatBytes(navigation.memory?.jsHeapUsedSize)}${lifecycle}`);
    }
}

function printSeries(series) {
    if (series.length < 3) throw new Error('A browser metrics series requires at least three reports.');
    const sampleIds = series.map(report => report.sampleId);
    if (new Set(sampleIds).size !== sampleIds.length) {
        throw new Error('A browser metrics series requires a unique sampleId for every smoke execution.');
    }
    const generatedTimes = series.map(report => report.generatedAt);
    if (new Set(generatedTimes).size !== generatedTimes.length) {
        throw new Error('A browser metrics series requires a unique generatedAt for every smoke execution.');
    }
    const measurementFingerprints = series.map(measurementFingerprint);
    if (new Set(measurementFingerprints).size !== measurementFingerprints.length) {
        throw new Error('A browser metrics series contains duplicate measurement payloads.');
    }
    const identity = seriesIdentity(series[0]);
    if (series.some(report => seriesIdentity(report) !== identity)) {
        throw new Error('A browser metrics series must use the same revision, distribution, Chrome, and hardware/CI environment.');
    }
    printIdentity(series[0]);
    console.log(`Series: ${series.length} cold-profile samples; advisory only.`);
    for (const route of expectedRoutes) {
        const navigations = series.map(report => report.navigations.find(navigation => navigation.route === route));
        const mounts = navigations.map(navigation => navigation.mountElapsedMs);
        const heaps = navigations.map(navigation => navigation.memory?.jsHeapUsedSize).filter(Number.isFinite);
        console.log(`${route}: mount p50 ${percentile(mounts, 0.50)} ms; p95 ${percentile(mounts, 0.95)} ms; JS heap p50 ${formatBytes(percentile(heaps, 0.50))}; p95 ${formatBytes(percentile(heaps, 0.95))}`);
    }
}

function printIdentity(report) {
    console.log(`Browser metrics revision: ${report.revision ?? 'unknown'}`);
    console.log(`Chrome: ${report.browser?.product ?? 'unknown'}`);
    console.log(`Distribution SHA-256: ${report.distributionFingerprintSha256}`);
    console.log(`Environment: ${report.environment.platform}/${report.environment.architecture}; ${report.environment.cpuModel}; ${report.environment.logicalCpuCount} logical CPUs; ${formatBytes(report.environment.totalMemoryBytes)}`);
}

function validateReport(value) {
    if (value?.schemaVersion !== 2 || !Array.isArray(value.navigations)) {
        throw new Error('Browser metrics report must use schemaVersion 2 and contain navigations.');
    }
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value.sampleId ?? '')) {
        throw new Error('Browser metrics must identify the smoke execution with a UUID v4 sampleId.');
    }
    const generatedAtMs = Date.parse(value.generatedAt);
    if (
        typeof value.generatedAt !== 'string' ||
        !Number.isFinite(generatedAtMs) ||
        new Date(value.generatedAt).toISOString() !== value.generatedAt
    ) {
        throw new Error('Browser metrics generatedAt must be a canonical ISO-8601 timestamp.');
    }
    if (generatedAtMs > Date.now() + maxClockSkewMs) {
        throw new Error('Browser metrics generatedAt cannot be more than five minutes in the future.');
    }
    if (value.smoke?.status !== 'passed') {
        throw new Error(`Browser metrics cannot certify a non-passing smoke (${value.smoke?.status ?? 'missing'}).`);
    }
    const declaredRoutes = value.smoke?.expectedRoutes;
    const completedRoutes = value.smoke?.completedRoutes;
    const measuredRoutes = value.navigations.map(({ route }) => route);
    for (const [label, routes] of [['expected', declaredRoutes], ['completed', completedRoutes], ['measured', measuredRoutes]]) {
        if (JSON.stringify(routes) !== JSON.stringify(expectedRoutes)) {
            throw new Error(`Browser metrics ${label} routes must be exactly: ${expectedRoutes.join(', ')}.`);
        }
    }
    if (!/^[0-9a-f]{64}$/i.test(value.distributionFingerprintSha256 ?? '')) {
        throw new Error('Browser metrics must identify the exact distribution with SHA-256.');
    }
    if (!/^(?:[0-9a-f]{40}|[0-9a-f]{64})$/i.test(value.revision ?? '')) {
        throw new Error('Browser metrics must identify the source revision.');
    }
    const environment = value.environment;
    if (
        !value.browser?.product || !environment?.platform || !environment?.architecture ||
        !environment.osRelease || !environment.node || !environment.cpuModel || !environment.ci ||
        !Number.isInteger(environment.logicalCpuCount) || environment.logicalCpuCount < 1 ||
        !Number.isFinite(environment.totalMemoryBytes) || environment.totalMemoryBytes < 1
    ) {
        throw new Error('Browser metrics must identify Chrome and the measurement environment.');
    }
    for (const [index, navigation] of value.navigations.entries()) {
        const expectedKind = index === 0 ? 'full-document' : 'same-document-hash';
        if (navigation.navigationKind !== expectedKind) {
            throw new Error(`Browser metrics route ${navigation.route} must use ${expectedKind}.`);
        }
        if (
            Object.hasOwn(navigation, 'domContentLoadedMs') ||
            Object.hasOwn(navigation, 'loadMs')
        ) {
            throw new Error(`Browser metrics route ${navigation.route} uses legacy top-level lifecycle fields.`);
        }
        requireNonNegativeMetric(navigation.mountElapsedMs, `${navigation.route}.mountElapsedMs`);

        if (index === 0) {
            if (!navigation.documentLifecycle || typeof navigation.documentLifecycle !== 'object') {
                throw new Error('Browser metrics auth route requires documentLifecycle.');
            }
            requireNonNegativeMetric(
                navigation.documentLifecycle.domContentLoadedMs,
                'auth.documentLifecycle.domContentLoadedMs',
            );
            requireNonNegativeMetric(
                navigation.documentLifecycle.loadMs,
                'auth.documentLifecycle.loadMs',
            );
        } else if (navigation.documentLifecycle !== null) {
            throw new Error(`Browser metrics hash route ${navigation.route} must set documentLifecycle to null.`);
        }

        if (!navigation.memory || typeof navigation.memory !== 'object') {
            throw new Error(`Browser metrics route ${navigation.route} requires memory metrics.`);
        }
        requireNonNegativeMetric(navigation.memory.jsHeapUsedSize, `${navigation.route}.memory.jsHeapUsedSize`);
        requireNonNegativeMetric(navigation.memory.jsHeapTotalSize, `${navigation.route}.memory.jsHeapTotalSize`);
        requireNullableNonNegativeMetric(
            navigation.memory.processPrivateMemory,
            `${navigation.route}.memory.processPrivateMemory`,
        );
    }
}

function requireNonNegativeMetric(value, field) {
    if (!Number.isFinite(value) || value < 0) {
        throw new Error(`Browser metrics ${field} must be finite and non-negative.`);
    }
}

function requireNullableNonNegativeMetric(value, field) {
    if (value !== null) requireNonNegativeMetric(value, field);
}

function parseArguments(args) {
    if (args.length === 0 || args.includes('--help') || args.includes('-h')) {
        console.log('Usage: node scripts/web-browser-metrics.mjs --report REPORT.json [--report REPORT-2.json ...]');
        process.exit(args.length === 0 ? 1 : 0);
    }
    const reports = [];
    for (let index = 0; index < args.length; index += 2) {
        if (args[index] !== '--report' || !args[index + 1] || args[index + 1].startsWith('--')) {
            throw new Error('Only repeated --report PATH arguments are supported.');
        }
        reports.push(args[index + 1]);
    }
    return { reports };
}

function formatBytes(value) {
    if (!Number.isFinite(value)) return 'n/a';
    return `${(value / (1024 * 1024)).toFixed(2)} MiB`;
}

function percentile(values, fraction) {
    if (values.length === 0) return null;
    const sorted = [...values].sort((left, right) => left - right);
    return sorted[Math.ceil(fraction * sorted.length) - 1];
}

function measurementFingerprint(report) {
    const {
        sampleId: _sampleId,
        generatedAt: _generatedAt,
        distribution: _distributionPath,
        ...measurement
    } = report;
    return createHash('sha256').update(canonicalJson(measurement)).digest('hex');
}

function canonicalJson(value) {
    if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
    if (value && typeof value === 'object') {
        return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
    }
    return JSON.stringify(value);
}

function seriesIdentity(report) {
    return JSON.stringify({
        revision: report.revision,
        distributionFingerprintSha256: report.distributionFingerprintSha256,
        browser: report.browser.product,
        environment: {
            platform: report.environment.platform,
            architecture: report.environment.architecture,
            osRelease: report.environment.osRelease,
            node: report.environment.node,
            cpuModel: report.environment.cpuModel,
            logicalCpuCount: report.environment.logicalCpuCount,
            totalMemoryBytes: report.environment.totalMemoryBytes,
            ci: report.environment.ci,
            runnerOs: report.environment.runnerOs,
            runnerArchitecture: report.environment.runnerArchitecture,
        },
    });
}
