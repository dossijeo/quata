# Ronda funcional final del propietario

Estado: pendiente de ejecución. Este handoff reúne comprobaciones manuales ya exigidas por el
modelo operativo; no añade reglas, no sustituye evidencia exacta y no pide repetir matrices E2E.

## Preparación y registro

- Usar builds derivados del `main` integrado y anotar commit, plataforma, fecha y resultado.
- Usar cuentas propias autorizadas. No copiar credenciales, tokens ni cadenas de conexión al
  informe; basta identificar cada actor con un alias local.
- Registrar `PASS` o `FAIL` por recorrido, con una observación breve. Adjuntar captura sólo cuando
  ayude a localizar un fallo; no es necesaria para un `PASS` evidente.
- Si un recorrido falla, conservar el estado observado y detener sus variaciones. La ronda no
  autoriza a cambiar producto, firma, backend o permisos para forzar un resultado.

## Android — referencia y producto migrado

1. Abrir el cliente actual y confirmar Feed público, Login y restauración de sesión.
2. Recorrer las raíces autenticadas Feed, Official, Comunidades, Conversaciones y Cuenta.
3. Abrir un Chat existente, enviar un texto sintético exclusivo, responderlo y volver a la lista
   de conversaciones. Retirar el mensaje desde la propia UI si el flujo lo ofrece.
4. Abrir Crear publicación y volver sin publicar; abrir Ajustes/legales y cerrar el visor.
5. Comparar cualquier divergencia funcional con el AAB publicado v32 / 1.0.4 identificado en
   [la referencia Android](ANDROID_PUBLISHED_REFERENCE_V32.md). Su commit exacto es desconocido.

## Web/Wasm — navegación, sesión y notificación

1. En una ventana normal de Chrome, comprobar Feed público, Login, recarga con sesión restaurada y
   Logout.
2. Recorrer Feed, Official, Comunidades, Conversaciones, Chat y Cuenta; verificar apertura y Back
   sin perder el destino.
3. Abrir Crear publicación y volver sin publicar; abrir Ajustes/legales y cerrar el visor.
4. Activar notificaciones desde Ajustes, dejar la aplicación en segundo plano y recibir una alerta
   propia con conversación y mensaje conocidos.
5. Pulsar una vez el cuerpo del banner recién recibido. Registrar por separado:
   - si el banner desaparece;
   - si Chrome entrega `notificationclick` abriendo o enfocando el Chat/mensaje exacto;
   - si, ya dentro del Chat, se puede enviar un único texto sintético exclusivo y observarlo una
     sola vez.

Las APIs estándar de Web Notifications/Push no definen entrada de texto inline. La equivalencia
funcional propuesta para Web es banner nativo → `notificationclick` → Chat exacto → envío desde la
UI real de Chat. No llamar al handler ni fabricar el callback. Si el banner desaparece sin abrir
Chat, registrar ese hecho como fallo de activación y no atribuir ausencia de soporte del producto
sin diagnóstico adicional.

## iOS Simulator — producto común y notificaciones

1. Comprobar Feed público, Login/restauración, navegación primaria y Logout en el Simulator.
2. Recorrer Feed, Official, Comunidades, Conversaciones, Chat y Cuenta; verificar apertura y Back.
3. Abrir Crear publicación y volver sin publicar; abrir Ajustes/legales y cerrar el visor.
4. Ejecutar la recepción focal ya documentada con `simctl push`: app en background, alerta propia,
   apertura del mensaje exacto y retorno.
5. Ejecutar la respuesta focal: expandir el banner, localizar el editor nativo, escribir un texto
   sintético exclusivo, comprobarlo antes de un único Send y observar un solo mensaje en Chat.

La receta y los límites están en
[validación iOS Simulator](IOS_PUSH_SIMULATOR_VALIDATION.md) y
[Notification Reply iOS](IOS_NOTIFICATION_REPLY.md). No se requiere dispositivo físico. APNs real,
App Store Connect/TestFlight y distribución pagada permanecen fuera del alcance aceptado.

## Resultado de la ronda

La ronda queda completada cuando los recorridos anteriores tienen resultado registrado y cualquier
`FAIL` está clasificado con su punto exacto de ruptura. Un fallo Web en `notificationclick` mantiene
abiertos `FLOW-PUSH-LIFECYCLE` y `FLOW-NOTIFICATION-REPLY` para Web; no invalida por sí solo Android,
iOS Simulator, dispatch/delivery Web ni las unidades ya integradas.
