# External Share en iOS

La Share Extension copia un máximo de cinco adjuntos a un directorio de
staging del App Group `group.com.quata.ios.share`, escribe el manifiesto de
forma atómica y publica el conjunto mediante un rename dentro del mismo
volumen. La extensión no recibe sesión, claves de Supabase ni acceso directo a
Chat.

iOS no ofrece a una Share Extension una API pública soportada para abrir su
aplicación contenedora. Por ello la extensión termina después de publicar el
payload. Quata reclama el elemento pendiente más antiguo en el siguiente
arranque o foreground autenticado y reutiliza el repositorio Chat y la sesión
renovable que ya posee la aplicación.

La reclamación mueve `pending/<id>` a `processing/<id>`. Un registro de claims
activo evita que dos callbacks del mismo proceso presenten o envíen el mismo
payload; un nuevo proceso puede recuperar un elemento que quedó en
`processing` tras una terminación. Cerrar, cancelar o completar el envío
elimina el directorio procesado.

La cola se limita a diez elementos para evitar crecimiento sin límite si el
usuario comparte repetidamente sin abrir Quata. Los IDs, nombres, rutas,
cantidad y tamaño se validan antes de exponer archivos al host compartido.

La CI sin firma sólo valida compilación, enlace, tests y estructura del
archive. App Group y Share Extension requieren perfiles firmados compatibles y
una prueba en dispositivo físico; no deben declararse operativos por un archive
sin firma.
