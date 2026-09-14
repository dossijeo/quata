# Reply Android: corrección de reintentos integrada

La [PR #330](https://github.com/dossijeo/quata/pull/330) se integró el 14 de septiembre
de 2026, merge `ecb5baf51dc1df742a39e6baf9f9b18fb35485e5`, desde el head
`b58e4dc4ca835cd2a1e4082331ca53458ef492b9`.

Una respuesta enviada desde la notificación conserva ahora el mismo identificador
de cliente durante sus tres intentos. Si el servidor guarda el mensaje y se pierde
la respuesta HTTP, el reintento puede reconocer ese mismo mensaje. Se mantienen
las pausas de dos segundos y el tratamiento existente de éxito y error.

La compilación Android, tres pruebas JVM focales y 485 contratos rápidos pasaron,
con revisión independiente sobre la PR exacta. Las pruebas simulan pérdida de
respuesta, agotamiento de intentos y envíos distintos; no prueban entrega remota.
Certificación final: [Android](https://github.com/dossijeo/quata/actions/runs/34856913539),
[gate iOS sin impacto de runtime](https://github.com/dossijeo/quata/actions/runs/34856913564)
y [CodeQL](https://github.com/dossijeo/quata/actions/runs/34856812119), todos SUCCESS.
La ejecución preliminar Android cancelada al promover fue sustituida por la final.

`FLOW-NOTIFICATION-REPLY` sigue con aceptación pendiente. Este checkpoint no acredita
reinicios de proceso, invocaciones independientes del receiver, acción nativa iOS,
equivalencia Web, entrega real ni estados visuales del sistema. No cambia APNs ni SQL.

Prueba pendiente del propietario, sobre un build que incluya el merge: responder
desde una notificación de una conversación de prueba autorizada, comprobar el texto
exacto y la retirada de la notificación; verificar el aviso de fallo sin conexión.
No se afirma que esta ronda se haya ejecutado.
