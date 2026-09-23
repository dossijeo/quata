# Plan de reconciliación de contadores de follow

Este documento describe el diseño operativo y las migraciones versionadas preparadas en la PR
draft. Su presencia no autoriza DDL/DML sobre producción: el despliegue continúa sujeto a todos
los gates de backup, compatibilidad, preflight, revisión y release.

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

Actualización read-only del 22 de septiembre: el conjunto vivo creció a 178
perfiles y 129 aristas; 86 perfiles presentan drift (76 de followers y 33 de
following), sin self-follow. Esta variación confirma que los gates deben ser
dinámicos y estar ligados a fingerprints del corte, como hace la migración
versionada, en vez de fijar las cifras históricas 112/107/74.

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
4. Insertar el snapshot de todos los perfiles observados en el corte y actualizar los contadores
   desde agregados de aristas en la misma transacción.
5. Exigir antes de commit que los conteos dinámicos de perfiles, aristas y mismatches coincidan con
   el snapshot de la propia transacción, que los fingerprints no cambien y que el mismatch final
   sea cero. Las cifras históricas 112/107/74 son diagnóstico, no constantes de release.
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

## Custodia y restauración lógica previa

El backup lógico Full cifrado de esta candidata debe validarse con el alcance focal antes de
cualquier ventana de release:

```powershell
.\scripts\restore-db-logical-backup-drill.ps1 `
  -BackupSet 'C:\ruta\al\backup\release-…' `
  -EncryptionKeyFile 'C:\ruta\separada\release.key' `
  -ProfileFollowScope `
  -ExpectedCommunityProfiles <conteo-preflight> `
  -ExpectedCommunityProfileFollows <conteo-preflight>
```

El drill verifica cifrado/checksums, presencia en el TOC de tablas, datos y ACL, restaura
`community_profiles` y `community_profile_follows` en PostgreSQL 17 desechable y compara sus
conteos. No sustituye el backup administrado/PITR de Supabase: mientras no exista un restore point
enumerable, el despliegue remoto permanece bloqueado.
