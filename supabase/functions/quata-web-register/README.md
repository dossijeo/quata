# Web registration contract

`quata-web-register` is the server boundary for browser registration. It creates
the Supabase Auth identity, `community_profiles` row and Web session as one
idempotent saga. Browser code never receives the service-role key.

Despite its legacy route name, this is the single registration orchestrator.
`channel` is strictly `web` or `android`; both require a fresh Turnstile token
verified by Siteverify and use the identical E.164/idempotency/saga/hash path.
Android obtains the token through the official Turnstile WebView integration.
Until that client ships, its channel remains unusable and fail-closed.

Required environment variables: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`,
`QUATA_WEB_REGISTRATION_API_KEY`, `QUATA_WEB_REGISTRATION_ALLOWED_ORIGINS`,
`QUATA_WEB_REGISTRATION_PEPPER`, `QUATA_INTERNAL_AUTH_PASSWORD_SECRET`,
`QUATA_INTERNAL_AUTH_PASSWORD_SECRET_VERSION`,
`QUATA_WEB_REGISTRATION_ENABLED`. Both secrets must contain at least
32 characters. When registration is enabled, configure
`QUATA_WEB_REGISTRATION_TURNSTILE_SECRET` and the comma-separated
`QUATA_TURNSTILE_ALLOWED_HOSTNAMES`. Siteverify must match both hostname and
`register_web`/`register_android` action. The feature and challenge are
fail-closed; the browser also requires the
public `quata-web-registration-enabled=true` meta flag.

Apply `20260726171004_web_registration_contract.sql` only after the
`community_profiles` actor guard. Then configure secrets and deploy through the
release workflow. This branch intentionally does not deploy.

The endpoint accepts only the documented profile fields plus
`client_installation_id` and `idempotency_key`. It validates them, applies
durable phone/client/IP rate limits, stores only peppered request identifiers,
and compensates profile then Auth on partial failure. Requests whose cleanup
cannot complete are quarantined as `cleanup_required` for operator review.

Run:

```text
npm run test:web-registration-contract
gradlew :web:compileKotlinWasmJs :feature:auth:compileKotlinMetadata
```

Existing Android registration remains unchanged. The function uses service-role
and therefore does not depend on anonymous `community_profiles` INSERT/UPDATE.

Operator cleanup is auditable via
`deno run --allow-env --allow-net scripts/cleanup-web-registration.ts`.
It atomically scans/leases quarantined records, revokes Web sessions, verifies
ownership, removes profile and Auth, verifies absence, preserves the ledger and
appends structured audit events with row counts. Failures schedule bounded
backoff and exit non-zero. Set `QUATA_CLEANUP_ALERT_WEBHOOK` to deliver the
redacted structured alert to the release monitoring integration.

The browser build injects `quata-web-registration-api-key`,
`quata-web-registration-enabled`, and `quata-turnstile-site-key` meta values.
The API key must equal `QUATA_WEB_REGISTRATION_API_KEY` on the Edge runtime.
Use `node scripts/inject-web-registration-config.mjs <dist/index.html>`; it is
fail-closed and refuses an enabled build without both public values.

## Internal Auth secret rotation

`QUATA_INTERNAL_AUTH_PASSWORD_SECRET` is version-independent application key
material and must not be coupled to service-role. During rotation, deploy the
bridge first: a successfully verified legacy password that cannot sign in causes
an admin password update using the current secret. Keep the previous release
available until active accounts have transitioned; rollback the code and secret
together. Never log either value. Automated bridge tests must cover an identity
created with the previous derivation and its first-login transition.

For an incident rollback, run the reviewed
`supabase/rollback/20260726171004_web_registration_contract_disable.sql`; it
revokes new claims but preserves cleanup RPCs, ledger and audit events. Restore
service only through a forward migration.
