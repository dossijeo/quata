# Hallazgos RLS pendientes

## Política temporal de compatibilidad durante la migración — 2026-07-31

La ausencia de RLS o la existencia de políticas permisivas **no bloquea la paridad
funcional de los nuevos clientes Wasm e iOS**. Mientras Android publicado y la Web
antigua sigan dependiendo del backend actual, los clientes migrados pueden usar los
mismos endpoints, tablas y mutaciones que Android para completar los flujos reales.
No autoriza introducir claves `service-role` en clientes, ampliar grants, crear bypasses
adicionales ni exponer capacidades que Android publicado no tenga; los rechazos reales
del backend tampoco pueden convertirse en éxitos locales u optimistas.

Esta política sustituye las instrucciones históricas de contención cliente
`fail-closed` que aparecen más abajo; esas se conservan únicamente como evidencia
del estado auditado. La autorización es estrictamente de compatibilidad: no autoriza a modificar,
endurecer o eliminar políticas RLS, ni a romper clientes publicados. Cada exposición
permanece documentada en este fichero, sin rebajar su severidad, y su corrección se
aplaza hasta después de la publicación de la nueva Web. Los planes y artefactos de
endurecimiento pueden mantenerse preparados, pero no son prerrequisito para implementar
o validar una pantalla; las ramas temporales siguen sujetas a la política de limpieza.

## Nota de corte 2026-07-29

Los PRs 93, 94, 96 y 97, las capturas de baseline Wasm y las validaciones Web/Android/iOS del corte `c87e82af` no modificaron Supabase: no hubo cambios de RLS, DDL, funciones, grants ni datos. Esta nota no cierra, rebaja ni autoriza el rollout de ninguno de los hallazgos siguientes. La Web publicada, Android publicado y el Feed anónimo continúan siendo restricciones de compatibilidad para cualquier corrección futura.

## community_profiles — evidencia de registro Web

La auditoría read-only confirmó grants y policies amplias para `anon`, incluidos
INSERT/UPDATE. Este contrato no los usa ni modifica: `quata-register` opera
con service-role. El endurecimiento corresponde a la migración coordinada
`20260726171003`; registro debe aplicarse después como `20260726171004`.

Este registro reúne hallazgos confirmados por pruebas E2E. No es una migración
y no autoriza cambios de esquema, funciones, políticas ni datos de producción.

Reconciliación DOC-001 (`ea0322c1`): RLS-001 está desplegada y hardened mediante
el forward `20260726171005`; queda pendiente acreditar el SB-07 remoto mutante
completo antes de retirar la contención de producto. RLS-002/SB-09, RLS-003,
RLS-004 y RLS-005 continúan abiertas. Communities y Official mantienen sus
mutaciones afectadas `fail-closed`.

## RLS-001 — Un outsider puede borrar un comentario ajeno

- **Detectado:** 2026-07-26, SB-07 de Communities.
- **Severidad:** alta.
- **Estado remoto:** forward `20260726171005` desplegado y catálogo hardened; falta
  ejecutar el SB-07 remoto mutante completo con fixtures y purga autorizados.
- **Estado de producto:** contenido en los clientes KMP hasta acreditar ese gate.
- **Superficie:** `public.community_comments`, operación `DELETE` mediante la clave
  publicable y un JWT autenticado.
- **Reproducción segura:** crear dos perfiles E2E aislados, un wall y post del actor; crear un
  comentario con el JWT del actor; intentar `DELETE` sobre el ID del comentario con el JWT del
  outsider. La operación eliminó la fila, en vez de devolver una representación vacía o una
  denegación de autorización.
- **Evidencia:** `scripts/run-supabase-e2e-sb07.ps1` finalizó con `rls_violation`. No se
  registraron IDs, JWT, teléfonos, URL de conexión ni secretos. El lote purgó sus perfiles, wall
  y post efímeros y verificó la ausencia de residuos.
- **Impacto:** cualquier usuario autenticado que alcance el endpoint puede eliminar comentarios
  de otro perfil, con independencia de la UI.

### Despliegue y contención de clientes (reconciliado 2026-07-27)

El forward `20260726171005_community_comments_reapply_rls` está desplegado: conserva 171001 en
el ledger y deja hardened el catálogo de comentarios. No se debe reaplicar ni reparar ese
timestamp. Esto acredita el despliegue de RLS-001, pero no el recorrido remoto mutante completo
de SB-07. Mientras ese gate permanezca pendiente, `CommunityMutationSafety` en
`feature:neighborhoods` mantiene fallos cerrados para crear
o borrar comentarios, seguir usuarios, reportar posts y cambiar roles. Los adaptadores Web e iOS
usan esa misma barrera para las mutaciones que exponen; las capacidades Web conservan
`Communities/Mutate` deshabilitado. La creación de conversaciones no se incluye en esta barrera:
pertenece al contrato Chat, que cuenta con la evidencia independiente SB-04.

