#!/usr/bin/env bash
# Opt-in locally signed Intel simulator lane for Keychain-backed visual gates.
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" || "$(uname -m)" != "x86_64" ]]; then
  echo "Intel simulator build requires macOS on x86_64." >&2
  exit 2
fi

env_file="${QUATA_IOS_ENV_FILE:-$HOME/.config/quata/ios-intel.env}"
if [[ -f "$env_file" ]]; then source "$env_file"; fi
for command in java xcodebuild xcodegen codesign; do
  command -v "$command" >/dev/null 2>&1 || { echo "Required command is unavailable: $command." >&2; exit 2; }
done
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" || -z "${JBR_HOME:-}" || ! -x "$JBR_HOME/bin/java" ]]; then
  echo "Run scripts/bootstrap-ios-intel-mac.sh first." >&2
  exit 2
fi

export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.java.installations.paths=$JAVA_HOME,$JBR_HOME -Dorg.gradle.java.installations.auto-download=false"
derived_data_path="${QUATA_IOS_SIGNED_DERIVED_DATA_PATH:-build/ios-intel-simulator-signed-derived-data}"
result_bundle_path="${QUATA_IOS_SIGNED_RESULT_BUNDLE_PATH:-build/reports/ios/QuataIos-intel-signed-build.xcresult}"
rm -rf "$derived_data_path" "$result_bundle_path"

bash ./gradlew :ios-shared:compileKotlinIosX64 :ios-shared:linkDebugFrameworkIosX64 --stacktrace --warning-mode all --console=plain
intel_framework="ios-shared/build/bin/iosX64/debugFramework/QuataShared.framework"
xcframework_path="ios-shared/build/XCFrameworks/debug/QuataShared.xcframework"
[[ -d "$intel_framework" ]] || { echo "Expected Intel framework was not produced." >&2; exit 1; }
rm -rf "$xcframework_path"
mkdir -p "$(dirname "$xcframework_path")"
xcodebuild -create-xcframework -framework "$intel_framework" -output "$xcframework_path"
( cd iosApp && xcodegen generate )

xcodebuild \
  -project iosApp/QuataIos.xcodeproj \
  -scheme QuataIos \
  -configuration SimulatorSigned \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  -derivedDataPath "$derived_data_path" \
  -resultBundlePath "$result_bundle_path" \
  ARCHS=x86_64 \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=YES \
  CODE_SIGNING_REQUIRED=YES \
  CODE_SIGN_STYLE=Automatic \
  CODE_SIGN_IDENTITY=- \
  AD_HOC_CODE_SIGNING_ALLOWED=YES \
  build-for-testing

products="$derived_data_path/Build/Products/SimulatorSigned-iphonesimulator"
app="$products/QuataIos.app"
[[ -d "$app" ]] || { echo "Expected signed app was not produced." >&2; exit 1; }
bash scripts/sync-ios-compose-resources.sh --verify "$app"
# Keep the final local signature entitlement-free. The simulator rejects ad-hoc
# signatures that claim restricted Keychain access groups.
codesign --force --sign - "$app"
while IFS= read -r -d '' bundle; do
  codesign --verify --deep --strict "$bundle"
done < <(find "$products" -type d \( -name '*.app' -o -name '*.xctest' -o -name '*.appex' \) -print0)
entitlements="$(codesign -d --entitlements :- "$app" 2>&1)"
! grep -q '<key>keychain-access-groups</key>' <<<"$entitlements"
! grep -q '<key>aps-environment</key>' <<<"$entitlements"
! grep -q '<key>com.apple.security.application-groups</key>' <<<"$entitlements"
! grep -q '<key>application-identifier</key>' <<<"$entitlements"
echo "Intel SimulatorSigned Keychain lane validated."
