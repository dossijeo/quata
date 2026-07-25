# Convergencia de transporte Feed

## Lote MP-A11 inicial

La lectura Feed ya tenía en `feature:feed/commonMain` los DTO (`FeedRemote*`), el
repositorio de lectura, el sondeo, la agregación y la paginación (`limit`, cursor
`beforeCreatedAt` y detalle por `postId`). Este lote mueve también el mapeo puro de
campos escalares a esos DTO: `feedRemote*FromFields`.

Los adaptadores Web e iOS únicamente convierten su representación JSON nativa
(`JsonObject` o `Map<*, *>`) a lector de campos y conservan sus detalles de
transporte.

## Límites deliberados

No se han unificado los `select` ni los errores HTTP/RLS. Hoy no son contratos
equivalentes verificables:

- iOS y Web piden columnas distintas para `community_post_likes` y
  `community_profiles`.
- Web conserva `WebPostgrestReadException` con categoría y código de estado; iOS
  conserva los errores de `URLSession` y el código HTTP nativo.
- SB-01, mientras esté pendiente de entorno, sólo verifica relaciones/RPC/buckets;
  no prueba el conjunto desplegado de columnas ni RLS de lectura.

Cambiar esos detalles antes de SB-01/SB-03 podría convertir una extracción en una
alteración del contrato PostgREST. Por ello no se tocaron URLs, `fetch`,
`URLSession`, credenciales, autenticación, RLS, endpoints ni DDL.

## Siguiente paso condicionado

Tras ejecutar SB-01 y SB-03 contra un entorno autorizado, comparar las columnas
observadas y la semántica de errores. Sólo entonces se podrá proponer un plan de
consulta/error común respaldado por evidencia E2E.
