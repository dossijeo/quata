# Evidencia local del visor Web

El siguiente comando valida el adaptador Web de DocMentis sin credenciales ni
documentos de usuarios:

```powershell
./gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
node scripts/web-browser-smoke.mjs --docmentis
```

Al habilitar `--docmentis`, el harness crea en un directorio temporal un PDF y
un DOCX estructuralmente validos. Cada uno pasa por la importacion dinamica de
`@docmentis/udoc-viewer`, crea cliente/visor, espera una superficie renderizada
y destruye ambos objetos. La prueba falla si queda el host DOM temporal. PPTX y
XLSX siguen admitidos por el adaptador y cubiertos por la politica Wasm, pero
requieren documentos OOXML completos: sus fixtures funcionales pertenecen al
E2E con archivos de staging, no a generadores minimales que el parser rechaza.

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
