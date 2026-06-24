# Q&uuml;ata Android

Version: **0.9.7**
Fecha de version: **2026-06-24**
Estado: **candidata para prueba privada de Play Store**

Q&uuml;ata es una aplicacion Android social y comunitaria construida con Kotlin y Jetpack Compose. Reune feed visual, barrios/comunidades, perfiles, chat en tiempo real sobre Better Messages, notificaciones, SOS, publicacion de contenido y navegacion anonima con acciones protegidas por login.

La version `0.9.7` es la candidata para la prueba privada de Play Store. Consolida la app como una beta avanzada offline-first: mantiene el flujo de publicacion multimedia con editores nativos de video e imagen, incorpora traductor Fang cacheado para chats y comentarios, reduce llamadas de red en perfiles/chat/feed, mejora el polling y las notificaciones de Better Messages, actualiza la identidad visual de Q&uuml;ata, endurece el soporte landscape en Android 9 y Android moderno, y cierra los detalles finales del editor de video para que el crop sea consistente en pausa, reproduccion, exportacion y preview del post. El nucleo funcional ya esta muy completo y probado en emulador y dispositivo fisico, pero todavia queda margen de endurecimiento de release, QA amplio, analitica, monitorizacion y cierre de detalles previos a una `1.0`.

## Mejoras recientes de rendimiento y estabilidad

- Modo offline-first para lecturas Supabase: las consultas GET usan una cache SQLite local por clave exacta de consulta, emiten el valor cacheado al instante y refrescan desde red en segundo plano.
- Apertura de chats cache-first desde perfiles y desde **Q&uuml;ata**: si la conversacion ya esta en la lista/cache local se navega directamente, sin buscar ni crear remoto.
- Polling de Better Messages simplificado: `checkNew` es la unica vigilancia periodica; `/thread/{id}` solo se consulta cuando `checkNew` indica cambios y esos cambios son delta real frente a la cache local.
- Primer arranque sin cache de mensajes: el barrido inicial de Better Messages se carga como leido y sin disparar notificaciones ni sonidos.
- Cadencia de `checkNew` corregida: el modo relajado consulta cada 15 segundos y el minimo cada 30 segundos cuando la app esta viva, evitando esperas de un minuto tras el bootstrap.
- Notificaciones nativas corregidas para app minimizada o pantalla apagada: una conversacion activa en memoria solo silencia la notificacion si la app esta realmente visible; en segundo plano se notifica con Android y se evita el sonido interno del chat.
- Sin barrido completo de hilos al arrancar o reconectar: el polling solo usa `checkNew` y refresca hilos concretos cuando hay cambios reales.
- Estado global de conectividad derivado de Android: si el dispositivo pierde red aparece una banda **Sin conexion** bajo la barra superior de toda la app; al recuperar red se reactiva el polling.
- Cache local de recursos: imagenes con Coil en memoria/disco y videos con Media3 `SimpleCache` LRU de 256 MB, con poda de ficheros antiguos.
- La cache de imagenes aplica a imagenes del feed, adjuntos, avatares publicos y foto propia en Cuenta; los avatares reintentan al recuperar red.
- Feed mas ligero: carga comentarios y likes en la primera consulta para evitar peticiones de detalle por post, mantiene el reproductor actual y vecinos para swipe fluido, y solo recrea el player activo si se recupera la red tras quedar roto.
- El player del feed usa `texture_view` para respetar el recorte del pager y evitar que el siguiente video se superponga en la parte inferior.
- Reduccion de llamadas al abrir perfiles: cache local de perfiles con refresco en segundo plano y reutilizacion de informacion ya cargada para no bloquear la UI.
- Boton **Seguir/Siguiendo** con indicador de carga y actualizacion local inmediata de seguidores, siguiendo, contadores y listas abiertas.
- Menos trabajo por frame en la UI global: tema claro/oscuro se mantiene en estado superior y se evita crear animaciones infinitas cuando no estan activas.
- Sonidos del chat con liberacion explicita de `MediaPlayer` en completado, error o fallo de arranque.
- Publicacion de posts con `author_id` y `content` enviados explicitamente a Supabase para que las politicas RLS y los borrados por autor funcionen de forma consistente.
- Modo traductor Fang integrado en chats y comentarios: deteccion local de idioma, warmup del Space remoto, overlay esmerilado, traducciones cacheadas en SQLite y toggle entre texto original/traducido por mensaje.
- Cajas de escritura de chat y comentarios con altura minima estricta y selector de emojis ajustado para convivir con el teclado sin tapar el texto.
- Textos de chat y comentarios descodifican entidades HTML como `&quot;` antes de renderizarse.
- Publicacion de imagen con ubicacion editable manualmente: si EXIF/GPS falla o detecta mal el lugar, el usuario puede escribirlo; al intentar publicar sin lugar, la pantalla sube a esa seccion y resalta el boton de edicion.
- Identidad visual actualizada: la seccion principal pasa a llamarse **Q&uuml;ata**, el logo compacto usa `Q&#776;` como marca aislada y el splash nativo usa el nuevo icono corporativo.
- Landscape revisado para pantallas grandes y Android moderno: navegacion lateral contenida, cabeceras compactas, SOS flotante, ajuste de insets y comportamiento validado en Android 9 y API 37.
- Ventana SOS adaptada a horizontal con distribucion en dos columnas para contactos y mensaje, evitando el scroll inutil en pantallas anchas.
- Selector de emojis ajustado en chat y comentarios para cerrarle espacio al teclado en landscape y colocarse junto al borde util de la pantalla.
- Editor de video reforzado: crops `Original`, `1:1`, `4:5`, `9:16` y `16:9` mantienen el mismo encuadre en preview, reproduccion y exportacion dentro de una salida final `9:16` con bandas difuminadas, incluyendo fuentes con rotacion de camara y exportacion compatible con Android 9 y Android moderno.

