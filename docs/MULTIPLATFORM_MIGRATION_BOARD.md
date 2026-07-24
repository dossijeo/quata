# Tablero operativo de migración multiplataforma

Este tablero es la fuente persistente para despacho. El inventario mantiene la arquitectura;
este archivo mantiene estado, evidencia y dependencias.

## Reglas

- Consultar este tablero antes de crear una tarea. No reasignar una fila **Ya compartido**.
- Cada entrega vive en worktree/branch aislado y sólo es **Integrado** tras merge en `main`.
- **En revisión** no es completado: debe contener PR y validación.
- Actualizar la fila al abrir PR, corregir un fallo, fusionar o descubrir un bloqueo externo.
- Tras merge verde, eliminar la rama remota y su worktree; nunca reutilizar una rama para otra feature.

## Leyenda

| Estado | Significado |
| --- | --- |
| Integrado | En `main` con evidencia de validación. |
| En revisión | PR abierta; no cuenta como terminado. |
| En curso | Worktree con tarea concreta. |
| Pendiente | Unidad válida no asignada. |
| Bloqueado externo | Falta backend, credencial o contrato verificable. |
| Ya compartido | Auditado; extraer de nuevo duplicaría UI. |

## Foto de control — 2026-07-24

| Área/lote | Estado | Evidencia y siguiente acción |
| --- | --- | --- |
| CI iOS base | Integrado | `main` `67336fd`: 14 targets Kotlin/Native, framework, host Swift y 1 XCTest verdes. |
| Web Share Target, External Share común, Feed iOS y composición iOS | En revisión | PR #4 `codex/web-share-ios-feed`; Android/Wasm locales verdes. URLSession iOS corregido en `22efc2d`; esperar CI automática. |
| Cámara Web, audio Chat y lifecycle polling | Integrado | Merge #5 `63a8f2f`; MediaDevices, grabación Chat y pausa de polling con pestaña oculta. |
| Audio AVFoundation y taps de notificación iOS | En revisión | PR #6 `codex/next-ios`; imports nativos corregidos en `d60d11f`. |
| Dedupe de CI iOS | Integrado | Merge #7 `1967578`; elimina trigger `push` para `codex/**`. |
| Tests KMP Feed | En revisión | PR #8 `codex/next-tests`; fuentes JS/Wasm compilan; ejecución bloqueada por ICE JS conocido y aviso stdlib/compiler Wasm. |
| SOS/Profile diálogo público | Integrado | Merge #9 `a958985`; layout portable con slots, manteniendo permisos y contactos en Android. |
| Composer preview estructural | Integrado | Merge #11 `4a488cf`; validado localmente con Wasm y Android antes de integración. |
| Chat residual | Integrado | Merge #12 `08c9d9c`; title bar común con slots de navegación, avatar y acciones de plataforma. |
| Sesión segura Keychain iOS | En revisión | PR #13 `codex/next-platform`; Keychain y sesión renovable; pendiente validación/merge. |
| Contact picker browser | En revisión (conflicto) | PR #15 `codex/next-web-profile`; toca contratos de plataforma y `WebPlatformServices`; rebase sobre main antes de merge. |
| Documentos Web | En revisión | PR #17 `codex/next-web-documents`; reader/open service y host Chat/Web. |
| Caché IndexedDB Web | Integrado | Merge #20 `ecfc730`; `FileCacheService`, IndexedDB Blob cache y liberación segura de URLs propias. |
| Metadata de imágenes Web | En revisión (conflicto) | PR #22 `codex/next-web-gallery`; `ImageMetadataService` y `WebPlatformServices`; requiere rebase. |
| Recovery Auth Web | En revisión | PR #23 `codex/next-web-auth`; formularios comunes y `WebLoginHost`. |
| Lifecycle de cuenta Web | En revisión | PR #24 `codex/next-web-account-lifecycle`; bearer y limpieza local sólo tras respuesta `ok`. |
| Caché de audio iOS | En revisión | PR #18 `codex/next-ios-audio`; `AudioCacheService` e `IosPlatformServices`. |
| Ajustes Web: lifecycle de cuenta | En curso (dependencia interna) | `codex/next-web-settings-account` `ee0a4b5`; UI común, diálogo y callbacks listos. No abrir PR ni validar hasta que PR #24 esté integrado; después rebase y validación. |

## Dependencias de integración activas

