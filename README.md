# Q&uuml;ata Android

Version: **1.0.0**
Fecha de version: **2026-07-09**
Estado: **release 1.0.0 preparada para produccion inicial**

Q&uuml;ata es una aplicacion Android social y comunitaria construida con Kotlin y Jetpack Compose. Reune feed visual, muro oficial, barrios/comunidades, perfiles, chat en tiempo real sobre Supabase Realtime, notificaciones Firebase, SOS, publicacion de contenido y navegacion anonima con acciones protegidas por login.

La version `1.0.0` consolida el primer lanzamiento completo de Q&uuml;ata: chat Supabase con estados de entrega/lectura, muro oficial multidioma, feeds paginados offline-first, editor oficial rapido/avanzado con traduccion DeepL, layouts seguros en portrait/landscape, borrado moderado por administradores y una limpieza final de artefactos de QA.

## Mejoras recientes de rendimiento y estabilidad

- Doble check de chat estilo WhatsApp: `Pendiente`, `Enviado`, `Entregado` y `Leido`, con checks junto a la hora y color corporativo cuando el destinatario lee el mensaje.
- Nueva tabla Supabase `chat_message_states` para estados `DELIVERED` y `READ`, con eventos Realtime para que el emisor vea los cambios sin polling.
- La recepcion FCM marca mensajes como `DELIVERED`; si falla la red, se guarda una cola local JSON y WorkManager reintenta el envio al recuperar conectividad.
- La apertura de un chat marca como `READ` los mensajes visibles y guarda el estado en cache offline-first para pintar checks desde cache al reabrir la conversacion.
- Feeds normal y oficial unificados en paginacion de 50 publicaciones, pull-to-refresh, cache offline-first y carga automatica de paginas antiguas al llegar al final.
- Ambos feeds conservan la publicacion visible cuando solo cambia la orientacion del dispositivo.
- Rail de acciones compartido para feed normal y muro oficial: ranking, LIVE, likes, comentarios, compartir, borrar y publicar con tamanos y comportamiento consistentes.
- Reproductores de video unificados para feed, muro oficial, preview, editor y adjuntos, preservando rotacion y proporcion en API antiguas y modernas.
- Panel flotante comun para comentarios, LIVE, nuevo chat, reenviar, anadir participantes y **Leer mas**, con modal amplia centrada en landscape.
- Pantallas no-feed envueltas en layout seguro para evitar solapes con barra de estado o navegacion Android en landscape.
- Configuracion de contactos de emergencia, crear publicaciones y editor rich text oficial adaptados al layout normal de la app.
- Muro oficial con modo de publicacion **Rapido** y **Avanzado**. El modo rapido deriva titulo/resumen del texto enriquecido; el avanzado conserva campos completos.
- Publicaciones oficiales multidioma: se guarda idioma, grupo de traduccion y fallback a idioma por defecto para clientes antiguos.
- Traduccion automatica de publicaciones oficiales ES/EN/FR mediante DeepL, con deteccion local de idioma y modal de confirmacion antes de publicar.
- Etiquetas **Leer mas**, **Mas informacion**, **Seguir leyendo** y **Detalles** guardadas como shortcodes localizables para que cada usuario las vea en su idioma.
- El muro oficial refresca tras publicar y enfoca la nueva publicacion; al borrar una publicacion oficial muestra el mismo aviso localizado que el feed normal.
- RLS de publicaciones oficiales ajustada para permitir soft delete por autor o administrador sin exponer publicaciones borradas al publico.
- Nuevo tab **Oficial** en la barra inferior, sustituyendo al acceso central de publicar y abriendo un muro independiente de publicaciones de cuentas oficiales.
- El feed normal conserva la creacion de publicaciones mediante boton flotante `+`, reubicado en el rail de acciones para mantener el flujo de publicacion.
- Cuentas oficiales con insignia azul de verificacion en avatares y perfiles, visible tambien en cards y listados.
- Administradores iniciales configurados en Supabase para Juan y Gabriel, con switches en perfiles para marcar usuarios como administradores o cuentas oficiales.
- Los administradores pueden eliminar cualquier publicacion del feed normal o del muro oficial; Supabase incluye politicas RLS especificas para esta excepcion.
- Muro oficial legible en modo anonimo, con acciones protegidas por login cuando requieren escritura.
- Publicaciones oficiales con tipos `Comunicado`, `Noticia`, `Evento` y `Urgente`, resumen corto, texto completo y etiqueta personalizable de **Leer mas**.
- Editor de publicaciones oficiales a pantalla completa, inspirado en el editor normal de publicaciones, con seleccion de imagen/video, recorte/edicion multimedia, preview y boton de publicar con progreso.
- Editor enriquecido integrado para el cuerpo largo de publicaciones oficiales: bloques, listas, checks, citas, estilos inline, enlaces, highlight y soporte claro/oscuro.
- Renderizador de texto enriquecido para leer publicaciones oficiales largas en panel de **Leer mas**, adaptado al tema y al scroll de pantalla.
- Muro oficial con tarjetas tipo reel en portrait y layout de dos columnas en landscape, manteniendo comentarios, likes, compartir, LIVE/ranking y borrado.
- Los comentarios y el panel LIVE usan componentes comunes compartidos por feed normal y muro oficial, incluido responder comentarios y traductor Fang.
- Imagenes y videos oficiales se abren con el visor multimedia compartido de adjuntos, evitando clones y respetando barras del sistema.
- Los videos oficiales se muestran como thumbnail con boton play dentro de la tarjeta y se reproducen a pantalla completa al abrirlos.
- Deep links para publicaciones oficiales mediante `#official-post-...`, con navegacion directa al tab Oficial y post enfocado.
- Publicaciones oficiales de prueba y seeds Supabase para demo interna con imagen/video y contenido institucional.
- Nuevo boton flotante **Nuevo chat** en la lista de conversaciones, con selector de usuarios categorizado por contactos, seguidos, seguidores, barrio propio y otros barrios.
- La camara integrada permite cambiar entre camara trasera y selfie en foto, video y modo dual.
- La foto de perfil usa la camara integrada y abre el editor `1:1` a pantalla completa.
- Las notas de voz cambian al auricular con el sensor de proximidad, duplican mono a estereo en altavoz y bloquean la rotacion mientras se reproducen.
- La app apaga la pantalla cuando el movil esta pegado al oido y bloquea SOS si el sensor de proximidad indica distancia corta.
- Busqueda de nuevos chats paginada desde Supabase por nombre, barrio o telefono, sin exponer el numero de telefono en la interfaz.
- El panel comun de seleccion de usuarios se reutiliza para nuevo chat, anadir participantes y reenviar mensajes, con formato de panel inferior en portrait y modal amplia centrada en landscape.
- El reenvio de mensajes cierra el panel al instante y reutiliza conversaciones existentes cuando ya hay hilo abierto con el destinatario.
- Las conversaciones privadas abiertas pero vacias se limpian automaticamente al salir si no se envio ningun mensaje.
- Envio de mensajes con `client_message_id` para evitar duplicados cuando una peticion se reintenta o Realtime confirma tarde.
- Boton de deshacer borrado de conversacion reubicado a la izquierda para convivir con el nuevo boton de chat sin saltos visuales.
- Auth bridge de Supabase reforzado para usuarios legacy existentes, generando credenciales Supabase de forma transparente durante el login.
- Push Firebase ajustado para notas de voz, conversaciones silenciadas y entregas solo cuando la app no esta visible.
- Las notificaciones de chat admiten respuesta directa desde Android: la respuesta se envia por Supabase, reintenta cortes breves de red y cierra correctamente la notificacion retenida por `RemoteInput`.
- Se eliminan los avisos y permisos de exclusion de ahorro de bateria/datos en segundo plano: el segundo plano queda cubierto por FCM.
- Feed y preview de publicacion de video usan recorte en portrait y contain en landscape, igualando el comportamiento de las imagenes.
- El feed pausa videos al mandar la app a segundo plano y mantiene el estado global de sonido/mute entre publicaciones.
- Los adjuntos de audio compartidos desde perfiles usan el reproductor embebido de notas de voz en lugar de abrir un reproductor externo.
- Las notas de voz permiten arrastrar la posicion de reproduccion, dejan de reproducirse en bucle y reproducen consecutivamente solo notas inmediatas del mismo interlocutor.
- El visor de documentos genera preview para PDF, documentos ofimaticos y formatos de texto plano habituales como XML, HTML, CSS, CSV, Markdown y logs.
- El motor de documentos se modernizo para evitar APIs obsoletas y reforzar navegacion atras, layouts y cierre seguro de tareas internas.
- El editor de video limita publicaciones a 90 segundos y muestra aviso claro si el clip supera `1:30`.
- Los errores tecnicos de red, timeout o subida se mapean a mensajes genericos localizados para no exponer detalles internos al usuario.
- La foto de perfil usa el editor de imagen con recorte fijo `1:1`, y los avatares propios o de perfiles pueden abrirse en el visor integrado.
- Chat migrado a Supabase: esquema propio de conversaciones, participantes, mensajes, adjuntos, favoritos, administradores, SOS y comunidades, con RPCs transaccionales y sin dependencias Better Messages en Android.
- Supabase Realtime sustituye el polling del chat: la app se suscribe a cambios de conversaciones/mensajes y reconecta al recuperar red o renovar sesion.
- Sesion Supabase con refresh preventivo global al arrancar y volver a primer plano; al caducar el token se renueva con `refresh_token` antes de romper Realtime o llamadas protegidas.
- Cache offline-first nueva para chat con esquema de nombres renovado, invalidando automaticamente caches antiguas de Better Messages en dispositivos actualizados.
- Apertura cache-first de lista de conversaciones y de hilos: si hay datos locales, se muestran inmediatamente y luego se refrescan desde Supabase.
- Chats privados, grupales, SOS y chats de comunidad funcionan sobre Supabase. Los chats comunitarios se abren por comunidad y anaden al usuario al hilo existente en vez de crear grupos duplicados.
- Acciones completas de chat en Supabase: responder, editar, favorito, borrar mensaje, silenciar conversacion, permitir invitaciones, anadir participantes, abandonar, borrar/restaurar conversacion y quitar moderador.
- Adjuntos compartidos entre usuarios consultados directamente en Supabase, sin hacer scan completo de todas las conversaciones.
- Push nativo con Firebase Cloud Messaging y Edge Function `quata-push-dispatch`; respeta `muted_at`, deshabilita tokens `UNREGISTERED` y evita notificacion nativa cuando la app esta en primer plano.
- Grabadora de audio integrada en chat: AAC/M4A en API 26-28 y Opus/Ogg en API 29+, con reproductor tipo nota de voz basado en Media3/ExoPlayer.
- Camara integrada comun para foto, video o modo dual, usada en chat y publicar. Evita comportamientos inconsistentes de grabadoras externas en API antiguas.
- Captura y grabacion validadas en API 26, 27, 28 y 37, portrait y landscape, incluyendo orientacion, thumbnails, preview, reproduccion, exportacion y barra de navegacion de 3 botones.
- SOS usa `FusedLocationProviderClient.getLastLocation()` para enviar ayuda inmediata. Si la ubicacion falta o tiene mas de un minuto, solicita una ubicacion precisa en segundo plano y envia una actualizacion diferida al mismo hilo.
- Mensajes SOS persistidos como shortcodes localizables (`[SOS:kind=alert|update;...]`), de modo que cada receptor ve la tarjeta en el idioma de su dispositivo.
- Publicaciones de texto con selector manual de patron de degradado, texto adaptativo, elipsis y accion **Leer mas** con modal a pantalla completa.
- Preview de publicaciones de texto alineada visualmente con el feed real, con rail de acciones, rank/LIVE, ancho seguro y escala compacta.
- Feed actualizado: numero de post-rank dentro del circulo sombreado, `LIVE` con formato de accion, titulo/ubicacion flotante con degradado superior, mute global y pausa de videos al pasar a segundo plano.
- Publicacion de imagen usa `getLastLocation()` instantaneo si no hay EXIF util y no bloquea si la ubicacion llega vacia, porque el lugar es editable a mano.
- Modo offline-first para lecturas Supabase: las consultas GET usan una cache SQLite local por clave exacta de consulta, emiten el valor cacheado al instante y refrescan desde red en segundo plano.
- Visor interno de documentos para `doc`, `docx`, `pdf`, `ppt`, `pptx`, `rtf`, `txt`, `xls`, `xlsx` y `csv`, integrado visualmente en Q&uuml;ata con cabecera propia, boton de descarga, tema claro/oscuro y localizacion.
- PDF del visor interno basado en APIs de plataforma, sin librerias nativas Pdfium, para cumplir con dispositivos Android de 16 KB page size.
- PowerPoint en el visor interno con controles de diapositiva anterior/siguiente y contador de pagina; Excel permite scroll y zoom tactil por gesto.
- Barras del sistema sincronizadas con el tema: en modo oscuro el reloj y los iconos de estado usan color claro, y la cabecera superior ya no calcula desplazamientos extra por camara cuando la app vive dentro del area segura.
- Q&uuml;ata y Chats ajustan sus listas al area util para eliminar la franja inferior sobrante sobre la navegacion.
- Envio de mensajes propios reconciliado con la cache de conversaciones: tras confirmacion de Supabase, la lista de Chats actualiza preview, fecha y orden inmediatamente.
- Uso de `MediaMetadataRetriever` centralizado con cierre explicito seguro y miniaturas de video remotas sin extractor nativo para reducir crashes de finalizador en dispositivos Android 13 de gama baja.
- Apertura de chats cache-first desde perfiles y desde **Q&uuml;ata**: si la conversacion ya esta en la lista/cache local se navega directamente, sin buscar ni crear remoto.
- Notificaciones nativas corregidas para app minimizada o pantalla apagada: una conversacion activa en memoria solo silencia la notificacion si la app esta realmente visible; en segundo plano se notifica con Android y Firebase.
- Estado global de conectividad derivado de Android: si el dispositivo pierde red aparece una banda **Sin conexion** bajo la barra superior de toda la app; al recuperar red se reactivan llamadas Supabase y canales Realtime.
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
- Compatibilidad edge-to-edge revisada para Android 15+: insets seguros, corte de pantalla siempre permitido, barra lateral contenida en API 28/API 37 y sin superposicion del rail sobre el feed.
- Editor de video reforzado: crops `Original`, `1:1`, `4:5`, `9:16` y `16:9` mantienen el mismo encuadre en preview, reproduccion y exportacion dentro de una salida final `9:16` con bandas difuminadas, incluyendo fuentes con rotacion de camara y exportacion compatible con Android 9 y Android moderno.

