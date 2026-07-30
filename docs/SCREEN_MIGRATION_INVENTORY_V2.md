# Inventario de pantallas — migración Compose Multiplatform v2

Estado base auditado: `main` en `22879a1f` (30 de julio de 2026).

Este documento toma Android como referencia de producto. Una pantalla solo se considera migrada
cuando Android, Wasm e iOS montan la misma raíz Compose de `commonMain`. Compartir modelos,
ViewModels o componentes sueltos no basta.

## Significado de los estados

- **COMÚN**: misma raíz Compose de `commonMain`; la plataforma solo inyecta adaptadores.
- **PARCIAL**: comparte piezas, pero conserva una raíz u orquestación propia.
- **FALLBACK**: pantalla alternativa simplificada, read-only o con funciones no disponibles.
- **AUSENTE**: no existe una ruta utilizable en esa plataforma.
- **REVISAR**: la raíz parece común, pero falta comparación visual/funcional.
- **INTERNA**: utilidad de desarrollo que no forma parte del producto publicado.

## Pantallas de producto

| Orden | Pantalla Android de referencia | Android actual | `commonMain` actual | Wasm actual | iOS actual | Trabajo exacto para que sea realmente común | Estado |
|---:|---|---|---|---|---|---|---|
| 1 | `LoginScreen.kt` — `LoginScreen` | En `app`; usa `LoginViewModel` mediante un adaptador Android, `AuthScreenLayoutContent` y `LoginForm`. | Formulario, catálogo, estado, eventos y ViewModel ya comunes. En PR #125 se añade `LoginScreenHost` como raíz literal y Android pasa a usarla. | En `main`, `WebLoginHost` monta `AuthBrowserLoginHostContent` y sustituye inputs/botón por controles Web. PR #125 crea una app Wasm aislada que monta literalmente `LoginScreenHost` y realiza login real. | Monta `AuthBrowserLoginHostContent`, no la raíz Android. | Aceptar visualmente PR #125; después hacer que el launcher iOS monte el mismo `LoginScreenHost` y conservar únicamente transporte/Keychain como adaptadores. | **PR #125 / Wasm validado funcionalmente por el usuario** |
| 2 | `RegisterScreen.kt` — `RegisterScreen` | Wrapper Android propio; usa el ViewModel, layout y formulario comunes. | `RegisterViewModel`, `RegisterForm`, catálogo, estados y eventos están comunes; falta una raíz `RegisterScreenHost` completa. | Forma parte del host combinado Web y puede quedar deshabilitado por configuración; no es la pantalla Android literal. | Forma parte del host combinado iOS y depende de `registrationEnabled`; no es la pantalla Android literal. | Extraer el wrapper completo a `RegisterScreenHost`; Android/Wasm/iOS deben llamarlo. Inyectar solo el challenge de registro y transporte; eliminar formularios alternativos y overrides visuales. | **PARCIAL** |
| 3 | `ForgotPasswordScreen.kt` — `ForgotPasswordScreen` | Wrapper Android propio; todavía contiene resolución de pregunta, Toast y navegación. | `ForgotPasswordViewModel` y `ForgotPasswordForm` son comunes; falta la raíz completa y un efecto de éxito independiente de Toast. | Está embebida en `AuthBrowserLoginHostContent`, con navegación interna distinta. | También está embebida en el host combinado. | Crear `ForgotPasswordScreenHost` común con catálogo, resolución de pregunta y efecto de éxito; inyectar únicamente presentación de mensaje/navegación. Sustituir las tres raíces. | **PARCIAL** |
| 4 | `FeedScreen.kt` — `FeedScreen` | Wrapper Android fino sobre `FeedScreenHost`; conserva los adaptadores Android de medios, avatar, share, permisos, lifecycle y navegación. | `FeedScreenHost` posee la orquestación completa: ViewModel, pager vertical, viewport, rail, autor, ranking, comentarios, emojis, presencia, refresh y paginación. | Monta la misma raíz `FeedScreenHost` con repositorio, media/avatar, presencia, navegación y acciones Web reales; el CutreFeed dejó de ser una ruta de producto. | Monta la misma raíz `FeedScreenHost` con repositorio autenticado/público y adaptadores iOS de media, avatar, presencia, share y navegación; mantiene el shell superior e inferior tanto anónimo como autenticado. | Ronda funcional del usuario sobre `main`: navegación pública/autenticada, medios y mutaciones. La arquitectura y el gate visual quedaron cerrados en PR #138; no queda una raíz alternativa que migrar. | **COMÚN — GO VISUAL / MERGED #138** |
| 5 | `OfficialFeedScreen.kt` — `OfficialFeedScreen` | Pager completo, medios, autor, detalle, comentarios, acciones y editor. | Existen ViewModel, viewport, pager, tarjeta, detalle y rail comunes, pero no la raíz completa Android. | `OfficialBrowserHostContent` read-only, sin slots reales de media/acciones. | El mismo browser read-only mediante `QuataOfficialViewController`. | Extraer la orquestación completa a `OfficialFeedScreenHost`; inyectar medios/avatar/navegación; conectar repositorio autenticado común y eliminar `OfficialBrowserHostContent`. | **FALLBACK P0** |
| 6 | `OfficialPostEditorScreen.kt` — `OfficialPostEditorScreen` | Editor completo de texto/media, metadatos, traducción, previews, publicación y edición. | Hay numerosos formularios y shells comunes, pero estado, permisos, selección, traducción, publicación y composición final siguen en Android. | No ofrece el editor completo de producto. | `QuataOfficialEditorViewController` solo recibe un `content` arbitrario del host; no monta el editor Android. | Crear una única raíz común con ViewModel y todos los pasos del editor; inyectar picker/cámara/editor de medios/traductor y repositorio. Sustituir el host de contenido arbitrario. | **AUSENTE/PARCIAL** |
| 7 | `NeighborhoodsScreen.kt` — `NeighborhoodsScreen` | Directorio, búsqueda, miembros, chats, follow y navegación a perfiles. | `NeighborhoodsViewModel`, `NeighborhoodListContent` y `NeighborhoodUsersContent` son comunes; la máquina de navegación local sigue duplicada. | Host Web propio; reutiliza listas, pero recibe ranking vacío/callbacks deshabilitados desde `Main`. | Host iOS propio; usa avatar fallback, padding vacío y `onOpenAttachment = {}`. | Extraer una raíz común que posea lista/miembros/selección y eventos. Inyectar avatar, navegación, conversación y adjuntos. Eliminar las máquinas de estado Web/iOS duplicadas. | **PARCIAL** |
| 8 | `NeighborhoodsScreen.kt` — `CommunityProfileScreen` | Perfil comunitario completo con cabecera, KPIs, roles, follow/chat, adjuntos, galería, posts, comentarios y ranking. | Muchas secciones son comunes, pero no existe una raíz completa con el ViewModel y todas las acciones. | Host propio con acciones y datos parcialmente vacíos; medios/adjuntos son slots incompletos. | Solo existe un visor mínimo de miembro y helpers sueltos para detalle/comentarios/ranking. | Crear `CommunityProfileScreenHost` común desde la pantalla Android; inyectar medios/avatar/documentos/navegación. Conectar repositorio común con posts, comentarios, roles y follow reales. | **AUSENTE/PARCIAL P0** |
| 9 | `ConversationsScreen.kt` — `ConversationsScreen` | Inbox completo, búsqueda, avatars, contactos, invitaciones, privado/grupo, community chat, borrar/undo y navegación. | `ConversationsViewModel`, cabecera, lista, picker, FAB, invitaciones y undo tienen piezas comunes; la raíz y parte de la orquestación siguen Android-only. | `ChatBrowserHostContent` ofrece una lista simplificada con botones Material y operaciones limitadas. | Usa el mismo browser simplificado. | Extraer `ConversationsScreenHost` completo. Inyectar contactos/permisos/avatar/navegación; mantener repositorio y eventos comunes. Eliminar la rama `conversationId == null` del browser fallback. | **FALLBACK/PARCIAL P0** |
| 10 | `ChatScreen.kt` — `ChatScreen` | Conversación completa: realtime/outbox, selección, reply, editar/borrar/favoritos/forward/report, adjuntos, audio, SOS, traducción, participantes y composer. | ViewModel, estados y muchas piezas visuales están comunes; `ChatBrowserHostContent` es otra composición simplificada y no equivale a Android. | Usa el browser host y además sustituye input/botón por controles Web nativos. | Usa el browser host; varios callbacks declarados (`avatar`, mapa, traducción) ni siquiera se propagan. | Mover la raíz Android completa a commonMain. Inyectar player/recorder/picker/documentos/mapa/avatar/traducción y lifecycle de plataforma. Eliminar controles visuales alternativos y browser host. | **FALLBACK/PARCIAL P0** |
| 11 | `NotificationsScreen.kt` — `NotificationsScreen` | Wrapper fino alrededor de `NotificationsHostContent`; añade catálogo Android, reloj y navegación. | La raíz visual y ViewModel ya son comunes. | Monta `NotificationsHostContent`; repositorio solo proyecta parte de las categorías y el tiempo relativo es pobre. | Monta `NotificationsHostContent`; fija permiso requerido y muestra timestamps sin formatear. | Compartir catálogo/relative-time y modelo de delivery; conectar repositorios equivalentes. No hace falta reescribir la pantalla, solo retirar divergencias de datos/adaptadores y revisar visualmente. | **COMÚN / REVISAR** |
| 12 | `ProfileScreen.kt` — `ProfileScreen` | Perfil completo: avatar, datos, tema/touch flow, cuenta, SOS, preguntas, guardado, logout y borrado. | `ProfileViewModel`, layouts, formularios, cuenta y SOS tienen piezas comunes; no existe una raíz completa. | `WebProfileHost` es composición propia y muestra acciones de guardado aunque el repositorio remoto rechaza escrituras. | Solo hay editor SOS aislado y visor mínimo de miembros; no existe “Mi perfil” completo. | Extraer `ProfileScreenHost` completo con navegación y estados. Inyectar avatar, contactos, preferencias y lifecycle de cuenta. Conectar repositorio común real y eliminar acciones engañosas/no-op. | **AUSENTE/PARCIAL P0** |
| 13 | `CreatePostScreen.kt` — `CreatePostScreen` | Compositor completo de texto/imagen/vídeo con edición, exportación, ubicación, EXIF, permisos, preview, upload y publicación. | ViewModel y numerosos formularios/previews son comunes, pero la máquina de pasos y pipeline completo siguen Android-only. | Host simplificado; `WebPostComposerPublicationUnavailableRepository` siempre falla al publicar. | Host simplificado; `iosComposerPublicationUnavailableRepository` siempre falla y no tiene captura de vídeo/editor/export completo. | Extraer la raíz y máquina de pasos Android a commonMain. Inyectar captura/picker/editor/export/upload/ubicación. Implementar repositorio común sobre contratos existentes sin modificar RLS. Sustituir por completo ambos hosts parciales. | **FALLBACK P0** |
| 14 | `WhatsNewScreen.kt` — `WhatsNewScreen` | Wrapper mínimo de `WhatsNewContent`. | La raíz visual es común. | Monta `WhatsNewContent`, pero la ruta no es descubrible normalmente y usa RPCs nominalmente Android. | Monta `WhatsNewContent`. | Unificar bootstrap, catálogo, versión instalada y repositorio multiplataforma; después comparación visual. No requiere recrear UI. | **COMÚN / REVISAR** |
| 15 | `ReleaseHistoryScreen.kt` — `ReleaseHistoryScreen` | Wrapper mínimo de `ReleaseHistoryContent`. | La raíz visual es común. | Monta `ReleaseHistoryContent`; depende de acceso por hash/flujo de Novedades. | Monta `ReleaseHistoryContent`. | Unificar navegación, catálogo y repositorio; revisar visualmente en las tres plataformas. | **COMÚN / REVISAR** |

