# quata-account-lifecycle

`community_profiles.pass_hash` now contains the same PBKDF2-SHA-256 format
created by `quata-register`. Account deactivation and deletion verify that
format through the shared credential verifier. The verifier continues to
recognize the historical SHA-256 hash and `pass_plain` formats while those rows
are migrated.

This change is source-only: it does **not** deploy the Edge Function or modify
database schema, RLS policies, grants, or stored credentials. Before deploying,
run the Edge Function test suite and validate deactivation/delete with a test
account registered by the current `quata-register` deployment, plus one account
for each legacy format in a non-production Supabase project. Roll out the
registration and lifecycle functions together or ensure the deployed lifecycle
version includes PBKDF2 support before enabling new web registrations.
