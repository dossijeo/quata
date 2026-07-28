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
en Storage ni en Supabase. El producto dispone ya de una compuerta de preparación
de ciclo de vida: exige el evento documentado `document:load`, el callback
`customPageOverlay`, `isLoaded` y al menos una página antes de declarar listo el
visor. Ese contrato se prueba sin inferir éxito de un `iframe`, canvas, tamaño o
selector de Quata.

Sin embargo, la sonda `--docmentis` hermética bloquea deliberadamente el permiso
remoto de DocMentis. Por ello sólo ejercita importación, cliente, visor, limpieza
y el cierre seguro hacia `BrowserDocumentOpenService`; no acredita que un
PDF/DOCX/XLSX/PPTX haya alcanzado la compuerta ni que se haya renderizado. Tampoco
es una prueba de píxeles, frame o salida visual de esos formatos.

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
modo que no existe un seam unitario que equivalga a un render. La construcción de
`QLPreviewController` no acredita por sí sola que Quick Look haya decodificado ni
mostrado una fixture.

## Bloqueo para afirmar renderizado real

Para Web, queda disponer de un entorno autorizado con licencia offline o con el
origen de permiso aprobado en staging para ejecutar y observar la compuerta de
ciclo de vida sobre fixtures. Que esa compuerta se alcance es evidencia de ciclo
de vida, no de píxeles ni de salida visual; una afirmación visual requiere una
captura o una inspección de UI verificada de forma independiente.

Para iOS, falta una ejecución en simulador que copie las cuatro fixtures al
sandbox, presente Quick Look y capture/inspeccione el resultado. Aún no se ha
registrado evidencia de simulador con fixture de Quick Look; los XCTest de
compilación/enlace existentes no la sustituyen.

No se ha modificado RLS, Supabase, Storage ni los adaptadores Android.

## Siguiente validación honesta

1. Ejecutar la compuerta Web existente en un entorno aprobado con licencia offline
   o permiso de staging para PDF, DOCX, XLSX y PPTX, y conservar evidencia no
   secreta a nivel de evento. Esa evidencia debe mantenerse separada de cualquier
   afirmación visual; ésta requerirá capturas o inspección UI verificadas.
2. Ejecutar un XCTest/UI test de iOS en simulador de forma exclusiva: crear/copiar
   esas mismas fixtures, presentar `QLPreviewController`, esperar la miniatura o
   el árbol visible y guardar `xcresult` y capturas.
3. Mantener separados esos resultados de las pruebas de admisión, ZIP y
   fail-closed; ninguna de ellas sustituye la evidencia visual.
