# Evidencia de validación multiplataforma

**Corte documental:** `main` `d8652326f61d93f33bb860d64565ad74e3e80ed5` (2026-07-29).

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
| GitHub Actions | GO limitado | La matriz pública #102 sigue acreditada por [`30425431607`](https://github.com/dossijeo/quata/actions/runs/30425431607). PR #106 añadió logout autenticado y el run [`30429034347`](https://github.com/dossijeo/quata/actions/runs/30429034347) terminó verde sobre `db2c2b1a`: compilación y contratos Swift/Kotlin, sin acreditar interacción visual. |
| Matriz pública simulador | GO funcional suplementario | PR #102 ejecutó serialmente iOS 18.3 e iOS 26.5 con configuración pública temporal, HTTP 200, PID/logs filtrados, 0 crash/fatal observados, OCR/capturas y cleanup. CPU-raster Intel no acredita SLA ni rendimiento de producto. |
| Rutas/factorías iOS | GO de contrato | PR #103 añadió contratos de rutas instaladas y factorías; no sustituye una sesión ni un backend real. |
| Backend de cuenta de prueba | Protocolo verificado, no E2E | Una comprobación redactada obtuvo sesión y perfil. No se guardaron ni publicaron credenciales, ni este resultado afirma UI, refresh o logout. |
| Login visual real | HOLD técnico | Falta el fichero de configuración remoto con modo `0600` y aislar Keychain/test host. PR #107 se cerró sin merge después de que su fixture desconectara el proceso Compose; no se declara autenticación, refresh ni logout visual. |
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
