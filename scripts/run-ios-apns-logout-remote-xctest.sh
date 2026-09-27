#!/usr/bin/env bash
set -euo pipefail

: "${QUATA_IOS_DERIVED_DATA_PATH:?QUATA_IOS_DERIVED_DATA_PATH is required}"
: "${QUATA_IOS_SIMULATOR_UDID:?QUATA_IOS_SIMULATOR_UDID is required}"
: "${QUATA_IOS_APNS_REMOTE_EVIDENCE:?QUATA_IOS_APNS_REMOTE_EVIDENCE is required}"

required_secret_names=(
  QUATA_IOS_APNS_BACKEND_URL
  QUATA_IOS_APNS_PUBLISHABLE_KEY
  QUATA_IOS_APNS_PROFILE_ID
  QUATA_IOS_APNS_AUTH_USER_ID
  QUATA_IOS_APNS_ACCESS_TOKEN
  QUATA_IOS_APNS_DEVICE_TOKEN
)
for name in "${required_secret_names[@]}"; do
  [[ -n "${!name:-}" ]] || { echo "Missing private XCTest input: $name" >&2; exit 2; }
done

method="testProductionTransportRegistersAndUnregistersOwnedSyntheticToken"
selected_test="QuataIosTests/QuataIosApnsRemoteLogoutEvidenceTests/$method"
output_dir="build/ios-apns-logout-remote"
log="$output_dir/xctest.log"
summary="$output_dir/xctest-summary.json"
result_bundle="$output_dir/xctest.xcresult"
mkdir -p "$output_dir"
rm -f "$log" "$summary"
rm -rf "$result_bundle"

xctestruns=()
while IFS= read -r path; do xctestruns+=("$path"); done < <(
  find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-apns-logout.xctestrun' -type f -print
)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one original .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"

stale_source="$(find iosApp/iosAppTests core/src/iosMain -type f \( -name '*.swift' -o -name '*.kt' \) -newer "$xctestrun" -print -quit)"
[[ -z "$stale_source" ]] || { echo "Signed XCTest artifacts are stale for the current source." >&2; exit 2; }

patched_xctestrun="$(dirname "$xctestrun")/$(basename "$xctestrun" .xctestrun)-quata-apns-logout.xctestrun"
cleanup() {
  rm -f "$patched_xctestrun"
  rm -rf "$result_bundle"
}
trap cleanup EXIT
cp "$xctestrun" "$patched_xctestrun"
chmod 600 "$patched_xctestrun"

/usr/bin/python3 - "$patched_xctestrun" "${required_secret_names[@]}" <<'PY'
import os
import plistlib
import sys

path = sys.argv[1]
names = sys.argv[2:]
with open(path, "rb") as handle:
    document = plistlib.load(handle)
target = document.get("QuataIosTests")
if not isinstance(target, dict):
    raise SystemExit("QuataIosTests target missing from xctestrun")
environment = dict(target.get("EnvironmentVariables") or {})
environment["QUATA_IOS_APNS_REMOTE_EVIDENCE"] = os.environ["QUATA_IOS_APNS_REMOTE_EVIDENCE"]
for name in names:
    environment[name] = os.environ[name]
target["EnvironmentVariables"] = environment
with open(path, "wb") as handle:
    plistlib.dump(document, handle, fmt=plistlib.FMT_BINARY, sort_keys=False)
PY

set +e
/usr/bin/python3 scripts/run-ios-command-watchdog.py \
  --timeout-seconds 240 \
  --log "$log" \
  -- xcodebuild test-without-building \
    -xctestrun "$patched_xctestrun" \
    -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" \
    -resultBundlePath "$result_bundle" \
    -only-testing:"$selected_test"
watchdog_status=$?
set -e

/usr/bin/python3 scripts/check-ios-xctest-executed.py \
  --method "$method" \
  --log "$log" \
  --require-terminal-success-marker \
  --accept-selected-pass-before-watchdog-timeout
if [[ "$watchdog_status" -ne 0 && "$watchdog_status" -ne 124 ]]; then
  echo "xcodebuild failed before the selected XCTest was accepted." >&2
  exit "$watchdog_status"
fi

/usr/bin/python3 - "$log" "$summary" "$method" "${required_secret_names[@]}" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import sys

log_path = Path(sys.argv[1])
summary_path = Path(sys.argv[2])
method = sys.argv[3]
names = sys.argv[4:]
payload = log_path.read_bytes()
for name in names:
    value = os.environ[name].encode("utf-8")
    if value and value in payload:
        raise SystemExit("private XCTest input appeared in xcodebuild log")
summary = {
    "method": method,
    "status": "passed",
    "logSha256": hashlib.sha256(payload).hexdigest(),
    "terminalSuccess": True,
}
summary_path.write_text(json.dumps(summary, sort_keys=True) + "\n", encoding="utf-8")
PY

echo "iOS APNs logout XCTest passed."
