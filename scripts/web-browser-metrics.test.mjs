import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { SMOKE_ROUTE_FRAGMENTS } from './web-browser-route-contract.mjs';

const routes = SMOKE_ROUTE_FRAGMENTS;
const reporter = fileURLToPath(new URL('./web-browser-metrics.mjs', import.meta.url));

test('accepts the exhaustive ordered route contract without imposing an absolute performance threshold', async () => {
    const result = await runReporter(validReport({
        mountElapsedMs: 999_999_999,
        jsHeapUsedSize: 999_999_999_999,
    }));
    assert.equal(result.status, 0, result.stderr);
    for (const route of routes) assert.match(result.stdout, new RegExp(`^${route}:`, 'm'));
});

test('rejects failed, incomplete, and distribution-unidentified reports', async () => {
    const failed = validReport();
    failed.smoke.status = 'failed';
    assert.notEqual((await runReporter(failed)).status, 0);

    const incomplete = validReport();
    incomplete.navigations.pop();
    incomplete.smoke.completedRoutes.pop();
    assert.notEqual((await runReporter(incomplete)).status, 0);

    const unidentified = validReport();
    unidentified.distributionFingerprintSha256 = null;
    assert.notEqual((await runReporter(unidentified)).status, 0);
});

test('rejects adversarial navigation semantics, legacy fields, and invalid heap metrics', async () => {
    const sixFullDocuments = validReport();
    for (const navigation of sixFullDocuments.navigations) {
        navigation.navigationKind = 'full-document';
        navigation.documentLifecycle = { domContentLoadedMs: 10, loadMs: 20 };
    }
    assert.notEqual((await runReporter(sixFullDocuments)).status, 0);

    const inheritedLifecycle = validReport();
    inheritedLifecycle.navigations[1].documentLifecycle = { domContentLoadedMs: 10, loadMs: 20 };
    assert.notEqual((await runReporter(inheritedLifecycle)).status, 0);

    const legacyLifecycle = validReport();
    legacyLifecycle.navigations[0].domContentLoadedMs = 10;
    legacyLifecycle.navigations[0].loadMs = 20;
    assert.notEqual((await runReporter(legacyLifecycle)).status, 0);

    const negativeHeap = validReport();
    negativeHeap.navigations[3].memory.jsHeapUsedSize = -1;
    assert.notEqual((await runReporter(negativeHeap)).status, 0);
});

test('rejects partial and non-finite metric records', async () => {
    const partialLifecycle = validReport();
    delete partialLifecycle.navigations[0].documentLifecycle.loadMs;
    assert.notEqual((await runReporter(partialLifecycle)).status, 0);

    const partialMemory = validReport();
    delete partialMemory.navigations[2].memory.jsHeapUsedSize;
    assert.notEqual((await runReporter(partialMemory)).status, 0);

    const missingHashLifecycle = validReport();
    delete missingHashLifecycle.navigations[4].documentLifecycle;
    assert.notEqual((await runReporter(missingHashLifecycle)).status, 0);

    const negativeLifecycle = validReport();
    negativeLifecycle.navigations[0].documentLifecycle.domContentLoadedMs = -1;
    assert.notEqual((await runReporter(negativeLifecycle)).status, 0);

    const overflowingMount = JSON.stringify(validReport()).replace(
        '"mountElapsedMs":100',
        '"mountElapsedMs":1e400',
    );
    assert.notEqual((await runReporter(overflowingMount)).status, 0);
});

test('summarises p50/p95 only for a fixed three-sample series', async () => {
    const series = Array.from({ length: 3 }, (_, index) => validReport({
        mountElapsedMs: 100 + index,
        sampleId: sampleId(index),
        generatedAt: `2026-01-27T15:00:0${index}.000Z`,
    }));
    const result = await runReporter(series);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /Series: 3 cold-profile samples; advisory only\./);
    assert.match(result.stdout, /auth: mount p50 101 ms; p95 102 ms/);

    assert.notEqual((await runReporter(series.slice(0, 2))).status, 0);
    series[2].environment.cpuModel = 'Different CI CPU';
    assert.notEqual((await runReporter(series)).status, 0);
});

