import assert from 'node:assert/strict';
import test from 'node:test';

import { classifyPaths } from './classify-ci-impact.mjs';

function platforms(paths) {
    const result = classifyPaths(paths);
    return {
        web: result.web,
        android: result.android,
        ios: result.ios,
        unknown: result.unknown,
        docs_only: result.docs_only,
    };
}

test('documentation-only changes do not select an expensive platform lane', () => {
    assert.deepEqual(platforms(['README.md', 'docs/wiki/Home.md', 'docs/ARCHITECTURE.md']), {
        web: false, android: false, ios: false, unknown: false, docs_only: true,
    });
});

test('platform source sets select only their actual platform', () => {
    assert.deepEqual(platforms(['web/src/wasmJsMain/kotlin/com/quata/web/Main.kt']), {
        web: true, android: false, ios: false, unknown: false, docs_only: false,
    });
    assert.deepEqual(platforms(['app/src/main/java/com/quata/MainActivity.kt']), {
        web: false, android: true, ios: false, unknown: false, docs_only: false,
    });
    assert.deepEqual(platforms(['feature/profile/src/iosMain/kotlin/ProfileGateway.kt']), {
        web: false, android: false, ios: true, unknown: false, docs_only: false,
    });
});

test('mixed platform changes select exactly the affected lanes', () => {
    assert.deepEqual(platforms([
        'feature/profile/src/iosMain/kotlin/ProfileGateway.kt',
        'feature/profile/src/wasmJsMain/kotlin/ProfileGateway.kt',
    ]), {
        web: true, android: false, ios: true, unknown: false, docs_only: false,
    });
});

test('shared source, build logic, workflows and capabilities select every lane', () => {
    for (const path of [
        'feature/profile/src/commonMain/kotlin/ProfileScreen.kt',
        'build-logic/src/main/kotlin/QuataPlugin.kt',
        '.github/workflows/ios-build.yml',
        'capabilities/platform-capability-matrix.json',
    ]) {
        assert.deepEqual(platforms([path]), {
            web: true, android: true, ios: true, unknown: false, docs_only: false,
        }, path);
    }
});

test('platform-specific scripts and operational documents remain selective', () => {
    assert.deepEqual(platforms(['scripts/ios-public-runtime-contract.test.mjs']), {
        web: false, android: false, ios: true, unknown: false, docs_only: false,
    });
    assert.deepEqual(platforms(['docs/CI_WEB_ANDROID.md']), {
        web: true, android: true, ios: false, unknown: false, docs_only: false,
    });
    assert.deepEqual(platforms(['docs/IOS_CI.md']), {
        web: false, android: false, ios: true, unknown: false, docs_only: false,
    });
});

test('an unknown non-documentation path fails safe by selecting every lane', () => {
    assert.deepEqual(platforms(['new-runtime/opaque.config']), {
        web: true, android: true, ios: true, unknown: true, docs_only: false,
    });
});
