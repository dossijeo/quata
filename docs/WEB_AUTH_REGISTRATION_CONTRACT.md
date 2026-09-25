# Contrato de alta segura para Quata Web

## Estado

El contrato servidor está implementado y su base se desplegó el 25 de septiembre
de 2026 tras backup completo y restore drill. `quata-register` v1 y
`quata-auth-bridge` v81 están activos en modo fail-closed; el alta permanece
deshabilitada. Se habilita únicamente cuando
coinciden el flag público `quata-web-registration-enabled=true`, el flag servidor
`QUATA_WEB_REGISTRATION_ENABLED=true`, Turnstile y todos los secretos requeridos.

La API `quata-register` acepta exclusivamente `version=1` y una allowlist
cerrada. Canonicaliza la identidad como E.164, crea Auth, perfil y sesión Web
mediante service-role, y usa una saga durable con idempotencia, rate limiting y
compensación. Su respuesta es siempre `202 {"version":1,"status":"accepted"}` para
identidad nueva o existente, con suelo temporal y jitter; el cliente continúa
por el login normal. El cliente persiste la clave de idempotencia por identidad
para reintentos incluso tras recargar.

La migración `20260726171004_web_registration_contract.sql` es transaccional y
debe aplicarse después del actor guard `20260726171003`. Android migra su alta
al mismo orquestador con `channel=android`, `phone_local`,
`client_instance_id` y un challenge Turnstile de acción `register_android`.
iOS usa ese orquestador con `channel=ios` y un challenge efímero de acción
`register_ios`; el token se obtiene al enviar y no forma parte de la
configuración del bundle.
Ninguna policy RLS existente se relaja en este cambio.

`quata-auth-bridge` no acepta altas: la API key pública sólo enruta y el
challenge verificable no sustituye la saga durable del servidor.

## Operación

Las filas `cleanup_required` quedan en cuarentena. El contrato automatizado de
limpieza exige revocar `web_client_sessions`, borrar el perfil ligado, borrar
Auth, auditar el ledger y emitir alerta, en ese orden; un fallo conserva la
cuarentena y alerta para reintento. La ejecución real requiere credenciales de
operador y no está expuesta al navegador.

Los secretos y nombres de configuración están documentados en
`supabase/functions/quata-register/README.md`; no se almacenan valores en el
repositorio. La activación sólo procede tras configurar una credencial
Turnstile real y ejecutar E2E temporal con purga verificada. El recibo del
despliegue de base y funciones está en
[`auth-register-foundation-rollout-20260925.json`](runbooks/migration/evidence/auth-register-foundation-rollout-20260925.json).
