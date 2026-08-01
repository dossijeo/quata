# Migración multiplataforma

El documento vinculante es el [Modelo operativo de la migración](https://github.com/dossijeo/quata/blob/main/docs/MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md). Esta página lo resume; en caso de discrepancia prevalece el documento versionado.

## Unidad de trabajo

La unidad de migración es una pantalla o flujo vertical completo, no un módulo vacío ni una colección de componentes.

Cada unidad debe incluir:

- Raíz Compose común derivada de Android.
- Montaje literal desde Android, Web e iOS.
- Estado, eventos y navegación comunes.
- Adaptadores reales por plataforma.
- Lecturas y mutaciones reales del backend.
- Tests y compilaciones.
- Comparación funcional y visual.

## Flujo de aceptación

```text
Android de referencia
        ↓
raíz commonMain completa
        ↓
adaptadores Android · Web · iOS
        ↓
compilación y pruebas locales
        ↓
PR draft
        ↓
revisión independiente del SHA exacto
        ↓
CI + backend + visual/funcional
        ↓
GO → merge → inventario → limpieza
```

## Gates

### GO

Requiere simultáneamente:

- La misma raíz común.
- Navegación, auth y logout correctos.
- Backend real para reads y writes.
- Sin no-ops, placeholders ni errores absorbidos.
- Android, Wasm e iOS verdes.
- Evidencia del commit exacto.
- Comparación Android↔Web y Android↔iOS con datos/sesión equivalentes.

### HOLD

Se usa cuando falta evidencia externa o una lane aún no ha terminado. No equivale a aprobación.

### NO-GO

Se aplica ante cualquier incumplimiento concreto: pantalla paralela, backend falso, callback vacío, navegación rota, compilación roja, evidencia de otro SHA o diferencia funcional relevante.

## Ramas y PR

- Cada implementación usa rama y worktree aislados.
- Las PR permanecen draft hasta el GO independiente.
- Un “static GO” queda invalidado cuando cambia el head.
- Una PR supersedida se cierra cuando el sucesor tiene evidencia suficiente.
- Tras merge se eliminan ramas y worktrees integrados.

## Evidencia

La evidencia vive fuera del repositorio en una estructura por pantalla, SHA y plataforma. Debe registrar procedencia, ruta, sesión, resultado y limpieza, sin secretos ni datos personales innecesarios.