test('rejects repeated files, identical copies, duplicate sample IDs, and invalid timestamps', async () => {
    const repeated = validReport();
    assert.notEqual((await runReporter(repeated, { repeatSamePath: 3 })).status, 0);

    const identicalCopies = Array.from({ length: 3 }, () => structuredClone(repeated));
    assert.notEqual((await runReporter(identicalCopies)).status, 0);

    const duplicateIdDifferentTimes = Array.from({ length: 3 }, (_, index) => validReport({
        sampleId: repeated.sampleId,
        generatedAt: `2026-01-27T15:01:0${index}.000Z`,
    }));
    assert.notEqual((await runReporter(duplicateIdDifferentTimes)).status, 0);

    const invalidTimestamp = validReport();
    invalidTimestamp.generatedAt = '27/07/2026 15:00';
    assert.notEqual((await runReporter(invalidTimestamp)).status, 0);
});

test('rejects copied measurements, duplicate timestamps, and future timestamps', async () => {
    const copiedMeasurement = Array.from({ length: 3 }, (_, index) => validReport({
        sampleId: sampleId(index),
        generatedAt: `2026-01-27T16:00:0${index}.000Z`,
        distribution: `copy-${index}/productionExecutable`,
    }));
    const copiedResult = await runReporter(copiedMeasurement);
    assert.notEqual(copiedResult.status, 0);
    assert.match(copiedResult.stderr, /duplicate measurement payloads/);

    const duplicateGeneratedAt = Array.from({ length: 3 }, (_, index) => validReport({
        mountElapsedMs: 200 + index,
        sampleId: sampleId(index),
        generatedAt: '2026-01-27T17:00:00.000Z',
    }));
    const duplicateTimeResult = await runReporter(duplicateGeneratedAt);
    assert.notEqual(duplicateTimeResult.status, 0);
    assert.match(duplicateTimeResult.stderr, /unique generatedAt/);

    const future = validReport({ generatedAt: '2999-01-01T00:00:00.000Z' });
    const futureResult = await runReporter(future);
    assert.notEqual(futureResult.status, 0);
    assert.match(futureResult.stderr, /five minutes in the future/);
});

function validReport(overrides = {}) {
    return {
        schemaVersion: 2,
        sampleId: overrides.sampleId ?? sampleId(15),
        generatedAt: overrides.generatedAt ?? '2026-01-27T15:00:00.000Z',
        revision: 'a'.repeat(40),
        distribution: overrides.distribution ?? 'web/build/dist/wasmJs/productionExecutable',
        distributionFingerprintSha256: 'b'.repeat(64),
        browser: { product: 'Chrome/150.0.0.0' },
        environment: {
            platform: 'win32',
            architecture: 'x64',
            osRelease: '10.0.fixed',
            node: 'v20.11.0',
            cpuModel: 'Fixed CI CPU',
            logicalCpuCount: 8,
            totalMemoryBytes: 16 * 1024 * 1024 * 1024,
            ci: 'github-actions',
            runnerOs: 'Windows',
            runnerArchitecture: 'X64',
        },
        smoke: {
            status: 'passed',
            expectedRoutes: [...routes],
            completedRoutes: [...routes],
        },
        navigations: routes.map((route, index) => ({
            route,
            navigationKind: index === 0 ? 'full-document' : 'same-document-hash',
            mountElapsedMs: overrides.mountElapsedMs ?? 100 + index,
            documentLifecycle: index === 0 ? { domContentLoadedMs: 10, loadMs: 20 } : null,
            memory: {
                jsHeapUsedSize: overrides.jsHeapUsedSize ?? 1024,
                jsHeapTotalSize: 2048,
                processPrivateMemory: null,
            },
        })),
    };
}

function sampleId(index) {
    return `00000000-0000-4000-8000-${index.toString(16).padStart(12, '0')}`;
}

async function runReporter(reportOrReports, options = {}) {
    const directory = await mkdtemp(join(tmpdir(), 'quata-web-metrics-contract-'));
    const reports = Array.isArray(reportOrReports) ? reportOrReports : [reportOrReports];
    try {
        const arguments_ = [reporter];
        let firstReportPath;
        for (const [index, report] of reports.entries()) {
            const reportPath = join(directory, `report-${index}.json`);
            await writeFile(reportPath, typeof report === 'string' ? report : JSON.stringify(report));
            arguments_.push('--report', reportPath);
            firstReportPath ??= reportPath;
        }
        if (options.repeatSamePath) {
            arguments_.splice(1);
            for (let index = 0; index < options.repeatSamePath; index += 1) {
                arguments_.push('--report', firstReportPath);
            }
        }
        return spawnSync(process.execPath, arguments_, {
            encoding: 'utf8',
            windowsHide: true,
        });
    } finally {
        await rm(directory, { recursive: true, force: true });
    }
}
