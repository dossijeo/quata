# Tablero operativo de migración multiplataforma

> Fuente de verdad del método de trabajo: [`MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`](./MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).
> Este tablero es una fotografía de progreso y no puede redefinir los gates, la arquitectura ni el
> presupuesto de ejecución establecidos allí.

## Foto de control — 2026-08-04

**HEAD integrado:** `main` `702fb7174a758778e4f5d8f2ded0b6853378208f` (PR #175), posterior a #154,
#156, #159, #168, #169, #170, #172, #173 y #174. El proyecto sigue incompleto: #175 integra las
raíces comunes de Comunidades y perfil público global, pero conserva límites `PROF-*`,
`FLOW-COMMUNITY-CHAT` y overlays dependientes según `SCREEN_MIGRATION_INVENTORY_V2.md`.

| Área | Estado | Qué acredita | Límite vigente |
| --- | --- | --- | --- |
| Web/Wasm | GO limitado | Shell público, rutas principales y varias raíces Compose comunes están integrados; #154 incorpora `CreatePostRoot` y #156 `ProfileScreenHost`. | Faltan postflights autenticados por flujo y paridad visual exacta. Avatar Web se acredita por contratos, no por una mutación E2E real guardada y limpiada. |
| Presupuesto Wasm | Integrado | Watchdog sin ventanas visibles, baseline Linux aprobado y captura canónica reproducible. | Windows sigue siendo diagnóstico: el artefacto Wasm/JS depende del host. El presupuesto es un gate técnico, no un SLO de producto. |
| Android | GO limitado | Build, `install -r`, arranque frío y Feed anónimo API-37 con 0 crash/ANR tras cold boot. | Falta matriz autenticada controlada; no se modifica Android publicado ni el Feed anónimo. |
| iOS CI | GO limitado | CI y contratos Swift/Kotlin siguen siendo gates obligatorios; #156 restauró el acceso Swift/Kotlin requerido por el host. | No prueba IPA/TestFlight/APNs/dispositivo físico. El runner auth debe rechazar explícitamente `SKIPPED`/no ejecutado aunque `xcodebuild` devuelva 0. |
| iOS simulador | GO funcional suplementario | El postflight de `main` `5d2a52d1` pasó Feed y perfil remoto públicos; auth real ejecutada mediante `.xctestrun` con `QUATA_IOS_AUTH_E2E_FILE`; relanzamiento normal sin reinstalar conserva/restaura sesión; Cuenta/Perfil visual PASS. | SOS es parcial: acceso/estado y 1/5 contactos visibles; el puntero remoto no automatizó la navegación de forma fiable. No hubo mutaciones. CPU-raster no es SLA ni reemplaza CI ARM. |
| Crear publicación (#154) | COMÚN con límites | `CreatePostRoot` común está integrado en Android, Wasm e iOS. | La evidencia de #154 no debe presentarse como GO visual/funcional final: validar publicación, adaptadores de medios y paridad autenticada sin modificar RLS. |
| Cuenta/Perfil/SOS (#156) | COMÚN con límites | `ProfileScreenHost` común integrado; postflight iOS de Feed/perfil público, auth, relanzamiento y Cuenta/Perfil visual PASS. | Completar el subflujo SOS sin ocultar que sólo se verificaron 1/5 contactos; avatar Web continúa contractual sin mutación E2E acreditada. |
| Comunidades/perfil público (#175) | COMÚN con límites | `NeighborhoodsScreenHost` y `CommunityProfileScreenHost` integrados en Android, Wasm e iOS; repositorios reales, entradas globales y gate de sesión conectados. | P2 vigentes: mutaciones `PROF-*`, entradas visuales desde Oficial/Conversaciones/Chat, listas anidadas, contenido/overlays, Chat↔Perfil, back Android de miembros y estados de error/retorno. |
| Feed iOS medios (#175) | COMÚN con límites | Gradiente URL/hash detrás de vídeo, superficies UIKit/AVPlayer transparentes, controles Compose play/pause y mute global conectado a `AVPlayer`; evidencia exacta del merge `5fd040ae`. | Duración/seek iOS de `OVR-MEDIA` continúa pendiente; no atribuye GO a los demás overlays de Feed. |
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
| [#170](https://github.com/dossijeo/quata/pull/170) | `4c719072` | Clasificador de impacto fail-closed integrado; certificación final exacta verde y rama remota limpiada. |
| [#159](https://github.com/dossijeo/quata/pull/159) | `568c60c7` | Raíces comunes de Novedades e Historial; permanecen postflights de versión, catálogo, cierre y retorno. |
| [#172](https://github.com/dossijeo/quata/pull/172) | `72552d86` | `NotificationsHostContent` común en Android, Wasm e iOS; destinos/mutaciones y postflight integrado continúan como límites. |
| [#174](https://github.com/dossijeo/quata/pull/174) | `ff088b55` | Baseline Wasm actualizado sobre las integraciones anteriores. |
| [#173](https://github.com/dossijeo/quata/pull/173) | `855f167f` | `ConversationsScreenHost` y transporte realtime común; Chat Web/iOS sigue siendo fallback browser-style. |
| [#175](https://github.com/dossijeo/quata/pull/175) | `702fb717` | Comunidades y perfil público global pasan a raíces comunes con límites; Feed iOS integra gradiente de medios y controles globales. Candidato exacto final: base `855f167f`, head `aee41fa7`, merge sintético `5fd040ae`; gates protegidos verdes. |

## Registro de candidato #156 y mejora de preflight

La candidata congelada de #156 requirió **tres rondas finales de certificación**. Dos defectos de
implementación se escaparon del preflight local y se registran para no maquillarlos como incidencias
normales de CI:

| Defecto escapado del preflight local | Corrección integrada | Gate preventivo |
| --- | --- | --- |
| La factoría Kotlin requerida por Swift no estaba disponible al construir el host iOS. | Se restauró la factoría/puente Swift-Kotlin. | Añadido: compilar Kotlin/Native y construir el host Swift localmente antes de publicar. |
| El gateway de perfil impedía la lectura pública sin sesión. | Se corrigió el fallback público del gateway. | Añadido y ejecutado en `main` `5d2a52d1`: arrancar Feed/perfil público iOS sin sesión y acreditar lectura remota y recuperación. |

Hallazgo postflight operativo (no es una tercera corrección integrada de #156): `xcodebuild` puede
devolver `0` aunque el test auth lanzado por `.xctestrun` quede `SKIPPED` o no se ejecute. El gate
preventivo añadido exige que el runner falle si no encuentra ejecución PASS, incluso con exit code
`0`; el postflight ejecutado usó `QUATA_IOS_AUTH_E2E_FILE` explícito.

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

## Auditoría honesta #154 — Create Post

#154 (`68d1fab7`) integró `CreatePostRoot` común y sus montajes Android/Web/iOS; la CI exacta y el
host de simulador estrecho están acreditados. Eso no acredita publicación ni paridad visual 1:1.
La PR se fusionó con validación visual pendiente y sin una revisión durable de producto, por lo que
no se reconstruye retrospectivamente un GO. Se mantienen los estados del manifiesto: Web
`composer.publish` **contract-only** e iOS **blocked** hasta una E2E real.

Tarea focal de cierre: comparar 1:1 Android/Web/iOS con la misma sesión y ruta, y ejecutar una E2E
desechable de Storage/PostgREST que cubra publicación y rollback/limpieza. La corrección de la fila
obsoleta de `MULTIPLATFORM_INVENTORY.md` se mantiene como cambio documental separado; no eleva las
capacidades ni oculta los límites del manifiesto.

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
`C:\Users\PC\Desktop\QÜATA\migration-v2\evidence\notifications\3147b928-ios\seed.log`,
`C:\Users\PC\Desktop\QÜATA\migration-v2\evidence\notifications\3147b928-ios\bootstatus.log`; plan/rutas:
`C:\Users\PC\Desktop\QÜATA\migration-v2\preflight\pr157\PLAN.md`.

## Cierre #170 — clasificador de impacto CI

PR #170 está fusionada como `main` `4c719072` (2 de agosto de 2026). El candidato certificado
tuvo base `1fe3bf74`, head `66052c95` y merge sintético `4f3ce660`. La primera ronda `fca3` quedó
invalidada por un P1 del clasificador que omitía orígenes `D`/`T`/rename y se canceló: se conserva
sólo como diagnóstico. El candidato corregido pasó preflight exacto **137/137**, Wave2 **245/245**,
`actionlint`, `diff --check` y revisión independiente **GO**.

La certificación final remota del head integrado concluyó verde: fast contracts Web/iOS, distribución
Wasm/Chrome, host/simulador/archive Kotlin iOS, tests Android/KMP, lint Android, CodeQL y ambos
gates finales Web/Android e iOS. Tras el merge la referencia remota
`codex/platform-change-classifier` ya no existe; esa limpieza confirma el cierre de la candidata,
no una nueva evidencia de producto.

## Próxima cola

1. Cerrar la candidata activa #187 sin bloquear trabajo independiente y promocionar `codex/official-editor-rebuild-v2` para `SCR-OFFICIAL-EDITOR`: Web/iOS ya montan `OfficialPostEditorRoot`, publican mediante planes comunes, suben adjuntos con los transportes reales y tienen evidencia REST reversible; queda evidencia visual integrada Android-Wasm-iOS y mantener traduccion automatica Web/iOS como limite documentado.
2. Cerrar los límites de #175: `PROF-*`, `FLOW-COMMUNITY-CHAT`, entradas/retornos globales y duración/seek iOS de `OVR-MEDIA`, mediante datos reales y mutaciones reversibles con limpieza.
3. Completar los postflights de `SCR-NOTIFICATIONS`, `SCR-CONVERSATIONS`, `SCR-WHATS-NEW`, `SCR-RELEASE-HISTORY`, `SCR-ACCOUNT`, `SCR-SOS` y `SCR-CREATE-POST`; una raíz integrada no equivale a GO.
4. Cerrar la evidencia Auth #168: sesión restaurada caducada en el mismo data-container, seeder realmente ejecutado y relanzamiento sin reinstalar.
5. Mantener integración secuencial y ejecución local paralela: una sola candidata final activa; GitHub Actions certifica un SHA ya congelado.
6. Configurar firma Apple y completar APNs/dispositivo físico en carriles independientes. Mantener RLS-001..005 documentados; no cambiar políticas en este backlog.

## Decisiones vigentes

- Kotlin `2.2.21` y Compose `1.10.0` permanecen fijados. Kotlin `2.3.20`/Compose `1.11.0` se rechazó porque Compose 1.11 no publica `iosX64` para el carril Intel y no acredita el Skiko CPU-raster.
- CI conserva la prueba Metal estricta. CPU-raster Intel es un carril suplementario, no una relajación del contrato.
- `migrationComplete`, `webReady` e `iosReady` siguen siendo `false` hasta terminar los gates externos de autenticación, firma, APNs/dispositivo y backend.
