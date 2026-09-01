# UGC Terms Parity Evidence Summary

This file is durable attestation metadata for `docs/candidate-attestations/ugc-terms-parity.json`.
The raw `build-reports/*` artifacts remain local evidence outputs and are not committed to Git.

## Candidate

- Unit: `OVR-UGC-TERMS`, `FLOW-LEGAL-DOCUMENTS`
- Product/Evidence SHA: `101e652603f0616a330c9180cf6fd459a93f21c9`
- Attestation HEAD first linked from: `e0a59061f2472429742554b603cd0af1e73b4f98`
- Exact working tree for product evidence: clean product checkout at `101e652603f0616a330c9180cf6fd459a93f21c9`

## Evidence Outputs

| Platform | Local report | SHA-256 | Status |
| --- | --- | --- | --- |
| Web/Wasm | `build-reports/web/ugc-terms-evidence.json` | `e4dcc641a6e8a0be22ab304c4e652676cb291790323eaf89ade4af7a1c1ca7a6` | `passed` |
| Android | `build-reports/android/ugc-terms-evidence.json` | `161e2cfd3b62171689f5587b73cb16c14d142c0df7933600745195f68418bab6` | `passed` |
| iOS | `build-reports/ios/ugc-terms-evidence.json` | `18397c2b9fa48228f87bb45fbac35adc6a74bae307f89a9b11de1fc6918f8c61` | `passed` |
| iOS XCTest log | `build-reports/ios/UGC-TERMS-ui/ui.log` | `2303724265e0c60945eb61f81be5726c3d3926f6705663303f97f7159b8e891f` | contains PASS markers |

## iOS Log Markers

The iOS XCTest evidence log contained these required markers:

- `Test Case '-[QuataIosUITests.QuataIosHostUITests testUgcTermsFixtureRendersCommonGateLegalLinksAndAccepts]' passed`
- `Executed 1 test, with 0 failures`
- `PASS_EXECUTED:testUgcTermsFixtureRendersCommonGateLegalLinksAndAccepts`

The iOS XCTest command reached `TEST EXECUTE SUCCEEDED`; the local log is copied and audited by the candidate manifest markers.

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
