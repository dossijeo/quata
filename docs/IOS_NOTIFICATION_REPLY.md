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
`backendVerified: false`. `scripts/notification-reply-ios-trial.mjs` prepara dos
actores desechables con el protocolo de fixtures existente, instala una sesión
verificada y registra el intento en un journal privado antes de la UI. El envío
desde la acción nativa es el único productor del mensaje de Reply en el ensayo.

El runner comprueba un único mensaje con actor, conversación, texto y clave de
cliente esperados. Después consulta las notificaciones entregadas a la app mediante
`QuataIosNotificationReplyOutcomeTests`, vuelve a observar la unicidad y retira la
conversación y las identidades/sesiones propias. La auditoría bajo locks de limpieza
invalida también un duplicado tardío observado. Reutilizar el namespace interno
`FLOW-DEEP-LINKS` de esos fixtures no certifica esa unidad: el informe del ensayo
identifica `FLOW-NOTIFICATION-REPLY`.

Los tests del coordinador, canal, verificación y limpieza han pasado, y el test
nativo de outcome compila. El recorrido real aún no está acreditado: la notificación
inyectada apareció, pero no se observó la acción «Responder» al mantener pulsados
su texto o su tarjeta. No se envió una respuesta. Los intentos se reconciliaron,
incluidas la alerta propia, la sesión y las identidades/conversación desechables.

`testReconcileOnlyTheOwnedFailedNotification` pasó: exige actor, conversación,
marker y título propios, retira sólo el request ID correspondiente y verifica
ausencia. Sus metadatos de categorías describen el host después de relanzarlo;
no prueban retrospectivamente el registro durante el fallo de la UI.
El piloto `testInspectReplyAffordanceWithoutSending` termina antes de pulsar Reply,
escribir o enviar. Su controlador comprueba sesión vacía antes y después y no crea
fixtures de backend. Se está aislando la presentación de la acción; estos pilotos
no sustituyen el recorrido con sesión y persistencia remota verificadas.

El comparativo local `QuataIosLocalNotificationComparisonTests` también mostró la
tarjeta sin «Responder», con la misma pulsación de un segundo. Antes de programarla
comprobó permiso y categoría/acción; después retiró la única solicitud propia y
verificó sesión vacía. No creó actores ni envió mensajes. Este resultado debilita
la hipótesis de un fallo exclusivo de `simctl push`, sin identificar aún la causa.
La fecha efectiva de entrega quedó entre la parada de la app y la solicitud de
launch del UITest. El sondeo del coordinador no acredita ausencia continua: su
última muestra llegó después de esa solicitud. El indicador automático de background
de ese ensayo se conserva con una corrección explícita; no se acepta como gate.
La revisión del vídeo muestra un desplazamiento lateral que revela «Abrir» y
después se repliega. El evento de XCTest declara coordenadas iguales al pulsar y
soltar. Esta discrepancia orienta el diagnóstico hacia la interacción sintetizada
y su interpretación por SpringBoard; no demuestra que falte la categoría ni
identifica todavía la causa. No justifica cambiar la semántica de Reply.

El control `testSystemIconLongPressControl` abrió visualmente el menú contextual
de Ajustes con la misma pulsación de un segundo. Su XCTest original falló porque
exigía un acceso Bluetooth que ese simulador no muestra. Captura y jerarquía
confirman las opciones «Editar pantalla de inicio» y «Eliminar app», sin pulsarlas;
el criterio posterior usa sus identificadores observados. El fallo original se
conserva y no se repitió el ensayo para convertirlo en PASS. La limpieza verificó
sesión vacía y preservó el simulador estable. Esta observación positiva queda
limitada al icono: debilita una explicación general del gesto, sin acreditar Reply.

