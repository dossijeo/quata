# Mapa de documentación

El repositorio contiene documentación normativa, operativa, evidencia e historia. Este mapa evita interpretar un snapshot antiguo como estado actual.

## Normativa vigente

- `MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`: reglas de la migración.
- `DATABASE_RELEASE_SAFETY.md`: seguridad de releases de base de datos.
- `BACKEND_COMPATIBILITY_GATES.md`: compatibilidad del backend.
- `CI_WEB_ANDROID.md` e `IOS_CI.md`: contratos de CI.

## Estado vivo

- `SCREEN_MIGRATION_INVENTORY_V2.md`: estado por pantalla.
- `MULTIPLATFORM_MIGRATION_BOARD.md`: tablero operativo.
- `capabilities/platform-capability-matrix.json`: capacidades por plataforma.
- GitHub PR/Actions: candidatos y ejecuciones actuales.

El estado vivo debe actualizarse después de integrar cambios; si diverge del código, se corrige antes de usarlo para planificar.

## Runbooks y operaciones

- Documentos `SUPABASE_E2E_SB*.md`.
- Guías `IOS_*.md`.
- Guías `WEB_*.md` y `WASM_*.md`.
- Runbooks de backup, release y rollback.

## Evidencia e histórico

Documentos con fecha, `*_ASSESSMENT_*`, `*_SNAPSHOT_*`, planes `*_PLAN.md` y auditorías describen una observación o propuesta concreta. Conservan valor histórico, pero no sustituyen una verificación posterior.

## Decisiones

Las decisiones técnicas estables deben explicar contexto, opción elegida, alternativas y consecuencias. `JS_IR_TARGET_DECISION.md` es un ejemplo. Si una decisión queda obsoleta, se marca como supersedida y se enlaza su reemplazo; no se borra el razonamiento histórico.
