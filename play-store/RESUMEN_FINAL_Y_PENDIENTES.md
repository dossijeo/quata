# Resumen final y pendientes para Google Play

Fecha: 2026-06-21.

## Material preparado

- AAB release generado: `app/build/outputs/bundle/release/app-release.aab`.
- App Links release: `play-store/04-release/assetlinks.json`.
- Capturas finales: `play-store/screenshots/phone-1212x2424/`.
- Ficha de Play en espanol, ingles y frances: `play-store/01-store-listing/`.
- Declaraciones de App Content: `play-store/02-app-content/`.
- Borradores legales y privacidad: `play-store/03-legal-and-privacy/`.
- Inventario de assets: `play-store/05-assets/asset-inventory.md`.

## Capturas finales

Serie recomendada para subir:

1. `01-chat-lachana.png`: chat comunitario "La Chana".
2. `02-translator-chat.png`: modo traductor activo con resultado `ES->FAN`.
3. `03-create-publication.png`: selector de tipo de publicacion.
4. `04-image-editor.png`: editor de imagen.
5. `05-image-publication-location.png`: publicacion con imagen y ubicacion editable.
6. `06-video-editor-subtitles.png`: editor de video con subtitulos.
7. `07-video-publication.png`: publicacion con video.

Formato:

- PNG.
- 1212 x 2424 px.
- Relacion 2:1 exacta.
- Sin recorte superior/inferior.

## Datos definidos

- Responsable legal, privacidad, quejas, reclamaciones y cierre de cuenta: Juan Antonio Nkono Ekaha Ddoho.
- Email del responsable: `juanantonio.sti.tic@gmail.com`.
- Soporte tecnico: Gabriel Fernandez Robles.
- Email de soporte tecnico: `gfrgabriel@gmail.com`.
- La app no contiene anuncios.
- Publico objetivo: mayores de edad de Guinea Ecuatorial, con uso posible por adultos de cualquier pais.
- Proveedores externos:
  - Supabase.
  - Web propia Qüata/WordPress y Better Messages.
  - Hugging Face Spaces para traduccion.
  - Firebase/Google para notificaciones.
  - Google Play Feature Delivery para modelos Vosk bajo demanda.

## Pendientes criticos

- Crear o confirmar cuenta de desarrollador Google Play.
- Subir el AAB release a un track de prueba.
- Publicar politica de privacidad en una URL accesible sin login.
- Publicar `.well-known/assetlinks.json` en `egquata.com` y `www.egquata.com`.
- Completar Data Safety en Play Console con la realidad final del backend.
- Completar cuestionario IARC de clasificacion.
- Crear cuenta demo y credenciales para revision de Google.
- Exportar icono Play 512 x 512.
- Crear grafico destacado 1024 x 500.
- Confirmar regiones de lanzamiento.
- Confirmar plazos de retencion y eliminacion de datos.
- Confirmar reglas de comunidad, denuncia, bloqueo y moderacion.

## Pendientes recomendables antes de produccion

- Probar instalacion desde Internal testing o Closed testing.
- Probar Play Feature Delivery real para modelos de subtitulos:
  - Espanol.
  - Frances.
  - Ingles como fallback.
  - Cambio de idioma del sistema.
  - Descarga bajo demanda si falta el paquete del idioma.
- Verificar que la eliminacion de cuenta funciona o que el proceso por email esta documentado y atendido.
- Sustituir la ubicacion de emulador "Mountain View, California" en capturas si se prefiere una demo mas alineada con Guinea Ecuatorial.
- Revisar licencias de los medios demo usados en capturas si se publican tal cual.

## Nota interna sobre DNI

Dato aportado por el proyecto por si Play, contratos o tramites formales lo solicitan:

- `76441462L`

No incluir este dato en textos publicos salvo necesidad formal.

## Fuentes oficiales consultadas

- Recursos graficos y capturas: https://support.google.com/googleplay/android-developer/answer/9866151
- Preparar app para revision: https://support.google.com/googleplay/android-developer/answer/9859455
- Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Clasificacion de contenido: https://support.google.com/googleplay/android-developer/answer/9859655
