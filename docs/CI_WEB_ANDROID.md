# CI de PR para Web/Wasm y Android

El workflow `web-android-pr.yml` valida los cambios que afectan a Android o a
los módulos multiplataforma. Usa JDK 17, Node.js 20.19.0 y Chrome 150, y ejecuta
los siguientes gates sin credenciales ni acceso a Supabase:

- `:web:wasmJsBrowserTest`, distribución Web de producción y smoke local con DocMentis;
- matriz explícita de `:app:testDebugUnitTest`, host test Android de `core` y
  los `wasmJsNodeTest` de los módulos que contienen pruebas comunes;
- los tests de `app` y `document-reader`, y `:app:assembleDebug`.

Los tests comunes Wasm se ejecutan de forma explícita con Node
(`wasmJsNodeTest` por módulo), no con el agregador `wasmJsTest`. El host Web
mantiene `:web:wasmJsBrowserTest`: instala Chrome y exporta `CHROME_BIN` desde
el output real de `browser-actions/setup-chrome` antes de invocar Gradle. Cada
tarea conserva el fallo cuando no se descubre ninguna, sin configurar
`failOnNoDiscoveredTests=false`.

En GitHub Actions, `web/karma.config.d/ci-chrome-no-sandbox.js` registra sólo
para `CI=true` el launcher `ChromeHeadlessNoSandbox`. Es necesario porque el
runner Ubuntu puede denegar el sandbox de espacios de nombres de Chromium; no
altera la configuración ni el launcher de desarrollo.

`:app:lintDebug` y `:document-reader:lintDebug` son temporalmente
**informativos**: la deuda heredada actual es de 54 errores/312 advertencias en
`app` y 20 errores/418 advertencias en `document-reader`. El job publica sus
informes y un resumen aunque lint falle; no convierte en opcionales los tests ni
el ensamblado debug. La deuda debe eliminarse antes de convertir lint en gate
requerido.

Los artefactos conservan JUnit, lint, métricas y logs del smoke, inventario del
bundle, distribución Web y APK debug durante 14 días. Los límites de cada job y
de los pasos costosos evitan consumir indefinidamente la cuota si Kotlin/Wasm,
`wasm-opt`, Chrome o Android lint quedan bloqueados.

## Requisito externo pendiente

El workflow sólo versiona y nombra los checks. Convertirlos en checks requeridos
de la rama protegida sigue pendiente en la configuración de GitHub: una persona
con permisos de administración debe habilitar branch protection o rulesets para
que un PR afectado no pueda integrarse hasta que todos estos jobs estén verdes.
Ese cambio externo no se puede acreditar mediante un commit del repositorio.
