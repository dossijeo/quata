"""Synthetic protocol/files only. Run on Mac/POSIX; never starts Xcode or a device."""
import base64
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import uuid

spec = importlib.util.spec_from_file_location('worker', Path(__file__).with_name('flow-deep-links-ios-worker.py'))
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)


def fixture():
    data = {key: str(uuid.uuid4()) for key in ('runId', 'stepId', 'profileId', 'authUserId')}
    data['stage'] = 'read-owned'
    session_id = str(uuid.uuid4())
    claims = {'sub': data['authUserId'], 'session_id': session_id, 'exp': 2000000000}
    payload = base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip('=')
    session = {'profileId': data['profileId'], 'authUserId': data['authUserId'], 'authSessionId': session_id,
               'accessToken': 'synthetic.' + payload + '.synthetic', 'refreshToken': 'synthetic-refresh',
               'expiresAt': claims['exp'], 'email': 'fixture@example.invalid', 'displayName': 'Synthetic', 'isOfficial': False}
    receipt = {key: data[key] for key in ('runId', 'stepId', 'stage')}
    return data, {**receipt, 'verified': True, 'privateSession': session}


class OwnedReadTests(unittest.TestCase):
    def test_receipt_binding_rejects_foreign_and_mixed(self):
        data, receipt = fixture()
        self.assertEqual(worker.owned_read_session(data, receipt), receipt['privateSession'])
        for key, value in [('profileId', str(uuid.uuid4())), ('authUserId', str(uuid.uuid4())),
                           ('authSessionId', str(uuid.uuid4())), ('expiresAt', 1), ('expiresAt', True),
                           ('refreshToken', ''), ('isOfficial', 1), ('extra', 'unexpected')]:
            changed = copy.deepcopy(receipt)
            changed['privateSession'][key] = value
            with self.assertRaises(Exception):
                worker.owned_read_session(data, changed)
        for change in ({**receipt, 'extra': True}, {**receipt, 'stepId': str(uuid.uuid4())}):
            with self.assertRaises(Exception):
                worker.owned_read_session(data, change)

    def test_private_files_reject_public_permissions_symlinks_and_size(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            file = root / 'private.json'
            worker.write_private(file, b'{"synthetic":true}')
            self.assertEqual(worker.read_private(file), {'synthetic': True})
            file.chmod(0o644)
            with self.assertRaises(Exception):
                worker.read_private(file)
            file.chmod(0o600)
            link = root / 'link.json'
            link.symlink_to(file)
            with self.assertRaises(Exception):
                worker.read_private(link)
            file.write_bytes(b' ' * 32769)
            with self.assertRaises(Exception):
                worker.read_private(file)

    def test_ack_retires_only_matching_private_exchange_and_still_requires_clear(self):
        data, receipt = fixture()
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            worker.write_private(directory / 'input.json', json.dumps(data).encode())
            worker.write_private(directory / 'private-response.json', json.dumps(receipt).encode())
            actor = worker.Worker.__new__(worker.Worker)
            actor.run_id = data['runId']
            actor.installed = {'runId': data['runId'], **receipt['privateSession']}
            actor.pending_owned_read = {'stepId': data['stepId'], 'directory': directory, 'input': data}
            # Every premature operation must fail before any device method is available.
            for request in ({'action': 'close'}, {'action': 'chat'}, {'action': 'probe'},
                            {'action': 'session', 'input': {**data, 'stage': 'clear'}},
                            {'action': 'read-ack', 'runId': data['runId'], 'stepId': str(uuid.uuid4())}):
                with self.assertRaises(Exception):
                    actor.execute(request)
                self.assertTrue((directory / 'private-response.json').exists())
            ack = {'action': 'read-ack', 'runId': data['runId'], 'stepId': data['stepId']}
            self.assertEqual(actor.execute(ack), {'runId': data['runId'], 'stepId': data['stepId'], 'acknowledged': True})
            self.assertIsNone(actor.pending_owned_read)
            self.assertFalse((directory / 'private-response.json').exists())
            self.assertFalse((directory / 'input.json').exists())
            self.assertIsNotNone(actor.installed)
            with self.assertRaises(Exception):
                actor.execute({'action': 'close'})
            with self.assertRaises(Exception):
                actor.execute(ack)

    def test_changed_response_is_preserved_without_ack(self):
        data, receipt = fixture()
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            worker.write_private(directory / 'input.json', json.dumps(data).encode())
            changed = copy.deepcopy(receipt)
            changed['privateSession']['refreshToken'] = 'different-synthetic-refresh'
            worker.write_private(directory / 'private-response.json', json.dumps(changed).encode())
            actor = worker.Worker.__new__(worker.Worker)
            actor.run_id = data['runId']
            actor.installed = {'runId': data['runId'], **receipt['privateSession']}
            actor.pending_owned_read = {'stepId': data['stepId'], 'directory': directory, 'input': data}
            with self.assertRaises(Exception):
                actor.execute({'action': 'read-ack', 'runId': data['runId'], 'stepId': data['stepId']})
            self.assertTrue((directory / 'private-response.json').exists())
            self.assertIsNotNone(actor.pending_owned_read)


if __name__ == '__main__':
    unittest.main()
