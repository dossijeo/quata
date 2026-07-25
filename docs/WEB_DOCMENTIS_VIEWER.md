# Visor integrado de documentos Web (DocMentis)

Quata Web usa `@docmentis/udoc-viewer` **0.7.9** exclusivamente desde
`web/src/wasmJsMain`. La carga es dinámica: el SDK y su runtime Wasm no se
inicializan hasta que el usuario abre un adjunto compatible. No hay imports de
paquetes JS ni de navegador en `commonMain`.

## Compatibilidad efectiva

La versión fijada anuncia PDF, DOCX, PPTX y XLSX; son los únicos formatos que
el adaptador intenta mostrar integrado. No se reclama soporte integrado para
DOC, XLS, PPT ni RTF. Esos formatos, los tipos desconocidos, URL no válidas,
cancelaciones y errores de red/renderizado conservan el adaptador seguro
`BrowserDocumentOpenService`: descarga del archivo con URL normalizada (o el
resultado explícito que devuelva el navegador).

El visor acepta únicamente URL HTTP(S), rechaza credenciales embebidas y no
acepta Blob URL. Así no se amplía la superficie de capacidades respecto de la
política de descarga existente. Un único visor puede estar abierto: reemplazarlo
o pulsar Cerrar/Escape destruye tanto `viewer` como `client` y elimina el DOM.

## Licencia, branding y red

No se incluye licencia, token ni configuración comercial en el código. La
integración invoca `UDocClient.create()` sin opciones: se conservan branding,
atribución, licencia, comprobación de actualización y telemetría conforme a los
valores por defecto del proveedor. El runtime Wasm y las fuentes/documentos
remotos pueden requerir conectividad; una política de despliegue/CSP debe
permitir sólo los orígenes que apruebe producto/legal. No se desactiva
telemetría ni se oculta atribución sin una licencia válida externa.

## Validación manual ejecutable

Después de generar el bundle de producción:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
node scripts/web-browser-smoke.mjs --docmentis
```

`--docmentis` abre el launcher con un parámetro de prueba explícito, ejecuta la
importación dinámica real, crea y destruye un `UDocClient`, y falla si el
paquete no fue empaquetado o su ciclo de vida no se completa. No carga un
documento remoto de ejemplo: la validación funcional con PDF/DOCX/PPTX/XLSX
propio, CORS y permisos de Storage sigue siendo un E2E separado.
