"""Read only the fixed native HTTP rejection diagnostic; never export raw logs."""
import calendar
from datetime import datetime
import json
import re
import subprocess
import time

SIMULATOR = 'F2E1EA50-FBAD-443C-A98F-2A576C14C70B'
MESSAGES = {f'Quata auth refresh rejected status={status}': status for status in (400, 401)}


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


def select_ios_refresh_rejection(log_text, pid, started_at_ns, ended_at_ns):
    try:
        configuration(pid, started_at_ns, ended_at_ns)
        require(isinstance(log_text, str) and len(log_text.encode('utf8')) <= 1024 * 1024)
        rows = json.loads(log_text)
        require(isinstance(rows, list))
        for row in rows:
            if not isinstance(row, dict) or type(row.get('processID')) is not int or row['processID'] != pid:
                continue
            message = row.get('eventMessage')
            if not isinstance(message, str) or message not in MESSAGES:
                continue
            instant = timestamp_ns(row['timestamp'])
            if started_at_ns <= instant <= ended_at_ns:
                return {'observed': True, 'pid': pid, 'status': MESSAGES[message], 'timestampNs': str(instant),
                        'startedAtNs': str(started_at_ns), 'endedAtNs': str(ended_at_ns)}
        raise RuntimeError()
    except Exception:
        raise RuntimeError('ios_refresh_rejection_unverified') from None


def read_ios_refresh_rejection(pid, started_at_ns, app_pid, execute=subprocess.run, clock=time.time_ns):
    """Caller holds the simulator lease and captured start immediately before delivery.

    PID stability bounds this witness; it does not establish an exhaustive HTTP count.
    The original JSON remains private in memory and is never returned or written.
    """
    try:
        ended_at_ns = clock()
        configuration(pid, started_at_ns, ended_at_ns)
        require(app_pid() == pid)
        predicate = ' OR '.join(f'composedMessage == "{message}"' for message in MESSAGES)
        result = execute(['xcrun', 'simctl', 'spawn', SIMULATOR, 'log', 'show', '--style', 'json',
                          '--timezone', 'UTC', '--process', str(pid),
                          '--start', '@' + str(started_at_ns // 1_000_000_000),
                          '--end', '@' + str(ended_at_ns // 1_000_000_000 + 1),
                          '--predicate', predicate], capture_output=True, text=True, check=True, timeout=15)
        require(app_pid() == pid)
        return select_ios_refresh_rejection(result.stdout, pid, started_at_ns, ended_at_ns)
    except Exception:
        raise RuntimeError('ios_refresh_rejection_unverified') from None
