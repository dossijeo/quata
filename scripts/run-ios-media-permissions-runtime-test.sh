#!/usr/bin/env bash
set -euo pipefail

udid="${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID to an explicit booted Simulator UDID.}"
xctestrun="${QUATA_IOS_XCTESTRUN:?Set QUATA_IOS_XCTESTRUN to the signed build-for-testing manifest.}"
report_dir="${QUATA_IOS_MEDIA_PERMISSION_REPORT_DIR:-build/reports/ios/media-permissions-runtime}"
app_bundle_id="com.quata.ios"
test_bundle_id="com.quata.ios.tests"
test_class="QuataIosTests/IosMediaPermissionRuntimeTests"

[[ -f "$xctestrun" ]] || { echo "Missing xctestrun: $xctestrun" >&2; exit 2; }
xcrun simctl list devices -j | python3 -c '
import json, sys
udid = sys.argv[1]
devices = [device for runtime in json.load(sys.stdin)["devices"].values() for device in runtime]
match = next((device for device in devices if device["udid"] == udid), None)
if match is None or match["state"] != "Booted":
    raise SystemExit(f"Simulator {udid} is not booted")
' "$udid"

rm -rf "$report_dir"
mkdir -p "$report_dir"

cleanup() {
  xcrun simctl privacy "$udid" reset all "$app_bundle_id" >/dev/null 2>&1 || true
  xcrun simctl privacy "$udid" reset all "$test_bundle_id" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

run_test() {
  local method="$1"
  local log="$2"
  local result_bundle="$3"
  rm -rf "$result_bundle"
  xcodebuild test-without-building \
    -xctestrun "$xctestrun" \
    -destination "platform=iOS Simulator,id=$udid" \
    -only-testing:"$test_class/$method" \
    -resultBundlePath "$result_bundle" \
    > "$log" 2>&1
}

for bundle_id in "$app_bundle_id" "$test_bundle_id"; do
  xcrun simctl privacy "$udid" reset all "$bundle_id"
done
run_test \
  testResetMediaPermissionsExposeNativeUndeterminedStateAndPickerScopedFiles \
  "$report_dir/reset.log" \
  "$report_dir/reset.xcresult"

for bundle_id in "$app_bundle_id" "$test_bundle_id"; do
  xcrun simctl privacy "$udid" grant microphone "$bundle_id"
  xcrun simctl privacy "$udid" grant photos "$bundle_id"
done
run_test \
  testGrantedMicrophoneReflectsSimulatorPrivacyStateAndFilesRemainPickerScoped \
  "$report_dir/granted-microphone.log" \
  "$report_dir/granted-microphone.xcresult"

photo_grant="supported"
if ! run_test \
  testGrantedPhotoReadWritePermissionReflectsSimulatorPrivacyState \
  "$report_dir/granted-photo-read-write.log" \
  "$report_dir/granted-photo-read-write.xcresult"; then
  xcrun xcresulttool get test-results summary \
    --path "$report_dir/granted-photo-read-write.xcresult" \
    --format json > "$report_dir/granted-photo-read-write-summary.json"
  xcrun xcresulttool get test-results tests \
    --path "$report_dir/granted-photo-read-write.xcresult" \
    --format json > "$report_dir/granted-photo-read-write-tests.json"
  if node scripts/classify-ios-media-permission-photo-grant.mjs \
    --summary "$report_dir/granted-photo-read-write-summary.json" \
    --tests "$report_dir/granted-photo-read-write-tests.json" \
    > "$report_dir/granted-photo-read-write-classification.json"; then
    photo_grant="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["classification"])' \
      "$report_dir/granted-photo-read-write-classification.json")"
  else
    cat "$report_dir/granted-photo-read-write.log" >&2
    exit 1
  fi
fi

for bundle_id in "$app_bundle_id" "$test_bundle_id"; do
  xcrun simctl privacy "$udid" revoke microphone "$bundle_id"
  xcrun simctl privacy "$udid" revoke photos "$bundle_id"
done
run_test \
  testRevokedMediaPermissionsReflectSimulatorPrivacyState \
  "$report_dir/revoked.log" \
  "$report_dir/revoked.xcresult"

python3 - "$report_dir/result.json" "$udid" "$photo_grant" <<'PY'
import json
from pathlib import Path
import sys

output = Path(sys.argv[1])
document = {
    "schemaVersion": 1,
    "simulatorUdid": sys.argv[2],
    "overall": "go",
    "states": {
        "reset": "passed",
        "grantedMicrophone": "passed",
        "grantedPhotoReadWrite": sys.argv[3],
        "revokedMicrophonePhotoVideo": "passed",
        "pickerScopedFiles": "passed",
    },
    "limitations": (
        ["simctl privacy grant photos did not change PHAccessLevelReadWrite from Denied"]
        if sys.argv[3] != "supported"
        else []
    ),
}
output.write_text(json.dumps(document, sort_keys=True) + "\n", encoding="utf-8")
PY

cat "$report_dir/result.json"
