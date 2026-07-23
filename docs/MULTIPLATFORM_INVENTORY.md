# Inventario de migración multiplataforma

Actualizado durante la FASE 6. Este documento separa lógica compartible de adaptadores de plataforma; no autoriza mover varias áreas a la vez.

| Área actual | Estado | Trabajo pendiente |
| --- | --- | --- |
| `core` | Parcialmente KMP | Modelos, navegación/deep links, preferencias de sesión/tema/Touch Flow y contratos de cámara, portapapeles, compartir, preferencias, selector de archivos, permisos, ubicación, notificaciones y media se comparten; permanecen adaptadores Android concretos para Media3, cámara, permisos, notificaciones, almacenamiento y bridges de sistema. |
| `designsystem` | Parcialmente KMP | Theme, controles base, shell `QuataScreen`, splash, barra inferior genérica, pull-to-refresh, rail de acciones de publicaciones, scaffold de editores, Touch Flow, estilos de canvas de texto, ranking, layout de panel flotante, fila/banner/input de comentarios y renderer de selector emoji comunes; quedan componentes con recursos Android, Coil, cámara, audio y configuración de ventana. |
| Feed | KMP parcial | Dominio, estado y ViewModel compartidos; media y repositorio siguen como adaptadores Android. |
| Chat | KMP parcial | Dominio, estado, ViewModels, políticas de lista, agrupación de destinos del selector de conversaciones, banner de compositor y panel visual de adjuntos compartidos; caché, contactos, WorkManager, launchers de adjuntos y reproductores siguen Android. |
| Official | KMP parcial | Dominio, estado, eventos, ViewModel, ranking, transformaciones HTML y bloques Compose de carga/vacío, autor con slot de avatar, tipología, texto y lectura compartidos; data source, paginación, Coil, compartir, media y el resto de pantalla Android continúan en adaptación progresiva. |
| Post Composer | KMP parcial | Dominio, estado, eventos, ViewModel y selectores Compose de tipo/fondo de publicación compartidos; ubicación, selectores de sistema, foto/vídeo y editores siguen Android. |
| Profile / SOS | KMP parcial | Dominio, ViewModel, política de búsqueda/ordenación y componentes Compose de cabecera/fila SOS compartidos; launcher, permisos, contactos del sistema, avatar y diálogo contenedor siguen Android. |
| Auth | KMP parcial | Contrato sin `Context`, casos de uso, estados, eventos, ViewModels y formularios Compose de login/registro/recuperación viven en `:feature:auth/commonMain`. Data source, Google Sign-In, recursos y opciones localizadas continúan como adaptadores Android. |
| Communities (`neighborhoods`) | KMP parcial | Dominio, estado, ViewModel, listado, tarjetas, miembros, seguidores/seguidos, filas de usuario, KPI, adjuntos, galería y acciones de perfil/roles Compose compartidos en `:feature:neighborhoods`; caché, data source, perfil ampliado, reproducción de vídeo y adaptadores Android permanecen pendientes. |
| Notifications | KMP parcial | Dominio, estado, ViewModel y pantalla Compose (lista, tarjetas y descarte) compartidos en `:feature:notifications`; canales, push, receivers, reloj/localización y data source son adaptadores Android. |
| About / release history (`whatsnew`) | KMP parcial | Modelos, contrato, coordinador de inicio y pantallas Compose de novedades/historial compartidos en `:feature:whatsnew`; caché, WorkManager, recursos y window insets son adaptadores Android. |
| External share | KMP parcial | Payload, ViewModel y política de tipos de adjunto compartidos en `:feature:externalshare`; reutiliza la agrupación común de destinos de chat. Intents, URI, shortcuts, lectura y visor son adaptadores Android. |
| Traductor Fang | KMP parcial | Modelos, protocolo HTTP, errores, calentamiento, caso de uso y estado de cada bloque viven en `core/commonMain`; el registro, modificador de texto y renderer de fondo Compose viven en `designsystem/commonMain`. FastText, OkHttp, caché SQLite, captura de fondo y textura/recurso localizado siguen como adaptadores Android. |
| Quata Touch Flow | KMP | El efecto, su estado, geometría, animación y renderer Compose viven en `designsystem/commonMain`; su contrato de preferencia por usuario vive en `core/commonMain` y `SharedPreferences` es el adaptador Android actual. |
| Fondos procedurales de chat | KMP parcial | La semilla FNV-1a, selección de paleta, clave de caché y renderer Compose portable viven en `designsystem/commonMain`. `ProceduralChatBackground` conserva EGL/GLES, `Bitmap`, ficheros WebP y `Context` como optimización Android; faltan adaptadores JS/iOS y un contrato de almacenamiento binario para persistir imágenes. |
| Settings | KMP parcial | `:feature:settings/commonMain` contiene los controles Compose de apariencia (tema y Quata Touch Flow), reutilizados por Perfil; preferencias y almacenamiento son adaptadores de plataforma. |
| Visor de documentos | KMP parcial | La detección de formatos previsualizables vive en `core/commonMain`; `document-reader`, lectura de archivo, miniaturas y renderer integrado siguen como adaptador Android. |
| Texto enriquecido | KMP parcial | Motor, estado, parser, acciones, runtime y renderer Compose viven en `designsystem/commonMain`; el editor visual Android conserva recursos, contexto y portapapeles. |
| Foto y vídeo | KMP parcial | Perfiles de vídeo, metadatos, rotación, geometría/presets de recorte, contratos multimedia, muestreo y transformaciones de orientación de imagen viven en `commonMain`; editores, Media3, cámara, `ContentResolver` y almacenamiento requieren adaptadores `FilePicker`, `Camera`, `VideoEditor` y `MediaStore`. |
| Subtítulos y transcripción de vídeo | KMP parcial | Segmentos, tiempos, estilos, especificaciones de plantilla, animación y layout de palabras viven en `core/commonMain`; `CaptionBitmapRenderer` adapta tipografías/métricas/dibujo Android, y Media3 burn-in, Vosk y entrega de modelo permanecen como adaptadores. |
| Editor de imagen | KMP parcial | Modo de edición y perfiles de salida de post/avatar viven en `feature:postcomposer/commonMain`; estado, operaciones de edición, renderer Compose, bitmap y almacenamiento siguen Android. |
| Audio (grabación, reproducción y adjuntos) | Android | Grabador, permisos, URI, reproductores y caché son Android; definir contratos `AudioRecorder`, `AudioPlayer` y almacenamiento para compartir estado y controles Compose. |
| Presencia, proximidad y ranking | KMP parcial | Contratos/repositorio de presencia pueden compartirse; proximidad, conectividad, Supabase y UI de ranking requieren separar fuentes de sistema y renderer Compose. |
| Moderación, términos y documentos legales | KMP parcial | Modelos y rutas legales comunes; aceptación persistente, assets, `FileProvider` y lectura local continúan Android. |
| Navegación y shell de aplicación | KMP parcial | Destinos y deep links comunes; `AppNavGraph`, barras de sistema, splash, `MainActivity` y control de ventana deben reducirse a launcher/adaptadores de plataforma. |
| Componentes de comunidades | KMP parcial | El renderer de emoji, fila/banner/input de comentarios, ranking y layout de panel flotante ya viven en `designsystem/commonMain`; catálogo localizado, cierre por toque exterior, traducción visual y contenedor Android siguen como adaptadores. |

## Orden de ejecución

1. Auth: crear módulo KMP y extraer contrato, estados, eventos y ViewModels; Google Sign-In queda como adaptador.
2. Communities y Notifications: un módulo por feature, sin mover sus repositorios Android.
3. About/release history y External share: extraer primero modelos y lógica sin intents.
4. SOS: separar coordinador común de ubicación, permisos, notificaciones e intents Android.
5. Traductor Fang: migrar el renderer Compose del overlay; conservar detector, caché y captura como adaptadores de plataforma.
6. Quata Touch Flow: extraer la preferencia por usuario mediante el contrato multiplataforma de preferencias.
7. Fondos procedurales de chat: extraer semilla, selección de paleta, clave y contrato de renderizado; mantener EGL/GLES, WebP y ficheros como adaptadores hasta contar con implementaciones JS/iOS.
8. Rich text, documento y multimedia: definir contratos de plataforma y mover sólo la lógica/UI que compile en `commonMain`.
