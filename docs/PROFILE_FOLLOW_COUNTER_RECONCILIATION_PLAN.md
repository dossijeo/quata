# Plan de reconciliación de contadores de follow

Este documento es un diseño operativo. No contiene una migración desplegable y
no autoriza DML sobre producción.

## Semántica confirmada

La fuente autoritativa es `public.community_profile_follows`:

- tiene FK a perfil para seguidor y seguido, `ON DELETE CASCADE`;
- impide self-follow;
- impide duplicar el par seguidor/seguido;
- Android crea y elimina directamente esas aristas;
- el detalle de perfil Android calcula `followers.size`/`following.size` desde
  las aristas;
- los directorios Android/Web/iOS leen los campos cacheados
  `community_profiles.followers_count` y `following_count`.

No hay trigger desplegado sobre `community_profile_follows`. Existe
`recalculate_profile_follow_counts(uuid)`, pero no tiene call sites en el repo.
Las funciones legacy `followers_count_profile`/`following_count_profile`
referencian columnas inexistentes `following_id`/`follower_id`, mientras la
tabla real usa `followed_profile_id`/`follower_profile_id`.

`public.follows` es otra tabla legacy y actualmente tiene cero aristas; no debe
sumarse al contador de perfiles Community.

## Evidencia remota de solo lectura

Sin registrar IDs ni PII:

- 112 perfiles; los 112 tienen ambos contadores almacenados a cero.
- 107 aristas reales, entre 24 seguidores y 68 perfiles seguidos.
- 74 perfiles difieren de las aristas: 68 en followers y 24 en following, con
  solapamiento.
- Todos los deltas son infracontajes; no hay positivos almacenados ni
  sobreconteos.
- Máximos reales: 8 followers y 56 following.
- Aristas por mes: abril 1, mayo 3, junio 38, julio 65.
- No hay aristas desde/hacia perfiles desactivados.

Conclusión: no son métricas de legado distintas. Son caches derivadas que nunca
se han mantenido. La reconciliación exacta contra
`community_profile_follows` es la semántica correcta.

## Bloqueo de seguridad relacionado

La tabla de aristas tiene policies públicas `USING/WITH CHECK (true)` y grants
amplios, incluidos INSERT/DELETE/UPDATE para `anon`. Antes de habilitar follow
en Web o confiar en los contadores debe tener su propio guard de actor y RLS.
Esto se registra como RLS-005; no se corrige dentro de 171003.

## Propuesta reversible, separada de 171003

1. Preparar primero una migración RLS específica para follows que vincule
   `follower_profile_id` al actor activo, conserve lectura pública y pruebe
   spoof/delete ajeno/actor inactivo.
2. En una migración distinta, adquirir advisory lock transaccional y crear una
   tabla de auditoría por `batch_id` con:
   `profile_id`, contadores anteriores, contadores derivados, timestamp,
   rowcount y fingerprint SHA-256 ordenado de todas las aristas.
3. Instalar una función `SECURITY DEFINER`, con `search_path` fijo, y trigger
   `AFTER INSERT OR UPDATE OR DELETE` que recalcule desde la tabla autoritativa
   sólo los perfiles afectados. No aceptar incrementos enviados por cliente.
4. Insertar el snapshot de los 112 perfiles y actualizar los contadores desde
   agregados de aristas en la misma transacción.
5. Exigir como gates antes de commit:
   snapshot=112, mismatches iniciales=74, filas actualizadas=74,
   mismatch final=0, edges=107 y fingerprint sin cambios.
6. Conservar el snapshot para auditoría. El rollback que restaure valores
   anteriores debe abortar salvo que count+fingerprint actuales de aristas sean
   idénticos al snapshot; así nunca pisa follows creados tras el backfill.
7. Eliminar trigger/función sólo mediante rollback versionado. Si hubo tráfico,
   conservar los contadores corregidos o recalcular, nunca restaurar ceros.

## Gates de producto

- PostgreSQL concurrente: insert/delete/update de arista actualiza ambos lados,
  idempotencia y rollback.
- PostgREST con dos actores: propio permitido; spoof, delete ajeno e inactivo
  bloqueados.
- Android API-37 autenticado: toggle follow, directorio y detalle convergen;
  untoggle restaura ambos contadores.
- Feed anónimo Android/Web/iOS sigue leyendo perfiles y muestra los valores
  reconciliados.
- Realtime/cache: invalidación tras cada arista y sin doble incremento.
- Repetir el preflight de 171003 con fingerprints de roles aprobados; sólo un
  resultado completamente verde permite considerar el guard.
