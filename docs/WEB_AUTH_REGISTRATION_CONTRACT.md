# Contrato de alta segura para Quata Web

## Estado actual

Quata Web **no ofrece alta de cuentas**. `quata-auth-bridge` autentica y vincula un perfil de `community_profiles` ya existente; no debe crear perfiles a partir de campos recibidos desde el navegador. La aplicación Android crea perfiles con su cliente de confianza, una capacidad que no puede trasladarse al bundle Web.

El guard `web_auth_registration_contract_unavailable` impide que el host Web simule ese flujo. La prueba Wasm asociada verifica que el rechazo es local y no realiza ninguna petición de red. SB-02 mantiene su negativa explícita a crear usuarios: sólo puede ejercitar login con una cuenta efímera aprovisionada por un flujo autorizado y luego revocar sus sesiones.

En la ola 2 (`9cc84dc2`) este límite permanece `fail-closed`: los tests Wasm,
la distribución y el smoke Web pasaron, pero eso no constituye un E2E de alta
ni habilita registro remoto.

## API mínima requerida antes de habilitar el formulario

El backend debe exponer una única operación de alta, separada de `quata-auth-bridge`, con estas propiedades:

1. Endpoint público con clave publishable, límite de tasa, protección antiabuso y validación estricta de `display_name`, barrio, teléfono E.164, contraseña y pregunta/respuesta de recuperación.
2. La operación crea el perfil y su usuario Auth dentro de una transacción o un flujo compensable e idempotente por identidad telefónica. No acepta `auth_user_id`, roles, estado de cuenta, avatar ni campos de moderación del cliente.
3. La contraseña nunca se almacena como `pass_plain`; el servidor genera el hash y aplica la política de contraseña. Las respuestas de identidad existente no deben permitir enumeración de teléfonos.
4. El endpoint sólo devuelve una sesión mínima tras crear correctamente la identidad; no devuelve claves de administración ni permite ejecutar RLS con privilegios elevados desde el navegador.
5. Debe existir una vía administrativa aprobada para purgar una cuenta E2E y sus dependencias, más una comprobación de ausencia. El cliente Web no obtiene acceso a esa vía.
6. Deben existir pruebas de contrato para duplicado, validación, limitación, rollback ante fallo Auth/perfil, login posterior y purga. Sólo tras evidencia E2E se podrá declarar la mutación de alta como disponible.

## Integración prevista

Cuando exista ese contrato, se añadirá una acción `web_register` explícita con una respuesta versionada. Se conservarán `web_login`, recuperación y lifecycle sin cambiar su semántica. La implementación Web deberá usar exclusivamente URL y clave publishable configuradas en runtime, persistir la sesión mediante `WebAuthStorage`, y ampliar el navegador E2E con una cuenta temporal purgada.

No se deben relajar políticas RLS existentes para desbloquear este flujo. Cualquier hallazgo de autorización se añade a `docs/RLS_FINDINGS.md` con evidencia, sin aplicar un endurecimiento que pueda romper la versión Web publicada.