## Funcionalidad principal

- Feed visual tipo reel con publicaciones de texto, imagen y video.
- Editor nativo de video integrado en publicar, con previsualizacion, recorte temporal, recorte de encuadre, mute, subtitulos y exportacion.
- Editor nativo de imagen integrado en publicar, con crop/zoom vertical `9:16`, limite de salida y preservacion de metadatos utiles de localizacion.
- Preview de publicacion de imagen/video en formato `9:16`, con aspecto cosmetico equivalente al feed y reproduccion local para validar el resultado antes de publicar.
- Ranking de publicaciones por likes y fecha, con badge `#rank`.
- Publicaciones de texto con fondo degradado determinista a partir del contenido.
- Shortcodes embebidos en el texto del post para canal, ubicacion, titulo de media, Alka y estado de video.
- Badge de ubicacion o titulo sobre publicaciones de imagen/video.
- Comentarios, likes, reportes y compartido.
- Selector global de emojis con secciones tabuladas y carga perezosa.
- Navegacion anonima: se puede explorar contenido sin cuenta.
- Localizacion por idioma del sistema en espanol, ingles y frances.
- Traductor Fang para mensajes de chat y comentarios del feed, con rutas ES/EN/FR -> FAN y FAN -> idioma de interfaz.
- Modo de color configurable: sistema, modo oscuro y modo claro, con plantillas globales para colores y tamanos de texto.
- Modal de autenticacion para acciones que guardan datos: chats, likes, comentarios, publicar, SOS, seguir, reportar y cuenta.
- **Q&uuml;ata** como seccion principal de barrios/comunidades, con usuarios, perfiles, publicaciones y chats de comunidad.
- Panel de perfil con cache local, refresco en segundo plano, animaciones de contador en KPIs y archivos compartidos en chats abiertos con el usuario.
- Seguimiento de usuarios con estado de carga, actualizacion optimista/local de contadores y listas de seguidores/siguiendo.
- Halo de carga corporativo en avatares clicables.
- Barra inferior de navegacion con iconos ampliados para mejorar la pulsacion y convivencia con la navegacion Android de 3 botones.
- Cuenta con preferencias locales por usuario, incluido `Q&uuml;ata TouchFlow`.
- Pantallas de Cuenta, login, registro y recuperacion ajustadas para funcionar como pantallas completas sin scroll en resoluciones bajas.
- SOS con contactos configurables y rate limit.

