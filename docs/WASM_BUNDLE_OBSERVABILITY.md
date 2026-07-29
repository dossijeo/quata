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
fichero [wasm-bundle-baseline.json](wasm-bundle-baseline.json) sigue siendo un
candidato historico y no es aprobable desde esta rama. Despues de integrar
CI-001, la captura canónica se ejecuta manualmente en GitHub Actions mediante
`Canonical Linux Wasm baseline capture`, exclusivamente cuando el evento es
`refs/heads/main` y su SHA sigue siendo exactamente el `origin/main` recién
obtenido. El runner Ubuntu 24.04 hace checkout detached, exige un árbol limpio
y publica un candidato y su informe como artifact; nunca escribe `docs/` ni
aprueba un baseline. Windows es solo diagnóstico local de `wasm-opt`, no una
fuente canónica de baseline.

Para una inspección local excepcional, la captura se hace desde un worktree
limpio y detached del `origin/main` actualizado, nunca desde el commit elegido
por la rama de aprobacion:

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
`baselineState: candidate`; cambiarlo a `approved` y aprobar el budget exige una
PR posterior dedicada. El segundo comando es el **gate aprobado** para CI. Rechaza un baseline sin
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
gate. La PR de aprobacion solo puede modificar exactamente
`docs/wasm-bundle-baseline.json` y/o `docs/wasm-bundle-budget.json`; incluso la
documentacion narrativa se revisa en una PR separada. Desde un presupuesto
propuesto, solo se admite el cambio de `state` a `approved`; una vez aprobado,
el presupuesto debe permanecer semanticamente identico. Una PR ordinaria de
payload conserva la comparacion de crecimiento, pero no puede autoaprobar un
baseline nuevo.

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
crea el lanzador PowerShell suspendido, lo asigna a un Windows Job Object con
`KILL_ON_JOB_CLOSE` y solo entonces lo reanuda. Gradle, JVM, Node y cualquier
descendiente heredan esa pertenencia antes de poder ejecutar. El script conserva
stdout, stderr, CPU, memoria y artefactos en
`gradle-production-observation.json`, y detiene **solo el Job Object de ese
diagnostico** por inactividad o limite explicito. El polling de procesos aporta
metricas; no decide ownership ni autoriza terminaciones por PID.

El cierre del Job Object se ejecuta en `finally` en salida correcta, fallo del
comando, timeout, cancelacion y error del propio watchdog. Si Windows devuelve
estado desconocido, `AccessDenied`, falla una consulta nativa o no confirma que
el Job Object quedo vacio, el resultado falla de forma cerrada y no certifica
el bundle. `KILL_ON_JOB_CLOSE` queda como backstop del kernel si el cleanup
explicito se interrumpe.
No se debe cambiar `wasmJs {}` para desactivar optimizacion o bajar la
toolchain.

Antes de cambiar ese watchdog se puede ejecutar su contrato local, sin lanzar
Gradle ni modificar el bundle:

```powershell
.\scripts\run-wasm-production-observed.ps1 -ContractTest
```

El contrato ejecuta rutas reproducibles de exito, fallo del comando, timeout,
cancelacion, error interno y estado `unknown/AccessDenied`. Cada ruta crea un
hijo duradero y exige que el Job Object quede vacio. Tambien mantiene un proceso
ajeno durante toda la suite y presenta una identidad PID/hora obsoleta: ambos
sobreviven porque el cleanup solo opera sobre pertenencia kernel al Job Object,
no sobre PPID, PID observado o una identidad reconstruida despues.

Las negativas nativas usan seams deterministas del interop embebido. Una fuerza
`AssignProcessToJobObject` a devolver el equivalente a Win32 error 5 despues de
crear los handles de Job, proceso suspendido e hilo; el contrato exige una sola
clausura por handle y confirma `TerminateProcess` mas espera del proceso
suspendido. Otra fuerza error 5 en `QueryInformationJobObject` y exige resultado
fail-closed, `TerminateJobObject`, Job vacio y una sola clausura por handle. Es
evidencia de esas ramas de cleanup, no una afirmacion de haber reproducido en
esta maquina todas las politicas posibles de nested Jobs de un runner externo.

La ruta de cancelacion no usa un booleano simulado: inicia el script en un
runspace, espera un hijo real y llama a `PowerShell.Stop()`. El runspace debe
terminar con `PipelineStoppedException`, mientras un marcador escrito por el
mismo `finally` de produccion confirma Job vacio y el hijo deja de existir. La
identidad PID/hora obsoleta se pasa al cleanup productivo como hint diagnostico;
el contrato confirma que fue recibida e ignorada y que el proceso ajeno sigue
vivo.

La validacion de este cambio se realizo con Windows PowerShell 5.1. PowerShell 7
no estaba instalado en el entorno de validacion, por lo que no se afirma aqui
que esa edicion haya sido ejecutada. El script conserva sintaxis compatible con
5.1 y rechaza plataformas no Windows antes de intentar el interop; una futura
afirmacion sobre PowerShell 7 requiere ejecutar este mismo contrato con
`pwsh.exe`, sin instalar herramientas globales como parte del gate.

Al registrar una incidencia, separar estos hitos del log:

1. `compileProductionExecutableKotlinWasmJs` (compilador Kotlin),
2. `wasmJsBrowserProductionWebpack` (webpack/chunks),
3. cualquier linea `wasm-opt` / tarea posterior de distribucion,
4. presencia y fecha de `web/build/dist/wasmJs/productionExecutable`.

Adjuntar `gradle --version`, versiones Kotlin/Compose del build raiz, CPU,
memoria pico, tiempo por hito y el informe de assets solo cuando el directorio
de distribucion exista completo.
