# Q&uuml;ata Android

Version: **0.9.2**
Fecha de version: **2026-06-07**
Estado: **beta avanzada**

Q&uuml;ata es una aplicacion Android social y comunitaria construida con Kotlin y Jetpack Compose. Reune feed visual, barrios/comunidades, perfiles, chat en tiempo real sobre Better Messages, notificaciones, SOS, publicacion de contenido y navegacion anonima con acciones protegidas por login.

La version `0.9.2` deja muy avanzado el flujo de publicacion multimedia: editor nativo de video e imagen con salida vertical `9:16`, fondo desenfocado dinamico, subtitulos automaticos incrustados, previews finales antes de publicar, exportacion adaptativa segun capacidad del dispositivo, rutas rapidas sin recodificacion y limpieza de temporales. El nucleo funcional ya esta muy completo y probado en emulador y dispositivo fisico, pero todavia queda margen de endurecimiento de release, QA amplio, analitica, monitorizacion y cierre de detalles previos a una `1.0`.

## Mejoras recientes de rendimiento y estabilidad

- Apertura de chats cache-first desde perfiles y desde **Barrios**: si la conversacion ya esta en la lista/cache local se navega directamente, sin buscar ni crear remoto.
- Polling de Better Messages simplificado: `checkNew` es la unica vigilancia periodica; `/thread/{id}` solo se consulta cuando `checkNew` indica cambios y esos cambios son delta real frente a la cache local.
- Sincronizacion completa solo al arranque, al reconstruir una cache expirada en primer plano o al pasar de sin conexion a con conexion.
- Estado global de conectividad derivado del polling: si falla, aparece una banda **sin conexion** bajo la barra superior de la app; al recuperarse, se fuerza una ronda de sincronizacion.
- Cache local de recursos: imagenes con Coil en memoria/disco y videos con Media3 `SimpleCache` LRU de 256 MB, con poda de ficheros antiguos.
- La cache de imagenes aplica a imagenes del feed, adjuntos, avatares publicos y foto propia en Cuenta.
- Feed mas ligero: carga comentarios y likes en la primera consulta para evitar peticiones de detalle por post, y solo instancia el reproductor del video activo.
- Reduccion de llamadas al abrir perfiles: cache local de perfiles con refresco en segundo plano y reutilizacion de informacion ya cargada para no bloquear la UI.
- Boton **Seguir/Siguiendo** con indicador de carga y actualizacion local inmediata de seguidores, siguiendo, contadores y listas abiertas.
- Menos trabajo por frame en la UI global: tema claro/oscuro se mantiene en estado superior y se evita crear animaciones infinitas cuando no estan activas.
- Sonidos del chat con liberacion explicita de `MediaPlayer` en completado, error o fallo de arranque.

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
- Modo de color configurable: sistema, modo oscuro y modo claro, con plantillas globales para colores y tamanos de texto.
- Modal de autenticacion para acciones que guardan datos: chats, likes, comentarios, publicar, SOS, seguir, reportar y cuenta.
- Barrios/comunidades con usuarios, perfiles, publicaciones y chats de comunidad.
- Panel de perfil con cache local, refresco en segundo plano, animaciones de contador en KPIs y archivos compartidos en chats abiertos con el usuario.
- Seguimiento de usuarios con estado de carga, actualizacion optimista/local de contadores y listas de seguidores/siguiendo.
- Halo de carga corporativo en avatares clicables.
- Barra inferior de navegacion con iconos ampliados para mejorar la pulsacion y convivencia con la navegacion Android de 3 botones.
- Cuenta con preferencias locales por usuario, incluido `Q&uuml;ata TouchFlow`.
- SOS con contactos configurables y rate limit.

## Chat y Better Messages

El chat usa Better Messages en WordPress como backend principal, con una capa Android propia para cache, polling y estado de UI.

- Polling centralizado para conversaciones y mensajes basado en `checkNew`.
- Modos de polling: agresivo en chats, medio en la app, relajado en segundo plano y minimo con la app cerrada; ahora regulan la frecuencia de `checkNew`.
- Sin barrido periodico de hilos: la app no repasa conversaciones sin cambios por temporizador.
- `checkNew` descubre hilos con actividad y la app filtra contra la cache local para distinguir cambios reales de ruido repetido del servidor.
- Cuando hay delta real en un hilo, se consulta `/thread/{id}` una vez para reconciliar mensajes, ultimo mensaje, unread y metadatos.
- Ronda completa de sincronizacion solo al arranque, al recuperar conexion o al reconstruir cache expirada en primer plano.
- Estado de conectividad de polling expuesto a la UI; la app muestra una banda global sin conexion bajo la barra superior si las comprobaciones fallan.
- Notificaciones nativas Android solo cuando la app esta en segundo plano o cerrada.
- Cache local de lista de conversaciones, hilos, mensajes favoritos y perfiles.
- Retencion de cache: 24 horas, con reconstruccion solo en primer plano cuando expira.
- Los perfiles usan la cache de conversaciones para localizar hilos abiertos con un usuario y consultar los adjuntos compartidos via Better Messages.
- Apertura cache-first de chats privados desde perfiles y listas de usuarios.
- Apertura cache-first de chats comunitarios desde **Barrios** cuando la conversacion ya existe en cache/lista.
- Skeleton loading en chats si no hay cache y aun no termino la primera carga real.
- Mensajes favoritos cacheados: primera entrada consulta `getFavorited`, despues usa almacenamiento local y se actualiza al marcar/desmarcar favoritos.
- Al abrir un mensaje favorito, se navega al mensaje exacto sin auto-scroll al final.
- Los fondos procedurales de chat usan paletas dependientes de la plantilla de color y guardan cache separada por modo para regenerarse al cambiar entre claro y oscuro.
- Efectos de sonido del chat con ciclo de vida seguro para evitar `MediaPlayer finalized without being released`.

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
- Barrios, chats, feed, publicar, cuenta, SOS, comentarios y ranking LIVE.
- Editor de video y editor de imagen en sus barras, paneles, controles y estados de carga.
- Selector de emojis compartido.
- Campos de texto, dropdowns y selector de prefijo telefonico con borde de input comun.

