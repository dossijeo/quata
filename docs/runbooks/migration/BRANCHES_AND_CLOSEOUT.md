# Ramas y cierre operativo

Detalle del [modelo operativo](../../MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md); forma parte de la misma fuente de verdad, con el mismo alcance y autorizaciones.

### Inventario = estado operativo actual

`docs/SCREEN_MIGRATION_INVENTORY_V2.md` refleja el último estado integrado y certificado
de cada unidad. No es un registro histórico de estados provisionales ya superados.

- Durante la candidata puede indicar «aceptación local verificada · certificación/integración
  pendiente», con Product/Evidence SHA y límites explícitos. Ese estado todavía no es GO integrado.
- Después de `candidate-final`, la candidata permanece congelada durante la certificación final.
  No se modifica su producto ni su inventario para anticipar el resultado o invalidar evidencia.
- Inmediatamente después del merge se sincroniza una rama documental con `origin/main`, se
  confirman merge SHA y resultados de los jobs finales reales y se actualiza la fila focal del
  inventario maestro para reflejar la integración certificada. Se eliminan «pendiente de
  certificación», «PR pendiente» y «aceptación pendiente» cuando ya no correspondan, conservando
  todos los límites abiertos. No se promocionan padres ni unidades vecinas por inferencia.
- Esta actualización se revisa e integra como cierre documental obligatorio. El handoff de la
  lane de certificación permite preparar la siguiente unidad según el two-lane pipeline, pero
  el handoff operativo de una unidad no está completamente cerrado hasta que su inventario
  actualizado esté integrado en `main`.

Un cierre exclusivamente documental no exige repetir evidencia de producto que su diff no
invalide; se comprueba el alcance real del diff y se aplican los gates documentales establecidos.

## 6. Ramas, commits y PR

- Prefijo por defecto: `codex/`.
- Cada agente de código usa una rama/worktree aislados; ningún revisor edita ese worktree.
- No se fuerza push, no se rebasa una rama compartida y no se escribe directamente en `main`.
- Una reparación puede avanzar la rama de una PR solo mediante fast-forward verificado.
- Los commits son pequeños y describen una unidad real de producto o validación.
- Las PR permanecen draft hasta obtener GO independiente.
- Durante la certificación CI de una PR publicada se aplica el **Two-lane migration pipeline**:
  tras fast gates verdes y tramo largo iniciado, la candidata queda congelada y el orquestador
  empieza la siguiente superficie en una rama normal o apilada.
- El happy path remoto usa GitHub nativo: una PR con `candidate-final` y auto-merge habilitado se
  fusiona cuando branch protection queda satisfecha y el repositorio elimina automáticamente la
  rama remota de head mediante `Automatically delete head branches`.
- La limpieza remota retroactiva no usa workflows propios: se ejecuta
  `node scripts/cleanup-merged-remote-branches.mjs --json`, se revisa el plan y solo despues
  `node scripts/cleanup-merged-remote-branches.mjs --apply`. La herramienta borra ramas remotas
  `codex/*` unicamente si la PR asociada esta confirmadamente mergeada, el SHA remoto coincide con
  el `headRefOid` mergeado, no existe PR abierta que use esa rama como head/base, la rama no esta
  protegida y GitHub no devuelve estado ambiguo.
- El happy path local se limpia en el siguiente checkpoint natural antes de abrir otra superficie:
  `git fetch --prune`, `node scripts/cleanup-merged-worktrees.mjs --json`, revisión del plan y
  `node scripts/cleanup-merged-worktrees.mjs --apply` solo para candidatos inequívocamente seguros.
  Si el proceso ejecuta Git/GitHub CLI con un SID distinto y aparece `dubious ownership`, se exporta
  `QUATA_GIT_SAFE_DIRECTORY=<ruta absoluta del repo>` para que las utilidades propaguen
  `safe.directory` tambien a las llamadas internas de `gh`, sin cambiar la configuracion global.
- En ramas apiladas, si el padre ya fue fusionado, primero se rebasa la hija activa sobre
  `origin/main`; solo después, cuando la hija ya no depende de la rama/worktree padre, se limpia el
  padre localmente.
- La limpieza local falla cerrada: nunca usa `git worktree remove --force` ni `git branch -D` ante
  cambios sin commit, archivos no trackeados, commits no publicados, PR abierta, PR no demostrada
  como mergeada, rama usada por otro worktree, dependencia apilada o estado ambiguo.
- Una PR superseded se cierra solo cuando su sucesora contiene su ancestry necesaria y ha obtenido
  evidencia suficiente; después se eliminan ambas ramas obsoletas.
- Tras completar la migración y limpiar lo integrado, el objetivo de repositorio es conservar solo
  `main`.
