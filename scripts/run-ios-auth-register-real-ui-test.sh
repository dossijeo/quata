#!/usr/bin/env bash
set -euo pipefail

: "${QUATA_IOS_AUTH_REGISTER_E2E_FILE:?Set the private registration input JSON path.}"
: "${QUATA_IOS_AUTH_REGISTER_REAL_OPT_IN:?Set the explicit real-registration opt-in.}"
: "${QUATA_IOS_DERIVED_DATA_PATH:?Set the signed simulator build directory.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set the ephemeral simulator UDID.}"
: "${QUATA_IOS_AUTH_REGISTER_LOG_DIR:=build/reports/ios/auth-register-real-ui}"

expected_opt_in="I_ACCEPT_IOS_REGISTRATION_PRODUCT_TRIAL"
[[ "$QUATA_IOS_AUTH_REGISTER_REAL_OPT_IN" == "$expected_opt_in" ]] || {
  echo "Real iOS registration requires the exact product-trial opt-in." >&2
  exit 2
}

watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog." >&2; exit 2; }
mkdir -p "$QUATA_IOS_AUTH_REGISTER_LOG_DIR"

runner_stage="initialize"
capture_failure_diagnostics() {
  local diagnostics="$QUATA_IOS_AUTH_REGISTER_LOG_DIR/termination.log"
  {
    xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" log show --last 20m --style compact \
      --predicate 'process == "QuataIos" OR eventMessage CONTAINS "com.quata.ios"' 2>/dev/null \
      | grep -Ei 'abort|crash|exception|exit|jetsam|kill|terminat' || true
    crash_root="$HOME/Library/Developer/CoreSimulator/Devices/$QUATA_IOS_SIMULATOR_UDID/data/Library/Logs/CrashReporter"
    if [[ -d "$crash_root" ]]; then
      find "$crash_root" -type f -name 'QuataIos*' -mmin -30 -print
    fi
  } | sed -E \
    -e 's/\b[0-9]{6,}\b/[digits]/g' \
    -e 's/(bearer |authorization[=: ]+|token[=: ]+|password[=: ]+|secret[=: ]+|apikey[=: ]+)[^ ,;]+/\1[REDACTED]/Ig' \
    -e 's/[A-Za-z0-9+\/_=-]{48,}/[REDACTED]/g' \
    > "$diagnostics" || true
}

report_runner_failure() {
  local status=$?
  trap - EXIT
  if [[ "$status" -ne 0 ]]; then
    if [[ -n "${QUATA_IOS_AUTH_REGISTER_STAGE_FILE:-}" ]]; then
      printf '%s\n' "$runner_stage" > "$QUATA_IOS_AUTH_REGISTER_STAGE_FILE"
    fi
    capture_failure_diagnostics
    printf 'registration_product_ios_runner_failed:%s\n' "$runner_stage" >&2
  fi
  exit "$status"
}
trap report_runner_failure EXIT

runner_stage="discover_artifacts"
xctestruns=()
while IFS= read -r path; do xctestruns+=("$path"); done < <(
  find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' -type f -print
)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one .xctestrun." >&2; exit 2; }
xctestrun="${xctestruns[0]}"
app="$(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -path '*SimulatorSigned-iphonesimulator/QuataIos.app' -type d -print -quit)"
[[ -n "$app" ]] || { echo "Signed simulator app missing." >&2; exit 2; }

runner_stage="patch_xctestrun"
python3 - "$xctestrun" "$QUATA_IOS_AUTH_REGISTER_E2E_FILE" "$QUATA_IOS_AUTH_REGISTER_REAL_OPT_IN" <<'PY'
import plistlib, sys
path, input_file, opt_in = sys.argv[1:]
with open(path, 'rb') as handle:
    data = plistlib.load(handle)
matched = False
def patch(target, hint=''):
    global matched
    name = f"{hint} {target.get('TestTargetName', '')} {target.get('BlueprintName', '')}"
    if 'QuataIosUITests' not in name:
        return
    env = target.setdefault('EnvironmentVariables', {})
    env['QUATA_IOS_AUTH_REGISTER_E2E_FILE'] = input_file
    env['QUATA_IOS_AUTH_REGISTER_REAL_OPT_IN'] = opt_in
    matched = True
for configuration in data.get('TestConfigurations', []):
    for target in configuration.get('TestTargets', []):
        patch(target)
for key, target in data.items():
    if isinstance(target, dict):
        patch(target, key)
if not matched:
    raise SystemExit('xctestrun QuataIosUITests target missing')
with open(path, 'wb') as handle:
    plistlib.dump(data, handle)
PY

run_bounded() {
  local label="$1" timeout="$2" log="$3"
  shift 3
  state_args=()
  if [[ -n "${QUATA_IOS_AUTH_REGISTER_WATCHDOG_STATE_DIR:-}" ]]; then
    safe_label="${label//[^A-Za-z0-9_.-]/_}"
    state_file="$QUATA_IOS_AUTH_REGISTER_WATCHDOG_STATE_DIR/watchdog-$safe_label.json"
    printf '%s\n' "$state_file" >> "$QUATA_IOS_AUTH_REGISTER_WATCHDOG_STATE_DIR/watchdog-manifest"
    state_args=(--state-file "$state_file")
  fi
  /usr/bin/python3 "$watchdog" --timeout-seconds "$timeout" --log "$log" "${state_args[@]}" -- "$@"
  local status=$?
  cat "$log"
  return "$status"
}

