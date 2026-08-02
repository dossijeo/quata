#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const ALL_PLATFORMS = Object.freeze(['web', 'android', 'ios']);

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

export function classifyPaths(paths) {
    const result = platformResult();

    for (const rawPath of paths) {
        const path = normalize(rawPath.trim());
        if (!path) continue;

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

        if (/^(README(?:\.[^/]+)?|LICENSE|CONTRIBUTING(?:\.[^/]+)?|docs\/wiki\/|docs\/.*\.md$)/i.test(path)) {
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
        if (/^(?:app\/|document-reader\/|.*\/src\/(?:android|androidHost|androidUnit)(?:Main|Test)\/)/.test(path)) {
            add(result, ['android'], `android:${path}`);
            continue;
        }
        if (/^(?:iosApp\/|ios-shared\/|.*\/src\/(?:ios|iosX64|iosArm64|iosSimulatorArm64)(?:Main|Test)\/)/.test(path) || /\.swift$/.test(path)) {
            add(result, ['ios'], `ios:${path}`);
            continue;
        }

        if (/^(?:core|designsystem|feature)\/.*\/src\/(?:commonMain|commonTest)\//.test(path)) {
            all(result, `shared-source:${path}`);
            continue;
        }
        if (/^(?:core|designsystem|feature)\/.*\/build\.gradle\.kts$/.test(path)) {
            all(result, `shared-module-build:${path}`);
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

function changedPaths(base, head) {
    if (!base || /^0{40}$/.test(base)) return null;
    return execFileSync('git', ['diff', '--name-only', '--diff-filter=ACMR', base, head], {
        encoding: 'utf8',
    }).split(/\r?\n/).filter(Boolean);
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
    const paths = options.all ? null : changedPaths(options.base, options.head);
    const result = paths === null
        ? { ...platformResult(), web: true, android: true, ios: true, docs_only: false, reasons: ['manual-or-unknown-base:all'] }
        : classifyPaths(paths);
    emit(result, options.githubOutput);
}
