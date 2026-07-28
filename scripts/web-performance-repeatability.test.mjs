import assert from 'node:assert/strict';
import test from 'node:test';
import { validateRepeatabilityEvidence } from './web-performance-repeatability.mjs';

function evidence() {
    return {
        schemaVersion: 1,
        status: 'passed',
        iterationCount: 3,
        configuration: {
            node: 'v22.0.0',
            coldProfile: true,
            docmentis: true,
            chrome: 'Chrome.exe',
            smokeScript: 'scripts/web-browser-smoke.mjs',
            metricsReporter: 'scripts/web-browser-metrics.mjs',
            distribution: 'web/build/dist/wasmJs/productionExecutable',
        },
        identity: { revision: 'a'.repeat(40), distributionFingerprintSha256: 'b'.repeat(64), chrome: 'Chrome/150', node: 'v22.0.0' },
        iterations: [0, 1, 2].map(index => ({
            ordinal: index + 1,
            report: `build/reports/repeatability/iteration-0${index + 1}.json`,
            sampleId: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
            generatedAt: `2026-07-28T10:00:0${index}.000Z`,
        })),
    };
}

test('accepts three identified cold-profile iterations without a timing threshold', () => {
    const value = evidence();
    value.advisory = 'informative';
    assert.doesNotThrow(() => validateRepeatabilityEvidence(value));
});

test('rejects absent, malformed, or repeated iteration evidence', () => {
    const missing = evidence();
    missing.iterations.pop();
    assert.throws(() => validateRepeatabilityEvidence(missing), /exactly three/);

    const duplicate = evidence();
    duplicate.iterations[2].sampleId = duplicate.iterations[1].sampleId;
    assert.throws(() => validateRepeatabilityEvidence(duplicate), /distinct/);

    const malformed = evidence();
    malformed.identity.distributionFingerprintSha256 = 'not-a-hash';
    assert.throws(() => validateRepeatabilityEvidence(malformed), /revision, distribution, Chrome, and Node/);
});
