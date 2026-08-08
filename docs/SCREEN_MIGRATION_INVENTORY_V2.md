# Inventario maestro de pantallas y flujos — migración Compose Multiplatform

> Fuente de verdad del método de trabajo: [`MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`](./MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).
> Este documento es la fuente de verdad del **alcance de producto** de la migración. El tablero y el
> registro de ejecución pueden ordenar el trabajo, pero no pueden omitir ni declarar terminada una
> superficie incluida aquí.

Base consolidada auditada: `main` en `702fb7174a758778e4f5d8f2ded0b6853378208f`
(4 de agosto de 2026), después de #154, #156, #159, #168, #169, #170, #172, #173, #174 y #175.

Android es la referencia funcional y visual. El objetivo no es que las tres plataformas tengan
archivos con nombres parecidos: Android, Wasm e iOS deben consumir la misma raíz Compose de
`commonMain`, con adaptadores de plataforma únicamente para capacidades del sistema.

## Cómo se mantiene este inventario

1. Cada pantalla, panel global o subflujo tiene un identificador estable. Una PR debe citar los ID que
   modifica y no puede atribuirse el cierre de filas que no haya validado.
2. Una fila sólo pasa a **GO** si satisface la Definition of Done del modelo operativo en Android,
   Wasm e iOS sobre el candidato integrado exacto. Un host, ViewModel, test o build aislado no es GO.
3. Una pantalla principal no queda terminada si alguno de sus subflujos obligatorios permanece
   `AUSENTE`, `FALLBACK` o `PARCIAL`. Puede indicarse `COMÚN con límites`, enumerando esos ID.
4. Después de cada merge se actualizan en el mismo lote: SHA base, estado, PR integrada, límites,
   evidencia y siguiente bloqueo. No se conserva como vigente el estado de una rama ya obsoleta.
5. Las candidatas abiertas son trabajo en curso. Sus checks históricos no modifican el estado de
   `main` y deben repetirse sobre el nuevo head/merge sintético cuando cambie su base.
6. Si aparece en Android una ruta, overlay, diálogo o modo funcional no registrado, se añade aquí
   **antes** de asignar su migración. El inventario nunca se reduce para hacer coincidir la cola.

## Estados

- **GO**: raíz común conectada y equivalencia funcional/visual acreditada en las tres plataformas.
- **COMÚN con límites**: raíz común en `main`; quedan subflujos o evidencias concretas pendientes.
- **PARCIAL**: se comparten modelos, ViewModels o contenidos, pero no la composición completa.
- **FALLBACK**: alguna plataforma monta una pantalla alternativa, simplificada o browser-style.
- **AUSENTE**: no existe una superficie de producto equivalente conectada.
- **NO APLICA**: herramienta interna deliberadamente específica de plataforma y no parte del producto.

## A. Pantallas y paneles principales