El fondo compartido de las pantallas usa el mismo token que la barra superior para mantener continuidad visual. Los reproductores de video del feed y de la previsualizacion de publicacion usan superficies de plantilla durante la carga para evitar flashes negros en modo claro.

La seleccion de tema se observa una sola vez en el nivel superior de la app y se propaga como estado, evitando lecturas repetidas del helper de plantillas en recomposiciones calientes.

## Publicacion y media

- Publicacion de texto, imagen y video.
- Cache local de recursos multimedia: imagenes en memoria/disco mediante Coil y videos mediante Media3 `CacheDataSource`.
- Limites de cache: 128 MB para imagenes, 256 MB para videos y limpieza de ficheros con mas de 14 dias.
- Los videos seleccionados para publicar pasan por un editor nativo en Compose antes de incorporarse a la publicacion.
- El editor permite recortar duracion desde la timeline, mover la posicion de reproduccion, silenciar, aplicar recorte de encuadre con zoom y guardar el resultado.
- La seleccion temporal de video tiene un limite maximo de 15 minutos: las asas de la timeline no pueden abrir un intervalo mayor y, si el usuario sigue arrastrando un extremo, el asa contraria se desplaza para mantener el lapso maximo sin bloquear el gesto.
- La previsualizacion del editor y la exportacion mantienen siempre salida vertical `9:16`, con el video/crop centrado y fondo desenfocado basado en el area visible cuando la fuente no encaja en ese formato.
- La exportacion se realiza con Media3 Transformer sobre el video original, no grabando la UI de preview, para conservar resolucion, sincronizacion y audio de forma estable.
- Cuando no hay ediciones y la fuente ya es `9:16` con resolucion compatible, el guardado es instantaneo y usa el video original sin exportar.
- Cuando solo hay trim temporal, o mute sin efectos visuales, el editor usa rutas rapidas con `MediaExtractor`/`MediaMuxer` para evitar recodificar; si el video esta silenciado, se remuxea sin pista de audio.
- La resolucion maxima de subida se calcula por dispositivo: `1080x1920@30` si el codec H.264 soporta el punto de rendimiento requerido y `720x1280@30` como fallback; si el video supera ese limite, se fuerza recodificacion.
- Durante exportacion, la pantalla se mantiene encendida, se ocultan controles no interactivos, el progreso mueve el contador y la linea del timeline, y el boton atras muestra confirmacion para cancelar.
- Los archivos temporales exportados por el editor se eliminan al publicar, al reemplazar el video editado, al cancelar la exportacion o al salir de la pantalla de publicacion sin publicar.
- Los captions automaticos usan Vosk con modelo ingles incluido como fallback base y modelos espanol/frances en recursos `raw-es`/`raw-fr`, preparados para splits de idioma en AAB.
- Las plantillas de captions disponibles son `Karaoke`, `PopWord`, `Hormozi` y `Typewriter`, con preview PNG transparente sobre la previsualizacion y burn-in durante la exportacion.
- Si se aplican captions, la exportacion fuerza salida de calidad objetivo para preservar nitidez de texto.
- El editor de imagen genera una copia vertical `9:16` con limite de resolucion, crop/zoom interactivo y conservacion de datos de localizacion de la imagen original cuando estan disponibles.
- En publicar, los previews de imagen y video se expanden a `9:16` con avatar, acciones y metadatos cosmeticos del feed para validar el aspecto final antes de subir.
- El boton publicar actua como barra de progreso durante la subida y, si el usuario intenta salir, pregunta si desea cancelar la operacion.
- El feed actualiza explicitamente el reproductor de video cuando se publica o elimina una publicacion para evitar estados negros al reutilizar paginas del reel.
- El feed usa la cache local de video para no volver a descargar recursos ya reproducidos.
- El feed solo crea `ExoPlayer` para la pagina activa, usa buffers mas contenidos y deshabilita la pista de audio cuando el video esta silenciado.
- La carga inicial del feed incluye comentarios y likes para rellenar contadores, estado de like y comentarios sin consultas adicionales por publicacion.
- Al eliminar una publicacion con media remota, la app intenta borrar tambien el fichero fisico en WordPress mediante el endpoint AJAX correspondiente; un fallo en ese borrado no bloquea el borrado del post en Supabase.
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
- Cache local JSON bajo almacenamiento privado de la app.
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
    neighborhoods/       Barrios, comunidades y perfiles publicos
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
versionCode = 11
versionName = 0.9.2
APP_VERSION_DATE = 2026-06-07
```

La app muestra esta informacion en la modal **Acerca de Q&uuml;ata**, accesible pulsando el logo de la esquina superior izquierda.

## Pendiente antes de 1.0

- QA de larga duracion en dispositivos reales.
- Revision de consumo de bateria y red con cuentas grandes.
- Validacion final de permisos y enlaces admitidos en Android moderno.
- Monitorizacion de errores en produccion.
- Politicas finales de privacidad, datos y soporte.
- Ajustes de accesibilidad y pruebas con tamanos de fuente del sistema.
