#!/usr/bin/env node
/**
 * Collects a small, reproducible series of cold-profile Chrome measurements.
 *
 * This is an evidence gate, not a performance budget: all three executions
 * must complete and describe the same build environment, but their elapsed
 * times are deliberately never compared against a threshold here.
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve, sep } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const iterations = 3;

if (isMainModule()) await main();

async function main() {
    const options = parseArguments(process.argv.slice(2));
    await mkdir(options.metricsDirectory, { recursive: true });
    const reports = [];
    for (let ordinal = 1; ordinal <= iterations; ordinal += 1) {
        const reportPath = resolve(options.metricsDirectory, `iteration-${String(ordinal).padStart(2, '0')}.json`);
        const smokeArguments = [options.smokeScript, '--dist', options.distribution, '--chrome', options.chrome, '--metrics-report', reportPath];
        if (options.docmentis) smokeArguments.push('--docmentis');
        const result = spawnSync(process.execPath, smokeArguments, {
            cwd: repositoryRoot,
            encoding: 'utf8',
            windowsHide: true,
        });
        if (result.error || result.status !== 0) {
            const cause = result.error?.message ?? `exit_${result.status ?? 'unknown'}`;
            throw new Error(`Repeatability iteration ${ordinal} failed (${cause}).`);
        }
        reports.push({ ordinal, path: reportPath, report: JSON.parse(await readFile(reportPath, 'utf8')) });
    }

    // Reuse the established metric contract to reject malformed reports,
    // repeated samples, a changed distribution, or a changed environment.
    const reportArguments = [options.metricsReporter];
    for (const { path } of reports) reportArguments.push('--report', path);
    const reporter = spawnSync(process.execPath, reportArguments, {
        cwd: repositoryRoot,
        encoding: 'utf8',
        windowsHide: true,
    });
    if (reporter.error || reporter.status !== 0) {
        const cause = reporter.error?.message ?? (reporter.stderr.trim() || `exit_${reporter.status ?? 'unknown'}`);
        throw new Error(`Repeatability evidence is malformed (${cause}).`);
    }

    const evidence = createRepeatabilityEvidence(options, reports);
    validateRepeatabilityEvidence(evidence);
    await mkdir(dirname(options.output), { recursive: true });
    await writeFile(options.output, `${JSON.stringify(evidence, null, 2)}\n`);
    process.stdout.write(`${reporter.stdout}${formatCompletionOutput(options.output)}\n`);
}

export function createRepeatabilityEvidence(options, reports) {
    const first = reports[0].report;
    return {
        schemaVersion: 1,
        status: 'passed',
        generatedAt: new Date().toISOString(),
        iterationCount: iterations,
        configuration: {
            node: process.version,
            smokeScript: relativeRepositoryPath(options.smokeScript),
            metricsReporter: relativeRepositoryPath(options.metricsReporter),
            distribution: relativeRepositoryPath(options.distribution),
            docmentis: options.docmentis,
            coldProfile: true,
        },
        identity: {
            revision: first.revision,
            distributionFingerprintSha256: first.distributionFingerprintSha256,
            // The browser product comes from the smoke report; never persist the local launcher path.
            chrome: first.browser?.product,
            node: first.environment?.node,
            environment: first.environment,
        },
        iterations: reports.map(({ ordinal, path, report }) => ({
            ordinal,
            report: relativeRepositoryPath(path),
            sampleId: report.sampleId,
            generatedAt: report.generatedAt,
        })),
        advisory: 'No timing or memory variation threshold is enforced until a controlled baseline is approved.',
    };
}

export function formatCompletionOutput(output) {
    return JSON.stringify({ repeatabilityEvidence: relativeRepositoryPath(output), iterations }, null, 2);
}

function parseArguments(arguments_) {
    const values = {};
    for (let index = 0; index < arguments_.length; index += 1) {
        const flag = arguments_[index];
        if (flag === '--docmentis') {
            values.docmentis = true;
            continue;
        }
        if (!['--out', '--metrics-dir', '--dist', '--chrome', '--smoke-script', '--metrics-reporter'].includes(flag) || !arguments_[index + 1]) {
            throw new Error('Usage: node scripts/web-performance-repeatability.mjs --out FILE --metrics-dir DIR --dist DIR --chrome PATH [--docmentis].');
        }
        values[flag.slice(2).replaceAll('-', '')] = arguments_[index + 1];
        index += 1;
    }
    for (const key of ['out', 'metricsdir', 'dist', 'chrome']) {
        if (!values[key]) throw new Error(`Missing required --${key === 'metricsdir' ? 'metrics-dir' : key} argument.`);
    }
    return {
        output: resolve(values.out),
        metricsDirectory: resolve(values.metricsdir),
        distribution: resolve(values.dist),
        chrome: values.chrome,
        smokeScript: resolve(values.smokescript ?? 'scripts/web-browser-smoke.mjs'),
        metricsReporter: resolve(values.metricsreporter ?? 'scripts/web-browser-metrics.mjs'),
        docmentis: values.docmentis === true,
    };
}

export function validateRepeatabilityEvidence(evidence) {
    if (evidence?.schemaVersion !== 1 || evidence.status !== 'passed' || evidence.iterationCount !== iterations || !Array.isArray(evidence.iterations) || evidence.iterations.length !== iterations) {
        throw new Error('Repeatability evidence must declare exactly three passed iterations using schemaVersion 1.');
    }
    if (
        !/^v\d+\.\d+\.\d+/.test(evidence.configuration?.node ?? '') ||
        evidence.configuration?.coldProfile !== true ||
        typeof evidence.configuration?.docmentis !== 'boolean' ||
        !isRepositoryRelativePath(evidence.configuration?.smokeScript) ||
        !isRepositoryRelativePath(evidence.configuration?.metricsReporter) ||
        !isRepositoryRelativePath(evidence.configuration?.distribution) ||
        Object.hasOwn(evidence.configuration ?? {}, 'chrome')
    ) {
        throw new Error('Repeatability evidence must record the Node, distribution, and cold-profile configuration without retaining a Chrome launcher path.');
    }
    if (!/^[0-9a-f]{40,64}$/i.test(evidence.identity?.revision ?? '') || !/^[0-9a-f]{64}$/i.test(evidence.identity?.distributionFingerprintSha256 ?? '') || !evidence.identity?.chrome || !evidence.identity?.node) {
        throw new Error('Repeatability evidence must identify the revision, distribution, Chrome, and Node.');
    }
    const sampleIds = new Set();
    const reportPaths = new Set();
    for (const [index, iteration] of evidence.iterations.entries()) {
        if (iteration?.ordinal !== index + 1 || !isUuidV4(iteration.sampleId) || !isRepositoryRelativePath(iteration.report) || !iteration.generatedAt) {
            throw new Error('Repeatability evidence contains an invalid iteration record.');
        }
        sampleIds.add(iteration.sampleId);
        reportPaths.add(iteration.report);
    }
    if (sampleIds.size !== iterations || reportPaths.size !== iterations) throw new Error('Repeatability evidence iterations must be distinct.');
}

function relativeRepositoryPath(path) {
    const value = relative(repositoryRoot, resolve(path));
    if (!value || value === '..' || value.startsWith(`..${sep}`) || value.startsWith('../')) throw new Error('Repeatability evidence paths must remain inside the repository.');
    return value.replaceAll('\\', '/');
}

function isUuidV4(value) {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value ?? '');
}

function isRepositoryRelativePath(value) {
    return typeof value === 'string' && value.length > 0 && !value.startsWith('..') && !value.startsWith('/') && !value.startsWith('\\');
}

function isMainModule() {
    return process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
}
