# Tablero operativo de migración multiplataforma

> Fuente de verdad del método de trabajo: [`MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`](./MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).
> Este tablero es una fotografía de progreso y no puede redefinir los gates, la arquitectura ni el
> presupuesto de ejecución establecidos allí.

## Foto de control — 2026-08-09

**HEAD integrado:** `main` `4fffa2f87a3ebd86e014aeedaa2f5737eca5aed4` (PR #215), posterior a #154,
#156, #159, #168, #169, #170, #172, #173, #174, #175, #190, #191, #192, #193, #204, #206, #208,
#210, #211, #212, #214 y #215. El proyecto sigue incompleto: #215 cierra `SCR-AUTH-RECOVERY` con
raíz común Android/Wasm/iOS, evidencias focales y reales, limpieza Supabase verificada y certificación
final verde. No hay GO de `SCR-OFFICIAL-EDITOR` hasta completar error y comparativa
Android-Wasm-iOS. La migracion RLS `20260808_0001_official_posts_actor_guard.sql` fue aplicada
remotamente como SQL exacto versionado, sin `supabase db push` ni `migration repair`; el gate real
`OFFICIAL-EDITOR-REAL-BACKEND-001` paso con cuenta no oficial denegada, publicacion oficial, lectura
publica y hard-delete verificado.

| Área | Estado | Qué acredita | Límite vigente |
| --- | --- | --- | --- |
| Web/Wasm | GO limitado | Shell público, rutas principales y varias raíces Compose comunes están integrados; #154 incorpora `CreatePostRoot` y #156 `ProfileScreenHost`. | Faltan postflights autenticados por flujo y paridad visual exacta. Avatar Web se acredita por contratos, no por una mutación E2E real guardada y limpiada. |
| Presupuesto Wasm | Integrado | Watchdog sin ventanas visibles, baseline Linux aprobado y captura canónica reproducible. | Windows sigue siendo diagnóstico: el artefacto Wasm/JS depende del host. El presupuesto es un gate técnico, no un SLO de producto. |
| Android | GO limitado | Build, `install -r`, arranque frío y Feed anónimo API-37 con 0 crash/ANR tras cold boot. | Falta matriz autenticada controlada; no se modifica Android publicado ni el Feed anónimo. |
| iOS CI | GO limitado | CI y contratos Swift/Kotlin siguen siendo gates obligatorios; #156 restauró el acceso Swift/Kotlin requerido por el host. | No prueba IPA/TestFlight/APNs/dispositivo físico. El runner auth debe rechazar explícitamente `SKIPPED`/no ejecutado aunque `xcodebuild` devuelva 0. |
| iOS simulador | GO funcional suplementario | El postflight de `main` `5d2a52d1` pasó Feed y perfil remoto públicos; auth real ejecutada mediante `.xctestrun` con `QUATA_IOS_AUTH_E2E_FILE`; relanzamiento normal sin reinstalar conserva/restaura sesión; Cuenta/Perfil visual PASS. En #215, `IOS_AUTH_RECOVERY_REAL_UI_GATE_PASSED` ejecutó recovery real con `IosAuthRepository` sobre `9810c142` tras añadir `imePadding()` común a Auth y fijar el idioma de evidencia a español; las capturas muestran copy común y submit visible con teclado. | SOS es parcial: acceso/estado y 1/5 contactos visibles; el puntero remoto no automatizó la navegación de forma fiable. CPU-raster no es SLA ni reemplaza CI ARM. |
| Crear publicación (#154) | COMÚN con límites | `CreatePostRoot` común está integrado en Android, Wasm e iOS. | La evidencia de #154 no debe presentarse como GO visual/funcional final: validar publicación, adaptadores de medios y paridad autenticada sin modificar RLS. |
| Cuenta/Perfil/SOS (#156) | COMÚN con límites | `ProfileScreenHost` común integrado; postflight iOS de Feed/perfil público, auth, relanzamiento y Cuenta/Perfil visual PASS. | Completar el subflujo SOS sin ocultar que sólo se verificaron 1/5 contactos; avatar Web continúa contractual sin mutación E2E acreditada. |
| Comunidades/perfil público (#175) | COMÚN con límites | `NeighborhoodsScreenHost` y `CommunityProfileScreenHost` integrados en Android, Wasm e iOS; repositorios reales, entradas globales y gate de sesión conectados. | P2 vigentes: mutaciones `PROF-*`, entradas visuales desde Oficial/Conversaciones/Chat, listas anidadas, contenido/overlays, Chat↔Perfil, back Android de miembros y estados de error/retorno. |
| Feed iOS medios (#175, #206) | COMÚN con límites | Gradiente URL/hash detrás de vídeo, superficies UIKit/AVPlayer transparentes, controles Compose play/pause y mute global conectado a `AVPlayer`; #206 publica duración/posición reales desde `AVPlayerItem`/asset/rangos seekable y acredita `seekTo` mediante XCTest local con MP4 generado. | El límite específico de duración/seek iOS de Feed queda cerrado al integrar #206; no atribuye GO a los demás visores, entradas ni retornos de `OVR-MEDIA`. |
| Pipeline CI (#169) | Integrado, fail-closed | Preflight rápido local exacto, gates finales requeridos y concurrencia por PR sin cancelar evidencia de `main`/manual. | Aún no acredita producto; certifica candidatos ya validados localmente. |
| RLS/DB | Official backend corregido; GO UI pendiente | El bypass remoto de `official_posts` quedo corregido con RLS explicita y trigger `SECURITY INVOKER`. `OFFICIAL-EDITOR-REAL-BACKEND-001` paso: cuenta no oficial denegada, cuenta oficial publicada/leida y fila temporal limpiada por hard delete exacto. | La migracion se aplico como SQL exacto versionado, sin registrar `supabase_migrations` ni usar `migration repair`; conservar esta condicion en proximos rollouts. No declara GO de `SCR-OFFICIAL-EDITOR` hasta cerrar errores y comparativa Android-Wasm-iOS. |

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
| [#204](https://github.com/dossijeo/quata/pull/204) | `e2e8b8d5` | Feed/Official comments translator trigger parity: Web/Wasm e iOS ya no exponen triggers inertes de comentarios, el overlay compartido vive en `designsystem/commonMain`, Android conserva el controlador nativo y FastText queda como detector común con loaders Android/Web/iOS. No declara GO global hasta cerrar Chat/global translator y comparativa visual/operativa completa. |
| [#206](https://github.com/dossijeo/quata/pull/206) | `51929442` | Feed iOS publica duración/posición reales y `seekTo` desde el surface nativo hacia el chrome Compose común; XCTest genera un MP4 local y verifica duración + seek. No declara GO global de `OVR-MEDIA`. |
| [#208](https://github.com/dossijeo/quata/pull/208) | `e0aca480` | Official editor deja de exponer acciones Live/Ranking/overflow inertes en preview, comparte la detección de idioma FastText Android/Web/iOS y evita traducir automáticamente Fang/Unknown como si fueran el idioma de la UI. No declara GO global de `SCR-OFFICIAL-EDITOR` hasta cerrar publicación/adjuntos/errores y comparativa visual Android-Wasm-iOS. |
| [#210](https://github.com/dossijeo/quata/pull/210) | `817f2057` | Release History suma tags comunes de evidencia, cierre/prev/next verificables y scroll comun para notas largas. No declara GO global hasta comparativa visual/operativa Android-Wasm-iOS. |
| [#211](https://github.com/dossijeo/quata/pull/211) | `cc6d4f72` | Registra el cierre semantico de #210 en board/inventario sin cambiar producto. |
| [#212](https://github.com/dossijeo/quata/pull/212) | `0793ad80` | About QÜATA pasa a contenido comun Android/Web/iOS, Web deja de aliasar `about` a historial, iOS instala `QuataIosAboutViewController`, y el logo/menu abren About con enlace real a Release History y legales. No declara GO global hasta evidencia visual/operativa de About->Historial y retorno. |
| [#214](https://github.com/dossijeo/quata/pull/214) | `9004bcc0` | About/Release History añade ruta pública `#about`, entrada autenticada iOS y anclas comunes de evidencia; la certificación final Web/Android/iOS quedó verde con `candidate-final`. No declara GO global hasta completar evidencia visual/operativa About->Historial->retorno en Android-Wasm-iOS. |
| [#215](https://github.com/dossijeo/quata/pull/215) | `4fffa2f8` | Auth Recovery queda integrado como raíz común Android/Wasm/iOS con anclas comunes, tests common, evidencias Web/Android/iOS reales, backend reversible con Gabrielu restaurado y gates finales `candidate-final` verdes. `LANG-FASTTEXT-PARITY-001` mantiene FastText compartido como detector de idioma de referencia y bloquea identificadores básicos paralelos. |

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

1. `SCR-OFFICIAL-EDITOR` queda en GO local desde `1ddff1930ae17caa1d29d091f6a69cbdb4443a46`: Android, Web/Wasm e iOS acreditan publicacion real reversible, validacion/error comun sin mutacion, permisos UI no oficiales, limpieza exacta y comparativa visual final. Web/Wasm mantiene publicacion real reversible de texto, imagen y video con cleanup Storage/WordPress segun medio; iOS mantiene texto, imagen y video, y el cierre final sin medio sobre el SHA actual; Android mantiene publicacion real y ruta directa protegida por rol. Evidencias finales del mismo SHA: `build-reports/android/official-editor-real-evidence.json`, `build-reports/web/official-editor-final-visual-evidence-r2.json`, `build-reports/ios/official-editor-final-visual-evidence-r3.json` y `build-reports/official-editor/final-visual-comparison/official-editor-final-visual-comparison.md`. `LANG-FASTTEXT-PARITY-001` sigue siendo invariante: no reintroducir detectores basicos paralelos.
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
<!-- Actualizacion operativa 2026-08-09: main integrado e0aca4803749692246580c3278e4230965bdadd2 (PR #208) reduce `SCR-OFFICIAL-EDITOR`: Web/Wasm e iOS dejan de usar el idioma preferido como deteccion normal del editor y pasan por FastText compartido (`BrowserFastTextLanguageIdentifier`/`IosFastTextLanguageIdentifier`); Android usa el mismo helper comun con `QuataLanguageIdentifier`. Fang/Unknown y errores del modelo publican con fallback elegido sin traduccion automatica. Las previews del editor Official dejan de exponer acciones Live/Ranking/overflow sin callbacks reales. No declara GO global hasta evidencias Android-Wasm-iOS reales de publicacion, adjuntos, permisos UI, errores y limpieza. -->
<!-- Actualizacion operativa 2026-08-09: main integrado 817f205719c31d588cd25700ca5b3a616710681a (PR #210) reduce `SCR-RELEASE-HISTORY`: `ReleaseHistoryContent` comun gana tags de evidencia `release-history-*`, cierre/prev/next verificables y paginas con scroll vertical comun para notas largas; Android/Web/iOS conservan el montaje comun y no aceptan callbacks inertes. No declara GO global hasta comparativa visual/operativa Android-Wasm-iOS de catalogo, navegacion real y retorno. -->
<!-- Actualizacion operativa 2026-08-09: main integrado 0793ad804f30177cc4dabae76f8bdfd0e6422910 (PR #212) reduce `OVR-ABOUT`: Android/Web/iOS consumen `QuataAboutDialogContent` comun, Web separa la ruta `about` de `release-history`, iOS instala `QuataIosAboutViewController` con enlaces legales y el menu/logo abren About antes de navegar al historial. La ronda remota detecto un defecto escapado del preflight en `QuataFeedFrameworkTests`; se corrigio el XCTest y se anadio guard preventivo en `whats-new-release-history-contract.test.mjs`. No declara GO global hasta comparativa visual/operativa Android-Wasm-iOS de About, Historial y retorno. -->
<!-- Actualizacion operativa 2026-08-09: main integrado 4fffa2f87a3ebd86e014aeedaa2f5737eca5aed4 (PR #215) cierra `SCR-AUTH-RECOVERY`: formulario, repositorio, evidencia Web/Android/iOS, reset real reversible con Gabrielu y gates finales Web/Android/iOS verdes. La rama remota del candidato debe quedar limpia tras el merge; la cola vuelve a `SCR-OFFICIAL-EDITOR`. -->
<!-- Actualizacion operativa 2026-08-09: candidato local d908839b8be8542481b1f1fccbfcf7abe69ce596 reduce `SCR-OFFICIAL-EDITOR`: Web/Wasm real pasa texto, imagen y video sobre el SHA candidato con PostgREST 201, readback, hard-delete, Storage cleanup de imagen y WordPress video delete+404 verificado; iOS pasa build SimulatorSigned Intel, seeder Keychain real y `IOS_AUTHENTICATED_OFFICIAL_EDITOR_UI_GATE_PASSED` tras enriquecer `isOfficial` desde `community_profiles` autenticado. No declara GO global: iOS todavia no publica/limpia un post real ni cubre adjuntos/permisos/error, y falta comparativa visual Android-Wasm-iOS final. -->
<!-- Actualizacion operativa 2026-08-09: candidato local 44ae38392c0877bdcc591d39e30138299a59253e reduce `SCR-OFFICIAL-EDITOR`: iOS pasa build SimulatorSigned Intel y `OFFICIAL-EDITOR-IOS-REAL-UI-001` con publicacion real reversible desde el editor oficial mediante campos avanzados comunes; Supabase verifico creacion/readback, hard-delete exacto de 1 `official_posts`, translation_group_id resuelto y ausencia post-cleanup. Evidencia: `build-reports/ios/official-editor-real-evidence.json` y `build-reports/ios/official-editor-real-evidence/mac-ui-report`. No declara GO global: faltan adjuntos iOS, permisos/error y comparativa visual Android-Wasm-iOS final; el cuerpo rich text comun queda observable pero la escritura directa por XCUITest Compose iOS sigue como borde de automatizacion a cerrar en `FLOW-RICH-TEXT`. -->
<!-- Actualizacion operativa 2026-08-09: candidato local 1a6f7334260447fecaa89fdbab3f3c159defcad4 reduce `SCR-OFFICIAL-EDITOR`: iOS pasa `OFFICIAL-EDITOR-IOS-REAL-UI-001 --media image` con seleccion por slot iOS opt-in, preview comun `official-editor-media-preview`, publicacion real, readback Supabase con `media_url`, borrado Storage community-posts por API, verificacion read-only de ausencia en `storage.objects`, hard-delete exacto de 1 `official_posts` y ausencia post-cleanup. Evidencia: `build-reports/ios/official-editor-real-evidence.json` y `build-reports/ios/official-editor-real-evidence/mac-ui-report`. No declara GO global: faltan video iOS, permisos/error y comparativa visual Android-Wasm-iOS final. -->
<!-- Actualizacion operativa 2026-08-09: candidato local 8b72c3c395a1ffd5015359f18088f2cee91c4089 reduce `SCR-OFFICIAL-EDITOR`: iOS pasa `OFFICIAL-EDITOR-IOS-REAL-UI-001 --media video` con fixture MP4, seleccion por slot iOS opt-in, preview comun `authenticated-official-editor-real-video-preview`/`official-editor-media-preview`, publicacion real, readback Supabase, WordPress video delete+404 verificado, hard-delete exacto de 1 `official_posts` y ausencia post-cleanup. El host iOS actualiza el estado comun del medio de video antes de esperar thumbnail nativo para que la preview comun no dependa de AVFoundation. Evidencia: `build-reports/ios/official-editor-real-evidence.json` y `build-reports/ios/official-editor-real-evidence/mac-ui-report`. No declara GO global: faltan permisos/error y comparativa visual Android-Wasm-iOS final. `LANG-FASTTEXT-PARITY-001` sigue bloqueando detectores basicos paralelos: el detector de referencia es FastText compartido Android/Web/iOS. -->
<!-- Actualizacion operativa 2026-08-09: candidato local 299973d07ea64a94c870210902316cebddac5c60 reduce `SCR-OFFICIAL-EDITOR`: permisos UI pasan en Android, Web/Wasm e iOS sobre el mismo SHA. Los runners fuerzan temporalmente `is_official=false` para la cuenta autorizada, verifican que no se monta el editor ni aparece el CTA/ruta directa (`OFFICIAL-EDITOR-ANDROID-PERMISSIONS-001`, Web `--expect-ineligible`, iOS `--expect-ineligible`), no solicitan publicacion, comprueban ausencia de filas temporales y restauran el rol original. Android corrige ademas el grafo: `official/editor` ya no monta `OfficialPostEditorRoute` si la sesion no es oficial. Evidencias: `build-reports/android/official-editor-permissions-evidence.json`, `build-reports/web/official-editor-permissions-evidence.json`, `build-reports/ios/official-editor-permissions-evidence.json`. El error de validacion comun del editor ya queda cubierto en Android/Web/iOS por `official-editor-feedback` y ausencia de mutacion backend ante borrador vacio; no reintroducir detectores de idioma basicos, `LANG-FASTTEXT-PARITY-001` mantiene FastText como referencia Android/Web/iOS. No declara GO global: falta comparativa visual Android-Wasm-iOS final. -->
<!-- Actualizacion operativa 2026-08-09: candidato local 1ddff1930ae17caa1d29d091f6a69cbdb4443a46 declara GO local de `SCR-OFFICIAL-EDITOR`: Android, Web/Wasm e iOS pasan en el mismo SHA publicacion real reversible, validacion/error comun sin mutacion, permisos UI no oficiales ya acreditados, limpieza exacta y comparativa visual final. Se corrigieron los runners Web/iOS para no aceptar falsos positivos de input/publicacion: Web publica desde campos avanzados comunes y iOS exige cierre real del editor antes de dar por visto el post. Evidencias: `build-reports/android/official-editor-real-evidence.json`, `build-reports/web/official-editor-final-visual-evidence-r2.json`, `build-reports/ios/official-editor-final-visual-evidence-r3.json`, `build-reports/official-editor/final-visual-comparison/official-editor-final-visual-comparison.md`. -->
