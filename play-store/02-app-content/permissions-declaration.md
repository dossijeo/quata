# Permisos y declaraciones

## Permisos Android detectados

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.CAMERA`
- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.RECEIVE_BOOT_COMPLETED`

Feature:

- `android.hardware.camera` con `required=false`

## Justificación funcional

- Internet y estado de red: feed, login, chats, multimedia, traducción, Supabase/WordPress/Better Messages y notificaciones.
- Cámara: crear publicaciones, imágenes, videos o contenido de perfil.
- Ubicación aproximada/precisa: función SOS y posible preservación de metadatos de ubicación en contenido multimedia.
- Notificaciones: avisos de chats, actividad social o eventos relevantes.
- Boot completed: reactivar tareas o recepción de notificaciones tras reinicio si aplica.

## Permisos sensibles no detectados

- SMS.
- Registro de llamadas.
- Contactos del dispositivo.
- Micrófono explícito en manifest.
- Ubicación en segundo plano.

## Pendiente de validar

- Si el uso de ubicación debe explicarse con pantalla previa antes del diálogo del sistema.
- Si alguna función de video/audio requiere declarar micrófono en builds futuros.
