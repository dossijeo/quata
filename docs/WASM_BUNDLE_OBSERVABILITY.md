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
baseline aprobado corresponde al checkout limpio y detached de
`origin/main` en `1801626553022101a66e59ce61f2e7fe3eb81055`: 37.066.472 bytes
raw y 14.227.219 bytes gzip. La captura debe ejecutarse desde ese tipo de
worktree (o de un tag confiable), nunca desde el commit elegido por la rama de
aprobacion:

```powershell
node .\scripts\wasm-bundle-report.mjs `
  --write-baseline .\docs\wasm-bundle-baseline.json `
  --trusted-ref origin/main
$baseSha = '<sha-base-del-pr>'
node .\scripts\wasm-bundle-report.mjs `
  --budget .\docs\wasm-bundle-budget.json `
  --policy-base $baseSha
```

La captura falla si `HEAD` no coincide con la referencia confiable, si el
checkout esta en una rama o si hay cambios tracked/untracked. Produce siempre
`baselineState: candidate`; elevarlo a `approved` y aprobar el budget exige una
PR posterior dedicada, sin payload ni cambios de runtime/build/gate. El segundo
comando es el **gate aprobado** para CI. Rechaza un baseline sin
`baselineState: approved`, sin SHA de revision, ausente, o el crecimiento sobre
sus margenes. No lee warnings del compilador ni impone un maximo total. El
baseline incluye hashes, tamanos, distribucion y la presencia efectiva de chunks
DocMentis. Ademas, el baseline conserva una atestacion reproducible: SHA-256
del arbol Git completo del SHA indicado y SHA-256 del inventario canonico de
assets (ruta, hash, tamanos y clasificacion). El gate recalcula ambos y tambien
los totales desde los assets antes de comparar tamanos; rechaza SHA invalido,
arbol distinto, inventario/totales manipulados o baseline ausente. No contiene
secretos ni depende de un token de GitHub. Nunca se desactiva el gate
silenciosamente.

En una PR que modifica baseline o budget aprobados, CI pasa el SHA base mediante
`--policy-base`. El gate exige que `capture.sourceRevision` sea exactamente ese
SHA y que el inventario de la distribucion construida por la PR coincida con el
baseline. Rechaza la aprobacion si el mismo diff toca `web`, `core`,
`designsystem`, `feature`, Gradle, lockfiles/package, build logic o el propio
gate. Una PR ordinaria de payload conserva la comparacion de crecimiento, pero
no puede autoaprobar un baseline nuevo.

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
