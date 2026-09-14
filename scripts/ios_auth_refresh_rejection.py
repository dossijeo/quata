"""Read only the fixed native HTTP rejection diagnostic; never export raw logs."""
import calendar
from datetime import datetime
import json
import re
import subprocess
import time

SIMULATOR = 'F2E1EA50-FBAD-443C-A98F-2A576C14C70B'
MESSAGES = {f'Quata auth refresh rejected status={status}': status for status in (400, 401)}
CATEGORIES = {'pid-mismatch', 'invalid-shape', 'oversize', 'invalid-json', 'invalid-timestamp', 'no-match', 'unexpected'}


class Unverified(RuntimeError):
    def __init__(self, category):
        super().__init__('ios_refresh_rejection_unverified')
        self.category = category


def check(condition, category):
    if not condition:
        raise Unverified(category)


def require(condition):
    if not condition:
        raise RuntimeError('ios_refresh_rejection_unverified')


def timestamp_ns(value):
    match = re.fullmatch(r'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\.(\d{1,9})([+-]\d{4})', value)
    require(match is not None)
    seconds = datetime.strptime(match[1] + match[3], '%Y-%m-%d %H:%M:%S%z')
    return calendar.timegm(seconds.utctimetuple()) * 1_000_000_000 + int(match[2].ljust(9, '0'))


def configuration(pid, started_at_ns, ended_at_ns):
    require(type(pid) is int and 0 < pid <= 2_147_483_647)
    require(type(started_at_ns) is int and type(ended_at_ns) is int
            and 0 < started_at_ns <= ended_at_ns)


def select_ios_refresh_rejection(log_text, pid, started_at_ns, ended_at_ns, *, diagnostic=None):
    details = diagnostic if diagnostic is not None else {}
    try:
        configuration(pid, started_at_ns, ended_at_ns)
        details['phase'] = 'decode'
        check(isinstance(log_text, str), 'invalid-shape')
        details['bytes'] = len(log_text.encode('utf8'))
        check(details['bytes'] <= 1024 * 1024, 'oversize')
        try:
            rows = json.loads(log_text)
        except (ValueError, TypeError):
            raise Unverified('invalid-json') from None
        check(isinstance(rows, list), 'invalid-shape')
        details.update(phase='select', rows=len(rows), pidMatches=0, messageMatches=0, windowMatches=0)
        # Counts cover only rows inspected before the first valid witness.
        for row in rows:
            if not isinstance(row, dict) or type(row.get('processID')) is not int or row['processID'] != pid:
                continue
            details['pidMatches'] += 1
            message = row.get('eventMessage')
            if not isinstance(message, str) or message not in MESSAGES:
                continue
            details['messageMatches'] += 1
            try:
                instant = timestamp_ns(row['timestamp'])
            except Exception:
                raise Unverified('invalid-timestamp') from None
            if started_at_ns <= instant <= ended_at_ns:
                details['windowMatches'] += 1
                return {'observed': True, 'pid': pid, 'status': MESSAGES[message], 'timestampNs': str(instant),
                        'startedAtNs': str(started_at_ns), 'endedAtNs': str(ended_at_ns)}
        raise Unverified('no-match')
    except Unverified:
        raise
    except Exception:
        raise Unverified('unexpected') from None


def read_ios_refresh_rejection(pid, started_at_ns, app_pid, execute=subprocess.run, clock=time.time_ns, *, diagnostic=None):
    """Caller holds the simulator lease and captured start immediately before delivery.

    PID stability bounds this witness; it does not establish an exhaustive HTTP count.
    The original JSON remains private in memory and is never returned or written.
    """
    details = diagnostic if diagnostic is not None else {}
    started = time.monotonic_ns()
    details.update(phase='configuration', outcome='failed')
    try:
        ended_at_ns = clock()
        configuration(pid, started_at_ns, ended_at_ns)
        details.update(pid=pid, startedAtNs=str(started_at_ns), endedAtNs=str(ended_at_ns), phase='pid-before')
        check(app_pid() == pid, 'pid-mismatch')
        messages = ' OR '.join(f'composedMessage == "{message}"' for message in MESSAGES)
        # Keep both restrictions in one predicate. A separate --process option
        # broadens the simulator query beyond the fixed diagnostic messages.
        predicate = f'processIdentifier == {pid} AND ({messages})'
        details['phase'] = 'query'
        result = execute(['xcrun', 'simctl', 'spawn', SIMULATOR, 'log', 'show', '--style', 'json',
                          '--timezone', 'UTC',
                          '--start', '@' + str(started_at_ns // 1_000_000_000),
                          '--end', '@' + str(ended_at_ns // 1_000_000_000 + 1),
                          '--predicate', predicate], capture_output=True, text=True, check=True, timeout=15)
        details['phase'] = 'pid-after'
        check(app_pid() == pid, 'pid-mismatch')
        selected = select_ios_refresh_rejection(result.stdout, pid, started_at_ns, ended_at_ns, diagnostic=details)
        details.update(phase='complete', outcome='verified')
        return selected
    except Exception as error:
        details['category'] = ('timeout' if isinstance(error, subprocess.TimeoutExpired)
                               else 'command-failed' if isinstance(error, subprocess.CalledProcessError)
                               else error.category if isinstance(error, Unverified) and error.category in CATEGORIES else 'unexpected')
        raise RuntimeError('ios_refresh_rejection_unverified') from None
    finally:
        details['durationNs'] = str(time.monotonic_ns() - started)
