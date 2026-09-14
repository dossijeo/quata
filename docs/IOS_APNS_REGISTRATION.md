# Registro APNs iOS

El host iOS incorpora el transporte de registro APNs en `IosApnsLifecycleBridge`.
Es una frontera de plataforma: solicita `registerForRemoteNotifications()` solo despues de
que `UNUserNotificationCenter` informe `authorized`, `provisional` o `ephemeral`, y entrega
los callbacks de token/error a `IosApnsRegistrationAdapter`.

El bridge convierte el token a hexadecimal minúsculo y lo retiene en memoria para
conectarlo a `IosApnsSessionRuntime`. La raíz de composición instala ese runtime con
la misma sesión renovable que usa la app. El coordinador serializa registro y retirada;
el transporte usa RPC autenticados y conserva en Keychain un journal de limpieza por
backend, sin guardar credenciales de sesión en ese journal.

`QUATA_IOS_APNS_ENABLED=true` habilita nuevos registros. El valor predeterminado es
`false`; un entorno ausente o inválido también impide nuevos registros. El runtime de
limpieza sigue disponible para retirar registros anteriores al cerrar sesión. Un fallo
de limpieza conserva la sesión y permite reintentar el logout.

El entorno de firma `QUATA_APNS_ENVIRONMENT=development` se convierte en `sandbox`
para el registro; `production` se conserva. El RPC nuevo `quata_register_apns_token`
requiere el entorno explícito. La migración aditiva que lo define sigue pendiente de
integración remota; el RPC Android existente no cambia.

## Limites para entrega real

Esto no acredita entrega APNs. Un release posterior necesita, fuera del repositorio:

- un dispositivo fisico y un perfil de provisioning que permita Push Notifications;
- `QUATA_APNS_ENVIRONMENT=development` o `production` inyectado en la configuracion firmada;
- desplegar y verificar el paquete SQL de registro/retirada con sus gates de historial;
- credenciales APNs de proveedor y una prueba de entrega/deep-link con limpieza verificable.

`QuataIos.entitlements` solo referencia ese build setting. No contiene certificados, claves,
tokens ni team IDs. La CI y el archive actual son deliberadamente sin firma, por lo que validan
el enlace Swift/Kotlin y XCTest, no el entitlement ni la entrega APNs.

Los requisitos de operación, firma, backend, seguridad y validación de dispositivo se detallan
en [IOS_APNS_PRODUCTION_REQUIREMENTS.md](IOS_APNS_PRODUCTION_REQUIREMENTS.md).

Como evidencia histórica, para la ola 2 (`9cc84dc2`), la CI exacta #30210875187 terminó verde. Sólo
acredita este plumbing y el archive sin firma; la entrega APNs sigue sin
verificarse hasta completar los requisitos anteriores.
