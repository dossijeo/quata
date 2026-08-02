# Inventario de pantallas — migración Compose Multiplatform v2

> Fuente de verdad del método de trabajo: [`MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`](./MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).
> Este inventario describe el estado del producto; si una regla operativa entra en conflicto con él,
> prevalece el modelo operativo.

Base auditada: `main` en `5d2a52d1` (2 de agosto de 2026), después de #154 y #156.

Android sigue siendo la referencia de producto. En este inventario **COMÚN** significa que Android,
Wasm e iOS invocan la misma raíz Compose de `commonMain`; los servicios del sistema, el transporte y
los renderizadores de medios pueden seguir siendo adaptadores de plataforma. No implica que todas las
mutaciones o la comparación visual estén cerradas. **PARCIAL** significa que se comparten ViewModels o
componentes, pero todavía hay una composición, flujo o sustituto específico de plataforma.

## Estados

- **COMÚN / revisar**: raíz común comprobada en código; falta evidencia visual o funcional suficiente.
- **COMÚN con límites**: raíz común, pero hay una capacidad concreta incompleta o no verificada.
- **PARCIAL**: existen piezas comunes, no una pantalla equivalente completa en las tres plataformas.
- **FALLBACK**: ruta de producto monta un host simplificado o alternativo.
- **AUSENTE**: no hay ruta de producto equivalente demostrada.

## Pantallas de producto

