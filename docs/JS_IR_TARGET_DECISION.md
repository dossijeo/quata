# Decisión de target Kotlin/JS IR

Fecha de inventario: 2026-07-25. Alcance: `js(IR)` frente al producto
Kotlin/Wasm de Quata. Esta decisión no elimina todavía ningún target.

## Decisión

`js(IR)` **no es actualmente un fallback de producto**. Se considera un target
redundante de compatibilidad y se aprueba su retirada gradual, una vez se ejecute
el lote de grafo descrito abajo. El único producto Web soportado es Kotlin/Wasm
en `:web`.

La conclusión no equivale a afirmar que Web esté completo: las capacidades y
E2E pendientes siguen gobernadas por el tablero de migración.

## Evidencia inventariada

| Área | Evidencia | Conclusión |
| --- | --- | --- |
| Host y distribución | `web/build.gradle.kts` declara sólo `wasmJs { browser(); binaries.executable() }`; `web/src/wasmJsMain/.../Main.kt` es el único `main()` y los recursos PWA viven en `web/src/wasmJsMain/resources`. | No hay host ni binario JS que pueda servir al usuario. |
| Código específico JS | Sólo existen seis ficheros: `core` (`PlatformDispatchers.js.kt`, `SessionClock.js.kt`, `PlatformCapabilities.js.kt`), `designsystem` (`RichTextClock.js.kt`, `QuataWindowLayoutInfo.js.kt`) y `feature:chat` (`ChatClock.js.kt`). | Son `actual` pequeños; sus equivalentes Wasm existen y, a diferencia de ellos, los JS no implementan servicios de navegador reales. |
| Grafo | Declaran `js(IR) { browser() }` los módulos `core`, `designsystem` y las 11 features `auth`, `chat`, `externalshare`, `feed`, `neighborhoods`, `notifications`, `official`, `postcomposer`, `profile`, `settings`, `whatsnew`. Todos sus bloques `jsMain.dependencies` están vacíos. | Hay un target JS mantenido en 13 módulos sin consumidor de producto. |
| Pruebas y CI | No hay `src/jsTest`; `ios-build.yml` compila Kotlin/Native y el host Swift; `codeql.yml` ensambla Android. Los gates Web documentados son `:web:wasmJsBrowserDistribution` y `scripts/web-browser-smoke.mjs`. | Ningún gate acredita una distribución JS; por tanto JS no puede declararse soportado. |
| Publicación/consumo | No hay `maven-publish`, `MavenPublication`, publicación npm ni pipeline que consuma un artefacto JS. Las dependencias npm (DocMentis) se declaran en `wasmJsMain`. | No hay consumidor interno ni externo conocido que requiera JS IR. |
| Coste de infraestructura | `settings.gradle.kts` conserva `RepositoriesMode.PREFER_PROJECT` por la distribución Node de Kotlin/JS. Kotlin/Wasm también requiere Node/webpack, por lo que retirar JS no elimina esa necesidad. Sí reduce configuración, source sets, tareas y superficie de compatibilidad de 13 módulos. | Ahorro de mantenimiento y de grafo; no prometer ahorro de Node ni de CI sin medición posterior. |

## Alternativas descartadas

1. Conservar JS como fallback: exigiría un módulo host JS, `binaries.executable`,
   routing/adaptadores equivalentes a `wasmJsMain`, distribución, smoke de
   navegador, publicación y un gate CI. Hoy no existe ninguno y duplicaría el
   host Web, contrario a la regla de no duplicar lógica.
2. Migrar de Wasm a JS: contradice el host real, el bundle y los adaptadores
   browser que ya se validan en Wasm; no es una corrección mínima.

## Lote de retirada reversible

1. En una rama efímera independiente, retirar `js(IR)` y `jsMain` de los 13
   módulos, incluyendo los seis `actual` exclusivos JS. No tocar
   `wasmJs`/`wasmJsMain`, Android ni iOS.
2. Confirmar que ninguna referencia Gradle, script o documentación de producto
   invoca `compileKotlinJs`, `jsBrowser*` o un artefacto JS. Mantener la
   infraestructura Node porque Wasm la necesita.
3. Validar, sobre el SHA exacto: `:web:wasmJsBrowserDistribution`, smoke
   `scripts/web-browser-smoke.mjs`, `:app:compileDebugKotlin` y el workflow
   iOS completo (Kotlin/Native, framework, Swift y XCTest).
4. Medir tareas/configuración antes y después. Sólo documentar ahorro que sea
   observable; la retirada no es una optimización de bundle Wasm.
5. Si aparece un consumidor JS no inventariado, revertir el único commit de
   grafo o restaurar únicamente los módulos que demuestre necesitar, con host,
   smoke y gate JS antes de volver a llamarlo producto soportado.

## Criterio de cierre MP-A08

MP-A08 se cierra cuando la retirada esté integrada y los cuatro gates del paso
3 estén verdes, o cuando producto solicite explícitamente JS y se entregue el
host/gate/publicación enumerados en la primera alternativa. Hasta entonces la
decisión está tomada, pero su ejecución permanece pendiente y reversible.
