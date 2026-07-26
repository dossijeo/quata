# Candidata de corrección RLS-002

Esta rama prepara, pero no despliega,
`supabase/migrations/20260726171002_official_post_likes_actor_guard.sql`.

## Causa confirmada

El catálogo desplegado muestra `official_post_likes` con RLS desactivado, cero
políticas y el trigger guard instalado como función `SECURITY DEFINER` propiedad
de `postgres`. Dentro de ese contexto, `quata_current_role_is_service()` observa
`current_user = postgres`; por ello toma el bypass de mantenimiento también
cuando la petición original llega con un JWT `authenticated`.

## Diseño

- El trigger pasa a `SECURITY INVOKER`, por lo que queda como defensa fail-loud
  y ve el rol real de PostgREST.
- RLS se convierte en la frontera principal.
- INSERT exige que `profile_id` coincida con
  `quata_current_profile_id()`, el mapeo canónico de `auth.uid()`.
- DELETE usa una función de política que permite propietario o administrador y
  eleva `42501` para un actor ajeno. El trigger conserva la misma comprobación
  como segunda defensa.
- SELECT sigue permitido para `anon` y `authenticated`; no cambia la lectura de
  Feed Official, Realtime ni Android.
- Los grants autenticados de INSERT/DELETE permanecen; RLS los restringe por fila.

No se usa `FORCE ROW LEVEL SECURITY`: mantenimiento con `postgres`/service-role
y cascadas de ciclo de vida conservan su comportamiento operativo.

## Compatibilidad y riesgos

El cliente Android actual inserta con el `profileId` autenticado y borra por ID;
ambos recorridos siguen permitidos para el propietario. Un cliente antiguo que
envíe un `profileId` distinto queda correctamente rechazado.

El cambio de SELECT es intencionadamente nulo: los likes continúan públicos.
Activar RLS sí cambia Realtime para mutaciones ajenas, que dejarán de entregarse
como operaciones autorizables; las lecturas permanecen visibles por la política
pública.

Las funciones guard de posts y comentarios comparten el patrón histórico
`SECURITY DEFINER` más comprobación de `current_user`. Esta migración no amplía
su alcance: deben auditarse por separado antes de habilitar esas mutaciones.

## Secuencia de validación

1. Ejecutar `node scripts/official-likes-rls-migration-contract.mjs`.
2. Aplicar la migración sólo en staging.
3. Provisionar A, B y un post Official aislado con purga autorizada.
4. Ejecutar SB-09 completo.
5. Purgar cada cuenta en una conexión/transacción separada; después eliminar Auth.
6. Verificar ausencia de perfiles, Auth, post y likes.
7. Ejecutar el smoke Android de like/unlike y lectura anónima antes de despliegue.

## Rollback de emergencia

El rollback versionado es
`supabase/rollbacks/20260726171002_official_post_likes_actor_guard.rollback.sql`.
Reabre RLS-002 y sólo debe usarse si la compatibilidad de producción lo exige.
No borra ni modifica filas. Restaura exactamente el catálogo capturado antes de
la migración: RLS desactivado, cero políticas en la tabla, trigger
`SECURITY DEFINER`, helper de DELETE ausente y grants `anon`/`authenticated`
originales.

Es un rollback *fail-closed*: exige el fingerprint exacto del estado de esta
release: definición y ACL de guard/helper, dueño de tabla/funciones, binding del
trigger, RLS sin `FORCE`, ACL de tabla y cada nombre/rol/`USING`/`WITH CHECK` de
las tres políticas. Si detecta incluso una política con el mismo nombre pero
otro cuerpo, aborta la transacción sin tocar catálogo ni datos; se debe tomar un
backup nuevo y preparar una reversión específica.

Para ensayar de forma aislada PostgreSQL **y** PostgREST:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-official-likes-rls-migration.ps1
```

El harness fija `postgres:17-alpine`; las huellas de catálogo del rollback se
han calculado y validado contra PostgreSQL 17, versión requerida antes de un
eventual GO de producción.

El ensayo crea una fila previa y verifica esta secuencia completa:

1. baseline histórico: PostgREST acepta la suplantación conocida;
2. aplicar: RLS bloquea INSERT y DELETE ajenos con `42501`, mientras SELECT
   anónimo y likes propios funcionan;
3. rollback: el catálogo vuelve al baseline y la fila previa sigue intacta;
4. reapply: se repiten los ataques bloqueados a través de PostgREST.

El mismo ensayo altera intencionadamente el `WITH CHECK` de una política con el
mismo nombre, intenta el rollback y comprueba que falla atómicamente: conserva
la fila previa y el fingerprint del catálogo drifted sin cambios. Después
reaplica la migración y continúa el rollback/reapply positivo.

En producción el operador debe tomar primero el snapshot de catálogo/datos del
runbook de release y ejecutar los smokes de Feed anónimo y Android. El SQL no es
una migración automática de Supabase: se ejecuta manualmente sólo como respuesta
de emergencia documentada.

La forma abreviada del efecto catalogado es:

```sql
begin;
drop policy if exists official_post_likes_authenticated_delete_own_or_admin
    on public.official_post_likes;
drop policy if exists official_post_likes_authenticated_insert_own
    on public.official_post_likes;
drop policy if exists official_post_likes_public_read
    on public.official_post_likes;
alter table public.official_post_likes disable row level security;
alter function public.quata_guard_official_post_likes() security definer;
drop function if exists public.quata_official_like_delete_allowed(uuid);
commit;
```

Después del rollback, mantener las mutaciones Web deshabilitadas y registrar de
nuevo RLS-002 como exposición activa.
