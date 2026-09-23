# Ronda funcional final del propietario

Estado: ejecutada el 21 de septiembre de 2026 sobre Product SHA
`77bdffff818ba76c1b867b4445aefc46e47a86e4`, ya integrado en `main`. Esta ronda conserva las
comprobaciones manuales ya exigidas por el modelo operativo; no añade reglas, no sustituye
evidencia exacta y no repite matrices E2E por un mero cambio de SHA.

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

### Registro ejecutado

| Plataforma | Resultado | Observación |
|---|---|---|
| Android | PASS | Instalación limpia del APK exacto, permiso de notificaciones, Feed público, Login, restauración, cinco raíces, Chat real, un envío y una respuesta únicos, vuelta a la lista, Crear publicación cancelado y visor legal. APK SHA-256 `F657291867DE23FB26D2DEAE4D9424D266C1F2A5A34039BF6D5A83FDDBF6598A`. |
| Web/Wasm | PASS salvo envío autenticado posterior a la activación corregida | El artefacto de la certificación final se sirvió con sus metadatos públicos de despliegue, sin modificar Wasm. Pasaron Feed público, Login real, recarga con sesión restaurada, cinco raíces, Chat abierto desde un aviso, Crear publicación cancelado, visor legal y Logout. Los fallos nativos anteriores se conservan. La traza discriminante posterior identificó el rechazo de `WindowClient.navigate` sobre el cliente aún no controlado; Product SHA `ad018b5a` añade el fallback al mismo destino y un único control nativo observó `notificationclick` y la ruta Chat sintética exacta. Falta renovar el tramo autenticado texto → Send → mensaje único → limpieza sobre el worker corregido. |
| iOS Simulator | PASS con evidencia no afectada reutilizada | Build `SimulatorSigned` exacto, bundle `com.quata.ios`, ejecutable SHA-256 `3fbead250e07599cf0ad606b70cb53a50da2583cff8b4935441fc770aacf54e8` y firma verificada. Pasaron en el Simulator dedicado el seeder autenticado, Crear publicación y retorno sin publicar, Cuenta, detalles, gestión, cancelación de desactivar/eliminar y restauración. La recepción con `simctl push` y Reply nativo conservan el GO de [la evidencia integrada](candidate-attestations/ios-notification-reply-simulator-acceptance.json): banner propio, editor nativo, texto exacto antes de un único Send, delegate/transporte, mensaje único, negativo acotado y limpieza. |

La aceptación iOS no se repitió sólo por el SHA: `IosNotificationReplyAction.swift`,
`QuataIosNotificationReplyUITests.swift`, `notification-reply-ios-trial.mjs` y el coordinador de UI
permanecen idénticos desde la evidencia aceptada. Los cambios posteriores de `QuataIosApp.swift`
afectan al reset focal de términos UGC y al retorno del Chat de comunidad, no al callback ni al
helper de Reply. Los jobs finales de la candidata recertificaron el producto integrado.

No se registraron credenciales, tokens ni cadenas de conexión. La ronda Web no publicó contenido ni
envió mensajes; los recorridos iOS no publicaron contenido. Android creó sólo el texto y su respuesta
sintéticos autorizados. El límite de registro APNs del Simulator virtualizado, la entrega del proveedor
y la distribución pagada permanecen clasificados como limitaciones externas; no requieren un
dispositivo físico para el alcance aceptado.
