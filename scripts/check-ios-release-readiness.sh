#!/usr/bin/env bash
# Validates the source-controlled iOS signing contract. It never contacts Apple
# services, reads a keychain, signs code, or emits an archive.
set -euo pipefail

mode="static"
require_public_runtime="false"
while (( $# != 0 )); do
  case "$1" in
    --signed-release) mode="signed-release" ;;
    --require-public-runtime) require_public_runtime="true" ;;
    *)
      echo "Usage: bash scripts/check-ios-release-readiness.sh [--signed-release] [--require-public-runtime]" >&2
      exit 64
      ;;
  esac
  shift
done

python3 - "$mode" "$require_public_runtime" <<'PY'
from __future__ import annotations

import os
import plistlib
import re
import sys
from pathlib import Path

mode = sys.argv[1]
require_public_runtime = sys.argv[2] == "true"
root = Path.cwd()
errors: list[str] = []

app_id = "com.quata.ios"
extension_id = "com.quata.ios.shareextension"
app_group = "group.com.quata.ios.share"
project = (root / "iosApp/project.yml").read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)

def load_plist(relative_path: str) -> dict:
    with (root / relative_path).open("rb") as source:
        return plistlib.load(source)

require(re.search(r"PRODUCT_BUNDLE_IDENTIFIER:\s*com\.quata\.ios(?:\s|$)", project) is not None,
        f"QuataIos must use {app_id}")
require(re.search(r"PRODUCT_BUNDLE_IDENTIFIER:\s*com\.quata\.ios\.shareextension(?:\s|$)", project) is not None,
        f"QuataShareExtension must use {extension_id}")
require(project.count("DEVELOPMENT_TEAM: $(QUATA_DEVELOPMENT_TEAM)") == 2,
        "both Release targets must require QUATA_DEVELOPMENT_TEAM")
require("PROVISIONING_PROFILE_SPECIFIER: $(QUATA_IOS_APP_PROVISIONING_PROFILE)" in project,
        "QuataIos Release must require QUATA_IOS_APP_PROVISIONING_PROFILE")
require("PROVISIONING_PROFILE_SPECIFIER: $(QUATA_IOS_SHARE_EXTENSION_PROVISIONING_PROFILE)" in project,
        "QuataShareExtension Release must require QUATA_IOS_SHARE_EXTENSION_PROVISIONING_PROFILE")
require(re.search(r"QuataShareQueue:\s+type:\s+library\.static", project) is not None and
        "PRODUCT_MODULE_NAME: QuataShareQueue" in project and 'DEFINES_MODULE: "YES"' in project,
        "Share queue production code and tests must link the same static Swift/C module")
require(project.count("CODE_SIGN_STYLE: Manual") == 2,
        "both Release targets must use manual signing")
require("QUATA_APNS_ENVIRONMENT: production" in project,
        "QuataIos Release must fix QUATA_APNS_ENVIRONMENT to production")

app_entitlements = load_plist("iosApp/iosApp/QuataIos.entitlements")
extension_entitlements = load_plist("iosApp/iosShareExtension/QuataShareExtension.entitlements")
require(app_entitlements.get("aps-environment") == "$(QUATA_APNS_ENVIRONMENT)",
        "app aps-environment must be injected through QUATA_APNS_ENVIRONMENT")
for name, entitlements in (("app", app_entitlements), ("share extension", extension_entitlements)):
    require(entitlements.get("com.apple.security.application-groups") == [app_group],
            f"{name} entitlements must contain only {app_group}")

for relative_path in ("iosApp/iosApp/Info.plist", "iosApp/iosShareExtension/Info.plist"):
    info = load_plist(relative_path)
    require(info.get("CFBundleIdentifier") == "$(PRODUCT_BUNDLE_IDENTIFIER)",
            f"{relative_path} must derive CFBundleIdentifier from PRODUCT_BUNDLE_IDENTIFIER")

share_source = (root / "iosApp/iosShareExtension/ShareViewController.swift").read_text(encoding="utf-8")
queue_source = (root / "iosApp/iosShareQueue/ShareQueue.swift").read_text(encoding="utf-8")
require(f'static let appGroup = "{app_group}"' in share_source,
        "share extension runtime must use the declared App Group")
require("try ShareQueue.persist(" in share_source,
        "share extension runtime must use the isolated queue persistence boundary")
require("static let maximumFiles = 5" in queue_source and "static let maximumPendingShares = 10" in queue_source,
        "share queue must enforce the five-file and ten-pending limits")
require("options: .atomic" in queue_source and "moveItem(at: staging, to: destination)" in queue_source,
        "share queue must atomically write the manifest and publish with a same-volume rename")
require("removeItem(at: staging)" in queue_source,
        "share queue must clean staging after a failed publication")
