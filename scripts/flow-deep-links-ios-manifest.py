#!/usr/bin/env python3
"""Read-only native artifact fingerprint; prints hashes, never runtime values."""
import argparse
import hashlib
import json
import plistlib
from pathlib import Path
import subprocess


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def bundle(path):
    files = []
    for item in sorted(path.rglob('*')):
        if item.is_file():
            if not item.resolve().is_relative_to(path.resolve()):
                raise RuntimeError('external_bundle_reference')
            files.append([str(item.relative_to(path)), digest(item)])
    if not files:
        raise RuntimeError('empty_bundle')
    return hashlib.sha256(json.dumps(files, separators=(',', ':')).encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--products', type=Path, required=True)
    args = parser.parse_args()
    root, products = args.root.resolve(strict=True), args.products.resolve(strict=True)
    if not products.is_relative_to(root):
        raise RuntimeError('invalid_products')
    result = {'productSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, timeout=15).decode().strip(),
              'appBundleSha256': bundle(products / 'SimulatorSigned-iphonesimulator/QuataIos.app'),
              'uiRunnerBundleSha256': bundle(products / 'SimulatorSigned-iphonesimulator/QuataIosUITests-Runner.app')}
    info = plistlib.loads((products / 'SimulatorSigned-iphonesimulator/QuataIos.app/Info.plist').read_bytes())
    for key, label in [('QUATA_SUPABASE_URL', 'builtBackendUrlSha256'), ('QUATA_SUPABASE_PUBLISHABLE_KEY', 'builtPublicKeySha256')]:
        value = info[key]
        if not isinstance(value, str) or not value or '$(' in value:
            raise RuntimeError('runtime_not_expanded')
        result[label] = hashlib.sha256(value.encode()).hexdigest()
    result['productSourcesClean'] = subprocess.run(['git', 'diff', '--quiet', 'HEAD', '--', 'feature', 'core', 'app',
        'ios-shared', 'iosApp/iosApp', 'iosApp/iosShareQueue', 'iosApp/iosShareExtension', 'gradle',
        'build.gradle.kts', 'settings.gradle.kts', 'third_party', ':(exclude,glob)**/src/commonTest/**'], cwd=root, timeout=15).returncode == 0
    plans = list(products.glob('*.xctestrun'))
    if len(plans) != 1:
        raise RuntimeError('ambiguous_test_plan')
    plan = plistlib.loads(plans[0].read_bytes())
    app = products / 'SimulatorSigned-iphonesimulator/QuataIos.app'
    runner = products / 'SimulatorSigned-iphonesimulator/QuataIosUITests-Runner.app'
    for name, host in [('QuataIosTests', app), ('QuataIosUITests', runner)]:
        targets = [t for c in plan.get('TestConfigurations', []) for t in c.get('TestTargets', [])
                   if t.get('BlueprintName', t.get('TestTargetName')) == name]
        if isinstance(plan.get(name), dict):
            targets.append(plan[name])
        if len(targets) != 1:
            raise RuntimeError('ambiguous_test_target')
        target = targets[0]
        def resolved(value):
            return Path(value.replace('__TESTROOT__', str(products)).replace('__TESTHOST__', str(host))).resolve(strict=True)
        if resolved(target['TestHostPath']) != host.resolve(strict=True) or not host.resolve().is_relative_to(products):
            raise RuntimeError('unexpected_test_host')
        expected_bundle = host / 'PlugIns' / (name + '.xctest')
        if resolved(target['TestBundlePath']) != expected_bundle.resolve(strict=True) or not expected_bundle.resolve().is_relative_to(host.resolve()):
            raise RuntimeError('unexpected_test_bundle')
        if name == 'QuataIosUITests' and resolved(target['UITargetAppPath']) != app.resolve(strict=True):
            raise RuntimeError('unexpected_ui_target')
    result['testPlanSha256'] = digest(plans[0])
    result['testPlanTargetsVerified'] = True
    for name, relative in {
        'sessionSourceSha256': 'iosApp/iosAppTests/QuataIosDeepLinkSessionTests.swift',
        'observerSourceSha256': 'iosApp/iosAppUITests/QuataIosExternalChatLinkUITests.swift',
        'workerSha256': 'scripts/flow-deep-links-ios-worker.py',
        'publicRuntimeSha256': 'iosApp/Configuration/QuataPublicRuntime.local.xcconfig',
    }.items():
        result[name] = digest(root / relative)
    print(json.dumps(result))


if __name__ == '__main__':
    main()
