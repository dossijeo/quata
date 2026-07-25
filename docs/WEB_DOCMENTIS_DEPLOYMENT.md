# Despliegue Web de DocMentis

## Alcance y propietario de cabeceras

Quata no contiene actualmente configuración de hosting ni archivos que emitan
cabeceras HTTP (`_headers`, `netlify.toml`, `vercel.json`, `firebase.json`,
nginx o equivalente). `web/src/wasmJsMain/resources/index.html` solo forma
parte de la distribución estática. Por ello este documento es la especificación
para quien publique `web/build/dist/wasmJs/productionExecutable`; no añade una
meta CSP que pudiera diferir de la cabecera real del CDN/hosting.

No se publican en `index.html` licencias de DocMentis, tokens, service-role de
Supabase ni claves VAPID privadas. Las dos metas de Supabase existentes son
configuración pública de cliente y deben seguir recibiendo exclusivamente URL
pública y clave publicable.

## Superficie de red de `@docmentis/udoc-viewer` 0.7.9

La lista se obtuvo inspeccionando el tarball fijado por
`kotlin-js-store/wasm/yarn.lock`, no una versión posterior del SDK. Al invocar
`UDocClient.create()` sin opciones, que es exactamente lo que hace
`WebDocmentisDocumentOpenService`, el proveedor usa estas rutas/orígenes:

| Motivo | Origen o ruta que debe aprobarse de forma explícita |
| --- | --- |
| Chunks Web, módulo DocMentis y primer intento de `udoc_bg.wasm` | `'self'` |
| Worker DocMentis creado desde el código empaquetado | `worker-src 'self' blob:` |
| Fallback del Wasm si el asset empaquetado no se resuelve | `https://cdn.jsdelivr.net/npm/@docmentis/udoc-viewer@0.7.9/dist/src/wasm/udoc_bg.wasm` |
| Apertura de cada adjunto | el origen HTTPS exacto de adjuntos de Quata (por ejemplo el Storage/CDN aprobado), con CORS para el origen de la Web |
| Permiso online del tier libre | `https://www.docmentis.com/api/udoc-viewer/permit` |
| Telemetría por apertura | `https://www.docmentis.com/api/udoc-viewer/telemetry` |
| Comprobación no bloqueante de versión | `https://registry.npmjs.org/@docmentis/udoc-viewer/latest` |
| CSS de fuentes que el SDK habilita por defecto | `https://fonts.googleapis.com` |
| Binarios de las fuentes declaradas por ese CSS | `https://fonts.gstatic.com` |

No usar comodines como `https://*.docmentis.com`, `https:`, `data:` en
`script-src`, ni permitir cualquier origen de adjuntos. Si se cambia la versión
del paquete, si se configura `baseUrl`, una licencia, fuentes propias o se
deshabilita alguna opción, hay que volver a inspeccionar el tarball y actualizar
esta tabla antes del despliegue.

DocMentis crea un `Worker` desde `blob:`. Ese permiso es necesario para esta
versión del paquete y no autoriza scripts Blob en la página: `script-src` debe
seguir sin `blob:`. La aplicación Compose/Wasm también requiere que la política
de scripts permita compilar/instanciar Wasm en los navegadores objetivo; se debe
probar la directiva CSP escogida contra el bundle real en Chrome, Firefox y
Safari, sin sustituirla por `unsafe-eval` de forma indiscriminada.

## Baseline CSP para staging

El responsable de hosting debe partir de una política restrictiva equivalente
a la siguiente y sustituir `<ORIGEN_ADJUNTOS_QUATA>` por el único origen de
Storage/CDN aprobado. Es un *baseline* de revisión, no una cabecera que Quata
pueda declarar desde este repositorio:

```text
default-src 'self';
base-uri 'self';
object-src 'none';
frame-ancestors 'self';
script-src 'self' 'wasm-unsafe-eval';
style-src 'self' 'sha256-eb6QDCbmfs7HUMW7vNCCBXEBfnFjcegGGmHy4WEQnP4=' https://fonts.googleapis.com;
font-src 'self' https://fonts.gstatic.com;
worker-src 'self' blob:;
connect-src 'self' <ORIGEN_ADJUNTOS_QUATA> https://cdn.jsdelivr.net https://www.docmentis.com https://registry.npmjs.org https://fonts.googleapis.com https://fonts.gstatic.com;
img-src 'self' data: blob: <ORIGEN_ADJUNTOS_QUATA>;
manifest-src 'self';
```

El hash de `style-src` corresponde al único bloque de estilo actual de
`index.html`; si cambia ese bloque se recalcula antes de publicar. No se admite
`'unsafe-inline'` para scripts ni estilos como atajo. `wasm-unsafe-eval` es la
excepción de Wasm que se debe validar: si un navegador objetivo requiere otra
directiva documentada, se añade solo esa directiva y se registra la evidencia.

La CSP de Quata completa también necesita las allowlists ya exigidas por
Supabase/Web Push. No se deben ampliar estas entradas para hacer que DocMentis
funcione: su origen debe quedar separado y revisable.

## Decisiones de proveedor que no se ocultan

Sin licencia, DocMentis solicita un permiso firmado por apertura. El Wasm
también envía telemetría anónima por apertura, conserva un identificador aleatorio
en `localStorage`, consulta npm para detectar versiones y permite descargar
fuentes Google para tipografías ausentes. La integración de Quata usa sus
valores por defecto y mantiene la atribución visible. No se introduce una clave
de licencia, `disableTelemetry`, `disableUpdateCheck`, `googleFonts: false` ni
una técnica para ocultar la atribución en código o configuración de despliegue.

Producto, legal y privacidad deben aceptar esas comunicaciones antes de activar
el visor en producción. Bloquearlas mediante CSP no convierte el visor libre en
offline: un bloqueo del permiso hace que no pueda renderizar y Quata debe caer
al flujo de descarga segura.

## Gate de despliegue

Antes de promover la Web, con cabeceras reales de staging y un adjunto de cada
formato integrado (PDF, DOCX, PPTX y XLSX), comprobar:

1. `:web:wasmJsBrowserDistribution` y `node scripts/web-browser-smoke.mjs --docmentis` pasan con la distribución que se publicará.
2. La consola no informa bloqueos CSP, CORS ni fallos de worker/Wasm al abrir y
   cerrar cada documento; el visor se destruye al cerrar o reemplazarlo.
3. La pestaña Network solo contiene los orígenes de la tabla, más los orígenes
   ya aprobados de Quata; registrar método, ruta, status y motivo de cada uno.
4. El origen de adjuntos responde con CORS mínimo (`Access-Control-Allow-Origin`
   del origen Web, no `*` si se usan credenciales) y no expone cabeceras/sesiones
   innecesarias.
5. Se verifica que permiso, telemetría, version check y fuentes se comportan
   como el proveedor declara. Si legal no los aprueba, el visor no se habilita
   hasta disponer de una licencia/configuración aprobada y una nueva revisión.

La evidencia de este gate es de despliegue real; el smoke local no prueba CSP,
CORS, permisos remotos ni límites del tier libre.
