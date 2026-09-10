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
    def trial(self, pre_delivery_pid=None, ready=True):
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
            events = []
            worker.stop = lambda: events.append('stop')
            worker.call = lambda args, **kwargs: events.append(args[2] if args[:2] == ['xcrun', 'simctl'] else 'check')
            pids = iter([None, pre_delivery_pid, 412, 412])
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
                    self.assertTrue(worker.observe_chat(request)['passed'])
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

    def test_ready_observer_precedes_single_delivery(self):
        self.trial()

    def test_observer_launching_product_invalidates_cold_delivery(self):
        self.trial(pre_delivery_pid=999)

    def test_terminal_observer_before_ready_never_delivers(self):
        self.trial(ready=False)


if __name__ == '__main__':
    unittest.main()
