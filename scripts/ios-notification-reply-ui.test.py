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
from ios_notification_reply_ui import validate_request, patch_test_plan, read_phase, notification_payload, run_notification_reply, verify_notification_reply_outcome


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
            def inject(*args, **kwargs):
                self.assertTrue((directory / 'injection-started.json').exists())
                self.assertEqual(json.loads(kwargs['input'])['conversation_id'], 'sb:123')
            with patch('ios_notification_reply_ui.subprocess.Popen', return_value=process) as launch, \
                    patch('ios_notification_reply_ui.subprocess.run', side_effect=inject) as push, \
                    patch('ios_notification_reply_ui.read_phase', side_effect=lambda *args: next(phases)), \
                    patch('ios_notification_reply_ui.time.sleep'):
                receipt = run_notification_reply(worker, self.request, 'candidate-fixture')
            self.assertEqual(push.call_count, 1)
            command = launch.call_args.args[0]
            self.assertEqual(command[command.index('-test-iterations') + 1], '1')
            self.assertNotIn('-retry-tests-on-failure', command)
            self.assertFalse(receipt['backendVerified'])
            self.assertEqual(receipt['replyMarker'], markers['text'])
            self.assertTrue((directory / 'ui-receipt.json').exists())
            with self.assertRaises(Exception):
                run_notification_reply(worker, self.request, 'candidate-fixture')

    def test_outcome_requires_the_same_previously_submitted_fixture(self):
        for previous in (None, {**self.request, 'uiVerified': False},
                         {**self.request, 'uiVerified': True, 'threadId': '124'}):
            worker = SimpleNamespace(installed=self.installed, run_id=self.request['runId'], seen=set(),
                pending_owned_read=None, native_login=None, notification_reply=previous)
            with self.assertRaises(Exception):
                verify_notification_reply_outcome(worker,
                    {**self.request, 'action': 'notification-reply-outcome'}, 'candidate-fixture')


if __name__ == '__main__':
    unittest.main()
