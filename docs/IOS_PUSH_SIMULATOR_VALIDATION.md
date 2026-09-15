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
| Interacción | Foreground: comprobar política de presentación equivalente a Android y continuidad de Chat. Background/terminada: tap en la notificación presentada. | Restauración, ruta y mensaje exactos; ausencia de duplicación visible. |
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

## Evidencia focal ejecutada — 15 de septiembre de 2026

PASS del caso **app terminada, destinatario autenticado y mensaje exacto**:
Login nativo real con fixtures propios, verificación de la sesión contra Auth/backend,
inyección única mediante `simctl push`, tap en el aviso visible, apertura del mensaje
esperado y Back a la lista de Chats. XCTest y coordinador terminaron con código 0;
la sesión, el hilo y los perfiles se retiraron con ausencia verificada. Las capturas
y el árbol de accesibilidad conservan los tres estados de la interacción.

- Producto congelado: `fa72792e48bcf2f2ef30d4878ca2b8ebc346aa2f`.
- SHA-256 del bundle: `7be9ff6d095e07366b004de662d022637a5da8953ee7d7364aa9af2ce5198e0a`.
- Ejecución: `69bd1647-c578-4de3-8be1-dc2db86fd255`; paso push: `6c092a1b-6ff6-415f-8ada-4a7132cefff4`.
- Informes privados del host: `build-reports/flow-push-lifecycle/owned-chat-trial-4b3d3cf3-30a0-479f-b4a6-def0d6928cd1` y `owned-push-6c092a1b-6ff6-415f-8ada-4a7132cefff4` bajo el mismo directorio.

El ensayo separado de **background y foreground** terminó con XCTest y coordinador
en código 0 y limpieza completa. Comprobó estado de segundo plano antes del tap,
apertura del mensaje exacto, continuidad de Chat sin banner observado durante la
inyección en primer plano y Back a la lista. La observación de primer plano se armó
antes de la inyección y cubrió su confirmación y una ventana posterior; no acredita
por sí sola un callback de la app ni entrega del proveedor.

- Mismo producto y bundle congelados; integridad posterior idéntica al preflight.
- Ejecución: `1faa2355-395b-4e8e-a790-22c0a9ea423d`; paso push: `3900daaf-2ba0-45f6-8636-88f66f7b865d`.
- Informes privados bajo `build-reports/flow-push-lifecycle/`: `bgfg2-chat-trial-c16f92ec-c0a9-433a-b001-0962b5cf765a` y `bgfg2-push-3900daaf-2ba0-45f6-8636-88f66f7b865d`.

El primer ensayo de background/foreground sigue registrado como fallido: exigía
que el resaltado temporal del mensaje persistiera después de su duración de 8 s.
Se corrigió únicamente el observador para verificar el mensaje exacto tras expirar
el resaltado, conservando las comprobaciones de ruta, contenido y Back.

Es evidencia focal del producto indicado, no certificación integrada de PR ni cierre
completo del flujo. Permisos y cambios de sesión conservan sus comprobaciones
pendientes; no se deducen de estos casos de recepción e interacción.
Los intentos previos fallidos mantienen sus informes y reconciliaciones de limpieza.

El intento separado de registro real no obtuvo token: tras solicitarlo mediante el
bridge del producto, el Simulator siguió sin registro durante la ventana de 30 s.
La configuración usada carece de `aps-environment`; además, la VM expone x86_64 sin
T2 detectado. No atribuir el resultado exclusivamente a virtualización ni confundir
este intento con entrega APNs. La autenticación y entrega del proveedor conservan
su evidencia y requisitos separados.
