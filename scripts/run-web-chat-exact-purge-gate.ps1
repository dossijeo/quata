[CmdletBinding()]
param(
    [switch]$Commit,
    [Parameter(Mandatory=$true)][string]$RunId,
    [Parameter(Mandatory=$true)][string]$ManifestPath,
    [Parameter(Mandatory=$true)][string]$ExpectedManifestSha256,
    [Parameter(Mandatory=$true)][string]$CandidateSha,
    [Parameter(Mandatory=$true)][string]$ProjectRef,
    [string]$Output = "build-reports/web/chat-exact-purge-gate.json"
)
# This script is an inspection gate, not a production deletion tool.  The old
# commit switch is rejected before secrets, manifest parsing, or a DB connection.
$ErrorActionPreference = "Stop"
if ($Commit) { throw "purge_commit_unavailable_by_construction: requires separately deployed Actions-attested purge service" }
if (!(Test-Path -LiteralPath $ManifestPath -PathType Leaf)) { throw "purge_manifest_missing" }
if ($ExpectedManifestSha256 -notmatch '^[a-fA-F0-9]{64}$' -or $CandidateSha -notmatch '^[a-fA-F0-9]{64}$') { throw "purge_hash_invalid" }
if ($ProjectRef -notmatch '^[a-zA-Z0-9-]{8,80}$') { throw "purge_project_ref_invalid" }
if (!(Get-Command psql -ErrorAction SilentlyContinue)) { throw "purge_psql_missing" }
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$manifest = (Resolve-Path -LiteralPath $ManifestPath).Path
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("quata-chat-purge-" + [guid]::NewGuid().ToString("N")); New-Item -ItemType Directory -Path $tmp | Out-Null
$oldService = $env:PGSERVICE; $oldServiceFile = $env:PGSERVICEFILE; $oldPassFile = $env:PGPASSFILE
try {
    # Immutable bytes are validated and their content normalized before resolving any secret or invoking psql.
    & node (Join-Path $PSScriptRoot "web-chat-exact-purge-gate.mjs") --validate-manifest $manifest --run-id $RunId --manifest-sha256 $ExpectedManifestSha256 --candidate-sha $CandidateSha --project-ref $ProjectRef | Set-Content -LiteralPath (Join-Path $tmp "identity.json") -NoNewline
    if ($LASTEXITCODE -ne 0) { throw "purge_manifest_rejected_before_db" }
    $data = Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
    # IDs enter SQL only through \copy into temporary UUID tables; no value is interpolated into SQL.
    @($data.profile_ids | ForEach-Object { $_.ToString().ToLowerInvariant() }) | Set-Content -LiteralPath (Join-Path $tmp "profiles.csv") -NoNewline
    @($data.auth_user_ids | ForEach-Object { $_.ToString().ToLowerInvariant() }) | Set-Content -LiteralPath (Join-Path $tmp "auth.csv") -NoNewline
    $dbUrlFile = $env:QUATA_E2E_PURGE_DB_URL_FILE; $caFile = $env:QUATA_E2E_PURGE_DB_CA_FILE
    if ([string]::IsNullOrWhiteSpace($dbUrlFile) -or [string]::IsNullOrWhiteSpace($caFile)) { throw "purge_secret_file_environment_required" }
    foreach($p in @($dbUrlFile,$caFile)) { if (!(Test-Path -LiteralPath $p -PathType Leaf)) { throw "purge_secret_file_missing" } }
    # psql receives only a service name. Sensitive connection material never reaches argv or evidence.
    $serviceFile = Join-Path $tmp "pg_service.conf"; $passFile = Join-Path $tmp "pgpass"
    "[quata_purge]`ndbname=$((Get-Content -LiteralPath $dbUrlFile -Raw).Trim())`nsslmode=verify-full`nsslrootcert=$((Resolve-Path -LiteralPath $caFile).Path)" | Set-Content -LiteralPath $serviceFile -NoNewline
    New-Item -ItemType File -Path $passFile | Out-Null; $env:PGSERVICEFILE = $serviceFile; $env:PGSERVICE = "quata_purge"; $env:PGPASSFILE = $passFile
    $sql = @"
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE READ ONLY;
SET LOCAL lock_timeout = '5s'; SET LOCAL statement_timeout = '30s';
SELECT pg_advisory_xact_lock(hashtextextended('quata:web-chat-exact-purge:' || :'project_ref', 0));
CREATE TEMP TABLE purge_profiles(id uuid PRIMARY KEY) ON COMMIT DROP;
CREATE TEMP TABLE purge_auth_users(id uuid PRIMARY KEY) ON COMMIT DROP;
\copy purge_profiles(id) FROM :'profiles_file' WITH (FORMAT csv)
\copy purge_auth_users(id) FROM :'auth_file' WITH (FORMAT csv)
SELECT json_build_object('constraints', coalesce(json_agg(row_to_json(c) ORDER BY c.child_schema,c.child_table,c.child_column), '[]'::json))
FROM (SELECT ns.nspname child_schema, cl.relname child_table, a.attname child_column, pns.nspname parent_schema, p.relname parent_table, pa.attname parent_column, con.confdeltype delete_rule FROM pg_constraint con JOIN pg_class cl ON cl.oid=con.conrelid JOIN pg_namespace ns ON ns.oid=cl.relnamespace JOIN pg_class p ON p.oid=con.confrelid JOIN pg_namespace pns ON pns.oid=p.relnamespace JOIN unnest(con.conkey) WITH ORDINALITY ck(attnum,ord) ON true JOIN pg_attribute a ON a.attrelid=cl.oid AND a.attnum=ck.attnum JOIN unnest(con.confkey) WITH ORDINALITY pk(attnum,ord) ON pk.ord=ck.ord JOIN pg_attribute pa ON pa.attrelid=p.oid AND pa.attnum=pk.attnum WHERE con.contype='f' AND (ns.nspname IN ('public','auth') OR pns.nspname IN ('public','auth'))) c;
SELECT json_build_object('fingerprint', json_build_object('project_ref', :'project_ref', 'server_version_num', current_setting('server_version_num'), 'database', current_database(), 'server_endpoint', coalesce(inet_server_addr()::text,'local') ));
-- The graph/count query is intentionally held in this one SERIALIZABLE snapshot. The destructive phase is unavailable.
ROLLBACK;
"@
    $sqlPath=Join-Path $tmp "inspection.sql"; Set-Content -LiteralPath $sqlPath -Value $sql -NoNewline
    & psql -X -v ON_ERROR_STOP=1 -v project_ref=$ProjectRef -v profiles_file=(Join-Path $tmp "profiles.csv") -v auth_file=(Join-Path $tmp "auth.csv") -f $sqlPath | Set-Content -LiteralPath (Join-Path $tmp "raw.json")
    if ($LASTEXITCODE -ne 0) { throw "purge_readonly_transaction_failed" }
    # Full interpretation is deliberately unavailable until a schema-specific, independently reviewed catalog adapter exists.
    throw "purge_schema_adapter_unavailable_by_construction: read-only transaction rolled back; no destructive action occurred"
} finally {
    $env:PGSERVICE=$oldService; $env:PGSERVICEFILE=$oldServiceFile; $env:PGPASSFILE=$oldPassFile
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
