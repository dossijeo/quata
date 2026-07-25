# Compilacion iOS en GitHub Actions

Qüata usa GitHub Actions como host macOS para comprobar la migracion a Compose
Multiplatform desde un equipo Windows. El workflow se encuentra en
`.github/workflows/ios-build.yml`.

## Que valida

1. Compila los source sets `iosArm64` e `iosSimulatorArm64` de todos los
   modulos KMP.
2. Enlaza `QuataShared.framework` para un simulador Apple Silicon.
3. Genera el proyecto Swift mediante XcodeGen.
4. Compila la aplicacion host `QuataIos` con Xcode y sin firma.
5. Ejecuta `QuataIosUITests` en simulador: verifica que Swift presenta la superficie Compose
   exportada por `QuataShared.framework` mediante su identificador de accesibilidad.
6. Conserva los logs, el framework, el proyecto generado, los bundles `.xcresult` y el resumen
   estructurado `xctest-summary.json` durante 30 dias.

La compilacion usa `macos-15`, JDK 17 y Xcode 26.3. Kotlin/Native `2.2.21`
incluye bibliotecas de plataforma generadas para Xcode 26; usar Xcode 16.3
haría fallar el enlace al no resolver `_LocationEssentials`. El workflow fija
además el runtime iOS 26.2 para que el resultado sea reproducible.

## Concurrencia y cola

La Action agrupa sus ejecuciones por ref (`ios-compile-${{ github.ref }}`) y
no cancela una ejecución ya iniciada cuando llega otro push a esa misma rama.
Esto permite que la fase XCTest termine y conserve su diagnóstico. Cada rama
mantiene su propio grupo: una rama no bloquea la compilación de otra.

GitHub limita cada grupo a una ejecución activa y una pendiente; si se acumulan
varios pushes para la misma ref, la ejecución pendiente más antigua puede ser
sustituida por la más reciente. Por tanto, no se habilita paralelismo ilimitado
en runners macOS, pero un lote puede permanecer en cola y consumir más tiempo
de validación. Esta política no corrige cancelaciones o fallos anteriores a
que GitHub inicie los pasos del job.

`QuataShared.framework` es el único framework Kotlin/Native embebido por el
host. Lo produce `:ios-shared`, que concentra las exportaciones de Core, Auth,
Feed, Chat y Notifications. No convierte `feature:feed` en un composition root
ni le añade dependencias hacia features hermanas.

> El proyecto usa Kotlin/Compose Compiler/serialization `2.2.21`, Compose
> Multiplatform `1.10.0`, Gradle 9.3.1 y AGP 9.1.0. La Action conserva esas
> versiones reales para detectar incompatibilidades de toolchain sin ocultarlas
> con una configuración distinta a la utilizada por Android.

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
./gradlew :ios-shared:linkDebugFrameworkIosSimulatorArm64
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
