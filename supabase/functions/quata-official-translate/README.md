# quata-official-translate

Authenticated translation proxy for Official authors. It validates the Supabase JWT and reads the
actor's existing `community_profiles.is_official` / `is_admin` flags through the caller bearer.
Only ES, EN and FR are accepted. HTML uses DeepL `tag_handling=html`, preserving tags and
attributes. `DEEPL_API_KEY` remains an Edge Function secret and is never returned to clients.

Deployment and secret changes are intentionally outside this migration.
