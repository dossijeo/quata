# Compilacion iOS en GitHub Actions

Qüata usa GitHub Actions como host macOS para comprobar la migracion a Compose
Multiplatform desde un equipo Windows. El workflow se encuentra en
`.github/workflows/ios-build.yml`.

## Que valida

1. Compila los source sets `iosArm64` e `iosSimulatorArm64` de todos los
   modulos KMP.
2. Enlaza `QuataFeed.framework` para un simulador Apple Silicon.
3. Genera el proyecto Swift mediante XcodeGen.
4. Compila la aplicacion host `QuataIos` con Xcode y sin firma.
5. Conserva los logs, el framework, el proyecto generado y
   `QuataIos.xcresult` durante 30 dias.

La compilacion usa `macos-15`, JDK 17 y Xcode 16.3. Esto evita que una
actualizacion silenciosa de Xcode cambie el resultado de la migracion.

> Kotlin `2.2.10` declara compatibilidad con Xcode 16.3, Gradle hasta 8.14
> y AGP hasta 8.10. El proyecto usa actualmente Gradle 9.3.1 y AGP 9.1.0.
> La Action conserva esas versiones reales para detectar si la diferencia
> ya supone un bloqueo, en vez de ocultarla con una toolchain distinta a la
> utilizada por Android.

## Lanzar y descargar el informe desde PowerShell

Requisitos:

- GitHub CLI (`gh`) instalado.
- Una sesion valida de `gh auth login`.
- La rama que se quiere compilar subida a GitHub.

Desde la raiz del repositorio:

```powershell
.\scripts\run-ios-ci.ps1
```

Para compilar otra rama o commit:

```powershell
.\scripts\run-ios-ci.ps1 -Ref codex/mi-rama
```

El script dispara la Action, espera hasta que termine, imprime los pasos
fallidos y descarga el artefacto en `build-reports/ios/<run-id>`.

## Operativa equivalente con GitHub CLI

```powershell
gh workflow run ios-build.yml --ref NOMBRE_DE_RAMA
gh run list --workflow ios-build.yml --branch NOMBRE_DE_RAMA --limit 10
gh run watch RUN_ID --exit-status
gh run view RUN_ID --log-failed
gh run download RUN_ID --name ios-build-report-RUN_ID --dir build-reports/ios/RUN_ID
```

Para obtener un resumen mecanizable:

```powershell
gh run view RUN_ID --json status,conclusion,url,jobs | ConvertFrom-Json
```

## Leer el resultado de Xcode

En macOS, el bundle estructurado puede inspeccionarse con Xcode:

```bash
open build-reports/ios/RUN_ID/QuataIos.xcresult
```

O convertirse a JSON para otro proceso:

```bash
xcrun xcresulttool get \
  --legacy \
  --path build-reports/ios/RUN_ID/QuataIos.xcresult \
  --format json > xcresult.json
```

Los errores de Kotlin se encuentran en `kotlin-ios.log`, los del enlace del
framework en `framework-link.log` y los de Swift/Xcode en `xcodebuild.log`.

## Compilacion local en un Mac

```bash
./gradlew compileKotlinIosArm64 compileKotlinIosSimulatorArm64
./gradlew :feature:feed:linkDebugFrameworkIosSimulatorArm64
brew install xcodegen
cd iosApp
xcodegen generate
cd ..
xcodebuild \
  -project iosApp/QuataIos.xcodeproj \
  -scheme QuataIos \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  ARCHS=arm64 \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=NO \
  build
```

La Action compila, pero no firma ni publica una aplicacion. Para generar un
IPA distribuible sera necesario configurar el equipo de Apple Developer,
certificados y perfiles de aprovisionamiento como una fase independiente.
