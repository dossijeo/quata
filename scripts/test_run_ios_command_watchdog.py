#!/usr/bin/env python3
"""Deterministic contracts for the iOS command watchdog cleanup path."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import signal
import tempfile
import unittest
from unittest.mock import patch


WATCHDOG_PATH = Path(__file__).with_name("run-ios-command-watchdog.py")
SPEC = importlib.util.spec_from_file_location("ios_command_watchdog", WATCHDOG_PATH)
assert SPEC and SPEC.loader
WATCHDOG = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WATCHDOG)


class FakeProcess:
    pid = 4242

    def __init__(self, polls, waits=()):
        self._polls = iter(polls)
        self._last_poll = None
        self._waits = iter(waits)
        self.terminate_calls = 0
        self.kill_calls = 0

    def poll(self):
        try:
            self._last_poll = next(self._polls)
        except StopIteration:
            pass
        return self._last_poll

    def wait(self, timeout=None):
        outcome = next(self._waits, 0)
        if isinstance(outcome, BaseException):
            raise outcome
        return outcome

    def terminate(self):
        self.terminate_calls += 1

    def kill(self):
        self.kill_calls += 1


class StopProcessGroupTests(unittest.TestCase):
    def test_permission_error_falls_back_to_the_child_and_does_not_escape(self):
        process = FakeProcess([None], waits=[0])
        with tempfile.TemporaryDirectory() as directory:
            log_file = Path(directory) / "watchdog.log"
            with patch.object(WATCHDOG, "append_process_snapshot"), patch.object(
                WATCHDOG.os,
                "killpg",
                side_effect=PermissionError(1, "Operation not permitted"),
                create=True,
            ):
                WATCHDOG.stop_process_group(process, log_file)

            self.assertEqual(process.terminate_calls, 1)
            self.assertEqual(process.kill_calls, 0)
            self.assertIn("Unable to signal watchdog process group with SIGTERM", log_file.read_text())

    def test_exited_process_race_does_not_signal_the_child(self):
        process = FakeProcess([None, 0], waits=[0])
        with tempfile.TemporaryDirectory() as directory:
            log_file = Path(directory) / "watchdog.log"
            with patch.object(WATCHDOG, "append_process_snapshot"), patch.object(
                WATCHDOG.os, "killpg", side_effect=ProcessLookupError(), create=True
            ):
                WATCHDOG.stop_process_group(process, log_file)

        self.assertEqual(process.terminate_calls, 0)
        self.assertEqual(process.kill_calls, 0)


if __name__ == "__main__":
    unittest.main()
