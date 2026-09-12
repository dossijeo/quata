#!/usr/bin/env python3
"""Mac-only leased session transport. JSON lines on stdin; receipts on stdout.

No credentials in arguments/environment. Any failure ends the worker without a
success receipt and preserves private input for the Windows recovery journal.
The caller must reconcile such a failure; it must never blindly retry a step.
"""
import argparse
import base64
import fcntl
import json
import os
from pathlib import Path
import plistlib
import subprocess
import sys
import stat
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


def read_private(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, 'rb') as stream:
        info = os.fstat(stream.fileno())
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid()
                and stat.S_IMODE(info.st_mode) == 0o600 and 0 < info.st_size <= 32768)
        value = stream.read(32769)
        require(len(value) <= 32768)
        return json.loads(value)


def owned_read_session(data, receipt):
    require(set(receipt) == {'runId', 'stepId', 'stage', 'verified', 'privateSession'}
            and receipt['verified'] is True
            and all(receipt[key] == data[key] for key in ('runId', 'stepId', 'stage')))
    session = receipt['privateSession']
    require(set(session) == {'profileId', 'authUserId', 'authSessionId', 'accessToken', 'refreshToken',
                            'expiresAt', 'email', 'displayName', 'isOfficial'})
    require(all(session[key] == data[key] for key in ('profileId', 'authUserId')))
    require(str(uuid.UUID(session['authSessionId'])) == session['authSessionId'])
    require(all(isinstance(session[key], str) and session[key] for key in ('accessToken', 'refreshToken', 'email', 'displayName')))
    require(type(session['expiresAt']) is int and 0 < session['expiresAt'] <= 9007199254740991
            and type(session['isOfficial']) is bool)
    parts = session['accessToken'].split('.')
    require(len(parts) == 3)
    claims = json.loads(base64.urlsafe_b64decode(parts[1] + '=' * (-len(parts[1]) % 4)))
    require(claims.get('sub') == session['authUserId'] and claims.get('session_id') == session['authSessionId']
            and type(claims.get('exp')) is int and claims['exp'] == session['expiresAt'])
    return session


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
        self.pending_owned_read = None
        self.seen = set()
        self.last_chat = None
        self.native_gate = None
        self.native_gate_started = False
        self.native_login = None

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
        if action == 'read-ack':
            require(set(request) == {'action', 'runId', 'stepId'} and self.pending_owned_read is not None)
            pending = self.pending_owned_read
            require(request['runId'] == self.run_id and request['stepId'] == pending['stepId'])
            require(read_private(pending['directory'] / 'input.json') == pending['input'])
            require({'runId': self.run_id, **owned_read_session(pending['input'], read_private(pending['directory'] / 'private-response.json'))} == self.installed)
            # The caller has durably journaled this private response before ACK.
            # Until then it remains available for reconciliation after pipe loss.
            for name in ('private-response.json', 'input.json'):
                (pending['directory'] / name).unlink()
            descriptor = os.open(pending['directory'], os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
            self.pending_owned_read = None
            return {'runId': self.run_id, 'stepId': request['stepId'], 'acknowledged': True}
        require(self.pending_owned_read is None)
        if action == 'native-login':
            return self.observe_native_login(request)
        if action in ('chat', 'native-gate'):
            return self.observe_chat(request)
        if action == 'close':
            require(set(request) == {'action'} and self.installed is None and self.pending_owned_read is None)
            require(self.native_login is None or self.native_login['state'] == 'cleared')
            self.stop()
            return {'closed': True}
        require(action in ('probe', 'session'))
        if self.native_login is not None and self.native_login['state'] != 'cleared':
            require(action == 'session' and request.get('input', {}).get('stage') in ('read-owned', 'clear'))
        require(set(request) == ({'action', 'runId', 'stepId'} if action == 'probe' else {'action', 'input'}))
        data = request if action == 'probe' else request['input']
        run_id, step_id = data['runId'], data['stepId']
        require(str(uuid.UUID(run_id)) == run_id.lower() and str(uuid.UUID(step_id)) == step_id.lower())
        require(self.run_id in (None, run_id) and step_id not in self.seen)
        self.run_id = run_id
        self.seen.add(step_id)
        if action == 'session':
            require(data['stage'] in ('install', 'clear', 'install-expired', 'clear-expired', 'read-owned') and self.pending_owned_read is None)
            if data['stage'] in ('install-expired', 'clear-expired'):
                require(type(data.get('originalExpiresAt')) is int and type(data.get('expiresAt')) is int
                        and 0 < data['expiresAt'] < data['originalExpiresAt'])
            else:
                require('originalExpiresAt' not in data)
            if data['stage'] in ('install', 'install-expired', 'read-owned'):
                expiry_read = data['stage'] == 'read-owned' and self.installed is not None
                if expiry_read:
                    require('originalExpiresAt' in self.installed and self.native_login is None)
                    require(all(data[key] == self.installed[key] for key in ('runId', 'profileId', 'authUserId')))
                else:
                    require(self.installed is None)
                if data['stage'] == 'read-owned':
                    require(self.native_login is None or self.native_login['state'] == 'observed')
                    if self.native_login is not None:
                        require(all(data[key] == self.native_login[key] for key in ('profileId', 'authUserId')))
                    require(set(data) == {'runId', 'stepId', 'stage', 'profileId', 'authUserId'})
                    require(all(str(uuid.UUID(data[key])) == data[key] for key in ('profileId', 'authUserId')))
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
        owned_read = action == 'session' and data['stage'] == 'read-owned'
        methods = PROBES if action == 'probe' else ['testReadOwnedNativeSession' if owned_read else METHOD]
        if action == 'probe':
            environment['QUATA_IOS_DEEP_LINK_KEYCHAIN_PROBE'] = '1'
        else:
            environment['QUATA_IOS_DEEP_LINK_OWNED_READ' if owned_read else 'QUATA_IOS_DEEP_LINK_SESSION_E2E'] = '1'
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
            if owned_read:
                receipt = read_private(directory / 'private-response.json')
                session = owned_read_session(data, receipt)
                if self.installed is not None:
                    # Preserve the original installed receipt until the new private
                    # snapshot is structurally bound to the same Auth session.
                    require('originalExpiresAt' in self.installed)
                    require(all(session[key] == self.installed[key]
                                for key in ('profileId', 'authUserId', 'authSessionId')))
                self.installed = {'runId': run_id, **session}
                self.pending_owned_read = {'stepId': step_id, 'directory': directory, 'input': data}
            else:
                receipt = json.loads((directory / 'receipt.json').read_text())
                require(receipt == {'runId': run_id, 'stepId': step_id, 'stage': data['stage'], 'verified': True})
                (directory / 'input.json').unlink()
                self.installed = ({k: v for k, v in data.items() if k not in ('stage', 'stepId')}
                                  if data['stage'] in ('install', 'install-expired') else None)
                if data['stage'] == 'clear' and self.native_login is not None:
                    self.native_login['state'] = 'cleared'
        else:
            receipt = {'runId': run_id, 'stepId': step_id, 'probe': True, 'verified': True}
        patched.rename(directory / 'executed-plan.xctestrun')
        return receipt

    def app_pid(self):
        output = subprocess.check_output(['xcrun', 'simctl', 'spawn', SIMULATOR, 'launchctl', 'list'], timeout=30).decode()
        rows = [line.split() for line in output.splitlines() if 'UIKitApplication:com.quata.ios[' in line]
        require(len(rows) <= 1)
        return int(rows[0][0]) if rows and rows[0][0].isdigit() else None

    def observe_native_login(self, request):
        require(set(request) == {'action', 'input'} and self.native_gate is not None
                and self.native_login is None and self.installed is None)
        data = request['input']
        require(set(data) == {'runId', 'stepId', 'ticketId', 'profileId', 'authUserId',
                              'countryCode', 'phone', 'password', 'messageId'})
        require(all(isinstance(data[key], str) and str(uuid.UUID(data[key])) == data[key]
                    for key in ('runId', 'stepId', 'ticketId', 'profileId', 'authUserId')))
        require(data['runId'] == self.run_id and data['stepId'] not in self.seen
                and data['messageId'] == self.native_gate['target'][1] and data['countryCode'] == '240')
        require(isinstance(data['phone'], str) and data['phone'].isascii() and data['phone'].isdigit()
                and 8 <= len(data['phone']) <= 15 and isinstance(data['password'], str)
                and 12 <= len(data['password']) <= 128)
        require(self.state() == 'Booted' and self.app_pid() == self.native_gate['pid'])
        require(subprocess.run(['pgrep', '-x', 'xcodebuild'], capture_output=True, timeout=15).returncode == 1)
        step = data['stepId']
        self.seen.add(step)
        self.native_login = {'state': 'started', 'stepId': step,
                             'profileId': data['profileId'], 'authUserId': data['authUserId']}
        directory = self.root / 'build/reports/ios' / ('recovery-secret-' + step)
        directory.mkdir(mode=0o700)
        private_input = {key: value for key, value in data.items() if key != 'messageId'}
        private_input['stage'] = 'login'
        write_private(directory / 'input.json', json.dumps(private_input).encode())
        plan = plistlib.loads(self.original.read_bytes())
        targets = [t for c in plan.get('TestConfigurations', []) for t in c.get('TestTargets', [])
                   if t.get('BlueprintName', t.get('TestTargetName')) == 'QuataIosUITests']
        if isinstance(plan.get('QuataIosUITests'), dict):
            targets.append(plan['QuataIosUITests'])
        require(len(targets) == 1)
        target = targets[0]
        target['CommandLineArguments'] = []
        env = target.setdefault('EnvironmentVariables', {})
        require(not any(key.startswith('QUATA_IOS_') for key in env))
        env.update({'QUATA_IOS_NATIVE_CHAT_LOGIN_E2E': '1', 'QUATA_IOS_NATIVE_CHAT_LOGIN_DIRECTORY': str(directory),
                    'QUATA_IOS_EXTERNAL_CHAT_RUN': self.run_id, 'QUATA_IOS_EXTERNAL_CHAT_STEP': step,
                    'QUATA_IOS_EXTERNAL_CHAT_THREAD': self.native_gate['target'][0],
                    'QUATA_IOS_EXTERNAL_CHAT_MESSAGE': data['messageId']})
        method = 'testResumeDeliveredChatAfterNativeLogin'
        selected = 'QuataIosNativeChatLoginUITests/' + method
        target['OnlyTestIdentifiers'] = [selected]
        patched = self.products / ('native-login-' + step + '.xctestrun')
        write_private(patched, plistlib.dumps(plan))
        log = directory / 'tests.log'
        self.call(['python3', 'scripts/run-ios-command-watchdog.py', '--timeout-seconds', '240', '--log', str(log), '--',
                   'xcodebuild', 'test-without-building', '-xctestrun', str(patched),
                   '-destination', 'platform=iOS Simulator,id=' + SIMULATOR, '-parallel-testing-enabled', 'NO',
                   '-resultBundlePath', str(directory / 'tests.xcresult'), '-only-testing:QuataIosUITests/' + selected], timeout=300)
        self.call(['python3', 'scripts/check-ios-xctest-executed.py', '--method', method,
                   '--log', str(log), '--require-terminal-success-marker'])
        require(subprocess.run(['pgrep', '-x', 'xcodebuild'], capture_output=True, timeout=15).returncode == 1)
        require(self.app_pid() == self.native_gate['pid'])
        receipt = read_private(directory / 'receipt.json')
        require(receipt == {'runId': self.run_id, 'stepId': step, 'stage': 'login',
                           'profileId': data['profileId'], 'authUserId': data['authUserId'],
                           'result': {'passed': True, 'submitCount': 1, 'messageId': data['messageId'],
                                      'clipboardCleared': True, 'postExitObservationMs': 2000}})
        require((directory / 'started').is_file())
        (directory / 'input.json').unlink()
        descriptor = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        patched.rename(directory / 'executed-plan.xctestrun')
        self.native_login['state'] = 'observed'
        return {'runId': self.run_id, 'stepId': step, 'passed': True}

    def observe_chat(self, request):
        native_gate = request['action'] == 'native-gate'
        renewal_prelude = request.get('renewalPrelude') is True
        target_mode = request.get('targetMode')
        require(target_mode in (None, 'missing-thread', 'missing-message'))
        expected_keys = {'action', 'runId', 'stepId', 'mode', 'threadId', 'messageId', 'body'}
        if renewal_prelude:
            expected_keys.add('renewalPrelude')
            require(not native_gate and target_mode is None and request['mode'] == 'warm'
                    and self.last_chat is None and self.installed is not None
                    and 'originalExpiresAt' in self.installed)
        if native_gate:
            expected_keys.remove('body')
            require(target_mode is None and not self.native_gate_started and self.installed is None)
        if target_mode is not None:
            expected_keys.add('targetMode')
        if target_mode == 'missing-message':
            expected_keys.add('visibleMessageId')
        require(set(request) == expected_keys)
        if target_mode == 'missing-message':
            visible = request['visibleMessageId']
            require(isinstance(visible, str) and visible.isascii() and visible.isdigit()
                    and 1 <= len(visible) <= 16 and not visible.startswith('0')
                    and visible != request['messageId'])
        require((native_gate or self.installed is not None) and request['runId'] == self.run_id)
        step = request['stepId']
        require(str(uuid.UUID(step)) == step.lower() and step not in self.seen)
        require(request['mode'] in ('cold', 'warm'))
        require(all(isinstance(request[key], str) and request[key].isascii() and request[key].isdigit()
                    and 1 <= len(request[key]) <= 16 for key in ('threadId', 'messageId')))
        if not native_gate:
            require(request['body'] == 'Deep link ' + self.run_id)
        self.seen.add(step)
        target = (request['threadId'], request['messageId'], target_mode, request.get('visibleMessageId'))
        if native_gate:
            self.native_gate_started = True
        prelude_pid = None
        if request['mode'] == 'cold' or native_gate or renewal_prelude:
            require(self.last_chat is None)
            self.stop()
            self.call(['xcrun', 'simctl', 'boot', SIMULATOR])
            self.call(['xcrun', 'simctl', 'bootstatus', SIMULATOR, '-b'], timeout=180)
            require(self.app_pid() is None)
            if (native_gate and request['mode'] == 'warm') or renewal_prelude:
                # A public product launch warms this dedicated process before
                # external delivery; no route or authentication fixture arguments.
                self.call(['xcrun', 'simctl', 'launch', SIMULATOR, 'com.quata.ios'])
                prelude_pid = self.app_pid()
                require(prelude_pid is not None)
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
        env.update({('QUATA_IOS_NATIVE_CHAT_GATE_E2E' if native_gate else 'QUATA_IOS_EXTERNAL_CHAT_E2E'): '1', 'QUATA_IOS_EXTERNAL_CHAT_THREAD': target[0],
                    'QUATA_IOS_EXTERNAL_CHAT_MESSAGE': target[1], 'QUATA_IOS_EXTERNAL_CHAT_BODY': 'Deep link ' + self.run_id,
                    'QUATA_IOS_EXTERNAL_CHAT_STEP': step})
        method = ('testObserveDeliveredMissingChatAndBack' if target_mode == 'missing-thread'
                  else 'testObserveDeliveredMissingMessageAndBack' if target_mode == 'missing-message'
                  else 'testObserveDeliveredChatMessageAndBack')
        if target_mode == 'missing-message':
            env['QUATA_IOS_EXTERNAL_CHAT_VISIBLE_MESSAGE'] = request['visibleMessageId']
        if target_mode is not None:
            env['QUATA_IOS_EXTERNAL_CHAT_TARGET_MODE'] = target_mode
        selected = 'QuataIosExternalChatLinkUITests/' + method
        if native_gate:
            method = 'testObserveDeliveredNativeLoginGate'
            selected = 'QuataIosNativeChatLoginUITests/' + method
        targets[0]['OnlyTestIdentifiers'] = [selected]
        patched = self.products / ('deep-link-chat-' + step + '.xctestrun')
        write_private(patched, plistlib.dumps(plan))
        url = 'quata://egquata.com/#chat-sb%3A' + target[0] + '?message=' + target[1]
        log = directory / 'tests.log'
        expected_pid = self.app_pid() if request['mode'] == 'warm' else None
        if renewal_prelude:
            require(expected_pid == prelude_pid)
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
            require(diagnostic['preDeliveryPid'] == expected_pid)
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
                require(pid == expected_pid)
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
        if native_gate:
            self.native_gate = {'target': target, 'pid': pid, 'runId': self.run_id}
        receipt = {'runId': self.run_id, 'stepId': step, 'mode': request['mode'], 'passed': True}
        if renewal_prelude:
            receipt['renewalPrelude'] = True
        if target_mode is not None:
            receipt['targetMode'] = target_mode
        write_private(directory / 'delivery.json', json.dumps({**receipt, 'pid': pid, 'url': url,
                      'observerReadyBeforeDelivery': True, 'coldHadNoAppPid': request['mode'] == 'cold',
                      'pidUnchangedThroughObservation': True,
                      **({'publicPreludePid': prelude_pid} if renewal_prelude else {})}).encode())
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
