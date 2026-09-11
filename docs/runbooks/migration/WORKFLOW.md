# Flujo de candidata e integración

Detalle del [modelo operativo](../../MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md); forma parte de la misma fuente de verdad, con el mismo alcance y autorizaciones.

## 4. Flujo por pantalla

1. El orquestador revisa personalmente Android y el inventario de pantallas. Define raíz Compose,
   datos, eventos, navegación, mutaciones y adaptadores que deben existir.
2. Un agente de implementación Terra Medium trabaja en una rama y un worktree propios.
3. El agente sustituye cualquier fallback por la raíz común completa y conserva el comportamiento
   Android. No añade mocks ni funciones provisionales de producto.
4. Compila, ejecuta y revisa localmente todas las pruebas relevantes de las plataformas afectadas.
   No se publica un candidato mientras siga un build local relevante o falte una comprobación
   reproducible de la ruta, sesión, backend, mutación o estado de error afectado.
5. Acumula los commits intencionales en el worktree. Tras actualizar e integrar `origin/main` una
   sola vez, resuelve conflictos, repite los checks afectados, revisa el diff completo y congela el
   head. Sólo entonces publica una única tanda candidata y su PR draft.
6. Un agente Sol independiente revisa la PR exacta. Comprueba código, contratos, navegación,
   backend real y comparación visual contra Android en el mismo estado de autenticación.
7. El revisor guarda capturas e informe en
   `C:\Users\PC\Desktop\QÜATA\migration-v2\evidence\<pantalla>\<sha>-<plataforma>`.
8. Solo un candidato con compilación, CI y gate visual/funcional **GO** puede marcarse ready y
   fusionarse.
