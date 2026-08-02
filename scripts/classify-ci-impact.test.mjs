import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, unlinkSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import test from 'node:test';

import { classifyChanges, classifyGitRange, classifyPaths, parseNameStatusZ } from './classify-ci-impact.mjs';

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

function git(directory, args, options = {}) {
    return execFileSync('git', args, { cwd: directory, encoding: 'utf8', ...options }).trim();
}

function write(directory, relativePath, content = 'fixture\n') {
    const target = join(directory, ...relativePath.split('/'));
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, content);
}

function commit(directory, message) {
    git(directory, ['add', '-A']);
    git(directory, ['commit', '-qm', message]);
    return git(directory, ['rev-parse', 'HEAD']);
}

function commitStaged(directory, message) {
    git(directory, ['commit', '-qm', message]);
    return git(directory, ['rev-parse', 'HEAD']);
}

function stageBlob(directory, relativePath, content = 'fixture\n', mode = '100644') {
    const blob = git(directory, ['hash-object', '-w', '--stdin'], { input: content });
    git(directory, ['update-index', '--add', '--cacheinfo', `${mode},${blob},${relativePath}`]);
}

function diffEntries(directory, base, head) {
    const output = execFileSync('git', [
        'diff', '--name-status', '-z', '--find-renames', '--find-copies-harder', base, head,
    ], { cwd: directory });
    return parseNameStatusZ(output);
}

function withRepository(callback) {
    const directory = mkdtempSync(join(tmpdir(), 'quata-ci-impact-'));
    try {
        git(directory, ['init', '-q']);
        git(directory, ['config', 'user.email', 'ci-impact@example.invalid']);
        git(directory, ['config', 'user.name', 'CI impact fixture']);
        return callback(directory);
    } finally {
        rmSync(directory, { recursive: true, force: true });
    }
}

function assertAllPlatforms(result, unknown = false) {
    assert.deepEqual(
        { web: result.web, android: result.android, ios: result.ios, unknown: result.unknown, docs_only: result.docs_only },
        { web: true, android: true, ios: true, unknown, docs_only: false },
    );
}

test('real Git ranges classify deletes and both rename endpoints conservatively', () => withRepository((directory) => {
    write(directory, 'core/src/commonMain/kotlin/Shared.kt');
    const commonBase = commit(directory, 'common source');
    unlinkSync(join(directory, 'core', 'src', 'commonMain', 'kotlin', 'Shared.kt'));
    const commonDelete = commit(directory, 'delete common source');
    assert.equal(diffEntries(directory, commonBase, commonDelete)[0].status, 'D');
    assertAllPlatforms(classifyGitRange(commonBase, commonDelete, { cwd: directory }));

    write(directory, 'core/src/commonMain/kotlin/Renamed.kt');
    const commonRenameBase = commit(directory, 'common rename source');
    mkdirSync(join(directory, 'docs'), { recursive: true });
    git(directory, ['mv', 'core/src/commonMain/kotlin/Renamed.kt', 'docs/Renamed.md']);
    const commonToDocs = commit(directory, 'rename common to docs');
    assert.equal(diffEntries(directory, commonRenameBase, commonToDocs)[0].status, 'R');
    assertAllPlatforms(classifyGitRange(commonRenameBase, commonToDocs, { cwd: directory }));

    const docsRenameBase = commonToDocs;
    mkdirSync(join(directory, 'core/src/commonMain/kotlin'), { recursive: true });
    git(directory, ['mv', 'docs/Renamed.md', 'core/src/commonMain/kotlin/Renamed.kt']);
    const docsToCommon = commit(directory, 'rename docs to common');
    assert.equal(diffEntries(directory, docsRenameBase, docsToCommon)[0].status, 'R');
    assertAllPlatforms(classifyGitRange(docsRenameBase, docsToCommon, { cwd: directory }));
}));

test('real Git ranges cover type changes, copies and NUL-sensitive names', () => withRepository((directory) => {
    write(directory, 'core/src/commonMain/kotlin/Type.kt');
    const regular = commit(directory, 'regular file');
    unlinkSync(join(directory, 'core', 'src', 'commonMain', 'kotlin', 'Type.kt'));
    stageBlob(directory, 'core/src/commonMain/kotlin/Type.kt', 'Target.kt\n', '120000');
    const symbolic = commitStaged(directory, 'symbolic link');
    assert.equal(diffEntries(directory, regular, symbolic)[0].status, 'T');
    assertAllPlatforms(classifyGitRange(regular, symbolic, { cwd: directory }));

    write(directory, 'docs/copy-source.md', 'copy me\n');
    const copyBase = commit(directory, 'copy source');
    write(directory, 'core/src/commonMain/kotlin/Copy.kt', 'copy me\n');
    const copyHead = commit(directory, 'copy destination');
    assert.ok(diffEntries(directory, copyBase, copyHead).some(entry => entry.status === 'C'), 'Git must report the fixture as a copy');
    assertAllPlatforms(classifyGitRange(copyBase, copyHead, { cwd: directory }));

    // Git for Windows rejects control characters in worktree/index paths, so
    // spaces/unicode use a real temporary repository and newline paths exercise
    // the exact NUL-delimited parser below.
    write(directory, 'docs/guía con espacio.md');
    const unusualDocs = commit(directory, 'unicode space docs');
    const docsResult = classifyGitRange(copyHead, unusualDocs, { cwd: directory });
    assert.equal(docsResult.docs_only, true);
    assert.equal(docsResult.unknown, false);

    write(directory, 'core/src/commonMain/kotlin/nombre ñ.kt');
    const unusualCommon = commit(directory, 'unicode space common');
    assertAllPlatforms(classifyGitRange(unusualDocs, unusualCommon, { cwd: directory }));
}));

test('malformed Git statuses and ranges fail closed instead of becoming docs-only', () => {
    assert.throws(() => parseNameStatusZ(Buffer.from('Q\0docs/only.md\0')));
    assert.throws(() => parseNameStatusZ(Buffer.from('M100\0docs/only.md\0')));
    assert.throws(() => parseNameStatusZ(Buffer.from('R100\0docs/old.md\0')));
    assertAllPlatforms(classifyChanges([{ status: 'Q', paths: ['docs/only.md'] }]), true);
    assertAllPlatforms(classifyChanges([{ status: 'R', paths: ['docs/only.md'] }]), true);
    assertAllPlatforms(classifyGitRange('not-a-commit', 'also-not-a-commit'), true);
    assertAllPlatforms(classifyGitRange('0000000000000000000000000000000000000000', 'HEAD'), true);
});

test('NUL-delimited records retain newline path bytes instead of line-splitting them', () => {
    const docsEntry = parseNameStatusZ(Buffer.from('A\0docs/guía\ncon salto.md\0'));
    assert.equal(classifyChanges(docsEntry).docs_only, true);
    const commonEntry = parseNameStatusZ(Buffer.from('A\0core/src/commonMain/kotlin/nombre\ncon salto.kt\0'));
    assertAllPlatforms(classifyChanges(commonEntry));
});

test('an empty real Git range remains an empty docs-only change set', () => withRepository((directory) => {
    write(directory, 'README.md');
    const head = commit(directory, 'initial');
    assert.deepEqual(platformsFromResult(classifyGitRange(head, head, { cwd: directory })), {
        web: false, android: false, ios: false, unknown: false, docs_only: true,
    });
}));

function platformsFromResult(result) {
    return {
        web: result.web,
        android: result.android,
        ios: result.ios,
        unknown: result.unknown,
        docs_only: result.docs_only,
    };
}
