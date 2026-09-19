# Respuesta desde una notificación iOS

Estado: implementación integrada en la [PR #339](https://github.com/dossijeo/quata/pull/339), merge `21e5706e`, con gates finales Web/Android, iOS y CodeQL SUCCESS. La aceptación focal de Reply en iOS Simulator quedó integrada y certificada por la [PR #358](https://github.com/dossijeo/quata/pull/358), merge `ee7a325b`; Web sigue pendiente para el cierre multiplataforma de `FLOW-NOTIFICATION-REPLY`. El GO iOS comprende el recorrido positivo y el negativo acotado observados; no acredita entrega APNs, dispositivo físico, firma ni distribución, estado HTTP exacto, traza negativa completa, offline, reinicio ni navegación posterior.

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
El ensayo autenticado 11 acreditó editor y Send nativos, delegate, transporte
autenticado y persistencia de un único mensaje exacto en la conversación propia,
además de retirada de la notificación y limpieza completa. Los intentos anteriores
sin mensaje permanecen registrados abajo. Este PASS acredita el recorrido positivo
observado en Simulator; no cubre aceptación de error, offline o reinicio ni entrega
APNs. Las pruebas simuladas no sustituyen esas comprobaciones pendientes.
Invocaciones separadas y reinicios de proceso no comparten la clave de reintento.

El ensayo UI opt-in `QuataIosNotificationReplyUITests` usa la app normal, el permiso
del sistema y el editor/Enviar nativos; Responder es opcional si el editor aparece
directamente. El paso
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
nativo de outcome compila. En los primeros intentos, la notificación inyectada
apareció, pero no se observó la acción «Responder» al mantener pulsados su texto
o su tarjeta. En esos intentos no se envió una respuesta y se reconciliaron,
incluidas la alerta propia, la sesión y las identidades/conversación desechables.

`testReconcileOnlyTheOwnedFailedNotification` pasó: exige actor, conversación,
marker y título propios, retira sólo el request ID correspondiente y verifica
ausencia. Sus metadatos de categorías describen el host después de relanzarlo;
no prueban retrospectivamente el registro durante el fallo de la UI.
El piloto inicial `testInspectReplyAffordanceWithoutSending` terminaba antes de pulsar Reply,
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

El piloto focal de banner del 16 de septiembre, basado en
[NotificationActionTest](https://github.com/aokj4ck/NotificationActionTest/blob/main/NotificationActionTestUITests/NotificationActionTestUITests.swift),
sí abrió directamente el editor en el simulador iOS 18.3. Antes de la entrega se
comprobaron en el host permiso, categoría y acción de texto con
`authenticationRequired`; XCTest dejó Home visible y el coordinador inyectó una
única alerta propia. La pulsación de 1,5 segundos sobre su `NotificationShortLookView`
produjo la expansión y el editor sin botón Responder intermedio.

El piloto2 con ese banner conserva un FAIL de selector: PNG/AX ya mostraban
el editor, pero el test lo buscaba en el contenedor de la tarjeta. La jerarquía real
lo sitúa en otra ventana de SpringBoard y también expone el cuerpo del aviso como
`TextView`. El helper compartido distingue el editor por su placeholder observado,
exige la alerta propia expandida única y vincula input/Enviar a su fila nativa.
El piloto corregido pasó sin escribir ni enviar; la reconciliación independiente
retiró sólo la alerta propia, verificó sesión vacía y cerró el candidato conservando
el estable. No hizo falta arrastre ni control con ratón. Los fallos anteriores sobre
Centro de notificaciones se conservan; no prueban ausencia de soporte de Reply.
Este resultado acredita apertura del editor, todavía no envío autenticado ni APNs.

El primer ensayo autenticado con el banner conserva otro FAIL de selector, antes
de Send: al escribir, SpringBoard dejó de exponer el placeholder `Mensaje`.
La captura muestra el texto sintético exacto y Enviar habilitado; la consulta
anterior ya no encontraba el editor. La reconciliación confirmó sólo el seed en
backend, retiró la alerta propia, borró la sesión exacta y cerró los fixtures.
El test vuelve a resolver el editor lleno por su valor exacto y único, excluye
`NotificationBody` y revalida la expansión propia, la fila input/Enviar y sus
anclajes horizontales antes del único tap. La apertura compartida con el piloto
no cambia. Este fallo acredita escritura, no envío ni aceptación de Reply.

El siguiente ensayo se detuvo antes de escribir: al enfocar el editor apareció
el menú nativo «Autorrellenar» y el contenedor informativo expandido dejó de ser
`isHittable`, aunque la grabación conserva la alerta visible. Se exige a ese
contenedor identidad única, marcador propio y límites dentro de pantalla; editor
y Enviar siguen requiriendo interacción posible y la misma fila. Sus anclajes se
capturan antes de enfocar. La segunda reconciliación también verificó sólo el seed,
retiró la notificación propia y cerró sesión y fixtures. Se conserva el FAIL.

El ensayo del merge `37f537855f7f1f4abde30c6f2e4223687fdc5591` pasó el XCTest
de interacción en Simulator iOS 18.3: banner propio, expansión, editor, texto
sintético exacto verificado y un solo Send. Capturas y jerarquías conservan cada
paso. La app mantuvo el mismo binario; sólo cambió el runner de UI.
El coordinador falló después con `notification_reply_message_missing`: no encontró
el mensaje en 30 segundos y la auditoría posterior confirmó sólo el seed.
No se atribuye ejecución del delegate, transporte autenticado ni aceptación
funcional a ese PASS de UI. No se repitió Send.

Una lectura nativa independiente confirmó ausencia actual de notificación para
la ruta y sesión vacía tras relanzar el host; no prueba éxito de Reply. Con el
productor cerrado, las guardas existentes volvieron a auditar destinos y seed
dentro de la transacción de limpieza, retiraron sólo los fixtures propios y
verificaron su ausencia antes de retirar los journals. El candidato quedó apagado
y el estable conservó su estado. La evidencia privada está en
`build-reports/ios-notification-reply/ios-banner-authenticated3/`; el FAIL funcional
y los fallos previos se conservan. No cambió producto, firma ni proveedor APNs.

El diagnóstico posterior del mismo xcresult encontró SIGTERM del proceso de la
app al terminar la suite, pocos segundos después de Send. El runtime tiene un
presupuesto de 20 segundos: el ensayo anterior no conservó esa ventana completa.
No demuestra por sí solo la causa del mensaje ausente. XCTest mantiene ahora una
observación monotónica de 25 segundos tras el único Send, acredita que el proceso
sigue presente y conserva capturas y estados. Suspensión no equivale a ejecución
del callback; la aceptación sigue exigiendo el mensaje exacto. No cambian los
límites globales del coordinador ni la espera backend y no se reutiliza el intento.

El nuevo ensayo independiente `1f28098e-4c6d-43cf-a23f-b3b1d089f710`, sobre el
merge `935a718990254a5ccf5d86ca10fd10a781dbbffb`, pasó la interacción nativa
(144,867 s): texto exacto, un Send y observación de 25,201 s con la app presente
(estado XCTest 3, background). Capturas y jerarquías acreditan el editor antes de
Send y Home después. Volvió a fallar con `notification_reply_message_missing`;
la auditoría posterior encontró sólo el seed. El teardown inmediato no basta para
explicar el fallo. No se acredita todavía callback, transporte ni aceptación.

La comprobación independiente tras relanzar el host confirmó ausencia actual de
la alerta y sesión vacía; no es prueba retrospectiva de éxito del delegate. Se
reconciliaron los fixtures propios con auditoría transaccional de destinos,
sin repetir Send. El candidato quedó apagado y el estable conservó su estado.
Evidencia privada: `build-reports/ios-notification-reply/ios-banner-authenticated4/`.
Se conservan todos los FAIL anteriores. La observación de vida del proceso es un
ajuste del ensayo, no una corrección del producto ni un cierre funcional.

El análisis posterior sólo leyó registros existentes; no lanzó otro intento.
Los logs unificados del simulador acreditan a las 18:11:28 UTC la construcción
por SpringBoard de `UNTextInputNotificationResponse`, con `QUATA_CHAT_REPLY` y el
texto exacto. El proceso Qüata recibió `UINotificationResponseAction`; a las
18:11:29,868 devolvió `BSActionErrorDomain` 4 (`empty-response`). Ese resultado de
UIKit no identifica por sí solo un rechazo de nuestro handler. El binario fijado
incluye el selector Objective-C correcto y las implementaciones de delegate,
`handleReply`, instalación y handler; su ejecución sigue sin estar acreditada.

Los logs HTTP del servidor cubren 18:05–18:15 UTC: 38 eventos de API con ruta y
ninguno de `quata_chat_send_message`. Cuatro RPC anteriores al Send devolvieron
200 con el actor, la sesión y el cliente iOS del ensayo. Eso acredita la sesión
en esas peticiones, no su estado exacto al Send. La frontera pendiente queda entre
la recepción en el proceso y la llegada de la petición de envío al servidor;
no distingue todavía callback omitido, rechazo local o fallo de red. Se conservan
extractos sanitizados y consultas de cobertura en la misma carpeta privada.

Un preflight instrumental posterior, sin push ni backend, se detuvo antes de
adjuntar LLDB: la ruta del proceso no coincidía con el contenedor devuelto por
`simctl get_app_container`. Los logs identifican dos contenedores distintos; el
del proceso ya no existía al inspeccionarlo. El UUID del ejecutable registrado
coincide con el ejecutable fijado, pero no acredita la biblioteca de producto
cargada ni explica el fallo de Reply. No hubo nueva respuesta ni evidencia de
callback. Se conserva el FAIL en
`build-reports/ios-notification-reply/ios-reply-trace-preflight1/`, junto con la
reconciliación independiente de todos sus procesos y del plan temporal: candidato
apagado, estable intacto y reserva exclusiva recuperada. La sesión vacía se
comprobó antes; no se atribuye una comprobación posterior que no se ejecutó.

El preflight instrumental `ios-reply-trace-early14` pasó posteriormente sin push,
Send ni backend: verificó el proceso y las imágenes cargadas, resolvió los tres
símbolos de Reply y observó como control positivo el callback existente de entrada
en background. El observador se desconectó antes del cierre de XCTest; sesión
vacía antes/después, candidato apagado y estable conservado. Ese control acredita
la observación pasiva, no la ejecución del delegate de Reply.

La tentativa `919e448e-bfdb-4220-bb8b-9f6189b354a3`, sobre el merge
`5777f1e22d1cf8b8fac46b48e73d781307aa02d8`, se detuvo antes de inyectar:
la señal de Home listo superó la guarda de frescura de cinco segundos. No hubo
push ni Send y XCTest terminó interrumpido. No es un fallo de expansión ni prueba
de ausencia de la acción. Los contadores Reply permanecieron en cero y el control
positivo en uno; ambos grupos de procesos quedaron cerrados.

La recuperación inicial usó incorrectamente el test que exige una alerta inyectada
y falló al encontrar cero. Después se ejecutó una sola vez el clear exacto y
pasaron los tests existentes de sesión vacía y ausencia de notificación. El
coordinador rechazó inicialmente ese recibo por comparar el orden de claves JSON;
la reconciliación comparó sus claves y valores sin repetir las pruebas. Se
conservan ambos fallos. La auditoría encontró únicamente el seed y retiró los
fixtures propios con las guardas transaccionales existentes. Evidencia privada:
`build-reports/ios-notification-reply/ios-banner-authenticated5/`.
El resultado sigue siendo FAIL con limpieza completa, no aceptación de Reply.
No se modificaron producto, firma, binarios ni proveedor para este diagnóstico.

El preflight `ios-reply-trace-early15` comprobó después la continuidad del PID ya
vinculado mediante inicio del proceso y ruta del candidato, sin redescubrirlo con
`launchctl`. Las consultas duraron 19–28 ms y Home listo tenía 0,042 s de antigüedad.
Se mantuvieron la guarda de cinco segundos y el presupuesto total de 240 segundos.
Pasó sin push, Send ni backend, con control positivo, sesión vacía antes/después y
cierre de los grupos propios. No acredita por sí solo el recorrido Reply.

El ensayo `e5f4a22b-311b-4e01-a057-b8908ba2bcdb`, sobre el merge
`913bea2043da703ff453fe59a4953f9a6b8107eb`, pasó la interacción con un único Send.
El observador registró una entrada en el delegate Objective-C y una en
`handleReply`, frente a cero antes de la inyección. No inspeccionó argumentos ni
invocó handlers. No registró entrada en el puente de envío de Chat, pero la
comprobación final acabó a los 33,363 s, con app y XCTest ya cerrados: la cobertura
es incompleta y ese cero no demuestra ausencia durante toda la ventana.
El backend volvió a contener sólo el seed; el mensaje exacto no se observó.
La sesión se retiró una vez y los diagnósticos existentes confirmaron después
sesión vacía y ausencia actual de notificación, sin atribuirles éxito de Reply.
La reconciliación retiró los fixtures propios tras auditar el baseline y los
destinos dentro de la transacción. El ensayo queda FAIL con limpieza completa.
Evidencia privada: `build-reports/ios-notification-reply/ios-banner-authenticated6/`.

El diagnóstico siguiente, `27f3a857-d527-4f53-a30f-058eb5784ff7`, reutilizó los
binarios sobre el merge `dbf1c35c77ce1ddaa52a1d2803c86762c995748a`. Añadió sólo
cuatro contadores privados, resueltos contra la huella y UUID del binario, para
separar rechazo de guardas, llamada al handler y llegada al puente Swift del
runtime. La
preparación estática y 27 contratos focales pasaron con revisión independiente.
No se inspeccionaron argumentos ni se invocaron handlers.

El helper volvió a abrir el editor, pero XCTest falló antes de Send al encontrar
cero nodos con el texto sintético exacto después de `typeText`. No hubo marcador
de envío ni entradas en los puntos Reply; el control de segundo plano sí pasó.
Esto no acredita envío ni localiza una guarda defectuosa. El observador terminó
con cobertura incompleta y cierre acreditado de sus grupos. El `.xcresult`
conservado carece de `Info.plist`: se conserva el bundle y el log,
pero `xcresulttool` no pudo exportar los adjuntos para distinguir escritura de
selector. No se atribuye a este intento evidencia visual exportada.
La recuperación posterior retiró únicamente la alerta propia, completó el clear
ya registrado y acreditó el cierre nativo. La auditoría encontró sólo el seed;
se retiraron hilo, cuentas sintéticas y journals con las guardas existentes.
El ensayo conserva FAIL, ahora con limpieza completa y sin repetir Send.
Evidencia privada: `build-reports/ios-notification-reply/ios-banner-authenticated7/`.

El ensayo `17707780-fd0a-4ff3-865c-819565df4c50`, sobre el merge
`75827caebe84fa4f659a53bed6b40bc5f64e2cf0`, volvió a pasar la interacción nativa:
texto exacto comprobado y un único Send. Se exportaron 19 adjuntos del XCTest,
incluidas las capturas y jerarquías antes/después de abrir el editor y enviar.
El observador registró una entrada en el delegate, una en `handleReply` y una en
la salida común de rechazo de sus guardas, frente a cero antes de la inyección.
No registró llamada al handler ni entrada en el puente Swift del runtime. Cerró
a los 11,751 s desde el marcador de envío, con app y XCTest vivos y cobertura
verificada. La ejecución de esa salida acredita rechazo de alguna guarda, sin
identificar cuál de las seis condiciones falló. No acredita transporte.
El mensaje exacto tampoco apareció en backend; el resultado funcional sigue FAIL.
El clear original pasó una vez y el diagnóstico posterior confirmó sesión vacía
y ausencia actual de notificación, sin repetir envío ni clear. La reconciliación
confirmó sólo el seed y retiró hilo, cuentas sintéticas y journals propios;
el ensayo conserva FAIL con limpieza completa.
Evidencia privada: `build-reports/ios-notification-reply/ios-banner-authenticated8/`.

El ensayo `eacb4abc-d3b6-44e3-bcd2-f46292f97db2`, sobre el merge
`0955a843745185f2e4044fc03d45952fb1851875`, pasó nuevamente la UI con texto exacto,
un único Send y 19 adjuntos exportados. La traza registró específicamente
`reject_missing_target`, además de la salida común de rechazo; las condiciones
anteriores de tipo, texto y destinatario habían pasado. La comprobación final
terminó a los 11,665 s con app y XCTest vivos. El adaptador devolvió un destino
nulo antes de llamar al handler: todavía no acredita transporte ni mensaje.

Una reproducción aislada de las funciones actuales de normalización en Kotlin/JVM
mostró que `putAll(toNotificationStringValues())`, dentro de `buildMap`, toma el
builder como receptor y pierde los campos raíz, incluido `conversation_id`.
Conserva los campos anidados y también pierde la precedencia de la raíz cuando
hay conflicto. El receptor explícito recuperó esos tres casos en la reproducción.
Este diagnóstico usa mapas de strings y no certifica Kotlin/Native ni Reply;
En aquel momento faltaba validar el adaptador real corregido y el recorrido
autenticado completo. La reparación se integró después separada del ajuste de interacción.
El ensayo conserva FAIL por mensaje ausente. Clear original una vez, diagnóstico
de sesión vacía/alerta ausente y reconciliación de baseline, hilo, cuentas y
journals completados; no hubo replay. Evidencia privada:
`build-reports/ios-notification-reply/ios-banner-authenticated9/`.

La reparación del receptor explícito del adaptador se mergeó en la PR #340
(`2f313d8d47fc70e38a4d58914d7ff3007a503966`). Cinco pruebas del adaptador real en
Kotlin/Native dieron cuatro fallos antes de corregirlo y cero después, también
sobre el merge sintético. Cubren campos raíz, envelopes anidados, precedencia,
fallback y destino ausente. La revisión independiente y los contratos pertinentes
pasaron; no se modificaron firma, capacidades, CI ni dispatcher.

La preparación del nuevo binario conserva dos fallos anteriores al ensayo nativo:
directorio privado de informes ausente (`ios-reply-trace-fixed1`) y dependencia
privada `watchdog.py` ausente (`ios-reply-trace-fixed2`). Ambos quedaron reconciliados
sin push ni Send. El preflight `ios-reply-trace-fixed3` pasó con permiso, categoría,
acción de texto con `authenticationRequired`, sesión vacía y control del observador;
cerró antes de entregar una alerta. El intento `ios-banner-authenticated10` falló
antes de abrir el canal por la raíz no admitida, sin fixtures ni envío. Se copiaron
los productos compilados a la raíz operativa ya admitida, verificando sus bytes.
El sondeo `supported-channel-probe1` conserva su fallo de consulta del contenedor
después del apagado esperado; la verificación complementaria acreditó los dos
tests nativos pasados, la app instalada idéntica y el cierre completo, sin repetirlos.

El ensayo `4c93f5bc-be14-4849-b9a7-10aabc3a0a12`, paso
`55525ecd-60f4-46df-8fba-307917c83eab`, pasó sobre el merge de la PR #339
`34c4778ec0379ff2313a16990f006fd66ad9358c` (head
`f29a9aae752586869919a44fa2dd85825edc92e8`). Reutilizó los productos compilados
en `1e44d7450d0dc5f67692a22a9ee13b45d48ae145`, con árbol completo idéntico al merge
ensayado y huellas de fuentes, productos y helpers congeladas antes del ensayo.
No se repitió una matriz por el cambio exclusivo de SHA.

Con Home visible, Centro de notificaciones cerrado y observador preparado, la
alerta propia apareció como `NotificationShortLookView`. El mismo helper del piloto
abrió directamente el editor tras pulsar el banner; no necesitó Responder, arrastre
ni control de ratón. XCTest verificó el texto sintético exacto antes de un único
Send. Se exportaron 19 adjuntos, con capturas y jerarquías de Home, banner,
editor y antes/después del envío. La traza pasiva registró una entrada en delegate,
`handleReply`, despacho al handler y puente del runtime, con cero rechazos de
guardas; cerró a los 11,096 s con app y XCTest vivos y cobertura completa.
No inspeccionó argumentos ni fabricó una respuesta nativa.

El coordinador confirmó un mensaje exacto, actor y conversación propios y unicidad,
incluida la observación posterior al resultado nativo y la auditoría de limpieza.
La notificación propia se retiró; hilo, cuentas, sesiones y journals quedaron
limpios. Los recibos parciales de UI y traza conservan respectivamente
`backendVerified: false` y `transportVerified: false`: la acreditación backend
procede del informe del ensayo completo, no de esos observadores. Resultado
`passed`, `cleanupComplete: true`, `traceVerified: true`; entrega Apple no certificada.
Evidencia privada: `build-reports/ios-notification-reply/ios-banner-authenticated11/`.

La preparación del ensayo negativo reutiliza el coordinador con
`expectedOutcome: 'server-rejected'`; el recorrido positivo sigue siendo el
predeterminado. El bloqueo temporal se limita a peer → owner del hilo sintético,
con intención durable antes de insertarlo, baseline de un único seed del peer y
cero mensajes del owner. Se conserva hasta cerrar productor, sesión y transporte;
su retirada exige ID y tupla exactos, ausencia de mensajes nuevos y posterior
limpieza del hilo y cuentas propias. Una operación incierta conserva la custodia.

El nuevo XCTest observa el aviso realmente entregado, exige ruta propia, contenido
de fallo y categoría sin otro Send inline. Un paso separado vuelve a comprobarlo
y retira sólo ese aviso; no puede repetirse dentro del worker tras iniciar el clear.
El coordinador comprueba ausencia del mensaje antes y después. Su alcance declarado
es `message-absence-delivered-failure-and-cleanup`: incluso un PASS de ese ensayo
no acredita visibilidad en SpringBoard, retorno al chat, estado HTTP concreto,
reintentos ni offline. La compilación Swift y los contratos de preparación pasaron;
la aceptación negativa real y esas comprobaciones siguen pendientes. No cambia la
acción de producto, sus entitlements, firma, backend desplegado ni los fallos previos.

El modo privado con observador incorpora una barrera opt-in antes del único Home,
compartida por piloto y ensayo real. Dentro del presupuesto existente, XCTest
publica su espera y consume una autorización atómica ligada a run, step y marcador;
el coordinador sólo la publica con proceso vigente y los 18 puntos resueltos.
El control de segundo plano se exige después de Home. Se conservan la aserción de
estado, las comprobaciones de permiso/categoría y el helper nativo del editor.

Los preflights `ios-reply-trace-negative2` y `ios-reply-trace-before-home1`
fallaron antes de inyectar: Home mostraba iconos, pero XCTest informó foreground.
La barrera eliminó el solapamiento observado con attach, sin resolver por sí sola
el fallo; el segundo trace registró una parada no verificada del observador.
El diagnóstico posterior `ios-reply-trace-event-diagnostic1`, sobre el commit local
`26479e5ae4f847be0e517d76ce234affdc7aef3c`, pasó: 18 puntos resueltos, control de
segundo plano una vez, los otros contadores a cero y observador/custodia cerrados.
No hubo inyección, Send ni efectos backend. El fallo no se reprodujo; no se atribuye
su reparación a la recogida de metadatos. Este PASS sólo permite preparar el
ensayo negativo con ese paquete exacto; no acredita rechazo, reintentos ni entrega.
La compilación corresponde a la base privada `1e44d745` con los overlays de los
XCTest de outcome y UI, cotejados con el commit local; no se presenta como un build
limpio de un merge de GitHub. Los productos y fallos anteriores se conservan.

El ensayo `ios-banner-negative-authenticated2`, preparado contra el merge
`0ab324e08f5fc58ea8cf86192413b016b8b3a31f` (head `19b26167`), falló antes de
inyectar: no existe intención de push ni recibo de Send. La traza registró una
parada por señal, sin recoger su número; no establece su causa ni acredita un
fallo de expansión o ausencia de Reply. El control de segundo plano se registró
una vez y todos los contadores de Reply quedaron a cero. No se repitió el ensayo.

El fallo inicial conservó custodia y fixtures. Su reconciliación independiente
acreditó cierre de los procesos propios y que el clear original no se había
despachado. Ese mismo clear pasó; las comprobaciones nativas confirmaron sesión
vacía y ausencia de la notificación propia. Tras verificar únicamente el seed y
cero mensajes del owner, se retiraron el bloqueo exacto, hilo, cuentas y sesiones;
los journals se eliminaron tras verificar ausencia. Resultado final `failed`,
`cleanupComplete: true`, sin aceptación negativa. Se preservan el informe previo,
la traza y los recibos de recuperación; el simulador estable quedó preservado.
La aceptación positiva del ensayo 11 no se repitió ni se amplió: rechazo,
reintentos, offline y resultado visible de error siguen pendientes.

El log XCTest de ese fallo confirmó Home visible seguido de una lectura de estado
foreground. Como [XCTest actualiza `state` de forma asíncrona](https://developer.apple.com/documentation/xcuiautomation/xcuiapplication/state-swift.property),
el commit local `5889f40d6bad2c1e3d39b502a1fb40d6c5764c64` espera explícitamente
background activo o suspendido antes de entregar. Comparte los diez segundos de
espera con la comprobación de Home, sin otro gesto; las consultas síncronas de
accesibilidad no quedan limitadas por ese plazo. No acepta una app cerrada.

`ios-reply-trace-background-state1` volvió a fallar por timeout de ese estado,
con control de segundo plano una vez. El diferencial puntual
`ios-reply-background-noobserver1`, con los mismos productos y XCTest pero sin
adjuntar el observador ni activar su barrera, alcanzó `ready-for-notification`.
Ambos terminaron limpios, con sesión vacía antes/después y simulador estable
preservado, sin inyección, Send ni fixtures backend. El diferencial se detuvo
antes de la entrega: acredita sólo el estado previo, no un PASS del XCTest
completo ni aceptación Reply. Es compatible con interferencia del observador,
sin demostrar causalidad. Se conservan ambos resultados; no se declara resuelto
el fallo con observador ni se repite el ensayo positivo.

El intento `ios-banner-negative-canonical1`, sin wrapper LLDB y contra el merge
`6ab0a3970f068064fcdfb03dd804d5a8c51220af` (head `e6fee04b`), abrió el editor,
comprobó el texto y realizó un único Send. Después falló la aserción que exigía
presencia del productor durante la ventana posterior al envío. No se repitió.
La recuperación observó mediante XCTest el aviso real de fallo del hilo propio;
otro paso lo retiró y verificó su ausencia. El clear original de sesión pasó,
los procesos quedaron cerrados y la auditoría confirmó sólo el seed, sin mensajes
del owner, antes de retirar bloqueo, hilo, cuentas y journals. Resultado
`failed`, `cleanupComplete: true`; los diagnósticos no convierten el ensayo en PASS
ni explican la salida del proceso. El bundle original quedó incompleto, sin
`Info.plist`: se conserva con su log, pero no se pudieron exportar sus adjuntos.
La aceptación de error/reintento sigue pendiente; no se atribuyen contadores,
HTTP ni continuidad del proceso a este intento sin observador.

El intento `ios-banner-negative-canonical2`, sobre el commit local `d3617906`,
compiló una observación posterior a Send que conserva los 25 segundos y registra
estados sin teardown anticipado por ausencia del proceso. No llegó a ejercitarla:
falló el waiter de background antes de inyectar, sin alerta ni Send. El log sitúa
Home en 25,22 s y las consultas de iconos hasta 34,55 s, dentro del plazo compartido
de diez segundos; no registra el estado efectivo de la app al fallar. Se exportaron
siete adjuntos y dos fotogramas revisados muestran el host y después Home, sin
acreditar estado background. Se reconcilió el clear original y se verificaron sesión
vacía, ausencia de alerta, cierre nativo, baseline de un único seed y retirada de
bloqueo, hilo, cuentas y journals. Resultado `failed`, `cleanupComplete: true`.
La modificación de observación permanece local, sin validación de runtime ni
publicación; este intento no amplía la aceptación positiva ni negativa.

El diagnóstico anónimo `ios-reply-home-order1` registró estado background
(`stateRaw: 3`) y presupuesto restante cero tanto al entrar como al salir del
waiter. La primera consulta de iconos ya había consumido el plazo antes del
waiter; el registro no permite determinar cuándo cambió el estado de la app.
Falló antes de entregar y terminó con limpieza completa. Se conserva ese fallo.

El ajuste local posterior comprueba background antes de cualquier consulta de
iconos, con el mismo Home único y plazo compartido de diez segundos. Los preflights
anónimos `ios-reply-background-first1` y `ios-reply-trace-background-first1`
alcanzaron la barrera previa a entrega con los mismos productos; el segundo
registró un control de segundo plano y cero eventos Reply. Ambos terminaron
con sesión vacía, candidato detenido, transporte cerrado y simulador estable
preservado, sin inyección, Send ni fixtures backend. Sus capturas posteriores
revisadas muestran Home desbloqueado y Centro de notificaciones cerrado.
Son PASS del preflight, no del XCTest completo, detenido intencionalmente antes
de inyectar. Acreditan background en la comprobación y Home después, sin demostrar
estado continuo ni simultáneo. Las consultas síncronas pueden consumir más tiempo
que el timeout solicitado. La observación posterior a Send sigue sin ejercitarse
en estos preflights; el reintento real y el resultado negativo siguen pendientes.

El ensayo `ios-banner-negative-observed-retry1` abrió el editor directamente desde
el banner y escribió el texto sintético completo, pero agotó los 240 segundos del
coordinador antes de Send. El log termina en consultas del editor y su contenedor
a los 208,55 s de XCTest; el arranque previo también consume ese presupuesto.
La captura posterior a la interrupción, revisada, muestra la alerta propia, el
texto completo y Enviar. No consta Tap en Enviar ni fase de envío; la traza cerrada
registra un control y cero eventos Reply. El xcresult quedó incompleto y se conserva;
esa captura posterior no sustituye sus adjuntos previos sin exportar.
La recuperación retiró únicamente la alerta inyectada mediante el test existente,
verificó el clear original, sesión vacía, ausencia de notificación y cierre nativo,
y retiró bloqueo, hilo y cuentas tras comprobar sólo el seed. Los journals quedaron
vacíos. Resultado `failed`, `cleanupComplete: true`: sin aceptación de reintento ni
ejercicio de la observación posterior a Send. No se modificaron plazos ni gestos.

Los pilotos locales de geometría conservan sus resultados separados. `ios-banner-geometry-pilot1` falló antes de iniciar UI o inyectar; no se recuperó la excepción exacta. `ios-banner-geometry-pilot2` falló en la comprobación de background antes de entregar. Ambos quedaron reconciliados. Tras recuperar capacidad de instalación, `ios-banner-geometry-pilot3` abrió el editor vacío directamente mediante una pulsación de 1,5 segundos sobre el banner propio y terminó limpio, sin escribir ni enviar. No acredita por sí solo el envío ni atribuye causalidad temporal a la capacidad recuperada.

`ios-banner-negative-observed-retry-geometry1` volvió a fallar antes de inyectar, en el waiter de background, con limpieza completa. El xcresult incompleto no conservó las muestras necesarias. El diagnóstico posterior guarda las muestras ya obtenidas, sin nuevas consultas de estado ni cambios de plazo. `home-diagnostic-preflight`, sobre `60ecb394`, alcanzó la barrera de entrega con el observador activo, recibo ligado al run/step, un control y cero eventos Reply; terminó limpio sin inyección, Send ni fixtures backend. Es un preflight, no un PASS del XCTest completo, detenido intencionalmente antes de entregar.

`home-diagnostic-negative-run`, también sobre `60ecb394`, abrió el editor desde el banner, verificó el texto y pulsó Enviar una vez. Alcanzó la observación posterior de 25 segundos, pero el proceso sufrió `NSInternalInconsistencyException` con el motivo «Call must be made on main thread». La pila sitúa el fallo en el completion de `UNUserNotificationCenter.add`, ejecutado en la cola call-out y conectado al completion de UIKit. No demuestra en qué hilo empezó el callback del transporte. El observador registró rutas de reintento y error, pero su cobertura quedó incompleta y no hubo PASS terminal del XCTest; el ensayo sigue siendo FAIL. La recuperación comprobó el aviso de fallo realmente entregado, ejecutó el clear original, retiró sólo ese aviso y verificó ausencia, sesión vacía y cierre nativo. La auditoría confirmó sólo el seed y cero mensajes del actor; bloqueo, hilo, cuentas y journals quedaron retirados.

La corrección local `f5bd1d82` remite al hilo principal únicamente el completion de `center.add` en la rama de fallo. La compilación y el XCTest focal que simula un callback desde una cola secundaria pasaron, con cierre completo. Esto valida el callback, no el caso negativo nativo completo: su aceptación sigue pendiente. El ensayo positivo previo se conserva sin repetición. No cambian firma, authenticationRequired, plazos, transporte ni contenido del aviso.

El coordinador iOS reutiliza las guardas de fixtures: espera el seed push sin
destinos antes de instalar la sesión, verifica la exclusión del remitente en el
dispatcher fijado y audita el peer sin sesiones ni destinos antes de Send y dentro
de la transacción de limpieza. Esa auditoría no declara terminado el trigger de
Reply; un envío o cierre incierto conserva los journals.

El ensayo Android de envío `NotificationReplyProductInstrumentedTest` usa el runner
normal (`quataDeepLinkCustody=false`)
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
El primer ensayo autenticado sobre el merge integrado
`5cb8bf9f82ab002c0b5b06725993914fac222054` abrió el editor según UiAutomator, pero
falló al comprobar su nodo de accesibilidad antes de escribir texto o crear la
intención de Send. Se conservaron el fallo, la captura previa y la ausencia de
archivos de envío; la reconciliación nativa y el borrado de sesión pasaron. Una
reconciliación independiente confirmó sólo el mensaje seed y retiró los fixtures
propios con las guardas existentes. No acredita envío, backend Reply ni FCM.
El segundo ensayo, sobre el merge integrado
`77c8fd2b807506891f445f92f80b1fc06f9786bd`, consultó las ventanas interactivas con
la conexión de UiAutomator y volvió a fallar antes de escribir o enviar. PNG/XML
muestran el editor vacío; la consulta filtrada devolvió cero nodos, sin aislar si
falló la búsqueda por ID o la comparación de geometría. Los fixtures se retiraron
tras verificar nuevamente que sólo existía el seed. No acredita Reply.
El diagnóstico posterior sin backend se detuvo antes de crear su intención: había
persistido en disco el ID exacto de aquella notificación, aunque la reconciliación
nativa había observado las preferencias vacías en memoria. Su recibo no acredita
persistencia de esa limpieza. Una reparación limitada a ese ID pasó con `commit`
síncrono y lectura independiente del conjunto vacío en disco tras cerrar el proceso.
El nuevo piloto sin envío encontró un editor visible con geometría exacta y hint
confirmado mediante recorrido público del árbol; la búsqueda por ID seguía vacía.
Su captura también conserva una tarjeta histórica debajo del editor. La ausencia
en `activeNotifications` y en preferencias no acredita desaparición visual de esa
tarjeta; falta verificarla antes de otro ensayo autenticado. El candidato Android
quedó cerrado y no se atribuye aceptación funcional a estos diagnósticos.

Un fallo conserva directorio y journals para reconciliación antes de repetir.
Un éxito del ensayo completo acreditará sólo ese recorrido observado, sin sustituir
las comprobaciones de error, offline o reinicio que sigan pendientes.

El cambio de categoría aún no se ha desplegado al dispatcher remoto. Su despliegue
debe seguir el paquete focal revisado del [runbook APNs](IOS_APNS_PRODUCTION_REQUIREMENTS.md).
El [Personal Team local](IOS_LOCAL_DEVELOPMENT_SIGNING.md) no habilita APNs;
una notificación inyectada en el simulador no demuestra entrega desde Apple.

El intento negativo `main-completion-negative-run`, con los productos de la corrección
local de completion, se detuvo antes de Send: XCTest sintetizó el texto, pero la
guarda exacta encontró cero coincidencias y no consta un tap en Enviar. La jerarquía
cruda recuperada del adjunto posterior a la escritura expone el editor enfocado con
`value: q`, no el marcador completo. El bundle de resultado está incompleto; esta
evidencia no distingue entrada truncada de una diferencia de representación de
accesibilidad y no acredita envío, rechazo ni reintento. El intento continúa como
FAIL; la reconciliación posterior completó el clear, comprobó ausencia de la
notificación, cierre nativo y baseline sin mensajes del owner, y retiró bloqueo,
hilo, cuentas y journals. El resultado mantiene `cleanupComplete: true` y
`observationAccepted: false`; no amplía la aceptación positiva ni negativa.

El ensayo negativo `editor-capture-negative-run` (`fe65ab3d-7dc6-41b9-bcce-33b5c856699c`) terminó `passed` con el XCTest nativo de Reply: verificó el marcador sintético exacto, pulsó **Enviar** exactamente una vez y completó la observación posterior. Las copias persistentes de la captura y la jerarquía con el marcador completo antes de verificar el texto se inspeccionaron y están ligadas al mismo run/step. La traza ligada al mismo PID registró ejecución real de delegate, guard y runtime; su prueba acotada sólo acredita los mínimos `attempt >= 3` y `delay >= 2` (también delegate, guard y runtime `>= 1`).

El alcance negativo observado fue ausencia del mensaje propio, aviso de fallo realmente observado y clear posterior de ese aviso. La reconciliación confirmó la retirada de bloqueo e hilo, cuentas y journals propios, con limpieza terminal. El positivo `auth11` se preserva y no se repitió.

Estos hechos no convierten la cobertura acotada en una traza completa: `fullTraceVerified`, HTTP, rechazo del servidor, APNs, offline, navegación y los campos de fallo de UI del sistema permanecen `false`. No se infiere conteo exacto de reintentos, estado HTTP, cobertura completa ni entrega de Apple a partir de los mínimos observados.
