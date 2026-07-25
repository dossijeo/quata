# Incidente histórico de distribución Kotlin/Wasm (resuelto)

## Síntoma

Antes del lote de actualización, la distribución de producción Web fallaba en la fase de compilación Kotlin/Wasm:

```text
:web:compileProductionExecutableKotlinWasmJs
java.lang.IllegalArgumentException: Function throwLinkageError not found
```

El incidente no está activo. El lote `a0d77ab` actualizó Kotlin, Compose Compiler
y serialization a `2.2.21`, conservó Compose Multiplatform `1.10.0`, generó
`:web:wasmJsBrowserDistribution` y pasó el smoke de navegador. La evidencia y el
alcance exacto se mantienen en [WASM_WEB_VALIDATION.md](WASM_WEB_VALIDATION.md).

## Diagnóstico

En el estado afectado, el proyecto aplicaba Kotlin `2.2.10` y Compose
Multiplatform `1.10.0`. No era un defecto de la UI de Quata ni se solucionaba
con flags de caché o cambios del launcher.

El stack trace y las versiones coinciden con el fallo publicado por JetBrains:

- [CMP-9282: requisitos mínimos de Kotlin](https://youtrack.jetbrains.com/issue/CMP-9282),
  que registra `Function throwLinkageError not found` específicamente para
  Kotlin `2.2.0` y `2.2.10` al compilar Web con Compose Multiplatform 1.10.
- [CMP-8767](https://youtrack.jetbrains.com/issue/CMP-8767), informe anterior
  del mismo ICE en `WasmSymbols`.

CMP-9282 indica que Compose Multiplatform 1.10 para Web requiere al menos
Kotlin `2.2.20` (la discusión original menciona temporalmente `2.2.21`). Por
tanto, no es seguro ocultar este ICE con flags de Gradle, limpieza de caché ni
cambios en el launcher: el backend falla antes de compilar el código de Quata.

## Decisión histórica y criterio vigente

La corrección se realizó como lote global validado también para Android/iOS. La
validación vigente para cambios Web sigue siendo:

```powershell
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
node .\scripts\web-browser-smoke.mjs
.\gradlew.bat :app:assembleDebug --no-daemon
```

No se debe reintroducir una excepción que convierta fallos Wasm en éxito. Un
bundle y smoke no autenticados verdes prueban el host y las rutas básicas, pero
no sustituyen E2E remoto autenticado ni convierten Web/iOS en productos completos.
