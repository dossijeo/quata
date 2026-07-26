# RLS-005 — Integridad de follows y contadores

## Estado

Trabajo preparado y validado en la rama
`codex/fix-community-follows-integrity`. No se ha desplegado ni se ha ejecutado
DML remoto.

Por decisión del release manager, los SQL están en `supabase/templates/` y no
en `supabase/migrations/`: los timestamps se asignarán cuando se resuelva el
ledger bloqueado de 171003/171004. Hay dos unidades independientes:

1. `community_profile_follows_actor_guard.sql.template`;
2. `community_profile_follow_counter_reconciliation.sql.template`.

Cada una tiene rollback propio y transacciones explícitas.

## Evidencia remota de solo lectura

`community_profile_follows` tiene RLS activado, pero cuatro policies públicas:
`allow all` y read/insert/delete con condiciones `true`. `anon` y
`authenticated` tienen todos los privilegios de tabla. No existe trigger de
actor.

La tabla tiene 107 aristas válidas y restricciones FK, no-self y unicidad. Las
112 filas de `community_profiles` conservan ambos contadores cacheados a cero:
74 perfiles difieren de las aristas. No hay datos en la tabla legacy
`public.follows`.

No hay trigger productor. Android crea/elimina aristas directamente. El detalle
de perfil deriva los tamaños de las listas, pero los directorios
Android/Web/iOS leen los caches a cero. El RPC
`recalculate_profile_follow_counts` existe y estaba ejecutable por
PUBLIC/anon/auth; no tiene call sites de cliente y queda reservado a
`service_role`. `toggle_follow_profile` usa el Auth UUID como profile UUID y
columnas legacy; queda deprecado y no ejecutable por clientes.

## Guard de actor

- SELECT público permanece idéntico.
- Sólo un actor autenticado y activo puede insertar una arista cuyo
  `follower_profile_id` sea su perfil canónico.
- El objetivo también debe estar activo.
- Sólo el dueño activo o un admin activo puede borrar; outsider falla con
  `42501`.
- UPDATE y toda mutación anónima quedan sin grant/policy.
- Un trigger `SECURITY INVOKER` replica la frontera fail-loud.
- `service_role` conserva mantenimiento y cascadas.

El contrato coincide con Android: `toggleProfileFollow` hace GET seguido de
INSERT/DELETE con el profile ID de la sesión. Web/iOS mantienen FollowUser
fail-closed.

## Reconciliación reversible

La segunda plantilla:

1. adquiere advisory lock transaccional;
2. crea batch y snapshot de los contadores anteriores/derivados;
3. guarda profile/mismatch/edge counts y SHA-256 ordenados tanto del conjunto
   de perfiles como de las aristas;
4. instala un trigger `AFTER INSERT OR UPDATE OR DELETE` que recalcula ambos
   lados desde la tabla autoritativa, bloqueando antes los profile IDs en orden
   UUID para evitar lost updates y deadlocks recíprocos;
5. actualiza sólo perfiles con diferencias;
6. exige que rowcount actualizado=mismatch inicial, snapshot completo,
   fingerprints estables y cero mismatches antes de commit;
7. revoca el recálculo manual a PUBLIC/anon/auth.

El rollback se niega a restaurar si count/fingerprint de perfiles o aristas
cambió desde el snapshot o si los counters ya no son los derivados guardados.
Si siguen idénticos, restaura los valores anteriores, elimina el productor y
limpia las tablas de auditoría.

Tras el primer follow real se usa la plantilla forward-safe
`community_profile_follow_counter_producer_decommission.sql.template`: retira
el trigger sin restaurar caches ni borrar snapshot/índices. Su rollback
versionado vuelve a instalar el productor.

`__MIGRATION_VERSION__` es un placeholder obligatorio: release management debe
reemplazarlo por el timestamp/nombre definitivo al promover la plantilla.

## Evidencia aislada

`scripts/run-community-profile-follows-integrity-test.ps1` aplica y prueba en
PostgreSQL 16 desechable:

- lectura anónima;
- INSERT propio;
- rechazo de spoof, anon e inactivo;
- rechazo de DELETE ajeno;
- DELETE propio y admin;
- snapshot, rowcounts, backfill y producer trigger;
- recalculate RPC denegado a cliente y permitido a servicio;
- dos conexiones concurrentes para inserts/deletes recíprocos y target
  compartido, sin deadlock ni lost update;
- rollback de ambas unidades, reproducción controlada del fallo histórico y
  reaplicación segura;
- limpieza.

`scripts/run-community-profile-follows-postgrest-test.ps1` repite por HTTP real
PostgREST 12 actor/anon/admin/inactivo y convergencia de counters.

Resultados:

```text
COMMUNITY_PROFILE_FOLLOWS_INTEGRITY_TEST_OK
COMMUNITY_PROFILE_FOLLOWS_POSTGREST_TEST_OK
```

Los contenedores y redes se eliminan al terminar.

## Gates antes de promoción

- Asignar timestamps respetando el ledger global.
- Revisión independiente del SQL ya renombrado, sin placeholders.
- Aplicar guard antes de reconciliación.
- Confirmar preflight remoto de sólo lectura y fingerprints aprobados.
- Staging: PostgreSQL/PostgREST más Android API-37 autenticado, feed anónimo,
  toggle/untoggle y cache/realtime.
- No activar FollowUser Web/iOS hasta retirar su contención mediante evidencia
  específica.
- No mezclar este rollout con RLS-004 ni con 171003 mientras sus dependencias
  Android sigan bloqueadas.
