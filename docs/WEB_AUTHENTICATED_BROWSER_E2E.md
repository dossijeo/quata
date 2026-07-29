# Web autenticado en navegador

`scripts/run-web-authenticated-browser-e2e.ps1` verifica un bundle Compose/Wasm ya construido en
Chrome real. El modo predeterminado es hermético: sirve un backend fixture local, bloquea toda red
externa y ejecuta login, recarga/restauración, el GET de Profile a través del producto, una matriz
de rutas de solo lectura y logout mediante `WebAuthRepository` y `WebPushSessionCoordinator`.
Chat y Novedades no forman parte de este carril: Novedades usa un RPC de lectura transportado como
`POST`, y la mensajería remota conserva su propio E2E, datos y limpieza. La insignia global de
Notifications reutiliza exclusivamente `POST /rest/v1/rpc/quata_chat_get_inbox`; el runner lo
admite sólo durante restauración, la matriz autenticada y el logout mientras la sesión aún existe,
lo responde con un sobre vacío en el fixture y lo registra como evidencia de lectura. Ningún otro
RPC o POST queda permitido.

```powershell
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
.\scripts\run-web-authenticated-browser-e2e.ps1
```

El gate no crea controles DOM alternativos. Si Login expone controles HTML nativos, valida sus
roles, nombres, foco y activación por teclado. Si el Login literal se renderiza sólo en el canvas
Compose, exige un shell/canvas estable y el puente de producto v1 opt-in, y llama a sus operaciones
`login` y `logout`; el puente delega en los mismos repositorios y coordinador. Ese puente sólo se
publica en `localhost` cuando la URL contiene `quata-auth-e2e=1`.

La distribución incorpora `quata-source-revision.txt` al terminar
`:web:wasmJsBrowserDistribution`. El runner exige que ese SHA sea exactamente el `HEAD` actual y
que no haya cambios tracked. Una distribución antigua, un marcador ausente o un árbol sucio
fallan antes de abrir Chrome.

La matriz autenticada recorre Feed, Profile, Settings, Communities y Official mediante los deep
links publicados por el propio producto. El gate observa los GET autenticados emitidos por
`WebProfileHost`/`WebProfileRemoteGateway`; no fabrica un `fetch` paralelo. En el navegador se
permiten GET/HEAD/OPTIONS, dos efectos POST declarados: `web_login` durante login y
`quata-web-push/logout` durante la limpieza, y la lectura exacta de inbox que exige la insignia de
Notifications. Cualquier otro POST/PUT/PATCH/DELETE, incluido cualquier otro RPC PostgREST, se
aborta en origen y hace fallar el resultado.

## Modo backend real, opt-in

El modo real no llama a registro, lifecycle, Supabase Admin ni una conexión PostgreSQL desde el
runner. Sin embargo, **el bridge desplegado no es de solo lectura**: `web_login` actualiza el
usuario de Supabase Auth y `community_profiles` (`auth_user_id`, `last_login_at` y estado), hace
upsert de `web_client_sessions` y puede crear el usuario de Supabase Auth si todavía no existe.
El logout deshabilita suscripciones Web, revoca la sesión Web y la limpieza final revoca
globalmente los refresh tokens del usuario.

Por eso el modo real exige una cuenta dedicada exclusivamente a este E2E, confirmada como
preprovisionada, y aceptaciones separadas para los efectos del bridge y la revocación global. Las
confirmaciones son una declaración operativa: el runner no usa acceso administrativo para
comprobarlas. No se debe reutilizar una cuenta activa en Android, iOS o la Web publicada.

Tras el logout de producto el gate comprueba que el refresh token emitido ya no puede renovarse; si
esa comprobación falla, el gate falla.
Credenciales y tokens permanecen en memoria y no se escriben en logs ni en el informe seguro.
La comprobación sólo acepta un `400`/`401` con un error explícito de refresh token inválido,
revocado o inexistente. Límites de cuota, errores `5xx`, respuestas transitorias o payloads
desconocidos fallan de forma cerrada. La clave debe ser `sb_publishable_*` o un JWT cuyo payload
declare exactamente `role: "anon"`; `sb_secret_*` y cualquier otro rol son rechazados antes de red.

```powershell
$env:QUATA_SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:QUATA_SUPABASE_PUBLISHABLE_KEY = '<publishable-key>'
$env:QUATA_E2E_COUNTRY_CODE = '<country-code>'
$env:QUATA_E2E_PHONE = '<isolated-phone>'
$env:QUATA_E2E_PASSWORD = '<isolated-password>'
.\scripts\run-web-authenticated-browser-e2e.ps1 `
  -Real `
  -AllowExistingTestUser `
  -AcceptSessionRevocation `
  -AcceptBridgeIdentityAndSessionMutations `
  -ConfirmDedicatedWebAccount `
  -ConfirmPreprovisionedAuthUser
```

El runner nunca recibe conexión PostgreSQL, clave `service_role` ni credenciales administrativas,
y rechaza el modo real si detecta variables de DB, service-role o Supabase CLI en su proceso. No
toca RLS ni migraciones. El éxito acredita el recorrido UI, lecturas de producto y limpieza de
sesión; no promete cero cambios de base porque `last_login_at` y los registros/revocaciones de
sesión son efectos deliberados del contrato de autenticación.