## Pantallas o superficies internas

| Superficie Android | Situación | Decisión |
|---|---|---|
| `FeedMessageScreen` (privada dentro de `FeedScreen.kt`) | Estados loading/error/empty del Feed; ya usa `FeedStatusContent`. | Debe quedar absorbida por la futura raíz común del Feed, no convertirse en tarea independiente. |
| `NeighborhoodUsersScreen` (privada dentro de `NeighborhoodsScreen.kt`) | Subruta de miembros; su contenido principal ya es común. | Debe quedar absorbida por `NeighborhoodsScreenHost`. |
| `RichTextEditorQaScreen.kt` | Herramienta interna de QA, no destino de producto. | Mantener Android-only salvo que el editor común necesite una aplicación de demostración multiplataforma. No bloquea la migración del producto. |

## Superficies de pantalla completa sin archivo `*Screen.kt`

| Superficie | Android | Wasm/iOS | Tratamiento |
|---|---|---|---|
| Splash | `QuataSplashScreen` ya reside en el design system común. | Debe poder montarse desde el launcher común. | Revisar junto con el shell; no reimplementar. |
| Shell y navegación | `AppNavGraph`, Scaffold, barras y deep links siguen en `app`. | Web tiene `Main.kt`/hash router propios; iOS usa `QuataIosApp.swift`. | Extraer una única `QuataApp`/máquina de navegación común después de Auth y antes del resto de pantallas. |
| Share to Qüata | `ShareToQuataDialog` Android. | Hay hosts comunes/parciales y adapters de plataforma. | Inventariar como flujo transversal cuando Compositor sea común. |
| Visor multimedia/documentos | Visor Android y lector Office vendorizado. | Web usa DocMentis; iOS Quick Look. | Mantener renderers como adaptadores de plataforma; compartir selección, estado, errores y chrome cuando proceda. |
| Ajustes | Integrados dentro de `ProfileScreen`. | Web/iOS tienen pantallas de Ajustes separadas. | No conservar una ruta distinta como sustituto del Perfil; absorber apariencia/cuenta en `ProfileScreenHost`. |

## Regla de ejecución por pantalla

1. El orquestador inspecciona personalmente la pantalla Android y redacta el contrato exacto.
2. Un único Terra implementa ese contrato en una rama aislada y reemplaza cualquier fallback.
3. El Terra solo compila los targets afectados; no ejecuta suites instrumentadas.
4. El Terra abre una PR.
5. El revisor Sol levanta la rama, captura Android y Wasm/iOS, y compara la misma pantalla.
6. Solo un GO visual y la comprobación de raíz común permiten mergear.
7. Después del merge, el revisor actualiza `USER_TEST_QUEUE.md` y mantiene Wasm/iOS de `main` activos.

## Orden de dependencia

`Login → Register → Recuperación → Shell/navegación → Feed → Oficial → Conversaciones → Chat → Comunidades → Perfil comunitario → Mi perfil → Compositor → Editor oficial → Notificaciones → Novedades → Historial`

Este orden no es un backlog especulativo: cada elemento corresponde a una pantalla Android
existente y no se descompone en trabajo adicional hasta que el orquestador haya terminado su
análisis concreto.
