# Tablero operativo de migración multiplataforma

> Fuente de verdad del método de trabajo: [`MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`](./MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).
> Este tablero es una fotografía de progreso y no puede redefinir los gates, la arquitectura ni el
> presupuesto de ejecución establecidos allí.

## Foto de control — 2026-08-02

**HEAD integrado:** `main` `1fe3bf74831415951779b50e651f89505a562169` (PR #169), posterior a #154 y #156. El proyecto sigue incompleto:
las raíces comunes de Crear publicación y Cuenta/Perfil/SOS están integradas, pero integración de
raíz no equivale a GO visual o funcional final en Web/iOS.

| Área | Estado | Qué acredita | Límite vigente |
| --- | --- | --- | --- |
| Web/Wasm | GO limitado | Shell público, rutas principales y varias raíces Compose comunes están integrados; #154 incorpora `CreatePostRoot` y #156 `ProfileScreenHost`. | Faltan postflights autenticados por flujo y paridad visual exacta. Avatar Web se acredita por contratos, no por una mutación E2E real guardada y limpiada. |
| Presupuesto Wasm | Integrado | Watchdog sin ventanas visibles, baseline Linux aprobado y captura canónica reproducible. | Windows sigue siendo diagnóstico: el artefacto Wasm/JS depende del host. El presupuesto es un gate técnico, no un SLO de producto. |
| Android | GO limitado | Build, `install -r`, arranque frío y Feed anónimo API-37 con 0 crash/ANR tras cold boot. | Falta matriz autenticada controlada; no se modifica Android publicado ni el Feed anónimo. |
| iOS CI | GO limitado | CI y contratos Swift/Kotlin siguen siendo gates obligatorios; #156 restauró el acceso Swift/Kotlin requerido por el host. | No prueba IPA/TestFlight/APNs/dispositivo físico. El runner auth debe rechazar explícitamente `SKIPPED`/no ejecutado aunque `xcodebuild` devuelva 0. |
| iOS simulador | GO funcional suplementario | El postflight de `main` `5d2a52d1` pasó Feed y perfil remoto públicos; auth real ejecutada mediante `.xctestrun` con `QUATA_IOS_AUTH_E2E_FILE`; relanzamiento normal sin reinstalar conserva/restaura sesión; Cuenta/Perfil visual PASS. | SOS es parcial: acceso/estado y 1/5 contactos visibles; no se abrió el subflujo para evitar mutación. CPU-raster no es SLA ni reemplaza CI ARM. |
| Crear publicación (#154) | COMÚN con límites | `CreatePostRoot` común está integrado en Android, Wasm e iOS. | La evidencia de #154 no debe presentarse como GO visual/funcional final: validar publicación, adaptadores de medios y paridad autenticada sin modificar RLS. |
| Cuenta/Perfil/SOS (#156) | COMÚN con límites | `ProfileScreenHost` común integrado; postflight iOS de Feed/perfil público, auth, relanzamiento y Cuenta/Perfil visual PASS. | Completar el subflujo SOS sin ocultar que sólo se verificaron 1/5 contactos; avatar Web continúa contractual sin mutación E2E acreditada. |
| Pipeline CI (#169) | Integrado, fail-closed | Preflight rápido local exacto, gates finales requeridos y concurrencia por PR sin cancelar evidencia de `main`/manual. | Aún no acredita producto; certifica candidatos ya validados localmente. |
| RLS/DB | Sin cambios | Esta ola no cambió RLS, DDL, funciones, grants ni datos de Supabase. | Hallazgos existentes siguen abiertos; no se endurecen políticas mientras convivan clientes publicados. |

## Integraciones recientes

| PR | Merge | Resultado |
| --- | --- | --- |
| [#98](https://github.com/dossijeo/quata/pull/98) | `801dcbad` | Evidencia final inicial del corte `c87`. |
| [#99](https://github.com/dossijeo/quata/pull/99) | `f38503b9` | Arnés de cinco muestras de rendimiento Web; observación, no SLO. |
| [#100](https://github.com/dossijeo/quata/pull/100) | `be56cacf` | UX pública Web: responsive, accesibilidad, deep links y recarga. |
| [#101](https://github.com/dossijeo/quata/pull/101) | `54a2f07e` | Matriz de capacidades fail-closed. |
| [#102](https://github.com/dossijeo/quata/pull/102) | `32f1bb65` | Matriz pública endurecida para simuladores iOS. |
| [#103](https://github.com/dossijeo/quata/pull/103) | `9344b5fa` | Contratos de rutas y factorías iOS instaladas. |
| [#104](https://github.com/dossijeo/quata/pull/104) | `18596076` | Requisitos de producción APNs y hallazgo del dispatcher. |
| [#106](https://github.com/dossijeo/quata/pull/106) | `d8652326` | UI de logout autenticado iOS; CI iOS verde. |
| [#154](https://github.com/dossijeo/quata/pull/154) | `68d1fab7` | `CreatePostRoot` integrado como raíz común. No atribuye GO visual ni publicación E2E acreditada. |
| [#168](https://github.com/dossijeo/quata/pull/168) | `07ec8826` | Compilación/host iOS y renderer público acreditados. No acredita el fallback visual de sesión restaurada caducada. |
| [#156](https://github.com/dossijeo/quata/pull/156) | `5d2a52d1` | `ProfileScreenHost` común; postflight iOS PASS para lectura pública, auth/relanzamiento y Cuenta/Perfil visual. SOS parcial; avatar Web contractual sin mutación E2E. |
| [#169](https://github.com/dossijeo/quata/pull/169) | `1fe3bf74` | Pipeline CI fail-closed: lane rápida replicable localmente y certificación final separada/exacta. |

## Registro de candidato #156 y mejora de preflight

La candidata congelada de #156 requirió **tres rondas finales de certificación**. Dos defectos se
escaparon del preflight local y se registran para no maquillarlos como incidencias normales de CI:

| Defecto escapado del preflight local | Corrección integrada | Gate preventivo |
| --- | --- | --- |
| La factoría Kotlin requerida por Swift no estaba disponible al construir el host iOS. | Se restauró la factoría/puente Swift-Kotlin. | Añadido: compilar Kotlin/Native y construir el host Swift localmente antes de publicar. |
| El gateway de perfil impedía la lectura pública sin sesión. | Se corrigió el fallback público del gateway. | Añadido y ejecutado en `main` `5d2a52d1`: arrancar Feed/perfil público iOS sin sesión y acreditar lectura remota y recuperación. |
| `xcodebuild` puede devolver `0` aunque el test auth lanzado por `.xctestrun` quede `SKIPPED` o no se ejecute. | El postflight se ejecutó realmente usando `QUATA_IOS_AUTH_E2E_FILE` explícito. | Añadido: el runner auth falla si no encuentra ejecución PASS del test, incluso con exit code 0. |

Las rondas anteriores al congelado sólo fueron diagnósticas; no se reutilizan como evidencia final.
La evidencia final exige base, head y merge sintético exactos según el modelo operativo.

## Auditoría honesta #168 — fallback iOS de sesión caducada

El merge `07ec8826` acredita compilación/host iOS y renderer del Feed público. El seeder de auth de
CI quedó `SKIPPED` porque no se proporcionó `QUATA_IOS_AUTH_E2E_FILE`. La evidencia local exacta
instaló la app después de sembrar la sesión, cambiando su *data-container*; por tanto no conserva el
estado restaurado y **no acredita** el fallback de sesión caducada. No se interpreta como fallo del
fallback ni como PASS visual.

Tarea focal de cierre: con `QUATA_IOS_AUTH_E2E_FILE` explícito, sembrar una sesión restaurada
caducada en el mismo simulador/data-container y relanzar sin reinstalar ni borrar datos; capturar el
fallback a Feed público y su recuperación. El informe debe registrar SHA, UDID, estado del
container, resultado real del seeder (nunca `SKIPPED`) y la captura resultante.

## Cierre de pipeline #169

La PR #169 completó dos rondas remotas del candidato. La primera, `3a94c9c6`, falló por un
**DEFECTO ESCAPADO DEL PREFLIGHT LOCAL**: el contrato del simulador iOS aún exigía rutas del filtro
`paths` eliminado. `e945d6ab` incorporó el contrato correcto al preflight rápido exacto local y la
segunda ronda remota quedó verde. La política final queda fail-closed: checks finales exactos
omitidos, cancelados o fallidos bloquean el gate requerido.

## Preparación local #157 — no integrada

La preparación local `3147` de Notificaciones no es una candidata ni una integración. Web auth,
visual y navegación PASS; Android exacto ya tiene el recorrido `d036` en `Pixel_9` API 37, mientras
el Android estable API 35 sólo se conserva como referencia; iOS tiene product build/auth previo
PASS. El gate real de Notificaciones iOS continúa bloqueado por un handshake de infraestructura
XCTest: no se ha confirmado una pantalla Inbox negra.

Actualización de evidencia: Android exacto `d036` pasó en ese AVD con los recorridos sin mutación
`Feed → Avisos vacío → Volver` (público) y `Feed → Avisos` con badge 4/dato Gabriel `→ Volver`
(auth); el estable API 35 se restauró después. El runner iOS `3147` pasó build firmado,
cold-boot y `bootstatus`, pero el seeder XCTest/testmanager agotó 120 s en dos condiciones
controladas (antes y tras cold boot), por lo que la UI de Notifications **no se ejecutó** y el estado
es **INFRASTRUCTURE BLOCK**. Evidencia: `C:\Users\PC\Desktop\QÜATA\migration-v2\evidence\notifications\3147b928-ios\runner.log`,
`seed.log`, `bootstatus.log`; plan/rutas: `C:\Users\PC\Desktop\QÜATA\migration-v2\preflight\pr157\PLAN.md`.

## Candidata #170 — final activa, no integrada

PR #169 ya está fusionada en `main` `1fe3bf74`. La única candidata actual es #170:
base `1fe3bf74`, head `66052c95` y merge sintético `4f3ce660`. La primera ronda sobre `fca3`
quedó invalidada por un P1 del clasificador que omitía orígenes `D`/`T`/rename y fue cancelada;
es diagnóstico, no evidencia reutilizable. El candidato congelado actual pasó el preflight exacto
**137/137**, Wave2 **245/245**, `actionlint` y `diff --check`, y recibió revisión independiente
**GO**. La ronda final remota está activa: #170 permanece no integrada hasta que sus gates exactos
verdes certifiquen ese merge sintético.

## Próxima cola

1. Completar el postflight de #156 pendiente: subflujo SOS (el PASS actual cubre acceso/estado y 1/5 contactos, no su mutación) y comparación Android↔Wasm↔iOS donde corresponda. Acreditar o dejar pendiente la subida real de avatar Web con datos temporales y limpieza.
2. Ejecutar el postflight de #154: Crear publicación común en Web/iOS con sesión real, adaptadores de medios y comparación visual; no convertir sus contratos en un GO no probado.
3. Desbloquear el handshake XCTest de Notificaciones para convertir la preparación local #157 en una candidata; repetir después sus gates exactos. El Android `d036` API 37 es PASS exacto local, pero no integrado; el seeder iOS no ejecutó UI y continúa bloqueado por infraestructura.
4. Esperar únicamente la certificación final remota exacta de #170; no alterar su head ni promocionar otra candidata. En paralelo, preparar trabajo local aislado sin reutilizarlo como evidencia final.
5. Aplicar el modelo de integración secuencial/ejecución paralela: una candidata final, preflight local completo, merge sintético y CI como certificación. Preparar localmente la siguiente unidad mientras CI está activo, sin publicar candidatos adicionales.
6. Cerrar la evidencia #168: sesión restaurada caducada en el mismo data-container, seeder realmente ejecutado con `QUATA_IOS_AUTH_E2E_FILE` y relanzamiento sin reinstalar.
7. Configurar firma Apple y completar APNs/dispositivo físico en carriles independientes. Mantener RLS-001..005 documentados; no cambiar políticas en este backlog.

## Decisiones vigentes

- Kotlin `2.2.21` y Compose `1.10.0` permanecen fijados. Kotlin `2.3.20`/Compose `1.11.0` se rechazó porque Compose 1.11 no publica `iosX64` para el carril Intel y no acredita el Skiko CPU-raster.
- CI conserva la prueba Metal estricta. CPU-raster Intel es un carril suplementario, no una relajación del contrato.
- `migrationComplete`, `webReady` e `iosReady` siguen siendo `false` hasta terminar los gates externos de autenticación, firma, APNs/dispositivo y backend.
