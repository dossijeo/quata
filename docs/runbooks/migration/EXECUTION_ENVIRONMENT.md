# Entorno y presupuesto de ejecución

Detalle del [modelo operativo](../../MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md); forma parte de la misma fuente de verdad, con el mismo alcance y autorizaciones.

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
- La automatización del canvas Compose sigue la [estrategia por capas](TESTING_STRATEGY.md), usando anclas
  semánticas dentro de la sesión del simulador. No se inyectan eventos remotos mediante CGEvent.
