#!/usr/bin/env python3
"""Mac-only leased session transport. JSON lines on stdin; receipts on stdout.

No credentials in arguments/environment. Any failure ends the worker without a
success receipt and preserves private input for the Windows recovery journal.
The caller must reconcile such a failure; it must never blindly retry a step.
"""
import argparse
import fcntl
import json
import os
from pathlib import Path
import plistlib
import subprocess
import sys
import time
import uuid

SIMULATOR = 'F2E1EA50-FBAD-443C-A98F-2A576C14C70B'
METHOD = 'testOwnedDeepLinkSessionStep'
PROBES = ['testPrivateExchangeRejectsReplayAndMixedReceiptWithoutKeychainWrites',
          'testIsolatedSessionImportRefusesReplacementAndClearsOnlyExactSession']


def require(condition):
    if not condition:
        raise RuntimeError('unverified')


def write_private(path, data):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, 'wb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


class Worker:
    def __init__(self, root, products):
        self.root = root.resolve(strict=True)
        self.products = products.resolve(strict=True)
        require(self.products.is_relative_to(self.root))
        os.chdir(self.root)
        lock_path = Path.home() / '.quata' / ('simulator-' + SIMULATOR + '.lock')
        lock_path.parent.mkdir(exist_ok=True)
        self.lock = os.open(lock_path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        fcntl.flock(self.lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        originals = list(self.products.glob('*.xctestrun'))
        require(len(originals) == 1)
        self.original = originals[0]
        self.run_id = None
        self.installed = None
        self.seen = set()
        self.last_chat = None

    def call(self, arguments, timeout=60):
        subprocess.run(arguments, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=timeout)

    def state(self):
        devices = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'devices', '--json'], timeout=30))
        matches = [d for group in devices['devices'].values() for d in group if d['udid'] == SIMULATOR]
        require(len(matches) == 1 and matches[0]['name'] == 'Quata-FLOW-DEEP-LINKS-iOS18')
        return matches[0]['state']

    def stop(self):
        require(subprocess.run(['pgrep', '-x', 'xcodebuild'], capture_output=True, timeout=15).returncode == 1)
        if self.state() == 'Booted':
            self.call(['xcrun', 'simctl', 'shutdown', SIMULATOR])
        require(self.state() == 'Shutdown')

    def execute(self, request):
        action = request.get('action')
        if action == 'chat':
            return self.observe_chat(request)
        if action == 'close':
            require(set(request) == {'action'} and self.installed is None)
            self.stop()
            return {'closed': True}
        require(action in ('probe', 'session'))
        require(set(request) == ({'action', 'runId', 'stepId'} if action == 'probe' else {'action', 'input'}))
        data = request if action == 'probe' else request['input']
        run_id, step_id = data['runId'], data['stepId']
        require(str(uuid.UUID(run_id)) == run_id.lower() and str(uuid.UUID(step_id)) == step_id.lower())
        require(self.run_id in (None, run_id) and step_id not in self.seen)
        self.run_id = run_id
        self.seen.add(step_id)
        if action == 'session':
            require(data['stage'] in ('install', 'clear'))
            if data['stage'] == 'install':
                require(self.installed is None)
            else:
                require(self.installed is not None)
                require({k: v for k, v in data.items() if k not in ('stage', 'stepId')} == self.installed)
        else:
            require(self.installed is None)
        self.stop()
        directory = self.root / 'build/reports/ios' / ('deep-link-session-' + step_id)
        directory.mkdir(mode=0o700)
        directory = directory.resolve(strict=True)
        if action == 'session':
            write_private(directory / 'input.json', json.dumps(data).encode())
        plan = plistlib.loads(self.original.read_bytes())
        targets = [t for c in plan.get('TestConfigurations', []) for t in c.get('TestTargets', [])
                   if t.get('BlueprintName', t.get('TestTargetName')) == 'QuataIosTests']
        if isinstance(plan.get('QuataIosTests'), dict):
            targets.append(plan['QuataIosTests'])
        require(len(targets) == 1)
        target = targets[0]
        target['CommandLineArguments'] = ['-quata-ui-test-fixture', 'anonymous']
        environment = target.setdefault('EnvironmentVariables', {})
        require(not any(key.startswith('QUATA_IOS_') for key in environment))
        methods = PROBES if action == 'probe' else [METHOD]
        if action == 'probe':
            environment['QUATA_IOS_DEEP_LINK_KEYCHAIN_PROBE'] = '1'
        else:
            environment['QUATA_IOS_DEEP_LINK_SESSION_E2E'] = '1'
            environment['QUATA_IOS_DEEP_LINK_SESSION_DIRECTORY'] = str(directory)
        target['OnlyTestIdentifiers'] = ['QuataIosDeepLinkSessionTests/' + method for method in methods]
        patched = self.products / ('deep-link-session-' + step_id + '.xctestrun')
        write_private(patched, plistlib.dumps(plan))
        self.call(['xcrun', 'simctl', 'boot', SIMULATOR])
        self.call(['xcrun', 'simctl', 'bootstatus', SIMULATOR, '-b'], timeout=180)
        log = directory / 'tests.log'
        command = ['python3', 'scripts/run-ios-command-watchdog.py', '--timeout-seconds', '600', '--log', str(log), '--',
                   'xcodebuild', 'test-without-building', '-xctestrun', str(patched),
                   '-destination', 'platform=iOS Simulator,id=' + SIMULATOR, '-parallel-testing-enabled', 'NO',
                   '-resultBundlePath', str(directory / 'tests.xcresult')]
        command += ['-only-testing:QuataIosTests/QuataIosDeepLinkSessionTests/' + method for method in methods]
        self.call(command, timeout=720)
        for method in methods:
            self.call(['python3', 'scripts/check-ios-xctest-executed.py', '--method', method,
                       '--log', str(log), '--require-terminal-success-marker'])
        self.stop()
        if action == 'session':
            receipt = json.loads((directory / 'receipt.json').read_text())
            require(receipt == {'runId': run_id, 'stepId': step_id, 'stage': data['stage'], 'verified': True})
            (directory / 'input.json').unlink()
            self.installed = ({k: v for k, v in data.items() if k not in ('stage', 'stepId')}
                              if data['stage'] == 'install' else None)
        else:
            receipt = {'runId': run_id, 'stepId': step_id, 'probe': True, 'verified': True}
        patched.rename(directory / 'executed-plan.xctestrun')
        return receipt

    def app_pid(self):
        output = subprocess.check_output(['xcrun', 'simctl', 'spawn', SIMULATOR, 'launchctl', 'list'], timeout=30).decode()
        rows = [line.split() for line in output.splitlines() if 'UIKitApplication:com.quata.ios[' in line]
        require(len(rows) <= 1)
        return int(rows[0][0]) if rows and rows[0][0].isdigit() else None

    def observe_chat(self, request):
        target_mode = request.get('targetMode')
        require(target_mode in (None, 'missing-thread'))
        expected_keys = {'action', 'runId', 'stepId', 'mode', 'threadId', 'messageId', 'body'}
        if target_mode is not None:
            expected_keys.add('targetMode')
        require(set(request) == expected_keys)
        require(self.installed is not None and request['runId'] == self.run_id)
        step = request['stepId']
        require(str(uuid.UUID(step)) == step.lower() and step not in self.seen)
        require(request['mode'] in ('cold', 'warm'))
        require(all(isinstance(request[key], str) and request[key].isascii() and request[key].isdigit()
                    and 1 <= len(request[key]) <= 16 for key in ('threadId', 'messageId')))
        require(request['body'] == 'Deep link ' + self.run_id)
        self.seen.add(step)
        target = (request['threadId'], request['messageId'], target_mode)
        if request['mode'] == 'cold':
            require(self.last_chat is None)
            self.stop()
            self.call(['xcrun', 'simctl', 'boot', SIMULATOR])
            self.call(['xcrun', 'simctl', 'bootstatus', SIMULATOR, '-b'], timeout=180)
            require(self.app_pid() is None)
        else:
            require(self.last_chat is not None and self.last_chat['target'] == target)
            require(self.state() == 'Booted' and self.app_pid() == self.last_chat['pid'])
        directory = self.root / 'build/reports/ios' / ('deep-link-chat-' + step)
        directory.mkdir(mode=0o700)
        plan = plistlib.loads(self.original.read_bytes())
        targets = [t for c in plan.get('TestConfigurations', []) for t in c.get('TestTargets', [])
                   if t.get('BlueprintName', t.get('TestTargetName')) == 'QuataIosUITests']
        if isinstance(plan.get('QuataIosUITests'), dict):
            targets.append(plan['QuataIosUITests'])
        require(len(targets) == 1)
        env = targets[0].setdefault('EnvironmentVariables', {})
        require(not any(key.startswith('QUATA_IOS_') for key in env))
        env.update({'QUATA_IOS_EXTERNAL_CHAT_E2E': '1', 'QUATA_IOS_EXTERNAL_CHAT_THREAD': target[0],
                    'QUATA_IOS_EXTERNAL_CHAT_MESSAGE': target[1], 'QUATA_IOS_EXTERNAL_CHAT_BODY': request['body'],
                    'QUATA_IOS_EXTERNAL_CHAT_STEP': step})
        method = ('testObserveDeliveredMissingChatAndBack' if target_mode == 'missing-thread'
                  else 'testObserveDeliveredChatMessageAndBack')
        if target_mode is not None:
            env['QUATA_IOS_EXTERNAL_CHAT_TARGET_MODE'] = target_mode
        selected = 'QuataIosExternalChatLinkUITests/' + method
        targets[0]['OnlyTestIdentifiers'] = [selected]
        patched = self.products / ('deep-link-chat-' + step + '.xctestrun')
        write_private(patched, plistlib.dumps(plan))
        url = 'quata://egquata.com/#chat-sb%3A' + target[0] + '?message=' + target[1]
        log = directory / 'tests.log'
        observer = subprocess.Popen(['python3', 'scripts/run-ios-command-watchdog.py', '--timeout-seconds', '240', '--log', str(log), '--',
                   'xcodebuild', 'test-without-building', '-xctestrun', str(patched),
                   '-destination', 'platform=iOS Simulator,id=' + SIMULATOR, '-parallel-testing-enabled', 'NO',
                   '-resultBundlePath', str(directory / 'tests.xcresult'), '-only-testing:QuataIosUITests/' + selected],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # Retain only fixed phase names and process IDs on failure, never the
        # request/session, URL, exception text or subprocess output.
        diagnostic = {'stepId': step, 'phase': 'waiting_ready'}
        try:
            marker = 'QUATA_DEEP_LINK_CHAT_OBSERVER_READY:' + step
            deadline = time.monotonic() + 120
            ready = False
            while observer.poll() is None and time.monotonic() < deadline:
                if log.exists() and marker in log.read_text(errors='replace').splitlines():
                    ready = True
                    break
                time.sleep(0.1)
            require(ready)
            diagnostic['phase'] = 'checking_pre_delivery_pid'
            # Starting the observer must not launch/relaunch the product itself.
            diagnostic['preDeliveryPid'] = self.app_pid()
            require(diagnostic['preDeliveryPid'] == (None if request['mode'] == 'cold' else self.last_chat['pid']))
            diagnostic['phase'] = 'openurl'
            self.call(['xcrun', 'simctl', 'openurl', SIMULATOR, url])
            diagnostic['phase'] = 'waiting_app_pid'
            deadline = time.monotonic() + 30
            pid = self.app_pid()
            while pid is None and time.monotonic() < deadline:
                time.sleep(0.25)
                pid = self.app_pid()
            require(pid is not None)
            diagnostic['deliveredPid'] = pid
            if request['mode'] == 'warm':
                require(pid == self.last_chat['pid'])
            diagnostic['phase'] = 'waiting_observer_terminal'
        finally:
            try:
                exit_code = observer.wait(timeout=300)
                diagnostic['observerExitCode'] = exit_code
            finally:
                write_private(directory / 'delivery-diagnostic.json', json.dumps(diagnostic).encode())
        require(exit_code == 0)
        self.call(['python3', 'scripts/check-ios-xctest-executed.py', '--method', method,
                   '--log', str(log), '--require-terminal-success-marker'])
        require(subprocess.run(['pgrep', '-x', 'xcodebuild'], capture_output=True, timeout=15).returncode == 1)
        require(self.app_pid() == pid)
        self.last_chat = {'target': target, 'pid': pid}
        receipt = {'runId': self.run_id, 'stepId': step, 'mode': request['mode'], 'passed': True}
        if target_mode is not None:
            receipt['targetMode'] = target_mode
        write_private(directory / 'delivery.json', json.dumps({**receipt, 'pid': pid, 'url': url,
                      'observerReadyBeforeDelivery': True, 'coldHadNoAppPid': request['mode'] == 'cold',
                      'pidUnchangedThroughObservation': True}).encode())
        patched.rename(directory / 'executed-plan.xctestrun')
        return receipt


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--products', type=Path, required=True)
    args = parser.parse_args()
    worker = Worker(args.root, args.products)
    print(json.dumps({'ready': True, 'simulator': SIMULATOR}), flush=True)
    while True:
        line = sys.stdin.buffer.readline(32769)
        require(line and len(line) <= 32768 and line.endswith(b'\n'))
        response = worker.execute(json.loads(line))
        print(json.dumps(response), flush=True)
        if response.get('closed'):
            return


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('{"error":"deep_link_ios_worker_unresolved"}', flush=True)
        sys.exit(1)
