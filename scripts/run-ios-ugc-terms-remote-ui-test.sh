#!/usr/bin/env bash
set -euo pipefail
: "${QUATA_IOS_AUTH_E2E_FILE:?Set the temporary credentials file.}"
: "${QUATA_IOS_DERIVED_DATA_PATH:?Set the signed simulator DerivedData path.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set the simulator UDID.}"
: "${QUATA_IOS_UGC_TERMS_PROFILE_ID:?Set the authenticated profile id.}"
: "${QUATA_IOS_UGC_TERMS_UI_LOG_DIR:=build/reports/ios/UGC-TERMS-remote-ui}"
: "${QUATA_IOS_UGC_TERMS_UI_TIMEOUT_SECONDS:=300}"

watchdog="scripts/run-ios-command-watchdog.py"
xctestrun="$(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-patched.xctestrun' -type f -print -quit)"
[[ -n "$xctestrun" ]] || { echo "No .xctestrun found" >&2; exit 2; }
mkdir -p "$QUATA_IOS_UGC_TERMS_UI_LOG_DIR"
patched="$(dirname "$xctestrun")/$(basename "$xctestrun" .xctestrun)-quata-ugc-terms-patched.xctestrun"
cp "$xctestrun" "$patched"
xctestrun="$patched"

/usr/bin/python3 - "$xctestrun" "$QUATA_IOS_AUTH_E2E_FILE" <<'PY'
import plistlib, sys
path, credentials = sys.argv[1:]
with open(path, 'rb') as f: data = plistlib.load(f)
matched=set()
def patch(target, hint=''):
    name=f"{hint} {target.get('TestTargetName','')} {target.get('BlueprintName','')}"
    env=target.setdefault('EnvironmentVariables',{})
    if 'QuataIosTests' in name:
        env['QUATA_IOS_AUTH_E2E_FILE']=credentials; matched.add('seed')
    if 'QuataIosUITests' in name:
        env['QUATA_IOS_UGC_TERMS_REMOTE_E2E']='1'; matched.add('ui')
for config in data.get('TestConfigurations',[]):
    for target in config.get('TestTargets',[]): patch(target)
for key,target in data.items():
    if isinstance(target,dict): patch(target,key)
if matched != {'seed','ui'}: raise SystemExit(f'xctestrun targets missing: {matched}')
with open(path,'wb') as f: plistlib.dump(data,f)
PY

run_one() {
  local selected="$1" method="$2" log="$3"
  /usr/bin/python3 "$watchdog" --timeout-seconds "$QUATA_IOS_UGC_TERMS_UI_TIMEOUT_SECONDS" --log "$log" -- \
    xcodebuild test-without-building -xctestrun "$xctestrun" \
    -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" -only-testing:"$selected"
  cat "$log"
  /usr/bin/python3 scripts/check-ios-xctest-executed.py --method "$method" --log "$log" --require-terminal-success-marker
}

run_one 'QuataIosTests/QuataIosAuthenticatedSessionSeederTests/testSeedAuthenticatedSessionForVisualGates' \
  testSeedAuthenticatedSessionForVisualGates "$QUATA_IOS_UGC_TERMS_UI_LOG_DIR/seed.log"
xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" defaults delete com.quata.ios \
  "ugc_terms:accepted:$QUATA_IOS_UGC_TERMS_PROFILE_ID:2026-07" >/dev/null 2>&1 || true
xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" defaults delete com.quata.ios \
  "ugc_terms:pending:$QUATA_IOS_UGC_TERMS_PROFILE_ID:2026-07" >/dev/null 2>&1 || true
run_one 'QuataIosUITests/QuataIosUgcTermsRemoteUITests/testAuthenticatedUserAcceptsTermsThroughProductGate' \
  testAuthenticatedUserAcceptsTermsThroughProductGate "$QUATA_IOS_UGC_TERMS_UI_LOG_DIR/ui.log"
echo IOS_UGC_TERMS_REMOTE_UI_GATE_PASSED >&2
