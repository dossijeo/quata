# Registro APNs iOS

El host iOS incorpora el transporte de registro APNs en `IosApnsLifecycleBridge`.
Es una frontera de plataforma: solicita `registerForRemoteNotifications()` solo despues de
que `UNUserNotificationCenter` informe `authorized`, `provisional` o `ephemeral`, y entrega
los callbacks de token/error a `IosApnsRegistrationAdapter`.

El token se convierte a hexadecimal minusculo en memoria y no se registra, persiste ni envia
desde el host. No hay un receptor de token ni de error conectado por defecto, asi que el
adaptador devuelve `Unsupported` en vez de fingir un registro de proveedor exitoso.

## Limites para entrega real

Esto no acredita entrega APNs. Un release posterior necesita, fuera del repositorio:

- un dispositivo fisico y un perfil de provisioning que permita Push Notifications;
- `QUATA_APNS_ENVIRONMENT=development` o `production` inyectado en la configuracion firmada;
- un endpoint autenticado para registrar, rotar y revocar el token;
- credenciales APNs de proveedor y una prueba de entrega/deep-link con limpieza verificable.

`QuataIos.entitlements` solo referencia ese build setting. No contiene certificados, claves,
tokens ni team IDs. La CI y el archive actual son deliberadamente sin firma, por lo que validan
el enlace Swift/Kotlin y XCTest, no el entitlement ni la entrega APNs.

Los requisitos de operación, firma, backend, seguridad y validación de dispositivo se detallan
en [IOS_APNS_PRODUCTION_REQUIREMENTS.md](IOS_APNS_PRODUCTION_REQUIREMENTS.md).

Para la ola 2 (`9cc84dc2`), la CI exacta #30210875187 terminó verde. Sólo
acredita este plumbing y el archive sin firma; la entrega APNs sigue sin
verificarse hasta completar los requisitos anteriores.
