#!/usr/bin/env bash
# Opt-in local gate: seed a real Keychain session, then open and leave Create Post without publishing.
set -euo pipefail

: "${QUATA_IOS_AUTH_E2E_FILE:?Set QUATA_IOS_AUTH_E2E_FILE to the local credentials JSON.}"
: "${QUATA_IOS_DERIVED_DATA_PATH:?Set QUATA_IOS_DERIVED_DATA_PATH to the built simulator test bundle.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID.}"
: "${QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_LOG_DIR:=build/reports/ios/CREATE-POST-POSTFLIGHT-ui}"
: "${QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_TIMEOUT_SECONDS:=300}"
: "${QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_RESULT_BUNDLE_DIR:=}"

watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog: $watchdog" >&2; exit 2; }

xctestruns=()
while IFS= read -r path; do xctestruns+=("$path"); done < <(
  find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-patched.xctestrun' -type f -print
)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"
mkdir -p "$QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_LOG_DIR"
patched="$(dirname "$xctestrun")/$(basename "$xctestrun" .xctestrun)-quata-patched.xctestrun"
rm -f "$patched"
cp "$xctestrun" "$patched"
xctestrun="$patched"

run_bounded() {
  local label="$1" seconds="$2" log="$3"
  shift 3
  set +e
  /usr/bin/python3 "$watchdog" --timeout-seconds "$seconds" --log "$log" -- "$@"
  local status=$?
  set -e
  cat "$log"
  return "$status"
}

run_bounded bootstatus 120 "$QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_LOG_DIR/bootstatus.log" \
  xcrun simctl bootstatus "$QUATA_IOS_SIMULATOR_UDID" -b

/usr/bin/python3 - "$xctestrun" "$QUATA_IOS_AUTH_E2E_FILE" <<'PY'
import plistlib, sys
path, credentials = sys.argv[1:]
with open(path, 'rb') as stream:
    data = plistlib.load(stream)
matched = set()
def patch(target, hint=''):
    name = f"{hint} {target.get('TestTargetName', '')} {target.get('BlueprintName', '')}"
    env = target.setdefault('EnvironmentVariables', {})
    if 'QuataIosTests' in name:
        env['QUATA_IOS_AUTH_E2E_FILE'] = credentials
        matched.add('seed')
    if 'QuataIosUITests' in name:
        env['QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_E2E'] = '1'
        matched.add('ui')
for configuration in data.get('TestConfigurations', []):
    for target in configuration.get('TestTargets', []):
        patch(target)
for key, target in data.items():
    if isinstance(target, dict):
        patch(target, key)
if matched != {'seed', 'ui'}:
    raise SystemExit(f'xctestrun targets missing: {matched}')
with open(path, 'wb') as stream:
    plistlib.dump(data, stream)
PY

run_and_require() {
  local selected="$1" method="$2" log="$3"
  local result_args=()
  if [[ -n "$QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_RESULT_BUNDLE_DIR" ]]; then
    mkdir -p "$QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_RESULT_BUNDLE_DIR"
    local bundle="$QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_RESULT_BUNDLE_DIR/${method}.xcresult"
    rm -rf "$bundle"
    result_args=(-resultBundlePath "$bundle")
  fi
  run_bounded "$method" "$QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_TIMEOUT_SECONDS" "$log" \
    xcodebuild test-without-building -xctestrun "$xctestrun" \
    -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" "${result_args[@]}" -only-testing:"$selected"
  /usr/bin/python3 scripts/check-ios-xctest-executed.py \
    --method "$method" --log "$log" --require-terminal-success-marker
  printf 'PASS_EXECUTED:%s\n' "$method" | tee -a "$log"
}

seed='QuataIosTests/QuataIosAuthenticatedSessionSeederTests/testSeedAuthenticatedSessionForVisualGates'
ui='QuataIosUITests/QuataIosAuthenticatedCreatePostPostflightUITests/testAuthenticatedCreatePostRootOpensAndReturnsWithoutPublishing'
run_and_require "$seed" testSeedAuthenticatedSessionForVisualGates "$QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_LOG_DIR/seed.log"
run_and_require "$ui" testAuthenticatedCreatePostRootOpensAndReturnsWithoutPublishing "$QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_LOG_DIR/ui.log"
echo "IOS_CREATE_POST_POSTFLIGHT_UI_GATE_PASSED" >&2
