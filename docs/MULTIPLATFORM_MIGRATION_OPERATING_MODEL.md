# Modelo operativo de la migración multiplataforma

Estado: **fuente de verdad vigente**
Última revisión: 2 de agosto de 2026

Este documento define cómo se completa y valida la migración de Qüata a Kotlin/Compose
Multiplatform. Si una nota, backlog, agente o PR contradice este documento, prevalece este
documento hasta que el responsable del producto lo modifique explícitamente.

## 1. Objetivo y referencia de producto

- Android publicado es la referencia funcional y visual.
- Web e iOS deben montar literalmente las mismas raíces Compose de `commonMain` que Android.
- El objetivo prioritario es que Android, iOS y Web sean, en la medida en que las capacidades de
  cada plataforma lo permitan, funcionalmente equivalentes y visualmente coherentes, compartiendo
  la máxima cantidad posible de código en `commonMain`.
- Este objetivo también actúa como criterio de revisión retroactiva de lo ya migrado: cualquier
  pantalla o flujo en estado `COMÚN CON LÍMITES`, `PARCIAL` o `AUSENTE` debe evaluarse contra esta
  premisa antes de considerarse cerrado.
- En caso de conflicto entre mantener la paridad funcional y optimizar tamaño, tiempos de
  compilación o métricas de rendimiento, prevalece la paridad funcional salvo que exista una
  limitación técnica real de la plataforma.
- No se eliminan, simplifican ni degradan funcionalidades para una plataforma sin aprobación
  explícita. Si una restricción obliga a hacerlo, se documenta la causa y se proponen alternativas
  antes de modificar el comportamiento.
- `commonMain` es la implementación de referencia. Android, iOS y Web solo divergen cuando es
  estrictamente necesario por APIs nativas.
- Si una funcionalidad no puede implementarse igual, se crea una abstracción (`expect`/`actual`,
  interfaces u otro contrato equivalente) antes que eliminarla.
- Los presupuestos de tamaño, bundle, Wasm o CI son ajustables; no justifican por sí solos la
  eliminación de funcionalidades.
- La experiencia del usuario debe permanecer consistente entre plataformas.
- No se aceptan pantallas HTML, hosts simplificados, «cutrescreens», maquetas ni implementaciones
  paralelas que imiten Android.
- Un adaptador de plataforma solo puede contener capacidades realmente específicas del sistema:
  picker/cámara, Keychain, Web Push/APNs, compartir, mapas, Quick Look/DocMentis, codecs,
  reproducción y exportación de medios, permisos y transporte HTTP.
- ViewModels, estado, reglas, navegación de producto, composición visual y eventos pertenecen a
  código común siempre que Android no dependa de una API del sistema.
- Que una pantalla compile o comparta un ViewModel no demuestra que esté migrada.
- No se acepta ningún control visible conectado a `no-op`, datos falsos, borradores locales que se
  presenten como persistencia, repositorios «unavailable» o fallos convertidos en éxito.

## 2. Contrato de navegación y autenticación

### Superficies públicas

- Feed, Comunidades, Oficial, Notificaciones y perfiles públicos se pueden abrir sin sesión.
- Header y navegación principal permanecen visibles durante la navegación anónima por esas rutas.
- Feed nunca solicita autenticación para leer.

### Superficies y acciones privadas

- Chats, Cuenta/SOS y Ajustes privados requieren sesión.
- Publicar, comentar, dar like, reportar y las demás acciones restringidas solicitan sesión aunque
  el contenido que las contiene sea público.
- Una acción restringida anónima muestra primero el diálogo común «Ya tengo cuenta / Registrar»
  sobre el contenido actual.
- Login, Registro y Recuperar contraseña son pantallas completas, fuera del shell principal.
- Tras autenticar, se restaura la ruta y la acción pendientes. Cancelar el diálogo o abandonar Auth
  elimina la acción pendiente; un login posterior no puede ejecutarla de forma accidental.
- Cerrar sesión revoca/limpia la sesión de plataforma y vuelve al Feed público.
- El origen debe conservarse: una acción iniciada desde Feed, Oficial o Comunidades regresa al mismo
  origen, no a una ruta fija.

### Contrato Web específico

- Login y logout Web usan el flujo `web_login` y la integración Web Push descrita en
  `supabase/WEB_PUSH_INTEGRATION.md`.
- Nunca se usa la publishable key como bearer de usuario.
- La sesión debe ser renovable y los fallos HTTP, timeout y cancelación deben propagarse de forma
  honesta.

## 3. Seguridad y compatibilidad con producción

