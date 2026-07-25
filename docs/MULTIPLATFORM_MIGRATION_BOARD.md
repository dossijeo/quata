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

## Foto de control — 2026-07-25 (`main` `9e980bf`)

| Área/lote | Estado | Evidencia y siguiente acción |
| --- | --- | --- |
| CI iOS base y XCTest | Integrado | Workflow macOS compila los 14 targets Kotlin/Native, enlaza el framework y construye el host Swift; #25 añadió el XCTest de enlace. |
| Dedupe CI iOS | Integrado | #7 elimina el trigger `push` redundante en ramas `codex/**`; las validaciones provienen de PR. |
| Tests comunes Feed | Integrado con límite conocido | #8 cubre `RemoteFeedReadRepository` en `commonTest`. `wasmJsTest` de Feed falla por el ICE/incompatibilidad de versión conocida de Compose JS; mientras persista, `:feature:feed:compileKotlinWasmJs` es el gate temporal documentado, nunca un falso éxito de tests. |
| Servicios Web base | Integrado | Cámara MediaDevices, audio Chat, pausa de polling, documentos, caché IndexedDB, thumbnails de vídeo, recuperación Auth y lifecycle de cuenta (#5, #17, #20, #21, #23, #24). |
| Servicios iOS base | Integrado | AVFoundation/deep links (#6), MIME de galería y UTI Office (#14/#16), caché de audio (#18), APNs bridge (#19), Quick Look (#30), Keychain con renovación de sesión (#13), transporte Auth nativo, ContactsUI y thumbnails Quick Look locales. |
| Profile/SOS y UI compartida | Integrado | SOS/Profile (#9) y las acciones inyectables del host SOS iOS se integraron en `main`; Composer preview (#11), Chat title bar (#12), Feed media Web (#27), lista/burbuja Chat, detalle/comentarios Official y overlay fullscreen del Design System están en `commonMain` mediante slots. |
| Catálogo Auth común | Integrado | `AuthCatalog` centraliza prefijos, preguntas secretas y copy inglés/español/francés para Android, Web e iOS; las antiguas matrices Android se eliminaron y `commonTest` cubre el catálogo. |
| Hosts Web | Parcial | `Main.kt` enruta Auth, Feed, Chat, Official, Notifications, Profile, Settings, Communities, Composer, WhatsNew/About y External Share. Los hosts consumen UI/lógica común; faltan pruebas de navegador y recorridos autenticados completos. |
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
| Plataforma Web | Composer, Communities y WhatsNew/About ya enrutan desde `Main.kt`; coordinar cualquier nueva ruta Web para evitar solapes en ese launcher. |
| Hosts iOS | Los exports por feature, Auth/Feed launcher, ContactsUI y thumbnails existen. Las dependencias se siguen componiendo desde el launcher y se deben probar con permisos/configuración reales; no sustituirlas por datos de ejemplo Swift. |

## Auditoría de features

| Feature | Estado | Límite real restante |
| --- | --- | --- |
| Feed | Parcial | Dominio/estado, tarjeta de metadata, overlay de reel y estado de controles de vídeo ya son comunes con slots de media/acciones/navegación. El bootstrap y launcher Auth/Feed iOS están integrados; faltan mutaciones backend verificables y validación extremo a extremo con runtime configurado. |
| Chat | Parcial | Estados, ViewModels, renderer, lista de conversaciones, burbuja de mensaje y controles estructurales son comunes; host iOS integrado. Quedan adjuntos/audio/mapa/traducción y Realtime/typing configurado de forma real. |
| Profile/SOS | Parcial | UI/estado, acciones inyectables del host SOS iOS, host Web enrutado y adaptador ContactsUI están integrados. Quedan permisos/selección real cubierta por pruebas, bootstrap de repositorios y navegación de host. |
| Communities | Parcial | Listados, miembros, KPI, comentarios, emoji, ranking, paneles y hosts Web/iOS integrados. Persisten media, URI, audio y navegación específica de plataforma. |
| Official | Parcial | Dominio, listado/detalle, entrada de comentarios, editor y host iOS integrados. Quedan media/avatar/HTML/URI/navegación y mutaciones backend. |
| Post Composer | Parcial | Formularios, preview textual, controles de vídeo, previews estructurales y ruta/host Web e iOS integrados. Quedan cámara, galería, bitmap/vídeo, MediaStore y exportación. |
| Settings/Auth/WhatsNew/Notifications | Parcial | El catálogo Auth es común, el transporte/launcher Auth iOS están integrados y los hosts iOS inyectables de Settings/Auth/WhatsNew y Notifications existen; Web enruta Auth, Settings, Notifications y WhatsNew/About. Faltan configuración real de runtime y credenciales/pruebas de push. |
| External Share | Parcial | Política/payload común, Android, host iOS inyectable y Share Target Web están integrados. Falta la validación de flujo completo con datos reales y el bootstrap iOS autenticado para destinos que dependan de Feed. |

## Adaptadores y hosts

| Capacidad | Android | Web | iOS | Límite/siguiente acción |
| --- | --- | --- | --- | --- |
| Cámara/galería | Real | MediaDevices y metadata de navegador integrados | Cámara, PHPicker/UTI y selector de documentos existen como adaptadores | Completar edición/exportación y conectar los adaptadores desde hosts que los necesiten. |
| Audio | Real | Grabador/reproductor de navegador disponibles; Chat recibe reproductor | AVFoundation y caché existen como adaptadores | Falta cobertura completa de reproducción/URI/caché inyectada en hosts reales. |
| Documentos | Real | Reader/open de navegador integrado | Quick Look, tipos Office y thumbnails locales integrados | Faltan renderer/thumbnails multiplataforma completos y pruebas de archivos reales. |
| Sesión | Existente | localStorage/lifecycle integrado | Keychain renovable, transporte Auth y launcher Feed/Auth integrados | Requiere configuración runtime y recorrido extremo a extremo real. |
| Notificaciones | FCM/canales por auditar | Web Push y service worker integrados según Supabase | Bridge APNs/permiso/deep links como adaptadores | Requiere credenciales, registro desde el host y pruebas de entrega reales. |
| Navegación host | MainActivity/AppNavGraph | Launcher enruta los hosts migrados, incluido Composer, Communities, WhatsNew/About y External Share | Composition root UIKit instala Feed restaurado o Auth si no hay sesión | Completar pruebas autenticadas iOS y pruebas de rutas Web, sin duplicar UI. |

## Backlog válido

| Prioridad | Unidad | Estado | Criterio de salida |
| --- | --- | --- | --- |
| P0 | Validación Auth/Feed iOS configurada | Pendiente | Recorrido login/restauración → Feed contra configuración runtime real, sin credenciales de servicio ni datos de ejemplo. |
| P0 | Validación ContactsUI, thumbnails y hosts iOS | Pendiente | Permisos/selección y archivos locales reales, más XCTest/UI que cubra rutas configuradas además del smoke de arranque. |
| P0 | Completar launcher/composition roots Web e iOS | Pendiente | Cada host integrado se compone desde `web/` o `iosApp/`; sin duplicar UI o lógica común. |
| P1 | Feed, Chat y Official restantes con slots | Pendiente | Estructura Compose común y adaptadores de media/acciones/navegación de plataforma. |
| P1 | Contactos/permisos y pruebas de hosts | Pendiente | Adaptadores reales Web/iOS, inyección desde launcher y pruebas funcionales de host. |
| P1 | Web Chat Realtime/typing y mutaciones Feed/Official | Bloqueado externo | Cliente/configuración oficial Supabase y contratos RPC/RLS/identidad verificables; no usar implementación manual insegura. |
| P2 | Foto/vídeo, documentos, audio y notificaciones restantes | Pendiente | Adaptadores concretos por plataforma y pruebas de integración. |
| P3 | Auditoría final | Pendiente | Imports Android en `commonMain`, compilaciones JS relevantes, CI iOS, Android ensamblado/instalado/arrancado y evidencia por requisito. |

## Política de ramas

1. Programación: worktree `codex/*`, sin Gradle redundante por agente.
2. Integración: instantánea congelada; correcciones sólo en la misma rama.
3. Publicación: PR a `main`; CI iOS se dispara por `pull_request`, no por `push` de `codex/**`.
4. Merge: sólo después de checks verdes o del ICE conocido documentado con compilación equivalente verde.
5. Limpieza: tras merge verde, eliminar branch remota y worktree; una rama nunca se reutiliza para otra feature.
