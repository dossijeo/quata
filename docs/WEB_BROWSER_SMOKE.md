# Smoke de navegador para Quata Web

Este smoke carga la distribución real de Compose/Wasm en Chrome headless y comprueba que el shell
monta, sin excepciones no capturadas, en las rutas `#auth`, `#feed`, `#chat`, `#official`,
`#settings` y `#share-target`.

No usa Supabase ni crea una sesión falsa: tras verificar `#auth`, sólo activa el indicador local de
shell para recorrer las rutas protegidas. Los repositorios permanecen sin token y sin configuración
de backend; por tanto es una prueba de arranque/routing, no una prueba de integración remota.

## Ejecución

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
node .\scripts\web-browser-smoke.mjs
```

El script sirve temporalmente `web/build/dist/wasmJs/productionExecutable`, arranca un perfil de
Chrome descartable y lo elimina al finalizar. No instala paquetes npm ni deja un servidor en
segundo plano. En Node 20 activa internamente el flag experimental estándar de `WebSocket` que
necesita para CDP. Si Chrome no está en la ubicación estándar, se puede indicar explícitamente:

```powershell
node .\scripts\web-browser-smoke.mjs --chrome 'C:\ruta\a\chrome.exe'
```

También puede probarse otra distribución ya construida:

```powershell
node .\scripts\web-browser-smoke.mjs --dist 'C:\ruta\a\distribution'
```
