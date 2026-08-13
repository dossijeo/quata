# E2E Recorder Platform Probes Plan

Date: 2026-08-13
Base SHA: `02d64ddfe5277e3150acacecd448aa335823c37a`
Branch: `codex/e2e-recorder-platform-probes`

## Scope

Close the two recorder gaps identified by PR #243:

1. iOS/macOS: capture the Accessibility element under a visual tap/click and convert it into an `accessibilityIdentifier`, label, role/type and frame.
2. Android: capture the current UIAutomator hierarchy fail-closed and normalize either
   UIAutomator nodes or a separately-produced Compose semantics JSON tree. This MVP does not yet
   inject a Compose semantics exporter into the product.

## Dependency

This unit builds on the MVP in PR #243. It must not be promoted before #243 is integrated, or it must explicitly rebase onto the integrated recorder commit.

## GO Criteria

- iOS probe can take a point in simulator/window coordinates and return an AX element payload with identifier/label/frame when available.
- Android probe can return Quata-owned UIAutomator/semantics anchors or clearly report
  `missing_stable_anchor` without promoting launcher/system nodes.
- Both probes feed the existing `quata-e2e-macro` format.
- Contract tests cover fail-closed behavior and at least one stable-anchor success path.
- Local evidence includes one real probe run per implemented platform.

## MVP Added In This Unit

- `tools/e2e-recorder/lib/platform-probes.mjs` normalizes Android UIAutomator/semantics JSON and iOS AX JSON into macro targets.
- `tools/e2e-recorder/probe-target.mjs` probes a point and exits with `missing_stable_anchor` behavior when the target is not stable.
- Contract tests cover Android stable semantics, Android external app rejection and iOS AX identifier resolution.
- `tools/e2e-recorder/android-dump-tree.mjs` captures a real UIAutomator tree through ADB so a visual tap can be resolved after the fact; when UIAutomator reports failure, it removes stale dumps and fails closed instead of reusing old XML.
- `tools/e2e-recorder/ios-ax-probe.swift` is the minimal macOS-side AX element-under-point probe to validate whether Compose/iOS exposes stable identifiers before writing XCTest selectors.
- `tools/e2e-recorder/append-step.mjs` turns a probe point into a macro step, completing the minimal capture -> normalize -> compile path for non-Web platforms.
