# Evidencia local del visor Web

El siguiente comando valida el adaptador Web de DocMentis sin credenciales ni
documentos de usuarios:

```powershell
./gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
node scripts/web-browser-smoke.mjs --docmentis
```

Al habilitar `--docmentis`, el harness crea en un directorio temporal un PDF y
un DOCX estructuralmente validos, y copia fixtures PPTX/XLSX OOXML completos
desde `scripts/fixtures/docmentis/`. Los cuatro contienen solo texto inocuo de
smoke y no proceden de documentos de usuarios.

La integración contiene un seam de éxito deliberadamente estricto: no declara
el visor listo al resolver `viewer.load()`, sino después del evento documentado
`document:load` y del callback `customPageOverlay` que DocMentis ejecuta cuando
monta un slot de página real. También exige `isLoaded` y al menos una página. El
marcador DOM `data-quata-docmentis-render-ready` solo se escribe cuando esos
cuatro hechos ocurren; no se infiere de un `iframe`, canvas, tamaño o selector
de Quata. Cualquier error del renderer o diez segundos sin ese evento hace que
el adaptador vuelva a la descarga segura.

Sin una licencia de DocMentis, una prueba completamente aislada de red **no
puede** alcanzar ese éxito: el runtime exige y verifica criptográficamente un
permiso remoto por apertura. El smoke hermético intercepta y bloquea ese permiso
de forma explícita, por lo que valida el ciclo import/client/viewer/cleanup y la
ruta de fallo segura, no el render real. No se debe interpretar una ejecución
verde de este comando como evidencia de PDF/DOCX/PPTX/XLSX renderizados.

Para obtener la evidencia de render de los cuatro formatos habrá que ejecutar el
mismo seam contra un entorno aprobado con licencia offline o permitir el origen
del permiso en staging, con CSP, telemetría y tratamiento legal aprobados. No
se ha añadido una licencia, un simulador de permiso ni una excepción de red a
esta prueba.

Tambien carga un DOCX desde un segundo origen loopback. Ese servidor simula una
URL de Storage firmada temporal: exige un token aleatorio de una sola ejecucion,
responde CORS solo al origen del launcher y no usa Supabase, cuentas, buckets ni
secretos. Tanto los dos servidores como el token, ficheros y perfil Chrome se
eliminan al terminar.

DOC/XLS/PPT heredados y RTF no se entregan a DocMentis. Permanecen en la ruta de
descarga segura, cubierta por `DocmentisDocumentPolicyTest` y
`BrowserDocumentOpenPolicyTest`. Esta evidencia no es una aprobacion de CSP,
licencia, telemetria ni CORS de produccion: esas propiedades deben validarse en
staging con las cabeceras y el origen Storage reales.
