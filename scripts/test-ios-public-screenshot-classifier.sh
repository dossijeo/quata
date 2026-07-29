#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Screenshot classifier fixtures require macOS Vision and AppKit." >&2
  exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "Screenshot classifier fixture assertions require python3." >&2
  exit 2
fi

fixture_dir="$(mktemp -d "${TMPDIR:-/tmp}/quata-ios-classifier.XXXXXX")"
trap 'rm -rf "$fixture_dir"' EXIT INT TERM

xcrun swiftc scripts/ios-public-screenshot-classifier.swift -o "$fixture_dir/classifier"
xcrun swiftc scripts/ios-public-screenshot-classifier-fixtures.swift -o "$fixture_dir/fixtures"

assert_result() {
  local fixture="$1"
  local expected="$2"
  local marker_expectation="$3"
  local ratio_expectation="$4"
  local screenshot="$fixture_dir/$fixture.png"
  local result="$fixture_dir/$fixture.json"

  "$fixture_dir/fixtures" "$fixture" "$screenshot"
  "$fixture_dir/classifier" "$screenshot" > "$result"
  python3 - "$result" "$fixture" "$expected" "$marker_expectation" "$ratio_expectation" <<'PYTHON'
import json
import sys

path, fixture, expected, marker_expectation, ratio_expectation = sys.argv[1:]
with open(path, encoding="utf-8") as source:
    result = json.load(source)
marker_found = "contenido multimedia" in result["markersFound"]
ratio = result["mediaTextContrastRatio"]
ratio_valid = (
    ratio >= 4.5 if ratio_expectation == "aa"
    else 1.0 < ratio < 4.5 if ratio_expectation == "below-aa"
    else ratio == 0.0 if ratio_expectation == "none"
    else False
)
valid = (
    result["classification"] == expected
    and marker_found == (marker_expectation == "present")
    and ratio_valid
)
if not valid:
    print(
        f"Fixture {fixture} violated its causal contract: {json.dumps(result, sort_keys=True)}",
        file=sys.stderr,
    )
    raise SystemExit(1)
PYTHON
}

assert_result pass-white-on-black pass present aa
assert_result fail-light-on-light fail present below-aa
assert_result fail-dark-on-dark fail present below-aa
assert_result fail-mirrored-bright-region fail present below-aa
assert_result fail-marker-absent fail absent none

echo "iOS public screenshot classifier contrast fixtures passed."