require("isSafeID(payload.id)" in queue_source and "id.utf8.allSatisfy" in queue_source,
        "share queue must validate a strict ASCII ID before composing App Group paths")
lock_source = (root / "iosApp/iosShareQueue/ShareQueueLock.c").read_text(encoding="utf-8")
require("quata_flock" in queue_source and "flock(descriptor, operation)" in lock_source and
        "O_NOFOLLOW" in queue_source and "Darwin.close" in queue_source,
        "share queue must use a crash-safe Darwin flock descriptor, never stale-lock recovery")
inbox_source = (root / "feature/externalshare/src/iosMain/kotlin/com/quata/feature/externalshare/IosExternalShareInbox.kt").read_text(encoding="utf-8")
require("NSFileTypeRegular" in inbox_source and "NSFileTypeDirectory" in inbox_source and
        "manifest.json" in inbox_source and "URLByResolvingSymlinksInPath" in inbox_source,
        "iOS inbox must reject symlinked queue nodes/manifests and validate canonical App Group containment")
require("ensureVerifiedChildDirectory" in inbox_source and "createDirectoryAtPath(childPath, false" in inbox_source and
        "removeVerifiedQueueNode" in inbox_source,
        "iOS inbox must verify every App Group queue component before creation and purge invalid nodes")

runtime_defaults = root / "iosApp/Configuration/QuataPublicRuntime.xcconfig"
runtime_local = root / "iosApp/Configuration/QuataPublicRuntime.local.xcconfig"
runtime_example = root / "iosApp/Configuration/QuataPublicRuntime.local.xcconfig.example"
require(runtime_defaults.is_file(), "versioned public runtime defaults must exist")
require(runtime_example.is_file(), "public runtime local example must exist")
if runtime_defaults.is_file():
    defaults = runtime_defaults.read_text(encoding="utf-8")
    require('#include? "QuataPublicRuntime.local.xcconfig"' in defaults,
            "public runtime defaults must optionally include the local override")
    for setting in ("QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY"):
        require(re.search(rf"^{setting}\s*=\s*$", defaults, re.MULTILINE) is not None,
                f"{setting} must default to an empty value")

def load_xcconfig(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("//") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values

def expand_xcconfig_values(values: dict[str, str]) -> dict[str, str]:
    variable = re.compile(r"\$\(([^)]+)\)")

    def expand(value: str, resolving: set[str]) -> str:
        def replacement(match: re.Match[str]) -> str:
            name = match.group(1)
            if name in resolving:
                return match.group(0)
            return expand(values.get(name, match.group(0)), resolving | {name})
        return variable.sub(replacement, value)

    return {key: expand(value, {key}) for key, value in values.items()}

if require_public_runtime:
    require(runtime_local.is_file(), "public runtime fixture/local override must exist before building")
    if runtime_local.is_file():
        runtime = expand_xcconfig_values({
            **load_xcconfig(runtime_defaults),
            **load_xcconfig(runtime_local),
        })
        url = runtime.get("QUATA_SUPABASE_URL", "")
        key = runtime.get("QUATA_SUPABASE_PUBLISHABLE_KEY", "")
        require(bool(re.fullmatch(r"https://[A-Za-z0-9.-]+(?:/[^\s]*)?", url)),
                "public runtime URL must be a non-empty HTTPS URL")
        require(re.search(r"^\s*QUATA_SUPABASE_URL\s*=\s*https://", runtime_local.read_text(encoding="utf-8"), re.MULTILINE) is None,
                "public runtime URL must compose // through QUATA_XCCONFIG_SLASH for Xcode")
        require(bool(key) and "$(" not in key and not re.search(r"[\r\n]", key),
                "public runtime publishable key must be non-empty and single-line")
        local_source = runtime_local.read_text(encoding="utf-8").lower()
        require("service_role" not in local_source and "jwt" not in local_source,
                "public runtime override must not contain service-role or JWT material")

if mode == "signed-release":
    required = (
        "QUATA_DEVELOPMENT_TEAM",
        "QUATA_IOS_APP_PROVISIONING_PROFILE",
        "QUATA_IOS_SHARE_EXTENSION_PROVISIONING_PROFILE",
        "QUATA_SUPABASE_URL",
        "QUATA_SUPABASE_PUBLISHABLE_KEY",
    )
    for key in required:
        value = os.environ.get(key, "")
        require(bool(value.strip()) and "$" not in value and "\n" not in value and "\r" not in value,
                f"{key} must be supplied as a non-empty external build setting")
    require(os.environ.get("QUATA_APNS_ENVIRONMENT") == "production",
            "QUATA_APNS_ENVIRONMENT must be production for a signed Release build")

if errors:
    print("iOS release readiness check failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    sys.exit(1)

print(f"iOS release readiness check passed ({mode}).")
PY
