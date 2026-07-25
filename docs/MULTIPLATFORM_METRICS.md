# Métricas reproducibles de migración

`scripts/multiplatform-metrics.ps1` genera una instantánea de lectura de las señales
arquitectónicas que el tablero necesita para detectar regresiones. No sustituye la
auditoría funcional, las compilaciones ni los recorridos E2E.

```powershell
.\scripts\multiplatform-metrics.ps1
```

Por defecto deja estos artefactos no rastreados en `build-reports/multiplatform-metrics/`:

- `multiplatform-metrics.json`: formato estable para comparar SHA o adjuntar a CI.
- `multiplatform-metrics.md`: resumen humano de la misma instantánea.

Se puede archivar un JSON aprobado de un SHA anterior fuera del árbol y asociarlo
al informe actual para dejar constancia de su procedencia:

```powershell
.\scripts\multiplatform-metrics.ps1 -BaselinePath C:\evidence\quata-main.json
```

El esquema v1 contiene un ejemplo de las categorías que forman una baseline:

```json
{
  "schemaVersion": 1,
  "sourceSets": {
    "commonMain": { "files": 0, "lines": 0 },
    "androidMain": { "files": 0, "lines": 0 },
    "wasmJsMain": { "files": 0, "lines": 0 },
    "iosMain": { "files": 0, "lines": 0 }
  },
  "androidImportsInCommonMain": { "count": 0 },
  "jsTargetDeclarations": { "count": 0 },
  "explicitUnavailableOperations": { "count": 0 },
  "capabilityManifestsAndHosts": { "count": 0 }
}
```

Los ceros del ejemplo no son una baseline del proyecto. Una baseline se obtiene
ejecutando el script sobre un commit identificado y guardando el JSON resultante
como evidencia de ese SHA. Las métricas no declaran progreso por líneas: una
reducción sólo es valiosa si el cambio conserva la capacidad y tiene la
validación proporcional indicada por el tablero.

## Límites deliberados

- Sólo recorre los roots propios `app`, `core`, `designsystem`, `feature`,
  `ios-shared`, `iosApp` y `web`.
- Excluye `document-reader` (vendorizado), outputs `build`, modelos, cachés y
  dependencias; por tanto no infla artificialmente el código migrable.
- Reporta imports `android.*` de `commonMain` con ruta y línea.
- Localiza señales explícitas de operaciones no implementadas o no soportadas.
  Las exclusiones y su motivo se incluyen tanto en JSON como en el Markdown.
- Enumera declaraciones `js(IR)`/`wasmJs` y los manifests/hosts detectables por
  nombre para ayudar a localizar superficie de plataforma, sin afirmar que una
  ruta esté compuesta, conectada al backend o probada E2E.
- No lee ni emite variables de entorno, contenido de secretos, tokens o URLs de
  conexión; tampoco reescribe el inventario ni el tablero.

No se añade como gate de CI todavía: el informe sirve primero como baseline por
SHA. Un umbral sólo debe activarse tras acordar qué variaciones son regresiones
reales y no una migración intencionada.
