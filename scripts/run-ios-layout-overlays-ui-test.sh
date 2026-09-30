#!/usr/bin/env bash
# Opt-in focal gate: prove keyboard-safe rotation on real Auth, composer and Official surfaces.
set -euo pipefail

: "${QUATA_IOS_AUTH_E2E_FILE:?Set QUATA_IOS_AUTH_E2E_FILE to the local credentials JSON.}"
: "${QUATA_IOS_DERIVED_DATA_PATH:?Build the simulator test bundle first and set QUATA_IOS_DERIVED_DATA_PATH.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID.}"
: "${QUATA_IOS_LAYOUT_UI_LOG_DIR:=build/reports/ios/layout-overlays-ui}"
: "${QUATA_IOS_LAYOUT_UI_TIMEOUT_SECONDS:=300}"
: "${QUATA_IOS_LAYOUT_UI_RESULT_BUNDLE_DIR:=}"
watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog: $watchdog" >&2; exit 2; }

xctestruns=()
while IFS= read -r xctestrun_path; do
  xctestruns+=("$xctestrun_path")
done < <(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-patched.xctestrun' -type f -print)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"
mkdir -p "$QUATA_IOS_LAYOUT_UI_LOG_DIR"
patched_xctestrun="$(dirname "$xctestrun")/$(basename "$xctestrun" .xctestrun)-quata-patched.xctestrun"
rm -f "$patched_xctestrun"
cp "$xctestrun" "$patched_xctestrun"
xctestrun="$patched_xctestrun"

redact_diagnostics() {
  /usr/bin/python3 -c '
import re, sys
secret = re.compile(r"(?i)(authorization\s*[:=]\s*(?:bearer\s+)?|bearer\s+|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+")
for line in sys.stdin:
    print(secret.sub(lambda match: match.group(1) + "[REDACTED]", line), end="")
'
}

timeout_diagnostics() {
  local label="$1" diagnostics="$QUATA_IOS_LAYOUT_UI_LOG_DIR/${label}-timeout-diagnostics.log"
  local devices_diag="$QUATA_IOS_LAYOUT_UI_LOG_DIR/${label}-timeout-devices.log"
  local sim_log_diag="$QUATA_IOS_LAYOUT_UI_LOG_DIR/${label}-timeout-simulator.log"
  {
    echo "===== bounded iOS command timeout: $label ====="
    /usr/bin/python3 "$watchdog" --timeout-seconds 10 --log "$devices_diag" -- xcrun simctl list devices >/dev/null 2>&1 || true
    grep -F "$QUATA_IOS_SIMULATOR_UDID" "$devices_diag" || true
    pgrep -fl 'testmanager|QuataIos|xcodebuild|simctl|run-ios-command-watchdog' || true
    /usr/bin/python3 "$watchdog" --timeout-seconds 15 --log "$sim_log_diag" -- xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" log show --last 2m --style compact --predicate 'process == "testmanagerd" OR process == "QuataIos"' >/dev/null 2>&1 || true
    redact_diagnostics < "$sim_log_diag"
  } > "$diagnostics"
  echo "Watchdog timeout diagnostics: $diagnostics" >&2
}

run_bounded() {
  local label="$1" seconds="$2" log="$3"
  shift 3
  set +e
  /usr/bin/python3 "$watchdog" --timeout-seconds "$seconds" --log "$log" -- "$@"
  local status=$?
  set -e
  redact_diagnostics < "$log"
  [[ "$status" -eq 124 ]] && timeout_diagnostics "$label"
  return "$status"
}

run_bounded bootstatus 120 "$QUATA_IOS_LAYOUT_UI_LOG_DIR/bootstatus.log" xcrun simctl bootstatus "$QUATA_IOS_SIMULATOR_UDID" -b

/usr/bin/python3 - "$xctestrun" "$QUATA_IOS_AUTH_E2E_FILE" <<'PY'
import plistlib, sys
path, credentials = sys.argv[1:]
with open(path, 'rb') as f:
    data = plistlib.load(f)
matched = set()
def patch_target(target, hint=''):
    name = f"{hint} {target.get('TestTargetName', '')} {target.get('BlueprintName', '')}"
    env = target.setdefault('EnvironmentVariables', {})
    if 'QuataIosTests' in name:
        env['QUATA_IOS_AUTH_E2E_FILE'] = credentials
        matched.add('seed')
    if 'QuataIosUITests' in name:
        env['QUATA_IOS_LAYOUT_UI_E2E'] = '1'
        env['QUATA_IOS_AUTH_UI_E2E'] = '1'
        matched.add('ui')
for configuration in data.get('TestConfigurations', []):
    for target in configuration.get('TestTargets', []):
        patch_target(target)
for key, target in data.items():
    if isinstance(target, dict):
        patch_target(target, key)
if matched != {'seed', 'ui'}:
    raise SystemExit(f'xctestrun targets missing: {matched}')
with open(path, 'wb') as f:
    plistlib.dump(data, f)
PY

run_and_require() {
  local selected="$1" method="$2" log="$3"
  local result_args=()
  if [[ -n "$QUATA_IOS_LAYOUT_UI_RESULT_BUNDLE_DIR" ]]; then
    mkdir -p "$QUATA_IOS_LAYOUT_UI_RESULT_BUNDLE_DIR"
    local result_bundle="$QUATA_IOS_LAYOUT_UI_RESULT_BUNDLE_DIR/${method}.xcresult"
    rm -rf "$result_bundle"
    result_args=(-resultBundlePath "$result_bundle")
  fi
  if [[ "${#result_args[@]}" -gt 0 ]]; then
    run_bounded "$method" "$QUATA_IOS_LAYOUT_UI_TIMEOUT_SECONDS" "$log" \
      xcodebuild test-without-building -xctestrun "$xctestrun" \
      -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" "${result_args[@]}" -only-testing:"$selected"
  else
    run_bounded "$method" "$QUATA_IOS_LAYOUT_UI_TIMEOUT_SECONDS" "$log" \
      xcodebuild test-without-building -xctestrun "$xctestrun" \
      -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" -only-testing:"$selected"
  fi
  /usr/bin/python3 scripts/check-ios-xctest-executed.py --method "$method" --log "$log" --require-terminal-success-marker
  printf 'PASS_EXECUTED:%s\n' "$method" | tee -a "$log"
}

auth_method='testAuthLaunchKeepsPhoneDraftAndKeyboardSafeAcrossRotation'
seed_method='testSeedAuthenticatedSessionForVisualGates'
cleanup_method='testClearAuthenticatedSessionAfterVisualGates'
composer_method='testAuthenticatedComposerKeepsFocusedDraftAcrossRotation'
official_method='testAuthenticatedOfficialEditorKeepsFocusedBodyAcrossRotation'
session_cleanup_required=0
finish() {
  local primary_status="$1" cleanup_status=0
  trap - EXIT
  if [[ "$session_cleanup_required" -eq 1 ]]; then
    set +e
    run_and_require "QuataIosTests/QuataIosAuthenticatedSessionSeederTests/$cleanup_method" \
      "$cleanup_method" "$QUATA_IOS_LAYOUT_UI_LOG_DIR/cleanup.log"
    cleanup_status=$?
    set -e
    if [[ "$cleanup_status" -ne 0 ]]; then
      echo "Authenticated visual-gate session cleanup was not verified." >&2
      [[ "$primary_status" -eq 0 ]] && primary_status="$cleanup_status"
    fi
  fi
  exit "$primary_status"
}
trap 'finish $?' EXIT
run_and_require "QuataIosUITests/QuataIosHostUITests/$auth_method" "$auth_method" "$QUATA_IOS_LAYOUT_UI_LOG_DIR/auth.log"
session_cleanup_required=1
run_and_require "QuataIosTests/QuataIosAuthenticatedSessionSeederTests/$seed_method" "$seed_method" "$QUATA_IOS_LAYOUT_UI_LOG_DIR/seed.log"
run_and_require "QuataIosUITests/QuataIosAuthenticatedPostPublishUITests/$composer_method" "$composer_method" "$QUATA_IOS_LAYOUT_UI_LOG_DIR/composer.log"
run_and_require "QuataIosUITests/QuataIosAuthenticatedOfficialEditorUITests/$official_method" "$official_method" "$QUATA_IOS_LAYOUT_UI_LOG_DIR/official.log"
echo "IOS_LAYOUT_OVERLAYS_UI_GATE_PASSED" >&2
