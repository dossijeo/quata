# Tablero operativo de migración multiplataforma

> Fuente de verdad del método de trabajo: [`MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`](./MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).
> Este tablero es una fotografía de progreso y no puede redefinir los gates, la arquitectura ni el
> presupuesto de ejecución establecidos allí.

## Foto de control — 2026-08-04

**HEAD integrado:** `main` `59fc98f0980845c5ead581ccd0e12ec2fbc54f1c` (PR #193), posterior a #154,
#156, #159, #168, #169, #170, #172, #173, #174, #175, #190, #191 y #192. El proyecto sigue incompleto:
#192 integra semantica comun observable del editor oficial y evidencia Web hermetica de validacion/fallo.
#193 actualiza el estado documental y permite gates finales en PRs docs-only. La evidencia reversible local
del editor oficial sobre `59fc98f0` encontro que `official_posts` acepta una publicacion PostgREST de una
cuenta no oficial; el runner hizo hard delete exacto y verifico `remainingRows = 0`. No hay GO de
`SCR-OFFICIAL-EDITOR` hasta completar adjuntos, permisos UI y comparativa Android-Wasm-iOS. La migracion
RLS `20260808_0001_official_posts_actor_guard.sql` fue aplicada remotamente como SQL exacto versionado,
sin `supabase db push` ni `migration repair`; el gate real `OFFICIAL-EDITOR-REAL-BACKEND-001` paso con
cuenta no oficial denegada, publicacion oficial, lectura publica y hard-delete verificado.

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
| RLS/DB | Official backend corregido; GO UI pendiente | El bypass remoto de `official_posts` quedo corregido con RLS explicita y trigger `SECURITY INVOKER`. `OFFICIAL-EDITOR-REAL-BACKEND-001` paso: cuenta no oficial denegada, cuenta oficial publicada/leida y fila temporal limpiada por hard delete exacto. | La migracion se aplico como SQL exacto versionado, sin registrar `supabase_migrations` ni usar `migration repair`; conservar esta condicion en proximos rollouts. No declara GO de `SCR-OFFICIAL-EDITOR` hasta cerrar adjuntos, permisos UI, errores y comparativa Android-Wasm-iOS. |

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
| [#190](https://github.com/dossijeo/quata/pull/190) | `99ae1ef4` | Official editor Web expone la accion real desde Feed/Oficial mediante elegibilidad comun y evidencia hermetica con identidad de PR; no declara GO global de `SCR-OFFICIAL-EDITOR` hasta cerrar publicacion/validacion/adjuntos/error Android-Wasm-iOS con datos reversibles y comparativa visual. |
| [#192](https://github.com/dossijeo/quata/pull/192) | `ae6af455` | Official editor incorpora anclas `testTag` comunes en `commonMain`, validacion/fail-closed observable y evidencia Web hermetica de borrador invalido + publicacion denegada sin mutar Supabase real. Ajusta el job rapido Web/Android para instalar SDK 36.1 antes de imports KMP limpios. No declara GO global hasta cerrar publicacion real, adjuntos, permisos, lectura backend, limpieza y comparativa Android-Wasm-iOS. |
| [#193](https://github.com/dossijeo/quata/pull/193) | `59fc98f0` | Actualiza el board/inventario tras #192 y permite que los gates finales acepten PRs docs-only sin exigir `candidate-final`. No cambia producto ni RLS. |

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

1. Cerrar `SCR-OFFICIAL-EDITOR` con la semantica comun estable ya integrada: recorrer publicacion real, validacion, adjuntos, permisos y error en Android-Wasm-iOS con Gabrielo/Gabrielu o datos temporales reversibles, verificar lectura backend y limpieza, capturar comparativa visual y mantener traduccion automatica Web/iOS como limite documentado hasta que exista abstraccion comun real.
2. Mantener el postflight RLS de Official en cada cierre del editor: `OFFICIAL-EDITOR-REAL-BACKEND-001` debe seguir pasando y cualquier rollout futuro debe recordar que `20260808_0001_official_posts_actor_guard.sql` se aplico manualmente sin sincronizar historial de migraciones.
3. Cerrar los límites de #175: `PROF-*`, `FLOW-COMMUNITY-CHAT`, entradas/retornos globales y duración/seek iOS de `OVR-MEDIA`, mediante datos reales y mutaciones reversibles con limpieza.
4. Completar los postflights de `SCR-NOTIFICATIONS`, `SCR-CONVERSATIONS`, `SCR-WHATS-NEW`, `SCR-RELEASE-HISTORY`, `SCR-ACCOUNT`, `SCR-SOS` y `SCR-CREATE-POST`; una raíz integrada no equivale a GO.
5. Cerrar la evidencia Auth #168: sesión restaurada caducada en el mismo data-container, seeder realmente ejecutado y relanzamiento sin reinstalar.
6. Mantener integración secuencial y ejecución local paralela: una sola candidata final activa; GitHub Actions certifica un SHA ya congelado.
7. Configurar firma Apple y completar APNs/dispositivo físico en carriles independientes. Mantener RLS-001..005 documentados; no cambiar políticas fuera de release autorizado.

## Decisiones vigentes

- Kotlin `2.2.21` y Compose `1.10.0` permanecen fijados. Kotlin `2.3.20`/Compose `1.11.0` se rechazó porque Compose 1.11 no publica `iosX64` para el carril Intel y no acredita el Skiko CPU-raster.
- CI conserva la prueba Metal estricta. CPU-raster Intel es un carril suplementario, no una relajación del contrato.
- `migrationComplete`, `webReady` e `iosReady` siguen siendo `false` hasta terminar los gates externos de autenticación, firma, APNs/dispositivo y backend.
<!-- Actualizacion operativa 2026-08-08: HEAD integrado medido main b7b76b5e456a27d92b5f6eb5b9a806edc5c5c317 (PR #195). #194 versiono el cierre RLS de official_posts; #195 documento el despliegue remoto exacto de 20260808_0001_official_posts_actor_guard.sql y el postflight OFFICIAL-EDITOR-REAL-BACKEND-001 verde. El candidato OFFICIAL-EDITOR-WEB-REAL-UI-001 anade evidencia Web real opt-in; no declara GO de SCR-OFFICIAL-EDITOR hasta adjuntos, permisos UI, errores y comparativa Android-Wasm-iOS. -->
<!-- Actualizacion operativa 2026-08-08: candidato local e122205085f4e0654cea552071c6aa33ffad5c6b amplía la evidencia Web real del editor oficial con adjunto imagen. La lane local acredita picker, preview comun, publicacion visible, limpieza exacta de official_posts, borrado de Storage community-posts por path y verificacion read-only en storage.objects; el postflight RLS real sigue verde. Siguiente cierre: video/errores/permisos UI y comparativa Android-Wasm-iOS, sin degradar funcionalidad por presupuestos. -->
<!-- Actualizacion operativa 2026-08-08: main integrado 65565592dd8e355881c9a85386c832fb85ed03dd (PR #197) deja cerrada la evidencia Web/Wasm de imagen real del editor oficial. El candidato de esta rama cierra Web/Wasm video real con fixture MP4 versionada, nombre multipart con extension, preview comun, upload WordPress 200, publicacion PostgREST 201, cleanup WordPress por quqos_delete_post_video y ausencia post-cleanup verificada; permanece pendiente la comparativa Android-Wasm-iOS y permisos/errores UI antes de cualquier GO global. -->
<!-- Actualizacion operativa 2026-08-08: base medida main 392aae61ea76407bce056fdc766241dafd2417ad. Candidato OFFICIAL-EDITOR-PARITY-ERRORS-001: AuthSession transporta `isOfficial` en Android/Web/iOS, iOS persiste el permiso en Keychain de forma compatible, el router iOS oculta/bloquea `Crear comunicado` cuando la sesion no es oficial, Web/iOS sustituyen callbacks vacios de edicion de medio por reemplazo real via picker, y el runner Web de video exige readback 404/410 del archivo WordPress tras `quqos_delete_post_video`. No declara GO global: siguen pendientes comparativa visual Android-Wasm-iOS completa, rich text/traduccion equivalentes y evidencia final sobre SHA limpio. -->
<!-- Actualizacion operativa 2026-08-08: base medida main e1bfc7e61e812c96308a2b013419bfebf00dc385. Candidato OFFICIAL-EDITOR-COMMON-RICHTEXT-001: Web/Wasm e iOS dejan de usar `prompt`/`OutlinedTextField` como editor de cuerpo oficial y montan `QuataPortableRichTextEditorBox` desde `designsystem/commonMain`, con estado `QuataRichTextEditorState`, toolbar basica, serializacion HTML comun y contrato fail-closed. Android conserva su editor avanzado existente. No declara GO global de `SCR-OFFICIAL-EDITOR`: traduccion automatica sigue limitada a Android hasta crear un adaptador real Web/iOS o backend autorizado, y falta evidencia visual/operativa completa Android-Wasm-iOS sobre SHA final. -->
<!-- Actualizacion operativa 2026-08-08: base integrada main ee74915f1d0193d5c7a80ecb35cd37f82d4ad19f. Candidato OFFICIAL-EDITOR-IOS-RICHTEXT-EVIDENCE-001: la UI test autenticada iOS del editor oficial deja de certificar el boton compacto antiguo y pasa a exigir los nodos comunes `official-editor-body-action`, `quata-portable-rich-text-field` y `official-editor-preview`; el contrato rich-text fail-closed cubre esa evidencia. Pendiente: traduccion Web/iOS y comparativa visual/operativa Android-Wasm-iOS completa antes de GO. -->
<!-- Actualizacion operativa 2026-08-08: base integrada main 347a2fbe286cee798c113061534f53356766639b. Candidato OFFICIAL-EDITOR-WEB-PERMISSIONS-CTA-001: Web/Wasm deja de exponer el CTA del editor oficial por constante; `Main.kt` propaga `currentUserIsOfficial` desde la sesion restaurada/login, `WebOfficialHost` recibe ese permiso y `official-editor` redirige a `official` cuando la sesion autenticada no es oficial. La matriz de capacidades se revisa con el nuevo hash de `Main.kt`. No declara GO global: siguen pendientes traduccion Web/iOS y comparativa visual/operativa Android-Wasm-iOS completa. -->
<!-- Actualizacion operativa 2026-08-08: base integrada main 7acae540b2040c9ec99888d531d3761eb1bfa834. Candidato OFFICIAL-EDITOR-TRANSLATION-PARITY-001: Web/Wasm e iOS dejan de publicar el editor oficial en modo monolingue por `translator = null`; ambos hosts recuerdan `OfficialPostEditorFangTranslator` comun con transportes existentes Browser/iOS y el contrato hermetico prohibe regresar al fallback. La matriz de capacidades se revisa con el nuevo hash de `QuataOfficialViewController.kt`. No declara GO global: deteccion real Web/iOS, trigger de traductor en feed/comentarios y comparativa visual/operativa Android-Wasm-iOS siguen pendientes. -->