## Chat y Better Messages

El chat usa Better Messages en WordPress como backend principal, con una capa Android propia para cache, polling y estado de UI.

- Polling centralizado para conversaciones y mensajes basado en `checkNew`.
- Modos de polling: agresivo en chats, medio en la app, relajado en segundo plano y minimo con la app cerrada; ahora regulan la frecuencia real de `checkNew`.
- Si la app arranca sin cache de mensajes, el primer `checkNew(lastUpdate=0)` procesa todos los hilos devueltos, los persiste, los marca como leidos y no dispara notificaciones.
- Sin barrido periodico de hilos: la app no repasa conversaciones sin cambios por temporizador.
- `checkNew` descubre hilos con actividad y la app filtra contra la cache local para distinguir cambios reales de ruido repetido del servidor.
- Cuando hay delta real en un hilo, se consulta `/thread/{id}` una vez para reconciliar mensajes, ultimo mensaje, unread y metadatos.
- Al abrir la app o recuperar conexion no se fuerzan decenas de `/thread/{id}`: la app reanuda `checkNew` y solo refresca los hilos que el servidor marca con actividad.
- Estado de conectividad del dispositivo expuesto a la UI; la app muestra una banda global **Sin conexion** bajo la barra superior cuando Android informa perdida de red.
- Notificaciones nativas Android cuando la app esta en segundo plano, cerrada, minimizada o con pantalla apagada; el chat activo solo se considera visible si la app esta en `RESUMED`.
- El hilo activo solo se envia a Better Messages como `visibleThreads` en primer plano real, evitando marcar como leidos mensajes recibidos mientras el usuario no mira la conversacion.
- El sonido interno de mensaje recibido solo se reproduce en primer plano; en background se delega en la notificacion nativa Android.
- Cache local de lista de conversaciones, hilos, mensajes favoritos y perfiles.
- Retencion de cache: 24 horas, con reconstruccion solo en primer plano cuando expira.
- Las conversaciones cacheadas pueden abrirse sin red; si falta cache, la busqueda remota queda como fallback.
- Los perfiles usan la cache de conversaciones para localizar hilos abiertos con un usuario y consultar los adjuntos compartidos via Better Messages.
- Apertura cache-first de chats privados desde perfiles y listas de usuarios.
- Apertura cache-first de chats comunitarios desde **Q&uuml;ata** cuando la conversacion ya existe en cache/lista.
- Skeleton loading en chats si no hay cache y aun no termino la primera carga real.
- Mensajes favoritos cacheados: primera entrada consulta `getFavorited`, despues usa almacenamiento local y se actualiza al marcar/desmarcar favoritos.
- Al abrir un mensaje favorito, se navega al mensaje exacto sin auto-scroll al final.
- Los fondos procedurales de chat usan paletas dependientes de la plantilla de color y guardan cache separada por modo para regenerarse al cambiar entre claro y oscuro.
- Efectos de sonido del chat con ciclo de vida seguro para evitar `MediaPlayer finalized without being released`.

## Traductor Fang

La app incluye un modo traductor pensado para conversaciones y comentarios. No vive en el encabezado global: se abre desde el encabezado de una conversacion y desde el panel flotante de comentarios de una publicacion.

