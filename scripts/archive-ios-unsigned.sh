#!/usr/bin/env bash
# Builds a generic iOS device archive for structural release validation only.
# It deliberately never exports, signs or distributes an IPA.
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Unsigned iOS archive validation requires macOS with Xcode." >&2
  exit 2
fi

for command in xcodebuild xcodegen; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Required command is unavailable: $command" >&2
    exit 2
  fi
done

archive_path="${QUATA_IOS_ARCHIVE_PATH:-build/reports/ios/QuataIos-unsigned.xcarchive}"
derived_data_path="${QUATA_IOS_DERIVED_DATA_PATH:-build/ios-archive-derived-data}"

rm -rf "$archive_path" "$derived_data_path"

# The XCFramework contains the iosArm64 slice used by the generic device
# archive and the simulator slice used by XCTest in the separate lane.
# Invoke the wrapper through bash so a fresh checkout stays clean: macOS users
# do not need to chmod the tracked wrapper before structural validation.
bash ./gradlew :ios-shared:assembleQuataSharedDebugXCFramework --stacktrace --warning-mode all --console=plain
bash scripts/validate-ios-xcframework-privacy-artifacts.sh \
  ios-shared/build/XCFrameworks/debug/QuataShared.xcframework

(
  cd iosApp
  xcodegen generate
)

xcodebuild \
  -project iosApp/QuataIos.xcodeproj \
  -scheme QuataIos \
  -configuration Debug \
  -sdk iphoneos \
  -destination "generic/platform=iOS" \
  -derivedDataPath "$derived_data_path" \
  -archivePath "$archive_path" \
  ARCHS=arm64 \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  archive

app_path="$archive_path/Products/Applications/QuataIos.app"
if [[ ! -d "$app_path" ]]; then
  echo "Archive completed without the expected host app: $app_path" >&2
  exit 1
fi

if [[ ! -d "$app_path/Frameworks/QuataShared.framework" ]]; then
  echo "Archive is missing the embedded QuataShared.framework." >&2
  exit 1
fi

bash scripts/validate-ios-privacy-artifacts.sh "$app_path"

echo "Unsigned generic-device archive validated: $archive_path"
