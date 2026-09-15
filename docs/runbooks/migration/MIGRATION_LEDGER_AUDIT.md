# Inventario de sentencias para auditar el historial SQL

Esta herramienta prepara la revisión del historial; no cambia el gate de despliegue,
el ledger remoto ni las clasificaciones de `supabase/migration-reconciliation.json`.
Una sentencia parseada no demuestra ejecución ni equivalencia semántica.

Desde la raíz del checkout, en PowerShell y con Python 3.12:

```powershell
python -m pip install --target build-reports/migration-ledger/python-deps -r scripts/requirements-migration-audit.txt
$env:PYTHONPATH = Join-Path (Get-Location) 'build-reports/migration-ledger/python-deps'
python -B scripts/migration-statement-inventory.test.py
python scripts/migration-statement-inventory.py --migrations supabase/migrations --out build-reports/migration-ledger/statement-inventory.json
```

El archivo de salida debe ser nuevo: no se sobrescribe evidencia anterior. Incluye
versiones del parser, hash del archivo completo y AST, tipo y posición de cada
sentencia. Las posiciones son bytes UTF-8 después de retirar un BOM opcional;
pueden incluir comentarios o espacio anteriores a la sentencia.

La revisión posterior debe comparar efectos completos y sustituciones posteriores.
Los bloques `DO`, cuerpos de funciones y cambios de datos requieren inspección de su
contenido: contarlos no acredita sus efectos. El informe mantiene
`deploymentHistoryProven` y `semanticEquivalenceProven` en `false`.

El programa solo lee SQL local. No conecta a PostgreSQL ni ejecuta las sentencias.
Los informes incluyen el AST y, por tanto, literales y cuerpos presentes en los
archivos de entrada; se guardan en el directorio local ignorado `build-reports`.

La [comparación focal de funciones del 14 de septiembre](MIGRATION_LEDGER_FUNCTION_FINDINGS_20260914.md)
separa divergencias de procedencia pendiente de paquetes documentados como pendientes.
El [resultado de la auditoría](MIGRATION_LEDGER_AUDIT_DECISION_20260914.md) explica
los límites de la evidencia y su consecuencia para el despliegue APNs.

El [mapa por sentencia Official/Community](MIGRATION_LEDGER_OFFICIAL_STATEMENT_MAP_20260915.md)
registra supersesiones focales, pendientes del manifiesto y el límite temporal del preflight APNs.
