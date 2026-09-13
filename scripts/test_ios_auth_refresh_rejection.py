"""Synthetic parser/transport checks. No simulator, session or backend mutations."""
import json
import subprocess
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
            self.assertNotIn('--process', calls[0])
            self.assertIn('@' + str(self.start // 1_000_000_000), calls[0])
            self.assertEqual(calls[0].count('--predicate'), 1)
            self.assertEqual(calls[0][calls[0].index('--predicate') + 1],
                             'processIdentifier == 1234 AND (composedMessage == "Quata auth refresh rejected status=400" OR composedMessage == "Quata auth refresh rejected status=401")')
            self.assertNotIn('erase', calls[0])

    def test_diagnostic_distinguishes_failure_without_exporting_private_content(self):
        scenarios = [
            ('timeout', subprocess.TimeoutExpired('private command', 15, output='private stdout', stderr='private stderr'), 'query'),
            ('command-failed', subprocess.CalledProcessError(1, 'private command', output='private stdout', stderr='private stderr'), 'query'),
            ('unexpected', RuntimeError('private exception'), 'query'),
            ('invalid-json', 'private invalid json', 'decode'),
            ('invalid-shape', '{"private":"value"}', 'decode'),
            ('oversize', 'private' * 200000, 'decode'),
            ('no-match', '[]', 'select'),
            ('invalid-timestamp', json.dumps([{**self.row, 'timestamp': 'private invalid timestamp'}]), 'select')]
        for category, value, phase in scenarios:
            with self.subTest(category=category):
                diagnostic, calls = {}, []

                def execute(*args, **kwargs):
                    calls.append(args)
                    if isinstance(value, Exception):
                        raise value
                    return SimpleNamespace(stdout=value)

                with self.assertRaisesRegex(RuntimeError, '^ios_refresh_rejection_unverified$'):
                    read_ios_refresh_rejection(1234, self.start, lambda: 1234, execute, lambda: self.end, diagnostic=diagnostic)
                self.assertEqual(len(calls), 1)
                self.assertEqual(diagnostic['phase'], phase)
                self.assertEqual(diagnostic['category'], category)
                self.assertEqual(diagnostic['outcome'], 'failed')
                self.assertEqual(diagnostic['startedAtNs'], str(self.start))
                self.assertEqual(diagnostic['endedAtNs'], str(self.end))
                self.assertGreaterEqual(int(diagnostic['durationNs']), 0)
                self.assertNotIn('private', json.dumps(diagnostic))
                self.assertTrue(set(diagnostic) <= {'phase', 'outcome', 'pid', 'startedAtNs', 'endedAtNs',
                    'durationNs', 'category', 'bytes', 'rows', 'pidMatches', 'messageMatches', 'windowMatches'})

    def test_diagnostic_counts_discarded_rows_without_changing_selection(self):
        rows = [{**self.row, 'processID': 999}, {**self.row, 'eventMessage': 'private'},
                {**self.row, 'timestamp': '2026-09-12 22:14:24.123456790+0000'}]
        for valid in (False, True):
            diagnostic = {}
            def execute(*args, **kwargs):
                return SimpleNamespace(stdout=json.dumps(rows + ([self.row] if valid else [])))
            if valid:
                receipt = read_ios_refresh_rejection(1234, self.start, lambda: 1234, execute, lambda: self.end, diagnostic=diagnostic)
                self.assertEqual(receipt['status'], 400)
                self.assertEqual(diagnostic['outcome'], 'verified')
            else:
                with self.assertRaises(RuntimeError):
                    read_ios_refresh_rejection(1234, self.start, lambda: 1234, execute, lambda: self.end, diagnostic=diagnostic)
                self.assertEqual(diagnostic['category'], 'no-match')
            self.assertEqual(diagnostic['rows'], 3 + valid)
            self.assertEqual(diagnostic['pidMatches'], 2 + valid)
            self.assertEqual(diagnostic['messageMatches'], 1 + valid)
            self.assertEqual(diagnostic['windowMatches'], int(valid))
            self.assertNotIn('private', json.dumps(diagnostic))

    def test_diagnostic_distinguishes_pid_checks(self):
        for pids, phase in (([999], 'pid-before'), ([1234, 999], 'pid-after')):
            values = iter(pids)
            diagnostic = {}
            def execute(*args, **kwargs):
                return SimpleNamespace(stdout=json.dumps([self.row]))
            with self.assertRaises(RuntimeError):
                read_ios_refresh_rejection(1234, self.start, lambda: next(values), execute, lambda: self.end, diagnostic=diagnostic)
            self.assertEqual(diagnostic['phase'], phase)
            self.assertEqual(diagnostic['category'], 'pid-mismatch')


if __name__ == '__main__':
    unittest.main()
