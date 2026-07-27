# Validación funcional del visor de documentos (Web e iOS)

## Estado comprobado el 2026-07-27

La migración tiene un contrato común para clasificar PDF, RTF y Office, pero el
renderizado sigue siendo responsabilidad del host. Por tanto, que una fixture sea
un ZIP OOXML válido no demuestra que se haya mostrado en pantalla.

### Web

El recorrido de producción es:

`ChatBrowserHostContent` → `WebChatHost.openWebAttachment` →
`WebDocmentisDocumentOpenService` → `@docmentis/udoc-viewer` para PDF/DOCX/XLSX/PPTX.

Las fixtures locales de `scripts/web-browser-smoke.mjs` son válidas y no escriben
en Storage ni en Supabase. Sin embargo, la sonda `--docmentis` actual prueba
deliberadamente el caso de fallo seguro: observa que el overlay se monta, que se
destruye y que el servicio pasa al `BrowserDocumentOpenService`. No comprueba un
frame, canvas ni señal de documento cargado por UDoc. En consecuencia, no puede
usarse como evidencia de que un PDF/DOCX/XLSX/PPTX se haya renderizado.

No hay un renderer alternativo local para Office: el fallback de navegador descarga
RTF y Office, y sólo delega PDF HTTP(S) en el visor nativo del navegador. Forzar el
SDK a aceptar los ficheros locales sin una garantía de su API de permisos/licencia
sería una modificación de producto y no una prueba aislada.

### iOS

El recorrido de producción es:

`UIDocumentPickerViewController(asCopy: true)` → referencia `file:` dentro del
sandbox → `IosDocumentOpenService` → `QLPreviewController`.

La importación como copia y la comprobación de existencia del fichero son reales.
Los XCTest actuales confirman que Quick Look está enlazado, que el picker anuncia
los UTI correctos y que se rechazan URL remotas; no presentan una fixture ni
esperan una miniatura/render de Quick Look. `IosQuickLookDataSource` es privado y
Quick Look sólo decodifica de forma asíncrona tras presentar el controlador, de
modo que no existe un seam unitario que equivalga a un render.

## Bloqueo para afirmar renderizado real

Para Web, falta una señal estable y documentada del SDK UDoc que pruebe que cada
fixture terminó de cargar sin depender de DocMentis remoto. Para iOS, falta una
ejecución en simulador que copie las cuatro fixtures al sandbox, presente Quick
Look y capture/inspeccione el resultado. La política de esta tanda reserva los
simuladores para otra validación, por lo que este trabajo no los usa.

No se ha modificado RLS, Supabase, Storage ni los adaptadores Android.

## Siguiente validación honesta

1. Obtener del proveedor de UDoc una vía oficialmente soportada para carga local
   sin red o licencia de producción, y hacer que la sonda Web espere su evento de
   documento listo para PDF, DOCX, XLSX y PPTX.
2. Ejecutar un XCTest/UI test de iOS en simulador de forma exclusiva: crear/copiar
   esas mismas fixtures, presentar `QLPreviewController`, esperar la miniatura o
   el árbol visible y guardar `xcresult` y capturas.
3. Mantener separados esos resultados de las pruebas de admisión, ZIP y
   fail-closed; ninguna de ellas sustituye la evidencia visual.
