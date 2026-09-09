#!/usr/bin/env python3
"""One focal XCTest step. Private JSON stdin/stdout; never attach stdout to a console.

The Windows coordinator journals the step/path before run, verifies the receipt,
then calls release. Raw xcresult/logs stay private until evidence review and cleanup.
Status observes an existing step; it never restarts it. Uncertainty retains the lock.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import plistlib
import signal
import shutil
import stat
import subprocess
import sys
import uuid


def require(value):
    if not value:
        raise ValueError('focal_step_unverified')


def sync_dir(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def write_private(path, value):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as output:
        output.write(json.dumps(value).encode())
        output.flush()
        os.fsync(output.fileno())
    sync_dir(path.parent)


def read_private(path):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as source:
        info = os.fstat(source.fileno())
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == 0o600)
        require(0 < info.st_size <= 32768)
        return json.loads(source.read(32769))


def private_directory(path, create=False):
    require(path.resolve() == path)
    if create:
        path.mkdir(mode=0o700, exist_ok=True)
    info = path.lstat()
    require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == 0o700)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['run', 'status', 'release', 'capture', 'purge', 'stop'])
    parser.add_argument('--worktree', required=True)
    parser.add_argument('--simulator', required=True)
    options = parser.parse_args()
    root = Path(options.worktree)
    require(root.is_absolute() and root.resolve() == root and (root / 'iosApp/project.yml').is_file())
    require(str(uuid.UUID(options.simulator)).upper() == options.simulator.upper())
    raw = sys.stdin.buffer.read(32769)
    require(len(raw) <= 32768)
    payload = json.loads(raw)
    identity = {key: payload[key] for key in ['runId', 'stepId', 'stage', 'profileId', 'authUserId']}
    for key in ['runId', 'stepId', 'profileId', 'authUserId']:
        require(str(uuid.UUID(identity[key])) == identity[key])
    stage = identity['stage']
    require(stage in ['login', 'logout', 'empty', 'identity', 'clear-owned', 'open', 'configure', 'read', 'recover'])
    base = root / 'build/reports/ios'
    require(base.resolve() == base and base.is_dir())
    run = base / ('recovery-private-' + identity['runId'])
    private_directory(run, create=options.action == 'run')
    report = run / ('step-' + identity['stepId'])
    exchange = run / ('recovery-secret-' + identity['stepId'])
    products = root / 'build/ios-intel-simulator-signed-derived-data/Build/Products'
    plan_path = products / ('Recovery-' + identity['stepId'] + '.xctestrun')
    lock = base / 'recovery-ios-active.json'

    def result():
        private_directory(report)
        require(read_private(report / 'owner.json') == identity)
        if not (report / 'terminal.json').exists():
            return {'terminal': False, 'exitCode': None}
        terminal = read_private(report / 'terminal.json')
        if terminal['terminal'] and terminal['exitCode'] == 0:
            private_directory(exchange)
            receipt = read_private(exchange / 'receipt.json')
            require({key: receipt[key] for key in identity} == identity)
            terminal['receipt'] = receipt
        return terminal

    if options.action == 'status':
        return result()
    if options.action == 'capture':
        completed = result()
        require(completed['terminal'] and completed['exitCode'] == 0)
        names = {'open': 'recovery-secret-account-before-configure',
            'read': 'recovery-secret-account-read-answer-empty', 'recover': 'recovery-secret-login-return'}
        require(stage in names)
        exported = report / 'attachments'
        require(not exported.exists())
        subprocess.run(['xcrun', 'xcresulttool', 'export', 'attachments', '--path', str(report / 'result.xcresult'),
            '--output-path', str(exported)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60, check=True)
        manifest = json.loads((exported / 'manifest.json').read_bytes())
        matches = [item for group in manifest for item in group.get('attachments', [])
            if item.get('deviceId') == options.simulator and item.get('isAssociatedWithFailure') is False
            and item.get('suggestedHumanReadableName', '').startswith(names[stage] + '_')
            and item.get('exportedFileName', '').endswith('.png')]
        require(len(matches) == 1)
        artifact = exported / matches[0]['exportedFileName']
        require(artifact.parent == exported and artifact.resolve() == artifact and artifact.stat().st_size <= 8 * 1024 * 1024)
        png = artifact.read_bytes()
        require(png.startswith(b'\x89PNG\r\n\x1a\n'))
        return {'name': names[stage], 'sha256': hashlib.sha256(png).hexdigest(), 'png': base64.b64encode(png).decode()}
    if options.action == 'stop':
        private_directory(report)
        require(stage == 'empty' and read_private(report / 'owner.json') == identity
            and read_private(report / 'released.json') == identity
            and read_private(report / 'terminal.json') == {'terminal': True, 'exitCode': 0}
            and not exchange.exists() and not lock.exists())
        for bundle in ['com.quata.ios', 'com.quata.ios.uitests.xctrunner']:
            def terminate():
                return subprocess.run(['xcrun', 'simctl', 'terminate', options.simulator, bundle],
                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=10)
            stopped = terminate()
            if stopped.returncode == 0:
                stopped = terminate()  # Confirm absence after a successful termination.
            require(stopped.returncode != 0 and b'domain=NSPOSIXErrorDomain, code=3' in stopped.stderr
                and b'found nothing to terminate' in stopped.stderr)
        return {'hostsStopped': True}
    if options.action == 'purge':
        private_directory(report)
        require(read_private(report / 'owner.json') == identity and read_private(report / 'released.json') == identity)
        terminal = read_private(report / 'terminal.json')
        require(terminal == {'terminal': True, 'exitCode': 0} and not exchange.exists() and not lock.exists())
        require(payload.get('evidenceReviewed') is True)
        # The coordinator has retained the safe screenshots and terminal summary.
        # Remove only this successful step's private automatic artifacts.
        log_hash = hashlib.sha256((report / 'test.log').read_bytes()).hexdigest()
        for name in ['result.xcresult', 'attachments', 'test.log']:
            artifact = report / name
            require(artifact.resolve() == artifact and artifact.parent == report)
            if artifact.is_dir():
                shutil.rmtree(artifact)
            elif artifact.exists():
                artifact.unlink()
        write_private(report / 'purged.json', {'identity': identity, 'testLogSha256': log_hash})
        return {'purged': True, 'testLogSha256': log_hash}
    if options.action == 'release':
        completed = result()
        require(completed['terminal'] and completed['exitCode'] == 0)
        require(read_private(lock) == identity)
        require(sorted(item.name for item in exchange.iterdir()) == ['input.json', 'receipt.json', 'started'])
        for name in ['input.json', 'receipt.json', 'started']:
            require(not (exchange / name).is_symlink())
            (exchange / name).unlink()
        exchange.rmdir()
        require(plan_path.resolve() == plan_path)
        plan_path.unlink()
        write_private(report / 'released.json', identity)
        lock.unlink()
        sync_dir(base)
        sync_dir(run)
        return {'released': True}

    # A durable lock prevents overlapping actors and automatic replay after failure.
    write_private(lock, identity)
    report.mkdir(mode=0o700)
    write_private(report / 'owner.json', identity)
    exchange.mkdir(mode=0o700)
    write_private(exchange / 'input.json', payload)
    sources = [path for path in products.glob('*.xctestrun') if not path.name.startswith('Recovery-')]
    require(len(sources) == 1)
    plan = plistlib.loads(sources[0].read_bytes())
    ui = stage in ['open', 'configure', 'read', 'recover']
    target_name = 'QuataIosUITests' if ui else 'QuataIosTests'
    test_class = 'QuataIosRecoverySecretUITests' if ui else 'QuataIosRecoverySecretSessionTests'
    method = 'testRecoverySecretUIStep' if ui else 'testRecoverySessionStep'
    targets = [(key, value) for key, value in plan.items() if isinstance(value, dict)]
    targets += [('', target) for config in plan.get('TestConfigurations', []) for target in config.get('TestTargets', [])]
    matched = 0
    for hint, target in targets:
        names = [hint, target.get('TestTargetName'), target.get('BlueprintName')]
        if target_name in names:
            target.setdefault('EnvironmentVariables', {}).update(QUATA_IOS_RECOVERY_SECRET_E2E='1',
                QUATA_IOS_RECOVERY_SECRET_STEP_DIRECTORY=str(exchange))
            matched += 1
    require(matched == 1)
    with plan_path.open('xb') as output:
        plistlib.dump(plan, output)
    # The exclusive unit lock is held; ensure the selected host starts in a new process.
    stopped = subprocess.run(['xcrun', 'simctl', 'terminate', options.simulator, 'com.quata.ios'],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
    require(stopped.returncode == 0 or
        (b'domain=NSPOSIXErrorDomain, code=3' in stopped.stderr and b'found nothing to terminate' in stopped.stderr))
    command = ['xcodebuild', 'test-without-building', '-xctestrun', str(plan_path),
        '-destination', 'platform=iOS Simulator,id=' + options.simulator,
        '-only-testing:' + '/'.join([target_name, test_class, method]),
        '-parallel-testing-enabled', 'NO', '-collect-test-diagnostics', 'never',
        '-resultBundlePath', str(report / 'result.xcresult')]
    timed_out = False
    with (report / 'test.log').open('xb') as log:
        process = subprocess.Popen(command, cwd=root, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        write_private(report / 'process.json', {'pid': process.pid})
        try:
            code = process.wait(timeout=300)
        except subprocess.TimeoutExpired:
            timed_out = True
            # Only this command group; no global process dumps or unrelated kills.
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=10)
            code = 124
    log_path = report / 'test.log'
    require(log_path.stat().st_size <= 16 * 1024 * 1024)
    log_text = log_path.read_text(errors='replace')
    passed = code == 0 and (test_class + ' ' + method + "]' passed") in log_text and '** TEST EXECUTE SUCCEEDED **' in log_text
    terminal = {'terminal': not timed_out, 'exitCode': code if not code == 0 or passed else 1}
    write_private(report / 'terminal.json', terminal)
    return result()


if __name__ == '__main__':
    try:
        print(json.dumps(main()))
    except Exception:
        # Private files and the lock remain for explicit reconciliation.
        print(json.dumps({'error': 'recovery_ios_step_unverified'}))
        sys.exit(1)