El comparativo posterior en un candidato iOS 26.5, con los mismos binarios y la misma
pulsación, reprodujo el desplazamiento hacia «Abrir» y el repliegue sin «Responder».
La revisión visual independiente confirmó el resultado; se retiró la alerta propia
y se verificó sesión vacía antes y después. No hubo envío ni fixtures de backend.
El fenómeno no queda limitado al entorno iOS 18 observado; su causa sigue sin
aislarse y no demuestra un defecto del producto. Los intentos previos detenidos por
primer arranque incompleto o falta de espacio no llegaron a XCTest y no cuentan
como evidencia de la interacción. La comprobación de capacidad posterior usa el
volumen de datos real de CoreSimulator, separado del disco de builds.

Un control UIKit independiente, sin Compose/Kotlin, sesión ni backend, reprodujo
la ausencia visible de «Responder» con la misma pulsación de un segundo. La app
comprobó permiso y categoría antes de programar su alerta local; PNG y AX muestran
el mismo marcador antes y después, todavía en presentación compacta, sin editor
ni envío. La revisión independiente confirma esta observación, no una expansión
correcta de la tarjeta. El control comparte XCTest, gesto y Centro de notificaciones:
debilita una causa exclusiva de Qüata, pero no identifica un defecto de iOS ni de
virtualización. El intervalo local de entrega y la ausencia de relanzamiento de
la app también difieren del comparativo anterior. La limpieza independiente retiró
sólo su solicitud, verificó sesión vacía y desinstaló los tres bundles del control;
preservó Qüata y restauró el estado de los simuladores. Su salida correcta acredita
observación y limpieza, no aceptación funcional de Reply.

Como referencia Android, un piloto aislado API 35 con `NotificationFactory` real
mostró la acción de respuesta y abrió el editor de SystemUI asociado a su marcador.
El intento único y la limpieza independiente pasaron; PNG/XML y recibos privados
conservan el editor vacío, sesión vacía y ausencia posterior de la notificación.
No se escribió ni envió texto, no hubo fixtures ni verificación de backend y no se
acredita entrega FCM. El proceso del observador terminó antes de la limpieza;
el emulador candidato se cerró y el estable quedó intacto. Esta referencia acredita
la interacción Android observada, sin resolver la causa del resultado iOS ni dar
aceptación funcional multiplataforma a Reply.

El ensayo Android de envío `NotificationReplyProductInstrumentedTest` está preparado,
pero aún no ejecutado con backend. Usa el runner normal (`quataDeepLinkCustody=false`)
y exige `QuataApp`, actor exacto y sesión fresca; el APK de custodia se construye
por separado con el flag existente en `true`. El intercambio de APKs de tests se
hace con el proceso cerrado. El recibo de Send mantiene `backendVerified: false`;
`notification-reply-android-trial.mjs` conecta fixtures, sesión, envío, verificación
remota y reconciliación independiente. Sus pruebas de coordinación cubren también
incertidumbre y fallos de limpieza; no sustituyen la ejecución.
`notification-reply-android-channel.mjs` conecta el socket privado de sesión y los
pasos nativos: comprueba AVD, huellas de APKs y paquete de tests antes de instalar,
preserva los datos de Qüata y exige proceso cerrado también tras el sondeo final.
Un fallo anterior a `intent.json` sólo permite limpieza baseline tras acreditar
independientemente ausencia de registros y notificaciones; conserva el fallo.
El canal tiene revisión estática y contratos focales, pero sigue pendiente acreditar
el recorrido real con APKs actualizados e identidad integrada congelada.

Un fallo conserva directorio y journals para reconciliación antes de repetir.
Un éxito del ensayo completo acreditará sólo ese recorrido observado, sin sustituir
las comprobaciones de error, offline o reinicio que sigan pendientes.

El cambio de categoría aún no se ha desplegado al dispatcher remoto. Su despliegue
debe seguir el paquete focal revisado del [runbook APNs](IOS_APNS_PRODUCTION_REQUIREMENTS.md).
El [Personal Team local](IOS_LOCAL_DEVELOPMENT_SIGNING.md) no habilita APNs;
una notificación inyectada en el simulador no demuestra entrega desde Apple.
