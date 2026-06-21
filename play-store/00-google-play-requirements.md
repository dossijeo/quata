# Requisitos de Google Play consultados

Consulta realizada el 2026-06-21 sobre fuentes oficiales de Google Play Console:

- Crear y configurar una app: https://support.google.com/googleplay/android-developer/answer/9859152
- Preparar la app para revision: https://support.google.com/googleplay/android-developer/answer/9859455
- Recursos graficos y capturas: https://support.google.com/googleplay/android-developer/answer/9866151
- Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Clasificacion de contenido: https://support.google.com/googleplay/android-developer/answer/9859655

## Ficha publica

- Nombre de app: maximo 30 caracteres.
- Descripcion breve: maximo 80 caracteres.
- Descripcion completa: maximo 4000 caracteres.
- La ficha se comparte entre tracks, incluyendo tracks de prueba.

## Recursos graficos

- Icono de Play: PNG 32-bit con alfa, 512 x 512 px, maximo 1024 KB.
- Grafico destacado: JPEG o PNG 24-bit sin alfa, 1024 x 500 px.
- Capturas: minimo 2 capturas en total para publicar.
- Capturas: JPEG o PNG 24-bit sin alfa, dimension minima 320 px y maxima 3840 px.
- La dimension mayor no puede ser mas del doble que la menor.
- Recomendado para apps: al menos 4 capturas con minimo 1080 px.
- Se pueden subir hasta 8 capturas por tipo de dispositivo.

Serie preparada para Qüata:

- `play-store/screenshots/phone-1212x2424/`
- 7 capturas PNG.
- 1212 x 2424 px.
- Relacion 2:1 exacta.

## App Content

Play Console solicita informacion sobre:

- Politica de privacidad.
- Si la app contiene anuncios.
- Instrucciones de acceso para partes restringidas por login.
- Publico objetivo y contenido.
- Permisos sensibles o de alto riesgo cuando aplique.
- Clasificacion de contenido.
- Practicas de privacidad y seguridad.

Datos preparados para Qüata:

- Anuncios: no.
- Publico objetivo: adultos, principalmente Guinea Ecuatorial.
- Contenido generado por usuarios: si.
- Login requerido para funciones sociales privadas: si.

## Data Safety

- Todos los desarrolladores con apps publicadas en Google Play deben completar el formulario de Data Safety, salvo apps exclusivamente activas en Internal testing.
- Incluso las apps que no recopilan datos deben completar el formulario y proporcionar politica de privacidad.
- La declaracion debe cubrir datos tratados por la app y por librerias o SDKs de terceros.
- El desarrollador es responsable de que la declaracion sea completa y exacta.

## Acceso para revision

Si la app o partes de la app estan restringidas por credenciales, ubicacion, membresia u otra autenticacion, se deben proporcionar instrucciones de acceso para revision.

Qüata requiere esto porque varias funciones sociales requieren login.
