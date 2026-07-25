# SB-07: Communities, comentarios y reacciones

`scripts/run-supabase-e2e-sb07.ps1` valida los contratos PostgREST consumidos
por `SupabaseCommunityApi`: carga de un post perteneciente a una comunidad
aislada, membresía visible del actor, alta/listado/borrado de
`community_comments`, y alta/listado/borrado de una reacción emoji en
`community_post_reactions`.

El segundo usuario intenta borrar el comentario del actor. Se acepta una
denegación HTTP de autenticación/autorización (401/403) o la respuesta
PostgREST vacía causada por un predicado RLS,
pero sólo si el actor sigue leyendo su fila. Una fila devuelta al segundo JWT o
una fila ausente para el actor se informa como `rls_violation`, nunca como
éxito.

No hay una ruta persistente de ranking consumida por `SupabaseCommunityApi`.
El ranking compartido es una proyección de UI, de modo que SB-07 valida la ruta
emoji existente e informa `ranking.not_exercised`; no inventa una API ni
declara una validación inexistente.

## Seguridad y limpieza

El runner no acepta URL, claves, teléfonos, contraseñas ni IDs como argumentos.
Usa exclusivamente una clave publicable y JWT obtenidos del bridge de
autenticación. Rechaza `service_role`, `sb_secret_*`, JWT privilegiados, SQL,
DDL, migraciones, RPC y conexiones directas a base de datos.

Aunque `return=representation` confirma el borrado visible de las filas
creadas, no demuestra que no exista auditoría o borrado lógico. Antes de toda
red exige una purga externa autorizada de las dos cuentas aisladas. Por eso un
recorrido correcto termina en `passed_with_external_hard_cleanup_pending`; sólo
un operador puede confirmar la purga. Un rollback no confirmado queda marcado
como `rollback_pending`.

## Ejecución

Los usuarios deben ser exclusivos de SB-07. El `POST_ID` debe pertenecer al
`WALL_ID` y el actor debe tener una membresía visible. El runner no crea posts
ni comunidades porque no existe un contrato público revisado para su ciclo de
vida.

```powershell
$env:QUATA_SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:QUATA_SUPABASE_PUBLISHABLE_KEY = '<publishable-key>'
$env:QUATA_E2E_COMMUNITIES_ACTOR_COUNTRY_CODE = '<country-code>'
$env:QUATA_E2E_COMMUNITIES_ACTOR_PHONE = '<isolated-actor-phone>'
$env:QUATA_E2E_COMMUNITIES_ACTOR_PASSWORD = '<isolated-actor-password>'
$env:QUATA_E2E_COMMUNITIES_OUTSIDER_COUNTRY_CODE = '<country-code>'
$env:QUATA_E2E_COMMUNITIES_OUTSIDER_PHONE = '<isolated-outsider-phone>'
$env:QUATA_E2E_COMMUNITIES_OUTSIDER_PASSWORD = '<isolated-outsider-password>'
$env:QUATA_E2E_COMMUNITIES_WALL_ID = '<isolated-wall-uuid>'
$env:QUATA_E2E_COMMUNITIES_POST_ID = '<isolated-post-uuid>'
$env:QUATA_E2E_COMMUNITIES_ACTOR_E2E_SCOPE = 'isolated_sb07_community_actor'
$env:QUATA_E2E_COMMUNITIES_OUTSIDER_E2E_SCOPE = 'isolated_sb07_community_outsider'
$env:QUATA_E2E_COMMUNITIES_EXTERNAL_HARD_CLEANUP = 'approved_isolated_communities_purge'
.\scripts\run-supabase-e2e-sb07.ps1 -AllowExistingTestData -AllowCommunityMutation
```

El informe local no contiene secretos ni identificadores remotos. Tras la
ejecución, un operador debe anotar fecha, commit, resultado RLS y confirmación
de purga externa en el tablero antes de cerrar realmente el lote.
