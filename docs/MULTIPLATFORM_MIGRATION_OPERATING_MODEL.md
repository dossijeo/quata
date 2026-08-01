# Modelo operativo de la migración multiplataforma

Estado: **fuente de verdad vigente**
Última revisión: 1 de agosto de 2026

Este documento define cómo se completa y valida la migración de Qüata a Kotlin/Compose
Multiplatform. Si una nota, backlog, agente o PR contradice este documento, prevalece este
documento hasta que el responsable del producto lo modifique explícitamente.

## 1. Objetivo y referencia de producto

- Android publicado es la referencia funcional y visual.
- Web e iOS deben montar literalmente las mismas raíces Compose de `commonMain` que Android.
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
4. Compila y ejecuta las pruebas relevantes de las plataformas afectadas.
5. Publica commits intencionales y una PR draft. Se evitan rondas de CI por cada cambio mínimo:
   primero se valida localmente y después se usa CI sobre un SHA candidato.
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
