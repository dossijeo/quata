#!/usr/bin/env bash
# Runs the unauthenticated iOS public-runtime matrix on an Intel/CPU-raster Mac.
set -euo pipefail

readonly bundle_id="com.quata.ios"
readonly public_backend_config="core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt"
readonly runtime_config="iosApp/Configuration/QuataPublicRuntime.local.xcconfig"
readonly default_simulators=(
  "F4891A29-9B84-4465-8805-E551EFD69CB2"
  "92D84A67-2E9D-4AE0-A34B-CF29A2F5CEF4"
)

simulators=()
while (($#)); do
  case "$1" in
    --simulator) simulators+=("${2:?--simulator requires a UDID}"); shift 2 ;;
    --help)
      cat <<'USAGE'
Usage: bash scripts/run-ios-public-simulator-matrix.sh [--simulator UDID]...

Builds once and validates cold/warm anonymous Feed launches serially. No authenticated
session, database write, RLS change, or production deployment is involved.
USAGE
      exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done
if ((${#simulators[@]} == 0)); then simulators=("${default_simulators[@]}"); fi
for udid in "${simulators[@]}"; do
  [[ "$udid" =~ ^[A-Fa-f0-9-]{36}$ ]] || { echo "Invalid simulator UDID." >&2; exit 2; }
done

[[ "$(uname -s)" == "Darwin" && "$(uname -m)" == "x86_64" ]] || {
  echo "This matrix requires the Intel macOS CPU-raster lane." >&2; exit 2;
}
[[ -f "$public_backend_config" ]] || { echo "Missing versioned client configuration source." >&2; exit 2; }

if [[ -f "$HOME/.config/quata/ios-intel.env" ]]; then
  # shellcheck source=/dev/null
  source "$HOME/.config/quata/ios-intel.env"
fi
[[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] || {
  echo "JAVA_HOME must be configured by bootstrap-ios-intel-mac.sh." >&2; exit 2;
}
[[ -n "${JBR_HOME:-}" && -x "$JBR_HOME/bin/java" ]] || {
  echo "JBR_HOME must be configured by bootstrap-ios-intel-mac.sh." >&2; exit 2;
}
for command in python3 xcrun xcodebuild xcodegen java; do
  command -v "$command" >/dev/null || { echo "Required command unavailable: $command" >&2; exit 2; }
done
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.java.installations.paths=$JAVA_HOME,$JBR_HOME -Dorg.gradle.java.installations.auto-download=false"

readonly worktree_root="$(pwd -P)"
readonly run_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
readonly report_dir="build/reports/ios/public-simulator-matrix/$run_id"
readonly derived_data="$worktree_root/build/ios-public-simulator-matrix-derived-data-$run_id"
readonly lock_dir="${TMPDIR:-/tmp}/quata-ios-public-simulator-matrix.lock"
readonly lock_token="$run_id-$RANDOM"
readonly classifier_bin="$report_dir/ios-public-screenshot-classifier"
mkdir -p "$report_dir"
umask 077
backup_config=""
runtime_config_touched=0
lock_acquired=0
QUATA_RUNTIME_CONFIG_HAD=0
QUATA_RUNTIME_CONFIG_MODE=""
# shellcheck source=ios-public-runtime-config-backup.sh
source scripts/ios-public-runtime-config-backup.sh

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if ((runtime_config_touched)); then
    if ! quata_restore_runtime_config "$runtime_config" "$backup_config"; then
      echo "Failed to restore the local runtime configuration." >&2
      status=1
    fi
  elif [[ -n "$backup_config" ]]; then
    rm -f "$backup_config" || status=1
  fi
  if command -v xcrun >/dev/null 2>&1; then
    for cleanup_udid in "${simulators[@]}"; do
      xcrun simctl shutdown "$cleanup_udid" >/dev/null 2>&1 || true
    done
  fi
  case "$derived_data" in
    "$worktree_root"/build/ios-public-simulator-matrix-derived-data-"$run_id")
      rm -rf "$derived_data" || status=1
      ;;
    *) echo "Refusing unsafe DerivedData cleanup target." >&2; status=1 ;;
  esac
  if ((lock_acquired)); then
    if [[ -f "$lock_dir/owner" ]]; then
      if grep -Fxq "token=$lock_token" "$lock_dir/owner"; then
        if ! rm -f "$lock_dir/owner" || ! rmdir "$lock_dir" 2>/dev/null; then
          echo "Owned lock could not be removed." >&2
          status=1
        fi
      else
        echo "Lock ownership changed; refusing to remove it." >&2
        status=1
      fi
    else
      rmdir "$lock_dir" 2>/dev/null || {
        echo "Owned lock without owner file could not be removed." >&2
        status=1
      }
    fi
  fi
  printf '{"outcome":"%s","run_id":"%s","report_directory":"%s"}\n' \
    "$([[ $status -eq 0 ]] && echo success || echo failure)" "$run_id" "$report_dir" > "$report_dir/summary.json"
  exit "$status"
}

# mkdir is the atomic cross-process mutex. The owner file is diagnostic only; stale locks are
# never stolen automatically because another process may still own simulators or local config.
if ! mkdir "$lock_dir" 2>/dev/null; then
  echo "iOS public simulator matrix is already locked." >&2
  [[ -f "$lock_dir/owner" ]] && sed -n '1,4p' "$lock_dir/owner" >&2
  exit 75
fi
lock_acquired=1
trap cleanup EXIT INT TERM
{
  printf 'token=%s\n' "$lock_token"
  printf 'pid=%s\n' "$$"
  printf 'started_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'worktree=%s\n' "$worktree_root"
} > "$lock_dir/owner"

backup_config="$(mktemp "${TMPDIR:-/tmp}/quata-public-runtime.XXXXXX")"
quata_backup_runtime_config "$runtime_config" "$backup_config"
runtime_config_touched=1

# The parser accepts exactly one uncommented Kotlin declaration per public constant, validates
# both values, and writes an ignored xcconfig without printing either value.
python3 scripts/ios-public-client-config.py --source "$public_backend_config" --output "$runtime_config"
chmod 600 "$runtime_config"
bash scripts/check-ios-release-readiness.sh --require-public-runtime > "$report_dir/readiness.log"
xcrun swiftc scripts/ios-public-screenshot-classifier.swift -o "$classifier_bin"

# Do not use xcodebuild -showBuildSettings: it can expose the temporary public values.
QUATA_IOS_DERIVED_DATA_PATH="$derived_data" \
QUATA_IOS_RESULT_BUNDLE_PATH="$report_dir/QuataIos-build.xcresult" \
bash scripts/build-ios-intel-simulator.sh > "$report_dir/build.log" 2>&1
app_path="$derived_data/Build/Products/Debug-iphonesimulator/QuataIos.app"
[[ -d "$app_path" ]] || { echo "Built app bundle was not found." >&2; exit 1; }

launch_and_verify() {
  local udid="$1"
  local phase="$2"
  local output pid
  output="$(xcrun simctl launch "$udid" "$bundle_id" 2>&1)"
  printf '%s\n' "$output" > "$report_dir/$udid-$phase-launch.log"
  pid="$(printf '%s\n' "$output" | sed -nE 's/^[^:]+:[[:space:]]*([1-9][0-9]*)[[:space:]]*$/\1/p')"
  [[ "$pid" =~ ^[1-9][0-9]*$ ]] || {
    echo "Could not parse a positive $phase launch PID for $udid." >&2
    return 1
  }
  xcrun simctl spawn "$udid" launchctl procinfo "$pid" \
    > "$report_dir/$udid-$phase-procinfo.log" 2>&1
  xcrun simctl spawn "$udid" launchctl print user/501 \
    > "$report_dir/$udid-$phase-launchctl.log" 2>&1
  grep -Fq "program path = " "$report_dir/$udid-$phase-procinfo.log"
  grep -Fq "/QuataIos.app/QuataIos" "$report_dir/$udid-$phase-procinfo.log"
  grep -Eq "^[[:space:]]*pid = $pid$" "$report_dir/$udid-$phase-procinfo.log"
  grep -Fq "job state = running" "$report_dir/$udid-$phase-procinfo.log" || {
    echo "Launched $phase PID is not alive as QuataIos on $udid." >&2
    return 1
  }
  grep -Fq "UIKitApplication:$bundle_id" "$report_dir/$udid-$phase-launchctl.log"
  launched_pid="$pid"
}

capture_and_classify() {
  local udid="$1"
  local phase="$2"
  local initial_wait="$3"
  local attempt screenshot result status
  sleep "$initial_wait"
  classified_status="fail"
  classified_result=""
  classified_screenshot=""
  for attempt in 1 2 3; do
    screenshot="$report_dir/$udid-$phase-attempt-$attempt.png"
    result="$report_dir/$udid-$phase-attempt-$attempt.visual.json"
    xcrun simctl io "$udid" screenshot "$screenshot" \
      > "$report_dir/$udid-$phase-attempt-$attempt-screenshot.log" 2>&1
    "$classifier_bin" "$screenshot" > "$result"
    status="$(python3 - "$result" <<'PY'
import json, pathlib, sys
value = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))["classification"]
if value not in {"pass", "degraded", "fail"}:
    raise SystemExit("invalid visual classification")
