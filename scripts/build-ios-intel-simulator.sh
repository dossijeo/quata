#!/usr/bin/env bash
# Builds the Swift host against the x86_64 simulator slice on an Intel Mac.
# It does not boot a simulator or run XCTest/UI tests.
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" || "$(uname -m)" != "x86_64" ]]; then
  echo "Intel simulator build requires macOS on x86_64." >&2
  exit 2
fi

env_file="${QUATA_IOS_ENV_FILE:-$HOME/.config/quata/ios-intel.env}"
if [[ -f "$env_file" ]]; then
  # shellcheck source=/dev/null
  source "$env_file"
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JAVA_HOME must point to the JDK installed by scripts/bootstrap-ios-intel-mac.sh." >&2
  exit 2
fi
if [[ -z "${JBR_HOME:-}" || ! -x "$JBR_HOME/bin/java" ]]; then
  echo "JBR_HOME must point to the JBR 21 installed by scripts/bootstrap-ios-intel-mac.sh." >&2
  exit 2
fi
for command in java xcodebuild xcodegen; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Required command is unavailable: $command. Run scripts/bootstrap-ios-intel-mac.sh first." >&2
    exit 2
  }
done

# This VM reports its CPU marketing string through the kernel architecture
# query. Register the verified local JDK explicitly so Gradle never reaches
# its auto-provisioning path (which asks that malformed query for an arch).
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.java.installations.paths=$JAVA_HOME,$JBR_HOME -Dorg.gradle.java.installations.auto-download=false"

derived_data_path="${QUATA_IOS_DERIVED_DATA_PATH:-build/ios-intel-simulator-derived-data}"
result_bundle_path="${QUATA_IOS_RESULT_BUNDLE_PATH:-build/reports/ios/QuataIos-intel-build.xcresult}"
rm -rf "$derived_data_path" "$result_bundle_path"

bash ./gradlew --version

# Build only the Intel simulator framework in this lane. The generic-device
# archive script separately runs the canonical XCFramework assembly task and
# therefore continues to produce/validate its iosArm64 slice.
bash ./gradlew \
  :ios-shared:compileKotlinIosX64 \
  :ios-shared:linkDebugFrameworkIosX64 \
  --stacktrace \
  --warning-mode all \
  --console=plain

intel_framework="ios-shared/build/bin/iosX64/debugFramework/QuataShared.framework"
xcframework_path="ios-shared/build/XCFrameworks/debug/QuataShared.xcframework"
if [[ ! -d "$intel_framework" ]]; then
  echo "Expected Intel framework was not produced: $intel_framework" >&2
  exit 1
fi
rm -rf "$xcframework_path"
mkdir -p "$(dirname "$xcframework_path")"
xcodebuild -create-xcframework \
  -framework "$intel_framework" \
  -output "$xcframework_path"

(
  cd iosApp
  xcodegen generate
)

xcodebuild \
  -project iosApp/QuataIos.xcodeproj \
  -scheme QuataIos \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  -derivedDataPath "$derived_data_path" \
  -resultBundlePath "$result_bundle_path" \
  ARCHS=x86_64 \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  build

app="$derived_data_path/Build/Products/Debug-iphonesimulator/QuataIos.app"
bash scripts/sync-ios-compose-resources.sh --verify "$app"

echo "Intel x86_64 simulator host build validated."
