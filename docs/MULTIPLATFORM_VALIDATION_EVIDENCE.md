# Evidencia de validación multiplataforma

**Corte documental:** `main` `185960769011f03d644130012fe8051ad536e9bf` (2026-07-29).

La evidencia se delimita por SHA y tipo de prueba: build acredita artefactos;
smoke acredita el recorrido descrito; E2E exige backend real, identidad y limpieza.
Ninguna fila amplía su alcance por inferencia.

## Web/Wasm

| Prueba | Resultado | Evidencia y límite |
| --- | --- | --- |
| Producción observada | GO | Harness reproducible de PR #99 con cinco perfiles fríos. Son muestras de diagnóstico, no un SLO de producto. |
| Browser/UX público | GO limitado | PR #100 acreditó rutas profundas, recarga, tres viewports, scroll interno, foco secuencial y árbol AX sobre los nodos del formulario. |
| Bundle Linux | GO | Baseline canónico aprobado por PR #97. Windows conserva sólo valor diagnóstico porque Wasm/JS depende del host. |
| `wasmJsBrowserTest` | GO | Verde en la CI del lote #102 junto a bundle y smoke Chrome. |
| E2E autenticado | HOLD | No se afirma hasta terminar una ejecución controlada con identidad aislada y limpieza verificable. |

## Android API-37

| Prueba | Resultado | Límite |
| --- | --- | --- |
| Compilación e instalación | GO | Build y `install -r` validados en el corte previo; los checks Android del lote #102 siguen verdes. |
| Arranque/Feed anónimo | GO limitado | Cold start observado de 4,924 s, PID vivo y 0 crash/ANR tras cold boot. Es un smoke de entorno, no benchmark. |
| Matriz autenticada | HOLD | Pendiente de cuenta aislada y preservación del AVD anónimo. |

## iOS

| Carril | Resultado | Límite |
| --- | --- | --- |
| GitHub Actions | GO limitado | Run [`30425431607`](https://github.com/dossijeo/quata/actions/runs/30425431607), job `90490809295`, `success` sobre `ba6a72a1649239a4abf7408d63712d206a5d0a4c`: Kotlin/Native, framework/XCFramework, host Swift, XCTest, archive sin firma y contratos de la matriz pública. |
| Matriz pública simulador | GO funcional suplementario | PR #102 ejecutó serialmente iOS 18.3 e iOS 26.5 con configuración pública temporal, HTTP 200, PID/logs filtrados, 0 crash/fatal observados, OCR/capturas y cleanup. CPU-raster Intel no acredita SLA ni rendimiento de producto. |
| Rutas/factorías iOS | GO de contrato | PR #103 añadió contratos de rutas instaladas y factorías; no sustituye una sesión ni un backend real. |
| Login visual real | En validación / HOLD | La cuenta aislada se prueba en un carril separado. Hasta resultado final y limpieza no se declara autenticación, refresh ni logout. |
| Signing/distribución | HOLD | El archive genérico sin firma está disponible como evidencia de estructura. Faltan identidad Apple/Team, certificados, perfiles, App Group, IPA/TestFlight y dispositivo físico. |
| APNs | HOLD | Hay bridge/plumbing y requisitos documentados; no hay canal de dispatcher APNs ni entrega en dispositivo físico firmada. |

## Seguridad y límites de producción

No se desplegaron cambios de Supabase ni se modificaron RLS, DDL, funciones, grants
o datos durante PRs #98–#104. Los hallazgos permanecen abiertos en
[RLS_FINDINGS.md](RLS_FINDINGS.md) y las mutaciones no acreditadas siguen
fail-closed.

## Conclusión del corte

Web y Android conservan un recorrido público validado. iOS compila, enlaza, ejecuta
el Feed público en ambos simuladores y tiene una matriz reproducible, pero sigue
sin firma, entrega APNs, dispositivo físico ni E2E autenticado terminado. La
migración global, `webReady` e `iosReady` permanecen incompletos.
