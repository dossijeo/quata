# E2E Recorder Platform Probes Plan

Date: 2026-08-13
Base SHA: `02d64ddfe5277e3150acacecd448aa335823c37a`
Branch: `codex/e2e-recorder-platform-probes`

## Scope

Close the two recorder gaps identified by PR #243:

1. iOS/macOS: capture the Accessibility element under a visual tap/click and convert it into an `accessibilityIdentifier`, label, role/type and frame.
2. Android: expose product semantics for Compose screens when UIAutomator returns no Quata hierarchy or only launcher/system nodes.

## Dependency

This unit builds on the MVP in PR #243. It must not be promoted before #243 is integrated, or it must explicitly rebase onto the integrated recorder commit.

## GO Criteria

- iOS probe can take a point in simulator/window coordinates and return an AX element payload with identifier/label/frame when available.
- Android probe can return Quata-owned semantics or clearly report `missing_stable_anchor` without promoting launcher/system nodes.
- Both probes feed the existing `quata-e2e-macro` format.
- Contract tests cover fail-closed behavior and at least one stable-anchor success path.
- Local evidence includes one real probe run per implemented platform.
