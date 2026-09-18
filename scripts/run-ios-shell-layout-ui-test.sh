#!/usr/bin/env bash
set -euo pipefail

: "${QUATA_IOS_DERIVED_DATA_PATH:?Build the signed simulator test bundle first and set QUATA_IOS_DERIVED_DATA_PATH.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID.}"
: "${QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR:=build/reports/ios/FLOW-IOS-LAYOUT-ui}"
: "${QUATA_IOS_SHELL_LAYOUT_UI_TIMEOUT_SECONDS:=240}"
: "${QUATA_IOS_SHELL_LAYOUT_UI_RESULT_BUNDLE_DIR:=}"
: "${QUATA_IOS_SHELL_LAYOUT_TEST_METHOD:=testAuthenticatedFeedShellKeepsSafeViewportAcrossRotation}"

watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog: $watchdog" >&2; exit 2; }

xctestruns=()
while IFS= read -r xctestrun_path; do
  xctestruns+=("$xctestrun_path")
done < <(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-patched.xctestrun' -type f -print)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"
mkdir -p "$QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR"

redact_diagnostics() {
  /usr/bin/python3 scripts/redact-ios-diagnostics.py
}

capture_bounded_diagnostic() {
  local seconds="$1" log="$2"
  shift 2
  set +e
  /usr/bin/python3 "$watchdog" --timeout-seconds "$seconds" --log "$log" -- "$@"
  local status=$?
  set -e
  return "$status"
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
    local diagnostic_dir="$QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR/${label}-timeout-diagnostics"
    mkdir -p "$diagnostic_dir"
    capture_bounded_diagnostic 15 "$diagnostic_dir/simctl-devices.log" \
      xcrun simctl list devices || true
    capture_bounded_diagnostic 15 "$diagnostic_dir/simulator-system.log" \
      xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" log show --last 2m --style compact \
        --predicate 'process == "testmanagerd" OR process == "QuataIos"' || true
    redact_diagnostics < "$diagnostic_dir/simulator-system.log" \
      > "$diagnostic_dir/simulator-system.redacted.log"
    mv "$diagnostic_dir/simulator-system.redacted.log" "$diagnostic_dir/simulator-system.log"
    {
      echo "===== bounded iOS command timeout: $label ====="
      grep -F "$QUATA_IOS_SIMULATOR_UDID" "$diagnostic_dir/simctl-devices.log" || true
      ps -axo pid,ppid,state,etime,command | grep -E '[t]estmanager|[Q]uataIos' || true
      cat "$diagnostic_dir/simulator-system.log"
    } > "$QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR/${label}-timeout-diagnostics.log"
  fi
  return "$status"
}

set +e
run_bounded bootstatus 120 "$QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR/bootstatus.log" \
  xcrun simctl bootstatus "$QUATA_IOS_SIMULATOR_UDID" -b
boot_status=$?
set -e
[[ "$boot_status" -eq 0 ]] || exit "$boot_status"

selected="QuataIosUITests/QuataIosHostUITests/$QUATA_IOS_SHELL_LAYOUT_TEST_METHOD"
result_args=()
if [[ -n "$QUATA_IOS_SHELL_LAYOUT_UI_RESULT_BUNDLE_DIR" ]]; then
  mkdir -p "$QUATA_IOS_SHELL_LAYOUT_UI_RESULT_BUNDLE_DIR"
  result_bundle="$QUATA_IOS_SHELL_LAYOUT_UI_RESULT_BUNDLE_DIR/ios-shell-layout.xcresult"
  rm -rf "$result_bundle"
  result_args=(-resultBundlePath "$result_bundle")
fi

test_command=(
  xcodebuild test-without-building -xctestrun "$xctestrun"
  -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID"
)
if [[ "${#result_args[@]}" -gt 0 ]]; then
  test_command+=("${result_args[@]}")
fi
test_command+=(-only-testing:"$selected")

set +e
run_bounded "$QUATA_IOS_SHELL_LAYOUT_TEST_METHOD" "$QUATA_IOS_SHELL_LAYOUT_UI_TIMEOUT_SECONDS" "$QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR/ui.log" \
  "${test_command[@]}"
xcode_status=$?
set -e

/usr/bin/python3 scripts/check-ios-xctest-executed.py \
  --method "$QUATA_IOS_SHELL_LAYOUT_TEST_METHOD" \
  --log "$QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR/ui.log"
if [[ "$xcode_status" -ne 0 ]]; then
  if [[ "$xcode_status" -eq 124 ]] && grep -q 'simctl diagnose' "$QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR/ui.log"; then
    echo "IOS_SHELL_LAYOUT_XCODE_DIAGNOSTICS_TIMEOUT_AFTER_PASS" >&2
  else
    grep -q '\*\* TEST EXECUTE SUCCEEDED \*\*' "$QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR/ui.log" || exit "$xcode_status"
  fi
fi
printf 'PASS_EXECUTED:%s\n' "$QUATA_IOS_SHELL_LAYOUT_TEST_METHOD" | tee -a "$QUATA_IOS_SHELL_LAYOUT_UI_LOG_DIR/ui.log"
echo "IOS_SHELL_LAYOUT_UI_GATE_PASSED" >&2
