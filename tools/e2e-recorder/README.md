# Quata E2E Macro Recorder

This is a small experiment for building E2E routes from one visual pass instead of guessing selectors or coordinates through CI.

Pipeline:

1. Record a real interaction and save `quata-e2e-macro` JSON.
2. Normalize every action to the strongest available anchor.
3. Stop with `missing_stable_anchor` when a tap/click/fill/assertion only has geometry.
4. Add a product anchor (`testTag`, `accessibilityIdentifier`, `resource-id`, accessibility label or visible text) when that is architecturally clean.
5. Compile/replay locally.
6. Use CI only as final certification.

Anchor order:

1. Explicit stable IDs: `testTag`, `accessibilityIdentifier`, `resource-id`, DOM `id`.
2. Accessibility/semantic data: `aria-label`, `contentDescription`, visible text, role/class.
3. Context: current screen, nearby text and bounds.
4. Relative or absolute geometry as diagnostic fallback only.

Commands:

```powershell
node tools/e2e-recorder/web-recorder.mjs --flow legal-web --url http://127.0.0.1:4174/ --out build-reports/e2e-recorder/legal-web.macro.json --demo legal
node tools/e2e-recorder/compile.mjs build-reports/e2e-recorder/legal-web.macro.json
node tools/e2e-recorder/compile.mjs build-reports/e2e-recorder/legal-web.macro.json --emit build-reports/e2e-recorder/legal-web.spec.generated.mjs
node tools/e2e-recorder/web-replay.mjs --macro build-reports/e2e-recorder/legal-web.macro.json
```

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
node tools/e2e-recorder/android-compose-semantics.mjs --out build-reports/e2e-recorder/android-compose-semantics.json --adb $adb
node tools/e2e-recorder/probe-target.mjs --platform android --input build-reports/e2e-recorder/android-compose-semantics.json --point 120,760
node tools/e2e-recorder/append-step.mjs --macro build-reports/e2e-recorder/android-whats-new.macro.json --flow android-whats-new --platform android --action tap --probe build-reports/e2e-recorder/android-compose-semantics.json --point 120,760
node tools/e2e-recorder/compile.mjs build-reports/e2e-recorder/android-whats-new.macro.json --emit build-reports/e2e-recorder/android-whats-new.generated.kt
node tools/e2e-recorder/android-dump-tree.mjs --out build-reports/e2e-recorder/android-ui.json --adb $adb
node tools/e2e-recorder/android-recorder.mjs --flow legal-android --out build-reports/e2e-recorder/legal-android.macro.json --tap 120,760 --adb $adb
node tools/e2e-recorder/android-replay.mjs --macro build-reports/e2e-recorder/legal-android.macro.json --adb $adb
```

```powershell
ssh quata-mac 'cd ~/quata && swift tools/e2e-recorder/ios-ax-probe.swift --point 120,760' > build-reports/e2e-recorder/ios-ax.json
node tools/e2e-recorder/ios-compile.mjs build-reports/e2e-recorder/legal-ios.macro.json
```

Probe a captured platform tree at a visual point:

```powershell
node tools/e2e-recorder/probe-target.mjs --platform android --input build-reports/e2e-recorder/android-ui.json --point 120,760
node tools/e2e-recorder/probe-target.mjs --platform ios --input build-reports/e2e-recorder/ios-ax.json --point 120,760
node tools/e2e-recorder/append-step.mjs --macro build-reports/e2e-recorder/legal-android.macro.json --flow legal-android --platform android --action tap --probe build-reports/e2e-recorder/android-ui.json --point 120,760
```

iOS currently compiles macro files into XCUI snippets using `accessibilityIdentifier`/label anchors. The first MVP intentionally does not add a large remote AX recorder until the SSH/macOS helper exposes a reliable AX element-under-point API. If a recorded iOS step cannot resolve to an identifier or label, the compile step fails instead of producing blind coordinates.

Artifact rules:

- Raw macro sessions and screenshots belong under `build-reports/e2e-recorder/` unless a deterministic sample is useful.
- Generated XCTest/Playwright code is reviewed before promotion to CI.
- `compile.mjs --emit` writes a reviewable runner snippet only when every actionable step has a stable product anchor.
- Android prefers `android-compose-semantics.mjs` for Compose screens: it builds the debug AndroidTest APK, mounts a focused shared Compose surface, exports `testTag`, content description, text, role and bounds, and feeds the same `probe-target.mjs` JSON shape. `android-dump-tree.mjs` remains a UIAutomator fallback for native/system surfaces; it deletes stale dumps before capture, fails closed on UIAutomator errors, and stores the normalized tree outside source control.
- `ios-ax-probe.swift` queries the macOS Accessibility element under a point for the simulator/window session and prints the same tree shape.
- `probe-target.mjs` converts Android Compose/UIAutomator or iOS AX trees into the same macro target shape and exits non-zero when the point cannot be resolved to a stable product anchor.
- `append-step.mjs` appends a resolved probe point to the common macro file so Android/iOS captures can move directly into `compile.mjs`.
- Coordinates are allowed only as diagnostics or temporary discovery data.
- A replay failure must name the step and selector, and should include URL/screen and visible state when that platform surface exposes them.
