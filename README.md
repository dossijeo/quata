# Q&uuml;ata Android

Version: **0.9.0**
Fecha de version: **2026-06-01**
Estado: **beta avanzada**

Q&uuml;ata es una aplicacion Android social y comunitaria construida con Kotlin y Jetpack Compose. Reune feed visual, barrios/comunidades, perfiles, chat en tiempo real sobre Better Messages, notificaciones, SOS, publicacion de contenido y navegacion anonima con acciones protegidas por login.

La version `0.9.0` incorpora perfiles con archivos compartidos reales desde Better Messages, localizacion francesa por idioma del sistema, ajustes visuales en la navegacion inferior y un editor nativo de video para el flujo de publicacion. El nucleo funcional ya esta muy completo y probado en emulador, pero todavia queda margen de endurecimiento de release, QA amplio en dispositivos reales, analitica, monitorizacion y cierre de detalles previos a una `1.0`.

## Funcionalidad principal

- Feed visual tipo reel con publicaciones de texto, imagen y video.
- Editor nativo de video integrado en publicar, con previsualizacion, recorte temporal, recorte de encuadre, mute y exportacion.
- Ranking de publicaciones por likes y fecha, con badge `#rank`.
- Publicaciones de texto con fondo degradado determinista a partir del contenido.
- Shortcodes embebidos en el texto del post para canal, ubicacion, titulo de media, Alka y estado de video.
- Badge de ubicacion o titulo sobre publicaciones de imagen/video.
- Comentarios, likes, reportes y compartido.
- Selector global de emojis con secciones tabuladas y carga perezosa.
- Navegacion anonima: se puede explorar contenido sin cuenta.
- Localizacion por idioma del sistema en espanol, ingles y frances.
- Modal de autenticacion para acciones que guardan datos: chats, likes, comentarios, publicar, SOS, seguir, reportar y cuenta.
- Barrios/comunidades con usuarios, perfiles, publicaciones y chats de comunidad.
- Panel de perfil con cache local, refresco en segundo plano, animaciones de contador en KPIs y archivos compartidos en chats abiertos con el usuario.
- Halo de carga corporativo en avatares clicables.
- Barra inferior de navegacion con iconos ampliados para mejorar la pulsacion y convivencia con la navegacion Android de 3 botones.
- Cuenta con preferencias locales por usuario, incluido `Q&uuml;ata TouchFlow`.
- SOS con contactos configurables y rate limit.

## Chat y Better Messages

El chat usa Better Messages en WordPress como backend principal, con una capa Android propia para cache, polling y estado de UI.

- Polling centralizado para conversaciones y mensajes.
- Modos de polling: agresivo en chats, medio en la app, relajado en segundo plano y minimo con la app cerrada.
- Consultas encadenadas por conversacion para evitar bloques masivos de POST.
- Priorizacion inteligente: conversaciones recientes, activas y abiertas se refrescan antes.
- Descubrimiento de nuevas conversaciones desde `checkNew`.
- Notificaciones nativas Android solo cuando la app esta en segundo plano o cerrada.
- Cache local de lista de conversaciones, hilos, mensajes favoritos y perfiles.
- Retencion de cache: 24 horas, con reconstruccion solo en primer plano cuando expira.
- Los perfiles usan la cache de conversaciones para localizar hilos abiertos con un usuario y consultar los adjuntos compartidos via Better Messages.
- Skeleton loading en chats si no hay cache y aun no termino la primera carga real.
- Mensajes favoritos cacheados: primera entrada consulta `getFavorited`, despues usa almacenamiento local y se actualiza al marcar/desmarcar favoritos.
- Al abrir un mensaje favorito, se navega al mensaje exacto sin auto-scroll al final.

## Publicacion y media

- Publicacion de texto, imagen y video.
- Los videos seleccionados para publicar pasan por un editor nativo en Compose antes de incorporarse a la publicacion.
- El editor permite recortar duracion desde la timeline, mover la posicion de reproduccion, silenciar, aplicar recorte de encuadre con zoom y guardar el resultado.
- La previsualizacion del editor y la exportacion mantienen siempre salida vertical `9:16`, con el video/crop centrado y fondo desenfocado basado en el area visible cuando la fuente no encaja en ese formato.
- La exportacion se realiza con Media3 Transformer sobre el video original, no grabando la UI de preview, para conservar resolucion, sincronizacion y audio de forma estable.
- El feed actualiza explicitamente el reproductor de video cuando se publica o elimina una publicacion para evitar estados negros al reutilizar paginas del reel.
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
    media/               Optimizacion de imagen/video
    navigation/          NavGraph, deep links y chrome global
    localization/        Seleccion de idioma por locale del sistema
    notifications/       Notificaciones nativas y background polling
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
      videoeditor/       Editor nativo de video en Compose + Media3 Transformer
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
versionCode = 9
versionName = 0.9.0
APP_VERSION_DATE = 2026-06-01
```

La app muestra esta informacion en la modal **Acerca de Q&uuml;ata**, accesible pulsando el logo de la esquina superior izquierda.

## Pendiente antes de 1.0

- QA de larga duracion en dispositivos reales.
- Revision de consumo de bateria y red con cuentas grandes.
- Validacion final de permisos y enlaces admitidos en Android moderno.
- Monitorizacion de errores en produccion.
- Politicas finales de privacidad, datos y soporte.
- Ajustes de accesibilidad y pruebas con tamanos de fuente del sistema.
