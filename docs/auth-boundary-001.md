# AUTH-BOUNDARY-001: shadow adapter

## Compatibility inventory

- Android login and post-registration login call Edge Function `quata-auth-bridge` with
  `version: 1`, `action: "login"`, `country_code`, `phone`, and `password`.
- The published session remains `profile`, `session`, and `user`; Android continues to
  persist the same access/refresh tokens and expiry.
- Password recovery continues to use the same bridge actions. Web continues to use
  `web_login` and is not routed through this Android adapter.
- Existing REST/RPC calls and all grants/RLS policies are out of scope for this slice.

## Shadow flag

`-Pquata.authBoundaryShadow=true` enables a local Android diagnostic that checks the
existing login response shape after a successful request. It does not make another request,
change the request, alter tokens, or reject a login. Diagnostics contain only enum codes.
The default is `false`.

## Next slice backlog

1. Add fixture-based response compatibility tests generated from the Edge Function contract,
   including legacy `profile_id` login and Web `web_login`; keep both endpoint actions intact.
2. Introduce an explicit transport interface for recovery/reset beside this login adapter,
   still delegating to `quata-auth-bridge` and preserving request JSON byte-for-byte.
3. After observed shadow telemetry is clean, propose an opt-in rollout flag for the adapter;
   do not change database grants, policies, RPC signatures, or legacy client support without a
   separately reviewed migration.
