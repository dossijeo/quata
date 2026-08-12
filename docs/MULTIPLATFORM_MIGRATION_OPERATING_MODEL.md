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

El preflight rápido exacto de CI es obligatorio antes de congelar/publicar: ejecuta los contratos
rápidos que replica la automatización remota, imports Wasm focales y `diff --check`. Un candidato
no se publica si esa réplica falla. Los workflows y sus gates finales son *fail-closed*: un job
final omitido, cancelado o fallido nunca puede convertir el gate requerido en verde.
Los checks requeridos de certificación son exactamente **Web/Android final certification gate**,
**iOS final certification gate**, **Analyze java-kotlin** y **Analyze javascript-typescript**;
los dos primeros sólo son GO cuando todos sus jobs finales exactos concluyen correctamente.

GitHub Actions es la **certificación final en runners limpios**, no el primer lugar donde descubrir
que una implementación no compila ni funciona. Si CI revela un defecto reproducible localmente, el
informe lo clasifica como **DEFECTO ESCAPADO DEL PREFLIGHT LOCAL** e incorpora obligatoriamente el
comando, test o contrato preventivo al preflight antes de publicar el siguiente candidato.

Durante esa certificación se permite preparar localmente la siguiente unidad, analizar conflictos,
revisar contratos, preparar tests focales y documentación. Se prohíbe fusionar otra PR, publicar
rondas repetidas de trabajo futuro o alterar el head congelado salvo defecto bloqueante demostrado.
La cola de integración no convierte el equipo en una sola lane: cada lane local libre debe poder
avanzar trabajo preparatorio aislado y reproducible. Ese trabajo se mantiene fuera de la promoción
remota hasta que el candidato activo cierre; no puede reutilizar evidencia final ni competir por
`main`.

La certificación CI en GitHub se observa de forma asíncrona. Una vez lanzados los workflows del
candidato congelado, el orquestador no debe quedarse esperando ociosamente a que terminen jobs de
varios minutos: registra el PR, base/head/merge y checks esperados, deja una comprobación periódica
no bloqueante, y usa las lanes locales libres para avanzar otra unidad segura. Sólo vuelve al
candidato cuando cambia el estado remoto, aparece un fallo que clasificar, o todos los checks
requeridos están verdes para proceder al merge. En ningún caso este trabajo paralelo puede modificar
el SHA congelado ni crear una segunda candidata final.

La comprobación periódica de GitHub Actions se planifica como seguimiento asíncrono, no como espera
activa del turno: si la certificación remota no ha cambiado, la siguiente acción debe ser trabajo
local preparatorio, aislado y seguro, con evidencia propia y sin mezclarlo con el SHA congelado del
candidato.

Regla persistente: CI nunca debe mantener al orquestador en espera pasiva. Tras lanzar la
certificacion remota, se consulta GitHub Actions por polling asincrono y se cambia a trabajo local
independiente hasta que haya una transicion de estado que exija clasificar fallo, revalidar o
promover el candidato.

Todo defecto descubierto tras publicar se clasifica antes de corregirlo: **DEFECTO ESCAPADO DEL
PREFLIGHT LOCAL** si era reproducible con comandos/artefactos locales disponibles; defecto de
runner, caché, toolchain o servicio exclusivo remoto si no lo era. En el primer caso no basta con
arreglar el código: se añade el gate preventivo, se ejecuta y se registra antes de la siguiente
promoción. El head previo queda invalidado y cualquier CI cancelado se conserva sólo como
diagnóstico, nunca como evidencia GO.

Los runners de autenticación iOS que invocan `xcodebuild` mediante un `.xctestrun` y
`QUATA_IOS_AUTH_E2E_FILE` explícito deben verificar el resultado semántico del test: un proceso con
salida `0` no es PASS si el test figura como `SKIPPED` o no llegó a ejecutarse. El runner falla en
esos casos y conserva el diagnóstico redactado.

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
La espera de CI no es trabajo activo: si no hay cambio de estado remoto, se continúa con tareas
locales independientes y se revisa el resultado de GitHub Actions de manera asíncrona.

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
