# Observabilidad del bundle Kotlin/Wasm

`wasmJsBrowserDistribution` es el gate de produccion: termina en la cadena de
Kotlin/Wasm, webpack y `wasm-opt`. No se considera Web verde hasta que exista el
directorio de distribucion y pase el smoke del navegador. Esta guia separa una
compilacion lenta de una distribucion realmente producida, sin reducir la
optimizacion de salida.

## Captura reproducible

En PowerShell con el JBR de Android Studio:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\scripts\run-wasm-production-observed.ps1 -NoProgressMinutes 10 -MaximumMinutes 20
node .\scripts\wasm-bundle-report.mjs
node .\scripts\web-browser-smoke.mjs --docmentis
```

El informe se guarda en
`build/reports/wasm-bundle/wasm-bundle-report.json`, incluye SHA-256, tamano y
tamano gzip de cada asset, y agrupa candidatos conocidos (Kotlin/Wasm, Skiko,
iconos y DocMentis). El grupo es una clasificacion heuristica de nombres y
marcadores de los assets emitidos, no una atribucion exacta de linker: si
webpack ofusca un chunk hay que
usar el listado de assets y el stats de webpack antes de adjudicar su peso.

DocMentis se importa dinamicamente. El tamano de su paquete npm (observado
antes de empaquetar como aproximadamente 18.2 MiB) no equivale al peso servido:
la referencia operativa es su chunk emitido y su tamano gzip en este informe.

## Presupuesto gradual

Por defecto el script **nunca falla por tamano**. Esto permite crear un baseline
real sobre una distribucion finalizada sin transformar un bloqueo local de
`wasm-opt` en un falso fallo de budget. Tras revisar un JSON versionado, un job
de CI puede fijar de forma explicita uno de estos gates:

```powershell
node .\scripts\wasm-bundle-report.mjs --baseline .\docs\wasm-bundle-baseline.json --max-growth-bytes 262144
node .\scripts\wasm-bundle-report.mjs --max-total-bytes 52428800
```

No se ha activado un umbral global todavia: no existe baseline de una
distribucion finalizada del SHA actual. El primer baseline debe anotar SHA,
fecha, plataforma, comando y si DocMentis estaba incluido; el umbral debe ser
de regresion, nunca una excusa para retirar el visor o degradar `wasm-opt`.

## Diagnostico de `wasm-opt`

Kotlin/Compose invoca `wasm-opt` internamente durante la distribucion. No hay
un timeout Gradle estable y soportado que permita interrumpir exclusivamente
esa herramienta sin matar el build entero. `run-wasm-production-observed.ps1`
es el workaround seguro: sigue el arbol Gradle/JVM/Node, conserva stdout,
stderr, CPU, memoria y artefactos en `gradle-production-observation.json`, y
detiene **solo el proceso de diagnostico** por inactividad o limite explicito.
No se debe cambiar `wasmJs {}` para desactivar optimizacion o bajar la
toolchain.

Al registrar una incidencia, separar estos hitos del log:

1. `compileProductionExecutableKotlinWasmJs` (compilador Kotlin),
2. `wasmJsBrowserProductionWebpack` (webpack/chunks),
3. cualquier linea `wasm-opt` / tarea posterior de distribucion,
4. presencia y fecha de `web/build/dist/wasmJs/productionExecutable`.

Adjuntar `gradle --version`, versiones Kotlin/Compose del build raiz, CPU,
memoria pico, tiempo por hito y el informe de assets solo cuando el directorio
de distribucion exista completo.
