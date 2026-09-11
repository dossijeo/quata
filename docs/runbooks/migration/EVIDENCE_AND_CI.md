# Evidencia y certificación

Detalle del [modelo operativo](../../MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md); forma parte de la misma fuente de verdad, con el mismo alcance y autorizaciones.

## 5. Evidencia y gates

### Preflight local, candidato y certificación remota

La integración es **secuencial**: sólo existe un candidato final de merge a la vez. La ejecución es
**paralela**: mientras ese candidato recibe certificación remota, las lanes locales libres preparan
la siguiente unidad sin cambiar el head del candidato ni promocionar otra PR.

Antes de publicar un candidato se ejecuta el preflight local proporcional al diff: compilación y
tests focales, Android/Wasm/iOS afectados, Kotlin/Native, host Swift, simulador, rutas, navegación,
backend real, sesión pública o autenticada, mutaciones, errores recuperables, comparación visual,
limpieza de datos/procesos y revisión completa del diff. La evidencia local registra los comandos,
SHA y resultado.

Para rutas E2E visuales complejas, el orden preferido del preflight es la grabacion de macro visual
con `tools/e2e-recorder`: primero se recorre la ruta una vez de forma visual, despues se resuelve
cada evento a anclas semanticas estables, se anaden anclas de producto si aparece
`missing_stable_anchor`, se compila/reproduce localmente la macro y solo entonces se promueve el
runner al preflight/CI. Las coordenadas absolutas solo son diagnostico o fallback temporal de
descubrimiento; no son mecanismo principal de replay ni justifican iteraciones ciegas de push/CI.
Si una accion funciona visualmente pero no tiene `testTag`, `accessibilityIdentifier`,
`resource-id`, etiqueta accesible, texto o contexto estable suficiente, el recorder debe fallar
cerrado y senalar el paso antes de crear un test fragil.

El preflight rápido exacto de CI es obligatorio antes de congelar/publicar: ejecuta los contratos
rápidos que replica la automatización remota, imports Wasm focales y `diff --check`. Un candidato
no se publica si esa réplica falla. Los workflows y sus gates finales son *fail-closed*: un job
final omitido, cancelado o fallido nunca puede convertir el gate requerido en verde.
Los checks requeridos de certificacion son exactamente **PR fast contracts and focal imports**,
**iOS fast contracts**, **Web/Android final certification gate**, **iOS final certification gate**
y **CodeQL final security gate**. Los gates finales solo son GO cuando todos sus jobs finales
exactos concluyen correctamente o cuando la clasificacion `docs_only` permite omitirlos de forma
explicita y fail-closed. CodeQL usa un gate estable: **Analyze java-kotlin** y
**Analyze javascript-typescript** ejecutan el analisis real cuando el diff no es documental, pero
branch protection exige **CodeQL final security gate** para que PRs docs-only no queden bloqueadas
por jobs matriciales omitidos.

Una PR preparatoria sin `candidate-final` no debe quedar roja por los gates agregados finales si
todos los jobs finales afectados fueron `skipped` por la guarda de `candidate-final`. Ese estado
solo significa "todavia no certificada"; no es GO, no permite merge a `main` y falla cerrado si
cualquier job final corre, se cancela o produce un resultado distinto de `skipped` antes de la
promocion.


### Product/Evidence SHA y attestation documental

La evidencia de producto se acredita sobre un **Product/Evidence SHA**: el commit exacto que se compilo, ejecuto y recorrio visual/operativamente. Las actualizaciones posteriores que solo registran evidencia, inventario, tablero, manifest de candidato o informes son **Attestation/Documentation SHA** y no obligan por si mismas a repetir evidencia.

Esta reutilizacion solo es valida si `scripts/validate-candidate-attestation.mjs` demuestra con el diff real `productSha..HEAD` que todos los cambios pertenecen a la allowlist documental de attestation. El gate falla cerrado si aparece cualquier cambio ejecutable, workflow, runner, test, fuente Kotlin/Swift/JS/MJS, recurso de producto, Gradle/configuracion/dependencia, estado Git no confiable o evidencia incompleta. Los mensajes de commit no cuentan como prueba.

