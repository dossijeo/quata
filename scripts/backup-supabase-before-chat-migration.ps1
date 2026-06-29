param(
    [string]$DatabaseUrl = $env:SUPABASE_DB_URL,
    [string]$OutDir = "backups/supabase",
    [string]$PgDumpPath = "",
    [string]$DockerImage = "postgres:17-alpine",
    [switch]$UseDocker,
    [switch]$SkipFullDump,
    [switch]$SkipSchemaDump,
    [switch]$SkipCriticalTablesDump
)

$ErrorActionPreference = "Stop"

function Resolve-Tool {
    param([string]$Name)
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        return $null
    }
    return $command.Source
}

function Test-DockerReady {
    $docker = Resolve-Tool "docker"
    if ($null -eq $docker) {
        return $false
    }
    try {
        & $docker ps --format "{{.ID}}" *> $null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Invoke-PgDump {
    param(
        [string[]]$Arguments,
        [string]$OutputFile
    )

    if ($script:UseDocker) {
        $mountPath = (Resolve-Path $script:BackupDir).Path
        $fileName = Split-Path $OutputFile -Leaf
        $dockerArgs = @(
            "run", "--rm",
            "-e", "PGCONNECT_TIMEOUT=20",
            "-v", "${mountPath}:/backup",
            $DockerImage,
            "pg_dump",
            "-f", "/backup/$fileName"
        ) + $Arguments
        & docker @dockerArgs
    } else {
        & $script:PgDump "-f" $OutputFile @Arguments
    }

    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump failed for $OutputFile"
    }
}

if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
    throw @"
Set SUPABASE_DB_URL or pass -DatabaseUrl.

Example:
  `$env:SUPABASE_DB_URL = "postgresql://postgres.<project-ref>:<password>@aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require"
  .\scripts\backup-supabase-before-chat-migration.ps1
"@
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$script:BackupDir = Join-Path $OutDir $timestamp
New-Item -ItemType Directory -Force $script:BackupDir | Out-Null

$script:PgDump = if (-not [string]::IsNullOrWhiteSpace($PgDumpPath)) {
    $PgDumpPath
} else {
    Resolve-Tool "pg_dump"
}

$script:UseDocker = $UseDocker.IsPresent
if ($script:UseDocker) {
    if (Test-DockerReady) {
        $script:PgDump = ""
    } else {
        throw "Docker was requested but Docker is not running. Start Docker Desktop and retry."
    }
} elseif ([string]::IsNullOrWhiteSpace($script:PgDump)) {
    if (Test-DockerReady) {
        $script:UseDocker = $true
    } else {
        throw "No pg_dump found and Docker is not running. Install PostgreSQL 17 client tools or start Docker Desktop."
    }
}

$manifestPath = Join-Path $script:BackupDir "manifest.txt"
@(
    "created_at=$(Get-Date -Format o)",
    "database_url_host=$(([Uri]$DatabaseUrl).Host)",
    "tool=$($(if ($script:UseDocker) { "docker $DockerImage pg_dump" } else { $script:PgDump }))",
    "note=Generated before applying Quata chat Supabase migrations."
) | Set-Content -Path $manifestPath -Encoding UTF8

if (-not $SkipSchemaDump) {
    $schemaFile = Join-Path $script:BackupDir "schema-before-chat-migration.sql"
    Invoke-PgDump -OutputFile $schemaFile -Arguments @(
        "--schema-only",
        "--no-owner",
        "--no-acl",
        $DatabaseUrl
    )
}

if (-not $SkipCriticalTablesDump) {
    $criticalFile = Join-Path $script:BackupDir "critical-existing-tables.sql"
    Invoke-PgDump -OutputFile $criticalFile -Arguments @(
        "--data-only",
        "--no-owner",
        "--no-acl",
        "--table=public.community_profiles",
        "--table=public.community_walls",
        "--table=public.community_emergency_contacts",
        "--table=storage.buckets",
        $DatabaseUrl
    )
}

if (-not $SkipFullDump) {
    $fullFile = Join-Path $script:BackupDir "database-before-chat-migration.dump"
    Invoke-PgDump -OutputFile $fullFile -Arguments @(
        "--format=custom",
        "--compress=9",
        "--no-owner",
        "--no-acl",
        $DatabaseUrl
    )
}

Write-Host "Supabase backup completed:"
Write-Host "  $((Resolve-Path $script:BackupDir).Path)"
Write-Host ""
Write-Host "Keep this folder until the chat migration has been applied and verified."
