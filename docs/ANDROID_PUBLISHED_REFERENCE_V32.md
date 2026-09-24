# Referencia Android publicada: AAB v32

El 8 de septiembre de 2026 el propietario confirmó explícitamente que el archivo
`C:\Users\PC\Desktop\QÜATA\Builds\Release\quata-release-v32.aab` es el publicado
en Google Play. SHA-256 comprobado:
`bf6aadc60e18b05d4f4203c8356a9e9a8b4c917262cc0a1d28518b8c6baf70ff`.
La referencia es un **AAB**, no el APK debug empleado por evidencias anteriores ni
un APK generado/firmado por Google Play.

El archivo local se corresponde por nombre con código 32 / versión 1.0.4; el registro
de Google Play sincronizado el 2026-09-01T15:15:05Z declara `com.quata`, production,
versionCode 32, versionName 1.0.4, status completed. El commit
`f0fd50ca65ca35372093c127fb09e379743274ff` declara esa misma versión; esta coincidencia
no demuestra que sea el commit exacto del build. El snapshot histórico `bd8a73b` tampoco
se considera una reproducción exacta del artefacto publicado.

El propio AAB conserva `base/root/META-INF/version-control-info.textproto`, pero su
contenido es `generate_error_reason: NO_VALID_GIT_FOUND`; no incorpora una revisión VCS
que permita recuperar el commit del build. `BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties`
identifica Android Gradle Plugin 9.1.0, sin añadir procedencia Git. Por tanto, el AAB,
su hash, versión y registro de Play son la referencia publicada verificable; el commit
exacto permanece desconocido y no se sustituye por el APK derivado y firmado por Play.

## Consulta preferente del código fuente

Por indicación del propietario, buscar primero el commit titulado
«Versión 1.0.4»: `1b8f3b70c21c361e7566b69d0bcc4fbeb9f96c55` para leer
el código directamente. Declara versionCode 31 y versionName 1.0.4; el commit
`f0fd50ca65ca35372093c127fb09e379743274ff` declara código 32. Comparar los
cambios pertinentes antes de atribuir comportamiento al AAB confirmado. Priorizar
la fuente y consultar el binario cuando una duda afecte a la compatibilidad.

## Contrato de recuperación comprobado en el binario

Se extrajeron `base/dex/classes.dex`, `classes2.dex` y el mapping R8 incluido en
`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`. El mapping enlaza
`AuthRepositoryImpl.resetPassword` con `ce5.S` y `CommunityProfile.secret_answer`
con `xt0.t`. El desensamblado mediante Android SDK dexdump acredita en `classes.dex`:

- Método `ce5.S`, offset `0x4762e0`.
- Lectura de `xt0.t` en `0x4763ca`, sustitución de null por cadena vacía y trim.
- Comparación en cliente con la respuesta introducida en `0x4763f6`; el fallo lanza
  «La respuesta secreta no es correcta».
- Construcción de patch `pass_hash` / `pass_plain` en `0x47640e` / `0x476424` y llamada
  de actualización de perfil en `0x476456`.

Por tanto, anular `secret_answer` al guardar sólo un hash rompe el consumidor publicado.
El contrato backend debe conservar compatibilidad durante la migración; el endurecimiento
se realiza después de publicar los clientes migrados, conforme al operating model §3.
Esto es evidencia estática del artefacto confirmado, no una nueva ejecución E2E ni GO de
ACCOUNT-RECOVERY-SECRET. No altera los límites de los recorridos históricos.

La compatibilidad de `20260726171003` usa además la firma HTTP observable del
binario: OkHttp 4.12.0, rol anónimo y las rutas directas de `community_profiles`.
El cliente publicado no envía atestación, por lo que esa firma no demuestra de
forma criptográfica el origen APK. La rama conserva esa inseguridad únicamente
para v32, registra sólo contador/último uso y puede apagarse con el interruptor
`quata_legacy_android_v32_compatibility.enabled`. El cliente actual se distingue
con `x-quata-client-generation: android-auth-boundary-v1` y no puede entrar en
esa rama.
