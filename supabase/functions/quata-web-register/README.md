# Web registration contract

`quata-web-register` is the server boundary for browser registration. It creates
the Supabase Auth identity, `community_profiles` row and Web session as one
idempotent saga. Browser code never receives the service-role key.

Required environment variables: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`,
`SUPABASE_PUBLISHABLE_KEY`, `QUATA_WEB_ALLOWED_ORIGINS`, and
`QUATA_WEB_REGISTRATION_PEPPER` (at least 32 characters). Optional Turnstile
configuration uses `TURNSTILE_SECRET_KEY`.

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
