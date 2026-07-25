# SB-01: catálogo Supabase de sólo lectura

`scripts/run-supabase-e2e-sb01.ps1` comprueba el catálogo que consumen los
repositorios KMP/Web/iOS antes de probar flujos mutantes. No acepta la cadena
por argumento, no llama RPC, no consulta filas de negocio y abre una transacción
`READ ONLY`; además, el servidor recibe `default_transaction_read_only=on`.

El informe contiene únicamente versión de PostgreSQL, nombres/tipos de
relaciones, nombres/tipos de RPC y buckets. No incluye URL, usuario, contraseña,
tokens, parámetros ni datos de usuarios.

## Ejecución en Windows

En una consola cuyo entorno ya tenga la cadena del pooler:

```powershell
$env:SUPABASE_DB_URL = '<cadena configurada fuera del repositorio>'
.\scripts\run-supabase-e2e-sb01.ps1
```

El wrapper descarga temporalmente `pg@8.16.3` fuera del repositorio, con scripts
de instalación desactivados, y lo elimina al terminar. Para elegir un destino
local de informe:

```powershell
.\scripts\run-supabase-e2e-sb01.ps1 -Output build-reports/supabase/sb-01.json
```

Un código `0` significa que todas las relaciones, RPC y buckets declarados por
los adaptadores actuales están presentes. `1` indica catálogo incompleto o fallo
de conexión; el error se normaliza para no mostrar detalles de la conexión.

## Registro de evidencia

Al cerrar SB-01 en `docs/MULTIPLATFORM_MIGRATION_BOARD.md`, anotar el commit,
fecha, plataforma/entorno, estado del informe y las listas `missing` vacías. No
subir el informe si el proceso de revisión considera sensibles los nombres de
objetos; basta adjuntar el hash local o copiar el resumen sin credenciales. El
runner no crea ningún prefijo E2E ni precisa limpieza: por diseño no escribe.
