# UGC Terms Parity Evidence Summary

This file is durable attestation metadata for `docs/candidate-attestations/ugc-terms-parity.json`.
The raw `build-reports/*` artifacts remain local evidence outputs and are not committed to Git.

## Candidate

- Unit: `OVR-UGC-TERMS`, `FLOW-LEGAL-DOCUMENTS`
- Product/Evidence SHA: `760b8e4c4e46c6f6ca300bc0ed15846353472495`
- Attestation HEAD first linked from: `e0a59061f2472429742554b603cd0af1e73b4f98`; reattested after the Web authenticated browser CI gate fix at `760b8e4c4e46c6f6ca300bc0ed15846353472495`
- Exact working tree for product evidence: clean product checkout at `760b8e4c4e46c6f6ca300bc0ed15846353472495`

## Evidence Outputs

| Platform | Local report | SHA-256 | Status |
| --- | --- | --- | --- |
| Web/Wasm | `build-reports/web/ugc-terms-evidence.json` | `0b82bbca7faa9d2b14413846779bba56d9f552c62d48e443370bcc05c28c07cc` | `passed` |
| Android | `build-reports/android/ugc-terms-evidence.json` | `58ae0f55ada80a9ea20494f9cf7cff54c5dae3d5ed55f4dfce9fa01f51e04e90` | `passed` |
| iOS | `build-reports/ios/ugc-terms-evidence.json` | `5e2ea75df6d11338885aa17c49649df4550087900686e3b725eb879645637645` | `passed` |
| iOS XCTest log | `build-reports/ios/UGC-TERMS-ui/ui.log` | `20ac776692ffa4a88066bf212e3868d5856dddfafecf226b90892b8b35127f32` | contains PASS markers |

## iOS Log Markers

The iOS XCTest evidence log contained these required markers:

- `Test Case '-[QuataIosUITests.QuataIosHostUITests testUgcTermsFixtureRendersCommonGateLegalLinksAndAccepts]' passed`
- `Executed 1 test, with 0 failures`
- `ios-ugc-terms-required`
- `ios-ugc-terms-child-safety`
- `ios-ugc-terms-privacy`
- `ios-ugc-terms-accepted`
- `TEST EXECUTE SUCCEEDED`

The iOS XCTest command reached `TEST EXECUTE SUCCEEDED`; the local log is copied and audited by the candidate manifest markers.

## Covered Product Behaviors

- Common UGC terms gate blocks authenticated shell until acceptance.
- Child-safety and privacy legal links open from the common gate on Web/Wasm, Android and iOS.
- Acceptance is recorded locally before remote sync, avoiding reprompt when the network/logout path is unavailable.
- Web logout preserves server/browser/local ordering and bounds browser push unsubscribe through a cancellable service-worker bridge before clearing local state.
- Web/Wasm validates a real Supabase-backed acceptance row and fresh-context no-reprompt behavior, then restores the original acceptance state.
- Android exercises the common Compose gate on a real emulator with screenshots and semantic anchors.
- iOS exercises the common gate on the Mac Hyper-V simulator with XCTest/accessibility anchors.

## Cleanup

- Web/Wasm restored the original Supabase acceptance state and verified zero physical residue for the temporary acceptance mutation.
- Android and iOS used local focused fixtures with no persistent remote test data.

## Reuse Rule

This evidence remains valid for later attestation/documentation commits only while `scripts/validate-candidate-attestation.mjs --manifest docs/candidate-attestations/ugc-terms-parity.json` classifies the real diff from `productSha` to `HEAD` as attestation-only. Any source, workflow, runner, resource, test, configuration or dependency change after the Product/Evidence SHA invalidates this evidence and requires the affected lanes to be repeated.
