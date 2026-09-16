# FLOW-PUSH-LIFECYCLE — validación iOS en Simulator

Decisión del propietario, 15 de septiembre de 2026: no habrá dispositivo iOS físico.
Su ausencia no bloquea este flujo. Completar todo lo verificable en Simulator,
probar APNs/provider por separado e intentar el registro APNs real del simulador.
Esta decisión no convierte una inyección de payload en entrega del proveedor ni
certifica distribución en App Store/TestFlight.

## Evidencia separada por frontera

| Frontera | Comprobación | Qué acredita |
| --- | --- | --- |
| Recepción y presentación | `xcrun simctl push <UDID> com.quata.ios <payload.apns>` con payload de prueba aprobado | Entrada del payload por el sistema del Simulator; presentación y manejo por la app. |
| Interacción | Foreground: comprobar política de presentación equivalente a Android y continuidad de Chat. Background/terminada: tap en la notificación presentada. | Restauración, ruta y mensaje exactos; ausencia de duplicación visible. |
| Permisos y sesión | Denegar/conceder permisos, login, cambio de actor y logout con perfiles de prueba propios | Comportamiento de permisos y aislamiento del destinatario; registrar por separado cualquier frontera backend no ejecutada. |
| Registro APNs | Invocar el registro real y observar callback de éxito o error, sin exponer token | Capacidad real del entorno para obtener token; no deducirla de `simctl push`. |
| Proveedor | Contratos de firma/payload/errores y pruebas independientes del endpoint APNs | Distinguir transporte HTTP/2, autenticación del proveedor y entrega a token válido. Cada resultado conserva su alcance. |

Usar el simulador candidato con lease exclusivo, SHA y configuración congelados;
preservar el simulador estable. Los payloads temporales no contienen credenciales,
tokens ni contenido de terceros. Los mensajes y perfiles usados son los fixtures
autorizados, con snapshot y limpieza verificables. No publicar el payload completo.

Si la virtualización impide obtener un device token, conservar esa limitación
explícita y continuar recepción, interacción y proveedor. Registrar el error real:
no atribuir a virtualización un fallo de entitlement, cuenta, red o configuración.
No recrear certificados ni exigir un iPhone para resolver esa limitación del entorno.

El cierre de FLOW-PUSH-LIFECYCLE debe enumerar las pruebas realizadas y cualquier
frontera externa no acreditada. La falta de dispositivo físico no es un gate del
flujo. Los requisitos de firma y distribución de producción permanecen separados
en [IOS_APNS_PRODUCTION_REQUIREMENTS.md](IOS_APNS_PRODUCTION_REQUIREMENTS.md).

## Evidencia focal ejecutada — 15 de septiembre de 2026

PASS del caso **app terminada, destinatario autenticado y mensaje exacto**:
Login nativo real con fixtures propios, verificación de la sesión contra Auth/backend,
inyección única mediante `simctl push`, tap en el aviso visible, apertura del mensaje
esperado y Back a la lista de Chats. XCTest y coordinador terminaron con código 0;
la sesión, el hilo y los perfiles se retiraron con ausencia verificada. Las capturas
y el árbol de accesibilidad conservan los tres estados de la interacción.

- Producto congelado: `fa72792e48bcf2f2ef30d4878ca2b8ebc346aa2f`.
- SHA-256 del bundle: `7be9ff6d095e07366b004de662d022637a5da8953ee7d7364aa9af2ce5198e0a`.
- Ejecución: `69bd1647-c578-4de3-8be1-dc2db86fd255`; paso push: `6c092a1b-6ff6-415f-8ada-4a7132cefff4`.
- Informes privados del host: `build-reports/flow-push-lifecycle/owned-chat-trial-4b3d3cf3-30a0-479f-b4a6-def0d6928cd1` y `owned-push-6c092a1b-6ff6-415f-8ada-4a7132cefff4` bajo el mismo directorio.

El ensayo separado de **background y foreground** terminó con XCTest y coordinador
en código 0 y limpieza completa. Comprobó estado de segundo plano antes del tap,
apertura del mensaje exacto, continuidad de Chat sin banner observado durante la
inyección en primer plano y Back a la lista. La observación de primer plano se armó
antes de la inyección y cubrió su confirmación y una ventana posterior; no acredita
por sí sola un callback de la app ni entrega del proveedor.

