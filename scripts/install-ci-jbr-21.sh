#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUNNER_OS:-}" != "Linux" || "${RUNNER_ARCH:-}" != "X64" ]]; then
  echo "install-ci-jbr-21.sh currently supports GitHub-hosted Linux X64 runners only." >&2
  exit 2
fi

jbr_home="${RUNNER_TOOL_CACHE:-$HOME/.cache}/quata-jbr-21-linux-x64"
jbr_java="$jbr_home/bin/java"

if [[ ! -x "$jbr_java" ]]; then
  rm -rf "$jbr_home"
  mkdir -p "$jbr_home"
  archive="$RUNNER_TEMP/quata-jbr-21-linux-x64.tar.gz"
  curl -fsSL --retry 5 --retry-all-errors --connect-timeout 20 \
    "https://api.foojay.io/disco/v3.0/ids/398ffe3949748bfb1d5636f023d228fd/redirect" \
    -o "$archive"
  tar -xzf "$archive" --strip-components=1 -C "$jbr_home"
fi

if [[ ! -x "$jbr_java" ]]; then
  echo "Pinned JBR 21 installation did not produce an executable java." >&2
  exit 3
fi

if [[ -z "${JAVA_HOME_17_X64:-}" || ! -x "$JAVA_HOME_17_X64/bin/java" ]]; then
  echo "JAVA_HOME_17_X64 must be available after the Temurin 17 setup step." >&2
  exit 4
fi

{
  printf 'JBR_HOME=%s\n' "$jbr_home"
  printf 'GRADLE_OPTS=%s -Dorg.gradle.java.installations.paths=%s,%s -Dorg.gradle.java.installations.auto-download=false\n' "${GRADLE_OPTS:-}" "$JAVA_HOME_17_X64" "$jbr_home"
} >> "$GITHUB_ENV"

"$jbr_java" -version
