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

El rollback reabre RLS-002 y sólo debe usarse si la compatibilidad de producción
lo exige:

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
