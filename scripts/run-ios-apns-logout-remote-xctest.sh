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

scrub_private_log() {
  [[ ! -f "$log" ]] && return 0
  /usr/bin/python3 - "$log" "${required_secret_names[@]}" <<'PY'
import os
from pathlib import Path
import sys

payload = Path(sys.argv[1]).read_bytes()
for name in sys.argv[2:]:
    value = os.environ[name].encode("utf-8")
    if value and value in payload:
        raise SystemExit(1)
PY
}

xctestruns=()
while IFS= read -r path; do xctestruns+=("$path"); done < <(
  find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-apns-logout.xctestrun' -type f -print
)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one original .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"

if [[ -f .quata-product-sha ]]; then
  product_sha="$(tr -d '[:space:]' < .quata-product-sha)"
else
  product_sha="$(git rev-parse HEAD)"
fi
[[ "$product_sha" =~ ^[0-9a-f]{40}$ ]] || { echo "Exact product SHA is unavailable." >&2; exit 2; }
provenance="$QUATA_IOS_DERIVED_DATA_PATH/Build/Products/quata-signed-build-provenance.json"
app="$QUATA_IOS_DERIVED_DATA_PATH/Build/Products/SimulatorSigned-iphonesimulator/QuataIos.app"
test_bundle="$app/PlugIns/QuataIosTests.xctest/QuataIosTests"
shared_framework="$app/Frameworks/QuataShared.framework/QuataShared"
[[ -f "$provenance" ]] || { echo "Signed build provenance is missing." >&2; exit 2; }
/usr/bin/python3 - "$provenance" "$QUATA_IOS_DERIVED_DATA_PATH" "$product_sha" \
  xctestrun "$xctestrun" app "$app/QuataIos" testBundle "$test_bundle" sharedFramework "$shared_framework" <<'PY'
import hashlib
import json
from pathlib import Path
import sys

provenance = Path(sys.argv[1])
root = Path(sys.argv[2]).resolve()
expected_sha = sys.argv[3]
arguments = sys.argv[4:]
document = json.loads(provenance.read_text(encoding="utf-8"))
if document.get("schemaVersion") != 1 or document.get("productSha") != expected_sha:
    raise SystemExit("Signed build provenance does not match the exact product SHA.")
artifacts = document.get("artifacts")
if not isinstance(artifacts, dict):
    raise SystemExit("Signed build provenance has no artifact inventory.")
for index in range(0, len(arguments), 2):
    name = arguments[index]
    path = Path(arguments[index + 1]).resolve()
    try:
        relative = path.relative_to(root).as_posix()
    except ValueError:
        raise SystemExit("Signed evidence artifact escaped DerivedData.")
    record = artifacts.get(name)
    if not isinstance(record, dict) or record.get("relativePath") != relative:
        raise SystemExit("Signed build provenance path mismatch.")
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    if record.get("sha256") != digest.hexdigest():
        raise SystemExit("Signed build provenance hash mismatch.")
PY

patched_xctestrun="$(dirname "$xctestrun")/$(basename "$xctestrun" .xctestrun)-quata-apns-logout.xctestrun"
cleanup() {
  local status=$?
  trap - EXIT
  rm -f "$patched_xctestrun"
  rm -rf "$result_bundle"
  if ! scrub_private_log; then
    rm -f "$log" "$summary"
    echo "Private XCTest input was removed from failed evidence output." >&2
    status=3
  fi
  exit "$status"
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
