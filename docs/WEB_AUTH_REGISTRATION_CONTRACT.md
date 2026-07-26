# Contrato de alta segura para Quata Web

## Estado

El contrato servidor está implementado, pero permanece deshabilitado por
defecto y no se despliega desde esta rama. Se habilita únicamente cuando
coinciden el flag público `quata-web-registration-enabled=true`, el flag servidor
`QUATA_WEB_REGISTRATION_ENABLED=true`, Turnstile y todos los secretos requeridos.

La API `quata-web-register` acepta exclusivamente `version=1` y una allowlist
cerrada. Canonicaliza la identidad como E.164, crea Auth, perfil y sesión Web
mediante service-role, y usa una saga durable con idempotencia, rate limiting y
compensación. Su respuesta es siempre `202 {"version":1,"status":"accepted"}` para
identidad nueva o existente, con suelo temporal y jitter; el cliente continúa
por el login normal. El cliente persiste la clave de idempotencia por identidad
para reintentos incluso tras recargar.

La migración `20260726171004_web_registration_contract.sql` es transaccional y
debe aplicarse después del actor guard `20260726171003`. Android mantiene su
contrato existente. Ninguna policy RLS existente se relaja en este cambio.

La activación funcional queda bloqueada hasta que Android migre su alta al mismo
orquestador mediante un canal con atestación verificable. `quata-auth-bridge` no
acepta altas: una API key pública no sustituye Play Integrity ni la saga durable.

## Operación

Las filas `cleanup_required` quedan en cuarentena. El contrato automatizado de
limpieza exige revocar `web_client_sessions`, borrar el perfil ligado, borrar
Auth, auditar el ledger y emitir alerta, en ese orden; un fallo conserva la
cuarentena y alerta para reintento. La ejecución real requiere credenciales de
operador y no está expuesta al navegador.

Los secretos y nombres de configuración están documentados en
`supabase/functions/quata-web-register/README.md`; no se almacenan valores en el
repositorio. La activación sólo procede tras E2E temporal con purga verificada.