## Funcionalidad principal

- Feed visual tipo reel con publicaciones de texto, imagen y video.
- Muro oficial independiente para publicaciones de cuentas verificadas, con lectura anonima, acciones sociales y tarjetas adaptadas a claro/oscuro.
- Editor nativo de video integrado en publicar, con previsualizacion, recorte temporal, recorte de encuadre, mute, subtitulos y exportacion.
- Editor nativo de imagen integrado en publicar, con crop/zoom vertical `9:16`, limite de salida y preservacion de metadatos utiles de localizacion.
- Preview de publicacion de imagen/video en formato `9:16`, con aspecto cosmetico equivalente al feed y reproduccion local para validar el resultado antes de publicar.
- Ranking de publicaciones por likes y fecha, con badge `#rank` legible dentro del circulo sombreado.
- Publicaciones de texto con fondo degradado seleccionable, tipografia adaptativa, elipsis y lectura completa mediante **Leer mas**.
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
- SOS con contactos configurables, rate limit, ubicacion aproximada inmediata y actualizacion precisa diferida.
- Chat privado, grupal, comunitario y SOS sobre Supabase Realtime.
- Adjuntos de chat: archivo, imagen/video de galeria, camara dual integrada y audio comprimido integrado.

## Muro oficial y cuentas oficiales

