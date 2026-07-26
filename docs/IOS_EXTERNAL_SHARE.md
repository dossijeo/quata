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

Cada manifiesto guarda `createdAtEpochMillis` al comenzar la publicación. La
cola se ordena por ese timestamp y, para empates, por ID y nombre de directorio;
el UUID nunca determina por sí solo qué elemento es el más antiguo.

La reclamación mueve atómicamente `pending/<id>` a
`processing/claim-<claimedAt>-<owner>-<id>`. El propio nombre es una lease:
identifica la generación y cuándo fue reclamada. Un registro de claims activo
evita duplicados dentro del proceso. Tras un crash, otro proceso sólo puede
recuperar una lease vencida (TTL de dos minutos) y lo hace renombrando
atómicamente el directorio a una generación nueva. Dos recuperadores pueden
observar el mismo candidato, pero sólo uno puede completar el rename desde el
nombre exacto anterior.

Cerrar, cancelar o completar el envío elimina únicamente el directorio de la
generación reclamada. Por tanto, un callback tardío de una generación anterior
no puede borrar un payload que ya haya sido recuperado con una lease nueva.
Los directorios `processing/<id>` creados por versiones anteriores se
reconocen y migran a la nueva lease al recuperarlos.

La cola pendiente se limita a diez elementos para evitar crecimiento sin
límite si el usuario comparte repetidamente sin abrir Quata. Los IDs, nombres,
rutas, cantidad y tamaño se validan antes de exponer archivos al host
compartido.

La CI sin firma sólo valida compilación, enlace, tests y estructura del
archive. App Group y Share Extension requieren perfiles firmados compatibles y
una prueba en dispositivo físico; no deben declararse operativos por un archive
sin firma.

La CI exacta #30210875187 de la ola 2 (`9cc84dc2`) terminó verde. Su archive
sin firma no crea un App Group físico ni sustituye la validación con
provisioning compatible y dispositivo real.