print(value)
PY
)"
    classified_status="$status"
    classified_result="${result#$report_dir/}"
    classified_screenshot="${screenshot#$report_dir/}"
    [[ "$status" == "pass" ]] && return 0
    ((attempt < 3)) && sleep 6
  done
  return 0
}

printf '{"run_id":"%s","mode":"public-unauthenticated","simulators":[' "$run_id" > "$report_dir/matrix.json"
first=1
matrix_hold=0
for udid in "${simulators[@]}"; do
  # Only one maintained simulator may be booted by this locked run.
  for other in "${simulators[@]}"; do
    [[ "$other" == "$udid" ]] || xcrun simctl shutdown "$other" >/dev/null 2>&1 || true
  done
  xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
  xcrun simctl boot "$udid" > "$report_dir/$udid-boot.log" 2>&1
  python3 scripts/run-ios-command-watchdog.py --timeout-seconds 360 \
    --log "$report_dir/$udid-bootstatus.log" -- xcrun simctl bootstatus "$udid" -b
  xcrun simctl install "$udid" "$app_path" > "$report_dir/$udid-install.log" 2>&1
  xcrun simctl get_app_container "$udid" "$bundle_id" app > "$report_dir/$udid-app-container.log" 2>&1
  for service in location camera; do
    xcrun simctl privacy "$udid" grant "$service" "$bundle_id" \
      >> "$report_dir/$udid-permissions.log" 2>&1 || true
  done

  launch_and_verify "$udid" cold
  cold_pid="$launched_pid"
  capture_and_classify "$udid" cold 12
  cold_status="$classified_status"
  cold_visual="$classified_result"
  cold_screenshot="$classified_screenshot"

  xcrun simctl terminate "$udid" "$bundle_id" > "$report_dir/$udid-warm-terminate.log" 2>&1
  launch_and_verify "$udid" warm
  warm_pid="$launched_pid"
  capture_and_classify "$udid" warm 12
  warm_status="$classified_status"
  warm_visual="$classified_result"
  warm_screenshot="$classified_screenshot"

  # Mandatory, fail-closed app diagnostics. At least one real HTTPS 200 must accompany a visual
  # Feed classification; no screenshot alone is enough.
  xcrun simctl spawn "$udid" log show --last 5m --style compact \
    --predicate "processIdentifier == $cold_pid OR processIdentifier == $warm_pid" \
    > "$report_dir/$udid-app.log" 2>&1
  [[ -s "$report_dir/$udid-app.log" ]] || { echo "Required app log is empty for $udid." >&2; exit 1; }
  python3 scripts/ios-public-log-evidence.py \
    --log "$report_dir/$udid-app.log" \
    --pid "$cold_pid" \
    --pid "$warm_pid" \
    > "$report_dir/$udid-log-evidence.json"

  # These are public OS URL-dispatch checks. They do not claim authenticated app routing.
  for route in \
    'https://egquata.com/#post-public-matrix' \
    'https://egquata.com/#official-public-matrix' \
    'https://egquata.com/#whats-new'; do
    xcrun simctl openurl "$udid" "$route" >> "$report_dir/$udid-public-deeplinks.log" 2>&1
  done
  xcrun simctl list devices -j > "$report_dir/$udid-devices.json"

  [[ "$cold_status" == "pass" && "$warm_status" == "pass" ]] || matrix_hold=1
  if ((first)); then first=0; else printf ',' >> "$report_dir/matrix.json"; fi
  printf '{"udid":"%s","cold":{"pid":"%s","classification":"%s","screenshot":"%s","visual":"%s"},"warm":{"pid":"%s","classification":"%s","screenshot":"%s","visual":"%s"},"http_200":true,"crash_signatures":0,"public_deep_links":"simctl-dispatched"}' \
    "$udid" "$cold_pid" "$cold_status" "$cold_screenshot" "$cold_visual" \
    "$warm_pid" "$warm_status" "$warm_screenshot" "$warm_visual" >> "$report_dir/matrix.json"
  xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
done
matrix_overall="go"
((matrix_hold)) && matrix_overall="hold"
printf '],"overall":"%s"}\n' "$matrix_overall" >> "$report_dir/matrix.json"
if ((matrix_hold)); then
  echo "iOS public simulator matrix is HOLD: at least one visual phase was degraded or failed." >&2
  exit 3
fi
echo "iOS public simulator matrix passed; evidence: $report_dir"
