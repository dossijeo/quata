#!/usr/bin/env bash
# Copies Compose Multiplatform resources into the location resolved by the
# Kotlin/Native resource loader inside the iOS application bundle.
set -euo pipefail

usage() {
  echo "Usage: $0 [--verify] <QuataIos.app>" >&2
  exit 2
}

verify_only=false
if [[ "${1:-}" == "--verify" ]]; then
  verify_only=true
  shift
fi

[[ $# -eq 1 ]] || usage

app_bundle="$1"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$script_dir/.." && pwd)"
source_resources="$repository_root/designsystem/src/commonMain/composeResources"
destination_resources="$app_bundle/compose-resources/composeResources/quata.designsystem.generated.resources"
required_resources=(
  "font/quata_header_logo_q_subset.ttf"
  "drawable/quata_feed_emoji_sos.png"
  "drawable/quata_feed_emoji_rank.png"
  "drawable/quata_feed_emoji_location.png"
  "drawable/quata_feed_emoji_note.png"
  "drawable/quata_feed_emoji_document.png"
)

[[ -d "$app_bundle" ]] || { echo "iOS app bundle does not exist: $app_bundle" >&2; exit 1; }
[[ -d "$source_resources" ]] || { echo "Compose resource source does not exist: $source_resources" >&2; exit 1; }

for resource in "${required_resources[@]}"; do
  [[ -f "$source_resources/$resource" ]] || {
    echo "Required Compose resource source does not exist: $source_resources/$resource" >&2
    exit 1
  }
done

if [[ "$verify_only" == false ]]; then
  # Start clean so an incremental Xcode build cannot retain a removed COLR font or stale glyph.
  rm -rf "$destination_resources"
  for resource in "${required_resources[@]}"; do
    mkdir -p "$(dirname "$destination_resources/$resource")"
    cp "$source_resources/$resource" "$destination_resources/$resource"
  done
fi

for resource in "${required_resources[@]}"; do
  [[ -f "$destination_resources/$resource" ]] || {
    echo "Required Compose resource is missing from iOS app bundle: $destination_resources/$resource" >&2
    exit 1
  }
done

actual_resources=()
while IFS= read -r file; do
  actual_resources+=("${file#"$destination_resources/"}")
done < <(find "$destination_resources" -type f | sort)

[[ ${#actual_resources[@]} -eq ${#required_resources[@]} ]] || {
  echo "iOS app bundle has unexpected Compose resources: ${actual_resources[*]}" >&2
  exit 1
}

for resource in "${required_resources[@]}"; do
  [[ " ${actual_resources[*]} " == *" $resource "* ]] || {
    echo "Required Compose resource is not part of the exact iOS app bundle set: $resource" >&2
    exit 1
  }
done

if [[ "$verify_only" == true ]]; then
  echo "iOS Compose resources verified in $app_bundle."
else
  echo "iOS Compose resources synchronized into $app_bundle."
fi
