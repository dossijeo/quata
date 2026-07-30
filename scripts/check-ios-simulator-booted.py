#!/usr/bin/env python3
"""Fail closed unless a simctl JSON document reports one exact UDID as Booted."""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any


def is_booted(devices: dict[str, Any], expected_udid: str) -> bool:
    """Return true only for an exact UDID whose simulator state is Booted."""
    return any(
        device.get("udid") == expected_udid and device.get("state") == "Booted"
        for runtime_devices in devices.values()
        if isinstance(runtime_devices, list)
        for device in runtime_devices
        if isinstance(device, dict)
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--udid", required=True)
    args = parser.parse_args()

    try:
        payload = json.load(sys.stdin)
        devices = payload["devices"]
    except (json.JSONDecodeError, KeyError, OSError, TypeError, ValueError) as error:
        print(f"Could not parse simctl device JSON: {error}", file=sys.stderr)
        return 1

    if not isinstance(devices, dict):
        print("Could not parse simctl device JSON: devices is not an object", file=sys.stderr)
        return 1
    return 0 if is_booted(devices, args.udid) else 1


if __name__ == "__main__":
    sys.exit(main())
