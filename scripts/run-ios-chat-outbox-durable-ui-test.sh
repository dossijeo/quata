#!/usr/bin/env bash
# Opt-in local gate: seed a real Keychain session, then execute the normal-launch Chat durable outbox UI test.
set -euo pipefail

: "${QUATA_IOS_AUTH_E2E_FILE:?Set QUATA_IOS_AUTH_E2E_FILE to the local credentials JSON.}"
: "${QUATA_IOS_DERIVED_DATA_PATH:?Build the signed simulator test bundle first and set QUATA_IOS_DERIVED_DATA_PATH.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID.}"
: "${QUATA_IOS_CHAT_E2E_CONVERSATION_ID:?Set QUATA_IOS_CHAT_E2E_CONVERSATION_ID.}"
: "${QUATA_IOS_CHAT_OUTBOX_DURABLE_MARKER:?Set QUATA_IOS_CHAT_OUTBOX_DURABLE_MARKER.}"
: "${QUATA_IOS_CHAT_OUTBOX_DURABLE_LOG_DIR:=build/reports/ios/chat-outbox-durable}"
: "${QUATA_IOS_CHAT_OUTBOX_DURABLE_RESULT_BUNDLE_DIR:=}"
watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog: $watchdog" >&2; exit 2; }

xctestruns=()
while IFS= read -r xctestrun_path; do
  xctestruns+=("$xctestrun_path")
done < <(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-patched.xctestrun' -type f -print)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"
mkdir -p "$QUATA_IOS_CHAT_OUTBOX_DURABLE_LOG_DIR"
patched_xctestrun="$(dirname "$xctestrun")/$(basename "$xctestrun" .xctestrun)-quata-patched.xctestrun"
rm -f "$patched_xctestrun"
cp "$xctestrun" "$patched_xctestrun"
xctestrun="$patched_xctestrun"

redact_diagnostics() {
  /usr/bin/python3 -c '
import re, sys
secret = re.compile(r"(?i)(authorization\s*[:=]\s*bearer\s+|bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+")
for line in sys.stdin:
    print(secret.sub(lambda match: match.group(1) + "[REDACTED]", line), end="")
'
}

timeout_diagnostics() {
  local label="$1" diagnostics="$QUATA_IOS_CHAT_OUTBOX_DURABLE_LOG_DIR/${label}-timeout-diagnostics.log"
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
  redact_diagnostics < "$log"
  if [[ "$status" -eq 124 ]]; then
    timeout_diagnostics "$label"
  fi
  return "$status"
}

run_bounded bootstatus 120 "$QUATA_IOS_CHAT_OUTBOX_DURABLE_LOG_DIR/bootstatus.log" \
  xcrun simctl bootstatus "$QUATA_IOS_SIMULATOR_UDID" -b

/usr/bin/python3 - "$xctestrun" "$QUATA_IOS_AUTH_E2E_FILE" "$QUATA_IOS_CHAT_E2E_CONVERSATION_ID" "$QUATA_IOS_CHAT_OUTBOX_DURABLE_MARKER" <<'PY'
import plistlib, sys
path, credentials, conversation, marker = sys.argv[1:]
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
        env['QUATA_IOS_CHAT_OUTBOX_DURABLE_UI_E2E'] = '1'
        env['QUATA_IOS_CHAT_E2E_CONVERSATION_ID'] = conversation
        env['QUATA_IOS_CHAT_OUTBOX_DURABLE_MARKER'] = marker
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

seed='QuataIosTests/QuataIosAuthenticatedSessionSeederTests/testSeedAuthenticatedSessionForVisualGates'
clear='QuataIosTests/QuataIosAuthenticatedSessionSeederTests/testClearAuthenticatedSessionAfterVisualGates'
ui='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testDurableOutboxSurvivesAppRecreationAndReplaysAfterNetworkRecovery'
ui_method='testDurableOutboxSurvivesAppRecreationAndReplaysAfterNetworkRecovery'

run_and_require() {
  local selected="$1" method="$2" log="$3" timeout_seconds="${4:-180}"
  local result_args=()
  if [[ -n "$QUATA_IOS_CHAT_OUTBOX_DURABLE_RESULT_BUNDLE_DIR" ]]; then
    mkdir -p "$QUATA_IOS_CHAT_OUTBOX_DURABLE_RESULT_BUNDLE_DIR"
    local result_bundle="$QUATA_IOS_CHAT_OUTBOX_DURABLE_RESULT_BUNDLE_DIR/${method}.xcresult"
    [[ ! -e "$result_bundle" ]] || { echo "Refusing existing result bundle: $result_bundle" >&2; return 2; }
    result_args=(-resultBundlePath "$result_bundle")
  fi
  run_bounded "$method" "$timeout_seconds" "$log" \
    xcodebuild test-without-building -xctestrun "$xctestrun" \
    -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" "${result_args[@]}" -only-testing:"$selected"
  /usr/bin/python3 scripts/check-ios-xctest-executed.py \
    --method "$method" --log "$log" --require-terminal-success-marker || exit 1
  printf 'PASS_EXECUTED:%s\n' "$method" | tee -a "$log"
}

run_and_require "$seed" testSeedAuthenticatedSessionForVisualGates "$QUATA_IOS_CHAT_OUTBOX_DURABLE_LOG_DIR/seed.log"
# The host-app seeder leaves the built product installed. Reinstalling the same
# bundle immediately as the UI-test target can make CoreSimulator reject its
# placeholder while preserving the Keychain-backed session across uninstall.
xcrun simctl terminate "$QUATA_IOS_SIMULATOR_UDID" com.quata.ios >/dev/null 2>&1 || true
xcrun simctl uninstall "$QUATA_IOS_SIMULATOR_UDID" com.quata.ios >/dev/null 2>&1 || true
run_and_require "$ui" "$ui_method" "$QUATA_IOS_CHAT_OUTBOX_DURABLE_LOG_DIR/ui.log" 300
run_and_require "$clear" testClearAuthenticatedSessionAfterVisualGates "$QUATA_IOS_CHAT_OUTBOX_DURABLE_LOG_DIR/clear.log"
echo "CHAT_OUTBOX_DURABLE_IOS_UI_GATE_PASSED" >&2