| Pantalla Android de referencia | Android y raíz común actual | Wasm actual | iOS actual | Estado y bloqueo siguiente |
|---|---|---|---|---|
| `LoginScreen.kt` — `LoginScreen` | Android llama a `LoginScreenHost`. Login, registro y recuperación se agrupan en `AuthProductHostContent` común. | `WebLoginHost` llama a `AuthProductHostContent` con `WebAuthRepository`; conserva sólo sesión, Web Push y preferencias en el borde Web. Login y restauración de sesión Web ya tienen evidencia acreditada. | `QuataAuthViewController` llama a `AuthProductHostContent` con repositorio/sesión iOS. | **COMÚN con evidencia Web parcial.** La raíz de login ya no es una maqueta Web/iOS. Falta la evidencia final de retorno a ruta, logout completo de producto y ciclo Web Push; el shell Web permanente sigue sin integrarse en `main`. |
| `RegisterScreen.kt` — `RegisterScreen` | Android llama a `RegisterScreenHost`. | Se llega mediante la navegación común de `AuthProductHostContent`; disponibilidad depende de la configuración pública Web y del backend. | Se llega mediante esa misma navegación; el alta iOS continúa cerrada si faltan los ajustes públicos/challenge requeridos. | **COMÚN con límites.** No introducir rutas ni formularios paralelos; comprobar el contrato de alta configurado en cada plataforma. |
| `ForgotPasswordScreen.kt` — `ForgotPasswordScreen` | Android llama a `ForgotPasswordScreenHost`. | Ruta común dentro de `AuthProductHostContent`. | Ruta común dentro de `AuthProductHostContent`. | **COMÚN / revisar.** Falta E2E de recuperación, no extracción de otra raíz. |
| `FeedScreen.kt` \| `CutreFeed` | Android llama a `FeedScreenHost`. | `WebFeedHost` llama a la misma raíz; el antiguo **CutreFeed** ya no debe tratarse como ruta de producto. La protección contra pager vacío de #146 evita el `IndexOutOfBoundsException` durante la navegación. | `QuataFeedViewController` llama a esa raíz con adaptadores iOS de medios, avatar, presencia y compartición, pero no pasa `isLandscape`: se queda en el valor por defecto `false`. | **COMÚN — GO visual global PR #138, con deuda iOS concreta.** La raíz está compartida; siguen defectos de shell/navegación y glifos emoji en Wasm. En iOS Feed falta inyectar la información real de ventana/orientación y repetir la comparación landscape; no atribuirle la adaptación ya acreditada sólo para Oficial. |
| `OfficialFeedScreen.kt` — `OfficialFeedScreen` | Android llama a `OfficialFeedScreenHost`. | `WebOfficialHost` llama a `OfficialFeedScreenHost`. | `QuataOfficialViewController` llama a `OfficialFeedScreenHost`; la raíz consulta `rememberQuataWindowLayoutInfo` y PR #141 corrigió su adaptación horizontal. | **COMÚN — GO Android/Web/iOS PR #141.** Se conserva ese GO acreditado. El pendiente concreto es retorno a autenticación desde Oficial dentro del shell Web, no una nueva ronda genérica de paridad visual. |
| `OfficialPostEditorScreen.kt` — `OfficialPostEditorScreen` | Editor Android completo y específico de Android. | La navegación actual redirige a `composer`; no hay un editor oficial equivalente probado. | El router expone fábrica para editor, pero la composición de producto no demuestra el editor Android. | **AUSENTE/PARCIAL P0.** Extraer una raíz de editor común y dejar picker, cámara, medios, traducción y permisos como slots. No confundir el feed oficial común con su editor. |
| `NeighborhoodsScreen.kt` — directorio | Android conserva la orquestación completa. Listas, miembros y varias secciones están en `commonMain`. | `WebNeighborhoodsHost` es un host propio; algunos callbacks/datos se inyectan vacíos (ranking y comentarios). | `QuataNeighborhoodsViewController` usa host iOS propio, con adaptadores y subrutas específicas. | **PARCIAL P0.** Crear una raíz común del directorio y sus transiciones; no aceptar listas comunes como equivalencia de flujo. |
| `NeighborhoodsScreen.kt` — `CommunityProfileScreen` | Perfil comunitario completo en la composición Android. | Se abre a través de `WebNeighborhoodsHost`; adjuntos, ranking y comentarios no son equivalentes en todas las rutas. | Hay detalle/perfil de miembro y piezas iOS, no una raíz de perfil comunitario completa demostrada. | **PARCIAL/AUSENTE P0.** Extraer perfil comunitario, posts, comentarios, roles y acciones a un host común. |
| `ConversationsScreen.kt` — `ConversationsScreen` | Inbox Android completo; comparte ViewModel y contenidos de lista/picker/invitaciones. | La lista se resuelve dentro de `ChatBrowserHostContent`, que es una composición alternativa. | El controlador de chat iOS usa también `ChatBrowserHostContent`. | **FALLBACK/PARCIAL P0.** Sustituir el host browser por una raíz `ConversationsScreenHost` derivada de Android; conservar avatar, contactos y navegación como adaptadores. |
| `ChatScreen.kt` — `ChatScreen` | Chat Android completo. Hay numerosas burbujas, estados y ViewModels en común. | `WebChatHost` monta `ChatBrowserHostContent` y sustituye campos por controles nativos Web. | `QuataChatViewController` monta `ChatBrowserHostContent`; no propaga varios slots declarados (avatar, mapa, traducción). | **FALLBACK/PARCIAL P0.** La raíz compartida actual es explícitamente browser-style, no la pantalla Android. Migrar la composición Android, dejando grabador, reproductor, picker, mapa y visor como adaptadores. |
| `NotificationsScreen.kt` — `NotificationsScreen` | Android llama a `NotificationsHostContent`. | `WebNotificationsHost` llama a `NotificationsHostContent`. | `QuataNotificationsViewController` llama a `NotificationsHostContent`. | **COMÚN con límites.** Verificar categorías, permisos, badge y tiempo relativo en sesión real; el shell/ruta sigue siendo plataforma-específico. |
| `ProfileScreen.kt` — `ProfileScreen` / Cuenta y SOS | #156 integra `ProfileScreenHost` como raíz Compose común y Android monta esa raíz. | #156 sustituye la composición parcial por el host común; el editor de avatar Web existe con contratos de selección, transformación y subida. | #156 monta `IosProfileHost`/SOS sobre la raíz común con gateway y uploader iOS. El postflight de `main` `5d2a52d1` acredita Feed y perfil remoto públicos, auth real y relanzamiento normal, además de Cuenta/Perfil visual. | **COMÚN con límites.** El postflight iOS PASS no abre el subflujo SOS para no mutar: acredita acceso, estado y 1/5 contactos visibles, no el flujo completo. La mutación E2E de avatar Web no está acreditada: no se guardó/subió un avatar desechable, por lo que sigue como capacidad contractual pendiente. |
| `CreatePostScreen.kt` — `CreatePostScreen` | #154 extrajo e integró `CreatePostRoot` como raíz común; Android la monta con sus adaptadores de captura/pipeline. | #154 sustituye el host alternativo por `CreatePostRoot` y un transporte Web de borde. | #154 sustituye el host iOS alternativo por `CreatePostRoot` y adaptadores iOS de medios/transporte. | **COMÚN con límites.** Ya no es AUSENTE/FALLBACK. La publicación real, picker/cámara/exportación y paridad visual autenticada requieren postflight; #154 no aporta por sí sola un GO visual final. No cambiar RLS para cerrar esos checks. |
| `WhatsNewScreen.kt` — `WhatsNewScreen` | Android consume `WhatsNewContent`. | `WebWhatsNewHost` consume `WhatsNewContent`. | El bootstrap/controlador iOS consume `WhatsNewContent`. | **COMÚN / revisar.** Confirmar descubribilidad y datos/versionado en los flujos reales. |
| `ReleaseHistoryScreen.kt` — `ReleaseHistoryScreen` | Android consume `ReleaseHistoryContent`. | Web llega desde el host de Novedades/ruta hash. | Hay controlador y bootstrap iOS para `ReleaseHistoryContent`. | **COMÚN / revisar.** Falta validación de navegación, catálogo y aspecto; no una reescritura visual. |

