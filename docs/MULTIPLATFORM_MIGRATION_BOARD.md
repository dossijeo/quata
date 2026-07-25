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

## Foto de control — 2026-07-25 (`main` `79a8458`)

| Área/lote | Estado | Evidencia y siguiente acción |
| --- | --- | --- |
| CI iOS base y XCTest | Integrado | Workflow macOS compila los 14 targets Kotlin/Native, enlaza el framework y construye el host Swift; #25 añadió el XCTest de enlace. |
| Dedupe CI iOS | Integrado | #7 elimina el trigger `push` redundante en ramas `codex/**`; las validaciones provienen de PR. |
| Tests comunes Feed | Integrado con límite conocido | #8 cubre `RemoteFeedReadRepository` en `commonTest`. `wasmJsTest` de Feed falla por el ICE/incompatibilidad de versión conocida de Compose JS; mientras persista, `:feature:feed:compileKotlinWasmJs` es el gate temporal documentado, nunca un falso éxito de tests. |
| Runner smoke Web | Integrado; ejecución bloqueada upstream | `scripts/web-browser-smoke.mjs` y su guía están en `main`, pero necesitan la distribución Wasm de producción. `:web:compileProductionExecutableKotlinWasmJs` cae antes de compilar Quata por los ICE [CMP-9282](https://youtrack.jetbrains.com/issue/CMP-9282) / [CMP-8767](https://youtrack.jetbrains.com/issue/CMP-8767); ver [WASM_PRODUCTION_ICE.md](WASM_PRODUCTION_ICE.md). No se registra como PASS. |
| Servicios Web base | Integrado | Cámara MediaDevices, audio Chat, pausa de polling, documentos, caché IndexedDB, thumbnails de vídeo, recuperación Auth y lifecycle de cuenta (#5, #17, #20, #21, #23, #24). |
| Servicios iOS base | Integrado | AVFoundation/deep links (#6), grabación/reproducción/caché de audio AVFoundation, MIME de galería y UTI Office (#14/#16), APNs bridge (#19), Quick Look (#30), Keychain con renovación de sesión (#13), transporte Auth nativo, ContactsUI, thumbnails Quick Look de documentos y thumbnails AVFoundation de vídeo locales. |
| Profile/SOS y UI compartida | Integrado | SOS/Profile (#9) y las acciones inyectables del host SOS iOS se integraron en `main`; Composer preview (#11), Chat title bar (#12), Feed media Web (#27), lista/burbuja Chat, detalle/comentarios Official y overlay fullscreen del Design System están en `commonMain` mediante slots. |
| Catálogo Auth común | Integrado | `AuthCatalog` centraliza prefijos, preguntas secretas y copy inglés/español/francés para Android, Web e iOS; las antiguas matrices Android se eliminaron y `commonTest` cubre el catálogo. |
| Playback Feed común | Integrado | `FeedReelVideoPlaybackHostContent` extrae la estructura visual y el estado de reproducción del reel a `commonMain`; Android conserva el reproductor/media y las APIs nativas como slots. |
| Hosts Web | Parcial | `Main.kt` enruta Auth, Feed, Chat, Official, Notifications, Profile, Settings, Communities, Composer, WhatsNew/About y External Share. El runner de smoke está integrado, pero su ejecución de navegador queda bloqueada por la distribución Wasm de producción; faltan recorridos autenticados completos. |
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
| Plataforma Web | Composer, Communities y WhatsNew/About ya enrutan desde `Main.kt`. El runner de smoke no puede consumir distribución de producción hasta resolver el ICE de toolchain documentado en [WASM_PRODUCTION_ICE.md](WASM_PRODUCTION_ICE.md); coordinar rutas nuevas para evitar solapes. |
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
| Documentos | Real | Reader/open de navegador integrado | Quick Look, tipos Office y thumbnails locales integrados | Faltan renderer/thumbnails multiplataforma completos y pruebas de archivos reales. |
| Sesión | Existente | localStorage/lifecycle integrado | Keychain renovable, transporte Auth y launcher Feed/Auth integrados | Requiere configuración runtime y recorrido extremo a extremo real. |
| Notificaciones | FCM/canales por auditar | Web Push y service worker integrados según Supabase | Bridge APNs/permiso/deep links como adaptadores | Requiere credenciales, registro desde el host y pruebas de entrega reales. |
| Navegación host | MainActivity/AppNavGraph | Launcher enruta los hosts migrados, incluido Composer, Communities, WhatsNew/About y External Share | Composition root UIKit instala Feed restaurado o Auth si no hay sesión | Completar pruebas autenticadas iOS y pruebas de rutas Web, sin duplicar UI. |

## Backlog válido

| Prioridad | Unidad | Estado | Criterio de salida |
| --- | --- | --- | --- |
| P0 | Toolchain Wasm de producción | Bloqueado externo | Lote explícito de actualización Kotlin compatible con Compose Web y validación de `:web:wasmJsBrowserDistribution` + runner; los ICE [CMP-9282](https://youtrack.jetbrains.com/issue/CMP-9282) / [CMP-8767](https://youtrack.jetbrains.com/issue/CMP-8767) impiden declararlo verde. Ver [WASM_PRODUCTION_ICE.md](WASM_PRODUCTION_ICE.md). |
| P0 | Validación Auth/Feed iOS configurada | Pendiente | Recorrido login/restauración → Feed contra configuración runtime real, sin credenciales de servicio ni datos de ejemplo. |
| P0 | Validación ContactsUI, thumbnails y hosts iOS | Pendiente | Permisos/selección y archivos locales reales, más XCTest/UI que cubra rutas configuradas además del smoke de arranque. |
| P0 | Completar launcher/composition roots Web e iOS | Pendiente | Cada host integrado se compone desde `web/` o `iosApp/`; sin duplicar UI o lógica común. |
| P1 | Feed, Chat y Official restantes con slots | Pendiente | Estructura Compose común y adaptadores de media/acciones/navegación de plataforma. |
| P1 | Contactos/permisos y pruebas de hosts | Pendiente | Adaptadores reales Web/iOS, inyección desde launcher y pruebas funcionales de host. |
| P1 | Web Chat Realtime/typing y mutaciones Feed/Official | Bloqueado externo | Cliente/configuración oficial Supabase y contratos RPC/RLS/identidad verificables; no usar implementación manual insegura. |
| P1 | Mutaciones de comentarios Communities Web | Bloqueado externo | Definir la acción común por `postId` (el host actual recibe sólo el texto y mezcla comentarios de los posts del perfil) y revisar un `POST community_comments` con RLS/identidad verificables. El `WebPostgrestClient` actual es de sólo lectura y `WebFeedRepository.addComment` falla de forma explícita; no habilitar el composer hasta disponer de ambos contratos. |
| P2 | Foto/vídeo, documentos, audio y notificaciones restantes | Pendiente | Edición/exportación, renderers y pruebas de integración; no volver a crear adaptadores AVFoundation/thumbnails ya integrados. |
| P3 | Auditoría final | Pendiente | Imports Android en `commonMain`, compilaciones JS relevantes, CI iOS, Android ensamblado/instalado/arrancado y evidencia por requisito. |

## Política de ramas

1. Programación: worktree `codex/*`, sin Gradle redundante por agente.
2. Integración: instantánea congelada; correcciones sólo en la misma rama.
3. Publicación: PR a `main`; CI iOS se dispara por `pull_request`, no por `push` de `codex/**`.
4. Merge: sólo después de checks verdes o del ICE conocido documentado con compilación equivalente verde.
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
| Feed y Official de lectura | Pendiente | Crear post efímero por la ruta permitida, leerlo con los repositorios compartidos y eliminarlo; verificar RLS tanto autenticado como anónimo cuando aplique. |
| Chat y adjuntos | Pendiente | Dos usuarios efímeros, conversación, mensaje y adjunto permitido; comprobar transporte KMP, descarga sin cabeceras privilegiadas y limpieza de objeto/filas. |
| Profile/SOS | Pendiente | Perfil y hasta cinco contactos SOS efímeros, prueba de patch/normalización y restauración del estado previo o borrado completo. |
| Communities/comentarios | Pendiente | Validar el contrato por `postId`, RLS y mutación antes de habilitar Web; crear y eliminar comentario de prueba en la misma transacción lógica. |
| Notificaciones/push | Bloqueado externo | Las credenciales de APNs/Web Push/FCM y el dispositivo registrado son necesarios; mientras falten, sólo validar el repositorio y deep links con datos efímeros. |

Al cerrar una fila se debe anotar: commit validado, fecha, adaptador/plataforma,
casos ejecutados, identificadores efímeros ya eliminados y resultado de la
verificación de limpieza. Un fallo abre una unidad de corrección separada; no se
oculta como una prueba satisfactoria.
