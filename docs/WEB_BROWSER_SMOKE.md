# Smoke de navegador para Quata Web

Este smoke carga la distribución real de Compose/Wasm en Chrome headless y comprueba que el shell
monta, sin excepciones no capturadas, en las rutas `#auth`, `#feed`, `#chat`, `#official`,
`#settings` y `#share-target`.

No usa Supabase ni crea una sesión falsa: tras verificar `#auth`, sólo activa el indicador local de
shell para recorrer las rutas protegidas. Los repositorios permanecen sin token y sin configuración
de backend; por tanto es una prueba de arranque/routing, no una prueba de integración remota.

## Ejecución

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
node .\scripts\web-browser-smoke.mjs
```

El script sirve temporalmente `web/build/dist/wasmJs/productionExecutable`, arranca un perfil de
Chrome descartable y lo elimina al finalizar. No instala paquetes npm ni deja un servidor en
segundo plano. En Node 20 activa internamente el flag experimental estándar de `WebSocket` que
necesita para CDP. Si Chrome no está en la ubicación estándar, se puede indicar explícitamente:

```powershell
node .\scripts\web-browser-smoke.mjs --chrome 'C:\ruta\a\chrome.exe'
```

También puede probarse otra distribución ya construida:

```powershell
node .\scripts\web-browser-smoke.mjs --dist 'C:\ruta\a\distribution'
```

## Línea base de rendimiento reproducible

El mismo smoke emite una muestra de arranque y navegación de las seis rutas. El lanzador de
repetibilidad ejecuta exactamente tres muestras con perfiles Chrome descartables y caché fría,
sobre la misma distribución, y conserva cada JSON junto al SHA. La serie exige identidades de
muestra distintas y una misma revisión, distribución, Chrome y entorno; los valores de máquinas
distintas son telemetría informativa, no un umbral de aprobación.

```powershell
node .\scripts\web-performance-repeatability.mjs `
  --dist web/build/dist/wasmJs/productionExecutable `
  --chrome 'C:\ruta\a\chrome.exe' `
  --metrics-dir build-reports\web\repeatability `
  --out build-reports\web\repeatability.json
```

El contrato determinista es el smoke: las seis rutas deben montar sin excepciones, errores HTTP ni
acceso externo. El `<script>` incondicional de Turnstile se satisface con un stub local porque el
registro permanece sin configurar; cualquier otro origen externo se bloquea y hace fallar el
smoke. La telemetría registra `mountElapsedMs` y heap por ruta; DOM/load se atribuye sólo
a `#auth`, la única navegación de documento completo, y no se repite engañosamente para los cambios
de hash. Para comparar revisiones el resumen calcula mediana y p95 con las tres muestras de perfil
frío del mismo runner. El contrato de métricas acepta tres o más `--report PATH` sólo si SHA,
distribución, Chrome y entorno coinciden; menos de tres muestras falla por serie insuficiente. No
existe un umbral temporal o de memoria en este contrato:
adoptarlo exige aprobar por separado una línea base del runner y su tolerancia.

## DocMentis sin credenciales de proveedor

`node .\scripts\web-browser-smoke.mjs --docmentis` llama al `DocumentOpenService` instalado por la
composición Web con un documento sin extensión y MIME `application/pdf`. Así atraviesa
`WebDocmentisDocumentOpenService.open`, la política de admisión, el modal real y el fallback del
producto; no importa el SDK desde un camino de test paralelo. El smoke exige que el overlay se
monte y elimine, que la apertura del SDK cierre por falta de permit y que el fallback del navegador
reciba exactamente la URL local. No se descarga ni abre un documento en el puesto de trabajo.

El SDK exige que cada apertura gratuita obtenga un permit de corta duración firmado por DocMentis
y verifica la firma dentro de Wasm. El repositorio no contiene ni simula esa clave privada. El smoke
satisface localmente sólo el `OPTIONS` exacto de
`https://www.docmentis.com/api/udoc-viewer/permit`, valida que el `POST` contiene exclusivamente
`distinct_id`, `host`, `nonce` y `viewer_version`, y corta ese `POST` para probar de forma
determinista la ruta no disponible. No se fabrica un permit ni se permite tráfico al proveedor.

`scripts/web-docmentis-product-smoke-contract.test.mjs` impide volver a un falso positivo basado
sólo en montar el SDK: exige la llamada al servicio de composición, una entrada admitida por MIME
sin extensión hardcoded, el ciclo completo del overlay, el permit exacto y un único fallback.

La política de red tiene pruebas anti-bypass en
`scripts/web-browser-network-policy.test.mjs`: método, origen, path y query de Turnstile están
fijados al único bootstrap `GET .../api.js?render=explicit`; DocMentis sólo reconoce el preflight
`OPTIONS` y el `POST` del endpoint de permit; cualquier variante u otro origen sigue siendo una
solicitud inesperada y hace fallar el smoke. Un render real sin conexión requiere una licencia
comercial válida inyectada fuera del repositorio, o un permit auténtico del proveedor.

El resumen rechaza un informe si el smoke falló, falta una de las seis rutas o no identifica el
artefacto. Cada smoke genera un `sampleId` UUID v4 y un `generatedAt` ISO; una serie rechaza IDs
o tiempos duplicados, tiempos futuros más de cinco minutos y huellas canónicas de medición
idénticas aunque cambien UUID, timestamp o ruta local. Esta huella no está criptográficamente
atestada: sólo dificulta duplicación accidental o manipulación trivial y no autoriza usar la
telemetría como SLO ni como release gate. Aun así acepta cualquier cifra finita no negativa:
evita convertir telemetría local en
un SLO falso. El JSON incluye Chrome, Node, SO, CPU/memoria, SHA del repositorio y SHA-256 de toda
la distribución, sin guardar rutas absolutas, hostname, tokens ni secretos.
