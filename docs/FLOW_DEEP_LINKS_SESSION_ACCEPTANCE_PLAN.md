# FLOW-DEEP-LINKS: aceptación pendiente de sesión Web

Estado: revocación real y renovación válida verificadas en Web frío sobre
`3bcfec15`. Sin GO integrado. Cada ensayo conserva su producto y distribución
indicados en su sección. No cambia el inventario.

El arranque de `web/src/wasmJsMain/kotlin/com/quata/web/Main.kt` llama a
`sessionForAuthenticatedRequest()`. `restoreLocalSession()` pertenece a otro
contrato: borra credenciales localmente vencidas y no solicita renovación.
Invocar `restore()` por el bridge no acredita el arranque real con sesión vencida.

## Dos casos distintos

1. **Metadatos locales vencidos, refresh válido.** Usar una sesión del fixture
   exclusivo con recibos verificados. Cerrar y reconciliar el contexto anterior;
   abrir otro con la misma sesión cambiando únicamente `expires_at` al pasado.
   Entregar el enlace de Chat en frío, observar la renovación real y exigir hilo,
   mensaje y foco exactos, sin splash que los cubra. Esto demuestra renovación
   provocada por metadatos locales, no vencimiento real del JWT.
2. **Sesión propia revocada.** Después de cerrar y reconciliar el contexto,
   revocar la sesión por sus recibos exactos y comprobar ausencia remota. Abrir
   otro contexto con las credenciales anteriores y el metadato vencido. Exigir
   rechazo real del refresh, barrera anónima y ausencia de contenido/foco privado.
   No reemplazar la revocación por un token inventado. No dar por comprobado el
   borrado de almacenamiento: el rechazo de `refreshSession()` por sí solo no lo
   demuestra.

## Registro y restitución antes de la ejecución

- Registrar duraderamente el intento de refresh antes de permitir la petición.
  La respuesta y sus tokens permanecen en el journal privado.
- Validar el bearer recibido en Auth y comprobar actor, Auth session y Web
  session contra los recibos originales. No asumir que sus identidades se
  conservan tras renovar. Una identidad distinta exige parar y reconciliar;
  no atribuirla por diferencias temporales ni registrarla como otro login.
- `recordRecoverySession()` admite recibos iniciales sin IDs previos: no se puede
  reutilizar directamente para sobrescribir el recibo de una renovación.
  La comprobación focal debe conservar el recibo original.
- Incluir `/auth/v1/token` en seguimiento y cierre de solicitudes. Un timeout o
  el cierre del navegador no demuestran que el servidor haya terminado. No
  repetir un refresh incierto ni retirar su journal sin reconciliación.
- Antes de cualquier prueba real, comprobar sintéticamente éxito, rechazo,
  respuesta perdida e identidad inesperada. Mantener el runner actual de login
  y sus evidencias; no extender ACCOUNT ni modificar el producto por esta prueba.

## Destino inexistente: preparación separada

La resolución común distingue mensaje ausente de error de lectura y agota el
historial antes de declarar `Unavailable`; permite recuperarlo si aparece en un
snapshot posterior. Los tests de `ChatMessageDeepLinkFocusTest` documentan ese
contrato, pero no acreditan una entrega externa real a un hilo inexistente.
El próximo runner debe distinguir hilo ausente, mensaje ausente en hilo propio
y fallo de red. No convertir un timeout sin foco en PASS de destino inexistente
ni reabrir CHAT-FOCUSED-MESSAGE por inferencia.

## Implementación preparatoria

`scripts/e2e-fixtures/chat-deep-link-refresh.mjs` incorpora el registro del
intento único y la verificación del recibo conservado. Vincula privadamente
refresh token y Web token a la respuesta original del login antes de permitir
transporte. No adopta una identidad nueva ni interpreta un rechazo como prueba
de revocación. El coordinador conserva fixtures y journals si encuentra un
intento de refresh sin verificar, incluso después de cerrar el navegador.

Los tests sintéticos cubren orden durable, credenciales mezcladas, pérdida de
respuesta, rechazo, cambio de identidad y fallo de disco. La prueba del
coordinador usa un journal DPAPI real con backend simulado.

El modo opt-in `sessionMode: "refresh"` conecta ahora el adaptador de navegador:
intercepta la petición de token del producto y valida URL, método, API key y
payload antes del transporte. Permite un solo envío, sin redirects ni retries,
después del checkpoint y entrega la respuesta original tras persistirla. La
verificación del recibo continúa después y sigue siendo necesaria para PASS.
Un timeout no libera el estado del observador mientras continúe su promesa real,
ni permite envíos/entregas tardíos. La prueba fría cambia sólo el metadato de
vencimiento inicial y conserva el almacenamiento renovado al recargar.

Chrome sintético comprueba un refresh y ninguna repetición tras volver/recargar;
el negativo rechaza el recorrido que alcanza Chat sin renovar. Este mecanismo
no acredita por sí solo el producto real. El caso pre-revocado sigue necesitando
su ciclo específico.

## Primer ensayo real: fallido, limpieza reconciliada

Runner `ba956797`, run `913a34ff-10b9-4c78-8639-f89748c0dfc7`, producto y
distribución indicados en la matriz focal. Artefactos locales:
`build-reports/flow-deep-links/web-session-refresh-c9266467-2c50-40be-850c-18007a851513/`.
El proceso `20484` terminó con código 1: no alcanzó la ruta Chat, no observó
selección y no hubo errores de página. La captura de fallo muestra la barrera
anónima sobre Feed. El journal sí conservó respuesta de refresh HTTP 200 y
verificación durable de los recibos originales. Esto no es aceptación del flujo.

La restitución automática quedó detenida. Se revalidó el bearer en Auth, se
confirmaron los IDs originales y exactamente una sesión Auth y una Web propias,
sin sesiones del segundo perfil. Se ejecutaron los helpers existentes de retiro
con auditoría de identidad/referencias y verificación final de ausencia. El
proceso de restitución `24768` terminó con código 0; `cleanup-reconciled.json`
registra `cleanupComplete: true`, y el directorio privado quedó vacío.
`report.json` conserva el fallo original; la reconciliación posterior no lo
transforma en PASS.

Antes hubo una preparación interrumpida por timeout del runtime interactivo:
`web-session-refresh-89138f35-c3d1-412b-8801-bdee84fbac27` sólo contenía el
manifiesto, sin proceso coordinador ni directorio privado. Se verificó esto antes
del ensayo. La primera apertura interactiva de journals para restitución falló
por `process is not defined`, antes de cualquier retiro; se usó Node completo.

Corrección posterior implementada y revisada: auditar y registrar intención antes de abrir la
página; después cotejar y enviar su petición real. Persistir respuesta antes de
entregarla, pero no esperar la posterior verificación Auth/SQL para la entrega.
El observador permanece activo y prohíbe PASS/limpieza hasta verificar el recibo.
Los reportes miden preparación, petición, respuesta, checkpoint, entrega y
verificación sin secretos. Chrome sintético usa un plazo de petición de 700 ms
y retrasa la verificación 1200 ms después de entregar: el recorrido pasa sin
esperar esa verificación para recibir la respuesta. El caso sin refresh falla.
El ensayo real posterior sobre esta corrección se registra a continuación.
El plazo de 15 segundos de `browserPostJson` y la latencia añadida por el runner
son una hipótesis de interferencia; este ensayo no identifica todavía la causa.

## Segundo ensayo real: aceptación local focal

Runner `bc4c915a`, run `868b5f1d-88c6-4c4b-9d0a-aacf7a7b0f79`. Producto
`c252e00035e97065726fc51aee5a4d6469975324`, distribución
`ca9990b2840087bfcb31508d65968a722bf7fdb38e8af594a8b7f1e0fc9019c4`.
Artefactos locales:
`build-reports/flow-deep-links/web-session-refresh-timed-5499e03a-f3bb-4ce4-a6a9-1bd980931e53/`.

El proceso `24920` terminó con código 0. `report.json` registra PASS y limpieza
completa; el directorio privado está vacío. Sólo se usaron perfiles, sesión,
hilo y mensaje temporales propios, posteriormente retirados por el coordinador.
No hubo despliegue ni modificación del producto o de cuentas existentes.

La apertura fría resolvió el hilo `2533` y mensaje `11163`, con texto accesible
exacto, un episodio de selección visible sin cobertura y posterior retirada del
foco. Las capturas `ui/web-chat-cold-target.png` y `ui/web-chat-cold-back.png`
se inspeccionaron: mensaje temporal resaltado y listado de Chats tras volver.
La recarga mantuvo el listado sin reapertura, sin segundo refresh y sin errores
de página. La respuesta de renovación conservó las identidades Auth/Web
originales verificadas; la aceptación esperó también esa verificación.

Hitos relativos al inicio del observador, en milisegundos:

| Hito | Tiempo |
| --- | ---: |
| Preparación durable terminada | 4253 |
| Petición del producto | 5464 |
| Envío real | 5465 |
| Respuesta HTTP | 5866 |
| Respuesta persistida | 12703 |
| Entrega al producto | 12706 |
| Recibo verificado | 17910 |

La entrega tardó 7242 ms desde la petición; la verificación terminó 5204 ms
después de entregar. La persistencia privada sigue añadiendo latencia: no es un
benchmark de rendimiento. Estos tiempos acreditan la separación de fases del
runner actual; no reconstruyen los tiempos del primer ensayo fallido.

