[CmdletBinding()]
param(
    [string]$OutputDirectory = "build-reports/multiplatform-metrics",
    [string]$BaselinePath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# This report intentionally only scans Quata-owned source roots. In particular it excludes
# :document-reader (a vendored reader), generated build outputs, models and node_modules so a
# third-party binary/source drop cannot make the migration look more complete than it is.
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ownedRoots = @("app", "core", "designsystem", "feature", "ios-shared", "iosApp", "web") |
    ForEach-Object { Join-Path $projectRoot $_ } |
    Where-Object { Test-Path $_ }
$trackedSourceSets = @("commonMain", "androidMain", "wasmJsMain", "iosMain")
$sourceExtensions = @(".kt")

function Get-RelativePath([string]$Path) {
    # Windows PowerShell 5.1 runs on .NET Framework and has no Path.GetRelativePath.
    $rootUri = New-Object Uri(($projectRoot.TrimEnd("\", "/") + [IO.Path]::DirectorySeparatorChar))
    $pathUri = New-Object Uri($Path)
    return [Uri]::UnescapeDataString($rootUri.MakeRelativeUri($pathUri).ToString()).Replace("\", "/")
}

function Get-SourceSetFiles([string]$SourceSet) {
    $result = @()
    foreach ($root in $ownedRoots) {
        $result += Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Extension -in $sourceExtensions -and
                $_.FullName -match ([regex]::Escape("\src\$SourceSet\") + "|" + [regex]::Escape("/src/$SourceSet/"))
            }
    }
    return @($result | Sort-Object FullName -Unique)
}

function Get-PhysicalLineCount([IO.FileInfo]$File) {
    $content = [IO.File]::ReadAllText($File.FullName)
    if ($content.Length -eq 0) { return 0 }
    return ([regex]::Matches($content, "\r\n|\n|\r").Count + 1)
}

function New-FileMetric([IO.FileInfo]$File) {
    return [pscustomobject]@{
        path = Get-RelativePath $File.FullName
        lines = Get-PhysicalLineCount $File
    }
}

$sourceSetMetrics = [ordered]@{}
$allMainFiles = @()
foreach ($sourceSet in $trackedSourceSets) {
    $files = Get-SourceSetFiles $sourceSet
    $fileMetrics = @($files | ForEach-Object { New-FileMetric $_ })
    $sourceSetMetrics[$sourceSet] = [ordered]@{
        files = $fileMetrics.Count
        lines = @($fileMetrics | Measure-Object -Property lines -Sum).Sum
        fileMetrics = $fileMetrics
    }
    $allMainFiles += $files
}

$androidImports = @()
foreach ($file in (Get-SourceSetFiles "commonMain")) {
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadLines($file.FullName)) {
        $lineNumber++
        if ($line -match '^\s*import\s+android(?:\.|\s|$)') {
            $androidImports += [ordered]@{
                path = Get-RelativePath $file.FullName
                line = $lineNumber
                import = $line.Trim()
            }
        }
    }
}

$jsTargets = @()
foreach ($root in $ownedRoots) {
    foreach ($buildFile in (Get-ChildItem -LiteralPath $root -Filter "build.gradle.kts" -File -Recurse -ErrorAction SilentlyContinue)) {
        $lineNumber = 0
        foreach ($line in [IO.File]::ReadLines($buildFile.FullName)) {
            $lineNumber++
            if ($line -match '^\s*(js\s*\(|wasmJs\s*\()') {
                $jsTargets += [ordered]@{
                    path = Get-RelativePath $buildFile.FullName
                    line = $lineNumber
                    target = if ($Matches[1] -match '^\s*wasmJs') { "wasmJs" } else { "js" }
                    declaration = $line.Trim()
                }
            }
        }
    }
}
$jsTargets = @($jsTargets | Sort-Object path, line)

# These expressions identify deliberately explicit capability gaps, not every generic occurrence
# of "unsupported" in prose. Entries matching the exclusions are retained separately so future
# audits can see precisely why they did not count as product operations.
$operationPattern = '(?i)(not[_ -]?implemented|UnsupportedOperationException|throw\s+Unsupported|TODO\s*\()'
$operationExclusions = @(
    [ordered]@{ pattern = '(^|/)src/(commonTest|androidTest|iosTest|wasmJsTest)/'; reason = 'test source is not a runtime operation' },
    [ordered]@{ pattern = '(^|/)build/'; reason = 'generated output is excluded' },
    [ordered]@{ pattern = '(^|/)document-reader/'; reason = 'vendored reader is excluded from Quata migration metrics' }
)
$operations = @()
$excludedOperations = @()
foreach ($file in ($allMainFiles | Sort-Object FullName -Unique)) {
    $relative = Get-RelativePath $file.FullName
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadLines($file.FullName)) {
        $lineNumber++
        if ($line -match $operationPattern) {
            $entry = [ordered]@{ path = $relative; line = $lineNumber; code = $line.Trim() }
            $exclusion = $operationExclusions | Where-Object { $relative -match $_.pattern } | Select-Object -First 1
            if ($null -eq $exclusion) { $operations += $entry }
            else {
                $excludedOperations += [pscustomobject]@{
                    path = $entry.path
                    line = $entry.line
                    code = $entry.code
                    exclusion = $exclusion.reason
                }
            }
        }
    }
}

$hostPatterns = '(?i)(Capability.*Manifest|FeatureCapability|Host\.kt$|RuntimeBootstrap|QuataIosApp\.swift$|Main\.kt$)'
$hostFiles = @()
foreach ($root in $ownedRoots) {
    $hostFiles += Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -in @(".kt", ".swift") -and (Get-RelativePath $_.FullName) -match $hostPatterns } |
        ForEach-Object {
            $relative = Get-RelativePath $_.FullName
            [pscustomobject]@{
                path = $relative
                kind = if ($relative -match 'Capability') { "capability" } elseif ($relative -match 'Bootstrap') { "bootstrap" } elseif ($relative -match 'Host\.kt$') { "host" } else { "entrypoint" }
            }
        }
}
$hostFiles = @($hostFiles | Sort-Object path -Unique)

$baseline = $null
if ($BaselinePath) {
    $resolvedBaseline = Resolve-Path -LiteralPath $BaselinePath
    $baseline = Get-Content -LiteralPath $resolvedBaseline -Raw | ConvertFrom-Json
}

$outputPath = if ([IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory } else { Join-Path $projectRoot $OutputDirectory }
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

$report = [ordered]@{
    schemaVersion = 1
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    projectRoot = $projectRoot
    scope = [ordered]@{
        ownedRoots = @($ownedRoots | ForEach-Object { Get-RelativePath $_ })
        sourceExtensions = $sourceExtensions
        excludedRoots = @("document-reader", "**/build", "node_modules", ".git", ".gradle", ".kotlin", "vosk_model_*")
        note = "No source text, environment variables, credentials, tokens, or generated inventories are emitted."
    }
    sourceSets = $sourceSetMetrics
    androidImportsInCommonMain = [ordered]@{ count = $androidImports.Count; matches = $androidImports }
    jsTargetDeclarations = [ordered]@{ count = $jsTargets.Count; declarations = $jsTargets }
    explicitUnavailableOperations = [ordered]@{
        pattern = $operationPattern
        count = $operations.Count
        matches = $operations
        exclusions = $operationExclusions
        excludedMatches = $excludedOperations
    }
    capabilityManifestsAndHosts = [ordered]@{ count = $hostFiles.Count; files = $hostFiles }
    baseline = if ($null -eq $baseline) { $null } else { [ordered]@{ path = $resolvedBaseline.Path; schemaVersion = $baseline.schemaVersion } }
}

$jsonPath = Join-Path $outputPath "multiplatform-metrics.json"
$markdownPath = Join-Path $outputPath "multiplatform-metrics.md"
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$markdown = @(
    "# Métricas de migración multiplataforma",
    "",
    "Generado: $($report.generatedAtUtc)",
    "",
    "## Source sets propios",
    "",
    "| Source set | Archivos Kotlin | Líneas físicas |",
    "| --- | ---: | ---: |"
)
foreach ($sourceSet in $trackedSourceSets) {
    $metric = $sourceSetMetrics[$sourceSet]
    $markdown += "| $sourceSet | $($metric.files) | $($metric.lines) |"
}
$markdown += @(
    "",
    "## Señales de arquitectura",
    "",
    "- Imports Android en ``commonMain``: $($androidImports.Count)",
    "- Declaraciones de targets JS/IR o Wasm: $($jsTargets.Count)",
    "- Operaciones explícitamente no implementadas/no soportadas: $($operations.Count)",
    "- Manifests de capacidad y hosts detectados: $($hostFiles.Count)",
    "",
    "El JSON contiene las rutas y líneas exactas. Las exclusiones aplicadas se documentan en ``explicitUnavailableOperations.exclusions``. Este informe no mide completitud funcional: debe interpretarse junto al tablero y la evidencia E2E.",
    "",
    "## Alcance",
    "",
    "No cuenta ``document-reader``, directorios ``build``, modelos, dependencias ni fuentes de terceros. No escribe ``MULTIPLATFORM_INVENTORY.md`` ni contiene secretos."
)
$markdown -join [Environment]::NewLine | Set-Content -LiteralPath $markdownPath -Encoding utf8

Write-Host "Generated $jsonPath"
Write-Host "Generated $markdownPath"
