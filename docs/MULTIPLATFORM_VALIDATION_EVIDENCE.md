# Evidencia de validación multiplataforma

## FLOW-DEEP-LINKS — aceptación local de #327, 11 de septiembre de 2026

**Estado parcial, sin certificación final ni GO integrado.** Este corte focal no sustituye
los cortes históricos posteriores ni promueve CHAT-FOCUSED-MESSAGE o FLOW-SHELL-NAV.
La evidencia corresponde al merge sintético `1807d1ecee0d04f3ce1db1d15cc13cc3db1be4a7`,
base `9afe514cea667ee3abbf9f9e726d8ac5c32dac77` y head de PR
`209f7847ca32a59ea3bb9d29135d32d41d353129`. Los resultados anteriores conservan sus SHA;
no se han renombrado ni transferido por inferencia. Esta sección es documentación,
no un manifest de candidata certificada.

Los originales locales están en el worktree `quata-flow-deep-links-pr327-1807d1ec`,
bajo `build-reports/flow-deep-links-integrated/`. `acceptance-checklist.json` conserva
los directorios completos por recorrido. Los resultados originales no se modifican
para registrar la revisión: la aceptación independiente se anota por separado.

| Evidencia renovada | Resultado y alcance | Límites conservados |
| --- | --- | --- |
| Web producción | Build terminal 0, 790,89 s; fingerprint `93c73f7a0771ba483b0c2a1e6995ebc6eabe0488463b67c2bb8d4d404f436f82`. Diecinueve contratos browser pasan, sin omitidos. | Los contratos sintéticos prueban el mecanismo, no sustituyen los recorridos reales. |
| Feed/Oficial públicos Web | Existentes e inexistentes, frío/caliente, destino, vuelta y recarga. Ausentes: HTTP 200 sin filas inicial y al reintentar. Capturas revisadas. | No fallo de red ni reproducción multimedia. Las recargas de casos ausentes se acreditan por estado. |
| Web sin ID | `post-`, `official-` y `chat-` resuelven Feed visible frío/caliente; seis capturas, mismo documento caliente y recarga a Feed. | No generalizar a todo enlace malformado. |
| Chat Web válido | Run `ac07b93c-bda7-4603-8cdc-f48da128a2b7`: hilo 2566/mensaje 11198, foco único descubierto frío/caliente y vuelta. | Sesión válida del fixture, sin claim de ciclo de vida del sistema. |
| Chat Web continuar/cancelar | Runs `f3b9c80e-5577-4cf1-b3b7-54577807e10b` y `79d243d8-e9d9-4585-b9bb-ec48f9a0626f`: continuación al destino exacto; cancelar y autenticar después mantiene Feed sin foco residual. | Login real por bridge del repositorio, no escritura/Submit manual. Cancelación observada dos segundos antes de recarga. |
| Chat Web mensaje/hilo ausente | Mensaje: run `d6067916-440e-4a52-9109-27701763e6c1`, historial agotado y cero foco. Hilo: directorio `chat-missing-thread-b5564687-6598-4e8e-82e9-635d5c368d8f`, error legible y vuelta, frío/caliente. | Mensaje sin aviso explícito de inexistencia. Hilo ausente comprobado por DB; no inferido del 403. No se ejecutó Retry de Chat. |
| Chat Web refresh/revocación | Directorios `chat-refresh-cold-86205fa0-376c-4093-82e6-b8146889abca`, `chat-refresh-warm-b2a232b1-98f9-4afc-a3f6-6630f0c4e2a8`, `chat-revoked-cold-ac76143e-700b-4d4f-a5fc-0ed8682c8ecf` y `chat-revoked-warm-a351a6f6-7bd6-453a-8ca9-4b6deff574a9`: una renovación o rechazo real verificados; destino o barrera correspondientes. | Se vence el metadato local; no se acredita vencimiento real/anticipado del JWT ni borrado de almacenamiento. Ausencia de mensaje revocado muestreada en la barrera final. |
| iOS build nativo | Framework raster x86_64 y host build-for-testing terminal 0 sobre el merge indicado; manifiesto `ios-native-manifest.json`. | Simulador iOS 18.3 dedicado; no sustituye compilación ARM de CI. |
| iOS públicos | Feed y Oficial existentes/inexistentes frío/caliente, vuelta, PID estable y capturas revisadas; probes de sesión vacía antes/después y cierre del simulador. | Custom scheme, no Universal Links, lectura HTTP nativa ni Retry ejecutado. No reproducción multimedia. |
| iOS Chat | `ios-owned-valid-f90047cd-8d53-49a2-b334-290f50709b4e` y `ios-owned-missing-thread-967eb48e-1c9f-411d-9388-71340167a6de`: entrega externa frío/caliente, foco válido o error legible y vuelta. | Sesión propia importada con custodia verificada; no login nativo ni refresh real iOS. |
| iOS anónimo | Run `34eefaaa-115b-42e5-9338-2636e260b1f5`: barrera, Login vacío, cierre nativo y shell Feed; PID estable, sesión vacía y simulador cerrado. | La captura final tiene centro negro: no acredita publicaciones cargadas, login real ni entrega caliente. |
| Android | `:app:assembleDebug` terminal 0, 219 s; APK `f390488b84751fd2ff3ec7799015683ad8c9d1b22653c6b3eeb191b10e14e7d6`. | Build únicamente. Falta recepción externa fría/caliente y salidas; el control automático rechazó `adb shell am start` como `blocked by policy`. No se eludió. |

Los recorridos reales de Chat tienen restitución verificada de sus fixtures propios;
los directorios privados de los ensayos terminados están vacíos. Los públicos no crean
fixtures. La revisión independiente acepta los recorridos de la tabla con sus límites;
el build Android no equivale a aceptación de entrega externa.

Nueve regresiones sintéticas iOS adicionales terminaron 0 en
`ios-synthetic-regressions-bfb67301`, run `bfb67301-e712-47f7-95a3-ad3994287737`.
Los logs y la revisión independiente acreditan ejecución exacta, sin omitidos, de contratos
de intercambio/aislamiento de sesión, Auth, cancelación y entrega pendiente. Sesión vacía
antes/después y simulador cerrado. No acreditan login ni persistencia remota reales.

Falta cerrar la auditoría focal de cobertura y la aceptación Android antes de congelar
el head. Los fast gates verdes y los agregados finales con jobs reales omitidos mientras
la PR es draft no acreditan certificación final. Tras promoción autorizada e integración,
el cierre obligatorio debe confirmar merge/CI y actualizar el inventario operativo.

## Corte histórico general

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
