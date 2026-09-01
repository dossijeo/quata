# UGC Terms Parity Evidence Summary

This file is durable attestation metadata for `docs/candidate-attestations/ugc-terms-parity.json`.
The raw `build-reports/*` artifacts remain local evidence outputs and are not committed to Git.

## Candidate

- Unit: `OVR-UGC-TERMS`, `FLOW-LEGAL-DOCUMENTS`
- Product/Evidence SHA: `a4a341f9fdc7182c7ae15bad5918806afde8c833`
- Attestation HEAD first linked from: `e0a59061f2472429742554b603cd0af1e73b4f98`
- Exact working tree for product evidence: clean product checkout at `a4a341f9fdc7182c7ae15bad5918806afde8c833`

## Evidence Outputs

| Platform | Local report | SHA-256 | Status |
| --- | --- | --- | --- |
| Web/Wasm | `build-reports/web/ugc-terms-evidence.json` | `13468732cf2391592b29787716e73d193f30fbaf7b0cba500e03b63bca4b0e05` | `passed` |
| Android | `build-reports/android/ugc-terms-evidence.json` | `44b99631de9649de19af2184778410797a59c17ec6d38c88be010c15e432cae0` | `passed` |
| iOS | `build-reports/ios/ugc-terms-evidence.json` | `a77a87b5eafa49ac9ff2701cd0db848700934b80cb8dca3207fa984d6e974af2` | `passed` |
| iOS XCTest log | `build-reports/ios/UGC-TERMS-ui/ui.log` | `e00c873b4bdb3c536517b6fe59652c817ca82c63619771475fa25169448f7f16` | contains PASS markers |

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
- Web logout preserves server/browser/local ordering and bounds browser push unsubscribe through a cancellable service-worker bridge before clearing local state.
- Web/Wasm validates a real Supabase-backed acceptance row and fresh-context no-reprompt behavior, then restores the original acceptance state.
- Android exercises the common Compose gate on a real emulator with screenshots and semantic anchors.
- iOS exercises the common gate on the Mac Hyper-V simulator with XCTest/accessibility anchors.

## Cleanup

- Web/Wasm restored the original Supabase acceptance state and verified zero physical residue for the temporary acceptance mutation.
- Android and iOS used local focused fixtures with no persistent remote test data.

## Reuse Rule

This evidence remains valid for later attestation/documentation commits only while `scripts/validate-candidate-attestation.mjs --manifest docs/candidate-attestations/ugc-terms-parity.json` classifies the real diff from `productSha` to `HEAD` as attestation-only. Any source, workflow, runner, resource, test, configuration or dependency change after the Product/Evidence SHA invalidates this evidence and requires the affected lanes to be repeated.
