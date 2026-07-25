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

No se incluye licencia, token ni configuración comercial en el código. El
wrapper JavaScript del paquete se publica como MIT, pero el binario Wasm se
distribuye bajo la licencia de runtime de DocMentis. La integración invoca
`UDocClient.create()` sin opciones: conserva la atribución, telemetría,
comprobación de actualización y descarga de Google Fonts que el proveedor tenga
activadas por defecto.

En la versión fijada, el uso libre/sin licencia requiere una verificación online
por cada apertura antes de renderizar. Si el servicio de permisos no responde o
se alcanza su límite, el documento no se muestra y Quata vuelve a la descarga
segura. Una licencia comercial puede cambiar esas condiciones; no se debe
desactivar telemetría, ocultar atribución ni suponer funcionamiento offline sin
que producto/legal provea y apruebe esa licencia. La CSP de despliegue debe
autorizar explícitamente sólo los orígenes necesarios para documentos, permisos,
telemetría, comprobación de versión y fuentes.

La revisión de licencia, límites y datos debe hacerse contra la
[documentación publicada de la versión 0.7.9](https://www.npmjs.com/package/@docmentis/udoc-viewer/v/0.7.9),
no contra supuestos derivados del wrapper de Quata.

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
