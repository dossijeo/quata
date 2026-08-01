# Official editor publication debt

`official_posts` exposes no reviewed batch/RPC transaction for ES/EN/FR creation. Android, Web
and iOS therefore use the existing row contract. Web and iOS request `return=representation`,
record every returned ID and, on failure or cancellation, soft-delete confirmed rows in reverse
order under `NonCancellable`. Uploaded media is then removed using the existing Supabase Storage
or WordPress cleanup contract. Rollback failures are attached as suppressed failures and are never
reported as success.

This compensation cannot provide database atomicity if connectivity is lost during rollback. A
server transaction would remove that window, but adding a function, table or RLS policy is outside
this migration and explicitly prohibited by the screen contract.

Translation also remains Android-only at the deployment boundary: the only configured DeepL
credential is Android `BuildConfig.DEEPL_API_KEY`. Web and iOS expose neither a client-safe
translation proxy nor a public translation credential. Shipping that secret in either client is
not acceptable; those targets require a reviewed authenticated proxy before their translation
control can be enabled honestly.