| Grupo | Ramas/PR | Orden seguro |
| --- | --- | --- |
| Servicios de plataforma Web | PR #15, #17 y #22 | #20 ya está integrado. Las tres ramas restantes modifican `WebPlatformServices.kt`; rebasar #15 sobre la caché integrada, integrar de forma serial y después rebasar las restantes. |
| Launcher Web | PR #4, #17 y ajustes retenidos | PR #4 y #17 modifican `web/Main.kt`. La rama de Ajustes debe esperar a PR #24 y rebasarse después de los merges que afecten `Main.kt`; no duplicar routing, worker o host de documentos. |
| Servicios iOS | PR #6 y #18 | Ambas ramas modifican `IosPlatformServices`; integrar una y rebasar la otra antes de merge. |

## Auditoría de features

| Feature | Estado | Límite real restante |
| --- | --- | --- |
| Feed | Ya compartido | `FeedReelPostContent` y slots de tarjeta/reel son comunes. Quedan Media3/Coil/URI/navegación y mutaciones/backend. |
| Chat | Parcial | Frame, selector, composer y paneles son comunes. Quedan adjuntos/audio/mapa/traducción de plataforma; Realtime/typing requieren cliente/configuración Supabase oficial. |
| Profile/SOS | Parcial | UI/estado y diálogo portable ya integrados. Quedan contactos/permisos/repositorio Web/iOS. |
| Communities | Ya compartido | Layouts, comentarios, emoji, ranking y paneles son comunes. Quedan Media3, URI, Coil, audio, Context y navegación Android. |
| Official | Ya compartido | Listado, detalle, editor, preview y diálogos comunes con slots. Quedan media/avatar/HTML/URI/navegación y mutaciones backend. |
| Post Composer | Parcial | Formularios y previews estructurales comunes integrados. Quedan bitmap/vídeo/cámara/galería/MediaStore. |
| External Share | En revisión | PR #4 aporta destino común y Web Share Target IndexedDB. Quedan intents/URI/visor Android. |

## Adaptadores y hosts

| Capacidad | Android | Web | iOS | Siguiente acción |
| --- | --- | --- | --- | --- |
| Cámara | Real | En revisión MediaDevices | En revisión UIKit/composición | Validar PR #4/#5; no usar FilePicker como cámara. |
| Audio | Real | En revisión recorder Chat | En revisión AVFoundation | Validar PR #5/#6; luego routing/caché. |
| Sesión | Existente | localStorage actual | En curso Keychain | Proveedor Auth iOS renovable, sin datos de ejemplo. |
| Notificaciones | FCM/canales por auditar | Web Push entrega | Tap bridge en revisión | APNs requiere credenciales/navegación real. |
| Feed iOS | N/A | Lectura PostgREST | En revisión URLSession | Configuración pública + sesión Auth iOS. |

## Backlog válido

| Prioridad | Unidad | Estado | Criterio de salida |
| --- | --- | --- | --- |
| P0 | Resolver CI iOS de PR #4/#6 | En curso | Una CI automática verde por SHA, sin workflow dispatch duplicado. |
| P0 | Validar/PR Composer, Chat y Keychain | En curso | JS/Wasm/Android o iOS CI según alcance. |
| P1 | Host iOS Auth + Feed de lectura | Pendiente | Sesión renovable y configuración inyectada, sin repositorio falso. |
| P1 | Contactos/permisos Profile Web/iOS | Pendiente | Adaptadores reales y navegación de host. |
| P1 | Web Chat Realtime/typing | Bloqueado externo | Cliente y configuración oficial Realtime; no Phoenix manual. |
| P1 | Mutaciones Feed/Official Web | Bloqueado externo | RPC/RLS/identity mapping verificables. |
| P2 | APNs/FCM, documentos, foto/vídeo y cachés | Pendiente | Adaptadores concretos por plataforma. |
| P3 | Auditoría final | Pendiente | Imports Android commonMain, JS, Android emulador, CI iOS y evidencia por requisito. |

## Política de ramas

1. Programación: worktree `codex/*`, sin Gradle redundante por agente.
2. Integración: instantánea congelada, correcciones sólo en la misma rama.
3. Publicación: PR a `main`; tras PR #7, iOS se dispara por `pull_request` y no por push de `codex/**`.
4. Merge: sólo después de checks verdes o ICE conocido documentado con compilación equivalente verde.
5. Limpieza: tras merge verde, eliminar branch remota y worktree; una rama nunca se reutiliza para otra feature.
