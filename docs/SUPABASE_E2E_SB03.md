# SB-03: Feed y Official de lectura con deep links

`scripts/run-supabase-e2e-sb03.ps1` comprueba las rutas de lectura que usan
los repositorios Web: `community_posts` y `official_posts`. Realiza el mismo
login público autorizado de SB-02, lee cada fila preparada como identidad
autenticada y con la clave publishable sin bearer, y verifica la forma del
fragmento de deep link compartido (`#post-<id>` y `#official-<id>`).

No es un creador de contenido. Actualmente no hay un endpoint Web seguro y
revisado para crear y borrar posts Feed/Official; por ello SB-03 **no inventa
uno ni usa service-role**. Antes de ejecutarlo, un operador debe preparar dos
filas aisladas y efímeras por el flujo autorizado, conservar la responsabilidad
de borrarlas y declarar su contrato de visibilidad pública.

## Precondiciones

SB-02 debe haber terminado con `sessions_revoked`. Se necesita una cuenta E2E
aislada autorizada y dos IDs ya existentes que la identidad de esa cuenta pueda
leer. Los valores `*_PUBLIC_EXPECTED` sólo admiten:

- `visible`: la consulta con clave publishable sin bearer debe devolver la fila.
- `denied`: la consulta autenticada debe devolver la fila y la pÃºblica debe
  devolver una lista vacÃ­a por RLS. No se acepta un 401/403 como sustituto de
  ese contrato porque los repositorios de lectura consumen colecciones.

```powershell
$env:QUATA_SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:QUATA_SUPABASE_PUBLISHABLE_KEY = '<publishable-key>'
$env:QUATA_E2E_COUNTRY_CODE = '<country-code>'
$env:QUATA_E2E_PHONE = '<isolated-e2e-phone>'
$env:QUATA_E2E_PASSWORD = '<isolated-e2e-password>'
$env:QUATA_E2E_FEED_POST_ID = '<approved-feed-id>'
$env:QUATA_E2E_FEED_PUBLIC_EXPECTED = 'visible' # o denied
$env:QUATA_E2E_OFFICIAL_POST_ID = '<approved-official-id>'
$env:QUATA_E2E_OFFICIAL_PUBLIC_EXPECTED = 'visible' # o denied
.\scripts\run-supabase-e2e-sb03.ps1 -AllowExistingTestData
```

El script no acepta URL, credenciales ni IDs como parÃ¡metros. No escribe
tokens, URL, teléfono, contraseña ni IDs en el informe; al terminar revoca las
sesiones de la cuenta E2E. El operador elimina los posts de prueba por su flujo
aprobado y anota esa limpieza en el tablero.

Un resultado correcto verifica el contrato PostgREST y el formato de enlace,
no sustituye un smoke de navegador autenticado ni pruebas visuales del host.
Si no se puede preparar una fila aislada o su visibilidad/RLS no está definida,
el lote queda pendiente: no se debe simular contenido ni convertir el runner
en una mutación privilegiada.
