# SEC-MUTATIONS-001 — auditoría de superficies mutantes

**Estado:** auditoría estática, preparada para una serie de releases; no aplicada.
**Alcance revisado:** Android, Web/Wasm, migraciones y funciones versionadas en este
checkout. No se abrió una conexión con capacidad mutante, no se ejecutó DDL/RLS/grants,
ni se desplegó ninguna función/UI.

## Modelo de contrato

`actor` significa el perfil canónico activo derivado en servidor de la sesión autenticada;
no el `profile_id` enviado por el cliente. `outsider` es otro perfil autenticado. `anon`
no tiene sesión. `admin` es un actor con la marca administrativa activa comprobada en
servidor; no es una elevación por un campo enviado por el cliente. Toda mutación debe
fallar cerrada si el resolver de perfil no devuelve exactamente un perfil activo.

| Dominio | actor | outsider | anon | admin | Evidencia versionada / hallazgo |
|---|---|---|---|---|---|
| Official (`official_posts`) | Crear/editar/borrado lógico solo de su cuenta oficial; ninguna modificación de `profile_id`, roles o traducciones ajenas. | Denegado. | Solo lectura de publicaciones publicadas, idioma solicitado o fallback `es`. | Puede moderar/publicar como cuenta oficial conforme al guard. | Guard trigger en `20260702_0003`; las políticas de `20260709_0002` usan `with check (true)`, por lo que el trigger es la barrera decisiva. Revisar su propiedad/ACL antes de cada cambio. |
| Official likes (`official_post_likes`) | Insertar/borrar únicamente su arista `(post, actor)`. | Denegado. | Lectura pública; INSERT/DELETE denegados. | Borrar para moderación solo con verificación administrativa activa. | `20260726171002` endurece RLS, grants y el guard; es una release pendiente según la allowlist. |
| Official comments (`official_post_comments`) | Crear su comentario; UPDATE **no es seguro actualmente**. El owner puede cambiar `new.profile_id` porque el guard solo comprueba la identidad en INSERT y, para UPDATE/DELETE, compara el actor con `old.profile_id`. | No puede editar una fila inicialmente ajena, pero puede convertirse en owner de una fila que el owner original le reasigne; dominio no seguro. | Lectura pública; mutaciones denegadas. | Moderación conforme al guard, sin permitir cambios de autor salvo contrato explícito. | **Bloqueador real:** `quata_guard_official_post_comments` de `20260702_0003` debe exigir `new.profile_id = old.profile_id` en UPDATE (o una protección equivalente de columna/RLS) antes de declarar seguro el dominio. También falta una auditoría de catálogo posterior equivalente a RLS-002. |
| Community comments (`community_comments`) | Insertar con `profile_id = actor`; borrar solo el propio. No UPDATE (cliente distribuido no lo usa). | Borrado/insert con identidad ajena denegados. | Lectura pública; sin INSERT/UPDATE/DELETE. | Puede borrar para moderación si está activo. | `20260726171005` es el forward vigente de RLS-001. Prueba SQL y contrato PostgREST existen. |
| Community reactions (`community_post_likes`) | Crear/borrar solo su `(post, actor)`. | Denegado. | Lectura, si el producto la mantiene pública; mutaciones denegadas. | Borrado de moderación solo si se decide y se prueba explícitamente. | El Android aún hace GET + POST/DELETE con `profile_id` cliente. No hay migración/contrato RLS versionado de esta tabla: **bloqueador**. |
| Follows de perfiles (`community_profile_follows`) | Crear/borrar solo aristas con `follower_profile_id = actor`, sin auto-follow. | No puede suplantar ni borrar aristas ajenas. | Sin mutación. | Sin bypass salvo RPC administrativo separado y auditado. | Hay plantillas y pruebas locales, pero no una migración allowlisted aplicada en `supabase/migrations`: **bloqueador**. |
| Follows de muros (`community_wall_follows`) | Crear/borrar solo `profile_id = actor`; debe ser miembro/autorizado para el muro según producto. | Denegado. | Sin mutación. | Solo política administrativa explícita. | Cliente Android tiene toggle; no hay contrato RLS versionado localizado: **bloqueador**. |
| Composer + Storage | Publicar solo con `profile_id/author_id = actor`, muro de membresía del actor y objeto Storage bajo prefijo propiedad del actor. | No puede escribir post, objeto ni sobrescribir ruta ajena. | Sin publicación/carga. | No implica acceso directo a objetos; moderación por ruta/RPC separada. | Web evita el fallback de muro y usa `x-upsert:false`; Android usa `upsert=true` en prefijos de perfil. Las políticas de `storage.objects` no están versionadas: **bloqueador crítico**. |
| Notificaciones | Registro, baja y logout solo para el usuario/sesión autenticados. No permite enumerar tokens/subscriptions. | Denegado. | Denegado. | Operación de entrega interna únicamente; no lectura de secretos. | Push nativo usa RPC autenticada; web usa Edge Function con JWT + token de sesión. Tablas web revocan acceso directo a clientes. |

## Clientes dependientes y compatibilidad

