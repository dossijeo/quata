# Modelo operativo de la migración multiplataforma

Estado: **fuente de verdad vigente** · Refactorización documental: 11 de septiembre de 2026.

Este documento define cómo se completa y valida la migración de Qüata a Kotlin/Compose
Multiplatform. Si una nota, backlog, agente o PR contradice este documento, prevalece este
documento hasta que el responsable del producto lo modifique explícitamente.

**Entrada de lectura:** este esquema y sus runbooks son el mismo modelo. El detalle se consulta por fase o plataforma, no como otra lista de obligaciones. Se conserva el alcance de las reglas; no se añaden gates ni se cambian permisos.
**Alcance de producto:** [inventario maestro](SCREEN_MIGRATION_INVENTORY_V2.md). **Referencia publicada:** [AAB v32](ANDROID_PUBLISHED_REFERENCE_V32.md). Requisito, implementación, integración y evidencia son dimensiones distintas; el historial no sustituye al estado actual.

## 1. Objetivo y referencia de producto

Android publicado manda en comportamiento y aspecto. Web e iOS montan las mismas raíces Compose de `commonMain`; ViewModels, estado, reglas, composición, eventos y navegación son comunes salvo necesidad nativa real. Las diferencias del sistema se resuelven con abstracciones/adaptadores, no con producto paralelo.
La paridad prevalece sobre tamaño, bundle, Wasm, rendimiento y duración de CI. No degradar funciones sin aprobación explícita; no HTML alternativo, maquetas, no-op, persistencia ficticia ni errores convertidos en éxito. Revisar también los estados `COMÚN CON LÍMITES`, `PARCIAL` y `AUSENTE`.
Detalle: [producto y referencia](runbooks/migration/PRODUCT_AND_NAVIGATION.md#1-objetivo-y-referencia-de-producto).

## 2. Contrato de navegación y autenticación

<a id="superficies-públicas"></a>
<a id="superficies-y-acciones-privadas"></a>
<a id="contrato-web-específico"></a>

| Situación | Comportamiento requerido |
|---|---|
| Lectura pública | Feed, Comunidades, Oficial, Notificaciones y perfiles públicos sin sesión; header y navegación visibles. Feed nunca exige login para leer. |
| Ruta/acción privada | Chats, Cuenta/SOS, Ajustes privados y acciones restringidas exigen sesión. Primero diálogo común «Ya tengo cuenta / Registrar» sobre el contenido. |
| Auth y retorno | Login/Registro/Recuperar fuera del shell. Autenticar restaura ruta y acción; cancelar/abandonar elimina lo pendiente. Conservar origen; logout revoca/limpia y vuelve al Feed público. |
| Web | `web_login` + Web Push; publishable key nunca como bearer de usuario. Sesión renovable y propagación honesta de HTTP, timeout y cancelación. |

Detalle: [contrato completo](runbooks/migration/PRODUCT_AND_NAVIGATION.md#2-contrato-de-navegación-y-autenticación).

## 3. Seguridad y compatibilidad con producción

Operaciones focales, revisadas, compatibles y reversibles cubiertas por la [autorización permanente](MIGRATION_REMOTE_OPERATIONS_AUTHORIZATION.md): ejecutarlas autónomamente; informar antes de desplegar no es pedir permiso ni esperar entre plataformas.
Antes de mutar: diff/hash/config exactos, sin sobrescritura concurrente, actor autorizado, snapshot, rollback, registro previo y privacidad. Tras fallo: reconciliar/restaurar antes de repetir; retirar journals/locks después de verificar cierre y devolver flags temporales al estado seguro.
No romper Android publicado, Web antigua ni Feed anónimo; la deuda RLS se documenta, no se endurece incompatiblemente. No secretos en clientes, Git, logs o capturas; metadatos públicos sólo en copia temporal con original/hash intactos.
Las excepciones —datos reales ajenos, destrucción, RLS amplio, credenciales de producción, infraestructura ajena, billing, DNS, tiendas, alertas a terceros/SOS, incompatibilidad o falta de rollback— siguen requiriendo autorización específica. [Condiciones completas](runbooks/migration/SECURITY_AND_REMOTE.md).

## 4. Flujo por pantalla

Las fases siguientes condensan el procedimiento existente; no son nuevos checks ni sustituyen sus condiciones detalladas.

| Fase | Condición y siguiente acción |
|---|---|
| Delimitar | Orquestador revisa Android e inventario: raíz común, datos, eventos, navegación, mutaciones y adaptadores. |
| Implementar | Rama/worktree propios; sustituir fallbacks con producto real. Preflight local proporcional de plataformas afectadas, sin builds relevantes ni comprobaciones reproducibles pendientes. |
| Publicar draft | Integrar `origin/main`, resolver conflictos, repetir checks afectados, revisar diff y congelar la tanda. Publicar PR draft; revisión independiente de la PR exacta. |
| Acreditar integrada | Web/iOS sobre `refs/pull/<N>/merge` exacto, con base/head/merge verificados. Evidencia funcional/visual real, cleanup y revisión; un head-only no da GO integrado. |
| Promover autónomamente | Alcance terminado + revisión independiente + preflight/evidencia válidos + recursos reconciliados + límites explícitos → Ready, head congelado, `candidate-final`, auto-merge. No esperar otro permiso ni el resultado de CI final para promover. |
| Certificar / handoff | Fast gates verdes **y** jobs finales reales iniciados → candidata inmutable; trabajar la siguiente unidad conforme al two-lane. GitHub sólo integra cuando se cumplen los checks y branch protection. |
| Cerrar | Confirmar merge/CI final; integrar cierre del inventario y nota de prueba para el propietario; limpiar sólo ramas/worktrees inequívocamente seguros. |

Roles, secuencia y comandos: [WORKFLOW](runbooks/migration/WORKFLOW.md). Nombres de agentes del procedimiento original conservados allí, sin reasignarlos en esta refactorización.
<a id="inventario--estado-operativo-actual"></a>
**Inventario = estado operativo actual:** antes del merge sólo estado provisional; tras el merge, cierre documental obligatorio integrado en `main`, conservando límites y sin promover padres/hermanos. El handoff de certificación no equivale al cierre operativo. [Procedimiento](runbooks/migration/BRANCHES_AND_CLOSEOUT.md#inventario--estado-operativo-actual).

## 5. Evidencia y gates

<a id="preflight-local-candidato-y-certificación-remota"></a>
**Preflight:** compilar, probar, recorrer e inspeccionar plataformas afectadas; backend/sesión reales cuando corresponde. Replicar los contratos rápidos de CI, imports Wasm focales y `diff --check` antes de congelar/publicar. [Detalle y GO](runbooks/migration/EVIDENCE_AND_CI.md).
<a id="identidad-obligatoria-del-candidato-integrado"></a>
**Identidad integrada:** fijar base `origin/main`, head `refs/pull/<N>/head` y merge `refs/pull/<N>/merge`; dos padres exactos y en ese orden. Registrar PR/SHA completos en informe, logs y capturas. Si la identidad no vale, repetir el gate; head-only se marca **DESCARTADOS: HEAD-ONLY**. [Regla exacta](runbooks/migration/EVIDENCE_AND_CI.md#identidad-obligatoria-del-candidato-integrado).
<a id="productevidence-sha-y-attestation-documental"></a>
**Attestation:** cambios documentales posteriores sólo reutilizan evidencia si el validador acepta el diff `productSha..HEAD` y el manifest. Código, tests, runners, recursos, workflow/config/dependencias o evidencia incompleta invalidan esa reutilización; repetir lo afectado. La reutilización entre revisiones requiere regla formal, inputs intactos y diff revisado, no parecido entre SHAs. [Manifest y excepciones](runbooks/migration/EVIDENCE_AND_CI.md#productevidence-sha-y-attestation-documental).

| Checks requeridos | Interpretación |
|---|---|
| `PR fast contracts and focal imports`; `iOS fast contracts` | Gates rápidos; un verde preparatorio no acredita integración. |
| `Web/Android final certification gate`; `iOS final certification gate`; `CodeQL final security gate` | GO final sólo con sus jobs exactos correctos, o excepción `docs_only` explícita y fail-closed. |

Sin `candidate-final`, los jobs finales omitidos por esa guarda significan **no certificado**, no GO. CI en GitHub Actions es la certificación final en runners limpios; no es el laboratorio inicial. Un defecto reproducible que escapó del preflight exige incorporar su prevención antes de la siguiente promoción. [Diagnóstico](runbooks/migration/WORKFLOW.md#fallo-anomalía-o-integración-detenida).
<a id="two-lane-migration-pipeline--native-auto-merge"></a>
**Two-lane migration pipeline + native auto-merge:** una candidata final + una siguiente superficie. No hay handoff sólo por hacer push. Si B depende de A, parte de su SHA congelado; tras integrar A, sincronizar B en checkpoint natural. Anomalías por watcher o checkpoints, nunca polling ocioso. [Promoción, backfill, dependencias y fallos](runbooks/migration/WORKFLOW.md).

## 6. Ramas, commits y PR

Prefijo `codex/`; ramas/worktrees aislados; revisor no edita el del implementador. Commits pequeños con propósito real. No push forzado, rebase de rama compartida ni escritura directa en `main`; reparaciones sólo por fast-forward verificado.
Draft hasta GO independiente. GitHub gestiona auto-merge y borrado remoto normal; limpieza retroactiva/local con plan revisado y herramientas existentes, nunca forzar ante trabajo no publicado, dependencias o ambigüedad. [Comandos, SID y superseded](runbooks/migration/BRANCHES_AND_CLOSEOUT.md#6-ramas-commits-y-pr).

## 7. Presupuesto de ejecución y procesos

| Plataforma | Capacidad simultánea de candidata | Instancia estable que se preserva |
|---|---|---|
| Android | 1 compilación + 1 emulador visual | Identificar versión instalada y propósito antes de reutilizarlo. |
| Wasm | 1 compilación + 1 candidato visual en puerto distinto de 4174 | Última `main` en `http://localhost:4174/`, HTTP 200. |
| iOS | 1 compilación + 1 simulador candidato | Otro simulador con última `main` para revisión manual. |

Una tarea multiplataforma ocupa cada lane afectada. Auditar procesos/dispositivos/puertos antes de lanzar; registrar dueño, PR/rama, worktree, comando, PID/cache o UDID/puerto, propósito, SHA y resultado. Cerrar sólo recursos propios; no Android Studio ni procesos ajenos. [Detalle](runbooks/migration/EXECUTION_ENVIRONMENT.md#7-presupuesto-de-ejecución-y-procesos).
<a id="informes-durante-procesos-largos"></a>
**Informes:** sólo ante cambio útil de estado; indicar PR, base/head/merge, lanes, trabajo paralelo, resultado, bloqueo y siguiente decisión. [Regla completa](runbooks/migration/EXECUTION_ENVIRONMENT.md#informes-durante-procesos-largos).

## 8. Runtimes estables

No sustituir runtimes estables por candidatos. Usar la configuración pública local ignorada por Git y mantener secretos fuera del artefacto. [Runtimes](runbooks/migration/EXECUTION_ENVIRONMENT.md#8-runtimes-estables).
<a id="mac-hyper-v-sin-metal-renderer-raster-cpu"></a>
**Mac Hyper-V:** renderer CPU, init script y repositorio raster, resolución exacta de Skiko y UDID completo. Sin shutdown global ni CGEvent; la VM no exime de ARM en CI. [Procedimiento y contexto del helper](runbooks/migration/EXECUTION_ENVIRONMENT.md#mac-hyper-v-sin-metal-renderer-raster-cpu).
<a id="testing-por-capas-preferencia-para-nuevas-unidades-y-suites-con-churn-real"></a>
**Testing:** Compose UI primero para comportamiento común; Maestro sólo tras piloto repetible y privado; XCTest para bordes nativos o sin sustituto probado. No reescribir suites sanas. Los fakes/targets aislados no prueban backend ni otras plataformas; para rutas visuales complejas usar la grabación de macro visual con el recorder y anclas estables; ante `missing_stable_anchor`, fallar cerrado y resolver el paso antes del replay. [Estrategia completa](runbooks/migration/TESTING_STRATEGY.md).

## 9. Criterios que nunca justifican un atajo

Un build, CI verde o una captura aislada no sustituyen producto común, backend, navegación, persistencia y comparación visual en la misma sesión. Un test lento/flaky se diagnostica: no subir timeout, omitirlo ni declararlo éxito. Una limitación real de plataforma requiere adaptador y evidencia, no una pantalla degradada. [Criterios íntegros](runbooks/migration/PRODUCT_AND_NAVIGATION.md#9-criterios-que-nunca-justifican-un-atajo).
**Detener/replantear:** tercer workaround de la misma interacción; mutación incierta; riesgo de secretos; incompatibilidad no resuelta; evidencia contradictoria; revisión bloqueante; alcance mezclado; SHA desconocido o dependencia real pendiente. No confundir estas condiciones con falta de otra autorización de promoción. [Promoción](runbooks/migration/WORKFLOW.md#autorización-permanente-de-promoción) · [operaciones remotas](runbooks/migration/SECURITY_AND_REMOTE.md).

## 10. Cierre de la migración

GO Web/iOS de todo el inventario; flujos anónimos/autenticados equivalentes a Android; `web_login` + Web Push; firma/configuración/distribución/notificaciones iOS resueltas; clientes publicados preservados; cero fallbacks/no-op/backend ficticio; evidencia exacta y ronda funcional del propietario; PRs aprobadas integradas, inventario actual y temporales retirados. [Lista original completa](runbooks/migration/PRODUCT_AND_NAVIGATION.md#10-cierre-de-la-migración).

Mapa de procedencia para revisar esta reorganización, no para cada iteración: [MIGRATION_DOCUMENTATION_MAP.md](MIGRATION_DOCUMENTATION_MAP.md).
