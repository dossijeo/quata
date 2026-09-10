# FLOW-DEEP-LINKS: aceptación pendiente de sesión Web

Estado: renovación por metadatos locales vencidos verificada en Web frío;
revocación real pendiente, sin GO integrado. Producto de
referencia `c252e00035e97065726fc51aee5a4d6469975324`. No cambia el inventario.

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
