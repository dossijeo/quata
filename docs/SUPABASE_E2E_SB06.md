# SB-06: Profile y SOS autenticados

`scripts/run-supabase-e2e-sb06.ps1` y `scripts/supabase-e2e-sb06.mjs` prueban
el contrato real que consume `ProfileRemoteGateway`: proyección de
`community_profiles`, patch de `display_name`, lectura/creación ordenada de
`community_emergency_contacts` y la normalización común (trim, `distinct`,
máximo cinco).

No usa `SUPABASE_DB_URL`, service-role, SQL, DDL, RPC ni crea usuarios. Sólo usa
URL y clave publicable junto con sesiones de usuario autenticadas. La URL y la
clave publicable pueden venir del entorno o de
`QuataPublicBackendConfig.kt`; el runner rechaza claves privilegiadas.

## Condición de seguridad

SB-06 tiene dos modos seguros:

- **Perfil aislado vacío:** antes de mutar exige que el perfil no tenga
  contactos SOS. Así, cada fila creada por el lote se puede borrar
  completamente por el mismo JWT.
- **Perfiles existentes aprobados:** cuando se define
  `QUATA_CHAT_GROUP_CREDENTIALS_FILE`, el runner lee de ese fichero privado los
  dos perfiles autorizados por el operador, inicia sesión con ambos, usa el
  segundo perfil como contacto candidato del primero y toma una instantánea
  exacta de `display_name` y de `community_emergency_contacts` antes de mutar.
  Al terminar, borra el estado temporal, restaura la instantánea original y
  verifica que el orden restaurado coincide.

En ambos modos obtiene el valor previo de `display_name` y lo restaura, incluido
`null`. Si falta cualquiera de estas pruebas, aborta antes del primer patch o
insert.

Si un fallo impide la restauración o el borrado, el informe indica
`rollback_pending`; no se ejecuta otro lote sobre esa cuenta hasta restaurar su
`display_name` y su conjunto SOS esperado. El runner nunca afirma haber limpiado
datos si no pudo verificarlo.

En modo aislado, los contactos candidatos ya deben ser perfiles efímeros,
visibles para esa sesión y distintos del perfil de prueba. Se pueden pasar entre
uno y cinco, separados por comas; los IDs no se imprimen ni se guardan en el
informe. En modo de perfiles aprobados, los perfiles salen exclusivamente del
fichero privado indicado y el informe sólo guarda una huella SHA-256 corta del
perfil actor.

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

Modo de perfiles aprobados, sin exponer credenciales en comandos ni artefactos:

```powershell
$env:QUATA_CHAT_GROUP_CREDENTIALS_FILE = 'C:\Users\PC\QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt'
node scripts\supabase-e2e-sb06.mjs --allow-profile-mutation --allow-sos-contact-mutation --out build-reports\supabase\sb-06-profile-sos-existing-users.json
Remove-Item Env:\QUATA_CHAT_GROUP_CREDENTIALS_FILE -ErrorAction SilentlyContinue
```

Este modo conserva la misma política de no usar service-role, DB URL, SQL, DDL,
RPC, cambios de schema ni creación de usuarios. Si el conjunto SOS original no
estaba vacío, se restaura exactamente en el cleanup comprobado.
