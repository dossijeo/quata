#!/usr/bin/env bash
# Validates the source-controlled iOS signing contract. It never contacts Apple
# services, reads a keychain, signs code, or emits an archive.
set -euo pipefail

mode="static"
if [[ "${1:-}" == "--signed-release" ]]; then
  mode="signed-release"
  shift
fi
if (( $# != 0 )); then
  echo "Usage: bash scripts/check-ios-release-readiness.sh [--signed-release]" >&2
  exit 64
fi

python3 - "$mode" <<'PY'
from __future__ import annotations

import os
import plistlib
import re
import sys
from pathlib import Path

mode = sys.argv[1]
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
