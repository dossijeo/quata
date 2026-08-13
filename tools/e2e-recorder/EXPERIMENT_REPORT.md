# E2E Macro Recorder Experiment

Date: 2026-08-13
Base SHA: `02d64ddfe5277e3150acacecd448aa335823c37a`
Branch: `codex/e2e-macro-recorder`

## Diagnosis

CI is still valuable because it catches defects that local preflight can miss. The expensive part is building visual E2E routes by repeatedly guessing selectors, bounds and scroll behavior. The experiment validates a local-first macro step between visual exploration and CI: record once, resolve to stable anchors, replay locally, then promote only reviewed runners.

## Implemented MVP

- Common macro format: `quata-e2e-macro` v1.
- Compiler: `tools/e2e-recorder/compile.mjs`.
- Shared anchor ranking in `tools/e2e-recorder/lib/macro-core.mjs`.
- Web recorder/replay using Playwright, DOM hit testing, `data-testid`, DOM id, aria label, role, visible text, bounds and screenshots.
- Android recorder/replay using ADB + UIAutomator XML when accessibility exposes a hierarchy; it now fails closed if UIAutomator returns `null root node`.
- iOS compiler that turns macro steps with `accessibilityIdentifier` or label anchors into XCUI snippets.

## Real Flow Used

Legal document route:

1. Tap `legal-document-link-privacy`.
2. Assert `document-viewer-status-root`.

Those anchors already exist in the product legal-document work and match Android Compose test tags, Web DOM/test ids and iOS accessibility identifiers used by `QuataIosHostUITests`.

## Results

Web fixture replay:

- Recorded visually once with Playwright.
- Derived `testTag` anchors for both steps.
- Compiled with `fragileSteps = 0`.
- Replayed locally 3/3 times.
- Screenshots and observable state were saved under `build-reports/e2e-recorder/`.

iOS compile:

- Macro compiled to:
  - `app.descendants(matching: .any).matching(identifier: "legal-document-link-privacy").firstMatch.tap()`
  - `XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "document-viewer-status-root").firstMatch.waitForExistence(timeout: 30))`
- SSH Mac helper was reachable and the booted simulator was `48950F56-C309-4AA7-921F-D76C6042AC2C`.
- This proves the stable-anchor path, but the MVP does not yet capture AX element-under-point from macOS.

Android probe:

- ADB device `emulator-5554` was available and `com.quata` launched.
- The first UIAutomator probe returned `ERROR: null root node returned by UiTestAutomationBridge` while the app was starting.
- A later probe exposed only launcher nodes (`com.google.android.apps.nexuslauncher`) after focus returned there. The recorder now marks nodes outside `com.quata` as external and does not promote their `resource-id` as a stable product anchor.
- The recorder therefore cannot honestly derive a Quata Android selector from that screen in this environment. It fails closed instead of recording blind coordinates or external app IDs.

## Stable Anchors Derived

- Web: `legal-document-link-privacy`, `document-viewer-status-root`.
- iOS: same identifiers compile to XCUI selectors.
- Android: no real Quata selector derived from the live app because UIAutomator either had no root or exposed the launcher, not product nodes.

## Coordinate Use

Coordinates were captured as diagnostics in the Web macro, but replay used `data-testid`/id selectors. No successful replay depended on absolute coordinates.

## Limitations

- Web was validated on a deterministic fixture carrying the same legal anchors, not on a fresh Wasm distribution. A full app run should be the next iteration after the current active candidate lane is clear.
- iOS needs a small macOS-side AX probe to convert a visual click into an AX element-under-point automatically. The compiler side is useful now.
- Android Compose screens may require instrumentation-side semantics export, because UIAutomator can return no root for the current app state.

## Recommendation

The approach is promising for Web and iOS anchored flows and useful as a guardrail even where platform discovery is incomplete. It should reduce automation cost most when a human/agent can visually reach the route and the product has stable anchors. When it reports `missing_stable_anchor` or no AX hierarchy, the next action should be to add clean product anchors or a platform semantics exporter, not to iterate on coordinates.
