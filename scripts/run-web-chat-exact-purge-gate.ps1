[CmdletBinding()]
param(
    [switch]$Commit,
    [switch]$ApproveExactIdPurge,
    [Parameter(Mandatory=$true)][string]$RunId,
    [Parameter(Mandatory=$true)][string]$AllowlistPath,
    [Parameter(Mandatory=$true)][string]$ExpectedAllowlistSha256,
    [string]$Output = "build-reports/web/chat-exact-purge-gate.json"
)

# The default is a READ ONLY inspection followed by an explicit ROLLBACK.
# -Commit remains impossible unless this exact run is separately approved.
$ErrorActionPreference = "Stop"
if ($Commit -and -not $ApproveExactIdPurge) { throw "purge_commit_requires_ApproveExactIdPurge" }
if ($Commit -and $env:QUATA_E2E_CHAT_PURGE_COMMIT_AUTHORIZATION -cne "MANAGER_APPROVED_EXACT_ID_PURGE") { throw "purge_commit_authorization_missing" }
if (-not (Test-Path -LiteralPath $AllowlistPath -PathType Leaf)) { throw "purge_allowlist_missing" }
if ($RunId -notmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$') { throw "purge_run_id_invalid" }
if ($ExpectedAllowlistSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw "purge_allowlist_hash_invalid" }

function Resolve-SecretFile([string]$envName, [string]$fallback) {
    $path = [Environment]::GetEnvironmentVariable($envName); if ([string]::IsNullOrWhiteSpace($path)) { $path = $fallback }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "$envName`_file_missing" }; return (Resolve-Path -LiteralPath $path).Path
}
$dbUrlPath = Resolve-SecretFile "QUATA_E2E_PURGE_DB_URL_FILE" "C:\Users\PC\.quata-supabase-db-url.txt"
$caPath = Resolve-SecretFile "QUATA_E2E_PURGE_DB_CA_FILE" "C:\Users\PC\.quata-supabase-pooler-ca.pem"
$dbUrl = (Get-Content -LiteralPath $dbUrlPath -Raw).Trim(); if ([string]::IsNullOrWhiteSpace($dbUrl)) { throw "purge_db_url_file_empty" }
if ([string]::IsNullOrWhiteSpace((Get-Content -LiteralPath $caPath -Raw))) { throw "purge_ca_file_empty" }
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) { throw "purge_psql_missing" }

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$allowlist = Resolve-Path -LiteralPath $AllowlistPath
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("quata-chat-purge-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    # Catalog and counts are the only pre-commit reads.  The SQL contains no pattern matching,
    # only the UUIDs accepted by the Node allowlist gate. Do not replace these with LIKE/regex.
    $ids = (Get-Content -LiteralPath $allowlist -Raw | ConvertFrom-Json).profile_ids | ForEach-Object { "'$_'::uuid" }
    $idList = $ids -join ","
    $authIds = (Get-Content -LiteralPath $allowlist -Raw | ConvertFrom-Json).auth_user_ids | ForEach-Object { "'$_'::uuid" }
    $authList = $authIds -join ","
    $catalogSql = @"
select json_agg(x order by child_schema,child_table,child_column) from (
 select ns.nspname child_schema,c.relname child_table,a.attname child_column,pns.nspname parent_schema,p.relname parent_table,pa.attname parent_column,con.confdeltype delete_rule
 from pg_constraint con join pg_class c on c.oid=con.conrelid join pg_namespace ns on ns.oid=c.relnamespace join pg_class p on p.oid=con.confrelid join pg_namespace pns on pns.oid=p.relnamespace
 join unnest(con.conkey) with ordinality ck(attnum,ord) on true join pg_attribute a on a.attrelid=c.oid and a.attnum=ck.attnum
 join unnest(con.confkey) with ordinality pk(attnum,ord) on pk.ord=ck.ord join pg_attribute pa on pa.attrelid=p.oid and pa.attnum=pk.attnum where con.contype='f'
   and (c.relname = any(array['chat_threads','chat_private_threads','chat_participants','chat_messages','chat_attachments','chat_message_favorites','chat_message_reactions','chat_message_reads','chat_profile_blocks','chat_events','chat_sos_events','chat_sos_recipients','push_tokens','push_delivery_log','web_client_sessions','web_push_subscriptions','web_push_delivery_log','account_deletion_requests','community_profiles','users'])
        or p.relname = any(array['chat_threads','chat_private_threads','chat_participants','chat_messages','chat_attachments','chat_message_favorites','chat_message_reactions','chat_message_reads','chat_profile_blocks','chat_events','chat_sos_events','chat_sos_recipients','push_tokens','push_delivery_log','web_client_sessions','web_push_subscriptions','web_push_delivery_log','account_deletion_requests','community_profiles','users']))
) x;
"@
    $rawCatalog = & psql $dbUrl "sslmode=verify-full sslrootcert=$caPath" -X -A -t -v ON_ERROR_STOP=1 -c $catalogSql
    if ($LASTEXITCODE -ne 0) { throw "purge_catalog_query_failed" }
    # Every count is derived from the two exact profile UUIDs and their explicitly reachable
    # thread/message graph. There is no marker, prefix or time-window selector.
    $snapshotSql = "with p(id) as (values ($idList)), t(id) as (select id from public.chat_threads where created_by_profile_id in(select id from p) union select thread_id from public.chat_participants where profile_id in(select id from p)), m(id) as (select id from public.chat_messages where thread_id in(select id from t) or sender_profile_id in(select id from p)) select json_agg(x order by table) from (select 'chat_threads' table,count(*) count from public.chat_threads where id in(select id from t) union all select 'chat_private_threads',count(*) from public.chat_private_threads where thread_id in(select id from t) or profile_low_id in(select id from p) or profile_high_id in(select id from p) union all select 'chat_participants',count(*) from public.chat_participants where thread_id in(select id from t) or profile_id in(select id from p) union all select 'chat_messages',count(*) from public.chat_messages where id in(select id from m) union all select 'chat_attachments',count(*) from public.chat_attachments where thread_id in(select id from t) or message_id in(select id from m) or uploaded_by_profile_id in(select id from p) union all select 'chat_message_favorites',count(*) from public.chat_message_favorites where message_id in(select id from m) or profile_id in(select id from p) union all select 'chat_message_reactions',count(*) from public.chat_message_reactions where message_id in(select id from m) or profile_id in(select id from p) union all select 'chat_message_reads',count(*) from public.chat_message_reads where message_id in(select id from m) or profile_id in(select id from p) union all select 'chat_profile_blocks',count(*) from public.chat_profile_blocks where thread_id in(select id from t) or blocker_profile_id in(select id from p) or blocked_profile_id in(select id from p) union all select 'chat_events',count(*) from public.chat_events where thread_id in(select id from t) or actor_profile_id in(select id from p) union all select 'chat_sos_events',count(*) from public.chat_sos_events where thread_id in(select id from t) or message_id in(select id from m) or profile_id in(select id from p) union all select 'chat_sos_recipients',count(*) from public.chat_sos_recipients where delivered_thread_id in(select id from t) or recipient_profile_id in(select id from p) union all select 'push_tokens',count(*) from public.push_tokens where user_id in(select id from p) or auth_user_id in($authList) union all select 'push_delivery_log',count(*) from public.push_delivery_log where message_id in(select id from m) or profile_id in(select id from p) union all select 'web_client_sessions',count(*) from public.web_client_sessions where profile_id in(select id from p) or auth_user_id in($authList) union all select 'web_push_subscriptions',count(*) from public.web_push_subscriptions where profile_id in(select id from p) or auth_user_id in($authList) union all select 'web_push_delivery_log',count(*) from public.web_push_delivery_log where message_id in(select id from m) or profile_id in(select id from p) union all select 'account_deletion_requests',count(*) from public.account_deletion_requests where auth_user_id in($authList) union all select 'community_profiles',count(*) from public.community_profiles where id in(select id from p) union all select 'auth.users',count(*) from auth.users where id in($authList)) x;"
    $rawSnapshot = & psql $dbUrl "sslmode=verify-full sslrootcert=$caPath" -X -A -t -v ON_ERROR_STOP=1 -c $snapshotSql
    if ($LASTEXITCODE -ne 0) { throw "purge_snapshot_query_failed" }
    $dbFingerprint = (& psql $dbUrl "sslmode=verify-full sslrootcert=$caPath" -X -A -t -v ON_ERROR_STOP=1 -c "select encode(sha256((current_database()||'|'||current_user)::bytea),'hex');").Trim()
    if ($LASTEXITCODE -ne 0) { throw "purge_fingerprint_query_failed" }
    @{ constraints=(@($rawCatalog|ConvertFrom-Json)); snapshots=(@($rawSnapshot|ConvertFrom-Json)); databaseFingerprint=$dbFingerprint } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $tmp "input.json") -NoNewline
    $result = & node (Join-Path $PSScriptRoot "web-chat-exact-purge-gate.mjs") --manifest $allowlist --run-id $RunId --allowlist-sha256 $ExpectedAllowlistSha256 --input (Join-Path $tmp "input.json")
    if ($LASTEXITCODE -ne 0) { throw "purge_plan_rejected" }; $result | Set-Content -LiteralPath (Join-Path $tmp "plan.json") -NoNewline
    # A dry run opens an explicit read-only transaction and rolls it back. It never invokes a
    # cleanup RPC or DELETE. The commit path uses only mappings already hash-validated above.
    if ($Commit) {
        $fixtureSql = @(); $manifest = Get-Content -LiteralPath $allowlist -Raw | ConvertFrom-Json
        foreach ($fixture in $manifest.fixtures) { $fixtureSql += "select public.quata_account_delete_data('$($fixture.profile_id)'::uuid,'$($fixture.auth_user_id)'::uuid);" }
        # Keep the final absence proof inside the same transaction: a failed proof rolls every
        # statement back. auth.users remains the last destructive relation.
        $sql = "BEGIN; $($fixtureSql -join ' ') delete from auth.users where id in ($authList); DO `$`$ BEGIN IF exists(select 1 from auth.users where id in ($authList)) OR exists(select 1 from public.community_profiles where id in ($idList)) THEN RAISE EXCEPTION 'exact_id_postcondition_failed'; END IF; END `$`$; COMMIT;"
    } else { $sql = "BEGIN READ ONLY; ROLLBACK;" }
    & psql $dbUrl "sslmode=verify-full sslrootcert=$caPath" -X -v ON_ERROR_STOP=1 -c $sql | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "purge_transaction_failed" }
    $parsed=(Get-Content (Join-Path $tmp "plan.json") -Raw|ConvertFrom-Json); $parsed.evidence.status=if($Commit){"committed"}else{"dry_run_rolled_back"}; $parsed.evidence|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $Output -NoNewline
} finally { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue }
