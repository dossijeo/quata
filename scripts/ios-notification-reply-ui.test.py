"""Synthetic coordinator tests; no login, simulator, push or backend mutations."""
import copy
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid
import plistlib
import signal
import subprocess
from ios_notification_reply_ui import validate_request, patch_test_plan, read_phase, notification_payload, run_notification_reply, verify_notification_reply_outcome, close_owned_process, inject_once


class ReplyCoordinatorTests(unittest.TestCase):
    def setUp(self):
        self.request = {key: str(uuid.uuid4()) for key in ('runId', 'stepId', 'profileId')}
        self.request.update(action='notification-reply', threadId='123')
        self.installed = {key: self.request[key] for key in ('runId', 'profileId')}

    def validate(self, request=None, installed=None, seen=None):
        return validate_request(request or self.request, installed or self.installed,
                                self.request['runId'], seen or set())

    def test_actor_run_thread_and_attempt_are_bound_before_execution(self):
        markers = self.validate()
        self.assertEqual(markers['text'], 'qadata-reply-text-' + self.request['stepId'])
        for field, value in [('profileId', str(uuid.uuid4())), ('runId', str(uuid.uuid4())),
                             ('threadId', '0'), ('threadId', '01'), ('threadId', '1;echo x'),
                             ('threadId', '9223372036854775808'), ('threadId', 123),
                             ('password', 'must-not-be-accepted')]:
            with self.subTest(field=field, value=value), self.assertRaises(Exception):
                self.validate({**self.request, field: value})
        with self.assertRaises(Exception):
            self.validate(seen={self.request['stepId']})
        with self.assertRaises(Exception):
            validate_request(self.request, None, self.request['runId'], set())

    def test_plan_requires_one_clean_ui_target(self):
        plan = {'TestConfigurations': [{'TestTargets': [{'BlueprintName': 'QuataIosUITests'}]}]}
        result = patch_test_plan(copy.deepcopy(plan), Path('/private/quata-ios-reply-fixture'), self.validate())
        target = result['TestConfigurations'][0]['TestTargets'][0]
        self.assertEqual(target['OnlyTestIdentifiers'],
                         ['QuataIosNotificationReplyUITests/testReplyThroughTheSystemNotification'])
        self.assertEqual(len(target['EnvironmentVariables']), 4)
        for bad in ({}, {'QuataIosUITests': {}, **plan},
                    {'QuataIosUITests': {'EnvironmentVariables': {'QUATA_IOS_AUTH_E2E_FILE': '/private/stale'}}}):
            with self.assertRaises(Exception):
                patch_test_plan(bad, Path('/private/quata-ios-reply-fixture'), self.validate())

    def test_handshake_rejects_foreign_marker_extra_fields_and_unknown_phase(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            self.assertIsNone(read_phase(directory, 'expected'))
            phase = directory / 'ui-phase.json'
            for value in ({'marker': 'foreign', 'phase': 'ready-for-notification'},
                          {'marker': 'expected', 'phase': 'delivered'},
                          {'marker': 'expected', 'phase': 'ready-for-notification', 'extra': True}):
                phase.write_text(json.dumps(value))
                with self.assertRaises(Exception):
                    read_phase(directory, 'expected')
            for state in ('ready-for-notification', 'submitted-by-system-ui'):
                phase.write_text(json.dumps({'marker': 'expected', 'phase': state}))
                self.assertEqual(read_phase(directory, 'expected'), state)

    def test_injection_has_real_category_and_exact_recipient_without_secrets(self):
        payload = notification_payload(self.request, self.validate())
        self.assertEqual(set(payload), {'aps', 'conversation_id', 'recipient_profile_id'})
        self.assertEqual(payload['aps']['category'], 'QUATA_CHAT_MESSAGE')
        self.assertEqual(payload['conversation_id'], 'sb:123')
        self.assertEqual(payload['recipient_profile_id'], self.request['profileId'])

    def test_repeated_ready_phase_injects_only_once_and_receipt_cannot_claim_delivery(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'build/reports/ios').mkdir(parents=True)
            products = root / 'products'
            products.mkdir()
            original = products / 'original.xctestrun'
            original.write_bytes(plistlib.dumps({'QuataIosUITests': {}}))
            worker = SimpleNamespace(root=root, products=products, original=original,
                installed=self.installed, run_id=self.request['runId'], seen=set(),
                pending_owned_read=None, native_login=None, prepare_cold_app=lambda: None,
                call=lambda command: None)
            directory = root / 'build/reports/ios' / ('quata-ios-reply-' + self.request['stepId'])
            process = SimpleNamespace(pid=123, returncode=0)
            polls = iter([None, None, None, 0])
            process.poll = lambda: next(polls)
            markers = self.validate()
            phases = iter(['ready-for-notification', 'ready-for-notification',
                           'submitted-by-system-ui', 'submitted-by-system-ui'])
            def inject(directory_arg, simulator, payload):
                self.assertTrue((directory / 'injection-started.json').exists())
                self.assertEqual(payload['conversation_id'], 'sb:123')
            with patch('ios_notification_reply_ui.subprocess.Popen', return_value=process) as launch, \
                    patch('ios_notification_reply_ui.inject_once', side_effect=inject) as push, \
                    patch('ios_notification_reply_ui.close_owned_process') as close, \
                    patch('ios_notification_reply_ui.read_phase', side_effect=lambda *args: next(phases)), \
                    patch('ios_notification_reply_ui.time.sleep'):
                receipt = run_notification_reply(worker, self.request, 'candidate-fixture')
            self.assertEqual(push.call_count, 1)
            close.assert_called_once_with(process, directory)
            self.assertTrue(launch.call_args.kwargs['start_new_session'])
            command = launch.call_args.args[0]
            self.assertNotIn('-test-iterations', command)
            self.assertNotIn('-retry-tests-on-failure', command)
            self.assertNotIn('-run-tests-until-failure', command)
            self.assertFalse(receipt['backendVerified'])
            self.assertEqual(receipt['replyMarker'], markers['text'])
            self.assertTrue((directory / 'ui-receipt.json').exists())
            with self.assertRaises(Exception):
                run_notification_reply(worker, self.request, 'candidate-fixture')

    def test_exited_leader_does_not_hide_surviving_group(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            process = SimpleNamespace(pid=123, poll=lambda: 0)
            signals = []
            alive = True
            def killpg(pid, value):
                nonlocal alive
                self.assertEqual(pid, 123)
                if not alive:
                    raise ProcessLookupError()
                if value:
                    signals.append(value)
                    alive = False
            with patch('ios_notification_reply_ui.os.killpg', side_effect=killpg):
                close_owned_process(process, directory)
            self.assertEqual(signals, [signal.SIGTERM])
            self.assertTrue(json.loads((directory / 'process-closed.json').read_text())['groupAbsent'])

    def test_surviving_child_requires_kill_even_after_leader_exit(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            process = SimpleNamespace(pid=123, poll=lambda: 0)
            signals = []
            alive = True
            def killpg(pid, value):
                nonlocal alive
                self.assertEqual(pid, 123)
                if not alive:
                    raise ProcessLookupError()
                if value:
                    signals.append(value)
                    if value == signal.SIGKILL:
                        alive = False
            with patch('ios_notification_reply_ui.os.killpg', side_effect=killpg), \
                    patch('ios_notification_reply_ui.time.monotonic', side_effect=[0, 11, 12]):
                close_owned_process(process, directory)
            self.assertEqual(signals, [signal.SIGTERM, signal.SIGKILL])
            self.assertTrue((directory / 'process-closed.json').exists())

    def test_transient_permission_probe_does_not_prevent_reaping(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            polls = 0
            signals = []
            def poll():
                nonlocal polls
                polls += 1
                return None if polls == 1 else -signal.SIGTERM
            def killpg(pid, value):
                self.assertEqual(pid, 123)
                if value:
                    signals.append(value)
                elif polls == 1:
                    raise PermissionError()
                elif polls >= 2:
                    raise ProcessLookupError()
            with patch('ios_notification_reply_ui.os.killpg', side_effect=killpg), \
                    patch('ios_notification_reply_ui.time.sleep'):
                close_owned_process(SimpleNamespace(pid=123, poll=poll), directory)
            self.assertEqual(signals, [signal.SIGTERM])
            self.assertEqual(polls, 2)
            self.assertTrue(json.loads((directory / 'process-closed.json').read_text())['groupAbsent'])

    def test_persistent_permission_probe_never_proves_absence(self):
        class Retained(BaseException):
            pass
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            signals = []
            def killpg(pid, value):
                self.assertEqual(pid, 123)
                if value:
                    signals.append(value)
                else:
                    raise PermissionError()
            with patch('ios_notification_reply_ui.os.killpg', side_effect=killpg), \
                    patch('ios_notification_reply_ui.time.monotonic', side_effect=[0, 11, 12, 23]), \
                    patch('ios_notification_reply_ui.retain_process_custody', side_effect=Retained()) as retain:
                with self.assertRaises(Retained):
                    close_owned_process(SimpleNamespace(pid=123, poll=lambda: 0), directory)
            self.assertEqual(signals, [signal.SIGTERM, signal.SIGKILL])
            retain.assert_called_once_with(directory, 123)
            self.assertFalse((directory / 'process-closed.json').exists())

    def test_uncertain_group_or_receipt_retains_custody(self):
        class Retained(BaseException):
            pass
        for mode in ('permission', 'survivor', 'receipt'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                process = SimpleNamespace(pid=123, poll=lambda: 0)
                def killpg(pid, value):
                    self.assertEqual(pid, 123)
                    if mode == 'permission':
                        raise PermissionError()
                    if mode == 'receipt':
                        raise ProcessLookupError()
                with patch('ios_notification_reply_ui.os.killpg', side_effect=killpg), \
                        patch('ios_notification_reply_ui.time.monotonic', side_effect=[0, 11, 12, 23]), \
                        patch('ios_notification_reply_ui.exclusive_write', side_effect=OSError()), \
                        patch('ios_notification_reply_ui.retain_process_custody', side_effect=Retained()) as retain:
                    with self.assertRaises(Retained):
                        close_owned_process(process, directory)
                retain.assert_called_once_with(directory, 123)
                self.assertFalse((directory / 'process-closed.json').exists())

    def test_push_timeout_still_closes_its_distinct_owned_group(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            process = SimpleNamespace(pid=456)
            def communicate(**kwargs):
                self.assertEqual(json.loads(kwargs['input'])['conversation_id'], 'sb:123')
                raise subprocess.TimeoutExpired('owned-push', 30)
            process.communicate = communicate
            with patch('ios_notification_reply_ui.subprocess.Popen', return_value=process) as launch, \
                    patch('ios_notification_reply_ui.close_owned_process') as close:
                with self.assertRaises(subprocess.TimeoutExpired):
                    inject_once(directory, 'candidate-fixture', notification_payload(self.request, self.validate()))
            self.assertTrue(launch.call_args.kwargs['start_new_session'])
            close.assert_called_once_with(process, directory / 'push-process')

    def test_outcome_requires_the_same_previously_submitted_fixture(self):
        for previous in (None, {**self.request, 'uiVerified': False},
                         {**self.request, 'uiVerified': True, 'threadId': '124'}):
            worker = SimpleNamespace(installed=self.installed, run_id=self.request['runId'], seen=set(),
                pending_owned_read=None, native_login=None, notification_reply=previous)
            with self.assertRaises(Exception):
                verify_notification_reply_outcome(worker,
                    {**self.request, 'action': 'notification-reply-outcome'}, 'candidate-fixture')

    def test_failure_observation_rejects_a_success_receipt_and_preserves_plan(self):
        for wrong_receipt in (False, True):
            with self.subTest(wrong_receipt=wrong_receipt), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                (root / 'build/reports/ios').mkdir(parents=True)
                products = root / 'products'
                products.mkdir()
                original = products / 'original.xctestrun'
                original.write_bytes(plistlib.dumps({'QuataIosTests': {}}))
                request = {**self.request, 'action': 'notification-reply-failure', 'stepId': str(uuid.uuid4())}
                expected = {'runId': request['runId'], 'stepId': request['stepId'],
                            'failedNotificationObserved': True, 'notificationRemoved': False,
                            'backendVerified': False, 'retriesVerified': False}
                commands = []
                def call(command, **kwargs):
                    commands.append(command)
                    if 'xcodebuild' in command:
                        directory = root / 'build/reports/ios' / ('quata-ios-reply-failure-' + request['stepId'])
                        plan = plistlib.loads(Path(command[command.index('-xctestrun') + 1]).read_bytes())
                        target = plan['QuataIosTests']
                        self.assertEqual(target['OnlyTestIdentifiers'], [
                            'QuataIosNotificationReplyOutcomeTests/testFailedReplyLeavesTheOwnedFailureNotification'])
                        self.assertEqual(target['EnvironmentVariables'], {'QUATA_IOS_REPLY_FAILURE_DIRECTORY': str(directory)})
                        receipt = ({'runId': request['runId'], 'stepId': request['stepId'], 'notificationRemoved': True}
                                   if wrong_receipt else expected)
                        (directory / 'outcome-receipt.json').write_text(json.dumps(receipt))
                worker = SimpleNamespace(root=root, products=products, original=original,
                    installed=self.installed, run_id=self.request['runId'], seen={self.request['stepId']},
                    pending_owned_read=None, native_login=None, prepare_cold_app=lambda: None,
                    notification_reply={**self.request, 'uiVerified': True}, call=call)
                if wrong_receipt:
                    with self.assertRaises(Exception):
                        verify_notification_reply_outcome(worker, request, 'candidate-fixture')
                else:
                    self.assertEqual(verify_notification_reply_outcome(worker, request, 'candidate-fixture'), expected)
                patched = products / ('notification-reply-failure-' + request['stepId'] + '.xctestrun')
                self.assertEqual(patched.exists(), wrong_receipt)
                self.assertEqual(len([command for command in commands if 'xcodebuild' in command]), 1)
                self.assertNotIn('-retry-tests-on-failure', commands[0])

    def test_failure_clear_requires_observation_before_any_native_command(self):
        for observation in (None, {}, {'failedNotificationObserved': True, 'notificationRemoved': True}):
            worker = SimpleNamespace(installed=self.installed, run_id=self.request['runId'], seen=set(),
                pending_owned_read=None, native_login=None,
                notification_reply={**self.request, 'uiVerified': True, 'failureObservation': observation})
            with self.subTest(observation=observation), self.assertRaises(Exception):
                verify_notification_reply_outcome(worker,
                    {**self.request, 'action': 'notification-reply-failure-clear'}, 'candidate-fixture')
            self.assertNotIn('failureClearStarted', worker.notification_reply)
            self.assertEqual(worker.seen, set())

    def test_observe_then_clear_binds_method_receipt_and_preserves_failed_plan(self):
        for corruption in (None, 'runId', 'stepId', 'notificationRemoved', 'extra', 'backendVerified'):
            with self.subTest(corruption=corruption), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                (root / 'build/reports/ios').mkdir(parents=True)
                products = root / 'products'
                products.mkdir()
                original = products / 'original.xctestrun'
                original.write_bytes(plistlib.dumps({'QuataIosTests': {}}))
                observation = {**self.request, 'action': 'notification-reply-failure', 'stepId': str(uuid.uuid4())}
                clearing = {**self.request, 'action': 'notification-reply-failure-clear', 'stepId': str(uuid.uuid4())}
                receipts = {}
                commands = []

                def call(command, **kwargs):
                    commands.append(command)
                    if 'xcodebuild' not in command:
                        return
                    is_clear = any('testRemoveOnlyTheObservedOwnedFailureNotification' in arg for arg in command)
                    request = clearing if is_clear else observation
                    stage = 'failure-clear' if is_clear else 'failure'
                    directory = root / 'build/reports/ios' / ('quata-ios-reply-' + stage + '-' + request['stepId'])
                    method = ('testRemoveOnlyTheObservedOwnedFailureNotification' if is_clear
                              else 'testFailedReplyLeavesTheOwnedFailureNotification')
                    identifier = 'QuataIosNotificationReplyOutcomeTests/' + method
                    plan = plistlib.loads(Path(command[command.index('-xctestrun') + 1]).read_bytes())
                    self.assertEqual(plan['QuataIosTests']['OnlyTestIdentifiers'], [identifier])
                    self.assertEqual(plan['QuataIosTests']['EnvironmentVariables'],
                                     {'QUATA_IOS_REPLY_FAILURE_DIRECTORY': str(directory)})
                    self.assertIn('-only-testing:QuataIosTests/' + identifier, command)
                    self.assertEqual(json.loads((directory / 'input.json').read_text()),
                                     {key: value for key, value in request.items() if key != 'action'})
                    for flag in ('-test-iterations', '-retry-tests-on-failure', '-run-tests-until-failure'):
                        self.assertNotIn(flag, command)
                    receipt = {'runId': request['runId'], 'stepId': request['stepId'],
                               'failedNotificationObserved': True, 'notificationRemoved': is_clear,
                               'backendVerified': False, 'retriesVerified': False}
                    receipts[stage] = copy.deepcopy(receipt)
                    if is_clear and corruption:
                        receipt[corruption] = (str(uuid.uuid4()) if corruption in ('runId', 'stepId')
                                               else False if corruption == 'notificationRemoved' else True)
                    (directory / 'outcome-receipt.json').write_text(json.dumps(receipt))

                worker = SimpleNamespace(root=root, products=products, original=original,
                    installed=self.installed, run_id=self.request['runId'], seen={self.request['stepId']},
                    pending_owned_read=None, native_login=None, prepare_cold_app=lambda: None,
                    notification_reply={**self.request, 'uiVerified': True}, call=call)
                observed = verify_notification_reply_outcome(worker, observation, 'candidate-fixture')
                self.assertEqual(worker.notification_reply['failureObservation'], observed)
                if corruption:
                    with self.assertRaises(Exception):
                        verify_notification_reply_outcome(worker, clearing, 'candidate-fixture')
                else:
                    self.assertEqual(verify_notification_reply_outcome(worker, clearing, 'candidate-fixture'),
                                     receipts['failure-clear'])
                self.assertEqual(worker.notification_reply['failureObservation'], observed)
                self.assertTrue(worker.notification_reply['failureClearStarted'])
                patched = products / ('notification-reply-failure-clear-' + clearing['stepId'] + '.xctestrun')
                self.assertEqual(patched.exists(), corruption is not None)
                command_count = len(commands)
                # A new step ID cannot turn an uncertain or successful clear into a second mutation.
                with self.assertRaises(Exception):
                    verify_notification_reply_outcome(worker, {**clearing, 'stepId': str(uuid.uuid4())}, 'candidate-fixture')
                self.assertEqual(len(commands), command_count)
                self.assertEqual(len([command for command in commands if 'xcodebuild' in command]), 2)

    def test_outcome_selects_one_method_without_repetition_flags(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'build/reports/ios').mkdir(parents=True)
            products = root / 'products'
            products.mkdir()
            original = products / 'original.xctestrun'
            original.write_bytes(plistlib.dumps({'QuataIosTests': {}}))
            request = {**self.request, 'action': 'notification-reply-outcome', 'stepId': str(uuid.uuid4())}
            commands = []
            expected = {'runId': request['runId'], 'stepId': request['stepId'], 'notificationRemoved': True}
            def call(command, **kwargs):
                commands.append(command)
                if 'xcodebuild' in command:
                    directory = root / 'build/reports/ios' / ('quata-ios-reply-outcome-' + request['stepId'])
                    (directory / 'outcome-receipt.json').write_text(json.dumps(expected))
            worker = SimpleNamespace(root=root, products=products, original=original,
                installed=self.installed, run_id=self.request['runId'], seen={self.request['stepId']},
                pending_owned_read=None, native_login=None, prepare_cold_app=lambda: None,
                notification_reply={**self.request, 'uiVerified': True}, call=call)
            self.assertEqual(verify_notification_reply_outcome(worker, request, 'candidate-fixture'), expected)
            command = commands[0]
            for flag in ('-test-iterations', '-retry-tests-on-failure', '-run-tests-until-failure'):
                self.assertNotIn(flag, command)
            self.assertEqual(command[command.index('-parallel-testing-enabled') + 1], 'NO')
            self.assertEqual(len([arg for arg in command if arg.startswith('-only-testing:')]), 1)


if __name__ == '__main__':
    unittest.main()
