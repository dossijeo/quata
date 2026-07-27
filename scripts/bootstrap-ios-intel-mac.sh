#!/usr/bin/env bash
# Installs the two pinned JVMs and XcodeGen required by the Intel iOS lane.
# Everything is user-local; no sudo or shell-profile mutation is performed.
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" || "$(uname -m)" != "x86_64" ]]; then
  echo "This bootstrap is only for an Intel (x86_64) Mac." >&2
  exit 2
fi

for command in curl openssl tar git swift install mktemp; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Required command is unavailable: $command" >&2
    exit 2
  }
done

tool_root="${QUATA_IOS_TOOL_ROOT:-$HOME/.local/share/quata-ios}"
bin_dir="${QUATA_IOS_BIN_DIR:-$HOME/.local/bin}"
env_file="${QUATA_IOS_ENV_FILE:-$HOME/.config/quata/ios-intel.env}"
download_dir="$tool_root/downloads"
jdk_dir="$tool_root/jdks"
source_dir="$tool_root/src"
mkdir -p "$download_dir" "$jdk_dir" "$source_dir" "$bin_dir" "$(dirname "$env_file")"

temurin_name="OpenJDK17U-jdk_x64_mac_hotspot_17.0.20_8.tar.gz"
temurin_url="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20%2B8/$temurin_name"
temurin_sha256="3710c3131c5d7c090582b357f1310133a90bf701183d065223f1a0b90b9ed5ae"
temurin_install="$jdk_dir/temurin-17.0.20+8"

# This is the exact x64 artifact selected by gradle/gradle-daemon-jvm.properties
# for vendor JETBRAINS, version 21.
jbr_name="jbrsdk_jcef-21.0.10-osx-x64-b1163.110.tar.gz"
jbr_url="https://d2xrhe97vsfxuc.cloudfront.net/$jbr_name"
jbr_sha256="405772ad98423443fbec5a2b8039723fea679a84c3528eab4b20c3f0decf05b5"
jbr_install="$jdk_dir/jbr-21.0.10-b1163.110"

verify_sha256() {
  local archive="$1"
  local expected="$2"
  local actual
  actual="$(openssl dgst -sha256 -r "$archive" | awk '{print $1}')"
  [[ "$actual" == "$expected" ]]
}

download_verified() {
  local name="$1"
  local url="$2"
  local expected="$3"
  local archive="$download_dir/$name"
  local partial

  if [[ -f "$archive" ]] && verify_sha256 "$archive" "$expected"; then
    printf '%s\n' "$archive"
    return
  fi

  partial="$(mktemp "$download_dir/.${name}.partial.XXXXXX")"
  if ! curl --fail --location --proto '=https' --tlsv1.2 --retry 3 "$url" -o "$partial"; then
    rm -f "$partial"
    return 1
  fi
  if ! verify_sha256 "$partial" "$expected"; then
    rm -f "$partial"
    echo "SHA-256 verification failed for $name." >&2
    return 1
  fi
  mv "$partial" "$archive"
  printf '%s\n' "$archive"
}

install_jdk_bundle() {
  local archive="$1"
  local destination="$2"
  local expected_major="$3"
  local staging
  local extracted_home
  local extracted_bundle
  local version_line

  if [[ ! -x "$destination/Contents/Home/bin/java" ]]; then
    if [[ -e "$destination" ]]; then
      echo "Refusing to replace an incomplete JDK installation: $destination" >&2
      return 1
    fi
    staging="$(mktemp -d "$jdk_dir/.jdk-staging.XXXXXX")"
    tar -xzf "$archive" -C "$staging"
    extracted_home="$(find "$staging" -type d -path '*/Contents/Home' -print -quit)"
    if [[ -z "$extracted_home" || ! -x "$extracted_home/bin/java" ]]; then
      echo "Verified archive did not contain a usable macOS JDK bundle." >&2
      return 1
    fi
    extracted_bundle="$(dirname "$(dirname "$extracted_home")")"
    mv "$extracted_bundle" "$destination"
    rmdir "$staging"
  fi
  version_line="$("$destination/Contents/Home/bin/java" -version 2>&1 | head -n 1)"
  [[ "$version_line" == *"\"$expected_major."* || "$version_line" == *"\"$expected_major\""* ]]
}

temurin_archive="$(download_verified "$temurin_name" "$temurin_url" "$temurin_sha256")"
jbr_archive="$(download_verified "$jbr_name" "$jbr_url" "$jbr_sha256")"
install_jdk_bundle "$temurin_archive" "$temurin_install" 17
install_jdk_bundle "$jbr_archive" "$jbr_install" 21
temurin_home="$temurin_install/Contents/Home"
jbr_home="$jbr_install/Contents/Home"

xcodegen_version="2.44.1"
xcodegen_commit="21ac9944b0ab546a07422dbed86f33dd2ebd76f8"
xcodegen_source="$source_dir/XcodeGen-$xcodegen_version"
if [[ ! -d "$xcodegen_source/.git" ]]; then
  xcodegen_staging="$(mktemp -d "$source_dir/.XcodeGen-$xcodegen_version.XXXXXX")"
  git clone --depth 1 --branch "$xcodegen_version" \
    https://github.com/yonaskolb/XcodeGen.git "$xcodegen_staging"
  [[ "$(git -C "$xcodegen_staging" rev-parse HEAD)" == "$xcodegen_commit" ]]
  git -C "$xcodegen_staging" describe --exact-match --tags HEAD | grep -Fx "$xcodegen_version"
  mv "$xcodegen_staging" "$xcodegen_source"
fi
git -C "$xcodegen_source" diff --exit-code
[[ "$(git -C "$xcodegen_source" rev-parse HEAD)" == "$xcodegen_commit" ]]
git -C "$xcodegen_source" describe --exact-match --tags HEAD | grep -Fx "$xcodegen_version"
if [[ ! -x "$bin_dir/xcodegen" ]] ||
  [[ "$("$bin_dir/xcodegen" --version)" != "Version: $xcodegen_version" ]]; then
  (
    cd "$xcodegen_source"
    swift build --configuration release
  )
  xcodegen_binary_staging="$(mktemp "$bin_dir/.xcodegen.XXXXXX")"
  install -m 0755 "$xcodegen_source/.build/release/xcodegen" "$xcodegen_binary_staging"
  mv "$xcodegen_binary_staging" "$bin_dir/xcodegen"
fi

env_staging="$(mktemp "$(dirname "$env_file")/.ios-intel.env.XXXXXX")"
{
  echo "# Generated by scripts/bootstrap-ios-intel-mac.sh. Source for iOS work."
  printf "export JAVA_HOME='%s'\n" "$temurin_home"
  printf "export JBR_HOME='%s'\n" "$jbr_home"
  printf "export PATH='%s':\"\$JAVA_HOME/bin\":\"\$PATH\"\n" "$bin_dir"
} > "$env_staging"
chmod 0600 "$env_staging"
mv "$env_staging" "$env_file"

"$temurin_home/bin/java" -version
"$jbr_home/bin/java" -version
"$bin_dir/xcodegen" --version
echo "Pinned Intel iOS toolchain installed. Run: source '$env_file'"
