param(
    [switch]$Push
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$source = Join-Path $repositoryRoot 'docs\wiki'
$remote = 'https://github.com/dossijeo/quata.wiki.git'
$pages = @(
    'Home.md',
    '01-Producto-y-alcance.md',
    '02-Arquitectura-multiplataforma.md',
    '03-Plataformas-y-capacidades.md',
    '04-Backend-y-datos.md',
    '05-Navegacion-y-autenticacion.md',
    '06-Migracion-multiplataforma.md',
    '07-Desarrollo-y-builds.md',
    '08-Pruebas-CI-y-evidencia.md',
    '09-Seguridad-y-releases.md',
    '10-Estado-del-proyecto.md',
    '11-Glosario.md',
    '12-Mapa-de-documentacion.md',
    '_Sidebar.md',
    '_Footer.md'
)

foreach ($page in $pages) {
    $path = Join-Path $source $page
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Falta la pagina de Wiki declarada: $path"
    }
}

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("quata-wiki-sync-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporaryRoot | Out-Null

try {
    & git clone --quiet $remote $temporaryRoot
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo clonar la GitHub Wiki. Inicializa primero la pagina Home.' }

    foreach ($page in $pages) {
        Copy-Item -LiteralPath (Join-Path $source $page) -Destination (Join-Path $temporaryRoot $page) -Force
    }

    & git -C $temporaryRoot diff --check
    if ($LASTEXITCODE -ne 0) { throw 'La Wiki contiene errores de whitespace.' }

    $status = & git -C $temporaryRoot status --short
    if (-not $status) {
        Write-Output 'La GitHub Wiki ya esta sincronizada.'
        exit 0
    }

    Write-Output $status
    & git -C $temporaryRoot diff --stat

    if (-not $Push) {
        Write-Output 'Revision completada. Usa -Push para publicar estos cambios.'
        exit 0
    }

    & git -C $temporaryRoot add -- $pages
    & git -C $temporaryRoot commit -m 'docs: actualizar Wiki tecnica en espanol'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el commit de la Wiki.' }
    & git -C $temporaryRoot push origin HEAD
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo publicar la Wiki.' }
    Write-Output 'GitHub Wiki publicada correctamente.'
}
finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        $resolvedTemporaryRoot = (Resolve-Path -LiteralPath $temporaryRoot).Path
        $resolvedSystemTemp = (Resolve-Path -LiteralPath ([System.IO.Path]::GetTempPath())).Path.TrimEnd('\')
        if (-not $resolvedTemporaryRoot.StartsWith($resolvedSystemTemp + '\', [System.StringComparison]::OrdinalIgnoreCase) -or
            -not ([System.IO.Path]::GetFileName($resolvedTemporaryRoot)).StartsWith('quata-wiki-sync-', [System.StringComparison]::Ordinal)) {
            throw "Se rechazo limpiar un directorio temporal inesperado: $resolvedTemporaryRoot"
        }
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
