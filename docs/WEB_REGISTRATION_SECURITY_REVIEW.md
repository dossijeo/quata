# Web registration security review

## Result

The Web registration path is implemented behind a dedicated Edge Function. On
25 September 2026 the database contract was applied atomically after a complete
encrypted backup and restore drill, and `quata-register` v1 plus the compatible
`quata-auth-bridge` v81 were deployed. Registration remains disabled and the
endpoint returns `503 registration_unavailable` until a real Turnstile widget
and its secret are configured.

The browser sends a strict, versioned allowlist. Privileged fields are rejected.
The server creates Auth, profile, and Web session records with durable
idempotency, fixed-window anti-abuse limits, and compensating cleanup. Passwords
use salted PBKDF2-SHA256; recovery answers use a peppered digest. Responses keep
the existing Web login/session shape.

## Compatibility and release order

Android registration is migrated to the same strict `quata-register` contract;
its login remains on `quata-auth-bridge`, which also understands the new hashes.
Migration `20260726171004_web_registration_contract.sql` followed the separate
`community_profiles` actor-guard migration. Its postflight verifies service-role
only tables/RPCs, RLS on all registration tables and zero initial rows. No
existing RLS policy was relaxed, and the implementation does not rely on
anonymous table writes.

## Findings retained for follow-up

The published Android v32 compatibility branch remains behind its deployed
server switch. It accepts only the observed v32 request signature, records
aggregate use, and rejects the `android-auth-boundary-v1` generation header sent
by the current client. This deliberately limited compatibility is tracked
separately from registration.
`community_comments` and `official_post_likes` are explicitly out of scope.

Operational follow-up should alert on `cleanup_required`, rotate the registration
pepper under a controlled migration, and review rate-limit thresholds after
observing real traffic. No secret values are stored in this repository.
