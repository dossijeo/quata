# Tablero operativo de migración multiplataforma

Este tablero es la fuente persistente para despacho. El inventario mantiene la
arquitectura; este archivo mantiene estado, evidencia y dependencias.

## Reglas

- Consultar este tablero antes de crear una tarea. No reasignar una fila **Ya compartido**.
- Cada entrega vive en worktree/branch aislado y sólo es **Integrado** tras merge en `main`.
- **En revisión** no es completado: debe contener PR y validación vigente.
- Actualizar la fila al abrir PR, corregir un fallo, fusionar o descubrir un bloqueo externo.
- Tras merge verde, eliminar la rama remota y su worktree; nunca reutilizar una rama para otra feature.

## Leyenda

| Estado | Significado |
| --- | --- |
| Integrado | En `main` con evidencia de validación proporcional al lote. |
| En revisión | PR abierta; no cuenta como terminado. |
| Pendiente | Unidad válida todavía no asignada. |
| Bloqueado externo | Falta backend, credencial o contrato verificable. |
| Ya compartido | Auditado; extraer de nuevo duplicaría UI. |

## Foto de control — 2026-07-25 (`main` `e950f04`)

| Área/lote | Estado | Evidencia y siguiente acción |
| --- | --- | --- |
| CI iOS base y XCTest | Integrado | Workflow macOS compila los 14 targets Kotlin/Native, enlaza el framework y construye el host Swift; #25 añadió el XCTest de enlace. |
| Alcance CI iOS | Integrado | El workflow evita el trigger amplio de `codex/**`: valida PRs y las ramas efímeras `codex/next-*`, además de `main`/`master`. |
| Tests comunes Feed | Integrado con límite conocido | #8 cubre `RemoteFeedReadRepository` en `commonTest`. Si reaparece el fallo interno conocido de Compose JS en `wasmJsTest`, el gate de ese lote es `:feature:feed:compileKotlinWasmJs`; no se convierte ese test en un falso PASS. Es distinto del ICE de producción, ya resuelto. |
| Runner smoke Web | Integrado y verificado arquitectónicamente | El lote `a0d77ab` actualizó Kotlin/Compose a `2.2.21`/`1.10.0`; `:web:wasmJsBrowserDistribution` generó el bundle y `scripts/web-browser-smoke.mjs` pasó rutas no autenticadas. Esto no prueba login ni Supabase real. Ver [WASM_WEB_VALIDATION.md](WASM_WEB_VALIDATION.md). |
| Servicios Web base | Integrado | Cámara MediaDevices, audio Chat, pausa de polling, documentos, caché IndexedDB, thumbnails de vídeo, recuperación Auth y lifecycle de cuenta (#5, #17, #20, #21, #23, #24). |
| Servicios iOS base | Integrado | AVFoundation/deep links (#6), grabación/reproducción/caché de audio AVFoundation, MIME de galería y UTI Office (#14/#16), APNs bridge (#19), Quick Look (#30), Keychain con renovación de sesión (#13), transporte Auth nativo, ContactsUI, thumbnails Quick Look de documentos y thumbnails AVFoundation de vídeo locales. |
| Profile/SOS y UI compartida | Integrado | SOS/Profile (#9) y las acciones inyectables del host SOS iOS se integraron en `main`; Composer preview (#11), Chat title bar (#12), Feed media Web (#27), lista/burbuja Chat, detalle/comentarios Official y overlay fullscreen del Design System están en `commonMain` mediante slots. |
| Catálogo Auth común | Integrado | `AuthCatalog` centraliza prefijos, preguntas secretas y copy inglés/español/francés para Android, Web e iOS; las antiguas matrices Android se eliminaron y `commonTest` cubre el catálogo. |
| Playback Feed común | Integrado | `FeedReelVideoPlaybackHostContent` extrae la estructura visual y el estado de reproducción del reel a `commonMain`; Android conserva el reproductor/media y las APIs nativas como slots. |
| Hosts Web | Parcial, host real | `Main.kt` enruta Auth, Feed, Chat, Official, Notifications, Profile, Settings, Communities, Composer, WhatsNew/About y External Share. El bundle Wasm y el smoke de navegador no autenticado están verificados; faltan recorridos autenticados, datos efímeros y E2E remoto. |
| Bootstrap y launcher Feed/Auth iOS | Integrado, parcial funcional | `IosFeedRuntimeBootstrap` restaura una sesión Keychain y, con `QUATA_SUPABASE_URL` y clave publicable configuradas, crea dependencias PostgREST de sólo lectura sin datos Swift de ejemplo. Si no existe sesión, el composition root instala el host Auth común con el transporte iOS y, tras login, vuelve a instalar Feed. |
| Hosts iOS y smoke tests | Parcial | Profile/SOS (#32), Composer (#36), Official (#35), Notifications (#39), Chat (#40), Communities (#42), WhatsNew/About (#37), Auth (#29), Settings (#34), External Share (#41) y Feed (#4) exponen hosts inyectables. XCTest/UI smoke cubre el arranque/rearranque del composition root sin configuración, pero faltan recorridos autenticados y pruebas funcionales de adaptadores reales. |

## PRs activas — no integradas

| PR | Lote | Estado actual | Dependencia o siguiente acción |
| --- | --- | --- | --- |
| #3 `codex/security-hardening` | Endurecimiento de seguridad existente | En revisión (externa) | PR draft ajena al flujo de migración; no reutilizar ni borrar sin autorización explícita. |

## Lotes en desarrollo — sin PR

No hay lotes de migración activos sin PR en la foto de control. Los cinco lotes iOS
de Auth transport, launcher autenticado, ContactsUI, thumbnails Quick Look y smoke
tests ya están integrados en `main`; las próximas tareas deben abrir una rama nueva
en vez de reutilizar sus ramas efímeras.

## Dependencias de integración activas

| Grupo | Orden seguro |
| --- | --- |
| Feed y composición iOS | El transporte de lectura, `IosFeedRuntimeBootstrap`, transporte Auth y launcher autenticado están integrados. El bootstrap restaura Keychain y el launcher presenta Auth o Feed según la sesión; la siguiente evidencia necesaria es un recorrido configurado extremo a extremo, sin datos Swift de ejemplo. |
| Share | El Share Target Web y la UI External Share común están integrados; revisar su interacción con el host y almacenamiento del navegador al validar flujos reales. |
| Plataforma Web | Composer, Communities y WhatsNew/About ya enrutan desde `Main.kt`. Bundle Wasm de producción y smoke no autenticado verificados; coordinar rutas nuevas para evitar solapes y mantener las pruebas autenticadas separadas. |
| Hosts iOS | Los exports por feature, Auth/Feed launcher, ContactsUI y thumbnails existen. Las dependencias se siguen componiendo desde el launcher y se deben probar con permisos/configuración reales; no sustituirlas por datos de ejemplo Swift. |

## Auditoría de features

| Feature | Estado | Límite real restante |
| --- | --- | --- |
| Feed | Parcial | Dominio/estado, tarjeta de metadata, overlay de reel, controles de vídeo y host común de playback ya son comunes con slots de media/acciones/navegación. El bootstrap y launcher Auth/Feed iOS están integrados; faltan mutaciones backend verificables y validación extremo a extremo con runtime configurado. |
| Chat | Parcial | Estados, ViewModels, renderer, lista de conversaciones, burbuja de mensaje y controles estructurales son comunes; host iOS integrado. Quedan adjuntos/audio/mapa/traducción y Realtime/typing configurado de forma real. |
| Profile/SOS | Parcial | UI/estado, acciones inyectables del host SOS iOS, host Web enrutado y adaptador ContactsUI están integrados. Quedan permisos/selección real cubierta por pruebas, bootstrap de repositorios y navegación de host. |
| Communities | Parcial | Listados, miembros, KPI, comentarios, emoji, ranking, paneles y hosts Web/iOS integrados. En Web el panel de comentarios se mantiene correctamente de solo lectura: `WebNeighborhoodsHost` recibe sólo el texto (`onSubmitComment: (String) -> Unit`) y agrega los comentarios de todos los posts del perfil, por lo que no puede identificar el `postId` que exige `community_comments`. Además, `WebPostgrestClient` expone sólo `GET` y `WebFeedRepository.addComment` declara explícitamente `web_feed_mutation_not_implemented`. Habilitar el botón actual inventaría persistencia o publicaría contra un post arbitrario. Hace falta un contrato común por post y un transporte browser/RLS revisado; mientras tanto el host debe conservar `commentsEnabled = false`. Persisten también media, URI, audio y navegación específica de plataforma. |
| Official | Parcial | Dominio, listado/detalle, entrada de comentarios, editor y host iOS integrados. Quedan media/avatar/HTML/URI/navegación y mutaciones backend. |
| Post Composer | Parcial | Formularios, preview textual, controles de vídeo, previews estructurales y ruta/host Web e iOS integrados. Quedan cámara, galería, bitmap/vídeo, MediaStore y exportación. |
| Settings/Auth/WhatsNew/Notifications | Parcial | El catálogo Auth es común, el transporte/launcher Auth iOS están integrados y los hosts iOS inyectables de Settings/Auth/WhatsNew y Notifications existen; Web enruta Auth, Settings, Notifications y WhatsNew/About. Faltan configuración real de runtime y credenciales/pruebas de push. |
| External Share | Parcial | Política/payload común, Android, host iOS inyectable y Share Target Web están integrados. Falta la validación de flujo completo con datos reales y el bootstrap iOS autenticado para destinos que dependan de Feed. |

## Adaptadores y hosts

| Capacidad | Android | Web | iOS | Límite/siguiente acción |
| --- | --- | --- | --- | --- |
| Cámara/galería | Real | MediaDevices y metadata de navegador integrados | Cámara, PHPicker/UTI y selector de documentos existen como adaptadores | Completar edición/exportación y conectar los adaptadores desde hosts que los necesiten. |
| Audio | Real | Grabador/reproductor de navegador disponibles; Chat recibe reproductor | Grabación, reproducción y caché AVFoundation integradas | Falta cobertura de URI/caché inyectada y pruebas de host reales. |
| Vídeo | Media3/Android | Thumbnails de navegador integrados | Thumbnails AVFoundation de archivos locales integradas | Faltan edición/exportación y pruebas de medios reales. |
| Documentos | Real | Reader/open de navegador integrado; PDF abre con el visor nativo y RTF/Office descarga mediante URL normalizada (sin `javascript:`, `data:` ni Blob de otro origen) | Quick Look, tipos Office y thumbnails locales integrados | Faltan renderer/thumbnails multiplataforma completos y pruebas de archivos reales. |
| Sesión | Existente | localStorage/lifecycle integrado | Keychain renovable, transporte Auth y launcher Feed/Auth integrados | Requiere configuración runtime y recorrido extremo a extremo real. |
| Notificaciones | FCM/canales por auditar | Web Push y service worker integrados según Supabase | Bridge APNs/permiso/deep links como adaptadores | Requiere credenciales, registro desde el host y pruebas de entrega reales. |
| Navegación host | MainActivity/AppNavGraph | Launcher enruta los hosts migrados, incluido Composer, Communities, WhatsNew/About y External Share | Composition root UIKit instala Feed restaurado o Auth si no hay sesión | Completar pruebas autenticadas iOS y pruebas de rutas Web, sin duplicar UI. |

## Unidades pendientes verificables

| Prioridad | Unidad | Estado | Archivos o superficie de control | Criterio de salida |
| --- | --- | --- | --- | --- |
| P0 | E2E configurado Auth/Feed iOS | Pendiente de entorno | `iosApp/iosApp/QuataIosApp.swift`, `feature/feed/src/iosMain/**/IosFeedRuntimeBootstrap.kt` | Login/restauración → Feed contra URL y clave publicable de runtime, con cuenta efímera y sin datos Swift de ejemplo. |
| P0 | Pruebas funcionales de hosts iOS | Pendiente de entorno macOS | `iosApp/iosAppTests/`, `IosProfileSosHost.kt`, adaptadores ContactsUI/Quick Look/AVFoundation | XCTest/UI con permisos, selección de contacto y archivo local reales; el smoke de arranque actual no basta. |
| P0 | E2E navegador autenticado | Pendiente de entorno | `web/src/wasmJsMain/**/Main.kt`, `scripts/web-browser-smoke.mjs`, runners `scripts/run-supabase-e2e-sb*.ps1` | Bundle ya validado; ejecutar login/restauración/rutas con configuración pública y datos efímeros limpiados. |
| P1 | Mutaciones y tiempo real Web | Bloqueado externo | `WebFeedRepository.kt`, `WebOfficialRepository.kt`, `WebChatRepository.kt` | Contratos RPC/RLS/identidad aprobados y pruebas con dos usuarios; no implementar un transporte manual inseguro. |
| P1 | Comentarios Communities Web por post | Bloqueado externo | `WebNeighborhoodsHost.kt`, `WebPostgrestClient.kt`, contrato `community_comments` | Acción común que reciba `postId`, POST RLS revisado y E2E crear/listar/borrar; conservar `commentsEnabled = false` hasta entonces. |
| P1 | Media/documentos multiplataforma restantes | Pendiente | `core` contratos de media/documentos y hosts consumidores Android/Web/iOS | Edición/exportación, renderer/miniaturas y pruebas de archivos reales; no recrear adaptadores AVFoundation ya existentes. |
| P1 | Visor Office integrado Web con DocMentis | En revisiÃ³n | `web/`, interop Wasm/JS y host de documentos Web | `@docmentis/udoc-viewer` 0.7.9 se carga perezosamente para PDF/DOCX/PPTX/XLSX y conserva descarga segura para RTF/legacy/error; falta bundle y smoke real de cliente/browser antes de integrar. |
| P1 | Push entregado | Bloqueado externo | `supabase/WEB_PUSH_INTEGRATION.md`, service worker Web, bridge APNs/FCM | Credenciales, suscripción/dispositivo y entrega con deep link; revocación comprobada y sin tokens en repo. |
| P2 | Auditoría final | Pendiente | Inventario, `commonMain`, Gradle/CI y emulador API-37 | Imports Android, JS/Wasm, CI iOS, ensamblado/instalación/arranque Android y evidencia requisito por requisito. |

## Política de ramas

1. Programación: worktree `codex/*`, sin Gradle redundante por agente.
2. Integración: instantánea congelada; correcciones sólo en la misma rama.
3. Publicación: PR a `main`; CI iOS se dispara en PR y también en `main`/ramas `codex/next-*` según el workflow vigente.
4. Merge: sólo después de checks verdes proporcionales al lote; no hay excepción activa para el ICE Wasm de producción, ya resuelto.
5. Limpieza: tras merge verde, eliminar branch remota y worktree; una rama nunca se reutiliza para otra feature.

## Carril de validación Supabase

La validación contra Supabase es un trabajo independiente de la extracción de
código: puede ejecutarse en paralelo y no autoriza cambios de esquema,
migraciones, tablas, funciones ni `db push`. Usa exclusivamente usuarios y datos
efímeros identificables, una transacción de limpieza y una comprobación final de
que no quedan filas creadas por el lote. No registrar secretos, URLs de conexión
ni tokens en este repositorio, logs o informes.

| Área de prueba | Estado | Alcance y evidencia requerida |
| --- | --- | --- |
| Conectividad y catálogo de contratos | Pendiente | Consulta de solo lectura para confirmar tablas, RPC y políticas que consumen los repositorios KMP; registrar nombres de contrato y resultado, nunca credenciales. |
| Auth y restauración de sesión | Pendiente | Crear usuario de prueba, login/logout/restauración desde los adaptadores Web/iOS cuando el runtime esté configurado y borrar el usuario/datos dependientes. |
| Feed y Official de lectura | Runner preparado; pendiente de entorno | `scripts/run-supabase-e2e-sb03.ps1` valida lectura PostgREST autenticada/pública y forma de deep link sobre filas efímeras ya aprobadas. No existe un endpoint Web seguro para crear/borrar estos posts: el runner registra esa precondición en vez de simularla. |
| Chat y adjuntos | Pendiente | Dos usuarios efímeros, conversación, mensaje y adjunto permitido; comprobar transporte KMP, descarga sin cabeceras privilegiadas y limpieza de objeto/filas. |
| Profile/SOS | Pendiente | Perfil y hasta cinco contactos SOS efímeros, prueba de patch/normalización y restauración del estado previo o borrado completo. |
| Communities/comentarios | Pendiente | Validar el contrato por `postId`, RLS y mutación antes de habilitar Web; crear y eliminar comentario de prueba en la misma transacción lógica. |
| Notificaciones/push | Bloqueado externo | Las credenciales de APNs/Web Push/FCM y el dispositivo registrado son necesarios; mientras falten, sólo validar el repositorio y deep links con datos efímeros. |

Al cerrar una fila se debe anotar: commit validado, fecha, adaptador/plataforma,
casos ejecutados, identificadores efímeros ya eliminados y resultado de la
verificación de limpieza. Un fallo abre una unidad de corrección separada; no se
oculta como una prueba satisfactoria.

### Cola E2E delegable

Cada lote se ejecuta en una rama o commit ya compilado y usa un prefijo único
`e2e-<fecha>-<lote>` para usuario, post, mensaje, objeto de Storage y cualquier
otro dato creado. Un agente de pruebas no cambia DDL, migraciones, funciones,
políticas ni configuración remota; su único permiso mutante es crear y borrar
los datos de su propio prefijo. Antes de cerrar el lote debe consultar que no
quedan filas ni objetos con ese prefijo.

| ID | Prerrequisito | Recorrido E2E concreto | Estado | Evidencia de cierre |
| --- | --- | --- | --- | --- |
| SB-01 | URL de pooler presente en el entorno del proceso | Ejecutar `scripts/run-supabase-e2e-sb01.ps1`: catálogo PostgreSQL/Storage y RPC de adaptadores KMP, en transacción `READ ONLY`, sin datos de negocio ni secretos | Pendiente de entorno | Informe local sin secretos, listas `missing` vacías, commit y fecha; guía en `docs/SUPABASE_E2E_SB01.md` |
| SB-02 | Cuenta efímera ya aprovisionada por el flujo autorizado; URL y clave publicable en entorno | Ejecutar `scripts/run-supabase-e2e-sb02.ps1 -AllowExistingTestUser`: login Web bridge, forma de persistencia en memoria, refresh, logout Web, login posterior y revocación global final como limpieza | Pendiente de entorno | Informe seguro, `sessions_revoked` y borrado de la cuenta anotado por quien la aprovisionó; guía en `docs/SUPABASE_E2E_SB02.md`. `-CreateUser` rechaza el lote porque Web aún no expone un alta pública segura |
| SB-03 | SB-02 verde, dos filas aisladas ya aprovisionadas y contrato de visibilidad explícito | Ejecutar `scripts/run-supabase-e2e-sb03.ps1 -AllowExistingTestData`: Feed/Official autenticado, lectura publishable visible o denegada por RLS según contrato y forma de deep link | Runner preparado; pendiente de entorno | Informe seguro, sesiones revocadas y limpieza de las filas anotada por quien las aprovisionó; guía en `docs/SUPABASE_E2E_SB03.md`. No crea ni borra posts porque falta endpoint Web seguro revisado. |
| SB-04 | Dos usuarios efímeros preaprovisionados, SB-02 verde y purga externa autorizada de ambas cuentas | Ejecutar `scripts/run-supabase-e2e-sb04.ps1 -AllowExistingTestData -AllowChatMutation`: hilo privado, inbox/detail, mensaje/reply, read y mute/unmute con JWT de cada cuenta | Runner preparado; pendiente de entorno | Informe sin secretos; borrado lógico solicitado, sesiones revocadas y purga externa de cuentas/datos Chat confirmada. Guía: `docs/SUPABASE_E2E_SB04.md`. Sin contrato de purga exacto aborta antes de red/mutación. |
| SB-05 | SB-04, dos cuentas aisladas exclusivas y purga autorizada por ciclo de vida de cuenta | Ejecutar `scripts/run-supabase-e2e-sb05.ps1 -AllowExistingTestData -AllowChatAttachmentMutation`: Blob no sensible, subida `chat-attachments`, registro `file_ids`, lectura/descarga por el segundo JWT y borrado del objeto | Runner preparado; pendiente de entorno y de contrato externo de purga | Informe seguro confirma borrado de objeto y denegación posterior; la purga autorizada debe eliminar filas `chat_attachments` y anotar su verificación posterior. Guía: `docs/SUPABASE_E2E_SB05.md`. Sin doble opt-in aborta antes de red/mutación. |
| SB-06 | Perfil aislado sin contactos SOS previos y entre uno y cinco perfiles candidatos efímeros visibles | Ejecutar `scripts/run-supabase-e2e-sb06.ps1 -AllowProfileMutation -AllowSosContactMutation`: proyección `ProfileRemoteGateway`, patch/lectura de `display_name`, normalización KMP y posiciones SOS | Runner preparado; pendiente de entorno | `display_name` previo restaurado exactamente, conjunto SOS creado borrado/verificado vacío y sesión revocada; guía: `docs/SUPABASE_E2E_SB06.md`. Sin snapshot, conjunto SOS inicial vacío y acknowledgements exactos aborta antes de red/mutación. |
| SB-07 | Dos usuarios aislados, comunidad/post preaprovisionados y purga externa autorizada | Ejecutar `scripts/run-supabase-e2e-sb07.ps1 -AllowExistingTestData -AllowCommunityMutation`: validar post/membresía, crear/listar/borrar comentario y reacción emoji, y comprobar que el segundo JWT no borra el comentario del actor | Runner preparado; pendiente de entorno | Informe seguro sin IDs, filas borradas por PostgREST, sesiones revocadas y confirmación externa de purga. Guía: `docs/SUPABASE_E2E_SB07.md`. No se declara ranking remoto: no existe una ruta persistente consumida por `SupabaseCommunityApi`. |
| SB-08 | Credenciales push y cliente/dispositivo real | Registrar y revocar suscripción; entregar notificación y comprobar deep link de Chat normalizado | Bloqueado externo | Identificador de suscripción revocado y evidencia de entrega, sin tokens en el repo |

La asignación respeta dependencias: SB-01 abre SB-02; SB-02 abre SB-03 y
SB-04; SB-04 abre SB-05. SB-06 y SB-07 pueden ejecutarse en paralelo una vez
confirmado el catálogo. Ningún resultado de compilación sustituye estas pruebas
de integración real.
