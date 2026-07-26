# RLS-001 — cierre de DELETE en `community_comments`

Estado: **preparado y probado localmente; no desplegado**.

## Evidencia de catálogo desplegado

El 26 de julio de 2026 se inspeccionó exclusivamente metadata dentro de una
transacción `READ ONLY`, usando TLS con el certificado del pooler. No se
consultaron filas de perfiles, comentarios o usuarios y no se ejecutó DDL/DML.

- RLS está activo en `public.community_comments`, sin `FORCE ROW LEVEL SECURITY`.
- La política desplegada `public delete comments` es `PERMISSIVE`, aplica a
  `public` y tiene `USING (true)`.
- `anon` y `authenticated` tienen `DELETE`; la tabla también conserva políticas
  públicas `SELECT`, `INSERT` y `UPDATE`.
- `profile_id` referencia `community_profiles(id)`.
- `community_profiles.auth_user_id` referencia `auth.users(id)` y posee índice
  único parcial.
- `quata_chat_auth_profile_id()` es `STABLE SECURITY DEFINER`, tiene
  `search_path = public, auth`, resuelve el perfil enlazado a `auth.uid()` y
  exige `account_status = 'active'`.
- `quata_current_profile_is_admin()` comprueba el `is_admin` persistido. La
  nueva política exige además que el resolver de perfil activo no sea nulo.

## Cambio propuesto

La migración
`20260726171001_community_comments_delete_rls.sql` elimina únicamente la
política DELETE permisiva, revoca DELETE a `anon`/`public` y lo conserva para
`authenticated` bajo este predicado:

1. existe un perfil canónico activo para el JWT;
2. el perfil es propietario de la fila, o
3. el perfil activo tiene `is_admin = true`.

No cambia el endpoint ni el filtro Android: `DELETE ...?id=eq.<comment-id>`
continúa funcionando, pero PostgREST devuelve cero filas cuando RLS no autoriza
la fila. Las políticas de lectura permanecen intactas para anónimos y
autenticados.

## Pruebas y limpieza

`supabase/tests/rls001_community_comments_delete.sql` ejecuta en una sola
transacción:

- tres identidades `auth.users` efímeras enlazadas por `auth_user_id`;
- perfiles propietario, outsider y administrador explícito;
- wall, post y dos comentarios efímeros;
- lectura anónima/autenticada;
- DELETE outsider = 0 y persistencia de la fila;
- DELETE propietario = 1;
- DELETE administrador activo = 1;
- ensayo del rollback;
- eliminación explícita y verificación de ausencia de todos los fixtures;
- `ROLLBACK` final, que también retira el DDL ensayado.

El runner exige una base aislada y recibe la conexión solo por entorno. No
acepta URL, contraseña ni tokens como argumentos y no escribe secretos en
informes.

## Riesgos y despliegue

- `INSERT` y `UPDATE` siguen teniendo las políticas públicas preexistentes.
  Son hallazgos de autorización separados; esta migración no los endurece para
  evitar ampliar RLS-001 o romper clientes publicados sin evidencia específica.
- La excepción administrativa depende del rol persistido actual. Debe
  conservarse la protección ya existente sobre cambios de `is_admin`.
- El rollback restaura deliberadamente la política vulnerable y solo debe
  utilizarse si la migración causa una incompatibilidad operativa confirmada.
- Antes de producción: aplicar en staging, ejecutar SB-07/PostgREST con cuentas
  nuevas, confirmar respuesta `401/403` o representación vacía del outsider,
  comprobar propietario/admin y verificar la purga externa.
