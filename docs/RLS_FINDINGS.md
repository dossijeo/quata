# Hallazgos RLS pendientes

Este registro reúne hallazgos confirmados por pruebas E2E. No es una migración
y no autoriza cambios de esquema, funciones, políticas ni datos de producción.

Reconciliación MP-A14 (`9cc84dc2`): RLS-001/SB-07 y RLS-002/SB-09 continúan
abiertos. Communities y Official mantienen sus mutaciones afectadas
`fail-closed`; no se aplicó ninguna política ni cambio remoto.

## RLS-001 — Un outsider puede borrar un comentario ajeno

- **Detectado:** 2026-07-26, SB-07 de Communities.
- **Severidad:** alta.
- **Estado de producto:** contenido en los clientes KMP; abierto en el servicio hasta una
  corrección RLS coordinada que no rompa la Web ya publicada.
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

### Contención de clientes (2026-07-26)

No se ha modificado ninguna política RLS, esquema, función ni despliegue. Mientras RLS-001 esté
abierto, `CommunityMutationSafety` en `feature:neighborhoods` mantiene fallos cerrados para crear
o borrar comentarios, seguir usuarios, reportar posts y cambiar roles. Los adaptadores Web e iOS
usan esa misma barrera para las mutaciones que exponen; las capacidades Web conservan
`Communities/Mutate` deshabilitado. La creación de conversaciones no se incluye en esta barrera:
pertenece al contrato Chat, que cuenta con la evidencia independiente SB-04.

La regresión común `CommunityMutationSafetyTest` verifica que ninguna de esas operaciones pueda
activarse accidentalmente mientras el identificador de evidencia siga siendo `RLS-001`. Esto es
contención de producto, no una corrección de autorización del servidor: clientes antiguos,
llamadas directas o futuras integraciones siguen expuestos hasta corregir la política.

### Corrección requerida

Preparar una migración revisada y reversible en un entorno no productivo que elimine o restrinja
la política permisiva de `DELETE` y permita borrar sólo al propietario (`profile_id = auth.uid()`
o el mapeo de perfil equivalente) y a administradores explícitos. Antes de aplicarla a producción
hay que evaluar la Web publicada que hoy depende de las políticas existentes.

### Criterio de cierre

1. SB-07 se repite con dos perfiles nuevos y aislados.
2. El outsider recibe una denegación HTTP o una representación vacía.
3. El actor sigue viendo su comentario y puede borrar únicamente el suyo.
4. Se purgan y verifican todos los datos E2E.
5. Se registra el SHA de migración y la evidencia sin IDs, tokens, teléfonos ni secretos.
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
