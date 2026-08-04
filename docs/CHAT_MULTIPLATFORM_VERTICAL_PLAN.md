# Plan de vertical común — Chat

Base de preparación: `main` `97b325afab3cf35739d795d3a1457e5ee1c2376c` (documentación posterior a #175 integrada).

Estado de producto de partida: `SCR-CHAT` permanece **FALLBACK/PARCIAL**. Android monta la
composición completa `ChatScreen`; Wasm e iOS montan `ChatBrowserHostContent`, una composición
alternativa simplificada. Esta vertical no se declara terminada por compilar o sustituir el nombre
del host: cada `CHAT-*` conserva estado y evidencia propios.

## Plantilla G obligatoria

| Campo | Decisión de esta vertical |
|---|---|
| IDs afectados | `SCR-CHAT`, `CHAT-MESSAGES`, `CHAT-COMPOSER`, `CHAT-MESSAGE-ACTIONS`, `CHAT-FAVORITES`, `CHAT-NOTIFICATIONS`, `CHAT-FOCUSED-MESSAGE`, `CHAT-ATTACHMENTS`, `CHAT-AUDIO`, `CHAT-FORWARD`, `CHAT-GROUP`, `CHAT-LOCATION-SOS`, `CHAT-TRANSLATION`, `CHAT-PROFILE`; dependencias `OVR-PUBLIC-PROFILE`, `OVR-MEDIA`, `FLOW-DOCUMENT-VIEWER`, `FLOW-TRANSLATOR`, `FLOW-EMOJI` y `FLOW-SHELL-NAV`. |
| Android de referencia | `app/src/main/java/com/quata/feature/chat/presentation/chat/ChatScreen.kt`, composable `ChatScreen` (2.054 líneas), entrada `AppNavGraph` ruta `chat/{conversationId}`, conversación especial de favoritos, `focusedMessageId`, perfil global y retorno. Incluye cabecera/selección, miembros/roles, fondo procedural, historial paginado, skeleton, typing, reply/edit/forward/favorite/delete/report, retry de eco local, adjuntos, audio consecutivo, emoji, cámara/galería/documentos, mapa/SOS, traductor, confirmaciones y cierre. |
| Raíz común prevista | `ChatScreenHost` en `feature/chat/commonMain`, consumida directamente por Android, Wasm e iOS. Se extraerán de Android la estructura, estado de composición y controles visibles sin crear HTML/Swift paralelos. `ChatBrowserHostContent` dejará de ser ruta de producto y podrá quedar sólo como compatibilidad temporal interna hasta retirarlo. |
| Datos y lecturas | `ChatRepository` y `ChatViewModel` comunes: `observeConversations`, `observeMessages`, `loadOlderMessages`, candidatos, usuario, realtime/presencia/typing, sync, favoritos y estado unread. Android usa `ChatRepositoryImpl`; Wasm e iOS usan `PostgrestChatRepository` con `WebChatPostgrestTransport`/`IosChatPostgrestTransport`, sesión autenticada, uploader y gateways Realtime reales. Estados obligatorios: carga inicial, carga histórica, vacío, offline/sync, error terminal, retry y mensaje enfocado ausente. |
| Eventos y mutaciones | `MessageChanged`, `Send`, `AttachmentSelected/ClearAttachment`, reply, edit/cancel, favorite, delete, report, forward, mute, member invites, add participant, promote/demote, remove, block, leave, hide/delete conversation, retry pending y mark-read. Cada control visible despacha el evento común; se conserva optimismo/rollback del ViewModel y se acreditan éxito/error con datos desechables antes de cerrar el ID correspondiente. |
| Navegación | Entrada desde `SCR-CONVERSATIONS`, notificaciones, favoritos, perfil/comunidad y deep link; `focusedMessageId` pagina, enfoca/resalta y se consume una sola vez; favoritos abre la conversación origen/mensaje; avatar y miembros abren `OVR-PUBLIC-PROFILE`; back vuelve al origen sin perder estado; rutas privadas pasan por `OVR-AUTH-REQUIRED`; Chat↔Perfil conserva la pila. |
| Adaptadores permitidos | Picker/cámara/permisos; grabador y reproductor de audio; thumbnail/decoder/player de imagen/vídeo; descarga/Quick Look/DocMentis/lector Android; abrir URL/mapa; clipboard; feedback sonoro/háptico; ciclo foreground/IME/insets; avatar remoto. Reciben estado/callbacks comunes y no pueden poseer cabecera, composer, acciones, confirmaciones ni navegación de producto. |
| Plataformas afectadas | `commonMain` y consumidores Android/Wasm/iOS: clasificador fail-closed de las tres. Preflight: contratos rápidos/imports, common tests, metadata, Android compile/tests/assemble/lint, Wasm tests/distribución/Chrome, Kotlin/Native `iosX64Test`/framework, host Swift firmado y simulador. |
| Evidencia pendiente | Comparación Android↔Wasm y Android↔iOS con la misma conversación/sesión para cabecera, historial, composer, selección/acciones, adjuntos/audio, miembros y estados carga/vacío/error. E2E reversible por mutación con limpieza; deep link/favoritos/retorno; logs de navegador/simulador; informe Sol read-only del head y merge sintético exactos. |

## Contraste personal de las composiciones actuales

### Android de referencia

La raíz Android no es sólo una lista y un campo de texto. Conserva, en una única composición:

- cabecera normal y barra de selección con copiar, responder, reenviar, editar, reportar,
  favorito y borrar;
- menú de conversación con mute, invitaciones, participantes, salir y ocultar, además del panel
  expandido de miembros con roles, bloqueo y expulsión;
- historial paginado, skeleton inicial, indicador de escritura, seguimiento del final, eco local,
  retry y apertura enfocada de un mensaje;
- conversación especial de favoritos con salto a conversación/mensaje origen;
- reply/edit banners, emoji, quick panel de adjuntos, preview pendiente, cámara, galería,
  documentos, grabación y envío;
- burbujas con avatar/perfil, texto enlazado/traducible, SOS/mapa, imagen/vídeo, documento, audio
  y reproducción consecutiva;
- confirmaciones, estados de progreso/error y retorno del grafo real.

### Fallback Wasm/iOS actual

`ChatBrowserHostContent` contiene una pantalla alternativa: cabecera con botones de texto, burbujas
reducidas, sólo acción de responder y composer vertical. Web sustituye incluso input y envío por
controles nativos (`WebNativeInput`/`WebNativeButton`). No monta la barra de selección, favoritos,
menús/mutaciones de conversación y grupo, emoji, fondo Android, confirmaciones, preview pendiente,
retry visual equivalente ni la misma jerarquía del composer.

iOS declara `onOpenMap` y `onTranslateMessage` en `IosChatHostDependencies`, pero el host actual no
los propaga a la composición; sus valores por defecto vacíos no acreditan `CHAT-LOCATION-SOS` ni
`CHAT-TRANSLATION`. El visor Quick Look y la descarga autenticada sí son adaptadores permitidos,
pero no sustituyen una raíz de producto común.

## Unidades de implementación y criterio de cierre

1. **Raíz y lectura:** extraer `ChatScreenHost`, fondo/cabecera/historial/skeleton/typing, selección y
   foco; montar la misma raíz en las tres plataformas. IDs: `SCR-CHAT`, `CHAT-MESSAGES`,
   `CHAT-FOCUSED-MESSAGE`, `CHAT-PROFILE`.
2. **Composer y acciones:** portar reply/edit/favorite/delete/report/forward, emoji, adjunto pendiente,
   confirmaciones y errores. IDs: `CHAT-COMPOSER`, `CHAT-MESSAGE-ACTIONS`, `CHAT-FAVORITES`,
   `CHAT-FORWARD`, `FLOW-EMOJI`.
3. **Conversación/grupo:** mute, invitaciones, altas, roles, bloqueo, expulsión, salida y ocultación.
   IDs: `CHAT-NOTIFICATIONS`, `CHAT-GROUP`.
4. **Bordes de medios/sistema:** conectar picker/cámara, audio, media/documentos, mapa/SOS y
   traductor sin UI paralela. IDs: `CHAT-ATTACHMENTS`, `CHAT-AUDIO`, `CHAT-LOCATION-SOS`,
   `CHAT-TRANSLATION`, `OVR-MEDIA`, `FLOW-DOCUMENT-VIEWER`, `FLOW-TRANSLATOR`.
5. **E2E y evidencia:** mutaciones reversibles, navegación/retorno y comparación visual por ID.

Hasta completar las cinco unidades, `SCR-CHAT` sólo podrá declararse **COMÚN con límites** y el
informe enumerará los IDs aún no acreditados. Ninguna carencia de RLS autoriza cambios de política
durante esta migración: se documenta y se mantiene compatibilidad con Android y Web antigua.

## Identidad visual común contrastada con Android

- `CHAT-TRANSLATION`/`FLOW-TRANSLATOR`: la raíz Compose común conserva el overlay de cristal
  esmerilado, cabecera naranja, pie, copy exacto y burbuja traducida de Android. El host iOS real
  acredita activación, traducción backend y retorno en `evidence/chat/22ede9ba-ios-translation`.
- `SCR-CHAT`/`CHAT-MESSAGE-ACTIONS`/`CHAT-GROUP`/`CHAT-NOTIFICATIONS`: la cabecera común usa la
  flecha compacta de Android y un catálogo EN/ES/FR que replica el copy de recursos Android para
  accesibilidad, menús, miembros, selección y confirmaciones. Compilar o localizar estos controles
  no cierra todavía sus mutaciones ni la equivalencia visual completa.

## Resultado local de la unidad 1 (pendiente de preflight integrado)

- `ChatScreenHost` pasa a ser la raíz de lectura consumida por Wasm e iOS mediante `ChatBrowserHostContent`: fondo procedural, cabecera, historial paginado, skeleton, typing, selección y foco usan `ChatViewModel`/`ChatRepository` reales en `commonMain`.
- Android conserva `ChatScreen` completo: montar todavía la raíz de lectura allí recortaría composer, acciones de selección, confirmaciones y adaptadores de medios. El reemplazo Android queda ligado a las unidades 2--4 y este commit no declara equivalencia.
- Siguen pendientes `CHAT-COMPOSER`, `CHAT-MESSAGE-ACTIONS`, `CHAT-FAVORITES`, `CHAT-NOTIFICATIONS`, `CHAT-ATTACHMENTS`, `CHAT-AUDIO`, `CHAT-FORWARD`, `CHAT-GROUP`, `CHAT-LOCATION-SOS`, `CHAT-TRANSLATION`, `OVR-MEDIA`, `FLOW-DOCUMENT-VIEWER`, `FLOW-TRANSLATOR`, `FLOW-EMOJI` y la evidencia funcional/visual de esta unidad.
