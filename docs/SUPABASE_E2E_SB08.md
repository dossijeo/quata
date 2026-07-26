# SB-08: Web Push y puente APNs

SB-08 valida el transporte de notificaciones, no el inbox de Notifications.
El inbox se alimenta de Chat; Push/Web Push/APNs sólo despiertan el cliente y
transportan el mismo payload de navegación.

## Evidencia disponible

- El `GET /functions/v1/quata-web-push` desplegado devolvió una clave VAPID
  pública con forma válida el 2026-07-26; `OPTIONS` aceptó el header
  `x-quata-web-session`. No se registró ni imprimió la clave.
- `node scripts/web-push-worker-contract-test.mjs` ejecuta el worker real sin
  red ni secretos. Acredita `push` -> notificación y el tap hacia
  `#chat-sb%3A<thread>?message=<id>`, incluido el fallback legado
  `thread_id` y el cierre seguro a la raíz si faltan identificadores.
- El host iOS normaliza un tap ya entregado con
  `IosNotificationResponseBridge`/`IosNotificationDeepLinkAdapter`. El
  bridge no declara que APNs esté registrado ni que exista entrega.
- Auditoría del host: `AppDelegate` instala sólo el delegado de tap. No hay
  una llamada a `registerForRemoteNotifications`, callbacks
  `didRegisterForRemoteNotificationsWithDeviceToken`/fallo, ni entitlement
  `aps-environment` en `iosApp`. `IosApnsRegistrationAdapter` es un contrato
  fail-closed no conectado al host actual; por tanto no hay que interpretar el
  enlace del framework o XCTest como registro APNs.

## Bloqueos externos para la evidencia E2E completa

1. Una cuenta Web aislada y autorizada, con URL Supabase, clave publicable,
   país/teléfono y contraseña disponibles sólo como variables de proceso.
   No hay alta Web pública segura y el runner no puede crear una cuenta.
2. Chrome/Chromium con un proveedor Push operativo para que `PushManager`
   produzca una suscripción real. El modo headless o un perfil sin mensajería
   registrada no constituyen evidencia de entrega.
3. Dos perfiles aislados y un mensaje Chat autorizado para disparar
   `quata-push-dispatch`; el emisor no debe reutilizar datos de negocio.
4. Para APNs: una app firmada con entitlement `aps-environment`, perfil de
   aprovisionamiento, credenciales de proveedor y dispositivo físico. El
   simulador no recibe APNs. No se sustituye esta condición por un mock.

## Criterio de ejecución

Con las precondiciones, el operador debe registrar la suscripción usando el
launcher Web real, enviar un Chat desde el segundo perfil y comprobar en el
navegador receptor que el tap abre la conversación normalizada. Después debe
ejecutar logout Web, confirmar que la suscripción queda revocada y revocar
globalmente las sesiones de las cuentas de prueba. Los informes no pueden
incluir endpoint Push, ID de usuario, teléfono, tokens, VAPID ni claves.

No se debe cambiar RLS ni crear una política para SB-08. Un fallo de registro,
entrega o revocación se anota como evidencia y conserva el estado
`Bloqueado externo` hasta disponer de una entrega real reproducible.