El muro oficial introduce una capa institucional separada del feed social. Esta pensada para cuentas verificadas de organismos, barrios, administraciones o entidades que necesitan publicar avisos mas estructurados.

- Tabla `official_posts` en Supabase, con likes, comentarios, soft delete, tipos de publicacion, media opcional, resumen y cuerpo enriquecido.
- Soporte multidioma en `official_posts`: `language`, `translation_group_id` y lectura con fallback para mostrar la version del idioma de interfaz o la version por defecto.
- Campos `is_admin` e `is_official` en `community_profiles`, con indices y guards SQL para que solo administradores puedan cambiar roles.
- Lectura publica del muro oficial para clientes anonimos; creacion, likes, comentarios y borrado requieren sesion Supabase.
- Publicacion oficial limitada a perfiles marcados como `is_official`.
- Administradores con permisos de moderacion para actualizar o borrar publicaciones oficiales aunque no sean autores.
- Politica adicional para que administradores puedan borrar publicaciones del feed normal creadas por otros usuarios.
- Editor oficial con campos de titulo, descripcion corta, texto del enlace **Leer mas**, descripcion larga enriquecida, tipo de publicacion y media.
- Editor oficial con modo **Rapido** y **Avanzado**: el modo rapido reduce campos, exige descripcion enriquecida y deriva titulo/resumen automaticamente; el avanzado mantiene control completo.
- El texto del enlace de lectura completa se guarda como shortcode localizable para evitar etiquetas fijas en un idioma.
- La publicacion oficial puede autogenerar versiones ES/EN/FR mediante DeepL despues de detectar el idioma localmente.
- La descripcion larga se edita en pantalla completa con el editor rich text comun y se renderiza despues con el lector enriquecido.
- Preview oficial dentro del editor con la misma estructura visual del muro final.
- Imagenes y videos oficiales se optimizan/suben mediante la misma capa de media usada por publicar y adjuntos.
- Cards oficiales con perfil verificado, tipo de aviso, ranking, LIVE, likes, comentarios, compartir, borrar y enlace de lectura completa.
- Las cards oficiales usan la misma paginacion, pull-to-refresh, cache offline-first y rail de acciones que el feed normal.
- Comentarios oficiales reutilizan el componente comun de comentarios del feed normal, con respuesta a comentarios y traductor Fang.
- Panel LIVE comun reutilizado para rankings de feed y muro oficial.
- Deep links oficiales compartibles con `https://egquata.com/#official-post-...`.

