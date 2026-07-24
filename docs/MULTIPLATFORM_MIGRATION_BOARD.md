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

## Foto de control — 2026-07-24 (`main` `a300836`)

| Área/lote | Estado | Evidencia y siguiente acción |
| --- | --- | --- |
| CI iOS base y XCTest | Integrado | Workflow macOS compila los 14 targets Kotlin/Native, enlaza el framework y construye el host Swift; #25 añadió el XCTest de enlace. |
| Dedupe CI iOS | Integrado | #7 elimina el trigger `push` redundante en ramas `codex/**`; las validaciones provienen de PR. |
| Tests comunes Feed | Integrado con límite conocido | #8 cubre `RemoteFeedReadRepository` en `commonTest`. `wasmJsTest` de Feed falla por el ICE/incompatibilidad de versión conocida de Compose JS; mientras persista, `:feature:feed:compileKotlinWasmJs` es el gate temporal documentado, nunca un falso éxito de tests. |
| Servicios Web base | Integrado | Cámara MediaDevices, audio Chat, pausa de polling, documentos, caché IndexedDB, thumbnails de vídeo, recuperación Auth y lifecycle de cuenta (#5, #17, #20, #21, #23, #24). |
| Servicios iOS base | Integrado | AVFoundation/deep links (#6), MIME de galería y UTI Office (#14/#16), caché de audio (#18), APNs bridge (#19), Quick Look (#30) y Keychain con renovación de sesión (#13). |
| Diseño y UI compartida | Integrado | SOS/Profile (#9), Composer preview (#11), Chat title bar (#12), Feed media Web (#27) y bloques comunes ya descritos en el inventario. |
| Hosts Web integrados | Integrado | Feed media (#27), Auth recovery (#23), Settings/account lifecycle (#28), Composer (#33) y Communities (#38) consumen UI/lógica común desde `web/`. |
| Hosts iOS integrados | Integrado | Profile/SOS (#32), Composer (#36), Official (#35), Notifications (#39), Chat (#40), Communities (#42) y WhatsNew/About (#37) son hosts inyectables para UI común. |

## PRs activas — no integradas

| PR | Lote | Estado actual | Dependencia o siguiente acción |
| --- | --- | --- | --- |
| #4 `codex/web-share-ios-feed` | Web Share Target, External Share común y transporte/host Feed iOS | En revisión | Validar CI macOS del SHA vigente antes de integrar; #43 debe rebasarse después si el transporte Feed cambia. |
| #22 `codex/next-web-gallery` | Lectura de metadata de imágenes de navegador | En revisión | Rebasear sobre `main` y validar; comparte `WebPlatformServices` con los hosts Web recientes. |
| #29 `codex/next-ios-auth-host` | Host Auth Compose iOS | En revisión | Keychain ya está en `main`; validar CI iOS sobre la base actual. |
| #34 `codex/next-ios-settings-host` | Host Settings Compose iOS | En revisión | Validar CI iOS y rebasar si `main` avanza antes del merge. |
| #41 `codex/next-ios-external-share-host` | Host External Share iOS | En revisión | Integrar sólo tras CI verde; rebasar tras #4 si toca las mismas APIs de share. |
| #43 `codex/next-ios-composition-root` | Composition root UIKit iOS | En revisión | Conectar al transporte Feed real sólo tras #4 y validar host Swift en CI. |
| #44 `codex/next-web-profile-host` | Host Web Profile/SOS | En revisión | Validar CI y confirmar que usa el contact picker integrado por #15. |
| #3 `codex/security-hardening` | Endurecimiento de seguridad existente | En revisión (externa) | PR draft ajena al flujo de migración; no reutilizar ni borrar sin autorización explícita. |

## Dependencias de integración activas

| Grupo | Orden seguro |
| --- | --- |
| Feed y composición iOS | #4 → rebase/conexión de #43. |
| Share | #4 → rebase/validación de #41 si el contrato común cambia. |
| Plataforma Web | #22 y #44 se rebasan serialmente sobre `main` porque ambos pueden tocar la composición de servicios de navegador. |
| Hosts iOS independientes | #29 y #34 pueden validarse por separado; no se consideran acabados hasta que su CI macOS sea verde sobre su SHA actual. |

## Auditoría de features

| Feature | Estado | Límite real restante |
| --- | --- | --- |
| Feed | Parcial | Dominio/estado y piezas Compose ya son comunes. Faltan integración de tarjeta/reel completa con slots de media/acciones/navegación y mutaciones backend verificables; Feed iOS sigue en #4. |
| Chat | Parcial | Estados, ViewModels, renderer y controles estructurales comunes; host iOS integrado. Quedan adjuntos/audio/mapa/traducción y Realtime/typing configurado de forma real. |
| Profile/SOS | Parcial | UI/estado y host iOS integrados; Web Profile/SOS está en #44. Quedan contactos/permisos y repositorios reales donde no existan. |
| Communities | Parcial | Listados, miembros, KPI, comentarios, emoji, ranking, paneles y hosts Web/iOS integrados. Persisten media, URI, audio y navegación específica de plataforma. |
| Official | Parcial | Dominio, editor y host iOS integrados. Quedan media/avatar/HTML/URI/navegación y mutaciones backend. |
| Post Composer | Parcial | Formularios, previews estructurales y hosts Web/iOS integrados. Quedan cámara, galería, bitmap/vídeo, MediaStore y exportación. |
| Settings/Auth/WhatsNew/Notifications | Parcial | UI y varios hosts comunes/integrados; Settings/Auth iOS siguen en #34/#29. Push real, credenciales y navegación final requieren configuración de plataforma. |
| External Share | Parcial | Política/payload común y Android existentes; Share Target Web y Feed/share iOS siguen en #4, host iOS en #41. |

## Adaptadores y hosts

| Capacidad | Android | Web | iOS | Límite/siguiente acción |
| --- | --- | --- | --- | --- |
| Cámara/galería | Real | MediaDevices integrado; metadata en #22 | Galería/UTI base integrados | Completar cámara/edición/exportación iOS y Web donde aplique. |
| Audio | Real | Grabación Chat integrada | AVFoundation y caché integrados | Falta cobertura completa de reproducción/URI/caché en hosts reales. |
| Documentos | Real | Reader/open integrado | Quick Look y tipos Office integrados | Faltan renderer/thumbnails multiplataforma completos. |
| Sesión | Existente | localStorage/lifecycle integrado | Keychain renovable integrado | Conectar y validar Auth host #29. |
| Notificaciones | FCM/canales por auditar | Web Push implementado según integración Supabase | APNs bridge integrado | Requiere credenciales, permisos y pruebas de entrega reales. |
| Navegación host | MainActivity/AppNavGraph | Hosts por feature integrados/parciales | Hosts inyectables + composition root #43 | Unificar el composition root iOS después de #4. |

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
