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
node tools/e2e-recorder/web-replay.mjs --macro build-reports/e2e-recorder/legal-web.macro.json
```

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
node tools/e2e-recorder/android-recorder.mjs --flow legal-android --out build-reports/e2e-recorder/legal-android.macro.json --tap 120,760 --adb $adb
node tools/e2e-recorder/android-replay.mjs --macro build-reports/e2e-recorder/legal-android.macro.json --adb $adb
```

```powershell
node tools/e2e-recorder/ios-compile.mjs build-reports/e2e-recorder/legal-ios.macro.json
```

iOS currently compiles macro files into XCUI snippets using `accessibilityIdentifier`/label anchors. The first MVP intentionally does not add a large remote AX recorder until the SSH/macOS helper exposes a reliable AX element-under-point API. If a recorded iOS step cannot resolve to an identifier or label, the compile step fails instead of producing blind coordinates.

Artifact rules:

- Raw macro sessions and screenshots belong under `build-reports/e2e-recorder/` unless a deterministic sample is useful.
- Generated XCTest/Playwright code is reviewed before promotion to CI.
- Coordinates are allowed only as diagnostics or temporary discovery data.
- A replay failure must name the step, selector, URL/screen and visible state where possible.
