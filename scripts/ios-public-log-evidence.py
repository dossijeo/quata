#!/usr/bin/env python3
"""Validate that simulator log evidence belongs only to the two launched app PIDs."""
from __future__ import annotations

import argparse
import json
import pathlib
import re


APP_EVENT = re.compile(r"\bQuataIos\[(?P<pid>[1-9][0-9]*):[^\]]+\]")
HTTP_200 = re.compile(r"(?:status 200|response_status=200)")
CRASH = re.compile(
    r"(?:fatal error|terminating app due to uncaught exception|application crashed)",
    re.IGNORECASE,
)


def validate_log(content: str, expected_pids: set[int]) -> dict[str, object]:
    if len(expected_pids) != 2 or any(pid <= 0 for pid in expected_pids):
        raise ValueError("exactly two distinct positive launch PIDs are required")
    seen: set[int] = set()
    events = 0
    http_200_events = 0
    crash_events = 0
    for line in content.splitlines():
        if "QuataIos[" not in line:
            continue
        match = APP_EVENT.search(line)
        if match is None:
            raise ValueError("unparseable QuataIos log event")
        pid = int(match.group("pid"))
        if pid not in expected_pids:
            raise ValueError(f"foreign QuataIos PID in evidence: {pid}")
        seen.add(pid)
        events += 1
        if HTTP_200.search(line):
            http_200_events += 1
        if CRASH.search(line):
            crash_events += 1
    if seen != expected_pids:
        raise ValueError("both cold and warm launch PIDs must have log events")
    if http_200_events < 1:
        raise ValueError("no HTTP 200 event belongs to the launched PIDs")
    if crash_events:
        raise ValueError("crash signature belongs to a launched PID")
    return {
        "allowed_pids": sorted(expected_pids),
        "app_events": events,
        "http_200_events": http_200_events,
        "crash_signatures": 0,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", type=pathlib.Path, required=True)
    parser.add_argument("--pid", type=int, action="append", required=True)
    args = parser.parse_args()
    result = validate_log(
        args.log.read_text(encoding="utf-8", errors="replace"),
        set(args.pid),
    )
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))


if __name__ == "__main__":
    main()
