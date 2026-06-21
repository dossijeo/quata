# Estado de publicacion

Ultima revision: 2026-06-21.

## Preparado

- Proyecto Android con `applicationId` `com.quata`.
- Bundle release firmado generado en `app/build/outputs/bundle/release/app-release.aab`.
- App Links preparados con `play-store/04-release/assetlinks.json`.
- Capturas finales de telefono preparadas en `play-store/screenshots/phone-1212x2424/`.
- Entrega dinamica de modelos Vosk:
  - `vosk_model_en`
  - `vosk_model_es`
  - `vosk_model_fr`
- Borradores de ficha publica en espanol, ingles y frances.
- Borradores de Data Safety, privacidad, permisos, clasificacion y acceso para revision.
- Correccion verificada de ubicacion en publicacion con imagen: se muestra texto editable, no coordenadas.
- Galeria del emulador limpiada para capturas demo, moviendo medios antiguos a respaldo oculto.

## Datos ya definidos

- Responsable legal/privacidad/reclamaciones/cierre de cuenta: Juan Antonio Nkono Ekaha Ddoho, `juanantonio.sti.tic@gmail.com`.
- Soporte tecnico: Gabriel Fernandez Robles, `gfrgabriel@gmail.com`.
- La app no contiene anuncios.
- Proveedores externos: Supabase, web propia Qüata/WordPress/Better Messages, Hugging Face Spaces, Firebase/Google para notificaciones y Play Feature Delivery.
- Publico objetivo: personas mayores de edad de Guinea Ecuatorial, con uso posible por adultos de otros paises.

## Requiere decision o aporte externo

- Cuenta de desarrollador de Google Play y acceso a Play Console.
- URL publica de politica de privacidad.
- Publicar `.well-known/assetlinks.json` en `egquata.com` y `www.egquata.com`.
- Credenciales de cuenta demo para el equipo de revision de Google.
- Direccion legal, telefono publico si se decide mostrarlo y datos fiscales que Play solicite.
- Confirmacion legal final de condiciones de uso, privacidad, moderacion y retencion.
- Confirmar regiones/paises de lanzamiento.
- Exportar icono 512 x 512 y crear grafico destacado 1024 x 500.

## Riesgos antes de enviar

- El flujo de modelos bajo demanda debe probarse desde Internal testing o Closed testing de Play, porque la entrega dinamica real depende de Play.
- Play espera mecanismos claros de denuncia, bloqueo o moderacion para contenido generado por usuarios.
- Si existen pantallas protegidas por login, Google requiere credenciales e instrucciones de acceso.
- La politica de eliminacion de cuenta debe estar operativa y ser coherente con el backend.