## Chat Supabase, Realtime y Firebase

El chat usa Supabase como backend principal. Better Messages fue retirado del cliente Android; la compatibilidad legacy anonima se mantiene en las zonas que leen feed/contenido publico, pero la mensajeria moderna usa identidades Supabase, RPCs, Realtime y cache local propia.

- Tablas Supabase para conversaciones, participantes, mensajes, adjuntos, favoritos, eventos de lectura, push tokens y logs de entrega.
- Tabla `chat_message_states` para registrar `DELIVERED` y `READ` por mensaje y usuario.
- RPCs para crear o abrir chats privados, grupales, SOS y comunitarios, enviar mensajes, adjuntar archivos, responder, editar, marcar favorito, borrar mensajes, silenciar, permitir invitaciones, anadir participantes, abandonar, borrar/restaurar conversaciones y gestionar administradores.
- Realtime para recibir cambios de mensajes y conversaciones sin polling en primer plano ni segundo plano.
- Eventos Realtime para estados de mensaje: el emisor ve un check al enviar, doble check al entregarse y doble check naranja al leerse.
- FCM marca `DELIVERED` en el receptor aunque el chat no este abierto; si falla la llamada, se encola en JSON local y WorkManager reintenta con red disponible.
- Al abrir un chat o renderizar mensajes nuevos, el receptor marca `READ` y el estado se reconcilia con la cache offline-first.
- Reconexiones Realtime coordinadas con estado de red, foreground y renovacion de token Supabase.
- Cache local offline-first de conversaciones, hilos, mensajes, favoritos, perfiles, adjuntos y fondo procedural; si existe cache, la pantalla abre sin skeleton persistente.
- Esquema de cache nuevo para invalidar automaticamente la cache antigua asociada a Better Messages.
- Mensajes favoritos: refresco al abrir la vista de estrella y navegacion al mensaje concreto.
- Chats comunitarios: se abren por comunidad; si el hilo ya existe, el usuario se une al chat existente en vez de crear otro grupo con todos los miembros.
- Busqueda de adjuntos compartidos con un usuario mediante consulta directa a Supabase.
- Barra inferior redisenada: emoji, texto, adjunto, camara y microfono.
- Panel horizontal de adjuntos con archivo y foto/video de galeria.
- Overlay grande para adjuntos pendientes, con preview de imagen, reproductor de video, primera pagina de documentos o icono generico.
- Camara dual integrada para foto/video en chat.
- Grabadora de audio integrada con permiso de microfono, formato comprimido y reproductor tipo nota de voz.
- Swipe a la derecha sobre un mensaje para iniciar respuesta.
- Tarjetas especiales para mensajes SOS con ubicacion, precision, velocidad, antiguedad y apertura en Google Maps.
- Los mensajes SOS se guardan como shortcodes y se localizan al renderizar, no como texto fijo en el idioma del emisor.
- Notificaciones internas en foreground y push nativo Firebase solo cuando la app esta cerrada o en segundo plano.
- Las push de chat usan shortcodes/localizacion en cliente para mensajes, adjuntos y notas de voz; la accion **Responder** tambien se localiza con el idioma de la app.
- `muted_at` se respeta antes de enviar push Firebase.
- Sonidos del chat con ciclo de vida seguro para evitar `MediaPlayer finalized without being released`.

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

