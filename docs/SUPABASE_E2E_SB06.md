# SB-06: Profile y SOS autenticados

`scripts/run-supabase-e2e-sb06.ps1` prueba el contrato real que consume
`ProfileRemoteGateway`: proyección de `community_profiles`, patch de
`display_name`, lectura/creación ordenada de `community_emergency_contacts` y
la normalización común (trim, `distinct`, máximo cinco).

No usa `SUPABASE_DB_URL`, service-role, SQL, DDL, RPC ni crea usuarios. Sólo
usa URL y clave publicable junto con la sesión de una cuenta aislada.

## Condición de seguridad

SB-06 no intenta restaurar un conjunto SOS ajeno: antes de mutar exige que el
perfil aislado no tenga contactos SOS. Así, cada fila creada por el lote se
puede borrar completamente por el mismo JWT. También obtiene el valor previo
de `display_name` y lo restaura, incluido `null`. Si falta cualquiera de estas
pruebas, aborta antes del primer patch o insert.

Si un fallo impide la restauración o el borrado, el informe indica
`rollback_pending`; no se ejecuta otro lote sobre esa cuenta hasta restaurar su
`display_name` y borrar todas sus filas SOS. El runner nunca afirma haber
limpiado datos si no pudo verificarlo.

Los contactos candidatos ya deben ser perfiles efímeros, visibles para esa
sesión y distintos del perfil de prueba. Se pueden pasar entre uno y cinco,
separados por comas; los IDs no se imprimen ni se guardan en el informe.

## Ejecución

```powershell
$env:QUATA_SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:QUATA_SUPABASE_PUBLISHABLE_KEY = '<publishable-key>'
$env:QUATA_E2E_PROFILE_COUNTRY_CODE = '<country-code>'
$env:QUATA_E2E_PROFILE_PHONE = '<isolated-e2e-phone>'
$env:QUATA_E2E_PROFILE_PASSWORD = '<isolated-e2e-password>'
$env:QUATA_E2E_PROFILE_SOS_CONTACT_IDS = '<one-to-five-isolated-profile-uuids>'
$env:QUATA_E2E_PROFILE_SOS_SCOPE = 'isolated_sb06_profile'
$env:QUATA_E2E_PROFILE_SOS_CLEANUP = 'restore_display_name_and_delete_empty_contact_set'
.\scripts\run-supabase-e2e-sb06.ps1 -AllowProfileMutation -AllowSosContactMutation
```

Los dos switches, los dos acknowledgements exactos y todas las variables son
obligatorios antes de cualquier acceso de red. El informe local no contiene
URL, clave, teléfono, contraseña, JWT ni IDs de perfil/contacto.
