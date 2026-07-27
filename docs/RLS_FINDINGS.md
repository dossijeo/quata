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

**Preparada, no desplegada (2026-07-26):**
`20260726171001_community_comments_delete_rls.sql` reemplaza la política DELETE
permisiva por propietario canónico activo/administrador explícito. También
cierra la cadena de evasión `UPDATE → DELETE` haciendo inmutable la fila y
limita `INSERT` al perfil canónico activo para impedir suplantación. Conserva
SELECT público y los contratos Android de alta y borrado por `id`; incluye
rollback y regresión SQL/E2E aislada con fixtures efímeros y purga verificada.
La evidencia y riesgos están en
`docs/RLS001_COMMUNITY_COMMENTS_DELETE_PLAN.md`.

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
