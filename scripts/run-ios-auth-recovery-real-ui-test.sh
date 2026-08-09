#!/usr/bin/env bash
# Opt-in local gate: execute the real iOS shared Auth recovery UI against an authorized account.
set -euo pipefail

: "${QUATA_IOS_AUTH_RECOVERY_E2E_FILE:?Set QUATA_IOS_AUTH_RECOVERY_E2E_FILE to the local recovery credentials JSON.}"
: "${QUATA_IOS_AUTH_RECOVERY_REAL_OPT_IN:?Set QUATA_IOS_AUTH_RECOVERY_REAL_OPT_IN explicitly.}"
: "${QUATA_IOS_DERIVED_DATA_PATH:?Build the signed simulator test bundle first and set QUATA_IOS_DERIVED_DATA_PATH.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID.}"
: "${QUATA_IOS_AUTH_RECOVERY_LOG_DIR:=build/reports/ios/auth-recovery-real-ui}"

expected_opt_in="I_ACCEPT_IOS_PASSWORD_RESET_ROUNDTRIP"
[[ "$QUATA_IOS_AUTH_RECOVERY_REAL_OPT_IN" == "$expected_opt_in" ]] || {
  echo "Real iOS Auth recovery requires explicit password roundtrip opt-in." >&2
  exit 2
}

watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog: $watchdog" >&2; exit 2; }

xctestruns=()
while IFS= read -r xctestrun_path; do
  xctestruns+=("$xctestrun_path")
done < <(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' -type f -print)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"
mkdir -p "$QUATA_IOS_AUTH_RECOVERY_LOG_DIR"

redact_diagnostics() {
  /usr/bin/python3 -c '
import re, sys
secret = re.compile(r"(?i)(bearer\\s+|authorization\\s*[:=]\\s*|token\\s*[:=]\\s*|password\\s*[:=]\\s*|secret\\s*[:=]\\s*|apikey\\s*[:=]\\s*)[^\\s,;]+")
for line in sys.stdin:
    print(secret.sub(lambda match: match.group(1) + "[REDACTED]", line), end="")
'
}

timeout_diagnostics() {
  local label="$1" diagnostics="$QUATA_IOS_AUTH_RECOVERY_LOG_DIR/${label}-timeout-diagnostics.log"
  {
    echo "===== bounded iOS command timeout: $label ====="
    echo "===== selected simulator state ====="
    xcrun simctl list devices | grep -F "$QUATA_IOS_SIMULATOR_UDID" || true
    echo "===== host processes: testmanager / QuataIos ====="
    ps -axo pid,ppid,state,etime,command | grep -E '[t]estmanager|[Q]uataIos' || true
    echo "===== last two minutes: testmanager / QuataIos (redacted) ====="
    xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" log show --last 2m --style compact \
      --predicate 'process == "testmanagerd" OR process == "QuataIos"' 2>&1 | redact_diagnostics
  } > "$diagnostics"
  echo "Watchdog timeout diagnostics: $diagnostics" >&2
}

run_bounded() {
  local label="$1" seconds="$2" log="$3"
  shift 3
  echo "[$label] starting (watchdog ${seconds}s)" >&2
  set +e
  /usr/bin/python3 "$watchdog" --timeout-seconds "$seconds" --log "$log" -- "$@"
  local status=$?
  set -e
  cat "$log"
  if [[ "$status" -eq 124 ]]; then
    timeout_diagnostics "$label"
  fi
  return "$status"
}

run_bounded bootstatus 120 "$QUATA_IOS_AUTH_RECOVERY_LOG_DIR/bootstatus.log" \
  xcrun simctl bootstatus "$QUATA_IOS_SIMULATOR_UDID" -b

/usr/bin/python3 - "$xctestrun" "$QUATA_IOS_AUTH_RECOVERY_E2E_FILE" "$QUATA_IOS_AUTH_RECOVERY_REAL_OPT_IN" <<'PY'
import plistlib, sys
path, credentials, opt_in = sys.argv[1:]
with open(path, 'rb') as f:
    data = plistlib.load(f)
matched = False
def patch_target(target, hint=''):
    global matched
    name = f"{hint} {target.get('TestTargetName', '')} {target.get('BlueprintName', '')}"
    if 'QuataIosUITests' not in name:
        return
    env = target.setdefault('EnvironmentVariables', {})
    env['QUATA_IOS_AUTH_RECOVERY_E2E_FILE'] = credentials
    env['QUATA_IOS_AUTH_RECOVERY_REAL_OPT_IN'] = opt_in
    matched = True
for configuration in data.get('TestConfigurations', []):
    for target in configuration.get('TestTargets', []):
        patch_target(target)
for key, target in data.items():
    if isinstance(target, dict):
        patch_target(target, key)
if not matched:
    raise SystemExit('xctestrun QuataIosUITests target missing')
with open(path, 'wb') as f:
    plistlib.dump(data, f)
PY

selected='QuataIosUITests/QuataIosHostUITests/testRealAuthRecoveryFixtureRoundTripsPasswordAndKeepsEvidence'
method='testRealAuthRecoveryFixtureRoundTripsPasswordAndKeepsEvidence'
log="$QUATA_IOS_AUTH_RECOVERY_LOG_DIR/ui.log"

run_bounded "$method" 240 "$log" \
  xcodebuild test-without-building -xctestrun "$xctestrun" \
  -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" -only-testing:"$selected"

/usr/bin/python3 scripts/check-ios-xctest-executed.py \
  --method "$method" --log "$log" --require-terminal-success-marker
printf 'PASS_EXECUTED:%s\n' "$method" | tee -a "$log"
echo "IOS_AUTH_RECOVERY_REAL_UI_GATE_PASSED" >&2
