# FLOW-PUSH-LIFECYCLE — validación iOS en Simulator

Decisión del propietario, 15 de septiembre de 2026: no habrá dispositivo iOS físico.
Su ausencia no bloquea este flujo. Completar todo lo verificable en Simulator,
probar APNs/provider por separado e intentar el registro APNs real del simulador.
Esta decisión no convierte una inyección de payload en entrega del proveedor ni
certifica distribución en App Store/TestFlight.

## Evidencia separada por frontera

| Frontera | Comprobación | Qué acredita |
| --- | --- | --- |
| Recepción y presentación | `xcrun simctl push <UDID> com.quata.ios <payload.apns>` con payload de prueba aprobado | Entrada del payload por el sistema del Simulator; presentación y manejo por la app. |
| Interacción | Tap en la notificación con app foreground, background y terminada | Restauración, ruta y mensaje exactos; ausencia de duplicación visible. |
| Permisos y sesión | Denegar/conceder permisos, login, cambio de actor y logout con perfiles de prueba propios | Comportamiento de permisos y aislamiento del destinatario; registrar por separado cualquier frontera backend no ejecutada. |
| Registro APNs | Invocar el registro real y observar callback de éxito o error, sin exponer token | Capacidad real del entorno para obtener token; no deducirla de `simctl push`. |
| Proveedor | Contratos de firma/payload/errores y pruebas independientes del endpoint APNs | Distinguir transporte HTTP/2, autenticación del proveedor y entrega a token válido. Cada resultado conserva su alcance. |

Usar el simulador candidato con lease exclusivo, SHA y configuración congelados;
preservar el simulador estable. Los payloads temporales no contienen credenciales,
tokens ni contenido de terceros. Los mensajes y perfiles usados son los fixtures
autorizados, con snapshot y limpieza verificables. No publicar el payload completo.

Si la virtualización impide obtener un device token, conservar esa limitación
explícita y continuar recepción, interacción y proveedor. Registrar el error real:
no atribuir a virtualización un fallo de entitlement, cuenta, red o configuración.
No recrear certificados ni exigir un iPhone para resolver esa limitación del entorno.

El cierre de FLOW-PUSH-LIFECYCLE debe enumerar las pruebas realizadas y cualquier
frontera externa no acreditada. La falta de dispositivo físico no es un gate del
flujo. Los requisitos de firma y distribución de producción permanecen separados
en [IOS_APNS_PRODUCTION_REQUIREMENTS.md](IOS_APNS_PRODUCTION_REQUIREMENTS.md).

Estado inicial de esta decisión: plan de validación, todavía sin nueva evidencia
de ejecución. No presentar esta documentación como PASS de Simulator o del proveedor.
