# Hallazgos RLS pendientes

Este registro reúne hallazgos confirmados por pruebas E2E. No es una migración
ni autoriza cambios de esquema, funciones, políticas o datos de producción.

## RLS-001 — Un outsider puede borrar un comentario ajeno

- **Detectado:** 2026-07-26, SB-07 de Communities.
- **Severidad:** alta.
- **Superficie:** `public.community_comments`, operación `DELETE` mediante la
  clave publicable y un JWT autenticado.
- **Reproducción segura:** crear dos perfiles E2E aislados, un wall y post del
  actor; crear un comentario con el JWT del actor; intentar `DELETE` sobre el
  ID del comentario con el JWT del outsider. La operación eliminó la fila, en
  vez de devolver una representación vacía o una denegación de autorización.
- **Evidencia:** `scripts/run-supabase-e2e-sb07.ps1` finalizó con
  `rls_violation`; el lote purgó perfiles, wall y post efímeros y verificó la
  ausencia de residuos.
- **Impacto:** cualquier usuario autenticado que alcance el endpoint puede
  eliminar comentarios de otro perfil, con independencia de la UI.

### Corrección requerida

Preparar una migración revisada y reversible en un entorno no productivo que
elimine o restrinja la política permisiva de `DELETE` y permita borrar sólo al
propietario (`profile_id = auth.uid()` o el mapeo de perfil equivalente) y a
administradores explícitos. Antes de aplicarla a producción hay que evaluar la
web publicada que hoy depende de políticas existentes.

### Criterio de cierre

1. SB-07 se repite con dos perfiles nuevos y aislados.
2. El outsider recibe una denegación HTTP o una representación vacía.
3. El actor sigue viendo su comentario y puede borrar únicamente el suyo.
4. Se purgan y verifican todos los datos E2E.
5. Se registra el SHA de migración y la evidencia sin IDs, tokens ni teléfonos.