Cada candidata que quiera reutilizar evidencia debe mantener un manifest versionado en `docs/candidate-attestations/`. El manifest declara unidades, `productSha`, reportes por plataforma, estado `passed`, SHA exacto de cada evidencia y limpieza verificada. Actualizar ese manifest es metadata de attestation; no crea un bucle de recertificacion mientras el diff siga siendo attestation-only. Si el validador imprime el archivo que invalida la evidencia, se repite la evidencia afectada antes de promocionar la candidata.

Para PRs realmente `docs_only`, CI ejecuta solo el camino barato: checkout, `diff --check`, contratos documentales/attestation y gates agregados. No se instala Java, Gradle, Android SDK, Wasm, Xcode ni CodeQL en PRs de documentacion pura. `push`, `schedule` y `workflow_dispatch` conservan certificacion completa o diagnostica segun corresponda; si hay duda, se ejecuta CI caro.

Los runners E2E de plataforma no deben copiar helpers backend comunes. La plataforma lanza la app, navega, interactua y captura evidencia; los fixtures backend reutilizables viven en `scripts/e2e-fixtures/` y registran cleanup antes de mutaciones remotas cuando sea posible. Si un runner necesita documento/audio/chat/storage, primero extiende la libreria comun y sus contratos.

GitHub Actions es la **certificación final en runners limpios**, no el primer lugar donde descubrir
que una implementación no compila ni funciona. Si CI revela un defecto reproducible localmente, el
informe lo clasifica como **DEFECTO ESCAPADO DEL PREFLIGHT LOCAL** e incorpora obligatoriamente el
comando, test o contrato preventivo al preflight antes de publicar el siguiente candidato.

### Identidad obligatoria del candidato integrado

- Todo gate integrado Web o iOS —compilación, tests, browser/simulador y comparación visual— se
  ejecuta sobre el commit de merge sintético exacto publicado por GitHub en
  `refs/pull/<N>/merge`, no sobre el head aislado de la rama.
- Antes del gate se obtienen y congelan tres identidades: la `origin/main` exacta esperada como
  base, `refs/pull/<N>/head` como head exacto de la PR y `refs/pull/<N>/merge` como candidato
  integrado.
- El merge sintético debe tener exactamente dos padres. El primero debe coincidir byte por byte con
  la base `main` registrada y el segundo con el head de PR registrado. Si falta el ref, cambia
  cualquiera de los padres o no coincide el orden, no existe evidencia integrada válida y el gate
  se repite desde cero.
- El informe, los logs y el directorio de capturas registran el número de PR y los SHA completos de
  base, head y merge. El SHA principal de la evidencia es siempre el del merge sintético.
- Un build del head aislado solo diagnostica la rama. No autoriza GO ni decisión de merge; sus
  informes y capturas se marcan explícitamente como **DESCARTADOS: HEAD-ONLY** para evitar su
  reutilización como evidencia integrada.
- La evidencia de una plataforma sólo puede reutilizarse si existe una regla formal que lo autoriza
  y un diff revisado demuestra que desde la evidencia previa no cambió ningún input de esa
  plataforma (código, recursos, configuración, dependencias, host, datos/contrato ni ruta). El
  informe identifica la evidencia origen, ambos SHA, el diff y al revisor. Si no puede demostrarse,
  el gate exacto del merge sintético se repite.

Una pantalla solo es **GO** cuando existe evidencia para todos estos puntos:

- Android, Wasm e iOS invocan la raíz Compose común prevista.
- Las rutas pública/privada, el diálogo Auth, el retorno y logout coinciden con Android.
- Lecturas y mutaciones usan backend real y sesión real cuando corresponde.
- No quedan callbacks vacíos, placeholders, controles engañosos ni errores absorbidos.
- Android compila y pasa lint/tests relevantes sin baseline nuevo ni `suppress` especulativo.
- Wasm compila, genera distribución de producción y pasa tests browser/rutas.
- iOS compila Kotlin para dispositivo/simulador, Xcode/Swift y tests focales.
- CI corresponde al SHA exacto y todos los checks requeridos están verdes. Los fallos de
  infraestructura se demuestran por logs y se reintentan; no se confunden con un GO.
- Existe comparación visual Android↔Wasm y Android↔iOS en el mismo flujo y sesión.
- La captura está inspeccionada; una captura de Login, Cuenta u otra ruta no prueba la pantalla
  objetivo.

Las pruebas instrumentadas y de contrato ayudan a detectar regresiones, pero nunca sustituyen la
conexión conceptual a `commonMain`, el backend real o la comparación visual 1:1.
