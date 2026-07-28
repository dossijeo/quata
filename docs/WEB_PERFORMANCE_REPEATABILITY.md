# Repetibilidad de métricas Web

La evidencia de rendimiento Web ejecuta tres arranques de Chrome con perfiles
desechables sobre la misma distribución Wasm ya construida. Cada arranque usa el
smoke existente y conserva su propia métrica; el manifiesto de la serie registra
el SHA fuente, huella de la distribución, Chrome, Node, entorno, configuración y
las tres iteraciones.

```powershell
node .\scripts\web-performance-repeatability.mjs `
  --dist web/build/dist/wasmJs/productionExecutable `
  --chrome 'C:\Program Files\Google\Chrome\Application\chrome.exe' `
  --docmentis `
  --metrics-dir build/reports/web-performance-repeatability `
  --out build/reports/web-performance-repeatability.json
```

La serie falla si falta una de las tres ejecuciones, un proceso smoke falla, una
métrica no cumple el esquema existente, se repite una muestra o cambia la
identidad de revisión/distribución/Chrome/entorno. No falla por variación de
tiempo de montaje ni memoria: hasta disponer de un runner y baseline controlados,
los percentiles son informativos.

El lanzador no crea PowerShell ni invoca `Start-Process`: ejecuta Node y Chrome
de forma headless. Cuando sea necesario compilar la distribución localmente,
usar `scripts/run-wasm-production-observed.ps1`, cuyo watchdog usa Job Object y
proceso sin ventana; no usar wrappers antiguos de espera externa.
