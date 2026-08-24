# ACCOUNT-AVATAR evidence contract

This is sidecar scaffolding for account-avatar evidence. The contract validator itself does not run
a browser, emulator, XCTest, login, upload, Storage query, PostgREST write, or Supabase cleanup.

The future platform runners must produce one JSON report with `version: 1`, unit `ACCOUNT-AVATAR`,
the candidate `productSha`, and `web`, `android`, and `ios` entries. Each entry must prove the same
sequence: select an image, confirm the locked square editor, upload the edited JPEG, observe the
profile write, reload and observe the persisted avatar, force a failure after upload and verify
Storage rollback has zero physical residue, retry successfully, then verify final cleanup with zero
physical residue. The report validator is:

```powershell
node scripts/account-avatar-evidence-contract.mjs build-reports/account-avatar/evidence.json
```

The bounded reversible backend runner is:

```powershell
npm run evidence:account-avatar-backend
```

It requires `QUATA_ACCOUNT_AVATAR_REAL_MUTATION_OPT_IN=I_ACCEPT_REVERSIBLE_ACCOUNT_AVATAR_MUTATION`
and verifies the real authenticated Storage/PostgREST avatar write path, forced post-upload
rollback, restore of the previous `avatar_url`, and zero `storage.objects` residue. It is not a
substitute for the final visual Web/Android/iOS route evidence.

The real lane is intentionally separate from this contract. If implemented, it must be opt-in with
`QUATA_ACCOUNT_AVATAR_REAL_MUTATION_OPT_IN=I_ACCEPT_REVERSIBLE_ACCOUNT_AVATAR_MUTATION`, use the
existing `QUATA_ACCOUNT_AVATAR_CREDENTIALS_FILE` pattern (falling back to
`C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt`), and keep credentials, access tokens, database
URLs, and secret values out of stdout and report JSON. A fixture lane should remain the default.

The current product-side anchors and unit tests remain the source of truth for Web, Android, and iOS
implementation details. This contract intentionally does not add or require product tags.