- Android publicado, la Web antigua y el Feed anónimo no se pueden romper.
- La ausencia o amplitud temporal de RLS no bloquea la migración funcional: Web/iOS implementan el
  mismo contrato backend que Android utiliza hoy.
- No se endurecen, eliminan ni despliegan políticas RLS, esquema, tablas, funciones o datos que
  puedan romper clientes actuales.
- La deuda de seguridad se documenta con evidencia y se aplicará después de publicar los clientes
  migrados.
- Una Edge Function nueva puede desarrollarse de forma aditiva, pero su despliegue, secretos y
  activación se validan por separado. Nunca se incluye una service-role key ni un secreto privado en
  clientes, commits, logs o capturas.
- En una validación se pueden inyectar metadatos **públicos** de despliegue únicamente en una copia
  temporal del artefacto que se sirve o instala. El artefacto original y su hash permanecen
  inmutables. Ni la copia ni el original pueden contener una service-role key o una clave VAPID
  privada.
- Supabase CLI se usa en modo de lectura para auditar el estado actual salvo autorización explícita
  para una operación aditiva ya revisada.
- Los datos y cuentas temporales de prueba se eliminan al terminar.
- Las credenciales locales, claves SSH, certificados y ficheros de sesión nunca se versionan ni se
  imprimen en logs.

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
9. Tras el merge se actualizan el inventario y una nota para que el responsable del producto pruebe
   la pantalla. Después se eliminan rama y worktree integrados.
10. Los bugs funcionales encontrados por el responsable del producto forman una segunda ronda; no
    invalidan la obligación de entregar primero una pantalla conectada y visualmente comparable.

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

### Two-lane migration pipeline + native auto-merge

La certificacion CI larga nunca forma parte del camino critico activo del orquestador. Cuando una
rama candidata termina desarrollo, supera el preflight local suficiente y tiene Product/Evidence SHA
o attestation valida, se promociona con:

`node scripts/promote-candidate-final.mjs --pr <numero> --sha <head-sha-congelado>`

La promocion verifica que la PR no es draft, que el head actual coincide exactamente con el SHA
congelado, que existen los gates estables requeridos, que GitHub native auto-merge esta habilitado
en el repositorio, aplica `candidate-final` si falta y solicita auto-merge nativo con metodo
`SQUASH`. Auto-merge no sustituye branch protection, reviews requeridas, conversaciones resueltas,
checks requeridos, estado actualizado ni conflicto de merge; solo autoriza a GitHub a fusionar el
SHA congelado cuando GitHub ya lo considera apto.

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
- Una PR superseded se cierra solo cuando su sucesora contiene su ancestry necesaria y ha obtenido
  evidencia suficiente; después se eliminan ambas ramas obsoletas.
- Tras completar la migración y limpiar lo integrado, el objetivo de repositorio es conservar solo
  `main`.

## 7. Presupuesto de ejecución y procesos

Se permiten simultáneamente:

- **Una compilación Android**.
- **Una compilación Wasm**.
- **Una compilación iOS**.
- **Un emulador Android** de validación visual.
- **Un candidato Wasm** de validación visual en un puerto distinto de `4174`.
- **Un simulador iOS** de validación visual de candidato.
- Además, `http://localhost:4174/` queda reservado permanentemente para la última `main`.
- Además, se mantiene un simulador iOS separado con la última `main` para revisión manual del
  responsable del producto.

Reglas de aplicación:

- Una tarea Gradle que compila varias plataformas ocupa todas las lanes afectadas.
- Cada build registra plataforma, rama/worktree, comando, PID/cache y resultado.
- Se usan caches/worktrees aislados cuando evitan colisiones, pero no se dejan daemons acumulados.
- Al finalizar se ejecuta el cierre apropiado (`gradlew --stop` para la cache usada) y se comprueba
  que no quedan wrappers, daemons Gradle/Kotlin, Node/Chrome, servidores o procesos de esa tarea.
- No se cierra Android Studio ni un proceso ajeno al proyecto.
- Los candidatos temporales se detienen y sus puertos se liberan después de capturar evidencia.
- Los simuladores/emuladores de candidato se apagan al terminar; las instancias estables se
  conservan.
- Antes de lanzar otra tarea se auditan procesos Java, puertos `4174+`, dispositivos ADB y
  simuladores booted.
- Un proceso activo y registrado es válido; un proceso sin dueño o posterior a la finalización es
  una fuga y debe cerrarse.
