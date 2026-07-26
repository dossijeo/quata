# Web autenticado en navegador

`scripts/run-web-authenticated-browser-e2e.ps1` verifica un bundle Compose/Wasm ya construido
contra el backend real sin introducir credenciales en el bundle versionado ni en el informe.

El runner requiere una cuenta efímera autorizada. Hace `web_login` con la clave publicable,
inyecta URL y clave **públicas** sólo en una copia temporal de `index.html`, persiste en Chrome
los mismos campos que `WebAuthRepository`, abre `#feed` y exige una lectura autenticada de su
propio perfil desde el origen del navegador. Finalmente llama al logout Web y revoca globalmente
las sesiones. El informe no contiene URL, teléfono, identificadores ni tokens.

No acredita todavía la introducción física de credenciales en los controles Compose: acredita la
restauración de una sesión realmente emitida por el bridge, la configuración de despliegue y una
solicitud autenticada desde Chrome. La automatización semántica del formulario Compose será un
gate adicional.

```powershell
$env:QUATA_SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:QUATA_SUPABASE_PUBLISHABLE_KEY = '<publishable-key>'
$env:QUATA_E2E_COUNTRY_CODE = '<country-code>'
$env:QUATA_E2E_PHONE = '<isolated-phone>'
$env:QUATA_E2E_PASSWORD = '<isolated-password>'
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
.\scripts\run-web-authenticated-browser-e2e.ps1 -AllowExistingTestUser
```

La provisión y la purga de la cuenta son deliberadamente externas al script público: el registro
Web aún no existe y el runner nunca recibe una cadena PostgreSQL ni credenciales administrativas.
