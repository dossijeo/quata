"""Synthetic native Login exchange only; no device, Keychain, Xcode or backend."""
import importlib.util
import json
from pathlib import Path
import plistlib
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid

spec = importlib.util.spec_from_file_location('worker', Path(__file__).with_name('flow-deep-links-ios-worker.py'))
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)


class NativeLoginTests(unittest.TestCase):
    def test_private_dispatch_success_and_uncertain_results_never_repeat(self):
        for outcome in ('passed', 'lost', 'wrong-receipt', 'pid-changed'):
            with self.subTest(outcome=outcome), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                (root / 'build/reports/ios').mkdir(parents=True)
                products = root / 'products'
                products.mkdir()
                original = products / 'original.xctestrun'
                original.write_bytes(plistlib.dumps({'QuataIosUITests': {}}))
                data = {key: str(uuid.uuid4()) for key in ('runId', 'stepId', 'ticketId', 'profileId', 'authUserId')}
                data.update(countryCode='240', phone='799000000000', password='Synthetic-only-password', messageId='456')
                actor = worker.Worker.__new__(worker.Worker)
                actor.root, actor.products, actor.original = root, products, original
                actor.run_id, actor.seen = data['runId'], set()
                actor.installed = actor.pending_owned_read = actor.native_login = None
                actor.native_gate = {'target': ('123', '456', None, None), 'pid': 123, 'runId': data['runId']}
                actor.state = lambda: 'Booted'
                actor.app_pid = lambda: 123
                calls = []
                directory = root / 'build/reports/ios' / ('recovery-secret-' + data['stepId'])

                def call(args, **kwargs):
                    calls.append(args)
                    self.assertNotIn(data['password'], json.dumps(args))
                    if 'xcodebuild' not in args:
                        return
                    private = worker.read_private(directory / 'input.json')
                    self.assertEqual(private['password'], data['password'])
                    self.assertEqual(actor.native_login['state'], 'started')
                    if outcome == 'lost':
                        raise RuntimeError('synthetic')
                    worker.write_private(directory / 'started', b'')
                    receipt = {key: private[key] for key in ('runId', 'stepId', 'stage', 'profileId', 'authUserId')}
                    receipt['result'] = {'passed': True, 'submitCount': 1, 'messageId': data['messageId'],
                                         'clipboardCleared': True, 'postExitObservationMs': 2000}
                    if outcome == 'wrong-receipt':
                        receipt['result']['submitCount'] = 2
                    if outcome == 'pid-changed':
                        actor.app_pid = lambda: 999
                    worker.write_private(directory / 'receipt.json', json.dumps(receipt).encode())

                actor.call = call
                request = {'action': 'native-login', 'input': data}
                with patch.object(worker.subprocess, 'run', return_value=SimpleNamespace(returncode=1)):
                    if outcome == 'passed':
                        self.assertEqual(actor.execute(request), {'runId': data['runId'], 'stepId': data['stepId'], 'passed': True})
                        self.assertEqual(actor.native_login['state'], 'observed')
                        self.assertFalse((directory / 'input.json').exists())
                    else:
                        with self.assertRaises(Exception):
                            actor.execute(request)
                        self.assertEqual(actor.native_login['state'], 'started')
                        self.assertTrue((directory / 'input.json').exists())
                    before = len(calls)
                    for retry in (request, {'action': 'close'}, {'action': 'probe', 'runId': data['runId'], 'stepId': str(uuid.uuid4())}):
                        with self.assertRaises(Exception):
                            actor.execute(retry)
                    self.assertEqual(len(calls), before)


if __name__ == '__main__':
    unittest.main()
