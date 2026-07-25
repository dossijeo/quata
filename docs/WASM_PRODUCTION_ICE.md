# Límite temporal de distribución Kotlin/Wasm

## Síntoma

La distribución de producción Web falla en la fase de compilación Kotlin/Wasm:

```text
:web:compileProductionExecutableKotlinWasmJs
java.lang.IllegalArgumentException: Function throwLinkageError not found
```

En consecuencia, `:web:wasmJsBrowserDistribution` y el smoke de navegador que
consume `web/build/dist/wasmJs/productionExecutable` no son actualmente gates
verdes. No deben comunicarse como validados.

## Diagnóstico

El proyecto aplica Kotlin `2.2.10` y Compose Multiplatform `1.10.0` en el
build raíz. No hay una dependencia declarada de `kotlinx-browser`, ni ajustes
de incrementalidad o de enlace Wasm que sustituyan la librería estándar del
compilador.

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

## Decisión actual

No se modifica la versión global de Kotlin en esta rama: afectaría Android,
iOS y todos los módulos KMP y requiere un lote de actualización y validación
propio. Mientras tanto, la compilación Kotlin/Wasm de desarrollo que no llegue
a este ICE conserva su papel de comprobación incremental, pero una distribución
de producción Web no se considera entregada.

La corrección real debe ser un lote explícito de toolchain que eleve Kotlin al
mínimo compatible y valide, como mínimo:

```powershell
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
node .\scripts\web-browser-smoke.mjs
.\gradlew.bat :app:assembleDebug --no-daemon
```

No se debe añadir una excepción que convierta este fallo en éxito ni publicar
el artefacto Web hasta que esos comandos terminen correctamente.
