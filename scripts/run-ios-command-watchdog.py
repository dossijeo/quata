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


def log_signal_failure(log_file: Path, target: str, signum: signal.Signals, error: OSError) -> None:
    """Record a best-effort cleanup failure without turning it into a traceback."""
    with log_file.open("a", encoding="utf-8") as log:
        log.write(f"Unable to signal {target} with {signum.name}: {error}\n")


def signal_child(process: subprocess.Popen[bytes], log_file: Path, signum: signal.Signals) -> None:
    """Signal only the watchdog child when its process group cannot be used."""
    if process.poll() is not None:
        return
    try:
        if signum == signal.SIGTERM:
            process.terminate()
        else:
            process.kill()
    except ProcessLookupError:
        # The child can exit between poll() and terminate()/kill().
        return
    except OSError as error:
        log_signal_failure(log_file, "watchdog child process", signum, error)


def signal_process_group(
    process: subprocess.Popen[bytes], log_file: Path, signum: signal.Signals
) -> bool:
    """Signal the isolated group, returning whether that operation succeeded."""
    if process.poll() is not None:
        return True
    try:
        os.killpg(process.pid, signum)
        return True
    except ProcessLookupError:
        # The group can disappear while the child is being reaped. If the child
        # still exists, fall back to its PID only; never signal another group.
        return False
    except OSError as error:
        log_signal_failure(log_file, "watchdog process group", signum, error)
        return False


def wait_for_exit(process: subprocess.Popen[bytes], timeout: int) -> bool:
    try:
        process.wait(timeout=timeout)
        return True
    except subprocess.TimeoutExpired:
        return False


def stop_process_group(process: subprocess.Popen[bytes], log_file: Path) -> None:
    append_process_snapshot(log_file, "watchdog timeout: process snapshot before SIGTERM")
    if not signal_process_group(process, log_file, signal.SIGTERM):
        signal_child(process, log_file, signal.SIGTERM)

    if wait_for_exit(process, timeout=30):
        return

    append_process_snapshot(log_file, "watchdog timeout: process snapshot before SIGKILL")
    if not signal_process_group(process, log_file, signal.SIGKILL):
        signal_child(process, log_file, signal.SIGKILL)

    # Avoid a second unbounded wait when a runner forbids signalling the child.
    # main() still returns 124 for the original timeout in that situation.
    if not wait_for_exit(process, timeout=30):
        with log_file.open("a", encoding="utf-8") as log:
            log.write("Watchdog child did not exit after cleanup attempts.\n")


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