- El registro de cada lane incluye propietario, PR/rama, worktree, plataforma, comando, PID (o
  UDID/puerto), propósito, SHA y resultado. Una lane ocupada no bloquea las demás; sólo se respeta
  su propia capacidad y los límites reales de CPU, memoria, dispositivos y puertos.

### Informes durante procesos largos

No se informa mediante polling del mismo job. Cada actualización útil indica: PR activa; base/head/
merge; lane remota; lanes locales ocupadas; trabajo paralelo; último resultado; bloqueo concreto y
siguiente decisión. Sólo se comunica de nuevo cuando cambia uno de esos elementos.
La espera de CI no es trabajo activo: tras el handoff del **Two-lane migration pipeline**, si no hay
cambio de estado remoto se continúa con la siguiente superficie o con trabajo auxiliar útil.

## 8. Runtimes estables

- `http://localhost:4174/`: distribución Wasm de la última `main`, HTTP 200 y sin ser sustituida por
  una rama candidata.
- Android: como máximo un emulador enlazado para validación. Debe identificarse versión instalada y
  propósito antes de reutilizarlo.
- iOS estable: un simulador con la última `main`, configuración pública local ignorada por Git y sin
  secretos dentro del artefacto.
- iOS candidato: otro simulador como máximo, usado de forma coordinada para evidencia exacta de PR.
- En el Mac Hyper-V se usa la lane de arquitectura/renderer compatible documentada por el proyecto;
  que una lane ARM no funcione en esa VM no autoriza a omitir la compilación ARM de CI.

### Mac Hyper-V sin Metal: renderer raster CPU

- Todo gate iOS ejecutado en ese Mac copia
  `~/.gradle/init.d/hyperv-compose-raster.init.gradle` dentro de
  `$GRADLE_USER_HOME/init.d/` cuando usa un `GRADLE_USER_HOME` aislado. El home aislado no puede
  omitir silenciosamente el init script que selecciona el renderer CPU.
- Antes de Gradle se exporta
  `HYPERV_RASTER_REPOSITORY=$HOME/.local/share/macos-hyperv-builder/raster-m2/repository`.
  El repositorio raster se añade a los repositorios públicos necesarios; no sustituye ni elimina
  Google, Maven Central o los repositorios públicos de JetBrains requeridos por el build.
- El preflight de resolución debe acreditar exactamente
  `org.jetbrains.skiko:skiko-iosx64:0.9.37.3-hyperv-raster.1-SNAPSHOT`. Si falta ese componente o
  Gradle resuelve el `skiko-iosx64` stock, el gate aborta antes de compilar o arrancar el simulador.
- El simulador candidato recomendado es `Quata-Raster-iOS-18-Clean`, con UDID registrado cuyo
  prefijo conocido es `3EDE`. Antes de actuar se resuelve y registra siempre el UDID completo.
  El simulador estable cuyo UDID empieza por `69D` se preserva.
- Está prohibido usar `hyperv-simulator.sh shutdown` durante un gate: detiene servicios globales y
  puede derribar el runtime estable. Arranque, apagado, borrado o limpieza se realizan solo con
  `xcrun simctl` dirigido al UDID completo del candidato; nunca con acciones globales.
- La automatización del canvas Compose usa XCTest/XCUI, coordenadas y labels accesibles dentro de
  la sesión del simulador. No se inyectan eventos remotos mediante CGEvent.

## 9. Criterios que nunca justifican un atajo

- Backend temporalmente inseguro: se implementa el contrato actual y se documenta la deuda.
- Falta de renderer nativo en una VM: se usa la lane CPU compatible y CI para arquitecturas reales.
- Un test lento o flaky: se diagnostica; no se aumenta timeout, no se omite y no se convierte en
  éxito.
- Un componente difícil de portar: se crea el adaptador real; no se reemplaza por HTML, texto,
  icono genérico o botón inerte.
- CI verde: no sustituye el gate visual ni demuestra por sí solo paridad funcional.
- Captura visual correcta: no sustituye persistencia, backend, navegación o tests.

## 10. Cierre de la migración

La migración global solo se considera terminada cuando:

- todas las pantallas del inventario tienen GO Web e iOS;
- los flujos anónimos y autenticados completos coinciden con Android;
- login/logout Web incluye `web_login` y Web Push;
- firma, configuración, distribución y requisitos de notificaciones iOS están resueltos;
- Android publicado y Web antigua siguen funcionando;
- no queda ningún fallback de producto, no-op visible o backend ficticio;
- existe evidencia exacta y el responsable del producto ha completado su ronda funcional;
- todas las PR aprobadas están fusionadas, el inventario está actualizado y las ramas/worktrees
  temporales están eliminados.
