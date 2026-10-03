#!/usr/bin/env bash
# Hermetic iOS gate for one recoverable local document-open failure and Quick Look retry.
set -euo pipefail

: "${QUATA_IOS_DERIVED_DATA_PATH:?Build the SimulatorSigned test bundle first and set QUATA_IOS_DERIVED_DATA_PATH.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID.}"
: "${QUATA_IOS_DOCUMENT_RETRY_LOG_DIR:=build/reports/ios/document-retry-local}"
: "${QUATA_IOS_DOCUMENT_RETRY_TIMEOUT_SECONDS:=360}"
: "${QUATA_IOS_DOCUMENT_RETRY_RESULT_BUNDLE:=}"

watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog: $watchdog" >&2; exit 2; }

xctestruns=()
while IFS= read -r xctestrun_path; do
  xctestruns+=("$xctestrun_path")
done < <(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" \
  -name '*.xctestrun' ! -name '*-document-retry-patched.xctestrun' -type f -print)
[[ "${#xctestruns[@]}" -eq 1 ]] || {
  echo "Expected exactly one .xctestrun, found ${#xctestruns[@]}" >&2
  exit 2
}
xctestrun="${xctestruns[0]}"
mkdir -p "$QUATA_IOS_DOCUMENT_RETRY_LOG_DIR"
patched_xctestrun="$(dirname "$xctestrun")/$(basename "$xctestrun" .xctestrun)-document-retry-patched.xctestrun"
cp "$xctestrun" "$patched_xctestrun"

/usr/bin/python3 - "$patched_xctestrun" <<'PY'
import plistlib
import sys

path = sys.argv[1]
with open(path, "rb") as source:
    data = plistlib.load(source)

matched = False

def patch_target(target, hint=""):
    global matched
    name = f"{hint} {target.get('TestTargetName', '')} {target.get('BlueprintName', '')}"
    if "QuataIosUITests" not in name:
        return
    target.setdefault("EnvironmentVariables", {})["QUATA_IOS_DOCUMENT_RETRY_LOCAL_UI_E2E"] = "1"
    matched = True

for configuration in data.get("TestConfigurations", []):
    for target in configuration.get("TestTargets", []):
        patch_target(target)
for key, target in data.items():
    if isinstance(target, dict):
        patch_target(target, key)

if not matched:
    raise SystemExit("QuataIosUITests target missing from xctestrun")
with open(path, "wb") as destination:
    plistlib.dump(data, destination)
PY

cleanup_fixture() {
  local container fixture
  xcrun simctl terminate "$QUATA_IOS_SIMULATOR_UDID" com.quata.ios >/dev/null 2>&1 || true
  container="$(xcrun simctl get_app_container "$QUATA_IOS_SIMULATOR_UDID" com.quata.ios data)"
  case "$container" in
    "$HOME/Library/Developer/CoreSimulator/Devices/$QUATA_IOS_SIMULATOR_UDID/data/Containers/Data/Application/"*) ;;
    *) echo "Refusing unexpected Simulator data container" >&2; return 1 ;;
  esac
  fixture="$container/tmp/quata-document-retry.rtf"
  /bin/rm -f -- "$fixture"
  [[ ! -e "$fixture" ]] || { echo "iOS document retry fixture cleanup failed" >&2; return 1; }
  {
    printf 'product_sha=%s\n' "$(git rev-parse HEAD)"
    printf 'simulator_udid=%s\n' "$QUATA_IOS_SIMULATOR_UDID"
    printf 'bundle_id=com.quata.ios\n'
    printf 'local_fixture_file_absent=true\n'
  } > "$QUATA_IOS_DOCUMENT_RETRY_LOG_DIR/cleanup.log"
}

# Remove residue from an interrupted prior run before creating this run's one fixed fixture.
cleanup_fixture

result_args=()
if [[ -n "$QUATA_IOS_DOCUMENT_RETRY_RESULT_BUNDLE" ]]; then
  rm -rf "$QUATA_IOS_DOCUMENT_RETRY_RESULT_BUNDLE"
  result_args=(-resultBundlePath "$QUATA_IOS_DOCUMENT_RETRY_RESULT_BUNDLE")
fi

selected='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testLocalDocumentRetryOpensQuickLookAndReturnsWithoutBackend'
method='testLocalDocumentRetryOpensQuickLookAndReturnsWithoutBackend'
log="$QUATA_IOS_DOCUMENT_RETRY_LOG_DIR/ui.log"
set +e
/usr/bin/python3 "$watchdog" --timeout-seconds "$QUATA_IOS_DOCUMENT_RETRY_TIMEOUT_SECONDS" --log "$log" -- \
  xcodebuild test-without-building -xctestrun "$patched_xctestrun" \
    -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" \
    "${result_args[@]}" -only-testing:"$selected"
xcode_status=$?
set -e
cat "$log"

# Cleanup is part of the gate and runs before interpreting XCTest's terminal result.
cleanup_fixture
/usr/bin/python3 scripts/check-ios-xctest-executed.py \
  --method "$method" --log "$log" --require-terminal-success-marker
[[ "$xcode_status" -eq 0 ]] || exit "$xcode_status"
printf 'PASS_EXECUTED:%s\n' "$method" | tee -a "$log"
echo "IOS_DOCUMENT_RETRY_LOCAL_UI_GATE_PASSED" >&2
