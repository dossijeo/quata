# Plan de vertical común — Chat

Base de preparación histórica: `main` `97b325afab3cf35739d795d3a1457e5ee1c2376c` (documentación posterior a #175 integrada).
Base re-medida para el corte `SCR-CHAT` actual: `origin/main` `acea3baab6cb8ec7aef414127059bdd220496e87` (después de #177).

Estado histórico de producto de partida: `SCR-CHAT` permanecía **FALLBACK/PARCIAL**. Android montaba la
composición completa `ChatScreen`; Wasm e iOS montaban `ChatBrowserHostContent`, una composición
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

### Android de referencia histórica

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

### Fallback Wasm/iOS histórico

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
- `CHAT-COMPOSER`/`CHAT-FORWARD`/`CHAT-ATTACHMENTS`/`CHAT-AUDIO`: el mismo catálogo alimenta
  placeholder, banners de edición/respuesta, selector de reenvío, adjunto pendiente, panel rápido,
  cámara, grabación y envío; los controles siguen siendo Compose comunes y los servicios de sistema
  continúan inyectados como adaptadores. También localiza los fallos/estados no soportados de
  selector, galería, cámara y grabación; un error de plataforma ya no rompe el idioma del flujo.
- `CHAT-ATTACHMENTS`/`OVR-MEDIA`: Wasm usa imagen canvas y vídeo HTML real; iOS descarga de forma
  autenticada y usa thumbnail AVFoundation/visor UIKit. El adaptador iOS distingue carga de fallo
  terminal para no dejar un spinner infinito; el fallback y retry son Compose comunes y el
  adaptador conserva sólo descarga/decoder. Falta acreditar el retry con un fallo controlado real.

## Resultado focal `CHAT-FAVORITES` + `CHAT-FOCUSED-MESSAGE` (candidato `c0f06dd2`)

- `CHAT-FAVORITES` queda cerrado como candidato: Android, Web/Wasm e iOS abren la ruta común
  `FavoriteMessagesConversationId`, muestran el mensaje favorito creado por fixture reversible,
  abren la conversación origen desde esa ruta, eliminan el favorito por RPC autorizado y vuelven a
  abrir favoritos verificando el estado vacío común.
- `CHAT-FOCUSED-MESSAGE` queda cerrado como candidato: la misma conversación temporal se abre por
  deep link enfocado, el foco se resuelve desde `ChatScreenHost` común, se expone una señal
  observable por plataforma sin duplicar producto y se consume una sola vez.
- Evidencias finales del mismo SHA `c0f06dd23f8e06e59aee5c123f34383b46825937`: Web
  `build-reports/web/chat-favorites-focused-evidence.json`, Android
  `build-reports/android/chat-favorites-focused-evidence.json`, iOS
  `build-reports/ios/chat-favorites-focused-evidence.json`.
- Las tres lanes usaron datos temporales reversibles con identificador único, capturas reales
  `*-favorites-list`, `*-favorites-open-source`, `*-focused-message` y `*-favorites-empty`, y
  limpieza física verificada con residuo cero en `chat_threads`, `chat_messages`,
  `chat_participants`, `chat_attachments`, `chat_message_states`, `chat_events` y
  `conversation_user_state`.
- `SCR-CHAT` permanece **COMÚN con límites**: siguen pendientes composer/envío/emoji, acciones de
  mensaje restantes, adjuntos/audio, grupo/notificaciones, perfil global, traducción, mapa/SOS,
  retorno completo y errores/retry por subflujo.

## Resultado local consolidado (SHA `26d385bd`, todavía sin PR)

- Android, Wasm e iOS consumen ya `ChatProductHostContent`/`ChatScreenHost` para la ruta de producto. Android inyecta únicamente adaptadores de avatar, picker/cámara, audio, media/documentos, clipboard, navegación y su `ChatAndroidViewModel` lifecycle-safe; el antiguo `ChatScreen` dejó de ser la ruta activa.
- El renderer alternativo antiguo de lista/detalle se eliminó. La raíz común incluye cabecera normal y seleccionada, historial/foco, favoritos, composer, reply/edit, reenvío, adjuntos, audio, acciones y gestión de grupo, conectados al `ChatViewModel` y `ChatRepository` comunes.
- La regresión Android de `CHAT-TRANSLATION`/`FLOW-TRANSLATOR` quedó corregida: el botón común hereda el registro global de textos y activa el overlay de shell Android con captura `PixelCopy`. La evidencia `evidence/chat/26d385bd-android-global-fang` acredita ventana completa, coincidencia captura/hitboxes y traducción real de la burbuja pulsada.
- Preflight incremental del SHA: Android `compileDebugKotlin`, 21 tests focales y `assembleDebug`; Wasm 62/62 browser tests; iOS Intel x86_64 host y `build-for-testing` firmado, todos PASS.
- `SCR-CHAT` permanece **COMÚN con límites**: faltan preflight exhaustivo, evidencia visual Android↔Wasm↔iOS con la misma conversación, mutaciones reversibles y retorno para cada subflujo, retry real de `OVR-MEDIA`, reproducción consecutiva de `CHAT-AUDIO`, comportamiento fino de autoscroll/IME, y evidencia de `OVR-PUBLIC-PROFILE`, `FLOW-DOCUMENT-VIEWER`, `FLOW-EMOJI` y `FLOW-SHELL-NAV` desde Chat.
