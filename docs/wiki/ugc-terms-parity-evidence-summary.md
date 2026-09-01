# UGC Terms Parity Evidence Summary

This file is durable attestation metadata for `docs/candidate-attestations/ugc-terms-parity.json`.
The raw `build-reports/*` artifacts remain local evidence outputs and are not committed to Git.

## Candidate

- Unit: `OVR-UGC-TERMS`, `FLOW-LEGAL-DOCUMENTS`
- Product/Evidence SHA: `f64d0c9f8a58e14e2eacd63b66eb6d4004029c1e`
- Attestation HEAD first linked from: `e0a59061f2472429742554b603cd0af1e73b4f98`
- Exact working tree for product evidence: clean product checkout at `f64d0c9f8a58e14e2eacd63b66eb6d4004029c1e`

## Evidence Outputs

| Platform | Local report | SHA-256 | Status |
| --- | --- | --- | --- |
| Web/Wasm | `build-reports/web/ugc-terms-evidence.json` | `2db6e39c463c6c36b956e44d600ad6f00809c4839a0f104931c79a2586e08166` | `passed` |
| Android | `build-reports/android/ugc-terms-evidence.json` | `9e1643e9d42142b6cd18a32cd0c808b1f572768f2d74f9aed1c661b0236a56db` | `passed` |
| iOS | `build-reports/ios/ugc-terms-evidence.json` | `8d29f91f6ff302aaf93e7b0049d949475fa81134b89e7fc27497a76c3838c6af` | `passed_with_post_xcodebuild_interrupt` |
| iOS XCTest log | `build-reports/ios/UGC-TERMS-ui/ui.log` | `369a18ae2f5ca40e32f8d977c8da5a151fdd6fb3145c4e13958671f2168b72cb` | contains PASS markers |

## iOS Log Markers

The iOS XCTest evidence log contained these required markers:

- `Test Case '-[QuataIosUITests.QuataIosHostUITests testUgcTermsFixtureRendersCommonGateLegalLinksAndAccepts]' passed`
- `Executed 1 test, with 0 failures`
- `PASS_EXECUTED:testUgcTermsFixtureRendersCommonGateLegalLinksAndAccepts`

The `xcodebuild` process was interrupted only after the XCTest PASS markers and `.xcresult` presence were observed, because the command remained in post-test processing. This is recorded in the iOS report as `passed_with_post_xcodebuild_interrupt`; it is not counted as a product failure.

## Covered Product Behaviors

- Common UGC terms gate blocks authenticated shell until acceptance.
- Child-safety and privacy legal links open from the common gate on Web/Wasm, Android and iOS.
- Acceptance is recorded locally before remote sync, avoiding reprompt when the network/logout path is unavailable.
- Web/Wasm validates a real Supabase-backed acceptance row and fresh-context no-reprompt behavior, then restores the original acceptance state.
- Android exercises the common Compose gate on a real emulator with screenshots and semantic anchors.
- iOS exercises the common gate on the Mac Hyper-V simulator with XCTest/accessibility anchors.

## Cleanup

- Web/Wasm restored the original Supabase acceptance state and verified zero physical residue for the temporary acceptance mutation.
- Android and iOS used local focused fixtures with no persistent remote test data.

## Reuse Rule

This evidence remains valid for later attestation/documentation commits only while `scripts/validate-candidate-attestation.mjs --manifest docs/candidate-attestations/ugc-terms-parity.json` classifies the real diff from `productSha` to `HEAD` as attestation-only. Any source, workflow, runner, resource, test, configuration or dependency change after the Product/Evidence SHA invalidates this evidence and requires the affected lanes to be repeated.