9. Inmediatamente tras el merge se completa el cierre documental obligatorio del inventario
   conforme a la regla [«Inventario = estado operativo actual»](BRANCHES_AND_CLOSEOUT.md#inventario--estado-operativo-actual) y se deja una nota para
   que el responsable del producto pruebe la pantalla. Después se eliminan rama y worktree
   integrados cuando la limpieza sea inequívocamente segura.
10. Los bugs funcionales encontrados por el responsable del producto forman una segunda ronda; no
    invalidan la obligación de entregar primero una pantalla conectada y visualmente comparable.

### Two-lane migration pipeline + native auto-merge

<a id="autorización-permanente-de-promoción"></a>

**Autorización permanente de promoción (usuario, 11 de septiembre de 2026).**
El orquestador debe promover autónomamente cualquier PR de la migración cuando el alcance
focal esté terminado, el diff haya pasado revisión independiente, el preflight local requerido
esté verde y la evidencia necesaria corresponda al Product/Evidence SHA. Antes de congelar
el head, debe reconciliar fixtures y recursos, descartar mutaciones remotas inciertas y
documentar los límites abiertos. La ausencia de otra aprobación humana no es un bloqueo.

Cumplidas esas condiciones, marcar Ready, congelar el head, aplicar `candidate-final`, armar
auto-merge y comprobar tanto los fast gates como el inicio de los jobs finales reales. La
certificación final decide la integración; no debe exigirse su resultado antes de promover.
No cambiar el head congelado salvo para resolver un fallo atribuible a la candidata, con
nueva revisión y promoción. Clasificar los fallos flaky o ajenos conforme a este workflow.
Tras el merge, confirmar SHA y CI final, reconciliar el inventario operativo con `main`,
limpiar sólo trabajo inequívocamente integrado y efectuar el handoff previsto.

Detener la promoción ante cambios destructivos o irreversibles, compatibilidad de producción
sin resolver, mutaciones inciertas, evidencia contradictoria, hallazgos bloqueantes, posible
exposición de secretos, scope mezclado, identidad Product/Evidence SHA desconocida o una
dependencia real todavía no integrada que pueda cambiar el producto. Esta autorización
no elimina ninguna comprobación técnica ni amplía el alcance focal.

La certificacion CI larga nunca forma parte del camino critico activo del orquestador. Cuando una
rama candidata termina desarrollo, supera el preflight local suficiente y tiene Product/Evidence SHA
o attestation valida, se promociona con:

`node scripts/promote-candidate-final.mjs --pr <numero> --sha <head-sha-congelado>`

La promocion verifica que la PR no es draft, que el head actual coincide exactamente con el SHA
congelado, que GitHub native auto-merge esta habilitado en el repositorio, aplica `candidate-final`
si falta y solicita auto-merge nativo con metodo
`SQUASH`. Auto-merge no sustituye branch protection, reviews requeridas, conversaciones resueltas,
checks requeridos, estado actualizado ni conflicto de merge; solo autoriza a GitHub a fusionar el
SHA congelado cuando GitHub ya lo considera apto.

Si una PR abierta ya tenia `candidate-final` antes de existir la automatizacion, o en cualquier
checkpoint natural aparece una candidata valida con `autoMergeRequest: null`, se ejecuta primero
`node scripts/backfill-candidate-auto-merge.mjs --pr <numero> --dry-run`. Solo si ese diagnostico
confirma que no hay draft, conflicto, cambios solicitados, gates requeridos pendientes/faltantes ni
parent stack desactualizado, se repite sin `--dry-run` para solicitar auto-merge nativo. Si la PR
dependia de una rama padre ya fusionada, se rebasa/sincroniza antes sobre `main`; cualquier cambio de
producto no recertificado bloquea el backfill.

Cuando la PR candidata queda con `candidate-final` y auto-merge nativo, pasa los checks rapidos
iniciales relevantes y se observa que los jobs pesados ya entraron en builds, simuladores,
distribuciones o pruebas largas, esa rama pasa a estado **CANDIDATE FROZEN / CERTIFICATION IN
PROGRESS / AUTO-MERGE ARMED** y queda concluida a efectos del trabajo activo.

El handoff no ocurre inmediatamente despues de hacer push. Primero se observan los fallos rapidos:
clasificacion de impacto, contratos baratos, sintaxis/imports, configuracion, gates preliminares,
rechazo de `candidate-final` y errores al solicitar auto-merge. Si alguno falla, se corrige en la
rama candidata. Si esos gates estan verdes y el tramo largo ya empezo, el orquestador deja de
vigilar activamente esa PR.

El pipeline normal tiene dos carriles:

- **Lane A: candidate frozen / certification in progress / auto-merge armed.** La rama publicada
  queda inmutable salvo correccion de un fallo real. GitHub la fusiona automaticamente cuando todos
  los checks requeridos de certificacion final pasen sobre el SHA exacto y branch protection quede
  satisfecha.
- **Lane B: next surface under active development.** El orquestador empieza inmediatamente la
  siguiente superficie elegible. Si B depende de A, se crea como rama apilada desde el SHA candidato
  de A; si es independiente, puede salir de `main`.

La profundidad normal maxima es dos: una candidata certificandose y una superficie activa por
delante. No se abre una cadena A/B/C/D salvo decision explicita por una dependencia real. Cuando
GitHub auto-mergea A, el orquestador no tiene que reaccionar al segundo exacto: en el siguiente
checkpoint natural hace `git fetch origin`, detecta que `main` avanzo, rebasa B sobre el nuevo
`origin/main`, resuelve conflictos y continua. Los checkpoints naturales son antes de un push
significativo de B, antes de promocionar B a `candidate-final`, al cerrar un bloque focal de
trabajo o cuando un watcher avise de una anomalia.

<a id="fallo-anomalía-o-integración-detenida"></a>

Si A falla mientras B avanza, se clasifica primero: un fallo de API comun, arquitectura compartida,
compilacion comun, fixtures, pipeline o contrato que B usa interrumpe B; un fallo focal de evidencia,
screenshot, selector o test localizado se corrige aislado en A sin destruir B.

La vigilancia de la lane A puede delegarse a un subagente Spark solo en modo anomalias: detectar CI
FAIL, auto-merge bloqueado durante tiempo anormal, required check ausente, rama desactualizada,
review/conversacion requerida, conflicto, merge queue/ruleset, auto-merge desactivado o SHA cambiado.
Spark ya no vigila el camino feliz para avisar PASS; GitHub hace el merge. Si Spark no esta
disponible, el orquestador revisa Actions solo en puntos naturales posteriores, antes de operaciones
que dependan del merge o cuando necesite clasificar un cambio de estado.

Si una candidata con `candidate-final` y auto-merge habilitado no tiene checks fallidos pero no se
fusiona durante un tiempo anormal, no se hace polling infinito. Se diagnostica concretamente:
reviews, conversaciones pendientes, conflicto, branch behind por proteccion estricta, required check
faltante, skip incorrecto de gate, ruleset/merge queue, auto-merge desactivado o SHA drift.

Queda prohibido el polling ocioso durante el tramo largo: `check CI -> sigue running -> esperar ->
check CI`. Si existe una superficie siguiente elegible, se trabaja en ella. Si una dependencia real
impide empezar B, se usa el tiempo en trabajo auxiliar util: preparar anclas semanticas, macros E2E,
fixtures, analisis de la siguiente unidad, contratos rapidos, investigacion de deuda o limpieza de
ramas. Mirar CI sin producir trabajo no cuenta como avance operativo.

Cuando se aplique este modelo a una candidata, el informe registra PR, momento del handoff, checks
rapidos ya verdes, job largo en ejecucion, siguiente superficie iniciada, si hubo rama apilada o
worktree, si Spark vigilo anomalias, trabajo avanzado mientras CI seguia ejecutandose, resultado
final, auto-merge de GitHub, rebase y conflictos.

Todo defecto descubierto tras publicar se clasifica antes de corregirlo: **DEFECTO ESCAPADO DEL
PREFLIGHT LOCAL** si era reproducible con comandos/artefactos locales disponibles; defecto de
runner, cache, toolchain o servicio exclusivo remoto si no lo era. En el primer caso no basta con
arreglar el codigo: se anade el gate preventivo, se ejecuta y se registra antes de la siguiente
promocion. El head previo queda invalidado y cualquier CI cancelado se conserva solo como
diagnostico, nunca como evidencia GO.

Los runners de autenticacion iOS que invocan `xcodebuild` mediante un `.xctestrun` y
`QUATA_IOS_AUTH_E2E_FILE` explicito deben verificar el resultado semantico del test: un proceso con
salida `0` no es PASS si el test figura como `SKIPPED` o no llego a ejecutarse. El runner falla en
esos casos y conserva el diagnostico redactado.
