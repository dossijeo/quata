# Repetibilidad de métricas Web

La evidencia de rendimiento Web ejecuta cinco arranques de Chrome con perfiles
desechables sobre la misma distribución Wasm ya construida. Cada arranque usa el
smoke existente y conserva su propia métrica; el manifiesto de la serie registra
el SHA fuente, huella de la distribución, Chrome, Node, entorno, configuración y
las cinco iteraciones. La ruta local usada para lanzar Chrome no se escribe en el
manifiesto ni en la salida: la identidad conserva solo el producto/version de
Chrome que informa el smoke.

```powershell
node .\scripts\web-performance-repeatability.mjs `
  --dist web/build/dist/wasmJs/productionExecutable `
  --chrome 'C:\Program Files\Google\Chrome\Application\chrome.exe' `
  --docmentis `
  --metrics-dir build/reports/web-performance-repeatability `
  --out build/reports/web-performance-repeatability.json
```

La serie falla si falta una de las cinco ejecuciones, un proceso smoke falla, una
métrica no cumple el esquema existente, se repite una muestra o cambia la
identidad de revisión/distribución/Chrome/entorno. No falla por variación de
tiempo de montaje ni memoria: hasta disponer de un runner y baseline controlados,
los percentiles son informativos.

El JSON de salida (`schemaVersion: 2`) incluye el **bootstrap** y el primer
estado estable que el harness puede observar honestamente: el shell Auth sin
sesion tras una navegacion de documento con perfil Chrome nuevo. Incluye los
percentiles de montaje, Navigation Timing y heap CDP, junto con una propuesta de
umbral con estado `proposed`. No es un SLO de producto ni un presupuesto de
release: sus unicos bloqueos son la validez estructural de las cinco muestras,
la identidad comun de build/entorno y el smoke funcional. Variacion de tiempo o
memoria, carga compartida, CPU/GPU, antivirus y estado termico se declaran como
ruido de entorno y permanecen en modo `advisory` hasta que se apruebe una linea
base en runner controlado.

El lanzador no crea PowerShell ni invoca `Start-Process`: ejecuta Node y Chrome
de forma headless. Cuando sea necesario compilar la distribución localmente,
usar `scripts/run-wasm-production-observed.ps1`, cuyo watchdog usa Job Object y
proceso sin ventana; no usar wrappers antiguos de espera externa.
