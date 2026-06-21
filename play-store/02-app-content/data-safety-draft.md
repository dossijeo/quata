# Borrador Data Safety

Ultima revision tecnica: 2026-06-21.

Este borrador resume lo que probablemente debe declararse en Google Play Console. Debe validarse con la configuracion real de backend y con la politica de privacidad final publicada.

## Resumen recomendado

Qüata recopila o procesa datos para funcionalidad principal social, mensajeria, publicacion multimedia, seguridad, soporte, notificaciones, traduccion y subtitulos bajo demanda. La app no contiene anuncios.

## Datos personales

Categorias probables:

- Nombre o nombre visible.
- Email.
- Telefono.
- Identificador de usuario.
- Foto/avatar de perfil.
- Pais, comunidad, barrio o informacion similar aportada por el usuario.

Finalidades:

- Gestion de cuenta.
- Perfil publico o visible dentro de la app.
- Comunicacion entre usuarios.
- Seguridad, soporte y recuperacion de cuenta.

## Contenido generado por usuarios

Qüata permite crear y compartir:

- Publicaciones.
- Comentarios.
- Mensajes de chat.
- Imagenes.
- Videos.
- Archivos compartidos.
- Mensajes o configuraciones SOS.

Finalidades:

- Funcionalidad social principal.
- Mensajeria y comunidades.
- Moderacion, denuncias y seguridad.
- Sincronizacion entre dispositivos.

## Ubicacion

Permisos detectados:

- `ACCESS_COARSE_LOCATION`
- `ACCESS_FINE_LOCATION`

Usos previstos:

- Asociar ubicacion editable a publicaciones cuando el usuario lo permite.
- Funciones SOS configuradas por el usuario.
- Posible contexto de comunidad/barrio.

No se ha detectado permiso de ubicacion en segundo plano en el manifiesto revisado.

## Fotos, videos y archivos

La app accede a medios solo cuando el usuario elige crear, editar, subir o compartir contenido.

Finalidades:

- Publicaciones.
- Mensajeria.
- Perfil/avatar.
- Edicion multimedia local.
- Generacion de subtitulos en videos cuando el usuario lo solicita.

## Actividad de la app

Datos probables:

- Likes.
- Seguimientos.
- Comentarios.
- Denuncias.
- Favoritos.
- Conversaciones.
- Interacciones con notificaciones.

Finalidades:

- Funcionalidad social.
- Personalizacion basica de experiencia.
- Seguridad, soporte y moderacion.

## Identificadores y datos del dispositivo

Datos probables:

- Token de notificaciones push.
- Identificadores internos de sesion o usuario.
- Datos tecnicos necesarios para funcionamiento, logs o seguridad.

Finalidades:

- Notificaciones.
- Sesion.
- Seguridad.
- Soporte tecnico.

## Servicios y proveedores externos

Proveedores declarados por el proyecto:

- Supabase.
- Web propia de Qüata en WordPress y servicios asociados, incluyendo Better Messages, en `https://egquata.com/`.
- Firebase Cloud Messaging o servicios equivalentes de Google para notificaciones.
- Hugging Face Spaces para traduccion remota.
- Google Play Feature Delivery para descarga bajo demanda de modelos Vosk.

En Play Console conviene distinguir:

- Datos compartidos con otros usuarios por accion del usuario, como posts, comentarios, perfil, chats o archivos.
- Datos procesados por proveedores de servicio para operar la app. Segun la guia de Play, este tratamiento puede no contarse como "sharing" si el proveedor actua como encargado/prestador de servicio y procesa los datos en nombre del desarrollador.

## Seguridad

Declaracion preliminar:

- Los datos se transmiten mediante HTTPS cuando los servicios externos lo soportan.
- Falta confirmar cifrado en reposo, controles internos de acceso, retencion y eliminacion efectiva.

## Respuestas pendientes antes de enviar

- Confirmar plazos de retencion.
- Confirmar flujo de eliminacion de cuenta y datos.
- Confirmar si todos los endpoints externos usan HTTPS.
- Confirmar si las denuncias y registros de moderacion se conservan y durante cuanto tiempo.
