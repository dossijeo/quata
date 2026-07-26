[CmdletBinding()]
param([string]$DockerImage = "postgres:17-alpine")

# End-to-end proof for the backup format. It creates no remote connection and
# removes the source, target, keys, certificates and dumps in finally.
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
function Fail([string]$Code) { throw $Code }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail "docker_required" }
$id=[guid]::NewGuid().ToString("N").Substring(0,12); $root=Join-Path ([IO.Path]::GetTempPath()) "quata-logical-backup-test-$id"; $network="quata-backup-net-$id"; $certificateVolume="quata-backup-tls-$id"; $certificateHelper="quata-backup-tls-copy-$id"; $source="quata-backup-source-$id"; $password=[guid]::NewGuid().ToString("N")
try {
    $tls=Join-Path $root "tls"; $out=Join-Path $root "out"; New-Item -ItemType Directory -Path $tls,$out | Out-Null
    # Generate a self-signed test CA/server certificate with the Docker DNS name.
    & docker volume create $certificateVolume 2>$null | Out-Null; if ($LASTEXITCODE -ne 0) { Fail "test_certificate_volume_failed" }
    & docker run --rm -v "${certificateVolume}:/tls" $DockerImage sh -ec "apk add --no-cache openssl >/dev/null 2>&1; openssl req -x509 -newkey rsa:2048 -nodes -keyout /tls/server.key -out /tls/server.crt -days 1 -subj /CN=db.local -addext subjectAltName=DNS:db.local 2>/dev/null; cp /tls/server.crt /tls/ca.pem; chown postgres:postgres /tls/server.key /tls/server.crt; chmod 600 /tls/server.key" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "test_certificate_setup_failed" }
    & docker create --name $certificateHelper -v "${certificateVolume}:/tls:ro" $DockerImage 2>$null | Out-Null; if ($LASTEXITCODE -ne 0) { Fail "test_certificate_copy_failed" }
    & docker cp "${certificateHelper}:/tls/ca.pem" (Join-Path $tls "ca.pem") 2>$null | Out-Null; if ($LASTEXITCODE -ne 0) { Fail "test_certificate_copy_failed" }
    & docker rm $certificateHelper 2>$null | Out-Null
    & docker network create $network 2>$null | Out-Null; if ($LASTEXITCODE -ne 0) { Fail "test_network_failed" }
    & docker run -d --rm --name $source --network $network --network-alias db.local -e "POSTGRES_PASSWORD=$password" -v "${certificateVolume}:/tls:ro" $DockerImage -c ssl=on -c ssl_cert_file=/tls/server.crt -c ssl_key_file=/tls/server.key 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "test_source_start_failed" }
    $ready=$false; foreach ($n in 1..40) { & docker exec $source pg_isready -U postgres 2>$null | Out-Null; if ($LASTEXITCODE -eq 0) { $ready=$true; break }; Start-Sleep -Milliseconds 500 }; if (-not $ready) { Fail "test_source_not_ready" }
    $sql="create table public.community_comments(id bigint primary key, body text not null); create table public.official_post_likes(id bigint primary key, emoji text not null); insert into public.community_comments values (1,'one'),(2,'two'); insert into public.official_post_likes values (1,'heart'),(2,'star'),(3,'heart');"
    & docker exec -e "PGPASSWORD=$password" $source psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c $sql 2>$null | Out-Null; if ($LASTEXITCODE -ne 0) { Fail "test_seed_failed" }
    $urlFile=Join-Path $root "url.txt"; [IO.File]::WriteAllText($urlFile,"postgresql://postgres:$password@db.local:5432/postgres?sslmode=verify-full")
    $keyFile=Join-Path $root "key.txt"; $key=[byte[]]::new(32); $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); try { $rng.GetBytes($key) } finally { $rng.Dispose() }; [IO.File]::WriteAllText($keyFile,[Convert]::ToBase64String($key)); [Array]::Clear($key,0,$key.Length)
    & (Join-Path $PSScriptRoot "new-db-logical-backup.ps1") -DbUrlFile $urlFile -TlsCaFile (Join-Path $tls "ca.pem") -EncryptionKeyFile $keyFile -OutRoot $out -Scope Full -DockerImage $DockerImage -DockerNetwork $network
    if ($LASTEXITCODE -ne 0) { Fail "test_backup_failed" }
    $set=(Get-ChildItem -LiteralPath $out -Directory | Where-Object Name -notlike '.working-*' | Select-Object -First 1).FullName; if ([string]::IsNullOrWhiteSpace($set)) { Fail "test_backup_set_missing" }
    $manifest=Get-Content -LiteralPath (Join-Path $set "manifest.json") -Raw | ConvertFrom-Json
    if ($manifest.grantsIncluded -ne $true -or $manifest.containsConnectionData -ne $false) { Fail "test_manifest_contract_failed" }
    if ($env:OS -eq "Windows_NT") {
        $unsafe=@((Get-Acl -LiteralPath $set).Access | Where-Object { $_.AccessControlType -eq "Allow" -and $_.IdentityReference.Value -match "(^|\\)(Everyone|Users|Authenticated Users)$" })
        if ($unsafe.Count -ne 0) { Fail "test_backup_acl_not_restricted" }
    }
    & (Join-Path $PSScriptRoot "restore-db-logical-backup-drill.ps1") -BackupSet $set -EncryptionKeyFile $keyFile -DockerImage $DockerImage -ExpectedCommunityComments 2 -ExpectedOfficialPostLikes 3
    if ($LASTEXITCODE -ne 0) { Fail "test_restore_failed" }
    Write-Output "logical_backup_disposable_tls_restore_test_passed"
}
finally {
    $previousErrorActionPreference=$ErrorActionPreference; $ErrorActionPreference="SilentlyContinue"
    try { & docker rm -f $source 2>$null | Out-Null; & docker rm -f $certificateHelper 2>$null | Out-Null; & docker network rm $network 2>$null | Out-Null; & docker volume rm $certificateVolume 2>$null | Out-Null } finally { $ErrorActionPreference=$previousErrorActionPreference }
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
}