La regresión común `CommunityMutationSafetyTest` verifica que ninguna de esas operaciones pueda
activarse accidentalmente mientras el identificador de evidencia siga siendo `RLS-001`. Esto es
contención de producto adicional al endurecimiento del servidor: clientes antiguos,
llamadas directas o futuras integraciones no habilitan el gate pendiente hasta que SB-07 remoto
complete su matriz mutante y purga verificable.

### Estado de la corrección

La corrección preparada como `20260726171001_community_comments_delete_rls.sql`
restringe DELETE a propietario canónico activo/administrador explícito, cierra
la evasión `UPDATE → DELETE` y limita INSERT al perfil canónico activo. El
forward desplegado `20260726171005_community_comments_reapply_rls.sql` la
reaplicó sobre la base de release vigente. Conserva SELECT público y los
contratos Android de alta y borrado por `id`; los artefactos incluyen rollback
y regresión SQL/E2E aislada. La evidencia y riesgos están en
`docs/RLS001_COMMUNITY_COMMENTS_DELETE_PLAN.md`.

### Criterio de cierre

1. SB-07 se repite con dos perfiles nuevos y aislados.
2. El outsider recibe una denegación HTTP o una representación vacía.
3. El actor sigue viendo su comentario y puede borrar únicamente el suyo.
4. Se purgan y verifican todos los datos E2E.
5. Se registra la evidencia remota sin IDs, tokens, teléfonos ni secretos, vinculada al forward
   `20260726171005` ya desplegado.
6. Sólo tras los cinco puntos anteriores se retira la contención por operación y se añaden
   pruebas de cliente específicas para el flujo habilitado.

## RLS-002 — Un actor puede suplantar el perfil al crear un like Official

- **Detectado:** 2026-07-26, SB-09 de Official con dos cuentas y un post aislados.
- **Superficie:** `public.official_post_likes`.
- **Evidencia:** la inspección de catálogo, sin DDL ni DML, confirmó RLS desactivado,
  cero políticas y el trigger `quata_guard_official_post_likes_trg` instalado. Sin
  embargo, SB-09 creó el like propio de A y después intentó crear un like con el JWT
  de A y `profile_id` de B: la operación no devolvió `42501`. El runner hizo rollback
  del like propio; los perfiles/Auth aislados y el post temporal se purgaron y se
  comprobó su ausencia.
- **Impacto:** no se puede confiar en que el trigger vincule el actor Web al
  `profile_id` enviado. Exponer like/unlike permitiría suplantación de identidad.
- **Candidata no desplegada:** `20260726171002_official_post_likes_actor_guard.sql`
  convierte RLS en la frontera principal, conserva SELECT anónimo y deja el
  trigger como defensa `SECURITY INVOKER`. Requiere SB-09 verde en staging y
  smoke Android antes de cambiar este estado.

### Límite y seguimiento

No es una autorización para endurecer RLS existente. Mantener todas las mutaciones
Official Web deshabilitadas. La corrección debe coordinarse con la web publicada y
demostrar en SB-09: insert propio, rechazo `42501` de suplantación, rechazo `42501`
del borrado ajeno, borrado propio y purga verificable de fixtures antes de habilitar
la UI.

## RLS-003 — Escalada de identidad, rol y estado en `community_profiles`

- **Detectado:** 2026-07-26 mediante inspección remota de catálogo de solo
  lectura.
- **Severidad:** crítica.
- **Evidencia:** RLS está activo, pero `public update profiles` usa
  `USING (true) WITH CHECK (true)` y `anon`/`authenticated` tienen UPDATE sobre
  todas las columnas. El guard de roles es `SECURITY DEFINER`, propiedad de
  `postgres`, y su helper trata a `current_user = postgres` como servicio, por
  lo que omite siempre la comprobación de admin.
- **Impacto:** una request pública puede cambiar `auth_user_id`, `is_admin`,
  `is_official`, `account_status` o la identidad de cualquier perfil.
- **Corrección preparada, no desplegada:**
  `20260726171003_community_profiles_actor_guard.sql`, con prueba aislada y
  rollback revisado. Véase `docs/RLS_003_COMMUNITY_PROFILES_FIX.md`.
- **Condición de rollout:** publicar junto al nuevo registro/recuperación
  privilegiado y repetir staging/E2E; no aplicar aisladamente mientras los
  clientes publicados dependan del UPDATE anónimo.
- **Preflight histórico:** la comprobación remota de solo lectura pasó las
  colisiones de mapping/identidad telefónica y falló cerrada al encontrar 74
  perfiles con contadores distintos de las aristas reales de follow. No se
  imprimieron IDs ni se corrigieron datos; requiere reconciliación separada
  antes de cualquier rollout. El análisis agregado confirmó 112 perfiles con
  ambos caches a cero frente a 107 aristas autoritativas; véase
  `docs/PROFILE_FOLLOW_COUNTER_RECONCILIATION_PLAN.md`.