## Traduccion oficial ES/EN/FR

Las publicaciones oficiales usan un helper separado basado en DeepL para traducir entre espanol, ingles y frances. El traductor Fang queda reservado para rutas hacia y desde Fang.

- La deteccion de idioma se realiza localmente con el modelo FastText incluido en la app.
- Antes de publicar una noticia oficial, la app pregunta si se quieren generar automaticamente las versiones que falten.
- La API key se lee desde `local.properties`, propiedad Gradle o variable de entorno `QUATA_DEEPL_API_KEY`.
- La respuesta traducida se mantiene en memoria durante la publicacion y se guarda como variantes enlazadas por `translation_group_id`.

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
- El chat mantiene cache offline-first separada para inbox, hilos, favoritos, perfiles y adjuntos; al abrir una conversacion se muestra cache local antes de refrescar desde Supabase.
- Los fondos procedurales de chat se pregeneran y cachean por conversacion/modo de color.
- Imagenes y avatares usan Coil con cache de memoria/disco y claves estables por URL; si estaban en cache pueden mostrarse sin red y reintentan al recuperar conectividad.
- Los videos del feed usan Media3 `SimpleCache` con LRU de 256 MB y poda de ficheros antiguos; al borrar una publicacion propia se purga tambien su entrada local de video.
- Las traducciones se cachean en SQLite por texto/idiomas/tokens y se sirven cache-first sin refresco de fondo.

