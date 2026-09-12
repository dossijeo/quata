"""Synthetic parser/transport checks. No simulator, session or backend mutations."""
import json
from types import SimpleNamespace
import unittest
from ios_auth_refresh_rejection import read_ios_refresh_rejection, select_ios_refresh_rejection, timestamp_ns


class RejectionTests(unittest.TestCase):
    def setUp(self):
        self.start = timestamp_ns('2026-09-12 22:14:22.123456789+0000')
        self.end = self.start + 2_000_000_000
        self.row = {'processID': 1234, 'timestamp': '2026-09-12 22:14:23.123456789+0000',
                    'eventMessage': 'Quata auth refresh rejected status=400', 'privateExtra': 'must not escape'}

    def select(self, rows):
        return select_ios_refresh_rejection(json.dumps(rows), 1234, self.start, self.end)

    def test_only_normalized_status_is_returned(self):
        self.assertEqual(self.select([{'private': 'ignored'}, self.row]),
                         {'observed': True, 'pid': 1234, 'status': 400, 'timestampNs': str(self.start + 1_000_000_000),
                          'startedAtNs': str(self.start), 'endedAtNs': str(self.end)})

    def test_timezone_preserves_exact_instant(self):
        self.assertEqual(timestamp_ns('2026-09-13 00:14:22.123456789+0200'), self.start)

    def test_foreign_or_nonexact_messages_are_not_evidence(self):
        for change in [{'processID': 5678}, {'processID': '1234'}, {'processID': True},
                       {'eventMessage': 'Quata auth refresh rejected status=500'},
                       {'eventMessage': 'Quata auth refresh rejected status=400 body=private'},
                       {'eventMessage': 'Other auth refresh rejected status=400'},
                       {'timestamp': '2026-09-12 22:14:22.123456788+0000'},
                       {'timestamp': '2026-09-12 22:14:24.123456790+0000'}]:
            with self.subTest(change=change), self.assertRaisesRegex(RuntimeError, '^ios_refresh_rejection_unverified$'):
                self.select([{**self.row, **change}])

    def test_boundaries_and_401(self):
        for timestamp in ['2026-09-12 22:14:22.123456789+0000', '2026-09-12 22:14:24.123456789+0000']:
            self.assertEqual(self.select([{**self.row, 'timestamp': timestamp,
                                          'eventMessage': 'Quata auth refresh rejected status=401'}])['status'], 401)

    def test_bad_json_time_or_window_fails_closed(self):
        for text, pid, start, end in [('private invalid json', 1234, self.start, self.end),
                                     ('{}', 1234, self.start, self.end), (' ' * (1024 * 1024 + 1), 1234, self.start, self.end),
                                     ('[]', True, self.start, self.end), ('[]', 1234, self.end, self.start),
                                     (json.dumps([{**self.row, 'timestamp': 'not-time'}]), 1234, self.start, self.end)]:
            with self.assertRaisesRegex(RuntimeError, '^ios_refresh_rejection_unverified$'):
                select_ios_refresh_rejection(text, pid, start, end)

    def test_read_binds_pid_and_queries_only_fixed_messages(self):
        for outcome in ['complete', 'pid-changed', 'transport-failed']:
            calls = []
            pids = iter([1234, 5678 if outcome == 'pid-changed' else 1234])

            def execute(args, **kwargs):
                calls.append(args)
                self.assertEqual(kwargs, {'capture_output': True, 'text': True, 'check': True, 'timeout': 15})
                if outcome == 'transport-failed':
                    raise RuntimeError('private log output')
                return SimpleNamespace(stdout=json.dumps([self.row]))

            def action():
                return read_ios_refresh_rejection(1234, self.start, lambda: next(pids), execute, lambda: self.end)

            if outcome == 'complete':
                self.assertEqual(action()['status'], 400)
            else:
                with self.assertRaisesRegex(RuntimeError, '^ios_refresh_rejection_unverified$'):
                    action()
            self.assertEqual(len(calls), 1)
            self.assertIn('--process', calls[0])
            self.assertIn('1234', calls[0])
            self.assertIn('@' + str(self.start // 1_000_000_000), calls[0])
            self.assertIn('composedMessage == "Quata auth refresh rejected status=400" OR composedMessage == "Quata auth refresh rejected status=401"', calls[0])
            self.assertNotIn('erase', calls[0])


if __name__ == '__main__':
    unittest.main()
