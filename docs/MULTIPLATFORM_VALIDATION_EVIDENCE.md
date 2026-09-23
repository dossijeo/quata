# Evidencia de validación multiplataforma

## FLOW-DEEP-LINKS — GO focal integrado, 14 de septiembre de 2026

[PR #327](https://github.com/dossijeo/quata/pull/327) fusionada el 14 de septiembre a las
10:06:16 UTC en `7407b36856e44a2674964c7d177da32b5aaa40ad`. La certificación final
corresponde al head `2bb4e5fa4e8cf64d0c793f3c39879a271c51126d`. Los recorridos locales
Android conservan ese SHA; Web/iOS conservan el merge sintético
`ca426e04c12279ccac8b0e05c339c53745efef6b`, padres ordenados
`69c6ff137e0824585270b4f34244420e344a7cdb` y `2bb4e5fa4e8cf64d0c793f3c39879a271c51126d`.
No se renombra evidencia local como ejecución del squash integrado.

| Certificación real del candidato | Resultado |
| --- | --- |
| [Web/Android 34826299075](https://github.com/dossijeo/quata/actions/runs/34826299075) | GO: distribución Web/Wasm y Chrome, tests Android/KMP/Wasm, lint Android, contratos rápidos y DPAPI; gate final aprobado. |
| [iOS 34826298954](https://github.com/dossijeo/quata/actions/runs/34826298954) | GO: tramo pesado iOS y gate final ejecutados y aprobados. |
| [CodeQL 34810234684](https://github.com/dossijeo/quata/actions/runs/34810234684) | GO: análisis Java/Kotlin y JavaScript/TypeScript ejecutados; gate de seguridad aprobado. |
| [Imports 34810234678](https://github.com/dossijeo/quata/actions/runs/34810234678) | GO. |

La aceptación local final comprende ocho casos públicos por plataforma (Feed y Official,
existente/ausente, frío/caliente) y siete ensayos privados por plataforma: Chat válido,
hilo ausente y mensaje ausente frío/caliente; Login, cancelación, metadato de sesión
vencido y rechazo de renovación en frío. Se inspeccionaron independientemente 125 PNG:
Web 24 públicos/20 privados, Android 16/27 e iOS 16/22. El número de capturas no implica
estados distintos: algunas capturas Android de detalle/foco son idénticas.

Los originales finales permanecen bajo `build-reports/flow-deep-links/` en los worktrees
`quata-flow-deep-links-cold-start` (Android) y `quata-pr327-integrated-ca426e04` (Web/iOS).
Los índices son `android-public-2bb4e5fa-summary.json`, `web-public-integrated-summary.json`,
`web-private-integrated-summary.json` y `private-native-progress-ca426e04.json`.
Este último conserva los 14 ensayos privados nativos, SHA de informes/capturas,
terminación y limpieza. Las revisiones independientes se conservan separadas:
`android-public-2bb4e5fa-independent-review.json`,
`independent-android-five-private-2bb4e5fa-review.json`,
`independent-android-renewal-rejection-2bb4e5fa-review.json`,
`web-public-independent-review.json`, `web-private-independent-review.json`,
`ios-public-independent-review.json`, `independent-ios-three-basic-ca426e04-review.json`
y `independent-ios-four-auth-ca426e04-review.json`.
Todos esos ensayos aceptados terminaron con limpieza verificada y directorios privados
vacíos. Los primeros intentos públicos fallidos Android `57223b87` e iOS `70762764`
conservan sus informes y reconciliaciones de cierre; no se cuentan como aceptación.

**Límites del GO:** Android vuelve con Back del sistema a Feed; Web/iOS vuelven de Chat
a Chats. iOS acredita `quata://`, no Universal Links/AASA. Web autentica por bridge real
del repositorio/backend, no Submit manual. Los ensayos nativos de metadato vencido
acreditan identidad, destino y cierre con snapshot rotado; `refreshObserved: false`
no acredita transporte, número de refresh ni vencimiento criptográfico del JWT.
El rechazo nativo observado es HTTP 400 en frío, con barrera y cancelación; no cubre
otros códigos ni rechazo caliente. Las ventanas de ausencia son acotadas (Back Android:
dos segundos; foco de mensaje ausente: cinco segundos). Retry nativo visible no fue
ejecutado; Retry público Web ausente sí obtuvo HTTP 200 sin filas. No se certifican
reproducción multimedia, persistencia de Like, relanzamiento nativo tras Back, paridad
total de idiomas, identidad del APK publicado en Play, entrega push/APNs ni lifecycle
global. No se promueven unidades vecinas ni se declara terminada la migración.

Para la ronda del propietario: abrir Feed/Official existentes y ausentes con la app
cerrada y abierta; abrir un Chat autorizado con mensaje objetivo; comprobar destino,
foco y vuelta; repetir sin sesión para continuar o cancelar Login y comprobar que
cancelar no reabre el enlace al autenticar después. Esta guía no equivale a una ronda
del propietario ya realizada. El cierre documental no altera runtime y no requiere
renovar matrices locales por el nuevo SHA documental.

## FLOW-DEEP-LINKS — corte histórico local de #327, 11 de septiembre de 2026

**Estado parcial, sin certificación final ni GO integrado.** Este corte focal no sustituye
los cortes históricos posteriores ni promueve CHAT-FOCUSED-MESSAGE o FLOW-SHELL-NAV.
La evidencia corresponde al merge sintético `1807d1ecee0d04f3ce1db1d15cc13cc3db1be4a7`,
base `9afe514cea667ee3abbf9f9e726d8ac5c32dac77` y head de PR
`209f7847ca32a59ea3bb9d29135d32d41d353129`. Los resultados anteriores conservan sus SHA;
no se han renombrado ni transferido por inferencia. Esta sección es documentación,
no un manifest de candidata certificada.

Los originales locales están en el worktree `quata-flow-deep-links-pr327-1807d1ec`,
bajo `build-reports/flow-deep-links-integrated/`. `acceptance-checklist.json` conserva
los directorios completos por recorrido. Los resultados originales no se modifican
para registrar la revisión: la aceptación independiente se anota por separado.

| Evidencia renovada | Resultado y alcance | Límites conservados |
| --- | --- | --- |
| Web producción | Build terminal 0, 790,89 s; fingerprint `93c73f7a0771ba483b0c2a1e6995ebc6eabe0488463b67c2bb8d4d404f436f82`. Diecinueve contratos browser pasan, sin omitidos. | Los contratos sintéticos prueban el mecanismo, no sustituyen los recorridos reales. |
| Feed/Oficial públicos Web | Existentes e inexistentes, frío/caliente, destino, vuelta y recarga. Ausentes: HTTP 200 sin filas inicial y al reintentar. Capturas revisadas. | No fallo de red ni reproducción multimedia. Las recargas de casos ausentes se acreditan por estado. |
| Web sin ID | `post-`, `official-` y `chat-` resuelven Feed visible frío/caliente; seis capturas, mismo documento caliente y recarga a Feed. | No generalizar a todo enlace malformado. |
| Chat Web válido | Run `ac07b93c-bda7-4603-8cdc-f48da128a2b7`: hilo 2566/mensaje 11198, foco único descubierto frío/caliente y vuelta. | Sesión válida del fixture, sin claim de ciclo de vida del sistema. |
| Chat Web continuar/cancelar | Runs `f3b9c80e-5577-4cf1-b3b7-54577807e10b` y `79d243d8-e9d9-4585-b9bb-ec48f9a0626f`: continuación al destino exacto; cancelar y autenticar después mantiene Feed sin foco residual. | Login real por bridge del repositorio, no escritura/Submit manual. Cancelación observada dos segundos antes de recarga. |
| Chat Web mensaje/hilo ausente | Mensaje: run `d6067916-440e-4a52-9109-27701763e6c1`, historial agotado y cero foco. Hilo: directorio `chat-missing-thread-b5564687-6598-4e8e-82e9-635d5c368d8f`, error legible y vuelta, frío/caliente. | Mensaje sin aviso explícito de inexistencia. Hilo ausente comprobado por DB; no inferido del 403. No se ejecutó Retry de Chat. |
| Chat Web refresh/revocación | Directorios `chat-refresh-cold-86205fa0-376c-4093-82e6-b8146889abca`, `chat-refresh-warm-b2a232b1-98f9-4afc-a3f6-6630f0c4e2a8`, `chat-revoked-cold-ac76143e-700b-4d4f-a5fc-0ed8682c8ecf` y `chat-revoked-warm-a351a6f6-7bd6-453a-8ca9-4b6deff574a9`: una renovación o rechazo real verificados; destino o barrera correspondientes. | Se vence el metadato local; no se acredita vencimiento real/anticipado del JWT ni borrado de almacenamiento. Ausencia de mensaje revocado muestreada en la barrera final. |
| iOS build nativo | Framework raster x86_64 y host build-for-testing terminal 0 sobre el merge indicado; manifiesto `ios-native-manifest.json`. | Simulador iOS 18.3 dedicado; no sustituye compilación ARM de CI. |
| iOS públicos | Feed y Oficial existentes/inexistentes frío/caliente, vuelta, PID estable y capturas revisadas; probes de sesión vacía antes/después y cierre del simulador. | Custom scheme, no Universal Links, lectura HTTP nativa ni Retry ejecutado. No reproducción multimedia. |
| iOS Chat | `ios-owned-valid-f90047cd-8d53-49a2-b334-290f50709b4e` y `ios-owned-missing-thread-967eb48e-1c9f-411d-9388-71340167a6de`: entrega externa frío/caliente, foco válido o error legible y vuelta. | Sesión propia importada con custodia verificada; no login nativo ni refresh real iOS. |
| iOS anónimo | Run `34eefaaa-115b-42e5-9338-2636e260b1f5`: barrera, Login vacío, cierre nativo y shell Feed; PID estable, sesión vacía y simulador cerrado. | La captura final tiene centro negro: no acredita publicaciones cargadas, login real ni entrega caliente. |
| Android | `:app:assembleDebug` terminal 0, 219 s; APK `f390488b84751fd2ff3ec7799015683ad8c9d1b22653c6b3eeb191b10e14e7d6`. | Build únicamente. Falta recepción externa fría/caliente y salidas; el control automático rechazó `adb shell am start` como `blocked by policy`. No se eludió. |

Los recorridos reales de Chat tienen restitución verificada de sus fixtures propios;
los directorios privados de los ensayos terminados están vacíos. Los públicos no crean
fixtures. La revisión independiente acepta los recorridos de la tabla con sus límites;
el build Android no equivale a aceptación de entrega externa.

Nueve regresiones sintéticas iOS adicionales terminaron 0 en
`ios-synthetic-regressions-bfb67301`, run `bfb67301-e712-47f7-95a3-ad3994287737`.
Los logs y la revisión independiente acreditan ejecución exacta, sin omitidos, de contratos
de intercambio/aislamiento de sesión, Auth, cancelación y entrega pendiente. Sesión vacía
antes/después y simulador cerrado. No acreditan login ni persistencia remota reales.

Falta cerrar la auditoría focal de cobertura y la aceptación Android antes de congelar
el head. Los fast gates verdes y los agregados finales con jobs reales omitidos mientras
la PR es draft no acreditan certificación final. Tras promoción autorizada e integración,
el cierre obligatorio debe confirmar merge/CI y actualizar el inventario operativo.

## Corte histórico general

**Corte documental:** `main` `d8652326f61d93f33bb860d64565ad74e3e80ed5` (2026-07-29).

La evidencia se delimita por SHA y tipo de prueba: build acredita artefactos;
smoke acredita el recorrido descrito; E2E exige backend real, identidad y limpieza.
Ninguna fila amplía su alcance por inferencia.

## Web/Wasm

| Prueba | Resultado | Evidencia y límite |
| --- | --- | --- |
| Producción observada | GO | Harness reproducible de PR #99 con cinco perfiles fríos. Son muestras de diagnóstico, no un SLO de producto. |
| Browser/UX público | GO limitado | PR #100 acreditó rutas profundas, recarga, tres viewports, scroll interno, foco secuencial y árbol AX sobre los nodos del formulario. |
| Bundle Linux | GO | Baseline canónico aprobado por PR #97. Windows conserva sólo valor diagnóstico porque Wasm/JS depende del host. |
| `wasmJsBrowserTest` | GO | Verde en la CI del lote #102 junto a bundle y smoke Chrome. |
| E2E autenticado | HOLD | No se afirma hasta terminar una ejecución controlada con identidad aislada y limpieza verificable. |

## Android API-37

| Prueba | Resultado | Límite |
| --- | --- | --- |
| Compilación e instalación | GO | Build y `install -r` validados en el corte previo; los checks Android del lote #102 siguen verdes. |
| Arranque/Feed anónimo | GO limitado | Cold start observado de 4,924 s, PID vivo y 0 crash/ANR tras cold boot. Es un smoke de entorno, no benchmark. |
| Matriz autenticada | HOLD | Pendiente de cuenta aislada y preservación del AVD anónimo. |

## iOS

| Carril | Resultado | Límite |
| --- | --- | --- |
| GitHub Actions | GO limitado | La matriz pública #102 sigue acreditada por [`30425431607`](https://github.com/dossijeo/quata/actions/runs/30425431607). PR #106 añadió logout autenticado y el run [`30429034347`](https://github.com/dossijeo/quata/actions/runs/30429034347) terminó verde sobre `db2c2b1a`: compilación y contratos Swift/Kotlin, sin acreditar interacción visual. |
| Matriz pública simulador | GO funcional suplementario | PR #102 ejecutó serialmente iOS 18.3 e iOS 26.5 con configuración pública temporal, HTTP 200, PID/logs filtrados, 0 crash/fatal observados, OCR/capturas y cleanup. CPU-raster Intel no acredita SLA ni rendimiento de producto. |
| Rutas/factorías iOS | GO de contrato | PR #103 añadió contratos de rutas instaladas y factorías; no sustituye una sesión ni un backend real. |
| Backend de cuenta de prueba | Protocolo verificado, no E2E | Una comprobación redactada obtuvo sesión y perfil. No se guardaron ni publicaron credenciales, ni este resultado afirma UI, refresh o logout. |
| Login visual real | HOLD técnico | Falta el fichero de configuración remoto con modo `0600` y aislar Keychain/test host. PR #107 se cerró sin merge después de que su fixture desconectara el proceso Compose; no se declara autenticación, refresh ni logout visual. |
| Signing/distribución | HOLD | El archive genérico sin firma está disponible como evidencia de estructura. Faltan identidad Apple/Team, certificados, perfiles, App Group, IPA/TestFlight y dispositivo físico. |
| APNs | HOLD | Hay bridge/plumbing y requisitos documentados; no hay canal de dispatcher APNs ni entrega en dispositivo físico firmada. |

## Seguridad y límites de producción

No se desplegaron cambios de Supabase ni se modificaron RLS, DDL, funciones, grants
o datos durante PRs #98–#104. Los hallazgos permanecen abiertos en
[RLS_FINDINGS.md](RLS_FINDINGS.md) y las mutaciones no acreditadas siguen
fail-closed.

## Conclusión del corte

Web y Android conservan un recorrido público validado. iOS compila, enlaza, ejecuta
el Feed público en ambos simuladores y tiene una matriz reproducible, pero sigue
sin firma, entrega APNs, dispositivo físico ni E2E autenticado terminado. La
migración global, `webReady` e `iosReady` permanecen incompletos.

## Textos genéricos Web Push — cierre focal de #334

[PR #334](https://github.com/dossijeo/quata/pull/334) integrada el 14 de septiembre
de 2026 a las 23:45 UTC en `f13ce52a4909fb43b30a97209a1e9a366ddc961f`.
Los cuerpos genéricos de voz, adjunto y mensaje coinciden con Android publicado
en inglés, español y francés, incluidos locales regionales. Sólo cambió la tabla
del worker y su hash de capacidad; no se elevaron estados de capacidades.

Identidad validada: base `ce03bdc0c76bbccaec500d2650a662250500b3cf`,
head `33021ab95e728037a915818f914d378dc0ece032` y merge de prueba
`473e267569618053405c4758b730ceb2c67baab3`, con ambos padres verificados.
El árbol finalmente integrado es idéntico al head revisado.
Chrome cargó el worker exacto del merge, persistió el locale mediante su mensaje
de producto en IndexedDB real y comprobó doce selecciones contra los recursos
Android. Navegador y servidor cerrados, sin fixtures de backend. Contratos focales,
22 contratos de capacidades, `diff --check` y revisión independiente aprobados.

Certificación final aprobada: [Web/Android](https://github.com/dossijeo/quata/actions/runs/34905903068),
[iOS](https://github.com/dossijeo/quata/actions/runs/34905903074) y
[CodeQL](https://github.com/dossijeo/quata/actions/runs/34905647829).
La comprobación focal de textos no ejecutó `showNotification`, entrega de proveedor
ni clic nativo. No cierra `FLOW-PUSH-LIFECYCLE`, `FLOW-NOTIFICATION-REPLY` ni la ronda
del propietario. Para esa ronda, comparar los avisos genéricos con Android en cada
idioma cuando se ejecute el flujo de entrega real autorizado; esa prueba queda pendiente.

## Web Push — dispatch y delivery reales; interacción pendiente

El 18 de septiembre de 2026 se ejecutaron cinco intentos reales sobre Product Head
`cf7bc5eead6153d30b8ec7ada6aedfd6edd92d16` y distribución
`7ce38a31c791871ebf05959d30ff9cd1fbfe4c3156e68c199770a87278301374`.
Los cinco acreditan dispatch completado, `webSent=1`, transporte asentado sin resultado
incierto y delivery log `sent`. Los run IDs fueron
`ece5ef73-e676-47af-b930-068c237594bd`,
`3e3e71d5-f7d3-4cf7-841e-6867a70c64b2`,
`484bc792-e310-4aa4-b488-b7c4df805a93`,
`1a905ae1-b3b6-4d62-bcdd-079d2ef3f269` y
`5615c1eb-7285-4403-b3b6-90b8367528e2`.

Los cinco ensayos terminaron fallidos en la fase de clic nativo. Un recibo informó dos
representaciones de la notificación (`GroupControl` y `ListItemControl`), pero no conservó
el marcador que permitiera atribuir unicidad exacta. No se acreditaron clic,
`notificationclick`, ruta ni Reply. Los cambios posteriores hasta el candidato de #345 no
modificaron el service worker, los dos Edge Functions de push ni los coordinadores Web Push;
la CI final de #345 validó de nuevo la distribución Web actual sin justificar otra matriz E2E.

El 19 de septiembre se ejecutó un control local acotado, sin backend ni envío, sobre un
banner Chrome recién creado con marcador exclusivo. El observador resolvió exactamente un
`PriorityToastView` por su jerarquía y límites visibles; la pulsación sobre ese contenedor
retiró la notificación (`remaining=0`), pero el worker instrumentado no observó
`notificationclick`. El control separa presentación/consumo nativos de activación del
producto: no acredita ruta, Chat ni Reply y tampoco demuestra que el producto carezca del
callback. El perfil Edge usado como control no completó sus llamadas de Service Worker, por
lo que no llegó a crear una notificación y no aporta una segunda conclusión de interacción.
No se encadenaron más gestos ni se abrió otro ensayo remoto.

Tras los fallos, recibos independientes de los cinco run IDs confirmaron cero
`auth_users`, `threads`, `messages`, `deliveries` y `privateRecoveryFiles`. La revisión
independiente aprobó documentar dispatch/delivery y limpieza, y rechazó elevar el recibo
nativo a tarjeta única con marcador exacto. Este corte no cierra `FLOW-PUSH-LIFECYCLE`,
`FLOW-NOTIFICATION-REPLY` ni la ronda funcional del propietario.

El candidato Web posterior añadió sobre el service worker una acción `reply` localizada
como `Reply`, `Responder` o `Répondre`, únicamente cuando el payload contiene un destino
Chat. No crea entrada de texto inline: el handler `notificationclick` existente conserva
la ruta exacta y deja escritura y envío dentro de la UI autenticada. El contrato del worker,
la distribución de producción y el catálogo de capacidades pasaron sobre Product SHA
`765fad3d98db8e233d77cc8f5c3cb1c8c3ca5b3e`; el runtime se ensayó sobre `4a80fdc1`
y el commit posterior sólo actualiza la huella del mismo blob en la matriz.

El ensayo real `8ce64d96-2614-46f4-adfd-916f6528359a` acreditó dispatch completado,
`webSent=1`, delivery log `sent` y un único `VerbButton` de respuesta dentro del aviso con
marcador exclusivo. El clic por `SendInput` dentro de sus límites retiró el aviso, pero el
host Windows 11/Chrome no entregó un `notificationclick` observable ni mostró Chat. El
ensayo falló cerrado y retuvo journals; la reanudación verificó y eliminó suscripción,
delivery, perfiles y journals, y una auditoría SQL posterior confirmó cero usuarios Auth,
hilos, mensajes y logs del run ID. Esta evidencia acredita producto y presentación de la
acción, no activación, ruta ni envío Web. No justifica repetir gestos sobre la misma
presentación sin una diferencia nueva del entorno.

Un diagnóstico local posterior, ejecutado el 23 de septiembre sin backend ni una nueva
notificación, encontró una diferencia concreta del entorno. El coordinador del ensayo crea
un `profileDirectory` exclusivo, inicia allí un contexto persistente Playwright y sirve la
distribución desde un puerto localhost efímero; su cleanup retira la suscripción y las
notificaciones propias antes de cerrar el contexto. En cambio, el único perfil Chrome del
host con base de Service Worker era `Default` y no contenía registro, script ni caché de
Qüata: no aparecieron `quata-sw.js`, el origen estable `127.0.0.1:4174`,
`chatNotificationTarget`, `openOrFocusQuataWindow`, `quata-web` ni `incoming-shares`.
Las entradas con `notificationclick` que sí existían en su ScriptCache pertenecían a Adobe
y SuprSend.

La implementación Windows de Chromium codifica tipo, origen, identificador e ID de perfil
en un `notification-launch-id`; `notification_helper` entrega después la operación y carga
el perfil por ese ID. Esa [ruta primaria de Chromium](https://chromium.googlesource.com/chromium/src/+/848c1835e99f213cd9a863a5a4b52afef0e89e9e/chrome/browser/notifications/notification_platform_bridge_win.cc)
motivó un control adicional para separar la raíz alternativa de datos del bridge nativo.

El control cerró Chrome cuando sólo conservaba una pestaña nueva, respaldó `Local State` y
creó un perfil temporal dentro de la raíz estándar sin modificar `Default`. Una página
local temporal registró el mismo `quata-sw.js` servido en `127.0.0.1:4174`, obtuvo permiso
`granted` y mostró `QUATA-STANDARD-PROFILE-FINAL`. La captura previa al clic conserva el
marcador exacto en el Centro de notificaciones. Un único clic retiró esa tarjeta, pero la
barra de direcciones permaneció durante más de 60 segundos en la página de control: no
aparecieron hash de Chat, ruta ni otro efecto observable de `notificationclick`. El ensayo
no usó backend ni dispatch remoto. Después cerró Chrome, eliminó sólo el perfil temporal,
restauró `Local State` con el mismo SHA-256 y retiró la página de control.

Usar la raíz estándar tampoco produjo navegación observable. El punto de interrupción
sigue sin localizarse entre la activación nativa, el dispatch del evento y la navegación;
el control no demuestra que el producto carezca del callback ni identifica como causa la
raíz de perfil o el bridge Windows/Chrome. La aceptación Web permanece abierta y no se
hicieron más gestos. El criterio no cambia: exige el evento y la ruta reales, sin fabricar
el evento ni llamar directamente al handler.

Un control posterior de sólo lectura comprobó la instalación nativa sin crear ni pulsar
otra notificación. `chrome.exe` y `notification_helper.exe` eran la versión
`153.0.8010.53`, tenían firma Authenticode válida y el mismo árbol de instalación. El
servidor COM `LocalServer32` de Chrome apuntaba a ese helper; el acceso directo principal
de Inicio declaraba `AppUserModelId=Chrome` y el mismo `ToastActivatorCLSID`. Los logs
Application y System no contenían avisos o errores de Chrome, notificaciones o
DistributedCOM en la ventana local `10:29–10:35` que incluye el clic acreditado de
`10:31:37`. Esto descarta una ausencia simple o una incoherencia visible de registro, pero
no demuestra que Windows invocara el activador ni localiza el corte posterior. La
conclusión y el pendiente permanecen sin cambios.

La consulta retrospectiva del canal habilitado
`Microsoft-Windows-PushNotification-Platform/Operational` añadió una señal temporal, sin
repetir la interacción. `WpnUserService` registró a `10:31:37.719` la limpieza del endpoint
(`3049`) y a `10:31:37.889` el borrado de notificaciones del sistema (`3055`), dentro del
mismo segundo del clic acreditado. Esos eventos confirman que la plataforma procesó la
retirada, pero el canal Operational no registra por sí solo la invocación del activador.
El canal Debug que contiene los eventos de callback `2025`–`2027` estaba deshabilitado y
no había registros de creación de procesos `4688` ni un log Sysmon disponible para esa
ventana. Por ello no puede reconstruirse retrospectivamente si se lanzó
`notification_helper` o si el corte fue posterior. No se habilitaron trazas ni se autorizó
otro gesto para obtenerlas.

La evidencia Prefetch del mismo control anterior redujo después esa incertidumbre:
`NOTIFICATION_HELPER.EXE-ECF674AF.pf` quedó actualizado a
`10:31:37.9070570`, dentro del segundo exacto del clic acreditado, con SHA-256
`EBD9A509E800CC2709BCC2913D4BE39233D17CB7A286B0858E84A768EE45CD84`.
Esto acredita ejecución de `notification_helper` para ese clic, pero Prefetch no conserva
el argumento `notification-launch-id` ni el resultado de la transferencia a Chrome.

Esa señal autorizó un único control local instrumentado posterior, todavía sin backend ni
dispatch remoto. Un perfil temporal nuevo registró el worker real, mostró el marcador
`QUATA-INSTRUMENTED-ACTIVATION-20260923` y conservó una captura ligada a la tarjeta. El
canal Debug de Windows registró la adición del callback `94056` (`2025`) al crear la
notificación y su invocación (`2027`) a `11:46:44.7970096`, seguida por finalización de
`ToastFeedbackWork` con operación completada correctamente. El único clic retiró la
tarjeta, pero Chrome permaneció en la página de control y su log instrumentado no registró
una operación de activación o dispatch del worker en ese instante. El observador de
procesos había vencido antes del clic, por lo que su silencio no se usa como evidencia; el
Prefetch tampoco se renovó en este segundo control y no autoriza una inferencia de proceso
para él.

Una consulta posterior de sólo lectura a `wpndatabase.db` conservaba la fila `94056` y su
XML exacto. El atributo `launch` no estaba vacío y, conforme al parser vigente de Chromium,
decodifica como activación normal para el perfil `QuataInstrumentedActivation`, AUMID
`Chrome`, origen `http://127.0.0.1:4174/` y el identificador que contiene el marcador
exclusivo. Esto descarta ausencia, formato insuficiente o perfil distinto en el launch ID
almacenado al crear ese toast. No acredita que Windows entregara `invokedArgs` sin cambios,
que el helper iniciara Chrome en este control ni que Chrome recibiera o procesara el
comando. Recibo privado
`build-reports/web/web-notification-profile-diagnostic-20260923/windows-notification-launch-id.json`,
SHA-256 `C9A78D8994CDC0717F6216BEBC1EAB28EE5DA772F8227373F3E5350BA1879B79`.

En conjunto, Windows invocó el callback asociado a la notificación exacta y, en el control
anterior, ejecutó `notification_helper`; además, el toast instrumentado almacenó un launch
ID válido dirigido al perfil correcto. El límite restante queda entre la entrega de ese
argumento al helper/Chrome, la operación de notificación de Chrome, el dispatch al Service
Worker y la navegación. La evidencia no identifica cuál de esas etapas falló ni demuestra
ausencia del callback del producto. Se restauraron el canal Debug, `Local State`, el perfil
y la página temporal. No se realizaron más gestos en este control.

## Notification Reply iOS — aceptación focal en Simulator

La evidencia privada conservada de `auth11` (`runId`
`4c93f5bc-be14-4849-b9a7-10aabc3a0a12`) termina `passed`, con el envío iniciado
desde el editor nativo de SpringBoard, texto sintético exacto, un solo Send,
delegate y runtime observados, un único mensaje propio persistido, retirada de la
notificación y limpieza completa. El piloto y el ensayo usan el mismo helper de
apertura: Home visible, alerta propia recién inyectada, pulsación de 1,5 segundos
sobre `NotificationShortLookView` y aceptación tanto del editor directo como de
la acción Reply opcional. No usan Centro de notificaciones, coordenadas fijas ni
llamada directa al handler.

El ensayo negativo acotado `editor-capture-negative-run` (`runId`
`fe65ab3d-7dc6-41b9-bcce-33b5c856699c`) también termina `passed`: verificó el
texto antes de un único Send, observó delegate, guard y runtime, acreditó como
mínimos tres intentos y dos pausas, confirmó ausencia del mensaje propio, observó
el aviso de fallo, retiró sólo ese aviso y limpió bloqueo, hilo, cuentas y journals.
La cobertura del observador quedó incompleta, por lo que no se infieren estado
HTTP, conteo exacto de reintentos ni traza negativa completa.

Con ambos recorridos, iOS alcanza GO focal de interacción en Simulator para
`FLOW-NOTIFICATION-REPLY`. Permanecen fuera entrega APNs, dispositivo físico,
firma de distribución, offline, reinicio y navegación posterior. Web conserva su
estado pendiente y este cierre no completa la ronda funcional del propietario.
