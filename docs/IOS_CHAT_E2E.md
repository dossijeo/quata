# E2E reproducible de Chat iOS

Esta comprobación requiere un usuario de prueba ya autenticado en un simulador iOS y un segundo
usuario que pertenezca a la misma conversación. No usa claves de servicio, cambios de RLS ni datos
simulados.

1. En macOS, configura únicamente `QUATA_SUPABASE_URL` y `QUATA_SUPABASE_PUBLISHABLE_KEY` en el
   esquema Debug y genera el framework y proyecto siguiendo `iosApp/README.md`.
2. Inicia `QuataIos` en el simulador autorizado y abre **Conversaciones** desde el menú de rutas.
3. Abre la conversación de los dos usuarios. Envía un texto, un PDF o imagen desde el selector y
   una nota de voz; verifica que los tres mensajes aparecen también en el segundo usuario.
4. En el primer usuario, abre el PDF o imagen y reproduce la nota de voz. La vista previa debe
   recibir un archivo temporal local y el audio debe comenzar sólo tras descargar el objeto desde
   el bucket `chat-attachments` autenticado.
5. Envía desde el segundo usuario una URL que no sea el objeto público canónico HTTPS de ese
   bucket, una URL con query/fragment o una redirección. Abrir/reproducir debe fallar sin que
   Quick Look ni AVFoundation reciban la URL remota.
6. Cierra la vista previa y cambia de audio; verifica que la reproducción se detiene y no quedan
   archivos reproducibles fuera del directorio temporal de Chat.

Antes de ejecutar en una VM compartida, coordina el simulador y la conversación de prueba para no
interferir con otros carriles iOS. La verificación de compilación sin simulador se ejecuta desde
Windows con:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :feature:chat:compileKotlinIosX64
```
