# Backlog de cierre de la migración multiplataforma

**Corte verificado:** `main` `d8652326f61d93f33bb860d64565ad74e3e80ed5` (2026-07-29).

Este backlog separa código integrado, evidencia por SHA y salida a producción.
Un build, smoke, contrato o simulador no habilita una mutación remota, distribución
Apple ni cambio de políticas.

## Invariantes

- No cambiar RLS, DDL, funciones, grants ni datos de Supabase en este backlog sin
  un rollout compatible y autorización de despliegue independientes.
- Conservar Android publicado, Web antigua y navegación anónima del Feed.
- Todo cambio usa rama/worktree aislado, commit, revisión independiente, CI del SHA
  y limpieza posterior.
- Serializar simuladores iOS. Una prueba pública no es autenticación y un archive
  sin firma no es distribución.

## Trabajo integrado en este corte

| ID | Estado | Evidencia |
| --- | --- | --- |
| WEB-PERF-001 | Integrado, observacional | PR [#99](https://github.com/dossijeo/quata/pull/99), `f38503b9`; cinco muestras frías reproducibles, sin declarar SLO. |
| WEB-UX-001 | Integrado | PR [#100](https://github.com/dossijeo/quata/pull/100), `be56cacf`; rutas públicas, recarga, responsive y accesibilidad observada. |
| CAPABILITY-DRIFT-001 | Integrado | PR [#101](https://github.com/dossijeo/quata/pull/101), `54a2f07e`; catálogo de capacidades que falla cerrado si deriva la matriz. |
| IOS-PUBLIC-MATRIX-001 | Integrado | PR [#102](https://github.com/dossijeo/quata/pull/102), `32f1bb65`; script serial, lock, configuración temporal restaurada, logs/PID, HTTP, OCR/capturas y limpieza. CI [`30425431607`](https://github.com/dossijeo/quata/actions/runs/30425431607) verde sobre el SHA de la PR. |
| IOS-ROUTES-001 | Integrado | PR [#103](https://github.com/dossijeo/quata/pull/103), `9344b5fa`; contratos de rutas/factorías iOS. |
| IOS-APNS-REQ-001 | Integrado, documental | PR [#104](https://github.com/dossijeo/quata/pull/104), `18596076`; requisitos de APNs y dispatcher central documentados, sin implementar entrega. |
| IOS-LOGOUT-UI-001 | Integrado | PR [#106](https://github.com/dossijeo/quata/pull/106), `d8652326`; UI de logout autenticado iOS y CI [`30429034347`](https://github.com/dossijeo/quata/actions/runs/30429034347) verde. No acredita la interacción visual completa. |
| VAL-WEB | GO limitado | Producción observada, browser tests, cinco perfiles fríos, UX/AX público; E2E autenticado no acreditado. |
| VAL-ANDROID | GO limitado | Build y Feed anónimo API-37 sin crash/ANR; matriz autenticada pendiente. |
| VAL-IOS-PUBLIC | GO funcional limitado | Feed público en iOS 18.3 e iOS 26.5, HTTP 200 y ausencia observada de crash/fatal; no es rendimiento ni autenticación. |

## Prioridad inmediata

| ID | P | Estado | Trabajo y criterio de salida |
| --- | --- | --- | --- |
| IOS-AUTH-001 | P0 | HOLD técnico | El backend de cuenta de prueba devolvió sesión y perfil en una comprobación redactada; falta recorrer visualmente login, refresh, logout y cold start. Requiere fichero de configuración remoto con `0600` y aislamiento de Keychain/test host. PR #107 se cerró sin merge por fixture y desconexión del proceso Compose. El resultado sólo pasa a GO con sesión limpiada, evidencia no secreta y revisión. |
| IOS-SIGN-001 | P0 | Bloqueada externamente | Configurar Team, App ID, certificados, perfiles y App Group en Apple. Debe producir archive/IPA firmado y verificarse sin subir secretos al repo. El archive actual es sin firma. |
| IOS-DEVICE-001 | P0 | Bloqueada externamente | En dispositivo físico firmado: APNs, Share Extension/App Group, permisos y rendering. |
| IOS-APNS-001 | P0 | Bloqueada externamente | Implementar el canal APNs del dispatcher central usando secreto de servidor y prueba de entrega/deep link. Ver [IOS_APNS_PRODUCTION_REQUIREMENTS.md](IOS_APNS_PRODUCTION_REQUIREMENTS.md). |
| WEB-E2E-001 | P0 | Pendiente | Ejecutar login/logout y rutas autenticadas con datos efímeros autorizados y purga verificable. |
| ANDROID-AUTH-001 | P0 | Pendiente | Repetir matriz autenticada con cuenta segura, preservando el AVD anónimo y Android publicado. |
| WEB-DOCS-001 | P1 | Bloqueada externamente | Aprobar licencia, telemetría, CSP/CORS y Storage autenticado de DocMentis. |
| SEC-RLS-001..005 | P0 | Abierto, no tocar | Mantener capacidades afectadas fail-closed; cualquier endurecimiento requiere release compatible independiente. |

## Política de limpieza

Toda rama fusionada o descartada se borra local y remotamente después de preservar
la evidencia necesaria. Las ramas de experimento de toolchain y relajación Metal no
son candidatas a merge.