- El icono esta construido en Compose: globo terraqueo trasero y etiqueta frontal `fang`, adaptado a modo claro y oscuro sin depender de un PNG estatico.
- Al abrir el modo traductor se lanza `POST /warmup` en segundo plano contra el Space remoto para despertar el runtime antes de la primera traduccion real.
- La pantalla actual se captura en memoria a baja resolucion y se usa como fondo del overlay, con una textura esmerilada semitransparente para mantener contexto sin distraer.
- En comentarios, el overlay se monta por encima del `ModalBottomSheet`, conserva el viewport real del panel y respeta barras de estado/navegacion, incluida la navegacion Android de 3 botones.
- En chat, el overlay se alinea con la vista de conversacion y clona las burbujas con el mismo estilo visual que los mensajes originales.
- En comentarios, las tarjetas traducibles clonadas mantienen el aspecto de las tarjetas reales, incluidos autor, fecha, respuesta, borde y contenido.
- El overlay entra y sale con fade. El pie usa un icono de toque naranja sin fondo y el texto "Toca cualquier mensaje para traducirlo".
- Los textos traducibles se marcan desde Compose con metadatos propios para que el modo traductor pueda replicar sus cajas y asociarlas al texto original.
- Al tocar una caja traducible se detecta el idioma con el modelo FastText local incluido en assets (`es`, `fr`, `en`, `fan`).
- Si el texto no esta en Fang, se traduce hacia `fan_Latn`; si ya esta en Fang, se traduce hacia el idioma de interfaz (`spa_Latn`, `eng_Latn` o `fra_Latn`).
- Al terminar la traduccion, el texto de la caja cambia al resultado traducido y se muestra una insignia de direccion (`ES->FAN`, `FAN->ES`, etc.).
- Tocar de nuevo una caja traducida funciona como toggle y restaura el texto original sin hacer otra llamada de red.
- Mientras una traduccion esta en curso se muestra spinner y un leve cambio visual de estado, sin la animacion circular de borde que generaba ruido visual.
- En chat, la insignia de traduccion se ubica en la fila superior junto a la hora para no robar espacio al cuerpo del mensaje ni provocar solapes.
- El modo traductor consume los gestos de arrastre mientras esta activo para traducir exactamente lo que habia en pantalla al abrirlo, sin desplazar accidentalmente la conversacion o los comentarios.

La API remota esta alojada en Hugging Face Spaces:

```text
https://dossijeo-nllb-fang-quata.hf.space
```

Endpoints usados por la app:

- `GET /` para health check.
- `POST /warmup` para cargar tokenizer/runtime antes de traducir.
- `POST /translate` para traducir texto.

Codigos soportados por el servicio:

```text
fan_Latn  Fang
spa_Latn  Espanol
eng_Latn  Ingles
fra_Latn  Frances
```

El modelo remoto `NLLB-Fang-Q&uuml;ata` es un prototipo experimental afinado desde `facebook/nllb-200-distilled-600M`, exportado a ONNX, cuantizado y servido mediante FastAPI. El README publico del Space describe que el modelo se entreno con corpus paralelo del Nuevo Testamento, diccionario Fang-Espanol curado, pivotes multilingues sinteticos y remapeo de tokenizer para mantener calidad con un vocabulario compacto. La ruta `fan_Latn -> eng_Latn` usa pivote interno por frances (`fan_Latn -> fra_Latn -> eng_Latn`) porque es la direccion inversa mas fuerte. Ver documentacion del Space: https://huggingface.co/spaces/dossijeo/NLLB-Fang-Quata/blob/main/README.md

Las traducciones usan cache SQLite local independiente de Supabase. La clave incluye texto normalizado, idioma origen, idioma destino y `max_new_tokens`; si existe respuesta local, se devuelve al instante y no se refresca desde red porque la salida del modelo se considera estable para esa clave.

## Tema claro/oscuro

La app tiene un motor de plantillas de tema en `core/designsystem/theme`:

- `QuataTemplates.darkMode` conserva la apariencia oscura original.
- `QuataTemplates.lightMode` mantiene el naranja como acento y cambia fondos/superficies a tonos claros.
- `quataTheme()` expone a las vistas los tokens activos: fondos, superficies, textos, divisores, bordes de input, chips, estados, colores de chat, paletas procedurales y tamanos de texto.
- `QuataTextSizes` centraliza los tamanos de texto para poder ajustarlos globalmente desde la plantilla.
- Los colores legacy de `Color.kt` apuntan a la plantilla oscura para compatibilidad con componentes pendientes de migracion.

La preferencia se guarda localmente con `ThemePreferences` y se aplica desde `MainActivity` al envolver la app con `QuataTheme(mode = ...)`. En la seccion **Cuenta** hay un selector con tres opciones:

