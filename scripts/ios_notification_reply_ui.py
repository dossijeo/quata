"""Single-attempt simulator Reply UI step under the existing native worker lease.

The caller owns the authenticated fixture and backend journal. This receipt proves
only UI submission, never APNs delivery, message persistence or fixture cleanup.
Failures preserve the attempt directory; no automatic retry is permitted.
"""
import json
import os
from pathlib import Path
import plistlib
import signal
import stat
import subprocess
import time
import uuid

METHOD = 'testReplyThroughTheSystemNotification'
TARGET = 'QuataIosUITests'
CLASS = 'QuataIosNotificationReplyUITests'


def require(condition):
    if not condition:
        raise RuntimeError('notification_reply_ui_unverified')


def exclusive_write(path, value):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, 'wb') as stream:
        stream.write(value)
        stream.flush()
        os.fsync(stream.fileno())
    directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def validate_request(request, installed, run_id, seen):
    require(set(request) == {'action', 'runId', 'stepId', 'profileId', 'threadId'})
    require(request['action'] == 'notification-reply' and installed is not None)
    for field in ('runId', 'stepId', 'profileId'):
        require(isinstance(request[field], str) and str(uuid.UUID(request[field])) == request[field])
    require(request['runId'] == run_id == installed['runId'])
    require(request['profileId'] == installed['profileId'] and request['stepId'] not in seen)
    thread = request['threadId']
    require(isinstance(thread, str) and thread.isascii() and thread.isdigit()
            and str(int(thread)) == thread and 0 < int(thread) <= 9223372036854775807)
    return {
        'notification': 'qadata-reply-alert-' + request['stepId'],
        'text': 'qadata-reply-text-' + request['stepId'],
    }


def patch_test_plan(plan, directory, markers):
    targets = [target for config in plan.get('TestConfigurations', [])
               for target in config.get('TestTargets', [])
               if target.get('BlueprintName', target.get('TestTargetName')) == TARGET]
    if isinstance(plan.get(TARGET), dict):
        targets.append(plan[TARGET])
    require(len(targets) == 1)
    target = targets[0]
    environment = target.setdefault('EnvironmentVariables', {})
    require(not any(key.startswith('QUATA_IOS_') for key in environment))
    target['OnlyTestIdentifiers'] = [CLASS + '/' + METHOD]
    environment.update({
        'QUATA_IOS_NOTIFICATION_REPLY_UI_E2E': '1',
        'QUATA_IOS_REPLY_NOTIFICATION_MARKER': markers['notification'],
        'QUATA_IOS_REPLY_TEXT_MARKER': markers['text'],
        'QUATA_IOS_REPLY_COORDINATOR_DIRECTORY': str(directory),
    })
    return plan


def read_phase(directory, marker):
    path = directory / 'ui-phase.json'
    try:
        descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    except FileNotFoundError:
        return None
    with os.fdopen(descriptor, 'rb') as stream:
        info = os.fstat(stream.fileno())
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid()
                and 0 < info.st_size <= 2048)
        value = json.loads(stream.read(2049))
    require(set(value) == {'phase', 'marker'} and value['marker'] == marker)
    require(value['phase'] in ('ready-for-notification', 'submitted-by-system-ui'))
    return value['phase']


def notification_payload(request, markers):
    return {'aps': {'alert': {'title': 'QADATA Reply', 'body': markers['notification']},
                    'category': 'QUATA_CHAT_MESSAGE'},
            'conversation_id': 'sb:' + request['threadId'], 'recipient_profile_id': request['profileId']}


def run_notification_reply(worker, request, simulator):
    markers = validate_request(request, worker.installed, worker.run_id, worker.seen)
    require(worker.pending_owned_read is None and worker.native_login is None)
    worker.seen.add(request['stepId'])
    # Same candidate-only lease and no overlapping xcodebuild checks as session steps.
    worker.prepare_cold_app()
    directory = worker.root / 'build/reports/ios' / ('quata-ios-reply-' + request['stepId'])
    directory.mkdir(mode=0o700)
    exclusive_write(directory / 'intent.json', json.dumps({**request, **markers}).encode())
    patched = worker.products / ('notification-reply-' + request['stepId'] + '.xctestrun')
    plan = patch_test_plan(plistlib.loads(worker.original.read_bytes()), directory, markers)
    exclusive_write(patched, plistlib.dumps(plan))
    command = ['xcodebuild', 'test-without-building', '-xctestrun', str(patched),
               '-destination', 'platform=iOS Simulator,id=' + simulator,
               '-parallel-testing-enabled', 'NO', '-test-iterations', '1',
               '-only-testing:' + TARGET + '/' + CLASS + '/' + METHOD,
               '-resultBundlePath', str(directory / 'tests.xcresult')]
    log_path = directory / 'tests.log'
    descriptor = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    injected = False
    with os.fdopen(descriptor, 'wb') as log:
        process = subprocess.Popen(command, cwd=worker.root, stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        try:
            exclusive_write(directory / 'process.json', json.dumps({'pid': process.pid}).encode())
            deadline = time.monotonic() + 240
            while process.poll() is None:
                require(time.monotonic() < deadline)
                phase = read_phase(directory, markers['notification'])
                if phase == 'ready-for-notification' and not injected:
                    # Persist intent before push. An uncertain simctl result is never retried.
                    exclusive_write(directory / 'injection-started.json', b'{"started":true}')
                    injected = True
                    subprocess.run(['xcrun', 'simctl', 'push', simulator, 'com.quata.ios', '-'],
                                   input=json.dumps(notification_payload(request, markers)).encode(),
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, timeout=30)
                if phase == 'submitted-by-system-ui':
                    require(injected)
                time.sleep(0.25)
            require(process.returncode == 0 and injected)
        except BaseException:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=10)
            raise
    require(read_phase(directory, markers['notification']) == 'submitted-by-system-ui')
    worker.call(['python3', 'scripts/check-ios-xctest-executed.py', '--method', METHOD,
                 '--log', str(log_path), '--require-terminal-success-marker'])
    receipt = {'runId': request['runId'], 'stepId': request['stepId'],
               'submittedBySystemUi': True, 'backendVerified': False,
               'replyMarker': markers['text'], 'notificationMarker': markers['notification']}
    exclusive_write(directory / 'ui-receipt.json', json.dumps(receipt).encode())
    patched.unlink()
    return receipt