## Publicacion y media

- Publicacion de texto, imagen y video.
- Publicacion de texto con selector manual de patron de degradado, texto adaptativo, elipsis y modal **Leer mas**.
- Cache local de recursos multimedia: imagenes en memoria/disco mediante Coil y videos mediante Media3 `CacheDataSource`.
- Limites de cache: 128 MB para imagenes, 256 MB para videos y limpieza de ficheros con mas de 14 dias.
- Los videos seleccionados para publicar pasan por un editor nativo en Compose antes de incorporarse a la publicacion.
- El editor permite recortar duracion desde la timeline, mover la posicion de reproduccion, silenciar, aplicar recorte de encuadre con zoom y guardar el resultado.
- La seleccion temporal de video para publicar tiene un limite maximo de `1:30`; si el archivo supera esa duracion, el editor muestra un aviso compacto encima de la timeline.
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
- El capturador integrado usa CameraX para foto/video y se comparte entre publicar y chat. En publicar puede abrirse en modo foto o video; en chat se usa modo dual.
- En publicar, los previews de imagen y video se expanden a `9:16` con avatar, acciones y metadatos cosmeticos del feed para validar el aspecto final antes de subir.
- La preview de publicaciones de texto usa el mismo frame visual del feed, con rail de acciones, rank/LIVE, ancho seguro y escala compacta.
- El boton publicar actua como barra de progreso durante la subida y, si el usuario intenta salir, pregunta si desea cancelar la operacion.
- En publicaciones de imagen, el lugar/ubicacion detectado por EXIF o reverse geocoding es editable como texto libre. Si no hay GPS, no se detecta nada o el lugar es incorrecto, el usuario puede escribirlo antes de publicar.
- Si no hay EXIF util y existe permiso de ubicacion, publicar imagen usa `getLastLocation()` para obtener una ubicacion instantanea sin bloquear el flujo.
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
- Soporte para adjuntos de archivo, imagen, video y audio en chat.