- Sistema.
- Modo Oscuro.
- Modo Claro.

Componentes y pantallas tematizados:

- Chrome global, barra superior, barra inferior y logo generado sin fondo.
- Pantallas de login, registro y recuperacion mediante logo generado.
- Icono de aplicacion, splash nativo, splash personalizado, logo de auth y logo superior actualizados para la marca compacta `Q&#776;` sin romper claro/oscuro.
- Q&uuml;ata, chats, feed, publicar, cuenta, SOS, comentarios y ranking LIVE.
- Editor de video y editor de imagen en sus barras, paneles, controles y estados de carga.
- Selector de emojis compartido.
- Campos de texto, dropdowns y selector de prefijo telefonico con borde de input comun.

El fondo compartido de las pantallas usa el mismo token que la barra superior para mantener continuidad visual. Los reproductores de video del feed y de la previsualizacion de publicacion usan superficies de plantilla durante la carga para evitar flashes negros en modo claro.

La seleccion de tema se observa una sola vez en el nivel superior de la app y se propaga como estado, evitando lecturas repetidas del helper de plantillas en recomposiciones calientes.

## Offline-first y cache local

- Las lecturas GET de Supabase pasan por `SupabaseHttpClient` y `SupabaseResponseCacheStore`, una base SQLite privada usada como cache NOSQL.
- La clave de cache es la consulta exacta (`GET + URL`), y el valor almacenado es el JSON crudo devuelto por Supabase.
- En modo cache-first, si existe respuesta local, la app la emite inmediatamente y refresca la misma consulta desde red en segundo plano.
- Las mutaciones (`POST`, `PATCH`, `DELETE`, RPC y uploads) siguen siendo network-only y no se cachean.
- Tras una mutacion se invalidan las tablas afectadas para que las vistas observables se actualicen con datos frescos.
- Imagenes y avatares usan Coil con cache de memoria/disco y claves estables por URL; si estaban en cache pueden mostrarse sin red y reintentan al recuperar conectividad.
- Los videos del feed usan Media3 `SimpleCache` con LRU de 256 MB y poda de ficheros antiguos; al borrar una publicacion propia se purga tambien su entrada local de video.
- Las traducciones se cachean en SQLite por texto/idiomas/tokens y se sirven cache-first sin refresco de fondo.

## Publicacion y media