## RLS-004 — Credenciales y recuperación visibles por SELECT público

- **Detectado:** 2026-07-26 mediante catálogo y contratos Android actuales.
- **Severidad:** crítica.
- **Estado:** abierto; solo documentado.
- **Evidencia:** `public read profiles` usa `USING (true)` y el grant SELECT de
  tabla incluye `pass_hash`, `pass_plain`, `secret_answer`, teléfono e
  identificadores Auth. Android legacy consulta esos campos directamente para
  login y recuperación. Un GET PostgREST anónimo real y de solo lectura
  confirmó valores no vacíos de los cuatro campos en una muestra de diez filas;
  no se conservaron valores ni identificadores.
- **Impacto:** una clave publicable puede leer secretos de autenticación y
  recuperación. El hash no mitiga la presencia adicional de `pass_plain`.
- **Límite:** no se restringe SELECT en RLS-003 para no romper los clientes
  publicados. Primero deben migrarse login/recuperación a Edge/RPC y eliminarse
  las lecturas directas; después se revoca SELECT de tabla y se conceden solo
  columnas públicas o una vista dedicada.

## RLS-005 — Las aristas de follow aceptan mutaciones públicas

- **Detectado:** 2026-07-26 mediante catálogo remoto de solo lectura y código
  Android.
- **Severidad:** alta.
- **Estado:** abierto; solo documentado.
- **Evidencia:** `community_profile_follows` tiene `allow all` y policies
  públicas de INSERT/DELETE con condiciones `true`; `anon` y `authenticated`
  conservan INSERT, UPDATE y DELETE. No hay trigger de actor. Android envía
  directamente `follower_profile_id` y `followed_profile_id`.
- **Impacto:** un cliente directo puede crear o eliminar relaciones atribuidas
  a otros perfiles. Además, los contadores cacheados no se actualizan.
- **Límite:** no se endurece aquí por la restricción de no cambiar producción.
  Requiere migración aislada owner/actor-active, pruebas PostgREST y Android, y
  coordinación con el backfill reversible descrito en
  `docs/PROFILE_FOLLOW_COUNTER_RECONCILIATION_PLAN.md`.

  **Corrección preparada, no desplegada:** las plantillas separadas de guardia
  de actor y reconciliación reversible están en `supabase/templates/`, pendientes
  de timestamps por el ledger bloqueado. Véase
  `docs/RLS_005_COMMUNITY_PROFILE_FOLLOWS_FIX.md`.

## RLS-006 — Publicación legacy y buckets de posts no vinculados uniformemente al actor

- **Detectado:** 2026-07-31 mediante una inspección remota del catálogo en una
  transacción de solo lectura; no se modificó Supabase. Esta evidencia describe
  ese snapshot y debe volver a verificarse antes de cualquier rollout.
- **Severidad:** alta.
- **Estado:** abierto; documentado y contenido en los adaptadores de cliente,
  pero no corregido en la frontera de datos.
- **Evidencia del snapshot:** `community_posts` conservaba, además de la policy
  autenticada que exige `author_id = auth.uid()`, la policy legacy
  `public insert community posts`. Esta aceptaba INSERT cuando existía
  `profile_id` y `author_id` era nulo o coincidía con ese perfil. `anon` y
  `authenticated` conservaban grants DML amplios y ningún trigger derivaba el
  actor; el único trigger de INSERT notificaba a seguidores.
- **Membership:** la elección de `wall_id` dependía de `community_members` o,
  como fallback legacy, del primer wall activo. Mientras el servidor no imponga
  esa pertenencia, cada cliente debe resolverla desde el actor de sesión y nunca
  aceptar `wall_id`, `profile_id` o `author_id` desde el launcher o el draft.
- **Storage:** coexistían los buckets públicos `community_posts` (48 MiB) y
  `community-posts` (1 GiB), con policies distintas sobre `storage.objects`.
  No deben intercambiarse por similitud de nombre: cada adaptador debe usar el
  bucket exacto de su contrato verificado y conservar rollback de uploads si
  falla el INSERT final.
- **Contención actual:** los adaptadores comunes de Composer vinculan el actor a
  la sesión renovable, resuelven el wall mediante membership, moderan antes del
  upload y ejecutan rollback/release no cancelable. Esto reduce suplantaciones
  accidentales y objetos huérfanos, pero no convierte al cliente en una frontera
  de seguridad ni demuestra que las policies remotas hayan cambiado.
- **Límite:** esta migración no cambia policies, grants, triggers, buckets ni
  datos. El endurecimiento requiere staging, compatibilidad con los clientes
  publicados, pruebas PostgREST por actor y rollback coordinado.
