# RLS-001 — cierre de mutaciones inseguras en `community_comments`

Estado: **preparado y probado localmente; no desplegado**.

## Evidencia del catálogo desplegado

El 26 de julio de 2026 se inspeccionó exclusivamente metadata dentro de una
transacción `READ ONLY`, usando TLS con el certificado del pooler. No se
consultaron filas de perfiles, comentarios o usuarios y no se ejecutó DDL/DML.

- RLS está activo en `public.community_comments`, sin `FORCE ROW LEVEL SECURITY`.
- Las políticas desplegadas `public delete comments`, `public insert comments`
  y `public update comments` son permisivas, aplican a `public` y aceptan
  cualquier fila (`true`).
- `anon` y `authenticated` conservan `DELETE`, `INSERT` y `UPDATE`, además de
  privilegios de tabla innecesarios (`TRUNCATE`, `REFERENCES`, `TRIGGER`).
- `profile_id` referencia `community_profiles(id)`.
- `community_profiles.auth_user_id` referencia `auth.users(id)` y posee índice
  único parcial.
- `quata_chat_auth_profile_id()` es `STABLE SECURITY DEFINER`, tiene
  `search_path = public, auth`, resuelve el perfil enlazado a `auth.uid()` y
  exige `account_status = 'active'`.
- `quata_current_profile_is_admin()` comprueba el `is_admin` persistido. La
  nueva política exige además que el resolver de perfil activo no sea nulo.

La inspección de catálogo confirma el contrato SQL. No se encontró una clave
publicable local con la que repetir una consulta PostgREST directa; la
reproducción SB-07 ya documenta la explotación desplegada por esa superficie.

## Cambio propuesto

La migración `20260726171001_community_comments_delete_rls.sql` se ejecuta en
una transacción explícita y:

1. elimina las políticas públicas de `DELETE`, `INSERT` y `UPDATE`;
2. revoca las mutaciones a `anon` y los privilegios de tabla innecesarios;
3. conserva `SELECT` público sin modificar su política;
4. permite a `authenticated` insertar únicamente con el `profile_id` de su
   perfil canónico activo;
5. permite borrar únicamente al propietario canónico activo o a un
   administrador explícito cuyo perfil también esté activo;
6. no concede `UPDATE`, dejando inmutables `profile_id`, `post_id` y `body`
   después de la inserción.

Cerrar sólo `DELETE` era insuficiente: la política pública de `UPDATE` permitía
a un outsider reasignarse `profile_id` y encadenar el borrado, mientras que la
de `INSERT` permitía suplantar la autoría de otro perfil. No hay llamadas de
actualización de comentarios en los clientes publicados examinados.

El contrato Android se conserva: `addComment(postId, session.userId, body)`
envía el perfil propio y `deleteComment(commentId)` filtra sólo por `id`. RLS
filtra la fila coincidente y devuelve cero filas al actor no autorizado.

## Pruebas y limpieza

`supabase/tests/rls001_community_comments_delete.sql` sólo puede ejecutarse en
una base aislada y valida:

- lectura anónima y autenticada;
- denegación de `INSERT`, `UPDATE` y `DELETE` anónimos;
- denegación de suplantación de autoría en `INSERT`;
- intento outsider `UPDATE → DELETE`, con persistencia e identidad inmutables;
- inserción propia y borrado Android por `id`;
- borrado del propietario;
- administrador inactivo = cero filas y administrador activo = una fila;
- purga explícita y comprometida de usuarios, perfiles, wall, post y
  comentarios efímeros, seguida de una comprobación externa de ausencia;
- ensayo del rollback, comprobación del contrato observado y reaplicación
  inmediata de la migración segura.

El runner recibe la conexión sólo por entorno. No acepta URL, contraseña ni
tokens como argumentos y no escribe secretos en informes.

## Riesgos y despliegue

- El cambio contiene dos vías que hacían el cierre de `DELETE` eludible. Puede
  afectar a clientes desconocidos que inserten comentarios anónimamente,
  suplanten `profile_id` o actualicen filas; esos comportamientos no son un
  contrato seguro y no aparecen en el código publicado revisado.
- La excepción administrativa depende del rol persistido actual. Debe
  conservarse la protección ya existente sobre cambios de `is_admin`.
- El rollback restaura deliberadamente las políticas y grants vulnerables,
  incluidos `INSERT` y `UPDATE`; sólo debe usarse ante una incompatibilidad
  operativa confirmada.
- Antes de producción: aplicar en staging, ejecutar SB-07/PostgREST con cuentas
  nuevas, comprobar outsider/propietario/admin, validar Android/Web publicados
  y verificar la purga externa.
