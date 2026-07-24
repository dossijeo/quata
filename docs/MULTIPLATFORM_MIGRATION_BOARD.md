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

## Foto de control — 2026-07-24 (main `0791ce5`)

| Área/lote | Estado | Evidencia y siguiente acción |
| --- | --- | --- |
| CI iOS base | Integrado | `main` `67336fd`: 14 targets Kotlin/Native, framework, host Swift y 1 XCTest verdes. |
| Web Share Target, External Share común, Feed iOS y composición iOS | En revisión | PR #4 `codex/web-share-ios-feed` (`c9c6747`); integra Share Target Web y host Feed iOS. CI automática pendiente; no cuenta como terminado. |
| Cámara Web, audio Chat y lifecycle polling | Integrado | Merge #5 `63a8f2f`; MediaDevices, grabación Chat y pausa de polling con pestaña oculta. |
| Audio AVFoundation y taps de notificación iOS | En revisión | PR #6 `codex/next-ios` (`efce7cb`); host AVFoundation y deep links, con CI automática pendiente. |
| Dedupe de CI iOS | Integrado | Merge #7 `1967578`; elimina trigger `push` para `codex/**`. |
| Tablero de migración inicial | Integrado | Merge #26 `de81460`; este documento queda sincronizado en esta actualización con el estado de main y de las PR activas. |
| Tests KMP Feed | Integrado | Merge #8 `309afd1`; cobertura de `RemoteFeedReadRepository` en `commonTest`. El ICE Compose JS conocido sigue siendo una limitación de ejecución, no de la compilación equivalente. |
| SOS/Profile diálogo público | Integrado | Merge #9 `a958985`; layout portable con slots, manteniendo permisos y contactos en Android. |
| Composer preview estructural | Integrado | Merge #11 `4a488cf`; validado localmente con Wasm y Android antes de integración. |
| Chat residual | Integrado | Merge #12 `08c9d9c`; title bar común con slots de navegación, avatar y acciones de plataforma. |
| Sesión segura Keychain iOS | En revisión | PR #13 `codex/next-platform` (`e4aa82d`); Keychain y sesión renovable. CI automática pendiente; no integrar sin verde. |
| Contact picker browser | En revisión | PR #15 `codex/next-web-profile` (`b3f0457`); modifica contratos de plataforma y `WebPlatformServices`. CI automática en curso tras su SHA vigente. |
| Documentos Web | Integrado | Merge #17 `74af755`; reader/open service y host Chat/Web. |
| Caché IndexedDB Web | Integrado | Merge #20 `ecfc730`; `FileCacheService`, IndexedDB Blob cache y liberación segura de URLs propias. |
| Metadata de imágenes Web | En revisión (requiere rebase) | PR #22 `codex/next-web-gallery` (`01bcb0e`); `ImageMetadataService` y `WebPlatformServices`; rebasear sobre main antes de CI final. |
| Recovery Auth Web | En revisión | PR #23 `codex/next-web-auth` (`c872e79`); recuperación de contraseña y host Web. CI automática pendiente. |
| Lifecycle de cuenta Web | Integrado | Merge #24 `7765503`; endpoint autenticado y limpieza local sólo tras respuesta `ok`. |
| Caché de audio iOS | Integrado | Merge #18 `7811ffa`; `AudioCacheService` e `IosPlatformServices`. |
| Infraestructura de pruebas iOS | Integrado | Merge #25 `0fcdd16`; target XCTest que valida el enlace del framework KMP Feed. |
| Ajustes Web: lifecycle de cuenta | En revisión | PR #28 `codex/next-web-settings-account` (`26ee0bf`); UI común, diálogo y callbacks sobre lifecycle ya integrado. Reintentar CI automática tras el fallo de infraestructura actual, sin cambiar código sin evidencia. |
| Media Feed Web | Integrado | Merge #27 `0791ce5`; renderer de media compatible para Feed Web. |
| Host Auth Compose iOS | En revisión | PR #29 `codex/next-ios-auth-host` (`878714a`); host inyectable, dependiente del contrato Keychain/Auth de #13. |
| Apertura de documentos iOS | En revisión | PR #30 `codex/next-ios-document-open` (`79c2a6d`); Quick Look para archivos locales, validar tras la composición de documentos ya integrada. |

## Dependencias de integración activas

| Grupo | Ramas/PR | Orden seguro |
| --- | --- | --- |
| Servicios de plataforma Web | PR #15 y #22 | #17 y #20 ya están integrados. Rebasear #15 y #22 de forma serial sobre main porque ambas tocan composición/servicios Web. |
| Launcher Web | PR #4, #23 y #28 | #17/#24/#27 ya están integrados. Integrar sólo en orden de rebase sobre main para conservar una única composición, routing y host por capacidad. |
| Servicios iOS | PR #6, #13, #29 y #30 | #18/#25 ya están integrados. Resolver primero #13 (sesión) antes de #29; rebasar los demás si `IosPlatformServices` o el host cambian. |

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
| Cámara | Real | MediaDevices integrado | En revisión UIKit/composición | No usar FilePicker como cámara; queda host iOS real. |
| Audio | Real | Recorder Chat integrado | En revisión AVFoundation | #18 ya integra caché iOS; #6 sigue pendiente para host AVFoundation/deep links. |
| Sesión | Existente | localStorage y lifecycle integrado | En revisión Keychain | Resolver #13 antes de conectar el host Auth iOS #29. |
| Notificaciones | FCM/canales por auditar | Web Push entrega | Tap bridge en revisión | APNs requiere credenciales/navegación real. |
| Feed iOS | N/A | Lectura PostgREST | En revisión URLSession | PR #4 requiere CI verde y depende de la sesión Auth iOS para composición real. |

## Backlog válido

| Prioridad | Unidad | Estado | Criterio de salida |
| --- | --- | --- | --- |
| P0 | Resolver CI iOS de PR #4/#6/#13 | En curso | Una CI automática verde por SHA, sin workflow dispatch duplicado. |
| P0 | Rebasear servicios Web #15/#22 y validar #23/#27/#28 | En curso | Integración serial sobre main y CI verde por SHA. |
| P1 | Host iOS Auth + Feed de lectura | Pendiente | Sesión renovable y configuración inyectada, sin repositorio falso. |
| P1 | Contactos/permisos Profile Web/iOS | Pendiente | Adaptadores reales y navegación de host. |
| P1 | Web Chat Realtime/typing | Bloqueado externo | Cliente y configuración oficial Realtime; no Phoenix manual. |
| P1 | Mutaciones Feed/Official Web | Bloqueado externo | RPC/RLS/identity mapping verificables. |
| P2 | APNs/FCM, foto/vídeo y cachés restantes | Pendiente | Adaptadores concretos por plataforma; documentos Web/iOS y caché audio iOS ya tienen entregas integradas/en revisión. |
| P3 | Auditoría final | Pendiente | Imports Android commonMain, JS, Android emulador, CI iOS y evidencia por requisito. |

## Política de ramas

1. Programación: worktree `codex/*`, sin Gradle redundante por agente.
2. Integración: instantánea congelada, correcciones sólo en la misma rama.
3. Publicación: PR a `main`; tras PR #7, iOS se dispara por `pull_request` y no por push de `codex/**`.
4. Merge: sólo después de checks verdes o ICE conocido documentado con compilación equivalente verde.
5. Limpieza: tras merge verde, eliminar branch remota y worktree; una rama nunca se reutiliza para otra feature.
