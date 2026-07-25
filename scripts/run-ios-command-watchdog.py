#!/usr/bin/env python3
"""Run one macOS CI command with a bounded lifetime and useful diagnostics.

`timeout-minutes` limits a GitHub Actions job, but does not provide a timely
diagnostic when CoreSimulator or xcodebuild blocks.  This helper starts the
command in its own session so a timeout can terminate only that command and
its children, never the workflow shell or unrelated runner processes.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shlex
import signal
import subprocess
import sys


def append_process_snapshot(log_file: Path, title: str) -> None:
    with log_file.open("a", encoding="utf-8") as log:
        log.write(f"\n===== {title} =====\n")
        try:
            subprocess.run(
                ["ps", "-axo", "pid,ppid,pgid,state,etime,command"],
                stdout=log,
                stderr=subprocess.STDOUT,
                check=False,
                text=True,
            )
        except OSError as error:
            log.write(f"Unable to collect process snapshot: {error}\n")


def stop_process_group(process: subprocess.Popen[bytes], log_file: Path) -> None:
    append_process_snapshot(log_file, "watchdog timeout: process snapshot before SIGTERM")
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return

    try:
        process.wait(timeout=30)
        return
    except subprocess.TimeoutExpired:
        append_process_snapshot(log_file, "watchdog timeout: process snapshot before SIGKILL")

    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        return
    process.wait()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--timeout-seconds", type=int, required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()

    if args.timeout_seconds <= 0:
        parser.error("--timeout-seconds must be positive")
    if not args.command or args.command[0] != "--" or len(args.command) == 1:
        parser.error("pass the command after --")

    command = args.command[1:]
    args.log.parent.mkdir(parents=True, exist_ok=True)
    with args.log.open("w", encoding="utf-8") as log:
        log.write(
            "Watchdog command (timeout={}s): {}\n".format(
                args.timeout_seconds, shlex.join(command)
            )
        )
        log.flush()
        # start_new_session gives the command a distinct PGID.  The timeout
        # handling below can therefore never signal the Actions shell.
        try:
            process = subprocess.Popen(
                command,
                stdout=log,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
        except OSError as error:
            log.write(f"WATCHDOG LAUNCH FAILURE: {error}\n")
            return 125
        try:
            exit_code = process.wait(timeout=args.timeout_seconds)
            log.write(f"Watchdog command completed with exit code {exit_code}.\n")
            return exit_code
        except subprocess.TimeoutExpired:
            log.write(
                "\nWATCHDOG TIMEOUT: command exceeded {} seconds; "
                "terminating its process group.\n".format(args.timeout_seconds)
            )
            log.flush()
            stop_process_group(process, args.log)
            return 124


if __name__ == "__main__":
    sys.exit(main())
