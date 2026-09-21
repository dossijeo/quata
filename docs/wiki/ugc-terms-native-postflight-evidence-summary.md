# UGC terms native remote postflight

- Unit: `OVR-UGC-TERMS`
- Candidate product head: `437df97aa7c5d489c2af0df35776d171b21723f1`
- Native evidence head: `535dcf2e034876a939e778c87fb1768c2d42a0c1`
- Result: Android and iOS passed the native common acceptance UI through their production gateways and created the expected Supabase acceptance row.
- Cleanup: both lanes restored the exact prior acceptance state; no credential or bearer material is retained.
- Integration: PR #395 merged frozen head `5fee44539dd3339306e1f0a4c54321a80e5a8e32` as Product SHA `77bdffff818ba76c1b867b4445aefc46e47a86e4`; both commits have tree `f85b704d0595d272edc1f0f3c37d49fb90be1984`, and the final Web/Android, iOS and CodeQL gates passed.

Android ran on the API 35 emulator at clean evidence head `535dcf2e034876a939e778c87fb1768c2d42a0c1`. The test mounted `QuataUgcTermsGateContent` with `ModerationRepository`, clicked the real common action, flushed the pending local-first acceptance through the product repository and observed the backend result. The corrected coordinator retained the snapshot before deletion and failed closed on credential cleanup; its exact-head rerun passed.

iOS ran on the macOS Hyper-V Simulator at the same clean evidence head. The existing authenticated-session seeder populated the product Keychain session. A bounded opt-in launch argument cleared only the owned profile's local UGC acceptance before the production gateway was constructed. XCTest observed the normal product prompt, tapped the common accept control once and verified that the prompt closed. The coordinator independently observed the backend row.

The candidate product head merges `main` commit `dae9933467262157860664f7241e5a05e692d421`. Its reviewed incoming #391, #396, #397 and #398 deltas add separately certified chat lifecycle, startup lifecycle and profile rollback behavior plus their custody. They change no UGC product, coordinator, selected test, platform configuration, dependency, backend contract or route, so the exact `535dcf2e034876a939e778c87fb1768c2d42a0c1` native UGC evidence remains valid and no native UGC E2E was repeated solely for the merge SHA.

The final reports are stored under `docs/candidate-attestations/evidence/` and bound by SHA-256 in `docs/candidate-attestations/ugc-terms-native-postflight.json`. Earlier Web remote evidence and local legal-document coverage remain governed by `docs/candidate-attestations/ugc-terms-parity.json`.
