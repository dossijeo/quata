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
node .\scripts\web-browser-smoke.mjs --docmentis --metrics-report .\build\reports\web-browser-smoke-metrics.json
node .\scripts\web-browser-metrics.mjs --report .\build\reports\web-browser-smoke-metrics.json
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

## Presupuesto revisable de regresiones

Por defecto el script **nunca falla por tamano**. Esto permite crear un baseline
real sobre una distribucion finalizada sin transformar un bloqueo local de
`wasm-opt` en un falso fallo de budget. El presupuesto propuesto y versionado es
[wasm-bundle-budget.json](wasm-bundle-budget.json): tolera hasta 1 MiB sin
comprimir y 256 KiB gzip por encima del baseline. Es una tolerancia de
regresion, no un limite total y no autoriza retirar DocMentis ni reducir la
optimizacion.

Los tamanos 35.29 MiB/13.55 MiB anotados en el tablero son redondeados. El
candidato versionado [wasm-bundle-baseline.json](wasm-bundle-baseline.json)
contiene en cambio el inventario y hashes exactos del artefacto local observado.
Su procedencia de SHA aun no esta certificada y por ello el presupuesto se
mantiene en `proposed`: ejecutarlo compara e informa, pero no bloquea. El
integrador que produzca una distribucion completa en el SHA que vaya a integrar
debe regenerar el candidato, revisar el diff y, en un commit separado, cambiar
`state` de `proposed` a `approved`:

```powershell
node .\scripts\wasm-bundle-report.mjs `
  --write-baseline .\docs\wasm-bundle-baseline.json
# Revisar el JSON y aprobar el cambio de state en docs/wasm-bundle-budget.json.
node .\scripts\wasm-bundle-report.mjs `
  --budget .\docs\wasm-bundle-budget.json
```

El segundo comando es el **gate opt-in** previsto para CI. Con estado `proposed`
emite la comparacion sin bloquear; con `approved` rechaza solo el crecimiento
sobre ese baseline y un baseline ausente. No lee warnings del compilador ni
impone un maximo total. El baseline incluye hashes, tamanos, distribucion y la
presencia efectiva de chunks DocMentis. Si un cambio deliberado supera el margen,
se revisa y actualiza baseline/presupuesto en un commit documentado, nunca se
desactiva el gate silenciosamente.

## Arranque y memoria de navegador

`web-browser-smoke.mjs --metrics-report` usa un perfil Chrome desechable y
abre inicialmente `about:blank`, por lo que la navegacion `auth` representa un
arranque frio del launcher. Para cada ruta guarda tiempo observado hasta que el
shell Compose monta, tiempos Navigation Timing y las metricas CDP disponibles
de heap/proceso. El resumen es legible con `web-browser-metrics.mjs`.

La memoria es la reportada por Chrome/CDP, no RSS del sistema; puede ser nula
en versiones que no expongan la metrica. Los resultados dependen de CPU, GPU,
Chrome y cache de la maquina. Por ello se conservan como evidencia de cada SHA
y todavia no bloquean CI. Un presupuesto de rendimiento solo sera valido tras
repeticiones en runner controlado, con version de Chrome y hardware fijados.

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
