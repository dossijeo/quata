# FLOW-DEEP-LINKS: estado de aceptación focal

Estado: **parcial; sin candidate-final ni GO integrado**. El inventario maestro
permanece pendiente. Esta matriz resume resultados actuales; no sustituye los
reportes y capturas ni promueve CHAT-FOCUSED-MESSAGE o FLOW-SHELL-NAV.

Última renovación Web de recorridos públicos frío/caliente: producto
`edb67fd56155cd4ff3585c841c40f98bf1d26fba`, distribución
`581fa8f9eb6a5a3b4704929a08e20b972fc1ff2a75335fddc3af956866dea044`.
Vuelta y recarga revisadas; detalles y reconstrucción en la sección de procedencia.

Producto Web de los recorridos autenticados siguientes: `5676e3a34334899d3682764911cfd4a8057230b2`.
Distribución: `b2bc94544576f482869f3c67e87affa6caf3aa10edd69ba8dc962aa032b21b19`.
Los ensayos revocado caliente, renovación fría/caliente y continuación/cancelación
tras login usan este binario. Hilo inexistente
conserva `edbb970b` / `39a6b782…` y su evidencia original.
Revocación fría y mensaje ausente conservan producto `3bcfec15`,
distribución `1253d2b7…`. Los recorridos públicos anteriores conservan producto
`c252e00035e97065726fc51aee5a4d6469975324`, distribución
`ca9990b2840087bfcb31508d65968a722bf7fdb38e8af594a8b7f1e0fc9019c4`.
Chat con sesión preinyectada sin renovación conserva producto `8cde7edf…`,
distribución `7c743c4f…`. Hashes completos y límites en el plan de sesión.
Ningún resultado se transfiere al head final por inferencia.

| Recorrido Web | Frío | Caliente | Salida / recarga | Límite pendiente |
| --- | --- | --- | --- | --- |
| Feed, post existente | Renovado en edb67fd5 | Renovado en edb67fd5; mismo documento hasta foco y Back | Lista sin reapertura tras Back/recarga; cero errores | Certificación de candidata final pendiente |
| Feed, post inexistente | Renovado en edb67fd5 | Renovado en edb67fd5; mismo documento | Reintento focal, vuelta y recarga sin reapertura; cero errores | No acredita fallo de red ni otras plataformas |
| Oficial, post existente | Renovado en edb67fd5 | Renovado en edb67fd5; mismo documento, destino de lista cargada | Lista sin reapertura tras Back/recarga; cero errores | No acredita reproducción multimedia; capturas con placeholder de vídeo |
| Oficial, post inexistente | Renovado en edb67fd5 | Renovado en edb67fd5; mismo documento | Reintento HTTP 200 sin filas, vuelta y recarga a Oficial; cero errores | No acredita fallo de red |
| Enlaces sin ID: post-, official-, chat- | Feed visible renovado en edb67fd5 | Feed visible renovado en edb67fd5 | Recarga resuelve Feed; hash original conservado | Recarga por aserciones, sin captura; no generalizar a todo enlace malformado |
| Chat, hilo/mensaje propio, sesión válida | Comprobado | Comprobado; mismo documento | Un foco visible; salida/recarga sin reapertura | Destino inexistente y sesión expirada; ver fila anónima para login |
| Chat anónimo | Barrera de acceso comprobada | Barrera y continuación con login real renovadas en 5676e3a3/b2bc9454 (runner ea6946db); cancelación renovada con runner 46e58186, GO local focal revisado | Hilo/mensaje exactos al continuar; tras cancelar, cierre normal de Novedades y Feed sin foco residual en mismo documento | Login por bridge de repositorio, no Submit manual; ventana acotada; no demuestra ausencia universal de peticiones privadas |
| Chat, metadato local de sesión vencido | GO local renovado en 5676e3a3/b2bc9454 con runner e09e2da7: un refresh real verificado y mensaje exacto visible | GO local renovado en 5676e3a3/b2bc9454: metadato vencido inmediatamente antes de entregar, mismo documento, cero refresh previos y uno verificado posterior | Vuelta y recarga sin reapertura ni segundo refresh; limpieza completa | Recargas acreditadas por estado, sin captura propia; no JWT realmente vencido ni sesión revocada; preservados los ensayos previos |
| Chat, mensaje ausente en hilo propio | Comprobado en 3bcfec15: RPC exitoso e historia del fixture agotada, cero foco | Comprobado; mismo documento | Vuelta y recarga a Chat sin reapertura; limpieza completa | No acredita hilo inexistente ni aviso explícito; ventana acotada |
| Chat, hilo inexistente | Comprobado en edbb970b: fallo de lectura legible único, sin mensajes ni foco | Comprobado; mismo documento | Vuelta/recarga a Chat sin reapertura; limpieza completa | No se ejecutó Retry; ausencia probada por DB, no inferida del 403; sin aviso específico de inexistencia |
| Chat, sesión propia revocada | PASS en 3bcfec15: un rechazo entregado/verificado, barrera anónima sobre Feed, cero foco/ruta Chat observados | GO local corregido en 5676e3a3/b2bc9454: rechazo verificado con metadato vencido al entregar, mismo documento, barrera Feed sin ruta/foco Chat observados | Limpieza automática completa; fallo edbb970b preservado | Ausencia de mensaje muestreada en barrera final; sin claim de JWT vencido anticipadamente, borrado de almacenamiento ni otras plataformas |

Los recorridos públicos usan lectura anónima de publicaciones existentes. Chat
usa perfiles, sesiones, hilo y mensaje temporales propios. Último run Web
`524ccfe5-0fb0-450c-a516-ff60dbca5a0f`: cancelar y hacer login después conserva Feed sin foco,
limpieza automática verificada y journals/lock retirados.
Las aceptaciones anteriores conservan sus límites y procedencia. Estos runs Web
no tienen mutaciones ni restituciones pendientes; el estado de ensayos iOS se
registra por separado. Detalle y límites en
[el plan de sesión](FLOW_DEEP_LINKS_SESSION_ACCEPTANCE_PLAN.md).

Las observaciones Web no prueban segundo plano/primer plano del sistema,
service workers ni recepción de push. El plazo observado tras salida es acotado;
no es una garantía indefinida. Los marcadores de diagnóstico no reemplazan la
inspección visual de las capturas.

### Renovación pública Web, 12 de septiembre de 2026

GO local revisado en `build-reports/flow-deep-links/web-public-renewal-edb67fd5`,
con el producto y distribución indicados al inicio. `report.json` y
`official-report.json` verifican inexistentes en frío/caliente, HTTP 200 sin filas
al abrir y reintentar, vuelta y recarga; doce capturas inspeccionadas por el
orquestador y revisor independiente. `official-existing-scoped-report.json`
acredita petición fría del ID `9779260c-e5b8-488e-aa04-0c11cc33654e`;
`official-existing-warm-cached-report.json` acredita el mismo destino desde la
lista ya cargada y continuidad del documento. Sus seis capturas muestran
«Lanzamiento musical», salida y recarga en Oficial, sin acreditar reproducción.

`malformed-visible-renewal/malformed-lifecycle-report.json` y sus seis capturas
acreditan Feed descubierto para `post-`, `official-` y `chat-`, frío/caliente.
La recarga se verifica por aserciones, sin captura posterior. Se exige aparición
y desaparición del splash al arrancar y un control concreto del Feed antes de
capturar. El ensayo original queda preservado: su captura `post-` caliente aún
mostraba splash y no se acepta visualmente, aunque el reporte declaraba PASS.
Los reportes renovados cierran browser/contextos/servidor y no registran errores
de página. Los observadores sólo realizan navegación y lectura; `mutations: 0`
es declarativo, no un contador exhaustivo de peticiones. No hay GO integrado.

## Android e iOS

La renovación nativa iOS fría tiene GO local en `29456502` y la entrega caliente
tras preludio público en `fcd0afb9`; Android y rechazo de sesión nativa siguen
pendientes. El
[plan focal](FLOW_DEEP_LINKS_NATIVE_SESSION_ACCEPTANCE_PLAN.md) identifica las
diferencias actuales de arranque/peticiones y la custodia necesaria para probarlos.

