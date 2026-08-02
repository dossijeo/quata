#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const ALL_PLATFORMS = Object.freeze(['web', 'android', 'ios']);
const NAME_STATUS = new Set(['A', 'C', 'D', 'M', 'R', 'T', 'U', 'X', 'B']);

const normalize = (value) => value.replaceAll('\\', '/').replace(/^\.\//, '');

const platformResult = () => ({
    web: false,
    android: false,
    ios: false,
    unknown: false,
    docs_only: true,
    reasons: [],
});

function add(result, platforms, reason) {
    for (const platform of platforms) result[platform] = true;
    if (platforms.length > 0) result.docs_only = false;
    result.reasons.push(reason);
}

function all(result, reason, unknown = false) {
    add(result, ALL_PLATFORMS, reason);
    result.unknown ||= unknown;
}

function unknownResult(reason) {
    const result = platformResult();
    all(result, reason, true);
    return result;
}

export function classifyPaths(paths) {
    const result = platformResult();

    for (const rawPath of paths) {
        // Do not trim: Git paths may legally begin/end with whitespace or contain newlines.
        const path = normalize(rawPath);
        if (!path) {
            all(result, 'malformed-empty-path', true);
            continue;
        }

        if (/^docs\//i.test(path)) {
            const documentedPlatforms = [];
            if (/(?:web|wasm)/i.test(path)) documentedPlatforms.push('web');
            if (/android/i.test(path)) documentedPlatforms.push('android');
            if (/ios/i.test(path)) documentedPlatforms.push('ios');
            if (documentedPlatforms.length > 0) {
                add(result, documentedPlatforms, `platform-doc:${path}`);
                continue;
            }
        }

        if (/^(README(?:\.[^/]+)?|LICENSE|CONTRIBUTING(?:\.[^/]+)?|docs\/wiki\/|docs\/[\s\S]*\.md$)/i.test(path)) {
            result.reasons.push(`docs:${path}`);
            continue;
        }

        if (/^\.github\/workflows\//.test(path) || /^scripts\/(?:classify-ci-impact|check-final-certification)/.test(path)) {
            all(result, `ci-control:${path}`);
            continue;
        }
        if (/^(?:settings\.gradle\.kts|build\.gradle\.kts|gradle\.properties|gradlew(?:\.bat)?|gradle\/|build-logic\/)/.test(path)) {
            all(result, `shared-build:${path}`);
            continue;
        }
        if (/^(?:capabilities\/|designsystem\/src\/commonMain\/composeResources\/)/.test(path)) {
            all(result, `shared-contract:${path}`);
            continue;
        }

        if (/^(?:web\/|.*\/src\/(?:wasmJs|js)(?:Main|Test)\/)/.test(path)) {
            add(result, ['web'], `web:${path}`);
            continue;
        }
        if (/^(?:iosApp\/|ios-shared\/|.*\/src\/(?:ios|iosX64|iosArm64|iosSimulatorArm64)(?:Main|Test)\/)/.test(path) || /\.swift$/.test(path)) {
            add(result, ['ios'], `ios:${path}`);
            continue;
        }

        // settings.gradle.kts identifies core, designsystem and feature:* as KMP
        // modules. Any common source set is therefore consumed by multiple targets.
        // Keep this before Android-only prefixes: a future commonMain under an
        // Android-named module is fail-closed instead of being misclassified Android-only.
        if (/^[^/]+(?:\/[^/]+)?\/src\/(?:commonMain|commonTest)\//.test(path)) {
            all(result, `shared-source:${path}`);
            continue;
        }
        if (/^(?:core|designsystem|feature)\/.*\/build\.gradle\.kts$/.test(path)) {
            all(result, `shared-module-build:${path}`);
            continue;
        }

        // document-reader is presently an Android library (its build file applies
        // com.android.library only), but the generic commonMain case above remains
        // cross-platform-safe if that module is migrated later.
        if (/^(?:app\/|document-reader\/|.*\/src\/(?:android|androidHost|androidUnit)(?:Main|Test)\/)/.test(path)) {
            add(result, ['android'], `android:${path}`);
            continue;
        }

        if (/^scripts\/(?:ios-|run-ios|test-ios)/.test(path)) {
            add(result, ['ios'], `ios-script:${path}`);
            continue;
        }
        if (/^scripts\/(?:web-|wasm-|run-wasm|run-web|docmentis)/.test(path)) {
            add(result, ['web'], `web-script:${path}`);
            continue;
        }
        if (/^scripts\/(?:android-|run-android|test-android)/.test(path)) {
            add(result, ['android'], `android-script:${path}`);
            continue;
        }

        if (/^(?:package(?:-lock)?\.json|supabase\/|scripts\/)/.test(path)) {
            all(result, `cross-platform-runtime:${path}`);
            continue;
        }

        all(result, `unknown:${path}`, true);
    }

    return result;
}

/** Parse `git diff --name-status -z` without newline/quote ambiguities. */
export function parseNameStatusZ(buffer) {
    if (!Buffer.isBuffer(buffer)) throw new Error('name-status output must be a Buffer');
    if (buffer.length === 0) return [];
    if (buffer[buffer.length - 1] !== 0) throw new Error('malformed name-status output: missing NUL terminator');

    const fields = buffer.toString('utf8').split('\0');
    fields.pop();
    const entries = [];
    for (let index = 0; index < fields.length;) {
        const statusToken = fields[index++];
        if (!statusToken) throw new Error('malformed name-status output: empty status');
        const status = statusToken[0];
        const validToken = status === 'R' || status === 'C'
            ? /^[RC]\d+$/.test(statusToken)
            : /^[ADMTUXB]$/.test(statusToken);
        if (!NAME_STATUS.has(status) || !validToken) throw new Error(`unknown Git diff status: ${statusToken}`);
        const pathCount = status === 'R' || status === 'C' ? 2 : 1;
        const paths = fields.slice(index, index + pathCount);
        if (paths.length !== pathCount || paths.some(path => path.length === 0)) {
            throw new Error(`malformed ${statusToken} name-status entry`);
        }
        index += pathCount;
        entries.push({ status, statusToken, paths });
    }
    return entries;
}

export function classifyChanges(entries) {
    const paths = [];
    for (const entry of entries) {
        const expectedPathCount = entry?.status === 'R' || entry?.status === 'C' ? 2 : 1;
        if (!entry || !NAME_STATUS.has(entry.status) || !Array.isArray(entry.paths) || entry.paths.length !== expectedPathCount || entry.paths.some(path => typeof path !== 'string' || path.length === 0)) {
            return unknownResult('malformed-change-entry:all');
        }
        // R and C intentionally classify both endpoints. A platform migration can
        // otherwise hide the removed source set from its former lane.
        paths.push(...entry.paths);
    }
    return classifyPaths(paths);
}

function resolvedCommit(revision, cwd) {
    if (!revision || /^0+$/.test(revision)) return null;
    try {
        return execFileSync('git', ['rev-parse', '--verify', '--quiet', `${revision}^{commit}`], {
            cwd,
            encoding: 'utf8',
            stdio: ['ignore', 'pipe', 'ignore'],
        }).trim();
    } catch {
        return null;
    }
}

export function classifyGitRange(base, head, { cwd = process.cwd() } = {}) {
    const baseCommit = resolvedCommit(base, cwd);
    const headCommit = resolvedCommit(head, cwd);
    if (!baseCommit || !headCommit) return unknownResult('invalid-or-unresolvable-git-range:all');
    try {
        const output = execFileSync('git', [
            'diff', '--name-status', '-z', '--find-renames', '--find-copies-harder',
            baseCommit, headCommit,
        ], { cwd, encoding: 'buffer', stdio: ['ignore', 'pipe', 'ignore'] });
        return classifyChanges(parseNameStatusZ(output));
    } catch {
        return unknownResult('git-diff-failure-or-malformed-output:all');
    }
}

function parseArguments(argv) {
    const options = { head: 'HEAD', all: false };
    for (let index = 0; index < argv.length; index += 1) {
        const argument = argv[index];
        if (argument === '--base') options.base = argv[++index];
        else if (argument === '--head') options.head = argv[++index];
        else if (argument === '--github-output') options.githubOutput = argv[++index];
        else if (argument === '--all') options.all = true;
        else throw new Error(`Unknown argument: ${argument}`);
    }
    return options;
}

function emit(result, githubOutput) {
    const lines = ['web', 'android', 'ios', 'unknown', 'docs_only']
        .map((key) => `${key}=${String(result[key])}`);
    lines.push(`summary=${JSON.stringify(result.reasons)}`);
    const output = `${lines.join('\n')}\n`;
    if (githubOutput) appendFileSync(githubOutput, output, 'utf8');
    process.stdout.write(output);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    const options = parseArguments(process.argv.slice(2));
    const result = options.all
        ? { ...platformResult(), web: true, android: true, ios: true, docs_only: false, reasons: ['manual-dispatch:all'] }
        : classifyGitRange(options.base, options.head);
    emit(result, options.githubOutput);
}
