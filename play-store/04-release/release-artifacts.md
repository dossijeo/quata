# Artefactos de release

Ultima regeneracion: 2026-06-21 16:49 Europe/Madrid.

## App Bundle release

Ruta:

`app/build/outputs/bundle/release/app-release.aab`

Estado:

- Generado correctamente con `bundleRelease`.
- Firmado en modo release.
- Preparado para subir a Play Console si las credenciales y Play App Signing estan listos.

Datos del artefacto:

- Tamano: 147062386 bytes.
- SHA-256: `8FABCE93439961BB049F7D4761CDC9109C2A2F4A8930948FA38722BC575CF22F`
- `versionCode`: 13.
- `versionName`: 0.9.4.

## APK debug

Ruta:

`app/build/outputs/apk/debug/app-debug.apk`

Estado:

- Regenerado correctamente con `assembleDebug --rerun-tasks`.
- Incluye el cambio tecnico de reverse geocoding.

Datos del artefacto:

- Tamano: 47454560 bytes.
- SHA-256: `CEFFA5C2AD37F70D5719C1C42D23D33F59115D3878BA4DA79E95EA98B341C4AB`

## APKs debug de modulos dinamicos

Para instalar la version debug con los modulos Vosk en emulador mediante `adb install-multiple`, usar tambien:

- `vosk_model_en/build/outputs/apk/debug/vosk_model_en-debug.apk`
- `vosk_model_es/build/outputs/apk/debug/vosk_model_es-debug.apk`
- `vosk_model_fr/build/outputs/apk/debug/vosk_model_fr-debug.apk`

## Firma

La firma release usa el keystore local del proyecto. No incluir contrasenas ni archivos `.jks` en documentacion publica o repositorios compartidos.

## App Signing de Google Play

Antes de publicar, confirmar:

- Si se usara Play App Signing con clave gestionada por Google.
- Si la clave local queda como upload key.
- Que existe backup seguro del keystore y contrasenas fuera del repositorio.

## Prueba recomendada

1. Subir AAB a Internal testing.
2. Instalar desde Play en un dispositivo real o emulador con cuenta de test.
3. Verificar login, feed, chats, publicacion, notificaciones, permisos y App Links.
4. Verificar descarga bajo demanda del modelo de subtitulos segun idioma.
5. Cambiar idioma del sistema y verificar que la app solicita descargar el paquete adecuado si falta.
