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

## Foto de control — 2026-07-24 (`main` `fa7843b`)

| Área/lote | Estado | Evidencia y siguiente acción |
| --- | --- | --- |
| CI iOS base y XCTest | Integrado | Workflow macOS compila los 14 targets Kotlin/Native, enlaza el framework y construye el host Swift; #25 añadió el XCTest de enlace. |
| Dedupe CI iOS | Integrado | #7 elimina el trigger `push` redundante en ramas `codex/**`; las validaciones provienen de PR. |
| Tests comunes Feed | Integrado con límite conocido | #8 cubre `RemoteFeedReadRepository` en `commonTest`. `wasmJsTest` de Feed falla por el ICE/incompatibilidad de versión conocida de Compose JS; mientras persista, `:feature:feed:compileKotlinWasmJs` es el gate temporal documentado, nunca un falso éxito de tests. |
| Servicios Web base | Integrado | Cámara MediaDevices, audio Chat, pausa de polling, documentos, caché IndexedDB, thumbnails de vídeo, recuperación Auth y lifecycle de cuenta (#5, #17, #20, #21, #23, #24). |
| Servicios iOS base | Integrado | AVFoundation/deep links (#6), MIME de galería y UTI Office (#14/#16), caché de audio (#18), APNs bridge (#19), Quick Look (#30) y Keychain con renovación de sesión (#13). |
| Diseño y UI compartida | Integrado | SOS/Profile (#9), Composer preview (#11), Chat title bar (#12), Feed media Web (#27) y bloques comunes ya descritos en el inventario. |
| Hosts Web | Parcial | El launcher enruta Auth, Feed, Chat, Official, Notifications, Profile y Settings hacia hosts que consumen UI/lógica común. Existen hosts de Composer y Communities, pero aún no están conectados desde `Main.kt`; WhatsNew/About sigue sin host Web integrado en `main`. |
| Hosts iOS | Parcial | Profile/SOS (#32), Composer (#36), Official (#35), Notifications (#39), Chat (#40), Communities (#42), WhatsNew/About (#37), Auth (#29), Settings (#34) y External Share (#41) exponen hosts inyectables. El `AppDelegate`/composition root (#43) sólo muestra el estado de migración hasta recibir un bootstrap autenticado real; no se deben considerar pantallas operativas de extremo a extremo. |

## PRs activas — no integradas

| PR | Lote | Estado actual | Dependencia o siguiente acción |
| --- | --- | --- | --- |
| #4 `codex/web-share-ios-feed` | Web Share Target, External Share común y transporte/host Feed iOS | En revisión | Sólo PR de migración activa. La CI macOS del SHA vigente es la evidencia requerida antes de integrar; después hay que conectar el bootstrap autenticado al composition root ya integrado. |
| #3 `codex/security-hardening` | Endurecimiento de seguridad existente | En revisión (externa) | PR draft ajena al flujo de migración; no reutilizar ni borrar sin autorización explícita. |

## Lotes en desarrollo — sin PR

| Rama | Lote | Estado | Siguiente acción |
| --- | --- | --- | --- |
| `codex/next-core-platform-contract-tests` | Tests comunes de contratos de plataforma | En desarrollo | Revisar el commit, compilar centralmente y abrir PR si procede. |
| `codex/next-feed-card-structure` | Slots de overlay de reel Feed | En desarrollo | Revisar la extracción y validar Feed Wasm + Android. |
| `codex/next-chat-ui-residual` | Avatar estructural compartido de conversación Chat | En desarrollo | Revisar la extracción y validar Chat Wasm + Android. |
| `codex/next-composer-ui-residual` | Preview textual común de Composer | En desarrollo | Revisar la extracción y validar Composer Wasm + Android. |
| `codex/next-neighborhoods-ui-residual` | Fila estructural de adjunto en Communities | En desarrollo | Revisar la extracción y validar Communities Wasm + Android. |
| `codex/next-official-ui-residual` | Sección común del editor Official | En desarrollo | Revisar la extracción y validar Official Wasm + Android. |
| `codex/next-designsystem-residual` | Geometría común de panel flotante | En desarrollo | Revisar la extracción y validar Design System Wasm + Android. |
| `codex/next-ios-contacts-adapter` | Adaptador ContactsUI | En desarrollo | Revisar la inyección en `IosPlatformServices` y validar CI iOS. |
| `codex/next-ios-host-smoke-tests` | Cobertura XCTest/UI del host UIKit | En desarrollo | Revisar y validar en CI macOS junto al host Swift. |
| `codex/next-web-whatsnew-host` | Host/ruta Web de WhatsNew/About | En desarrollo | Revisar y validar Web Wasm + Android. |

## Dependencias de integración activas

| Grupo | Orden seguro |
| --- | --- |
| Feed y composición iOS | #4 → conectar un bootstrap autenticado real al composition root UIKit ya integrado. |
| Share | #4 concentra el Share Target Web y el transporte Feed iOS; revisar su interacción con el host External Share ya integrado. |
| Plataforma Web | El host WhatsNew en desarrollo puede tocar `Main.kt`; no asignar simultáneamente otra ruta Web sin coordinar el mismo archivo. |
| Hosts iOS | Los exports por feature están integrados, pero las dependencias reales de sesión/repositorio se deben componer desde el launcher; no sustituirlas por datos de ejemplo Swift. |

## Auditoría de features

| Feature | Estado | Límite real restante |
| --- | --- | --- |
| Feed | Parcial | Dominio/estado y piezas Compose ya son comunes. Faltan integración de tarjeta/reel completa con slots de media/acciones/navegación y mutaciones backend verificables; Feed iOS sigue en #4. |
| Chat | Parcial | Estados, ViewModels, renderer y controles estructurales comunes; host iOS integrado. Quedan adjuntos/audio/mapa/traducción y Realtime/typing configurado de forma real. |
| Profile/SOS | Parcial | UI/estado, host Web enrutado y host iOS inyectable están integrados. Quedan contactos/permisos iOS reales, bootstrap de repositorios y navegación de host. |
| Communities | Parcial | Listados, miembros, KPI, comentarios, emoji, ranking, paneles y hosts Web/iOS integrados. Persisten media, URI, audio y navegación específica de plataforma. |
| Official | Parcial | Dominio, editor y host iOS integrados. Quedan media/avatar/HTML/URI/navegación y mutaciones backend. |
| Post Composer | Parcial | Formularios, previews estructurales y hosts Web/iOS integrados. Quedan cámara, galería, bitmap/vídeo, MediaStore y exportación. |
| Settings/Auth/WhatsNew/Notifications | Parcial | Los hosts iOS inyectables de Settings/Auth/WhatsNew y Notifications existen; Web enruta Auth, Settings y Notifications. Falta el host/ruta Web de WhatsNew/About, bootstrap iOS y configuración/credenciales de push reales. |
| External Share | Parcial | Política/payload común y Android existentes; el host iOS inyectable está integrado. Share Target Web y transporte Feed/share iOS siguen en #4. |

## Adaptadores y hosts

| Capacidad | Android | Web | iOS | Límite/siguiente acción |
| --- | --- | --- | --- | --- |
| Cámara/galería | Real | MediaDevices y metadata de navegador integrados | Cámara, PHPicker/UTI y selector de documentos existen como adaptadores | Completar edición/exportación y conectar los adaptadores desde hosts que los necesiten. |
| Audio | Real | Grabador/reproductor de navegador disponibles; Chat recibe reproductor | AVFoundation y caché existen como adaptadores | Falta cobertura completa de reproducción/URI/caché inyectada en hosts reales. |
| Documentos | Real | Reader/open de navegador integrado | Quick Look y tipos Office integrados | Faltan renderer/thumbnails multiplataforma completos. |
| Sesión | Existente | localStorage/lifecycle integrado | Keychain renovable y host Auth inyectable integrados | Falta el bootstrap autenticado del launcher iOS. |
| Notificaciones | FCM/canales por auditar | Web Push y service worker integrados según Supabase | Bridge APNs/permiso/deep links como adaptadores | Requiere credenciales, registro desde el host y pruebas de entrega reales. |
| Navegación host | MainActivity/AppNavGraph | Launcher enruta siete hosts; Composer/Communities/WhatsNew aún no | Composition root UIKit muestra estado y admite Feed inyectado | Completar rutas Web y composición autenticada iOS, sin duplicar UI. |

## Backlog válido

| Prioridad | Unidad | Estado | Criterio de salida |
| --- | --- | --- | --- |
| P0 | Resolver e integrar PRs activas | En curso | Rebase, checks verdes por SHA y limpieza inmediata de worktree/rama tras merge. |
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