| ID | Superficie Android de referencia | Entradas y responsabilidad | Estado consolidado en `main` | Candidato o siguiente cierre |
|---|---|---|---|---|
| `SCR-AUTH-LOGIN` | `LoginScreen` | Entrada anónima, restauración de sesión, errores, navegación y retorno a la ruta solicitada. | **COMÚN con límites.** `AuthProductHostContent` se consume en Android, Wasm e iOS. Faltan recorridos finales de sesión caducada, retorno y logout. | Validación funcional real; no crear formularios HTML ni Swift alternativos. |
| `SCR-AUTH-REGISTER` | `RegisterScreen` | Alta, validación, challenge/configuración pública y retorno. | **COMÚN con límites.** Comparte raíz de Auth; falta E2E real en los despliegues configurados. | Cerrar como subflujo de Auth sin sustituir backend por mensajes de “no disponible”. |
| `SCR-AUTH-RECOVERY` | `ForgotPasswordScreen` | Solicitud, confirmación, errores y regreso al login. | **COMÚN con límites.** Raíz compartida; falta E2E de recuperación. | Evidencia funcional y de navegación en las tres plataformas. |
| `SCR-FEED` | `FeedScreen` | Feed principal, paginación, acciones de publicación, perfiles, detalle, comentarios, medios, ranking y traducción. | **COMÚN con límites.** `FeedScreenHost` es común. #175 integra en iOS el gradiente determinista URL/hash detrás del medio, controles Compose de play/pause y mute global conectado a `AVPlayer`; #182 alinea Wasm con degradado URL/hash visible cuando el decoder no tiene frames y controles inferiores Compose con play/pause y mute/unmute. Persisten deuda landscape iOS, emoji Wasm y subflujos `OVR-PUBLIC-PROFILE`, `OVR-POST-DETAIL`, `OVR-COMMENTS`, `OVR-MEDIA`, `OVR-LIVE-RANKING` y `FLOW-TRANSLATOR`. | Conservar la evidencia exacta `feed/5fd040ae-ios-gradient`; cerrar duración/seek iOS y los demás límites por ID sin reabrir la raíz. |
| `SCR-OFFICIAL` | `OfficialFeedScreen` | Canal oficial, detalle, medios, comentarios, perfiles, traducción y acciones autorizadas. | **COMÚN con límites.** `OfficialFeedScreenHost` está integrado; retorno a Auth y overlays transversales siguen pendientes. | Mantener el GO ya acreditado de la raíz y validar sólo los límites afectados. |
| `SCR-OFFICIAL-EDITOR` | `OfficialPostEditorScreen` | Crear/editar publicacion oficial, adjuntos, traduccion, permisos, publicacion y errores. | **PARCIAL/COMUN con limites; GO bloqueado por RLS remoto.** PR #190 integrado en `main` (`99ae1ef4`): Web expone la accion real del editor oficial, conserva la elegibilidad comun (`state.currentUser?.isOfficial == true`) y queda certificado por evidencia hermetica Web con identidad de PR (`--require-pr-identity`). PR #192 integrado en `main` (`ae6af455`): `OfficialPostEditorRoot` incorpora anclas `testTag` comunes, mantiene validacion/fail-closed en `commonMain` y acredita en Web borrador invalido + publicacion denegada sin mutar Supabase real. Evidencia reversible local sobre `main` `59fc98f0` (`OFFICIAL-EDITOR-REAL-BACKEND-001`) demostro que `official_posts` acepta una publicacion PostgREST de cuenta no oficial; el runner hizo hard delete exacto y verifico ausencia. Existe migracion candidata local `20260808_0001_official_posts_actor_guard.sql` para cerrar el bypass con RLS explicita y trigger `SECURITY INVOKER`, pero no se ha desplegado remoto. El estado quick/advanced, validacion, media, preview, draft, copy comun y plan de traducciones viven en comun; Android, Web e iOS montan `OfficialPostEditorRoot` mediante slots de plataforma. Web usa ruta dedicada `official-editor`; iOS instala `QuataOfficialEditorViewController` desde la factory autenticada. Traduccion automatica Web/iOS sigue como limite explicito: publican el idioma actual mientras Android conserva traductor real. | No declarar GO hasta desplegar/certificar la correccion RLS autorizada, reejecutar la evidencia backend real con cuenta no oficial denegada y cuenta oficial publicada/leida/limpiada, y capturar Android-Wasm-iOS en el mismo flujo/sesion con publicacion real, adjuntos, permisos, errores, limpieza comprobada y comparativa visual Android<->Web<->iOS. |
| `SCR-COMMUNITIES` | `NeighborhoodsScreen` | Directorio, búsqueda, comunidades, miembros, secciones, navegación y estados vacío/error. | **COMÚN con límites.** #175 integra `NeighborhoodsScreenHost` como raíz consumida por Android, Wasm e iOS, con repositorios reales y gate de sesión para acciones privadas. | Quedan `FLOW-COMMUNITY-CHAT`, back de sistema Android en la subruta de miembros y recorridos funcionales/visuales de error y retorno. |
| `OVR-PUBLIC-PROFILE` | `CommunityProfileScreen` | **Panel global**, no subpantalla exclusiva de Comunidades. Se abre desde Feed, Oficial, Comunidades, Conversaciones y Chat. Incluye cabecera, seguidores/seguidos, publicaciones/galería, comentarios, seguir, conversación privada, roles, administración, reporte, bloqueo y adjuntos. | **COMÚN con límites.** #175 integra `CommunityProfileScreenHost` en Android, Wasm e iOS y conecta las entradas globales; no cierra por ello las mutaciones ni todos los recorridos `PROF-*`. | Mantener pendientes los límites enumerados en `PROF-ENTRY`, `PROF-HEADER`, `PROF-FOLLOW`, `PROF-FOLLOW-LISTS`, `PROF-CONTENT`, `PROF-PRIVATE-CHAT`, `PROF-ROLES` y `PROF-SAFETY`. |
| `SCR-CONVERSATIONS` | `ConversationsScreen` | Inbox, búsqueda, favoritos, candidato de nueva conversación, invitaciones, avatares y entrada a Chat/perfil. | **COMÚN con límites.** #173 integró `ConversationsScreenHost` y transporte realtime por plataforma. | Postflight de los subflujos `CONV-*`; no confundir la lista visible con el flujo completo. |
| `SCR-CHAT` | `ChatScreen` | Conversación privada/grupal, mensajes, realtime, adjuntos, audio, reenvío, perfiles, traducción, mapa/SOS y administración. | **COMÚN con límites.** `ChatProductHostContent`/`ChatScreenHost` se consume en Android, Wasm e iOS; la ruta Android activa es `AndroidChatProductScreen` y Web/iOS ya no montan el fallback browser-style como producto. | Cerrar `CHAT-*` individualmente con evidencia real; no declarar GO hasta completar composer, acciones, adjuntos/audio, grupo, perfiles, traducción, mapa/SOS y retorno. |
| `SCR-NOTIFICATIONS` | `NotificationsScreen` | Lista, badge, apertura de destino, marcado/estado, vacío/error, perfil y retorno. | **COMÚN con límites.** #172 integró `NotificationsHostContent` en las tres plataformas. | Postflight funcional/visual del SHA integrado y destinos reales; ya no debe figurar “#157 no integrada”. |
| `SCR-ACCOUNT` | `ProfileScreen` | Cuenta propia, datos, avatar, preferencias vinculadas, seguridad, ciclo de cuenta y acceso a SOS. | **COMÚN con límites.** #156 integró `ProfileScreenHost`. No equivale al perfil público `OVR-PUBLIC-PROFILE`. | Cerrar `ACCOUNT-*`, avatar Web real y navegación/retorno. |
| `SCR-SOS` | subflujo SOS de `ProfileScreen` | Configuración, contactos, alta/baja, estado, permisos, errores y persistencia real. | **COMÚN con límites.** La raíz está integrada, pero sólo se acreditó acceso parcial y 1/5 contactos; no las mutaciones. | E2E reversible con los cinco contactos/estados y limpieza posterior. |
| `SCR-CREATE-POST` | `CreatePostScreen` | Texto, audiencia/destino, adjuntos, ubicación, edición de medios, publicación, progreso, cancelación, rollback y errores. | **COMÚN con límites.** #154 integró `CreatePostRoot`; faltan operaciones reales y `POST-*`. | Postflight autenticado con backend/Storage real y limpieza; no dar GO sólo porque abre el compositor. |
| `SCR-WHATS-NEW` | `WhatsNewScreen` | Descubrimiento de versión, contenido, cierre y persistencia de visto. | **COMÚN con límites.** #159 integró la raíz común; #174 actualizó baseline Wasm. | Validar aparición real, versión, cierre y no repetición. |
| `SCR-RELEASE-HISTORY` | `ReleaseHistoryScreen` | Catálogo, navegación desde About/Novedades, detalle y retorno. | **COMÚN con límites.** #159 integrado. | Validar catálogo y navegación en ruta real; enlazado con `OVR-ABOUT`. |