- Publicacion de texto, imagen y video.
- Cache local de recursos multimedia: imagenes en memoria/disco mediante Coil y videos mediante Media3 `CacheDataSource`.
- Limites de cache: 128 MB para imagenes, 256 MB para videos y limpieza de ficheros con mas de 14 dias.
- Los videos seleccionados para publicar pasan por un editor nativo en Compose antes de incorporarse a la publicacion.
- El editor permite recortar duracion desde la timeline, mover la posicion de reproduccion, silenciar, aplicar recorte de encuadre con zoom y guardar el resultado.
- La seleccion temporal de video tiene un limite maximo de 15 minutos: las asas de la timeline no pueden abrir un intervalo mayor y, si el usuario sigue arrastrando un extremo, el asa contraria se desplaza para mantener el lapso maximo sin bloquear el gesto.
- La previsualizacion del editor y la exportacion fuerzan el formato final `9:16`: el crop elegido (`Original`, `1:1`, `4:5`, `9:16` o `16:9`) se centra sobre un fondo desenfocado para que preview, reproduccion, guardado y preview de publicacion coincidan.
- La exportacion se realiza con Media3 Transformer sobre el video original, no grabando la UI de preview, para conservar resolucion, sincronizacion y audio de forma estable.
- Cuando no hay ediciones y la fuente ya es `9:16`, tiene resolucion compatible y bitrate dentro del objetivo, el guardado puede usar copia directa sin recodificar.
- Cuando solo hay trim temporal, o mute sin efectos visuales, el editor usa rutas rapidas con `MediaExtractor`/`MediaMuxer` para evitar recodificar; si el video esta silenciado, se remuxea sin pista de audio.
- La resolucion maxima de subida se calcula por dispositivo: `720x1280@30` si el codec H.264 soporta el punto de rendimiento requerido y `480x854@30` como fallback; si el video supera ese limite o viene con bitrate inflado, se fuerza recodificacion.
- Bitrates actuales de exportacion: `720p` a 1.2 Mbps final y 1.8 Mbps intermedio; `480p` a 800 kbps final y 1.2 Mbps intermedio. La subida directa sin editor optimiza a `480p` y 800 kbps cuando detecta bitrate excesivo.
- Durante exportacion, la pantalla se mantiene encendida, se ocultan controles no interactivos, el progreso mueve el contador y la linea del timeline, y el boton atras muestra confirmacion para cancelar.
- Los archivos temporales exportados por el editor se eliminan al publicar, al reemplazar el video editado, al cancelar la exportacion o al salir de la pantalla de publicacion sin publicar.
- Los captions automaticos usan Vosk con modelo ingles incluido como fallback base y modelos espanol/frances en recursos `raw-es`/`raw-fr`, preparados para splits de idioma en AAB.
- Las plantillas de captions disponibles son `Karaoke`, `PopWord`, `Hormozi` y `Typewriter`, con preview PNG transparente sobre la previsualizacion y burn-in durante la exportacion.
- Si se aplican captions, la exportacion fuerza salida de calidad objetivo para preservar nitidez de texto.
- El editor de imagen genera una copia vertical `9:16` con limite de resolucion, crop/zoom interactivo y conservacion de datos de localizacion de la imagen original cuando estan disponibles.
- En publicar, los previews de imagen y video se expanden a `9:16` con avatar, acciones y metadatos cosmeticos del feed para validar el aspecto final antes de subir.
- El boton publicar actua como barra de progreso durante la subida y, si el usuario intenta salir, pregunta si desea cancelar la operacion.
- En publicaciones de imagen, el lugar/ubicacion detectado por EXIF o reverse geocoding es editable como texto libre. Si no hay GPS, no se detecta nada o el lugar es incorrecto, el usuario puede escribirlo antes de publicar.
- Si se intenta publicar una imagen sin lugar/ubicacion, la pantalla hace scroll automatico al bloque de ubicacion, abre el campo de texto y resalta el boton; al abrirse el editor, el boton cambia de **Editar** a **Guardar**.
- El feed guarda posicion por URL, conserva el player actual y vecinos para evitar flashes al hacer swipe, y solo recrea el player si se recupera red tras un estado roto.
- El feed usa la cache local de video para no volver a descargar recursos ya reproducidos y purga de esa cache el video de una publicacion propia cuando se elimina.
- El reproductor del feed usa `texture_view`, buffers contenidos y deshabilita la pista de audio cuando el video esta silenciado.
- La carga inicial del feed incluye comentarios y likes para rellenar contadores, estado de like y comentarios sin consultas adicionales por publicacion.
- Al eliminar una publicacion con media remota, la app intenta borrar tambien el fichero fisico en WordPress mediante el endpoint AJAX correspondiente; un fallo en ese borrado no bloquea el borrado del post en Supabase.
- La creacion de posts en Supabase envia `author_id` y `content` explicitamente para evitar filas inconsistentes cuando el serializador omite valores por defecto.
- Optimizacion global de imagenes antes de subir: redimensionado, conversion JPEG y compresion.
- Optimizacion de video cuando el archivo o la conexion lo aconsejan.
- La misma capa de optimizacion se reutiliza en publicar, foto de perfil y adjuntos de chat.
- Soporte para adjuntos de imagen/video en chat.

## Permisos e integracion Android

La app gestiona una secuencia de permisos y ajustes necesarios al terminar el splash:

- Permiso de notificaciones.
- Exclusion de optimizacion de bateria cuando hace falta recibir avisos en segundo plano.
- Ajuste de enlaces compatibles para abrir `egquata.com` directamente en Q&uuml;ata.
- Deep links de posts y chats mediante fragmentos como `https://egquata.com/#post-...`.
- Reutilizacion de instancia abierta para evitar multiples actividades al abrir enlaces.
- Barra inferior ajustada para iconos mas grandes y mejor encaje con la barra de navegacion Android en modo de 3 botones.