## Permisos e integracion Android

La app gestiona una secuencia de permisos y ajustes necesarios al terminar el splash:

- Permiso de notificaciones.
- Permiso de camara para capturador integrado de foto/video.
- Permiso de microfono para video con audio y grabadora de audio de chat.
- Permiso de ubicacion para publicaciones de imagen y SOS.
- Ajuste de enlaces compatibles para abrir `egquata.com` directamente en Q&uuml;ata.
- Deep links de posts y chats mediante fragmentos como `https://egquata.com/#post-...`.
- Reutilizacion de instancia abierta para evitar multiples actividades al abrir enlaces.
- Barra inferior ajustada para iconos mas grandes y mejor encaje con la barra de navegacion Android en modo de 3 botones.
- Firebase Cloud Messaging registra tokens por usuario/dispositivo y entrega push cuando la app no esta visible.

## Arquitectura

- Kotlin.
- Jetpack Compose.
- Material 3.
- MVVM con `ViewModel`, `UiState` y `UiEvent`.
- Repositorios por dominio.
- Navegacion con Navigation Compose.
- Capa de datos real para Supabase y WordPress.
- Modo mock disponible mediante propiedad Gradle.
- Cache local bajo almacenamiento privado: SQLite para respuestas Supabase, cache propia de chat/perfiles, Coil para imagenes y Media3 para videos.
- Cache SQLite especifica para traducciones Fang.
- Detector local de idioma mediante modelo FastText incluido en assets.
- Cliente HTTP para el Space `NLLB-Fang-Q&uuml;ata` con warmup y traduccion.
- Cliente HTTP DeepL para traducciones oficiales ES/EN/FR.
- Supabase Realtime para eventos de chat y Firebase Cloud Messaging para push nativo.
- Coil para imagenes.
- Media3 para reproduccion, previsualizacion y procesado multimedia, incluido Transformer para exportar videos editados.
- CameraX para capturador integrado de foto/video.
- Fused Location Provider para ubicacion rapida en publicaciones y SOS.
- kotlinx.serialization para modelos y cache.

## Estructura relevante

