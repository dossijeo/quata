# Presupuesto de avisos Kotlin/Wasm

La primera fase mide avisos reales sin convertir el inventario histórico en un fallo de build. El punto de partida es el compilador Kotlin, no una búsqueda de texto: `js(...)` requiere `ExperimentalWasmJsInterop` con Kotlin 2.2.21.

Los opt-ins se declaran únicamente por archivo en adaptadores `wasmJsMain` que contienen interop de navegador. No existe un `freeCompilerArgs` global, una supresión de módulo ni un opt-in en `commonMain`.

## Captura reproducible

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
New-Item -ItemType Directory -Force build\reports | Out-Null
.\gradlew.bat :core:compileKotlinWasmJs --no-daemon --console=plain --warning-mode=all --rerun-tasks `
  2>&1 | Tee-Object build\reports\wasm-warning-budget-core-rerun.log
node .\scripts\wasm-warning-report.mjs `
  --command ':core:compileKotlinWasmJs --no-daemon --console=plain --warning-mode=all --rerun-tasks'
```

El informe conserva el comando si se proporciona `--command`; así una captura de
otro módulo no queda atribuida erróneamente a Core.

La línea base versionada es [wasm-warning-baseline.json](wasm-warning-baseline.json). `experimentalWasmJsInterop` debe mantenerse en cero. Aún no es un gate por defecto: un CI futuro puede opt-in explícitamente tras revisar el cambio.

```powershell
node .\scripts\wasm-warning-report.mjs `
  --command ':core:compileKotlinWasmJs --no-daemon --console=plain --warning-mode=all --rerun-tasks' `
  --baseline .\docs\wasm-warning-baseline.json `
  --max-new-opt-ins 0
```

Los avisos Beta de `expect`/`actual` se conservan en el informe para seguimiento; no se silencian con `-Xexpect-actual-classes` durante esta fase porque eso no sería una corrección localizada. Las deprecaciones se clasifican por separado; sólo se corrigen si su reemplazo es semánticamente equivalente.

## Inventario inicial

La captura inicial de `:core:compileKotlinWasmJs --rerun-tasks` encontró 46 avisos de interop repartidos entre 16 adaptadores de `core/wasmJsMain`; el origen era cada llamada `js(...)`, no `commonMain`. Tras el opt-in por archivo, la misma compilación dejó **0** avisos `ExperimentalWasmJsInterop`, 20 avisos Beta de `expect`/`actual` y 0 deprecaciones. `:web:compileKotlinWasmJs` también terminó con 0 avisos de interop.

Una captura Web previa expuso dos deprecaciones: `Icons.Filled.Chat` se sustituyó por el equivalente semántico `Icons.AutoMirrored.Filled.Chat`; `rememberSwipeToDismissBoxState(confirmValueChange = ...)` requiere rediseñar anclas y queda fuera de esta fase para evitar cambiar el comportamiento de notificaciones sólo por silenciar un aviso.
