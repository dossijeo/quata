# Web registration contract

`quata-web-register` is the server boundary for browser registration. It creates
the Supabase Auth identity, `community_profiles` row and Web session as one
idempotent saga. Browser code never receives the service-role key.

Required environment variables: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`,
`QUATA_WEB_REGISTRATION_API_KEY`, `QUATA_WEB_REGISTRATION_ALLOWED_ORIGINS`,
`QUATA_WEB_REGISTRATION_PEPPER`, `QUATA_INTERNAL_AUTH_PASSWORD_SECRET`,
`QUATA_WEB_REGISTRATION_ENABLED`. Both secrets must contain at least
32 characters. When registration is enabled, configure
`QUATA_WEB_REGISTRATION_TURNSTILE_SECRET`. The feature and challenge are
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
`deno run --allow-env --allow-net scripts/cleanup-web-registration.ts <request-id>`.
It only accepts quarantined records, revokes Web sessions, removes profile and
Auth, then purges the ledger and emits a structured completion/failure event.