## Arquitectura

- Kotlin.
- Jetpack Compose.
- Material 3.
- MVVM con `ViewModel`, `UiState` y `UiEvent`.
- Repositorios por dominio.
- Navegacion con Navigation Compose.
- Capa de datos real para Supabase, WordPress y Better Messages.
- Modo mock disponible mediante propiedad Gradle.
- Cache local bajo almacenamiento privado: SQLite para respuestas Supabase, JSON para Better Messages/perfiles, Coil para imagenes y Media3 para videos.
- Cache SQLite especifica para traducciones Fang.
- Detector local de idioma mediante modelo FastText incluido en assets.
- Cliente HTTP para el Space `NLLB-Fang-Q&uuml;ata` con warmup y traduccion.
- JobService para polling suave en segundo plano.
- Coil para imagenes.
- Media3 para reproduccion, previsualizacion y procesado multimedia, incluido Transformer para exportar videos editados.
- kotlinx.serialization para modelos y cache.

## Estructura relevante

```text
app/src/main/java/com/quata/
  bettermessages/        Cliente REST/bridge de Better Messages
  core/
    config/              Configuracion global
    designsystem/theme/  Plantillas dark-mode/light-mode, tokens de color y tamanos de texto
    media/               Optimizacion de imagen/video y QuataMediaCache
    captions/            Transcripcion, layout, plantillas y burn-in de subtitulos
    language/            Detector local de idioma FastText para es/fr/en/fan
    translation/          Cliente API, cache SQLite y overlay del modo traductor Fang
    navigation/          NavGraph, deep links y chrome global
    localization/        Seleccion de idioma por locale del sistema
    notifications/       Notificaciones nativas y background polling
    preferences/         Preferencias locales, incluido el modo de color
    session/             Sesion y estado de autenticacion
    ui/                  Componentes compartidos, TouchFlow, splash
  data/supabase/         API y modelos Supabase
  feature/
    auth/                Login, registro y recuperacion
    chat/                Conversaciones, mensajes, favoritos, SOS
    feed/                Feed, comentarios y acciones sociales
    neighborhoods/       Q&uuml;ata, comunidades y perfiles publicos
    notifications/       Avisos dentro de la app
    postcomposer/        Publicacion de contenido
      videoeditor/       Editor nativo de video en Compose + Media3 Transformer/MediaMuxer
      imageeditor/       Editor nativo de imagen con crop/zoom vertical 9:16
    profile/             Cuenta, avatar, preferencias y SOS
```

## Configuracion

Configuracion principal:

```text
app/src/main/java/com/quata/core/config/AppConfig.kt
```

Puntos importantes:

- `QUATA_WORDPRESS_BASE_URL`
- `BETTER_MESSAGES_BASE_URL`
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- nombres de tablas Supabase
- `USE_MOCK_BACKEND`

El backend mock se puede activar al compilar:

```powershell
.\gradlew.bat assembleDebug -Pquata.useMockBackend=true
```

Por defecto, la build actual apunta al backend real.

## Build

Requisitos:

- Android Studio reciente.
- JDK 17, incluido el JBR de Android Studio.
- Android SDK con `compileSdk 35`.

Comando habitual:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

Instalacion en emulador:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Versionado

Version actual:

```text
versionCode = 16
versionName = 0.9.7
APP_VERSION_DATE = 2026-06-24
```

La app muestra esta informacion en la modal **Acerca de Q&uuml;ata**, accesible pulsando el logo de la esquina superior izquierda.

## Pendiente antes de 1.0

- QA de larga duracion en dispositivos reales.
- Revision de consumo de bateria y red con cuentas grandes.
- Validacion final de permisos y enlaces admitidos en Android moderno.
- Monitorizacion de errores en produccion.
- Politicas finales de privacidad, datos y soporte.
- Ajustes de accesibilidad y pruebas con tamanos de fuente del sistema.