Alcance: renovación real provocada por modificar únicamente el metadato local
de vencimiento antes del arranque Web. No acredita vencimiento real del JWT,
revocación, recorrido caliente, recepción nativa Android/iOS, service workers ni
ciclo de segundo plano. La ventana de observación posterior a salida es de dos
segundos. No constituye certificación de candidata final ni GO integrado.

## Caso revocado: runner y primer ensayo real fallido

El modo opt-in `sessionMode: "revoked"` revoca la única sesión propia antes de
abrir el navegador, usando los recibos exactos y el helper existente. Exige el
token original del journal, propiedad del fixture y ausencia de otras sesiones;
registra intención antes de revocar y verifica ausencia después. Una revocación
sin verificación impide limpieza automática y mantiene los journals.

El observador usa la credencial original, con metadato local vencido, y admite
únicamente HTTP 400/401 con `refresh_token_not_found` o `session_not_found`,
códigos documentados por [Supabase Auth](https://supabase.com/docs/guides/auth/debugging/error-codes).
Vuelve a verificar ausencia remota después de la respuesta. Un error genérico,
timeout, HTTP 200 o nueva sesión impiden aceptación. No se repite una operación
incierta. La respuesta privada queda durable antes de entregarse al producto.

La UI exige barrera anónima sobre Feed, sin episodios de foco ni rutas Chat
registrados por MutationObserver, y ausencia de nodos de mensaje en la
comprobación final. Esta observación no prueba ausencia absoluta de transiciones
dentro de un mismo lote de mutaciones ni inspecciona contenido continuamente.
La ventana tras verificar el rechazo es de dos segundos. No se exige ni se
acredita invalidación anticipada del JWT o borrado del almacenamiento local.

Los tests sintéticos prueban revocación exacta, credenciales mezcladas, fallos de
persistencia/transporte y ausencia remota inesperada. Chrome prueba barrera,
rechazo de una aparición breve del Chat y rechazo de un resultado no acreditado.
Las peticiones de refresh tienen seguimiento especializado hasta terminar su
handler/observador; así un rechazo esperado verificado no se confunde con un
error HTTP genérico. Las demás peticiones conservan su contabilidad anterior.

El primer ensayo real usa runner `7282d06b`, run
`fc74f885-31d2-4029-8d17-49f22012add5`, producto `c252e000` y distribución
`ca9990b2…` identificados íntegramente arriba. Artefactos:
`build-reports/flow-deep-links/web-session-revoked-3c8006a0-7c6f-47f7-903a-3373def2c88b/`.
El proceso `20500` terminó con código 1, `status: failed`, limpieza automática
completa y directorio privado vacío. No quedan recursos propios pendientes.

La captura `ui/web-chat-cold-failure-revoked_barrier.png` muestra barrera anónima
sobre Feed. El reporte confirma primer rechazo entregado y verificado, cero
errores de página y ninguna selección registrada del mensaje destino. Sin embargo, registró dos
intentos de refresh: el segundo fue abortado por el runner antes de enviarlo,
por lo que no hay aceptación del recorrido. La limpieza no transforma ese fallo
en PASS. No se flexibiliza la prohibición de intentos duplicados.

La entrega del primer rechazo ocurrió a 12869 ms desde el inicio del observador;
el segundo intento apareció a 12875 ms, seis milisegundos después. El campo
`request` del runner de este ensayo fue sobrescrito por ese segundo intento;
no debe interpretarse como tiempo de la primera petición. El envío inicial fue
a 5541 ms, respuesta a 5930 ms, checkpoint a 12863 ms y verificación a 17344 ms.
Estos datos no identifican por sí solos qué llamador inició la segunda petición.

Revisión independiente de fuente y evidencia: `sessionForAuthenticatedRequest()`
serializa mediante mutex, pero conserva la sesión almacenada cuando falla el
refresh. Una llamada posterior puede volver a renovarla. `browserPostJson`
extrae `error`, perdiendo la clasificación `error_code` de Auth. Esta combinación
es coherente con el segundo intento observado.

Corrección focal implementada en `3bcfec15`: clasificar sólo el refresh HTTP 400/401 con código
terminal conocido; limpiar la sesión persistida y activa dentro del mutex sólo
si todavía coincide con las credenciales rechazadas. No borrar un login nuevo
concurrente. Mantener credenciales ante red, timeout, 429, 5xx o respuestas
desconocidas. Verificar dos solicitantes concurrentes con una sola petición
terminal, ausencia de reintento posterior, conservación ante fallo transitorio y
protección del login nuevo. La corrección de producto requerirá nueva evidencia
proporcional al diff; no trasladar automáticamente el GO del binario anterior.


## Corrección Web y verificación del mecanismo

Producto `3bcfec15f209c4f31b73dae81b6f5df3725098c7`; distribución
`1253d2b7d752694b795989b35a32b0342d3d77afadda1e7308c758ea87bce684`.
La clasificación terminal se activa sólo en el transporte de refresh. Dentro del
mutex se retiran las credenciales persistidas y activas únicamente si aún coinciden
con la respuesta rechazada; se conserva un login nuevo concurrente. Los fallos
transitorios o desconocidos mantienen las credenciales. Revisión independiente
estática favorable; no se generaliza a coordinación entre pestañas.

`WebAuthRefreshRejectionTest`: cinco tests ChromeHeadless pasan sin errores ni
skips, usando el repositorio y transporte JS reales con fetch sintético. Cubren
solicitantes concurrentes y ausencia de reintento terminal, ambos códigos/estados,
fallos transitorios y desconocidos, login nuevo durante refresh y aislamiento de
la clasificación respecto al login. Build de distribución completado en 2m24s.
Artefactos locales: `web-refresh-rejection-tests.{log,xml}` y
`web-refresh-rejection-bundle.log` bajo `build-reports/flow-deep-links/`.
La evidencia real anterior conserva su procedencia; no se traslada automáticamente
al nuevo binario.


## Ensayo revocado después de la corrección

Run `c7b4a738-e35d-4153-a360-80fc2e68627e`, runner/producto `3bcfec15`,
distribución `1253d2b7…` identificada íntegramente arriba. Artefactos:
`build-reports/flow-deep-links/web-session-revoked-fixed-ab4f4f30-ee94-4774-a6c9-fc956c84cfc3/`.
Reporte PASS y limpieza completa, directorio privado vacío. Un único intento de
refresh, rechazo entregado y verificado; barrera sobre Feed, cero episodios de
foco y ninguna ruta Chat observada, ningún mensaje privado en muestra final,
cero errores de página. Captura `ui/web-chat-cold-revoked-barrier.png` inspeccionada.

Tiempos desde arming: petición 5416 ms, envío 5418, respuesta 6349, checkpoint
13552, entrega 13555, verificación 18021. La entrega precedió a la verificación
remota; no se flexibilizó el control de intentos duplicados. Conserva los límites
del runner descritos arriba, incluida ventana de dos segundos y muestreo de
nodos. No acredita borrado del almacenamiento en E2E (cubierto por test focal),
invalidación anticipada de JWT ni iOS/Android. El primer fallo permanece como
evidencia histórica y no se convierte retroactivamente en PASS.

El proceso `16820` terminó con código 0. Revisión independiente de reporte y
captura: **GO local focal** para arranque frío con sesión propia revocada y
metadatos vencidos, rechazo único y barrera anónima sobre Feed. No es GO integrado.


## Preparación del runner de destino ausente

Revisión independiente de fuente común actual (sin promover aceptación ni cambiar
producto): `ChatMessageDeepLinkFocus.kt` distingue `Unavailable` tras historial
agotado y admite recuperación posterior, pero `ChatScreenHost.kt` sólo representa
explícitamente `LoadFailed`. No se acredita por ello un aviso visible de mensaje
inexistente. `conversation == null` en el ViewModel tampoco demuestra ausencia
del hilo en backend.

`PostgrestChatRepository.observeMessages()` ignora el Result inicial de
`refreshThread` y emite el snapshot almacenado; un snapshot vacío no demuestra
lectura correcta. El runner deberá registrar la respuesta real de
`quata_chat_get_thread` y diferenciar respuesta exitosa sin destino,
inaccesibilidad y error de transporte. Para mensaje ausente, usar el hilo propio
de un mensaje y otro ID verificado ausente; exigir RPC exitoso, historial agotado,
conversación correcta visible y cero selección. Para hilo ausente, verificar
ausencia del ID y registrar tanto respuesta RPC como UI resultante. Un negativo
sintético de red deberá impedir PASS de ausencia. No convertir timeout en éxito
ni reabrir el cierre de CHAT-FOCUSED-MESSAGE por inferencia. La paridad del aviso
con Android publicado queda por comprobar antes de cualquier cambio de producto.


## Regresión de renovación válida en el producto corregido

Run `14481e43-353a-407a-8be6-c5f9f874558e`, producto `3bcfec15`, bundle
`1253d2b7…` (hash completo arriba), runner `e57f0b36`. Reporte PASS, PID `4304`
terminal con código 0, limpieza completa y directorio privado vacío. Artefactos:
`build-reports/flow-deep-links/web-session-refresh-fixed-retry-c9242e62-f7f8-4e4f-a4ac-f4788635683c/`.
Un refresh real entregado y verificado; hilo `2536`, mensaje `11166` y texto
accesible exactos, un episodio de selección visible sin cobertura, foco limpiado,
vuelta y recarga al listado Chat sin reapertura, cero errores. Capturas target y
back inspeccionadas. Tiempos ms: petición 5605, envío 5606, respuesta 6404,
checkpoint 13165, entrega 13170, verificación 18091. Conserva los límites de
metadato local vencido, arranque frío y ventana acotada; no prueba JWT realmente
vencido, warm ni otras plataformas.

El primer lanzamiento `web-session-refresh-fixed-cb9055fe-5216-4544-921c-9ab5f4b1c444`
terminó con código 1 antes del coordinador: sin informe, directorio privado ni UI.
La fuente crea el directorio/journal antes de cualquier fixture; no hubo mutación
que reconciliar. Causa inicial no clasificada. Una sonda TLS de sólo lectura pasó
antes del nuevo lanzamiento. `preparation.json` preserva ese fallo sin convertirlo
en resultado de producto.

Revisión independiente de informe y ambas capturas: **GO local focal** de la
regresión cold con metadatos vencidos tras la corrección terminal.

Referencia estática para el próximo caso: commit publicado `1b8f3b70`
(`Versión 1.0.4`), `app/src/main/java/com/quata/feature/chat/presentation/chat/ChatScreen.kt`,
`LaunchedEffect(focusedMessageId, state.messages)`: cuando no encuentra el mensaje
retorna sin seleccionar ni emitir aviso desde ese efecto. Es evidencia de ese
bloque de código, no una prueba completa de UI ni de todas las rutas de error.
No añadir un aviso como supuesto requisito de paridad sin revisar el flujo completo.


## Runner focal de mensaje ausente

Runner `94526a8f`, modo opt-in `targetMode: "missing-message"`, incompatible con
modos de login/refresco. Reutiliza dos perfiles, sesión e hilo propios; elige otro
ID numérico y verifica antes y después de la UI que está ausente, que el hilo
pertenece al fixture y que contiene exactamente el mensaje original. No crea
un mensaje adicional ni modifica producto/backend.

El observador consume las respuestas reales `quata_chat_get_thread`: exige
HTTP 200, actor e hilo exactos, el mensaje del fixture y una página inicial
completa más corta que el límite. Una respuesta incremental aislada, cuerpo
malformado, fallo HTTP, actor ajeno o aparición del objetivo impiden PASS.
La UI exige el mensaje existente y su texto accesible, cero episodios de foco,
ausencia del nodo solicitado, vuelta/recarga sin reapertura y mismo documento
en caliente. El resultado se revalida tras drenar/cerrar el contexto; un cuerpo
HTTP 200 inválido leído tarde tampoco puede conservar un PASS provisional.

Revisión independiente favorable tras corregir ese último caso. Tests locales:
21 regresiones de helpers/adaptador/coordinador, cinco casos Chrome del modo nuevo
y un negativo focal con lectura diferida pasan sin skips. Logs locales:
`web-missing-message-regression.log`, `web-missing-message-late-body.log` y
`web-missing-message-post-drain.log` bajo `build-reports/flow-deep-links/`.
Esto valida el mecanismo; no sustituye la aceptación del bundle real.
No acredita hilo inexistente ni aviso explícito de mensaje no disponible.


## Evidencia real de mensaje ausente

Run `663f17c9-f4fe-4068-989c-4ded7755d0f8`, runner `94526a8f`, producto
`3bcfec15`, distribución `1253d2b7…` identificada íntegramente arriba. Reporte PASS,
PID `19088` terminal con código 0, limpieza completa y directorio privado vacío.
Artefactos: `build-reports/flow-deep-links/web-missing-message-1308fe39-9677-4cd7-be0b-c86dd8ac9dc6/`.
En frío y caliente: hilo `2537`, mensaje solicitado ausente `4037450071889`,
mensaje visible `11167` y texto exacto del fixture. Dos respuestas RPC por
recorrido, historia del fixture agotada y ningún fallo del observador; cero
episodios de selección y ningún nodo del ID solicitado. Vuelta y recarga al
listado Chat sin reapertura, cero errores de página; caliente conserva documento.
Las cuatro capturas target/back se inspeccionaron: conversación con mensaje
existente sin resaltado y listado después de volver.

`exactMessageId` del reporte identifica el ID solicitado ausente;
`visibleMessageId` identifica el mensaje existente cuyo texto accesible se
comprobó. `uncoveredSelection: false` corresponde a que no hubo selección.
No acredita hilo inexistente, aviso explícito de ausencia, sesión vencida,
otras plataformas ni observación indefinida. No promueve el inventario.

Revisión independiente de reporte y cuatro capturas: **GO local focal** para
mensaje ausente en hilo propio en frío y caliente, con los límites anteriores.


## Preparación del caso de hilo inexistente

Auditoría remota en transacción READ ONLY de `quata_chat_get_thread`,
`quata_chat_mark_thread_read` y `quata_chat_cleanup_empty_private_thread`, guardada
en `build-reports/flow-deep-links/missing-thread-contract-audit.json`. Las dos
primeras rechazan antes de escribir con SQLSTATE `42501` y mensaje exacto
`profile is not a participant of this thread`; la última devuelve
`deleted: false`, el ID solicitado y `reason: not_participant`. No se ejecutaron
esas funciones durante la auditoría ni se desplegó nada.

El modo opt-in `targetMode: "missing-thread"` usa un ID numérico cuya ausencia
se comprueba antes y después de la UI, incluyendo mensajes, participantes y
estado de conversación. Conserva el hilo propio del fixture separado para
restitución. El observador sólo considera resueltas las respuestas completas
y exactas del actor/hilo: HTTP 403 con ese rechazo y HTTP 200 con cleanup no-op.
Errores desconocidos o transporte incierto mantienen el bloqueo de limpieza.
La inexistencia se acredita por DB, no se infiere del 403.

La UI debe mostrar el botón común «Reintentar mensajes», sin mensajes ni foco,
y permitir volver y recargar sin reapertura, en frío y caliente. Se revalida
tras drenar el contexto. No se acredita un aviso específico de inexistencia ni
funcionamiento de Retry. Revisión independiente estática favorable. Once tests
de helpers/Chrome (incluida regresión de mensaje ausente y fallo tardío) pasan
sin skips en `web-missing-thread-tests.log`; falta el ensayo real del bundle.

El runner quedó guardado en `5d1c49a3`. También pasan las doce regresiones
Chrome de los modos previos (sesión válida, renovación y revocación), sin skips,
en `web-missing-thread-regression.log`. El producto sigue siendo `3bcfec15`;
la ampliación sólo afecta al runner.


## Primer ensayo de hilo ausente: transporte PASS, sin GO visual

Run `0d54cf36-c155-4788-8927-6e94fe616f41`, runner `5d1c49a3`, producto
`3bcfec15`, bundle `1253d2b7…`. Artefactos:
`build-reports/flow-deep-links/web-missing-thread-54aff2cd-4627-41c8-9caa-2dae8b1488f2/`.
PID `22780` terminal 0, reporte técnico PASS, limpieza completa y directorio
privado vacío. Hilo solicitado `6333834294010`, mensaje `7761592420063`.
Frío y caliente: dos rechazos get_thread, cuatro mark_thread_read y dos cleanup
no-op por recorrido, todos verificados; cero mensajes/foco, vuelta y recarga
sin reapertura, cero errores de página; caliente mantiene documento.

Las cuatro capturas y revisión independiente impiden **GO visual**: el código
`web_postgrest_rlsdenied:postgrest_rpc_http_403` aparece tres veces (banner general,
fallo de lectura y compositor). El reporte automático no comprobaba la calidad
del mensaje de error. Su PASS no cierra esta aceptación.

Corrección focal: las dos rutas de fallo de lectura del ViewModel publican el
texto común traducido `LoadMessages` únicamente en `messageLoadFailure`; no lo
copian a `error`, que pertenece también a operaciones independientes de envío
y adjuntos. Transporte y contrato de foco permanecen intactos. Revisión estática
favorable. El runner se refuerza para exigir una única frase legible y ausencia
de códigos técnicos; necesita nueva distribución y nuevas capturas.

La corrección quedó guardada en `edbb970b`. Diecinueve tests comunes pasan
(diez de compositor/estado, incluidos los dos nuevos, y nueve de foco), sin
fallos ni skips. XML preservados como `chat-read-failure-{composer,focus}-tests.xml`
en `build-reports/flow-deep-links/`; log `chat-read-failure-presentation-tests.log`.
Los cuatro casos Chrome del guard visual también pasan, incluido rechazo del
código técnico (`web-missing-thread-visual-guard.log`). Falta renovar el ensayo
real sobre la distribución de este producto.


## Hilo ausente después de corregir la presentación

Producto `edbb970b0108d0e6804f7a010865edfcdfaecded`, distribución
`39a6b782a1be4c0fe010d1dc3ed1e4a455e67933dea09a7ce9747515a2f724a5`; build
PASS en 2m27s (`chat-read-failure-web-bundle.log`). Runner `39f55627`, run
`a8709686-2ff9-4274-b602-b6a77373f115`. Artefactos:
`build-reports/flow-deep-links/web-missing-thread-fixed-fed71882-3809-4ecf-9897-9683d5c9898d/`.
PID `20124` terminal 0, reporte PASS, limpieza completa y directorio privado vacío.

En frío y caliente, hilo ausente `5584317354010` y messageId `4727218173061`.
Dos rechazos get_thread, cuatro mark_thread_read y dos cleanup no-op por recorrido,
verificados; cero mensajes, foco o errores de página. Vuelta/recarga a Chats sin
reapertura; caliente conserva documento. Las cuatro capturas inspeccionadas
muestran una única frase «No se pudieron cargar los mensajes.» y el control
«Reintentar mensajes», sin códigos técnicos ni error duplicado en el compositor.
El listado aparece al volver.

El guard visual exige frase exacta única y ausencia de los códigos técnicos
conocidos; la inspección de capturas complementa esa comprobación acotada. No se
ejecutó Retry ni se acredita aviso específico de inexistencia, otras plataformas,
sesión vencida o ausencia indefinida de reapertura. El ensayo anterior conserva
su falta de GO visual. Las demás evidencias conservan su procedencia previa.

Revisión independiente de informe y cuatro capturas: **GO local focal Web**
para hilo inexistente en frío y caliente sobre `edbb970b`, con los límites
anteriores. No es certificación integrada ni se transfiere a iOS/Android.

## Preparación nativa iOS de sesión sobre edbb970b

El 10 de septiembre se compiló el producto `edbb970b0108d0e6804f7a010865edfcdfaecded`
en el worktree Mac separado `quata-flow-deep-links-edbb970b`, conservando el host
y las evidencias anteriores de `12128cb0`. Framework Intel/raster: BUILD SUCCESSFUL
en 10m59s, watchdog terminal 0 (`deep-links-framework-edbb970b.log`). Host
SimulatorSigned: TEST BUILD SUCCEEDED, recursos Compose y firma verificados,
watchdog terminal 0 (`deep-links-host-watchdog-edbb970b-retry.log`). El primer
intento de host terminó antes de compilar por faltar el override público local;
se conserva su log y se copió la configuración del entorno iOS existente.

El helper XCTest `QuataIosDeepLinkSessionTests.swift`, commit `ca1277bb`, SHA-256
`70efff56aeccaced8ef72d5b96aec9ff74482ad606f73a144912370bab1529a8`, está compilado
y enlazado en ese host. Su revisión independiente estática permite el probe
sintético aislado, sin autorizar por inferencia un ensayo de sesión real.
Comprueba archivos privados, recibos, rechazo de replay y correspondencia de
claims; la comprobación de claims no sustituye verificar el bearer con Auth.
La importación exige host pasivo, sesión vacía y lectura posterior coincidente;
el borrado exige coincidencia exacta. Un coordinador real todavía debe verificar
propiedad, excluir otros procesos del simulador y resolver renovación/limpieza.
No se reutiliza el login del seeder de otra unidad, que crearía otra sesión.

Se preparó el probe local `run-ios-session-synthetic.py` con dos métodos
explícitos: intercambio privado sin Keychain e importación/borrado sintéticos en
un servicio Keychain UUID independiente. Usa el host `anonymous`, no habilita
`testOwnedDeepLinkSessionStep` ni realiza login o mutaciones en Supabase.
Compilación y preparación no acreditan ejecución ni aceptación del enlace Chat;
el resultado del simulador debe registrarse por separado. El inventario sigue
pendiente y no hay candidata congelada ni GO integrado.

Resultado del probe `32d89986-04fc-4b77-b92d-50f40a597a89`: los dos métodos
seleccionados se ejecutaron una vez y pasaron, sin skips ni fallos. Keychain:
0,026 s; intercambio privado: 0,014 s. Xcode emitió `TEST EXECUTE SUCCEEDED` y
el watchdog/coordinador terminaron con salida 0. El guard del host pasivo pasó
en ejecución, y el servicio Keychain sintético quedó vacío tras su borrado
exacto. Reporte y log copiados a
`build-reports/flow-deep-links/ios-session-synthetic-32d89986-04fc-4b77-b92d-50f40a597a89/`;
el `.xcresult` permanece en el directorio `build/reports/ios/session-synthetic-32d89986-04fc-4b77-b92d-50f40a597a89`
del worktree Mac. Después se adquirió de nuevo el lock, se comprobó ausencia
de xcodebuild, se apagó y verificó el simulador dedicado y se archivó el plan
fuera de Products. Cierre terminal 0, cero operaciones de backend.

Este resultado acredita únicamente el mecanismo sintético de intercambio y
persistencia nativa. No acredita login, importación de bearer real, renovación,
revocación ni navegación/foco del enlace privado. El siguiente paso requiere
un coordinador iOS con recibos de propiedad y cierre completo antes de utilizar
sesiones temporales reales; la evidencia Web no cubre ese recorrido nativo.

Revisión independiente del log y del cierre: **GO acotado al mecanismo
sintético de intercambio privado y Keychain**, con los límites anteriores.

Preparador privado `scripts/e2e-fixtures/chat-deep-link-ios-session.mjs`:
compara la sesión con la respuesta privada guardada, vuelve a verificar el
bearer en Auth y exige IDs Auth/Web exactos, hash del token Web, propiedad del
run y perfil activo en DB. Sólo admite el recibo Web original sin renovación
ni revocación y requiere más de 900 segundos de vigencia. Exporta el input
nativo en memoria sin password ni token Web; no hace login ni modifica el
journal. Su mapping sigue `toIosAuthSession`, con campos explícitos exigidos
para este fixture. Cuatro pruebas sintéticas nuevas y diez regresiones del
login pasan; log `build-reports/flow-deep-links/ios-session-preparation-tests.log`.
Revisión independiente favorable al preparador aislado; se incorporó también
el rechazo de `ticketId` nativo en tickets Web.

Este preparador todavía no está conectado al ensayo real. Antes de conectarlo,
el coordinador debe guardar el intento de importación en el journal, mantener
la exclusión del simulador durante toda la sesión, transferir el input por
stdin a archivos privados y verificar el recibo antes de abrir producto.
Debe cerrar procesos antes de verificar/borrar la sesión exacta y comprobar
el recibo de borrado antes de retirar fixtures. Una respuesta perdida o sesión
renovada requiere reconciliación: no permite repetir importación ni borrar
una sesión diferente. No se ejecutaron operaciones reales con este módulo.

La custodia `chat-deep-link-ios-custody.mjs` persiste el input privado y el
intento antes de llamar al transporte. Sólo marca cada paso verificado tras
recibir el recibo exacto y guardar su confirmación. Respuesta perdida, recibo
incorrecto o fallo del checkpoint final conservan la incertidumbre e impiden
replay. El clear exige los mismos campos de sesión y un stepId distinto.
El guard de limpieza del coordinador ya considera `iosSession`: mientras
exista sin install/clear coincidentes y verificados, retiene journals y fixtures.
Revisión independiente estática favorable a este cambio preparatorio.

Nueve pruebas finales de preparación/custodia pasan, incluido fallo al guardar
la confirmación después de recibir un recibo exitoso; log
`build-reports/flow-deep-links/ios-custody-final-unit-tests.log`. El transporte
nativo real sigue pendiente: su contrato exige XCTest terminal y host pasivo
detenido antes de devolver el recibo. Estas transiciones por sí solas no prueban
ese cierre ni la entrega del enlace Chat.

La regresión conjunta previa terminó con 16 PASS y cero skips
(`ios-custody-tests.log`). El caso nuevo focal del coordinador con DPAPI real
también pasó: `closed browser with unresolved iosSession preserves journals
before any retirement`; los otros ocho casos se excluyeron por filtro en esa
ejecución (`ios-custody-coordinator-guard-test.log`). Todas estas pruebas usan
transportes/DB sintéticos; no tocaron Supabase.

Transporte Mac `scripts/flow-deep-links-ios-worker.py`: proceso persistente con
flock del simulador dedicado durante el intercambio de comandos JSON por stdin.
El stdout contiene únicamente estado/recibos y errores genéricos. Los inputs
de sesión se guardan con modo 0600 en directorio 0700; el entorno XCTest sólo
recibe la ruta privada. El plan selecciona el método focal y el host pasivo.
Se exige éxito terminal XCTest, apagado verificado del simulador y retirada
del input antes de confirmar. El plan ejecutado se archiva junto al resultado.
Las operaciones simctl y consultas tienen plazos explícitos, además del
watchdog XCTest. La revisión señaló ese requisito y quedó incorporado.

Un fallo puede dejar el host o archivos privados pendientes: el error del
worker no acredita cierre. El coordinador debe conservar la custodia, verificar
procesos y reconciliar el paso antes de cualquier limpieza backend. `close`
rechaza una sesión instalada sin clear confirmado. El worker aún no implementa
la entrega/observación UI ni está conectado al coordinador Windows; el modo
probe sólo permite comprobar el transporte con los dos tests sintéticos.

Probe del worker: run `40104655-b0ec-4556-95b7-89f627a800c6`, step
`e38411dd-1231-4f71-b91f-3158ab725cbc`. Ambos métodos ejecutados PASS (0,019 s
y 0,008 s), `TEST EXECUTE SUCCEEDED`, watchdog y proceso SSH terminal 0.
El protocolo devolvió ready, recibo verificado y closed. Comprobación posterior:
worker/xcodebuild ausentes y simulador dedicado apagado; el otro simulador
permanece activo. Logs `ios-worker-probe-{transport,tests}.log` y
`ios-worker-probe-exit.txt` en `build-reports/flow-deep-links/`. El primer intento
con JSON mal formado fue rechazado antes de XCTest, salida 1; se conservan sus
artefactos `ios-worker-probe-invalid-*`. No hubo input de sesión real ni backend.
Este PASS prueba el transporte sintético y su cierre, no los pasos install/clear
del Keychain de producto ni la aceptación Chat.

Revisión independiente de transporte, log y cierre: **GO acotado al probe
sintético del worker**; sin promoción de las aceptaciones pendientes.

Canal Windows `chat-deep-link-ios-channel.mjs`: SSH sin shell local, rutas
restringidas, input por stdin y protocolo de salida privado con recibos exactos.
`settled` exige recibo closed y proceso terminal 0 sin fallos. Abort/timeout
cierra el canal local y deja la limpieza remota sin acreditar; no reintenta.
El coordinador admite ahora `ui.iosSessionChannel` de forma opt-in: prepara y
registra install antes de la UI; en el cierre finaliza UI, verifica clear,
cierra el canal y comprueba settled antes de retirar los fixtures. Un fallo
retiene los journals y aborta el canal local. Esta ruta aún no se ha ejecutado
con credenciales reales ni cuenta con el adaptador de entrega/observación Chat.

Probe Windows→SSH→Mac `477cff56-2921-4823-9ffc-6d14821852db`, step
`9904c394-91da-4675-a3f1-f7acdf4edd87`: dos XCTest sintéticos PASS (0,019 s y
0,011 s), éxito terminal y watchdog 0; recibo de probe y cierre verificados,
proceso Node terminal 0, settled true. Simulador dedicado apagado y xcodebuild
ausente en comprobación posterior. Artefactos locales
`ios-channel-probe-{report.json,tests.log}` en `build-reports/flow-deep-links/`.
El abort explícito y la integración de custodia se añadieron después de lanzar
ese probe; sus comprobaciones son locales: 16 pruebas de canal/preparación/
custodia PASS y dos de integración de cierre PASS (nueve excluidas por filtro),
logs `ios-channel-final-tests.log` e `ios-channel-integration-tests.log`.
No se ejecutaron operaciones de backend ni importaciones de sesión real.

Revisión independiente del ensamblado y reporte favorable, limitada al canal
sintético y al orden de custodia. El adaptador UI real y su cierre requieren
revisión antes del primer ensayo con sesión.

Adaptador UI iOS preparado: `createIosDeepLinkUi` sólo admite el mensaje propio
válido, observa cold y después warm mediante el worker y rechaza modos de
destino ausente. Si la observación queda incierta, su cierre falla para impedir
clear/retiro automático. La entrega usa `simctl openurl` externo: cold exige
ausencia de PID antes de entregar; warm exige el PID del recorrido anterior,
y ambos comprueban que sigue igual al terminar XCTest. `delivery.json` conserva
URL/PID y alcance de la observación en el directorio de cada paso.

`QuataIosExternalChatLinkUITests.swift` no abre/activa la app ni entrega URL:
comprueba host/ruta, botón con ID seleccionado y etiqueta exacta
`Deep link fixture: <body>`, accesibilidad antes de captura y vuelta al listado.
La revisión corrigió el primer selector, que podía aceptar el marcador auxiliar
de 1 dp en lugar de la burbuja. El test conserva capturas para revisión visual;
ni la semántica ni isHittable acreditan por sí solas resaltado sin cobertura.
XCTest se limita aquí a recepción/observación iOS; Maestro sigue siendo piloto
parcial según la directiva vigente, y no se localizó su CLI en esta VM.

La versión corregida del observador, SHA-256
`a494c9a885fb61d34fa6ee818c250bf97eba050ac21a3bbe3baae23a245bc462`,
compiló/enlazó en el host del producto edbb970b: TEST BUILD SUCCEEDED, watchdog
terminal 0 (`deep-links-chat-observer-exact-{edbb970b,watchdog}.log` en Mac).
Diez pruebas locales del adaptador/canal pasan (`ios-chat-adapter-tests.log`).
El código Python pasa py_compile. No se ejecutó todavía el observador con sesión
real; recarga posterior, objetivos ausentes y autenticación siguen fuera de
estas comprobaciones preparatorias.

La revisión detectó además que el resaltado dura ocho segundos: entregar antes
de arrancar XCTest podía perder esa ventana. El worker ahora inicia primero
el observador, espera READY del step exacto y revalida el PID antes de entregar
una única URL. XCTest busca la burbuja mientras atiende, si aparece, el aviso
Abrir una sola vez; no espera cinco segundos fijos. Se conservan los plazos
del producto. Si no hay READY o el observador lanza/reemplaza la app, no entrega.
El worker espera al watchdog terminal incluso si falla esa preparación.

Tres pruebas Python locales con simctl/Xcode simulados verifican el orden,
el rechazo de cold si el observador arrancó la app y la ausencia de entrega
cuando termina antes de READY. No son evidencia de recepción real. Revisión
independiente: P2 de sincronización cerrado estáticamente. La versión final
del observador, SHA-256
`fcf25f2ede7095d22ba1defc0a623de0f9794e1adbdd02b6929688f19e257df7`,
compiló/enlazó con TEST BUILD SUCCEEDED y watchdog terminal 0;
logs `deep-links-chat-observer-single-open-{edbb970b,watchdog}.log` en Mac.
Los builds intermedios se conservan; ninguno acredita todavía el recorrido UI.

## Probe completo de importación privada con sesión sintética

Run `23bfcb7b-faeb-42ca-a4bb-daf4776fac48`: DPAPI Windows → canal SSH → input
privado del worker → método `testOwnedDeepLinkSessionStep` → Keychain de la app
en host pasivo → borrado exacto → cierre. Los tokens/IDs son sintéticos y no
corresponden a ninguna cuenta o sesión Auth; no existe cliente DB en el probe.
El install exige Keychain vacío, por lo que no sustituye una sesión existente.

Install `1b0ccefb-1639-48b2-8ac4-d0eff6231748` PASS 0,017 s y clear
`0be4c36f-b715-4460-9cd0-498e29fce744` PASS 0,014 s, ambos con éxito terminal
XCTest y watchdog 0. Canal cerrado/settled, proceso Node terminal 0,
cleanupComplete true, journal retirado y directorio privado Windows vacío.
Se comprobó además ausencia de ambos input.json en Mac, xcodebuild ausente y
simulador dedicado apagado. Reporte y logs en
`build-reports/flow-deep-links/ios-private-session-synthetic-23bfcb7b-faeb-42ca-a4bb-daf4776fac48/`.
El script local `probe-ios-private-session.mjs` impide otro run si uno anterior
queda sin limpiar. Cero operaciones backend; no acredita autenticación real,
sesión renovada ni navegación Chat.

Revisión independiente: **GO acotado a install/clear sintéticos por la rama
real del worker**, sin transferencia a autenticación o enlaces Chat.

El lector `scripts/flow-deep-links-ios-manifest.py` permite fijar hashes de los
bundles app/UITest y fuentes/worker/runtime público sin imprimir valores de
configuración. Primera lectura en `ios-native-build-fingerprint.json`: producto
edbb970b, app `45e4f5f46e15005b0bca5bf77789222ce6e2b6c1b4924e85dcc20ae0712cace1`,
runner UI `7a254048a30f3855c8267400bd94f2f7895d2318cefa559021f138aa97160b54`.
Se deberá volver a comprobar ese fingerprint antes de crear fixtures reales;
este archivo por sí solo no establece correspondencia entre fuente y binario.

## Ensamblado del ensayo real iOS

`flow-deep-links-ios-private.mjs` recibe el servicio Admin exclusivamente por
stdin y abre DB con CA verificada. `flow-deep-links-ios.mjs` usa el coordinador
de perfiles/sesiones propios, con preflight anterior a su creación: términos,
fuente de producto local/Mac, bundles/fuentes/worker, URL y clave pública
efectivas del Info.plist compilado, versiones/hash de Edge y fingerprint DB.
La revisión añadió hash y validación de rutas efectivas del único xctestrun:
host/bundle de sesión dentro de la app fingerprintada, runner/bundle UI y
UITargetAppPath exactos. Snapshot `ios-native-pretrial-fingerprint.json`, plan
`6e3d1c5667f6d340dbb8a8655cdd1cd6e5dd6e9f42af776dafe11b1e54dd6fd7`.
Revisión independiente sin bloqueantes estáticos tras ese ajuste.

Primer intento, runner bcda6cf0, run `06171960-1e69-4e2e-b392-649773a8b493`,
PID 20372 terminal 1: falló en preflight antes de crear fixtures. Cleanup
completo y directorio privado vacío. La URL compilada tenía barra final;
se aceptan ahora únicamente las dos representaciones equivalentes del mismo
backend raíz. No se altera configuración ni endpoint. Artefactos:
`ios-owned-chat-3d7d1440-2a12-4591-a6fc-8ae4761828fd/`.

Segundo intento lanzado sobre runner f934f268, PID 14884, directorio
`ios-owned-chat-53198e9c-32db-453d-ab9e-0f56ad401f58/` en
`build-reports/flow-deep-links/`. Superó preflight y alcanzó importación nativa
de sesión. Su resultado y limpieza todavía deben registrarse: este arranque
no concede aceptación iOS ni autoriza repetir un intento sin reconciliarlo.

Resultado del segundo intento: run `6b05776c-d8e7-4694-a155-f6a27cd1141f`,
proceso terminal 1. Install nativo `26e3b067-8483-46f0-9142-492affc21a86`
verificado; cold `1c60a827-0372-458b-bc4a-e59bb4ea3769` falló al no encontrar
el mensaje seleccionado (XCTest 65, watchdog 65). El log contiene READY,
pero no hay recibo de entrega. La captura observada muestra SpringBoard;
no permite atribuir todavía el fallo a producto, openurl o sincronización.
Warm no se ejecutó. **Sin GO iOS**.

Reconciliación exacta completada, proceso terminal 0: clear
`89018384-1c06-4cfb-883a-0e0f9c260a82`, XCTest y watchdog 0, input privado
retirado, canal nativo cerrado. El reconciliador verificó ausencia de los dos
perfiles/Auth y sus sesiones tras retirar el hilo propio; journals y locks
retirados, directorio privado Windows vacío. `reconciliation.json` registra
cleanupComplete true y nativeClosed true. Se conserva el fallo original en
`report-before-reconciliation.json`; `report.json` continúa failed, ahora con
cleanupComplete true y reconciled true. Esta limpieza no convierte el ensayo
en aceptación ni repite el recorrido. Antes de otro ensayo real se debe
instrumentar el punto de entrega para distinguir sus fases fallidas.

## Ensayo instrumentado y diagnóstico de la etiqueta iOS

Runner `2e0d33a7`, run `75ea641f-a3d9-4410-b9d6-8afffcdcbb6c`, directorio
`ios-owned-chat-d34b6508-460e-4e73-a68a-16faf45c9f26/`: preflight verificado,
install `e744d6e2-2ffc-4147-9276-4113c111403d` terminal 0. Cold
`398213d2-edea-4233-9dc8-cfbd3abf04f9` termina con XCTest/watchdog 65.
`delivery-diagnostic.json` conserva preDeliveryPid null, deliveredPid 38539,
fase waiting_observer_terminal y observerExitCode 65. La captura inspeccionada
`observed-live.png` muestra el hilo y body propios. Warm/vuelta no ejecutados.

Las jerarquías del xcresult exportadas después del fallo demuestran la causa
del selector: `selected-ax.txt` contiene la burbuja Button con ID
`chat.message.11171.selected`, estado selected y dimensiones reales, además
del marcador Other de 1 dp. La etiqueta iOS concatena descripción, remitente,
hora y texto; no es igual a la descripción Compose aislada. El predicado de
igualdad del observador no podía coincidir aunque hubiera selección.
La corrección conserva el ID exacto y exige el prefijo completo con separador,
más el StaticText descendiente con body exacto, antes de exigir visibilidad y
capturar. Es una corrección del observador; no cambia producto ni duración del
resaltado. Requiere recompilación y nuevo ensayo para acreditar el recorrido
completo; el fallo histórico no se transforma en PASS.

Reconciliación terminal 0: clear `bd02aa5a-24ae-46d8-9248-4b8c1403e899`
XCTest/watchdog 0, input ausente, simulador apagado y nativeClosed true.
El reconciliador verificó retirada de hilo, dos perfiles/Auth y sesiones;
private Windows vacío y cleanupComplete true. Se preserva el reporte original
y el actual queda failed/reconciled. No quedan recursos pendientes de este run.

## Chat iOS frío/caliente: ensayo corregido completo

Run `b5d489cb-8fae-420b-b861-af32a06b6152`, runner `a9a86189`, producto
`edbb970b0108d0e6804f7a010865edfcdfaecded`. Artefactos:
`build-reports/flow-deep-links/ios-owned-chat-7caf2a5a-03e2-472a-bb61-c4c6d5864285/`.
App bundle `45e4f5f46e15005b0bca5bf77789222ce6e2b6c1b4924e85dcc20ae0712cace1`,
UI runner `68929e5f37b89a7b895d3f6880dd26e92086633bdbd45aef14f9f0dccfffe74d`,
observador `c2b54ea42b9eb319c095c266dd5330fa332c3c93670886e927892cebbbad5f25`.
Build-for-testing y watchdog 0; preflight volvió a verificar procedencia,
runtime y backend antes de crear fixtures. No hubo despliegue.

Install `995efc04-12fe-4b11-b7c1-681e981c9453` verificado. Cold
`598ac966-5626-4825-b968-96e46210aec6` PASS 23,788 s; warm
`f331ca84-f2e1-4c67-aa6c-027198c5c993` PASS 25,873 s, ambos XCTest/watchdog 0.
Enlace al hilo propio 2542/mensaje 11172, una entrega por tramo después de READY.
Cold sin PID previo, PID 40099 tras entrega y conservado durante ambos tramos.
Las capturas `cold-focused.png`, `cold-back.png`, `warm-focused.png` y
`warm-back.png` se inspeccionaron por orquestador y revisor independiente:
burbuja exacta resaltada y descubierta, y listado de Chats al salir.

Clear `dfc4d244-3709-47c1-aae1-e15f3bba18e1` XCTest/watchdog 0. Coordinador
PID 9792 terminal 0, report passed/cleanupComplete true; retiro automático
verificado de fixture/sesiones, journals/lock retirados, private Windows vacío.
Inputs install/clear ausentes en Mac, simulador dedicado apagado y xcodebuild
ausente. Los fallos anteriores conservan su evidencia e identidad originales.

Alcance local: sesión real previamente verificada e importada en Keychain,
custom scheme externo al mensaje exacto, frío/caliente y Back. No acredita
login nativo, recuperación/renovación de sesión iOS, cancelación anónima,
Universal Links, reapertura tras reiniciar ni estabilidad indefinida. No cambia
el inventario integrado ni concede GO a toda FLOW-DEEP-LINKS o unidades vecinas.

Revisión independiente tras confirmar terminación y limpieza: **GO local focal
definitivo** únicamente para el alcance anterior.

## Preparación de sesión Web vencida con documento ya abierto

El opt-in `sessionDeliveryMode: "warm"` conserva una sola sesión/ticket y un
solo intento de refresh por run. Arranca Feed con metadatos originales vigentes,
espera ruta y ausencia de splash, exige cero refresh previos y fija timeOrigin.
Una misma evaluación comprueba que los tres tokens originales siguen intactos,
la expiración es finita y vigente y no hubo ruta/foco Chat; después cambia sólo
el metadato expires_at a 0 y entrega el hash. No restaura ni reinyecta una sesión
que bootstrap haya modificado. El observador verifica refresh real y recibo,
documento conservado, destino/foco/salida; revocado exige barrera sin episodios
privados y no convierte una barrera tardía en PASS.

Este diseño aborda vencimiento local al entregar con el documento abierto;
renovar durante bootstrap y entregar después sería otro caso. La revocación
propia sigue ocurriendo antes del bootstrap: no se afirma invalidación
anticipada del JWT ni revocación durante uso activo. El modo frío conserva
su recorrido. La preparación y los tests sintéticos no conceden aceptación
real ni cierran todavía las filas calientes.

Validación local: 19 tests Node/Chrome PASS, cero omitidos, proceso terminal 0.
Incluyen frío/caliente, renovación omitida, flash privado, rechazo no verificado,
token cambiado y expiración corrupta antes de entrega, más regresión del runner
ordinario (ancla ausente, body incorrecto, selección cubierta y cierre ante
diagnóstico bloqueado). Backend HTML/HTTP sintético; cero mutaciones Supabase.

## Renovación Web caliente: evidencia real y cierre

Run `9c4d6c12-eb81-425a-8620-0017ce2c5967`, runner `d9585295`, producto
`edbb970b0108d0e6804f7a010865edfcdfaecded`, distribución
`39a6b782a1be4c0fe010d1dc3ed1e4a455e67933dea09a7ce9747515a2f724a5`.
Directorio `web-warm-session-refresh-f0963358-8bb2-4857-809a-31be90118a39/`
bajo `build-reports/flow-deep-links`. PID 24784 terminal 0, PASS,
cleanupComplete true y private vacío tras retiro verificado de recursos propios.

Feed abierto con sesión original; cero refresh antes de vencer únicamente el
metadato local y entregar el enlace en una evaluación. Mismo documento, una
renovación real entregada y verificada, hilo 2543/mensaje 11173, un episodio de
foco descubierto que se consume, Back y recarga a Chat sin reapertura ni nuevo
refresh, cero errores. Orquestador y revisor inspeccionaron las dos capturas
`ui/web-chat-warm-{target,back}.png`: mensaje exacto resaltado y listado al salir.
La recarga sólo tiene evidencia de estado del runner, sin captura propia.

Revisión independiente: **GO local focal warm refresh**. Metadatos locales,
no JWT realmente vencido, sesión revocada ni otras plataformas. La procedencia
fría anterior se conserva y no se transfiere por inferencia. Sin GO integrado.

## Sesión revocada Web caliente: fallo de producto pendiente

Run `0066f5dc-6775-4223-a9d1-1f106d05cf50`, runner `d9585295`, producto
`edbb970b` / distribución `39a6b782…` anteriores. Directorio
`web-warm-session-revoked-a8562d61-9ed7-4503-93be-f1496a0e899b/`.
PID 10352 terminal 1, report failed pero cleanupComplete true y private vacío.
Una petición de refresh, rechazo real entregado y recibo verificado; cero
errores de página. El runner falló en revoked_barrier: expectedRouteReached
true y selectionSeen true. La captura inspeccionada muestra mensaje propio en
Chat y error de contactos, en vez de barrera anónima. No se rebaja el criterio
de ausencia de ruta/foco privado ni se concede GO por alcanzar una barrera tarde.

El gate Web mantiene currentUserId del bootstrap mientras el repositorio
revalida y retira credenciales rechazadas. Hace falta verificar el acceso antes
de montar una nueva ruta privada con enlace caliente, conservando destino
pendiente, login/cancelación y rechazo de resultados asíncronos obsoletos.
La corrección y su evidencia aún están pendientes; no despliegue backend ni
mutaciones/restituciones pendientes de este run.

## Corrección de acceso previo a la composición Web privada

`WebPrivateRouteAccess` vincula el permiso a revisión de navegación, decisión
de autenticación y fragmento exacto. Main no monta el destino privado ni mantiene
su marcador mientras resuelve `sessionForAuthenticatedRequest()`. Sólo el
resultado todavía vigente actualiza la identidad UI; el nulo reutiliza el
pendiente exacto y el diálogo común. Login, logout, apertura de Auth y cancelación
invalidan respuestas anteriores. La guarda no borra preferencias por su cuenta.

El consumo interno de messageId conserva conversación, permiso y host; un enlace
externo a otro mensaje exige otra validación. El eco hash del consumo no abre una
nueva revisión. El resultado suspendido comprueba revisión y coroutine activa
antes de aplicar efectos, incluso si el proveedor devuelve después de cancelar.

Revisión estática independiente sin bloqueantes. Pasada final Chrome/Kotlin:
22 tests PASS, cero omitidos (6 guarda, 11 navegación y 5 rechazo/renovación Auth),
proceso terminal 0. Log `web-private-route-access-final-tests.log`; la primera
pasada de 21 tests se conserva con su alcance anterior al último negativo.
Esto prueba guarda/repositorio/navegación; todavía no el montaje real de Main
con sesión revocada. Hace falta nuevo bundle y ensayo real; el fallo histórico
permanece fallido y la aceptación caliente revocada continúa pendiente.

## Revocado caliente corregido: aceptación real local

Run `22003a67-7aef-4727-b1ce-3eb516792dd1`, producto/runner
`5676e3a34334899d3682764911cfd4a8057230b2`, distribución
`b2bc94544576f482869f3c67e87affa6caf3aa10edd69ba8dc962aa032b21b19`.
Build terminal 0 (`web-private-route-access-bundle.log`). Directorio
`web-warm-session-revoked-419ccc52-9aae-4d45-9b03-6f1c2c334063/`, PID 4708
terminal 0, report PASS/cleanupComplete true y private vacío.

Feed con documento ya abierto, metadato vencido inmediatamente antes del enlace,
cero refresh previos y un rechazo real entregado/verificado. Mismo documento,
barrera sobre Feed, cero ruta/foco Chat observados y cero errores. Orquestador
y revisor inspeccionaron `ui/web-chat-warm-revoked-barrier.png`: diálogo común
de participación y Feed, sin conversación visible.

Revisión independiente: **GO local focal warm revocado**. Ausencia de mensaje
muestreada en barrera final; transiciones de ruta/foco observadas con
MutationObserver. No acredita invalidación anticipada del JWT, borrado local,
otras plataformas ni GO integrado. El fallo anterior permanece preservado.
Regresiones sobre este nuevo bundle aún necesarias: renovación válida caliente,
acceso privado frío y continuación/cancelación tras login. No se presume su
aceptación por esta comprobación negativa.

Regresión warm refresh sobre el mismo producto/runner `5676e3a3` y bundle
`b2bc9454…`: run `1d299de6-5fe0-4210-9d5d-9d00fa696950`, directorio
`web-warm-session-refresh-7f68900d-bdb1-44c2-be64-9d64fda04093/`. PID 18308
terminal 0, PASS/cleanupComplete true y private vacío. Hilo 2546/mensaje 11176,
cero refresh previos y uno real verificado tras vencer metadato y entregar;
mismo documento, foco único descubierto y consumido, Back y recarga a Chat,
cero errores. Orquestador y revisor inspeccionaron target/back: mensaje exacto
resaltado y listado. Recarga acreditada por estado, sin captura propia.
**GO local focal de regresión warm refresh**; no prueba JWT realmente vencido
ni certificación integrada. El caso previo edbb970b se conserva separado.

Regresión cold refresh: run `dbf3567a-858e-4995-9138-0a15d7c8e814`, runner
`e09e2da7`, mismo producto `5676e3a3` y bundle `b2bc9454…`. Directorio
`web-guard-regression-refresh-cold-84f37a75-0e1c-4cc7-b15c-cee7e8534bc0/`.
PID 24820 terminal 0, PASS/cleanupComplete true y private vacío. Un refresh
real entregado/verificado durante restauración fría con metadato vencido;
hilo 2547/mensaje 11177, foco único descubierto/consumido, Back y recarga a
Chat, cero errores. Dos capturas target/back inspeccionadas por orquestador y
revisor: mensaje resaltado y listado. Recarga sólo acreditada por estado.
**GO local focal cold refresh**; no JWT realmente vencido ni otras plataformas.
Quedan continuación y cancelación tras login sobre este bundle antes de dar
por cerrada la regresión de la guarda.

Regresión de continuación tras login: run `f8055fe2-5454-4e16-837c-bee4a59e6fd5`,
runner `ea6946db`, producto `5676e3a3` / bundle `b2bc9454…`. Directorio
`web-guard-regression-resume-5aa5cd40-8bd8-490a-bdea-cd56244013e9/`, PID 16748
terminal 0, PASS/cleanupComplete true y private vacío. Barrera anónima seguida
de un login real por el bridge del repositorio; mismo documento, hilo 2548,
mensaje 11178, un foco y cero errores. Capturas barrier/result inspeccionadas:
diálogo visible (fondo negro, sin claim del contenido público subyacente) y
burbuja exacta resaltada después del login. No acredita Submit manual, otras
plataformas, salida/recarga de este caso ni certificación integrada.

Revisión independiente de continuación: **GO local focal** sobre esos mismos
reportes y dos capturas. El diálogo sobre fondo negro no acredita contenido
Feed visible; no promueve cancelación ni otras plataformas.

Regresión de cancelación y login posterior: run `524ccfe5-0fb0-450c-a516-ff60dbca5a0f`,
runner `46e58186`, producto `5676e3a3` / bundle `b2bc9454…`. Directorio
`web-guard-regression-cancel-d1d8dbc8-3a41-4200-93b9-c6f985ef37d1/`.
PID 25584 terminal 0, PASS/cleanupComplete true y private vacío. Mismo documento,
ruta Feed, cero episodios de foco y cero errores. Tres capturas inspeccionadas
por el orquestador: barrera anónima, Novedades tras login y Feed visible después
del cierre normal de Novedades. Login por bridge de repositorio, no Submit manual.
Observación tras cancelar acotada a dos segundos antes de recarga; no acredita
Android/iOS ni ausencia indefinida de reapertura. Revisión independiente de
reporte y tres capturas: **GO local focal**. Quedan cerradas estas regresiones
Web de la guarda; no se promueven otras plataformas ni la candidata final.


## Cancelación del enlace Chat: contrato UIKit sin sesión real

Se añade `testCancelledExternalChatLinkDoesNotReplayWhenAuthenticationAndChatFactoryArrive`
a `QuataFeedFrameworkTests.swift` (37 líneas). Usa el parser de custom scheme,
router UIKit y factories sintéticas: entrega Chat/hilo/mensaje, espera aviso,
invoca el callback de cancelación, activa el estado autenticado del router e
instala tarde la factory Chat. Exige cero llamadas residuales y Feed conservado;
un segundo enlace distinto debe llegar exactamente a su conversación/mensaje.
Esto evita confundir cancelación efectiva con un router deshabilitado.

Separación por capas: la unidad ejercita el contenedor y ciclo modal UIKit,
por eso usa XCTest de contrato (sin XCUI, teclado ni gestos). No prueba el
contenido Compose del diálogo, el gesto para cerrarlo, una AuthSession real,
login ni entrega externa por el sistema; esas aceptaciones siguen pendientes.
No añade infraestructura Maestro ni modifica producto.

Run `5cffdc90-effe-4f40-b6e7-e721c089c7a0`, producto iOS `edbb970b`, fuente de test
SHA-256 `622195a87b2f140cae90682a3bc76bd494b072d62775febb39466776f33fc256`.
Report/log locales `build-reports/flow-deep-links/ios-cancel-contract-5cffdc90/`;
xcresult remoto `build/reports/ios/cancel-contract-5cffdc90-effe-4f40-b6e7-e721c089c7a0/tests.xcresult`.
Cuatro tests ejecutados, cero fallos/omitidos: nuevo caso, conservación de Chat
hasta factory autenticada y arranque frío con/sin restauración. XCTest/watchdog 0;
simulador dedicado apagado y plan temporal retirado. Sin importación de sesión,
fixtures backend ni mutaciones de credenciales. El primer build falló por declarar
no opcional el ID que entrega la factory; corregido a `String?`. Build final 0.
Revisión independiente del diff final y log: **GO del contrato local sintético**.
La reconstrucción del bundle de tests no sustituye los fingerprints de anteriores
ensayos E2E ni renueva su procedencia por inferencia.


## Enlace Chat anónimo externo iOS: barrera y salida de Login

Observador opt-in `testAnonymousExternalChatOpensLoginAndCancelsToFeed`: no inicia
ni activa la app. Publica READY, acepta como máximo un aviso Abrir del sistema,
exige el diálogo real, pulsa «Ya tengo cuenta», verifica `auth.forgot-password`
y cierra mediante `quata-ios-auth-close`. Captura barrera, Login y Feed final.
No introduce credenciales ni envía login. Las ausencias de Chat son comprobaciones
puntuales; no se afirma ausencia durante todo el intervalo ni cancelación directa
del diálogo inicial. El cierre de Auth no equivale a login posterior sin replay.

Antes y después, `testDedicatedDeepLinkHostHasNoStoredSession` verifica, sin escribir,
ausencia en Keychain y ausencia de error de lectura. El coordinador local reutiliza
el lease del worker, exige PID ausente antes de la única entrega fría, verifica
continuidad del PID hasta terminar XCTest, archiva planes y apaga el simulador.
El ID numérico centinela de la URL no implica existencia del hilo/mensaje ni lectura
autorizada. XCTest cubre recepción Apple y ciclo modal, sin nuevos gestos frágiles.
Revisión estática incorporada: ancla propia de Login y timeout exterior del probe.

Ensayo iniciado: `7e797863-b5da-4bf3-b24c-e536de78168b`. Producto `edbb970b`, build
`deep-links-anonymous-chat-build-login-anchor` terminal 0. Manifest local
`build-reports/flow-deep-links/ios-anonymous-chat-manifest.json` registra producto
limpio, hash app `718055a2…`, runner UI `51219599…`, observador `13dc02df…` y probe
`61213c58…`. Coordinador local `build-reports/flow-deep-links/run-ios-anonymous-chat.py`.
Resultado terminal fallido, XCTest/watchdog 65. Probe inicial vacío PASS, PID
antes de URL null y entregado 43662; fallo en selector de login, sin llegar a Auth.
Simulador apagado y plan archivado. Captura/AX exportados a
`build-reports/flow-deep-links/ios-anonymous-chat-7e797863/attachments/`:
diálogo inglés, botón exacto «I have an account»; el selector exigía español.
No hay GO del recorrido. Este fallo conserva su procedencia; no se repite ni se
reclasifica. El probe posterior no se ejecutó en esta primera versión del
coordinador; no hubo interacción con credenciales. La siguiente versión lo mueve
al finally también para fallos y conserva la comprobación previa a toda entrega.
Corrección sólo del observador: etiquetas exactas ES/EN, sin coordenadas ni
cambio de producto. Build `deep-links-anonymous-chat-build-localized` terminal 0.
Pendiente nuevo ensayo separado y revisión de capturas completas.


Revisión estática de la corrección y del coordinador: aprobada, sin relajar
criterios. Nuevo ensayo `e1b16bb5-6868-42ff-b6a1-4063703aa018` en ejecución;
manifest previo `build-reports/flow-deep-links/ios-anonymous-chat-localized-manifest.json`.
Esperar su proceso y resultado terminal; no relanzar por un timeout de observación.


Ensayo `e1b16bb5-6868-42ff-b6a1-4063703aa018` terminado: fallo XCTest/watchdog 65,
probes de almacenamiento vacío antes y después PASS, simulador apagado, planes
archivados. PID frío null→44605. Abrió Login real (`auth.forgot-password` presente),
pero falló `quata-ios-auth-close.isHittable`; captura Login inspeccionada sin X
visible. Report/capturas locales `build-reports/flow-deep-links/ios-anonymous-chat-e1b16bb5/`.
Sin credenciales introducidas, login enviado ni escrituras backend. No hay GO de
cancelación; preservar también el primer fallo de localización por separado.

Corrección focal de producto en preparación: `IosDismissibleAuthViewController`
aloja el formulario Compose como hijo y el botón nativo de salida como sibling
superior. Antes, el botón se añadía directamente a la vista de Compose, cuyo
renderer se monta después y puede cubrirlo. Se conservan callbacks, estilos
fullScreen/overFullScreen y formulario compartido. Contrato nuevo de hit-test
tras añadir contenido opaco tardío; contratos de Login y Registro adaptados a
containment. La revisión independiente pidió incluir el contrato de Registro;
corregido. Falta ejecución de contratos y nueva evidencia visual del producto.
La evidencia anterior conserva su SHA; esta corrección no se presume certificada.

Revisión focal de alcance: la matriz no exige todos los cruces plataforma×sesión.
Siguen pendientes recepción externa Android por cauce permitido, destino Chat
inexistente iOS con sesión autorizada y resolver límites de PID caliente donde
sean necesarios. No se añaden por inferencia JWT realmente vencido, multimedia,
Universal Links iOS ni Submit manual. Sigue pendiente la candidata final y su
integración certificada, con reconciliación del inventario tras merge.


Contenedor revisado sin bloqueantes estáticos tras corregir también Registro.
Build `deep-links-auth-close-container-build-final` terminal 0. Contratos en
curso: `f4d6a428-31a2-4eee-9b59-fa9789867cbe`; esperar el mismo proceso, sin
relanzarlo. Fuente producto SHA-256
`0e6d13a56179dd3edfdc59f3b2fa468355f88434c05deb8669cfc177f6c7e870`;
fuente de test `d099bf7618e64cb15aefba710a6c641e0317d6f907bd5456dabd30401b4adbc7`.
El Mac conserva base `edbb970b` con este diff de producto: no etiquetar la nueva
compilación como producto limpio edbb970b ni reutilizar su manifest anterior.
Reconciliar la identidad del producto antes del siguiente ensayo E2E.


Resultado de contratos `f4d6a428-31a2-4eee-9b59-fa9789867cbe`: cuatro ejecutados,
cero fallos/omitidos, XCTest/watchdog 0. Report/log locales
`build-reports/flow-deep-links/ios-auth-close-contract-f4d6a428/`. Simulador apagado
y plan temporal retirado. Los hashes de fuentes coinciden con los anteriores;
producto guardado en `c311f281`. Se verifica hit-testing sobre contenido tardío,
Registro, continuación a editor tras Login y ausencia de replay de Chat cancelado.
Estos contratos no sustituyen la repetición visual del E2E sobre el nuevo producto.

Revisión independiente de resultados: **GO acotado a los cuatro contratos UIKit**.
El reporte conserva base edbb970b más hashes de fuente; no se relabela como build
completo de c311f281. E2E pendiente tras reconciliar identidad y bundle.


## Reconstrucción identificada del cierre Auth iOS

Worktree Mac nuevo `/Users/gabriel/StudioProjects/quata-flow-deep-links-c311f281`,
HEAD exacto `c311f281d0ebedf1051d9f1472c51cb97b5f6abd`; el árbol anterior se conserva.
App Swift y tests recompilados desde ese checkout en `build/deep-links-derived-c311f281`.
Build `auth-close-c311f281-build` y watchdog 0; recursos Compose y firma verificados.

Framework Kotlin reutilizado de edbb970b mediante referencia al XCFramework
original: sin diff en core, designsystem, feature, ios-shared, gradle, build.gradle.kts,
settings.gradle.kts, gradle.properties, third_party y build-logic. La revisión
independiente añadió build-logic al conjunto; se comprobó su diff vacío y el hash
del árbol antes/después del build coincide. No hubo builds concurrentes sobre
ese framework. Recibo local `build-reports/flow-deep-links/ios-c311f281-framework-reuse.json`.
Esta reutilización sólo afecta el input Kotlin inalterado, no relabela los E2E anteriores.

Ensayo nuevo `a90d6cc7-33eb-4172-929b-b307cf69d7cd` en curso. El coordinador
`build-reports/flow-deep-links/run-ios-anonymous-chat-c311f281.py` exige manifest
nativo con producto c311f281, fuentes limpias y targets verificados, y registra
sus hashes dentro del reporte. Mantiene probes vacíos antes/después, entrega
única fría tras READY, continuidad de PID y cierre del simulador. Esperar su
terminación; todavía no hay GO del recorrido corregido.


Ensayo corregido `a90d6cc7-33eb-4172-929b-b307cf69d7cd` terminado PASS. XCTest y
watchdog 0, 31.693 s, un test ejecutado sin fallos/omitidos. PID antes de entrega
null→46714 y continuidad hasta terminar observación. Probes de Keychain vacío
antes/después PASS, simulador apagado, sin xcodebuild vivo y sólo plan original
en Products; planes ejecutados archivados bajo el run. No se introdujeron
credenciales, enviaron logins ni crearon fixtures backend.

Report/log/capturas locales `build-reports/flow-deep-links/ios-anonymous-chat-a90d6cc7/`.
Producto c311f281; app SHA-256 `0a035d1982e2c21f86a16a5edd815fbbe064d39f1ff3f95886a39e46fbf40b20`,
runner UI `463ff91bec2ecc94b896409eb356050180259ccaaa7cf6200ee2a6fad6958b52`.
Capturas inspeccionadas por orquestador: diálogo anónimo; Login real con X visible
y formulario intacto; cierre devuelve shell con Feed seleccionado y sin Auth/Chat.
El contenido central de Feed está negro en la captura final: no acredita posts
cargados ni ausencia permanente de reapertura. Comprueba salida de Login, no
cancelar directamente el aviso ni login posterior real. No acredita lectura de
un destino privado inexistente, entrega caliente, otras plataformas ni GO integrado.
Los fallos 7e797863/e1b16bb5 se conservan sin reclasificar.
