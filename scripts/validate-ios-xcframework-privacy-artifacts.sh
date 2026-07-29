#!/usr/bin/env bash
# Enumerates every real QuataShared.framework slice and validates its manifest.
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  echo "Usage: $0 /path/to/QuataShared.xcframework" >&2
  exit 2
fi

xcframework_path="$1"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
expected="$repo_root/ios-shared/PrivacyInfo.xcprivacy"
test -d "$xcframework_path"
slice_count=0
while IFS= read -r -d '' framework; do
  manifest="$framework/PrivacyInfo.xcprivacy"
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
  slice_count=$((slice_count + 1))
done < <(find "$xcframework_path" -type d -name 'QuataShared.framework' -print0)

test "$slice_count" -gt 0
echo "Validated exact privacy manifests in $slice_count QuataShared XCFramework slices."