- Mismo producto y bundle congelados; integridad posterior idéntica al preflight.
- Ejecución: `1faa2355-395b-4e8e-a790-22c0a9ea423d`; paso push: `3900daaf-2ba0-45f6-8636-88f66f7b865d`.
- Informes privados bajo `build-reports/flow-push-lifecycle/`: `bgfg2-chat-trial-c16f92ec-c0a9-433a-b001-0962b5cf765a` y `bgfg2-push-3900daaf-2ba0-45f6-8636-88f66f7b865d`.

El primer ensayo de background/foreground sigue registrado como fallido: exigía
que el resaltado temporal del mensaje persistiera después de su duración de 8 s.
Se corrigió únicamente el observador para verificar el mensaje exacto tras expirar
el resaltado, conservando las comprobaciones de ruta, contenido y Back.

Es evidencia focal del producto indicado, no certificación integrada de PR ni cierre
completo del flujo. Permisos y cambios de sesión tienen comprobaciones separadas;
no se deducen de estos casos de recepción e interacción.
Los intentos previos fallidos mantienen sus informes y reconciliaciones de limpieza.

El intento separado de registro real no obtuvo token: tras solicitarlo mediante el
bridge del producto, el Simulator siguió sin registro durante la ventana de 30 s.
La configuración usada carece de `aps-environment`; además, la VM expone x86_64 sin
T2 detectado. No atribuir el resultado exclusivamente a virtualización ni confundir
este intento con entrega APNs. La autenticación y entrega del proveedor conservan
su evidencia y requisitos separados.

## Permisos y reconciliación — 16 de septiembre de 2026

La denegación desde el diálogo real del sistema está acreditada: XCTest y limpieza
PASS, con lectura independiente de `authorizationStatus` de `0` (no determinado) a
`1` (denegado). Recibo privado: `fresh-permission-denial-report.json` bajo el directorio
de informes anterior.

La concesión posterior desde Ajustes también terminó con las cinco etapas XCTest y
limpieza PASS: lectura del permiso `1` antes y `2` (autorizado) después, interruptor
propio activado y retorno a Avisos sin el aviso de permiso denegado. Se utilizó una
entrada explícita a Ajustes, navegación a QuataIos y un único tap en el interruptor
hijo real. Recibo privado: `nested-settings-grant-report.json`; capturas y árboles en
`nested-settings-grant-attachments`. El bundle permaneció idéntico y el Simulator
candidato terminó apagado, conservando el estable.

Este resultado no acredita el destino del botón «Abrir ajustes» de la app. Su ensayo
anterior falló al esperar que Ajustes estuviera en primer plano; la grabación mostró
la raíz de Ajustes al final, sin acreditar la página propia. El intento posterior que
seleccionaba el interruptor padre compuesto también permanece FAIL: XCTest no pudo
determinar su punto de activación. Ninguno se convierte retroactivamente en PASS.

El primer ensayo de cambio A→B (`591de841-6776-4b97-b9cd-742b9318cdec`) permanece
FAIL tras el logout de A. La UI de logout terminó correctamente y se verificó la
ausencia de su sesión exacta en el backend. La recuperación separada comprobó sesión
local vacía, retiró ambos hilos y los tres actores propios y cerró la custodia tras
auditar los archivos privados. No acredita el cambio a B. Sus informes originales y
el recibo `ABORT_A_CLOSED` se conservan en
`session-full-trial-a362b434-d405-49a6-9181-7d3663811bd5`.

El segundo ensayo (`6f974fad-764b-43df-b4e8-c1498a5e8eea`) también conserva FAIL:
el colector intentó leer una ruta de Documents distinta del contenedor vigente del
runner tras el logout. La observación ligada al paso sí existía en el contenedor
vigente; esto no demuestra cuándo cambió la ruta. Su recuperación verificó sesión
local vacía, retirada de fixtures y ausencia backend; la auditoría privada y limpieza
cerraron otro `ABORT_A_CLOSED`, sin acreditar el cambio a B. Informes en
`session-full-trial-eb65c3af-e84c-41a5-948d-beab0cfee67f`.

