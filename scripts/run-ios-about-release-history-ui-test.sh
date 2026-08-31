#!/usr/bin/env bash
set -euo pipefail

: "${QUATA_IOS_DERIVED_DATA_PATH:?Build the signed simulator test bundle first and set QUATA_IOS_DERIVED_DATA_PATH.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID.}"
: "${QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR:=build/reports/ios/ABOUT-RELEASE-HISTORY-ui}"
: "${QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_TIMEOUT_SECONDS:=240}"
: "${QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_RESULT_BUNDLE_DIR:=}"

watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog: $watchdog" >&2; exit 2; }

xctestruns=()
while IFS= read -r xctestrun_path; do
  xctestruns+=("$xctestrun_path")
done < <(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-patched.xctestrun' -type f -print)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"
mkdir -p "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR"

redact_diagnostics() {
  /usr/bin/python3 -c '
import re, sys
secret = re.compile(r"(?i)(bearer\\s+|authorization\\s*[:=]\\s*|token\\s*[:=]\\s*|password\\s*[:=]\\s*|apikey\\s*[:=]\\s*)[^\\s,;]+")
for line in sys.stdin:
    print(secret.sub(lambda match: match.group(1) + "[REDACTED]", line), end="")
'
}

run_bounded() {
  local label="$1" seconds="$2" log="$3"
  shift 3
  echo "[$label] starting (watchdog ${seconds}s)" >&2
  set +e
  /usr/bin/python3 "$watchdog" --timeout-seconds "$seconds" --log "$log" -- "$@"
  local status=$?
  cat "$log"
  if [[ "$status" -eq 124 ]]; then
    {
      echo "===== bounded iOS command timeout: $label ====="
      xcrun simctl list devices | grep -F "$QUATA_IOS_SIMULATOR_UDID" || true
      ps -axo pid,ppid,state,etime,command | grep -E '[t]estmanager|[Q]uataIos' || true
      xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" log show --last 2m --style compact \
        --predicate 'process == "testmanagerd" OR process == "QuataIos"' 2>&1 | redact_diagnostics
    } > "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR/${label}-timeout-diagnostics.log"
  fi
  return "$status"
}

set +e
run_bounded bootstatus 120 "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR/bootstatus.log" \
  xcrun simctl bootstatus "$QUATA_IOS_SIMULATOR_UDID" -b
boot_status=$?
set -e
[[ "$boot_status" -eq 0 ]] || exit "$boot_status"

selected='QuataIosUITests/QuataIosHostUITests/testAboutReleaseHistoryFixtureRendersRealSharedComposeSurfaces'
result_args=()
if [[ -n "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_RESULT_BUNDLE_DIR" ]]; then
  mkdir -p "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_RESULT_BUNDLE_DIR"
  result_bundle="$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_RESULT_BUNDLE_DIR/about-release-history.xcresult"
  rm -rf "$result_bundle"
  result_args=(-resultBundlePath "$result_bundle")
fi

set +e
run_bounded testAboutReleaseHistoryFixtureRendersRealSharedComposeSurfaces "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_TIMEOUT_SECONDS" "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR/ui.log" \
  xcodebuild test-without-building -xctestrun "$xctestrun" \
  -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" "${result_args[@]}" -only-testing:"$selected"
xcode_status=$?
set -e

/usr/bin/python3 scripts/check-ios-xctest-executed.py \
  --method testAboutReleaseHistoryFixtureRendersRealSharedComposeSurfaces \
  --log "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR/ui.log" --require-terminal-success-marker
if [[ "$xcode_status" -ne 0 ]]; then
  grep -q '\*\* TEST EXECUTE SUCCEEDED \*\*' "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR/ui.log" || exit "$xcode_status"
fi
printf 'PASS_EXECUTED:%s\n' testAboutReleaseHistoryFixtureRendersRealSharedComposeSurfaces | tee -a "$QUATA_IOS_ABOUT_RELEASE_HISTORY_UI_LOG_DIR/ui.log"
echo "IOS_ABOUT_RELEASE_HISTORY_UI_GATE_PASSED" >&2
