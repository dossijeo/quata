# Capturas para Play

Ultima revision: 2026-06-21.

## Capturas finales preparadas

Las capturas recomendadas para subir a Google Play estan en:

- `play-store/screenshots/phone-1212x2424/`

Todas son PNG de 24-bit RGB, 1212 x 2424 px, con relacion exacta 2:1. Se eligio este formato para cumplir la regla de Google Play de que la dimension mayor no sea mas del doble que la menor, sin recortar arriba/abajo ni reducir la app dentro del emulador. La captura nativa 1080 x 2424 se conserva completa y se centra sobre fondo oscuro lateral.

Archivos:

- `01-chat-lachana.png`: chat comunitario "La Chana".
- `02-translator-chat.png`: modo traductor activo con resultado `ES->FAN`.
- `03-create-publication.png`: selector de creacion de publicaciones.
- `04-image-editor.png`: editor de imagen con herramienta de recorte/zoom.
- `05-image-publication-location.png`: publicacion con imagen y ubicacion editable sin coordenadas.
- `06-video-editor-subtitles.png`: editor de video con recorte, timeline y estilos de subtitulos.
- `07-video-publication.png`: compositor de publicacion con video editado.

## Capturas crudas

Las capturas crudas del emulador estan en:

- `play-store/screenshots/raw-emulator/`

Las capturas crudas mantienen la resolucion original 1080 x 2424. Algunas son solo de control o navegacion y no deben subirse directamente.

## Capturas antiguas

La carpeta antigua `play-store/screenshots/phone-1080x1920/` se conserva como historico, pero no se recomienda usarla porque las capturas estaban recortadas arriba/abajo y no reflejan la serie final.

## Limpieza de galeria del emulador

Para evitar que el Photo Picker mostrara capturas antiguas durante la demo, se movieron medios del emulador a:

- `/storage/emulated/0/.quata-playstore-media-backup/`

Esa carpeta tiene `.nomedia`. Quedaron visibles solo:

- `/storage/emulated/0/Pictures/Quata/ureca-bioko-waterfall.jpg`
- `/storage/emulated/0/Movies/Quata/sample-video-vertical.mp4`

## Revision antes de subir

Antes de subir a Play:

- Revisar que no aparezcan datos personales reales.
- Evitar capturas de feed con marcas de terceros.
- Confirmar que "La Chana" y los mensajes visibles pueden usarse como demo.
- Confirmar que la ubicacion "Mountain View, California" es aceptable como ubicacion de emulador o sustituir por una demo mas alineada con Guinea Ecuatorial.
