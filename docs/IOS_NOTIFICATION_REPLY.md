# Respuesta desde una notificación iOS

Estado: implementación en validación. No cierra `FLOW-NOTIFICATION-REPLY` ni
acredita entrega APNs, instalación física o distribución.

## Recorrido de producto

El dispatcher asigna `QUATA_CHAT_MESSAGE` a las alertas APNs de chat. Al arrancar,
la app registra esa categoría con la acción nativa de texto `QUATA_CHAT_REPLY`,
disponible con el dispositivo desbloqueado. La apertura normal conserva el
recorrido de navegación existente; acciones desconocidas no abren un chat.

El texto recibido por el delegate pasa al runtime de Reply, que usa la misma
sesión renovable de Auth/Feed y el transporte PostgREST de Chat. No crea otro
almacén de sesión. Exige destinatario explícito, conversación `sb:<id>` válida,
texto no vacío y un identificador de cliente conservado durante los reintentos.
El actor del cuerpo y el de la petición autenticada deben coincidir.

Se realizan hasta tres intentos con pausas de dos segundos, timeout de petición
de cinco segundos y límite global de veinte segundos. Un timeout de intento
permite reintentar; una cancelación de sesión detiene la operación. El logout
bloquea nuevos replies y cancela los pendientes antes de retirar el token APNs.
Si esa preparación falla, se restablece la disponibilidad con la sesión conservada.

Sólo una respuesta RPC que confirme éxito, mensaje positivo y la conversación
esperada permite retirar la notificación. Tras un fallo se muestra el aviso
existente de abrir el chat para volver a intentarlo, sin copiar el texto escrito
ni el contenido de la alerta original. Un rechazo por sesión no genera ese aviso.
El completion del sistema se completa también al cancelar.

## Validación y límites

Las pruebas comunes focales cubren respuesta perdida, identidad estable, cambio
de actor, entradas inválidas, respuestas HTTP sin confirmación de mensaje,
cancelación y diferencia entre timeout de intento y deadline externo:

```powershell
./gradlew.bat :feature:chat:wasmJsBrowserTest --tests com.quata.feature.chat.data.NotificationReplySenderTest --no-configure-on-demand --max-workers=1 --no-daemon --console=plain
```

Se usa Chrome Headless: este módulo Compose carga Skiko y el runner Node no sirve
como sustituto del runner de navegador. La configuración completa mantiene el
lockfile global; no se actualiza para ejecutar un subconjunto de módulos.

La compilación Kotlin/iOS y Swift y siete pruebas XCTest de categoría, aviso de
fallo y destinatario han pasado. Estas pruebas no accionan el botón del sistema.
Una prueba nativa adicional confirma que las etiquetas de inglés, español y francés,
con los mismos textos de Android, están empaquetadas en la aplicación.
Queda pendiente el recorrido de la acción real con sesión y conversación de pruebas
autorizadas, verificación remota del mensaje, estados de éxito/error y limpieza.
Las pruebas simuladas no sustituyen ese recorrido.
Invocaciones separadas y reinicios de proceso no comparten la clave de reintento.

El ensayo UI opt-in `QuataIosNotificationReplyUITests` usa la app normal, el permiso
del sistema y los controles nativos «Responder»/«Enviar». El paso
`notification-reply` del worker iOS conserva el lease del candidato y exige una
sesión instalada cuyo destinatario coincida. Genera marcadores exclusivos, guarda
la intención antes de inyectar y ejecuta XCTest una sola vez, sin paralelismo.
El coordinador `scripts/ios_notification_reply_ui.py` inyecta `conversation_id`
con el formato real `sb:<id>`; no despliega ni activa el dispatcher.

El recibo de ese paso declara únicamente envío desde la UI y mantiene
`backendVerified: false`. El runner de backend todavía debe comprobar el mensaje
exacto y los estados de notificación y retirar conversación/sesiones temporales.
Un fallo conserva el directorio del intento para reconciliación antes de repetir.

El cambio de categoría aún no se ha desplegado al dispatcher remoto. Su despliegue
debe seguir el paquete focal revisado del [runbook APNs](IOS_APNS_PRODUCTION_REQUIREMENTS.md).
El [Personal Team local](IOS_LOCAL_DEVELOPMENT_SIGNING.md) no habilita APNs;
una notificación inyectada en el simulador no demuestra entrega desde Apple.