| Superficie | Clientes que mutan | Forma que no se puede romper |
|---|---|---|
| Official | Android `SupabaseCommunityApi`; Web declara mutaciones no implementadas. | POST/PATCH a `official_posts`, toggle GET+POST/DELETE de likes y POST de comentario; los guards deben aceptar el shape existente y derivar actor en servidor. |
| Comentarios/reacciones comunitarios | Android `SupabaseCommunityApi`; Web Feed es de lectura. | Comentarios: POST y DELETE por `id`; likes: GET de arista y POST/DELETE. No introducir UPDATE de comentarios como requisito. |
| Follows | Android `SupabaseCommunityApi`. | Toggle optimista basado en GET + POST/DELETE por `id`; la política debe filtrar la fila, no confiar en filtros del cliente. |
| Composer/Storage | Android `SupabaseCommunityApi`; Web `WebPostComposerRepository`. | Web crea post tras resolver membresía y carga objeto con POST a `community-posts`; no requiere UI nueva. Android conserva rutas actuales de `community-posts`, avatars y `chat-attachments`. |
| Notificaciones | Android RPC de tokens; Web `BrowserWebPushRegistrationService` y `quata-web-push`. | Mantener RPC `quata_register_push_token`/baja y acciones Edge `subscribe`, `unsubscribe`, `logout`; no exponer tablas de tokens/subscriptions. |

## Plan de releases atómicos

Cada fila es una sola transacción versionada, con preflight de catálogo, pruebas locales
mutantes contra Supabase local, pruebas remotas exclusivamente de lectura y rollback
versionado. No agrupar dominios: un rollback no debe ampliar permisos de otro.

1. **S0 — congelar baseline.** Generar fingerprint de tablas, políticas, ACL, owner,
   funciones y triggers de las ocho superficies; inventariar políticas `storage.objects`
   por bucket. Bloquea S1--S5 hasta que el baseline sea revisado y anclado a commit.
2. **S1 — community comments.** Confirmar únicamente `20260726171005` y sus gates
   existentes; no reaplicarla. Validar anon/actor/outsider/admin y compatibilidad de DELETE
   por id.
3. **S2 — official likes y comentarios.** Aplicar el lote RLS-002 ya versionado solo
   después de los anchors prescritos. Preparar una migración independiente posterior para
   `official_post_comments` (inmutabilidad de `profile_id` en UPDATE, policy + ACL +
   trigger fingerprint) antes de considerarla segura o tocar otros permisos.
4. **S3 — reactions y follows.** Una migración por tabla: primero
   `community_post_likes`, luego `community_profile_follows`, por último
   `community_wall_follows`. Las migraciones deben contener precondición, RLS, grants
   mínimos, trigger/constraint de identidad, test actor/outsider/anon/admin y rollback
   que falle ante drift. No usar RPC toggle hasta que los clientes hayan migrado.
5. **S4 — Composer + Storage.** Separar post rows de `storage.objects`: asegurar primero
   que los posts derivan actor/membresía, después cada bucket con prefijo exacto y sin
   upsert ajeno. El upload sin objeto publicable debe limpiar o dejar una ruta inaccesible;
   no exponer un bucket para resolver el rollback.
6. **S5 — notifications.** Confirmar ACL cero para tablas internas; probar RPC/Edge con
   actor, outsider, anon y sesión web revocada. Rotar/limpiar únicamente mediante las
   rutas existentes autenticadas. La entrega de push no queda acoplada a S3/S4.

**Criterio de promoción:** cada release solo avanza con catálogo exacto, prueba local de
éxito y rechazo para los cuatro roles, smoke de clientes dependientes y evidencia firmada.
Un 401/403 de la API cuenta como rechazo; un `200` con cero filas no sustituye una prueba
de escritura negada. Tras cada release, ejecutar solo checks remotos no mutantes hasta que
se autorice una ventana de prueba distinta.

## Bloqueos y decisiones requeridas

1. Falta el baseline versionado de `community_post_likes`, ambos follow tables y
   `storage.objects`; no es seguro inferir las políticas activas desde clientes.
2. Falta un contrato de moderación explícito para likes comunitarios y follows. No asumir
   bypass admin porque cambia el producto y la superficie de abuso.
3. `official_post_comments` conserva un guard histórico vulnerable a reasignación por el
   owner durante UPDATE: autoriza usando `old.profile_id`, pero no impide modificar
   `new.profile_id`. Se exige inmutabilidad `new.profile_id = old.profile_id` o una
   protección equivalente, además de una prueba de catálogo post-release anclada como la
   de likes oficiales.
4. Android usa `upsert=true` para varias cargas; S4 debe decidir si se conserva con una
   política de propietario estricta o se cambia el cliente en una release compatible.

## Trazabilidad principal

- `supabase/migrations/20260726171001_community_comments_delete_rls.sql` y
  `20260726171005_community_comments_reapply_rls.sql`
- `supabase/migrations/20260726171002_official_post_likes_actor_guard.sql`
- `supabase/migrations/20260702_0003_official_accounts.sql`
- `supabase/migrations/20260723_0001_multidevice_fcm_and_web_push.sql`
- `app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt`
- `web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerRepository.kt` y
  `BrowserWebPushRegistrationService.kt`
