# UGC terms native remote postflight

- Unit: `OVR-UGC-TERMS`
- Candidate product head: `535dcf2e034876a939e778c87fb1768c2d42a0c1`
- Result: Android and iOS passed the native common acceptance UI through their production gateways and created the expected Supabase acceptance row.
- Cleanup: both lanes restored the exact prior acceptance state; no credential or bearer material is retained.

Android ran on the API 35 emulator at clean head `535dcf2e034876a939e778c87fb1768c2d42a0c1`. The test mounted `QuataUgcTermsGateContent` with `ModerationRepository`, clicked the real common action, flushed the pending local-first acceptance through the product repository and observed the backend result. The corrected coordinator retained the snapshot before deletion and failed closed on credential cleanup; its exact-head rerun passed.

iOS ran on the macOS Hyper-V Simulator at clean head `535dcf2e034876a939e778c87fb1768c2d42a0c1`. The existing authenticated-session seeder populated the product Keychain session. A bounded opt-in launch argument cleared only the owned profile's local UGC acceptance before the production gateway was constructed. XCTest observed the normal product prompt, tapped the common accept control once and verified that the prompt closed. The coordinator independently observed the backend row.

The final reports are stored under `docs/candidate-attestations/evidence/` and bound by SHA-256 in `docs/candidate-attestations/ugc-terms-native-postflight.json`. Earlier Web remote evidence and local legal-document coverage remain governed by `docs/candidate-attestations/ugc-terms-parity.json`.