### iOS: metadatos vencidos y renovación fría, 12 de septiembre de 2026

Run `c8be6efa-e74c-4ef6-a506-69e28e3bb710`, producto
`29456502c3b4119c127a93163ac413ba5c3033cb`: instalación vencida verificada,
snapshot rotado aceptado por Auth y misma sesión/actor, sin refresher del harness.
Entrega externa fría, hilo `2587` / mensaje `11223` enfocado y Back a Chats sin
reapertura en la ventana observada; PID `75230` continuo. PASS y limpieza completa,
proceso terminal 0, directorio privado vacío, simulador dedicado apagado y estable
conservado. Dos capturas inspeccionadas y GO independiente local.

App `8f6de8e5d0df44c8456b23667110e88cdaf6c4e8cf58b7a6e712858574ef8961`,
runner UI `f362da77669e37a9dbb51565da000265f739cda8df3393e61e09b5e38ae96052`;
binarios conservados de `40552bf5` con fuentes iOS verificadas sin cambios y
worker actualizado según manifiesto exacto. Procedencia y límites en el
[ensayo cerrado](FLOW_DEEP_LINKS_NATIVE_SESSION_ACCEPTANCE_PLAN.md#ensayo-ios-frío-cerrado-12-de-septiembre-de-2026).
No acredita JWT criptográficamente vencido, rechazo, conteo HTTP, causalidad
exclusiva del enlace, caliente, Android, Universal Links ni candidata integrada.

### iOS: entrega caliente tras preludio de renovación, 12 de septiembre de 2026

Run `28db5b0a-e727-45ed-87e4-e3789e36f7f6`, producto
`fcd0afb992801b994f2ac67c42b9786e5055bfb8`: metadatos vencidos instalados antes
del lanzamiento público, snapshot renovado e identidad remota verificados.
PID `76969` continuo desde el preludio hasta enlace externo, mensaje `11224` /
hilo `2588` enfocado y Back a Chats. PASS, limpieza completa y GO independiente
de dos capturas y recibos. Se conservan los binarios y fuentes iOS del ensayo
frío, con worker actualizado y manifiesto exacto. [Procedencia y hashes](FLOW_DEEP_LINKS_NATIVE_SESSION_ACCEPTANCE_PLAN.md#ensayo-ios-caliente-tras-preludio-cerrado-12-de-septiembre-de-2026).

La renovación puede preceder a la entrega. No acredita sesión vencida en ese
instante, causalidad exclusiva del enlace, conteo HTTP, rechazo, Android,
Universal Links ni candidata integrada.

### Android: renovación pública, 12 de septiembre de 2026

Ocho observaciones pasan sobre producto `cd1a5839`, APK `ce0dd68d…e16e9`:
Feed y Oficial, destino existente/inexistente, frío/caliente. Emisor compilado
con fuente SHA-256 `60fada8bb5575d3be87ba037e62c6e1195fef6cc88b8c613b16a940cd29fd620`
y APK de test `41bd4e99944c60902a0444f4f4d88dfefaef0c666e03a52a17714a99c84689ea`.
Directorios bajo `build-reports/android-external-sender`:

- `public-feed-existing-cb74ef5e-f8be-4a43-9e3e-4470d3670ec8`, PID `10120`.
- `public-feed-missing-6fef1103-73d7-4c8f-a182-cc1f2adfaa1b`, PID `10977`.
- `public-official-existing-1273b9e6-657d-4d19-90f4-57bad8dc14f7`, PID `11453`.
- `public-official-missing-f0004867-faeb-4216-84fa-f87402524c9c`, PID `11851`.

Cada pareja parte sin proceso y conserva PID al entregar en caliente. Entrega
HTTPS implícita desde otro UID con resolver público verificado; observador
separado sin reentrega. Existentes exigen recurso del ID exacto antes de capturar.
Lecturas anónimas previas/posteriores verifican el ID existente o cero filas
visibles al acceso público; no prueban ausencia global en la base de datos.
Las dieciséis capturas detail/back muestran destino y salida correspondiente:
JO/Feed, «Lanzamiento musical»/Oficial o estado terminal con Reintentar.
Back no reabre el detalle durante dos segundos. Todos los cierres tienen probe
vacío final y `cleanupComplete: true`; orquestador y revisor independiente
inspeccionaron las dieciséis capturas, con GO local acotado.
No se pulsó Reintentar ni se probó reproducción o autenticación.

El primer intento Feed inexistente `public-feed-missing-01e7e4cc-85d5-40a9-b8d8-1d3855c8e0dd`
se conserva fallido: ADB dividió el texto esperado antes de iniciar el observador.
`reconciliation.json` y el probe de cierre verifican dueño terminado, ausencia de
instrumentación activa, forward retirado y sesión vacía antes de repetir. El
runner cita ahora cada argumento del shell. El fallo no se transforma en PASS.

### Android: continuación tras login nativo

`NativeLoginTest#resumeDeliveredLink` y el transporte privado del host preparan
un Submit real sobre un enlace anónimo ya entregado. La entrada usa directamente
`ACTION_SET_TEXT`: se descartó `UiObject2.setText` antes de ejecutar credenciales,
porque su implementación registra el texto. Se comprueba `+240` antes de rellenar
y ambos campos ausentes antes de capturar el foco. Revisión estática independiente
favorable; doce contratos Node preparatorios pasan. El coordinador conectado en
`ac6620e6` controla PID, recibo Auth y restitución de la sesión exacta.

El primer Login real frío, ensayo `6851b0bc-b137-4214-bfda-e1e78c2e7548`, se conserva
en `build-reports/flow-deep-links/android-native-login-cold-aab5b08b-8cbf-4a64-9a77-a554ddafcf8b`.
Producto `cd1a58397e494463af24f85ba14cd2215039ce52`, APK
`ce0dd68de86cbe551d65bd71f661ddab2f0dd685d25a525d06f962634c6e16e9`:
un Submit llega al mensaje exacto `11209`, conservando PID `15346`; proceso terminal
0 y limpieza completa. **NO GO del recorrido:** aunque el reporte automático dice
`passed`, la captura `back.png` muestra Login vacío. El observador sólo comprobaba
la ausencia de Chat. Se preservan el reporte y las capturas originales; ese PASS
no acredita el retorno ni aceptación integrada. La corrección `5925822b` retira
Auth de la pila al continuar hacia Chat y exige Feed y ausencia de Login al volver.
La repetición corregida se registra abajo; no reutiliza el PASS anterior.

La repetición fría `781a4994-a1b4-49c9-a843-3ebacfc9a610`, directorio
`android-native-login-cold-7021c58c-0336-4abd-b17f-da275eb34d09`, queda fallida:
el emulador terminó durante el observador Login; proceso host terminal 1 y sin
recibo de finalización del observador. El AVD se recuperó conservando sus datos;
probes pasivos vacíos inicial/final y ausencia de sesiones Auth/Web y registros
nativos verificados. `reconciliation.json` acredita retirada del hilo/perfiles
propios, journals privados vacíos y lease retirada; el ensayo sigue fallido y
no acredita el fix. Producto `5925822ba2d9673cd43d8758b023de70a8556f85`, APK
`73860c48f5237e76d0fd7aa030d70bc511de3636c264556f5b7b387e002553bd`.

GO local frío revisado sobre ese producto/APK: ensayo
`f08a4620-9ea8-4019-9cfb-299aa6b0a099`, directorio
`android-native-login-cold-258f4d9c-d4aa-4b1f-bdc1-28a35704804a`.
Entrega externa anónima sin PID previo, un Submit nativo y PID `3864` conservado:
mensaje `11211` resaltado, Back a Feed seleccionado con contenido visible, sin
Login ni Chat durante la observación posterior de dos segundos. Las tres capturas
fueron inspeccionadas por el orquestador y el revisor independiente. Proceso
terminal 0, sesión exacta retirada y limpieza completa; ese ensayo no acredita
caliente, sesión vencida/revocada, iOS ni aceptación integrada. Build Android y emisor
correctos, trece contratos focales y 485 contratos rápidos pasan.

El primer intento caliente `10f949d4-7796-4dbe-9293-f8d92bb8f6d6`, directorio
`android-native-login-warm-8bc9e13b-4184-4ccb-b86f-7064d4d2c836`, terminó antes de
entregar el enlace: el preparador exigía ausencia de PID también en caliente.
Limpieza completa y proceso terminal 1; no es evidencia de Login. El ajuste
`4778124c`, revisado independientemente, permite el proceso inicial caliente y
exige conservarlo tras el preludio público, Chat y Login. Cold sigue exigiendo
ausencia de proceso; no introduce reinicios ni cambia el APK de producto.

GO local caliente revisado: ensayo `abf2f6ec-78c2-4485-a62d-1646adfbfcc1`, directorio
`android-native-login-warm-8f6c065d-f227-4c08-a4e5-d21b46efe35f`, producto/APK
`5925822b`/`73860c48…` anteriores y runner `4778124c`. Un preludio público abre Feed;
la entrega posterior de Chat y el Login conservan PID `4658`. Un Submit abre el
mensaje exacto `11213` resaltado; Back muestra Feed seleccionado y contenido
visible, sin Login ni Chat. Tres capturas inspeccionadas por orquestador y revisor
independiente; proceso terminal 0, limpieza completa, directorio privado vacío y
lease retirada. `initialPid: null`: acredita Chat con app caliente por el preludio,
no la variante con proceso existente antes de él. No acredita sesión vencida o
revocada, iOS ni aceptación integrada. Los 485 contratos rápidos pasan tras el
ajuste. Hashes y alcance de revisión: `build-reports/flow-deep-links/native-login-reviewed-4778124c.json`.

La nueva lectura pasiva `read-owned` comprueba el snapshot cifrado y su propietario,
sin restauración ni escrituras; sólo devuelve datos privados por socket. La guarda
instrumentada con clave efímera y datos sintéticos pasa en el AVD dedicado:
`build-reports/flow-deep-links/android-owned-read-guard-custody-9d3ae6f8`, APK de test
`6faaad816709dae79d6bf85beecdc2578dfcde30ee4d654030c2f6c871c0866d`.
Probe vacío final, forward retirado y cierre completo. No es evidencia de login.
El primer intento, compilado sin seleccionar el runner pasivo, no llegó a iniciar
la instrumentación; se conserva en `android-owned-read-guard-9d3ae6f8` junto a la
restauración verificada del APK anterior y reconciliación previa a la repetición.

### Android: sesión propia y enlace externo, 11 de septiembre de 2026

GO local focal revisado: producto `cd1a58397e494463af24f85ba14cd2215039ce52`,
APK `ce0dd68de86cbe551d65bd71f661ddab2f0dd685d25a525d06f962634c6e16e9`,
runner `be023389`. Ensayo `de69ad86-edff-4bf6-8dcb-aa71e6a0c7a5`, directorio
`build-reports/flow-deep-links/android-owned-chat-17fe28d1-a5a0-4056-97b9-bbc956a747c2`.
Emisor externo de otro UID, Intent HTTPS implícito sin package/component y dominios
verificados; hilo propio `2570`, mensaje `11202`. Frío sin proceso previo y caliente
con PID `5916` conservado: foco exacto descubierto, cuerpo propio, composer y vuelta
al Feed sin reapertura durante los dos segundos observados. Orquestador y revisor
independiente inspeccionaron las cuatro capturas focused/back.

La sesión fue importada mediante custodia instrumentada pasiva y canal privado;
no acredita login nativo. Install, clear y probe vacío final quedaron verificados;
forwards retirados y lease cerrado. El informe original conserva
`failed_cleanup_pending`: Android creó un registro Push y estado de Novedades del
fixture, rechazados por el limpiador anterior. `reconciliation.json` acredita cierre
posterior completo mediante retiro focal revisado, snapshot DPAPI previo, auditoría
de identidad/referencias y verificación de ausencia. La carpeta privada quedó vacía;
no se convierte retrospectivamente el cierre automático fallido en éxito.

Antes hubo un rechazo de preflight por terminadores CRCRLF de ADB, sin abrir custodia
ni crear fixtures. Se conserva el diagnóstico y su regresión. Contratos de custodia
nativa 30/30 y de retiro/coordinador 19/19 pasan. No hay GO integrado: quedan los casos
Android de continuación tras login/negativos y la certificación exacta de la candidata en todas las
plataformas. El estado Android anterior siguiente se conserva como procedencia.

Sobre el mismo APK, el emisor externo observa además cancelación del aviso anónimo
y apertura de Login seguida de Back, ambas en frío y caliente. Reportes locales:
`build-reports/android-external-sender/anonymous-cancel-9fdca245-749e-4cdc-82e3-b25ed53f863d`
y `build-reports/android-external-sender/anonymous-open-login-back-03ba50db-a6fa-4b6b-94bc-e934c5f0cef6`.
Cada pareja conserva su PID caliente (`6494` y `6849`) y parte sin proceso en frío.
Las diez capturas muestran barrera, formulario vacío cuando corresponde y regreso
al Feed; no se observa reapertura durante dos segundos. Los probes inicial/final
verifican sesión vacía y ambos cierres tienen `cleanupComplete: true`.
Fuente del emisor SHA-256 `be391b941b3b4bcfe13436bacf9bdb7e612facb62257f1ac0266c03a3dc7382e`.
El enlace apunta al fixture ya eliminado: acredita la barrera y sus salidas, no
lectura de Chat, autenticación completada ni tratamiento autenticado de inexistentes.
Revisión independiente del commit `a0f044f3`, recibos y diez capturas: GO local
acotado de ambas salidas anónimas, sin GO integrado.

### Android: hilo inexistente, 12 de septiembre de 2026

GO local focal revisado con el mismo producto `cd1a5839` y runner `edb67fd5`.
Ensayo `5ee9e1fc-bab9-4748-aff2-fa4fe7f7f301`, directorio
`build-reports/flow-deep-links/android-owned-missing-thread-9a259bae-d653-43c2-938d-6a275d190827`.
El coordinador verifica ausencia SQL del hilo `5033360217389` antes/después de
entregar el enlace externo al mensaje `1132740146538`. Frío sin PID previo,
caliente con PID `7544` conservado: error localizado de lectura, sin cuerpo propio
ni foco del mensaje, Back al Feed y dos segundos sin reapertura observada.
Orquestador y revisor inspeccionaron las cuatro capturas error/back; Retry está
visible, pero no se pulsó. No acredita login nativo, código HTTP ni mensaje ausente
en hilo existente. Proceso terminal 0, `passed`, `cleanupComplete: true`, custodia
retirada, carpeta privada vacía, lease ausente y forwards vacíos. El inventario
y la aceptación integrada siguen pendientes.

### Android: mensaje ausente en hilo propio, 12 de septiembre de 2026

Ensayo `3017f423-3689-47c5-af2e-8f6699c2f9d4`, producto `cd1a5839`, runner
`82b011c1`, directorio
`build-reports/flow-deep-links/android-owned-missing-message-5fc7ca89-923a-427d-9a2b-024c064c581a`.
Entrega externa fría sin PID previo y caliente con PID `8730` conservado. El
mensaje solicitado `1930448500090` no existe en el hilo propio, verificado por
el coordinador antes/después. Se observa el mensaje de control y composer,
cinco segundos sin foco y Back al Feed sin reapertura durante dos segundos.
Las cuatro capturas muestran el control sin resaltar y Feed tras salir; revisión
independiente visual favorable, GO local acotado. No acredita historia agotada, aviso explícito
de inexistencia ni login nativo. Proceso terminal 0, `passed`, limpieza completa,
carpeta privada vacía, lease ausente y forwards retirados. Sin GO integrado.

### iOS: continuación caliente tras Login nativo, 12 de septiembre de 2026

GO local focal revisado: producto/runner `8906d274e31caf5fc837c688b3c524551660669b`,
app `68434a3c556dd33b954bf7dca11a049a55fea5df0d636ba915eefc9f8b45d845`,
UI runner `f362da77669e37a9dbb51565da000265f739cda8df3393e61e09b5e38ae96052`.
Ensayo `f35dc65f-a496-43df-a84e-d82123754077`, directorio
`build-reports/flow-deep-links/ios-native-login-warm-b95ad735-38ff-4e96-a897-7612a82d579a`.
Tras arranque público previo, entrega externa custom scheme con PID `68587`
conservado: barrera sobre Feed, un Submit real y mensaje `11221` del hilo `2585`
resaltado con el cuerpo exacto del run. Back muestra Chats sin Login ni reapertura
durante dos segundos. Orquestador y revisor inspeccionaron las tres capturas
`visual/{gate,focused,back}.png`; hashes verificados contra la exportación privada.

Preflight verificado, custodia nativa y cierre completos: lectura pasiva propia
persistida antes del ACK, clear del snapshot exacto, fixtures retirados y carpeta
privada vacía. Proceso terminal 0 y simulador dedicado apagado; estable preservado.
No acredita un proceso existente antes del arranque público del preparador,
Back a Feed, Universal Links, sesión vencida/revocada ni candidata integrada.
La renovación fría sobre este mismo producto se registra a continuación.

Los intentos calientes `8bf71611-6dc8-45d8-bbcc-5d8bf36362ce` y
`739de40e-5f47-4af3-adc7-75d3d1a00715` conservan sus reportes fallidos y
reconciliaciones `closed`, con limpieza completa y observación no aceptada,
en los directorios `ios-native-login-warm-78207bfa-944d-4487-858c-088608a3fea3`
y `ios-native-login-warm-767efe06-b695-4c5c-a904-3b2536648bcb` bajo la misma raíz.
En el segundo, el diagnóstico midió 60,85 segundos en la autolectura de clipboard,
seguida de cambio de versión; no prueba el mecanismo del sistema. El runner
`8906d274` sustituye esa autolectura por guardas de escritura/versión y conserva
verificación de campos, expiración de 60 segundos, limpieza y un Submit. El
sintético por barrera real `26d4cc23` pasó antes del ensayo aceptado, sin Submit.

### iOS: renovación fría tras Login nativo, 12 de septiembre de 2026

GO local focal revisado sobre el mismo producto `8906d274` y hashes de app/UI
runner indicados en el ensayo caliente anterior. Ensayo
`4dccd2c5-8b99-448d-ac36-a8734d99158e`, directorio
`build-reports/flow-deep-links/ios-native-login-cold-f5427b53-488f-4380-aaaa-f0e5815136c7`.
Entrega externa custom scheme sin proceso previo, observador preparado antes de
entregar y PID `70175` conservado. Un Submit real abre el mensaje `11222` del hilo
`2586`, resaltado con el cuerpo exacto del run; Back muestra Chats sin Login ni
reapertura durante dos segundos. Orquestador y revisor independiente inspeccionaron
las tres capturas `visual/{gate,focused,back}.png`, con hashes verificados contra
`visual-export.json`.

Preflight verificado, proceso terminal 0 y limpieza completa: custodia propia
persistida antes del ACK, snapshot exacto retirado, fixtures retirados y carpeta
privada vacía. Simulador dedicado apagado y estable preservado. No acredita
Universal Links, Back a Feed, sesión vencida/revocada ni candidata integrada.
La aceptación fría anterior conserva su procedencia y no se transfiere a este build.

### iOS: continuación fría anterior tras Login nativo, 12 de septiembre de 2026

GO local focal revisado: producto/runner `2e8a43e3bdbac962b48a1e9948384c2db900e456`,
app `36b9f65271f2255c676913bc9467dc45b6caf2530e1f80b04f06638460ec395d`,
UI runner `ff5881651e3941020ae1b65a2b053a44ba2dc2df1f0056b219cc5976e953e23e`.
Ensayo `495f54ae-63ee-43c1-8916-e8f6f21df459`, directorio
`build-reports/flow-deep-links/ios-native-login-cold-4e12c377-7756-488d-966a-51a4a4fda8a2`.
Entrega externa custom scheme sin PID previo; aviso sobre Feed y un Submit real
continúan al hilo `2581`, mensaje `11217` resaltado. El PID `56230` se conserva
entre entrega y observación de Login. Back muestra la lista Chats, sin Login ni
reapertura durante dos segundos; no acredita Back a Feed. Orquestador y revisor
independiente inspeccionaron las tres capturas `visual/{gate,focused,back}.png`.

El coordinador verificó una sesión Auth nativa propia, ninguna Web, lectura
pasiva persistida en DPAPI antes del ACK y clear del snapshot exacto. Cierre
terminal 0, retirada verificada de fixtures, directorio privado vacío y simulador
dedicado apagado; instancia estable preservada. No acredita recorrido caliente,
sesión vencida/revocada, Universal Links ni candidata integrada.

El ensayo caliente posterior `84356637-c71e-4585-96e8-7841bda305d4`, directorio
`build-reports/flow-deep-links/ios-native-login-warm-acf42c6e-78ef-4fdb-b3c9-6a209012a4d4`,
falló sin recibo de Login sobre esos mismos binarios. El reporte original conserva
`failed_cleanup_pending`; no se acepta la observación. La reconciliación posterior
verificó ausencia de sesiones Auth y efectos nativos remotos, sesión local vacía
mediante el probe pasivo, retirada de fixtures y de la entrada privada retenida.
`reconciliation.json` registra `closed`, `cleanupComplete: true` y
`observationAccepted: false`; revisión independiente favorable sólo al cierre.
Los logs y el resultado parcial originales se conservan. La corrección posterior
`d62f17a65fdddc1789ab7cef62305218eb16408f` sustituye la selección geométrica del
teléfono por el tag del editor. En ese checkpoint quedaban pendientes su validación
funcional y la repetición caliente; la renovación posterior se registra arriba.
La evidencia anterior no se transfiere a otro producto.

El ensayo anónimo preparatorio `8332e050` se conserva fallido: el contenedor
nativo apareció antes del contenido Compose accionable. Se archivó su plan y
se verificó cierre antes de repetir. La espera del botón concreto pasó en
`ios-native-gate-bd3c4ed0`, con revisión visual; no convierte el fallo previo en PASS.

### iOS: procedencia de la lectura privada tras Login

`5c43caff` añade `testReadOwnedNativeSession`: lectura pasiva del Keychain,
propietario y coherencia token/JWT/expiry, doble snapshot y respuesta privada
0600 de un solo uso. Revisión estática independiente favorable; no verifica
firma/vigencia remota ni ejecuta Login. En ese checkpoint faltaba conectar el
coordinador y el observador; el ensayo frío anterior registra su ejecución posterior.

Xcode `build-for-testing` correcto y dos guardas sintéticas ejecutadas, cero
fallos: propietario/tokens mezclados y comandos/permisos/repetición del intercambio.
Logs locales `build-reports/flow-deep-links/ios-native-read-{xcode-build,guards-xctest}.log`,
recibo `ios-native-read-guards.json`, step `5d45d576-ee6c-4fc1-928b-ca1752b28467`.
No leen una sesión real de Keychain. El simulador dedicado terminó apagado y la
instancia estable siguió abierta. Trece contratos focales y 485 rápidos pasan.
Los productos anteriores se conservaron en el Mac, directorio
`build/reports/ios/native-read-preparation-632e0898/products-before`; este rebuild
de pruebas no transfiere al nuevo binario las aceptaciones visuales anteriores.

El protocolo preparatorio `33b05a68` conecta la respuesta privada al canal SSH:
recibo acotado y validado, archivo 0600 conservado hasta ACK y todas las demás
operaciones bloqueadas mientras éste falta. El coordinador posterior guarda
primero la respuesta en DPAPI y después confirma; el ACK coteja los archivos
con su snapshot y sólo entonces los retira. La custodia sigue abierta hasta clear
exacto. No es prueba de persistencia DPAPI ni de sesión real. Revisión independiente
favorable, 23 contratos Node y cuatro pruebas Python sintéticas en Mac pasan,
además de los 485 contratos rápidos. Logs `native-owned-read-channel-contracts.log`
y `ios-owned-read-protocol-python-tests.log` en `build-reports/flow-deep-links`.
Worker sincronizado al Mac con hash
`30770f8ae5ff0b320a4b2093ff6c2b2e66ed9499c23e06987d0419fc68ed501b`;
no se ejecutó contra Keychain real ni se atribuye aceptación funcional.

### iOS: sesión propia renovada, 12 de septiembre de 2026

GO local focal revisado: producto `b6f4e49eb9f6a9cef8fc065abe5d27a709ec508c`,
app `88dd4833755d11383ac69e8872063d09a32be9279f48ae3c55397cbca15243ff`.
Framework Raster x86_64, host SimulatorSigned, recursos y firma pasan. Ensayo
`ef8d1123-92dd-43d0-9b8a-6b26c9f54609`, directorio
`build-reports/flow-deep-links/ios-owned-chat-5b83e6bf-bb53-4c1e-b46f-3baa2d978ff7`.
Custom scheme externo al hilo `2572`, mensaje `11204`: frío sin proceso previo,
PID `17240` conservado hasta completar frío/caliente y observadores terminales 0.
Las cuatro capturas revisadas muestran cuerpo propio resaltado, composer y Back
al listado Chats. Proceso general 0, preflight verificado y cleanup completo;
privado Windows vacío y simulador dedicado apagado, estable conservado.
Es sesión importada por custodia, no login nativo ni Universal Links. Tampoco
es aceptación integrada. Fingerprints completos y recibos en el informe del run.

Renovación anónima sobre la misma app: run
`ae415f1d-f782-4b1d-b0e0-fd380075e3ea`, informe y seis capturas en
`build-reports/flow-deep-links/ios-anonymous-ae415f1d`. Barrera de acceso, Login
vacío y cierre al shell con Feed seleccionado pasan en frío y caliente; PID
`23417` conservado desde entrega fría hasta final caliente. Ambos observadores
estaban preparados antes de entregar y terminaron 0. Probes inicial/final vacíos,
proceso general 0 y simulador dedicado apagado, estable conservado. La captura
fría final tiene centro negro: acredita shell/selección, no publicaciones cargadas;
la caliente muestra tarjeta JO sobre fondo degradado, sin foto: no acredita carga
completa del contenido Feed. Orquestador y revisor independiente inspeccionaron
las seis capturas: GO local para barrera, Login vacío y cierre. No hubo Submit ni mutaciones de backend;
no acredita continuación tras autenticación, cancelación directa del aviso,
Universal Links ni GO integrado.

Preparación de mensaje ausente iOS: observador `8e22e6f5`, contratos Node 15/15,
worker macOS 5/5, build-for-testing correcto y 485 contratos rápidos pasan. La app
conserva el hash anterior. El primer run `3bef5855-f031-4f36-9f95-767e3c4adfe4`
falla antes de observar el negativo: el control existe mientras el splash aún
lo cubre y no es pulsable. Directorio
`build-reports/flow-deep-links/ios-owned-missing-message-bf3b2359-eed9-4ced-856f-13cd9fb2c266`.
Se conserva informe original, XCTest fallido, jerarquía y grabación. La sesión fue
retirada por clear exacto verificado; una segunda fase de reconciliación corrigió
el callback del auxiliar histórico sin repetir el clear, y completó la retirada
remota. `reconciliation-resume.json` acredita cleanup completo, privado vacío y
simulador apagado. Corrección `68219dec`: esperar control pulsable y splash ausente
antes de las aserciones. No se atribuye aceptación al ensayo fallido.

El ensayo corregido `dea47d2b-4b49-4ebf-aacc-64c284aee4a2` pasa frío/caliente,
con runner `68219dec`, app idéntica y ejecutable de tests
`fc3b02b95f4c6b64af65f3a1b9589c15423d47187320ff5cfd3d390564b227eb`.
Directorio `build-reports/flow-deep-links/ios-owned-missing-message-a80cc8d1-0a30-41b1-bd6f-9003a2515103`.
Hilo propio `2575`, mensaje solicitado ausente `6331512187353`, control visible;
ausencia verificada por el coordinador antes/después. Frío sin proceso previo,
PID `27126` conservado en caliente, cinco segundos sin selección y dos segundos
sin reapertura tras Back al listado. Las cuatro capturas inspeccionadas por el
orquestador muestran control sin resaltar, composer y listado Chats. Revisión
independiente estática y visual favorables: GO local acotado. Proceso general 0, limpieza
automática completa, privado vacío y simulador dedicado apagado. No acredita
historia agotada, aviso explícito de inexistencia, login ni GO integrado.

Procedencia iOS Chat anterior: **aceptación local de custom scheme con sesión válida
importada, frío/caliente y vuelta**, producto `edbb970b`. Los runs
`6b05776c-d8e7-4694-a155-f6a27cd1141f` y
`75ea641f-a3d9-4410-b9d6-8afffcdcbb6c` fallaron y quedaron completamente
reconciliados. El segundo acredita entrega fría al proceso y su jerarquía
guardada contiene la burbuja exacta seleccionada; falló un selector que exigía
igualdad con la etiqueta Compose, mientras iOS añadía los textos hijos.
Corrección del observador revisada en `a9a86189`, build-for-testing y watchdog 0.
Se mantienen producto y duración del resaltado. El ensayo corregido se registra en
`build-reports/flow-deep-links/ios-owned-chat-7caf2a5a-03e2-472a-bb61-c4c6d5864285/`;
run `b5d489cb-8fae-420b-b861-af32a06b6152`, terminal 0, PASS y cleanupComplete
true. Los dos XCTest/watchdog terminaron 0; PID 40099 conservado tras entrega
y durante el tramo caliente. Las cuatro capturas inspeccionadas por orquestador
y revisor muestran mensaje exacto resaltado y listado al volver. Private Windows
vacío, inputs nativos retirados y simulador apagado. El plan de sesión conserva
fingerprints y límites: no acredita login nativo, sesión inválida, Universal
Links ni reapertura tras reiniciar. Sin GO integrado ni promoción de vecinos.

Contrato iOS adicional: cancelación de URL Chat por callback UIKit seguida de
activación del estado autenticado y factory tardía, sin recuperar hilo/mensaje;
una URL nueva distinta sigue funcionando. Run `5cffdc90-effe-4f40-b6e7-e721c089c7a0`,
cuatro contratos sintéticos PASS, producto iOS `edbb970b`, sin sesión real ni
mutaciones. No cierra el E2E de cancelación/login ni acredita gestos del diálogo.
Detalles y hash de test en el plan de sesión.

E2E iOS anónimo corregido en producto `c311f281`: entrega externa fría, barrera
real, apertura de Login y botón nativo de cierre visible/pulsable, retorno al
shell con Feed seleccionado. Run `a90d6cc7-33eb-4172-929b-b307cf69d7cd` PASS,
PID 46714 estable; Keychain vacío antes/después, simulador apagado. La captura
final tiene contenido central negro: no prueba publicaciones cargadas. No es
login real, cancelación directa del aviso, entrega caliente ni GO integrado.
Los ensayos previos fallidos conservan procedencia; detalle en el plan de sesión.

iOS Chat inexistente: run `10e8f5ec-e82a-4f04-9f72-1223a25d0f4c`, producto
`c311f281`, runner `f75f19de`, GO local revisado frío/caliente y vuelta al listado. Ausencia
del hilo verificada en DB antes/después; PID 48023 conservado. Cuatro capturas
revisadas muestran un error localizado con Retry y salida, sin códigos técnicos
ni mensajes. Sesión temporal retirada y cleanupComplete true, private vacío.
No se ejecutó Retry ni se verificó el código HTTP nativo. Sin GO integrado.

Preflight Android renovado sobre fuente `302542a4`: `:app:assembleDebug` PASS
en 1m50s, APK SHA-256 `635daf1b12c598081956deb4828f47a33de9823ffbc683fac962a3e7f1b3dc20`.
Instalado correctamente en AVD nuevo y aislado `QuataDeepLinksApi35`,
`emulator-5560`, después de confirmar `sys.boot_completed=1`. El primer intento
de instalación durante el arranque falló por servicio de almacenamiento aún
inicializándose; no se interpreta como fallo del APK. `pm get-app-links` devuelve
`verified` para `egquata.com` y `www.egquata.com`. No había proceso de Qüata.
La apertura con `adb shell am start` no se ejecutó: el control automático de
ejecución la rechazó como `blocked by policy`, incluso sin detener la app.
No se eludió mediante otro mecanismo. Reporte local
`build-reports/flow-deep-links/android-302542a4-preflight.json`.
Build, instalación y verificación de dominios no acreditan recepción de la URL.

- Android: existen build e instalación previos, pero falta completar la recepción
  real fría/caliente de los tres tipos y sus salidas sobre la candidata. Un build
  o entrega explícita al paquete no acredita App Links público/chooser.
- iOS: existe la corrección y pruebas del orden de restauración/entrega del enlace,
  pero faltan recorridos externos reales completos en el host. Se valida custom
  scheme; no inferir Universal Links sin Associated Domains.

Avance iOS tras el reinicio: producto `12128cb0`, framework raster x86_64
`5ba878e424dc76b6767e3f58bae6ea93b9dd50e68d3804ba434a91c87b445ae1`.
Framework, build-for-testing SimulatorSigned, recursos y firma pasan. En el
simulador dedicado `F2E1EA50-FBAD-443C-A98F-2A576C14C70B`, `simctl openurl`
entrega el enlace Feed `e3aa9c1e-a458-4d3b-a35e-4cbd3b4e858b` y aparece el aviso
SpringBoard. El observador XCTest nuevo pasa 1/1, sin iniciar ni activar la app:
gestiona opcionalmente Abrir/Open y observa `feed.detail.chrome`, sin host Auth.
La captura posterior inspeccionada muestra detalle de publicación, autor JO y
la imagen esperada. Esto no prueba por sí solo ID exacto, entrega caliente,
consumo único ni salida. Revisión independiente estática aprobada con corrección
del comentario para no afirmar que un aviso opcional se verifica siempre.
Resultado remoto: `build/reports/ios/deep-links-external-feed-observer-12128cb0.xcresult`;
capturas locales: `build-reports/flow-deep-links/ios-feed-{first,observed}-12128cb0.png`.
El observador se compiló como adición de test sobre ese producto; no se transfirió
la evidencia de otros targets a esta comprobación.

Oficial caliente sobre el mismo producto iOS: enlace a
`9779260c-e5b8-488e-aa04-0c11cc33654e`, con PID `3225` idéntico antes de
`simctl openurl`, después de la entrega y después del observador XCTest (1/1 PASS).
La captura `ios-official-warm-12128cb0.png` muestra «Lanzamiento musical» y su
detalle. La extensión opt-in `CHECK_BACK`, revisada independientemente, pasa 1/1:
pulsa `official.detail.back`, exige desaparición del chrome y presencia del host
Oficial. `ios-official-back-12128cb0.png` confirma visualmente el listado sin
cabecera de detalle. Resultados remotos `deep-links-external-official-observer-12128cb0.xcresult`
y `deep-links-external-official-back-12128cb0.xcresult` en `build/reports/ios`.
La posterior orden `simctl launch` sin URL conserva PID; no equivale a un ciclo
completo de segundo plano ni acredita persistencia tras terminación/reinicio.
No se ha reproducido vídeo ni ejecutado acciones de escritura.

Feed caliente y vuelta: mismo PID `3225` antes/después de entregar el enlace y
tras el observador con `CHECK_BACK`; 1/1 PASS en
`deep-links-external-feed-back-12128cb0.xcresult`. Sus dos adjuntos exportados a
`build-reports/flow-deep-links/deep-links-feed-back-attachments-12128cb0`
se inspeccionaron: detalle con JO/imagen esperada y Feed sin cabecera de detalle.
Después se terminó la app, se comprobó ausencia de su entrada launchctl y se
abrió sin URL (nuevo PID `4535`). La captura `ios-feed-relaunch-12128cb0.png`
muestra Feed sin reapertura del detalle. Es una observación de ese arranque,
no una garantía temporal indefinida ni una prueba de otros destinos o sesiones.

Oficial frío: se termina la app, se comprueba ausencia de proceso y se entrega
el enlace antes del observador. `deep-links-external-official-cold-12128cb0.xcresult`
pasa con vuelta; sus dos capturas en `deep-links-official-cold-attachments-12128cb0`
muestran «Lanzamiento musical» en detalle y luego el listado, inspeccionadas.

Feed inexistente, entrega sobre app en ejecución: el observador ahora permite
exigir un texto público concreto antes de capturar. Con el ID
`00000000-0000-4000-8000-000000000001`, espera el mensaje completo «Esta publicación
ya no está disponible.» y comprueba vuelta. Pasa en
`deep-links-external-feed-missing-12128cb0.xcresult`; adjuntos inspeccionados en
`deep-links-feed-missing-attachments-12128cb0` muestran mensaje/Reintentar y Feed
tras volver. No acredita todavía reintento, arranque frío ni continuidad de PID
para este caso. La revisión independiente aprueba la espera textual con el límite
de que presencia accesible no sustituye revisión visual ni acredita un ID por sí sola.

Renovación de inexistentes completada sin cambiar el observador: Oficial sobre
app en ejecución pasa mensaje terminal y vuelta; después Feed y Oficial pasan
desde app terminada, comprobando ausencia de proceso antes de cada URL.
Resultados 1/1 por ensayo: `deep-links-external-official-missing-12128cb0.xcresult`,
`deep-links-external-feed-missing-cold-12128cb0.xcresult` y
`deep-links-external-official-missing-cold-12128cb0.xcresult`.
Los seis adjuntos en `deep-links-official-missing-attachments-12128cb0` y
`deep-links-{feed,official}-missing-cold-attachments-12128cb0` se inspeccionaron:
mensajes terminales visibles y vuelta a los listados. Reintentar está visible,
pero su ejecución sigue pendiente; tampoco se infiere continuidad de PID en los
ensayos de inexistentes sobre app en ejecución. El límite previo de frío pendiente
queda resuelto para esos dos destinos, sin promoción de Chat ni de toda la unidad.

Enlaces iOS sin ID (`#post-`, `#official-`, `#chat-`), mismo producto: las tres
entregas calientes conservan PID y el listado Oficial; capturas inspeccionadas
`empty-{post,official,chat}--warm-12128cb0.png`. El dispatcher rechaza el destino
ausente y el host ignora ese resultado, por lo que no se exige redirección a Feed
en caliente. En frío se terminó la app y se comprobó ausencia de proceso antes
de cada entrega. La captura de Chat a los cinco segundos muestra Feed; las de
post/official aún muestran splash y no acreditan el resultado final. Se conservaron
y se repitieron únicamente esos dos arranques, con captura a los quince segundos:
`empty-{post,official}--cold-after-wait-12128cb0.png`, ambas con Feed descubierto.
Las ocho capturas están en `build-reports/flow-deep-links` y se inspeccionaron.
Son observaciones visuales acotadas, sin aserción automática de finalización ni
garantía temporal indefinida; no cubren todas las clases de URL malformada.
No se modificó producto ni se hicieron mutaciones de cuentas o backend.

## Evidencia descartada y procedencia

El primer build de producción local sobre `b6f4e49e` (11 de septiembre) también
queda descartado: el enlace Feed resuelve el ID exacto, pero Back falla con
`illegal cast`. Reportes `web-feed-semantic-back-b6f4e49e.json` y
`web-feed-diagnostic-back-b6f4e49e.json` en `build-reports/flow-deep-links`.
El ensayo de diagnóstico con Wasm sin optimizar reproduce el fallo en
`FeedScreenHost`, con mapa de fuente en la línea de `remember { SnackbarHostState() }`;
no demuestra que ese estado sea la causa. La distribución fallida se conserva en
`wasm-b6f4e49e-failed-distribution`. Se aplica la recompilación completa prescrita
por el backport antes de aceptar otro fingerprint; no se atribuye todavía causa
exclusiva a caché, compilación incremental u optimización.

La recompilación completa termina PASS (163 tareas ejecutadas) con Product SHA
`edb67fd56155cd4ff3585c841c40f98bf1d26fba` y fingerprint
`581fa8f9eb6a5a3b4704929a08e20b972fc1ff2a75335fddc3af956866dea044`.
Entre `b6f4e49e` y `edb67fd5` sólo cambiaron observadores/tests y documentación;
las fuentes de producto, Gradle y backport son iguales. El recorrido repetido
resuelve el mismo post, vuelve a `#feed` y recarga sin detalle ni errores de página.
GO local acotado tras revisión independiente del informe y las tres capturas:
detalle antes de Back, Feed después de Back y Feed tras recarga. La vinculación de
hashes está en `build-reports/flow-deep-links/web-feed-full-rebuild-review.json`.
El sufijo `b6f4e49e` de los archivos conserva la etiqueta inicial del build;
el informe y la identidad registran el Product SHA real. No acredita todavía
aceptación integrada, ni aísla la causa del fallo previo.

El recorrido caliente posterior sobre esa misma distribución también tiene GO
local revisado: parte de Feed, entrega el hash del post exacto y conserva el mismo
documento tras foco y Back. Recarga sin detalle, cero errores y recursos cerrados.
Informe y tres capturas `web-feed-warm-verified-*-b6f4e49e` inspeccionados por
orquestador y revisor independiente; fingerprints y hashes vinculados en
`build-reports/flow-deep-links/web-feed-warm-verified-review.json`. Se conserva
el ensayo caliente anterior, cuya comprobación de identidad era sólo inmediata
a la entrega. Ninguno certifica la candidata integrada.

El bundle anterior `139a381f…` fallaba al volver del detalle Feed con `illegal cast`.
El mismo código y backport pasan tras recompilación completa. No se ha aislado
una causa exclusivamente incremental frente a caché/reutilización de tareas.
La receta de [backport](../third_party/compose-ui-web/README.md) recoge la
recompilación requerida al introducirlo o sustituirlo. No reutilizar aquel bundle
fallido como candidata ni trasladar su evidencia al nuevo fingerprint.

Un ensayo anterior de Chat devolvió PASS con el splash en las capturas; se conserva
como contradicción detectada. Producto y runner se corrigieron para exigir foco
con la conversación descubierta. No se considera aceptación de ese ensayo.

El [historial focal](FLOW_DEEP_LINKS_CHAT_FIXTURE_PLAN.md) detalla commits, runs,
comparaciones, límites y rutas locales de reportes. Los resultados vigentes están
en `build-reports/flow-deep-links/web-public-full-rebuild-8cde7edf` y
`build-reports/flow-deep-links/web-chat-clean-1b145e22-ac3a-4636-8ed2-83fcffd2ee14`.

## Cancelación del acceso anónimo Web

Sobre `c252e000` / distribución `ca9990b2840087bfcb31508d65968a722bf7fdb38e8af594a8b7f1e0fc9019c4`,
el enlace Chat caliente muestra «Ya tengo cuenta» en el mismo documento, sin
peticiones privadas ni errores. Escape no cerró el diálogo; no se presupone que
equivalga a Back en Compose Web. La búsqueda por rol `dialog` no encontró el nodo.
El descubrimiento posterior de ancestros del botón sí encontró el contenedor
visual, pero el observador de pulsación exterior falló antes de pulsar al buscar
«Registrar», cuando el código Web usa «Crear cuenta». Se conserva el diagnóstico
`chat-anonymous-semantic-backdrop-cancel-report.json` junto a
`chat-anonymous-dialog-discovery.json` en `web-feed-missing-c252e000`.
Todos los recursos se cerraron y no hubo mutaciones. La cancelación continúa
pendiente: estos fallos del observador no demuestran un fallo de producto.
Se detiene la cadena de intentos de interacción; antes de promover otro runner,
reconciliar el contrato real y las anclas semánticas del diálogo común.

Reconciliación completada: se inspeccionó `QuataAuthRequiredDialogContent` y el
DOM del ancestro del botón de login. El título y «Crear cuenta» identifican el
contenedor visible; el punto exterior se deriva de sus límites actuales, sin
coordenadas fijas. `observe-chat-contract-backdrop.mjs` pasa con una pulsación:
desaparece el diálogo, se resuelve Feed con hash vacío y no reaparece al recargar.
Reporte `chat-anonymous-contract-backdrop-cancel-report.json` y capturas
`chat-anonymous-contract-backdrop-{cancelled,reloaded}.png` en la misma carpeta;
ambas capturas muestran Feed descubierto y fueron inspeccionadas.
La revisión independiente acepta el ensayo focal sin nuevas anclas de producto.
Su alcance es producto `c252e000` y fingerprint indicado, no un head posterior
por inferencia. Cero errores y cero solicitudes a las cuatro rutas vigiladas
(`chat_threads`, `chat_messages`, `messages`, `conversations`); esto no prueba
ausencia universal de peticiones privadas. `mutations: 0` describe el recorrido
sin credenciales, no un contador instrumental de backend. Los recursos se cerraron.
No acredita continuación tras login, autorización de Chat ni cancelación iOS/Android.

## Cierre aún requerido

Ensayo real de continuación Web, runner `906b1e43`, producto `c252e000` y
distribución `ca9990b2…`: run `9e77e8d7-8e88-4623-ae9c-227fe51f5312` PASS,
proceso PID 19620 terminado con código 0. Contexto anónimo → enlace → aviso →
Login → repositorio real del producto → hilo `2528`, mensaje `11158`, un solo
episodio de foco descubierto, mismo documento y cero errores. Las capturas del
aviso y resultado se inspeccionaron: el mensaje propio está resaltado sin splash
ni UGC cubriéndolo. Carpeta local
`web-auth-resume-13840676-6811-4a5e-af1a-b561d1776fd9` bajo `build-reports/flow-deep-links`.
Reporte `cleanupComplete: true`; carpeta privada vacía tras verificar retiro de
las sesiones, hilo y perfiles propios. Preflight conservó fingerprint DB y hashes
de auth-bridge v79 / push v85. No hubo despliegue. Login por bridge de repositorio,
sin atribuir escritura ni Submit manual del formulario. No acredita sesión
expirada ni Android/iOS. La mención genérica a cancelación en `limits` del reporte
no corresponde a este modo y no se usa como evidencia de cancelación.

Primer ensayo real de login posterior a cancelar: run
`63814d6c-b1b2-4787-8db0-d75f9f08a32f`, carpeta
`web-auth-cancel-822edf28-7895-44de-9a49-d000d87a3f1c`, falla en observación de UI
sin errores de página. Proceso PID 23356 terminado con código 1; limpieza completa
y carpeta privada vacía. No demuestra aún defecto de producto: el reporte no
identificaba qué condición falló. Se añadió diagnóstico de etapa, estado readonly
acotado a 2 s y captura acotada a 5 s, conservando la respuesta real para registrar
recibo y limpiar. Revisión independiente aprobada y cinco tests Chrome verdes.
El reporte original se conserva y no se convierte en PASS.

Caso real de cancelación finalmente verificado con la política de arranque:
run `cae2c6b3-7bf9-40be-9fbb-df8aa55f17c0`, runner `836cdc21`, producto
`c252e000` / `ca9990b2…`. Carpeta
`web-auth-cancel-startup-cf3799f9-75f8-4178-bcdf-40cb1acfb6e4` bajo
`build-reports/flow-deep-links`. Anónimo → enlace → cancelar → Login posterior
real → Novedades → cierre por ancla → Feed. Mismo documento, sin recarga ni
navegación forzada a Feed, cero episodios de foco desde el inicio y cero errores.
Se inspeccionaron las tres capturas: aviso, Novedades y Feed final. Proceso PID
24080 termina con código 0 y `cleanupComplete: true`; carpeta privada vacía.
Se verifica sólo la ventana observada (2 s tras cerrar Novedades), no una garantía
indefinida. No acredita formulario manual, sesión expirada ni otras plataformas.
Revisión independiente de capturas, reporte y cierre: GO local para este recorrido,
igual que para la continuación tras login. No es GO integrado de FLOW-DEEP-LINKS.

Segundo ensayo de cancelación, con diagnóstico: run
`750f5cde-bccc-4f04-9aca-b9a42a2d74a0`, carpeta
`web-auth-cancel-diagnostic-1931ddff-35f9-4d28-a1de-4f1914d56cf1`, termina con
código 1 y limpieza completa; carpeta privada vacía. Falla `final_state` porque
la ruta es `whats-new`, sin destino Auth ni episodios de selección y con login
real confirmado. La captura inspeccionada muestra Novedades del primer acceso.
El contrato de arranque de Main presenta esa pantalla al volver autenticado a
Feed; no la presenta al continuar al Chat. Se conserva el ensayo como fallido.
La revisión independiente confirma que debe contemplarse la presentación opcional,
cerrarla una vez por `whats-new-dismiss` y exigir después Feed sin foco residual
en el mismo documento. No se suprime Novedades ni se cambia producto para el ensayo.

Runner adaptado al contrato: observa hasta 5 s la presentación opcional, cierra
una vez por su ancla y comprueba Feed durante 2 s, además de exigir cero episodios
de selección desde el inicio. Todo queda bajo el plazo global de observación.
Una señal de expiración impide pulsaciones tardías tras esperar captura/bounds.
Revisión independiente aprobada; siete casos Chrome verdes y el negativo focal
renovado confirma que bounds comenzó y terminó después del plazo, manteniendo
el contexto vivo, sin emitir el click tardío. Reporte
`web-auth-no-late-click-tests-e9adf1a7.log`. No es todavía aceptación real del caso.

Oficial existente Web renovado en `c252e000` / `ca9990b2…`, destino
`9779260c-e5b8-488e-aa04-0c11cc33654e`, «Lanzamiento musical». El primer
observador encontró el título tanto en chrome como en tarjeta: se acotó al
chrome. `official-existing-scoped-report.json` conserva el recorrido frío
completo, con respuesta focal HTTP 200, ID/título, vuelta y recarga; su resultado
global es fallido porque el tramo caliente esperaba incorrectamente otra
petición focal. `OfficialFeedViewModel` reutiliza el post ya presente en lista.
`official-existing-warm-cached-report.json` pasa el tramo caliente: listado
HTTP 200 con el ID/título exactos, tarjeta semántica del ID visible, chrome,
mismo documento, vuelta y recarga sin detalle. Cero errores; recursos cerrados.
Los seis PNG `official-existing-{cold,warm}-{detail,back,reload}.png` en
`web-feed-missing-c252e000` se inspeccionaron. Muestran contenido y navegación,
con placeholder de vídeo: no se atribuye reproducción ni carga de thumbnail.

La revisión independiente del pendiente de autenticación de Chat identifica dos
recorridos aún necesarios con fixture propio: (1) anónimo → enlace → login real
→ hilo/mensaje exactos con foco descubierto; (2) anónimo → enlace → cancelar →
login posterior → Feed sin hilo/foco residual, comprobado antes de recargar.
Reutilizar el fixture y sus recibos, con ticket registrado antes de cada login
y restitución verificada entre casos. La preparación actual inyecta sesión;
no sirve para demostrar esas transiciones. El bridge `__quataAuthE2eProduct.login`
atraviesa repositorio y `completeLogin`, pero no acredita escritura/Submit manual.
No usar `restore()` ni navegación manual al destino después del login, porque
ocultarían fallos de continuación. No se han ejecutado esas mutaciones todavía.

Preparación del coordinador para esos casos: `loginDeepLinkSession` admite un
transporte de login separado del transporte que verifica el recibo. La auditoría
del propietario y de ausencia de sesiones, y el checkpoint `requestStarted`,
siguen precediendo al callback. La pareja opt-in `ui.prepareLogin` / `ui.requestLogin`
prepara el destino propio antes de autenticar; el recorrido de sesión inyectada
conserva su orden anterior. Quince tests focales pasan, incluidos respuesta perdida,
prohibición de repetir ticket, transporte independiente y configuración incompleta.
Revisión independiente estática sin bloqueantes. Falta el adaptador browser y su
validación: debe devolver la respuesta real de un solo login, limitar la espera
y conservar incertidumbre si pierde respuesta o quedan operaciones pendientes.
Esto es preparación del runner, no evidencia de autenticación ni aceptación E2E.

Adaptador browser conectado, opt-in `authenticationMode: resume | cancel`:
contexto nuevo sin inyección de sesión, aviso real, entrada a Login y llamada al
bridge del repositorio de producto. Observa la respuesta HTTP de un solo login
y coteja los campos que envía realmente Web (sin exigir `profile_id` en petición;
la identidad final sigue verificándose en respuesta/recibo). Captura el foco
antes de verificar recibos remotos para no perder su estado transitorio.
La observación está acotada a 45 segundos; si falla o se bloquea, devuelve la
respuesta recibida para registrarla y no permite que una tarea tardía publique PASS.
Revisión independiente estática aprobada tras esas dos correcciones.
Validación local sintética: cinco tests del transporte y cinco de Chrome pasan,
incluidos foco ausente, foco residual tras cancelar y evaluación bloqueada.
También pasan los seis casos del runner browser anterior. Reportes locales
`web-auth-bounded-tests-1314c81e.log` y `web-auth-adapter-tests-1314c81e.log` bajo
`build-reports/flow-deep-links`. Los casos Chrome usan HTML/backend sintéticos:
validan el runner, nunca sustituyen aceptación Qüata. Todavía no se han creado
perfiles ni sesiones reales para estos dos recorridos.

Feed existente Web frío renovado en `c252e000` / `ca9990b2…`: contexto nuevo,
URL inicial al post `e3aa9c1e-a458-4d3b-a35e-4cbd3b4e858b`, espera de aparición
y desaparición del splash, comprobación del ID resuelto y body exactos. Vuelta
por el ancla `feed.detail.back`, Feed sin detalle y recarga sin reapertura; cero
errores y recursos cerrados. `web-feed-cold-semantic-diagnostic-c252e000.json`
y las tres capturas `web-feed-cold-semantic-{back,after-back,after-reload}-c252e000.png`
en `web-feed-missing-c252e000` se inspeccionaron: detalle JO y Feed tras salir.
`sameDocument` en ese reporte sólo indica que no hubo recarga durante la
observación; no convierte este ensayo de URL inicial en entrega caliente.

Evidencia adicional actual: `build-reports/flow-deep-links/web-feed-missing-c252e000`.
`official-report.json` y capturas `official-*-missing.png` / `official-cold-back.png`
verifican el estado terminal, reintento y salida de Oficial, con inspección visual.
`malformed-visible-report.json` verifica seis resoluciones internas de enlaces
sin ID. Sólo sus tres capturas calientes acreditan Feed visible: las frías siguen
mostrando splash. El selector de ausencia de splash del observador no basta;
conservar ambos ensayos como diagnóstico y resolver la espera antes de aceptar
el caso frío. No hay cambio de producto ni mutaciones en estos ensayos.

Resolución posterior al reinicio del PC: `malformed-lifecycle-report.json` exige
que el splash aparezca antes de esperar su desaparición en el arranque frío.
Las tres capturas frías resultantes muestran Feed descubierto y se inspeccionaron
visualmente. Seis recorridos pasan, con mismo documento en caliente, recarga a
Feed, cero errores y recursos cerrados. La espera anterior podía acabar antes
de que Compose montara el splash; no era evidencia de un bloqueo del producto.

El reporte verifica HTTP 200 sin filas tanto al abrir como al reintentar; las
capturas fría y caliente muestran el mensaje de publicación no disponible y el
botón Reintentar. La captura de vuelta muestra Feed. La regresión de publicación
existente verifica destino exacto, mismo documento, vuelta y recarga sin detalle
ni errores. Recursos cerrados y cero mutaciones. Validación común: seis tests
focales y 57 tests totales de Feed, sin fallos ni omitidos; build Web correcto.

Completar los casos pendientes y plataformas; revisión independiente del head
exacto; correcciones; congelación; candidate-final; auto-merge; fast gates verdes
y jobs finales reales; integración certificada y reconciliación inmediata del
inventario con main. Ninguna de esas etapas se presume por los PASS locales.
