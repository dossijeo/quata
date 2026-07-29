# Backlog de cierre de la migración multiplataforma

**Corte verificado:** `main` `c87e82af615a1778092ec3b5ecfc70d1ecd485ea` (2026-07-29).

Este backlog distingue una capacidad integrada de una capacidad acreditada en un runtime real. Ninguna compilación, smoke o prueba de contrato habilita por sí misma una mutación de backend, un despliegue o una distribución de iOS.

## Invariantes

- No cambiar RLS, DDL, funciones, grants ni datos de Supabase como parte de este backlog sin un plan de compatibilidad y autorización de despliegue separados.
- Conservar Android publicado, la Web antigua y el Feed anónimo navegable.
- Todo cambio vive en una rama/worktree aislado, tiene commit, revisión independiente, CI del SHA y se limpia tras integrarse.
- Serializar los simuladores iOS. Nunca interpretar una prueba sin sesión como autenticación, ni un archive sin firma como distribución.

## Trabajo integrado en este corte

| ID | Estado | Evidencia |
| --- | --- | --- |
| WEB-WATCHDOG-001 | Integrado | PR [#93](https://github.com/dossijeo/quata/pull/93), merge `7c260fa`; el observador Wasm falla de forma cerrada y limpia su Job Object. |
| WEB-BUNDLE-GATE-001 | Integrado | PR [#94](https://github.com/dossijeo/quata/pull/94), merge `40dc604`; la aprobación de baseline sólo admite el diff documental permitido y verifica la atestación. |
| WEB-BUNDLE-CAPTURE-001 | Integrado | PR [#96](https://github.com/dossijeo/quata/pull/96), merge `30682427`; captura canónica Linux manual, run `30410233909`. |
| WEB-BUNDLE-APPROVAL-001 | Integrado | PR [#97](https://github.com/dossijeo/quata/pull/97), merge `c87e82af`; baseline Linux aprobado y gate verde. |
| VAL-WEB-C87 | GO limitado | Distribución observada, 3 perfiles Chrome fríos, DocMentis, Share Target y rutas herméticas pasan; falta E2E autenticado real. |
| VAL-ANDROID-C87 | GO limitado | Compilación, instalación, arranque frío, Feed anónimo en Pixel y AVD temporal, sin crash/ANR; falta matriz autenticada. |
| VAL-IOS-C87 | GO funcional limitado | CI exacta y Feed anónimo real en dos simuladores. En iOS 26.5, un cold y dos warm alcanzaron Feed a 8 s/8 s/6 s y se estabilizaron; es evidencia de funcionalidad CPU-raster, no SLA. Faltan autenticación visual/E2E, firma y dispositivo físico. |

## Prioridad inmediata

| ID | P | Estado | Trabajo y criterio de salida |
| --- | --- | --- | --- |
| WEB-E2E-001 | P0 | Bloqueada externamente | Conseguir una cuenta efímera autorizada y automatizar login/logout UI real sin eludir Turnstile ni usar secretos. Debe recorrer rutas autenticadas y purgar sesiones/datos. |
| WEB-AUTH-001 | P0 | Bloqueada externamente | Aprobar el contrato seguro de registro y recuperación antes de habilitar registro Web. La ausencia de cuenta segura no se sustituye con fixtures privilegiados. |
| WEB-PERF-001 | P1 | Pendiente | Definir SLO de arranque/memoria en runner controlado, con al menos cinco muestras y sin PII. Las métricas actuales son observación, no gate. |
| WEB-DOCS-001 | P1 | Bloqueada externamente | Revisar licencia, telemetría, CSP/CORS y Storage autenticado de DocMentis antes de producción. |
| ANDROID-AUTH-001 | P0 | Bloqueada externamente | Repetir matriz autenticada cuando exista una cuenta de prueba segura, preservando el AVD anónimo y Android publicado. |
| IOS-AUTH-001 | P0 | Bloqueada externamente | Validar login, refresh, logout y cold start contra backend real con cuenta aislada; no afirmar el resultado del contrato de navegación como E2E. |
| IOS-AX-001 | P1 | Bloqueada por infraestructura | Resolver el acceso de automatización (TCC/AX) de la VM para que Chat y las verticales autenticadas puedan comprobarse visualmente por UI. |
| IOS-SIGN-001 | P0 | Bloqueada externamente | Configurar Team, App IDs, perfiles, App Group y push en una fase de signing separada. Debe producir un archive/IPA firmado verificable. |
| IOS-DEVICE-001 | P0 | Bloqueada externamente | Validar APNs, Share Extension/App Group, permisos y rendering en dispositivo físico firmado. |
| TOOLCHAIN-001 | P1 | Decisión tomada | Mantener Kotlin `2.2.21` y Compose `1.10.0`. Kotlin `2.3.20`/Compose `1.11.0` se rechazó: ya no resuelve `iosX64` y el Skiko CPU-raster local no es compatible. Revaluar sólo tras retirar o reemplazar el carril Intel. |
| SEC-RLS-001..005 | P0 | Abierto, no tocar | Mantener los hallazgos y las capacidades afectadas fail-closed. Cualquier endurecimiento requiere rollout compatible independiente; véase [RLS_FINDINGS.md](RLS_FINDINGS.md). |

## Política de limpieza

Una rama rechazada o ya fusionada se borra local y remotamente tras conservar la evidencia. Las ramas de experimento de toolchain y de relajación Metal no son candidatas a merge: el experimento de toolchain rompe `iosX64` y la relajación Metal fue descartada; el contrato Metal estricto permanece en CI.
