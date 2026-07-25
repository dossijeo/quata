# Validacion de Web Kotlin/Wasm

## Estado del toolchain

Kotlin Multiplatform, Compose Compiler y el plugin de serialization se actualizan
conjuntamente a `2.2.21`. Compose Multiplatform permanece en `1.10.0`.
Esta combinacion elimina el ICE `Function throwLinkageError not found` que
afectaba a Kotlin `2.2.10`, cuyo compilador no era compatible con la biblioteca
estandar Wasm `2.2.20-release-333` resuelta por Compose 1.10.

Los cuerpos `js(...)` de `wasmJsMain` se expresan como IIFEs cuando contienen
sentencias. El cambio es mecanico: conserva las mismas llamadas y callbacks,
pero evita que el backend emita una sentencia como valor de una propiedad
JavaScript al empaquetar Wasm.

## Smoke de navegador

Compose 1.10 monta su canvas bajo `#quata-root.shadowRoot`; por eso el smoke
debe aceptar tanto el arbol ligero como el Shadow DOM. El script tambien
recoge excepciones, errores de red y logs de Chrome, ignora solo el aviso WebGL
conocido del navegador headless y no convierte una peticion automatica de
`favicon.ico` en un falso negativo. En Windows usa SwiftShader y limpieza
tolerante del perfil temporal de Chrome.

La validacion obligatoria del lote, ejecutada por el responsable de CI, es:

```powershell
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
node .\scripts\web-browser-smoke.mjs
.\gradlew.bat :app:assembleDebug --no-daemon
```

Un resultado verde prueba que se genero el bundle de produccion y que el
launcher monta rutas no autenticadas en Chrome. No prueba flujos autenticados
ni integracion Supabase real; esos flujos permanecen como gates funcionales
separados.
