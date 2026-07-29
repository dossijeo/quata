#!/usr/bin/env bash
# Validates privacy manifests in the three executable/runtime bundles shipped by Quata.
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  echo "Usage: $0 /path/to/QuataIos.app" >&2
  exit 2
fi

app_path="$1"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
manifests=(
  "$app_path/PrivacyInfo.xcprivacy"
  "$app_path/PlugIns/QuataShareExtension.appex/PrivacyInfo.xcprivacy"
  "$app_path/Frameworks/QuataShared.framework/PrivacyInfo.xcprivacy"
)
expected_manifests=(
  "$repo_root/iosApp/iosApp/PrivacyInfo.xcprivacy"
  "$repo_root/iosApp/iosShareExtension/PrivacyInfo.xcprivacy"
  "$repo_root/ios-shared/PrivacyInfo.xcprivacy"
)

for index in "${!manifests[@]}"; do
  manifest="${manifests[$index]}"
  expected="${expected_manifests[$index]}"
  test -f "$manifest"
  plutil -lint "$manifest"
  python3 - "$expected" "$manifest" <<'PY'
import plistlib
import sys

expected_path, actual_path = sys.argv[1:]
with open(expected_path, "rb") as expected_file:
    expected = plistlib.load(expected_file)
with open(actual_path, "rb") as actual_file:
    actual = plistlib.load(actual_file)
if actual != expected:
    sys.stderr.write(
        f"Privacy manifest content mismatch: {actual_path} != {expected_path}\n"
    )
    raise SystemExit(1)
PY
done

echo "Validated exact privacy manifests in app, share extension and embedded framework."
