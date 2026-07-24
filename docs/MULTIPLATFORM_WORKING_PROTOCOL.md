# Protocolo de trabajo para la migración multiplataforma

Este documento fija el flujo operativo de la migración Kotlin Multiplatform. Es obligatorio para
las iteraciones posteriores y prevalece sobre la comodidad de hacer tareas secuenciales desde el
agente raíz.

## Roles separados

- **Orquestación (agente raíz):** mantiene el inventario, selecciona unidades pequeñas y no
  solapadas, crea/asigna agentes, revisa sus informes, decide qué ramas están listas, integra en
  `main` y elimina ramas/worktrees efímeros ya integrados.
- **Implementación (agentes de feature):** trabajan en una sola unidad acotada por rama/worktree;
  usan `apply_patch`, no ejecutan Gradle, no esperan CI y no integran en `main`. Entregan commit,
  push, archivos tocados, límites de plataforma y riesgos de integración.
- **Validación/CI (agente dedicado):** monitoriza GitHub Actions, ejecuta compilaciones locales,
  interpreta fallos y corrige sólo la rama validada cuando sea necesario. No implementa features,
  no integra ramas ni las borra. Las validaciones iOS se hacen en macOS/GitHub Actions; no se
  despachan workflows manualmente salvo autorización expresa del usuario.

## Flujo continuo

1. El agente raíz mantiene dos o más unidades de implementación independientes ocupando las
   plazas disponibles cuando exista trabajo no solapado.
2. En paralelo, el agente de validación procesa las ramas ya entregadas. Una compilación o una CI
   en curso **no detiene** la asignación de nuevas unidades de implementación.
3. El agente raíz no hace sondeos repetidos de Gradle/CI ni espera activamente sus resultados;
   recibe el informe del agente de validación. Sólo interviene si dicho agente solicita una
   decisión de alcance o informa una rama lista/fallida.
4. Tras un informe verde, el agente raíz revisa el alcance y fusiona directamente en `main` cuando
   el usuario haya autorizado el merge directo. Cada merge se valida en el orden acordado y se
   limpia de inmediato: worktree, rama local y rama remota.
5. Si GitHub no permite abrir PRs, las ramas siguen siendo efímeras: se validan primero y se
   integran directamente en `main`; no se acumulan ramas "pendientes" sin dueño.

## Invariantes de arquitectura y validación

- No reescrituras masivas ni migración de varias features completas en una unidad.
- `commonMain` no importa Android; las plataformas sólo contienen hosts, adaptadores y APIs de
  sistema.
- Los ViewModels comunes no dependen de `Context` ni de `AndroidViewModel`.
- Todo adaptador no disponible debe fallar de forma explícita; nunca se simulan datos o resultados
  de plataforma.
- El inventario en `docs/MULTIPLATFORM_MIGRATION_BOARD.md` se actualiza de forma puntual al
  cambiar estado real de hosts, adaptadores o validación.
- Los tests Wasm afectados por el ICE conocido de Compose se verifican por compilación Kotlin/Wasm
  y se documentan como tal; no se declara un falso éxito de tests.