```text
app/src/main/java/com/quata/
  core/
    config/              Configuracion global
    designsystem/theme/  Plantillas dark-mode/light-mode, tokens de color y tamanos de texto
    location/            Proveedor Fused Location para publicaciones y SOS
    media/               Optimizacion de imagen/video y QuataMediaCache
    captions/            Transcripcion, layout, plantillas y burn-in de subtitulos
    language/            Detector local de idioma FastText para es/fr/en/fan
    text/                Shortcodes de posts y SOS, localizacion de tarjetas especiales
    ui/richtext/         Editor, runtime, serializacion HTML y renderizador de texto enriquecido
    translation/          Cliente API, cache SQLite y overlay del modo traductor Fang
    navigation/          NavGraph, deep links y chrome global
    localization/        Seleccion de idioma por locale del sistema
    notifications/       Notificaciones internas, Firebase Messaging y tokens push
    preferences/         Preferencias locales, incluido el modo de color
    session/             Sesion y estado de autenticacion
    ui/                  Componentes compartidos, TouchFlow, splash, camara y audio
  data/supabase/         API, modelos, RPC, cache GET y Realtime Supabase
  feature/
    auth/                Login, registro y recuperacion
    chat/                Conversaciones, mensajes, favoritos, SOS
    feed/                Feed, comentarios y acciones sociales
    neighborhoods/       Q&uuml;ata, comunidades y perfiles publicos
    notifications/       Avisos dentro de la app
    official/            Muro oficial, editor oficial, roles y publicaciones verificadas
    postcomposer/        Publicacion de contenido
      videoeditor/       Editor nativo de video en Compose + Media3 Transformer/MediaMuxer
      imageeditor/       Editor nativo de imagen con crop/zoom vertical 9:16
    profile/             Cuenta, avatar, preferencias y SOS
supabase/
  migrations/            Esquema/RPC de chat, auth bridge, Realtime y push
  functions/             Edge Functions `quata-auth-bridge` y `quata-push-dispatch`
```

## Configuracion

Configuracion principal:

```text
app/src/main/java/com/quata/core/config/AppConfig.kt
```

Puntos importantes:

- `QUATA_WORDPRESS_BASE_URL`
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- nombres de tablas Supabase
- `SUPABASE_TABLE_PUSH_TOKENS`
- `USE_MOCK_BACKEND`

Firebase se configura mediante `app/google-services.json`. Las Edge Functions de Supabase requieren secretos de entorno para Firebase Admin y se despliegan desde `supabase/functions/`.

El backend mock se puede activar al compilar:

```powershell
.\gradlew.bat assembleDebug -Pquata.useMockBackend=true
```

Por defecto, la build actual apunta al backend real.

## Build

Requisitos:

- Android Studio reciente.
- JDK 17, incluido el JBR de Android Studio.
- Android SDK con `compileSdk 36`.

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
versionCode = 26
versionName = 1.0.0
APP_VERSION_DATE = 2026-07-09
```

La app muestra esta informacion en la modal **Acerca de Q&uuml;ata**, accesible pulsando el logo de la esquina superior izquierda.

## Evolucion futura

- Monitorizacion de errores y rendimiento en produccion real, especialmente consumo de red/bateria con cuentas grandes y sesiones largas.
- Segunda fase de hardening RLS en Supabase para tablas heredadas pendientes, disenando politicas antes de activar RLS completo.
- Mejora incremental de accesibilidad: tamanos de fuente del sistema, contraste, TalkBack y objetivos tactiles en pantallas densas.
- Herramientas de moderacion avanzadas para administradores: auditoria, motivos de borrado, restauracion y panel de revision.
- Optimizacion de traducciones oficiales: cache de resultados, reintentos y control de coste por lote de publicacion.
- Ampliacion del visor de documentos y de previews en perfiles conforme aparezcan formatos reales de usuarios.
- QA recurrente en dispositivos fisicos de gama baja y Android moderno para validar camara, notificaciones, Realtime y edge-to-edge.
