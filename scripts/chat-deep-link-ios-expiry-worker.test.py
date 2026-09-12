"""Synthetic private dispatch only; no Xcode, simulator, Keychain or backend."""
import importlib.util
import json
from pathlib import Path
import plistlib
import tempfile
import unittest
import uuid

spec = importlib.util.spec_from_file_location('worker', Path(__file__).with_name('flow-deep-links-ios-worker.py'))
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)


class ExpiryWorkerTests(unittest.TestCase):
    def test_exact_expired_install_and_clear_preserve_both_expirations(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'build/reports/ios').mkdir(parents=True)
            products = root / 'products'
            products.mkdir()
            original = products / 'original.xctestrun'
            original.write_bytes(plistlib.dumps({'QuataIosTests': {}}))
            actor = worker.Worker.__new__(worker.Worker)
            actor.root, actor.products, actor.original = root, products, original
            actor.run_id, actor.installed, actor.pending_owned_read, actor.native_login = None, None, None, None
            actor.seen = set()
            actor.stop = lambda: None
            executed = []

            def call(args, timeout=60):
                if 'xcodebuild' not in args:
                    return
                directories = list((root / 'build/reports/ios').glob('deep-link-session-*'))
                pending = [directory for directory in directories if (directory / 'input.json').exists()]
                self.assertEqual(len(pending), 1)
                directory = pending[0]
                data = json.loads((directory / 'input.json').read_text())
                executed.append(data)
                receipt = {key: data[key] for key in ('runId', 'stepId', 'stage')}
                (directory / 'receipt.json').write_text(json.dumps({**receipt, 'verified': True}))

            actor.call = call
            data = {key: str(uuid.uuid4()) for key in ('runId', 'stepId', 'profileId', 'authUserId', 'authSessionId')}
            data.update(stage='install-expired', accessToken='synthetic', refreshToken='synthetic-refresh',
                        expiresAt=1, originalExpiresAt=2000000000, email='fixture@example.invalid', displayName='Synthetic', isOfficial=False)
            receipt = actor.execute({'action': 'session', 'input': data})
            self.assertTrue(receipt['verified'])
            self.assertEqual(actor.installed['expiresAt'], 1)
            self.assertEqual(actor.installed['originalExpiresAt'], 2000000000)
            with self.assertRaises(Exception):
                actor.execute({'action': 'close'})
            with self.assertRaises(Exception):
                actor.execute({'action': 'session', 'input': {**data, 'stage': 'clear-expired', 'stepId': str(uuid.uuid4()), 'refreshToken': 'different'}})
            with self.assertRaises(Exception):
                actor.execute({'action': 'session', 'input': {**data, 'stage': 'clear', 'stepId': str(uuid.uuid4())}})
            clear = {**data, 'stage': 'clear-expired', 'stepId': str(uuid.uuid4())}
            actor.execute({'action': 'session', 'input': clear})
            self.assertIsNone(actor.installed)
            self.assertEqual(len(executed), 2)
            self.assertEqual(actor.execute({'action': 'close'}), {'closed': True})


if __name__ == '__main__':
    unittest.main()
