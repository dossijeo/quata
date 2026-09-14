"""Mac/Python local-only ordering checks; all simulator/Xcode calls are mocked."""
import importlib.util
import json
from pathlib import Path
import plistlib
import tempfile
import unittest
from unittest.mock import Mock, patch
import uuid
# The existing CI entry point also exercises the imported reader's privacy and
# exact-time contracts, without requiring another workflow job.
from test_ios_auth_refresh_rejection import RejectionTests

spec = importlib.util.spec_from_file_location('ios_worker', Path(__file__).with_name('flow-deep-links-ios-worker.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ColdPreparationTests(unittest.TestCase):
    def worker(self, states, pids):
        worker = module.Worker.__new__(module.Worker)
        worker.state = Mock(side_effect=states)
        worker.app_pid = Mock(side_effect=pids)
        worker.call = Mock()
        return worker

    def test_booted_without_app_preserves_os(self):
        worker = self.worker(['Booted', 'Booted'], [None, None])
        with patch.object(module.subprocess, 'run', return_value=Mock(returncode=1)):
            worker.prepare_cold_app()
        worker.call.assert_not_called()

    def test_booted_app_terminates_only_dedicated_product(self):
        worker = self.worker(['Booted', 'Booted'], [412, None])
        with patch.object(module.subprocess, 'run', return_value=Mock(returncode=1)):
            worker.prepare_cold_app()
        worker.call.assert_called_once_with(['xcrun', 'simctl', 'terminate', module.SIMULATOR, 'com.quata.ios'])

    def test_shutdown_boots_before_checking_app_absence(self):
        worker = self.worker(['Shutdown', 'Booted'], [None, None])
        with patch.object(module.subprocess, 'run', return_value=Mock(returncode=1)):
            worker.prepare_cold_app()
        self.assertEqual(worker.call.call_args_list, [
            unittest.mock.call(['xcrun', 'simctl', 'boot', module.SIMULATOR]),
            unittest.mock.call(['xcrun', 'simctl', 'bootstatus', module.SIMULATOR, '-b'], timeout=180)])

    def test_unexpected_state_or_busy_xcode_never_mutates(self):
        for states, returncode in ((['Booting'], 1), ([], 0), (['Booted'], 2)):
            worker = self.worker(states, [])
            with patch.object(module.subprocess, 'run', return_value=Mock(returncode=returncode)):
                with self.assertRaises(RuntimeError):
                    worker.prepare_cold_app()
            worker.call.assert_not_called()

    def test_persistent_pid_or_failed_termination_blocks(self):
        for fails in (False, True):
            worker = self.worker(['Booted', 'Booted'], [412, 412])
            if fails:
                worker.call.side_effect = RuntimeError('unverified')
            with patch.object(module.subprocess, 'run', return_value=Mock(returncode=1)):
                with self.assertRaises(RuntimeError):
                    worker.prepare_cold_app()
            self.assertEqual(worker.call.call_count, 1)


class ExpiredCustodyTests(unittest.TestCase):
    def trial(self, termination_fails=False):
        with tempfile.TemporaryDirectory() as folder:
            worker = module.Worker.__new__(module.Worker)
            worker.root = Path(folder)
            worker.products = worker.root / 'products'
            worker.products.mkdir()
            (worker.root / 'build/reports/ios').mkdir(parents=True)
            worker.original = worker.products / 'original.xctestrun'
            worker.original.write_bytes(plistlib.dumps({'QuataIosTests': {}}))
            worker.pending_owned_read = worker.native_login = worker.installed = worker.run_id = None
            worker.seen = set()
            data = {'runId': str(uuid.uuid4()), 'stepId': str(uuid.uuid4()),
                    'stage': 'install-expired', 'expiresAt': 1, 'originalExpiresAt': 2}
            directory = worker.root / 'build/reports/ios' / ('deep-link-session-' + data['stepId'])
            receipt = {key: data[key] for key in ('runId', 'stepId', 'stage')}
            receipt['verified'] = True
            events = []
            worker.stop = lambda: events.append('shutdown')

            def call(arguments, **kwargs):
                if 'scripts/run-ios-command-watchdog.py' in arguments:
                    (directory / 'receipt.json').write_text(json.dumps(receipt))

            def terminate():
                events.append('terminate')
                self.assertTrue((directory / 'input.json').exists())
                self.assertIsNone(worker.installed)
                if termination_fails:
                    raise RuntimeError('unverified')

            worker.call = call
            worker.terminate_app = terminate
            # These tests exercise receipt/ACK ordering on both host OSes;
            # private-file permissions remain covered by native custody tests.
            def write(path, content):
                path.write_bytes(content)

            with patch.object(module, 'write_private', side_effect=write):
                if termination_fails:
                    with self.assertRaises(RuntimeError):
                        worker.execute({'action': 'session', 'input': data})
                    self.assertTrue((directory / 'input.json').exists())
                    self.assertIsNone(worker.installed)
                    self.assertFalse((directory / 'executed-plan.xctestrun').exists())
                else:
                    self.assertEqual(worker.execute({'action': 'session', 'input': data}), receipt)
                    self.assertFalse((directory / 'input.json').exists())
                    self.assertEqual(worker.installed, {k: v for k, v in data.items() if k not in ('stage', 'stepId')})
                    self.assertTrue((directory / 'executed-plan.xctestrun').exists())
            self.assertEqual(events, ['shutdown', 'terminate'])

    def test_expired_install_terminates_host_before_ack_without_second_shutdown(self):
        self.trial()

    def test_failed_host_termination_retains_private_input_without_ack(self):
        self.trial(termination_fails=True)


class DeliveryOrderTests(unittest.TestCase):
    def trial(self, pre_delivery_pid=None, ready=True, target_mode=None, renewal_prelude=False, rejection=False, missing_http=False):
        with tempfile.TemporaryDirectory() as folder:
            worker = module.Worker.__new__(module.Worker)
            worker.root = Path(folder)
            worker.products = worker.root / 'products'
            worker.products.mkdir()
            (worker.root / 'build/reports/ios').mkdir(parents=True)
            worker.original = worker.products / 'original.xctestrun'
            worker.original.write_bytes(plistlib.dumps({'QuataIosUITests': {}}))
            worker.installed = {}
            worker.run_id = str(uuid.uuid4())
            worker.last_chat = None
            worker.seen = set()
            worker.native_rejection_started = False
            worker.native_gate = None
            worker.native_login = None
            request = {'action': 'chat', 'runId': worker.run_id, 'stepId': str(uuid.uuid4()), 'mode': 'cold',
                       'threadId': '123', 'messageId': '456', 'body': 'Deep link ' + worker.run_id}
            if target_mode is not None:
                request['targetMode'] = target_mode
            if target_mode == 'missing-message':
                request['visibleMessageId'] = '789'
            if renewal_prelude:
                request.update(mode='warm', renewalPrelude=True)
                worker.installed = {'originalExpiresAt': 2000000000}
            if rejection:
                request['action'] = 'native-rejection'
                worker.installed = {'originalExpiresAt': 2000000000}
            events = []
            worker.stop = lambda: events.append('stop')
            worker.prepare_cold_app = lambda: (events.append('prepare-cold-app'), module.require(worker.app_pid() is None))
            worker.call = lambda args, **kwargs: events.append(args[2] if args[:2] == ['xcrun', 'simctl'] else 'check')
            pids = iter([None, 412, 412, pre_delivery_pid if pre_delivery_pid is not None else 412, 412, 412]
                        if renewal_prelude else [None, pre_delivery_pid, 412, 412])
            worker.app_pid = lambda: next(pids)

            class Observer:
                def poll(self):
                    return None if ready else 1
                def wait(self, timeout):
                    events.append('wait-terminal')
                    return 0

            def start(args, **kwargs):
                events.append('observer-start')
                Path(args[args.index('--log') + 1]).write_text('QUATA_DEEP_LINK_CHAT_OBSERVER_READY:' + request['stepId'] + '\n')
                return Observer()

            def read_rejection(pid, started_at_ns, app_pid, *, diagnostic):
                events.append('http-witness')
                diagnostic.update(phase='select', outcome='failed' if missing_http else 'verified')
                self.assertEqual(pid, 412)
                self.assertIsInstance(started_at_ns, int)
                self.assertLess(events.index('wait-terminal'), events.index('http-witness'))
                if missing_http:
                    raise RuntimeError('unverified')
                return {'observed': True, 'pid': pid, 'status': 400, 'timestampNs': str(started_at_ns + 1),
                        'startedAtNs': str(started_at_ns), 'endedAtNs': str(started_at_ns + 2)}

            with patch.object(module.subprocess, 'Popen', side_effect=start), patch.object(module.subprocess, 'run') as run, \
                    patch.object(module, 'read_ios_refresh_rejection', side_effect=read_rejection):
                run.return_value.returncode = 1
                if pre_delivery_pid is not None or not ready or missing_http:
                    with self.assertRaises(RuntimeError):
                        worker.observe_chat(request)
                    if not missing_http:
                        self.assertNotIn('openurl', events)
                    else:
                        self.assertIn('openurl', events)
                        self.assertIsNone(worker.last_chat)
                        self.assertTrue(worker.native_rejection_started)
                        with self.assertRaises(RuntimeError):
                            worker.observe_chat({**request, 'stepId': str(uuid.uuid4())})
                else:
                    receipt = worker.observe_chat(request)
                    self.assertTrue(receipt['passed'])
                    self.assertEqual(receipt.get('targetMode'), target_mode)
                    self.assertEqual(receipt.get('renewalPrelude'), True if renewal_prelude else None)
                    if renewal_prelude:
                        self.assertLess(events.index('launch'), events.index('observer-start'))
                        delivery = json.loads((worker.root / 'build/reports/ios' /
                            ('deep-link-chat-' + request['stepId']) / 'delivery.json').read_text())
                        self.assertEqual(delivery['publicPreludePid'], delivery['pid'])
                        self.assertFalse(delivery['coldHadNoAppPid'])
                    archived = worker.root / 'build/reports/ios' / ('deep-link-chat-' + request['stepId']) / 'executed-plan.xctestrun'
                    plan = plistlib.loads(archived.read_bytes())['QuataIosUITests']
                    method = ('testObserveDeliveredMissingChatAndBack' if target_mode == 'missing-thread'
                              else 'testObserveDeliveredMissingMessageAndBack' if target_mode == 'missing-message'
                              else 'testObserveDeliveredChatMessageAndBack')
                    if rejection:
                        self.assertEqual(plan['OnlyTestIdentifiers'], ['QuataIosNativeChatLoginUITests/testObserveDeliveredNativeRejectionAndCancel'])
                        self.assertEqual(plan['EnvironmentVariables']['QUATA_IOS_NATIVE_CHAT_REJECTION_E2E'], '1')
                        self.assertNotIn('QUATA_IOS_EXTERNAL_CHAT_E2E', plan['EnvironmentVariables'])
                        self.assertTrue(receipt['cancelled'])
                        self.assertEqual(receipt['rejection']['status'], 400)
                    else:
                        self.assertEqual(plan['OnlyTestIdentifiers'], ['QuataIosExternalChatLinkUITests/' + method])
                        self.assertNotIn('http-witness', events)
                    if target_mode == 'missing-message':
                        self.assertEqual(plan['EnvironmentVariables']['QUATA_IOS_EXTERNAL_CHAT_VISIBLE_MESSAGE'], '789')
                    self.assertLess(events.index('observer-start'), events.index('openurl'))
                    self.assertLess(events.index('openurl'), events.index('wait-terminal'))
                self.assertIn('wait-terminal', events)
                diagnostic = json.loads((worker.root / 'build/reports/ios' /
                    ('deep-link-chat-' + request['stepId']) / 'delivery-diagnostic.json').read_text())
                expected_phase = ('waiting_ready' if not ready else 'checking_pre_delivery_pid'
                                  if pre_delivery_pid is not None else 'waiting_observer_terminal')
                self.assertEqual(diagnostic['phase'], expected_phase)
                self.assertEqual(diagnostic['observerExitCode'], 0)
                self.assertTrue(set(diagnostic) <= {'stepId', 'phase', 'preDeliveryPid',
                                                   'deliveredPid', 'observerExitCode'})
                post_file = worker.root / 'build/reports/ios' / ('deep-link-chat-' + request['stepId']) / 'rejection-diagnostic.json'
                if rejection:
                    post = json.loads(post_file.read_text())
                    self.assertEqual(post['verified'], not missing_http)
                    self.assertEqual(post['phase'], 'rejection-reader' if missing_http else 'complete')
                    self.assertEqual(set(post), {'stepId', 'phase', 'verified', 'reader'})
                else:
                    self.assertFalse(post_file.exists())

    def test_missing_thread_selects_its_own_method_and_receipt(self):
        self.trial(target_mode='missing-thread')

    def test_rejection_selects_cancel_observer_and_requires_http_witness_after_terminal(self):
        self.trial(rejection=True)

    def test_rejection_without_http_retains_unresolved_delivery_and_forbids_replay(self):
        self.trial(rejection=True, missing_http=True)

    def test_rejection_refuses_mixed_or_reused_state(self):
        for variant in ('warm', 'ordinary-install', 'no-install', 'gate', 'login', 'reused', 'negative', 'prelude'):
            with self.subTest(variant=variant):
                worker = module.Worker.__new__(module.Worker)
                worker.native_rejection_started = variant == 'reused'
                worker.native_gate = {} if variant == 'gate' else None
                worker.native_login = {} if variant == 'login' else None
                worker.installed = None if variant == 'no-install' else {} if variant == 'ordinary-install' else {'originalExpiresAt': 2000000000}
                request = {'action': 'native-rejection', 'runId': str(uuid.uuid4()), 'stepId': str(uuid.uuid4()),
                           'mode': 'warm' if variant == 'warm' else 'cold', 'threadId': '123', 'messageId': '456', 'body': 'synthetic'}
                if variant == 'negative':
                    request['targetMode'] = 'missing-thread'
                if variant == 'prelude':
                    request['renewalPrelude'] = True
                with self.assertRaises(RuntimeError):
                    worker.observe_chat(request)

    def test_renewal_public_prelude_precedes_warm_delivery_with_same_pid(self):
        self.trial(renewal_prelude=True)

    def test_renewal_public_prelude_replaced_pid_never_delivers(self):
        self.trial(renewal_prelude=True, pre_delivery_pid=999)

    def test_renewal_public_prelude_requires_ready_observer(self):
        self.trial(renewal_prelude=True, ready=False)

    def test_renewal_prelude_rejects_incompatible_state_before_any_command(self):
        for variant in ('cold', 'negative', 'no-install', 'ordinary-install', 'previous-chat', 'false-flag'):
            with self.subTest(variant=variant):
                worker = module.Worker.__new__(module.Worker)
                worker.last_chat = {} if variant == 'previous-chat' else None
                worker.installed = None if variant == 'no-install' else {} if variant == 'ordinary-install' else {'originalExpiresAt': 2000000000}
                request = {'action': 'chat', 'runId': str(uuid.uuid4()), 'stepId': str(uuid.uuid4()),
                           'mode': 'cold' if variant == 'cold' else 'warm', 'renewalPrelude': variant != 'false-flag',
                           'threadId': '123', 'messageId': '456', 'body': 'synthetic'}
                if variant == 'negative':
                    request['targetMode'] = 'missing-thread'
                with self.assertRaises(RuntimeError):
                    worker.observe_chat(request)

    def test_missing_message_selects_control_and_distinct_method(self):
        self.trial(target_mode='missing-message')

    def test_ready_observer_precedes_single_delivery(self):
        self.trial()

    def test_observer_launching_product_invalidates_cold_delivery(self):
        self.trial(pre_delivery_pid=999)

    def test_terminal_observer_before_ready_never_delivers(self):
        self.trial(ready=False)


if __name__ == '__main__':
    unittest.main()
