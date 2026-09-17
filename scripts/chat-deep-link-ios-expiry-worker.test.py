"""Synthetic private dispatch only; no Xcode, simulator, Keychain or backend."""
import importlib.util
import base64
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
    def test_read_after_expiry_requires_same_auth_session_and_ack_before_clear(self):
        for foreign in (False, True):
            with self.subTest(foreign=foreign), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                (root / 'build/reports/ios').mkdir(parents=True)
                products = root / 'products'
                products.mkdir()
                original = products / 'original.xctestrun'
                original.write_bytes(plistlib.dumps({'QuataIosTests': {}}))
                actor = worker.Worker.__new__(worker.Worker)
                actor.root, actor.products, actor.original = root, products, original
                data = {key: str(uuid.uuid4()) for key in ('runId', 'stepId', 'profileId', 'authUserId')}
                data['stage'] = 'read-owned'
                session_id = str(uuid.uuid4())
                before = {key: data[key] for key in ('runId', 'profileId', 'authUserId')}
                before.update(authSessionId=session_id, accessToken='original-synthetic', refreshToken='original-refresh',
                              expiresAt=1, originalExpiresAt=2000000000)
                actor.run_id, actor.installed = data['runId'], before.copy()
                actor.pending_owned_read, actor.native_login, actor.seen = None, None, set()
                actor.stop = lambda: None
                resulting_id = str(uuid.uuid4()) if foreign else session_id
                claims = {'sub': data['authUserId'], 'session_id': resulting_id, 'exp': 2000003600}
                payload = base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip('=')
                session = {'profileId': data['profileId'], 'authUserId': data['authUserId'], 'authSessionId': resulting_id,
                           'accessToken': 'synthetic.' + payload + '.synthetic', 'refreshToken': 'rotated-synthetic',
                           'expiresAt': 2000003600, 'email': 'fixture@example.invalid', 'displayName': 'Synthetic', 'isOfficial': False}
                directory = root / 'build/reports/ios' / ('deep-link-session-' + data['stepId'])

                def call(args, timeout=60):
                    if 'xcodebuild' in args:
                        worker.write_private(directory / 'private-response.json', json.dumps({
                            'runId': data['runId'], 'stepId': data['stepId'], 'stage': 'read-owned',
                            'verified': True, 'privateSession': session}).encode())
                actor.call = call
                if foreign:
                    with self.assertRaises(Exception):
                        actor.execute({'action': 'session', 'input': data})
                    self.assertEqual(actor.installed, before)
                    self.assertTrue((directory / 'private-response.json').exists())
                    continue
                receipt = actor.execute({'action': 'session', 'input': data})
                self.assertEqual(receipt['privateSession'], session)
                self.assertEqual(actor.installed, {'runId': data['runId'], **session})
                self.assertTrue((directory / 'private-response.json').exists())
                for request in ({'action': 'close'}, {'action': 'session', 'input': {**before, 'stepId': str(uuid.uuid4()), 'stage': 'clear-expired'}}):
                    with self.assertRaises(Exception):
                        actor.execute(request)
                actor.execute({'action': 'read-ack', 'runId': data['runId'], 'stepId': data['stepId']})
                self.assertFalse((directory / 'private-response.json').exists())
                self.assertIsNotNone(actor.installed)
                with self.assertRaises(Exception):
                    actor.execute({'action': 'session', 'input': {**before, 'stepId': str(uuid.uuid4()), 'stage': 'clear-expired'}})

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
            # This synthetic test must never inspect or terminate a real app.
            actor.terminate_app = lambda: None

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