El tercer ensayo A→B (`6a14a1ac-31b0-4a99-b326-e53f40b40358`) conserva FAIL:
el banner dejó de estar visible antes de completar el tap. La recuperación separada
de B verificó retirada de la sesión local y estado vacío; después se retiraron los
fixtures y se cerró la custodia con `ABORT_B_CLOSED`, manteniendo
`fullIsolationTrialPassed: false`. La auditoría revisó 145 archivos y el scanner
comprobó 2.453 archivos sin coincidencias de los secretos buscados. Informes en
`session-full-trial-6734aa30-e862-439e-b41c-d19019a3a29c`; cierre ligado a
`session-transition-recovery-6a14a1ac/abort-B-v3-finalize-review-hashes.json`
(SHA-256 `31dc4f7e818ea530fca5244f50c4da7382a8ac6e583a7efcc6715868cfc0613f`).
Esto acredita reconciliación, no aceptación del cambio completo A→B.

El cuarto ensayo (`956dbe73-6c28-4563-bf89-7f7f1f8e62a8`) conserva FAIL:
el banner de A estaba fuera de pantalla y no se completó el tap; B no inició sesión.
La recuperación nativa y el retiro de fixtures terminaron correctamente. Se revisaron
86 archivos manuales (18 imágenes y 68 no gráficos); el scanner comprobó 1.222
archivos sin coincidencias de los secretos buscados. La auditoría quedó integrada y
la finalización terminó correctamente con `ABORT_A_CLOSED`, manteniendo
`fullIsolationTrialPassed: false`: cierre de custodia, no aceptación funcional. Informes en
`session-full-trial-6858bcd1-9137-469f-a1f8-0ff04e6f03d3`.

El quinto ensayo (`8ebfebc2-af5d-4cae-918d-350dc0a4b9af`) obtuvo resultados
funcionales afirmativos: login de A, logout real, rechazo del tap dirigido al antiguo
A tanto en modo anónimo como con B autenticado, y control positivo de B hacia
Chat `2672`, mensaje `11308`, con selección, continuidad y vuelta a Chats. Los tests
nativos correspondientes terminaron PASS. Producto
`fa72792e48bcf2f2ef30d4878ca2b8ebc346aa2f`, bundle
`7be9ff6d095e07366b004de662d022637a5da8953ee7d7364aa9af2ce5198e0a`.
La revisión manual comprende 130 archivos y el scanner comprobó 624 sin coincidencias
de los secretos buscados. La auditoría y la verificación final terminaron correctamente:
`FULL_ISOLATION_TRIAL_RETIRED`, `fullIsolationTrialPassed: true` y `custodyClosed: true`.
El proceso terminó con código 0 y se verificó la retirada de journals y locks. Informes en
`session-full-trial-71808482-aaa6-420a-b000-17387f51bae4`. Esta evidencia utiliza
inyección del Simulator; no acredita entrega APNs ni integración del producto `0c6a8421`.

El producto `0c6a8421ed8e92eaf566eef1d4f83d97e176bdbd` cambia una línea de la API
usada para abrir Ajustes de notificaciones. Compilación e instalación PASS; el ensayo
del botón propio permanece FAIL: se observó la raíz de Ajustes, sin acreditar la
página de notificaciones de QuataIos. La revocación separada sí dejó el permiso de
`2` a `1`, donde permaneció al cierre; limpieza e integridad PASS. Recibo privado:
`notification-settings-action-report.json`.

El diagnóstico nativo posterior completó seis etapas y limpieza PASS. Una llamada
informó `opened: true`, con estado de la aplicación `0→1`, pero el permiso permaneció
`1→1` y volvió a observarse la raíz de Ajustes. No demuestra la causa ni acepta el
destino propio del botón. Recibo: `notification-settings-api-diagnostic-report.json`.
Ambas ejecuciones conservan `settingsDestinationAccepted: false`; no alteran la
evidencia histórica del bundle `fa72792e` ni cierran el inventario del flujo.

La evidencia del proveedor sigue separada: ocho contratos del cliente APNs y el
ensayo SQL aislado acreditan sus casos controlados. El probe alojado HTTP/2 obtuvo
respuesta upstream `405` sin credenciales APNs; acredita transporte, no autenticación
del proveedor ni entrega a un token válido. El despliegue SQL conserva su gate de
historial pendiente. Ninguno de estos resultados exige un dispositivo físico para
continuar el alcance Simulator.
