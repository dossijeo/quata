# FLOW-DEEP-LINKS: aceptación pendiente de sesión Web

Estado: preparación revisada; no es evidencia ejecutada ni GO. Producto de
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
coordinador usa un journal DPAPI real con backend simulado. Falta conectar el
adaptador de navegador: interceptar la petición real, comprobar URL y payload,
permitirla sólo después del checkpoint, entregar su respuesta al producto y
reconciliar su cierre. No se ha ejecutado renovación ni revocación real por
este código. El caso pre-revocado sigue necesitando su ciclo específico.
