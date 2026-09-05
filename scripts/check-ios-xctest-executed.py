#!/usr/bin/env python3
"""Certify that one selected XCTest really executed and passed.

Xcode's human-readable output for an Objective-C bridged Swift test is:
``Test Case '-[Module.Class method]' passed (1.234 seconds).``
The closing bracket is part of the test-case syntax, before the quote.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def selected_test_passed(log: str, method: str) -> bool:
    """Accept only the exact XCTest ``method]' passed (...)`` outcome."""
    outcome = re.compile(
        r"^Test Case '.*?\s" + re.escape(method) + r"\]' (passed|failed|skipped) \(",
        re.IGNORECASE,
    )
    results = [match.group(1).lower() for line in log.splitlines() if (match := outcome.match(line))]
    return results == ["passed"]


def xcodebuild_test_succeeded(log: str) -> bool:
    """Accept only the two exact successful terminal XCTest markers.

    Xcode 26 may print ``TEST EXECUTE SUCCEEDED`` for a selected test while
    older Xcode output uses ``TEST SUCCEEDED``.  Neither failed, skipped nor
    merely non-empty output is a success marker.
    """
    terminal_markers = [
        line.strip()
        for line in log.splitlines()
        if line.strip() in {
            "** TEST SUCCEEDED **",
            "** TEST EXECUTE SUCCEEDED **",
            "** TEST FAILED **",
            "** TEST SKIPPED **",
        }
    ]
    return terminal_markers in (["** TEST SUCCEEDED **"], ["** TEST EXECUTE SUCCEEDED **"])


def selected_test_passed_before_watchdog_timeout(log: str, method: str) -> bool:
    """Accept a bounded-runner timeout only after the selected test passed.

    Some local simulator runs leave xcodebuild finalizing diagnostics after the
    only selected XCTest has already emitted its terminal method-level pass. This
    remains fail-closed: failures/skips for the selected method are rejected, the
    selected method must pass exactly once, and the watchdog timeout must appear
    after that pass line.
    """
    pass_line = re.compile(
        r"^Test Case '.*?\s" + re.escape(method) + r"\]' passed \(",
        re.IGNORECASE,
    )
    terminal_line = re.compile(
        r"^Test Case '.*?\s" + re.escape(method) + r"\]' (passed|failed|skipped) \(",
        re.IGNORECASE,
    )
    pass_indexes = []
    terminal_results = []
    timeout_indexes = []
    lines = log.splitlines()
    for index, line in enumerate(lines):
        if match := terminal_line.match(line):
            terminal_results.append(match.group(1).lower())
        if pass_line.match(line):
            pass_indexes.append(index)
        if "WATCHDOG TIMEOUT:" in line:
            timeout_indexes.append(index)
    return (
        terminal_results == ["passed"]
        and len(pass_indexes) == 1
        and bool(timeout_indexes)
        and min(timeout_indexes) > pass_indexes[0]
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--method", required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--require-terminal-success-marker", action="store_true")
    parser.add_argument("--accept-selected-pass-before-watchdog-timeout", action="store_true")
    args = parser.parse_args()

    log = args.log.read_text(encoding="utf-8", errors="replace")
    if not selected_test_passed(log, args.method):
        print(f"Required test did not execute and pass exactly once: {args.method}", file=sys.stderr)
        return 1
    if args.require_terminal_success_marker and not xcodebuild_test_succeeded(log):
        if args.accept_selected_pass_before_watchdog_timeout and selected_test_passed_before_watchdog_timeout(
            log, args.method
        ):
            return 0
        print(f"xcodebuild did not emit an exact success marker: {args.method}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
