# Contrato de alta segura para Quata Web

## Estado

El contrato servidor está implementado y su base se desplegó el 25 de septiembre
de 2026 tras backup completo y restore drill. `quata-register` y
`quata-auth-bridge` están activos en modo fail-closed; el alta permanece
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
repositorio. La activación de producto sólo procede tras configurar una
credencial Turnstile de producción y ejecutar E2E temporal con purga verificada.
La aceptación controlada puede usar exclusivamente las credenciales oficiales
de prueba de Cloudflare mediante el modo temporal descrito abajo. El recibo del
despliegue de base y funciones está en
[`auth-register-foundation-rollout-20260925.json`](runbooks/migration/evidence/auth-register-foundation-rollout-20260925.json).

## Aceptación real reversible

El runner `scripts/auth-register-real-evidence.mjs` abre una ventana temporal
de alta únicamente con opt-in explícito. En producción exige una site key y un
secreto Turnstile reales y obtiene tokens efímeros con las acciones exactas
`register_web`, `register_android` y `register_ios`, y usa el mismo endpoint que
los clientes. Comprueba payload inválido, challenge inválido, aceptación opaca,
login, pregunta de recuperación e idempotencia Web. No llama directamente a
los RPC de creación para atribuir aceptación al producto.

El modo `QUATA_REGISTRATION_TURNSTILE_MODE=cloudflare-test` está limitado a las
dos credenciales oficiales de prueba de Cloudflare y sólo es válido con
cuarentena y registro temporalmente activos. Primero usa la credencial
`always-fail` para demostrar `challenge_failed`; después usa `always-pass` para
las tres altas. El secreto se carga mediante archivo privado con el binario Go
incluido en la versión fijada de Supabase CLI, porque el lanzador Bun interpreta
`--env-file` como opción propia. Los hashes remotos se comprueban antes de cada
fase. El modo y ambos secretos se retiran al cerrar la ventana y nunca se usan
como configuración permanente ni como evidencia de credenciales de producción.

Antes de habilitar el servidor guarda en el directorio privado un journal de
recuperación con los hashes y UUID sintéticos propios y el baseline de rate
limits. No guarda contraseñas, tokens Turnstile, access tokens ni el secreto
Turnstile. El journal conserva el origen HTTPS exacto necesario para verificar
el cierre. Un watchdog separado espera el plazo de gracia y confirma que el PID
propietario ya no existe antes de asumir la custodia: sólo entonces cierra el
flag servidor, retira el secreto y reintenta la limpieza, sin competir con la
limpieza normal. La conexión, las consultas, las sentencias y los locks de
PostgreSQL tienen límites de tiempo. La limpieza normal también intenta todas
sus fases aunque falle una: primero cierra y verifica el servidor, descubre sus
filas, revoca sesiones, elimina perfiles y Auth y sólo entonces retira el
ledger. Si falla perfiles o Auth, conserva el ledger para un reintento
recuperable. Restaura únicamente los scopes sintéticos de teléfono y cliente.
Los scopes IP son compartidos por todos los usuarios con la misma salida de
red: se observan y reportan, pero nunca se reescriben ni se atribuyen en
exclusiva a este ensayo.

Los valores privados se leen fuera del repositorio. Con la configuración ya
preparada por el operador, la ejecución es:

```powershell
$env:QUATA_AUTH_REGISTER_REAL_OPT_IN = 'I_ACCEPT_TEMPORARY_REAL_REGISTRATION_AND_EXACT_CLEANUP'
$env:QUATA_REGISTRATION_TURNSTILE_MODE = 'cloudflare-test' # sólo aceptación controlada
node --test scripts/auth-register-foundation-rollout-contract.test.mjs
node scripts/auth-register-real-evidence.mjs --out build-reports/auth-register/real-evidence.json
Remove-Item Env:QUATA_REGISTRATION_TURNSTILE_MODE
Remove-Item Env:QUATA_AUTH_REGISTER_REAL_OPT_IN
```

El proceso devuelve éxito sólo si las tres altas y sus comprobaciones pasan,
el endpoint vuelve a `503 registration_unavailable`, el secreto temporal queda
retirado y perfiles, Auth, ledger y scopes de rate limit exclusivamente propios
quedan reconciliados. Los incrementos del limiter IP compartido expiran según
su ventana normal y se informan sin borrar actividad concurrente.
El informe se escribe bajo `build-reports/`, que no se versiona. Un fallo antes
de cargar la configuración también produce un informe redactado; si la limpieza
no termina, el journal privado se conserva y el resultado indica
`recoveryPending=true`.

La ejecución del 28 de septiembre de 2026 acreditó el endpoint desplegado para
los valores de canal Web, Android e iOS: aceptación opaca, login y recuperación
en los tres, más replay idempotente Web. Terminó con `503
registration_unavailable`, sin secretos temporales y con cero perfiles, filas
de ledger o usuarios Auth propios. La ejecución focal vigente sobre Product SHA `0a41aa08` abrió Registro desde
la interfaz Web real, rellenó los controles compartidos, observó un único submit
con el payload propio exacto y los mismos identificadores de cliente e idempotencia
ya custodiados en el journal, recibió la respuesta opaca `202` y alcanzó la
transición autenticada. También verificó login, recuperación y replay
idempotente, restauró `503 registration_unavailable` y dejó cero residuos
propios. Un contrato focal cubre además la caída posterior al POST y anterior al
descubrimiento del ledger, usando ese hash y esos scopes exactos para recuperar.
La proyección saneada está en
[`auth-register-product-web-0a41aa08.json`](candidate-attestations/evidence/auth-register-product-web-0a41aa08.json).
Esta evidencia cierra el envío desde la interfaz Web; las interfaces Android e
iOS siguen pendientes aunque sus valores de canal ya estén acreditados por el
backend compartido.
