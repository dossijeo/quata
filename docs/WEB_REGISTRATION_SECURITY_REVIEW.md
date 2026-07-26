# Web registration security review

## Result

The Web registration path is implemented behind a dedicated Edge Function and
is ready for release validation, but is not deployed by this change.

The browser sends a strict, versioned allowlist. Privileged fields are rejected.
The server creates Auth, profile, and Web session records with durable
idempotency, fixed-window anti-abuse limits, and compensating cleanup. Passwords
use salted PBKDF2-SHA256; recovery answers use a peppered digest. Responses keep
the existing Web login/session shape.

## Compatibility and release order

Android's legacy registration and login contract was preserved. New hashes are
also understood by `quata-auth-bridge`. Migration
`20260726171004_web_registration_contract.sql` must follow the separate
`community_profiles` actor-guard migration. No existing RLS policy was changed
here, and the implementation does not rely on anonymous table writes.

## Findings retained for follow-up

The live read-only audit found broad anonymous grants/policies on
`community_profiles`. They are not hardened in this branch to avoid breaking
production and are being handled in a separately coordinated migration.
`community_comments` and `official_post_likes` are explicitly out of scope.

Operational follow-up should alert on `cleanup_required`, rotate the registration
pepper under a controlled migration, and review rate-limit thresholds after
observing real traffic. No secret values are stored in this repository.
