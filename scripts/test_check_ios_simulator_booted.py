#!/usr/bin/env python3
"""Functional contracts for the exact-UDID simctl boot state check."""

from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys
import unittest


SCRIPT = Path(__file__).with_name("check-ios-simulator-booted.py")
TARGET_UDID = "11111111-1111-1111-1111-111111111111"
OTHER_UDID = "22222222-2222-2222-2222-222222222222"


class CheckIosSimulatorBootedTests(unittest.TestCase):
    def run_check(self, payload: object) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--udid", TARGET_UDID],
            input=json.dumps(payload),
            text=True,
            capture_output=True,
            check=False,
        )

    def test_accepts_only_the_exact_booted_udid(self):
        result = self.run_check(
            {
                "devices": {
                    "com.apple.CoreSimulator.SimRuntime.iOS-26-2": [
                        {"udid": OTHER_UDID, "state": "Booted"},
                        {"udid": TARGET_UDID, "state": "Booted"},
                    ]
                }
            }
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rejects_a_different_booted_simulator_or_invalid_json(self):
        other_booted = self.run_check(
            {"devices": {"com.apple.CoreSimulator.SimRuntime.iOS-26-2": [{"udid": OTHER_UDID, "state": "Booted"}]}}
        )
        self.assertEqual(other_booted.returncode, 1)

        invalid = subprocess.run(
            [sys.executable, str(SCRIPT), "--udid", TARGET_UDID],
            input="not-json",
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(invalid.returncode, 1)
        self.assertIn("Could not parse simctl device JSON", invalid.stderr)


if __name__ == "__main__":
    unittest.main()
