# SB-01: catálogo Supabase de sólo lectura

`scripts/run-supabase-e2e-sb01.ps1` comprueba el catálogo que consumen los
repositorios KMP/Web/iOS antes de probar flujos mutantes. No acepta la cadena
por argumento, no llama RPC, no consulta filas de negocio y abre una transacción
`READ ONLY`; además, el servidor recibe `default_transaction_read_only=on`.

El informe contiene únicamente versión de PostgreSQL, nombres/tipos de
relaciones, nombres/tipos de RPC y buckets. No incluye URL, usuario, contraseña,
tokens, parámetros ni datos de usuarios.

## Ejecución en Windows

En una consola cuyo entorno ya tenga la cadena del pooler y una CA de confianza
entregada por Supabase o por el entorno. SB-01 no usa una CA implícita para este
pooler ni rebaja la verificación TLS. Configure **exactamente una** de estas dos
fuentes inyectadas, que nunca se guardan en el repositorio:

```powershell
# Recomendado: ruta local protegida a un PEM de CA confiable.
$env:SUPABASE_DB_TLS_CA_FILE = 'C:\ruta\segura\supabase-pooler-ca.pem'

# Alternativa para un secret manager que inyecte el PEM directamente.
# $env:SUPABASE_DB_TLS_CA_PEM = '<PEM completo de CA>'
```

No configure ambas fuentes. El runner aborta antes de abrir red si falta la CA,
si el PEM no tiene formato de certificado, si se intenta `sslmode=no-verify` o
si la URL aporta parámetros SSL que podrían sustituir la configuración estricta.
La conexión se crea con CA explícita, `rejectUnauthorized: true` y TLS 1.2 o
superior; no existe ningún modo de desactivar esta verificación.

Con esa configuración, ejecute:

```powershell
$env:SUPABASE_DB_URL = '<cadena configurada fuera del repositorio>'
.\scripts\run-supabase-e2e-sb01.ps1
```

En estaciones donde la URL se conserva en un archivo local protegido, puede
pasar **la ruta**, nunca la URL, al runner. El archivo no se versiona ni se
incluye en el informe:

```powershell
.\scripts\run-supabase-e2e-sb01.ps1 `
  -DbUrlFile 'C:\ruta\segura\supabase-db-url.txt' `
  -Output build-reports/supabase/sb-01.json
```

En CI, inyecte `SUPABASE_DB_URL` y `SUPABASE_DB_TLS_CA_PEM` como secretos del
repositorio. No materialice el PEM ni la URL en el workspace ni en los logs.
El workflow manual `.github/workflows/supabase-e2e-sb01.yml` usa ambos secretos,
fuerza el mismo runner y elimina el informe local al finalizar; no se dispara en
push o pull request.

El wrapper descarga temporalmente `pg@8.16.3` fuera del repositorio, con scripts
de instalación desactivados, valida antes la configuración TLS sin conectar y lo
elimina al terminar. Para elegir un destino
local de informe:

```powershell
.\scripts\run-supabase-e2e-sb01.ps1 -Output build-reports/supabase/sb-01.json
```

Un código `0` significa que todas las relaciones, RPC y buckets declarados por
los adaptadores actuales están presentes. `1` indica catálogo incompleto o fallo
de conexión; el error se normaliza para no mostrar detalles de la conexión.

Para validar sólo el guard de configuración local, sin instalar `pg` ni abrir
una conexión, usar:

```powershell
node --test scripts/supabase-e2e-sb01-tls.test.mjs
```

## Registro de evidencia

Al cerrar SB-01 en `docs/MULTIPLATFORM_MIGRATION_BOARD.md`, anotar el commit,
fecha, plataforma/entorno, estado del informe y las listas `missing` vacías. No
subir el informe si el proceso de revisión considera sensibles los nombres de
objetos; basta adjuntar el hash local o copiar el resumen sin credenciales. El
runner no crea ningún prefijo E2E ni precisa limpieza: por diseño no escribe.
