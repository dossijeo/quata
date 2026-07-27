# Web autenticado en navegador

`scripts/run-web-authenticated-browser-e2e.ps1` verifica un bundle Compose/Wasm ya construido en
Chrome real. El modo predeterminado es hermético: sirve un backend fixture local, bloquea toda red
externa y ejecuta login, recarga/restauración, lectura autenticada y logout mediante
`WebAuthRepository` y `WebPushSessionCoordinator`.

```powershell
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
.\scripts\run-web-authenticated-browser-e2e.ps1
```

El gate no crea controles DOM alternativos. El shell visible sigue siendo Compose/Wasm y el puente
de automatización delega en las implementaciones de producto. Ese puente sólo se publica en
`localhost` cuando la URL contiene `quata-auth-e2e=1`.

## Modo backend real, opt-in

El modo real nunca registra ni elimina cuentas. Requiere una cuenta de prueba preexistente y dos
confirmaciones visibles. Tras el logout de producto revoca globalmente sus sesiones y comprueba
que el refresh token emitido ya no puede renovarse; si esa comprobación falla, el gate falla.
Credenciales y tokens permanecen en memoria y no se escriben en logs ni en el informe seguro.

```powershell
$env:QUATA_SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:QUATA_SUPABASE_PUBLISHABLE_KEY = '<publishable-key>'
$env:QUATA_E2E_COUNTRY_CODE = '<country-code>'
$env:QUATA_E2E_PHONE = '<isolated-phone>'
$env:QUATA_E2E_PASSWORD = '<isolated-password>'
.\scripts\run-web-authenticated-browser-e2e.ps1 `
  -Real -AllowExistingTestUser -AcceptSessionRevocation
```

El runner nunca recibe conexión PostgreSQL, clave `service_role` ni credenciales administrativas,
y no toca RLS, migraciones ni datos mediante una vía privilegiada.
