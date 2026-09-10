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
