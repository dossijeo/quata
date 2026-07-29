#!/usr/bin/env bash
set -euo pipefail

fixture_dir="$(mktemp -d "${TMPDIR:-/tmp}/quata-config-backup-test.XXXXXX")"
backup_path="$(mktemp "${TMPDIR:-/tmp}/quata-config-backup.XXXXXX")"
cleanup_fixture() {
  rm -f "$backup_path"
  rm -rf "$fixture_dir"
}
trap cleanup_fixture EXIT

config_path="$fixture_dir/QuataPublicRuntime.local.xcconfig"
printf 'original\n' > "$config_path"
chmod 0644 "$config_path"

# shellcheck source=ios-public-runtime-config-backup.sh
source "$(dirname "$0")/ios-public-runtime-config-backup.sh"
quata_backup_runtime_config "$config_path" "$backup_path"
[[ "$(quata_config_mode "$backup_path")" == "600" ]]

printf 'mutated\n' > "$config_path"
quata_restore_runtime_config "$config_path" "$backup_path"
[[ "$(quata_config_mode "$config_path")" == "644" ]]
[[ "$(cat "$config_path")" == "original" ]]
echo "iOS public runtime config backup contract passed."