La entrada solicitada se conserva con su nombre visible: `FeedScreen.kt | CutreFeed`. Describe el
fallback histórico, no una segunda pantalla que deba mantenerse.

## Superficies transversales que bloquean validación real

| Superficie | Hecho comprobado | Pendiente prioritario |
|---|---|---|
| Shell, navegación y deep links | Android mantiene su grafo; Wasm usa `Main.kt`/hash; iOS usa `IosAuthenticatedHostRouter` en Swift. Wasm actual envuelve el chrome sólo cuando `isSessionReady`; por eso el trabajo de shell permanente no puede darse por integrado todavía. | Consolidar el comportamiento visible: header y navegación para Feed/Oficial también sin sesión, redirección de privadas a Auth y retorno a la ruta original. Validar transiciones repetidas contra el error del pager ya corregido. |
| Emoji y glifos | El feed común y Oficial usan catálogo/controles comunes; capturas Wasm muestran tofu en texto, comentarios y menús. | Resolver renderizado/atlas/fuentes para Wasm y contrastar con Android; no convertir los emoji en HTML alternativo. |
| Adaptación iOS | Oficial obtiene layout con `rememberQuataWindowLayoutInfo` dentro de su raíz común y #141 corrigió el tamaño/landscape. Feed tiene un parámetro `isLandscape`, pero `QuataFeedViewController` no lo inyecta y lo deja en `false`. | Corregir Feed iOS con una fuente real de tamaño/orientación y repetir su comparación landscape. No declarar que la mejora de Oficial cubre Feed. |
| Visualizador de documentos/Office | Android conserva lector vendorizado; Web usa DocMentis e iOS Quick Look. | Compartir estado, selección y chrome cuando se migre el flujo que los invoca; los renderizadores son adaptadores, no una razón para duplicar pantallas. |
| Ajustes y cierre de sesión | Web tiene `WebSettingsHost`; iOS tiene `QuataSettingsViewController`; Android los integra con perfil/grafo. | Diseñar la ruta común de perfil/cuenta y mantener los mecanismos de logout/Web Push y Keychain como bordes de plataforma. |
| Cuenta, Perfil y SOS (#156) | `ProfileScreenHost` común ya está integrado en Android, Wasm e iOS. En iOS `main` `5d2a52d1` pasó Feed/perfil público remoto, auth real mediante `.xctestrun`, relanzamiento y visual de Cuenta/Perfil. | Completar SOS: sólo se observó acceso/estado y 1/5 contactos, sin abrir su subflujo para evitar mutación. Registrar una subida de avatar Web temporal y su limpieza, o mantenerla explícitamente contractual. |

## Superficies internas y transversales de Android

| Superficie | Estado actual | Tratamiento |
|---|---|---|
| `NeighborhoodUsersScreen` (privada en `NeighborhoodsScreen.kt`) | Subruta Android de miembros; se apoya en contenidos comunes pero la navegación del directorio sigue parcial. | Migrarla junto con la futura raíz común de Comunidades, no como reemplazo autónomo. |
| `RichTextEditorQaScreen.kt` | Herramienta de QA en Android, expuesta desde `AppNavGraph`; no es una ruta de producto. | Mantener Android-only salvo que el editor común requiera una aplicación de demostración. No bloquea la migración funcional. |
| `QuataSplashScreen` | Está en `designsystem/commonMain`; Android la monta desde `MainActivity`. | Es una superficie común reutilizable. Revisar su conexión con los launchers Web/iOS y el shell, sin reimplementarla por plataforma. |
| `ShareToQuataDialog` | Diálogo Android de recepción/elección de destino. Web e iOS disponen de hosts de share/inbox y adaptadores propios. | Tratarlo como flujo transversal: compartir destino, estado y errores al migrar el compositor/chat; los mecanismos de entrega del sistema permanecen por plataforma. |

## Criterio de ejecución y evidencia

1. Antes de asignar código, revisar la pantalla Android y señalar la raíz, eventos, datos y servicios que deben ser comunes.
2. El agente de implementación sustituye el fallback por esa raíz en una rama aislada y sólo compila los destinos afectados.
3. El agente abre una PR. Un revisor independiente arranca la rama, captura Android y Wasm o iOS en la misma ruta y compara el resultado.
4. Sólo tras verificar la raíz común, compilación y comparación visual se integra. Las pruebas funcionales autenticadas del usuario se registran por plataforma; no se usan fixtures como evidencia de backend.
5. No se cambian políticas RLS ni esquema de Supabase para cerrar una pantalla. Una limitación de backend se documenta como tal.

## Orden técnico actual

`shell/autenticación integrada → Feed/Oficial (paridad y emojis) → Conversaciones/Chat → Comunidades/perfil comunitario → Mi perfil → Compositor → Editor oficial → Notificaciones → Novedades/Historial`.

Este orden parte de rutas existentes en Android y de bloqueos observables en `main`; no declara que
una capacidad esté terminada sólo porque exista un ViewModel o una pantalla de prueba.
