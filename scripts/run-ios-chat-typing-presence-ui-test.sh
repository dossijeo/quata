#!/usr/bin/env bash
set -euo pipefail

: "${QUATA_IOS_AUTH_E2E_FILE:?Set QUATA_IOS_AUTH_E2E_FILE.}"
: "${QUATA_IOS_DERIVED_DATA_PATH:?Set QUATA_IOS_DERIVED_DATA_PATH.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID.}"
: "${QUATA_IOS_CHAT_E2E_CONVERSATION_ID:?Set QUATA_IOS_CHAT_E2E_CONVERSATION_ID.}"
: "${QUATA_IOS_CHAT_TYPING_DRAFT:?Set QUATA_IOS_CHAT_TYPING_DRAFT.}"
: "${QUATA_IOS_CHAT_TYPING_COORDINATOR_DIRECTORY:?Set QUATA_IOS_CHAT_TYPING_COORDINATOR_DIRECTORY.}"
: "${QUATA_IOS_CHAT_TYPING_LOG_DIR:=build/reports/ios/chat-typing-presence}"
: "${QUATA_IOS_CHAT_TYPING_RESULT_BUNDLE_DIR:=build/reports/ios/chat-typing-presence/xcresults}"

watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog: $watchdog" >&2; exit 2; }
xctestruns=()
while IFS= read -r xctestrun_path; do
  xctestruns+=("$xctestrun_path")
done < <(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-patched.xctestrun' ! -name '*-quata-typing-patched.xctestrun' -type f -print)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"
patched_xctestrun="$(dirname "$xctestrun")/$(basename "$xctestrun" .xctestrun)-quata-typing-patched.xctestrun"
cp "$xctestrun" "$patched_xctestrun"
xctestrun="$patched_xctestrun"
mkdir -p "$QUATA_IOS_CHAT_TYPING_LOG_DIR" "$QUATA_IOS_CHAT_TYPING_RESULT_BUNDLE_DIR"

/usr/bin/python3 - "$xctestrun" <<'PY'
import os, plistlib, sys
path = sys.argv[1]
with open(path, 'rb') as source:
    data = plistlib.load(source)
matched = set()
def patch(target, hint=''):
    name = f"{hint} {target.get('TestTargetName', '')} {target.get('BlueprintName', '')}"
    env = target.setdefault('EnvironmentVariables', {})
    if 'QuataIosTests' in name:
        env['QUATA_IOS_AUTH_E2E_FILE'] = os.environ['QUATA_IOS_AUTH_E2E_FILE']
        matched.add('seed')
    if 'QuataIosUITests' in name:
        env['QUATA_IOS_CHAT_TYPING_PRESENCE_UI_E2E'] = '1'
        env['QUATA_IOS_CHAT_E2E_CONVERSATION_ID'] = os.environ['QUATA_IOS_CHAT_E2E_CONVERSATION_ID']
        env['QUATA_IOS_CHAT_TYPING_DRAFT'] = os.environ['QUATA_IOS_CHAT_TYPING_DRAFT']
        env['QUATA_IOS_CHAT_TYPING_COORDINATOR_DIRECTORY'] = os.environ['QUATA_IOS_CHAT_TYPING_COORDINATOR_DIRECTORY']
        matched.add('ui')
for configuration in data.get('TestConfigurations', []):
    for target in configuration.get('TestTargets', []): patch(target)
for key, target in data.items():
    if isinstance(target, dict): patch(target, key)
if matched != {'seed', 'ui'}: raise SystemExit(f'xctestrun targets missing: {matched}')
with open(path, 'wb') as destination: plistlib.dump(data, destination)
PY

run_test() {
  local selected="$1" method="$2" log="$3" result="$4"
  rm -rf "$result"
  /usr/bin/python3 "$watchdog" --timeout-seconds 240 --log "$log" -- \
    xcodebuild test-without-building -xctestrun "$xctestrun" \
      -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" \
      -resultBundlePath "$result" -only-testing:"$selected"
  cat "$log"
  /usr/bin/python3 scripts/check-ios-xctest-executed.py \
    --method "$method" --log "$log" --require-terminal-success-marker
  printf 'PASS_EXECUTED:%s\n' "$method" | tee -a "$log"
}

run_test \
  'QuataIosTests/QuataIosAuthenticatedSessionSeederTests/testSeedAuthenticatedSessionForVisualGates' \
  'testSeedAuthenticatedSessionForVisualGates' \
  "$QUATA_IOS_CHAT_TYPING_LOG_DIR/seed.log" \
  "$QUATA_IOS_CHAT_TYPING_RESULT_BUNDLE_DIR/seed.xcresult"
run_test \
  'QuataIosUITests/QuataIosRemoteTypingPresenceUITests/testAuthenticatedPeerAndComposerExchangeTypingPresence' \
  'testAuthenticatedPeerAndComposerExchangeTypingPresence' \
  "$QUATA_IOS_CHAT_TYPING_LOG_DIR/ui.log" \
  "$QUATA_IOS_CHAT_TYPING_RESULT_BUNDLE_DIR/ui.xcresult"
echo 'CHAT_TYPING_PRESENCE_IOS_UI_GATE_PASSED'