## B. Capacidades obligatorias de pantallas complejas

Estas filas evitan declarar GO por haber validado únicamente la primera pantalla visible. No todas
requieren una PR independiente, pero todas requieren estado y evidencia propios.

### Perfil público global (`OVR-PUBLIC-PROFILE`)

| ID | Capacidad | Estado consolidado y bloqueo siguiente |
|---|---|---|
| `PROF-ENTRY` | Abrir desde Feed, Oficial, Comunidades, Conversaciones y Chat; volver al origen sin perder estado. | **COMÚN con límites (#175).** Entradas y raíz están conectadas; falta evidencia visual desde Oficial, Conversaciones y Chat y cerrar las pilas de retorno encadenadas. |
| `PROF-HEADER` | Avatar, identidad, comunidad/rol, biografía y metadatos. | **COMÚN con límites (#175).** Datos/carga/error y apertura inmediata con caché están conectados; falta biografía y comparación completa de estados. |
| `PROF-FOLLOW` | Seguir/dejar de seguir y contadores. | **COMÚN con límites (#175).** Callbacks y repositorio real están conectados; falta E2E reversible de éxito, error, actualización y rollback. |
| `PROF-FOLLOW-LISTS` | Listas de seguidores y seguidos y apertura encadenada de perfiles. | **COMÚN con límites (#175).** Listas y navegación existen; faltan paginación, perfiles anidados y retorno completo. |
| `PROF-CONTENT` | Publicaciones, galería, detalle, comentarios y adjuntos. | **COMÚN con límites (#175).** Contenido real se monta en la raíz, pero `OVR-POST-DETAIL`, `OVR-COMMENTS` y `OVR-MEDIA` permanecen parciales. |
| `PROF-PRIVATE-CHAT` | Crear o abrir conversación privada. | **COMÚN con límites (#175).** Resolución y callback real conectados; falta acreditar éxito/error y retorno Chat↔Perfil. |
| `PROF-ROLES` | Roles, permisos y acciones administrativas de comunidad. | **COMÚN con límites (#175).** Controles y mutaciones están conectados según sesión; falta E2E reversible por rol y error. |
| `PROF-SAFETY` | Reportar, bloquear/desbloquear y moderar cuando corresponda. | **COMÚN con límites (#175).** Confirmaciones/callbacks reales conectados; falta persistencia E2E, error, rollback y limpieza. |

### Conversaciones (`SCR-CONVERSATIONS`)

| ID | Capacidad | Estado actual / pendiente |
|---|---|---|
| `CONV-INBOX` | Lista, paginación, unread, tiempo y realtime. | Raíz común integrada en #173; requiere postflight integrado. |
| `CONV-SEARCH-FAVORITES` | Búsqueda y favoritos. | Verificar comportamiento y estados vacío/error por plataforma. |
| `CONV-NEW` | Selector de candidatos y creación/apertura. | Verificar directorio real, unicidad y errores. |
| `CONV-INVITES` | Invitaciones/solicitudes y aceptar/rechazar. | No cerrar sin mutaciones reales reversibles. |
| `CONV-PROFILE` | Avatar → `OVR-PUBLIC-PROFILE` y retorno. | Depende de perfil global; no basta un callback no vacío. |

### Chat (`SCR-CHAT`)

| ID | Capacidad | Estado actual / pendiente |
|---|---|---|
| `CHAT-MESSAGES` | Historial, paginación, envío, edición/borrado si aplica, errores y realtime. | Raíz común conectada para lectura, carga inicial, error/retry, paginación y realtime en Android, Wasm e iOS. Envío, edición/borrado y mutaciones siguen pendientes de evidencia reversible por subflujo. |
| `CHAT-COMPOSER` | Compositor de texto, estado de escritura, respuesta, edición, cancelación de modo, emoji y envío con adjunto. | Composer común conectado a `ChatViewModel`; contrato común hermético cubre payload texto/adjunto, typing, reply/edit/cancel y rollback. Falta evidencia operativa de escritura, emoji, envío y errores con servicios reales por plataforma. |
| `CHAT-MESSAGE-ACTIONS` | Selección y acciones copiar, responder, reenviar, editar, reportar, favorito y borrar, con confirmaciones y permisos. | Barra y confirmaciones comunes conectadas; contrato común hermético cubre permisos de reply/edit/favorito/borrar/reportar, fallos y captura estable de selección antes de operaciones suspendidas. Falta acreditar copiar, mutaciones reversibles, errores y retorno visual en Android, Wasm e iOS. `CHAT-FORWARD` y `CHAT-FAVORITES` conservan estado propio. |
| `CHAT-FAVORITES` | Ruta/lista de mensajes favoritos, apertura del mensaje/conversación origen y alta/baja de favorito. | Ruta especial común conectada desde Android, Wasm e iOS a `FavoriteMessagesConversationId`; apertura del mensaje origen pasa por `onOpenMessageConversation`; el fallo de carga ya no se emite como snapshot vacío válido. Falta acreditar vacío, mutación reversible, retorno y foco visual real en las tres plataformas. |
| `CHAT-NOTIFICATIONS` | Silenciar/reactivar notificaciones de una conversación y reflejar el estado en cabecera/lista. | Mutación y persistencia reales pendientes de equivalencia Web/iOS y evidencia de retorno. |
| `CHAT-FOCUSED-MESSAGE` | Abrir Chat enfocando un mensaje desde notificación, favoritos, reenvío o deep link; paginar hasta encontrarlo, resaltarlo y consumir el foco una sola vez. | Existe contrato común de foco: espera snapshot autenticado, pagina historial, distingue mensaje ausente/error y consume el foco una sola vez. Falta evidencia visual/operativa sobre la misma conversación en las tres plataformas. |
| `CHAT-ATTACHMENTS` | Picker, imágenes, vídeo, documentos, subida, descarga y visor. | Adaptadores de sistema permitidos; estado/chrome/errores deben ser comunes. |
| `CHAT-AUDIO` | Grabación, permisos, envío y reproducción. | Adaptadores nativos; flujo de producto aún sin equivalencia acreditada. |
| `CHAT-FORWARD` | Reenvío y selector de destinos. | Selector común y estado compartido; #183 conserva selección/mensaje hasta éxito total, propaga `sent/errors` del RPC y trata parcial/fallo como error común reintentable. Falta evidencia visual/operativa Android, Wasm e iOS con backend real o fixture reversible autorizada. |
| `CHAT-GROUP` | Miembros, altas/bajas, nombre, roles y acciones administrativas. | Inventariar permisos y mutaciones; falta GO. |
| `CHAT-LOCATION-SOS` | Mapa, ubicación y mensajes SOS cuando corresponda. | Mapa es adaptador; modelo, navegación y estados deben ser comunes. |
| `CHAT-TRANSLATION` | Traducción de mensajes/comentarios. | Depende de `FLOW-TRANSLATOR`; no aceptar callback vacío. |
| `CHAT-PROFILE` | Avatar/miembro → perfil global y retorno. | Depende de `OVR-PUBLIC-PROFILE`. |

### Crear publicación (`SCR-CREATE-POST`)

| ID | Capacidad | Estado actual / pendiente |
|---|---|---|
| `POST-TEXT-DESTINATION` | Texto, audiencia/comunidad, validación y borrador. | Raíz común integrada; falta postflight completo. |
| `POST-PICKER-CAMERA` | Galería, cámara, permisos y cancelación. | Adaptadores de plataforma; flujo y resultado comunes. |
| `POST-IMAGE-EDITOR` | Recorte, rotación/transformación, preview, aceptar/cancelar. | Android conserva composición/editor específico; modelos comunes parciales. |
| `POST-VIDEO-EDITOR` | Preview, recorte, rotación, duración, subtítulos/exportación y cancelación. | Android conserva editor/pipeline específico; equivalencia no demostrada. |
| `POST-LOCATION` | Selección/eliminación de ubicación y permisos. | Validar en las tres plataformas con adaptador de mapa/ubicación. |
| `POST-PUBLISH` | Storage/PostgREST, progreso, éxito, error, reintento y rollback. | Falta E2E autenticado reversible y limpieza. |

### Cuenta y SOS (`SCR-ACCOUNT`, `SCR-SOS`)

| ID | Capacidad | Estado actual / pendiente |
|---|---|---|
| `ACCOUNT-AVATAR` | Selección, edición, subida, persistencia y rollback. | Contratos presentes; falta mutación Web/iOS acreditada y limpieza. |
| `ACCOUNT-SETTINGS` | Acceso a Ajustes y regreso a Cuenta. | Depende de `SCR-SETTINGS`. |
| `ACCOUNT-DEACTIVATE` | Desactivar cuenta con confirmación y error. | No consta como flujo multiplataforma cerrado. |
| `ACCOUNT-DATA-DELETE` | Solicitud/borrado de datos y confirmaciones. | No consta como flujo multiplataforma cerrado. |
| `SOS-CONTACTS` | Lista completa, añadir/eliminar/actualizar y estados límite. | Sólo evidencia parcial; mutaciones pendientes. |

## C. Pantallas secundarias, overlays y flujos transversales

| ID | Superficie | Referencia/entradas | Estado y obligación |
|---|---|---|---|
| `SCR-SETTINGS` | Ajustes, apariencia y cierre de sesión | Android lo integra con Perfil/grafo; Web tiene `WebSettingsHost`; iOS `QuataSettingsViewController`. | **PARCIAL.** Validar una composición común o justificar cada control como adaptador; tema, idioma/preferencias, logout, limpieza de sesión/Push y retorno deben coincidir. |
| `OVR-POST-DETAIL` | Detalle de publicación/artículo | Feed, Oficial y perfil público; Android dispone de paneles de detalle y en común existen `FeedPostDetailHostContent` y `OfficialPostDetailPanelContent`. | **PARCIAL.** Validar contenido completo, autor→perfil, acciones, enlace/artículo, comentarios, medios, back y restauración de scroll; una tarjeta del feed no demuestra el detalle. |
| `OVR-COMMENTS` | Panel/lista de comentarios | Feed, Oficial y perfil público. | **PARCIAL.** Estado, paginación, creación, respuesta, perfil, traducción y errores deben ser comunes; teclado es borde de plataforma. |
| `OVR-MEDIA` | Detalle/visor de imagen, vídeo y adjuntos | Feed, Oficial, perfil, Chat y documentos. | **PARCIAL.** #175 acredita en Feed iOS gradiente URL/hash, superficie nativa transparente, play/pause y mute global real; #182 acredita en Feed Wasm superficie nativa transparente, decoder oculto sin frames y chrome inferior común con play/pause y mute/unmute. Duración/seek iOS y los visores/retornos de las demás entradas siguen pendientes. |
| `OVR-LIVE-RANKING` | Ranking/panel Live | Feed y perfiles/comunidades donde Android lo ofrezca. | **PARCIAL/AUSENTE.** No se permiten datos o callbacks vacíos en Web/iOS. |
| `FLOW-TRANSLATOR` | Traductor Fang y backdrop global | Feed, Oficial, Chat y comentarios; Android captura fondo y monta modo global. | **PARCIAL.** Modelos, trigger y backdrop tienen piezas comunes, pero algunos hosts comunes usan `onClick = {}`. Requiere activación, estado, texto, errores y salida equivalentes; captura/render nativo puede ser adaptador. |
| `OVR-ABOUT` | About QÜATA | Menú/shell; enlaza versión, historial, legales y acciones informativas. | **PARCIAL.** Android lo monta en `AppNavGraph`; no estaba inventariado. Debe compartir contenido y navegación hacia `SCR-RELEASE-HISTORY`/legales. |
| `OVR-UGC-TERMS` | Aceptación de términos UGC | Primer uso o acción moderada. | **PARCIAL.** Existe contenido Compose común, pero falta inventariar persistencia, aceptar/rechazar, documentos y bloqueo del flujo. |
| `FLOW-LEGAL-DOCUMENTS` | Privacidad, términos y documentos legales | Auth, Ajustes y About. | **PARCIAL.** El renderer puede ser WebView/Quick Look/lector Android; selección, título, error y retorno son comunes. |
| `OVR-AUTH-REQUIRED` | Diálogo de autenticación requerida | Acciones privadas desde rutas públicas. | **COMÚN con límites.** Existe contenido común; validar destino original, cancelar, autenticar y regresar sin perder contexto. |
| `FLOW-EXTERNAL-SHARE` | Compartir hacia QÜATA | Android `ShareToQuataDialog`, Web Share Target e iOS Share Extension/inbox. | **PARCIAL.** Payload/estado tienen piezas comunes; validar texto, URL, Blob/archivo, selector de destinos, envío, descarte, errores y limpieza. |
| `FLOW-COMMUNITY-CHAT` | Comunidad/perfil → conversación | Directorio, perfil público y muro; resuelve comunidad, wall UUID y conversación autorizada antes de abrir Chat. | **COMÚN con límites (#175).** La resolución real y navegación están conectadas; faltan recorridos completos de éxito, comunidad sin muro/chat, permisos, error y retorno. |
| `FLOW-DOCUMENT-VIEWER` | Visor de PDF/Office/documentos | Adjuntos de Feed/Oficial/Chat y legales. | **PARCIAL.** Renderizadores son adaptadores; chrome, carga, error, descarga/compartir y retorno deben estar conectados. |
| `FLOW-SHELL-NAV` | Shell, navegación, deep links y retorno | Grafo Android, hash router Wasm y router iOS. | **PARCIAL.** Validar rutas públicas/privadas, Auth fullscreen, back, deep link, restauración y navegación repetida. |
| `FLOW-EMOJI` | Catálogo, picker y render de emoji | Feed, Oficial, comentarios y Chat. | **PARCIAL.** Tofu/glifos Wasm pendientes; no sustituir por HTML alternativo. |
| `FLOW-RICH-TEXT` | Edición y preview de texto enriquecido | Editor oficial y cualquier compositor que exponga formato/enlaces; Android conserva `QuataRichTextEditor`. | **PARCIAL.** Modelo, toolbar, selección, HTML/serialización, preview, errores y accesibilidad deben ser comunes. `RichTextEditorQaScreen` sólo es banco de prueba, no evidencia de producto. |
| `FLOW-SPLASH-STARTUP` | Splash, bootstrap y arranque | Launchers Android/Wasm/iOS, restauración de sesión y Novedades. | **PARCIAL.** `QuataSplashScreen` es común; comprobar secuencia, error, sesión y transición sin flashes/fallbacks. |
| `FLOW-IOS-LAYOUT` | Tamaño, orientación, insets y teclado | Todas las pantallas iOS. | **TRANSVERSAL pendiente.** Oficial corrigió landscape; Feed aún debe recibir layout real. Cada pantalla conserva su propia evidencia visual. |

## D. Superficies internas o deliberadamente no equivalentes

| ID | Superficie | Tratamiento |
|---|---|---|
| `INT-NEIGHBORHOOD-USERS` | `NeighborhoodUsersScreen` privada en Android | Es parte funcional de `SCR-COMMUNITIES`; no puede usarse como reemplazo autónomo del directorio común. |
| `INT-RICH-TEXT-QA` | `RichTextEditorQaScreen` | Herramienta interna Android. **NO APLICA** como pantalla de producto, salvo que se cree una demo multiplataforma explícita. |
| `INT-PLATFORM-PERMISSIONS` | Diálogos del sistema, picker, cámara, micrófono, notificaciones | Son adaptadores nativos. El estado previo/posterior, denegación y recuperación pertenecen al flujo común que los invoca. |

## E. Dependencias entre verticales

- `OVR-PUBLIC-PROFILE` es global. `SCR-COMMUNITIES` no puede declararlo cerrado únicamente porque
  el avatar del directorio sea accionable.
- `SCR-CONVERSATIONS` depende de `OVR-PUBLIC-PROFILE` para avatares y de `SCR-CHAT` para completar
  navegación. Puede quedar común con límites mientras Chat conserve subflujos pendientes, pero no GO global.
- `SCR-CHAT`, `SCR-FEED`, `SCR-OFFICIAL`, `OVR-POST-DETAIL` y `OVR-COMMENTS` dependen de
  `FLOW-TRANSLATOR`, `OVR-MEDIA` y `FLOW-EMOJI` según las capacidades visibles en Android.
- `SCR-CREATE-POST` no obtiene GO hasta cerrar los `POST-*` aplicables, aunque `CreatePostRoot`
  compile en los tres targets.
- `SCR-ACCOUNT` no cierra `SCR-SOS`, `SCR-SETTINGS` ni `OVR-PUBLIC-PROFILE`; son superficies
  diferentes aunque compartan datos de perfil.

## F. Cola consolidada desde esta base

1. Cerrar los límites de `SCR-CHAT` ya montado en raíz común: `CHAT-*`, evidencia visual/operativa y mutaciones reversibles.
2. Cerrar el bloqueo RLS de `SCR-OFFICIAL-EDITOR`: validar y desplegar de forma autorizada la migracion
   `20260808_0001_official_posts_actor_guard.sql`, y reejecutar `OFFICIAL-EDITOR-REAL-BACKEND-001`
   hasta obtener denegacion `42501` para cuenta no oficial, publicacion oficial, lectura y hard-delete verificado.
3. Cerrar la evidencia real de `SCR-OFFICIAL-EDITOR` desde `main` posterior a #193 usando las anclas comunes:
   publicacion, adjuntos, permisos, error, lectura backend, limpieza y comparativa Android-Wasm-iOS.
4. Cerrar los límites post-#175 de `SCR-COMMUNITIES`, `OVR-PUBLIC-PROFILE`, `PROF-*`,
   `FLOW-COMMUNITY-CHAT` y `OVR-MEDIA` mediante E2E reversibles y entradas/retornos globales.
5. Ejecutar postflight focal de integraciones ya presentes: `SCR-NOTIFICATIONS`,
   `SCR-CONVERSATIONS`, `SCR-WHATS-NEW`, `SCR-RELEASE-HISTORY`, `SCR-ACCOUNT`, `SCR-SOS` y
   `SCR-CREATE-POST`, actualizando aquí los límites cerrados.
6. Planificar las superficies que antes estaban omitidas: `SCR-SETTINGS`, `FLOW-TRANSLATOR`,
   `OVR-ABOUT`, `OVR-UGC-TERMS`, `FLOW-LEGAL-DOCUMENTS`, `FLOW-EXTERNAL-SHARE`, `FLOW-RICH-TEXT`,
   editores de medios y overlays de detalle/comentarios/medios/ranking.
7. Cerrar deuda transversal de shell, emoji, arranque y layout iOS sin reabrir raíces que ya estén
   acreditadas y cuyo diff no haya cambiado.

## G. Plantilla obligatoria para preparar una candidata

Antes de escribir código, el agente añade a su plan una tabla como ésta y la referencia en el
informe de evidencia:

| Campo | Contenido obligatorio |
|---|---|
| IDs afectados | Una o más filas exactas de este inventario. |
| Android de referencia | Archivo/composable, entradas, overlays y estado de sesión. |
| Raíz común prevista | Composable de `commonMain` que consumirán Android, Wasm e iOS. |
| Datos y lecturas | Repositorios/endpoints reales, carga, vacío y error. |
| Eventos y mutaciones | Callback por callback, éxito, error, rollback y limpieza. |
| Navegación | Entradas, back/return, Auth requerida, deep links y destinos secundarios. |
| Adaptadores permitidos | Sólo servicios/renderizadores de sistema; nunca UI de producto paralela. |
| Plataformas afectadas | Resultado del clasificador y compilaciones locales requeridas. |
| Evidencia pendiente | Funcional, visual Android↔Wasm/Android↔iOS, SHA y sesión equivalente. |

No se promociona una candidata si existe un callback de producto vacío, un control visible sin
función, una ruta alternativa simplificada, datos de ejemplo usados como producto o un ID dependiente
que la PR pretende declarar cerrado sin haberlo validado.