# Simulator can keep displaying a deleted ephemeral device and silently reconnect
# the Mac keyboard. Rebind the GUI to this trial's UDID before XCTest starts, then
# disable the hardware keyboard through Simulator's own menu so private values can
# be entered with coordinate taps without appearing in XCTest logs.
runner_stage="stop_stale_simulator"
killall Simulator >/dev/null 2>&1 || true
runner_stage="boot_selected_simulator"
xcrun simctl boot "$QUATA_IOS_SIMULATOR_UDID" >/dev/null 2>&1 || true
runner_stage="wait_for_selected_simulator"
run_bounded bootstatus 120 "$QUATA_IOS_AUTH_REGISTER_LOG_DIR/bootstatus.log" \
  xcrun simctl bootstatus "$QUATA_IOS_SIMULATOR_UDID" -b
runner_stage="configure_keyboard_preferences"
xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" defaults write \
  com.apple.keyboard.preferences MultilingualKeyboardTip -int 1
xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" defaults write \
  com.apple.keyboard.preferences KeyboardsCurrentAndNext -array \
  'es_ES@sw=QWERTY-Spanish;hw=Automatic;ml=2' \
  'es_ES@sw=QWERTY-Spanish;hw=Automatic;ml=2'
runner_stage="launch_selected_simulator"
open -na /Applications/Xcode.app/Contents/Developer/Applications/Simulator.app \
  --args -CurrentDeviceUDID "$QUATA_IOS_SIMULATOR_UDID"
runner_stage="configure_software_keyboard"
osascript <<'APPLESCRIPT'
tell application "Simulator" to activate
tell application "System Events"
  repeat 60 times
    if exists process "Simulator" then
      tell process "Simulator"
        try
          perform action "AXShowMenu" of menu bar item "I/O" of menu bar 1
          delay 0.2
          set keyboardItem to menu item "Keyboard" of menu 1 of menu bar item "I/O" of menu bar 1
          perform action "AXShowMenu" of keyboardItem
          delay 0.2
          set hardwareItem to menu item "Connect Hardware Keyboard" of menu 1 of keyboardItem
          if enabled of hardwareItem then
            set markValue to value of attribute "AXMenuItemMarkChar" of hardwareItem
            if markValue is not missing value then click hardwareItem
            if markValue is missing value then key code 53
            return
          end if
        end try
      end tell
    end if
    delay 0.5
  end repeat
  error "Simulator keyboard controls did not become available for the selected device."
end tell
APPLESCRIPT
runner_stage="install_application"
if ! xcrun simctl install "$QUATA_IOS_SIMULATOR_UDID" "$app"; then
  sleep 2
  xcrun simctl install "$QUATA_IOS_SIMULATOR_UDID" "$app"
fi
runner_stage="resolve_application_container"
container="$(xcrun simctl get_app_container "$QUATA_IOS_SIMULATOR_UDID" com.quata.ios data)"
input_dir="$container/Library/Application Support"
runner_stage="copy_private_input"
mkdir -p "$input_dir"
cp "$QUATA_IOS_AUTH_REGISTER_E2E_FILE" "$input_dir/auth-register-product-input.json"
chmod 600 "$input_dir/auth-register-product-input.json"

selected='QuataIosUITests/QuataIosHostUITests/testRealAuthRegistrationSubmitsOnceAndRestoresAuthenticatedFeed'
method='testRealAuthRegistrationSubmitsOnceAndRestoresAuthenticatedFeed'
log="$QUATA_IOS_AUTH_REGISTER_LOG_DIR/ui.log"
result_bundle="$QUATA_IOS_DERIVED_DATA_PATH/TestResults/AuthRegister.xcresult"
mkdir -p "$(dirname "$result_bundle")"
rm -rf "$result_bundle"
set +e
runner_stage="execute_xctest"
run_bounded "$method" 900 "$log" \
  xcodebuild test-without-building -xctestrun "$xctestrun" \
  -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" \
  -resultBundlePath "$result_bundle" \
  -only-testing:"$selected"
status=$?
set -e
rm -f "$input_dir/auth-register-product-input.json"
[[ "$status" -eq 0 ]] || exit "$status"

runner_stage="validate_xctest"
python3 scripts/check-ios-xctest-executed.py \
  --method "$method" --log "$log" --require-terminal-success-marker
grep -q 'IOS_AUTH_REGISTER_REAL_UI_GATE_PASSED' "$log"
if [[ -n "${QUATA_IOS_AUTH_REGISTER_STAGE_FILE:-}" ]]; then
  printf 'passed\n' > "$QUATA_IOS_AUTH_REGISTER_STAGE_FILE"
fi
printf 'PASS_EXECUTED:%s\n' "$method" | tee -a "$log"
runner_stage="complete"
