#!/usr/bin/env python3
"""Read-only native artifact fingerprint; prints hashes, never runtime values."""
import argparse
import hashlib
import json
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
