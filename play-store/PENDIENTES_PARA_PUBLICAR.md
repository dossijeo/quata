# Pendientes para publicar Qüata

Ultima revision: 2026-06-21.

## Imprescindible antes de enviar a revision

- Crear o confirmar cuenta de desarrollador Google Play.
- Subir `app/build/outputs/bundle/release/app-release.aab`.
- Revisar Play App Signing y actualizar `assetlinks.json` con el certificado final de Play si Google cambia el certificado usado para App Links.
- Publicar `.well-known/assetlinks.json` en `egquata.com` y `www.egquata.com`.
- Publicar URL de politica de privacidad.
- Preparar cuenta demo para revision de Google.
- Completar Data Safety con la realidad final de backend y proveedores.
- Completar cuestionario de clasificacion IARC.
- Confirmar regiones/paises de lanzamiento.
- Confirmar flujo real de eliminacion de cuenta y datos.
- Verificar que las reglas de comunidad, denuncia y moderacion estan visibles y operativas.

## Ya definido

- Responsable legal/privacidad/reclamaciones/cierre de cuenta: Juan Antonio Nkono Ekaha Ddoho, `juanantonio.sti.tic@gmail.com`.
- Soporte tecnico: Gabriel Fernandez Robles, `gfrgabriel@gmail.com`.
- La app no tiene anuncios.
- Publico objetivo: personas mayores de edad de Guinea Ecuatorial, con uso posible por adultos en otros paises.
- Proveedores externos: Supabase, Qüata/WordPress/Better Messages, Hugging Face Spaces, Firebase/Google y Play Feature Delivery.
- Capturas finales preparadas en `play-store/screenshots/phone-1212x2424/`.

## Legal y privacidad

- Publicar politica de privacidad en una URL accesible sin login.
- Publicar o documentar condiciones de uso.
- Publicar reglas de comunidad/contenido generado por usuarios.
- Definir plazo maximo de eliminacion de cuenta.
- Definir retencion de:
  - Cuenta.
  - Publicaciones y comentarios borrados.
  - Mensajes.
  - Logs tecnicos.
  - Denuncias y registros de moderacion.
- Confirmar si se requiere direccion legal publica, telefono o datos fiscales en Play Console.
- DNI interno aportado por el proyecto si hiciera falta en tramites: `76441462L`. No publicarlo salvo necesidad formal.

## Producto

- Confirmar nombre publico final: `Qüata`.
- Confirmar categoria: recomendada `Social`.
- Confirmar descripcion final en espanol, ingles y frances.
- Confirmar si se usara `Qüata` o tambien `Quata` para busquedas y materiales.
- Decidir si se publicara primero en Internal testing, Closed testing o Production.
- Confirmar que "La Chana" y el contenido visible en capturas puede usarse como demo publica.
- Valorar sustituir la ubicacion de emulador "Mountain View, California" por una demo mas alineada con Guinea Ecuatorial si se desea.

## QA tecnico

- Instalar desde Internal testing o Closed testing y probar:
  - Login/registro.
  - Feed.
  - Perfil.
  - Comunidades.
  - Chats.
  - Publicacion.
  - Notificaciones.
  - Camara.
  - Ubicacion/SOS.
  - App Links.
  - Traduccion.
  - Subtitulos automaticos.
- Probar modulos dinamicos Vosk desde Play:
  - Espanol.
  - Frances.
  - Fallback ingles.
  - Cambio de idioma del sistema.
  - Descarga bajo demanda cuando falte el paquete del idioma actual.
- Confirmar que no hay modelos Vosk en el modulo base.
- Confirmar que el tamano de descarga por dispositivo esta dentro de limites de Play.

## Activos

- Exportar icono 512 x 512.
- Crear grafico destacado 1024 x 500.
- Subir capturas finales `1212x2424`.
- Revisar licencias de medios demo si se usan publicamente en capturas o material promocional.
- Valorar capturas de tablet si se soporta tablet.

## Decisiones abiertas

- Paises iniciales de lanzamiento.
- SLA de soporte y moderacion.
- Politica detallada de ubicacion en SOS.
- Si se mantiene `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Si se implementa eliminacion de cuenta dentro de la app antes del primer envio.
