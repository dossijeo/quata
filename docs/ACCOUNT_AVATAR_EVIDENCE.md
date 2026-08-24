# ACCOUNT-AVATAR evidence contract

`ACCOUNT-AVATAR` is reduced as a focal GO on Product/Evidence SHA
`d3895ca9dab92853e4c6e1e1ae632bbb0eff04a9`. The attestation SHA may move after
documentation-only updates; reuse the product evidence only when
`scripts/validate-candidate-attestation.mjs` classifies the diff as attestation-only.

The three real platform lanes are:

```powershell
npm run evidence:account-avatar-web
npm run evidence:account-avatar-android
npm run evidence:account-avatar-ios
```

Exact-SHA evidence for `d3895ca9`:

- Web/Wasm: `build-reports/web/account-avatar-evidence.json`
- Android: `build-reports/android/account-avatar-evidence.json`
- iOS: `build-reports/ios/account-avatar-evidence.json`

Each lane must remain opt-in for reversible remote mutation, keep credentials and tokens out of
stdout/report JSON, and verify the same product sequence: open Cuenta, select an image through the
platform picker boundary, confirm the common locked square editor, save the edited JPEG, persist the
profile avatar, probe the resulting public JPEG, restore the previous `avatar_url`, delete the
Storage object and report cleanup success.

The sidecar aggregate contract is intentionally not a substitute for the real browser/emulator/XCTest
evidence. It validates report shape and fail-closed guarantees for any consolidated candidate report:

```powershell
node scripts/account-avatar-evidence-contract.mjs build-reports/account-avatar/evidence.json
```

The bounded reversible backend runner remains available for storage/profile rollback diagnostics:

```powershell
npm run evidence:account-avatar-backend
```

It requires `QUATA_ACCOUNT_AVATAR_REAL_MUTATION_OPT_IN=I_ACCEPT_REVERSIBLE_ACCOUNT_AVATAR_MUTATION`
and verifies the real authenticated Storage/PostgREST avatar write path, forced post-upload
rollback, restore of the previous `avatar_url`, and zero `storage.objects` residue. It is not a
substitute for the final visual Web/Android/iOS route evidence.

All real lanes must use the existing `QUATA_ACCOUNT_AVATAR_CREDENTIALS_FILE` pattern (falling back to
`C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt`), and keep credentials, access tokens, database
URLs, and secret values out of stdout and report JSON. A fixture lane should remain the default for
non-mutating fast checks.

The current product-side anchors and unit tests remain the source of truth for Web, Android, and iOS
implementation details. Do not add new product tags unless a real missing stable anchor blocks the
visual route.
