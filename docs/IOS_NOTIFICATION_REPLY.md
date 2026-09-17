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
El recorrido de editor y Send nativos con sesión y conversación propias ya pasó.
El mensaje no apareció en backend, incluso conservando la app 25 segundos tras
Send: sigue pendiente acreditar la ejecución del
delegate y el transporte autenticado, la persistencia exacta y los estados de
éxito/error. Las pruebas simuladas no sustituyen esa aceptación funcional.
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
