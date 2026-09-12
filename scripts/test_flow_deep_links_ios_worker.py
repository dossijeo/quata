"""Mac/Python local-only ordering checks; all simulator/Xcode calls are mocked."""
import importlib.util
import json
from pathlib import Path
import plistlib
import tempfile
import unittest
from unittest.mock import patch
import uuid

spec = importlib.util.spec_from_file_location('ios_worker', Path(__file__).with_name('flow-deep-links-ios-worker.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DeliveryOrderTests(unittest.TestCase):
    def trial(self, pre_delivery_pid=None, ready=True, target_mode=None, renewal_prelude=False):
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
            request = {'action': 'chat', 'runId': worker.run_id, 'stepId': str(uuid.uuid4()), 'mode': 'cold',
                       'threadId': '123', 'messageId': '456', 'body': 'Deep link ' + worker.run_id}
            if target_mode is not None:
                request['targetMode'] = target_mode
            if target_mode == 'missing-message':
                request['visibleMessageId'] = '789'
            if renewal_prelude:
                request.update(mode='warm', renewalPrelude=True)
                worker.installed = {'originalExpiresAt': 2000000000}
            events = []
            worker.stop = lambda: events.append('stop')
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

            with patch.object(module.subprocess, 'Popen', side_effect=start), patch.object(module.subprocess, 'run') as run:
                run.return_value.returncode = 1
                if pre_delivery_pid is not None or not ready:
                    with self.assertRaises(RuntimeError):
                        worker.observe_chat(request)
                    self.assertNotIn('openurl', events)
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
                    self.assertEqual(plan['OnlyTestIdentifiers'], ['QuataIosExternalChatLinkUITests/' + method])
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

    def test_missing_thread_selects_its_own_method_and_receipt(self):
        self.trial(target_mode='missing-thread')

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
