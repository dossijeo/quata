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
requiere el entorno explícito. La migración aditiva `20260914135400` quedó aplicada
de forma selectiva el 17 de septiembre de 2026, con ledger único, contrato Android
idéntico y conteos de tokens/logs sin cambios. Su rollback revisado se conserva en
`supabase/rollbacks/20260914135400_apns_registration_environment.rollback.sql` y
rechaza deriva o pérdida de registros iOS.

## Limites para entrega real

Esto no acredita entrega APNs. Un release posterior necesita, fuera del repositorio:

- un entorno APNs compatible, con el entitlement efectivo y un token real; los perfiles
  de dispositivo y de distribución se exigen en sus lanes correspondientes;
- `QUATA_APNS_ENVIRONMENT=development` o `production` inyectado en la configuracion firmada;
- credenciales APNs de proveedor y una prueba de entrega/deep-link con limpieza verificable.

La aceptación remota del RPC se ejecutó dentro de una transacción revertida: asociación
al actor autenticado y entorno `sandbox`, rechazo de suplantación, entorno/token inválidos,
ACL exclusiva de reserva, primera reserva y duplicado. Los conteos anteriores se
recuperaron exactamente al terminar. El token fue sintético; no sustituye el callback
de APNs ni acredita entrega desde Apple.

`QuataIos.entitlements` solo referencia ese build setting. No contiene certificados, claves,
tokens ni team IDs. El test de frontera Swift/Kotlin en CI usa `SimulatorSigned` con
firma ad hoc para ejecutar XCTest y las escrituras reales en Keychain. Esta configuración
elimina los entitlements restringidos y no usa la identidad Personal Team ni credenciales
de distribución. El archive de CI sigue sin firma. Ninguna de estas comprobaciones
acredita el entitlement efectivo de APNs ni entrega desde Apple.

La validación de `FLOW-PUSH-LIFECYCLE` en Simulator no requiere un dispositivo físico.
Recepción e interacción mediante `simctl push`, permisos y aislamiento de sesión se
acreditan por separado del registro y del proveedor real, según
[el alcance acordado para Simulator](IOS_PUSH_SIMULATOR_VALIDATION.md). No obtener un
token en ese entorno no convierte las inyecciones locales en entrega APNs ni bloquea
las demás comprobaciones del flujo.

Los requisitos de operación, firma, backend, seguridad y validación de dispositivo se detallan
en [IOS_APNS_PRODUCTION_REQUIREMENTS.md](IOS_APNS_PRODUCTION_REQUIREMENTS.md).

Como evidencia histórica, para la ola 2 (`9cc84dc2`), la CI exacta #30210875187 terminó verde. Sólo
acredita este plumbing y el archive sin firma; la entrega APNs sigue sin
verificarse hasta completar los requisitos anteriores.
