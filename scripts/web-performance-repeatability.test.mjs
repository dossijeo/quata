import assert from 'node:assert/strict';
import test from 'node:test';
import { createMeasurementSummary, createRepeatabilityEvidence, createThresholdProposal, formatCompletionOutput, validateRepeatabilityEvidence } from './web-performance-repeatability.mjs';

function evidence() {
    return {
        schemaVersion: 2,
        status: 'passed',
        iterationCount: 5,
        configuration: {
            node: 'v22.0.0',
            coldProfile: true,
            docmentis: true,
            smokeScript: 'scripts/web-browser-smoke.mjs',
            metricsReporter: 'scripts/web-browser-metrics.mjs',
            distribution: 'web/build/dist/wasmJs/productionExecutable',
        },
        identity: { revision: 'a'.repeat(40), distributionFingerprintSha256: 'b'.repeat(64), chrome: 'Chrome/150', node: 'v22.0.0' },
        iterations: [0, 1, 2, 3, 4].map(index => ({
            ordinal: index + 1,
            report: `build/reports/repeatability/iteration-0${index + 1}.json`,
            sampleId: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
            generatedAt: `2026-07-28T10:00:0${index}.000Z`,
        })),
    };
}

test('accepts five identified cold-profile iterations without a timing threshold', () => {
    const value = evidence();
    value.advisory = 'informative';
    const reports = value.iterations.map((iteration, index) => reportFor(iteration, index));
    value.measurements = createMeasurementSummary(reports);
    value.policy = { enforcement: 'advisory', productSlo: false, blockingConditions: ['schema'], nonBlockingConditions: ['variation'] };
    value.environmentNoise = ['shared host'];
    value.thresholdProposal = createThresholdProposal(reports);
    assert.doesNotThrow(() => validateRepeatabilityEvidence(value));
});

test('rejects absent, malformed, or repeated iteration evidence', () => {
    const missing = evidence();
    missing.iterations.pop();
    assert.throws(() => validateRepeatabilityEvidence(missing), /exactly five/);

    const duplicate = evidence();
    duplicate.iterations[2].sampleId = duplicate.iterations[1].sampleId;
    assert.throws(() => validateRepeatabilityEvidence(duplicate), /distinct/);

    const malformed = evidence();
    malformed.identity.distributionFingerprintSha256 = 'not-a-hash';
    assert.throws(() => validateRepeatabilityEvidence(malformed), /revision, distribution, Chrome, and Node/);
});

test('does not retain a user Chrome launcher path in the manifest or completion output', () => {
    const launcherPath = 'C:\\Users\\Ada Lovelace\\AppData\\Local\\Chrome\\chrome.exe';
    const input = evidence();
    const reports = input.iterations.map(iteration => ({
        ordinal: iteration.ordinal,
        path: iteration.report,
        report: reportFor(iteration, iteration.ordinal - 1),
    }));
    const output = createRepeatabilityEvidence({
        chrome: launcherPath,
        smokeScript: input.configuration.smokeScript,
        metricsReporter: input.configuration.metricsReporter,
        distribution: input.configuration.distribution,
        docmentis: input.configuration.docmentis,
    }, reports);
    const manifest = JSON.stringify(output);
    const completionLog = formatCompletionOutput('build/reports/web-performance-repeatability.json');

    assert.doesNotMatch(manifest, /Ada Lovelace|C:\\Users\\/);
    assert.doesNotMatch(completionLog, /Ada Lovelace|C:\\Users\\/);
    assert.equal(Object.hasOwn(output.configuration, 'chrome'), false);

    output.configuration.chrome = launcherPath;
    assert.throws(() => validateRepeatabilityEvidence(output), /without retaining a Chrome launcher path/);
});

test('aggregates bootstrap and first stable Auth state deterministically and leaves thresholds proposed', () => {
    const input = evidence();
    const reports = input.iterations.map((iteration, index) => reportFor(iteration, index));
    const summary = createMeasurementSummary(reports);
    assert.deepEqual(summary.bootstrap.mountElapsedMs, { count: 5, min: 100, p50: 102, p95: 104, max: 104 });
    assert.equal(summary.firstStableState.route, 'auth');
    const proposal = createThresholdProposal(reports);
    assert.equal(proposal.state, 'proposed');
    assert.equal(proposal.enforcement, 'advisory');
    assert.equal(proposal.productSlo, false);
});

function reportFor(iteration, index) {
    const routes = ['auth', 'feed', 'chat', 'official', 'settings', 'share-target'];
    return {
        schemaVersion: 2,
        sampleId: iteration.sampleId,
        generatedAt: iteration.generatedAt,
        revision: 'a'.repeat(40),
        distributionFingerprintSha256: 'b'.repeat(64),
        browser: { product: 'Chrome/150' },
        environment: { node: 'v22.0.0', platform: 'win32', architecture: 'x64', osRelease: 'test', cpuModel: 'test CPU', logicalCpuCount: 8, totalMemoryBytes: 16, ci: 'false' },
        smoke: { status: 'passed', expectedRoutes: routes, completedRoutes: routes },
        navigations: routes.map((route, routeIndex) => ({
            route,
            navigationKind: routeIndex === 0 ? 'full-document' : 'same-document-hash',
            mountElapsedMs: 100 + index + routeIndex,
            documentLifecycle: routeIndex === 0 ? { domContentLoadedMs: 20 + index, loadMs: 40 + index } : null,
            memory: { jsHeapUsedSize: 1000 + index + routeIndex, jsHeapTotalSize: 2000, processPrivateMemory: null },
        })),
    };
}
