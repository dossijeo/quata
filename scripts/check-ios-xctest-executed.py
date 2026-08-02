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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--method", required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--require-terminal-success-marker", action="store_true")
    args = parser.parse_args()

    log = args.log.read_text(encoding="utf-8", errors="replace")
    if not selected_test_passed(log, args.method):
        print(f"Required test did not execute and pass exactly once: {args.method}", file=sys.stderr)
        return 1
    if args.require_terminal_success_marker and not xcodebuild_test_succeeded(log):
        print(f"xcodebuild did not emit an exact success marker: {args.method}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
