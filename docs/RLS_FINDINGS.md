# Hallazgos RLS pendientes

Este registro reúne hallazgos confirmados por pruebas E2E. No es una migración
y no autoriza cambios de esquema, funciones, políticas ni datos de producción.

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
